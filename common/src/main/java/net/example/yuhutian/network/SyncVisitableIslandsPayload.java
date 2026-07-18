package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S2C 网络包：同步可拜访的空岛列表到客户端。
 * <p>
 * 服务端收到 {@link RequestVisitableIslandsPayload} 后，
 * 筛选出请求者拥有或被信任的所有空岛，将列表通过此包发回客户端。
 * </p>
 */
public record SyncVisitableIslandsPayload(
        List<IslandEntry> entries
) implements CustomPacketPayload {

    /**
     * 单个可拜访空岛的条目信息。
     *
     * @param ownerName 岛主显示名称
     * @param index     空岛索引号
     * @param ownerUuid 岛主 UUID（用于传送请求）
     */
    public record IslandEntry(String ownerName, int index, UUID ownerUuid) {}

    public static final Type<SyncVisitableIslandsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "sync_visitable_islands"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncVisitableIslandsPayload> STREAM_CODEC =
            StreamCodec.of(SyncVisitableIslandsPayload::write, SyncVisitableIslandsPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SyncVisitableIslandsPayload payload) {
        buf.writeInt(payload.entries.size());
        for (IslandEntry entry : payload.entries) {
            buf.writeUtf(entry.ownerName(), 64);
            buf.writeInt(entry.index());
            buf.writeUUID(entry.ownerUuid());
        }
    }

    private static SyncVisitableIslandsPayload read(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        List<IslandEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new IslandEntry(buf.readUtf(64), buf.readInt(), buf.readUUID()));
        }
        return new SyncVisitableIslandsPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
