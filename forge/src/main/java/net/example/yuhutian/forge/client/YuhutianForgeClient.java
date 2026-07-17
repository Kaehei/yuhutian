package net.example.yuhutian.forge.client;

import net.example.yuhutian.client.IslandNPCRenderer;
import net.example.yuhutian.entity.ModEntities;
import net.example.yuhutian.gui.IslandManagementScreen;
import net.example.yuhutian.gui.ModMenuTypes;
import net.example.yuhutian.network.NetworkInit;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * NeoForge 客户端初始化器。
 * 在 RegisterMenuScreensEvent 中注册 Screen 工厂。
 * 在 FMLClientSetupEvent 中注册实体渲染器和 S2C 网络包。
 */
@Mod(value = "yuhutian", dist = Dist.CLIENT)
public class YuhutianForgeClient {

    public YuhutianForgeClient(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(this::onRegisterMenuScreens);
        modBus.addListener(this::onClientSetup);
    }

    private void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ISLAND_MANAGEMENT.get(), IslandManagementScreen::new);
    }

    private void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            EntityRenderers.register(ModEntities.ISLAND_NPC.get(), IslandNPCRenderer::new);
            NetworkInit.registerS2CPackets();
        });
    }
}
