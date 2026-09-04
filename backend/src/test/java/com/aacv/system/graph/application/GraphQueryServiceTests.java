package com.aacv.system.graph.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ClientException;

class GraphQueryServiceTests {

    @Test
    void recognizesNeo4jTransactionTimeoutCode() {
        assertTrue(GraphQueryService.isTimeout(new ClientException(
                "Neo.ClientError.Transaction.TransactionTimedOutClientConfiguration", "timeout")));
        assertFalse(GraphQueryService.isTimeout(new ClientException(
                "Neo.TransientError.General.DatabaseUnavailable", "unavailable")));
    }
}
