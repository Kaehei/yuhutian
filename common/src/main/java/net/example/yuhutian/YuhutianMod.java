package net.example.yuhutian;

import dev.architectury.registry.DeferredRegister;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import net.example.yuhutian.entity.IslandNPCEntity;
import net.example.yuhutian.entity.ModEntities;
import net.example.yuhutian.events.IslandProtectionHandler;
import net.example.yuhutian.gui.ModMenuTypes;
import net.example.yuhutian.item.YuHuTianItem;
import net.example.yuhutian.network.NetworkInit;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 玉壶天模组主类。
 * 负责注册所有物品、实体、菜单类型、网络包和事件监听器。
 */
public class YuhutianMod {
    public static final String MOD_ID = "yuhutian";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(MOD_ID, BuiltInRegistries.ITEM);

    public static void init() {
        // ===== 注册物品 =====
        ITEMS.register(ResourceLocation.fromNamespaceAndPath(MOD_ID, "yu_hu_tian"),
                () -> new YuHuTianItem(new Item.Properties().stacksTo(1)));
        ITEMS.register();

        // ===== 注册实体类型 =====
        ModEntities.ENTITY_TYPES.register();
        EntityAttributeRegistry.register(ModEntities.ISLAND_NPC, IslandNPCEntity::createAttributes);

        // ===== 注册菜单类型 =====
        ModMenuTypes.MENU_TYPES.register();

        // ===== 注册网络包 =====
        NetworkInit.registerC2SPackets();

        // ===== 注册领地保护事件监听 =====
        IslandProtectionHandler.register();
    }
}
