package com.portablestorage.util;

/**
 * 仓库常量类
 * 定义仓库 UI 布局、尺寸、颜色等常量
 */
public class WarehouseConstants {
    // ========== 逻辑参数 ==========
    /** 每行槽位数量 */
    public static final int SLOTS_PER_ROW = 9;
    /** 最大行数 */
    public static final int MAX_ROWS = 12;
    /** 总槽位数 */
    public static final int TOTAL_SLOTS = SLOTS_PER_ROW * MAX_ROWS;

    // ========== 槽位索引 (InventoryMenu) ==========
    /** 3x3 合成槽位数量 */
    public static final int CRAFTING_INPUT_COUNT = 9;
    /** 原版 2x2 合成槽位数量 */
    public static final int VANILLA_CRAFTING_INPUT_COUNT = 4;
    /** 额外合成槽位数量（5个新槽位） */
    public static final int EXTRA_CRAFTING_SLOTS = CRAFTING_INPUT_COUNT - VANILLA_CRAFTING_INPUT_COUNT;

    /**
     * 获取仓库槽位起始索引
     * @return 始终从 51 开始（46 原版 + 5 额外合成槽位）
     */
    public static int getWarehouseSlotStart() {
        return 51;
    }

    /**
     * 获取仓库主格网起始索引
     * @return 从 63 开始（51 + 12 升级槽位）
     */
    public static int getWarehouseMainSlotStart() {
        return 63;
    }

    /** 玩家背包起始索引 */
    public static final int PLAYER_INVENTORY_START = 9;
    /** 玩家背包结束索引 */
    public static final int PLAYER_INVENTORY_END = 45;

    // ========== 3x3 合成槽位逻辑坐标（相对于 leftPos / topPos）==========
    /** 3x3 合成槽位 X 坐标 */
    public static final int CRAFT_3X3_X = 98;
    /** 3x3 合成槽位 Y 坐标 */
    public static final int CRAFT_3X3_Y = 18;
    /** 合成结果槽位 X 坐标 */
    public static final int CRAFT_RESULT_X = 154;
    /** 合成结果槽位 Y 坐标 */
    public static final int CRAFT_RESULT_Y = 28;

    // ========== GUI 基础布局 ==========
    /** 原版背包界面宽度 */
    public static final int VANILLA_INVENTORY_WIDTH = 176;
    /** 原版背包界面高度 */
    public static final int VANILLA_INVENTORY_HEIGHT = 166;

    /**
     * 动态获取升级列宽度
     * @return 若未注册任何升级，则宽度为 0，否则为 30
     */
    public static int getUpgradeColumnWidth() {
        return com.portablestorage.upgrade.UpgradeRegistry.getUpgradeCount() > 0 ? 30 : 0;
    }

    /**
     * 获取仓库宽度
     * @return 基础宽度 184 + 升级列宽度
     */
    public static int getWarehouseWidth() {
        return 184 + getUpgradeColumnWidth();
    }

    /**
     * 获取仓库高度
     * @param visibleRows 可见行数
     * @return 仓库高度
     */
    public static int getWarehouseHeight(int visibleRows) {
        return getWarehouseHeight(visibleRows, false);
    }

    /**
     * 获取仓库高度
     * @param visibleRows 可见行数
     * @param folded 是否折叠
     * @return 仓库高度
     */
    public static int getWarehouseHeight(int visibleRows, boolean folded) {
        if (folded)
            return WAREHOUSE_TITLE_HEIGHT;
        return WAREHOUSE_TITLE_HEIGHT + visibleRows * SLOT_SIZE;
    }

    /** 背包与仓库之间的垂直间距 */
    public static final int WAREHOUSE_Y_SPACING = 2;
    /** 背包与仓库之间的水平间距 */
    public static final int WAREHOUSE_X_SPACING = 2;

    /**
     * 获取仓库 X 轴偏移量
     * @return 根据存储位置计算的 X 偏移
     */
    public static int getWarehouseXOffset() {
        int columnWidth = getUpgradeColumnWidth();
        int warehouseWidth = getWarehouseWidth();

        return switch (com.portablestorage.config.ModConfig.storagePosition) {
            case TOP, BOTTOM -> {
                // 确保主格网居中：主背景宽度 184，居中偏移为 (176 - 184) / 2 = -4
                // 升级列在左侧，整体需再向左偏移 columnWidth
                yield -4 - columnWidth;
            }
            case LEFT -> -warehouseWidth - WAREHOUSE_X_SPACING;
            case RIGHT -> VANILLA_INVENTORY_WIDTH + WAREHOUSE_X_SPACING;
        };
    }

