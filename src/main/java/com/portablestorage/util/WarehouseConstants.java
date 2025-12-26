package com.portablestorage.util;

public class WarehouseConstants {
    // 逻辑参数
    public static final int SLOTS_PER_ROW = 9;
    public static final int MAX_ROWS = 12;
    public static final int TOTAL_SLOTS = SLOTS_PER_ROW * MAX_ROWS;
    
    // 槽位索引 (InventoryMenu)
    public static final int WAREHOUSE_SLOT_START = 46;
    public static final int PLAYER_INVENTORY_START = 9;
    public static final int PLAYER_INVENTORY_END = 45;

    // GUI 基础布局
    public static final int VANILLA_INVENTORY_WIDTH = 176;
    public static final int VANILLA_INVENTORY_HEIGHT = 166;
    public static final int WAREHOUSE_WIDTH = 192;
    public static final int WAREHOUSE_X_OFFSET = -8; // 相对于 leftPos
    public static final int WAREHOUSE_Y_SPACING = 2; // 背包与仓库之间的间距
    public static final int WAREHOUSE_Y_OFFSET = VANILLA_INVENTORY_HEIGHT + WAREHOUSE_Y_SPACING; // 170
    
    // 搜索框 (相对于 warehouseX, warehouseY)
    public static final int SEARCH_BOX_X_OFFSET = 16;
    public static final int SEARCH_BOX_Y_OFFSET = 6;
    public static final int SEARCH_BOX_WIDTH = 141;
    public static final int SEARCH_BOX_HEIGHT = 12;
    public static final int SEARCH_BOX_INNER_OFFSET = 1; // 内部文本偏移 1px 以避开描边

    // 槽位逻辑坐标 (相对于 leftPos / topPos)
    public static final int SLOT_LOGIC_X = 8; 
    public static final int SLOT_LOGIC_Y_BASE = 191; 
    public static final int SLOT_SIZE = 18;

    // 视觉修正：贴图需要比逻辑位置偏移 -1px 才能让物品居中
    public static final int SLOT_VISUAL_OFFSET = -1;

    // 滚动条 (相对于 warehouseX, warehouseY)
    public static final int SCROLLBAR_X_OFFSET = 182;
    public static final int SCROLLBAR_Y_OFFSET = 23;
    public static final int SCROLLBAR_WIDTH = 4;
    public static final int SCROLLBAR_PADDING = 4; // 滚动条上下边距

    // 侧边按钮 (相对于 warehouseX, warehouseY)
    public static final int SIDEBAR_X_OFFSET = 192;
    public static final int SIDEBAR_BUTTON_SIZE = 16;
    public static final int SIDEBAR_BUTTON_SPACING = 1;

    // +/- 按钮 (相对于 warehouseX, warehouseY)
    public static final int PLUS_MINUS_X_OFFSET = 160;
    public static final int PLUS_MINUS_Y_OFFSET = 5;
    public static final int TINY_BUTTON_SIZE = 12;
    public static final int TINY_BUTTON_SPACING = 2;

    // 折叠/展开按钮 (相对于 leftPos, topPos - 副手槽位上方)
    public static final int FOLD_BUTTON_X_OFFSET = 76;
    public static final int FOLD_BUTTON_Y_OFFSET = 43;

    // 仓库渲染参数
    public static final int WAREHOUSE_CORNER_SIZE = 10;
    public static final int WAREHOUSE_TITLE_HEIGHT = 27; // 包含搜索框区域的高度
    public static final int WAREHOUSE_FOLDED_HEIGHT = 22; // 折叠时的背景高度
    
    // 物品数量显示
    public static final float QUANTITY_TEXT_SCALE = 0.8f;
    public static final float QUANTITY_TEXT_Z_OFFSET = 300.0f;
    public static final int QUANTITY_TEXT_X_RELATIVE = 16; // 相对于槽位左上角的 X 偏移
    public static final int QUANTITY_TEXT_Y_RELATIVE = 11; // 相对于槽位左上角的 Y 偏移
    public static final int QUANTITY_TEXT_COLOR = 0xFFFFFF;

    // 颜色
    public static final int SEARCH_BOX_BG_COLOR = 0xFF222222;
    public static final int SEARCH_BOX_BORDER_DARK = 0xFF111111;
    public static final int SEARCH_BOX_BORDER_LIGHT = 0xFF555555;
    
    public static final int SCROLLBAR_BG_COLOR = 0xFF333333;
    public static final int SCROLLBAR_THUMB_COLOR = 0xFF888888;
    public static final int SCROLLBAR_THUMB_HOVER_COLOR = 0xFFAAAAAA;
    public static final int SCROLLBAR_BORDER_LIGHT = 0xFFBBBBBB;
    public static final int SCROLLBAR_BORDER_DARK = 0xFF444444;
}

