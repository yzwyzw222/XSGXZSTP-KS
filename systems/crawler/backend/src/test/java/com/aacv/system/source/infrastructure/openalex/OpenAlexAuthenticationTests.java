package com.aacv.system.source.infrastructure.openalex;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aacv.system.source.domain.SourceConnectionSettings;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

class OpenAlexAuthenticationTests {

    @Test
    void optionalKeyUsesAuthorizationHeaderAndNeverChangesOfficialUrl() {
        var builder = new OpenAlexRestClientFactory("test-only-placeholder").builder(settings());
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openalex.org/works"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-only-placeholder"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        builder.build().get().uri("/works").retrieve().toBodilessEntity();
        server.verify();
    }

    @Test
    void blankKeyKeepsAnonymousModeAndControlCharactersAreRejected() {
        var builder = new OpenAlexRestClientFactory(" ").builder(settings());
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openalex.org/works"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        builder.build().get().uri("/works").retrieve().toBodilessEntity();
        server.verify();
        assertThrows(IllegalArgumentException.class, () -> new OpenAlexRestClientFactory("value\r\nInjected: x"));
        assertThrows(IllegalArgumentException.class, () -> new OpenAlexRestClientFactory("x".repeat(513)));
    }

    private SourceConnectionSettings settings() {
        return new SourceConnectionSettings(1, 1, Duration.ofSeconds(5), Duration.ofSeconds(10), 1, 1024);
    }
}
