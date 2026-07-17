package net.example.yuhutian.gui;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.example.yuhutian.YuhutianMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * 模组菜单类型注册中心。
 * <p>
 * 1.21.1 中 MenuType 的 MenuSupplier 签名为 (int, Inventory) -> T。
 * 额外数据通过 ServerPlayer.openMenu(provider, buf -> ...) 传递，
 * 客户端构造器通过 FriendlyByteBuf 读取。
 * </p>
 */
public final class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(YuhutianMod.MOD_ID, BuiltInRegistries.MENU);

    public static final RegistrySupplier<MenuType<IslandManagementMenu>> ISLAND_MANAGEMENT =
            MENU_TYPES.register(
                    ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "island_management"),
                    () -> new MenuType<>(
                            (id, inv) -> new IslandManagementMenu(id, inv.player,
                                    new net.minecraft.network.FriendlyByteBuf(
                                            io.netty.buffer.Unpooled.buffer())),
                            FeatureFlags.DEFAULT_FLAGS)
            );

    private ModMenuTypes() {
    }
}
