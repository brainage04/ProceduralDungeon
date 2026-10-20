package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.command.GenerateDungeonCommand;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.guardian.DungeonGuardian;
import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockSaveData;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonChunkLedger;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonProgressionRooms;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonPuzzleRooms;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonPieceSpec;
import com.github.brainage04.procedural_dungeon.worldgen.structure.VariantSinglePoolElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lock, protection and encounter tests. They drive the same entry points a real player reaches (the
 * server's right-click and block-break paths, hoppers, pistons, fluids, explosions, entity
 * removal), so a hook that lets an action through fails here.
 */
public final class LockGameTests {
    private static final ResourceKey<LootTable> CHEST_LOOT =
            ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ProceduralDungeon.of("hallway_end/key_source/cobblestone/tier_1"));
    // Dungeons for these tests are built far from the test grid, each in its own area. The test
    // world is kept between
    // runs and lock records persist with it, so every run builds somewhere new.
    private static final int RUN_X = -40960 - 1024 * new Random().nextInt(4096);
    private static final AtomicInteger SLOTS = new AtomicInteger();

    private LockGameTests() {}

    public static List<DungeonGameTestSuite.TestCase> tests() {
        return List.of(
                new DungeonGameTestSuite.TestCase(
                        "locked_chests_stay_shut_through_the_loader_use_hook",
                        LockGameTests::lockedChestsStayShutThroughTheLoaderUseHook),
                new DungeonGameTestSuite.TestCase(
                        "sealed_boss_rooms_resist_digging_and_keep_their_door",
                        LockGameTests::sealedBossRoomsResistDiggingAndKeepTheirDoor),
                new DungeonGameTestSuite.TestCase(
                        "pistons_fluids_and_blasts_cannot_open_a_sealed_room",
                        LockGameTests::pistonsFluidsAndBlastsCannotOpenASealedRoom),
                new DungeonGameTestSuite.TestCase(
                        "puzzle_machinery_resists_digging_until_solved",
                        LockGameTests::puzzleMachineryResistsDiggingUntilSolved),
                new DungeonGameTestSuite.TestCase(
                        "hoppers_and_joined_chests_cannot_drain_locked_chests",
                        LockGameTests::hoppersAndJoinedChestsCannotDrainLockedChests),
                new DungeonGameTestSuite.TestCase(
                        "broken_key_chests_lose_explosion_protection",
                        LockGameTests::brokenKeyChestsLoseExplosionProtection),
                new DungeonGameTestSuite.TestCase(
                        "lock_reads_do_not_create_lock_data",
                        LockGameTests::lockReadsDoNotCreateLockData),
                new DungeonGameTestSuite.TestCase(
                        "lock_data_is_versioned_and_old_records_load",
                        LockGameTests::lockDataIsVersionedAndOldRecordsLoad),
                new DungeonGameTestSuite.TestCase(
                        "lock_plans_apply_by_chunk", LockGameTests::lockPlansApplyByChunk),
                new DungeonGameTestSuite.TestCase(
                        "boss_loot_waits_for_the_guardian",
                        LockGameTests::bossLootWaitsForTheGuardian),
                new DungeonGameTestSuite.TestCase(
                        "boss_key_burnt_in_lava_is_reissued_and_retired",
                        LockGameTests::bossKeyBurntInLavaIsReissuedAndRetired),
                new DungeonGameTestSuite.TestCase(
                        "stored_and_moved_boss_keys_are_not_reissued",
                        LockGameTests::storedAndMovedBossKeysAreNotReissued),
                new DungeonGameTestSuite.TestCase(
                        "cleared_and_creative_deleted_boss_keys_are_reissued",
                        LockGameTests::clearedAndCreativeDeletedBossKeysAreReissued),
                new DungeonGameTestSuite.TestCase(
                        "operators_can_reissue_a_lost_boss_key",
                        LockGameTests::operatorsCanReissueALostBossKey),
                new DungeonGameTestSuite.TestCase(
                        "door_locks_follow_their_chunk_not_the_lock_index",
                        LockGameTests::doorLocksFollowTheirChunkNotTheLockIndex));
    }

    /**
     * A right-click goes through {@code ServerPlayerGameMode.useItemOn}, which fires the loader's
     * use hook (Fabric's {@code UseBlockCallback}, NeoForge's {@code RightClickBlock}). A refused
     * lock must keep vanilla from opening it.
     */
    public static void lockedChestsStayShutThroughTheLoaderUseHook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rusted = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos puzzle = helper.absolutePos(new BlockPos(3, 1, 1));
        for (BlockPos pos : List.of(rusted, puzzle)) {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            ((ChestBlockEntity) level.getBlockEntity(pos)).setLootTable(CHEST_LOOT, 1L);
        }
        DungeonLockManager.applyPlanForPiece(
                level,
                plan(List.of(rusted.asLong()), List.of(), List.of()),
                new BoundingBox(rusted));
        // No mechanism repeater sits under this chest, so its puzzle can never read as solved.
        DungeonLockManager.lockPuzzleChest(level, puzzle);

        ServerPlayer player = survivalPlayer(helper);
        try {
            for (BlockPos pos : List.of(rusted, puzzle)) {
                rightClick(player, level, pos);
                helper.assertTrue(
                        player.containerMenu == player.inventoryMenu,
                        "A refused lock must not open the chest at " + pos.toShortString());
                helper.assertTrue(
                        ((ChestBlockEntity) level.getBlockEntity(pos)).getLootTable() != null,
                        "A refused lock must leave the chest's loot unrolled at "
                                + pos.toShortString());
            }
            helper.assertTrue(
                    DungeonLockManager.isPuzzleLocked(level, puzzle),
                    "The puzzle chest must stay locked");

            player.getInventory().setItem(0, DungeonKeyType.RUSTED.createStack());
            rightClick(player, level, rusted);
            helper.assertTrue(
                    player.containerMenu instanceof ChestMenu,
                    "The matching key must open the chest through the same hook");
        } finally {
            remove(player);
        }
        helper.succeed();
    }

    /**
     * P05: a survival player cannot mine into a locked boss room, nor under its door; and a door
     * losing its support by any other means stays standing while locked.
     */
    public static void sealedBossRoomsResistDiggingAndKeepTheirDoor(GameTestHelper helper) {
        PlacedBoss boss = placeBoss(helper);
        ServerLevel level = boss.level();
        ServerPlayer player = survivalPlayer(helper);
        try {
            BlockPos wall = boss.walls().getFirst();
            BlockState wallState = level.getBlockState(wall);
            player.gameMode.destroyBlock(wall);
            helper.assertTrue(
                    level.getBlockState(wall).equals(wallState),
                    "A survival player must not mine a sealed boss room's wall at "
                            + wall.toShortString());

            BlockPos support = boss.door().below();
            BlockState supportState = level.getBlockState(support);
            player.gameMode.destroyBlock(support);
            helper.assertTrue(
                    level.getBlockState(support).equals(supportState),
                    "A survival player must not mine the block under a locked door");

            level.setBlock(support, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(
                    level.getBlockState(boss.door()).is(Blocks.IRON_DOOR)
                            && level.getBlockState(boss.door().above()).is(Blocks.IRON_DOOR),
                    "A locked door must keep standing when its support goes");
        } finally {
            remove(player);
            boss.release();
        }
        helper.succeed();
    }

    /**
     * P05: a piston outside a sealed room cannot push its wall, water outside cannot flow through a
     * gap, and a blast outside cannot break the wall.
     */
    public static void pistonsFluidsAndBlastsCannotOpenASealedRoom(GameTestHelper helper) {
        PlacedBoss boss = placeBoss(helper);
        ServerLevel level = boss.level();
        List<BlockPos> walls = boss.walls();
        BlockPos pushed = walls.getFirst();
        BlockPos flooded = walls.get(walls.size() / 2);
        BlockPos blasted = walls.getLast();
        BlockState pushedState = level.getBlockState(pushed);
        BlockState blastedState = level.getBlockState(blasted);

        Direction inward = boss.inward(pushed);
        BlockPos piston = pushed.relative(inward.getOpposite());
        level.setBlock(
                piston,
                Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, inward),
                3);
        level.setBlock(
                piston.relative(inward.getOpposite()),
                Blocks.REDSTONE_BLOCK.defaultBlockState(),
                3);

        // A gap made some other way must still not let water in from outside.
        level.setBlock(flooded, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(
                flooded.relative(boss.inward(flooded).getOpposite()),
                Blocks.WATER.defaultBlockState(),
                3);

        BlockPos blast = blasted.relative(boss.inward(blasted).getOpposite());
        level.setBlock(blast, Blocks.AIR.defaultBlockState(), 3);
        level.explode(
                null,
                blast.getX() + 0.5,
                blast.getY() + 0.5,
                blast.getZ() + 0.5,
                4.0F,
                Level.ExplosionInteraction.TNT);

        helper.runAfterDelay(
                20,
                () -> {
                    try {
                        helper.assertTrue(
                                level.getBlockState(pushed).equals(pushedState),
                                "A piston outside must not push a sealed room's wall");
                        helper.assertTrue(
                                level.getFluidState(flooded).isEmpty(),
                                "Water outside must not flow into a sealed room");
                        helper.assertTrue(
                                level.getBlockState(blasted).equals(blastedState),
                                "A blast outside must not break a sealed room's wall");
                    } finally {
                        boss.release();
                    }
                    helper.succeed();
                });
    }

    /**
     * P05: a machine puzzle's room, mechanism included, cannot be mined while its chest is held
     * shut.
     */
    public static void puzzleMachineryResistsDiggingUntilSolved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceKey<StructureTemplatePool> poolKey =
                RegistryKeyUtils.create(
                        Registries.TEMPLATE_POOL,
                        RegistryKeyUtils.getKeyString(DungeonTheme.SCULK, DungeonTier.TIER_1)
                                + "/hallway/room");
        StructurePoolElement element =
                level
                        .registryAccess()
                        .lookupOrThrow(Registries.TEMPLATE_POOL)
                        .getOrThrow(poolKey)
                        .value()
                        .getShuffledTemplates(RandomSource.create(0))
                        .stream()
                        .filter(
                                candidate ->
                                        candidate instanceof VariantSinglePoolElement variant
                                                && variant.templateLocation()
                                                        .equals(
                                                                DungeonPuzzleRooms.Puzzle.TARGETS
                                                                        .template()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        helper.assertionException(
                                                Component.literal(
                                                        "The sculk room pool must offer the targets puzzle")));
        BlockPos origin = nextOrigin(100);
        BoundingBox box =
                element.getBoundingBox(level.getStructureManager(), origin, Rotation.NONE);
        forceChunks(level, box, true);
        ServerPlayer player = survivalPlayer(helper);
        try {
            element.place(
                    level.getStructureManager(),
                    level,
                    level.structureManager(),
                    level.getChunkSource().getGenerator(),
                    origin,
                    origin,
                    Rotation.NONE,
                    box,
                    RandomSource.create(0),
                    LiquidSettings.IGNORE_WATERLOGGING,
                    false);
            BlockPos chest = origin.offset(9, 2, 11);
            helper.assertTrue(
                    DungeonLockManager.isPuzzleLocked(level, chest),
                    "The targets puzzle must hold its chest shut");
            BlockPos signal = chest.below();
            BlockState signalState = level.getBlockState(signal);
            helper.assertTrue(
                    !signalState.isAir(), "The targets puzzle's chest must stand on its mechanism");
            player.gameMode.destroyBlock(signal);
            helper.assertTrue(
                    level.getBlockState(signal).equals(signalState),
                    "A survival player must not mine an unsolved puzzle's mechanism");
        } finally {
            remove(player);
            forceChunks(level, box, false);
        }
        helper.succeed();
    }

    /**
     * P05: a hopper under a locked chest pulls nothing (and does not roll its loot), and a chest
     * placed beside a locked chest does not open the locked half.
     */
    public static void hoppersAndJoinedChestsCannotDrainLockedChests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos locked = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(
                locked.below(),
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN),
                3);
        level.setBlock(
                locked,
                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH),
                3);
        ((ChestBlockEntity) level.getBlockEntity(locked)).setLootTable(CHEST_LOOT, 1L);
        DungeonLockManager.applyPlanForPiece(
                level,
                plan(List.of(locked.asLong()), List.of(), List.of()),
                new BoundingBox(locked));

        BlockPos joined = locked.east();
        level.setBlock(
                locked, level.getBlockState(locked).setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
        level.setBlock(
                joined,
                Blocks.CHEST
                        .defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.RIGHT),
                3);
        level.setBlock(
                joined.below(),
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN),
                3);
        ServerPlayer player = survivalPlayer(helper);
        rightClick(player, level, joined);
        boolean joinedOpened = player.containerMenu != player.inventoryMenu;
        remove(player);

        helper.runAfterDelay(
                40,
                () -> {
                    helper.assertTrue(
                            !joinedOpened,
                            "A chest joined to a locked chest must not open the pair");
                    helper.assertTrue(
                            ((ChestBlockEntity) level.getBlockEntity(locked)).getLootTable()
                                    != null,
                            "A hopper must not roll a locked chest's loot");
                    for (BlockPos hopper : List.of(locked.below(), joined.below())) {
                        helper.assertTrue(
                                ((HopperBlockEntity) level.getBlockEntity(hopper)).isEmpty(),
                                "A hopper must not pull from a locked chest or its joined half at "
                                        + hopper.toShortString());
                    }
                    helper.succeed();
                });
    }

    /** P07: once a key chest is broken, its position is no longer protected from explosions. */
    public static void brokenKeyChestsLoseExplosionProtection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos keyChest = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(keyChest, Blocks.CHEST.defaultBlockState(), 3);
        DungeonLockManager.applyPlanForPiece(
                level,
                plan(
                        List.of(),
                        List.of(
                                new DungeonLockPlan.KeySource(
                                        keyChest.asLong(), CHEST_LOOT.identifier())),
                        List.of()),
                new BoundingBox(keyChest));
        helper.assertTrue(
                DungeonLockManager.isExplosionProtected(level, keyChest),
                "A key chest must resist explosions");

        ServerPlayer player = survivalPlayer(helper);
        try {
            player.gameMode.destroyBlock(keyChest);
        } finally {
            remove(player);
        }
        helper.assertTrue(
                level.getBlockState(keyChest).isAir(), "A key chest can be broken by hand");
        level.setBlock(keyChest, Blocks.STONE.defaultBlockState(), 3);
        level.explode(
                null,
                keyChest.getX() + 0.5,
                keyChest.getY() + 1.5,
                keyChest.getZ() + 0.5,
                4.0F,
                Level.ExplosionInteraction.TNT);
        helper.assertTrue(
                level.getBlockState(keyChest).isAir(),
                "A block placed where a key chest stood must not inherit its protection");
        helper.succeed();
    }

    /**
     * P08: lock queries in a dimension that never held a dungeon must not create (and dirty) lock
     * data there.
     */
    public static void lockReadsDoNotCreateLockData(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        boolean existed = nether.getDataStorage().get(DungeonLockSaveData.TYPE) != null;
        BlockPos pos = new BlockPos(0, 64, 0);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DungeonLockManager.isDoorLocked(nether, pos);
        DungeonLockManager.isExplosionProtected(nether, pos);
        DungeonLockManager.isPuzzleLocked(nether, pos);
        DungeonLockManager.canBreak(player, nether, pos);
        DungeonLockManager.useBlock(player, nether, pos);
        // Worlds that already held Nether lock data (from an older version) cannot show the
        // difference.
        helper.assertTrue(
                existed || nether.getDataStorage().get(DungeonLockSaveData.TYPE) == null,
                "Reading locks must not create lock data in a dimension without dungeons");
        helper.succeed();
    }

    /**
     * P09: lock data carries PD's own format version under a neutral datafix type, and old records
     * load.
     */
    public static void lockDataIsVersionedAndOldRecordsLoad(GameTestHelper helper) {
        helper.assertTrue(
                DungeonLockSaveData.TYPE.dataFixType() != DataFixTypes.SAVED_DATA_MAP_DATA,
                "Lock data must not claim the vanilla map datafix type");
        long chest = new BlockPos(1, 2, 3).asLong();
        long door = new BlockPos(4, 5, 6).asLong();
        JsonObject old =
                JsonParser.parseString(
                                "{\"locked_chests\":["
                                        + chest
                                        + "],\"key_doors\":[{\"pos\":"
                                        + door
                                        + ",\"key\":\"boss_key\"}],\"key_source_chests\":[],\"puzzle_chests\":[]}")
                        .getAsJsonObject();
        var codec = DungeonLockSaveData.TYPE.codec();
        DungeonLockSaveData loaded = codec.parse(JsonOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(
                loaded.requiredKey(chest).orElseThrow() == DungeonKeyType.RUSTED
                        && loaded.requiredKey(door).orElseThrow() == DungeonKeyType.BOSS,
                "A record without a format version must load its locks");
        JsonObject saved =
                codec.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow().getAsJsonObject();
        helper.assertTrue(
                saved.has("format_version") && saved.get("format_version").getAsInt() >= 1,
                "Saved lock data must record its format version: " + saved);
        helper.succeed();
    }

    /**
     * P20: applying a plan piece by piece visits only the entries in the chunks each piece touches,
     * not the whole plan for every piece.
     */
    public static void lockPlansApplyByChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = nextOrigin(0);
        StagedDungeonLayout layout =
                GenerateDungeonCommand.compileLayout(
                                level,
                                DungeonTheme.COBBLESTONE,
                                DungeonTier.TIER_5,
                                DungeonTier.TIER_5.size,
                                origin)
                        .orElseThrow(
                                () ->
                                        helper.assertionException(
                                                Component.literal("A tier 5 layout must compile")));
        DungeonLockPlan plan = layout.lockPlan();

        // The test's own count of entries per chunk; a room counts in every chunk its box touches.
        Long2IntOpenHashMap perChunk = new Long2IntOpenHashMap();
        plan.lockedChests().forEach(pos -> perChunk.addTo(ChunkPos.pack(BlockPos.of(pos)), 1));
        plan.keySources()
                .forEach(source -> perChunk.addTo(ChunkPos.pack(BlockPos.of(source.pos())), 1));
        plan.doors().forEach(door -> perChunk.addTo(ChunkPos.pack(BlockPos.of(door.pos())), 1));
        plan.rooms().forEach(room -> forEachChunk(room.box(), chunk -> perChunk.addTo(chunk, 1)));
        long expected = 0;
        int records =
                plan.lockedChests().size()
                        + plan.keySources().size()
                        + plan.doors().size()
                        + plan.rooms().size();
        for (StagedDungeonPieceSpec piece : layout.pieces()) {
            long[] sum = {0};
            forEachChunk(piece.boundingBox(), chunk -> sum[0] += perChunk.get(chunk));
            expected += sum[0];
        }

        long before = DungeonLockManager.planEntriesVisited();
        try {
            for (StagedDungeonPieceSpec piece : layout.pieces()) {
                DungeonLockManager.applyPlanForPiece(level, plan, piece.boundingBox());
            }
            long visited = DungeonLockManager.planEntriesVisited() - before;
            helper.assertTrue(
                    visited == expected && visited < (long) layout.pieces().size() * records,
                    "Applying %d pieces visited %d plan entries; the per-chunk index allows %d (a full scan is %d)"
                            .formatted(
                                    layout.pieces().size(),
                                    visited,
                                    expected,
                                    (long) layout.pieces().size() * records));
        } finally {
            DungeonLockManager.forget(level, layout.boundingBox());
        }
        helper.succeed();
    }

    /**
     * P29: opening the boss room with its key does not open the reward chests; the guardian's death
     * does.
     */
    public static void bossLootWaitsForTheGuardian(GameTestHelper helper) {
        PlacedBoss boss = placeBoss(helper);
        ServerLevel level = boss.level();
        ServerPlayer player = survivalPlayer(helper);
        // A freshly placed far chunk loads its entities a few ticks after the chunk itself.
        helper.runAfterDelay(
                20,
                () -> {
                    player.getInventory().setItem(0, boss.takeKey());
                    rightClick(player, level, boss.door());
                    helper.assertTrue(
                            level.getBlockState(boss.door()).getValue(DoorBlock.OPEN),
                            "The vault's key must open the boss door");
                    // A chest under a solid block cannot open at all; use one that can.
                    BlockPos reward =
                            boss.rewardChests().stream()
                                    .filter(pos -> !ChestBlock.isChestBlockedAt(level, pos))
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    helper.assertionException(
                                                            Component.literal(
                                                                    "The boss room must hold an openable chest")));
                    rightClick(player, level, reward);
                    helper.assertTrue(
                            player.containerMenu == player.inventoryMenu,
                            "A boss chest must stay shut while the boss lives");

                    Mob guardian = boss.guardian();
                    guardian.kill(level);
                    // A dead mob leaves the world when its death animation ends.
                    helper.runAfterDelay(
                            30,
                            () -> {
                                try {
                                    helper.assertTrue(
                                            guardian.isRemoved(), "The boss must be gone");
                                    rightClick(player, level, reward);
                                    helper.assertTrue(
                                            player.containerMenu instanceof ChestMenu,
                                            "A boss chest must open once the boss is dead");
                                } finally {
                                    remove(player);
                                    boss.release();
                                }
                                helper.succeed();
                            });
                });
    }

    /**
     * P30: a Boss Key burnt in lava is replaced by exactly one new key in the vault, and the burnt
     * key's copy no longer opens the door; the new key does.
     */
    public static void bossKeyBurntInLavaIsReissuedAndRetired(GameTestHelper helper) {
        PlacedBoss boss = placeBoss(helper);
        ServerLevel level = boss.level();
        ItemStack key = boss.takeKey();
        ItemStack copy = key.copy();
        BlockPos lava = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(lava, Blocks.LAVA.defaultBlockState(), 3);
        ItemEntity dropped =
                new ItemEntity(
                        level,
                        lava.getX() + 0.5,
                        lava.getY() + 0.2,
                        lava.getZ() + 0.5,
                        key,
                        0.0,
                        0.0,
                        0.0);
        level.addFreshEntity(dropped);
        helper.runAfterDelay(
                60,
                () -> {
                    ServerPlayer player = survivalPlayer(helper);
                    try {
                        helper.assertTrue(dropped.isRemoved(), "Lava must burn the dropped key");
                        List<ItemStack> vaultKeys = boss.vaultKeys();
                        helper.assertTrue(
                                vaultKeys.size() == 1,
                                "Exactly one new Boss Key must appear in the vault, found "
                                        + vaultKeys.size());
                        helper.assertTrue(
                                !ItemStack.isSameItemSameComponents(vaultKeys.getFirst(), copy),
                                "The new key must not be the burnt one");

                        player.getInventory().setItem(0, copy);
                        rightClick(player, level, boss.door());
                        helper.assertTrue(
                                !level.getBlockState(boss.door()).getValue(DoorBlock.OPEN),
                                "A copy of the burnt key must not open the door");

                        player.getInventory().setItem(0, boss.takeKey());
                        rightClick(player, level, boss.door());
                        helper.assertTrue(
                                level.getBlockState(boss.door()).getValue(DoorBlock.OPEN),
                                "The new key must open the door");
                    } finally {
                        level.setBlock(lava, Blocks.AIR.defaultBlockState(), 3);
                        remove(player);
                        boss.release();
                    }
                    helper.succeed();
                });
    }

    /** P30: a key put in a chest, or picked up by a hopper, still exists and is not reissued. */
    public static void storedAndMovedBossKeysAreNotReissued(GameTestHelper helper) {
        PlacedBoss boss = placeBoss(helper);
        ServerLevel level = boss.level();
        BlockPos chest = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((Container) level.getBlockEntity(chest)).setItem(0, boss.takeKey());

        // A dropped copy is not a valid key either way; drop the real one into a hopper.
        PlacedBoss second = placeBoss(helper);
        BlockPos hopper = helper.absolutePos(new BlockPos(3, 1, 1));
        level.setBlock(hopper, Blocks.HOPPER.defaultBlockState(), 3);
        ItemEntity dropped =
                new ItemEntity(
                        level,
                        hopper.getX() + 0.5,
                        hopper.getY() + 1.2,
                        hopper.getZ() + 0.5,
                        second.takeKey(),
                        0.0,
                        0.0,
                        0.0);
        level.addFreshEntity(dropped);
        helper.runAfterDelay(
                40,
                () -> {
                    try {
                        helper.assertTrue(
                                dropped.isRemoved()
                                        && DungeonKeyType.BOSS.matches(
                                                ((Container) level.getBlockEntity(hopper))
                                                        .getItem(0)),
                                "The hopper must pick the key up");
                        helper.assertTrue(
                                boss.vaultKeys().isEmpty() && second.vaultKeys().isEmpty(),
                                "A key in a chest or picked up by a hopper must not be reissued");
                    } finally {
                        boss.release();
                        second.release();
                    }
                    helper.succeed();
                });
    }

    /**
     * P30: {@code /clear} and deleting the key from a creative inventory both reissue it; a
     * creative player moving the key to another slot does not.
     */
    public static void clearedAndCreativeDeletedBossKeysAreReissued(GameTestHelper helper) {
        PlacedBoss cleared = placeBoss(helper);
        PlacedBoss deleted = placeBoss(helper);
        PlacedBoss moved = placeBoss(helper);
        ServerLevel level = cleared.level();
        ServerPlayer survivor = survivalPlayer(helper);
        survivor.getInventory().setItem(0, cleared.takeKey());
        // Every mock player has the same name, and /clear refuses a UUID selector; a tag picks this
        // one.
        String target = "procedural_dungeon.clear_target_" + survivor.getId();
        survivor.addTag(target);
        int removed;
        try {
            removed =
                    level.getServer()
                            .getCommands()
                            .getDispatcher()
                            .execute(
                                    "clear @a[tag=" + target + "]",
                                    level.getServer()
                                            .createCommandSourceStack()
                                            .withSuppressedOutput());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw helper.assertionException(
                    Component.literal("/clear must accept the player: " + exception.getMessage()));
        }
        helper.assertTrue(
                removed > 0 && survivor.getInventory().isEmpty(),
                "/clear must remove the key, removed " + removed);
        helper.assertTrue(
                cleared.vaultKeys().size() == 1, "A key removed by /clear must be reissued");

        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        creative.setGameMode(GameType.CREATIVE);
        // Inventory menu slots 36-44 are the hotbar.
        creative.getInventory().setItem(0, deleted.takeKey());
        creative.getInventory().setItem(1, moved.takeKey());
        ItemStack movedKey = creative.getInventory().getItem(1).copy();
        creative.connection.handleSetCreativeModeSlot(
                new ServerboundSetCreativeModeSlotPacket(36, ItemStack.EMPTY));
        creative.connection.handleSetCreativeModeSlot(
                new ServerboundSetCreativeModeSlotPacket(37, ItemStack.EMPTY));
        creative.connection.handleSetCreativeModeSlot(
                new ServerboundSetCreativeModeSlotPacket(40, movedKey));
        helper.runAfterDelay(
                640,
                () -> {
                    try {
                        helper.assertTrue(
                                deleted.vaultKeys().size() == 1,
                                "A key deleted in creative must be reissued");
                        helper.assertTrue(
                                moved.vaultKeys().isEmpty(),
                                "A key a creative player only moved must not be reissued");
                    } finally {
                        remove(survivor);
                        remove(creative);
                        cleared.release();
                        deleted.release();
                        moved.release();
                    }
                    helper.succeed();
                });
    }

    /**
     * A key lost where no hook sees it (a crash rollback, a corrupted file) is reissued by an
     * operator command; the lost key's id retires like any other.
     */
    public static void operatorsCanReissueALostBossKey(GameTestHelper helper) {
        PlacedBoss boss = placeBoss(helper);
        ServerLevel level = boss.level();
        ItemStack lost = boss.takeKey();
        var server = level.getServer();
        var dispatcher = server.getCommands().getDispatcher();
        String command =
                "dungeon bosskey reissue %d %d %d"
                        .formatted(boss.door().getX(), boss.door().getY(), boss.door().getZ());
        var operator = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        var unprivileged =
                operator.withPermission(
                        net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS);
        ServerPlayer player = survivalPlayer(helper);
        try {
            helper.assertTrue(
                    dispatcher.getRoot().getChild("dungeon") != null
                            && !dispatcher.getRoot().getChild("dungeon").canUse(unprivileged),
                    "The dungeon command must exist and reject non-operators");
            int result;
            try {
                result = dispatcher.execute(command, operator);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                throw helper.assertionException(
                        Component.literal(
                                "The reissue command must accept a position: "
                                        + exception.getMessage()));
            }
            helper.assertTrue(
                    result == 1 && boss.vaultKeys().size() == 1,
                    "Reissuing must put one new Boss Key in the vault");
            player.getInventory().setItem(0, lost);
            rightClick(player, level, boss.door());
            helper.assertTrue(
                    !level.getBlockState(boss.door()).getValue(DoorBlock.OPEN),
                    "The lost key must not open the door after a reissue");
            player.getInventory().setItem(0, boss.takeKey());
            rightClick(player, level, boss.door());
            helper.assertTrue(
                    level.getBlockState(boss.door()).getValue(DoorBlock.OPEN),
                    "The reissued key must open the door");
        } finally {
            remove(player);
            boss.release();
        }
        helper.succeed();
    }

    /**
     * P04: a door's lock is saved with its chunk. If the level's lock index was saved before an
     * unlock and the chunk after it (a crash between the two), the reloaded chunk keeps the door
     * unlocked; if the chunk is the older one, its door comes back locked.
     */
    public static void doorLocksFollowTheirChunkNotTheLockIndex(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = nextOrigin(100);
        BlockPos upper = lower.above();
        BoundingBox box = new BoundingBox(lower);
        forceChunks(level, box, true);
        try {
            level.setBlock(lower.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(
                    lower,
                    Blocks.IRON_DOOR
                            .defaultBlockState()
                            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER),
                    3);
            level.setBlock(
                    upper,
                    Blocks.IRON_DOOR
                            .defaultBlockState()
                            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
                    3);
            DungeonLockManager.applyPlanForPiece(
                    level,
                    plan(
                            List.of(),
                            List.of(),
                            List.of(
                                    new DungeonLockPlan.Door(
                                            lower.asLong(), Optional.of(DungeonKeyType.BOSS)))),
                    box);
            var codec = DungeonLockSaveData.TYPE.codec();
            var indexBeforeUnlock =
                    codec.encodeStart(
                                    JsonOps.INSTANCE,
                                    level.getDataStorage().get(DungeonLockSaveData.TYPE))
                            .getOrThrow();

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.getInventory().add(DungeonKeyType.BOSS.createStack());
            DungeonLockManager.useBlock(player, level, lower);
            helper.assertTrue(
                    level.getBlockState(lower).getValue(DoorBlock.OPEN),
                    "The key must open the door");

            // The index on disk predates the unlock; the chunk does not.
            level.getDataStorage()
                    .set(
                            DungeonLockSaveData.TYPE,
                            codec.parse(JsonOps.INSTANCE, indexBeforeUnlock).getOrThrow());
            LevelChunk chunk = level.getChunk(lower.getX() >> 4, lower.getZ() >> 4);
            DungeonLockManager.onChunkLoad(level, chunk);
            helper.assertTrue(
                    !DungeonLockManager.isDoorLocked(level, lower)
                            && !DungeonLockManager.isDoorLocked(level, upper),
                    "A door unlocked in its saved chunk must not lock again from an older lock index");

            // Now the chunk is the older one: it still holds the locked, shut door.
            CompoundTag locked = new CompoundTag();
            locked.putString("door", DungeonKeyType.BOSS.getSerializedName());
            DungeonChunkLedger.putLockEntry(chunk, lower.asLong(), locked);
            DungeonChunkLedger.putLockEntry(chunk, upper.asLong(), locked);
            DungeonLockManager.onChunkLoad(level, chunk);
            helper.assertTrue(
                    DungeonLockManager.isDoorLocked(level, lower)
                            && DungeonLockManager.isDoorLocked(level, upper),
                    "A door saved locked in its chunk must be locked after the chunk loads");
        } finally {
            DungeonLockManager.forget(level, box.inflatedBy(1));
            forceChunks(level, box, false);
        }
        helper.succeed();
    }

    // ---- Fixtures

    /**
     * A tier-1 boss room and its key vault, placed far away with their locks applied and chunks
     * forced.
     */
    private static PlacedBoss placeBoss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = nextOrigin(0);
        StagedDungeonLayout layout =
                GenerateDungeonCommand.compileLayout(
                                level,
                                DungeonTheme.COBBLESTONE,
                                DungeonTier.TIER_1,
                                DungeonTier.TIER_1.size,
                                origin)
                        .orElseThrow(
                                () ->
                                        helper.assertionException(
                                                Component.literal("Layout must compile")));
        StagedDungeonPieceSpec room = piece(helper, layout, DungeonProgressionRooms.BOSS_ROOM);
        StagedDungeonPieceSpec vault =
                piece(helper, layout, DungeonProgressionRooms.BOSS_KEY_VAULT);
        forceChunks(level, room.boundingBox(), true);
        forceChunks(level, vault.boundingBox(), true);
        for (StagedDungeonPieceSpec piece : List.of(room, vault)) {
            StagedDungeonGenerationManager.placeSynchronously(
                    level, List.of(piece), LiquidSettings.IGNORE_WATERLOGGING);
            DungeonLockManager.applyPlanForPiece(level, layout.lockPlan(), piece.boundingBox());
        }
        BlockPos door =
                BlockPos.of(
                        layout.lockPlan().doors().stream()
                                .filter(
                                        planned ->
                                                planned.lock()
                                                        .equals(Optional.of(DungeonKeyType.BOSS)))
                                .findFirst()
                                .orElseThrow()
                                .pos());
        BlockPos vaultChest =
                BlockPos.of(
                        layout.lockPlan().keySources().stream()
                                .filter(
                                        source ->
                                                source.lootTable()
                                                        .getPath()
                                                        .startsWith("boss_key_vault/"))
                                .findFirst()
                                .orElseThrow()
                                .pos());
        return new PlacedBoss(
                helper, level, room.boundingBox(), vault.boundingBox(), door, vaultChest);
    }

    private record PlacedBoss(
            GameTestHelper helper,
            ServerLevel level,
            BoundingBox room,
            BoundingBox vault,
            BlockPos door,
            BlockPos vaultChest) {
        ChestBlockEntity vaultEntity() {
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(vaultChest);
            if (chest.getLootTable() != null) {
                chest.unpackLootTable(null);
            }
            return chest;
        }

        List<ItemStack> vaultKeys() {
            ChestBlockEntity chest = vaultEntity();
            List<ItemStack> keys = new ArrayList<>();
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (DungeonKeyType.BOSS.matches(chest.getItem(slot))) {
                    keys.add(chest.getItem(slot));
                }
            }
            return keys;
        }

        /** Takes the Boss Key out of the vault chest. */
        ItemStack takeKey() {
            ChestBlockEntity chest = vaultEntity();
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (DungeonKeyType.BOSS.matches(chest.getItem(slot))) {
                    return chest.removeItemNoUpdate(slot);
                }
            }
            throw helper.assertionException(Component.literal("The vault must hold a Boss Key"));
        }

        List<BlockPos> rewardChests() {
            List<BlockPos> chests = new ArrayList<>();
            for (BlockPos pos :
                    BlockPos.betweenClosed(
                            room.minX(),
                            room.minY(),
                            room.minZ(),
                            room.maxX(),
                            room.maxY(),
                            room.maxZ())) {
                if (level.getBlockEntity(pos) instanceof ChestBlockEntity) {
                    chests.add(pos.immutable());
                }
            }
            return chests;
        }

        Mob guardian() {
            List<Mob> guardians =
                    level.getEntitiesOfClass(
                            Mob.class,
                            AABB.of(room),
                            mob -> mob.entityTags().contains(DungeonGuardian.TAG));
            if (guardians.size() != 1) {
                throw helper.assertionException(
                        Component.literal(
                                "The boss room must hold one guardian, found " + guardians.size()));
            }
            return guardians.getFirst();
        }

        /** The direction from a wall block into the room. */
        Direction inward(BlockPos wall) {
            if (wall.getX() == room.minX()) return Direction.EAST;
            if (wall.getX() == room.maxX()) return Direction.WEST;
            if (wall.getZ() == room.minZ()) return Direction.SOUTH;
            return Direction.NORTH;
        }

        /** Pushable side-wall blocks with air behind them, away from the door. */
        List<BlockPos> walls() {
            List<BlockPos> walls = new ArrayList<>();
            for (BlockPos pos :
                    BlockPos.betweenClosed(
                            room.minX(),
                            room.minY() + 1,
                            room.minZ(),
                            room.maxX(),
                            room.maxY() - 1,
                            room.maxZ())) {
                boolean side =
                        pos.getX() == room.minX()
                                || pos.getX() == room.maxX()
                                || pos.getZ() == room.minZ()
                                || pos.getZ() == room.maxZ();
                boolean corner =
                        (pos.getX() == room.minX() || pos.getX() == room.maxX())
                                && (pos.getZ() == room.minZ() || pos.getZ() == room.maxZ());
                if (!side || corner || pos.distManhattan(door) < 4) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.isSolidRender()
                        && state.getPistonPushReaction() == PushReaction.NORMAL
                        && !state.hasBlockEntity()
                        && level.getBlockState(pos.relative(inward(pos))).isAir()) {
                    walls.add(pos.immutable());
                }
            }
            if (walls.size() < 3) {
                throw helper.assertionException(
                        Component.literal(
                                "The boss room must have plain wall blocks, found "
                                        + walls.size()));
            }
            return walls;
        }

        void release() {
            Mob guardian =
                    level
                            .getEntitiesOfClass(
                                    Mob.class,
                                    AABB.of(room),
                                    mob -> mob.entityTags().contains(DungeonGuardian.TAG))
                            .stream()
                            .findFirst()
                            .orElse(null);
            if (guardian != null) {
                guardian.discard();
            }
            forceChunks(level, room, false);
            forceChunks(level, vault, false);
        }
    }

    private static StagedDungeonPieceSpec piece(
            GameTestHelper helper,
            StagedDungeonLayout layout,
            net.minecraft.resources.Identifier template) {
        return layout.pieces().stream()
                .filter(
                        piece ->
                                piece.element() instanceof VariantSinglePoolElement variant
                                        && variant.templateLocation().equals(template))
                .findFirst()
                .orElseThrow(
                        () ->
                                helper.assertionException(
                                        Component.literal("The layout must hold " + template)));
    }

    private static BlockPos nextOrigin(int y) {
        int slot = SLOTS.getAndIncrement();
        return new BlockPos(RUN_X - 512 * (slot % 8), y == 0 ? 32 : y, -40960 - 512 * (slot / 8));
    }

    private static DungeonLockPlan plan(
            List<Long> lockedChests,
            List<DungeonLockPlan.KeySource> keySources,
            List<DungeonLockPlan.Door> doors) {
        return new DungeonLockPlan(lockedChests, keySources, doors, List.of());
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void remove(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    private static void forceChunks(ServerLevel level, BoundingBox box, boolean forced) {
        forEachChunk(
                box,
                chunk -> level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), forced));
    }

    private static void forEachChunk(BoundingBox box, java.util.function.LongConsumer action) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                action.accept(ChunkPos.pack(x, z));
            }
        }
    }

    static void rightClick(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.closeContainer();
        player.gameMode.useItemOn(
                player,
                level,
                player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
}
