package com.xsyu.academicgraph.application.crawl;

import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportPaperItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportRequest;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportSummary;
import com.xsyu.academicgraph.application.admin.DataImportService;
import com.xsyu.academicgraph.infrastructure.openalex.OpenAlexClientException;
import com.xsyu.academicgraph.infrastructure.openalex.OpenAlexHttpTransport;
import com.xsyu.academicgraph.infrastructure.openalex.OpenAlexHttpTransport.OpenAlexHttpResponse;
import com.xsyu.academicgraph.infrastructure.openalex.OpenAlexProperties;
import com.xsyu.academicgraph.infrastructure.openalex.OpenAlexResponseParser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OpenAlex 在线爬取应用服务（管理员功能）。
 * 链路：关键词校验 → HTTP 抓取（带指数退避重试）→ JSON 解析 → 映射成导入条目 →
 * 复用 {@link DataImportService#importPapers} 落库（判重/名称缓存/Outbox 事件全部继承）。
 *
 * 设计要点：
 *  - 同步单页：一次请求只抓一页（≤25 条），响应时间可控，不需要批处理/定时任务
 *  - 网络调用在事务外：本方法不加 @Transactional，HTTP 等待不占数据库连接；
 *    落库事务在 importPapers 内部开始
 *  - 重试策略照 feature/Luo 的 OpenAlexDataSourceAdapter：429/502/503/504 与
 *    网络抖动可重试，指数退避 + 随机抖动，避免对公共 API 造成冲击
 */
@Service
@RequiredArgsConstructor
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    /** 可重试的 HTTP 状态码：限流与网关/服务端暂时性故障 */
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 502, 503, 504);

    private final OpenAlexProperties properties;
    private final OpenAlexHttpTransport transport;
    private final OpenAlexResponseParser parser;
    private final OpenAlexWorkMapper mapper;
    private final DataImportService dataImportService;

    /**
     * 按关键词爬取 OpenAlex 论文并落库。
     * @param keyword    搜索关键词（必填）
     * @param maxRecords 期望条数（1~25，缺省用配置的 perPage）
     * @return 与文件导入同构的导入汇总（IMPORTED/SKIPPED/FAILED 逐条明细）
     */
    public ImportSummary crawl(String keyword, Integer maxRecords) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("搜索关键词最长 200 字符");
        }
        int perPage = maxRecords == null ? properties.getPerPage()
                : Math.min(Math.max(maxRecords, 1), properties.getPerPage());

        byte[] body = executeWithRetry(() -> transport.fetchWorks(trimmed, perPage));
        List<ImportPaperItem> items = parser.parseWorks(body).stream()
                .map(mapper::toImportItem)
                .toList();
        if (items.isEmpty()) {
            // OpenAlex 返回了空结果集（关键词太生僻）：正常返回空汇总，由前端展示"0 条"
            return new ImportSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of());
        }
        log.info("OpenAlex 关键词「{}」抓到 {} 条论文，开始落库", trimmed, items.size());
        return dataImportService.importPapers(new ImportRequest(items));
    }

    /** 带重试的单页抓取：照 Luo 原版——可重试状态或异常时退避等待后重来，耗尽次数则抛出 */
    private byte[] executeWithRetry(PageFetch operation) {
        int requestCount = 0;
        while (true) {
            requestCount++;
            try {
                OpenAlexHttpResponse response = operation.execute();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                boolean retryable = RETRYABLE_STATUSES.contains(response.statusCode());
                if (!retryable || requestCount > properties.getMaxRetries()) {
                    throw new OpenAlexClientException(
                            "HTTP_" + response.statusCode(),
                            retryable,
                            response.statusCode(),
                            "OpenAlex 请求返回 HTTP " + response.statusCode()
                                    + (response.statusCode() == 429 ? "（请求过于频繁）" : "") + "，已放弃");
                }
                log.warn("OpenAlex 返回 HTTP {}，第 {} 次尝试失败，准备重试",
                        response.statusCode(), requestCount);
                sleep(response.retryAfter() == null
                        ? exponentialBackoff(requestCount)
                        : response.retryAfter());
            } catch (OpenAlexClientException exception) {
                if (!exception.isRetryable() || requestCount > properties.getMaxRetries()) {
                    throw exception;
                }
                log.warn("OpenAlex 请求失败（{}），第 {} 次尝试，准备重试",
                        exception.getCategory(), requestCount);
                sleep(exponentialBackoff(requestCount));
            }
        }
    }

    /**
     * 指数退避 + 随机抖动：等待区间是 [上限/2, 上限] 的随机值，
     * 上限按 500ms × 2^(次数-1) 增长、封顶 30 秒——防止大量客户端同时重试再次打挂上游。
     */
    private Duration exponentialBackoff(int requestCount) {
        long upperBound = Math.min(30_000, 500L << Math.min(requestCount - 1, 6));
        long lowerBound = Math.max(1, upperBound / 2);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(lowerBound, upperBound + 1));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAlexClientException(
                    "INTERRUPTED", false, null, "OpenAlex 重试等待被中断", exception);
        }
    }

    @FunctionalInterface
    private interface PageFetch {
        OpenAlexHttpResponse execute();
    }
}
