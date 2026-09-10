package com.aacv.system.source.infrastructure.openalex;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.aacv.system.source.application.SourceEntityLookupException;
import com.aacv.system.source.application.SourceQuotaExhaustedException;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceEntity;
import tools.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

class OpenAlexEntityLookupTests {
    private MockRestServiceServer server;
    private OpenAlexEntityLookup lookup;
    private OpenAlexRestClientFactory factory;
    private static final SourceConnectionSettings SETTINGS = new SourceConnectionSettings(
            10, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 2, 1024 * 1024);

    @BeforeEach
    void setUp() {
        var builder = new OpenAlexRestClientFactory("").builder(SETTINGS);
        server = MockRestServiceServer.bindTo(builder).build();
        factory = mock(OpenAlexRestClientFactory.class);
        when(factory.create(any())).thenReturn(builder.build());
        lookup = new OpenAlexEntityLookup(new OpenAlexHttpTransport(factory, new OpenAlexRequestGate(Clock.systemUTC())),
                new ObjectMapper());
    }

    @Test
    void autocompleteEncodesNamesAndKeepsSameNameCandidatesDistinct() {
        server.expect(request -> {
            assertEquals("api.openalex.org", request.getURI().getHost());
            assertEquals("/autocomplete/authors", request.getURI().getPath());
            assertEquals("q=张三 & Smith+{x}", URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8));
            assertTrue(request.getURI().getRawQuery().contains("%26"));
        }).andRespond(withSuccess("""
                {"results":[{"id":"https://openalex.org/A1","display_name":"张三","hint":"大学甲","works_count":20},
                {"id":"https://openalex.org/A2","display_name":"张三","hint":"大学乙","works_count":null}]}
                """, MediaType.APPLICATION_JSON));
        var result = lookup.search(SETTINGS, SourceEntity.Kind.AUTHORS, "张三 & Smith+{x}");
        assertEquals(2, result.size());
        assertEquals(new SourceEntity("A1", "张三", "大学甲", 20L), result.getFirst());
        assertNull(result.get(1).worksCount());
        verify(factory).create(new SourceConnectionSettings(10, 1, Duration.ofSeconds(3), Duration.ofSeconds(8), 0, 256 * 1024));
        server.verify();
    }

    @Test
    void legacyIdsAreResolvedInOneBoundedRequestWithInstitutionHints() {
        server.expect(request -> {
            assertEquals("/institutions", request.getURI().getPath());
            String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
            assertTrue(query.contains("filter=ids.openalex:I1|I2"));
            assertTrue(query.contains("per_page=50"));
        }).andRespond(withSuccess("""
                {"results":[{"id":"https://openalex.org/I1","display_name":"大学甲","geo":{"city":"北京","country":"中国"}},
                {"id":"https://openalex.org/I9","display_name":"未请求的大学"}]}
                """, MediaType.APPLICATION_JSON));
        assertEquals(List.of(new SourceEntity("I1", "大学甲", "北京 · 中国", null)),
                lookup.resolve(SETTINGS, SourceEntity.Kind.INSTITUTIONS, List.of("I1", "I2")));
        server.verify();
    }

    @Test
    void authorResolutionUsesCurrentAffiliationField() {
        server.expect(request -> assertTrue(request.getURI().getQuery().contains("last_known_institutions")))
                .andRespond(withSuccess("""
                        {"results":[{"id":"https://openalex.org/A1","display_name":"作者",
                        "last_known_institutions":[{"display_name":"某大学"}]}]}
                        """, MediaType.APPLICATION_JSON));
        assertEquals("某大学", lookup.resolve(SETTINGS, SourceEntity.Kind.AUTHORS, List.of("A1")).getFirst().hint());
        server.verify();
    }

    @Test
    void malformedAndWrongEntityResponsesFailExplicitly() {
        for (String body : List.of("{}", "null", "not-json", "{\"results\":[{\"id\":\"I1\",\"display_name\":\"机构\"}]}",
                "{\"results\":[{\"id\":\"A1\",\"display_name\":\" \"}]}")) {
            setUp();
            server.expect(request -> {}).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThrows(SourceEntityLookupException.class, () -> lookup.search(SETTINGS, SourceEntity.Kind.AUTHORS, "作者"));
            server.verify();
        }
    }

    @Test
    void emptyResultsAreAllowedButHttpFailuresAndOversizedResponsesAreErrors() {
        server.expect(request -> {}).andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        assertEquals(List.of(), lookup.search(SETTINGS, SourceEntity.Kind.AUTHORS, "不存在"));
        server.verify();
        for (HttpStatus status : List.of(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.UNAUTHORIZED, HttpStatus.SERVICE_UNAVAILABLE)) {
            setUp();
            server.expect(request -> {}).andRespond(withStatus(status).body("不应透传的来源错误"));
            var error = assertThrows(SourceEntityLookupException.class,
                    () -> lookup.search(SETTINGS, SourceEntity.Kind.AUTHORS, "作者"));
            assertFalse(error.getMessage().contains("不应透传"));
            server.verify();
        }
        setUp();
        server.expect(request -> {}).andRespond(withSuccess("x".repeat(256 * 1024 + 1), MediaType.APPLICATION_JSON));
        assertThrows(SourceEntityLookupException.class, () -> lookup.search(SETTINGS, SourceEntity.Kind.AUTHORS, "作者"));
        server.verify();
    }

    @Test
    void interactiveWaitTimesOutWithoutLeakingConcurrencyPermit() {
        var gate = new OpenAlexRequestGate(Clock.systemUTC());
        try (var permit = gate.acquire(SETTINGS)) {
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertThrows(OpenAlexClientException.class,
                    () -> gate.acquire(SETTINGS, Duration.ofMillis(20))));
        }
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            try (var permit = gate.acquire(SETTINGS, Duration.ofMillis(500))) { assertNotNull(permit); }
        });
    }

    @Test
    void timeoutAndExhaustedQuotaHaveSafeActionableErrors() {
        var transport = mock(OpenAlexHttpTransport.class);
        var service = new OpenAlexEntityLookup(transport, new ObjectMapper());
        when(transport.searchEntities(any(), any(), anyString()))
                .thenThrow(new OpenAlexClientException("TIMEOUT", true, null, "内部网络细节"))
                .thenThrow(new SourceQuotaExhaustedException(Instant.now().plusSeconds(60)));
        assertTrue(assertThrows(SourceEntityLookupException.class,
                () -> service.search(SETTINGS, SourceEntity.Kind.AUTHORS, "作者")).getMessage().contains("超时"));
        assertTrue(assertThrows(SourceEntityLookupException.class,
                () -> service.search(SETTINGS, SourceEntity.Kind.AUTHORS, "作者")).getMessage().contains("每日额度已耗尽"));
    }

    @Test
    void keywordAndSelectedEntitiesAreCombinedInWorksQuery() {
        server.expect(request -> {
            var params = org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            assertEquals("/works", request.getURI().getPath());
            assertEquals("graph neural networks", URLDecoder.decode(params.getFirst("search"), StandardCharsets.UTF_8));
            assertEquals("from_publication_date:2025-01-01,to_publication_date:2025-12-31,author.id:A1|A2,institutions.id:I3",
                    URLDecoder.decode(params.getFirst("filter"), StandardCharsets.UTF_8));
        }).andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        var scope = new CrawlScope(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "graph neural networks",
                List.of("https://openalex.org/A1", "A2"), List.of("I3"), 1, 100);
        new OpenAlexHttpTransport(factory, new OpenAlexRequestGate(Clock.systemUTC())).fetchWorks(SETTINGS, scope, OpaqueCursor.first());
        server.verify();
    }
}
