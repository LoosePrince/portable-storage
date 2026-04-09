package com.portablestorage.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StorageWriteAuditTest {
    @Test
    void shouldTrackSourceCounters() {
        StorageWriteAudit.record("test.caseA", "allow");
        StorageWriteAudit.record("test.caseA", "allow");
        StorageWriteAudit.record("test.caseB", "reject");

        assertTrue(StorageWriteAudit.getTotalWrites() >= 3);
        assertEquals("test.caseB", StorageWriteAudit.getLastSource());
        assertEquals("reject", StorageWriteAudit.getLastDecision());
        assertTrue(StorageWriteAudit.snapshotBySource().containsKey("test.caseA"));
    }
}
