package net.example.yuhutian.gui;

import net.example.yuhutian.YuhutianDimension;
import net.example.yuhutian.world.IslandInfo;
import net.example.yuhutian.world.IslandSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 空岛管理面板的容器菜单。
 * <p>
 * 不包含物品栏格子，是一个纯功能性面板。
 * 持有当前空岛的基本信息、信任玩家列表和在线玩家列表，供客户端 Screen 渲染使用。
 * </p>
 * <p>
 * MC 1.21.1 的 ServerPlayer.openMenu 不再支持 Consumer&lt;FriendlyByteBuf&gt; 参数，
 * 因此通过静态缓冲区 {@link #pendingData} 接收 S2C 包传来的岛屿数据。
 * </p>
 */
public class IslandManagementMenu extends AbstractContainerMenu {

    /**
     * 客户端静态缓冲区。
     * S2C 包处理器在 openMenu 之前写入数据，客户端构造器读取后清空。
     */
    public static Object[] pendingData = null;

    private final int islandX;
    private final int islandZ;
    private final String ownerName;
    private final List<UUID> allowedPlayers;
    private final Map<UUID, String> onlinePlayers;

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
        this.onlinePlayers = new LinkedHashMap<>();
    }

    /**
     * 客户端构造：从静态缓冲区读取 S2C 包预存的数据。
     * <p>
     * MC 1.21.1 移除了 openMenu 的 buffer consumer 参数，
     * 因此在 ModMenuTypes 中注册的空构造器会传入一个空 FriendlyByteBuf，
     * 我们忽略它，改为从 pendingData 读取。
     * </p>
     */
    @SuppressWarnings("unchecked")
    public IslandManagementMenu(int containerId, Player player, net.minecraft.network.FriendlyByteBuf buf) {
        super(ModMenuTypes.ISLAND_MANAGEMENT.get(), containerId);
        if (pendingData != null) {
            this.islandX = (int) pendingData[0];
            this.islandZ = (int) pendingData[1];
            this.ownerName = (String) pendingData[2];
            this.allowedPlayers = new ArrayList<>((List<UUID>) pendingData[3]);
            this.onlinePlayers = new LinkedHashMap<>((Map<UUID, String>) pendingData[4]);
            pendingData = null;
        } else {
            this.islandX = 0;
            this.islandZ = 0;
            this.ownerName = "Unknown";
            this.allowedPlayers = new ArrayList<>();
            this.onlinePlayers = new LinkedHashMap<>();
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
    public Map<UUID, String> getOnlinePlayers() { return onlinePlayers; }

    // ==================== AbstractContainerMenu 必须实现 ====================

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
