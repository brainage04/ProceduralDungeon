package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.command.GenerateDungeonCommand;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.guardian.DungeonGuardian;
import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import com.github.brainage04.procedural_dungeon.test.DungeonVariantSmokeTester;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonProgressionRooms;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonPieceSpec;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayoutCompiler;
import com.github.brainage04.procedural_dungeon.worldgen.structure.VariantSinglePoolElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

public final class DungeonGameTestSuite {
    private static final List<DungeonTheme> LAYOUT_THEMES = List.of(
            DungeonTheme.COBBLESTONE,
            DungeonTheme.SCULK,
            DungeonTheme.NETHER_WASTES,
            DungeonTheme.END_CITY
    );
    private static final List<BlockPos> LAYOUT_ORIGINS = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(1024, 40, -2048),
            new BlockPos(-4096, 90, 3072)
    );

    private DungeonGameTestSuite() {}

    public static List<TestCase> tests() {
        return List.of(
                new TestCase("variant_catalog_is_stable_and_complete", DungeonGameTestSuite::variantCatalogIsStableAndComplete),
                new TestCase("seeded_loot_selection_is_repeatable_and_tier_bounded", DungeonGameTestSuite::seededLootSelectionIsRepeatableAndTierBounded),
                new TestCase("every_layout_has_a_boss_room_and_a_boss_key_vault", DungeonGameTestSuite::everyLayoutHasABossRoomAndABossKeyVault),
                new TestCase("guarded_door_opens_only_with_its_key", DungeonGameTestSuite::guardedDoorOpensOnlyWithItsKey),
                new TestCase("placed_boss_room_is_guarded_locked_and_stocked", DungeonGameTestSuite::placedBossRoomIsGuardedLockedAndStocked),
                new TestCase("trial_spawners_eject_tiered_dungeon_loot", DungeonGameTestSuite::trialSpawnersEjectTieredDungeonLoot),
                new TestCase("surface_entrances_lead_down_into_a_full_dungeon", DungeonGameTestSuite::surfaceEntrancesLeadDownIntoAFullDungeon)
        );
    }

    public static void variantCatalogIsStableAndComplete(GameTestHelper helper) {
        List<String> variants = DungeonVariantSmokeTester.getVariantKeys();
        helper.assertTrue(variants.size() == DungeonTheme.values().length * DungeonTier.values().length,
                "Every theme/tier pair must have one deterministic variant key");
        helper.assertTrue(variants.stream().distinct().count() == variants.size(), "Dungeon variant keys must be unique");
        helper.assertTrue(variants.getFirst().endsWith("tier_1"), "Variant catalog must start with tier 1");
        helper.assertTrue(variants.getLast().endsWith("tier_5"), "Variant catalog must end with tier 5");
        helper.succeed();
    }

    public static void seededLootSelectionIsRepeatableAndTierBounded(GameTestHelper helper) {
        List<DungeonTier> first = lootSequence(0xD06E0L);
        List<DungeonTier> second = lootSequence(0xD06E0L);
        helper.assertTrue(first.equals(second), "A fixed seed must select the same loot tiers");
        helper.assertTrue(first.stream().allMatch(tier -> tier.ordinal() >= 0 && tier.ordinal() < DungeonTier.values().length),
                "Loot selection must always return a defined dungeon tier");
        helper.succeed();
    }

    /**
     * The boss room and its key are guaranteed: every compiled layout has one boss room behind a boss-key door and
     * one vault whose chest holds the boss key, and every locked miniboss door has a chest planned for its key.
     */
    public static void everyLayoutHasABossRoomAndABossKeyVault(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (DungeonTheme theme : LAYOUT_THEMES) {
            for (DungeonTier tier : DungeonTier.values()) {
                for (BlockPos origin : LAYOUT_ORIGINS) {
                    String label = "%s tier %d at %s".formatted(theme.getSerializedName(), tier.tier, origin.toShortString());
                    Optional<StagedDungeonLayout> compiled = GenerateDungeonCommand.compileLayout(level, theme, tier, tier.size, origin);
                    helper.assertTrue(compiled.isPresent(), "Layout must compile: " + label);
                    StagedDungeonLayout layout = compiled.get();
                    DungeonLockPlan plan = layout.lockPlan();

                    List<StagedDungeonPieceSpec> bossRooms = pieces(layout, DungeonProgressionRooms.BOSS_ROOM);
                    List<StagedDungeonPieceSpec> vaults = pieces(layout, DungeonProgressionRooms.BOSS_KEY_VAULT);
                    helper.assertTrue(bossRooms.size() == 1, "Exactly one boss room: " + label);
                    helper.assertTrue(vaults.size() == 1, "Exactly one boss key vault: " + label);

                    BoundingBox bossBox = bossRooms.getFirst().boundingBox();
                    List<DungeonLockPlan.Door> bossDoors = plan.doors().stream()
                            .filter(door -> door.lock().equals(Optional.of(DungeonKeyType.BOSS)))
                            .toList();
                    helper.assertTrue(bossDoors.size() == 1 && bossBox.isInside(BlockPos.of(bossDoors.getFirst().pos())),
                            "The boss room door must need the boss key: " + label);

                    BoundingBox vaultBox = vaults.getFirst().boundingBox();
                    Identifier vaultLoot = ProceduralDungeon.of("boss_key_vault/tier_%d".formatted(tier.tier));
                    helper.assertTrue(plan.keySources().stream().anyMatch(source ->
                                    vaultBox.isInside(BlockPos.of(source.pos())) && source.lootTable().equals(vaultLoot)),
                            "The vault chest must hold the boss key: " + label);

                    long minibossKeys = plan.keySources().stream()
                            .filter(source -> source.lootTable().getPath().contains("/miniboss_key_source/"))
                            .count();
                    helper.assertTrue(minibossKeys == plan.lockedDoorCount(DungeonKeyType.MINIBOSS),
                            "Every locked miniboss door needs one key chest: " + label);
                    helper.assertTrue(plan.doors().size() - bossDoors.size() == pieces(layout, DungeonProgressionRooms.MINIBOSS_ROOM).size(),
                            "Every miniboss room must plan its door: " + label);
                }
            }
        }
        helper.succeed();
    }

    public static void guardedDoorOpensOnlyWithItsKey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos upper = lower.above();
        level.setBlock(lower, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(upper, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        DungeonLockManager.applyPlanForPiece(
                level,
                new DungeonLockPlan(List.of(), List.of(), List.of(new DungeonLockPlan.Door(lower.asLong(), Optional.of(DungeonKeyType.BOSS)))),
                new BoundingBox(lower)
        );

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(DungeonLockManager.useBlock(player, level, upper) == InteractionResult.FAIL,
                "A boss door must refuse a player without keys");
        helper.assertTrue(!DungeonLockManager.canBreak(player, level, lower) && !DungeonLockManager.canBreak(player, level, upper),
                "Both halves of a locked door must be unbreakable");

        player.getInventory().add(DungeonKeyType.RUSTED.createStack());
        player.getInventory().add(DungeonKeyType.MINIBOSS.createStack());
        ItemStack forgedBossKey = new ItemStack(DungeonKeyType.BOSS.item());
        forgedBossKey.set(DataComponents.ITEM_NAME, Component.literal(DungeonKeyType.BOSS.displayName()));
        player.getInventory().add(forgedBossKey);
        helper.assertTrue(DungeonLockManager.useBlock(player, level, lower) == InteractionResult.FAIL,
                "A boss door must refuse rusted and miniboss keys, and a plain key merely named like a boss key");
        helper.assertTrue(player.getInventory().countItem(DungeonKeyType.BOSS.item()) == 1,
                "The forged key must not be consumed");
        helper.assertTrue(keyCount(player, DungeonKeyType.RUSTED) == 1
                        && keyCount(player, DungeonKeyType.MINIBOSS) == 1,
                "A refused key must not be consumed");
        helper.assertTrue(!level.getBlockState(lower).getValue(DoorBlock.OPEN), "A refused door must stay shut");

        player.getInventory().add(DungeonKeyType.BOSS.createStack());
        helper.assertTrue(DungeonLockManager.useBlock(player, level, upper) == InteractionResult.SUCCESS,
                "The boss key must unlock the boss door");
        helper.assertTrue(keyCount(player, DungeonKeyType.BOSS) == 0, "Unlocking consumes the boss key");
        helper.assertTrue(level.getBlockState(lower).getValue(DoorBlock.OPEN) && level.getBlockState(upper).getValue(DoorBlock.OPEN),
                "An unlocked door opens");
        helper.assertTrue(DungeonLockManager.canBreak(player, level, lower) && DungeonLockManager.canBreak(player, level, upper),
                "An unlocked door is no longer protected");
        helper.succeed();
    }

    public static void placedBossRoomIsGuardedLockedAndStocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonTier tier = DungeonTier.TIER_3;
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(2048, 0, 2048);
        StagedDungeonLayout layout = GenerateDungeonCommand.compileLayout(level, DungeonTheme.COBBLESTONE, tier, tier.size, origin)
                .orElseThrow(() -> new AssertionError("Layout must compile"));
        StagedDungeonPieceSpec bossRoom = pieces(layout, DungeonProgressionRooms.BOSS_ROOM).getFirst();
        StagedDungeonPieceSpec vault = pieces(layout, DungeonProgressionRooms.BOSS_KEY_VAULT).getFirst();
        place(level, layout, bossRoom);
        place(level, layout, vault);

        BoundingBox bossBox = bossRoom.boundingBox();
        List<Mob> guardians = level.getEntitiesOfClass(Mob.class, AABB.of(bossBox), mob -> mob.entityTags().contains(DungeonGuardian.TAG));
        helper.assertTrue(guardians.size() == 1, "The boss room must spawn one guardian, found " + guardians.size());
        Mob boss = guardians.getFirst();
        helper.assertTrue(boss.getMaxHealth() == (float) DungeonGuardian.BOSS.maxHealth(tier.tier) && boss.isPersistenceRequired(),
                "The boss must have tier-scaled health and never despawn");

        BlockPos door = BlockPos.of(layout.lockPlan().doors().stream()
                .filter(planned -> planned.lock().equals(Optional.of(DungeonKeyType.BOSS)))
                .findFirst().orElseThrow().pos());
        helper.assertTrue(level.getBlockState(door).is(Blocks.IRON_DOOR) && level.getBlockState(door.above()).is(Blocks.IRON_DOOR),
                "The boss room door must be placed");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(DungeonLockManager.useBlock(player, level, door) == InteractionResult.FAIL, "The boss room door must be locked");

        List<ResourceKey<LootTable>> bossLoot = chestLoot(level, bossBox);
        helper.assertTrue(bossLoot.size() == 2 && bossLoot.stream().allMatch(loot -> loot.identifier().getPath().startsWith("boss_room/")),
                "The boss room must hold two boss loot chests: " + bossLoot);
        helper.assertTrue(chestLoot(level, vault.boundingBox()).equals(List.of(ResourceKey.create(
                        Registries.LOOT_TABLE, ProceduralDungeon.of("boss_key_vault/tier_%d".formatted(tier.tier))))),
                "The vault chest must hold the tiered boss key loot");

        boss.discard();
        helper.succeed();
    }

    public static void trialSpawnersEjectTieredDungeonLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonTier tier = DungeonTier.TIER_4;
        ResourceKey<StructureTemplatePool> roomPool = RegistryKeyUtils.create(
                Registries.TEMPLATE_POOL, "%s/hallway/room".formatted(RegistryKeyUtils.getKeyString(DungeonTheme.DEEPSLATE, tier)));
        StructurePoolElement corridor = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(roomPool).value()
                .getShuffledTemplates(RandomSource.create(0L)).stream()
                .filter(element -> element instanceof VariantSinglePoolElement variant
                        && variant.templateLocation().getPath().equals("dungeon/hallway/room/spawner_corridor"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The room pool must contain the spawner corridor"));

        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(-2048, 0, 2048);
        BoundingBox box = corridor.getBoundingBox(level.getStructureManager(), origin, Rotation.NONE);
        StagedDungeonGenerationManager.placeSynchronously(
                level,
                List.of(new StagedDungeonPieceSpec(corridor, origin, Rotation.NONE, box, 0)),
                LiquidSettings.IGNORE_WATERLOGGING
        );

        Identifier normal = ProceduralDungeon.of("trial_spawner/tier_%d".formatted(tier.tier));
        Identifier ominous = ProceduralDungeon.of("trial_spawner/ominous/tier_%d".formatted(tier.tier));
        int spawners = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity spawner) {
                spawners++;
                helper.assertTrue(ejectedLoot(spawner.getTrialSpawner().normalConfig().lootTablesToEject().unwrap()).equals(List.of(normal)),
                        "Normal trial spawner rewards must use " + normal);
                helper.assertTrue(ejectedLoot(spawner.getTrialSpawner().ominousConfig().lootTablesToEject().unwrap()).equals(List.of(ominous)),
                        "Ominous trial spawner rewards must use " + ominous);
            }
        }
        helper.assertTrue(spawners == 2, "The spawner corridor must place two trial spawners, found " + spawners);
        helper.succeed();
    }

    /**
     * Natural Overworld and End dungeons start at a surface entrance; the start room must hang below its shaft, and
     * the dungeon must grow from there to its boss room instead of stopping at the entrance.
     */
    public static void surfaceEntrancesLeadDownIntoAFullDungeon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (DungeonTheme theme : List.of(DungeonTheme.COBBLESTONE, DungeonTheme.END_CITY)) {
            for (DungeonTier tier : DungeonTier.values()) {
                String label = "%s tier %d".formatted(theme.getSerializedName(), tier.tier);
                ResourceKey<StructureTemplatePool> entrancePool = RegistryKeyUtils.create(
                        Registries.TEMPLATE_POOL, "%s/entrance".formatted(RegistryKeyUtils.getKeyString(theme, tier)));
                BlockPos origin = new BlockPos(tier.tier * 512, 150, -tier.tier * 512);
                var generator = level.getChunkSource().getGenerator();
                Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                        generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                        level.getSeed(), ChunkPos.containing(origin), level, ignored -> true);
                Optional<StagedDungeonLayout> compiled = StagedDungeonLayoutCompiler.compile(
                        context,
                        level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(entrancePool),
                        Optional.of(Identifier.withDefaultNamespace("start")),
                        tier.worldgenSize,
                        origin,
                        Optional.empty(),
                        new JigsawStructure.MaxDistance(tier.maxDistanceFromCenter),
                        LiquidSettings.IGNORE_WATERLOGGING
                );
                helper.assertTrue(compiled.isPresent(), "Entrance layout must compile: " + label);
                StagedDungeonLayout layout = compiled.get();
                BoundingBox entrance = layout.pieces().getFirst().boundingBox();
                List<StagedDungeonPieceSpec> startRooms = pieces(layout, ProceduralDungeon.of("dungeon/start_shaft"));
                helper.assertTrue(startRooms.size() == 1 && startRooms.getFirst().boundingBox().maxY() < entrance.minY(),
                        "The start room must hang below the entrance: " + label);
                helper.assertTrue(pieces(layout, DungeonProgressionRooms.BOSS_ROOM).size() == 1,
                        "The dungeon below an entrance must reach its boss room: " + label);
            }
        }
        helper.succeed();
    }

    private static int keyCount(Player player, DungeonKeyType type) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (type.matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void place(ServerLevel level, StagedDungeonLayout layout, StagedDungeonPieceSpec piece) {
        StagedDungeonGenerationManager.placeSynchronously(level, List.of(piece), LiquidSettings.IGNORE_WATERLOGGING);
        DungeonLockManager.applyPlanForPiece(level, layout.lockPlan(), piece.boundingBox());
    }

    private static List<StagedDungeonPieceSpec> pieces(StagedDungeonLayout layout, Identifier template) {
        return layout.pieces().stream()
                .filter(piece -> piece.element() instanceof VariantSinglePoolElement variant && variant.templateLocation().equals(template))
                .toList();
    }

    private static List<ResourceKey<LootTable>> chestLoot(ServerLevel level, BoundingBox box) {
        List<ResourceKey<LootTable>> lootTables = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.getLootTable() != null) {
                lootTables.add(chest.getLootTable());
            }
        }
        return lootTables;
    }

    private static List<Identifier> ejectedLoot(List<Weighted<ResourceKey<LootTable>>> lootTables) {
        return lootTables.stream().map(weighted -> weighted.value().identifier()).toList();
    }

    private static List<DungeonTier> lootSequence(long seed) {
        Random random = new Random(seed);
        List<DungeonTier> result = new ArrayList<>();
        for (DungeonTier tier : DungeonTier.values()) for (int roll = 0; roll < 32; roll++) result.add(tier.randomLootTier(random));
        return result;
    }

    public record TestCase(String path, Consumer<GameTestHelper> function) {}
}
