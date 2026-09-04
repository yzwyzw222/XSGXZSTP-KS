package com.aacv.system.graph.infrastructure.neo4j;

import com.aacv.system.graph.domain.GraphAchievementSnapshot;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class Neo4jProjectionTransaction {

    private static final String UPSERT_ACHIEVEMENT = """
            MERGE (node:Achievement {businessId: $businessId})
            ON CREATE SET node.aacvManaged = true, node.projectionVersion = 0
            WITH node
            WHERE coalesce(node.projectionVersion, 0) <= $projectionVersion
            SET node.aacvManaged = true, node.projectionVersion = $projectionVersion,
                node.title = $title, node.achievementType = $achievementType,
                node.language = $language, node.publicationDate = $publicationDate, node.doi = $doi
            RETURN count(node) AS changed
            """;
    private static final String DELETE_MANAGED_RELATIONSHIPS = """
            MATCH ()-[rel]-()
            WHERE rel.aacvManaged = true AND rel.achievementBusinessId = $businessId
            DELETE rel
            """;
    private static final String UPSERT_AUTHORS = """
            UNWIND $rows AS row
            MERGE (author:Author {businessId: row.id})
            SET author.aacvManaged = true, author.name = row.name, author.orcid = row.orcid
            WITH author, row
            MATCH (achievement:Achievement {businessId: $businessId})
            MERGE (author)-[rel:AUTHORED {achievementBusinessId: $businessId}]->(achievement)
            SET rel.aacvManaged = true
            """;
    private static final String UPSERT_AFFILIATIONS = """
            UNWIND $rows AS row
            MATCH (author:Author {businessId: row.authorId})
            MERGE (institution:Institution {businessId: row.institutionId})
            SET institution.aacvManaged = true, institution.name = row.institutionName,
                institution.standardCode = row.standardCode, institution.countryCode = row.countryCode
            MERGE (author)-[rel:AFFILIATED_WITH {
                achievementBusinessId: $businessId, institutionBusinessId: row.institutionId
            }]->(institution)
            SET rel.aacvManaged = true
            """;
    private static final String UPSERT_VENUE = """
            UNWIND $rows AS row
            MERGE (venue:Venue {businessId: row.id})
            SET venue.aacvManaged = true, venue.name = row.name,
                venue.venueType = row.venueType, venue.issn = row.issn
            WITH venue
            MATCH (achievement:Achievement {businessId: $businessId})
            MERGE (achievement)-[rel:PUBLISHED_IN {achievementBusinessId: $businessId}]->(venue)
            SET rel.aacvManaged = true
            """;
    private static final String UPSERT_TOPICS = """
            UNWIND $rows AS row
            MERGE (topic:Topic {businessId: row.id})
            SET topic.aacvManaged = true, topic.name = row.name,
                topic.code = row.code, topic.path = row.path
            WITH topic
            MATCH (achievement:Achievement {businessId: $businessId})
            MERGE (achievement)-[rel:HAS_TOPIC {achievementBusinessId: $businessId}]->(topic)
            SET rel.aacvManaged = true
            """;
    private static final String UPSERT_REFERENCES = """
            UNWIND $rows AS row
            MERGE (cited:Achievement {businessId: row.id})
            ON CREATE SET cited.projectionVersion = 0
            SET cited.aacvManaged = true, cited.title = row.title,
                cited.achievementType = row.achievementType, cited.language = row.language,
                cited.publicationDate = row.publicationDate, cited.doi = row.doi
            WITH cited
            MATCH (citing:Achievement {businessId: $businessId})
            MERGE (citing)-[rel:CITES {achievementBusinessId: $businessId}]->(cited)
            SET rel.aacvManaged = true
            """;
    private static final String DELETE_ORPHANS = """
            MATCH (node)
            WHERE node.aacvManaged = true
              AND (node:Author OR node:Institution OR node:Venue OR node:Topic)
              AND NOT (node)--()
            DELETE node
            """;

    private final Neo4jClient neo4jClient;

    Neo4jProjectionTransaction(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Transactional(transactionManager = "neo4jTransactionManager", timeout = 5)
    public void replace(GraphAchievementSnapshot snapshot, long projectionVersion) {
        Map<String, Object> achievement = new HashMap<>();
        achievement.put("businessId", snapshot.achievementId());
        achievement.put("projectionVersion", projectionVersion);
        achievement.put("title", snapshot.title());
        achievement.put("achievementType", snapshot.achievementType());
        achievement.put("language", snapshot.language());
        achievement.put("publicationDate", snapshot.publicationDate());
        achievement.put("doi", snapshot.doi());
        long changed = neo4jClient.query(UPSERT_ACHIEVEMENT)
                .bindAll(achievement)
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("changed").asLong())
                .one()
                .orElse(0L);
        if (changed == 0) {
            return;
        }
        neo4jClient.query(DELETE_MANAGED_RELATIONSHIPS)
                .bind(snapshot.achievementId()).to("businessId").run();
        runRows(UPSERT_AUTHORS, snapshot.achievementId(), snapshot.authors().stream()
                .map(row -> Map.of(
                        "id", row.id(), "name", nullable(row.name()), "orcid", nullable(row.orcid())))
                .toList());
        runRows(UPSERT_AFFILIATIONS, snapshot.achievementId(), snapshot.affiliations().stream()
                .map(row -> Map.of(
                        "authorId", row.authorId(),
                        "institutionId", row.institutionId(),
                        "institutionName", nullable(row.institutionName()),
                        "standardCode", nullable(row.standardCode()),
                        "countryCode", nullable(row.countryCode())))
                .toList());
        runRows(UPSERT_VENUE, snapshot.achievementId(),
                snapshot.venue() == null ? java.util.List.of() : java.util.List.of(Map.of(
                        "id", snapshot.venue().id(),
                        "name", nullable(snapshot.venue().name()),
                        "venueType", nullable(snapshot.venue().venueType()),
                        "issn", nullable(snapshot.venue().issn()))));
        runRows(UPSERT_TOPICS, snapshot.achievementId(), snapshot.topics().stream()
                .map(row -> Map.of(
                        "id", row.id(), "name", nullable(row.name()),
                        "code", nullable(row.code()), "path", nullable(row.path())))
                .toList());
        runRows(UPSERT_REFERENCES, snapshot.achievementId(), snapshot.references().stream()
                .map(row -> {
                    Map<String, Object> values = new HashMap<>();
                    values.put("id", row.id());
                    values.put("title", row.title());
                    values.put("achievementType", row.achievementType());
                    values.put("language", row.language());
                    values.put("publicationDate", row.publicationDate());
                    values.put("doi", row.doi());
                    return values;
                })
                .toList());
        neo4jClient.query(DELETE_ORPHANS).run();
    }

    private void runRows(String cypher, long businessId, Object rows) {
        neo4jClient.query(cypher)
                .bind(businessId).to("businessId")
                .bind(rows).to("rows")
                .run();
    }

    private Object nullable(Object value) {
        return value == null ? org.neo4j.driver.Values.NULL : value;
    }
}
