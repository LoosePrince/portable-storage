package com.portablestorage.block.entity;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.portablestorage.screen.BoundBarrelScreenHandler;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BoundBarrelBlockEntity extends BlockEntity implements SidedStorageBlockEntity, MenuProvider {
    private UUID ownerUuid;
    private String ownerName = "";
    private final SimpleContainer inventory = new SimpleContainer(5) {
        @Override
        public void setChanged() {
            super.setChanged();
            BoundBarrelBlockEntity.this.setChanged();
        }
    };
    
    // 缓存仓库引用以提高性能
    private PlayerWarehouse cachedWarehouse;

    public BoundBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOUND_BARREL, pos, state);
    }

    @Override
    public Component getDisplayName() {
        if (ownerName == null || ownerName.isEmpty()) {
            return Component.translatable("container.portablestorage.bound_barrel_unbound");
        }
        return Component.translatable("container.portablestorage.bound_barrel", ownerName);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new BoundBarrelScreenHandler(syncId, playerInventory, inventory);
    }

    public void setOwner(UUID uuid, String name) {
        this.ownerUuid = uuid;
        this.ownerName = name;
        this.cachedWarehouse = null; // 重置缓存
        this.setChanged();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    private PlayerWarehouse getWarehouse() {
        if (ownerUuid == null || level == null || level.isClientSide) return null;
        if (cachedWarehouse == null) {
            cachedWarehouse = ModComponents.getWarehouse(level.getServer(), ownerUuid);
        }
        return cachedWarehouse;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUuid != null) {
            tag.putUUID("owner", ownerUuid);
            tag.putString("ownerName", ownerName);
        }
        tag.put("inventory", inventory.createTag(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("owner")) {
            ownerUuid = tag.getUUID("owner");
            ownerName = tag.getString("ownerName");
        }
        inventory.fromTag(tag.getList("inventory", 10), registries);
        this.cachedWarehouse = null; // 加载后重置缓存
    }

    @Override
    public @Nullable Storage<ItemVariant> getItemStorage(Direction side) {
        PlayerWarehouse warehouse = getWarehouse();
        if (warehouse == null || !warehouse.isEnabled()) return null;

        // 返回一个自定义的 Storage 封装，连接到 PlayerWarehouse 并受本地 inventory 过滤
        return new BoundBarrelItemStorage(warehouse, inventory);
    }
}

