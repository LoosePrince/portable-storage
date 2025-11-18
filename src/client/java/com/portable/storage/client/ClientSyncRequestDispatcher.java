package com.portable.storage.client;

import com.portable.storage.PortableStorage;
import com.portable.storage.net.payload.SyncControlC2SPayload;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

/**
 * 统一管理客户端主动发起仓库同步请求的调度器：
 * - 保证每次登录至少请求一次全量同步
 * - 允许界面打开/其它事件重复触发请求
 */
public final class ClientSyncRequestDispatcher {
    private static boolean requestedAfterLogin = false;

    private ClientSyncRequestDispatcher() {}

    public static void registerLifecycleCallbacks() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        requestedAfterLogin = false;
    }

    public static void request(String reason) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        PortableStorage.LOGGER.info("[Client] 发起仓库同步请求（{}）", reason);
        PacketByteBuf rb = new PacketByteBuf(Unpooled.buffer());
        SyncControlC2SPayload.write(rb, new SyncControlC2SPayload(
            SyncControlC2SPayload.Op.REQUEST,
            0L,
            false
        ));
        ClientPlayNetworking.send(SyncControlC2SPayload.ID, rb);
    }

    public static void requestInitialIfNeeded(String reason) {
        if (requestedAfterLogin) return;
        request(reason);
        requestedAfterLogin = true;
    }
}

