package com.github.brainage04.procedural_dungeon.worldgen.structure;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record StagedDungeonLayout(
        ChunkPos startChunk,
        BlockPos locator,
        BoundingBox boundingBox,
        List<StagedDungeonPieceSpec> pieces
) {}
