package com.portable.storage.mixin.client;

import com.portable.storage.client.ClientConfig;
import com.portable.storage.client.ClientUpgradeState;
import com.portable.storage.net.payload.DepositSlotC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Unique;
import com.portable.storage.client.ClientStorageState;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
	@Shadow protected int x;
	@Shadow protected int y;
	@Shadow protected int backgroundWidth;
	@Shadow protected int backgroundHeight;
	
	@Shadow protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

	// 拦截 shift+点击，优先传入仓库
	@Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
	private void portableStorage$onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
		// 仅在背包界面或工作台界面 且 启用自动传入 且 shift+左键点击 且 槽位有物品时拦截
        if (!ClientConfig.getInstance().autoDeposit) return;
        // 仓库未启用时不拦截，保持原版 Shift+点击行为
        if (!ClientStorageState.isStorageEnabled()) return;
		if (!(((HandledScreen<?>)(Object)this) instanceof InventoryScreen) && 
		    !(((HandledScreen<?>)(Object)this) instanceof CraftingScreen)) return;
		if (actionType != SlotActionType.QUICK_MOVE) return;
		if (slot == null || !slot.hasStack()) return;
		
		// 排除合成相关槽位，这些槽位需要使用原版逻辑
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			var handler = client.player.currentScreenHandler;
			if (handler instanceof net.minecraft.screen.PlayerScreenHandler) {
				// PlayerScreenHandler: 槽位 0 是合成输出，1-4 是合成输入
				if (slotId >= 0 && slotId <= 4) {
					return; // 不拦截合成相关槽位
				}
			} else if (handler instanceof net.minecraft.screen.CraftingScreenHandler) {
				// CraftingScreenHandler: 槽位 0 是合成输出，1-9 是合成输入（3x3网格）
				if (slotId >= 0 && slotId <= 9) {
					return; // 不拦截合成相关槽位
				}
			}
		}
		
		// 发送传入仓库请求
		ClientPlayNetworking.send(new DepositSlotC2SPayload(slotId));
		ci.cancel();
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void portableStorage$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (portableStorage$isOverStorage(mouseX, mouseY) || portableStorage$isOverScrollbar(mouseX, mouseY) || portableStorage$isOverUpgradeSlots(mouseX, mouseY) || portableStorage$isOverCraftingStorage(mouseX, mouseY) || portableStorage$isOverCraftingUpgradeSlots(mouseX, mouseY)) {
			cir.setReturnValue(true);
		}
	}

	@Unique
	private boolean portableStorage$isOverStorage(double mouseX, double mouseY) {
		// 折叠状态下不拦截
		if (ClientConfig.getInstance().collapsed) return false;
		if (!(((HandledScreen<?>)(Object)this) instanceof InventoryScreen)) return false;
		int cols = 9;
		int visibleRows = 6;
		int slotSize = 18;
		int slotSpacing = 0;
		int gridLeft = this.x + 8;
		int gapBelow = 6;
		int gridTop = this.y + this.backgroundHeight + gapBelow;
		int width = cols * (slotSize + slotSpacing);
		int height = visibleRows * (slotSize + slotSpacing);
		return mouseX >= gridLeft && mouseX < gridLeft + width && mouseY >= gridTop && mouseY < gridTop + height;
	}

	@Unique
	private boolean portableStorage$isOverScrollbar(double mouseX, double mouseY) {
		// 折叠状态下不拦截
		if (ClientConfig.getInstance().collapsed) return false;
		if (!(((HandledScreen<?>)(Object)this) instanceof InventoryScreen)) return false;
		int cols = 9;
		int visibleRows = 6;
		int slotSize = 18;
		int slotSpacing = 0;
		int gridLeft = this.x + 8;
		int gapBelow = 6;
		int gridTop = this.y + this.backgroundHeight + gapBelow;
		int trackLeft = gridLeft + cols * (slotSize + slotSpacing) + 4;
		int trackTop = gridTop;
		int trackWidth = 6;
		int trackHeight = visibleRows * (slotSize + slotSpacing);
		return mouseX >= trackLeft && mouseX < trackLeft + trackWidth && mouseY >= trackTop && mouseY < trackTop + trackHeight;
	}
	
	@Unique
	private boolean portableStorage$isOverUpgradeSlots(double mouseX, double mouseY) {
		// 折叠状态下不拦截
		if (ClientConfig.getInstance().collapsed) return false;
		if (!(((HandledScreen<?>)(Object)this) instanceof InventoryScreen)) return false;
		int gapBelow = 6;
		int gridTop = this.y + this.backgroundHeight + gapBelow;
		int upgradeLeft = this.x - 24;
		int upgradeSlotSize = 18;
		int upgradeSpacing = 0;
		int baseUpgradeCount = 5;
		int extendedUpgradeCount = 6;
		
		// 检查基础升级槽位（0-4）
		for (int i = 0; i < baseUpgradeCount; i++) {
			int sx = upgradeLeft;
			int sy = gridTop + i * (upgradeSlotSize + upgradeSpacing);
			if (mouseX >= sx && mouseX < sx + upgradeSlotSize && mouseY >= sy && mouseY < sy + upgradeSlotSize) {
				return true;
			}
		}
		
		// 检查扩展升级槽位（5-10），仅在箱子升级激活时
		if (ClientUpgradeState.isChestUpgradeActive()) {
			int extendedLeft = upgradeLeft - (upgradeSlotSize + upgradeSpacing + 2);
			for (int i = 0; i < extendedUpgradeCount; i++) {
				int sx = extendedLeft;
				int sy = gridTop + i * (upgradeSlotSize + upgradeSpacing);
				if (mouseX >= sx && mouseX < sx + upgradeSlotSize && mouseY >= sy && mouseY < sy + upgradeSlotSize) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	@Unique
	private boolean portableStorage$isOverCraftingStorage(double mouseX, double mouseY) {
		// 仅在工作台界面检测
        if (!(((HandledScreen<?>)(Object)this) instanceof CraftingScreen)
            && !(((HandledScreen<?>)(Object)this) instanceof com.portable.storage.client.screen.PortableCraftingScreen)) return false;
		
		// 检查是否有工作台升级
        if (!portableStorage$hasCraftingTableUpgrade()) return false;
		
		int cols = 9;
		int visibleRows = 6;
		int slotSize = 18;
		int slotSpacing = 0;
		int gridLeft = this.x + 8;
		int gapBelow = 6;
		int gridTop = this.y + this.backgroundHeight + gapBelow;
		int width = cols * (slotSize + slotSpacing);
		int height = visibleRows * (slotSize + slotSpacing);
		return mouseX >= gridLeft && mouseX < gridLeft + width && mouseY >= gridTop && mouseY < gridTop + height;
	}
	
	@Unique
	private boolean portableStorage$isOverCraftingUpgradeSlots(double mouseX, double mouseY) {
		// 仅在工作台界面检测
        if (!(((HandledScreen<?>)(Object)this) instanceof CraftingScreen)
            && !(((HandledScreen<?>)(Object)this) instanceof com.portable.storage.client.screen.PortableCraftingScreen)) return false;
		
		// 检查是否有工作台升级
        if (!portableStorage$hasCraftingTableUpgrade()) return false;
		
		int gapBelow = 6;
		int gridTop = this.y + this.backgroundHeight + gapBelow;
		int upgradeLeft = this.x - 24;
		int upgradeSlotSize = 18;
		int upgradeSpacing = 0;
		int baseUpgradeCount = 5;
		int extendedUpgradeCount = 6;
		
		// 检查基础升级槽位（0-4）
		for (int i = 0; i < baseUpgradeCount; i++) {
			int sx = upgradeLeft;
			int sy = gridTop + i * (upgradeSlotSize + upgradeSpacing);
			if (mouseX >= sx && mouseX < sx + upgradeSlotSize && mouseY >= sy && mouseY < sy + upgradeSlotSize) {
				return true;
			}
		}
		
		// 检查扩展升级槽位（5-10），仅在箱子升级激活时
		if (ClientUpgradeState.isChestUpgradeActive()) {
			int extendedLeft = upgradeLeft - (upgradeSlotSize + upgradeSpacing + 2);
			for (int i = 0; i < extendedUpgradeCount; i++) {
				int sx = extendedLeft;
				int sy = gridTop + i * (upgradeSlotSize + upgradeSpacing);
				if (mouseX >= sx && mouseX < sx + upgradeSlotSize && mouseY >= sy && mouseY < sy + upgradeSlotSize) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	@Unique
	private boolean portableStorage$hasCraftingTableUpgrade() {
		// 检查升级槽位中是否有工作台且未禁用
		MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return false;

        var handler = client.player.currentScreenHandler;
        // 自定义工作台界面（PortableCraftingScreenHandler）视为已启用升级
        if (handler instanceof com.portable.storage.screen.PortableCraftingScreenHandler) return true;
        if (!(handler instanceof net.minecraft.screen.CraftingScreenHandler)) return false;

		// 检查所有升级槽位是否有工作台且未禁用
		for (int i = 0; i < 5; i++) {
			var stack = ClientUpgradeState.getStack(i);
			if (!stack.isEmpty() && stack.getItem() == net.minecraft.item.Items.CRAFTING_TABLE && !ClientUpgradeState.isSlotDisabled(i)) {
				return true;
			}
		}
		return false;
	}
}


