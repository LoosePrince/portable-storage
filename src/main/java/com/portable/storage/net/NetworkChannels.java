package com.portable.storage.net;

// 1.20.1 无 PayloadTypeRegistry：该类在 1.20.1 适配中不进行注册，改为在具体处使用 ServerPlayNetworking 直接注册

public final class NetworkChannels {
	private NetworkChannels() {}

    public static void registerCodecs() {
        // no-op for 1.20.1
    }
}