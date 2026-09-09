package com.example.academic_entity_extract_kg_construction.application.service;

import com.example.academic_entity_extract_kg_construction.api.dto.response.PaperDetailDto;
import com.example.academic_entity_extract_kg_construction.api.dto.response.PaperSummaryDto;
import com.example.academic_entity_extract_kg_construction.api.dto.response.PageResponse;
import com.example.academic_entity_extract_kg_construction.api.mapper.PaperMapper;
import com.example.academic_entity_extract_kg_construction.domain.model.*;
import com.example.academic_entity_extract_kg_construction.domain.model.enums.ExtractionStatus;
import com.example.academic_entity_extract_kg_construction.domain.model.enums.OutboxEventType;
import com.example.academic_entity_extract_kg_construction.domain.model.enums.VenueType;
import com.example.academic_entity_extract_kg_construction.domain.repository.*;
import com.example.academic_entity_extract_kg_construction.infrastructure.exception.PaperNotFoundException;
import com.example.academic_entity_extract_kg_construction.infrastructure.external.semantic.SemanticScholarClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaperService {

    private static final Logger log = LoggerFactory.getLogger(PaperService.class);

    private final PaperRepository paperRepository;
    private final AuthorRepository authorRepository;
    private final VenueRepository venueRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SemanticScholarClient semanticScholarClient;
    private final ObjectMapper objectMapper;

    public PaperService(PaperRepository paperRepository, AuthorRepository authorRepository,
                        VenueRepository venueRepository, OutboxEventRepository outboxEventRepository,
                        SemanticScholarClient semanticScholarClient, ObjectMapper objectMapper) {
        this.paperRepository = paperRepository;
        this.authorRepository = authorRepository;
        this.venueRepository = venueRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.semanticScholarClient = semanticScholarClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaperSummaryDto> searchPapers(String keyword, Integer year, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        Page<Paper> paperPage;

        if (keyword != null && !keyword.isBlank() && year != null) {
            paperPage = paperRepository.searchByKeywordAndYear(keyword, year, pageRequest);
        } else if (keyword != null && !keyword.isBlank()) {
            paperPage = paperRepository.searchByKeyword(keyword, pageRequest);
        } else if (year != null) {
            paperPage = paperRepository.findByYear(year, pageRequest);
        } else {
            paperPage = paperRepository.findAll(pageRequest);
        }

        List<PaperSummaryDto> items = paperPage.getContent().stream()
                .map(PaperMapper::toSummary)
                .toList();

        return PageResponse.of(items, page, size, paperPage.getTotalElements());
    }

    @Transactional
    public PageResponse<PaperSummaryDto> searchAndFetchPapers(String keyword, int page, int size) {
        PageResponse<PaperSummaryDto> localResult = searchPapers(keyword, null, page, size);
        if (localResult.getTotalElements() > 0) {
            return localResult;
        }

        log.info("No local papers found for '{}', fetching from Semantic Scholar", keyword);
        SemanticScholarClient.PaperSearchResult remoteResult = semanticScholarClient.searchPapers(keyword, page * size, size);

        for (SemanticScholarClient.PaperDto dto : remoteResult.papers()) {
            savePaperFromRemote(dto);
        }

        return searchPapers(keyword, null, page, size);
    }

    @Transactional(readOnly = true)
    public PaperDetailDto getPaperDetail(Long id) {
        Paper paper = paperRepository.findById(id)
                .orElseThrow(() -> new PaperNotFoundException(id));
        return PaperMapper.toDetail(paper);
    }

    @Transactional
    public Paper savePaperFromRemote(SemanticScholarClient.PaperDto dto) {
        Paper paper = paperRepository.findBySemanticScholarId(dto.paperId())
                .orElse(new Paper());

        boolean isNew = paper.getId() == null;
        paper.setSemanticScholarId(dto.paperId());
        paper.setTitle(dto.title());
        paper.setAbstractText(dto.abstractText());
        paper.setYear(dto.year());
        paper.setDoi(dto.doi());
        paper.setCitationCount(dto.citationCount());
        paper.setUrl(dto.url());
        paper.setExtractionStatus(ExtractionStatus.PENDING);

        if (dto.venue() != null && !dto.venue().isBlank()) {
            Venue venue = venueRepository.findByName(dto.venue())
                    .orElseGet(() -> {
                        Venue v = new Venue();
                        v.setName(dto.venue());
                        v.setType(VenueType.CONFERENCE);
                        return venueRepository.save(v);
                    });
            paper.setVenue(venue);
        }

        paper = paperRepository.save(paper);

        if (dto.authors() != null) {
            for (SemanticScholarClient.AuthorDto authorDto : dto.authors()) {
                Author author = authorRepository.findBySemanticScholarId(authorDto.authorId())
                        .orElseGet(() -> {
                            Author a = new Author();
                            a.setSemanticScholarId(authorDto.authorId());
                            a.setName(authorDto.name());
                            a.setAffiliation(authorDto.affiliation());
                            a.setHIndex(authorDto.hIndex());
                            a.setPaperCount(authorDto.paperCount());
                            a.setCitationCount(authorDto.citationCount());
                            a.setUrl(authorDto.url());
                            return authorRepository.save(a);
                        });
                paper.getAuthors().add(author);
            }
            paper = paperRepository.save(paper);
        }

        if (isNew) {
            writeOutboxEvent(OutboxEventType.PAPER_CREATED, "PAPER", paper.getId(), paper.getTitle());
        }

        return paper;
    }

    private void writeOutboxEvent(OutboxEventType eventType, String aggregateType, Long aggregateId, String payloadData) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(eventType.name());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        try {
            event.setPayload(objectMapper.writeValueAsString(new OutboxPayload(aggregateId, payloadData)));
        } catch (JacksonException e) {
            event.setPayload("{\"id\":" + aggregateId + "}");
        }
        outboxEventRepository.save(event);
    }

    private record OutboxPayload(Long id, String title) {}
}
