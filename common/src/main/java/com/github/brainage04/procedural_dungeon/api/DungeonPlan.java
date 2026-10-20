package com.github.brainage04.procedural_dungeon.api;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A compiled dungeon layout, not yet placed anywhere. Immutable and position-independent: one plan
 * can be built any number of times at different origins, and each build is the same dungeon moved.
 * Only {@link ProceduralDungeonApi#plan} makes plans.
 */
public interface DungeonPlan {
    DungeonRequest request();

    int totalPieces();

    /**
     * The blocks the dungeon occupies relative to its origin: {@code bounds().moved(origin)} is the
     * built dungeon's bounds.
     */
    BoundingBox bounds();
}
