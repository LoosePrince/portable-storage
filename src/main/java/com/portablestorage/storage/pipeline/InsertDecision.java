package com.portablestorage.storage.pipeline;

public record InsertDecision(boolean allowed, long amount, String reason) {
    public static InsertDecision allow(long amount) {
        return new InsertDecision(true, amount, "ok");
    }

    public static InsertDecision deny(String reason) {
        return new InsertDecision(false, 0, reason);
    }
}
