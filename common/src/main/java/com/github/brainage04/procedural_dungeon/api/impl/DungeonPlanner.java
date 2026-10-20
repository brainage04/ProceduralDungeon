package com.github.brainage04.procedural_dungeon.api.impl;

import com.github.brainage04.procedural_dungeon.api.DungeonPlan;
import com.github.brainage04.procedural_dungeon.api.DungeonRequest;
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

/**
 * Compiles {@link DungeonRequest}s off the server thread. A plan is compiled at {@link
 * #CANONICAL_ORIGIN} in a height range no world has, so its layout depends only on the request;
 * {@code build} moves it and checks that it fits the target level.
 */
public final class DungeonPlanner {
    public static final BlockPos CANONICAL_ORIGIN = BlockPos.ZERO;
    public static final int MIN_DEPTH = 1;
    public static final int MAX_DEPTH = 20;
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
        if (request.depth() < MIN_DEPTH || request.depth() > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "Dungeon depth must be %d to %d: %d"
                            .formatted(MIN_DEPTH, MAX_DEPTH, request.depth()));
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
        return compileOffThread(
                        server,
                        context,
                        pool,
                        request.depth(),
                        CANONICAL_ORIGIN,
                        new JigsawStructure.MaxDistance(tier.maxDistanceFromCenter))
                .thenApply(layout -> layout.map(compiled -> new CompiledPlan(request, compiled)));
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
        return CompletableFuture.supplyAsync(
                        () ->
                                StagedDungeonLayoutCompiler.compile(
                                        context,
                                        pool,
                                        Optional.of(START_JIGSAW),
                                        depth,
                                        start,
                                        Optional.empty(),
                                        maxDistance,
                                        LiquidSettings.IGNORE_WATERLOGGING),
                        Util.backgroundExecutor())
                .thenApplyAsync(layout -> layout, server);
    }

    private static DungeonTier tier(int number) {
        for (DungeonTier tier : DungeonTier.values()) {
            if (tier.tier == number) {
                return tier;
            }
        }
        throw new IllegalArgumentException("Dungeon tier must be 1 to 5: " + number);
    }
}
