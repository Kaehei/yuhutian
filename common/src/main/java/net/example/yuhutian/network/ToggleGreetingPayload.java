package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S 网络包：切换欢迎仪式开关。
 * <p>
 * 岛主在管理面板中开启/关闭入场欢迎（Title + 音效）时发送此包。
 * </p>
 */
public record ToggleGreetingPayload(boolean enableGreeting) implements CustomPacketPayload {

    public static final Type<ToggleGreetingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "toggle_greeting"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleGreetingPayload> STREAM_CODEC =
            StreamCodec.of(ToggleGreetingPayload::write, ToggleGreetingPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ToggleGreetingPayload payload) {
        buf.writeBoolean(payload.enableGreeting);
    }

    private static ToggleGreetingPayload read(RegistryFriendlyByteBuf buf) {
        return new ToggleGreetingPayload(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
