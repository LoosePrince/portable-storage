package com.portable.storage.client;

public final class ModernUiCompat {
	private static final String TOOLTIP_RENDERER = "icyllis.modernui.mc.TooltipRenderer";

	private ModernUiCompat() {}

	public static boolean isLoaded() {
		try {
			Class.forName(TOOLTIP_RENDERER, false, ModernUiCompat.class.getClassLoader());
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static void forceTooltipShadowRadiusZero() {
		try {
			Class<?> cls = Class.forName(TOOLTIP_RENDERER, false, ModernUiCompat.class.getClassLoader());
			var field = cls.getDeclaredField("sShadowRadius");
			field.setAccessible(true);
			field.setFloat(null, 0f);
		} catch (Throwable ignored) {
			// ignore if ModernUI not present or field not found
		}
	}
}


