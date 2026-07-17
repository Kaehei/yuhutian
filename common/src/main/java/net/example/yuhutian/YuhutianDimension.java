package net.example.yuhutian;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 玉壶天维度注册常量。
 * RegistryKey: yuhutian:yuhutian
 */
public final class YuhutianDimension {

    public static final ResourceKey<Level> YUHUTIAN_LEVEL =
            ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "yuhutian"));

    private YuhutianDimension() {
    }
}
