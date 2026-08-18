package com.portablestorage.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FakePlayerUtilsTest {
    @Test
    void exactClassIsNotAUserDefinedSubclass() {
        assertFalse(FakePlayerUtils.isSubclassOf(BasePlayer.class, BasePlayer.class));
    }

    @Test
    void userDefinedSubclassMatchesTheCompatibilityHeuristic() {
        assertTrue(FakePlayerUtils.isSubclassOf(TestPlayer.class, BasePlayer.class));
    }

    private static class BasePlayer {
    }

    private static final class TestPlayer extends BasePlayer {
    }
}