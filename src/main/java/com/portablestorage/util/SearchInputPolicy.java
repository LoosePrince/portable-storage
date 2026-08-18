package com.portablestorage.util;

public final class SearchInputPolicy {
    public static final int ESCAPE_KEY = 256;
    public static final int RIGHT_MOUSE_BUTTON = 1;

    public enum KeyAction {
        IGNORE,
        UNFOCUS,
        FORWARD_AND_CONSUME
    }

    private SearchInputPolicy() {
    }

    public static KeyAction resolveKeyAction(boolean visible, boolean active, boolean focused, int keyCode) {
        if (!visible || !active || !focused) {
            return KeyAction.IGNORE;
        }
        return keyCode == ESCAPE_KEY ? KeyAction.UNFOCUS : KeyAction.FORWARD_AND_CONSUME;
    }

    public static boolean shouldClearAndFocus(boolean visible, boolean active, boolean mouseOver, int button) {
        return visible && active && mouseOver && button == RIGHT_MOUSE_BUTTON;
    }
}