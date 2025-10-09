package com.portable.storage.mixin;

import com.portable.storage.net.ServerNetworkingHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 监听玩家起床事件，清理临时床方块
 */
@Mixin(PlayerEntity.class)
public class PlayerEntitySleepMixin {

    @Inject(method = "wakeUp", at = @At("HEAD"))
    private void portableStorage$onWakeUp(boolean skipSleepTimer, boolean updateSleepingPlayers, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        
        // 获取玩家睡觉的位置
        BlockPos sleepPos = self.getSleepingPosition().orElse(null);
        if (sleepPos != null) {
            // 检查是否是临时床方块
            if (ServerNetworkingHandlers.isTempBed(sleepPos)) {
                // 清理完整的临时床方块（头部和脚部）
                ServerNetworkingHandlers.cleanupCompleteTempBed(sleepPos, self.getWorld());
            }
        }
    }
}
