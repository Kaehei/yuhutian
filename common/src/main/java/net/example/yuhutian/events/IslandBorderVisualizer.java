package net.example.yuhutian.events;

import org.joml.Vector3f;
import dev.architectury.event.events.common.TickEvent;
import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.world.IslandInfo;
import net.example.yuhutian.world.IslandSavedData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 领地边界粒子墙可视化处理器。
 * <p>
 * 通过服务端 Tick 事件周期性检查玉壶天维度中的玩家，
 * 当玩家开启了 showBorder 且靠近领地边界时，在该玩家附近
 * 的边界线段上生成青色发光粒子，形成半透明的"粒子墙"效果。
 * </p>
 * <p>
 * 领地大小为 16×16 区块（256×256 格），边界距中心 ±128 格。
 * 仅在玩家周围 24 格内的边界线段上生成粒子，避免全图扫描的性能开销。
 * 每 8 个 Tick 处理一次（约 2.5 次/秒），防止服务端卡顿。
 * </p>
 */
public final class IslandBorderVisualizer {

    /** 领地边界距中心的格数（16 chunks × 16 blocks / 2 = 128） */
    private static final int BORDER_HALF_SIZE = 128;

    /** 粒子在玩家周围多远范围内可见 */
    private static final int VIEW_RADIUS = 24;

    /** 每隔多少 Tick 处理一次 */
    private static final int TICK_INTERVAL = 8;

    /** 粒子垂直范围：玩家 Y 坐标向下偏移 */
    private static final int Y_OFFSET_DOWN = 5;

    /** 粒子垂直范围：玩家 Y 坐标向上偏移 */
    private static final int Y_OFFSET_UP = 10;

    /** 粒子在边界线上的间距（每隔 N 格一个粒子） */
    private static final int PARTICLE_SPACING = 2;

    /** 粒子颜色：青色（RGB: 0.3, 0.8, 1.0） */
    private static final Vector3f BORDER_COLOR = new Vector3f(0.3f, 0.8f, 1.0f);

    /** 粒子大小 */
    private static final float PARTICLE_SCALE = 1.0f;

    private static int tickCounter = 0;

    private IslandBorderVisualizer() {
    }

    /**
     * 注册 Tick 事件监听器。
     * 使用 Architectury 的 SERVER_LEVEL_PRE 事件，在每个 ServerLevel tick 前触发。
     */
    public static void register() {
        TickEvent.SERVER_LEVEL_PRE.register(IslandBorderVisualizer::onServerLevelTick);
    }

    /**
     * 每个 ServerLevel tick 的回调。
     * 仅处理玉壶天维度，按 TICK_INTERVAL 控制频率。
     */
    private static void onServerLevelTick(ServerLevel level) {
        if (!level.dimension().equals(YuhutianDimension.YUHUTIAN_LEVEL)) return;

        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        IslandSavedData data = IslandSavedData.getOrCreate(level);

        for (ServerPlayer player : level.players()) {
            processPlayer(player, data);
        }
    }

