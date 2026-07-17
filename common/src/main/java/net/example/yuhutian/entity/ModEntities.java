package net.example.yuhutian.entity;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.example.yuhutian.YuhutianMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * 模组实体注册中心。
 * 使用 Architectury DeferredRegister 注册所有自定义实体类型。
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(YuhutianMod.MOD_ID, BuiltInRegistries.ENTITY_TYPE);

    /**
     * 空岛 NPC 实体类型。
     * 基于 PathfinderMob，宽高 0.6×1.95（与村民相同）。
     * 不配置自然生成，仅由 IslandGenerator 手动召唤。
     */
    public static final RegistrySupplier<EntityType<IslandNPCEntity>> ISLAND_NPC =
            ENTITY_TYPES.register(
                    ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "island_npc"),
                    () -> EntityType.Builder.of(IslandNPCEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .build(YuhutianMod.MOD_ID + ":island_npc")
            );

    private ModEntities() {
    }
}
