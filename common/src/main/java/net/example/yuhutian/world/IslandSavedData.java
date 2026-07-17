package net.example.yuhutian.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玉壶天空岛数据持久化类。
 * <p>
 * 使用 Minecraft 1.21.1 的 {@link SavedData} + {@link SavedData.Factory} 模式，
 * 通过 {@link ServerLevel#getDataStorage()} 管理，数据保存于世界存档文件中。
 * </p>
 * <p>
 * 存储内容：
 * <ul>
 *   <li>{@code nextIslandIndex} — 下一个待分配的空岛索引</li>
 *   <li>{@code Map<UUID, IslandInfo>} — 玩家 UUID 到空岛信息的映射</li>
 * </ul>
 * </p>
 */
public class IslandSavedData extends SavedData {

    private static final String DATA_NAME = "yuhutian_islands";

    private int nextIslandIndex = 0;
    private final Map<UUID, IslandInfo> islands = new HashMap<>();

    // ==================== 工厂方法 ====================

    /**
     * 1.21.1 SavedData.Factory 工厂实例。
     * loader 签名为 BiFunction&lt;CompoundTag, HolderLookup.Provider, T&gt;。
     */
    public static final SavedData.Factory<IslandSavedData> FACTORY =
            new SavedData.Factory<>(IslandSavedData::new, IslandSavedData::load, DataFixTypes.LEVEL);

    /**
     * 从 ServerLevel 获取或创建该维度的空岛数据。
     *
     * @param level 目标 ServerLevel（通常为玉壶天维度）
     * @return 该维度的 IslandSavedData 实例
     */
    public static IslandSavedData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // ==================== NBT 序列化 ====================

    /**
     * 从 NBT 加载数据（反序列化）。
     * 1.21.1 要求 loader 接受 HolderLookup.Provider 参数。
     *
     * @param tag            存储的 CompoundTag
     * @param lookupProvider 注册表查找提供者（本模组未使用，但签名必须匹配）
     * @return 恢复后的 IslandSavedData 实例
     */
    private static IslandSavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        IslandSavedData data = new IslandSavedData();
        data.nextIslandIndex = tag.getInt("NextIslandIndex");

        if (tag.contains("Islands", CompoundTag.TAG_LIST)) {
            ListTag islandsTag = tag.getList("Islands", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < islandsTag.size(); i++) {
                CompoundTag entryTag = islandsTag.getCompound(i);
                UUID uuid = entryTag.getUUID("UUID");
                IslandInfo info = IslandInfo.fromNbt(entryTag.getCompound("Island"));
                data.islands.put(uuid, info);
            }
        }

        return data;
    }

    /**
     * 将数据保存到 NBT（序列化）。
     * 1.21.1 的 save 签名为 save(CompoundTag, HolderLookup.Provider)。
     *
     * @param tag            写入目标的 CompoundTag
     * @param lookupProvider 注册表查找提供者（本模组未使用）
     * @return 写入完成的 CompoundTag
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        tag.putInt("NextIslandIndex", nextIslandIndex);

        ListTag islandsTag = new ListTag();
        for (Map.Entry<UUID, IslandInfo> entry : islands.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("UUID", entry.getKey());
            entryTag.put("Island", entry.getValue().toNbt());
            islandsTag.add(entryTag);
        }
        tag.put("Islands", islandsTag);

        return tag;
    }

    // ==================== 空岛管理 API ====================

    /**
     * 查询玩家是否已拥有空岛。
     *
     * @param uuid 玩家 UUID
     * @return true 表示该玩家已被分配空岛
     */
    public boolean hasIsland(UUID uuid) {
        return islands.containsKey(uuid);
    }

    /**
     * 获取玩家的空岛信息。
     *
     * @param uuid 玩家 UUID
     * @return 该玩家的 IslandInfo，若未分配则返回 null
     */
    public IslandInfo getIsland(UUID uuid) {
        return islands.get(uuid);
    }

    /**
     * 为指定玩家分配一个新空岛。
     * 使用当前 {@code nextIslandIndex} 创建空岛，然后索引自增。
     * <p>
     * 防重入：若该 UUID 已分配过空岛，直接返回已有信息。
     * 调用此方法后，务必调用 {@link #setDirty()} 标记数据已变更。
     * </p>
     *
     * @param uuid 玩家 UUID
     * @return 分配的 IslandInfo（若已存在则返回已有的）
     */
    public IslandInfo allocateIsland(UUID uuid) {
        // 防重入：如果已分配则直接返回
        if (islands.containsKey(uuid)) {
            return islands.get(uuid);
        }
        IslandInfo info = new IslandInfo(nextIslandIndex);
        islands.put(uuid, info);
        nextIslandIndex++;
        return info;
    }

    /**
     * 获取当前已分配的最高空岛索引（即下一个待分配的索引值）。
     */
    public int getNextIslandIndex() {
        return nextIslandIndex;
    }

    /**
     * 获取所有已分配的空岛信息（只读视图）。
     * 使用 unmodifiableMap 避免每次调用都做完整拷贝。
     */
    public Map<UUID, IslandInfo> getAllIslands() {
        return Collections.unmodifiableMap(islands);
    }
}
