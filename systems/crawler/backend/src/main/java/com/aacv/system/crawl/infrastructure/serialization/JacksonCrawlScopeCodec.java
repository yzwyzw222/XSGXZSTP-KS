package com.aacv.system.crawl.infrastructure.serialization;

import com.aacv.system.crawl.application.port.CrawlScopeCodec;
import com.aacv.system.crawl.domain.CrawlScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class JacksonCrawlScopeCodec implements CrawlScopeCodec {

    private final ObjectMapper objectMapper;

    public JacksonCrawlScopeCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String encode(CrawlScope scope) {
        try {
            return objectMapper.writeValueAsString(scope);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("采集任务参数无法序列化", exception);
        }
    }

    @Override
    public CrawlScope decode(String json) {
        try {
            return objectMapper.readValue(json, CrawlScope.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("数据库中的采集任务参数无效", exception);
        }
    }

    @Override
    public String hash(CrawlScope scope) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(encode(scope).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}
