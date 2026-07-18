package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S 网络包：更新空岛的欢迎寄语和提示音效。
 * <p>
 * 当岛主在 NPC 管理面板中修改了寄语文字或切换了音效并点击"保存"时，
 * 客户端发送此包将最新设置同步至服务端。
 * </p>
 */
public record UpdateGreetingPayload(String greetingText, String greetingSound) implements CustomPacketPayload {

    public static final Type<UpdateGreetingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "update_greeting"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateGreetingPayload> STREAM_CODEC =
            StreamCodec.of(UpdateGreetingPayload::write, UpdateGreetingPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, UpdateGreetingPayload payload) {
        buf.writeUtf(payload.greetingText, 128);
        buf.writeUtf(payload.greetingSound, 128);
    }

    private static UpdateGreetingPayload read(RegistryFriendlyByteBuf buf) {
        return new UpdateGreetingPayload(buf.readUtf(128), buf.readUtf(128));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
