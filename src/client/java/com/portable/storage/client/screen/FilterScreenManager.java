package com.portable.storage.client.screen;

import com.portable.storage.net.payload.RequestOpenScreenC2SPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * 筛选界面管理器
 * 处理筛选系统相关界面的打开和切换
 */
public class FilterScreenManager {
    
    /**
     * 打开筛选系统主界面
     */
    public static void openFilterMainScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.setScreen(new FilterMainScreen());
        }
    }
    
    /**
     * 打开筛选界面
     */
    public static void openFilterScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.setScreen(new FilterListScreen(FilterListScreen.Mode.FILTER));
        }
    }
    
    /**
     * 打开销毁界面
     */
    public static void openDestroyScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.setScreen(new FilterListScreen(FilterListScreen.Mode.DESTROY));
        }
    }
    
    /**
     * 请求服务器打开筛选系统主界面
     */
    public static void requestFilterMainScreen() {
        ClientPlayNetworking.send(new RequestOpenScreenC2SPayload(
            RequestOpenScreenC2SPayload.Screen.FILTER_MAIN,
            null,
            ""
        ));
    }
    
    /**
     * 请求服务器打开筛选界面
     */
    public static void requestFilterScreen() {
        ClientPlayNetworking.send(new RequestOpenScreenC2SPayload(
            RequestOpenScreenC2SPayload.Screen.FILTER_SCREEN,
            null,
            ""
        ));
    }
    
    /**
     * 请求服务器打开销毁界面
     */
    public static void requestDestroyScreen() {
        ClientPlayNetworking.send(new RequestOpenScreenC2SPayload(
            RequestOpenScreenC2SPayload.Screen.DESTROY_SCREEN,
            null,
            ""
        ));
    }
    
    /**
     * 请求服务器打开绑定木桶筛选界面
     */
    public static void requestBarrelFilterScreen(net.minecraft.util.math.BlockPos barrelPos) {
        ClientPlayNetworking.send(new RequestOpenScreenC2SPayload(
            RequestOpenScreenC2SPayload.Screen.BARREL_FILTER,
            barrelPos,
            ""
        ));
    }
}
