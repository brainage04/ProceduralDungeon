package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record StagedDungeonLayout(
        ChunkPos startChunk,
        BlockPos locator,
        BoundingBox boundingBox,
        List<StagedDungeonPieceSpec> pieces,
        DungeonLockPlan lockPlan
) {
    public StagedDungeonLayout(ChunkPos startChunk, BlockPos locator, BoundingBox boundingBox, List<StagedDungeonPieceSpec> pieces) {
        this(startChunk, locator, boundingBox, pieces, DungeonLockPlan.EMPTY);
    }

    /**
     * The same layout moved by the given offset, as the public API builds a plan compiled at a canonical origin. The
     * start chunk follows the locator.
     */
    public StagedDungeonLayout moved(int dx, int dy, int dz) {
        BlockPos movedLocator = locator.offset(dx, dy, dz);
        return new StagedDungeonLayout(
                ChunkPos.containing(movedLocator),
                movedLocator,
                boundingBox.moved(dx, dy, dz),
                pieces.stream().map(piece -> piece.moved(dx, dy, dz)).toList(),
                lockPlan.moved(dx, dy, dz)
        );
    }
}
