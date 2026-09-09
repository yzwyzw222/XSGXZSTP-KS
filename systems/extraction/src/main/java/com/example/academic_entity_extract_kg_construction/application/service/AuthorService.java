package com.example.academic_entity_extract_kg_construction.application.service;

import com.example.academic_entity_extract_kg_construction.api.dto.response.*;
import com.example.academic_entity_extract_kg_construction.api.mapper.AuthorMapper;
import com.example.academic_entity_extract_kg_construction.api.mapper.GraphMapper;
import com.example.academic_entity_extract_kg_construction.api.mapper.PaperMapper;
import com.example.academic_entity_extract_kg_construction.domain.model.Author;
import com.example.academic_entity_extract_kg_construction.domain.model.Paper;
import com.example.academic_entity_extract_kg_construction.domain.repository.AuthorRepository;
import com.example.academic_entity_extract_kg_construction.domain.repository.PaperRepository;
import com.example.academic_entity_extract_kg_construction.infrastructure.exception.AuthorNotFoundException;
import com.example.academic_entity_extract_kg_construction.infrastructure.external.semantic.SemanticScholarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthorService {

    private static final Logger log = LoggerFactory.getLogger(AuthorService.class);

    private final AuthorRepository authorRepository;
    private final PaperRepository paperRepository;
    private final SemanticScholarClient semanticScholarClient;
    private final PaperService paperService;

    public AuthorService(AuthorRepository authorRepository, PaperRepository paperRepository,
                         SemanticScholarClient semanticScholarClient, PaperService paperService) {
        this.authorRepository = authorRepository;
        this.paperRepository = paperRepository;
        this.semanticScholarClient = semanticScholarClient;
        this.paperService = paperService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuthorSummaryDto> searchAuthors(String name, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        Page<Author> authorPage = authorRepository.searchByName(name, pageRequest);

        List<AuthorSummaryDto> items = authorPage.getContent().stream()
                .map(AuthorMapper::toSummary)
                .toList();

        return PageResponse.of(items, page, size, authorPage.getTotalElements());
    }

    @Transactional
    public PageResponse<AuthorSummaryDto> searchAndFetchAuthors(String name, int page, int size) {
        PageResponse<AuthorSummaryDto> localResult = searchAuthors(name, page, size);
        if (localResult.getTotalElements() > 0) {
            return localResult;
        }

        log.info("No local authors found for '{}', fetching from Semantic Scholar", name);
        SemanticScholarClient.AuthorSearchResult remoteResult = semanticScholarClient.searchAuthors(name, page * size, size);

        for (SemanticScholarClient.AuthorDto dto : remoteResult.authors()) {
            saveAuthorFromRemote(dto);
        }

        return searchAuthors(name, page, size);
    }

    @Transactional(readOnly = true)
    public AuthorDetailDto getAuthorDetail(Long id) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new AuthorNotFoundException(id));

        List<Paper> authorPapers = paperRepository.findAll().stream()
                .filter(p -> p.getAuthors().contains(author))
                .toList();

        List<PaperSummaryDto> papers = authorPapers.stream()
                .map(PaperMapper::toSummary)
                .toList();

        Set<Author> coAuthors = new HashSet<>();
        for (Paper paper : authorPapers) {
            for (Author a : paper.getAuthors()) {
                if (!a.getId().equals(author.getId())) {
                    coAuthors.add(a);
                }
            }
        }

        List<AuthorSummaryDto> coAuthorDtos = coAuthors.stream()
                .map(AuthorMapper::toSummary)
                .toList();

        return AuthorDetailDto.builder()
                .id(author.getId())
                .semanticScholarId(author.getSemanticScholarId())
                .name(author.getName())
                .affiliation(author.getAffiliation())
                .hIndex(author.getHIndex())
                .paperCount(author.getPaperCount())
                .citationCount(author.getCitationCount())
                .url(author.getUrl())
                .papers(papers)
                .coAuthors(coAuthorDtos)
                .build();
    }

    @Transactional(readOnly = true)
    public GraphDataDto getAuthorGraph(Long id) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new AuthorNotFoundException(id));

        Set<Paper> papers = new HashSet<>();
        Set<Author> coAuthors = new HashSet<>();

        for (Paper paper : paperRepository.findAll()) {
            if (paper.getAuthors().contains(author)) {
                papers.add(paper);
                for (Author a : paper.getAuthors()) {
                    coAuthors.add(a);
                }
            }
        }

        return GraphMapper.buildAuthorGraph(author, papers, coAuthors);
    }

    @Transactional
    public Author saveAuthorFromRemote(SemanticScholarClient.AuthorDto dto) {
        Author author = authorRepository.findBySemanticScholarId(dto.authorId())
                .orElse(new Author());

        author.setSemanticScholarId(dto.authorId());
        author.setName(dto.name());
        author.setAffiliation(dto.affiliation());
        author.setHIndex(dto.hIndex());
        author.setPaperCount(dto.paperCount());
        author.setCitationCount(dto.citationCount());
        author.setUrl(dto.url());

        return authorRepository.save(author);
    }
}
