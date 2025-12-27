package com.portablestorage.gui;

import com.portablestorage.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ModSettingsScreen extends Screen {
    private final Screen parent;
    private int activeTab = 0; // 0: 客户端, 1: 服务端

    public ModSettingsScreen(Screen parent) {
        super(Component.translatable("gui.portablestorage.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        
        // Tab 按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.portablestorage.settings.tab.client"), b -> {
            this.activeTab = 0;
            this.init();
        }).bounds(this.width / 2 - 105, 40, 100, 20).build()).active = (activeTab != 0);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.portablestorage.settings.tab.server"), b -> {
            this.activeTab = 1;
            this.init();
        }).bounds(this.width / 2 + 5, 40, 100, 20).build()).active = (activeTab != 1);

        int y = 70;
        if (activeTab == 0) {
            // --- 客户端配置 ---
            
            // 偏移背包
            this.addRenderableWidget(CycleButton.onOffBuilder(ModConfig.offsetInventory)
                    .create(this.width / 2 - 100, y, 200, 20, 
                            Component.translatable("gui.portablestorage.settings.offset_inventory"), 
                            (button, value) -> {
                                ModConfig.offsetInventory = value;
                                if (value) ModConfig.hideRecipeBook = true;
                                ModConfig.save();
                                this.init(); // 刷新界面以同步隐藏配方书的状态
                            }));
            
            y += 25;
            
            // 隐藏配方书
            var recipeBookButton = CycleButton.onOffBuilder(ModConfig.hideRecipeBook)
                    .create(this.width / 2 - 100, y, 200, 20, 
                            Component.translatable("gui.portablestorage.settings.hide_recipe_book"), 
                            (button, value) -> {
                                ModConfig.hideRecipeBook = value;
                                ModConfig.save();
                            });
            recipeBookButton.active = !ModConfig.offsetInventory; // 如果偏移背包，强制隐藏
            this.addRenderableWidget(recipeBookButton);
            
        } else if (activeTab == 1) {
            // --- 服务端配置 ---
            
            // 3x3 合成 (只读)
            var craftingButton = CycleButton.onOffBuilder(ModConfig.is3x3Enabled())
                    .create(this.width / 2 - 100, y, 200, 20, 
                            Component.translatable("gui.portablestorage.settings.enable_3x3_crafting"), 
                            (button, value) -> {
                                // 只读，不执行修改逻辑
                            });
            craftingButton.active = false; // 禁用交互，使其成为只读展示
            this.addRenderableWidget(craftingButton);
        }

        // 完成按钮
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}

