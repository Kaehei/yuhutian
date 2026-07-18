package net.example.yuhutian.network;

import net.example.yuhutian.YuhutianMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S2C 网络包：将空岛管理面板的数据同步到客户端。
 * <p>
 * MC 1.21.1 的 ServerPlayer.openMenu 不再支持 Consumer&lt;FriendlyByteBuf&gt; 参数，
 * 因此需要在 openMenu 之前通过独立的 S2C 包将岛屿数据传递给客户端，
 * 客户端存入 IslandManagementMenu.pendingData 静态缓冲区，
 * 供菜单客户端构造器读取。
 * </p>
 */
public record OpenIslandPayload(
        int islandX,
        int islandZ,
        String ownerName,
        List<UUID> allowedPlayers,
        Map<UUID, String> onlinePlayers,
        boolean showBorder
) implements CustomPacketPayload {

    public static final Type<OpenIslandPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "open_island"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenIslandPayload> STREAM_CODEC =
            StreamCodec.of(OpenIslandPayload::write, OpenIslandPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, OpenIslandPayload payload) {
        buf.writeInt(payload.islandX);
        buf.writeInt(payload.islandZ);
        buf.writeUtf(payload.ownerName, 64);
        buf.writeInt(payload.allowedPlayers.size());
        for (UUID uuid : payload.allowedPlayers) {
            buf.writeUUID(uuid);
        }
        buf.writeInt(payload.onlinePlayers.size());
        for (Map.Entry<UUID, String> entry : payload.onlinePlayers.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue(), 64);
        }
        buf.writeBoolean(payload.showBorder);
    }

    private static OpenIslandPayload read(RegistryFriendlyByteBuf buf) {
        int islandX = buf.readInt();
        int islandZ = buf.readInt();
        String ownerName = buf.readUtf(64);
        int count = buf.readInt();
        List<UUID> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(buf.readUUID());
        }
        int onlineCount = buf.readInt();
        Map<UUID, String> onlinePlayers = new LinkedHashMap<>(onlineCount);
        for (int i = 0; i < onlineCount; i++) {
            UUID uuid = buf.readUUID();
            String name = buf.readUtf(64);
            onlinePlayers.put(uuid, name);
        }
        boolean showBorder = buf.readBoolean();
        return new OpenIslandPayload(islandX, islandZ, ownerName, players, onlinePlayers, showBorder);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
