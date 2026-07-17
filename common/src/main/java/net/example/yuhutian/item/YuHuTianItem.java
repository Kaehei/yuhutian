package net.example.yuhutian.item;

import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.world.IslandGenerator;
import net.example.yuhutian.world.IslandInfo;
import net.example.yuhutian.world.IslandSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 玉壶天物品 —— 右键使用后将玩家传送至 "玉壶天" 虚空维度的专属空岛。
 * <p>
 * 完整逻辑流程：
 * <ol>
 *   <li>检查玩家是否已分配空岛</li>
 *   <li>若未分配，为其分配新坐标并持久化</li>
 *   <li>若岛屿为新建（isNew == true），生成结构并召唤 NPC</li>
 *   <li>传送至该玩家的空岛坐标</li>
 * </ol>
 * </p>
 */
public class YuHuTianItem extends Item {

    private static final double TELEPORT_Y = 65.0;

    public YuHuTianItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ServerLevel targetLevel = serverPlayer.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);

            if (targetLevel != null) {
                // 获取该维度的空岛持久化数据
                IslandSavedData data = IslandSavedData.getOrCreate(targetLevel);

                // 查询或分配空岛
                boolean newlyAllocated = false;
                IslandInfo island;
                if (data.hasIsland(serverPlayer.getUUID())) {
                    island = data.getIsland(serverPlayer.getUUID());
                    if (island == null) {
                        return InteractionResultHolder.pass(stack); // 数据异常保护
                    }
                } else {
                    island = data.allocateIsland(serverPlayer.getUUID());
                    newlyAllocated = true;
                    data.setDirty();
                }

                // 如果是新分配的岛屿，生成结构并召唤 NPC
                if (newlyAllocated && island.isNew()) {
                    IslandGenerator.generate(targetLevel, island.getX(), island.getZ());
                    island.setNew(false);
                    data.setDirty();
                }

                double targetX = island.getX() + 0.5;
                double targetZ = island.getZ() + 0.5;

                // 在传送前播放瞬移音效（在玩家当前位置）
                level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

                // 1.21.1 跨维度传送
                serverPlayer.teleportTo(targetLevel, targetX, TELEPORT_Y, targetZ,
                        serverPlayer.getYRot(), serverPlayer.getXRot());

                // 在目标位置播放到达音效
                targetLevel.playSound(null, targetX, TELEPORT_Y, targetZ,
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

                return InteractionResultHolder.success(stack);
            }
        }

        return InteractionResultHolder.pass(stack);
    }
}
