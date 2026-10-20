package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consistency check of staged jobs after a crash: every (piece, chunk) part before the cursor has
 * its marker, no PD-spawned entity exists twice, and every planned lock matches a closed, locked
 * door. Loads chunks synchronously, so it is a development and soak-test tool.
 */
public final class StagedDungeonVerifier {
    private StagedDungeonVerifier() {}

    public static List<Report> verifyAll(ServerLevel level) {
        StagedDungeonSaveData data = StagedDungeonGenerationManager.existingData(level);
        if (data == null) {
            return List.of();
        }
        List<Report> reports = new ArrayList<>();
        for (StagedDungeonJob job : List.copyOf(data.jobList())) {
            reports.add(verify(level, job));
        }
        return reports;
    }

    static Report verify(ServerLevel level, StagedDungeonJob job) {
        int placedPieces = job.state() == JobState.COMPLETE ? job.pieces.size() : job.cursor();
        int checkedParts = 0;
        int missingMarkers = 0;
        for (int index = 0; index < placedPieces; index++) {
            BoundingBox box = job.pieces.get(index).boundingBox();
            for (int chunkX = SectionPos.blockToSectionCoord(box.minX());
                    chunkX <= SectionPos.blockToSectionCoord(box.maxX());
                    chunkX++) {
                for (int chunkZ = SectionPos.blockToSectionCoord(box.minZ());
                        chunkZ <= SectionPos.blockToSectionCoord(box.maxZ());
                        chunkZ++) {
                    LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                    checkedParts++;
                    if (!DungeonChunkLedger.isPlaced(chunk, job.id, index)) {
                        missingMarkers++;
                    }
                }
            }
        }

        String jobTag = StagedPlacementContext.JOB_TAG + job.id;
        Map<String, Integer> spawns = new HashMap<>();
        for (Entity entity :
                level.getEntities(
                        (Entity) null,
                        AABB.of(job.bounds),
                        candidate -> candidate.entityTags().contains(jobTag))) {
            String key =
                    entity.entityTags().stream()
                            .filter(
                                    tag ->
                                            tag.startsWith(StagedPlacementContext.PIECE_TAG)
                                                    || tag.startsWith(
                                                            StagedPlacementContext.MARKER_TAG))
                            .sorted()
                            .toList()
                            .toString();
            spawns.merge(key, 1, Integer::sum);
        }
        int duplicateEntities = spawns.values().stream().mapToInt(count -> count - 1).sum();

        int lockMismatches = 0;
        for (DungeonLockPlan.Door door : job.lockPlan.doors()) {
            if (door.lock().isEmpty()
                    || !placedBefore(job, BlockPos.of(door.pos()), placedPieces)) {
                continue;
            }
            BlockPos pos = BlockPos.of(door.pos());
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock)
                    || state.getValue(DoorBlock.OPEN)
                    || !DungeonLockManager.isDoorLocked(level, pos)) {
                lockMismatches++;
            }
        }

        return new Report(
                job.id,
                job.state(),
                placedPieces,
                checkedParts,
                missingMarkers,
                job.skippedParts(),
                spawns.size(),
                duplicateEntities,
                lockMismatches);
    }

    private static boolean placedBefore(StagedDungeonJob job, BlockPos pos, int placedPieces) {
        for (int index = 0; index < placedPieces; index++) {
            if (job.pieces.get(index).boundingBox().isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param missingMarkers parts before the cursor without a marker (players' chunks the job
     *     skipped count here too)
     * @param spawnedEntities distinct (piece, marker) entities found
     */
    public record Report(
            UUID job,
            JobState state,
            int placedPieces,
            int checkedParts,
            int missingMarkers,
            int skippedParts,
            int spawnedEntities,
            int duplicateEntities,
            int lockMismatches) {
        public boolean ok() {
            return missingMarkers <= skippedParts && duplicateEntities == 0 && lockMismatches == 0;
        }

        @Override
        public String toString() {
            return "%s %s %s: %d pieces, %d parts, %d missing markers (%d skipped), %d entities, %d duplicates, %d lock mismatches"
                    .formatted(
                            ok() ? "OK" : "FAIL",
                            job,
                            state,
                            placedPieces,
                            checkedParts,
                            missingMarkers,
                            skippedParts,
                            spawnedEntities,
                            duplicateEntities,
                            lockMismatches);
        }
    }
}
