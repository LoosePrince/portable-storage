package com.portable.storage.client;

/**
 * 用于在下一次打开工作台界面时跳过自动替换为自定义界面的标记。
 */
public final class ScreenSwapBypass {
    private static boolean skipNextCraftingSwap = false;

    private ScreenSwapBypass() {}

    public static void requestSkipNextCraftingSwap() {
        skipNextCraftingSwap = true;
    }

    public static boolean consumeSkipNextCraftingSwap() {
        if (skipNextCraftingSwap) {
            skipNextCraftingSwap = false;
            return true;
        }
        return false;
    }
}


