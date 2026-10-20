package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.jspecify.annotations.Nullable;

/**
 * Places staged dungeons on the server thread within a per-tick budget.
 *
 * <ul>
 *     <li>Jobs are keyed by UUID and hold their layout once plus a cursor (see {@link StagedDungeonJob}).</li>
 *     <li>The unit of work is one (piece, chunk) part; levels and jobs are served round-robin.</li>
 *     <li>The budget is half of what the last 20 ticks left of the tick-rate target, measured at the end of the tick so
 *     PD's own work counts, clamped to 2-20 ms.</li>
 *     <li>Up to {@link #MAX_ACTIVE_JOBS_PER_LEVEL} jobs per level hold chunk tickets at once; the rest wait.</li>
 * </ul>
 */
public final class StagedDungeonGenerationManager {
    private static final long MIN_BUDGET_NANOS = 2_000_000L;
    private static final long MAX_BUDGET_NANOS = 20_000_000L;
    static final int MAX_ACTIVE_JOBS_PER_LEVEL = 4;
    private static final int TICK_AVERAGE_WINDOW = 20;
    private static final long[] TICK_TIMES = new long[TICK_AVERAGE_WINDOW];
    private static int tickTimeIndex;
    private static int tickTimeCount;
    private static long tickTimeTotal;
    private static long serverTickStartNanos;
    private static int levelCursor;
    private static final List<ServerLevel> ACTIVE_LEVELS = new ArrayList<>();
    private static StagedJobListener listener = new StagedJobListener() {};
    private static LongSupplier nanoClock = System::nanoTime;
    private static long budgetOverrideNanos = -1L;

    private StagedDungeonGenerationManager() {}

    public static void beginServerTick() {
        serverTickStartNanos = nanoClock.getAsLong();
    }

    /**
     * Server start and stop: forget the previous session's tick window and ticket counts (integrated servers keep the
     * JVM between sessions).
     */
    public static void resetSession() {
        resetTickWindow();
        levelCursor = 0;
        ACTIVE_LEVELS.clear();
        DungeonChunkTickets.clear();
    }

    /**
     * Empties the tick-time window the budget is computed from.
     */
    public static void resetTickWindow() {
        tickTimeIndex = 0;
        tickTimeCount = 0;
        tickTimeTotal = 0L;
        Arrays.fill(TICK_TIMES, 0L);
        serverTickStartNanos = 0L;
    }

