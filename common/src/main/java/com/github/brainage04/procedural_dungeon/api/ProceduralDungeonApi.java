package com.github.brainage04.procedural_dungeon.api;

import com.github.brainage04.procedural_dungeon.api.impl.ApiDungeons;
import com.github.brainage04.procedural_dungeon.api.impl.DungeonPlanner;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.rule.DungeonSwitches;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * The entry point for other mods: plan a dungeon, build it, follow and control the build, and keep
 * Procedural Dungeon's world-wide behaviour out of places that should not have it. Everything is
 * server-side and runs on the server thread unless a method says otherwise.
 *
 * <p>Compatible changes keep {@link #VERSION}; a change that breaks callers raises it.
 */
public final class ProceduralDungeonApi {
    /** The API version this jar implements. */
    public static final int VERSION = 1;

    /** The tiers {@link DungeonRequest#tier()} accepts, inclusive. */
    public static final int MIN_TIER = 1;

    public static final int MAX_TIER = 5;

    /** The jigsaw depths {@link DungeonRequest#depth()} accepts, inclusive. */
    public static final int MIN_DEPTH = 1;

    public static final int MAX_DEPTH = 20;

    private static final List<String> THEMES =
            Arrays.stream(DungeonTheme.values()).map(DungeonTheme::getSerializedName).toList();

    private ProceduralDungeonApi() {}

    /**
     * Every theme name {@link #plan} accepts in {@link DungeonRequest#theme()}, such as {@code
     * "cobblestone"}, in a stable order. Each theme belongs to the Overworld, the Nether or the End
     * and is styled for it, but any theme can be built in any level.
     */
    public static List<String> themes() {
        return THEMES;
    }

    /**
     * Compiles {@code request} off the server thread. The future completes on the server thread
     * with the plan, or empty when no layout fits the request's constraints (try another seed). The
     * plan depends only on the request, not on any world or position.
     *
     * @throws IllegalArgumentException for an unknown theme or an out-of-range tier or depth
     */
    public static CompletableFuture<Optional<DungeonPlan>> plan(
            MinecraftServer server, DungeonRequest request) {
        return DungeonPlanner.plan(server, request);
    }

    /**
     * Starts placing {@code plan} in {@code level} with its start at {@code origin}: the start
     * room's entrance socket, or with {@link DungeonRequest#surfaceEntrance()} the foot of the
     * entrance shaft, which belongs at the surface. The build has its own id, chunk tickets and
     * lock records. It is placed over several ticks; a build that waits for chunks to load holds
     * them with its own tickets.
     *
     * @throws IllegalArgumentException when the moved plan does not fit the level's height
     */
    public static DungeonHandle build(
            ServerLevel level, DungeonPlan plan, BlockPos origin, BuildOptions options) {
        return ApiDungeons.build(level, plan, origin, options);
    }

    /**
     * The build with this id, also after a restart for {@link BuildOptions#persistent()} builds,
     * until {@link #forget} drops it.
     */
    public static Optional<DungeonHandle> handle(ServerLevel level, UUID id) {
        return ApiDungeons.handle(level, id);
    }

    /**
     * Drops everything Procedural Dungeon keeps inside {@code box}: builds (running ones stop and
     * release their tickets), placement markers, lock, puzzle and key records, and its entities
     * (guardians, puzzle frames) in loaded chunks. Placed blocks stay, so call this before clearing
     * or reusing the area.
     */
    public static void forget(ServerLevel level, BoundingBox box) {
        ApiDungeons.forget(level, box);
    }

    /**
     * Builds the same plan in the same place again: forgets the old build's bounds, then places
     * every piece afresh under a new id with the same options. Costs a full placement, not a
     * compile. The old handle is forgotten.
     */
    public static DungeonHandle reset(DungeonHandle handle) {
        return ApiDungeons.reset(handle);
    }

    /** Adds a listener for the events of every API build. Listeners cannot be removed. */
    public static void listen(DungeonListener listener) {
        ApiDungeons.listen(listener);
    }

    /**
     * Limits natural dungeon generation to the dimensions {@code enabled} accepts, on top of the
     * {@code procedural_dungeon:natural_generation} game rule. The default accepts every dimension;
     * the last call wins, and it may come before or after the server starts. The predicate runs on
     * worldgen threads, so it must be thread-safe. Chunks that already hold a dungeon start keep
     * it.
     */
    public static void naturalGeneration(Predicate<ResourceKey<Level>> enabled) {
        DungeonSwitches.setNaturalGeneration(enabled);
    }

    /**
     * Limits the mechanics that act outside dungeons (grindstone salvage, anvil over-max levels and
     * relic essence, Soulbound) to the levels {@code enabled} accepts. Grindstone salvage also
     * needs the {@code procedural_dungeon:grindstone_salvage} game rule. The default accepts every
     * level; the last call wins.
     */
    public static void globalMechanics(Predicate<ServerLevel> enabled) {
        DungeonSwitches.setGlobalMechanics(enabled);
    }
}