    /**
     * 获取仓库 Y 轴偏移量
     * @param visibleRows 可见行数
     * @return Y 轴偏移量
     */
    public static int getWarehouseYOffset(int visibleRows) {
        return getWarehouseYOffset(visibleRows, VANILLA_INVENTORY_HEIGHT);
    }

    /**
     * 获取仓库 Y 轴偏移量
     * @param visibleRows 可见行数
     * @param imageHeight 界面高度
     * @return Y 轴偏移量
     */
    public static int getWarehouseYOffset(int visibleRows, int imageHeight) {
        return getWarehouseYOffset(visibleRows, imageHeight, false);
    }

    /**
     * 获取仓库 Y 轴偏移量
     * @param visibleRows 可见行数
     * @param imageHeight 界面高度
     * @param folded 是否折叠
     * @return Y 轴偏移量
     */
    public static int getWarehouseYOffset(int visibleRows, int imageHeight, boolean folded) {
        StoragePosition pos = com.portablestorage.config.ModConfig.storagePosition;
        int height = getWarehouseHeight(visibleRows, folded);
        if (pos == StoragePosition.BOTTOM)
            return imageHeight + WAREHOUSE_Y_SPACING;
        if (pos == StoragePosition.TOP) {
            return -height - WAREHOUSE_Y_SPACING;
        }
        // 左右侧显示时，实现垂直居中：(背包高度 - 仓库高度) / 2
        return (imageHeight - height) / 2;
    }

    // ========== 搜索框（相对于 warehouseX, warehouseY）==========
    /**
     * 获取搜索框 X 轴偏移量
     * @return X 轴偏移量
     */
    public static int getSearchBoxXOffset() {
        return 8 + getUpgradeColumnWidth();
    }

    /** 搜索框 Y 轴偏移量 */
    public static final int SEARCH_BOX_Y_OFFSET = 6;
    /** 搜索框宽度 */
    public static final int SEARCH_BOX_WIDTH = 142;
    /** 搜索框高度 */
    public static final int SEARCH_BOX_HEIGHT = 12;
    /** 搜索框内边距 */
    public static final int SEARCH_BOX_INNER_OFFSET = 2;

    // ========== 升级槽位坐标（相对于 warehouseX, warehouseY）==========
    /** 升级槽位相对 X 坐标 */
    public static final int UPGRADE_SLOT_RELATIVE_X = 8;
    /** 升级槽位相对 Y 坐标 */
    public static final int UPGRADE_SLOT_RELATIVE_Y = 21;
    /** 升级滚动条 X 轴偏移量 */
    public static final int UPGRADE_SCROLLBAR_X_OFFSET = 28;

    // ========== 仓库槽位逻辑坐标（相对于 warehouseX, warehouseY）==========
    /**
     * 获取槽位相对 X 坐标
     * @return X 坐标
     */
    public static int getSlotRelativeX() {
        return 8 + getUpgradeColumnWidth();
    }

    /** 槽位相对 Y 坐标 */
    public static final int SLOT_RELATIVE_Y = 21;

    // ========== 槽位逻辑坐标（相对于 leftPos / topPos）==========
    /**
     * 获取槽位逻辑 X 坐标
     * @return X 坐标
     */
    public static int getSlotLogicX() {
        return getWarehouseXOffset() + getSlotRelativeX();
    }

    /**
     * 获取槽位逻辑 Y 坐标
     * @param visibleRows 可见行数
     * @return Y 坐标
     */
    public static int getSlotLogicY(int visibleRows) {
        return getSlotLogicY(visibleRows, VANILLA_INVENTORY_HEIGHT);
    }

    /**
     * 获取槽位逻辑 Y 坐标
     * @param visibleRows 可见行数
     * @param imageHeight 界面高度
     * @return Y 坐标
     */
    public static int getSlotLogicY(int visibleRows, int imageHeight) {
        return getSlotLogicY(visibleRows, imageHeight, false);
    }

    /**
     * 获取槽位逻辑 Y 坐标
     * @param visibleRows 可见行数
     * @param imageHeight 界面高度
     * @param folded 是否折叠
     * @return Y 坐标
     */
    public static int getSlotLogicY(int visibleRows, int imageHeight, boolean folded) {
        return getWarehouseYOffset(visibleRows, imageHeight, folded) + SLOT_RELATIVE_Y;
    }

    /** 槽位尺寸（像素） */
    public static final int SLOT_SIZE = 18;
    /** 槽位视觉偏移量 */
    public static final int SLOT_VISUAL_OFFSET = -1;

