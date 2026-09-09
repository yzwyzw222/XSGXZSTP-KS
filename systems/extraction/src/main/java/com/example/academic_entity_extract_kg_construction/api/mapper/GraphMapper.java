package com.example.academic_entity_extract_kg_construction.api.mapper;

import com.example.academic_entity_extract_kg_construction.api.dto.response.GraphDataDto;
import com.example.academic_entity_extract_kg_construction.domain.model.*;

import java.util.*;

public class GraphMapper {

    private GraphMapper() {}

    public static GraphDataDto buildAuthorGraph(Author author, Set<Paper> papers, Set<Author> coAuthors) {
        List<GraphDataDto.GraphNode> nodes = new ArrayList<>();
        List<GraphDataDto.GraphEdge> edges = new ArrayList<>();
        int edgeCounter = 0;

        nodes.add(GraphDataDto.GraphNode.builder()
                .data(GraphDataDto.NodeData.builder()
                        .id("author_" + author.getId())
                        .label(author.getName())
                        .type("AUTHOR")
                        .properties(Map.of(
                                "hIndex", author.getHIndex() != null ? author.getHIndex() : 0,
                                "paperCount", author.getPaperCount() != null ? author.getPaperCount() : 0,
                                "citationCount", author.getCitationCount() != null ? author.getCitationCount() : 0
                        ))
                        .build())
                .build());

        for (Paper paper : papers) {
            nodes.add(GraphDataDto.GraphNode.builder()
                    .data(GraphDataDto.NodeData.builder()
                            .id("paper_" + paper.getId())
                            .label(paper.getTitle())
                            .type("PAPER")
                            .properties(Map.of(
                                    "year", paper.getYear() != null ? paper.getYear() : 0,
                                    "citationCount", paper.getCitationCount() != null ? paper.getCitationCount() : 0
                            ))
                            .build())
                    .build());

            edgeCounter++;
            edges.add(GraphDataDto.GraphEdge.builder()
                    .data(GraphDataDto.EdgeData.builder()
                            .id("e" + edgeCounter)
                            .source("author_" + author.getId())
                            .target("paper_" + paper.getId())
                            .label("WROTE")
                            .type("WROTE")
                            .build())
                    .build());

            if (paper.getVenue() != null) {
                nodes.add(GraphDataDto.GraphNode.builder()
                        .data(GraphDataDto.NodeData.builder()
                                .id("venue_" + paper.getVenue().getId())
                                .label(paper.getVenue().getName())
                                .type("VENUE")
                                .properties(Map.of("type", paper.getVenue().getType().name()))
                                .build())
                        .build());

                edgeCounter++;
                edges.add(GraphDataDto.GraphEdge.builder()
                        .data(GraphDataDto.EdgeData.builder()
                                .id("e" + edgeCounter)
                                .source("paper_" + paper.getId())
                                .target("venue_" + paper.getVenue().getId())
                                .label("PUBLISHED_AT")
                                .type("PUBLISHED_AT")
                                .build())
                        .build());
            }
        }

        for (Author coAuthor : coAuthors) {
            if (coAuthor.getId().equals(author.getId())) continue;

            nodes.add(GraphDataDto.GraphNode.builder()
                    .data(GraphDataDto.NodeData.builder()
                            .id("author_" + coAuthor.getId())
                            .label(coAuthor.getName())
                            .type("AUTHOR")
                            .properties(Map.of(
                                    "hIndex", coAuthor.getHIndex() != null ? coAuthor.getHIndex() : 0,
                                    "paperCount", coAuthor.getPaperCount() != null ? coAuthor.getPaperCount() : 0
                            ))
                            .build())
                    .build());

            edgeCounter++;
            edges.add(GraphDataDto.GraphEdge.builder()
                    .data(GraphDataDto.EdgeData.builder()
                            .id("e" + edgeCounter)
                            .source("author_" + author.getId())
                            .target("author_" + coAuthor.getId())
                            .label("COLLABORATES_WITH")
                            .type("COLLABORATES_WITH")
                            .build())
                    .build());
        }

        Set<String> seenNodeIds = new HashSet<>();
        List<GraphDataDto.GraphNode> uniqueNodes = new ArrayList<>();
        for (GraphDataDto.GraphNode node : nodes) {
            if (seenNodeIds.add(node.getData().getId())) {
                uniqueNodes.add(node);
            }
        }

        return GraphDataDto.builder().nodes(uniqueNodes).edges(edges).build();
    }
}
