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
        net.minecraft.network.PacketByteBuf b = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        RequestOpenScreenC2SPayload.write(b, new RequestOpenScreenC2SPayload(
            RequestOpenScreenC2SPayload.Screen.FILTER_MAIN,
            null,
            ""
        ));
        ClientPlayNetworking.send(RequestOpenScreenC2SPayload.ID, b);
    }
    
    /**
     * 请求服务器打开筛选界面
     */
    public static void requestFilterScreen() {
        net.minecraft.network.PacketByteBuf b = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        RequestOpenScreenC2SPayload.write(b, new RequestOpenScreenC2SPayload(
            RequestOpenScreenC2SPayload.Screen.FILTER_SCREEN,
            null,
            ""
        ));
        ClientPlayNetworking.send(RequestOpenScreenC2SPayload.ID, b);
    }
    
    /**
     * 请求服务器打开销毁界面
     */
    public static void requestDestroyScreen() {
        net.minecraft.network.PacketByteBuf b = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        RequestOpenScreenC2SPayload.write(b, new RequestOpenScreenC2SPayload(
            RequestOpenScreenC2SPayload.Screen.DESTROY_SCREEN,
            null,
            ""
        ));
        ClientPlayNetworking.send(RequestOpenScreenC2SPayload.ID, b);
    }
    
    /**
     * 请求服务器打开绑定木桶筛选界面
     */
    public static void requestBarrelFilterScreen(net.minecraft.util.math.BlockPos barrelPos) {
        net.minecraft.network.PacketByteBuf b = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        RequestOpenScreenC2SPayload.write(b, new RequestOpenScreenC2SPayload(
            RequestOpenScreenC2SPayload.Screen.BARREL_FILTER,
            barrelPos,
            ""
        ));
        ClientPlayNetworking.send(RequestOpenScreenC2SPayload.ID, b);
    }
}
