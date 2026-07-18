package net.example.yuhutian.network;

import dev.architectury.networking.NetworkManager;
import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.events.WelcomeTriggerManager;
import net.example.yuhutian.gui.IslandManagementMenu;
import net.example.yuhutian.item.YuHuTianItem;
import net.example.yuhutian.world.IslandGenerator;
import net.example.yuhutian.world.IslandInfo;
import net.example.yuhutian.world.IslandSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        // 注册 ToggleGreetingPayload 接收器（切换欢迎仪式开关）
        NetworkManager.registerReceiver(NetworkManager.c2s(),
                ToggleGreetingPayload.TYPE, ToggleGreetingPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                    player.getServer().execute(() -> {
                        handleToggleGreeting(player, payload.enableGreeting());
                    });
                });

        // 注册 RequestVisitableIslandsPayload 接收器（请求可拜访的空岛列表）
        NetworkManager.registerReceiver(NetworkManager.c2s(),
                RequestVisitableIslandsPayload.TYPE, RequestVisitableIslandsPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                    player.getServer().execute(() -> {
                        handleRequestVisitableIslands(player);
                    });
                });

        // 注册 TeleportToIslandPayload 接收器（传送到指定空岛）
        NetworkManager.registerReceiver(NetworkManager.c2s(),
                TeleportToIslandPayload.TYPE, TeleportToIslandPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                    player.getServer().execute(() -> {
                        handleTeleportToIsland(player, payload.targetOwnerUuid());
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
                            payload.enableGreeting(),
                            payload.greetingText(),
                            payload.greetingSound(),
                            payload.trustedPlayerNames()
                    };
                });

        // 注册可拜访空岛列表 S2C 包接收器
        NetworkManager.registerReceiver(NetworkManager.s2c(),
                SyncVisitableIslandsPayload.TYPE, SyncVisitableIslandsPayload.STREAM_CODEC,
                (payload, context) -> {
                    net.example.yuhutian.gui.IslandManagementScreen.visitPendingData = payload.entries();
                });

        // 注册信任玩家列表 S2C 包接收器（实时刷新领地管理标签页）
        NetworkManager.registerReceiver(NetworkManager.s2c(),
                SyncTrustedPlayersPayload.TYPE, SyncTrustedPlayersPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.getPlayer() instanceof net.minecraft.client.player.LocalPlayer) {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc.player != null && mc.player.containerMenu
                                instanceof net.example.yuhutian.gui.IslandManagementMenu menu) {
                            menu.updateTrustedData(payload.allowedPlayers(), payload.trustedNames());
                        }
                    }
                    net.example.yuhutian.gui.IslandManagementScreen.refreshFromServer();
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

            // 获取名字用于提示
            String friendName = friendUuid.toString().substring(0, 8) + "...";
            ServerPlayer friendPlayer = requester.getServer().getPlayerList().getPlayer(friendUuid);
            if (friendPlayer != null) {
                friendName = friendPlayer.getName().getString();
            }
            requester.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§a已添加 " + friendName + " 到信任列表。"), false);

            // 立即 S2C 同步最新信任列表（含名字）
            sendTrustedSync(requester, island, data);
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

            // 立即 S2C 同步最新信任列表
            sendTrustedSync(requester, island, data);
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

    /**
     * 处理欢迎仪式开关切换。
     * 仅当发送者是该空岛 Owner 时生效。
     */
    private static void handleToggleGreeting(ServerPlayer requester, boolean enableGreeting) {
        ServerLevel yuhutianLevel = requester.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
        if (yuhutianLevel == null) return;

        IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);

        if (!data.hasIsland(requester.getUUID())) return;

        IslandInfo island = data.getIsland(requester.getUUID());
        if (island != null) {
            island.setEnableGreeting(enableGreeting);
            data.setDirty();
            String status = enableGreeting ? "§a欢迎仪式已开启。" : "§e欢迎仪式已关闭。";
            requester.displayClientMessage(
                    Component.literal(status), false);
        }
    }

    /**
     * 处理可拜访空岛列表请求。
     * 筛选出请求者拥有或被信任的所有空岛，将列表通过 S2C 包发回客户端。
     */
    private static void handleRequestVisitableIslands(ServerPlayer requester) {
        ServerLevel yuhutianLevel = requester.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
        if (yuhutianLevel == null) return;

        IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);
        List<SyncVisitableIslandsPayload.IslandEntry> entries = new ArrayList<>();

        for (Map.Entry<UUID, IslandInfo> entry : data.getAllIslands().entrySet()) {
            UUID ownerUuid = entry.getKey();
            IslandInfo info = entry.getValue();

            // 筛选：请求者是岛主或在信任列表中
            if (ownerUuid.equals(requester.getUUID()) || info.isAllowed(requester.getUUID())) {
                // 解析岛主名称（在线 + 离线 ProfileCache）
                String ownerName;
                ServerPlayer ownerPlayer = requester.getServer().getPlayerList().getPlayer(ownerUuid);
                if (ownerPlayer != null) {
                    ownerName = ownerPlayer.getName().getString();
                } else {
                    ownerName = requester.getServer().getProfileCache()
                            .get(ownerUuid)
                            .map(profile -> profile.getName())
                            .orElse(ownerUuid.toString().substring(0, 8) + "...");
                }
                entries.add(new SyncVisitableIslandsPayload.IslandEntry(
                        ownerName, info.getIndex(), ownerUuid));
            }
        }

        NetworkManager.sendToPlayer(requester, new SyncVisitableIslandsPayload(entries));
    }

    /**
     * 处理跨空岛传送请求。
     * 服务端重新验证权限后执行传送，并触发入场欢迎仪式。
     */
    private static void handleTeleportToIsland(ServerPlayer requester, UUID targetOwnerUuid) {
        ServerLevel yuhutianLevel = requester.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
        if (yuhutianLevel == null) return;

        IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);
        IslandInfo targetIsland = data.getIsland(targetOwnerUuid);

        if (targetIsland == null) {
            requester.displayClientMessage(
                    Component.literal("§c该空岛不存在。"), false);
            return;
        }

        // 权限二次验证：必须是岛主或在信任列表中
        if (!targetOwnerUuid.equals(requester.getUUID()) && !targetIsland.isAllowed(requester.getUUID())) {
            requester.displayClientMessage(
                    Component.literal("§c你没有拜访该空岛的权限！"), false);
            return;
        }

        // 确保目标空岛已生成结构
        if (targetIsland.isNew()) {
            IslandGenerator.generate(yuhutianLevel, targetIsland.getX(), targetIsland.getZ());
            targetIsland.setNew(false);
            data.setDirty();
        }

        // 如果玩家不在玉壶天维度，保存当前位置作为返回点
        if (!requester.level().dimension().equals(YuhutianDimension.YUHUTIAN_LEVEL)) {
            Level currentLevel = requester.level();
            IslandSavedData.ReturnPosition returnPos = new IslandSavedData.ReturnPosition(
                    currentLevel.dimension().location(),
                    requester.getX(), requester.getY(), requester.getZ(),
                    requester.getYRot(), requester.getXRot()
            );
            data.setReturnPosition(requester.getUUID(), returnPos);
        }

        double targetX = targetIsland.getX() + 0.5;
        double targetZ = targetIsland.getZ() + 0.5;
        double targetY = 106.0;

        // 播放离开音效
        requester.level().playSound(null, requester.getX(), requester.getY(), requester.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 跨维度传送
        requester.teleportTo(yuhutianLevel, targetX, targetY, targetZ,
                requester.getYRot(), requester.getXRot());

        // 播放到达音效
        yuhutianLevel.playSound(null, targetX, targetY, targetZ,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 确保 NPC 存在（如果被打死则自动重生）
        IslandGenerator.ensureNpcExists(yuhutianLevel, targetIsland.getX(), targetIsland.getZ());

        // 入场欢迎仪式：注册延迟触发（等待客户端加载完毕后再发送 Title + 音效）
        WelcomeTriggerManager.registerDelayedWelcome(requester,
                targetIsland.getGreetingText(), targetIsland.getGreetingSound(), targetIsland.isEnableGreeting());

        requester.displayClientMessage(
                Component.literal("§a已传送到空岛！再次右键玉壶天可返回原处。"), false);
    }

    /**
     * 解析所有信任玩家的名字并发送 S2C 同步包。
     * 优先使用在线玩家名，离线玩家通过 ProfileCache 查询。
     */
    private static void sendTrustedSync(ServerPlayer requester, IslandInfo island, IslandSavedData data) {
        List<UUID> allowed = new ArrayList<>(island.getAllowedPlayers());
        Map<UUID, String> names = new LinkedHashMap<>();
        for (UUID uuid : allowed) {
            // 优先查在线玩家
            ServerPlayer online = requester.getServer().getPlayerList().getPlayer(uuid);
            if (online != null) {
                names.put(uuid, online.getName().getString());
            } else {
                // 离线玩家通过 ProfileCache 查询
                requester.getServer().getProfileCache().get(uuid).ifPresentOrElse(
                        profile -> names.put(uuid, profile.getName()),
                        () -> names.put(uuid, uuid.toString().substring(0, 8) + "...")
                );
            }
        }
        NetworkManager.sendToPlayer(requester,
                new SyncTrustedPlayersPayload(allowed, names));
    }
}
