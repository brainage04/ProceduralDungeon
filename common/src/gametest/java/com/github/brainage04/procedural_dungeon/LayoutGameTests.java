package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.command.GenerateDungeonCommand;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonSpecLoader;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.guardian.DungeonGuardian;
import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonGenerationProfiler;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonProgressionRooms;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayoutCompiler;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonPieceSpec;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonStructure;
import com.github.brainage04.procedural_dungeon.worldgen.structure.VariantSinglePoolElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Layout compilation, natural world generation, and placement-processor tests. */
public final class LayoutGameTests {
    private static final Identifier NETHER_STRUCTURE =
            ProceduralDungeon.of("dungeon/the_nether/soul_sand_valley/tier_2");
    private static final int LAYOUTS_PER_TIER = 50;
    // Each natural-generation run builds in fresh chunks, because the test world is kept between
    // runs.
    private static final AtomicInteger NATURAL_RUNS = new AtomicInteger();

    private LayoutGameTests() {}

    public static List<DungeonGameTestSuite.TestCase> tests() {
        return List.of(
                new DungeonGameTestSuite.TestCase(
                        "released_servers_read_specs_from_the_jar",
                        LayoutGameTests::releasedServersReadSpecsFromTheJar),
                new DungeonGameTestSuite.TestCase(
                        "solid_density_samples_each_column_once",
                        LayoutGameTests::solidDensitySamplesEachColumnOnce),
                new DungeonGameTestSuite.TestCase(
                        "weighted_templates_are_tried_once_per_socket",
                        LayoutGameTests::weightedTemplatesAreTriedOncePerSocket),
                new DungeonGameTestSuite.TestCase(
                        "boss_key_vault_is_on_another_branch",
                        LayoutGameTests::bossKeyVaultIsOnAnotherBranch),
                new DungeonGameTestSuite.TestCase(
                        "buried_progression_rooms_are_carved_out",
                        LayoutGameTests::buriedProgressionRoomsAreCarvedOut),
                new DungeonGameTestSuite.TestCase(
                        "natural_starts_build_in_the_overworld",
                        helper -> naturalStartsBuild(helper, Level.OVERWORLD)),
                new DungeonGameTestSuite.TestCase(
                        "natural_starts_build_in_the_nether",
                        helper -> naturalStartsBuild(helper, Level.NETHER)),
                new DungeonGameTestSuite.TestCase(
                        "natural_starts_build_in_the_end",
                        helper -> naturalStartsBuild(helper, Level.END)),
                new DungeonGameTestSuite.TestCase(
                        "terrain_is_buried_around_every_room",
                        LayoutGameTests::terrainIsBuriedAroundEveryRoom),
                new DungeonGameTestSuite.TestCase(
                        "geometry_caches_follow_reloaded_templates",
                        LayoutGameTests::geometryCachesFollowReloadedTemplates));
    }

