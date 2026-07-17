package net.example.yuhutian.world;

import net.example.yuhutian.YuhutianMod;
import net.example.yuhutian.entity.IslandNPCEntity;
import net.example.yuhutian.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 空岛生成器。
 * <p>
 * 负责从 {@code data/yuhutian/structures/start_island.nbt} 加载结构文件，
 * 并在指定空岛中心坐标处放置结构，同时召唤一个 {@link IslandNPCEntity}。
 * </p>
 */
public final class IslandGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(YuhutianMod.MOD_ID);

    /** 结构文件在资源包中的 ID */
    private static final ResourceLocation START_ISLAND_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "start_island");

    /** 结构放置的 Y 基准坐标 */
    private static final int STRUCTURE_Y = 60;

    /** NPC 生成位置相对于岛屿中心的 Y 偏移（岛屿表面） */
    private static final double NPC_SPAWN_Y = 64.0;

    private IslandGenerator() {
    }

    /**
     * 在指定空岛中心坐标生成岛屿结构与 NPC。
     *
     * @param level   目标 ServerLevel（玉壶天维度）
     * @param islandX 空岛中心 X 坐标（= index * 1000）
     * @param islandZ 空岛中心 Z 坐标（= 0）
     */
    public static void generate(ServerLevel level, int islandX, int islandZ) {
        // 1. 强制加载目标区域区块，防止虚空区块未加载导致失败
        int chunkX = islandX >> 4;
        int chunkZ = islandZ >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, true);
            }
        }

        // 2. 加载并放置结构
        StructureTemplateManager templateManager = level.getStructureManager();
        Optional<StructureTemplate> templateOpt = templateManager.get(START_ISLAND_STRUCTURE);

        if (templateOpt.isEmpty()) {
            LOGGER.warn("[yuhutian] Structure template '{}' not found, skipping island generation",
                    START_ISLAND_STRUCTURE);
            return;
        }

        StructureTemplate template = templateOpt.get();
        BlockPos placePos = new BlockPos(islandX, STRUCTURE_Y, islandZ);

        // 居中放置：将结构中心对齐到空岛中心坐标
        StructurePlaceSettings settings = new StructurePlaceSettings();
        Vec3i structureSize = template.getSize();
        BlockPos offset = new BlockPos(
                -(structureSize.getX() / 2),
                0,
                -(structureSize.getZ() / 2)
        );
        BlockPos actualPos = placePos.offset(offset);

        template.placeInWorld(level, actualPos, actualPos, settings, level.getRandom(), 2);
        LOGGER.info("[yuhutian] Placed start_island structure at ({}, {}, {})",
                actualPos.getX(), actualPos.getY(), actualPos.getZ());

        // 3. 召唤空岛 NPC（仅在结构放置成功后）
        if (!ModEntities.ISLAND_NPC.isPresent()) {
            LOGGER.warn("[yuhutian] ISLAND_NPC entity type not yet registered, cannot spawn NPC");
            return;
        }

        IslandNPCEntity npc = ModEntities.ISLAND_NPC.get().create(level);
        if (npc != null) {
            npc.moveTo(islandX + 0.5, NPC_SPAWN_Y, islandZ + 0.5, 0.0F, 0.0F);
            // 1.21.1 持久化：确保 NPC 不会因距离被卸载
            npc.setPersistenceRequired();
            level.addFreshEntity(npc);
            LOGGER.info("[yuhutian] Spawned Island NPC at ({}, {}, {})",
                    islandX + 0.5, NPC_SPAWN_Y, islandZ + 0.5);
        } else {
            LOGGER.warn("[yuhutian] Failed to spawn Island NPC at ({}, {}, {})",
                    islandX, NPC_SPAWN_Y, islandZ);
        }
    }
}
