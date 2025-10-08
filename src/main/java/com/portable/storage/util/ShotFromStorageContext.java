package com.portable.storage.util;

/**
 * 在线程上下文中标记“当前一次弓箭发射是否来源于随身仓库”。
 * 供 PlayerEntity 与 BowItem 的 mixin 协同使用。
 */
public final class ShotFromStorageContext {
    private static final ThreadLocal<Boolean> FLAG = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ShotFromStorageContext() {}

    public static void markFromStorage() {
        FLAG.set(Boolean.TRUE);
    }

    public static boolean isFromStorage() {
        return Boolean.TRUE.equals(FLAG.get());
    }

    public static void clear() {
        FLAG.set(Boolean.FALSE);
    }
}