    /**
     * A server whose working directory sits inside a folder that looks like a ProceduralDungeon
     * checkout must still read its specs from the jar, unless development mode asks for the source
     * tree.
     */
    public static void releasedServersReadSpecsFromTheJar(GameTestHelper helper) {
        Path checkout;
        try {
            checkout = Files.createTempDirectory("procedural_dungeon_specs");
            Path specs =
                    Files.createDirectories(
                            checkout.resolve("common/src/main/resources/procedural_dungeon"));
            Files.writeString(specs.resolve("tiers.json"), "{\"planted\": true}");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        Path workingDirectory = checkout.resolve("run/server");

        JsonObject released = DungeonSpecLoader.load("tiers.json", workingDirectory);
        helper.assertTrue(
                !released.has("planted"),
                "Without the development flag the jar's tiers.json must win");

        String previous = System.getProperty(DungeonSpecLoader.SOURCE_SPECS_PROPERTY);
        System.setProperty(DungeonSpecLoader.SOURCE_SPECS_PROPERTY, "true");
        try {
            helper.assertTrue(
                    DungeonSpecLoader.load("tiers.json", workingDirectory).has("planted"),
                    "With the development flag the source tree's tiers.json must win");
        } finally {
            if (previous == null) {
                System.clearProperty(DungeonSpecLoader.SOURCE_SPECS_PROPERTY);
            } else {
                System.setProperty(DungeonSpecLoader.SOURCE_SPECS_PROPERTY, previous);
            }
        }
        helper.succeed();
    }

    /**
     * Nether starts pick their height from 25 terrain columns. Each column must be sampled once per
     * start, not once per candidate height, and the chosen height must be the window with the most
     * solid ground.
     */
    public static void solidDensitySamplesEachColumnOnce(GameTestHelper helper) {
        StagedDungeonStructure structure =
                (StagedDungeonStructure)
                        helper.getLevel()
                                .registryAccess()
                                .lookupOrThrow(Registries.STRUCTURE)
                                .getValue(NETHER_STRUCTURE);
        helper.assertTrue(
                structure != null, "The Nether structure must be registered: " + NETHER_STRUCTURE);
        LevelHeightAccessor height = LevelHeightAccessor.create(0, 256);
        // Solid ground from y 40 to 70, plus a solid pillar of varying height in each column.
        AtomicInteger samples = new AtomicInteger();
        StagedDungeonStructure.ColumnSampler sampler =
                (x, z) -> {
                    samples.incrementAndGet();
                    return syntheticColumn(height, x, z);
                };

        int chosen = structure.selectSolidDensityY(height, sampler, 8, 8, -1);
        helper.assertTrue(
                samples.get() == 25,
                "Solid density must sample 25 columns once each, sampled " + samples.get());

        int expected = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int candidate = 24; candidate <= 104; candidate += 4) {
            int score = 0;
            for (int x = 8 - 16; x <= 8 + 16; x += 8) {
                for (int z = 8 - 16; z <= 8 + 16; z += 8) {
                    NoiseColumn column = syntheticColumn(height, x, z);
                    for (int y = candidate; y < candidate + 24; y++) {
                        BlockState state = column.getBlock(y);
                        if (!state.isAir() && state.getFluidState().isEmpty()) {
                            score++;
                        }
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                expected = candidate;
            }
        }
        helper.assertTrue(
                chosen == expected,
                "Solid density must choose y %d, chose %d".formatted(expected, chosen));
        helper.succeed();
    }

    private static NoiseColumn syntheticColumn(LevelHeightAccessor height, int x, int z) {
        BlockState[] states = new BlockState[height.getHeight()];
        int pillarTop = 70 + Math.floorMod(x * 31 + z * 17, 29);
        for (int y = 0; y < states.length; y++) {
            boolean solid =
                    (y >= 40 && y <= 70)
                            || (y > 70 && y <= pillarTop && Math.floorMod(x + z, 16) == 0);
            states[y] =
                    solid
                            ? Blocks.NETHERRACK.defaultBlockState()
                            : y < 32
                                    ? Blocks.LAVA.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(height.getMinY(), states);
    }

    /**
     * Pools list each template once per weight. A socket where nothing fits must try each template
     * once, not once per weight.
     */
    public static void weightedTemplatesAreTriedOncePerSocket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(-24576, 40, 24576);
        // Every hallway is longer than the bounds allow, so every socket of the start room fails.
        DungeonGenerationProfiler.begin();
        Optional<StagedDungeonLayout> layout;
        DungeonGenerationProfiler.Snapshot profile;
        try {
            layout =
                    compile(
                            level,
                            DungeonTheme.COBBLESTONE,
                            DungeonTier.TIER_1,
                            origin,
                            new JigsawStructure.MaxDistance(8));
        } finally {
            profile = DungeonGenerationProfiler.finish();
        }
        helper.assertTrue(
                layout.isEmpty(), "A start room whose hallways cannot fit must not compile");
        helper.assertTrue(
                profile.graphSourceJigsaws() > 0 && !profile.graphCandidateCounts().isEmpty(),
                "The profiler must record the failed sockets");
        for (DungeonGenerationProfiler.GraphCandidateCount count : profile.graphCandidateCounts()) {
            helper.assertTrue(
                    count.attempts() <= profile.graphSourceJigsaws(),
                    "%s was tried %d times for %d sockets"
                            .formatted(
                                    count.template(),
                                    count.attempts(),
                                    profile.graphSourceJigsaws()));
        }
        helper.succeed();
    }

    /**
     * The boss key vault must hang off a different branch than the boss room: their routes split at
     * the hub, the first piece below the root with more than one child. Most seeds must still
     * compile.
     */
    public static void bossKeyVaultIsOnAnotherBranch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (DungeonTier tier : DungeonTier.values()) {
            int compiled = 0;
            for (int seed = 0; seed < LAYOUTS_PER_TIER; seed++) {
                BlockPos origin = new BlockPos(16384 + seed * 1024, 40, -16384 - tier.tier * 8192);
                Optional<StagedDungeonLayout> layout =
                        GenerateDungeonCommand.compileLayout(
                                level, DungeonTheme.COBBLESTONE, tier, tier.size, origin);
                if (layout.isEmpty()) {
                    continue;
                }
                compiled++;
                StagedDungeonLayout dungeon = layout.get();
                String label = "tier %d at %s".formatted(tier.tier, origin.toShortString());
                helper.assertTrue(
                        dungeon.parents().size() == dungeon.pieces().size(),
                        "The layout must record every piece's parent: " + label);
                int boss = pieceIndex(dungeon, DungeonProgressionRooms.BOSS_ROOM);
                int vault = pieceIndex(dungeon, DungeonProgressionRooms.BOSS_KEY_VAULT);
                int hub = hub(dungeon.parents());
                int bossBranch = branch(dungeon.parents(), boss, hub);
                int vaultBranch = branch(dungeon.parents(), vault, hub);
                helper.assertTrue(
                        bossBranch != vaultBranch,
                        "The vault (piece %d) must not share branch %d with the boss room (piece %d): %s"
                                .formatted(vault, bossBranch, boss, label));
            }
            helper.assertTrue(
                    compiled * 10 >= LAYOUTS_PER_TIER * 9,
                    "At least 90%% of tier %d layouts must compile, %d of %d did"
                            .formatted(tier.tier, compiled, LAYOUTS_PER_TIER));
        }
        helper.succeed();
    }

    private static int pieceIndex(StagedDungeonLayout layout, Identifier template) {
        for (int i = 0; i < layout.pieces().size(); i++) {
            if (layout.pieces().get(i).element() instanceof VariantSinglePoolElement variant
                    && variant.templateLocation().equals(template)) {
                return i;
            }
        }
        throw new AssertionError("The layout has no " + template);
    }

    private static int hub(List<Integer> parents) {
        int hub = 0;
        while (true) {
            int onlyChild = -1;
            int children = 0;
            for (int i = 0; i < parents.size(); i++) {
                if (parents.get(i) == hub) {
                    onlyChild = i;
                    children++;
                }
            }
            if (children != 1) {
                return hub;
            }
            hub = onlyChild;
        }
    }

    private static int branch(List<Integer> parents, int piece, int hub) {
        int current = piece;
        while (current != -1) {
            int parent = parents.get(current);
            if (parent == hub) {
                return current;
            }
            current = parent;
        }
        throw new AssertionError("Piece %d does not hang below the hub %d".formatted(piece, hub));
    }

    /**
     * Boss, vault, and miniboss rooms buried in solid stone must come out hollow: every air cell of
     * the template is air in the world, including the guardian's spawn cell, so nothing blocks the
     * room or entombs the guardian.
     */
    public static void buriedProgressionRoomsAreCarvedOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int run = NATURAL_RUNS.getAndIncrement();
        BlockPos origin = new BlockPos(-20480 - run * 512, 40, -20480);
        DungeonTier tier = DungeonTier.TIER_3;
        StagedDungeonLayout layout =
                GenerateDungeonCommand.compileLayout(
                                level, DungeonTheme.COBBLESTONE, tier, tier.size, origin)
                        .orElseThrow(() -> new AssertionError("Layout must compile"));
        List<StagedDungeonPieceSpec> rooms = new ArrayList<>();
        rooms.add(layout.pieces().get(pieceIndex(layout, DungeonProgressionRooms.BOSS_ROOM)));
        rooms.add(layout.pieces().get(pieceIndex(layout, DungeonProgressionRooms.BOSS_KEY_VAULT)));
        StructurePoolElement miniboss =
                roomPoolElement(
                        level,
                        DungeonTheme.COBBLESTONE,
                        tier,
                        DungeonProgressionRooms.MINIBOSS_ROOM);
        BlockPos minibossPos = origin.offset(0, 0, -256);
        rooms.add(
                new StagedDungeonPieceSpec(
                        miniboss,
                        minibossPos,
                        Rotation.CLOCKWISE_90,
                        miniboss.getBoundingBox(
                                level.getStructureManager(), minibossPos, Rotation.CLOCKWISE_90),
                        0));

        StructureTemplateManager templates = level.getStructureManager();
        for (StagedDungeonPieceSpec room : rooms) {
            BoundingBox box = room.boundingBox();
            BoundingBox solid = box.inflatedBy(1);
            for (BlockPos pos :
                    BlockPos.betweenClosed(
                            solid.minX(),
                            solid.minY(),
                            solid.minZ(),
                            solid.maxX(),
                            solid.maxY(),
                            solid.maxZ())) {
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            StagedDungeonGenerationManager.placeSynchronously(
                    level, List.of(room), LiquidSettings.IGNORE_WATERLOGGING);

            Identifier template = ((VariantSinglePoolElement) room.element()).templateLocation();
            StructureTemplate structure = templates.getOrCreate(template);
            StructurePlaceSettings settings =
                    new StructurePlaceSettings().setRotation(room.rotation());
            List<BlockPos> hollow = new ArrayList<>();
            for (Block block : List.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.STRUCTURE_BLOCK)) {
                structure
                        .filterBlocks(room.position(), settings, block)
                        .forEach(info -> hollow.add(info.pos()));
            }
            helper.assertTrue(
                    hollow.size() > 100,
                    "The %s template must have an interior".formatted(template));
            List<String> filled =
                    hollow.stream()
                            .filter(pos -> !level.getBlockState(pos).isAir())
                            .map(
                                    pos ->
                                            "%s at %s"
                                                    .formatted(
                                                            level.getBlockState(pos).getBlock(),
                                                            pos.toShortString()))
                            .toList();
            helper.assertTrue(
                    filled.isEmpty(),
                    "Every air cell of buried %s must be air, %d of %d are not: %s"
                            .formatted(
                                    template,
                                    filled.size(),
                                    hollow.size(),
                                    filled.stream().limit(8).toList()));
            level.getEntitiesOfClass(
                            Mob.class,
                            AABB.of(box),
                            mob -> mob.entityTags().contains(DungeonGuardian.TAG))
                    .forEach(Mob::discard);
        }
        helper.succeed();
    }

