package com.github.brainage04.procedural_dungeon.worldgen.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * The staged job and piece a placement belongs to. Entities the placement spawns carry {@link
 * #JOB_TAG}, {@link #PIECE_TAG} and {@link #MARKER_TAG} tags, and a spawn whose tags already exist
 * inside the dungeon is skipped, so a resumed job never adds a second guardian or frame.
 *
 * @param search where to look for an existing tagged entity: the dungeon's bounds (guardians can
 *     wander)
 */
public record StagedPlacementContext(UUID job, int piece, AABB search) {
    public static final String JOB_TAG = "procedural_dungeon.job=";
    public static final String PIECE_TAG = "procedural_dungeon.piece=";
    public static final String MARKER_TAG = "procedural_dungeon.marker=";

    private static final ThreadLocal<StagedPlacementContext> CURRENT = new ThreadLocal<>();

    static @Nullable StagedPlacementContext current() {
        return CURRENT.get();
    }

    static void set(@Nullable StagedPlacementContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    /**
     * Whether an entity spawned for data marker {@code marker} of this placement already exists.
     */
    boolean spawned(ServerLevel level, int marker) {
        Set<String> wanted = Set.of(JOB_TAG + job, PIECE_TAG + piece, MARKER_TAG + marker);
        return !level.getEntities(
                        (Entity) null, search, entity -> entity.entityTags().containsAll(wanted))
                .isEmpty();
    }

    void tag(Entity entity, int marker) {
        entity.addTag(JOB_TAG + job);
        entity.addTag(PIECE_TAG + piece);
        entity.addTag(MARKER_TAG + marker);
    }
}
