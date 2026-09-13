package com.aacv.system.ingestion.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class RawPayloadRetentionIntegrationTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42");

    @Test
    void clearsOnlyExpiredPayloadsWithinTheBatchAndPreservesEvidenceLinks() throws Exception {
        String jdbcUrl = MYSQL.getJdbcUrl();
        jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        var dataSource = new DriverManagerDataSource(jdbcUrl, MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE raw_record (
                    id BIGINT PRIMARY KEY, payload JSON, payload_hash CHAR(64) NOT NULL,
                    payload_expires_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NULL
                )
                """);
        jdbc.execute("CREATE TABLE achievement_source (raw_record_id BIGINT, FOREIGN KEY (raw_record_id) REFERENCES raw_record(id))");
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        jdbc.update("""
                INSERT INTO raw_record VALUES
                (1, '{}', REPEAT('a', 64), '2026-09-12 00:00:00', NULL),
                (2, '{}', REPEAT('b', 64), '2026-09-13 00:00:00', NULL),
                (3, '{}', REPEAT('c', 64), '2026-09-14 00:00:00', NULL),
                (4, NULL, REPEAT('d', 64), '2026-09-12 00:00:00', NULL)
                """);
        jdbc.update("INSERT INTO achievement_source VALUES (1), (2)");
        var configuration = new Configuration(new Environment("retention-test", new JdbcTransactionFactory(), dataSource));
        String resource = "mapper/ingestion/IngestionMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            var repository = new MyBatisIngestionRepository(session.getMapper(IngestionMapper.class));
            assertThrows(IllegalArgumentException.class, () -> repository.clearExpiredPayloads(null, 1));
            assertThrows(IllegalArgumentException.class, () -> repository.clearExpiredPayloads(now, 0));
            assertThrows(IllegalArgumentException.class, () -> repository.clearExpiredPayloads(now, 1001));
            assertEquals(1, repository.clearExpiredPayloads(now, 1));
            assertEquals(1, repository.clearExpiredPayloads(now, 1000));
            assertEquals(0, repository.clearExpiredPayloads(now, 1000));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM raw_record WHERE payload IS NOT NULL AND id = 3", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM raw_record WHERE payload_hash IS NOT NULL", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM achievement_source", Integer.class));
    }
}
