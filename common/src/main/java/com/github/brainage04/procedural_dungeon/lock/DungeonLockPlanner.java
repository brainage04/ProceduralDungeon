package com.github.brainage04.procedural_dungeon.lock;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonProgressionRooms;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonPieceSpec;
import com.github.brainage04.procedural_dungeon.worldgen.structure.VariantSinglePoolElement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class DungeonLockPlanner {
    private static final Identifier HALLWAY_LOOT = ProceduralDungeon.of("hallway_loot");
    private static final Identifier HALLWAY_END = ProceduralDungeon.of("hallway_end");
    private static final Identifier BOSS_KEY_VAULT = ProceduralDungeon.of("boss_key_vault");
    private static final Block[] CONTAINER_BLOCKS = {
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.BARREL
    };

    private DungeonLockPlanner() {}

    public static DungeonLockPlan create(StagedDungeonLayout layout, StructureTemplateManager templateManager, RandomSource random) {
        List<ChestTarget> chests = findChestTargets(layout, templateManager);
        Set<Long> usedPositions = new HashSet<>();
        List<DungeonLockPlan.KeySource> keySources = new ArrayList<>();
        List<DungeonLockPlan.Door> doors = new ArrayList<>();

        for (ChestTarget chest : chests) {
            if (BOSS_KEY_VAULT.equals(chest.lootTable())) {
                usedPositions.add(chest.pos().asLong());
                keySources.add(new DungeonLockPlan.KeySource(
                        chest.pos().asLong(),
                        ProceduralDungeon.of("%s/tier_%d".formatted(BOSS_KEY_VAULT.getPath(), chest.tier()))
                ));
            }
        }

        List<ChestTarget> lootChests = chests.stream()
                .filter(chest -> HALLWAY_LOOT.equals(chest.lootTable()) || HALLWAY_END.equals(chest.lootTable()))
                .toList();
        for (GuardedDoor door : findGuardedDoors(layout, templateManager)) {
            if (door.key() == DungeonKeyType.BOSS) {
                doors.add(new DungeonLockPlan.Door(door.pos().asLong(), Optional.of(DungeonKeyType.BOSS)));
                continue;
            }

            // A miniboss room is only locked when some ordinary loot chest can hold its key; otherwise it opens freely.
            Optional<ChestTarget> keyChest = shuffled(lootChests, random).stream()
                    .filter(chest -> !usedPositions.contains(chest.pos().asLong()))
                    .findFirst();
            if (keyChest.isEmpty()) {
                doors.add(new DungeonLockPlan.Door(door.pos().asLong(), Optional.empty()));
                continue;
            }

            ChestTarget chest = keyChest.get();
            usedPositions.add(chest.pos().asLong());
            keySources.add(new DungeonLockPlan.KeySource(
                    chest.pos().asLong(),
                    keySourceLootTable(chest, DungeonKeyType.MINIBOSS)
            ));
            doors.add(new DungeonLockPlan.Door(door.pos().asLong(), Optional.of(DungeonKeyType.MINIBOSS)));
        }

        List<Long> lockedChests = planRustedLocks(lootChests, usedPositions, keySources, random);
        return new DungeonLockPlan(lockedChests, keySources, doors);
    }

    private static List<Long> planRustedLocks(
            List<ChestTarget> lootChests,
            Set<Long> usedPositions,
            List<DungeonLockPlan.KeySource> keySources,
            RandomSource random
    ) {
        List<ChestTarget> chests = lootChests.stream()
                .filter(chest -> !usedPositions.contains(chest.pos().asLong()))
                .toList();
        if (chests.size() < 2) {
            return List.of();
        }

        int lockCount = Math.max(1, Math.min(3, chests.size() / 4));
        List<ChestTarget> lockCandidates = shuffled(
                chests.stream().filter(chest -> HALLWAY_END.equals(chest.lootTable())).toList(),
                random
        );
        if (lockCandidates.size() < lockCount) {
            lockCandidates = shuffled(chests, random);
        }

        Set<Long> rustedPositions = new HashSet<>();
        List<Long> lockedChests = new ArrayList<>(lockCount);
        for (ChestTarget chest : lockCandidates) {
            if (lockedChests.size() >= lockCount) {
                break;
            }
            if (rustedPositions.add(chest.pos().asLong())) {
                lockedChests.add(chest.pos().asLong());
            }
        }

        List<ChestTarget> keyCandidates = shuffled(
                chests.stream()
                        .filter(chest -> !rustedPositions.contains(chest.pos().asLong()))
                        .filter(chest -> HALLWAY_LOOT.equals(chest.lootTable()))
                        .toList(),
                random
        );
        if (keyCandidates.size() < lockedChests.size()) {
            keyCandidates = shuffled(
                    chests.stream()
                            .filter(chest -> !rustedPositions.contains(chest.pos().asLong()))
                            .toList(),
                    random
            );
        }

        List<DungeonLockPlan.KeySource> rustedKeySources = new ArrayList<>(lockedChests.size());
        for (ChestTarget chest : keyCandidates) {
            if (rustedKeySources.size() >= lockedChests.size()) {
                break;
            }
            if (rustedPositions.add(chest.pos().asLong())) {
                rustedKeySources.add(new DungeonLockPlan.KeySource(
                        chest.pos().asLong(),
                        keySourceLootTable(chest, DungeonKeyType.RUSTED)
                ));
            }
        }

        if (rustedKeySources.size() != lockedChests.size()) {
            return List.of();
        }
        usedPositions.addAll(rustedPositions);
        keySources.addAll(rustedKeySources);
        return lockedChests;
    }

    private static List<ChestTarget> findChestTargets(StagedDungeonLayout layout, StructureTemplateManager templateManager) {
        ArrayList<ChestTarget> targets = new ArrayList<>();
        for (StagedDungeonPieceSpec piece : layout.pieces()) {
            if (!(piece.element() instanceof VariantSinglePoolElement variantElement)) {
                continue;
            }

            StructureTemplate template = templateManager.getOrCreate(variantElement.templateLocation());
            StructurePlaceSettings settings = placeSettings(piece);
            for (Block block : CONTAINER_BLOCKS) {
                for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(piece.position(), settings, block)) {
                    Identifier lootTable = lootTable(info);
                    if (lootTable == null
                            || (!HALLWAY_LOOT.equals(lootTable) && !HALLWAY_END.equals(lootTable) && !BOSS_KEY_VAULT.equals(lootTable))) {
                        continue;
                    }
                    targets.add(new ChestTarget(info.pos(), lootTable, themeName(variantElement.variant()), variantElement.spawnerTier()));
                }
            }
        }
        return targets;
    }

    private static List<GuardedDoor> findGuardedDoors(StagedDungeonLayout layout, StructureTemplateManager templateManager) {
        ArrayList<GuardedDoor> doors = new ArrayList<>();
        for (StagedDungeonPieceSpec piece : layout.pieces()) {
            if (!(piece.element() instanceof VariantSinglePoolElement variantElement)) {
                continue;
            }

            Optional<DungeonKeyType> key = DungeonProgressionRooms.guardedRoomKey(variantElement.templateLocation());
            if (key.isEmpty()) {
                continue;
            }

            StructureTemplate template = templateManager.getOrCreate(variantElement.templateLocation());
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(piece.position(), placeSettings(piece), Blocks.IRON_DOOR)) {
                if (info.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    doors.add(new GuardedDoor(info.pos(), key.get()));
                }
            }
        }
        return doors;
    }

    private static StructurePlaceSettings placeSettings(StagedDungeonPieceSpec piece) {
        return new StructurePlaceSettings()
                .setRotation(piece.rotation())
                .setRotationPivot(BlockPos.ZERO)
                .setBoundingBox(piece.boundingBox());
    }

    private static Identifier lootTable(StructureTemplate.StructureBlockInfo info) {
        if (info.nbt() == null) {
            return null;
        }
        return info.nbt().getString("LootTable").map(Identifier::parse).orElse(null);
    }

    private static String themeName(Identifier variant) {
        String[] parts = variant.getPath().split("/");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Dungeon variant id does not include a theme path: " + variant);
        }
        return parts[parts.length - 2];
    }

    private static Identifier keySourceLootTable(ChestTarget chest, DungeonKeyType key) {
        String keySource = switch (key) {
            case RUSTED -> "key_source";
            case MINIBOSS -> "miniboss_key_source";
            case BOSS -> throw new IllegalArgumentException("Boss keys only come from the boss key vault");
        };
        return ProceduralDungeon.of("%s/%s/%s/tier_%d".formatted(chest.lootTable().getPath(), keySource, chest.theme(), chest.tier()));
    }

    private static <T> List<T> shuffled(List<T> input, RandomSource random) {
        ArrayList<T> output = new ArrayList<>(input);
        Util.shuffle(output, random);
        return output;
    }

    private record ChestTarget(BlockPos pos, Identifier lootTable, String theme, int tier) {}

    private record GuardedDoor(BlockPos pos, DungeonKeyType key) {}
}
