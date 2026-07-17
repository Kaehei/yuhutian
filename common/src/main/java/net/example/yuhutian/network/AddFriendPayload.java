package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S 网络包：请求添加好友到空岛权限列表。
 * <p>
 * 客户端发送玩家名（UTF-8 字符串，最长 16 字符），
 * 服务端根据名称查找 UUID 并添加到对应空岛的 allowedPlayers。
 * </p>
 */
public record AddFriendPayload(String playerName) implements CustomPacketPayload {

    public static final Type<AddFriendPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "add_friend"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddFriendPayload> STREAM_CODEC =
            StreamCodec.of(AddFriendPayload::write, AddFriendPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, AddFriendPayload payload) {
        buf.writeUtf(payload.playerName, 16);
    }

    private static AddFriendPayload read(RegistryFriendlyByteBuf buf) {
        return new AddFriendPayload(buf.readUtf(16));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
