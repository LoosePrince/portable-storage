package com.portable.storage.player;

import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.UpgradeInventory;

/**
 * 暴露给 mixin 的接口，以便在玩家实体上挂载/获取随身仓库。
 */
public interface PlayerStorageAccess {
	StorageInventory portableStorage$getInventory();
	void portableStorage$setInventory(StorageInventory inventory);
	UpgradeInventory portableStorage$getUpgradeInventory();
	void portableStorage$setUpgradeInventory(UpgradeInventory inventory);
	
	/**
	 * 检查玩家是否已启用随身仓库功能
	 */
	boolean portableStorage$isStorageEnabled();
	
	/**
	 * 设置玩家随身仓库启用状态
	 */
	void portableStorage$setStorageEnabled(boolean enabled);
}


