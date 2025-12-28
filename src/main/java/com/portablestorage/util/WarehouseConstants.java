package com.portablestorage.util;

public class WarehouseConstants {
    // 逻辑参数
    public static final int SLOTS_PER_ROW = 9;
    public static final int MAX_ROWS = 12;
    public static final int TOTAL_SLOTS = SLOTS_PER_ROW * MAX_ROWS;
    
    // 槽位索引 (InventoryMenu)
    public static final int CRAFTING_INPUT_COUNT = 9; // 3x3
    public static final int VANILLA_CRAFTING_INPUT_COUNT = 4; // 2x2
    public static final int EXTRA_CRAFTING_SLOTS = CRAFTING_INPUT_COUNT - VANILLA_CRAFTING_INPUT_COUNT; // 5个新槽位
    
    public static int getWarehouseSlotStart() {
        return 46 + (com.portablestorage.config.ModConfig.is3x3Enabled() ? EXTRA_CRAFTING_SLOTS : 0);
    }

    public static final int PLAYER_INVENTORY_START = 9;
    public static final int PLAYER_INVENTORY_END = 45;

    // 3x3 合成槽位逻辑坐标 (相对于 leftPos / topPos)
    public static final int CRAFT_3X3_X = 98;
    public static final int CRAFT_3X3_Y = 18;
    public static final int CRAFT_RESULT_X = 154;
    public static final int CRAFT_RESULT_Y = 28;

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
    public static final int SEARCH_BOX_WIDTH = 143;
    public static final int SEARCH_BOX_HEIGHT = 12;
    public static final int SEARCH_BOX_INNER_OFFSET = 2; // 内部文本偏移 1px 以避开描边

    // 槽位逻辑坐标 (相对于 leftPos / topPos)
    public static final int SLOT_LOGIC_X = 8; 
    public static final int SLOT_LOGIC_Y_BASE = 191; 
    public static final int SLOT_SIZE = 18;

    // 视觉修正：贴图需要比逻辑位置偏移 -1px 才能让物品居中
    public static final int SLOT_VISUAL_OFFSET = -1;

    // 滚动条 (相对于 warehouseX, warehouseY)
    public static final int SCROLLBAR_X_OFFSET = 181;
    public static final int SCROLLBAR_Y_OFFSET = 23;
    public static final int SCROLLBAR_WIDTH = 4;
    public static final int SCROLLBAR_PADDING = 4; // 滚动条上下边距

    // 侧边按钮 (相对于 warehouseX, warehouseY)
    public static final int SIDEBAR_X_OFFSET = 192;
    public static final int SIDEBAR_BUTTON_SIZE = 16;
    public static final int SIDEBAR_BUTTON_SPACING = 1;

    // +/- 按钮 (相对于 warehouseX, warehouseY)
    public static final int PLUS_MINUS_X_OFFSET = 162;
    public static final int PLUS_MINUS_Y_OFFSET = 6;
    public static final int TINY_BUTTON_SIZE = 11;
    public static final int TINY_BUTTON_SPACING = 2;

    // 折叠/展开按钮 (相对于 leftPos, topPos - 副手槽位上方)
    public static final int FOLD_BUTTON_X_OFFSET = 76;
    public static final int FOLD_BUTTON_Y_OFFSET = 43;

    // 仓库渲染参数
    public static final int WAREHOUSE_CORNER_SIZE = 10;
    public static final int WAREHOUSE_TITLE_HEIGHT = 27; // 包含搜索框区域的高度
    public static final int WAREHOUSE_FOLDED_HEIGHT = 22; // 折叠时的背景高度
    
    // 界面偏移计算参数 (yOffset)
    public static final int OFFSET_FOLDED = 0; // 折叠时回到原位
    public static final int OFFSET_BASE = 10; // 未折叠时相对于背包的偏移
    public static final int OFFSET_PER_ROW = 10; // 每行相对于背包的偏移

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

    // 贴图与图标参数
    public static final int ICON_TEXTURE_WIDTH = 80;
    public static final int ICON_TEXTURE_HEIGHT = 48;
    public static final int ICON_SIZE = 16;
    public static final int GUI_TEXTURE_SIZE = 30;
    
    // 图标索引
    public static final int ICON_FOLDED = 13;
    public static final int ICON_UNFOLDED = 0;
    public static final int ICON_SORT_MODE_BASE = 1;
    public static final int ICON_ORDER_DESC = 5;
    public static final int ICON_ORDER_ASC = 6;
    public static final int ICON_QUICK_INTERACTION = 9;
    public static final int ICON_SMART_COLLAPSE_ON = 10;
    public static final int ICON_SMART_COLLAPSE_OFF = 11;
    public static final int ICON_CRAFT_REFILL = 7;
    public static final int ICON_CRAFTING_TABLE = 14;
    
    // 智能折叠虚拟条目标识 (使用自定义 NBT)
    public static final String SMART_COLLAPSE_TAG = "portablestorage_collapsed";
}

