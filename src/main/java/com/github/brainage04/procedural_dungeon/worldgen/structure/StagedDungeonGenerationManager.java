package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

public final class StagedDungeonGenerationManager {
    private static final long TARGET_TICK_NANOS = 50_000_000L;
    private static final long MIN_BUDGET_NANOS = 2_000_000L;
    private static final long MAX_BUDGET_NANOS = 20_000_000L;
    private static final int TICK_AVERAGE_WINDOW = 20;
    private static final Map<ResourceKey<net.minecraft.world.level.Level>, Map<Long, Job>> JOBS = new HashMap<>();
    private static final long[] TICK_TIMES = new long[TICK_AVERAGE_WINDOW];
    private static int tickTimeIndex;
    private static int tickTimeCount;
    private static long tickTimeTotal;
    private static long serverTickStartNanos;

    private StagedDungeonGenerationManager() {}

    public static void initialize() {
        ServerTickEvents.START_SERVER_TICK.register(server -> serverTickStartNanos = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(StagedDungeonGenerationManager::runServerTick);
    }

    public static void enqueue(
            ServerLevel level,
            ChunkPos startChunk,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings
    ) {
        enqueue(level, startChunk, pieces, liquidSettings, false);
    }

    public static void enqueueFromWorldgenMarker(
            ServerLevel level,
            ChunkPos startChunk,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings
    ) {
        enqueue(level, startChunk, pieces, liquidSettings, true);
    }

    private static void enqueue(
            ServerLevel level,
            ChunkPos startChunk,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings,
            boolean tracked
    ) {
        if (tracked && data(level).isComplete(startChunk.pack())) {
            return;
        }

        Map<Long, Job> jobs = JOBS.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        jobs.computeIfAbsent(startChunk.pack(), ignored -> new Job(startChunk, pieces, liquidSettings, tracked));
    }

    public static Status status(ServerLevel level) {
        Map<Long, Job> jobs = JOBS.get(level.dimension());
        if (jobs == null || jobs.isEmpty()) {
            return new Status(0, 0);
        }

        int pendingPieces = 0;
        for (Job job : jobs.values()) {
            pendingPieces += job.pending.size();
        }
        return new Status(jobs.size(), pendingPieces);
    }

    private static void runServerTick(MinecraftServer server) {
        recordServerTickTime();
        if (JOBS.isEmpty()) {
            return;
        }

        long budgetNanos = dynamicBudgetNanos();
        long start = System.nanoTime();
        long deadline = System.nanoTime() + budgetNanos;
        int placedPieces = 0;
        while (!JOBS.isEmpty() && System.nanoTime() < deadline) {
            boolean placedAnyPiece = false;
            var dimensionIterator = JOBS.entrySet().iterator();
            while (dimensionIterator.hasNext() && System.nanoTime() < deadline) {
                var dimensionEntry = dimensionIterator.next();
                ServerLevel level = server.getLevel(dimensionEntry.getKey());
                Map<Long, Job> jobs = dimensionEntry.getValue();
                if (level == null || jobs.isEmpty()) {
                    dimensionIterator.remove();
                    continue;
                }

                var jobIterator = jobs.entrySet().iterator();
                while (jobIterator.hasNext() && System.nanoTime() < deadline) {
                    Job job = jobIterator.next().getValue();
                    if (job.placeNextReadyPiece(level)) {
                        placedAnyPiece = true;
                        placedPieces++;
                    }
                    if (job.isComplete()) {
                        if (job.tracked) {
                            data(level).markComplete(job.startChunk.pack());
                        }
                        job.releaseTickets(level);
                        jobIterator.remove();
                    }
                }

                if (jobs.isEmpty()) {
                    dimensionIterator.remove();
                }
            }
            if (!placedAnyPiece) {
                logBudgetUse(budgetNanos, System.nanoTime() - start, placedPieces);
                return;
            }
        }

        logBudgetUse(budgetNanos, System.nanoTime() - start, placedPieces);
    }

    private static void recordServerTickTime() {
        if (serverTickStartNanos == 0L) {
            return;
        }

        long elapsed = Math.max(0L, System.nanoTime() - serverTickStartNanos);
        if (tickTimeCount < TICK_TIMES.length) {
            tickTimeCount++;
        } else {
            tickTimeTotal -= TICK_TIMES[tickTimeIndex];
        }

        TICK_TIMES[tickTimeIndex] = elapsed;
        tickTimeTotal += elapsed;
        tickTimeIndex = (tickTimeIndex + 1) % TICK_TIMES.length;
    }

    private static long dynamicBudgetNanos() {
        long averageTickNanos = averageTickNanos();
        long remaining = Math.max(0L, TARGET_TICK_NANOS - averageTickNanos);
        long budget = remaining / 2L;
        return Math.clamp(budget, MIN_BUDGET_NANOS, MAX_BUDGET_NANOS);
    }

    private static long averageTickNanos() {
        if (tickTimeCount == 0) {
            return 0L;
        }

        return tickTimeTotal / tickTimeCount;
    }

    private static void logBudgetUse(long budgetNanos, long usedNanos, int placedPieces) {
        if (!ProceduralDungeon.LOGGER.isDebugEnabled()) {
            return;
        }

        Status status = globalStatus();
        ProceduralDungeon.LOGGER.debug(
                "Staged dungeon generation tick: avg MSPT {}, budget {} ms, used {} ms, placed {} piece(s), jobs {}, pending pieces {}.",
                "%.2f".formatted(averageTickNanos() / 1_000_000.0),
                "%.2f".formatted(budgetNanos / 1_000_000.0),
                "%.2f".formatted(usedNanos / 1_000_000.0),
                placedPieces,
                status.jobs(),
                status.pendingPieces()
        );
    }

    private static Status globalStatus() {
        int jobCount = 0;
        int pendingPieces = 0;
        for (Map<Long, Job> jobs : JOBS.values()) {
            jobCount += jobs.size();
            for (Job job : jobs.values()) {
                pendingPieces += job.pending.size();
            }
        }

        return new Status(jobCount, pendingPieces);
    }

    private static StagedDungeonSaveData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StagedDungeonSaveData.TYPE);
    }

    private static final class Job {
        private final ChunkPos startChunk;
        private final ArrayDeque<StagedDungeonPieceSpec> pending;
        private final LiquidSettings liquidSettings;
        private final boolean tracked;
        private final java.util.Set<Long> ticketedChunks = new java.util.HashSet<>();
        private final RandomSource placementRandom = RandomSource.create(0L);

        private Job(
                ChunkPos startChunk,
                List<StagedDungeonPieceSpec> pieces,
                LiquidSettings liquidSettings,
                boolean tracked
        ) {
            this.startChunk = startChunk;
            this.pending = new ArrayDeque<>(pieces);
            this.liquidSettings = liquidSettings;
            this.tracked = tracked;
        }

        private boolean isComplete() {
            return pending.isEmpty();
        }

        private boolean placeNextReadyPiece(ServerLevel level) {
            StagedDungeonPieceSpec piece = pending.peekFirst();
            if (piece == null) {
                return false;
            }

            ensureChunkTickets(level, piece.boundingBox());
            if (!chunksLoaded(level, piece.boundingBox())) {
                return false;
            }

            pending.removeFirst();
            placePiece(level, level.structureManager(), level.getChunkSource().getGenerator(), piece, liquidSettings, placementRandom);
            return true;
        }

        private void ensureChunkTickets(ServerLevel level, BoundingBox box) {
            int minChunkX = SectionPos.blockToSectionCoord(box.minX());
            int maxChunkX = SectionPos.blockToSectionCoord(box.maxX());
            int minChunkZ = SectionPos.blockToSectionCoord(box.minZ());
            int maxChunkZ = SectionPos.blockToSectionCoord(box.maxZ());
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long packed = ChunkPos.pack(chunkX, chunkZ);
                    if (ticketedChunks.add(packed)) {
                        level.getChunkSource().addTicketWithRadius(TicketType.FORCED, new ChunkPos(chunkX, chunkZ), 1);
                    }
                }
            }
        }

        private boolean chunksLoaded(ServerLevel level, BoundingBox box) {
            int minChunkX = SectionPos.blockToSectionCoord(box.minX());
            int maxChunkX = SectionPos.blockToSectionCoord(box.maxX());
            int minChunkZ = SectionPos.blockToSectionCoord(box.minZ());
            int maxChunkZ = SectionPos.blockToSectionCoord(box.maxZ());
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                        return false;
                    }
                }
            }

            return true;
        }

        private void releaseTickets(ServerLevel level) {
            for (long packed : ticketedChunks) {
                ChunkPos chunk = ChunkPos.unpack(packed);
                level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, chunk, 1);
            }
            ticketedChunks.clear();
        }

    }

    public static int placeSynchronously(ServerLevel level, List<StagedDungeonPieceSpec> pieces, LiquidSettings liquidSettings) {
        int placed = 0;
        RandomSource random = RandomSource.create(0L);
        for (StagedDungeonPieceSpec piece : pieces) {
            placePiece(level, level.structureManager(), level.getChunkSource().getGenerator(), piece, liquidSettings, random);
            placed++;
        }

        return placed;
    }

    private static void placePiece(
            WorldGenLevel world,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            StagedDungeonPieceSpec piece,
            LiquidSettings liquidSettings,
            RandomSource random
    ) {
        long start = System.nanoTime();
        random.setSeed(world.getSeed() ^ piece.position().asLong());
        piece.element().place(
                world.getLevel().getStructureManager(),
                world,
                structureManager,
                chunkGenerator,
                piece.position(),
                BlockPos.ZERO,
                piece.rotation(),
                piece.boundingBox(),
                random,
                liquidSettings,
                false
        );
        long elapsed = System.nanoTime() - start;
        if (elapsed > MAX_BUDGET_NANOS) {
            ProceduralDungeon.LOGGER.debug(
                    "Staged dungeon piece at {} exceeded max dynamic budget: {} ms",
                    piece.position().toShortString(),
                    "%.2f".formatted(elapsed / 1_000_000.0)
            );
        }
    }

    public record Status(int jobs, int pendingPieces) {}
}