    private static StructurePoolElement roomPoolElement(
            ServerLevel level, DungeonTheme theme, DungeonTier tier, Identifier template) {
        ResourceKey<StructureTemplatePool> roomPool =
                RegistryKeyUtils.create(
                        Registries.TEMPLATE_POOL,
                        "%s/hallway/room".formatted(RegistryKeyUtils.getKeyString(theme, tier)));
        return level
                .registryAccess()
                .lookupOrThrow(Registries.TEMPLATE_POOL)
                .getOrThrow(roomPool)
                .value()
                .getShuffledTemplates(RandomSource.create(0L))
                .stream()
                .filter(
                        element ->
                                element instanceof VariantSinglePoolElement variant
                                        && variant.templateLocation().equals(template))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The room pool must contain " + template));
    }

    /**
     * Natural generation end to end in one dimension: a seeded start from the dimension's default
     * world generator, its marker's post-processing, and the staged job it queues must build the
     * boss room, its locked door, and the key vault, then drain every job and release every
     * force-loaded chunk.
     */
    public static void naturalStartsBuild(GameTestHelper helper, ResourceKey<Level> dimension) {
        ServerLevel level = helper.getLevel().getServer().getLevel(dimension);
        helper.assertTrue(level != null, "The test server must have " + dimension.identifier());
        NaturalStart natural =
                naturalStart(level, dimension, NATURAL_RUNS.getAndIncrement(), pieces -> true);
        BoundingBox bounds = natural.start().getBoundingBox();
        // Generate the dungeon's chunks up front, so the ticks the test waits for only place
        // pieces.
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                level.getChunk(x, z);
            }
        }
        ChunkPos startChunk = natural.start().getChunkPos();
        BoundingBox startChunkBox =
                new BoundingBox(
                        startChunk.getMinBlockX(),
                        level.getMinY(),
                        startChunk.getMinBlockZ(),
                        startChunk.getMaxBlockX(),
                        level.getMaxY(),
                        startChunk.getMaxBlockZ());
        natural.start()
                .placeInChunk(
                        level,
                        level.structureManager(),
                        level.getChunkSource().getGenerator(),
                        RandomSource.create(0L),
                        startChunkBox,
                        startChunk);

        List<StagedDungeonPieceSpec> pieces = natural.pieces();
        BoundingBox bossBox =
                pieces.get(pieceIndex(pieces, DungeonProgressionRooms.BOSS_ROOM)).boundingBox();
        BoundingBox vaultBox =
                pieces.get(pieceIndex(pieces, DungeonProgressionRooms.BOSS_KEY_VAULT))
                        .boundingBox();
        String label = "%s start at %s".formatted(dimension.identifier(), startChunk);
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(
                            StagedDungeonGenerationManager.status(level).jobs() == 0
                                    || level.dimension() == Level.OVERWORLD,
                            "Every queued job must finish: " + label);
                    List<ResourceKey<LootTable>> bossLoot = chestLoot(level, bossBox);
                    helper.assertTrue(
                            bossLoot.size() == 2
                                    && bossLoot.stream()
                                            .allMatch(
                                                    loot ->
                                                            loot.identifier()
                                                                    .getPath()
                                                                    .startsWith("boss_room/")),
                            "The boss room must be built with its two chests: " + label);
                    // The vault is rolled from its tiered loot when placed, so its Boss Key can
                    // carry the door's key id.
                    helper.assertTrue(
                            boundBossKeys(level, vaultBox) == 1,
                            "The vault must be built holding one bound Boss Key: " + label);
                    BlockPos door =
                            BlockPos.betweenClosedStream(bossBox)
                                    .filter(pos -> level.getBlockState(pos).is(Blocks.IRON_DOOR))
                                    .findFirst()
                                    .map(BlockPos::immutable)
                                    .orElseThrow(
                                            () ->
                                                    new AssertionError(
                                                            "The boss room must have its door: "
                                                                    + label));
                    helper.assertTrue(
                            DungeonLockManager.isExplosionProtected(level, door),
                            "The boss room door must be locked: " + label);
                    if (level.dimension() != Level.OVERWORLD) {
                        helper.assertTrue(
                                level.getForceLoadedChunks().isEmpty(),
                                "Finished jobs must not leave force-loaded chunks: "
                                        + label
                                        + " "
                                        + level.getForceLoadedChunks());
                    }
                });
    }

    /**
     * Terrain adaptation must bury every room of a natural start, not only the layout's lowest
     * level: the ground density inside a room well above the lowest floor gets a burying
     * contribution.
     */
    public static void terrainIsBuriedAroundEveryRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(level != null, "The test server must have the Nether");
        NaturalStart natural =
                naturalStart(
                        level,
                        Level.NETHER,
                        NATURAL_RUNS.getAndIncrement(),
                        pieces -> !upperRooms(pieces).isEmpty());
        List<BoundingBox> upperRooms = upperRooms(natural.pieces());

        // The pieces of a non-jigsaw start enter the beardifier as their own boxes, as in
        // Beardifier.forStructuresInChunk.
        TerrainAdjustment adjustment = natural.start().getStructure().terrainAdaptation();
        List<Beardifier.Rigid> rigids =
                natural.start().getPieces().stream()
                        .map(piece -> new Beardifier.Rigid(piece.getBoundingBox(), adjustment, 0))
                        .toList();
        Beardifier beardifier =
                new Beardifier(rigids, List.of(), natural.start().getBoundingBox().inflatedBy(24));
        for (BoundingBox room : upperRooms) {
            BlockPos center = room.getCenter();
            double density =
                    beardifier.compute(
                            new DensityFunction.SinglePointContext(
                                    center.getX(), room.minY() + 1, center.getZ()));
            helper.assertTrue(
                    density > 0.0,
                    "The ground around the room at %s must be buried, density %.3f"
                            .formatted(center.toShortString(), density));
        }
        helper.succeed();
    }

    /** Pieces whose floor is beyond the burying reach of the lowest floor (12 blocks). */
    private static List<BoundingBox> upperRooms(List<StagedDungeonPieceSpec> pieces) {
        int lowestFloor =
                pieces.stream().mapToInt(piece -> piece.boundingBox().minY()).min().orElseThrow();
        return pieces.stream()
                .map(StagedDungeonPieceSpec::boundingBox)
                .filter(box -> box.minY() >= lowestFloor + 16)
                .toList();
    }

    private record NaturalStart(StructureStart start, List<StagedDungeonPieceSpec> pieces) {}

    /**
     * The first valid start of the dimension's tier 2 dungeon in a fresh area whose layout {@code
     * accept} takes, generated as by a default world.
     */
    private static NaturalStart naturalStart(
            ServerLevel level,
            ResourceKey<Level> dimension,
            int run,
            Predicate<List<StagedDungeonPieceSpec>> accept) {
        Identifier structureId;
        ResourceKey<NoiseGeneratorSettings> settings;
        if (dimension == Level.NETHER) {
            structureId = NETHER_STRUCTURE;
            settings = NoiseGeneratorSettings.NETHER;
        } else if (dimension == Level.END) {
            structureId = ProceduralDungeon.of("dungeon/the_end/end_city/tier_2");
            settings = NoiseGeneratorSettings.END;
        } else {
            structureId = ProceduralDungeon.of("dungeon/overworld/cobblestone/tier_2");
            settings = NoiseGeneratorSettings.OVERWORLD;
        }
        var registries = level.registryAccess();
        Holder<Structure> structure =
                registries
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(ResourceKey.create(Registries.STRUCTURE, structureId));
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        BiomeSource biomeSource =
                dimension == Level.END
                        ? TheEndBiomeSource.create(biomes)
                        : MultiNoiseBiomeSource.createFromPreset(
                                registries
                                        .lookupOrThrow(
                                                Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                                        .getOrThrow(
                                                dimension == Level.NETHER
                                                        ? MultiNoiseBiomeSourceParameterLists.NETHER
                                                        : MultiNoiseBiomeSourceParameterLists
                                                                .OVERWORLD));
        NoiseBasedChunkGenerator generator =
                new NoiseBasedChunkGenerator(
                        biomeSource,
                        registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settings));
        long seed = 0x5EED_D06EL;
        RandomState randomState = RandomState.create(registries, settings, seed);
        StructurePieceSerializationContext serialization =
                StructurePieceSerializationContext.fromLevel(level);

        // The End's outer islands start 1000 blocks out; the other dimensions have ground
        // everywhere.
        int baseChunk = 96 + run * 64;
        for (int attempt = 0; attempt < 256; attempt++) {
            ChunkPos chunk =
                    new ChunkPos(baseChunk + attempt % 16 * 3, baseChunk + attempt / 16 * 3);
            StructureStart start =
                    structure
                            .value()
                            .generate(
                                    structure,
                                    dimension,
                                    registries,
                                    generator,
                                    biomeSource,
                                    randomState,
                                    level.getStructureManager(),
                                    seed,
                                    chunk,
                                    0,
                                    level,
                                    biome -> true);
            if (start.isValid()) {
                List<StagedDungeonPieceSpec> pieces = markerPieces(start, serialization);
                if (accept.test(pieces)) {
                    return new NaturalStart(start, pieces);
                }
            }
        }
        throw new AssertionError(
                "No valid %s start near chunk %d, %d".formatted(structureId, baseChunk, baseChunk));
    }

    /** The layout the start's marker queues, read back from the marker's saved form. */
    private static List<StagedDungeonPieceSpec> markerPieces(
            StructureStart start, StructurePieceSerializationContext serialization) {
        StructurePiece marker = start.getPieces().getFirst();
        var ops = serialization.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        List<StagedDungeonPieceSpec> pieces =
                marker.createTag(serialization)
                        .read("pieces", StagedDungeonPieceSpec.CODEC.listOf(), ops)
                        .orElse(List.of());
        if (pieces.isEmpty()) {
            throw new AssertionError("The start's first piece must be the staged dungeon marker");
        }
        return pieces;
    }

    private static int pieceIndex(List<StagedDungeonPieceSpec> pieces, Identifier template) {
        for (int i = 0; i < pieces.size(); i++) {
            if (pieces.get(i).element() instanceof VariantSinglePoolElement variant
                    && variant.templateLocation().equals(template)) {
                return i;
            }
        }
        throw new AssertionError("The layout has no " + template);
    }

    /**
     * Pool elements cache template geometry. After a resource reload replaces a template, the next
     * lookup must read the new template's size, jigsaws, and markers instead of the cached ones.
     */
    public static void geometryCachesFollowReloadedTemplates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplateManager templates = level.getStructureManager();
        Identifier probe = ProceduralDungeon.of("gametest/geometry_cache_probe");
        var blocks = level.holderLookup(Registries.BLOCK);
        StructureTemplate vault = templates.getOrCreate(DungeonProgressionRooms.BOSS_KEY_VAULT);
        StructureTemplate bossRoom = templates.getOrCreate(DungeonProgressionRooms.BOSS_ROOM);
        try {
            templates.remove(probe);
            templates.getOrCreate(probe).load(blocks, vault.save(new CompoundTag()));
            VariantSinglePoolElement element =
                    new VariantSinglePoolElement(
                            (SinglePoolElement)
                                    StructurePoolElement.single(probe.toString())
                                            .apply(StructureTemplatePool.Projection.RIGID),
                            ProceduralDungeon.of("cobblestone/tier_1"),
                            1,
                            Integer.MAX_VALUE);
            BoundingBox before = element.getBoundingBox(templates, BlockPos.ZERO, Rotation.NONE);
            List<BlockPos> jigsawsBefore = jigsawPositions(element, templates);
            helper.assertTrue(
                    before.getLength().equals(vault.getSize().offset(-1, -1, -1)),
                    "The probe must start as the vault: " + before);

            // A resource reload clears the manager, which then loads a new template object.
            templates.remove(probe);
            templates.getOrCreate(probe).load(blocks, bossRoom.save(new CompoundTag()));
            BoundingBox after = element.getBoundingBox(templates, BlockPos.ZERO, Rotation.NONE);
            helper.assertTrue(
                    after.getLength().equals(bossRoom.getSize().offset(-1, -1, -1)),
                    "A reloaded template must change the cached box, still " + after);
            List<BlockPos> expectedJigsaws =
                    bossRoom.getJigsaws(BlockPos.ZERO, Rotation.NONE).stream()
                            .map(jigsaw -> jigsaw.info().pos())
                            .sorted(Comparator.comparingLong(BlockPos::asLong))
                            .toList();
            List<BlockPos> jigsawsAfter = jigsawPositions(element, templates);
            helper.assertTrue(
                    !jigsawsAfter.equals(jigsawsBefore) && jigsawsAfter.equals(expectedJigsaws),
                    "A reloaded template must change the cached jigsaws, still " + jigsawsAfter);
        } finally {
            templates.remove(probe);
        }
        helper.succeed();
    }

    private static List<BlockPos> jigsawPositions(
            VariantSinglePoolElement element, StructureTemplateManager templates) {
        return element
                .getShuffledJigsawBlocks(
                        templates, BlockPos.ZERO, Rotation.NONE, RandomSource.create(0L))
                .stream()
                .map(jigsaw -> jigsaw.info().pos())
                .sorted(Comparator.comparingLong(BlockPos::asLong))
                .toList();
    }

    private static Optional<StagedDungeonLayout> compile(
            ServerLevel level,
            DungeonTheme theme,
            DungeonTier tier,
            BlockPos origin,
            JigsawStructure.MaxDistance maxDistance) {
        ResourceKey<StructureTemplatePool> startPool =
                RegistryKeyUtils.create(
                        Registries.TEMPLATE_POOL,
                        "%s/start".formatted(RegistryKeyUtils.getKeyString(theme, tier)));
        var generator = level.getChunkSource().getGenerator();
        Structure.GenerationContext context =
                new Structure.GenerationContext(
                        level.registryAccess(),
                        generator,
                        generator.getBiomeSource(),
                        level.getChunkSource().randomState(),
                        level.getStructureManager(),
                        level.getSeed(),
                        ChunkPos.containing(origin),
                        level,
                        ignored -> true);
        return StagedDungeonLayoutCompiler.compile(
                context,
                level.registryAccess()
                        .lookupOrThrow(Registries.TEMPLATE_POOL)
                        .getOrThrow(startPool),
                Optional.of(Identifier.withDefaultNamespace("start")),
                tier.size,
                origin,
                Optional.empty(),
                maxDistance,
                LiquidSettings.IGNORE_WATERLOGGING);
    }

    private static List<ResourceKey<LootTable>> chestLoot(ServerLevel level, BoundingBox box) {
        List<ResourceKey<LootTable>> lootTables = new ArrayList<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(
                        box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest
                    && chest.getLootTable() != null) {
                lootTables.add(chest.getLootTable());
            }
        }
        return lootTables;
    }

    /** Boss Keys carrying a key id in the chests inside {@code box}. */
    private static int boundBossKeys(ServerLevel level, BoundingBox box) {
        int keys = 0;
        for (BlockPos pos :
                BlockPos.betweenClosed(
                        box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    if (DungeonKeyType.BOSS.matches(chest.getItem(slot))
                            && DungeonKeyType.keyId(chest.getItem(slot)).isPresent()) {
                        keys++;
                    }
                }
            }
        }
        return keys;
    }
}
