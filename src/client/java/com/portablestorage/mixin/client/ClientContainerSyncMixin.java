package com.portablestorage.mixin.client;

import java.util.List;

import com.portablestorage.handler.WarehouseMenuHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 防止 container_set_content / container_set_slot 导致 IndexOutOfBoundsException。
 *
 * 根因：服务端 AbstractContainerMenu 包含仓库/升级槽位（通过 addSlot 注入），
 * 发送同步包时包含这些槽位的数据；但客户端可能尚未注入仓库槽位，
 * remoteSlots 大小不足导致越界。
 *
 * 修复：在包处理前调用 injectWarehouseSlots，其内部的 addSlot() 调用
 * 会自动扩展 lastSlots 和 remoteSlots 列表，使后续索引访问安全。
 *
 * 不访问任何私有字段，仅使用公开 API。
 */
@Mixin(AbstractContainerMenu.class)
public class ClientContainerSyncMixin {

    @Inject(method = "initializeContents", at = @At("HEAD"))
    private void beforeInitializeContents(int stateId, List<ItemStack> items, ItemStack carried, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (items.size() > menu.slots.size()) {
            WarehouseMenuHandler.injectWarehouseSlots(menu, Minecraft.getInstance().player);
        }
    }

    @Inject(method = "setRemoteSlot", at = @At("HEAD"))
    private void beforeSetRemoteSlot(int slotIndex, ItemStack stack, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (slotIndex >= menu.slots.size()) {
            WarehouseMenuHandler.injectWarehouseSlots(menu, Minecraft.getInstance().player);
        }
    }
}