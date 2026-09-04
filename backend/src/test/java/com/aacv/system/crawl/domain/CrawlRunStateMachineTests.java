package com.aacv.system.crawl.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrawlRunStateMachineTests {

    @Test
    void acceptsOnlyDeclaredLifecycleTransitions() {
        assertTrue(CrawlRunStateMachine.canTransition(CrawlRunStatus.PENDING, CrawlRunStatus.RUNNING));
        assertTrue(CrawlRunStateMachine.canTransition(CrawlRunStatus.RUNNING, CrawlRunStatus.PAUSING));
        assertTrue(CrawlRunStateMachine.canTransition(CrawlRunStatus.PAUSING, CrawlRunStatus.PAUSED));
        assertTrue(CrawlRunStateMachine.canTransition(CrawlRunStatus.PAUSED, CrawlRunStatus.RUNNING));
        assertTrue(CrawlRunStateMachine.canTransition(CrawlRunStatus.RUNNING, CrawlRunStatus.CANCELLING));
        assertTrue(CrawlRunStateMachine.canTransition(CrawlRunStatus.CANCELLING, CrawlRunStatus.CANCELLED));
        assertFalse(CrawlRunStateMachine.canTransition(CrawlRunStatus.PAUSED, CrawlRunStatus.SUCCEEDED));
        assertFalse(CrawlRunStateMachine.canTransition(CrawlRunStatus.SUCCEEDED, CrawlRunStatus.RUNNING));
        assertThrows(
                IllegalStateException.class,
                () -> CrawlRunStateMachine.requireTransition(
                        CrawlRunStatus.CANCELLED, CrawlRunStatus.RUNNING));
    }

    @Test
    void terminalStatesAreExplicit() {
        assertTrue(CrawlRunStateMachine.isTerminal(CrawlRunStatus.SUCCEEDED));
        assertTrue(CrawlRunStateMachine.isTerminal(CrawlRunStatus.PARTIAL_SUCCESS));
        assertTrue(CrawlRunStateMachine.isTerminal(CrawlRunStatus.FAILED));
        assertTrue(CrawlRunStateMachine.isTerminal(CrawlRunStatus.CANCELLED));
        assertFalse(CrawlRunStateMachine.isTerminal(CrawlRunStatus.PAUSED));
    }
}
