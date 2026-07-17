package net.example.yuhutian.world;

import net.example.yuhutian.YuhutianMod;
import net.example.yuhutian.entity.IslandNPCEntity;
import net.example.yuhutian.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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
    private static final int STRUCTURE_Y = 100;

    /** NPC 生成位置相对于岛屿中心的 Y 偏移（岛屿表面） */
    private static final double NPC_SPAWN_Y = 104.0;

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
        boolean structurePlaced = false;
        try {
            StructureTemplateManager templateManager = level.getStructureManager();

            // 调试：检查 ResourceManager 是否能找到结构文件
            var resourceManager = level.getServer().getResourceManager();
            var resourceOpt = resourceManager.getResource(
                    ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "structures/start_island.nbt"));
            LOGGER.info("[yuhutian] ResourceManager lookup for 'structures/start_island.nbt': {}",
                    resourceOpt.isPresent() ? "FOUND" : "NOT FOUND");

            // 尝试通过 StructureTemplateManager 加载
            Optional<StructureTemplate> templateOpt = templateManager.get(START_ISLAND_STRUCTURE);

            if (templateOpt.isPresent()) {
                StructureTemplate template = templateOpt.get();
                structurePlaced = placeStructure(level, template, islandX, islandZ);
            } else {
                LOGGER.warn("[yuhutian] StructureTemplateManager.get() returned empty for '{}'",
                        START_ISLAND_STRUCTURE);

                // 后备方案：直接用 NbtIo 从 ResourceManager 加载
                LOGGER.info("[yuhutian] Attempting direct NbtIo load as fallback...");
                structurePlaced = loadAndPlaceDirectly(level, islandX, islandZ);
            }
        } catch (Exception e) {
            LOGGER.error("[yuhutian] Exception during structure loading: {}", e.getMessage(), e);
        }

        // 如果结构加载失败，生成应急石砖平台
        if (!structurePlaced) {
            LOGGER.info("[yuhutian] Generating fallback stone platform at ({}, {}, {})",
                    islandX, STRUCTURE_Y, islandZ);
            generateFallbackPlatform(level, islandX, STRUCTURE_Y, islandZ);
        }

        // 3. 召唤空岛 NPC
        if (!ModEntities.ISLAND_NPC.isPresent()) {
            LOGGER.warn("[yuhutian] ISLAND_NPC entity type not yet registered, cannot spawn NPC");
            return;
        }

        IslandNPCEntity npc = ModEntities.ISLAND_NPC.get().create(level);
        if (npc != null) {
            npc.moveTo(islandX + 0.5, NPC_SPAWN_Y, islandZ + 0.5, 0.0F, 0.0F);
            npc.setPersistenceRequired();
            npc.setInvulnerable(true);
            level.addFreshEntity(npc);
            LOGGER.info("[yuhutian] Spawned Island NPC at ({}, {}, {})",
                    islandX + 0.5, NPC_SPAWN_Y, islandZ + 0.5);
        } else {
            LOGGER.warn("[yuhutian] Failed to spawn Island NPC at ({}, {}, {})",
                    islandX, NPC_SPAWN_Y, islandZ);
        }
    }

    /**
     * 放置结构模板。
     */
    private static boolean placeStructure(ServerLevel level, StructureTemplate template,
                                          int islandX, int islandZ) {
        try {
            BlockPos placePos = new BlockPos(islandX, STRUCTURE_Y, islandZ);
            StructurePlaceSettings settings = new StructurePlaceSettings();
            Vec3i structureSize = template.getSize();
            BlockPos offset = new BlockPos(
                    -(structureSize.getX() / 2), 0, -(structureSize.getZ() / 2));
            BlockPos actualPos = placePos.offset(offset);

            template.placeInWorld(level, actualPos, actualPos, settings, level.getRandom(), 2);
            LOGGER.info("[yuhutian] Placed start_island structure at ({}, {}, {})",
                    actualPos.getX(), actualPos.getY(), actualPos.getZ());
            return true;
        } catch (Exception e) {
            LOGGER.error("[yuhutian] Failed to place structure: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 后备方案：直接用 NbtIo 从 ResourceManager 读取 NBT 并手动创建 StructureTemplate。
     */
    private static boolean loadAndPlaceDirectly(ServerLevel level, int islandX, int islandZ) {
        try {
            var resourceManager = level.getServer().getResourceManager();
            var resourceOpt = resourceManager.getResource(
                    ResourceLocation.fromNamespaceAndPath(YuhutianMod.MOD_ID, "structures/start_island.nbt"));

            if (resourceOpt.isEmpty()) {
                LOGGER.error("[yuhutian] Direct load: resource not found in ResourceManager");
                return false;
            }

            try (InputStream stream = resourceOpt.get().open()) {
                CompoundTag tag = NbtIo.readCompressed(stream);
                LOGGER.info("[yuhutian] Direct load: NBT loaded successfully, keys: {}", tag.getAllKeys());

                StructureTemplate template = new StructureTemplate();
                template.load(level.registryAccess(), tag);
                LOGGER.info("[yuhutian] Direct load: StructureTemplate loaded, size: {}", template.getSize());

                return placeStructure(level, template, islandX, islandZ);
            }
        } catch (IOException e) {
            LOGGER.error("[yuhutian] Direct load: IOException: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            LOGGER.error("[yuhutian] Direct load: Exception: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 应急平台：在指定坐标生成一个 7×7 的石砖平台，确保玩家不会掉入虚空。
     */
    private static void generateFallbackPlatform(ServerLevel level, int centerX, int y, int centerZ) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlock(new BlockPos(centerX + dx, y, centerZ + dz),
                        Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
        }
    }
}
