package net.example.yuhutian.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 单个空岛的信息数据。
 * <p>
 * 包含空岛中心坐标、索引、是否为新分配的岛屿、以及允许进入的玩家列表。
 * isNew 字段预留给第三阶段（判断是否需要生成岛屿结构）。
 * allowedPlayers 字段预留给第四阶段（权限管理）。
 * </p>
 */
public class IslandInfo {

    /** 默认欢迎寄语 */
    public static final String DEFAULT_GREETING_TEXT = "欢迎来到我的空岛！";
    /** 默认提示音效（成就达成音） */
    public static final String DEFAULT_GREETING_SOUND = "minecraft:entity.player.levelup";

    private final int index;
    private final int x;
    private final int z;
    private boolean isNew;
    private boolean showBorder;
    private boolean enableGreeting;
    private String greetingText;
    private String greetingSound;
    private final List<UUID> allowedPlayers;

    /**
     * 根据索引创建空岛信息。
     * X 轴一维线性递增：X = index * 1000, Z = 0。
     *
     * @param index 空岛索引（从 0 开始）
     * @throws ArithmeticException 当 index * 1000 溢出 int 范围时
     */
    public IslandInfo(int index) {
        this.index = index;
        this.x = Math.multiplyExact(index, 1000); // 溢出保护
        this.z = 0;
        this.isNew = true;
        this.showBorder = false;
        this.enableGreeting = true;
        this.greetingText = DEFAULT_GREETING_TEXT;
        this.greetingSound = DEFAULT_GREETING_SOUND;
        this.allowedPlayers = new ArrayList<>();
    }

    private IslandInfo(int index, int x, int z, boolean isNew, boolean showBorder,
                       boolean enableGreeting, String greetingText, String greetingSound,
                       List<UUID> allowedPlayers) {
        this.index = index;
        this.x = x;
        this.z = z;
        this.isNew = isNew;
        this.showBorder = showBorder;
        this.enableGreeting = enableGreeting;
        this.greetingText = greetingText;
        this.greetingSound = greetingSound;
        this.allowedPlayers = allowedPlayers;
    }

    // ==================== NBT 序列化 ====================

    /**
     * 将空岛信息序列化为 NBT。
     */
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Index", index);
        tag.putInt("X", x);
        tag.putInt("Z", z);
        tag.putBoolean("IsNew", isNew);
        tag.putBoolean("ShowBorder", showBorder);
        tag.putBoolean("EnableGreeting", enableGreeting);
        tag.putString("GreetingText", greetingText);
        tag.putString("GreetingSound", greetingSound);
        // 将 allowedPlayers 序列化为 LongArray（UUID 的高低 64 位交替存储）
        long[] uuidArray = new long[allowedPlayers.size() * 2];
        for (int i = 0; i < allowedPlayers.size(); i++) {
            UUID uuid = allowedPlayers.get(i);
            uuidArray[i * 2] = uuid.getMostSignificantBits();
            uuidArray[i * 2 + 1] = uuid.getLeastSignificantBits();
        }
        tag.putLongArray("AllowedPlayers", uuidArray);
        return tag;
    }

    /**
     * 从 NBT 反序列化空岛信息。
     */
    public static IslandInfo fromNbt(CompoundTag tag) {
        int index = tag.getInt("Index");
        int x = tag.getInt("X");
        int z = tag.getInt("Z");
        boolean isNew = tag.getBoolean("IsNew");
        // 向后兼容：旧存档无 ShowBorder 字段时默认为 false
        boolean showBorder = tag.contains("ShowBorder") && tag.getBoolean("ShowBorder");
        // 向后兼容：旧存档无 EnableGreeting 字段时默认为 true
        boolean enableGreeting = !tag.contains("EnableGreeting") || tag.getBoolean("EnableGreeting");
        // 向后兼容：旧存档无 Greeting 字段时使用默认值
        String greetingText = tag.contains("GreetingText") ? tag.getString("GreetingText") : DEFAULT_GREETING_TEXT;
        String greetingSound = tag.contains("GreetingSound") ? tag.getString("GreetingSound") : DEFAULT_GREETING_SOUND;

        List<UUID> allowedPlayers = new ArrayList<>();
        if (tag.contains("AllowedPlayers", Tag.TAG_LONG_ARRAY)) {
            long[] uuidArray = tag.getLongArray("AllowedPlayers");
            for (int i = 0; i + 1 < uuidArray.length; i += 2) {
                allowedPlayers.add(new UUID(uuidArray[i], uuidArray[i + 1]));
            }
        }

        return new IslandInfo(index, x, z, isNew, showBorder, enableGreeting,
                greetingText, greetingSound, allowedPlayers);
    }

    // ==================== Getters ====================

    public int getIndex() {
        return index;
    }

    /**
     * 获取空岛中心 X 坐标。
     */
    public int getX() {
        return x;
    }

    /**
     * 获取空岛中心 Z 坐标（始终为 0）。
     */
    public int getZ() {
        return z;
    }

    /**
     * 该空岛是否为新分配的（尚未生成岛屿结构）。
     * 预留给第三阶段使用。
     */
    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    /**
     * 是否显示领地边界粒子墙。
     * 岛主可在管理面板中切换此选项。
     */
    public boolean isShowBorder() {
        return showBorder;
    }

    public void setShowBorder(boolean showBorder) {
        this.showBorder = showBorder;
    }

    /**
     * 获取入场欢迎寄语文本。
     */
    public String getGreetingText() {
        return greetingText;
    }

    public void setGreetingText(String greetingText) {
        this.greetingText = greetingText;
    }

    /**
     * 获取入场提示音效的 ResourceLocation 字符串。
     */
    public String getGreetingSound() {
        return greetingSound;
    }

    public void setGreetingSound(String greetingSound) {
        this.greetingSound = greetingSound;
    }

    /**
     * 是否启用入场欢迎仪式（Title + 音效）。
     * 默认开启，岛主可关闭。
     */
    public boolean isEnableGreeting() {
        return enableGreeting;
    }

    public void setEnableGreeting(boolean enableGreeting) {
        this.enableGreeting = enableGreeting;
    }

    /**
     * 获取允许进入该空岛的玩家 UUID 列表（只读视图）。
     */
    public List<UUID> getAllowedPlayers() {
        return Collections.unmodifiableList(allowedPlayers);
    }

    /**
     * 添加一个被信任的玩家。
     */
    public void addAllowedPlayer(UUID uuid) {
        if (!allowedPlayers.contains(uuid)) {
            allowedPlayers.add(uuid);
        }
    }

    /**
     * 移除一个被信任的玩家。
     */
    public void removeAllowedPlayer(UUID uuid) {
        allowedPlayers.remove(uuid);
    }

    /**
     * 检查指定玩家是否在信任列表中。
     */
    public boolean isAllowed(UUID uuid) {
        return allowedPlayers.contains(uuid);
    }
}
