package net.example.yuhutian.item;

import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.world.IslandGenerator;
import net.example.yuhutian.world.IslandInfo;
import net.example.yuhutian.world.IslandSavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
 *   <li>如果玩家在玉壶天维度内，再次使用则返回主世界原位置</li>
 *   <li>检查玩家是否已分配空岛</li>
 *   <li>若未分配，为其分配新坐标并持久化</li>
 *   <li>若岛屿为新建（isNew == true），生成结构并召唤 NPC</li>
 *   <li>保存当前主世界位置，传送至该玩家的空岛坐标</li>
 * </ol>
 * </p>
 */
public class YuHuTianItem extends Item {

    private static final double TELEPORT_Y = 106.0;

    public YuHuTianItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ServerLevel yuhutianLevel = serverPlayer.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
            if (yuhutianLevel == null) {
                return InteractionResultHolder.pass(stack);
            }

            IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);

            // ========== 在玉壶天维度内：返回主世界 ==========
            if (level.dimension().equals(YuhutianDimension.YUHUTIAN_LEVEL)) {
                IslandSavedData.ReturnPosition returnPos = data.getReturnPosition(serverPlayer.getUUID());
                if (returnPos == null) {
                    serverPlayer.displayClientMessage(
                            Component.literal("§c找不到返回位置，请重新从主世界使用玉壶天进入。"), false);
                    return InteractionResultHolder.pass(stack);
                }

                // 查找目标维度
                ResourceKey<Level> targetDim = ResourceKey.create(Registries.DIMENSION, returnPos.dimension());
                ServerLevel targetLevel = serverPlayer.getServer().getLevel(targetDim);
                if (targetLevel == null) {
                    // 维度不存在则返回主世界
                    targetLevel = serverPlayer.getServer().overworld();
                }

                // 播放离开音效
                level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

                // 传送回原位置
                serverPlayer.teleportTo(targetLevel, returnPos.x(), returnPos.y(), returnPos.z(),
                        returnPos.yaw(), returnPos.pitch());

                // 播放到达音效
                targetLevel.playSound(null, returnPos.x(), returnPos.y(), returnPos.z(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

                // 清除保存的返回位置
                data.removeReturnPosition(serverPlayer.getUUID());
                data.setDirty();

                return InteractionResultHolder.success(stack);
            }

            // ========== 在主世界（或其他维度）：传送到玉壶天 ==========

            // 保存当前玩家位置作为返回点
            IslandSavedData.ReturnPosition returnPos = new IslandSavedData.ReturnPosition(
                    level.dimension().location(),
                    serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    serverPlayer.getYRot(), serverPlayer.getXRot()
            );
            data.setReturnPosition(serverPlayer.getUUID(), returnPos);

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
            }

            // 如果是新分配的岛屿，生成结构并召唤 NPC
            if (newlyAllocated && island.isNew()) {
                IslandGenerator.generate(yuhutianLevel, island.getX(), island.getZ());
                island.setNew(false);
            }

            data.setDirty();

            double targetX = island.getX() + 0.5;
            double targetZ = island.getZ() + 0.5;

            // 播放离开音效
            level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 跨维度传送
            serverPlayer.teleportTo(yuhutianLevel, targetX, TELEPORT_Y, targetZ,
                    serverPlayer.getYRot(), serverPlayer.getXRot());

            // 播放到达音效
            yuhutianLevel.playSound(null, targetX, TELEPORT_Y, targetZ,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 提示玩家如何返回
            serverPlayer.displayClientMessage(
                    Component.literal("§a已到达空岛！再次右键玉壶天可返回原处。"), false);

            // ===== 入场欢迎仪式：仅在岛主开启时触发 =====
            if (island.isEnableGreeting()) {
                playGreetingCeremony(serverPlayer, island);
            }

            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    /**
     * 入场欢迎仪式：向被传送的玩家发送 Title 和自定义音效。
     *
     * @param player 被传送的玩家
     * @param island 目标空岛信息
     */
    public static void playGreetingCeremony(ServerPlayer player, IslandInfo island) {
        // 1. 发送 Title 动画时序：淡入 10 tick，停留 40 tick，淡出 20 tick
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 20));

        // 2. 发送主标题（欢迎寄语文本）
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("§6" + island.getGreetingText())));

        // 3. 发送副标题（空岛坐标）
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("§7空岛 #" + island.getIndex()
                        + " (" + island.getX() + ", " + island.getZ() + ")")));

        // 4. 播放自定义音效
        ResourceLocation soundId = ResourceLocation.parse(island.getGreetingSound());
        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundId);
        if (soundEvent == null) soundEvent = SoundEvents.PLAYER_LEVELUP;
        player.playNotifySound(soundEvent, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
