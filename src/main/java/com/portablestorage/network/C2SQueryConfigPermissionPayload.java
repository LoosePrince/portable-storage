package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端请求：查询当前玩家是否有权限编辑服务器配置
 * 仅作为权限探测，不携带额外数据。
 */
public record C2SQueryConfigPermissionPayload() implements CustomPacketPayload {
    public static final Type<C2SQueryConfigPermissionPayload> TYPE =
            new Type<>(PortableStorage.id("query_config_permission"));

    public static final StreamCodec<FriendlyByteBuf, C2SQueryConfigPermissionPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        // 无数据包体
                    },
                    buf -> new C2SQueryConfigPermissionPayload()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

