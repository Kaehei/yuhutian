package net.example.yuhutian.entity;

import dev.architectury.networking.NetworkManager;
import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.gui.IslandManagementMenu;
import net.example.yuhutian.network.OpenIslandPayload;
import net.example.yuhutian.world.IslandInfo;
import net.example.yuhutian.world.IslandSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 空岛 NPC 实体。
 * <p>
 * 继承自 {@link PathfinderMob}，拥有基础的注视 AI（看向玩家 + 随机转头），
 * 但不具备移动 AI，因此会始终站在原地。
 * </p>
 * <p>
 * 右键交互：当空岛主人右键点击 NPC 时，打开空岛管理面板 GUI。
 * 1.21.1 中 Mob.interact 为 final，须覆写 mobInteract。
 * </p>
 */
public class IslandNPCEntity extends PathfinderMob {

    /** NPC 的家园坐标，掉入虚空时传送回此位置 */
    private double homeX = Double.NaN;
    private double homeY = Double.NaN;
    private double homeZ = Double.NaN;

    public IslandNPCEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        super.moveTo(x, y, z, yRot, xRot);
        if (Double.isNaN(homeX)) {
            homeX = x;
            homeY = y;
            homeZ = z;
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 虚空保护：Y 低于 50 时传送回生成位置
        if (!this.level().isClientSide() && this.getY() < 50.0 && !Double.isNaN(homeX)) {
            this.teleportTo(homeX, homeY, homeZ);
            this.setDeltaMovement(0, 0, 0);
            this.fallDistance = 0;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!Double.isNaN(homeX)) {
            tag.putDouble("HomeX", homeX);
            tag.putDouble("HomeY", homeY);
            tag.putDouble("HomeZ", homeZ);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) {
            homeX = tag.getDouble("HomeX");
            homeY = tag.getDouble("HomeY");
            homeZ = tag.getDouble("HomeZ");
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    /**
     * 右键交互处理。
     * 1.21.1 中 Mob.interact() 为 final，覆写 mobInteract() 代替。
     * 仅当交互玩家是该空岛的主人时，才打开管理面板 GUI。
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        // 查找当前 NPC 所在空岛的主人
        ServerLevel yuhutianLevel = serverPlayer.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
        if (yuhutianLevel == null || this.level() != yuhutianLevel) {
            return InteractionResult.PASS;
        }

        int npcX = (int) this.getX();
        IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);

        // 根据 NPC 位置查找所属空岛
        UUID ownerUuid = findIslandOwner(data, npcX);
        if (ownerUuid == null) {
            return InteractionResult.PASS;
        }

        // 检查交互者是否是空岛主人
        if (!player.getUUID().equals(ownerUuid)) {
            player.displayClientMessage(
                    Component.literal("§c你不是这个空岛的主人，无法打开管理面板。"), false);
            return InteractionResult.FAIL;
        }

        // 查找空岛信息
        IslandInfo island = data.getIsland(ownerUuid);
        if (island == null) {
            return InteractionResult.PASS;
        }

        // 1.21.1: openMenu 不再支持 Consumer<FriendlyByteBuf>，
        // 因此先通过 S2C 包将岛屿数据同步到客户端
        String ownerName = "Unknown";
        ServerPlayer ownerPlayer = serverPlayer.getServer().getPlayerList().getPlayer(ownerUuid);
        if (ownerPlayer != null) {
            ownerName = ownerPlayer.getName().getString();
        }

        // 构建在线玩家列表（排除岛主本人）
        Map<UUID, String> onlinePlayers = new LinkedHashMap<>();
        for (ServerPlayer onlinePlayer : serverPlayer.getServer().getPlayerList().getPlayers()) {
            if (!onlinePlayer.getUUID().equals(ownerUuid)) {
                onlinePlayers.put(onlinePlayer.getUUID(), onlinePlayer.getName().getString());
            }
        }

        List<UUID> allowedList = new ArrayList<>(island.getAllowedPlayers());

        // 解析信任玩家名字（在线 + 离线 ProfileCache）
        Map<UUID, String> trustedNames = new LinkedHashMap<>();
        for (UUID uuid : allowedList) {
            ServerPlayer online = serverPlayer.getServer().getPlayerList().getPlayer(uuid);
            if (online != null) {
                trustedNames.put(uuid, online.getName().getString());
            } else {
                serverPlayer.getServer().getProfileCache().get(uuid).ifPresentOrElse(
                        profile -> trustedNames.put(uuid, profile.getName()),
                        () -> trustedNames.put(uuid, uuid.toString().substring(0, 8) + "...")
                );
            }
        }

        NetworkManager.sendToPlayer(serverPlayer,
                new OpenIslandPayload(island.getX(), island.getZ(), ownerName, allowedList, onlinePlayers,
                        island.isShowBorder(), island.isEnableGreeting(),
                        island.getGreetingText(), island.getGreetingSound(), trustedNames));

        // 打开管理面板 GUI（无额外 buffer 数据）
        serverPlayer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("空岛管理面板");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                return new IslandManagementMenu(containerId, menuPlayer,
                        island.getX(), island.getZ());
            }
        });

        return InteractionResult.CONSUME;
    }

    /**
     * 根据 NPC 的 X 坐标查找所属空岛的主人 UUID。
     */
    private UUID findIslandOwner(IslandSavedData data, int npcX) {
        for (Map.Entry<UUID, IslandInfo> entry : data.getAllIslands().entrySet()) {
            IslandInfo info = entry.getValue();
            // 空岛占据 [centerX - 500, centerX + 500) 范围
            if (npcX >= info.getX() - 500 && npcX < info.getX() + 500) {
                return entry.getKey();
            }
        }
        return null;
    }
}
