package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.DungeonGameTestSuite.TestCase;
import com.github.brainage04.procedural_dungeon.command.GenerateDungeonCommand;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.guardian.DungeonGuardian;
import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonChunkLedger;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonChunkTickets;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonGenerationProfiler;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonProgressionRooms;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonPuzzleRooms;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.EnqueueResult;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobOptions;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobState;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobView;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonPieceSpec;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonSaveData;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedPlacementContext;
import com.github.brainage04.procedural_dungeon.worldgen.structure.VariantSinglePoolElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Staged generation and persistence: job records, tickets, failures, budget, chunk-wise placement
 * and crash recovery. Each test builds far from the test grid, in a region of its own, so tests
 * running together never share chunks.
 */
public final class StagedJobGameTests {
    private static final AtomicInteger REGIONS = new AtomicInteger();
    private static final int REGION_X = -16384 - 2048 * new Random().nextInt(2048);
    private static final int REGION_SPACING = 1024;
    private static final JobOptions KEPT = JobOptions.DEFAULT;
    private static final int UPDATE_SILENTLY =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private StagedJobGameTests() {}

    public static List<TestCase> tests() {
        return List.of(
                new TestCase(
                        "job_records_encode_each_piece_once_per_save",
                        StagedJobGameTests::jobRecordsEncodeEachPieceOncePerSave),
                new TestCase(
                        "shared_chunks_keep_their_ticket_until_the_last_job",
                        StagedJobGameTests::sharedChunksKeepTheirTicketUntilTheLastJob),
                new TestCase(
                        "staged_jobs_never_force_load_chunks",
                        StagedJobGameTests::stagedJobsNeverForceLoadChunks),
                new TestCase(
                        "a_failed_piece_fails_the_job_and_skips_its_locks",
                        StagedJobGameTests::aFailedPieceFailsTheJobAndSkipsItsLocks),
                new TestCase(
                        "unreadable_job_records_are_kept_as_failed",
                        StagedJobGameTests::unreadableJobRecordsAreKeptAsFailed),
                new TestCase(
                        "two_jobs_in_one_chunk_both_run",
                        StagedJobGameTests::twoJobsInOneChunkBothRun),
                new TestCase(
                        "round_robin_serves_every_level_under_a_tight_budget",
                        StagedJobGameTests::roundRobinServesEveryLevelUnderATightBudget),
                new TestCase(
                        "a_piece_places_the_chunks_that_are_loaded",
                        StagedJobGameTests::aPiecePlacesTheChunksThatAreLoaded),
                new TestCase(
                        "replay_after_chunks_saved_first_reverts_nothing",
                        StagedJobGameTests::replayAfterChunksSavedFirstRevertsNothing),
                new TestCase(
                        "replay_after_progress_saved_first_restores_lost_rooms",
                        StagedJobGameTests::replayAfterProgressSavedFirstRestoresLostRooms),
                new TestCase(
                        "replay_after_progress_saved_first_keeps_a_saved_guardian",
                        StagedJobGameTests::replayAfterProgressSavedFirstKeepsASavedGuardian),
                new TestCase(
                        "replay_places_only_the_missing_half_of_a_piece",
                        StagedJobGameTests::replayPlacesOnlyTheMissingHalfOfAPiece),
                new TestCase(
                        "replay_with_a_tagged_guardian_spawns_no_second",
                        StagedJobGameTests::replayWithATaggedGuardianSpawnsNoSecond),
                new TestCase(
                        "delayed_jobs_leave_player_builds_alone",
                        StagedJobGameTests::delayedJobsLeavePlayerBuildsAlone),
                new TestCase(
                        "levels_without_jobs_keep_no_staged_data",
                        StagedJobGameTests::levelsWithoutJobsKeepNoStagedData),
                new TestCase(
                        "legacy_job_records_migrate_and_release_their_tickets",
                        StagedJobGameTests::legacyJobRecordsMigrateAndReleaseTheirTickets),
                new TestCase(
                        "only_a_clean_stop_resumes_from_the_cursor",
                        StagedJobGameTests::onlyACleanStopResumesFromTheCursor),
                new TestCase(
                        "a_new_session_restarts_the_tick_window",
                        StagedJobGameTests::aNewSessionRestartsTheTickWindow));
    }

