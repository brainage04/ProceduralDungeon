package com.github.brainage04.procedural_dungeon.command;

import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonGenerationProfiler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class BenchmarkDungeonCommand {
    private static final int DEFAULT_SAMPLES = 3;
    private static final int DEFAULT_SPACING = 256;
    private static final int DEFAULT_CHUNK_RADIUS = 8;
    private static final long DEFAULT_SEED = 0xBEEFL;

    public static void initialize(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("benchmarkdungeons")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("reload")
                        .executes(context -> benchmarkReload(context.getSource()))
                )
                .then(literal("preload")
                        .executes(context -> preloadChunks(
                                context.getSource(),
                                DEFAULT_SAMPLES,
                                DEFAULT_SPACING,
                                DEFAULT_CHUNK_RADIUS
                        ))
                        .then(argument("samples", IntegerArgumentType.integer(1, DungeonTheme.values().length))
                                .executes(context -> preloadChunks(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "samples"),
                                        DEFAULT_SPACING,
                                        DEFAULT_CHUNK_RADIUS
                                ))
                                .then(argument("spacing", IntegerArgumentType.integer(1, 2048))
                                        .executes(context -> preloadChunks(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "samples"),
                                                IntegerArgumentType.getInteger(context, "spacing"),
                                                DEFAULT_CHUNK_RADIUS
                                        ))
                                        .then(argument("chunkRadius", IntegerArgumentType.integer(0, 16))
                                                .executes(context -> preloadChunks(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "samples"),
                                                        IntegerArgumentType.getInteger(context, "spacing"),
                                                        IntegerArgumentType.getInteger(context, "chunkRadius")
                                                ))
                                        )
                                )
                        )
                )
                .then(literal("clearpreload")
                        .executes(context -> clearPreloadedChunks(
                                context.getSource(),
                                DEFAULT_SAMPLES,
                                DEFAULT_SPACING,
                                DEFAULT_CHUNK_RADIUS
                        ))
                        .then(argument("samples", IntegerArgumentType.integer(1, DungeonTheme.values().length))
                                .executes(context -> clearPreloadedChunks(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "samples"),
                                        DEFAULT_SPACING,
                                        DEFAULT_CHUNK_RADIUS
                                ))
                                .then(argument("spacing", IntegerArgumentType.integer(1, 2048))
                                        .executes(context -> clearPreloadedChunks(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "samples"),
                                                IntegerArgumentType.getInteger(context, "spacing"),
                                                DEFAULT_CHUNK_RADIUS
                                        ))
                                        .then(argument("chunkRadius", IntegerArgumentType.integer(0, 16))
                                                .executes(context -> clearPreloadedChunks(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "samples"),
                                                        IntegerArgumentType.getInteger(context, "spacing"),
                                                        IntegerArgumentType.getInteger(context, "chunkRadius")
                                                ))
                                        )
                                )
                        )
                )
                .then(literal("generation")
                        .executes(context -> benchmarkGeneration(
                                context.getSource(),
                                DEFAULT_SAMPLES,
                                DEFAULT_SPACING,
                                DEFAULT_SEED
                        ))
                        .then(argument("samples", IntegerArgumentType.integer(1, DungeonTheme.values().length))
                                .executes(context -> benchmarkGeneration(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "samples"),
                                        DEFAULT_SPACING,
                                        DEFAULT_SEED
                                ))
                                .then(argument("spacing", IntegerArgumentType.integer(1, 2048))
                                        .executes(context -> benchmarkGeneration(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "samples"),
                                                IntegerArgumentType.getInteger(context, "spacing"),
                                                DEFAULT_SEED
                                        ))
                                        .then(argument("seed", LongArgumentType.longArg())
                                                .executes(context -> benchmarkGeneration(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "samples"),
                                                        IntegerArgumentType.getInteger(context, "spacing"),
                                                        LongArgumentType.getLong(context, "seed")
                                                ))
                                        )
                                )
                        )
                )
        );
    }

    private static int benchmarkReload(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        long start = System.nanoTime();

        source.sendSuccess(() -> Component.literal("Benchmarking data-pack reload..."), true);

        CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        reload.whenComplete((ignored, throwable) -> {
            long elapsed = System.nanoTime() - start;
            server.execute(() -> {
                if (throwable != null) {
                    source.sendFailure(Component.literal("Data-pack reload benchmark failed: " + throwable.getMessage()));
                    return;
                }

                source.sendSuccess(() -> Component.literal("Data-pack reload completed in %.2f ms.".formatted(toMillis(elapsed))), true);
            });
        });

        return 1;
    }

    private static int preloadChunks(CommandSourceStack source, int samples, int spacing, int chunkRadius) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        Set<ChunkPos> chunks = chunksForSamples(origin, samples, spacing, chunkRadius);

        long start = System.nanoTime();
        for (ChunkPos chunk : chunks) {
            level.setChunkForced(chunk.x(), chunk.z(), true);
            level.getChunk(chunk.x(), chunk.z());
        }
        long elapsed = System.nanoTime() - start;

        source.sendSuccess(() -> Component.literal(
                "Preloaded and force-loaded %d chunks for %d tier 5 samples in %.2f ms."
                        .formatted(chunks.size(), samples, toMillis(elapsed))
        ), true);

        return chunks.size();
    }

    private static int clearPreloadedChunks(CommandSourceStack source, int samples, int spacing, int chunkRadius) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        Set<ChunkPos> chunks = chunksForSamples(origin, samples, spacing, chunkRadius);

        for (ChunkPos chunk : chunks) {
            level.setChunkForced(chunk.x(), chunk.z(), false);
        }

        source.sendSuccess(() -> Component.literal(
                "Cleared %d force-loaded chunks for %d tier 5 samples.".formatted(chunks.size(), samples)
        ), true);

        return chunks.size();
    }

    private static int benchmarkGeneration(CommandSourceStack source, int samples, int spacing, long seed)
            throws CommandSyntaxException {
        List<GenerationSample> generationSamples = tierFiveSamples(samples, seed);
        BlockPos origin = BlockPos.containing(source.getPosition());
        Timing timing = new Timing();

        source.sendSuccess(() -> Component.literal(
                "Benchmarking %d tier 5 dungeon placements (spacing %d). Run /benchmarkdungeons preload first to exclude chunk loading."
                        .formatted(generationSamples.size(), spacing)
        ), true);

        long totalStart = System.nanoTime();
        for (int i = 0; i < generationSamples.size(); i++) {
            GenerationSample sample = generationSamples.get(i);
            BlockPos pos = samplePosition(origin, i, spacing);

            long placementStart = System.nanoTime();
            DungeonGenerationProfiler.begin();
            DungeonGenerationProfiler.Snapshot profile;
            try {
                place(source, sample, pos);
            } finally {
                profile = DungeonGenerationProfiler.finish();
            }
            long placementNanos = System.nanoTime() - placementStart;
            timing.add(placementNanos, profile);

            source.sendSuccess(() -> Component.literal(
                    "Tier 5 %s: place %.2f ms, %d pieces (%d placed, %d failed), bbox volume %,d, jigsaws %d/%d kept (%d pruned) at %s."
                            .formatted(
                                    sample.theme.name().toLowerCase(),
                                    toMillis(placementNanos),
                                    profile.pieces(),
                                    profile.placedPieces(),
                                    profile.failedPieces(),
                                    profile.boundingBoxVolume(),
                                    profile.keptExpandableJigsaws(),
                                    profile.expandableJigsaws(),
                                    profile.prunedExpandableJigsaws(),
                                    pos.toShortString()
                            )
            ), true);
        }
        long totalNanos = System.nanoTime() - totalStart;

        source.sendSuccess(() -> Component.literal(
                "Tier 5 benchmark completed in %.2f ms. Avg place %.2f ms, max place %.2f ms. Avg pieces %.1f, max pieces %d, avg bbox volume %,.0f, max bbox volume %,d."
                        .formatted(
                                toMillis(totalNanos),
                                timing.averagePlacementMillis(),
                                timing.maxPlacementMillis(),
                                timing.averagePieces(),
                                timing.maxPieces,
                                timing.averageBoundingBoxVolume(),
                                timing.maxBoundingBoxVolume
                        )
        ), true);

        return generationSamples.size();
    }

    private static List<GenerationSample> tierFiveSamples(int samples, long seed) {
        List<DungeonTheme> themes = new ArrayList<>(List.of(DungeonTheme.values()));
        Collections.shuffle(themes, new java.util.Random(seed ^ DungeonTier.TIER_5.tier));

        List<GenerationSample> generationSamples = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            generationSamples.add(new GenerationSample(themes.get(i), DungeonTier.TIER_5));
        }

        return generationSamples;
    }

    private static void place(CommandSourceStack source, GenerationSample sample, BlockPos pos) throws CommandSyntaxException {
        String key = RegistryKeyUtils.getKeyString(sample.theme, sample.tier);
        ResourceKey<Structure> structureKey = RegistryKeyUtils.create(Registries.STRUCTURE, key);
        var structure = source.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(structureKey);
        PlaceCommand.placeStructure(source.withSuppressedOutput().withPosition(Vec3.atBottomCenterOf(pos)), structure, pos);
    }

    private static Set<ChunkPos> chunksForSamples(BlockPos origin, int samples, int spacing, int radius) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (int i = 0; i < samples; i++) {
            addNearbyChunks(chunks, samplePosition(origin, i, spacing), radius);
        }

        return chunks;
    }

    private static BlockPos samplePosition(BlockPos origin, int sampleIndex, int spacing) {
        return origin.offset(sampleIndex * spacing, 0, 0);
    }

    private static void addNearbyChunks(Set<ChunkPos> chunks, BlockPos pos, int radius) {
        ChunkPos center = ChunkPos.containing(pos);

        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
    }

    private static double toMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record GenerationSample(DungeonTheme theme, DungeonTier tier) {}

    private static final class Timing {
        private int samples;
        private long placementNanos;
        private long maxPlacementNanos;
        private int pieces;
        private int maxPieces;
        private long boundingBoxVolume;
        private long maxBoundingBoxVolume;

        private void add(long placementNanos, DungeonGenerationProfiler.Snapshot profile) {
            samples++;
            this.placementNanos += placementNanos;
            this.maxPlacementNanos = Math.max(maxPlacementNanos, placementNanos);
            this.pieces += profile.pieces();
            this.maxPieces = Math.max(maxPieces, profile.pieces());
            this.boundingBoxVolume += profile.boundingBoxVolume();
            this.maxBoundingBoxVolume = Math.max(maxBoundingBoxVolume, profile.boundingBoxVolume());
        }

        private double averagePlacementMillis() {
            return samples == 0 ? 0.0 : toMillis(placementNanos) / samples;
        }

        private double maxPlacementMillis() {
            return toMillis(maxPlacementNanos);
        }

        private double averagePieces() {
            return samples == 0 ? 0.0 : (double) pieces / samples;
        }

        private double averageBoundingBoxVolume() {
            return samples == 0 ? 0.0 : (double) boundingBoxVolume / samples;
        }
    }
}
