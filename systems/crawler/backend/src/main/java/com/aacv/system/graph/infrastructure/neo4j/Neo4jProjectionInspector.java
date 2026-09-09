package com.aacv.system.graph.infrastructure.neo4j;

import com.aacv.system.graph.domain.GraphAchievementSnapshot;
import java.time.Duration;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.TransactionConfig;
import org.springframework.stereotype.Component;

@Component
public class Neo4jProjectionInspector {

    private static final TransactionConfig TIMEOUT = TransactionConfig.builder()
            .withTimeout(Duration.ofSeconds(5)).build();
    private final Driver driver;

    public Neo4jProjectionInspector(Driver driver) {
        this.driver = driver;
    }

    public boolean matches(GraphAchievementSnapshot snapshot, long desiredVersion) {
        try (var session = driver.session()) {
            Map<String, Object> counts = session.executeRead(transaction -> transaction.run("""
                    MATCH (achievement:Achievement {businessId: $id})
                    OPTIONAL MATCH (:Author)-[authored:AUTHORED]->(achievement)
                    WHERE authored.aacvManaged = true AND authored.achievementBusinessId = $id
                    WITH achievement, count(DISTINCT authored) AS authors
                    OPTIONAL MATCH (:Author)-[affiliation:AFFILIATED_WITH]->(:Institution)
                    WHERE affiliation.aacvManaged = true AND affiliation.achievementBusinessId = $id
                    WITH achievement, authors, count(DISTINCT affiliation) AS affiliations
                    OPTIONAL MATCH (achievement)-[venue:PUBLISHED_IN]->(:Venue)
                    WHERE venue.aacvManaged = true AND venue.achievementBusinessId = $id
                    WITH achievement, authors, affiliations, count(DISTINCT venue) AS venues
                    OPTIONAL MATCH (achievement)-[topic:HAS_TOPIC]->(:Topic)
                    WHERE topic.aacvManaged = true AND topic.achievementBusinessId = $id
                    WITH achievement, authors, affiliations, venues, count(DISTINCT topic) AS topics
                    OPTIONAL MATCH (achievement)-[citation:CITES]->(:Achievement)
                    WHERE citation.aacvManaged = true AND citation.achievementBusinessId = $id
                    RETURN achievement.projectionVersion AS version, authors, affiliations, venues,
                           topics, count(DISTINCT citation) AS citations
                    """, Map.of("id", snapshot.achievementId())).single().asMap(), TIMEOUT);
            return ((Number) counts.get("version")).longValue() == desiredVersion
                    && ((Number) counts.get("authors")).longValue() == snapshot.authors().size()
                    && ((Number) counts.get("affiliations")).longValue() == snapshot.affiliations().size()
                    && ((Number) counts.get("venues")).longValue() == (snapshot.venue() == null ? 0 : 1)
                    && ((Number) counts.get("topics")).longValue() == snapshot.topics().size()
                    && ((Number) counts.get("citations")).longValue() == snapshot.references().size();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void deleteManagedProjection() {
        try (var session = driver.session()) {
            session.executeWrite(transaction -> {
                transaction.run("MATCH (node) WHERE node.aacvManaged = true DETACH DELETE node").consume();
                return null;
            }, TIMEOUT);
        }
    }
}
