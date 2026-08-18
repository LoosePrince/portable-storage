package com.portablestorage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SearchInputPolicyTest {
    @Test
    void focusedSearchConsumesInventoryAndNumberKeys() {
        assertEquals(SearchInputPolicy.KeyAction.FORWARD_AND_CONSUME,
                SearchInputPolicy.resolveKeyAction(true, true, true, 69));
        assertEquals(SearchInputPolicy.KeyAction.FORWARD_AND_CONSUME,
                SearchInputPolicy.resolveKeyAction(true, true, true, 49));
    }

    @Test
    void escapeUnfocusesSearchBeforeClosingScreen() {
        assertEquals(SearchInputPolicy.KeyAction.UNFOCUS,
                SearchInputPolicy.resolveKeyAction(true, true, true, SearchInputPolicy.ESCAPE_KEY));
    }

    @Test
    void inactiveOrUnfocusedSearchDoesNotConsumeKeys() {
        assertEquals(SearchInputPolicy.KeyAction.IGNORE,
                SearchInputPolicy.resolveKeyAction(false, true, true, 69));
        assertEquals(SearchInputPolicy.KeyAction.IGNORE,
                SearchInputPolicy.resolveKeyAction(true, false, true, 69));
        assertEquals(SearchInputPolicy.KeyAction.IGNORE,
                SearchInputPolicy.resolveKeyAction(true, true, false, 69));
    }

    @Test
    void onlyRightClickInsideActiveSearchClearsAndFocuses() {
        assertTrue(SearchInputPolicy.shouldClearAndFocus(true, true, true, 1));
        assertFalse(SearchInputPolicy.shouldClearAndFocus(true, true, true, 0));
        assertFalse(SearchInputPolicy.shouldClearAndFocus(true, true, false, 1));
        assertFalse(SearchInputPolicy.shouldClearAndFocus(false, true, true, 1));
    }
}