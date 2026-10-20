package com.github.brainage04.procedural_dungeon.api;

/**
 * How {@link ProceduralDungeonApi#build} places a plan.
 *
 * @param callerDrivesBudget Procedural Dungeon's own tick never places this dungeon; the caller
 *     calls {@link DungeonHandle#advance} with its own budget instead
 * @param guardianHealthScale multiplies the maximum health of the dungeon's boss and miniboss
 *     guardians when they spawn; 1 keeps the tier's health
 * @param persistent the build survives a server restart and resumes; false suits worlds that are
 *     thrown away, whose dungeons are forgotten on restart
 */
public record BuildOptions(
        boolean callerDrivesBudget, double guardianHealthScale, boolean persistent) {
    /** Procedural Dungeon's budget, tier health, survives restarts: what natural dungeons get. */
    public static final BuildOptions DEFAULT = new BuildOptions(false, 1.0, true);

    public BuildOptions {
        if (!(guardianHealthScale > 0.0) || Double.isInfinite(guardianHealthScale)) {
            throw new IllegalArgumentException(
                    "guardianHealthScale must be positive and finite: " + guardianHealthScale);
        }
    }
}