    // ========== 滚动条（相对于 warehouseX, warehouseY）==========
    /**
     * 获取滚动条 X 轴偏移量
     * @return X 轴偏移量
     */
    public static int getScrollbarXOffset() {
        return 173 + getUpgradeColumnWidth();
    }

    /** 滚动条 Y 轴偏移量 */
    public static final int SCROLLBAR_Y_OFFSET = 23;
    /** 滚动条宽度 */
    public static final int SCROLLBAR_WIDTH = 4;
    /** 滚动条内边距 */
    public static final int SCROLLBAR_PADDING = 4;

    // ========== 侧边按钮（相对于 warehouseX, warehouseY）==========
    /**
     * 获取侧边栏 X 轴偏移量
     * @return X 轴偏移量
     */
    public static int getSidebarXOffset() {
        if (com.portablestorage.config.ModConfig.storagePosition.isHorizontal()) {
            return 8 + getUpgradeColumnWidth();
        }
        return 184 + getUpgradeColumnWidth();
    }

    /**
     * 获取侧边栏 Y 轴偏移量
     * @param visibleRows 可见行数
     * @return Y 轴偏移量
     */
    public static int getSidebarYOffset(int visibleRows) {
        return getSidebarYOffset(visibleRows, VANILLA_INVENTORY_HEIGHT);
    }

    /**
     * 获取侧边栏 Y 轴偏移量
     * @param visibleRows 可见行数
     * @param imageHeight 界面高度
     * @return Y 轴偏移量
     */
    public static int getSidebarYOffset(int visibleRows, int imageHeight) {
        return getSidebarYOffset(visibleRows, imageHeight, false);
    }

    /**
     * 获取侧边栏 Y 轴偏移量
     * @param visibleRows 可见行数
     * @param imageHeight 界面高度
     * @param folded 是否折叠
     * @return Y 轴偏移量
     */
    public static int getSidebarYOffset(int visibleRows, int imageHeight, boolean folded) {
        if (com.portablestorage.config.ModConfig.storagePosition.isHorizontal()) {
            return getWarehouseHeight(visibleRows, folded) + 2;
        }
        return 0;
    }

    /** 侧边栏按钮尺寸 */
    public static final int SIDEBAR_BUTTON_SIZE = 16;
    /** 侧边栏按钮间距 */
    public static final int SIDEBAR_BUTTON_SPACING = 1;

    // ========== +/- 按钮（相对于 warehouseX, warehouseY）==========
    /**
     * 获取 +/- 按钮 X 轴偏移量
     * @return X 轴偏移量
     */
    public static int getPlusMinusXOffset() {
        return SEARCH_BOX_WIDTH + 10 + getUpgradeColumnWidth();
    }

    /** +/- 按钮 Y 轴偏移量 */
    public static final int PLUS_MINUS_Y_OFFSET = 6;
    /** 小按钮尺寸 */
    public static final int TINY_BUTTON_SIZE = 11;
    /** 小按钮间距 */
    public static final int TINY_BUTTON_SPACING = 2;

    // ========== 折叠/展开按钮（相对于 leftPos, topPos）==========
    /** 折叠按钮 X 轴偏移量 */
    public static final int FOLD_BUTTON_X_OFFSET = 76;
    /** 折叠按钮 Y 轴偏移量 */
    public static final int FOLD_BUTTON_Y_OFFSET = 43;

    // ========== 仓库渲染参数 ==========
    /** 仓库背景圆角尺寸 */
    public static final int WAREHOUSE_CORNER_SIZE = 10;
    /** 仓库标题栏高度 */
    public static final int WAREHOUSE_TITLE_HEIGHT = 27;
    /** 仓库折叠状态高度 */
    public static final int WAREHOUSE_FOLDED_HEIGHT = 22;

    // ========== 物品数量显示 ==========
    /** 数量文本缩放比例 */
    public static final float QUANTITY_TEXT_SCALE = 0.8f;
    /** 数量文本 Z 轴偏移量 */
    public static final float QUANTITY_TEXT_Z_OFFSET = 300.0f;
    /** 数量文本相对 X 坐标 */
    public static final int QUANTITY_TEXT_X_RELATIVE = 16;
    /** 数量文本相对 Y 坐标 */
    public static final int QUANTITY_TEXT_Y_RELATIVE = 11;
    /** 数量文本颜色（白色） */
    public static final int QUANTITY_TEXT_COLOR = 0xFFFFFF;
    /** 数量文本颜色（零数量时的灰色） */
    public static final int QUANTITY_TEXT_COLOR_ZERO = 0xFFAAAAAA;

