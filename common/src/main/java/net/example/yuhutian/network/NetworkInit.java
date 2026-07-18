package net.example.yuhutian.network;

import dev.architectury.networking.NetworkManager;
import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.gui.IslandManagementMenu;
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
                    if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                    // 确保在主线程执行，避免并发修改世界数据
                    player.getServer().execute(() -> {
                        handleAddFriend(player, payload.playerUuid());
                    });
                });

        // 注册 RemoveFriendPayload 接收器
        NetworkManager.registerReceiver(NetworkManager.c2s(),
                RemoveFriendPayload.TYPE, RemoveFriendPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                    player.getServer().execute(() -> {
                        handleRemoveFriend(player, payload.playerUuid());
                    });
                });

        // 注册 ToggleBorderPayload 接收器（切换领地边界粒子墙显示）
        NetworkManager.registerReceiver(NetworkManager.c2s(),
                ToggleBorderPayload.TYPE, ToggleBorderPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                    player.getServer().execute(() -> {
                        handleToggleBorder(player, payload.showBorder());
                    });
                });

        // 注册 UpdateGreetingPayload 接收器（更新欢迎寄语和音效）
        NetworkManager.registerReceiver(NetworkManager.c2s(),
                UpdateGreetingPayload.TYPE, UpdateGreetingPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                    player.getServer().execute(() -> {
                        handleUpdateGreeting(player, payload.greetingText(), payload.greetingSound());
                    });
                });
    }

    /**
     * 注册所有 S2C 网络包接收器（仅客户端调用）。
     * 处理服务端推送的空岛管理面板数据。
     */
    public static void registerS2CPackets() {
        NetworkManager.registerReceiver(NetworkManager.s2c(),
                OpenIslandPayload.TYPE, OpenIslandPayload.STREAM_CODEC,
                (payload, context) -> {
                    IslandManagementMenu.pendingData = new Object[]{
                            payload.islandX(),
                            payload.islandZ(),
                            payload.ownerName(),
                            payload.allowedPlayers(),
                            payload.onlinePlayers(),
                            payload.showBorder(),
                            payload.greetingText(),
                            payload.greetingSound()
                    };
                });
    }

    /**
     * 处理添加好友请求。
     * 客户端直接发送 UUID，无需通过名字查找。
     */
    private static void handleAddFriend(ServerPlayer requester, UUID friendUuid) {
        ServerLevel yuhutianLevel = requester.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
        if (yuhutianLevel == null) return;

        IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);

        // 检查请求者是否拥有空岛
        if (!data.hasIsland(requester.getUUID())) {
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c你还没有空岛！"), false);
            return;
        }

        // 不能添加自己
        if (friendUuid.equals(requester.getUUID())) {
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c不能添加自己为好友！"), false);
            return;
        }

        // 添加到信任列表
        IslandInfo island = data.getIsland(requester.getUUID());
        if (island != null) {
            island.addAllowedPlayer(friendUuid);
            data.setDirty();

            // 尝试获取玩家名字用于提示
            String friendName = friendUuid.toString().substring(0, 8) + "...";
            ServerPlayer friendPlayer = requester.getServer().getPlayerList().getPlayer(friendUuid);
            if (friendPlayer != null) {
                friendName = friendPlayer.getName().getString();
            }
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

    /**
     * 处理领地边界粒子墙显示切换。
     * 仅当玩家拥有空岛时生效。
     */
    private static void handleToggleBorder(ServerPlayer requester, boolean showBorder) {
        ServerLevel yuhutianLevel = requester.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
        if (yuhutianLevel == null) return;

        IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);

        if (!data.hasIsland(requester.getUUID())) return;

        IslandInfo island = data.getIsland(requester.getUUID());
        if (island != null) {
            island.setShowBorder(showBorder);
            data.setDirty();
            String status = showBorder ? "§a已开启领地边界显示。" : "§e已关闭领地边界显示。";
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(status), false);
        }
    }

    /**
     * 处理欢迎寄语与音效更新。
     * 仅当发送者是该空岛 Owner 时生效。
     */
    private static void handleUpdateGreeting(ServerPlayer requester, String greetingText, String greetingSound) {
        ServerLevel yuhutianLevel = requester.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
        if (yuhutianLevel == null) return;

        IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);

        if (!data.hasIsland(requester.getUUID())) {
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c你还没有空岛！"), false);
            return;
        }

        IslandInfo island = data.getIsland(requester.getUUID());
        if (island != null) {
            island.setGreetingText(greetingText);
            island.setGreetingSound(greetingSound);
            data.setDirty();
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§a欢迎寄语已保存。"), false);
        }
    }
}
