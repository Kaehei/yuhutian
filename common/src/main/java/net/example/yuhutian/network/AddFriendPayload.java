package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * C2S 网络包：请求添加好友到空岛权限列表。
 * <p>
 * 客户端直接发送目标玩家的 UUID（从在线玩家下拉列表中选择），
 * 服务端根据 UUID 直接添加到对应空岛的 allowedPlayers，无需再通过名字查找。
 * </p>
 */
public record AddFriendPayload(UUID playerUuid) implements CustomPacketPayload {

    public static final Type<AddFriendPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "add_friend"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddFriendPayload> STREAM_CODEC =
            StreamCodec.of(AddFriendPayload::write, AddFriendPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, AddFriendPayload payload) {
        buf.writeUUID(payload.playerUuid);
    }

    private static AddFriendPayload read(RegistryFriendlyByteBuf buf) {
        return new AddFriendPayload(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