    /**
     * Server stopping, before the final save: the jobs' records are saved together with every chunk, so the next start
     * resumes from each cursor instead of walking every piece again.
     */
    public static void serverStopping(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            StagedDungeonSaveData data = existingData(level);
            if (data != null) {
                data.markStopping();
            }
        }
        resetSession();
    }

    public static void setListener(StagedJobListener jobListener) {
        listener = jobListener == null ? new StagedJobListener() {} : jobListener;
    }

    /**
     * Test hook: replaces the clock the budget loop reads; {@code null} restores {@link System#nanoTime()}.
     */
    public static void setNanoClock(@Nullable LongSupplier clock) {
        nanoClock = clock == null ? System::nanoTime : clock;
    }

    /**
     * Test hook: a fixed budget per tick; negative restores the adaptive budget.
     */
    public static void setBudgetOverride(long nanos) {
        budgetOverrideNanos = nanos;
    }

    public static EnqueueResult enqueue(
            ServerLevel level,
            ChunkPos startChunk,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings
    ) {
        return enqueue(level, startChunk, pieces, liquidSettings, DungeonLockPlan.EMPTY);
    }

    /**
     * Schedules a dungeon (commands, tools). Every call makes a new job, even in a chunk that already has one.
     */
    public static EnqueueResult enqueue(
            ServerLevel level,
            ChunkPos startChunk,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings,
            DungeonLockPlan lockPlan
    ) {
        return enqueue(level, startChunk, pieces, liquidSettings, lockPlan, false, false, JobOptions.DEFAULT);
    }

    /**
     * Schedules a dungeon for an API caller. The job stays queryable through {@link #job} after it ends, until
     * {@link #forget} drops it.
     */
    public static EnqueueResult enqueue(ServerLevel level, StagedDungeonLayout layout, LiquidSettings liquidSettings, JobOptions options) {
        return enqueue(level, layout.startChunk(), layout.pieces(), liquidSettings, layout.lockPlan(), false, true, options);
    }

    public static void enqueueFromWorldgenMarker(
            ServerLevel level,
            ChunkPos startChunk,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings,
            DungeonLockPlan lockPlan
    ) {
        enqueue(level, startChunk, pieces, liquidSettings, lockPlan, true, false, JobOptions.DEFAULT);
    }

    private static EnqueueResult enqueue(
            ServerLevel level,
            ChunkPos startChunk,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings,
            DungeonLockPlan lockPlan,
            boolean tracked,
            boolean retain,
            JobOptions options
    ) {
        if (pieces.isEmpty()) {
            return EnqueueResult.rejected("the layout has no pieces");
        }
        StagedDungeonSaveData existing = existingData(level);
        if (tracked && existing != null) {
            // Every chunk of a natural start decorates its marker piece, so the same start arrives many times.
            if (existing.isComplete(startChunk.pack())) {
                return EnqueueResult.rejected("this natural dungeon is already built");
            }
            for (StagedDungeonJob job : existing.jobList()) {
                if (job.tracked && job.startChunk.equals(startChunk)) {
                    return EnqueueResult.rejected("this natural dungeon is already queued");
                }
            }
        }

        StagedDungeonSaveData data = level.getDataStorage().computeIfAbsent(StagedDungeonSaveData.TYPE);
        StagedDungeonJob job = new StagedDungeonJob(UUID.randomUUID(), startChunk, pieces, liquidSettings, lockPlan, tracked, retain, options);
        data.jobs().put(job.id, job);
        data.jobChanged(job);
        if (options.persistent()) {
            // The record reaches the disk before any piece does. Otherwise a crash between the first placed piece and
            // the next autosave leaves a half-built dungeon that no job knows about, and nothing ever finishes it.
            level.getDataStorage().scheduleSave().join();
        }
        return EnqueueResult.accepted(job.id);
    }

    public static Status status(ServerLevel level) {
        StagedDungeonSaveData data = existingData(level);
        if (data == null) {
            return Status.EMPTY;
        }
        int running = 0;
        int failed = data.unreadableJobs().size();
        int pendingPieces = 0;
        for (StagedDungeonJob job : data.jobList()) {
            if (job.state() == JobState.RUNNING) {
                running++;
                pendingPieces += job.remainingPieces();
            } else if (job.state() == JobState.FAILED) {
                failed++;
            }
        }
        return new Status(running, pendingPieces, failed);
    }

    public static Optional<JobView> job(ServerLevel level, UUID id) {
        StagedDungeonSaveData data = existingData(level);
        StagedDungeonJob job = data == null ? null : data.jobs().get(id);
        return job == null ? Optional.empty() : Optional.of(job.view());
    }

    public static List<JobView> jobs(ServerLevel level) {
        StagedDungeonSaveData data = existingData(level);
        return data == null ? List.of() : data.jobList().stream().map(StagedDungeonJob::view).toList();
    }

    /**
     * Test hook: the job's record as it would be saved now.
     */
    public static Tag snapshot(ServerLevel level, UUID id) {
        StagedDungeonSaveData data = existingData(level);
        StagedDungeonJob job = data == null ? null : data.jobs().get(id);
        if (job == null) {
            throw new IllegalArgumentException("No staged job " + id);
        }
        return StagedDungeonJob.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), job).getOrThrow();
    }

    /**
     * Test hook: replaces the job's record with {@code record}, as a restart that read it would. Without a clean
     * shutdown the job walks every part again from the first piece.
     */
    public static void restore(ServerLevel level, Tag record, boolean cleanShutdown) {
        StagedDungeonJob job = StagedDungeonJob.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), record)
                .getOrThrow();
        StagedDungeonSaveData data = level.getDataStorage().computeIfAbsent(StagedDungeonSaveData.TYPE);
        StagedDungeonJob previous = data.jobs().put(job.id, job);
        if (previous != null) {
            previous.releaseTickets(level);
        }
        if (!cleanShutdown) {
            job.rewindForUncleanShutdown();
        }
        data.setDirty();
    }

    /**
     * The job's layout, while its record exists. The locator is the start piece's position (else the first piece's).
     * The record keeps no compiler parents, so the layout's {@code parents} is empty.
     */
    public static Optional<StagedDungeonLayout> layout(ServerLevel level, UUID id) {
        StagedDungeonSaveData data = existingData(level);
        StagedDungeonJob job = data == null ? null : data.jobs().get(id);
        if (job == null) {
            return Optional.empty();
        }
        BlockPos locator = job.pieces.stream().filter(StagedDungeonPieceSpec::startPiece).findFirst()
                .orElse(job.pieces.getFirst()).position();
        return Optional.of(
                new StagedDungeonLayout(job.startChunk, locator, job.bounds, job.pieces, job.lockPlan, List.of()));
    }

    /**
     * Chunks the job currently holds a ticket for.
     */
    public static int heldChunks(ServerLevel level, UUID id) {
        StagedDungeonSaveData data = existingData(level);
        StagedDungeonJob job = data == null ? null : data.jobs().get(id);
        return job == null ? 0 : job.heldChunks().size();
    }

    /**
     * Records kept because they could not be read; each is a failed job with its decoder error.
     */
    public static List<String> unreadableJobs(ServerLevel level) {
        StagedDungeonSaveData data = existingData(level);
        return data == null ? List.of() : data.unreadableJobs().stream().map(StagedDungeonSaveData.UnreadableJob::error).toList();
    }

    /**
     * Stops a running job and releases its chunk tickets; placed blocks stay.
     */
    public static boolean cancel(ServerLevel level, UUID id) {
        StagedDungeonSaveData data = existingData(level);
        StagedDungeonJob job = data == null ? null : data.jobs().get(id);
        if (job == null || job.state() != JobState.RUNNING) {
            return false;
        }
        job.cancel(level);
        return true;
    }

    /**
     * Runs a failed job again from the piece that failed.
     */
    public static boolean retry(ServerLevel level, UUID id) {
        StagedDungeonSaveData data = existingData(level);
        StagedDungeonJob job = data == null ? null : data.jobs().get(id);
        if (job == null || !job.retry()) {
            return false;
        }
        data.jobChanged(job);
        return true;
    }

    /**
     * Places parts of a caller-driven job until {@code budgetNanos} is spent or the job waits or ends.
     *
     * @return nanoseconds used
     */
    public static long advance(ServerLevel level, UUID id, long budgetNanos) {
        long start = nanoClock.getAsLong();
        StagedDungeonSaveData data = existingData(level);
        StagedDungeonJob job = data == null ? null : data.jobs().get(id);
        if (job == null || !job.options.callerDrivesBudget()) {
            return 0L;
        }
        long deadline = start + budgetNanos;
        while (nanoClock.getAsLong() < deadline) {
            StagedDungeonJob.StepResult result = job.step(level);
            if (result != StagedDungeonJob.StepResult.IDLE) {
                data.jobChanged(job);
            }
            if (result != StagedDungeonJob.StepResult.PLACED) {
                break;
            }
        }
        return nanoClock.getAsLong() - start;
    }

    /**
     * Drops every job whose bounds intersect {@code box}, releasing its tickets, and removes those jobs' markers from
     * the loaded chunks inside {@code box}.
     *
     * @return the number of jobs dropped
     */
    public static int forget(ServerLevel level, BoundingBox box) {
        StagedDungeonSaveData data = existingData(level);
        if (data == null) {
            return 0;
        }
        List<StagedDungeonJob> dropped = new ArrayList<>();
        for (Iterator<StagedDungeonJob> iterator = data.jobList().iterator(); iterator.hasNext(); ) {
            StagedDungeonJob job = iterator.next();
            if (job.bounds.intersects(box)) {
                job.releaseTickets(level);
                iterator.remove();
                dropped.add(job);
            }
        }
        if (dropped.isEmpty()) {
            return 0;
        }
        for (int chunkX = SectionPos.blockToSectionCoord(box.minX()); chunkX <= SectionPos.blockToSectionCoord(box.maxX()); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(box.minZ()); chunkZ <= SectionPos.blockToSectionCoord(box.maxZ()); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk != null) {
                    dropped.forEach(job -> DungeonChunkLedger.forget(chunk, job.id));
                }
            }
        }
        data.setDirty();
        return dropped.size();
    }

    /**
     * Removes the radius-1 FORCED tickets PD added before r61 inside known dungeon bounds (natural starts and current
     * jobs) and lists the other level-32 forced chunks, whose owner PD cannot prove.
     */
    public static LegacyTicketReport releaseLegacyForcedTickets(ServerLevel level, List<BoundingBox> knownDungeons) {
        LongSet forced = new LongOpenHashSet(DungeonChunkTickets.forcedChunks(level));
        int released = 0;
        List<ChunkPos> unknown = new ArrayList<>();
        LongIterator iterator = forced.iterator();
        while (iterator.hasNext()) {
            long chunk = iterator.nextLong();
            if (!DungeonChunkTickets.hasLegacyForcedTicket(level, chunk)) {
                continue;
            }
            ChunkPos pos = ChunkPos.unpack(chunk);
            boolean known = false;
            for (BoundingBox box : knownDungeons) {
                if (box.intersects(pos.getMinBlockX(), pos.getMinBlockZ(), pos.getMaxBlockX(), pos.getMaxBlockZ())) {
                    known = true;
                    break;
                }
            }
            if (known) {
                if (DungeonChunkTickets.releaseLegacyForcedTicket(level, chunk)) {
                    released++;
                }
            } else {
                unknown.add(pos);
            }
        }
        return new LegacyTicketReport(released, unknown);
    }

    /**
     * Bounds of the jobs of {@code level}, any state.
     */
    public static List<BoundingBox> jobBounds(ServerLevel level) {
        StagedDungeonSaveData data = existingData(level);
        return data == null ? List.of() : data.jobList().stream().map(job -> job.bounds).toList();
    }

    /**
     * Start chunks of the natural dungeons already built in {@code level}.
     */
    public static LongSet completedStartChunks(ServerLevel level) {
        StagedDungeonSaveData data = existingData(level);
        return data == null ? new LongOpenHashSet() : data.completedStartChunks();
    }

    public static void runServerTick(MinecraftServer server) {
        long start = nanoClock.getAsLong();
        long budgetNanos = budgetNanos(server);
        long deadline = start + budgetNanos;
        int placedParts = 0;

        ACTIVE_LEVELS.clear();
        for (ServerLevel level : server.getAllLevels()) {
            StagedDungeonSaveData data = existingData(level);
            if (data == null) {
                continue;
            }
            if (data.takeLegacyTicketRelease()) {
                releaseMigratedTickets(level, data);
            }
            if (hasBackgroundWork(data)) {
                ACTIVE_LEVELS.add(level);
            }
        }

        int levels = ACTIVE_LEVELS.size();
        int idleLevels = 0;
        while (levels > 0 && idleLevels < levels && nanoClock.getAsLong() < deadline) {
            ServerLevel level = ACTIVE_LEVELS.get(Math.floorMod(levelCursor++, levels));
            if (stepLevel(level, existingData(level))) {
                idleLevels = 0;
                placedParts++;
            } else {
                idleLevels++;
            }
        }

        recordServerTickTime();
        logBudgetUse(server, budgetNanos, nanoClock.getAsLong() - start, placedParts);
    }

    private static boolean hasBackgroundWork(StagedDungeonSaveData data) {
        for (StagedDungeonJob job : data.jobList()) {
            if (job.state() == JobState.RUNNING && !job.options.callerDrivesBudget()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives one part of work to the next admitted job of {@code level}, round-robin.
     *
     * @return whether a part was placed
     */
    private static boolean stepLevel(ServerLevel level, StagedDungeonSaveData data) {
        List<StagedDungeonJob> admitted = new ArrayList<>(MAX_ACTIVE_JOBS_PER_LEVEL);
        for (StagedDungeonJob job : data.jobList()) {
            if (job.state() == JobState.RUNNING && !job.options.callerDrivesBudget()) {
                admitted.add(job);
                if (admitted.size() == MAX_ACTIVE_JOBS_PER_LEVEL) {
                    break;
                }
            }
        }
        for (int tried = 0; tried < admitted.size(); tried++) {
            StagedDungeonJob job = admitted.get(Math.floorMod(data.jobCursor++, admitted.size()));
            StagedDungeonJob.StepResult result = job.step(level);
            if (result != StagedDungeonJob.StepResult.WAITING && result != StagedDungeonJob.StepResult.IDLE) {
                data.jobChanged(job);
            }
            if (result == StagedDungeonJob.StepResult.PLACED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Migrated jobs held radius-1 FORCED tickets that were saved with the world; release those inside their bounds.
     */
    private static void releaseMigratedTickets(ServerLevel level, StagedDungeonSaveData data) {
        LegacyTicketReport report = releaseLegacyForcedTickets(level, jobBounds(level));
        ProceduralDungeon.LOGGER.warn(
                "Released {} forced chunk ticket(s) left by {} migrated staged dungeon job(s) in {}; {} other level-32 forced chunk(s) remain. "
                        + "Run /dungeondebug releaseforced to release those inside built natural dungeons.",
                report.released(), data.migratedLegacyJobs(), level.dimension().identifier(), report.unknown().size());
    }

    static void progressed(ServerLevel level, StagedDungeonJob job) {
        listener.progressed(level, job.id, job.cursor(), job.pieces.size());
    }

    /**
     * A job left the RUNNING state. Completed and cancelled jobs are dropped unless an API caller asked to keep them;
     * failed jobs stay for {@code /dungeondebug}.
     */
    static void finished(ServerLevel level, StagedDungeonJob job) {
        StagedDungeonSaveData data = existingData(level);
        if (data == null) {
            return;
        }
        if (job.state() == JobState.COMPLETE && job.tracked) {
            data.markComplete(job.startChunk.pack());
        }
        if ((job.state() == JobState.COMPLETE || job.state() == JobState.CANCELLED) && !job.retain) {
            data.jobs().remove(job.id);
        }
        data.setDirty();
        switch (job.state()) {
            case COMPLETE -> listener.completed(level, job.id);
            case CANCELLED -> listener.cancelled(level, job.id);
            case FAILED -> listener.failed(level, job.id, job.failure());
            case RUNNING -> {
            }
        }
    }

    private static long budgetNanos(MinecraftServer server) {
        if (budgetOverrideNanos >= 0L) {
            return budgetOverrideNanos;
        }
        long target = server.tickRateManager().nanosecondsPerTick();
        long remaining = Math.max(0L, target - averageTickNanos());
        return Math.clamp(remaining / 2L, MIN_BUDGET_NANOS, MAX_BUDGET_NANOS);
    }

    /**
     * Records this tick's duration up to now, after PD's own work, so the budget reacts to what PD spends.
     */
    private static void recordServerTickTime() {
        if (serverTickStartNanos == 0L) {
            return;
        }

        long elapsed = Math.max(0L, nanoClock.getAsLong() - serverTickStartNanos);
        if (tickTimeCount < TICK_TIMES.length) {
            tickTimeCount++;
        } else {
            tickTimeTotal -= TICK_TIMES[tickTimeIndex];
        }

        TICK_TIMES[tickTimeIndex] = elapsed;
        tickTimeTotal += elapsed;
        tickTimeIndex = (tickTimeIndex + 1) % TICK_TIMES.length;
    }

    public static long averageTickNanos() {
        return tickTimeCount == 0 ? 0L : tickTimeTotal / tickTimeCount;
    }

    private static void logBudgetUse(MinecraftServer server, long budgetNanos, long usedNanos, int placedParts) {
        if (!ProceduralDungeon.LOGGER.isDebugEnabled() || placedParts == 0) {
            return;
        }

        int jobCount = 0;
        int pendingPieces = 0;
        for (ServerLevel level : server.getAllLevels()) {
            Status status = status(level);
            jobCount += status.jobs();
            pendingPieces += status.pendingPieces();
        }
        ProceduralDungeon.LOGGER.debug(
                "Staged dungeon generation tick: avg MSPT {}, budget {} ms, used {} ms, placed {} part(s), jobs {}, pending pieces {}.",
                "%.2f".formatted(averageTickNanos() / 1_000_000.0),
                "%.2f".formatted(budgetNanos / 1_000_000.0),
                "%.2f".formatted(usedNanos / 1_000_000.0),
                placedParts,
                jobCount,
                pendingPieces
        );
    }

    /**
     * The level's job data, without creating it: levels that never had a job keep no PD file.
     */
    static @Nullable StagedDungeonSaveData existingData(ServerLevel level) {
        return level.getDataStorage().get(StagedDungeonSaveData.TYPE);
    }

    /**
     * Operator and development tools only: pastes every piece in one call, generating chunks synchronously on the
     * server thread. A tier-5 dungeon takes seconds; use {@link #enqueue} for anything players wait on.
     */
    public static int placeSynchronously(ServerLevel level, List<StagedDungeonPieceSpec> pieces, LiquidSettings liquidSettings) {
        int placed = 0;
        RandomSource random = RandomSource.create(0L);
        for (StagedDungeonPieceSpec piece : pieces) {
            if (placePiece(level, piece, piece.boundingBox(), liquidSettings, random, null)) {
                placed++;
            }
        }

        return placed;
    }

    /**
     * Pastes {@code piece} clipped to {@code box}.
     *
     * @return the element's result: false when its template is missing or empty
     */
    static boolean placePiece(
            ServerLevel level,
            StagedDungeonPieceSpec piece,
            BoundingBox box,
            LiquidSettings liquidSettings,
            RandomSource random,
            @Nullable StagedPlacementContext context
    ) {
        long start = System.nanoTime();
        random.setSeed(level.getSeed() ^ piece.position().asLong());
        StagedPlacementContext.set(context);
        boolean placed;
        try {
            placed = piece.element().place(
                    level.getStructureManager(),
                    level,
                    level.structureManager(),
                    level.getChunkSource().getGenerator(),
                    piece.position(),
                    BlockPos.ZERO,
                    piece.rotation(),
                    box,
                    random,
                    liquidSettings,
                    false
            );
        } finally {
            StagedPlacementContext.set(null);
        }
        long elapsed = System.nanoTime() - start;
        if (elapsed > MAX_BUDGET_NANOS) {
            ProceduralDungeon.LOGGER.debug(
                    "Staged dungeon piece {} at {} (box {}) exceeded max dynamic budget: {} ms",
                    StagedDungeonJob.templateName(piece),
                    piece.position().toShortString(),
                    box,
                    "%.2f".formatted(elapsed / 1_000_000.0)
            );
        }
        return placed;
    }

    public enum JobState implements StringRepresentable {
        RUNNING,
        FAILED,
        CANCELLED,
        COMPLETE;

        public static final Codec<JobState> CODEC = StringRepresentable.fromEnum(JobState::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param callerDrivesBudget  the background tick skips the job; the caller places it through {@link #advance}
     * @param persistent          whether the job is saved with the level (false for disposable worlds)
     * @param placementSeed       seed recorded with the job for seeded placement
     * @param guardianHealthScale recorded with the job for the API, which applies it to spawned guardians
     */
    public record JobOptions(boolean callerDrivesBudget, boolean persistent, long placementSeed, double guardianHealthScale) {
        public static final JobOptions DEFAULT = new JobOptions(false, true, 0L, 1.0);
        public static final Codec<JobOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("caller_drives_budget", false).forGetter(JobOptions::callerDrivesBudget),
                Codec.BOOL.optionalFieldOf("persistent", true).forGetter(JobOptions::persistent),
                Codec.LONG.optionalFieldOf("placement_seed", 0L).forGetter(JobOptions::placementSeed),
                Codec.DOUBLE.optionalFieldOf("guardian_health_scale", 1.0).forGetter(JobOptions::guardianHealthScale)
        ).apply(instance, JobOptions::new));
    }

    public record EnqueueResult(@Nullable UUID id, @Nullable String rejection) {
        static EnqueueResult accepted(UUID id) {
            return new EnqueueResult(id, null);
        }

        static EnqueueResult rejected(String reason) {
            return new EnqueueResult(null, reason);
        }

        public boolean accepted() {
            return id != null;
        }
    }

    /**
     * @param placedPieces pieces before the cursor
     * @param failure      why the job failed, for FAILED jobs
     * @param retained     whether the record outlives completion (jobs enqueued with {@link JobOptions})
     */
    public record JobView(
            UUID id,
            JobState state,
            int placedPieces,
            int totalPieces,
            BoundingBox bounds,
            @Nullable String failure,
            JobOptions options,
            boolean retained
    ) {}

    /**
     * @param jobs          running jobs
     * @param pendingPieces pieces the running jobs have not finished
     * @param failedJobs    failed jobs, including unreadable records
     */
    public record Status(int jobs, int pendingPieces, int failedJobs) {
        static final Status EMPTY = new Status(0, 0, 0);
    }

    public record LegacyTicketReport(int released, List<ChunkPos> unknown) {}

    /**
     * Job events, called on the server thread after the change.
     */
    public interface StagedJobListener {
        default void progressed(ServerLevel level, UUID job, int placedPieces, int totalPieces) {}

        default void completed(ServerLevel level, UUID job) {}

        default void failed(ServerLevel level, UUID job, @Nullable String reason) {}

        default void cancelled(ServerLevel level, UUID job) {}
    }
}
