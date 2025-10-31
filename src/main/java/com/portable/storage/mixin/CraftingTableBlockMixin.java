package com.portable.storage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.screen.PortableCraftingScreenHandler;
import com.portable.storage.storage.UpgradeInventory;

import net.minecraft.block.BlockState;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(CraftingTableBlock.class)
public abstract class CraftingTableBlockMixin {

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void portableStorage$openPortableHandler(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient) return;
        if (!(player instanceof ServerPlayerEntity sp)) return;

        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(sp);
        boolean hasUpgrade = false;
        for (int i = 0; i < upgrades.getSlotCount(); i++) {
            var st = upgrades.getStack(i);
            if (!st.isEmpty() && st.getItem() == net.minecraft.item.Items.CRAFTING_TABLE && !upgrades.isSlotDisabled(i, (ServerPlayerEntity) player)) {
                hasUpgrade = true;
                break;
            }
        }
        if (!hasUpgrade) return;

        sp.openHandledScreen(new net.minecraft.screen.NamedScreenHandlerFactory() {
            @Override
            public net.minecraft.text.Text getDisplayName() {
                return net.minecraft.text.Text.translatable("container.crafting");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv, PlayerEntity p) {
                return new PortableCraftingScreenHandler(syncId, inv, ScreenHandlerContext.create(world, pos));
            }
        });
        cir.setReturnValue(ActionResult.SUCCESS);
    }
}


