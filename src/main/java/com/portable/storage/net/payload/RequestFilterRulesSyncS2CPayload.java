package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务器向客户端请求同步筛选规则的网络包
 */
public record RequestFilterRulesSyncS2CPayload() implements CustomPayload {
    public static final Id<RequestFilterRulesSyncS2CPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "request_filter_rules_sync"));
    public static final PacketCodec<RegistryByteBuf, RequestFilterRulesSyncS2CPayload> CODEC = PacketCodec.of(
        (payload, buf) -> {
            // 空载荷，不需要写入任何数据
        },
        buf -> new RequestFilterRulesSyncS2CPayload()
    );
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
