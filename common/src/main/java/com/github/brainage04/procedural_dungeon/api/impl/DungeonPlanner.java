package com.github.brainage04.procedural_dungeon.api.impl;

import com.github.brainage04.procedural_dungeon.api.DungeonPlan;
import com.github.brainage04.procedural_dungeon.api.DungeonRequest;
import com.github.brainage04.procedural_dungeon.api.ProceduralDungeonApi;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayoutCompiler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Compiles {@link DungeonRequest}s off the server thread. A plan is compiled at {@link
 * #CANONICAL_ORIGIN} in a height range no world has, so its layout depends only on the request;
 * {@code build} moves it and checks that it fits the target level.
 */
public final class DungeonPlanner {
    public static final BlockPos CANONICAL_ORIGIN = BlockPos.ZERO;
    private static final LevelHeightAccessor CANONICAL_HEIGHT =
            LevelHeightAccessor.create(-2048, 4096);
    private static final Identifier START_JIGSAW = Identifier.withDefaultNamespace("start");

    private DungeonPlanner() {}

    /**
     * Resolves the request's pool on the server thread, compiles it on a worldgen worker as natural
     * generation does, and completes the future on the server thread.
     *
     * @throws IllegalArgumentException for an unknown theme or an out-of-range tier or depth
     */
    public static CompletableFuture<Optional<DungeonPlan>> plan(
            MinecraftServer server, DungeonRequest request) {
        return onServer(
                server,
                CompletableFuture.supplyAsync(prepare(server, request), Util.backgroundExecutor()));
    }

    /**
     * The plan {@link #plan} would complete with, compiled on the calling server thread. For
     * GameTests and benchmarks that build a plan and must not race a worker against a tick
     * deadline; it stalls the tick for the whole compile.
     */
    public static Optional<DungeonPlan> planNow(MinecraftServer server, DungeonRequest request) {
        return prepare(server, request).get();
    }

    /**
     * Checks the request and resolves its pool on the server thread; the result compiles anywhere.
     */
    private static Supplier<Optional<DungeonPlan>> prepare(
            MinecraftServer server, DungeonRequest request) {
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "ProceduralDungeonApi.plan must run on the server thread");
        }
        DungeonTheme theme =
                DungeonTheme.find(request.theme())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Unknown dungeon theme: " + request.theme()));
        DungeonTier tier = tier(request.tier());
        if (request.depth() < ProceduralDungeonApi.MIN_DEPTH
                || request.depth() > ProceduralDungeonApi.MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "Dungeon depth must be %d to %d: %d"
                            .formatted(
                                    ProceduralDungeonApi.MIN_DEPTH,
                                    ProceduralDungeonApi.MAX_DEPTH,
                                    request.depth()));
        }
        String poolName =
                RegistryKeyUtils.getKeyString(theme, tier)
                        + (request.surfaceEntrance() ? "/entrance" : "/start");
        Holder<StructureTemplatePool> pool =
                server.registryAccess()
                        .lookupOrThrow(Registries.TEMPLATE_POOL)
                        .get(RegistryKeyUtils.create(Registries.TEMPLATE_POOL, poolName))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "No template pool procedural_dungeon:" + poolName));

        // The generator, biome source and noise only serve heightmap projection, which plans never
        // use; the random comes from the request's seed alone.
        ServerLevel overworld = server.overworld();
        ChunkGenerator generator = overworld.getChunkSource().getGenerator();
        Structure.GenerationContext context =
                new Structure.GenerationContext(
                        server.registryAccess(),
                        generator,
                        generator.getBiomeSource(),
                        overworld.getChunkSource().randomState(),
                        server.getStructureManager(),
                        request.seed(),
                        ChunkPos.containing(CANONICAL_ORIGIN),
                        CANONICAL_HEIGHT,
                        biome -> true);
        JigsawStructure.MaxDistance maxDistance =
                new JigsawStructure.MaxDistance(tier.maxDistanceFromCenter);
        return () ->
                compile(context, pool, request.depth(), CANONICAL_ORIGIN, maxDistance)
                        .map(compiled -> new CompiledPlan(request, compiled));
    }

    /**
     * Compiles a layout from {@code start} on a worldgen worker, as natural generation does, and
     * completes on the server thread. {@code context} must be built on the server thread and not
     * used again: its random belongs to this compile. {@code /generatedungeon} and {@link #plan}
     * both compile here.
     */
    public static CompletableFuture<Optional<StagedDungeonLayout>> compileOffThread(
            MinecraftServer server,
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> pool,
            int depth,
            BlockPos start,
            JigsawStructure.MaxDistance maxDistance) {
        return onServer(
                server,
                CompletableFuture.supplyAsync(
                        () -> compile(context, pool, depth, start, maxDistance),
                        Util.backgroundExecutor()));
    }

    private static Optional<StagedDungeonLayout> compile(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> pool,
            int depth,
            BlockPos start,
            JigsawStructure.MaxDistance maxDistance) {
        return StagedDungeonLayoutCompiler.compile(
                context,
                pool,
                Optional.of(START_JIGSAW),
                depth,
                start,
                Optional.empty(),
                maxDistance,
                LiquidSettings.IGNORE_WATERLOGGING);
    }

    /**
     * Completes on the server thread, always queued and never run inline: MinecraftServer.execute
     * runs a task at once on the server thread, which would complete the future inside the caller's
     * tick when the worker finishes before this stage is attached.
     */
    private static <T> CompletableFuture<T> onServer(
            MinecraftServer server, CompletableFuture<T> worker) {
        return worker.thenApplyAsync(
                result -> result, task -> server.schedule(server.wrapRunnable(task)));
    }

    private static DungeonTier tier(int number) {
        for (DungeonTier tier : DungeonTier.values()) {
            if (tier.tier == number) {
                return tier;
            }
        }
        throw new IllegalArgumentException(
                "Dungeon tier must be %d to %d: %d"
                        .formatted(
                                ProceduralDungeonApi.MIN_TIER,
                                ProceduralDungeonApi.MAX_TIER,
                                number));
    }
}
