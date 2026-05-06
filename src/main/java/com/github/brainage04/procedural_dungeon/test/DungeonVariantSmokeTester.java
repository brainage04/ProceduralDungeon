package com.github.brainage04.procedural_dungeon.test;

import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.PlaceCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DungeonVariantSmokeTester {
    public static final int DEFAULT_COUNT = 12;
    public static final int DEFAULT_SPACING = 192;
    public static final long DEFAULT_SEED = 0x5EED_0D06E0DEL;

    public record Result(int attempted, List<String> variants) {}

    public static Result placeRandomVariants(ServerCommandSource source, int count, int spacing, long seed)
            throws CommandSyntaxException {
        BlockPos origin = BlockPos.ofFloored(source.getPosition());
        return placeRandomVariants(source, origin, count, spacing, seed);
    }

    public static Result placeRandomVariants(ServerCommandSource source, BlockPos origin, int count, int spacing, long seed)
            throws CommandSyntaxException {
        List<String> variants = new ArrayList<>(getVariantKeys());
        Collections.shuffle(variants, new Random(seed));

        int attempts = Math.min(count, variants.size());
        int columns = (int) Math.ceil(Math.sqrt(attempts));
        List<String> placed = new ArrayList<>(attempts);
        ServerWorld world = source.getWorld();

        for (int i = 0; i < attempts; i++) {
            String variant = variants.get(i);
            BlockPos pos = origin.add((i % columns) * spacing, 0, (i / columns) * spacing);
            loadNearbyChunks(world, pos, 8);

            RegistryKey<Structure> key = RegistryKeyUtils.create(RegistryKeys.STRUCTURE, variant);
            var structure = source.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE).getOrThrow(key);
            PlaceCommand.executePlaceStructure(source.withPosition(Vec3d.ofBottomCenter(pos)), structure, pos);
            placed.add(variant);
        }

        return new Result(attempts, placed);
    }

    public static List<String> getVariantKeys() {
        List<String> variants = new ArrayList<>();

        for (DungeonTheme theme : DungeonTheme.values()) {
            for (DungeonTier tier : DungeonTier.values()) {
                variants.add(RegistryKeyUtils.getKeyString(theme, tier));
            }
        }

        return variants;
    }

    private static void loadNearbyChunks(ServerWorld world, BlockPos pos, int radius) {
        ChunkPos center = new ChunkPos(pos);

        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                world.getChunk(x, z);
            }
        }
    }
}
