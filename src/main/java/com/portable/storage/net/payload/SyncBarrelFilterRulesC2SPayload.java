package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 客户端到服务器的同步绑定木桶筛选规则
 */
public record SyncBarrelFilterRulesC2SPayload(BlockPos barrelPos, List<FilterRule> filterRules) implements CustomPayload {
    public static final CustomPayload.Id<SyncBarrelFilterRulesC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "sync_barrel_filter_rules"));

    public static final PacketCodec<RegistryByteBuf, SyncBarrelFilterRulesC2SPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeBlockPos(value.barrelPos());
            
            // 写入筛选规则
            buf.writeVarInt(value.filterRules().size());
            for (FilterRule rule : value.filterRules()) {
                buf.writeString(rule.matchRule());
                buf.writeBoolean(rule.isWhitelist());
                buf.writeBoolean(rule.enabled());
            }
        },
        buf -> {
            BlockPos pos = buf.readBlockPos();
            
            // 读取筛选规则
            int filterCount = buf.readVarInt();
            List<FilterRule> filterRules = new ArrayList<>();
            for (int i = 0; i < filterCount; i++) {
                filterRules.add(new FilterRule(
                    buf.readString(),
                    buf.readBoolean(),
                    buf.readBoolean()
                ));
            }
            
            return new SyncBarrelFilterRulesC2SPayload(pos, filterRules);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() { 
        return ID; 
    }
    
    /**
     * 筛选规则类
     */
    public record FilterRule(String matchRule, boolean isWhitelist, boolean enabled) {}
}