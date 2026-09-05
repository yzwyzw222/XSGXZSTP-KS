package com.aacv.system.source.infrastructure.openalex;

import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class OpenAlexRestClientFactory {

    private final String apiKey;

    OpenAlexRestClientFactory(@Value("${OPENALEX_API_KEY:}") String apiKey) {
        String normalized = apiKey == null ? "" : apiKey.trim();
        if (normalized.length() > 512 || normalized.chars().anyMatch(value -> value < 33 || value > 126)) {
            throw new IllegalArgumentException("OpenAlex API Key格式无效，请检查本机配置");
        }
        this.apiKey = normalized;
    }

    RestClient create(SourceConnectionSettings settings) {
        return builder(settings).build();
    }

    RestClient.Builder builder(SourceConnectionSettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.responseTimeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(DataSourceConfiguration.OPENALEX_BASE_URI.toString())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "AACV-System/0.0.1");
        // 密钥仅在固定官方地址的请求头中发送，不进入URL、数据库或探测响应。
        if (!apiKey.isEmpty()) builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        return builder;
    }
}
