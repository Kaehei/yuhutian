package net.example.yuhutian.forge.client;

import net.example.yuhutian.gui.IslandManagementScreen;
import net.example.yuhutian.gui.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge 客户端初始化器。
 * 在 FMLClientSetupEvent 中注册 Screen 工厂。
 */
@Mod.EventBusSubscriber(modid = "yuhutian", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class YuhutianForgeClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.ISLAND_MANAGEMENT.get(), IslandManagementScreen::new);
        });
    }
}
