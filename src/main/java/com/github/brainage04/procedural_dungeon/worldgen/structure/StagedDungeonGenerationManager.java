package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
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
    private static final long DEFAULT_BUDGET_NANOS = 2_000_000L;
    private static final Map<ResourceKey<net.minecraft.world.level.Level>, Map<Long, Job>> JOBS = new HashMap<>();

    private StagedDungeonGenerationManager() {}

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> runTick(level, DEFAULT_BUDGET_NANOS));
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

    private static void runTick(ServerLevel level, long budgetNanos) {
        Map<Long, Job> jobs = JOBS.get(level.dimension());
        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        long deadline = System.nanoTime() + budgetNanos;
        var iterator = jobs.entrySet().iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next().getValue();
            job.placeNextTickPiece(level);
            if (job.isComplete()) {
                if (job.tracked) {
                    data(level).markComplete(job.startChunk.pack());
                }
                job.releaseTickets(level);
                iterator.remove();
            }

            if (System.nanoTime() >= deadline) {
                return;
            }
        }
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

        private void placeNextTickPiece(ServerLevel level) {
            StagedDungeonPieceSpec piece = pending.peekFirst();
            if (piece == null) {
                return;
            }

            ensureChunkTickets(level, piece.boundingBox());
            if (!chunksLoaded(level, piece.boundingBox())) {
                return;
            }

            pending.removeFirst();
            place(level, level.structureManager(), level.getChunkSource().getGenerator(), piece, liquidSettings);
        }

        private void ensureChunkTickets(ServerLevel level, BoundingBox box) {
            for (ChunkPos chunk : box.intersectingChunks().toList()) {
                if (ticketedChunks.add(chunk.pack())) {
                    level.getChunkSource().addTicketWithRadius(TicketType.FORCED, chunk, 1);
                }
            }
        }

        private boolean chunksLoaded(ServerLevel level, BoundingBox box) {
            for (ChunkPos chunk : box.intersectingChunks().toList()) {
                if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) {
                    return false;
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

        private void place(
                WorldGenLevel world,
                StructureManager structureManager,
                ChunkGenerator chunkGenerator,
                StagedDungeonPieceSpec piece,
                LiquidSettings liquidSettings
        ) {
            long start = System.nanoTime();
            piece.element().place(
                    world.getLevel().getStructureManager(),
                    world,
                    structureManager,
                    chunkGenerator,
                    piece.position(),
                    BlockPos.ZERO,
                    piece.rotation(),
                    piece.boundingBox(),
                    RandomSource.create(world.getSeed() ^ piece.position().asLong()),
                    liquidSettings,
                    false
            );
            long elapsed = System.nanoTime() - start;
            if (elapsed > DEFAULT_BUDGET_NANOS) {
                ProceduralDungeon.LOGGER.debug(
                        "Staged dungeon piece at {} exceeded per-tick budget: {} ms",
                        piece.position().toShortString(),
                        "%.2f".formatted(elapsed / 1_000_000.0)
                );
            }
        }
    }

    public record Status(int jobs, int pendingPieces) {}
}
