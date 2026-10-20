package com.github.brainage04.procedural_dungeon.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import org.jspecify.annotations.Nullable;

/**
 * Events of dungeons built through {@link ProceduralDungeonApi#build}. Natural and {@code
 * /generatedungeon} dungeons raise none. Every method is a no-op by default and runs on the server
 * thread right after the change it reports.
 */
public interface DungeonListener {
    /** A piece was placed. */
    default void progressed(DungeonHandle dungeon, int placedPieces, int totalPieces) {}

    /** Every piece is placed. */
    default void completed(DungeonHandle dungeon) {}

    /** A piece could not be placed and the build stopped; {@code reason} names it. */
    default void failed(DungeonHandle dungeon, String reason) {}

    /** {@link DungeonHandle#cancel()} stopped the build. */
    default void cancelled(DungeonHandle dungeon) {}

    /**
     * A boss ({@code boss}) or miniboss guardian spawned, after {@link
     * BuildOptions#guardianHealthScale()}.
     */
    default void guardianSpawned(DungeonHandle dungeon, Mob guardian, boolean boss) {}

    /** A guardian died; {@code killer} is the player credited with the kill, if any. */
    default void guardianKilled(
            DungeonHandle dungeon, Mob guardian, @Nullable ServerPlayer killer, boolean boss) {}

    /** {@code player} unlocked the door or chest at {@code pos} with a {@code key} key. */
    default void unlocked(DungeonHandle dungeon, ServerPlayer player, String key, BlockPos pos) {}

    /** A puzzle room's chest at {@code chest} was opened after its puzzle was solved. */
    default void puzzleSolved(DungeonHandle dungeon, BlockPos chest) {}

    /** The {@code key} key was destroyed (lava, the void, despawning). */
    default void keyDestroyed(DungeonHandle dungeon, String key) {}
}
