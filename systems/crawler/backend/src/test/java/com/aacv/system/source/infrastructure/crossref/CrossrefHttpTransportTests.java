package com.aacv.system.source.infrastructure.crossref;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.source.domain.OpaqueCursor;
import java.net.URI;
import java.net.URLDecoder;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossrefHttpTransportTests {

    @Test
    void everyCursorRequestRepeatsAllParametersWithoutDateSort() {
        CrossrefHttpTransport transport = new CrossrefHttpTransport(
                new CrossrefRestClientFactory("contact@example.org"), new CrossrefRequestGate());
        CrawlScope scope = new CrawlScope(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "graph learning",
                List.of(),
                List.of(),
                List.of("10.1000/example"),
                List.of("0000-0003-1613-5981"),
                List.of("https://ror.org/03yrm5c26"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T23:59:59Z"),
                5,
                500);

        Map<String, String> first = query(transport.buildWorksUri(scope, OpaqueCursor.first()));
        Map<String, String> next = query(transport.buildWorksUri(scope, new OpaqueCursor("opaque+/=cursor")));

        assertEquals("100", first.get("rows"));
        assertEquals("*", first.get("cursor"));
        assertEquals("opaque+/=cursor", next.get("cursor"));
        assertTrue(transport.buildWorksUri(scope, new OpaqueCursor("opaque+/=cursor"))
                .getRawQuery().contains("opaque%2B%2F%3Dcursor"));
        Map<String, String> firstWithoutCursor = new LinkedHashMap<>(first);
        Map<String, String> nextWithoutCursor = new LinkedHashMap<>(next);
        firstWithoutCursor.remove("cursor");
        nextWithoutCursor.remove("cursor");
        assertEquals(firstWithoutCursor, nextWithoutCursor);
        assertTrue(first.get("filter").contains("from-pub-date:2026-08-01"));
        assertTrue(first.get("filter").contains("until-index-date:2026-08-31T23:59:59Z"));
        assertEquals("contact@example.org", first.get("mailto"));
        assertFalse(first.containsKey("sort"));
        assertFalse(first.containsKey("order"));
        assertFalse(List.of(first.get("select").split(",")).contains("language"));
    }

    @Test
    void fixedEndpointIgnoresUrlShapedSearchTextAndOversizedBodiesAreRejected() throws Exception {
        CrossrefHttpTransport transport = new CrossrefHttpTransport(
                new CrossrefRestClientFactory(""), new CrossrefRequestGate());
        CrawlScope scope = new CrawlScope(
                null, null, "https://attacker.invalid/redirect", List.of(), List.of(), 1, 10);

        URI uri = transport.buildWorksUri(scope, OpaqueCursor.first());

        assertEquals("https", uri.getScheme());
        assertEquals("api.crossref.org", uri.getHost());
        assertEquals("/works", uri.getPath());
        assertThrows(
                CrossrefClientException.class,
                () -> CrossrefHttpTransport.readBounded(
                        new ByteArrayInputStream(new byte[11]), 10, 200));
        assertEquals(
                10,
                CrossrefHttpTransport.readBounded(
                        new ByteArrayInputStream(new byte[10]), 10, 200).length);
    }

    @Test
    void invalidEnvironmentEmailIsNotSent() {
        CrossrefHttpTransport transport = new CrossrefHttpTransport(
                new CrossrefRestClientFactory("not-an-email"), new CrossrefRequestGate());
        CrawlScope scope = new CrawlScope(null, null, null, List.of(), List.of(), 1, 10);

        assertFalse(query(transport.buildWorksUri(scope, OpaqueCursor.first())).containsKey("mailto"));
    }

    private Map<String, String> query(URI uri) {
        Map<String, String> parameters = new LinkedHashMap<>();
        Arrays.stream(uri.getRawQuery().split("&")).forEach(parameter -> {
            String[] parts = parameter.split("=", 2);
            parameters.put(decode(parts[0]), parts.length == 1 ? "" : decode(parts[1]));
        });
        return parameters;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
