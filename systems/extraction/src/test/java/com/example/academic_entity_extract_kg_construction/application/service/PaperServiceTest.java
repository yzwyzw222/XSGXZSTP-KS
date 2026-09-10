package com.example.academic_entity_extract_kg_construction.application.service;

import com.example.academic_entity_extract_kg_construction.domain.model.OutboxEvent;
import com.example.academic_entity_extract_kg_construction.domain.model.Paper;
import com.example.academic_entity_extract_kg_construction.domain.repository.AuthorRepository;
import com.example.academic_entity_extract_kg_construction.domain.repository.OutboxEventRepository;
import com.example.academic_entity_extract_kg_construction.domain.repository.PaperRepository;
import com.example.academic_entity_extract_kg_construction.domain.repository.VenueRepository;
import com.example.academic_entity_extract_kg_construction.infrastructure.exception.PaperNotFoundException;
import com.example.academic_entity_extract_kg_construction.infrastructure.external.semantic.SemanticScholarClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaperServiceTest {
    private final PaperRepository papers = mock(PaperRepository.class);
    private final OutboxEventRepository events = mock(OutboxEventRepository.class);
    private final SemanticScholarClient remote = mock(SemanticScholarClient.class);
    private final PaperService service = new PaperService(papers, mock(AuthorRepository.class),
            mock(VenueRepository.class), events, remote, new ObjectMapper());
    private final SemanticScholarClient.PaperDto dto = mock(SemanticScholarClient.PaperDto.class);

    @BeforeEach
    void setUp() {
        when(dto.paperId()).thenReturn("test-paper");
        when(dto.title()).thenReturn("更新后的论文");
        when(dto.year()).thenReturn(2025);
        when(dto.citationCount()).thenReturn(12);
        when(papers.save(any())).thenAnswer(call -> {
            Paper paper = call.getArgument(0);
            if (paper.getId() == null) paper.setId(120L);
            return paper;
        });
    }

    @Test
    void createsAProjectionEventForANewPaper() {
        when(papers.findBySemanticScholarId("test-paper")).thenReturn(Optional.empty());

        service.savePaperFromRemote(dto);

        assertEvent("PAPER_CREATED");
    }

    @Test
    void refreshesProjectionWhenAnExistingPaperChanges() {
        Paper existing = new Paper();
        existing.setId(120L);
        existing.setTitle("旧标题");
        existing.setYear(2024);
        existing.setCitationCount(3);
        when(papers.findBySemanticScholarId("test-paper")).thenReturn(Optional.of(existing));

        Paper updated = service.savePaperFromRemote(dto);

        assertEquals(2025, updated.getYear());
        assertEquals(12, updated.getCitationCount());
        assertEvent("PAPER_UPDATED");
    }

    @Test
    void rejectsAnUnknownPaperUsingThePaperErrorType() {
        when(papers.existsById(120L)).thenReturn(false);

        assertThrows(PaperNotFoundException.class, () -> service.requirePaperExists(120L));
    }

    @Test
    void acceptsAnExistingPaperWithoutCallingExternalServices() {
        when(papers.existsById(120L)).thenReturn(true);

        assertDoesNotThrow(() -> service.requirePaperExists(120L));
        verifyNoInteractions(remote, events);
    }

    private void assertEvent(String type) {
        var captured = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(events).save(captured.capture());
        assertEquals(type, captured.getValue().getEventType());
        assertEquals(120L, captured.getValue().getAggregateId());
        assertEquals("PAPER", captured.getValue().getAggregateType());
        assertFalse(captured.getValue().getProcessed());
        verifyNoInteractions(remote);
    }
}
