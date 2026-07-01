package com.portablestorage.block.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.screen.BoundBarrelScreenHandler;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
    private boolean handledByPlayer = false;

    public void setHandledByPlayer(boolean handled) {
        this.handledByPlayer = handled;
    }

    public boolean isHandledByPlayer() {
        return handledByPlayer;
    }

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
        if (ownerUuid == null || level == null || level.isClientSide())
            return null;
        if (cachedWarehouse == null) {
            cachedWarehouse = ModComponents.getWarehouse(level.getServer(), ownerUuid);
        }
        return cachedWarehouse;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (ownerUuid != null) {
            out.putString("owner", ownerUuid.toString());
            out.putString("ownerName", ownerName);
        }
        // 保存物品栏：使用 ItemStack.CODEC 列表
        var list = out.list("inventory", ItemStack.CODEC);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            // 1.21.11 的 ItemStack.CODEC 不再允许 0 个 air，
            // 这里跳过空槽，加载时会自动补 EMPTY。
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        // 读取拥有者
        in.getString("owner").ifPresent(str -> {
            if (!str.isEmpty()) {
                try {
                    ownerUuid = UUID.fromString(str);
                } catch (IllegalArgumentException ignored) {
                    ownerUuid = null;
                }
            }
        });
        ownerName = in.getString("ownerName").orElse("");

        // 读取物品栏
        var list = in.listOrEmpty("inventory", ItemStack.CODEC);
        int idx = 0;
        for (ItemStack stack : list) {
            if (idx < inventory.getContainerSize()) {
                inventory.setItem(idx, stack);
                idx++;
            } else {
                break;
            }
        }
        // 剩余槽位补空
        while (idx < inventory.getContainerSize()) {
            inventory.setItem(idx, ItemStack.EMPTY);
            idx++;
        }

        this.cachedWarehouse = null; // 加载后重置缓存
    }

    @Override
    public @Nullable Storage<ItemVariant> getItemStorage(Direction side) {
        PlayerWarehouse warehouse = getWarehouse();
        if (warehouse == null || !warehouse.isEnabled())
            return null;

        // 返回一个自定义的 Storage 封装，连接到 PlayerWarehouse 并受本地 inventory 过滤
        return new BoundBarrelItemStorage(warehouse, inventory, level.getServer(), ownerUuid);
    }
}
