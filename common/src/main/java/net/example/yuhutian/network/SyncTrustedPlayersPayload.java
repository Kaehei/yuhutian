package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * S2C 网络包：实时同步信任玩家列表（含玩家名）。
 * <p>
 * 服务端在添加/移除好友后立即发送此包，
 * 客户端收到后更新 Menu 数据并局部刷新领地管理标签页。
 * </p>
 */
public record SyncTrustedPlayersPayload(
        List<UUID> allowedPlayers,
        Map<UUID, String> trustedNames
) implements CustomPacketPayload {

    public static final Type<SyncTrustedPlayersPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "sync_trusted_players"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTrustedPlayersPayload> STREAM_CODEC =
            StreamCodec.of(SyncTrustedPlayersPayload::write, SyncTrustedPlayersPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SyncTrustedPlayersPayload p) {
        // UUID 列表
        buf.writeInt(p.allowedPlayers.size());
        for (UUID uuid : p.allowedPlayers) {
            buf.writeUUID(uuid);
        }
        // 名字映射
        buf.writeInt(p.trustedNames.size());
        for (Map.Entry<UUID, String> e : p.trustedNames.entrySet()) {
            buf.writeUUID(e.getKey());
            buf.writeUtf(e.getValue(), 64);
        }
    }

    private static SyncTrustedPlayersPayload read(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        List<UUID> uuids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) uuids.add(buf.readUUID());

        int nameCount = buf.readInt();
        Map<UUID, String> names = new LinkedHashMap<>(nameCount);
        for (int i = 0; i < nameCount; i++) {
            names.put(buf.readUUID(), buf.readUtf(64));
        }
        return new SyncTrustedPlayersPayload(uuids, names);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