    /** P13: placing pieces only moves the cursor; the layout is encoded when the level saves. */
    public static void jobRecordsEncodeEachPieceOncePerSave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = region(level, 64);
        StagedDungeonPieceSpec piece = smallPiece(level, origin);
        List<StagedDungeonPieceSpec> pieces = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            pieces.add(piece);
        }
        holdChunks(level, piece.boundingBox());
        DungeonGenerationProfiler.begin();
        UUID id = enqueue(helper, level, pieces, DungeonLockPlan.EMPTY);
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(
                            state(level, id) == JobState.COMPLETE, "The 200-piece job must finish");
                    int encodes = DungeonGenerationProfiler.finish().pieceEncodes();
                    helper.assertTrue(
                            encodes <= 400,
                            "Placing 200 pieces must not re-encode the queue; encoded "
                                    + encodes
                                    + " pieces");
                    StagedDungeonGenerationManager.forget(level, piece.boundingBox());
                    releaseChunks(level, piece.boundingBox());
                });
    }

    /**
     * P02 (a): a chunk two jobs need keeps PD's ticket until the second one is done. The long job
     * starts first and holds the chunk; the short one then shares it and finishes.
     */
    public static void sharedChunksKeepTheirTicketUntilTheLastJob(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = region(level, 64);
        StagedDungeonPieceSpec piece = smallPiece(level, origin);
        long chunk = ChunkPos.containing(piece.position()).pack();
        holdChunks(level, piece.boundingBox());
        List<StagedDungeonPieceSpec> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add(piece);
        }
        JobOptions callerDriven = new JobOptions(true, true, 0L, 1.0);
        UUID second =
                StagedDungeonGenerationManager.enqueue(
                                level,
                                layout(many, DungeonLockPlan.EMPTY),
                                LiquidSettings.IGNORE_WATERLOGGING,
                                callerDriven)
                        .id();
        stepOnce(level, second);
        helper.assertTrue(
                state(level, second) == JobState.RUNNING && hasStagedTicket(level, chunk),
                "The long job must be running and hold the chunk");
        UUID first =
                StagedDungeonGenerationManager.enqueue(
                                level,
                                layout(List.of(piece), DungeonLockPlan.EMPTY),
                                LiquidSettings.IGNORE_WATERLOGGING,
                                callerDriven)
                        .id();
        // succeedWhen retries a failed assertion, so a one-time check keeps its failure.
        AtomicBoolean checkedOverlap = new AtomicBoolean();
        AtomicReference<String> overlapFailure = new AtomicReference<>();
        helper.succeedWhen(
                () -> {
                    if (!checkedOverlap.get()) {
                        StagedDungeonGenerationManager.advance(level, first, 50_000_000L);
                        helper.assertTrue(
                                state(level, first) == JobState.COMPLETE,
                                "The short job must finish" + describeJobs(level));
                        checkedOverlap.set(true);
                        if (state(level, second) != JobState.RUNNING
                                || !hasStagedTicket(level, chunk)) {
                            overlapFailure.set(
                                    "The second job's chunk must keep its ticket after the first"
                                            + " job finishes");
                        }
                    }
                    helper.assertTrue(
                            overlapFailure.get() == null, String.valueOf(overlapFailure.get()));
                    StagedDungeonGenerationManager.advance(level, second, 50_000_000L);
                    helper.assertTrue(
                            state(level, second) == JobState.COMPLETE,
                            "The second job must finish" + describeJobs(level));
                    helper.assertTrue(
                            !hasStagedTicket(level, chunk),
                            "No ticket may remain once both jobs are done");
                    StagedDungeonGenerationManager.forget(level, piece.boundingBox());
                    releaseChunks(level, piece.boundingBox());
                });
    }

    /**
     * Places exactly one part of a caller-driven job: a fake clock gives {@link
     * StagedDungeonGenerationManager#advance} room for one step. The test body runs synchronously
     * on the server thread, so no other job sees the fake clock.
     */
    private static void stepOnce(ServerLevel level, UUID id) {
        AtomicLong clock = new AtomicLong();
        StagedDungeonGenerationManager.setNanoClock(() -> clock.addAndGet(1_000_000L));
        try {
            StagedDungeonGenerationManager.advance(level, id, 1_500_000L);
        } finally {
            StagedDungeonGenerationManager.setNanoClock(null);
        }
    }

    /**
     * P02 (b): jobs load chunks with PD's own non-persistent ticket, never a forced one, and leave
     * none behind. The test holds the pieces' chunks itself (see {@link #holdChunks}); PD still
     * takes its own tickets on them.
     */
    public static void stagedJobsNeverForceLoadChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = region(level, 64);
        StagedDungeonPieceSpec piece = smallPiece(level, origin);
        List<StagedDungeonPieceSpec> pieces = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pieces.add(moved(piece, 40 * i, 0, 0));
        }
        for (StagedDungeonPieceSpec each : pieces) {
            holdChunks(level, each.boundingBox());
        }
        BoundingBox bounds = bounds(pieces);
        UUID id = enqueue(helper, level, pieces, DungeonLockPlan.EMPTY);
        // succeedWhen retries a failed assertion, so a forced chunk seen once stays a failure.
        AtomicReference<String> forced = new AtomicReference<>();
        helper.succeedWhen(
                () -> {
                    for (long chunk : chunks(bounds)) {
                        if (level.getForceLoadedChunks().contains(chunk)
                                || hasLegacyForcedTicket(level, chunk)) {
                            forced.compareAndSet(
                                    null,
                                    "A staged job must never force-load chunk "
                                            + ChunkPos.unpack(chunk));
                        }
                    }
                    helper.assertTrue(forced.get() == null, String.valueOf(forced.get()));
                    helper.assertTrue(
                            state(level, id) == JobState.COMPLETE,
                            "The job must finish" + describeJobs(level));
                    for (long chunk : chunks(bounds)) {
                        helper.assertTrue(
                                !hasStagedTicket(level, chunk),
                                "A finished job must leave no ticket at " + ChunkPos.unpack(chunk));
                    }
                    StagedDungeonGenerationManager.forget(level, bounds);
                    for (StagedDungeonPieceSpec each : pieces) {
                        releaseChunks(level, each.boundingBox());
                    }
                });
    }

    /** P03: a piece whose template is missing fails the job, and its locks are not applied. */
    public static void aFailedPieceFailsTheJobAndSkipsItsLocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = region(level, 64);
        StagedDungeonPieceSpec first = smallPiece(level, origin);
        StagedDungeonPieceSpec third = moved(first, 64, 0, 0);
        BlockPos missingAt = origin.offset(32, 0, 0);
        BoundingBox missingBox =
                new BoundingBox(
                        missingAt.getX(),
                        missingAt.getY(),
                        missingAt.getZ(),
                        missingAt.getX() + 4,
                        missingAt.getY() + 4,
                        missingAt.getZ() + 4);
        StructurePoolElement missing =
                StructurePoolElement.single(ProceduralDungeon.of("missing/template").toString())
                        .apply(StructureTemplatePool.Projection.RIGID);
        StagedDungeonPieceSpec broken =
                new StagedDungeonPieceSpec(missing, missingAt, Rotation.NONE, missingBox, 0);
        BlockPos door = missingAt.offset(1, 1, 1);
        holdChunks(level, bounds(List.of(first, broken, third)));
        level.setBlock(
                door,
                Blocks.IRON_DOOR
                        .defaultBlockState()
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER),
                UPDATE_SILENTLY);
        level.setBlock(
                door.above(),
                Blocks.IRON_DOOR
                        .defaultBlockState()
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
                UPDATE_SILENTLY);
        DungeonLockPlan plan =
                new DungeonLockPlan(
                        List.of(),
                        List.of(),
                        List.of(
                                new DungeonLockPlan.Door(
                                        door.asLong(), Optional.of(DungeonKeyType.BOSS))),
                        List.of());
        UUID id = enqueue(helper, level, List.of(first, broken, third), plan);
        helper.succeedWhen(
                () -> {
                    // The missing template is not one of PD's elements, so its part waits for the
                    // chunk's entities (see pace).
                    pace();
                    JobView view =
                            StagedDungeonGenerationManager.job(level, id)
                                    .orElseThrow(
                                            () ->
                                                    new AssertionError(
                                                            "A failed job must keep its record"));
                    helper.assertTrue(
                            view.state() == JobState.FAILED,
                            "A missing template must fail the job, not complete it: "
                                    + view.state());
                    helper.assertTrue(
                            view.placedPieces() == 1
                                    && view.failure() != null
                                    && view.failure().contains("missing/template"),
                            "The failure must name the piece: " + view);
                    helper.assertTrue(
                            !DungeonLockManager.isDoorLocked(level, door),
                            "The failed piece's door must not be locked");
                    helper.assertTrue(
                            StagedDungeonGenerationManager.heldChunks(level, id) == 0,
                            "A failed job must release its tickets");
                    StagedDungeonGenerationManager.forget(
                            level, bounds(List.of(first, broken, third)));
                    releaseChunks(level, bounds(List.of(first, broken, third)));
                });
    }

    /**
     * P03: a record that cannot be read stays in the level's file and counts as a failed job, also
     * after another job in the same level runs and the records are written again.
     */
    public static void unreadableJobRecordsAreKeptAsFailed(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        RegistryOps<Tag> ops = nether.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag broken = new CompoundTag();
        broken.putString("id", "not-a-uuid");
        broken.putString("note", "kept verbatim");
        CompoundTag data = new CompoundTag();
        data.putInt("format_version", 2);
        ListTag jobs = new ListTag();
        jobs.add(broken);
        data.put("jobs", jobs);
        nether.getDataStorage()
                .set(
                        StagedDungeonSaveData.TYPE,
                        StagedDungeonSaveData.TYPE.codec().parse(ops, data).getOrThrow());
        StagedDungeonPieceSpec piece = smallPiece(helper.getLevel(), region(nether, 100));
        holdChunks(nether, piece.boundingBox());
        UUID id = enqueue(helper, nether, List.of(piece), DungeonLockPlan.EMPTY);
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(
                            state(nether, id) != JobState.RUNNING,
                            "The other job must finish" + describeJobs(nether));
                    CompoundTag encoded =
                            (CompoundTag)
                                    StagedDungeonSaveData.TYPE
                                            .codec()
                                            .encodeStart(
                                                    ops,
                                                    nether.getDataStorage()
                                                            .get(StagedDungeonSaveData.TYPE))
                                            .getOrThrow();
                    ListTag unreadable = encoded.getListOrEmpty("unreadable_jobs");
                    helper.assertTrue(
                            unreadable.size() == 1
                                    && unreadable
                                            .getCompoundOrEmpty(0)
                                            .getCompoundOrEmpty("record")
                                            .equals(broken),
                            "The unreadable record must be kept verbatim: " + encoded);
                    helper.assertTrue(
                            StagedDungeonGenerationManager.status(nether).failedJobs() >= 1,
                            "The unreadable record must count as a failed job");
                    StagedDungeonGenerationManager.forget(nether, piece.boundingBox());
                    releaseChunks(nether, piece.boundingBox());
                });
    }

    /** P12: jobs are keyed by id, so a second dungeon scheduled in the same chunk is built too. */
    public static void twoJobsInOneChunkBothRun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = region(level, 64);
        StagedDungeonPieceSpec first = smallPiece(level, origin);
        StagedDungeonPieceSpec second = moved(first, 0, 24, 0);
        BoundingBox both = bounds(List.of(first, second));
        holdChunks(level, both);
        ChunkPos chunk = ChunkPos.containing(origin);
        EnqueueResult a =
                StagedDungeonGenerationManager.enqueue(
                        level, chunk, List.of(first), LiquidSettings.IGNORE_WATERLOGGING);
        EnqueueResult b =
                StagedDungeonGenerationManager.enqueue(
                        level, chunk, List.of(second), LiquidSettings.IGNORE_WATERLOGGING);
        helper.assertTrue(
                a.accepted() && b.accepted() && !a.id().equals(b.id()),
                "Both requests in one chunk must be accepted as separate jobs");
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(
                            StagedDungeonGenerationManager.job(level, a.id()).isEmpty()
                                    && StagedDungeonGenerationManager.job(level, b.id()).isEmpty(),
                            "Both jobs must finish" + describeJobs(level));
                    LevelChunk placed = level.getChunk(chunk.x(), chunk.z());
                    helper.assertTrue(
                            DungeonChunkLedger.isPlaced(placed, a.id(), 0)
                                    && DungeonChunkLedger.isPlaced(placed, b.id(), 0),
                            "Both dungeons must be built");
                    releaseChunks(level, both);
                });
    }

    /**
     * P14: with a tight budget, levels and jobs take turns instead of the first level using every
     * tick. The overworld always has more ready work than the budget covers (eight jobs), so a loop
     * that starts from the first level each tick never reaches the Nether.
     */
    public static void roundRobinServesEveryLevelUnderATightBudget(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        BlockPos origin = region(overworld, 64);
        StagedDungeonPieceSpec piece = smallPiece(overworld, origin);
        StagedDungeonPieceSpec netherPiece = moved(piece, 0, 100 - piece.position().getY(), 0);
        List<StagedDungeonPieceSpec> overworldPieces = new ArrayList<>();
        List<StagedDungeonPieceSpec> netherPieces = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            overworldPieces.add(piece);
            netherPieces.add(netherPiece);
        }
        holdChunks(overworld, piece.boundingBox());
        holdChunks(nether, netherPiece.boundingBox());
        AtomicLong clock = new AtomicLong(System.nanoTime());
        StagedDungeonGenerationManager.setNanoClock(() -> clock.addAndGet(500_000L));
        StagedDungeonGenerationManager.setBudgetOverride(2_000_000L);
        List<UUID> overworldJobs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            overworldJobs.add(
                    enqueue(
                            helper,
                            overworld,
                            overworldPieces.subList(0, 50),
                            DungeonLockPlan.EMPTY));
        }
        UUID netherJob = enqueue(helper, nether, netherPieces, DungeonLockPlan.EMPTY);
        helper.runAfterDelay(
                10,
                () -> {
                    StagedDungeonGenerationManager.setNanoClock(null);
                    StagedDungeonGenerationManager.setBudgetOverride(-1L);
                    int overworldPlaced =
                            overworldJobs.stream()
                                    .mapToInt(
                                            id ->
                                                    StagedDungeonGenerationManager.job(
                                                                    overworld, id)
                                                            .map(JobView::placedPieces)
                                                            .orElse(0))
                                    .sum();
                    int netherPlaced =
                            StagedDungeonGenerationManager.job(nether, netherJob)
                                    .map(JobView::placedPieces)
                                    .orElse(0);
                    StagedDungeonGenerationManager.forget(overworld, piece.boundingBox());
                    StagedDungeonGenerationManager.forget(nether, netherPiece.boundingBox());
                    releaseChunks(overworld, piece.boundingBox());
                    releaseChunks(nether, netherPiece.boundingBox());
                    helper.assertTrue(
                            overworldPlaced > 0 && netherPlaced > 0,
                            "Both levels must place pieces within 10 ticks: overworld %d, nether %d"
                                    .formatted(overworldPlaced, netherPlaced));
                    helper.succeed();
                });
    }

    /**
     * P15: a piece over four chunks places its part in a loaded chunk without waiting for the other
     * three. The job is caller-driven, so the check runs right after the one step that took the
     * other chunks' tickets: they cannot have loaded yet, and only the loaded part may be in.
     */
    public static void aPiecePlacesTheChunksThatAreLoaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = region(level, 64);
        StagedDungeonPieceSpec template = smallPiece(level, origin);
        BoundingBox box = template.boundingBox();
        // Straddle a chunk corner: the min corner sits in the last column of its chunk on both
        // axes.
        int cornerX =
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX()) + 1)
                        - 1;
        int cornerZ =
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ()) + 1)
                        - 1;
        StagedDungeonPieceSpec piece =
                moved(template, cornerX - box.minX(), 0, cornerZ - box.minZ());
        ChunkPos loaded = ChunkPos.containing(new BlockPos(cornerX, 0, cornerZ));
        // Hold the loaded chunk alone (radius 0: its neighbours stay below full status).
        level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, loaded, 0);
        level.getChunk(loaded.x(), loaded.z());
        UUID id =
                StagedDungeonGenerationManager.enqueue(
                                level,
                                layout(List.of(piece), DungeonLockPlan.EMPTY),
                                LiquidSettings.IGNORE_WATERLOGGING,
                                new JobOptions(true, true, 0L, 1.0))
                        .id();
        // succeedWhen retries a failed assertion, so the one-time check keeps its failure.
        AtomicBoolean checked = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();
        helper.succeedWhen(
                () -> {
                    if (!checked.get()) {
                        checked.set(true);
                        StagedDungeonGenerationManager.advance(level, id, 50_000_000L);
                        int otherParts = 0;
                        for (long other : chunks(piece.boundingBox())) {
                            LevelChunk otherChunk =
                                    level.getChunkSource()
                                            .getChunkNow(
                                                    ChunkPos.getX(other), ChunkPos.getZ(other));
                            if (other != loaded.pack()
                                    && otherChunk != null
                                    && DungeonChunkLedger.isPlaced(otherChunk, id, 0)) {
                                otherParts++;
                            }
                        }
                        if (!DungeonChunkLedger.isPlaced(
                                        level.getChunk(loaded.x(), loaded.z()), id, 0)
                                || state(level, id) != JobState.RUNNING
                                || otherParts > 0) {
                            failure.set(
                                    "The loaded part alone must be placed while the job waits for"
                                            + " the other chunks: %s, %d other part(s) placed"
                                                    .formatted(state(level, id), otherParts));
                        }
                        // The check is done; load the rest now so the job need not wait for
                        // asynchronous worldgen against the sprinting tick deadline.
                        holdChunks(level, piece.boundingBox());
                    }
                    helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
                    StagedDungeonGenerationManager.advance(level, id, 50_000_000L);
                    helper.assertTrue(
                            state(level, id) == JobState.COMPLETE,
                            "The whole piece must finish" + describeJobs(level));
                    StagedDungeonGenerationManager.forget(level, piece.boundingBox());
                    releaseChunks(level, piece.boundingBox());
                    level.getChunkSource()
                            .removeTicketWithRadius(TicketType.PLAYER_LOADING, loaded, 0);
                });
    }

    /**
     * P04 T1: the record is older than the chunks (chunks saved, progress not). The replay skips
     * every part already in the world, so nothing a player did is reverted and nothing spawns
     * twice.
     */
    public static void replayAfterChunksSavedFirstRevertsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = Fixture.build(helper);
        Tag atStart = StagedDungeonGenerationManager.snapshot(level, fixture.id);
        AtomicInteger phase = new AtomicInteger();
        AtomicInteger frames = new AtomicInteger();
        AtomicReference<BlockPos> wall = new AtomicReference<>();
        helper.succeedWhen(
                () -> {
                    pace();
                    if (phase.get() == 0) {
                        helper.assertTrue(
                                state(level, fixture.id) == JobState.COMPLETE,
                                "The fixture must finish" + describeJobs(level));
                        frames.set(fixture.taggedFrames(level));
                        helper.assertTrue(
                                frames.get() > 0 && fixture.guardians(level).size() == 1,
                                "The fixture must hang frames and spawn one guardian");
                        fixture.lootChest(level);
                        fixture.unlockDoor(helper, level);
                        fixture.guardians(level).forEach(Entity::discard);
                        wall.set(fixture.breakWall(level));
                        StagedDungeonGenerationManager.restore(level, atStart, true);
                        phase.set(1);
                    }
                    helper.assertTrue(
                            state(level, fixture.id) == JobState.COMPLETE,
                            "The replayed job must finish" + describeJobs(level));
                    fixture.assertLooted(helper, level);
                    fixture.assertDoorOpen(helper, level);
                    helper.assertTrue(
                            level.getBlockState(wall.get()).isAir(),
                            "A broken wall must stay broken");
                    helper.assertTrue(
                            fixture.guardians(level).isEmpty(),
                            "A killed guardian must not come back");
                    helper.assertTrue(
                            fixture.taggedFrames(level) == frames.get(),
                            "No frame may be hung twice");
                    fixture.assertNoTickets(helper, level);
                    fixture.clear(level);
                });
    }

    /**
     * P04 T2 (a): the record is newer than the chunks (progress saved, chunks lost with their
     * guardian). An unclean restart walks every part and places the lost room again, guardian
     * included.
     */
    public static void replayAfterProgressSavedFirstRestoresLostRooms(GameTestHelper helper) {
        replayAfterLostBossRoom(helper, true);
    }

    /**
     * P04 T2 (b): as (a), but the guardian's entity file was saved while the blocks were lost: no
     * second guardian.
     */
    public static void replayAfterProgressSavedFirstKeepsASavedGuardian(GameTestHelper helper) {
        replayAfterLostBossRoom(helper, false);
    }

    private static void replayAfterLostBossRoom(GameTestHelper helper, boolean guardianLost) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = Fixture.build(helper);
        AtomicInteger phase = new AtomicInteger();
        helper.succeedWhen(
                () -> {
                    pace();
                    if (phase.get() == 0) {
                        helper.assertTrue(
                                state(level, fixture.id) == JobState.COMPLETE,
                                "The fixture must finish" + describeJobs(level));
                        Tag finished = StagedDungeonGenerationManager.snapshot(level, fixture.id);
                        fixture.revertBossRoom(level);
                        if (guardianLost) {
                            fixture.guardians(level).forEach(Entity::discard);
                        }
                        StagedDungeonGenerationManager.restore(level, finished, false);
                        phase.set(1);
                    }
                    helper.assertTrue(
                            state(level, fixture.id) == JobState.COMPLETE,
                            "The replayed job must finish" + describeJobs(level));
                    fixture.assertBossRoomPlaced(helper, level);
                    helper.assertTrue(
                            fixture.guardians(level).size() == 1,
                            "Exactly one guardian must guard the room: "
                                    + fixture.guardians(level).size());
                    fixture.assertNoTickets(helper, level);
                    fixture.clear(level);
                });
    }

    /**
     * P04 T3: a piece over a chunk border whose marker survived in one chunk only. The replay
     * places the missing half and leaves the present half, with a player's edit, alone.
     */
    public static void replayPlacesOnlyTheMissingHalfOfAPiece(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = region(level, 64);
        StagedDungeonPieceSpec template = largestPiece(level, origin, 6);
        BoundingBox templateBox = template.boundingBox();
        int borderX =
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX()) + 1);
        StagedDungeonPieceSpec piece = moved(template, borderX - 3 - templateBox.minX(), 0, 0);
        BoundingBox box = piece.boundingBox();
        helper.assertTrue(
                SectionPos.blockToSectionCoord(box.minZ())
                        == SectionPos.blockToSectionCoord(box.maxZ()),
                "The test piece must span two chunks along x only");
        ChunkPos present = ChunkPos.containing(new BlockPos(box.minX(), 0, box.minZ()));
        ChunkPos lost = ChunkPos.containing(new BlockPos(box.maxX(), 0, box.minZ()));
        holdChunks(level, box);
        BoundingBox lostPart =
                new BoundingBox(
                        lost.getMinBlockX(),
                        box.minY(),
                        box.minZ(),
                        box.maxX(),
                        box.maxY(),
                        box.maxZ());
        Map<BlockPos, BlockState> before = states(level, lostPart);
        UUID id = enqueue(helper, level, List.of(piece), DungeonLockPlan.EMPTY);
        AtomicInteger phase = new AtomicInteger();
        AtomicReference<Map<BlockPos, BlockState>> placed = new AtomicReference<>();
        BlockPos edited = new BlockPos(box.minX(), box.minY() + 1, box.minZ() + 1);
        helper.succeedWhen(
                () -> {
                    if (phase.get() == 0) {
                        helper.assertTrue(
                                state(level, id) == JobState.COMPLETE,
                                "The piece must be placed" + describeJobs(level));
                        Tag finished = StagedDungeonGenerationManager.snapshot(level, id);
                        placed.set(states(level, lostPart));
                        level.setBlock(
                                edited, Blocks.GOLD_BLOCK.defaultBlockState(), UPDATE_SILENTLY);
                        before.forEach((pos, state) -> level.setBlock(pos, state, UPDATE_SILENTLY));
                        DungeonChunkLedger.unmark(level.getChunk(lost.x(), lost.z()), id, 0);
                        StagedDungeonGenerationManager.restore(level, finished, false);
                        phase.set(1);
                    }
                    helper.assertTrue(
                            state(level, id) == JobState.COMPLETE,
                            "The replayed piece must finish" + describeJobs(level));
                    helper.assertTrue(
                            DungeonChunkLedger.isPlaced(level.getChunk(lost.x(), lost.z()), id, 0),
                            "The lost half must be marked again");
                    helper.assertTrue(
                            states(level, lostPart).equals(placed.get()),
                            "The lost half must be placed again");
                    helper.assertTrue(
                            level.getBlockState(edited).is(Blocks.GOLD_BLOCK),
                            "The present half must keep the player's edit");
                    helper.assertTrue(
                            DungeonChunkLedger.isPlaced(
                                    level.getChunk(present.x(), present.z()), id, 0),
                            "The present half keeps its marker");
                    StagedDungeonGenerationManager.forget(level, box);
                    releaseChunks(level, box);
                });
    }

    /**
     * P04 T4: the guardian was saved but the boss room's markers were not. The room is placed
     * again; the tagged guardian is found, so no second one spawns.
     */
    public static void replayWithATaggedGuardianSpawnsNoSecond(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fixture fixture = Fixture.build(helper);
        AtomicInteger phase = new AtomicInteger();
        AtomicReference<BlockPos> wall = new AtomicReference<>();
        AtomicReference<BlockState> wallState = new AtomicReference<>();
        helper.succeedWhen(
                () -> {
                    pace();
                    if (phase.get() == 0) {
                        helper.assertTrue(
                                state(level, fixture.id) == JobState.COMPLETE,
                                "The fixture must finish" + describeJobs(level));
                        Tag finished = StagedDungeonGenerationManager.snapshot(level, fixture.id);
                        BlockPos broken = fixture.wallPos(level);
                        wall.set(broken);
                        wallState.set(level.getBlockState(broken));
                        level.setBlock(broken, Blocks.AIR.defaultBlockState(), UPDATE_SILENTLY);
                        fixture.unmarkBossRoom(level);
                        StagedDungeonGenerationManager.restore(level, finished, false);
                        phase.set(1);
                    }
                    helper.assertTrue(
                            state(level, fixture.id) == JobState.COMPLETE,
                            "The replayed job must finish" + describeJobs(level));
                    helper.assertTrue(
                            level.getBlockState(wall.get()).equals(wallState.get()),
                            "The unmarked room must be placed again");
                    helper.assertTrue(
                            fixture.guardians(level).size() == 1,
                            "The saved guardian must not get a twin: "
                                    + fixture.guardians(level).size());
                    fixture.clear(level);
                });
    }

    /**
     * P06: a part whose chunk saw players for a long time while the job waited is left alone. The
     * first piece sits in a chunk that still has to generate, so the job waits there while the
     * second piece's (loaded) chunk is already in its window.
     */
    public static void delayedJobsLeavePlayerBuildsAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = region(level, 64);
        StagedDungeonPieceSpec first = smallPiece(level, origin);
        StagedDungeonPieceSpec second = moved(first, 48, 0, 0);
        loadChunks(level, second.boundingBox());
        BlockPos build = writtenPos(level, second);
        JobOptions callerDriven = new JobOptions(true, true, 0L, 1.0);
        EnqueueResult result =
                StagedDungeonGenerationManager.enqueue(
                        level,
                        layout(List.of(first, second), DungeonLockPlan.EMPTY),
                        LiquidSettings.IGNORE_WATERLOGGING,
                        callerDriven);
        UUID id = result.id();
        // The job takes its window (both chunks) and waits for the first piece's chunk.
        StagedDungeonGenerationManager.advance(level, id, 50_000_000L);
        helper.assertTrue(
                StagedDungeonGenerationManager.job(level, id).orElseThrow().placedPieces() == 0,
                "The first piece must still wait for its chunk");
        // A player builds in the next piece's chunk while the job waits.
        level.setBlock(build, Blocks.GOLD_BLOCK.defaultBlockState(), UPDATE_SILENTLY);
        level.getChunkAt(build).incrementInhabitedTime(20L * 60L * 5L);
        // Then the waited-for chunk arrives (loaded here: GameTest servers sprint, so asynchronous
        // worldgen would race the tick deadline).
        holdChunks(level, first.boundingBox());
        helper.succeedWhen(
                () -> {
                    StagedDungeonGenerationManager.advance(level, id, 50_000_000L);
                    helper.assertTrue(
                            state(level, id) == JobState.COMPLETE,
                            "The job must finish" + describeJobs(level));
                    helper.assertTrue(
                            level.getBlockState(build).is(Blocks.GOLD_BLOCK),
                            "The player's block must survive the delayed piece");
                    StagedDungeonGenerationManager.forget(level, bounds(List.of(first, second)));
                    releaseChunks(level, first.boundingBox());
                });
    }

    /**
     * P08: a level that never had a job gets no staged data. The GameTest datapack adds an empty
     * dimension for this, since other tests build natural dungeons in all three vanilla ones.
     */
    public static void levelsWithoutJobsKeepNoStagedData(GameTestHelper helper) {
        ServerLevel empty =
                helper.getLevel()
                        .getServer()
                        .getLevel(
                                ResourceKey.create(
                                        Registries.DIMENSION, ProceduralDungeon.of("no_jobs")));
        helper.assertTrue(
                empty != null, "The GameTest datapack must add procedural_dungeon:no_jobs");
        helper.runAfterDelay(
                20,
                () -> {
                    helper.assertTrue(
                            empty.getDataStorage().get(StagedDungeonSaveData.TYPE) == null,
                            "A level without jobs must not get staged job data");
                    helper.succeed();
                });
    }

    /**
     * P09, D4: a format-1 record migrates to format 2, and the radius-1 forced tickets it leaked
     * are released inside its bounds only.
     */
    public static void legacyJobRecordsMigrateAndReleaseTheirTickets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        BlockPos origin = region(level, 64);
        StagedDungeonPieceSpec piece = smallPiece(level, origin);

        CompoundTag legacyJob = new CompoundTag();
        legacyJob.putLong("start_chunk", ChunkPos.containing(origin).pack());
        legacyJob.store(
                "liquid_settings", LiquidSettings.CODEC, LiquidSettings.IGNORE_WATERLOGGING);
        legacyJob.store("lock_plan", DungeonLockPlan.CODEC, DungeonLockPlan.EMPTY);
        legacyJob.putBoolean("tracked", false);
        legacyJob.store(
                "pending_pieces",
                StagedDungeonPieceSpec.CODEC.listOf(),
                ops,
                List.of(piece, piece));
        CompoundTag legacy = new CompoundTag();
        ListTag pending = new ListTag();
        pending.add(legacyJob);
        legacy.put("pending_jobs", pending);

        StagedDungeonSaveData migrated =
                StagedDungeonSaveData.TYPE.codec().parse(ops, legacy).getOrThrow();
        CompoundTag encoded =
                (CompoundTag)
                        StagedDungeonSaveData.TYPE.codec().encodeStart(ops, migrated).getOrThrow();
        helper.assertTrue(
                encoded.getIntOr("format_version", 0) == 2 && !encoded.contains("pending_jobs"),
                "A migrated record must be written in format 2: " + encoded.keySet());
        ListTag jobs = encoded.getListOrEmpty("jobs");
        helper.assertTrue(
                jobs.size() == 1 && jobs.getCompoundOrEmpty(0).getListOrEmpty("pieces").size() == 2,
                "The legacy job's remaining pieces must become its layout");
        helper.assertTrue(
                !encoded.getBooleanOr("clean_shutdown", true),
                "A migrated job resumes like after an unclean stop");
        CompoundTag again =
                (CompoundTag)
                        StagedDungeonSaveData.TYPE
                                .codec()
                                .encodeStart(
                                        ops,
                                        StagedDungeonSaveData.TYPE
                                                .codec()
                                                .parse(ops, encoded)
                                                .getOrThrow())
                                .getOrThrow();
        helper.assertTrue(
                again.getListOrEmpty("jobs").equals(jobs), "Format 2 must round-trip: " + again);

        ChunkPos inside = ChunkPos.containing(piece.position());
        ChunkPos outside = new ChunkPos(inside.x() + 40, inside.z());
        level.getChunkSource().addTicketWithRadius(TicketType.FORCED, inside, 1);
        level.getChunkSource().addTicketWithRadius(TicketType.FORCED, outside, 1);
        StagedDungeonGenerationManager.LegacyTicketReport report =
                StagedDungeonGenerationManager.releaseLegacyForcedTickets(
                        level, List.of(piece.boundingBox()));
        boolean insideReleased = !hasLegacyForcedTicket(level, inside.pack());
        boolean outsideKept = hasLegacyForcedTicket(level, outside.pack());
        level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, outside, 1);
        helper.assertTrue(
                insideReleased && report.released() >= 1,
                "The leaked ticket inside the dungeon must be released");
        helper.assertTrue(
                outsideKept && report.unknown().contains(outside),
                "A forced ticket outside known dungeons must only be listed");
        helper.succeed();
    }

    /**
     * P04: the shutdown flag decides between resuming from the cursor and walking every part again.
     */
    public static void onlyACleanStopResumesFromTheCursor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        StagedDungeonPieceSpec piece = smallPiece(level, region(level, 64));
        CompoundTag job = new CompoundTag();
        job.putString("id", UUID.randomUUID().toString());
        job.putLong("start_chunk", 0L);
        job.store(
                "pieces", StagedDungeonPieceSpec.CODEC.listOf(), ops, List.of(piece, piece, piece));
        job.putInt("cursor", 2);
        for (boolean clean : List.of(true, false)) {
            CompoundTag data = new CompoundTag();
            data.putInt("format_version", 2);
            data.putBoolean("clean_shutdown", clean);
            ListTag jobs = new ListTag();
            jobs.add(job.copy());
            data.put("jobs", jobs);
            CompoundTag encoded =
                    (CompoundTag)
                            StagedDungeonSaveData.TYPE
                                    .codec()
                                    .encodeStart(
                                            ops,
                                            StagedDungeonSaveData.TYPE
                                                    .codec()
                                                    .parse(ops, data)
                                                    .getOrThrow())
                                    .getOrThrow();
            int cursor = encoded.getListOrEmpty("jobs").getCompoundOrEmpty(0).getIntOr("cursor", 0);
            helper.assertTrue(
                    cursor == (clean ? 2 : 0),
                    "clean_shutdown=%s must resume from %d, got %d"
                            .formatted(clean, clean ? 2 : 0, cursor));
            helper.assertTrue(
                    !encoded.getBooleanOr("clean_shutdown", true),
                    "The next save must not claim a clean stop");
        }
        helper.succeed();
    }

    /**
     * P11: the tick-time window does not carry over into the next integrated-server session (both
     * loaders call {@link StagedDungeonGenerationManager#resetSession} on start and stop, which
     * empties it).
     */
    public static void aNewSessionRestartsTheTickWindow(GameTestHelper helper) {
        helper.runAfterDelay(
                2,
                () -> {
                    helper.assertTrue(
                            StagedDungeonGenerationManager.averageTickNanos() > 0L,
                            "Ticks must be sampled");
                    StagedDungeonGenerationManager.resetTickWindow();
                    helper.assertTrue(
                            StagedDungeonGenerationManager.averageTickNanos() == 0L,
                            "A new session must start with an empty tick window");
                    helper.succeed();
                });
    }

    /**
     * A tier-3 cobblestone layout's boss room and vault plus a frame puzzle room, as one kept job.
     */
    private record Fixture(
            UUID id,
            StagedDungeonLayout layout,
            StagedDungeonPieceSpec bossRoom,
            StagedDungeonPieceSpec vault,
            StagedDungeonPieceSpec puzzle,
            Map<BlockPos, BlockState> bossRoomBefore,
            BlockPos door) {
        static Fixture build(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            BlockPos origin = region(level, 40);
            DungeonTier tier = DungeonTier.TIER_3;
            StagedDungeonLayout compiled =
                    GenerateDungeonCommand.compileLayout(
                                    level, DungeonTheme.COBBLESTONE, tier, tier.size, origin)
                            .orElseThrow(() -> new AssertionError("Layout must compile"));
            StagedDungeonPieceSpec bossRoom =
                    pieces(compiled, DungeonProgressionRooms.BOSS_ROOM).getFirst();
            StagedDungeonPieceSpec vault =
                    pieces(compiled, DungeonProgressionRooms.BOSS_KEY_VAULT).getFirst();
            StagedDungeonPieceSpec puzzle = framePuzzle(level, origin.offset(-200, 0, 0));
            List<StagedDungeonPieceSpec> pieces = List.of(vault, bossRoom, puzzle);
            // The pieces lie far apart, so hold only their own chunks: the guardian and frames must
            // stay loaded after the job releases its tickets.
            for (StagedDungeonPieceSpec piece : pieces) {
                holdChunks(level, piece.boundingBox());
            }
            BlockPos door =
                    BlockPos.of(
                            compiled.lockPlan().doors().stream()
                                    .filter(
                                            planned ->
                                                    planned.lock()
                                                            .equals(
                                                                    Optional.of(
                                                                            DungeonKeyType.BOSS)))
                                    .findFirst()
                                    .orElseThrow()
                                    .pos());
            Map<BlockPos, BlockState> before = states(level, bossRoom.boundingBox());
            StagedDungeonLayout layout = StagedJobGameTests.layout(pieces, compiled.lockPlan());
            UUID id =
                    StagedDungeonGenerationManager.enqueue(
                                    level, layout, LiquidSettings.IGNORE_WATERLOGGING, KEPT)
                            .id();
            return new Fixture(id, layout, bossRoom, vault, puzzle, before, door);
        }

        List<Mob> guardians(ServerLevel level) {
            return level.getEntitiesOfClass(
                    Mob.class,
                    AABB.of(layout.boundingBox()).inflate(16.0),
                    mob -> mob.entityTags().contains(DungeonGuardian.TAG));
        }

        int taggedFrames(ServerLevel level) {
            return level.getEntitiesOfClass(
                            ItemFrame.class,
                            AABB.of(puzzle.boundingBox()).inflate(1.0),
                            frame ->
                                    frame.entityTags()
                                            .contains(StagedPlacementContext.JOB_TAG + id))
                    .size();
        }

        void lootChest(ServerLevel level) {
            ChestBlockEntity chest = bossChest(level);
            chest.setLootTable(null);
            chest.clearContent();
            chest.setChanged();
        }

        void assertLooted(GameTestHelper helper, ServerLevel level) {
            List<ChestBlockEntity> chests = chests(level, bossRoom.boundingBox());
            helper.assertTrue(
                    chests.stream()
                            .anyMatch(chest -> chest.getLootTable() == null && chest.isEmpty()),
                    "A looted chest must stay empty, not refill");
        }

        void unlockDoor(GameTestHelper helper, ServerLevel level) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            // The door opens only for the Boss Key bound to it, which the vault holds.
            BlockPos vaultChest =
                    BlockPos.of(
                            layout.lockPlan().keySources().stream()
                                    .filter(DungeonLockPlan::isBossKeyVault)
                                    .findFirst()
                                    .orElseThrow()
                                    .pos());
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(vaultChest);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (DungeonKeyType.BOSS.matches(chest.getItem(slot))) {
                    player.getInventory().add(chest.removeItemNoUpdate(slot));
                }
            }
            DungeonLockManager.useBlock(player, level, door);
            helper.assertTrue(
                    level.getBlockState(door).getValue(DoorBlock.OPEN),
                    "The boss key must open the door");
        }

        void assertDoorOpen(GameTestHelper helper, ServerLevel level) {
            helper.assertTrue(
                    level.getBlockState(door).is(Blocks.IRON_DOOR)
                            && level.getBlockState(door).getValue(DoorBlock.OPEN)
                            && !DungeonLockManager.isDoorLocked(level, door),
                    "An unlocked door must stay open and unlocked");
        }

        BlockPos wallPos(ServerLevel level) {
            BoundingBox box = bossRoom.boundingBox();
            for (BlockPos pos :
                    BlockPos.betweenClosed(
                            box.minX(),
                            box.minY(),
                            box.minZ(),
                            box.maxX(),
                            box.maxY(),
                            box.maxZ())) {
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.COBBLESTONE)
                        || state.is(Blocks.STONE_BRICKS)
                        || state.is(Blocks.MOSSY_COBBLESTONE)) {
                    return pos.immutable();
                }
            }
            throw new AssertionError("The boss room must have a stone wall");
        }

        BlockPos breakWall(ServerLevel level) {
            BlockPos pos = wallPos(level);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_SILENTLY);
            return pos;
        }

        void revertBossRoom(ServerLevel level) {
            bossRoomBefore.forEach(
                    (pos, state) -> {
                        level.removeBlockEntity(pos);
                        level.setBlock(pos, state, UPDATE_SILENTLY);
                    });
            unmarkBossRoom(level);
        }

        void unmarkBossRoom(ServerLevel level) {
            int index = layout.pieces().indexOf(bossRoom);
            for (long chunk : chunks(bossRoom.boundingBox())) {
                DungeonChunkLedger.unmark(
                        level.getChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)), id, index);
            }
        }

        void assertBossRoomPlaced(GameTestHelper helper, ServerLevel level) {
            helper.assertTrue(
                    level.getBlockState(door).is(Blocks.IRON_DOOR)
                            && DungeonLockManager.isDoorLocked(level, door),
                    "The lost boss room must be placed again with its door locked");
            helper.assertTrue(
                    chests(level, bossRoom.boundingBox()).stream()
                                    .filter(chest -> chest.getLootTable() != null)
                                    .count()
                            == 2,
                    "The lost boss room must get its two loot chests back");
        }

        void assertNoTickets(GameTestHelper helper, ServerLevel level) {
            helper.assertTrue(
                    StagedDungeonGenerationManager.heldChunks(level, id) == 0,
                    "The finished job must hold no tickets");
            for (long chunk : chunks(layout.boundingBox())) {
                helper.assertTrue(
                        !hasStagedTicket(level, chunk),
                        "No PD ticket may remain at " + ChunkPos.unpack(chunk));
            }
        }

        void clear(ServerLevel level) {
            guardians(level).forEach(Entity::discard);
            StagedDungeonGenerationManager.forget(level, layout.boundingBox());
            for (StagedDungeonPieceSpec piece : List.of(vault, bossRoom, puzzle)) {
                releaseChunks(level, piece.boundingBox());
            }
        }

        private ChestBlockEntity bossChest(ServerLevel level) {
            return chests(level, bossRoom.boundingBox()).stream()
                    .filter(chest -> chest.getLootTable() != null)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("The boss room must hold a loot chest"));
        }
    }

    private static StagedDungeonPieceSpec framePuzzle(ServerLevel level, BlockPos origin) {
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
                                                                DungeonPuzzleRooms.Puzzle.FRAMES
                                                                        .template()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "The sculk room pool must offer the frame puzzle"));
        BoundingBox box =
                element.getBoundingBox(level.getStructureManager(), origin, Rotation.NONE);
        return new StagedDungeonPieceSpec(element, origin, Rotation.NONE, box, 0);
    }

    private static List<StagedDungeonPieceSpec> pieces(
            StagedDungeonLayout layout, net.minecraft.resources.Identifier template) {
        return layout.pieces().stream()
                .filter(
                        piece ->
                                piece.element() instanceof VariantSinglePoolElement variant
                                        && variant.templateLocation().equals(template))
                .toList();
    }

    private static List<ChestBlockEntity> chests(ServerLevel level, BoundingBox box) {
        List<ChestBlockEntity> chests = new ArrayList<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(
                        box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                chests.add(chest);
            }
        }
        return chests;
    }

    /** A fresh region for one test, far from the test grid and from other tests. */
    private static BlockPos region(ServerLevel level, int y) {
        int slot = REGIONS.getAndIncrement();
        return new BlockPos(REGION_X - REGION_SPACING * slot, y, 20480 + 512 * (slot % 8));
    }

    private static UUID enqueue(
            GameTestHelper helper,
            ServerLevel level,
            List<StagedDungeonPieceSpec> pieces,
            DungeonLockPlan plan) {
        EnqueueResult result =
                StagedDungeonGenerationManager.enqueue(
                        level, layout(pieces, plan), LiquidSettings.IGNORE_WATERLOGGING, KEPT);
        helper.assertTrue(result.accepted(), "The job must be accepted: " + result.rejection());
        return result.id();
    }

    private static StagedDungeonLayout layout(
            List<StagedDungeonPieceSpec> pieces, DungeonLockPlan plan) {
        BoundingBox bounds = bounds(pieces);
        return new StagedDungeonLayout(
                ChunkPos.containing(pieces.getFirst().position()),
                pieces.getFirst().position(),
                bounds,
                pieces,
                plan,
                List.of());
    }

    /** The level's unfinished jobs, for failure messages: which job holds up which. */
    private static String describeJobs(ServerLevel level) {
        StringBuilder text = new StringBuilder(" [jobs:");
        for (JobView job : StagedDungeonGenerationManager.jobs(level)) {
            if (job.state() == JobState.COMPLETE) {
                continue;
            }
            text.append(' ')
                    .append(job.id().toString(), 0, 8)
                    .append(' ')
                    .append(job.state())
                    .append(' ')
                    .append(job.placedPieces())
                    .append('/')
                    .append(job.totalPieces())
                    .append(job.options().callerDrivesBudget() ? " caller" : "")
                    .append(" held=")
                    .append(StagedDungeonGenerationManager.heldChunks(level, job.id()))
                    .append(" at ")
                    .append(job.bounds().getCenter().toShortString())
                    .append(';');
        }
        return text.append(']').toString();
    }

    private static JobState state(ServerLevel level, UUID id) {
        return StagedDungeonGenerationManager.job(level, id)
                .map(JobView::state)
                .orElse(JobState.CANCELLED);
    }

    /**
     * The smallest piece of a tier-1 cobblestone layout that fits in one chunk, moved to {@code
     * origin}.
     */
    private static StagedDungeonPieceSpec smallPiece(ServerLevel level, BlockPos origin) {
        StagedDungeonPieceSpec piece =
                tier1Pieces(level).stream()
                        .filter(
                                candidate ->
                                        candidate.boundingBox().getXSpan() <= 12
                                                && candidate.boundingBox().getZSpan() <= 12)
                        .min(
                                Comparator.comparingInt(
                                        candidate ->
                                                candidate.boundingBox().getXSpan()
                                                        * candidate.boundingBox().getZSpan()))
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "A tier-1 layout must have a piece that fits in a"
                                                        + " chunk"));
        int chunkMinX =
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX())) + 2;
        int chunkMinZ =
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 2;
        BoundingBox box = piece.boundingBox();
        return moved(
                piece, chunkMinX - box.minX(), origin.getY() - box.minY(), chunkMinZ - box.minZ());
    }

    /**
     * The largest tier-1 piece at least {@code minXSpan} wide that fits in one chunk along z, moved
     * to {@code origin}.
     */
    private static StagedDungeonPieceSpec largestPiece(
            ServerLevel level, BlockPos origin, int minXSpan) {
        StagedDungeonPieceSpec piece =
                tier1Pieces(level).stream()
                        .filter(
                                candidate ->
                                        candidate.boundingBox().getXSpan() >= minXSpan
                                                && candidate.boundingBox().getXSpan() <= 16
                                                && candidate.boundingBox().getZSpan() <= 12)
                        .max(
                                Comparator.comparingInt(
                                        candidate -> candidate.boundingBox().getXSpan()))
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "A tier-1 layout must have a piece at least "
                                                        + minXSpan
                                                        + " wide"));
        int chunkMinZ =
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 2;
        BoundingBox box = piece.boundingBox();
        return moved(
                piece,
                origin.getX() - box.minX(),
                origin.getY() - box.minY(),
                chunkMinZ - box.minZ());
    }

    private static List<StagedDungeonPieceSpec> tier1Pieces(ServerLevel level) {
        return GenerateDungeonCommand.compileLayout(
                        level,
                        DungeonTheme.COBBLESTONE,
                        DungeonTier.TIER_1,
                        DungeonTier.TIER_1.size,
                        new BlockPos(0, 64, 0))
                .orElseThrow(() -> new AssertionError("A tier-1 layout must compile"))
                .pieces();
    }

    private static StagedDungeonPieceSpec moved(
            StagedDungeonPieceSpec piece, int dx, int dy, int dz) {
        return new StagedDungeonPieceSpec(
                piece.element(),
                piece.position().offset(dx, dy, dz),
                piece.rotation(),
                piece.boundingBox().moved(dx, dy, dz),
                piece.groundLevelDelta(),
                piece.startPiece());
    }

    private static BoundingBox bounds(List<StagedDungeonPieceSpec> pieces) {
        return BoundingBox.encapsulatingBoxes(
                        pieces.stream().map(StagedDungeonPieceSpec::boundingBox).toList())
                .orElseThrow();
    }

    private static List<Long> chunks(BoundingBox box) {
        List<Long> chunks = new ArrayList<>();
        for (int x = SectionPos.blockToSectionCoord(box.minX());
                x <= SectionPos.blockToSectionCoord(box.maxX());
                x++) {
            for (int z = SectionPos.blockToSectionCoord(box.minZ());
                    z <= SectionPos.blockToSectionCoord(box.maxZ());
                    z++) {
                chunks.add(ChunkPos.pack(x, z));
            }
        }
        return chunks;
    }

    private static void loadChunks(ServerLevel level, BoundingBox box) {
        for (long chunk : chunks(box)) {
            level.getChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
        }
    }

    /**
     * Loads the chunks now and holds them (and their neighbours) with a vanilla player-loading
     * ticket until {@link #releaseChunks}. GameTest servers sprint, so a test whose subject is not
     * chunk loading must not wait for asynchronous loads against its tick deadline. The ticket is
     * neither forced nor PD's, so the ticket assertions still see only PD's own.
     */
    static void holdChunks(ServerLevel level, BoundingBox box) {
        for (long chunk : chunks(box)) {
            level.getChunkSource()
                    .addTicketWithRadius(TicketType.PLAYER_LOADING, ChunkPos.unpack(chunk), 1);
        }
        loadChunks(level, box);
    }

    /**
     * Gives asynchronous entity loading wall-clock time by padding the tick to a real server's 50
     * ms. GameTest servers do not wait between ticks, and parts that spawn entities (or come from
     * elements other than PD's) wait for their chunks' entities, which are read on an I/O thread;
     * without pacing, a busy machine can spend a test's whole tick budget in about a second.
     */
    private static void pace() {
        LockSupport.parkNanos(50_000_000L);
    }

    static void releaseChunks(ServerLevel level, BoundingBox box) {
        for (long chunk : chunks(box)) {
            level.getChunkSource()
                    .removeTicketWithRadius(TicketType.PLAYER_LOADING, ChunkPos.unpack(chunk), 1);
        }
    }

    private static Map<BlockPos, BlockState> states(ServerLevel level, BoundingBox box) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(
                        box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            states.put(pos.immutable(), level.getBlockState(pos));
        }
        return states;
    }

    /**
     * A position inside {@code piece} that its template fills with a solid block, found by pasting
     * a copy 96 blocks higher.
     */
    private static BlockPos writtenPos(ServerLevel level, StagedDungeonPieceSpec piece) {
        StagedDungeonPieceSpec copy = moved(piece, 0, 96, 0);
        StagedDungeonGenerationManager.placeSynchronously(
                level, List.of(copy), LiquidSettings.IGNORE_WATERLOGGING);
        BoundingBox box = copy.boundingBox();
        for (BlockPos pos :
                BlockPos.betweenClosed(
                        box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (!level.getBlockState(pos).isAir()) {
                return pos.below(96).immutable();
            }
        }
        throw new AssertionError("The piece's template must place a solid block");
    }

    private static boolean hasStagedTicket(ServerLevel level, long chunk) {
        return DungeonChunkTickets.hasTicket(level, chunk);
    }

    private static boolean hasLegacyForcedTicket(ServerLevel level, long chunk) {
        return DungeonChunkTickets.hasLegacyForcedTicket(level, chunk);
    }
}
