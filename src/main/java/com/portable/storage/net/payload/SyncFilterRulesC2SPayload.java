package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 客户端向服务器同步筛选规则的网络包
 */
public record SyncFilterRulesC2SPayload(List<FilterRule> filterRules, List<FilterRule> destroyRules) implements CustomPayload {
    public static final Id<SyncFilterRulesC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "sync_filter_rules"));
    public static final PacketCodec<RegistryByteBuf, SyncFilterRulesC2SPayload> CODEC = PacketCodec.of(
        (payload, buf) -> {
            // 写入筛选规则
            buf.writeInt(payload.filterRules.size());
            for (FilterRule rule : payload.filterRules) {
                buf.writeString(rule.matchRule);
                buf.writeBoolean(rule.isWhitelist);
                buf.writeBoolean(rule.enabled);
            }
            // 写入销毁规则
            buf.writeInt(payload.destroyRules.size());
            for (FilterRule rule : payload.destroyRules) {
                buf.writeString(rule.matchRule);
                buf.writeBoolean(rule.isWhitelist);
                buf.writeBoolean(rule.enabled);
            }
        },
        buf -> {
            // 读取筛选规则
            int filterSize = buf.readInt();
            List<FilterRule> filterRules = new java.util.ArrayList<>();
            for (int i = 0; i < filterSize; i++) {
                String matchRule = buf.readString();
                boolean isWhitelist = buf.readBoolean();
                boolean enabled = buf.readBoolean();
                filterRules.add(new FilterRule(matchRule, isWhitelist, enabled));
            }
            // 读取销毁规则
            int destroySize = buf.readInt();
            List<FilterRule> destroyRules = new java.util.ArrayList<>();
            for (int i = 0; i < destroySize; i++) {
                String matchRule = buf.readString();
                boolean isWhitelist = buf.readBoolean();
                boolean enabled = buf.readBoolean();
                destroyRules.add(new FilterRule(matchRule, isWhitelist, enabled));
            }
            return new SyncFilterRulesC2SPayload(filterRules, destroyRules);
        }
    );
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
    
    /**
     * 筛选规则类
     */
    public static class FilterRule {
        public final String matchRule;
        public final boolean isWhitelist;
        public final boolean enabled;
        
        public FilterRule(String matchRule, boolean isWhitelist, boolean enabled) {
            this.matchRule = matchRule;
            this.isWhitelist = isWhitelist;
            this.enabled = enabled;
        }
    }
}
