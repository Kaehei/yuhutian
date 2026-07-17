package net.example.yuhutian.network;

import dev.architectury.networking.NetworkManager;
import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.world.IslandInfo;
import net.example.yuhutian.world.IslandSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 网络包注册与处理中心。
 * <p>
 * 使用 Architectury NetworkManager 注册 C2S 数据包接收器。
 * 处理添加好友和移除好友的逻辑。
 * </p>
 */
public final class NetworkInit {

    private static final Logger LOGGER = LoggerFactory.getLogger("yuhutian");

    private NetworkInit() {
    }

    /**
     * 注册所有 C2S 网络包接收器。
     * 应在服务端初始化时调用。
     */
    public static void registerC2SPackets() {
        // 注册 AddFriendPayload 接收器
        NetworkManager.registerReceiver(NetworkManager.c2s(),
                AddFriendPayload.TYPE, AddFriendPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = context.getPlayer();
                    if (player == null) return;
                    // 确保在主线程执行，避免并发修改世界数据
                    player.getServer().execute(() -> {
                        handleAddFriend(player, payload.playerName());
                    });
                });

        // 注册 RemoveFriendPayload 接收器
        NetworkManager.registerReceiver(NetworkManager.c2s(),
                RemoveFriendPayload.TYPE, RemoveFriendPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = context.getPlayer();
                    if (player == null) return;
                    player.getServer().execute(() -> {
                        handleRemoveFriend(player, payload.playerUuid());
                    });
                });
    }

    /**
     * 处理添加好友请求。
     */
    private static void handleAddFriend(ServerPlayer requester, String friendName) {
        ServerLevel yuhutianLevel = requester.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
        if (yuhutianLevel == null) return;

        IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);

        // 检查请求者是否拥有空岛
        if (!data.hasIsland(requester.getUUID())) {
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c你还没有空岛！"), false);
            return;
        }

        // 通过名称查找目标玩家
        ServerPlayer friendPlayer = requester.getServer().getPlayerList().getPlayerByName(friendName);
        if (friendPlayer == null) {
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c找不到玩家: " + friendName), false);
            return;
        }

        // 不能添加自己
        if (friendPlayer.getUUID().equals(requester.getUUID())) {
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c不能添加自己为好友！"), false);
            return;
        }

        // 添加到信任列表
        IslandInfo island = data.getIsland(requester.getUUID());
        if (island != null) {
            island.addAllowedPlayer(friendPlayer.getUUID());
            data.setDirty();
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§a已添加 " + friendName + " 到信任列表。"), false);
        }
    }

    /**
     * 处理移除好友请求。
     */
    private static void handleRemoveFriend(ServerPlayer requester, UUID friendUuid) {
        ServerLevel yuhutianLevel = requester.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
        if (yuhutianLevel == null) return;

        IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);

        if (!data.hasIsland(requester.getUUID())) return;

        IslandInfo island = data.getIsland(requester.getUUID());
        if (island != null) {
            island.removeAllowedPlayer(friendUuid);
            data.setDirty();
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§a已从信任列表中移除该玩家。"), false);
        }
    }
}
