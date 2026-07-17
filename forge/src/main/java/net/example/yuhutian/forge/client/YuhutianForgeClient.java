package net.example.yuhutian.forge.client;

import net.example.yuhutian.gui.IslandManagementScreen;
import net.example.yuhutian.gui.ModMenuTypes;
import net.example.yuhutian.network.NetworkInit;
import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * NeoForge 客户端初始化器。
 * 在 FMLClientSetupEvent 中注册 Screen 工厂和 S2C 网络包接收器。
 */
@Mod(value = "yuhutian", dist = Dist.CLIENT)
public class YuhutianForgeClient {

    public YuhutianForgeClient(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.ISLAND_MANAGEMENT.get(), IslandManagementScreen::new);
            NetworkInit.registerS2CPackets();
        });
    }
}
