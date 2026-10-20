package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobOptions;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobState;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One staged dungeon: the immutable layout, stored once, and a cursor over its pieces. The unit of
 * work is one (piece, chunk) part: the piece is pasted clipped to one chunk and the chunk's {@link
 * DungeonChunkLedger} marker is set in the same step, so a resumed job skips every part already in
 * the world.
 */
final class StagedDungeonJob {
    /**
     * Chunks a running job keeps loaded ahead of its cursor (all chunks of the current piece are
     * always held).
     */
    static final int WINDOW_CHUNKS = 16;

    /** Pieces the window looks ahead at most, for layouts whose pieces share few chunks. */
    static final int WINDOW_PIECES = 64;

    /**
     * A part whose chunk saw this many ticks of player presence since it entered the window is left
     * alone: players may have built there while the job waited (restart, stall or a starved
     * budget).
     */
    static final long PLAYER_PRESENCE_LIMIT_TICKS = 1200L;

    /** Placement attempts of one part that end in an exception before the job is marked failed. */
    static final int MAX_ATTEMPTS = 3;

    private static final Codec<Long2LongOpenHashMap> BASELINE_CODEC =
            Codec.LONG
                    .listOf()
                    .xmap(
                            flat -> {
                                Long2LongOpenHashMap map =
                                        new Long2LongOpenHashMap(flat.size() / 2);
                                for (int i = 0; i + 1 < flat.size(); i += 2) {
                                    map.put(flat.get(i).longValue(), flat.get(i + 1).longValue());
                                }
                                return map;
                            },
                            map -> {
                                List<Long> flat = new ArrayList<>(map.size() * 2);
                                map.long2LongEntrySet()
                                        .forEach(
                                                entry -> {
                                                    flat.add(entry.getLongKey());
                                                    flat.add(entry.getLongValue());
                                                });
                                return flat;
                            });

