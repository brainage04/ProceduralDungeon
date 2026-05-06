package com.github.brainage04.procedural_dungeon.test;

import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

public class DungeonVariantSmokeTester {
    public static final int DEFAULT_COUNT = 12;
    public static final int DEFAULT_SPACING = 192;
    public static final long DEFAULT_SEED = 0x5EED_0D06E0DEL;

    public record Result(int attempted, List<String> variants) {}

    public static Result placeRandomVariants(CommandSourceStack source, int count, int spacing, long seed)
            throws CommandSyntaxException {
        BlockPos origin = BlockPos.containing(source.getPosition());
        return placeRandomVariants(source, origin, count, spacing, seed);
    }

    public static Result placeRandomVariants(CommandSourceStack source, BlockPos origin, int count, int spacing, long seed)
            throws CommandSyntaxException {
        List<String> variants = new ArrayList<>(getVariantKeys());
        Collections.shuffle(variants, new Random(seed));

        int attempts = Math.min(count, variants.size());
        int columns = (int) Math.ceil(Math.sqrt(attempts));
        List<String> placed = new ArrayList<>(attempts);
        ServerLevel world = source.getLevel();

        for (int i = 0; i < attempts; i++) {
            String variant = variants.get(i);
            BlockPos pos = origin.offset((i % columns) * spacing, 0, (i / columns) * spacing);
            loadNearbyChunks(world, pos, 8);

            ResourceKey<Structure> key = RegistryKeyUtils.create(Registries.STRUCTURE, variant);
            var structure = source.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
            PlaceCommand.placeStructure(source.withPosition(Vec3.atBottomCenterOf(pos)), structure, pos);
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

    private static void loadNearbyChunks(ServerLevel world, BlockPos pos, int radius) {
        ChunkPos center = ChunkPos.containing(pos);

        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                world.getChunk(x, z);
            }
        }
    }
}
