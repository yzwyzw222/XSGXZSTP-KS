package com.aacv.system.source.infrastructure.openalex;

import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import java.net.http.HttpClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class OpenAlexRestClientFactory {

    RestClient create(SourceConnectionSettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.responseTimeout());
        return RestClient.builder()
                .baseUrl(DataSourceConfiguration.OPENALEX_BASE_URI.toString())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "AACV-System/0.0.1 OpenAlex-stage3")
                .build();
    }
}
