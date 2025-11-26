package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 客户端向服务器同步筛选规则的网络包
 */
public final class SyncFilterRulesC2SPayload {
    public static final Identifier ID = new Identifier(PortableStorage.MOD_ID, "sync_filter_rules");

    public final List<FilterRule> filterRules;
    public final List<FilterRule> destroyRules;
    public final List<FilterRule> autoEatRules;

    public SyncFilterRulesC2SPayload(List<FilterRule> filterRules, List<FilterRule> destroyRules, List<FilterRule> autoEatRules) {
        this.filterRules = filterRules;
        this.destroyRules = destroyRules;
        // 自动进食规则独立于漏斗拾取规则，但默认继承一份以兼容旧配置
        if (autoEatRules == null || autoEatRules.isEmpty()) {
            this.autoEatRules = new java.util.ArrayList<>(filterRules);
        } else {
            this.autoEatRules = autoEatRules;
        }
    }

    public static void write(PacketByteBuf buf, SyncFilterRulesC2SPayload payload) {
        buf.writeInt(payload.filterRules.size());
        for (FilterRule rule : payload.filterRules) {
            buf.writeString(rule.matchRule);
            buf.writeBoolean(rule.isWhitelist);
            buf.writeBoolean(rule.enabled);
        }
        buf.writeInt(payload.destroyRules.size());
        for (FilterRule rule : payload.destroyRules) {
            buf.writeString(rule.matchRule);
            buf.writeBoolean(rule.isWhitelist);
            buf.writeBoolean(rule.enabled);
        }

        buf.writeInt(payload.autoEatRules.size());
        for (FilterRule rule : payload.autoEatRules) {
            buf.writeString(rule.matchRule);
            buf.writeBoolean(rule.isWhitelist);
            buf.writeBoolean(rule.enabled);
        }
    }

    public static SyncFilterRulesC2SPayload read(PacketByteBuf buf) {
        int filterSize = buf.readInt();
        java.util.List<FilterRule> filterRules = new java.util.ArrayList<>();
        for (int i = 0; i < filterSize; i++) {
            String matchRule = buf.readString();
            boolean isWhitelist = buf.readBoolean();
            boolean enabled = buf.readBoolean();
            filterRules.add(new FilterRule(matchRule, isWhitelist, enabled));
        }
        int destroySize = buf.readInt();
        java.util.List<FilterRule> destroyRules = new java.util.ArrayList<>();
        for (int i = 0; i < destroySize; i++) {
            String matchRule = buf.readString();
            boolean isWhitelist = buf.readBoolean();
            boolean enabled = buf.readBoolean();
            destroyRules.add(new FilterRule(matchRule, isWhitelist, enabled));
        }

        int autoEatSize = buf.readInt();
        java.util.List<FilterRule> autoEatRules = new java.util.ArrayList<>();
        for (int i = 0; i < autoEatSize; i++) {
            String matchRule = buf.readString();
            boolean isWhitelist = buf.readBoolean();
            boolean enabled = buf.readBoolean();
            autoEatRules.add(new FilterRule(matchRule, isWhitelist, enabled));
        }

        return new SyncFilterRulesC2SPayload(filterRules, destroyRules, autoEatRules);
    }

    /** 筛选规则 */
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
