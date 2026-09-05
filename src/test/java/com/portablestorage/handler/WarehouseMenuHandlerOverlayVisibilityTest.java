package com.portablestorage.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 回归测试：物品丢失场景 —— 仓库处于激活状态，但玩家在普通容器界面（箱子）且未安装
 * 工作台升级，因此仓库覆盖层不可见。
 *
 * <p>此时仓库的幻影槽位必须保持未激活（getItem 返回空、mayPlace 返回 false）。否则
 * 26.2 原版 ChestMenu.quickMoveStack 在移动箱子槽位时调用
 * moveItemStackTo(stack, rows*9, slots.size(), true)，会从槽位列表【尾部】反向遍历，
 * 首先扫到注入的幻影槽位；其“堆叠合并”分支不检查 isActive()/mayPlace()，只要
 * getItem() 返回了仓库内的同种物品，就会 itemstack.setCount(...) 把玩家堆叠原地并入
 * 不可见的仓库 —— 物品从可见容器中凭空“消失”。
 */
class WarehouseMenuHandlerOverlayVisibilityTest {

    /** 代表“已安装工作台升级”的仓库。纯 JUnit 未引导游戏注册表，无法构造真实 ItemStack
     * 写入 upgradeStorage，因此直接覆写 hasWorkbenchUpgrade()。 */
    private static final class WorkbenchInstalledWarehouse extends PlayerWarehouse {
        WorkbenchInstalledWarehouse(UUID id) {
            super(id, ignored -> {
            });
        }

        @Override
        public boolean hasWorkbenchUpgrade() {
            return true;
        }
    }

    /** 代表任意“普通容器菜单”（如 ChestMenu/FurnaceMenu）：非背包、非模组专用界面。 */
    private static final class FakeContainerMenu extends AbstractContainerMenu {
        FakeContainerMenu() {
            // 26.2 的 AbstractContainerMenu(MenuType, int) 构造器仅保存字段、不访问注册表，
            // 纯 JUnit 环境下可安全传入 null MenuType。
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    @Test
    void containerMenuWithoutWorkbenchUpgradeHidesOverlayEvenWhenWarehouseEnabled() {
        PlayerWarehouse warehouse = new PlayerWarehouse(UUID.randomUUID(), ignored -> {
        });
        warehouse.setType(PlayerWarehouse.WarehouseType.BASE);
        warehouse.setEnabled(true);
        assertTrue(warehouse.isEnabled(), "场景前提：仓库处于激活状态");

        AbstractContainerMenu containerMenu = new FakeContainerMenu();
        assertTrue(WarehouseMenuHandler.isContainerMenu(containerMenu));
        assertFalse(WarehouseMenuHandler.isWarehouseOverlayVisible(containerMenu, warehouse),
                "未装工作台升级的普通容器界面中仓库覆盖层不可见：shift+点击不得触达仓库槽位");
    }

    @Test
    void containerMenuWithWorkbenchUpgradeShowsOverlay() {
        PlayerWarehouse warehouse = new WorkbenchInstalledWarehouse(UUID.randomUUID());
        warehouse.setType(PlayerWarehouse.WarehouseType.BASE);
        warehouse.setEnabled(true);
        assertTrue(warehouse.hasWorkbenchUpgrade());

        assertTrue(WarehouseMenuHandler.isWarehouseOverlayVisible(new FakeContainerMenu(), warehouse),
                "装了工作台升级后仓库覆盖层在容器界面可见");
    }
}
