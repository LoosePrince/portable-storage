package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端返回：当前玩家是否有权限编辑服务器配置
 */
public record S2CConfigPermissionResultPayload(boolean canEdit) implements CustomPacketPayload {
    public static final Type<S2CConfigPermissionResultPayload> TYPE =
            new Type<>(PortableStorage.id("config_permission_result"));

    public static final StreamCodec<FriendlyByteBuf, S2CConfigPermissionResultPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.canEdit),
                    buf -> new S2CConfigPermissionResultPayload(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

