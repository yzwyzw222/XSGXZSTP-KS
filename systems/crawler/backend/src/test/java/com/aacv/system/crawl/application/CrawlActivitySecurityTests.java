package com.aacv.system.crawl.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.aacv.system.crawl.application.port.CrawlRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CrawlActivitySecurityTests.Config.class)
class CrawlActivitySecurityTests {
    @Autowired private CrawlTaskService service;

    @Test
    @WithMockUser(authorities = "CRAWL_RUN_READ")
    void runReaderCannotReadSchedules() {
        assertEquals(List.of(), service.findLatestRuns(List.of(1L)));
        assertThrows(AccessDeniedException.class, () -> service.findSchedules(List.of(1L)));
    }

    @Test
    @WithMockUser(authorities = "CRAWL_SCHEDULE_MANAGE")
    void scheduleManagerCannotReadRuns() {
        assertEquals(List.of(), service.findSchedules(List.of(1L)));
        assertThrows(AccessDeniedException.class, () -> service.findLatestRuns(List.of(1L)));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean CrawlTaskService service() {
            return new CrawlTaskService(mock(CrawlRepository.class), null, null, null, null, null, null, null, null, null);
        }
    }
}
