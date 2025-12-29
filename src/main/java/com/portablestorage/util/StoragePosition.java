package com.portablestorage.util;

public enum StoragePosition {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT;

    public boolean isVertical() {
        return this == TOP || this == BOTTOM;
    }

    public boolean isHorizontal() {
        return this == LEFT || this == RIGHT;
    }
}

