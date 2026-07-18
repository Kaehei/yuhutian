package net.example.yuhutian.events;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.item.YuHuTianItem;
import net.example.yuhutian.world.IslandInfo;
import net.example.yuhutian.world.IslandSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 入场欢迎延迟触发管理器。
 * <p>
 * 当玩家传送到玉壶天维度时，不立即发送 Title 和音效数据包，
 * 而是将任务登记到延迟队列中。通过服务端 PlayerTick 事件倒计时，
 * 等客户端完成维度切换和区块加载（约 20 刻 = 1 秒）后再正式触发。
 * </p>
 * <p>
 * 统一拦截所有入场途径：物品右键、GUI 列表拜访、{@code /tp} 指令、
 * 以及维度内跨岛传送。
 * </p>
 */
public final class WelcomeTriggerManager {

    /** 延迟 Tick 数（20 ticks = 1 秒），确保客户端完全加载完毕 */
    private static final int DEFAULT_DELAY = 20;

    /** 跨岛传送的最小距离阈值（方块），超过即视为传送 */
    private static final double TELEPORT_THRESHOLD = 100.0;

    /** 待触发的欢迎任务队列 */
    private static final Map<UUID, WelcomeTask> pendingTasks = new ConcurrentHashMap<>();

    /** 玩家在玉壶天维度的上次已知位置（用于检测维度内传送） */
    private static final Map<UUID, Vec3> lastYuhutianPos = new ConcurrentHashMap<>();

    private WelcomeTriggerManager() {}

    // ==================== 注册与 API ====================

    /**
     * 注册所有事件监听器。
     * 应在模组初始化时调用。
     */
    public static void register() {
        TickEvent.PLAYER.register(WelcomeTriggerManager::onPlayerTick);
        PlayerEvent.PLAYER_QUIT.register(WelcomeTriggerManager::onPlayerDisconnect);
    }

    /**
     * 登记一个延迟欢迎任务。
     * <p>
     * 如果该玩家已有待触发的任务，会被新任务覆盖。
     * 内部会自动检查 {@code enableGreeting} 开关，关闭时不登记。
     * </p>
     *
     * @param player       目标玩家
     * @param greetingText 欢迎寄语文本
     * @param greetingSound 音效 ResourceLocation 字符串
     * @param enableGreeting 岛主是否开启了欢迎仪式
     */
    public static void registerDelayedWelcome(
            ServerPlayer player, String greetingText, String greetingSound, boolean enableGreeting) {
        if (!enableGreeting) return;
        pendingTasks.put(player.getUUID(),
                new WelcomeTask(greetingText, greetingSound, DEFAULT_DELAY));
    }

    // ==================== Tick 处理 ====================

    /**
     * 每个玩家 Tick 调用一次（服务端 + 客户端均触发，内部过滤 ServerPlayer）。
     * <ol>
     *   <li>倒计时并触发待执行的欢迎任务</li>
     *   <li>检测玩家进入玉壶天维度或维度内传送，自动登记新任务</li>
     *   <li>玩家离开维度时清理跟踪数据</li>
     * </ol>
     */
    private static void onPlayerTick(Player playerEntity) {
        if (!(playerEntity instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        UUID uuid = player.getUUID();

        // 1. 倒计时并触发待执行的欢迎任务
        WelcomeTask task = pendingTasks.get(uuid);
        if (task != null) {
            task.ticksLeft--;
            if (task.ticksLeft <= 0) {
                pendingTasks.remove(uuid);
                YuHuTianItem.playGreetingCeremony(player, task.greetingText, task.greetingSound);
            }
        }

        // 2. 检测玩家进入玉壶天维度或维度内传送
        ServerLevel yuhutianLevel = player.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
        if (yuhutianLevel == null) return;

        boolean inYuhutian = player.level().dimension().equals(YuhutianDimension.YUHUTIAN_LEVEL);

        if (inYuhutian) {
            Vec3 currentPos = player.position();
            Vec3 lastPos = lastYuhutianPos.get(uuid);

            if (lastPos == null) {
                // 玩家刚进入玉壶天维度 → 自动检测最近空岛并登记欢迎
                autoRegisterForNearestIsland(player, yuhutianLevel);
            } else if (lastPos.distanceTo(currentPos) > TELEPORT_THRESHOLD) {
                // 维度内长距离位移 → 跨岛传送检测
                autoRegisterForNearestIsland(player, yuhutianLevel);
            }

            lastYuhutianPos.put(uuid, currentPos);
        } else {
            // 玩家不在玉壶天维度 → 清理跟踪数据
            lastYuhutianPos.remove(uuid);
        }
    }

    // ==================== 自动检测 ====================

    /**
     * 根据玩家当前 X 坐标查找最近的空岛，
     * 如果岛主开启了欢迎仪式，则自动登记延迟欢迎任务。
     */
    private static void autoRegisterForNearestIsland(ServerPlayer player, ServerLevel yuhutianLevel) {
        IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);
        int playerX = (int) player.getX();

        for (Map.Entry<UUID, IslandInfo> entry : data.getAllIslands().entrySet()) {
            IslandInfo info = entry.getValue();
            // 空岛占据 [centerX - 500, centerX + 500) 范围
            if (playerX >= info.getX() - 500 && playerX < info.getX() + 500) {
                registerDelayedWelcome(player,
                        info.getGreetingText(),
                        info.getGreetingSound(),
                        info.isEnableGreeting());
                return;
            }
        }
    }

    // ==================== 断连清理 ====================

    /**
     * 玩家断开连接时清理所有跟踪数据，防止内存泄漏。
     */
    private static void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        pendingTasks.remove(uuid);
        lastYuhutianPos.remove(uuid);
    }

    // ==================== 内部数据结构 ====================

    /**
     * 待触发的欢迎任务。
     */
    private static class WelcomeTask {
        final String greetingText;
        final String greetingSound;
        int ticksLeft;

        WelcomeTask(String greetingText, String greetingSound, int ticksLeft) {
            this.greetingText = greetingText;
            this.greetingSound = greetingSound;
            this.ticksLeft = ticksLeft;
        }
    }
}
