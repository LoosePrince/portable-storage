package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端切换收藏后发往该玩家，供客户端即时刷新置顶状态。 */
public record S2CWarehousePinnedUpdatePayload(int sortedIndex, boolean pinned) implements CustomPacketPayload {
    public static final Type<S2CWarehousePinnedUpdatePayload> TYPE =
            new Type<>(PortableStorage.id("warehouse_pinned_update"));

    public static final StreamCodec<FriendlyByteBuf, S2CWarehousePinnedUpdatePayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.sortedIndex);
                        buf.writeBoolean(payload.pinned);
                    },
                    buf -> new S2CWarehousePinnedUpdatePayload(buf.readInt(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