    // ========== 搜索框颜色 ==========
    /** 搜索框背景颜色 */
    public static final int SEARCH_BOX_BG_COLOR = 0xFF222222;
    /** 搜索框边框深色 */
    public static final int SEARCH_BOX_BORDER_DARK = 0xFF111111;
    /** 搜索框边框浅色 */
    public static final int SEARCH_BOX_BORDER_LIGHT = 0xFF555555;

    // ========== 遮罩与高亮 ==========
    /** 白色半透明遮罩 */
    public static final int MASK_WHITE = 0x80FFFFFF;
    /** 黄色半透明遮罩 */
    public static final int MASK_YELLOW = 0x50FFFF00;

    // ========== 状态指示灯颜色 ==========
    /** 红色状态点 */
    public static final int STATUS_RED = 0xFFFF0000;
    /** 红色状态点边框 */
    public static final int STATUS_RED_BORDER = 0xFF550000;
    /** 绿色状态点 */
    public static final int STATUS_GREEN = 0xFF00FF00;
    /** 绿色状态点边框 */
    public static final int STATUS_GREEN_BORDER = 0xFF005500;
    /** 灰色状态点 */
    public static final int STATUS_GRAY = 0xFF888888;
    /** 灰色状态点边框 */
    public static final int STATUS_GRAY_BORDER = 0xFF444444;
    /** 玩家头像边框颜色 */
    public static final int AVATAR_BORDER = 0xFF444444;

    // ========== 按钮颜色 ==========
    /** 按钮悬停颜色 */
    public static final int BUTTON_HOVER_COLOR = 0xFFAAAAAA;
    /** 按钮默认颜色 */
    public static final int BUTTON_COLOR = 0xFF888888;
    /** 按钮边框浅色 */
    public static final int BUTTON_BORDER_LIGHT = 0xFFBBBBBB;
    /** 按钮边框深色 */
    public static final int BUTTON_BORDER_DARK = 0xFF444444;

    // ========== 滚动条颜色 ==========
    /** 滚动条背景颜色 */
    public static final int SCROLLBAR_BG_COLOR = 0xFF333333;
    /** 滚动条滑块颜色 */
    public static final int SCROLLBAR_THUMB_COLOR = 0xFF888888;
    /** 滚动条滑块悬停颜色 */
    public static final int SCROLLBAR_THUMB_HOVER_COLOR = 0xFFAAAAAA;
    /** 滚动条边框浅色 */
    public static final int SCROLLBAR_BORDER_LIGHT = 0xFFBBBBBB;
    /** 滚动条边框深色 */
    public static final int SCROLLBAR_BORDER_DARK = 0xFF444444;

    // ========== 贴图与图标参数 ==========
    /** 图标贴图宽度 */
    public static final int ICON_TEXTURE_WIDTH = 80;
    /** 图标贴图高度 */
    public static final int ICON_TEXTURE_HEIGHT = 48;
    /** 图标尺寸 */
    public static final int ICON_SIZE = 16;
    /** GUI 贴图尺寸 */
    public static final int GUI_TEXTURE_SIZE = 30;

    // ========== 图标索引 ==========
    /** 折叠图标索引 */
    public static final int ICON_FOLDED = 13;
    /** 展开图标索引 */
    public static final int ICON_UNFOLDED = 0;
    /** 排序模式图标基础索引 */
    public static final int ICON_SORT_MODE_BASE = 1;
    /** 降序图标索引 */
    public static final int ICON_ORDER_DESC = 5;
    /** 升序图标索引 */
    public static final int ICON_ORDER_ASC = 6;
    /** 快速交互图标索引 */
    public static final int ICON_QUICK_INTERACTION = 9;
    /** 智能折叠开启图标索引 */
    public static final int ICON_SMART_COLLAPSE_ON = 10;
    /** 智能折叠关闭图标索引 */
    public static final int ICON_SMART_COLLAPSE_OFF = 11;
    /** 合成补充图标索引 */
    public static final int ICON_CRAFT_REFILL = 7;
    /** 合成台图标索引 */
    public static final int ICON_CRAFTING_TABLE = 14;

    // ========== NBT 标签 ==========
    /** 智能折叠 NBT 标签 */
    public static final String SMART_COLLAPSE_TAG = "portablestorage_collapsed";
    /** 无限数量 NBT 标签 */
    public static final String INFINITE_TAG = "portablestorage_infinite";
    /** 无限数量阈值 */
    public static final long INFINITE_COUNT = 999_999_999_999L;
}