    /**
     * 处理单个玩家的边界粒子生成。
     * <p>
     * 逻辑流程：
     * 1. 查找该玩家拥有的空岛（通过遍历所有岛屿匹配 UUID）
     * 2. 检查 showBorder 是否为 true
     * 3. 计算四条边界线的世界坐标
     * 4. 判断玩家与每条边界线的距离，仅处理 24 格以内的边界
     * 5. 在玩家附近的边界线段上，沿 Y 轴每隔 2 格生成粒子
     * </p>
     */
    private static void processPlayer(ServerPlayer player, IslandSavedData data) {
        UUID playerUuid = player.getUUID();

        // 查找该玩家拥有的空岛
        IslandInfo island = data.getIsland(playerUuid);
        if (island == null || !island.isShowBorder()) return;

        int centerX = island.getX();
        int centerZ = island.getZ();
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        // 计算四条边界线的世界坐标
        int minX = centerX - BORDER_HALF_SIZE;  // 西边界
        int maxX = centerX + BORDER_HALF_SIZE;  // 东边界
        int minZ = centerZ - BORDER_HALF_SIZE;  // 北边界
        int maxZ = centerZ + BORDER_HALF_SIZE;  // 南边界

        // 检查玩家是否在领地附近（宽松判定，超出 24 格跳过）
        boolean nearWest = Math.abs(px - minX) <= VIEW_RADIUS;
        boolean nearEast = Math.abs(px - maxX) <= VIEW_RADIUS;
        boolean nearNorth = Math.abs(pz - minZ) <= VIEW_RADIUS;
        boolean nearSouth = Math.abs(pz - maxZ) <= VIEW_RADIUS;

        if (!nearWest && !nearEast && !nearNorth && !nearSouth) return;

        // Y 轴范围：玩家脚下 5 格到头顶 10 格
        int yBottom = (int) Math.floor(py) - Y_OFFSET_DOWN;
        int yTop = (int) Math.floor(py) + Y_OFFSET_UP;

        // 西边界（X = minX，Z 轴方向）
        if (nearWest) {
            spawnParticlesAlongLine(player, minX, yBottom, yTop,
                    Math.max(pz - VIEW_RADIUS, minZ),
                    Math.min(pz + VIEW_RADIUS, maxZ),
                    true);
        }

        // 东边界（X = maxX，Z 轴方向）
        if (nearEast) {
            spawnParticlesAlongLine(player, maxX, yBottom, yTop,
                    Math.max(pz - VIEW_RADIUS, minZ),
                    Math.min(pz + VIEW_RADIUS, maxZ),
                    true);
        }

        // 北边界（Z = minZ，X 轴方向）
        if (nearNorth) {
            spawnParticlesAlongLine(player, minZ, yBottom, yTop,
                    Math.max(px - VIEW_RADIUS, minX),
                    Math.min(px + VIEW_RADIUS, maxX),
                    false);
        }

        // 南边界（Z = maxZ，X 轴方向）
        if (nearSouth) {
            spawnParticlesAlongLine(player, maxZ, yBottom, yTop,
                    Math.max(px - VIEW_RADIUS, minX),
                    Math.min(px + VIEW_RADIUS, maxX),
                    false);
        }
    }

    /**
     * 沿一条边界线段生成粒子。
     *
     * @param player     目标玩家（仅向该玩家发送粒子）
     * @param fixedCoord 边界线的固定坐标（X 或 Z，取决于 isZDirection）
     * @param yBottom    粒子生成范围的 Y 轴下限
     * @param yTop       粒子生成范围的 Y 轴上限
     * @param rangeStart 沿线方向的起始坐标（玩家附近）
     * @param rangeEnd   沿线方向的结束坐标（玩家附近）
     * @param isZDirection true = 边界线沿 Z 轴方向（X 固定），false = 沿 X 轴方向（Z 固定）
     */
    private static void spawnParticlesAlongLine(
            ServerPlayer player,
            int fixedCoord, int yBottom, int yTop,
            double rangeStart, double rangeEnd,
            boolean isZDirection) {

        ServerLevel level = (ServerLevel) player.level();
        DustParticleOptions options = new DustParticleOptions(BORDER_COLOR, PARTICLE_SCALE);

        // 沿边界线方向，每隔 PARTICLE_SPACING 格生成一列粒子
        for (double along = Math.ceil(rangeStart / PARTICLE_SPACING) * PARTICLE_SPACING;
             along <= rangeEnd;
             along += PARTICLE_SPACING) {

            // 沿 Y 轴，每隔 PARTICLE_SPACING 格生成一个粒子
            for (int y = yBottom; y <= yTop; y += PARTICLE_SPACING) {
                double x, z;
                if (isZDirection) {
                    x = fixedCoord + 0.5;
                    z = along;
                } else {
                    x = along;
                    z = fixedCoord + 0.5;
                }

                // 向指定玩家发送粒子（force=false，不强制加载区块）
                // count=1, dx/dy/dz=0（不扩散），speed=0（静止）
                level.sendParticles(player, options, false,
                        x, y + 0.5, z,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}
