package net.example.yuhutian;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import net.example.yuhutian.entity.IslandNPCEntity;
import net.example.yuhutian.entity.ModEntities;
import net.example.yuhutian.events.IslandBorderVisualizer;
import net.example.yuhutian.events.IslandProtectionHandler;
import net.example.yuhutian.gui.ModMenuTypes;
import net.example.yuhutian.item.YuHuTianItem;
import net.example.yuhutian.network.NetworkInit;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 玉壶天模组主类。
 * 负责注册所有物品、实体、菜单类型、网络包和事件监听器。
 */
public class YuhutianMod {
    public static final String MOD_ID = "yuhutian";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(MOD_ID, Registries.ITEM);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(MOD_ID, Registries.CREATIVE_MODE_TAB);

    /** 玉壶天物品注册引用，用于创造栏图标和 displayItems */
    private static final ResourceLocation YU_HU_TIAN_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "yu_hu_tian");

    public static final RegistrySupplier<Item> YU_HU_TIAN_ITEM =
            ITEMS.register(YU_HU_TIAN_ID,
                    () -> new YuHuTianItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<CreativeModeTab> YUHUTIAN_TAB =
            CREATIVE_TABS.register(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "main"),
                    () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                            .title(Component.translatable("itemGroup.yuhutian"))
                            .icon(() -> new ItemStack(YU_HU_TIAN_ITEM.get()))
                            .displayItems((params, output) -> {
                                output.accept(YU_HU_TIAN_ITEM.get());
                            })
                            .build()
            );

    public static void init() {
        // ===== 注册物品 =====
        ITEMS.register();

        // ===== 注册创造模式物品栏 =====
        CREATIVE_TABS.register();

        // ===== 注册实体类型 =====
        ModEntities.ENTITY_TYPES.register();
        EntityAttributeRegistry.register(ModEntities.ISLAND_NPC, IslandNPCEntity::createAttributes);

        // ===== 注册菜单类型 =====
        ModMenuTypes.MENU_TYPES.register();

        // ===== 注册网络包 =====
        NetworkInit.registerC2SPackets();

        // ===== 注册领地保护事件监听 =====
        IslandProtectionHandler.register();

        // ===== 注册领地边界粒子墙渲染（服务端 Tick 事件） =====
        IslandBorderVisualizer.register();
    }
}
