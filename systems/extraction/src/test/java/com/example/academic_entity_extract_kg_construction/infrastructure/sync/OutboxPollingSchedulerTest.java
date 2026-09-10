package com.example.academic_entity_extract_kg_construction.infrastructure.sync;

import com.example.academic_entity_extract_kg_construction.domain.model.Author;
import com.example.academic_entity_extract_kg_construction.domain.model.OutboxEvent;
import com.example.academic_entity_extract_kg_construction.domain.model.Paper;
import com.example.academic_entity_extract_kg_construction.domain.model.Venue;
import com.example.academic_entity_extract_kg_construction.domain.model.enums.VenueType;
import com.example.academic_entity_extract_kg_construction.domain.repository.OutboxEventRepository;
import com.example.academic_entity_extract_kg_construction.domain.repository.PaperRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPollingSchedulerTest {
    private final OutboxEventRepository events = mock(OutboxEventRepository.class);
    private final PaperRepository papers = mock(PaperRepository.class);
    private final Driver driver = mock(Driver.class);
    private final Session session = mock(Session.class);
    private final TransactionContext transaction = mock(TransactionContext.class);
    private final OutboxPollingScheduler scheduler = new OutboxPollingScheduler(events, driver, new ObjectMapper(), papers);
    private final List<Map<String, Object>> writes = new ArrayList<>();
    private OutboxEvent event;

    @BeforeEach
    void setUp() {
        event = new OutboxEvent();
        event.setId(7L);
        event.setAggregateId(120L);
        event.setEventType("PAPER_UPDATED");
        event.setPayload("{\"id\":120,\"title\":\"旧标题\"}");
        when(events.findUnprocessedEvents(any())).thenReturn(List.of(event));
        when(driver.session()).thenReturn(session);
        when(session.executeWrite(any())).thenAnswer(call -> {
            TransactionCallback<?> callback = call.getArgument(0);
            return callback.execute(transaction);
        });
        when(transaction.run(anyString(), anyMap())).thenAnswer(call -> {
            writes.add(call.getArgument(1));
            return mock(Result.class);
        });
    }

    @Test
    void replaysLegacyEventsFromCurrentPersistentFieldsAndRelationships() {
        Paper paper = paper(120L, "当前标题", 2024, 33);
        Author author = new Author();
        author.setId(1L);
        author.setName("作者甲");
        paper.getAuthors().add(author);
        Venue venue = new Venue();
        venue.setId(3L);
        venue.setName("测试期刊");
        venue.setType(VenueType.JOURNAL);
        paper.setVenue(venue);
        paper.getReferences().add(paper(104L, "被引论文", 2020, 210));
        when(papers.findById(120L)).thenReturn(Optional.of(paper));

        scheduler.pollAndSync();

        assertEquals(Map.of("id", 120L, "title", "当前标题", "year", 2024, "citationCount", 33), writes.getFirst());
        assertTrue(writes.stream().anyMatch(write -> List.of(Map.of("id", 1L, "name", "作者甲")).equals(write.get("authors"))));
        assertTrue(writes.stream().anyMatch(write -> Long.valueOf(3).equals(write.get("venueId"))));
        assertTrue(writes.stream().anyMatch(write -> List.of(Map.of("id", 104L, "title", "被引论文", "year", 2020, "citationCount", 210)).equals(write.get("references"))));
        assertTrue(event.getProcessed());
        assertNotNull(event.getProcessedAt());
        verify(events).save(event);
        verify(session).close();
    }

    @Test
    void supportsNullStatisticsAndAbsentOptionalRelationships() {
        when(papers.findById(120L)).thenReturn(Optional.of(paper(120L, "缺少统计的论文", null, null)));

        scheduler.pollAndSync();

        assertTrue(event.getProcessed());
        assertTrue(writes.getFirst().containsKey("year"));
        assertNull(writes.getFirst().get("year"));
        assertNull(writes.getFirst().get("citationCount"));
        assertTrue(writes.stream().noneMatch(write -> write.containsKey("venueId")));
        assertTrue(writes.stream().anyMatch(write -> List.of().equals(write.get("authors"))));
        assertTrue(writes.stream().anyMatch(write -> List.of().equals(write.get("references"))));
    }

    @Test
    void doesNotResurrectAPaperMissingFromTheCanonicalDatabase() {
        when(papers.findById(120L)).thenReturn(Optional.empty());

        scheduler.pollAndSync();

        assertTrue(event.getProcessed());
        verifyNoInteractions(transaction);
        verify(events).save(event);
    }

    @Test
    void leavesFailedProjectionPendingUntilARetrySucceeds() {
        when(papers.findById(120L)).thenReturn(Optional.of(paper(120L, "重试论文", 2025, 12)));
        doThrow(new ServiceUnavailableException("临时图服务故障"))
                .doAnswer(call -> ((TransactionCallback<?>) call.getArgument(0)).execute(transaction))
                .when(session).executeWrite(any());

        scheduler.pollAndSync();

        assertFalse(event.getProcessed());
        assertNull(event.getProcessedAt());
        verify(events, never()).save(event);
        scheduler.pollAndSync();
        assertTrue(event.getProcessed());
        verify(events).save(event);
        verify(session, times(2)).close();
    }

    private Paper paper(Long id, String title, Integer year, Integer citations) {
        Paper paper = new Paper();
        paper.setId(id);
        paper.setTitle(title);
        paper.setYear(year);
        paper.setCitationCount(citations);
        return paper;
    }
}
