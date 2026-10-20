package com.github.brainage04.procedural_dungeon.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;
import java.util.UUID;

/**
 * One built dungeon. A handle is a view: every call reads the build's current state, so a handle
 * kept across ticks stays accurate. Use {@link ProceduralDungeonApi#handle} to find a persistent
 * build again after a restart. Server thread only.
 */
public interface DungeonHandle {
    UUID id();

    ServerLevel level();

    /** World coordinates of every block the dungeon occupies. */
    BoundingBox bounds();

    /**
     * Where a player can stand in the start room (or at the bottom of the entrance shaft): the
     * lowest floor block in the start piece's middle column with two blocks of air above it, once
     * the build is complete. Before that, the middle of the start piece.
     */
    BlockPos startSpawn();

    /** Lower half of the boss room's door, which the boss key unlocks. */
    Optional<BlockPos> bossDoor();

    int placedPieces();

    int totalPieces();

    DungeonState state();

    /** The piece and cause when {@link #state()} is {@link DungeonState#FAILED}. */
    Optional<String> failure();

    /** Whether {@link #state()} is {@link DungeonState#COMPLETE}. */
    default boolean complete() {
        return state() == DungeonState.COMPLETE;
    }

    /**
     * Places pieces until {@code budgetNanos} is spent or no piece is ready (its chunks still
     * load). Only for builds with {@link BuildOptions#callerDrivesBudget()}; others are advanced by
     * Procedural Dungeon's own tick and return 0 here.
     *
     * @return nanoseconds used
     */
    long advance(long budgetNanos);

    /**
     * Stops placing and releases this build's chunk tickets. Placed blocks, locks and guardians
     * stay; use {@link ProceduralDungeonApi#forget} to drop the records too.
     */
    void cancel();
}
