package net.example.yuhutian.fabric.client;

import net.example.yuhutian.client.IslandNPCRenderer;
import net.example.yuhutian.entity.ModEntities;
import net.example.yuhutian.gui.IslandManagementScreen;
import net.example.yuhutian.gui.ModMenuTypes;
import net.example.yuhutian.network.NetworkInit;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Fabric 客户端初始化器。
 * 注册 Screen 工厂、实体渲染器和 S2C 网络包接收器。
 */
public class YuhutianFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenuTypes.ISLAND_MANAGEMENT.get(), IslandManagementScreen::new);
        EntityRendererRegistry.register(ModEntities.ISLAND_NPC.get(), IslandNPCRenderer::new);
        NetworkInit.registerS2CPackets();
    }
}
