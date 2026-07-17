package net.example.yuhutian.fabric;

import net.example.yuhutian.YuhutianMod;
import net.fabricmc.api.ModInitializer;

public class YuhutianFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        YuhutianMod.init();
    }
}
