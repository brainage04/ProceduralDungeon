package com.github.brainage04.procedural_dungeon.lock;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonPieceSpec;
import com.github.brainage04.procedural_dungeon.worldgen.structure.VariantSinglePoolElement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class DungeonLockPlanner {
    private static final Identifier HALLWAY_LOOT = ProceduralDungeon.of("hallway_loot");
    private static final Identifier HALLWAY_END = ProceduralDungeon.of("hallway_end");
    private static final Block[] CONTAINER_BLOCKS = {
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.BARREL
    };

    private DungeonLockPlanner() {}

    public static DungeonLockPlan create(StagedDungeonLayout layout, StructureTemplateManager templateManager, RandomSource random) {
        List<ChestTarget> chests = findChestTargets(layout, templateManager);
        if (chests.size() < 2) {
            return DungeonLockPlan.EMPTY;
        }

        int lockCount = Math.min(3, chests.size() / 4);
        if (lockCount == 0) {
            lockCount = 1;
        }

        List<ChestTarget> lockCandidates = shuffled(
                chests.stream().filter(chest -> HALLWAY_END.equals(chest.lootTable())).toList(),
                random
        );
        if (lockCandidates.size() < lockCount) {
            lockCandidates = shuffled(chests, random);
        }

        List<Long> lockedChests = new ArrayList<>(lockCount);
        Set<Long> usedPositions = new HashSet<>();
        for (ChestTarget chest : lockCandidates) {
            if (lockedChests.size() >= lockCount) {
                break;
            }
            if (usedPositions.add(chest.pos().asLong())) {
                lockedChests.add(chest.pos().asLong());
            }
        }

        List<ChestTarget> keyCandidates = shuffled(
                chests.stream()
                        .filter(chest -> !usedPositions.contains(chest.pos().asLong()))
                        .filter(chest -> HALLWAY_LOOT.equals(chest.lootTable()))
                        .toList(),
                random
        );
        if (keyCandidates.size() < lockedChests.size()) {
            keyCandidates = shuffled(
                    chests.stream()
                            .filter(chest -> !usedPositions.contains(chest.pos().asLong()))
                            .toList(),
                    random
            );
        }

        List<DungeonLockPlan.KeySource> keySources = new ArrayList<>(lockedChests.size());
        for (ChestTarget chest : keyCandidates) {
            if (keySources.size() >= lockedChests.size()) {
                break;
            }
            if (usedPositions.add(chest.pos().asLong())) {
                keySources.add(new DungeonLockPlan.KeySource(
                        chest.pos().asLong(),
                        keySourceLootTable(chest.lootTable(), chest.tier())
                ));
            }
        }

        if (keySources.size() != lockedChests.size()) {
            return DungeonLockPlan.EMPTY;
        }
        return new DungeonLockPlan(lockedChests, keySources);
    }

    private static List<ChestTarget> findChestTargets(StagedDungeonLayout layout, StructureTemplateManager templateManager) {
        ArrayList<ChestTarget> targets = new ArrayList<>();
        for (StagedDungeonPieceSpec piece : layout.pieces()) {
            if (!(piece.element() instanceof VariantSinglePoolElement variantElement)) {
                continue;
            }

            StructureTemplate template = templateManager.getOrCreate(variantElement.templateLocation());
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(piece.rotation())
                    .setRotationPivot(BlockPos.ZERO)
                    .setBoundingBox(piece.boundingBox());

            for (Block block : CONTAINER_BLOCKS) {
                for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(piece.position(), settings, block)) {
                    Identifier lootTable = lootTable(info);
                    if (lootTable == null || (!HALLWAY_LOOT.equals(lootTable) && !HALLWAY_END.equals(lootTable))) {
                        continue;
                    }
                    targets.add(new ChestTarget(info.pos(), lootTable, variantElement.spawnerTier()));
                }
            }
        }
        return targets;
    }

    private static Identifier lootTable(StructureTemplate.StructureBlockInfo info) {
        if (info.nbt() == null) {
            return null;
        }
        return info.nbt().getString("LootTable").map(Identifier::parse).orElse(null);
    }

    private static Identifier keySourceLootTable(Identifier baseLootTable, int tier) {
        return ProceduralDungeon.of("%s/key_source/tier_%d".formatted(baseLootTable.getPath(), tier));
    }

    private static <T> List<T> shuffled(List<T> input, RandomSource random) {
        ArrayList<T> output = new ArrayList<>(input);
        Util.shuffle(output, random);
        return output;
    }

    private record ChestTarget(BlockPos pos, Identifier lootTable, int tier) {}
}
