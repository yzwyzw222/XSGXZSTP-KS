package com.xsyu.academicgraph.infrastructure.openalex;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenAlex 数据源配置（application.yaml 的 app.openalex 段）。
 * 从 feature/Luo 移植的简化版：只保留单页搜索需要的几个参数，
 * 去掉密钥、配额门控、游标分页等完整采集链路才需要的配置。
 */
@ConfigurationProperties(prefix = "app.openalex")
public class OpenAlexProperties {

    /** OpenAlex 官方 API 根地址（固定 https://api.openalex.org） */
    private String baseUrl = "https://api.openalex.org";

    /** 单页条数上限：本平台每次只爬一页，25 条足够演示且不会撞上速率限制 */
    private int perPage = 25;

    /** 建立 TCP 连接的超时 */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** 等待响应的超时（OpenAlex 偶尔较慢，给足 30 秒） */
    private Duration responseTimeout = Duration.ofSeconds(30);

    /** 遇到 429/5xx 等可重试状态时的最大重试次数 */
    private int maxRetries = 3;

    /** 联系邮箱：OpenAlex 官方建议填写以进入"礼貌池"（polite pool），不填也能用公共池 */
    private String mailto = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getPerPage() {
        return perPage;
    }

    public void setPerPage(int perPage) {
        this.perPage = perPage;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getMailto() {
        return mailto;
    }

    public void setMailto(String mailto) {
        this.mailto = mailto;
    }
}
