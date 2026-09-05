package com.portablestorage.mixin;

import java.util.function.Supplier;

import com.portablestorage.handler.WarehouseInteractionHandler;
import com.portablestorage.handler.WarehouseMenuHandler;
import com.portablestorage.util.CompatibilityDebug;
import com.portablestorage.util.FakePlayerUtils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void handleWarehouseClicks(int slotId, int button, ContainerInput clickType, Player player, CallbackInfo ci) {
        if (FakePlayerUtils.isFakePlayer(player))
            return;

        WarehouseMenuHandler.injectWarehouseSlots((AbstractContainerMenu) (Object) this, player);

        if (isQuickMove(clickType)) {
            if (WarehouseMenuHandler.handleQuickMove((AbstractContainerMenu) (Object) this, player, slotId) != null) {
                // 本模组已经亲自移动了物品并取消了原版 clicked()。原版在 doClick() 结尾会
                // broadcastChanges() 把槽位变化同步给客户端；取消后若不同步，玩家界面上被移动
                // 的物品会“凭空消失”（实际已在背包/仓库里，但客户端仍显示旧内容）。这里补上同步。
                ((AbstractContainerMenu) (Object) this).broadcastChanges();
                ci.cancel();
                return;
            }
        }

        if (WarehouseInteractionHandler.handleClicked((AbstractContainerMenu) (Object) this, slotId, button, clickType, player)) {
            ((AbstractContainerMenu) (Object) this).broadcastChanges();
            ci.cancel();
        }
    }

    /**
     * Prevents Vanilla's synchronizeSlotToRemote() from sending ClientboundContainerSetSlotPacket or 
     * incrementing stateId for UpgradeSlots or PlayerWarehouse slots.
     * Custom warehouse/upgrade syncing is handled via Portable Storage's own packets (S2CWarehouseSnapshotPayload).
     */
    @Inject(method = "synchronizeSlotToRemote", at = @At("HEAD"), cancellable = true)
    private void onSynchronizeSlotToRemote(int slotIndex, ItemStack stack, Supplier<ItemStack> supplier, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (slotIndex >= 0 && slotIndex < menu.slots.size()) {
            Slot slot = menu.slots.get(slotIndex);
            if (slot instanceof com.portablestorage.upgrade.UpgradeSlot || slot.container instanceof com.portablestorage.component.PlayerWarehouse) {
                CompatibilityDebug.logOnce("slot-sync-suppressed:" + menu.getClass().getName() + ":" + slot.getClass().getName(),
                        "sync", () -> "suppressed vanilla slot sync; menu=" + menu.getClass().getName()
                                + "; firstSlotIndex=" + slotIndex + "; slotType=" + slot.getClass().getName());
                ci.cancel();
            }
        }
    }

    private static boolean isQuickMove(Object clickType) {
        return clickType != null && "QUICK_MOVE".equals(clickType.toString());
    }
}