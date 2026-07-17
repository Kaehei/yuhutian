package net.example.yuhutian.gui;

import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.world.IslandInfo;
import net.example.yuhutian.world.IslandSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 空岛管理面板的容器菜单。
 * <p>
 * 不包含物品栏格子，是一个纯功能性面板。
 * 持有当前空岛的基本信息和信任玩家列表，供客户端 Screen 渲染使用。
 * </p>
 */
public class IslandManagementMenu extends AbstractContainerMenu {

    private final int islandX;
    private final int islandZ;
    private final String ownerName;
    private final List<UUID> allowedPlayers;

    /**
     * 服务端构造：由 NPC 交互触发。
     */
    public IslandManagementMenu(int containerId, Player player, int islandX, int islandZ) {
        super(ModMenuTypes.ISLAND_MANAGEMENT.get(), containerId);
        this.islandX = islandX;
        this.islandZ = islandZ;

        String name = "Unknown";
        if (player instanceof ServerPlayer sp) {
            ServerLevel yuhutianLevel = sp.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
            if (yuhutianLevel != null) {
                IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);
                for (Map.Entry<UUID, IslandInfo> entry : data.getAllIslands().entrySet()) {
                    if (entry.getValue().getX() == islandX) {
                        ServerPlayer ownerPlayer = sp.getServer().getPlayerList().getPlayer(entry.getKey());
                        if (ownerPlayer != null) {
                            name = ownerPlayer.getName().getString();
                        }
                        break;
                    }
                }
            }
        }
        this.ownerName = name;
        this.allowedPlayers = getAllowedPlayersForIsland(player, islandX);
    }

    /**
     * 客户端构造：从 FriendlyByteBuf 反序列化。
     * <p>
     * 注意：1.21.1 的 openMenu 机制中，服务端写入的 FriendlyByteBuf 在客户端
     * 实际上是 RegistryFriendlyByteBuf（FriendlyByteBuf 的子类），因此可以直接使用。
     * </p>
     */
    public IslandManagementMenu(int containerId, Player player, FriendlyByteBuf buf) {
        super(ModMenuTypes.ISLAND_MANAGEMENT.get(), containerId);
        this.islandX = buf.readInt();
        this.islandZ = buf.readInt();
        this.ownerName = buf.readUtf(64);

        int count = buf.readInt();
        this.allowedPlayers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.allowedPlayers.add(buf.readUUID());
        }
    }

    private static List<UUID> getAllowedPlayersForIsland(Player player, int islandX) {
        if (player instanceof ServerPlayer sp) {
            ServerLevel yuhutianLevel = sp.getServer().getLevel(YuhutianDimension.YUHUTIAN_LEVEL);
            if (yuhutianLevel != null) {
                IslandSavedData data = IslandSavedData.getOrCreate(yuhutianLevel);
                for (IslandInfo info : data.getAllIslands().values()) {
                    if (info.getX() == islandX) {
                        return new ArrayList<>(info.getAllowedPlayers());
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    // ==================== Getters ====================

    public int getIslandX() { return islandX; }
    public int getIslandZ() { return islandZ; }
    public String getOwnerName() { return ownerName; }
    public List<UUID> getMenuAllowedPlayers() { return allowedPlayers; }

    // ==================== AbstractContainerMenu 必须实现 ====================

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 限制在 NPC 附近 8 格内才能保持打开
        return player.distanceToSqr(player.getX(), player.getY(), player.getZ()) < 64.0;
    }
}
