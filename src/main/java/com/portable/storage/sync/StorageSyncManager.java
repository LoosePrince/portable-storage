package com.portable.storage.sync;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 同步管理器：负责管理会话与序号，后续将承载发送窗口与 ACK/重传。
 */
public class StorageSyncManager {
    private static final Map<UUID, SessionState> SESSION_BY_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, SnapshotEntry>> LAST_SNAPSHOT_BY_PLAYER = new ConcurrentHashMap<>();

    private record SessionState(long sessionId, int nextSeq) {}
    public static final class SnapshotEntry {
        public final long count;
        public final long timestamp;
        public SnapshotEntry(long count, long timestamp) {
            this.count = count;
            this.timestamp = timestamp;
        }
    }

    public static long getOrStartSession(UUID playerId) {
        SessionState state = SESSION_BY_PLAYER.get(playerId);
        if (state == null) {
            long sid = newSessionId();
            SESSION_BY_PLAYER.put(playerId, new SessionState(sid, 1));
            return sid;
        }
        return state.sessionId;
    }

    public static void startNewSession(UUID playerId) {
        long sid = newSessionId();
        SESSION_BY_PLAYER.put(playerId, new SessionState(sid, 1));
    }

    public static int nextSeq(UUID playerId) {
        SessionState s = SESSION_BY_PLAYER.get(playerId);
        if (s == null) {
            startNewSession(playerId);
            s = SESSION_BY_PLAYER.get(playerId);
        }
        int seq = s.nextSeq;
        SESSION_BY_PLAYER.put(playerId, new SessionState(s.sessionId, seq + 1));
        return seq;
    }

    private static long newSessionId() {
        // 以随机 long 作为会话标识，避免与历史会话冲突
        return ThreadLocalRandom.current().nextLong();
    }

    public static void handleSyncAck(UUID playerId, long syncId, boolean success) {
        // 当前简化：仅记录最后ACK的序号或用于统计；后续可加入窗口和重传
    }

    public static void cleanupPlayer(UUID playerId) {
        SESSION_BY_PLAYER.remove(playerId);
        LAST_SNAPSHOT_BY_PLAYER.remove(playerId);
    }

    public static void forceFullSync(UUID playerId) {
        startNewSession(playerId);
    }

    public static void shutdown() {
        SESSION_BY_PLAYER.clear();
        LAST_SNAPSHOT_BY_PLAYER.clear();
    }

    public static Map<String, SnapshotEntry> getLastSnapshot(UUID playerId) {
        return LAST_SNAPSHOT_BY_PLAYER.get(playerId);
    }

    public static void setLastSnapshot(UUID playerId, Map<String, SnapshotEntry> snapshot) {
        if (snapshot == null) {
            LAST_SNAPSHOT_BY_PLAYER.remove(playerId);
        } else {
            LAST_SNAPSHOT_BY_PLAYER.put(playerId, snapshot);
        }
    }
}
