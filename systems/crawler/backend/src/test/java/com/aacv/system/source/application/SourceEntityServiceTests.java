package com.aacv.system.source.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aacv.system.source.api.SourceEntityController;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.application.port.SourceEntityLookup;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceEntity;
import com.aacv.system.source.domain.SourceType;
import com.aacv.system.shared.infrastructure.web.ApiExceptionHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SourceEntityServiceTests {
    private AnnotationConfigApplicationContext context;
    private SourceEntityService service;
    private DataSourceRepository repository;
    private SourceEntityLookup lookup;
    private MockMvc mvc;
    private static final SourceConnectionSettings SETTINGS = new SourceConnectionSettings(
            2, 1, Duration.ofSeconds(3), Duration.ofSeconds(10), 1, 1024 * 1024);

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean DataSourceRepository repository() { return mock(DataSourceRepository.class); }
        @Bean SourceEntityLookup lookup() { return mock(SourceEntityLookup.class); }
        @Bean SourceEntityService service(DataSourceRepository repository, SourceEntityLookup lookup) {
            return new SourceEntityService(repository, lookup);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(SourceEntityService.class);
        repository = context.getBean(DataSourceRepository.class);
        lookup = context.getBean(SourceEntityLookup.class);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "operator", null, List.of(new SimpleGrantedAuthority("SOURCE_READ"))));
        when(repository.findById(1)).thenReturn(Optional.of(source(SourceType.OPENALEX, true)));
        mvc = MockMvcBuilders.standaloneSetup(new SourceEntityController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); context.close(); }

    @Test
    void searchesTrimmedNamesAndResolvesLegacyIdsWithoutChangingTaskData() throws Exception {
        var entity = new SourceEntity("A1", "同名作者", "某大学", 20L);
        when(lookup.search(SETTINGS, SourceEntity.Kind.AUTHORS, "同名作者")).thenReturn(List.of(entity));
        mvc.perform(get("/api/v1/sources/1/entities/authors").param("query", " 同名作者 "))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].displayName").value("同名作者"));
        service.resolve(1, "authors", List.of("https://openalex.org/A1", "A1", "A2"));
        verify(lookup).resolve(SETTINGS, SourceEntity.Kind.AUTHORS, List.of("A1", "A2"));
        verify(repository, never()).update(any(), anyLong());
    }

    @Test
    void invalidKindNamesAndIdsNeverReachRemoteLookup() {
        for (String query : List.of("", " ", "x".repeat(201), "abc\nxyz")) {
            assertThrows(IllegalArgumentException.class, () -> service.search(1, "authors", query));
        }
        assertThrows(IllegalArgumentException.class, () -> service.search(1, "authors", null));
        assertThrows(IllegalArgumentException.class, () -> service.search(1, "works", "abc"));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(1, "authors", List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(1, "authors", null));
        for (String id : List.of("I1", "A1|A2", "https://invalid.test/A1", "A1,works_count:>0")) {
            assertThrows(IllegalArgumentException.class, () -> service.resolve(1, "authors", List.of(id)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(1, "authors", java.util.Collections.nCopies(51, "A1")));
        verifyNoInteractions(lookup);
    }

    @Test
    void disabledUnsupportedAndMissingSourcesAreRejected() throws Exception {
        when(repository.findById(1)).thenReturn(Optional.of(source(SourceType.OPENALEX, false)));
        mvc.perform(get("/api/v1/sources/1/entities/authors").param("query", "作者"))
                .andExpect(status().isBadRequest());
        when(repository.findById(1)).thenReturn(Optional.of(source(SourceType.CROSSREF, true)));
        mvc.perform(get("/api/v1/sources/1/entities/institutions").param("query", "大学"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sources/9/entities/authors").param("query", "作者"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(lookup);
    }

    @Test
    void bothOperationsRequireSourceReadPermission() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("researcher", null, List.of()));
        assertThrows(AccessDeniedException.class, () -> service.search(1, "authors", "作者"));
        assertThrows(AccessDeniedException.class, () -> service.resolve(1, "institutions", List.of("I1")));
        verifyNoInteractions(repository, lookup);
    }

    @Test
    void upstreamFailureReturnsExplicitSafeProblemDetails() throws Exception {
        when(lookup.search(any(), any(), anyString())).thenThrow(new SourceEntityLookupException("名称查询超时，请重试"));
        mvc.perform(get("/api/v1/sources/1/entities/authors").param("query", "作者"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("SOURCE_LOOKUP_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("名称查询超时，请重试"));
        mvc.perform(get("/api/v1/sources/1/entities/authors/resolve").param("ids", "I1"))
                .andExpect(status().isBadRequest());
    }

    private DataSourceConfiguration source(SourceType type, boolean enabled) {
        return new DataSourceConfiguration(1, DataSourceConfiguration.sourceCode(type), type,
                DataSourceConfiguration.baseUri(type), enabled, SETTINGS, "测试来源", null, null,
                0, 0, Instant.EPOCH, Instant.EPOCH);
    }
}
