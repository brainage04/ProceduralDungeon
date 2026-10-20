package com.github.brainage04.procedural_dungeon.api.impl;

import com.github.brainage04.procedural_dungeon.api.DungeonPlan;
import com.github.brainage04.procedural_dungeon.api.DungeonRequest;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A layout compiled at the canonical origin {@link DungeonPlanner#CANONICAL_ORIGIN}; building it
 * moves it to the build origin.
 */
public record CompiledPlan(DungeonRequest request, StagedDungeonLayout layout)
        implements DungeonPlan {
    @Override
    public int totalPieces() {
        return layout.pieces().size();
    }

    @Override
    public BoundingBox bounds() {
        return layout.boundingBox();
    }
}
