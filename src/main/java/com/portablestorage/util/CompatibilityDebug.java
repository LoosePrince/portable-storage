package com.portablestorage.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.portablestorage.PortableStorage;

public final class CompatibilityDebug {
    public static final String ENABLE_PROPERTY = "portablestorage.pr9Debug";

    private static final Set<String> EMITTED_ONCE = ConcurrentHashMap.newKeySet();

    private CompatibilityDebug() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    public static void log(String area, Supplier<String> message) {
        if (isEnabled()) {
            PortableStorage.LOGGER.info("[PR9-DEBUG][{}] {}", area, message.get());
        }
    }

    public static void logOnce(String key, String area, Supplier<String> message) {
        if (isEnabled() && EMITTED_ONCE.add(key)) {
            PortableStorage.LOGGER.info("[PR9-DEBUG][{}] {}", area, message.get());
        }
    }
}