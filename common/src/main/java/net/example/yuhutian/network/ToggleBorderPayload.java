package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S 网络包：切换领地边界粒子墙的显示状态。
 * <p>
 * 当岛主在 NPC 管理面板中勾选或取消"显示边界"时，
 * 客户端发送此包通知服务端修改对应 IslandInfo 的 showBorder 字段。
 * </p>
 */
public record ToggleBorderPayload(boolean showBorder) implements CustomPacketPayload {

    public static final Type<ToggleBorderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "toggle_border"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleBorderPayload> STREAM_CODEC =
            StreamCodec.of(ToggleBorderPayload::write, ToggleBorderPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ToggleBorderPayload payload) {
        buf.writeBoolean(payload.showBorder);
    }

    private static ToggleBorderPayload read(RegistryFriendlyByteBuf buf) {
        return new ToggleBorderPayload(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
