package com.portable.storage.player;

import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.UpgradeInventory;
import net.minecraft.entity.player.PlayerEntity;

/**
 * 对外提供获取/读写玩家随身仓库的入口，封装 cast 与空值处理。
 */
public final class PlayerStorageService {
	private PlayerStorageService() {}

	public static StorageInventory getInventory(PlayerEntity player) {
		PlayerStorageAccess access = (PlayerStorageAccess) player;
		return access.portableStorage$getInventory();
	}
	
	public static UpgradeInventory getUpgradeInventory(PlayerEntity player) {
		PlayerStorageAccess access = (PlayerStorageAccess) player;
		return access.portableStorage$getUpgradeInventory();
	}
}


