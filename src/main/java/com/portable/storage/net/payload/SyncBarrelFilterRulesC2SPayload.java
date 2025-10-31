package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 客户端到服务器的同步绑定木桶筛选规则
 */
public final class SyncBarrelFilterRulesC2SPayload {
    public static final Identifier ID = new Identifier(MOD_ID, "sync_barrel_filter_rules");

    public final BlockPos barrelPos;
    public final List<FilterRule> filterRules;

    public SyncBarrelFilterRulesC2SPayload(BlockPos barrelPos, List<FilterRule> filterRules) {
        this.barrelPos = barrelPos;
        this.filterRules = filterRules;
    }

    public static void write(PacketByteBuf buf, SyncBarrelFilterRulesC2SPayload value) {
        buf.writeBlockPos(value.barrelPos);
        buf.writeVarInt(value.filterRules.size());
        for (FilterRule rule : value.filterRules) {
            buf.writeString(rule.matchRule);
            buf.writeBoolean(rule.isWhitelist);
            buf.writeBoolean(rule.enabled);
        }
    }

    public static SyncBarrelFilterRulesC2SPayload read(PacketByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int filterCount = buf.readVarInt();
        List<FilterRule> list = new ArrayList<>();
        for (int i = 0; i < filterCount; i++) {
            list.add(new FilterRule(buf.readString(), buf.readBoolean(), buf.readBoolean()));
        }
        return new SyncBarrelFilterRulesC2SPayload(pos, list);
    }

    /** 筛选规则类 */
    public static class FilterRule {
        public final String matchRule;
        public final boolean isWhitelist;
        public final boolean enabled;
        public FilterRule(String matchRule, boolean isWhitelist, boolean enabled) { this.matchRule = matchRule; this.isWhitelist = isWhitelist; this.enabled = enabled; }
    }
}