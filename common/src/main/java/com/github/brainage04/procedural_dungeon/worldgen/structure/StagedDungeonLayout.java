package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A compiled dungeon. {@code parents} holds, for each piece, the index of the piece it was attached to, or -1 for the
 * root; it is empty for layouts built without the compiler.
 */
public record StagedDungeonLayout(
        ChunkPos startChunk,
        BlockPos locator,
        BoundingBox boundingBox,
        List<StagedDungeonPieceSpec> pieces,
        DungeonLockPlan lockPlan,
        List<Integer> parents
) {
    public StagedDungeonLayout(ChunkPos startChunk, BlockPos locator, BoundingBox boundingBox, List<StagedDungeonPieceSpec> pieces) {
        this(startChunk, locator, boundingBox, pieces, DungeonLockPlan.EMPTY, List.of());
    }
}
