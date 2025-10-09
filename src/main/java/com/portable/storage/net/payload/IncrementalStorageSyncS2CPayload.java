package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.ArrayList;

/**
 * 增量仓库同步数据包
 * 只传输发生变化的数据，减少网络传输量
 */
public record IncrementalStorageSyncS2CPayload(
    long syncId,           // 同步ID，用于客户端确认
    List<StorageChange> changes,  // 变化列表
    boolean isFullSync     // 是否为全量同步（用于初始化或错误恢复）
) implements CustomPayload {
    
    public static final Id<IncrementalStorageSyncS2CPayload> ID = 
        new Id<>(Identifier.of(PortableStorage.MOD_ID, "incremental_storage_sync"));
    
    public static final PacketCodec<RegistryByteBuf, IncrementalStorageSyncS2CPayload> CODEC = 
        PacketCodec.tuple(
            PacketCodecs.VAR_LONG, IncrementalStorageSyncS2CPayload::syncId,
            PacketCodecs.collection(ArrayList::new, StorageChange.CODEC), IncrementalStorageSyncS2CPayload::changes,
            PacketCodecs.BOOL, IncrementalStorageSyncS2CPayload::isFullSync,
            IncrementalStorageSyncS2CPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
    
    /**
     * 单个仓库变化记录
     */
    public record StorageChange(
        int slotIndex,      // 槽位索引
        int typeOrdinal,    // 变化类型序号
        NbtCompound data    // 变化数据（ItemStack的NBT）
    ) {
        public ChangeType type() {
            return ChangeType.values()[typeOrdinal];
        }
        public static final PacketCodec<RegistryByteBuf, StorageChange> CODEC = 
            PacketCodec.tuple(
                PacketCodecs.VAR_INT, StorageChange::slotIndex,
                PacketCodecs.VAR_INT, StorageChange::typeOrdinal,
                PacketCodecs.NBT_COMPOUND, StorageChange::data,
                StorageChange::new
            );
    }
    
    /**
     * 变化类型枚举
     */
    public enum ChangeType {
        ADD,        // 添加新物品
        UPDATE,     // 更新现有物品数量
        REMOVE,     // 移除物品
        CLEAR;      // 清空所有物品
        
    }
}
