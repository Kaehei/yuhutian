package net.example.yuhutian.events;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.world.IslandInfo;
import net.example.yuhutian.world.IslandSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;

/**
 * 领地保护事件监听器。
 * <p>
 * 在玉壶天维度中，拦截所有方块的破坏和交互操作。
 * 仅允许空岛主人或其信任列表中的玩家进行操作，否则拒绝并发送红字提示。
 * </p>
 * <p>
 * Architectury 13.x 移除了 AttackBlockCallback / UseBlockCallback，
 * 改用 InteractionEvent.LEFT_CLICK_BLOCK / RIGHT_CLICK_BLOCK。
 * 回调返回 EventResult（而非 InteractionResult）：
 * pass() = 不干预，interruptFalse() = 取消操作。
 * </p>
 */
public final class IslandProtectionHandler {

    private IslandProtectionHandler() {
    }

    /**
     * 注册所有领地保护事件监听器。
     * 应在模组初始化时调用。
     */
    public static void register() {
        // 拦截方块破坏（左键挖掘）
        InteractionEvent.LEFT_CLICK_BLOCK.register((player, hand, pos, direction) -> {
            Level level = player.level();
            if (!isYuhutianDimension(level)) return EventResult.pass();
            if (level.isClientSide()) return EventResult.pass();
            return checkPermission(player, level, pos) ? EventResult.pass() : EventResult.interruptFalse();
        });

        // 拦截方块交互（右键使用，包括开门、开箱、放置方块等）
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, direction) -> {
            Level level = player.level();
            if (!isYuhutianDimension(level)) return EventResult.pass();
            if (level.isClientSide()) return EventResult.pass();
            return checkPermission(player, level, pos) ? EventResult.pass() : EventResult.interruptFalse();
        });
    }

    /**
     * 检查玩家是否有权在指定位置操作。
     *
     * @return true 表示允许操作，false 表示拒绝
     */
    private static boolean checkPermission(Player player, Level level, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)) return true;
        if (!(level instanceof ServerLevel serverLevel)) return true;

        // 查找该位置所属的空岛
        IslandSavedData data = IslandSavedData.getOrCreate(serverLevel);
        UUID islandOwner = findIslandOwner(data, pos.getX());

        // 如果该位置不属于任何空岛，则不允许操作（保护虚空区域）
        if (islandOwner == null) {
            sendDenyMessage(serverPlayer);
            return false;
        }

        // 检查是否是空岛主人
        if (player.getUUID().equals(islandOwner)) {
            return true;
        }

        // 检查是否在信任列表中
        IslandInfo island = data.getIsland(islandOwner);
        if (island != null && island.isAllowed(player.getUUID())) {
            return true;
        }

        // 无权限，拒绝操作
        sendDenyMessage(serverPlayer);
        return false;
    }

    /**
     * 根据 X 坐标查找所属空岛的主人 UUID。
     * 空岛以 index * 1000 为中心，占据 [centerX - 500, centerX + 500) 范围。
     */
    private static UUID findIslandOwner(IslandSavedData data, int x) {
        for (Map.Entry<UUID, IslandInfo> entry : data.getAllIslands().entrySet()) {
            IslandInfo info = entry.getValue();
            if (x >= info.getX() - 500 && x < info.getX() + 500) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean isYuhutianDimension(Level level) {
        return level.dimension().equals(YuhutianDimension.YUHUTIAN_LEVEL);
    }

    private static void sendDenyMessage(ServerPlayer player) {
        player.displayClientMessage(
                Component.literal("§c你没有该空岛的交互权限！"), false);
    }
}