    static final Codec<StagedDungeonJob> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            UUIDUtil.STRING_CODEC
                                                    .fieldOf("id")
                                                    .forGetter(job -> job.id),
                                            Codec.LONG
                                                    .fieldOf("start_chunk")
                                                    .forGetter(job -> job.startChunk.pack()),
                                            StagedDungeonPieceSpec.CODEC
                                                    .listOf()
                                                    .fieldOf("pieces")
                                                    .forGetter(job -> job.pieces),
                                            LiquidSettings.CODEC
                                                    .optionalFieldOf(
                                                            "liquid_settings",
                                                            LiquidSettings.IGNORE_WATERLOGGING)
                                                    .forGetter(job -> job.liquidSettings),
                                            DungeonLockPlan.CODEC
                                                    .optionalFieldOf(
                                                            "lock_plan", DungeonLockPlan.EMPTY)
                                                    .forGetter(job -> job.lockPlan),
                                            Codec.BOOL
                                                    .optionalFieldOf("tracked", false)
                                                    .forGetter(job -> job.tracked),
                                            Codec.BOOL
                                                    .optionalFieldOf("retain", false)
                                                    .forGetter(job -> job.retain),
                                            JobOptions.CODEC
                                                    .optionalFieldOf("options", JobOptions.DEFAULT)
                                                    .forGetter(job -> job.options),
                                            Codec.INT
                                                    .optionalFieldOf("cursor", 0)
                                                    .forGetter(job -> job.cursor),
                                            JobState.CODEC
                                                    .optionalFieldOf("state", JobState.RUNNING)
                                                    .forGetter(job -> job.state),
                                            Codec.STRING
                                                    .optionalFieldOf("failure")
                                                    .forGetter(
                                                            job ->
                                                                    Optional.ofNullable(
                                                                            job.failure)),
                                            Codec.INT
                                                    .optionalFieldOf("skipped_parts", 0)
                                                    .forGetter(job -> job.skippedParts),
                                            BASELINE_CODEC
                                                    .optionalFieldOf("presence_baselines")
                                                    .forGetter(
                                                            job ->
                                                                    job.presenceBaselines.isEmpty()
                                                                            ? Optional.empty()
                                                                            : Optional.of(
                                                                                    job.presenceBaselines)))
                                    .apply(instance, StagedDungeonJob::decoded));

    final UUID id;
    final ChunkPos startChunk;
    final List<StagedDungeonPieceSpec> pieces;
    final LiquidSettings liquidSettings;
    final DungeonLockPlan lockPlan;
    final boolean tracked;

    /**
     * Keep the record after it completes or is cancelled (API jobs), so its state stays queryable
     * until forgotten.
     */
    final boolean retain;

    final JobOptions options;
    final BoundingBox bounds;
    private int cursor;
    private JobState state;
    private @Nullable String failure;
    private int skippedParts;

    /** Inhabited time of each chunk when it entered the job's window. */
    private final Long2LongOpenHashMap presenceBaselines;

    private final LongSet heldChunks = new LongOpenHashSet();
    private final LongSet unbaselinedChunks = new LongOpenHashSet();
    private final LongSet currentPieceDone = new LongOpenHashSet();
    private int windowCursor = -1;
    private int attempts;
    private final RandomSource placementRandom = RandomSource.create(0L);

    StagedDungeonJob(
            UUID id,
            ChunkPos startChunk,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings,
            DungeonLockPlan lockPlan,
            boolean tracked,
            boolean retain,
            JobOptions options) {
        this(
                id,
                startChunk,
                pieces,
                liquidSettings,
                lockPlan,
                tracked,
                retain,
                options,
                0,
                JobState.RUNNING,
                null,
                0,
                new Long2LongOpenHashMap());
    }

    private StagedDungeonJob(
            UUID id,
            ChunkPos startChunk,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings,
            DungeonLockPlan lockPlan,
            boolean tracked,
            boolean retain,
            JobOptions options,
            int cursor,
            JobState state,
            @Nullable String failure,
            int skippedParts,
            Long2LongOpenHashMap presenceBaselines) {
        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("A staged dungeon job needs at least one piece");
        }
        this.id = id;
        this.startChunk = startChunk;
        this.pieces = List.copyOf(pieces);
        this.liquidSettings = liquidSettings;
        this.lockPlan = lockPlan;
        this.tracked = tracked;
        this.retain = retain;
        this.options = options;
        this.bounds =
                BoundingBox.encapsulatingBoxes(
                                this.pieces.stream()
                                        .map(StagedDungeonPieceSpec::boundingBox)
                                        .toList())
                        .orElseThrow();
        this.cursor = Math.clamp(cursor, 0, this.pieces.size());
        this.state = state;
        this.failure = failure;
        this.skippedParts = skippedParts;
        this.presenceBaselines = presenceBaselines;
    }

    private static StagedDungeonJob decoded(
            UUID id,
            long startChunk,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings,
            DungeonLockPlan lockPlan,
            boolean tracked,
            boolean retain,
            JobOptions options,
            int cursor,
            JobState state,
            Optional<String> failure,
            int skippedParts,
            Optional<Long2LongOpenHashMap> presenceBaselines) {
        return new StagedDungeonJob(
                id,
                ChunkPos.unpack(startChunk),
                pieces,
                liquidSettings,
                lockPlan,
                tracked,
                retain,
                options,
                cursor,
                state,
                failure.orElse(null),
                skippedParts,
                presenceBaselines.orElseGet(Long2LongOpenHashMap::new));
    }

    /**
     * Converts a job saved before r61 ({@code pending_pieces}: only the pieces not yet placed). It
     * has no markers, so it starts from the first remaining piece. The id is derived from the
     * record, so a crash before the migrated data is saved migrates to the same id again.
     */
    static <T> DataResult<StagedDungeonJob> fromLegacy(Dynamic<T> tag) {
        long startChunk = tag.get("start_chunk").asLong(0L);
        LiquidSettings liquidSettings =
                tag.get("liquid_settings")
                        .read(LiquidSettings.CODEC)
                        .result()
                        .orElse(LiquidSettings.IGNORE_WATERLOGGING);
        DungeonLockPlan lockPlan =
                tag.get("lock_plan")
                        .read(DungeonLockPlan.CODEC)
                        .result()
                        .orElse(DungeonLockPlan.EMPTY);
        boolean tracked = tag.get("tracked").asBoolean(false);
        return tag.get("pending_pieces")
                .read(StagedDungeonPieceSpec.CODEC.listOf())
                .flatMap(
                        pieces -> {
                            if (pieces.isEmpty()) {
                                return DataResult.error(
                                        () -> "Legacy staged dungeon job has no pending pieces");
                            }
                            BlockPos first = pieces.getFirst().position();
                            String name =
                                    "legacy:%d:%d:%d:%s"
                                            .formatted(
                                                    startChunk,
                                                    pieces.size(),
                                                    first.asLong(),
                                                    tracked);
                            UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
                            return DataResult.success(
                                    new StagedDungeonJob(
                                            id,
                                            ChunkPos.unpack(startChunk),
                                            pieces,
                                            liquidSettings,
                                            lockPlan,
                                            tracked,
                                            false,
                                            JobOptions.DEFAULT));
                        });
    }

    JobState state() {
        return state;
    }

    int cursor() {
        return cursor;
    }

    @Nullable String failure() {
        return failure;
    }

    int skippedParts() {
        return skippedParts;
    }

    int remainingPieces() {
        return state == JobState.RUNNING ? pieces.size() - cursor : 0;
    }

    JobView view() {
        return new JobView(id, state, cursor, pieces.size(), bounds, failure, options, retain);
    }

    /**
     * After an unclean shutdown the record may be older or newer than the chunks, so walk every
     * part again; the markers skip what the chunks already hold. A kept completed job is checked
     * the same way, since its chunks may have lost parts it placed.
     */
    void rewindForUncleanShutdown() {
        if (state == JobState.RUNNING || state == JobState.COMPLETE) {
            state = JobState.RUNNING;
            cursor = 0;
            currentPieceDone.clear();
            windowCursor = -1;
        }
    }

    /** Places at most one (piece, chunk) part. */
    StepResult step(ServerLevel level) {
        if (state != JobState.RUNNING) {
            return StepResult.IDLE;
        }
        if (cursor >= pieces.size()) {
            complete(level);
            return StepResult.FINISHED;
        }

        updateWindow(level);
        recordPendingBaselines(level);
        StagedDungeonPieceSpec piece = pieces.get(cursor);
        BoundingBox box = piece.boundingBox();
        if (isPuzzle(piece)) {
            return stepWholePiece(level, piece, box);
        }

        boolean waiting = false;
        int minChunkX = SectionPos.blockToSectionCoord(box.minX());
        int maxChunkX = SectionPos.blockToSectionCoord(box.maxX());
        int minChunkZ = SectionPos.blockToSectionCoord(box.minZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(box.maxZ());
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long packed = ChunkPos.pack(chunkX, chunkZ);
                if (currentPieceDone.contains(packed)) {
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null || !level.areEntitiesLoaded(packed)) {
                    waiting = true;
                    continue;
                }
                if (DungeonChunkLedger.isPlaced(chunk, id, cursor)) {
                    currentPieceDone.add(packed);
                    continue;
                }
                if (playersChanged(chunk, packed)) {
                    skipPart(packed);
                    continue;
                }
                return placePart(level, piece, chunk, clip(box, chunkX, chunkZ), List.of(chunk));
            }
        }

        if (waiting) {
            return StepResult.WAITING;
        }
        advance(level);
        return StepResult.PLACED;
    }

    /**
     * Puzzle rooms settle redstone across the whole room, so they are placed in one call once every
     * chunk is loaded. After a crash that kept only some of the room's chunks, the missing parts
     * are placed and the whole room is settled again.
     */
    private StepResult stepWholePiece(
            ServerLevel level, StagedDungeonPieceSpec piece, BoundingBox box) {
        List<LevelChunk> chunks = new ArrayList<>();
        int minChunkX = SectionPos.blockToSectionCoord(box.minX());
        int maxChunkX = SectionPos.blockToSectionCoord(box.maxX());
        int minChunkZ = SectionPos.blockToSectionCoord(box.minZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(box.maxZ());
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null || !level.areEntitiesLoaded(ChunkPos.pack(chunkX, chunkZ))) {
                    return StepResult.WAITING;
                }
                chunks.add(chunk);
            }
        }

        List<LevelChunk> missing = new ArrayList<>(chunks.size());
        for (LevelChunk chunk : chunks) {
            long packed = chunk.getPos().pack();
            if (!DungeonChunkLedger.isPlaced(chunk, id, cursor)) {
                if (playersChanged(chunk, packed)) {
                    skipPart(packed);
                } else {
                    missing.add(chunk);
                }
            }
        }
        if (missing.isEmpty()) {
            advance(level);
            return StepResult.PLACED;
        }
        if (missing.size() == chunks.size()) {
            return placePart(level, piece, null, box, missing);
        }

        for (LevelChunk chunk : missing) {
            StepResult result =
                    placePart(
                            level,
                            piece,
                            chunk,
                            clip(box, chunk.getPos().x(), chunk.getPos().z()),
                            List.of(chunk));
            if (result != StepResult.PLACED) {
                return result;
            }
        }
        DungeonPuzzleRooms.finishPlacement(level, box);
        return StepResult.PLACED;
    }

    private StepResult placePart(
            ServerLevel level,
            StagedDungeonPieceSpec piece,
            @Nullable LevelChunk single,
            BoundingBox box,
            List<LevelChunk> chunks) {
        boolean placed;
        try {
            placed =
                    StagedDungeonGenerationManager.placePiece(
                            level,
                            piece,
                            box,
                            liquidSettings,
                            placementRandom,
                            new StagedPlacementContext(id, cursor, AABB.of(bounds)));
        } catch (RuntimeException exception) {
            attempts++;
            ProceduralDungeon.LOGGER.warn(
                    "Staged dungeon job {} piece {} ({}) threw on attempt {} of {}.",
                    id,
                    cursor,
                    templateName(piece),
                    attempts,
                    MAX_ATTEMPTS,
                    exception);
            if (attempts >= MAX_ATTEMPTS) {
                fail(
                        level,
                        "piece %d (%s) threw %s".formatted(cursor, templateName(piece), exception));
                return StepResult.FAILED;
            }
            return StepResult.WAITING;
        }
        if (!placed) {
            fail(
                    level,
                    "piece %d (%s) could not be placed: its template is missing or empty"
                            .formatted(cursor, templateName(piece)));
            return StepResult.FAILED;
        }

        attempts = 0;
        DungeonLockManager.applyPlanForPiece(level, lockPlan, box);
        for (LevelChunk chunk : chunks) {
            long packed = chunk.getPos().pack();
            DungeonChunkLedger.markPlaced(chunk, id, cursor);
            currentPieceDone.add(packed);
            // Presence counts from the job's last write into the chunk.
            presenceBaselines.put(packed, chunk.getInhabitedTime());
        }
        if (single == null) {
            // The whole piece is placed.
            advance(level);
        }
        return StepResult.PLACED;
    }

    private void advance(ServerLevel level) {
        cursor++;
        currentPieceDone.clear();
        StagedDungeonGenerationManager.progressed(level, this);
        if (cursor >= pieces.size()) {
            complete(level);
        }
    }

    /**
     * A part whose chunk saw players for longer than {@link #PLAYER_PRESENCE_LIMIT_TICKS} since the
     * chunk entered the window is left alone. The baseline is taken when the chunk is first seen
     * loaded inside the window, which in normal operation is shortly before the job writes into it.
     */
    private boolean playersChanged(LevelChunk chunk, long packed) {
        long baseline = presenceBaselines.getOrDefault(packed, Long.MIN_VALUE);
        if (baseline == Long.MIN_VALUE) {
            presenceBaselines.put(packed, chunk.getInhabitedTime());
            return false;
        }
        return chunk.getInhabitedTime() - baseline > PLAYER_PRESENCE_LIMIT_TICKS;
    }

    private void recordPendingBaselines(ServerLevel level) {
        if (unbaselinedChunks.isEmpty()) {
            return;
        }
        LongIterator iterator = unbaselinedChunks.iterator();
        while (iterator.hasNext()) {
            long packed = iterator.nextLong();
            LevelChunk chunk =
                    level.getChunkSource()
                            .getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            if (chunk != null) {
                presenceBaselines.putIfAbsent(packed, chunk.getInhabitedTime());
                iterator.remove();
            }
        }
    }

    private void skipPart(long packed) {
        currentPieceDone.add(packed);
        skippedParts++;
        ProceduralDungeon.LOGGER.warn(
                "Staged dungeon job {} left piece {} alone in chunk {}: players were there while it"
                        + " waited.",
                id,
                cursor,
                ChunkPos.unpack(packed));
    }

    private void complete(ServerLevel level) {
        state = JobState.COMPLETE;
        releaseTickets(level);
        StagedDungeonGenerationManager.finished(level, this);
    }

    void fail(ServerLevel level, String reason) {
        state = JobState.FAILED;
        failure = reason;
        releaseTickets(level);
        ProceduralDungeon.LOGGER.error("Staged dungeon job {} failed: {}", id, reason);
        StagedDungeonGenerationManager.finished(level, this);
    }

    void cancel(ServerLevel level) {
        if (state != JobState.RUNNING) {
            return;
        }
        state = JobState.CANCELLED;
        releaseTickets(level);
        StagedDungeonGenerationManager.finished(level, this);
    }

    /** Runs a failed job again from its cursor. */
    boolean retry() {
        if (state != JobState.FAILED) {
            return false;
        }
        state = JobState.RUNNING;
        failure = null;
        attempts = 0;
        windowCursor = -1;
        return true;
    }

    /**
     * Holds the chunks of the current piece and of following pieces up to {@link #WINDOW_CHUNKS};
     * releases the rest.
     */
    private void updateWindow(ServerLevel level) {
        if (windowCursor == cursor) {
            return;
        }
        windowCursor = cursor;
        LongLinkedOpenHashSet wanted = new LongLinkedOpenHashSet();
        for (int index = cursor; index < pieces.size(); index++) {
            if (index > cursor
                    && (wanted.size() >= WINDOW_CHUNKS || index - cursor >= WINDOW_PIECES)) {
                break;
            }
            addChunks(wanted, pieces.get(index).boundingBox());
        }

        LongArrayList released = new LongArrayList();
        LongIterator held = heldChunks.iterator();
        while (held.hasNext()) {
            long chunk = held.nextLong();
            if (!wanted.contains(chunk)) {
                released.add(chunk);
            }
        }
        for (int i = 0; i < released.size(); i++) {
            long chunk = released.getLong(i);
            heldChunks.remove(chunk);
            unbaselinedChunks.remove(chunk);
            DungeonChunkTickets.release(level, chunk);
        }
        LongIterator iterator = wanted.iterator();
        while (iterator.hasNext()) {
            long chunk = iterator.nextLong();
            if (heldChunks.add(chunk)) {
                DungeonChunkTickets.acquire(level, chunk);
                if (!presenceBaselines.containsKey(chunk)) {
                    unbaselinedChunks.add(chunk);
                }
            }
        }
    }

    void releaseTickets(ServerLevel level) {
        LongIterator iterator = heldChunks.iterator();
        while (iterator.hasNext()) {
            DungeonChunkTickets.release(level, iterator.nextLong());
        }
        heldChunks.clear();
        unbaselinedChunks.clear();
        windowCursor = -1;
    }

    LongSet heldChunks() {
        return heldChunks;
    }

    /** Every chunk this job's pieces touch. */
    LongSet footprint() {
        LongSet chunks = new LongOpenHashSet();
        for (StagedDungeonPieceSpec piece : pieces) {
            addChunks(chunks, piece.boundingBox());
        }
        return chunks;
    }

    private static void addChunks(LongSet chunks, BoundingBox box) {
        for (int chunkX = SectionPos.blockToSectionCoord(box.minX());
                chunkX <= SectionPos.blockToSectionCoord(box.maxX());
                chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(box.minZ());
                    chunkZ <= SectionPos.blockToSectionCoord(box.maxZ());
                    chunkZ++) {
                chunks.add(ChunkPos.pack(chunkX, chunkZ));
            }
        }
    }

    private static BoundingBox clip(BoundingBox box, int chunkX, int chunkZ) {
        int minX = SectionPos.sectionToBlockCoord(chunkX);
        int minZ = SectionPos.sectionToBlockCoord(chunkZ);
        return new BoundingBox(
                Math.max(box.minX(), minX),
                box.minY(),
                Math.max(box.minZ(), minZ),
                Math.min(box.maxX(), minX + 15),
                box.maxY(),
                Math.min(box.maxZ(), minZ + 15));
    }

    private static boolean isPuzzle(StagedDungeonPieceSpec piece) {
        return piece.element() instanceof VariantSinglePoolElement variant
                && DungeonPuzzleRooms.isPuzzle(variant.templateLocation());
    }

    static String templateName(StagedDungeonPieceSpec piece) {
        return piece.element() instanceof VariantSinglePoolElement variant
                ? variant.templateLocation().toString()
                : piece.element().toString();
    }

    enum StepResult {
        /** A part was placed (or the cursor moved past parts already in the world). */
        PLACED,
        /** Waiting for chunks or entities to load. */
        WAITING,
        FAILED,
        FINISHED,
        IDLE
    }
}
