package com.example.academic_entity_extract_kg_construction.infrastructure.external.semantic;

import com.example.academic_entity_extract_kg_construction.infrastructure.exception.ExternalApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class SemanticScholarClient {

    private static final Logger log = LoggerFactory.getLogger(SemanticScholarClient.class);

    private final RestClient restClient;
    private final SemanticScholarProperties properties;
    private final ObjectMapper objectMapper;
    private long lastRequestTime = 0;

    public SemanticScholarClient(SemanticScholarProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    public PaperSearchResult searchPapers(String query, int offset, int limit) {
        rateLimit();
        try {
            String fields = "paperId,title,abstract,year,authors,venue,citationCount,externalIds,url";
            String responseBody = restClient.get()
                    .uri("/paper/search?query={query}&offset={offset}&limit={limit}&fields={fields}",
                            query, offset, limit, fields)
                    .headers(this::addHeaders)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            int total = root.has("total") ? root.get("total").asInt() : 0;
            List<PaperDto> papers = new ArrayList<>();

            if (root.has("data")) {
                for (JsonNode node : root.get("data")) {
                    papers.add(parsePaperDto(node));
                }
            }

            return new PaperSearchResult(total, papers);
        } catch (Exception e) {
            log.error("Failed to search papers: {}", e.getMessage());
            throw new ExternalApiException("Failed to search papers from Semantic Scholar", e);
        }
    }

    public AuthorSearchResult searchAuthors(String query, int offset, int limit) {
        rateLimit();
        try {
            String fields = "authorId,name,affiliations,hIndex,paperCount,citationCount,url";
            String responseBody = restClient.get()
                    .uri("/author/search?query={query}&offset={offset}&limit={limit}&fields={fields}",
                            query, offset, limit, fields)
                    .headers(this::addHeaders)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            int total = root.has("total") ? root.get("total").asInt() : 0;
            List<AuthorDto> authors = new ArrayList<>();

            if (root.has("data")) {
                for (JsonNode node : root.get("data")) {
                    authors.add(parseAuthorDto(node));
                }
            }

            return new AuthorSearchResult(total, authors);
        } catch (Exception e) {
            log.error("Failed to search authors: {}", e.getMessage());
            throw new ExternalApiException("Failed to search authors from Semantic Scholar", e);
        }
    }

    public List<PaperDto> getAuthorPapers(String authorId, int offset, int limit) {
        rateLimit();
        try {
            String fields = "paperId,title,abstract,year,authors,venue,citationCount,externalIds,url";
            String responseBody = restClient.get()
                    .uri("/author/{authorId}/papers?offset={offset}&limit={limit}&fields={fields}",
                            authorId, offset, limit, fields)
                    .headers(this::addHeaders)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            List<PaperDto> papers = new ArrayList<>();

            if (root.has("data")) {
                for (JsonNode node : root.get("data")) {
                    papers.add(parsePaperDto(node));
                }
            }

            return papers;
        } catch (Exception e) {
            log.error("Failed to get author papers: {}", e.getMessage());
            throw new ExternalApiException("Failed to get author papers from Semantic Scholar", e);
        }
    }

    public List<CitationDto> getPaperCitations(String paperId, int offset, int limit) {
        rateLimit();
        try {
            String fields = "paperId,title,year,authors,citationCount";
            String responseBody = restClient.get()
                    .uri("/paper/{paperId}/citations?offset={offset}&limit={limit}&fields={fields}",
                            paperId, offset, limit, fields)
                    .headers(this::addHeaders)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            List<CitationDto> citations = new ArrayList<>();

            if (root.has("data")) {
                for (JsonNode node : root.get("data")) {
                    if (node.has("citedPaper")) {
                        JsonNode cited = node.get("citedPaper");
                        citations.add(new CitationDto(
                                cited.has("paperId") ? cited.get("paperId").asText() : null,
                                cited.has("title") ? cited.get("title").asText() : null,
                                cited.has("year") ? cited.get("year").asInt() : null,
                                cited.has("citationCount") ? cited.get("citationCount").asInt() : 0
                        ));
                    }
                }
            }

            return citations;
        } catch (Exception e) {
            log.error("Failed to get paper citations: {}", e.getMessage());
            throw new ExternalApiException("Failed to get paper citations from Semantic Scholar", e);
        }
    }

    private void addHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.set("x-api-key", properties.getApiKey());
        }
    }

    private synchronized void rateLimit() {
        long interval = 1000L / properties.getRateLimitPerSecond();
        long elapsed = System.currentTimeMillis() - lastRequestTime;
        if (elapsed < interval) {
            try {
                Thread.sleep(interval - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }

    private PaperDto parsePaperDto(JsonNode node) {
        String paperId = node.has("paperId") ? node.get("paperId").asText() : null;
        String title = node.has("title") ? node.get("title").asText() : null;
        String abstractText = node.has("abstract") ? node.get("abstract").asText() : null;
        Integer year = node.has("year") && !node.get("year").isNull() ? node.get("year").asInt() : null;
        String venue = node.has("venue") && !node.get("venue").isNull() ? node.get("venue").asText() : null;
        int citationCount = node.has("citationCount") ? node.get("citationCount").asInt() : 0;
        String url = node.has("url") && !node.get("url").isNull() ? node.get("url").asText() : null;

        String doi = null;
        if (node.has("externalIds") && node.get("externalIds").has("DOI")) {
            doi = node.get("externalIds").get("DOI").asText();
        }

        List<AuthorDto> authors = new ArrayList<>();
        if (node.has("authors")) {
            for (JsonNode authorNode : node.get("authors")) {
                authors.add(parseAuthorDto(authorNode));
            }
        }

        return new PaperDto(paperId, title, abstractText, year, venue, citationCount, doi, url, authors);
    }

    private AuthorDto parseAuthorDto(JsonNode node) {
        String authorId = node.has("authorId") ? node.get("authorId").asText() : null;
        String name = node.has("name") ? node.get("name").asText() : null;
        int hIndex = node.has("hIndex") ? node.get("hIndex").asInt() : 0;
        int paperCount = node.has("paperCount") ? node.get("paperCount").asInt() : 0;
        int citationCount = node.has("citationCount") ? node.get("citationCount").asInt() : 0;
        String url = node.has("url") && !node.get("url").isNull() ? node.get("url").asText() : null;

        String affiliation = null;
        if (node.has("affiliations") && node.get("affiliations").isArray() && !node.get("affiliations").isEmpty()) {
            affiliation = node.get("affiliations").get(0).asText();
        }

        return new AuthorDto(authorId, name, affiliation, hIndex, paperCount, citationCount, url);
    }

    public record PaperSearchResult(int total, List<PaperDto> papers) {}
    public record AuthorSearchResult(int total, List<AuthorDto> authors) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaperDto(String paperId, String title, String abstractText, Integer year,
                           String venue, int citationCount, String doi, String url,
                           List<AuthorDto> authors) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorDto(String authorId, String name, String affiliation,
                            int hIndex, int paperCount, int citationCount, String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CitationDto(String paperId, String title, Integer year, int citationCount) {}
}
