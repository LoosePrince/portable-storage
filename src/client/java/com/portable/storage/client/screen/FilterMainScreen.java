package com.portable.storage.client.screen;

import com.portable.storage.sync.PlayerViewState;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 筛选系统主界面
 * 提供左右两个按钮进入筛选界面和销毁界面
 */
public class FilterMainScreen extends Screen {
    private ButtonWidget filterButton;
    private ButtonWidget destroyButton;
    private ButtonWidget backButton;
    
    public FilterMainScreen() {
        super(Text.translatable("portable-storage.filter.main.title"));
    }
    
    public FilterMainScreen(Screen parent) {
        super(Text.translatable("portable-storage.filter.main.title"));
        this.parent = parent;
    }
    
    public FilterMainScreen(Screen parent, net.minecraft.util.math.BlockPos barrelPos) {
        super(Text.translatable("portable-storage.barrel.filter.title"));
        this.parent = parent;
        this.barrelPos = barrelPos;
    }
    
    private Screen parent = null;
    private net.minecraft.util.math.BlockPos barrelPos = null;
    
    @Override
    protected void init() {
        super.init();
        
        // 计算界面位置
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // 创建按钮
        int buttonWidth = 80;
        int buttonHeight = 20;
        int gap = 20;
        
        int totalWidth = buttonWidth * 2 + gap;
        int startX = centerX - totalWidth / 2;
        
        this.filterButton = ButtonWidget.builder(
            Text.translatable("portable-storage.filter.main.filter_button"),
            button -> openFilterScreen()
        ).dimensions(startX, centerY - buttonHeight, buttonWidth, buttonHeight).build();
        
        this.destroyButton = ButtonWidget.builder(
            Text.translatable("portable-storage.filter.main.destroy_button"),
            button -> openDestroyScreen()
        ).dimensions(startX + buttonWidth + gap, centerY - buttonHeight, buttonWidth, buttonHeight).build();
        
        this.addDrawableChild(filterButton);
        this.addDrawableChild(destroyButton);
        
        this.backButton = ButtonWidget.builder(
            Text.translatable("portable-storage.filter.main.back_button"),
            button -> {
                if (parent != null) {
                    MinecraftClient.getInstance().setScreen(parent);
                } else {
                    close();
                }
            }
        ).dimensions(centerX - 40, centerY + 20, 80, 20).build();
        
        this.addDrawableChild(backButton);
        
        
        // 标记开始查看仓库界面
        if (MinecraftClient.getInstance().player != null) {
            PlayerViewState.startViewing(MinecraftClient.getInstance().player.getUuid());
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 先调用super.render()渲染背景和控件
        super.render(context, mouseX, mouseY, delta);
        
        // 然后在最上层渲染标题，确保不被模糊层覆盖
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
    }
    
    
    @Override
    public void close() {
        // 标记停止查看仓库界面
        if (MinecraftClient.getInstance().player != null) {
            PlayerViewState.stopViewing(MinecraftClient.getInstance().player.getUuid());
        }
        super.close();
    }
    
    private void openFilterScreen() {
        if (barrelPos != null) {
            MinecraftClient.getInstance().setScreen(new FilterListScreen(this, FilterListScreen.Mode.FILTER, barrelPos));
        } else {
            MinecraftClient.getInstance().setScreen(new FilterListScreen(this, FilterListScreen.Mode.FILTER));
        }
    }
    
    
    private void openDestroyScreen() {
        if (barrelPos != null) {
            MinecraftClient.getInstance().setScreen(new FilterListScreen(this, FilterListScreen.Mode.DESTROY, barrelPos));
        } else {
            MinecraftClient.getInstance().setScreen(new FilterListScreen(this, FilterListScreen.Mode.DESTROY));
        }
    }
}
