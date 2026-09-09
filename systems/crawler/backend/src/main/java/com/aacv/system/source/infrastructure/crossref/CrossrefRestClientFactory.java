package com.aacv.system.source.infrastructure.crossref;

import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import java.net.http.HttpClient;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class CrossrefRestClientFactory {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String contactEmail;

    CrossrefRestClientFactory(@Value("${CROSSREF_CONTACT_EMAIL:}") String contactEmail) {
        String normalized = contactEmail == null ? "" : contactEmail.trim();
        this.contactEmail = EMAIL_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    RestClient create(SourceConnectionSettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.responseTimeout());
        return RestClient.builder()
                .baseUrl(DataSourceConfiguration.CROSSREF_BASE_URI.toString())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "AACV-System/0.0.1 Crossref-stage4")
                .build();
    }

    String contactEmail() {
        return contactEmail;
    }
}
