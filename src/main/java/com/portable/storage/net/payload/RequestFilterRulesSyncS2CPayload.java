package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 服务器向客户端请求同步筛选规则的网络包
 */
public final class RequestFilterRulesSyncS2CPayload {
    public static final Identifier ID = new Identifier(PortableStorage.MOD_ID, "request_filter_rules_sync");
    public static void write(PacketByteBuf buf, RequestFilterRulesSyncS2CPayload v) { }
    public static RequestFilterRulesSyncS2CPayload read(PacketByteBuf buf) { return new RequestFilterRulesSyncS2CPayload(); }
}
