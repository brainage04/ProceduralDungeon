package com.github.brainage04.procedural_dungeon.api;

/** Where a build stands. Only {@link #RUNNING} changes again. */
public enum DungeonState {
    /** Pieces are still being placed. */
    RUNNING,
    /** A piece could not be placed; {@link DungeonHandle#failure()} says which and why. */
    FAILED,
    /** {@link DungeonHandle#cancel()} stopped it; the pieces placed so far stay. */
    CANCELLED,
    /** Every piece is placed and locked. */
    COMPLETE
}
