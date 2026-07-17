package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * C2S 网络包：请求从空岛权限列表中移除指定玩家。
 * <p>
 * 客户端发送目标玩家的 UUID，
 * 服务端从对应空岛的 allowedPlayers 中移除该 UUID。
 * </p>
 */
public record RemoveFriendPayload(UUID playerUuid) implements CustomPacketPayload {

    public static final Type<RemoveFriendPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "remove_friend"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveFriendPayload> STREAM_CODEC =
            StreamCodec.of(RemoveFriendPayload::write, RemoveFriendPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, RemoveFriendPayload payload) {
        buf.writeUUID(payload.playerUuid);
    }

    private static RemoveFriendPayload read(RegistryFriendlyByteBuf buf) {
        return new RemoveFriendPayload(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
