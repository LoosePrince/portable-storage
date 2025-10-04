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
}


