package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.data.DataOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.DataWriter;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DungeonSpawnerStructureProvider implements DataProvider {
    private final FabricDataOutput output;
    private final DataOutput.PathResolver pathResolver;
    private final CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup;

    public DungeonSpawnerStructureProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        this.output = output;
        this.pathResolver = output.getResolver(RegistryKeys.STRUCTURE);
        this.registryLookup = registryLookup;
    }

    private record BlockInfo(Block block, BlockPos pos, BlockState state) {}

    private void generateSpawner(SpawnerTier tier, SpawnerMob mob) {
        // todo
    }

    private void applySpawnerSettings(NbtCompound structure, SpawnerTier tier, SpawnerMob mob) {
        NbtList blocks = structure.getListOrEmpty("blocks");
        if (blocks.isEmpty()) return;

        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound block = blocks.getCompoundOrEmpty(i);
            if (block.isEmpty()) continue;
            NbtCompound nbt = block.getCompoundOrEmpty("nbt");
            if (nbt.isEmpty()) continue;
            String id = nbt.getString("id", "");
            if (id.isEmpty()) continue;

            if (id.equals("minecraft:mob_spawner")) {
                // apply SpawnerTier
                nbt.putInt("MaxNearbyEntities", tier.maxNearbyEntities);
                nbt.putInt("MinSpawnDelay", tier.minSpawnDelay);
                nbt.putInt("MaxSpawnDelay", tier.maxSpawnDelay);
                nbt.putInt("RequiredPlayerRange", tier.requiredPlayerRange);
                nbt.putInt("SpawnCount", tier.spawnCount);
                nbt.putInt("SpawnRange", tier.spawnRange);

                // apply SpawnerMob
                NbtCompound spawnData = nbt.getCompoundOrEmpty("SpawnData");
                if (spawnData.isEmpty()) continue;

                NbtCompound entity = spawnData.getCompoundOrEmpty("entity");
                entity.putString("id", mob.entityId);

                // todo: step through with debugger to see if these last statements are even required
                spawnData.put("entity", entity);
                nbt.put("SpawnData", spawnData);
                blocks.set(i, block);
            }
        }
    }

    static CompletableFuture<?> writeToPath(DataWriter writer, NbtCompound nbt, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                File file = path.toFile();
                if (!file.createNewFile()) return;

                NbtIo.writeCompressed(nbt, path);
            } catch (IOException e) {
                LOGGER.error("Failed to save file to {}", path, e);
            }
        }, Util.getMainWorkerExecutor().named("saveStable"));
    }

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        return this.registryLookup.thenCompose(lookup -> {
            NbtCompound result;
            Identifier id1 = ProceduralDungeon.of("dungeon/spawner_base");
            String resPath = "src/main/resources/data/%s/structures/%s.nbt".formatted(id1.getNamespace(), id1.getPath());

            try (InputStream is = DungeonSpawnerStructureProvider.class.getClassLoader().getResourceAsStream(resPath)) {
                if (is == null) {
                    throw new IllegalStateException("Base structure %s not found".formatted(resPath));
                }

                result = NbtIo.readCompressed(is, NbtSizeTracker.ofUnlimitedBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to load base structure", e);
            }
            NbtCompound base = result;

            final List<CompletableFuture<?>> futures = new ArrayList<>();

            SpawnerTier tier = SpawnerTier.TIER_1;
            for (SpawnerMob mob : SpawnerMob.values()) {
                NbtCompound copy = base.copy();
                applySpawnerSettings(copy, tier, mob);

                Identifier id = ProceduralDungeon.of(
                        "dungeons/spawner_%s_t%s".formatted(mob.id, tier.id)
                );

                Path path = pathResolver.resolve(id, "nbt");

                futures.add(DungeonSpawnerStructureProvider.writeToPath(writer, copy, path));
            }

            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        });
    }

    @Override
    public String getName() {
        return "Dungeon Spawner Structures";
    }
}