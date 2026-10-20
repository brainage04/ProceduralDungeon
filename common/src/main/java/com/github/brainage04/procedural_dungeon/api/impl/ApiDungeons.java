package com.github.brainage04.procedural_dungeon.api.impl;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.api.BuildOptions;
import com.github.brainage04.procedural_dungeon.api.DungeonHandle;
import com.github.brainage04.procedural_dungeon.api.DungeonListener;
import com.github.brainage04.procedural_dungeon.api.DungeonPlan;
import com.github.brainage04.procedural_dungeon.api.DungeonState;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobOptions;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobView;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.StagedJobListener;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Builds plans as retained jobs of {@link StagedDungeonGenerationManager} and turns that manager's
 * job events, and the lock and guardian hooks in {@link DungeonEvents}, into {@link
 * DungeonListener} calls for API builds.
 */
public final class ApiDungeons {
    private static final List<DungeonListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final String TAG_PREFIX = ProceduralDungeon.MOD_ID + ".";

    private ApiDungeons() {}

    public static void initialize() {
        StagedDungeonGenerationManager.setListener(new JobEvents());
    }

    public static void listen(DungeonListener listener) {
        LISTENERS.add(Objects.requireNonNull(listener));
    }

    public static DungeonHandle build(
            ServerLevel level, DungeonPlan plan, BlockPos origin, BuildOptions options) {
        requireServerThread(level);
        if (!(plan instanceof CompiledPlan compiled)) {
            throw new IllegalArgumentException(
                    "Only ProceduralDungeonApi.plan makes plans: " + plan);
        }
        BlockPos offset = origin.subtract(DungeonPlanner.CANONICAL_ORIGIN);
        StagedDungeonLayout layout =
                compiled.layout().moved(offset.getX(), offset.getY(), offset.getZ());
        BoundingBox bounds = layout.boundingBox();
        if (bounds.minY() < level.getMinY() || bounds.maxY() > level.getMaxY()) {
            throw new IllegalArgumentException(
                    "A dungeon built at %s spans y %d to %d, outside %s (y %d to %d)"
                            .formatted(
                                    origin.toShortString(),
                                    bounds.minY(),
                                    bounds.maxY(),
                                    level.dimension().identifier(),
                                    level.getMinY(),
                                    level.getMaxY()));
        }
        return enqueue(
                level,
                layout,
                new JobOptions(
                        options.callerDrivesBudget(),
                        options.persistent(),
                        compiled.request().seed(),
                        options.guardianHealthScale()));
    }

    public static Optional<DungeonHandle> handle(ServerLevel level, UUID id) {
        requireServerThread(level);
        return StagedDungeonGenerationManager.job(level, id)
                .filter(JobView::retained)
                .map(view -> new ApiDungeonHandle(level, id));
    }

    /**
     * Drops every job, placement marker, lock record and Procedural Dungeon entity inside {@code
     * box}. Blocks stay. Entities are only found in loaded chunks.
     */
    public static void forget(ServerLevel level, BoundingBox box) {
        requireServerThread(level);
        StagedDungeonGenerationManager.forget(level, box);
        DungeonLockManager.forget(level, box);
        for (Entity entity :
                level.getEntities(
                        (Entity) null,
                        AABB.of(box),
                        entity ->
                                entity.entityTags().stream()
                                        .anyMatch(tag -> tag.startsWith(TAG_PREFIX)))) {
            entity.discard();
        }
    }

    public static DungeonHandle reset(DungeonHandle handle) {
        ServerLevel level = handle.level();
        requireServerThread(level);
        JobView view =
                StagedDungeonGenerationManager.job(level, handle.id())
                        .filter(JobView::retained)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Dungeon " + handle.id() + " was forgotten"));
        StagedDungeonLayout layout =
                StagedDungeonGenerationManager.layout(level, handle.id()).orElseThrow();
        if (view.state() == StagedDungeonGenerationManager.JobState.RUNNING) {
            StagedDungeonGenerationManager.cancel(level, handle.id());
        }
        forget(level, view.bounds());
        return enqueue(level, layout, view.options());
    }

    static DungeonState state(StagedDungeonGenerationManager.JobState state) {
        return switch (state) {
            case RUNNING -> DungeonState.RUNNING;
            case FAILED -> DungeonState.FAILED;
            case CANCELLED -> DungeonState.CANCELLED;
            case COMPLETE -> DungeonState.COMPLETE;
        };
    }

    static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Procedural Dungeon builds run on the server thread");
        }
    }

    /** The API build whose bounds hold {@code pos}, for the lock and guardian events. */
    static Optional<DungeonHandle> at(ServerLevel level, BlockPos pos) {
        for (JobView view : StagedDungeonGenerationManager.jobs(level)) {
            if (view.retained() && view.bounds().isInside(pos)) {
                return Optional.of(new ApiDungeonHandle(level, view.id()));
            }
        }
        return Optional.empty();
    }

    static Optional<JobOptions> options(DungeonHandle handle) {
        return StagedDungeonGenerationManager.job(handle.level(), handle.id())
                .map(JobView::options);
    }

    static void dispatch(Consumer<DungeonListener> event) {
        for (DungeonListener listener : LISTENERS) {
            try {
                event.accept(listener);
            } catch (RuntimeException exception) {
                ProceduralDungeon.LOGGER.error("A dungeon listener failed", exception);
            }
        }
    }

    private static DungeonHandle enqueue(
            ServerLevel level, StagedDungeonLayout layout, JobOptions options) {
        StagedDungeonGenerationManager.EnqueueResult result =
                StagedDungeonGenerationManager.enqueue(
                        level, layout, LiquidSettings.IGNORE_WATERLOGGING, options);
        if (!result.accepted()) {
            throw new IllegalStateException("The dungeon was not scheduled: " + result.rejection());
        }
        return new ApiDungeonHandle(level, result.id());
    }

    private static final class JobEvents implements StagedJobListener {
        @Override
        public void progressed(ServerLevel level, UUID id, int placed, int total) {
            retained(level, id)
                    .ifPresent(
                            handle ->
                                    dispatch(
                                            listener ->
                                                    listener.progressed(handle, placed, total)));
        }

        @Override
        public void completed(ServerLevel level, UUID id) {
            retained(level, id)
                    .ifPresent(handle -> dispatch(listener -> listener.completed(handle)));
        }

        @Override
        public void failed(ServerLevel level, UUID id, @Nullable String reason) {
            String cause = reason == null ? "unknown" : reason;
            retained(level, id)
                    .ifPresent(handle -> dispatch(listener -> listener.failed(handle, cause)));
        }

        @Override
        public void cancelled(ServerLevel level, UUID id) {
            retained(level, id)
                    .ifPresent(handle -> dispatch(listener -> listener.cancelled(handle)));
        }

        private static Optional<DungeonHandle> retained(ServerLevel level, UUID id) {
            if (LISTENERS.isEmpty()) {
                return Optional.empty();
            }
            return StagedDungeonGenerationManager.job(level, id)
                    .filter(JobView::retained)
                    .map(view -> new ApiDungeonHandle(level, id));
        }
    }
}
