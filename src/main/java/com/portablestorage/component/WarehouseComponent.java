package com.portablestorage.component;

import net.minecraft.world.item.ItemStack;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.List;

public interface WarehouseComponent extends Component, AutoSyncedComponent {
    // 基础存储操作
    void addItem(ItemStack stack);
    ItemStack removeItem(int index, int amount);
    
    // 获取排序后的条目列表
    List<WarehouseEntry> getEntries();
    
    // 滚动偏移量管理
    int getScrollOffset();
    void setScrollOffset(int offset);
    
    // 获取当前视口显示的 54 个物品
    ItemStack getViewSlot(int slotIndex);

    // 获取实际存储的数量
    long getRealCount(int slotIndex);
}
