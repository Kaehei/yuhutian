package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * C2S 网络包：请求传送到指定空岛。
 * <p>
 * 客户端在拜访列表中选择一个空岛后发送此包，
 * 服务端重新验证权限（owner 或 allowedPlayers），
 * 通过后执行跨维度传送并触发入场欢迎仪式。
 * </p>
 */
public record TeleportToIslandPayload(UUID targetOwnerUuid) implements CustomPacketPayload {

    public static final Type<TeleportToIslandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "teleport_to_island"));

    /**
     * MC 1.21.1 的 FriendlyByteBuf 有两个 readUUID/writeUUID 重载，
     * 方法引用会产生歧义，因此使用 lambda 包装。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportToIslandPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUUID(pkt.targetOwnerUuid()),
                    buf -> new TeleportToIslandPayload(buf.readUUID())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
