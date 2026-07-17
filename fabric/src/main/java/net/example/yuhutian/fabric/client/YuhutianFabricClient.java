package net.example.yuhutian.fabric.client;

import net.example.yuhutian.gui.IslandManagementScreen;
import net.example.yuhutian.gui.ModMenuTypes;
import net.example.yuhutian.network.NetworkInit;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Fabric 客户端初始化器。
 * 注册 Screen 工厂和 S2C 网络包接收器。
 */
public class YuhutianFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenuTypes.ISLAND_MANAGEMENT.get(), IslandManagementScreen::new);
        NetworkInit.registerS2CPackets();
    }
}
