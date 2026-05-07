package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;

public class SpawnerStructureProvider implements DataProvider {
    private static final Gson GSON = new Gson();
    private static final Path SPEC_PATH = Path.of("src/main/datagen/procedural_dungeon/spawners.json");

    private final PackOutput.PathProvider structureResolver;
    private final PackOutput.PathProvider templatePoolResolver;
    private final JsonObject spec;

    public SpawnerStructureProvider(FabricPackOutput output) {
        this.structureResolver = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
        this.templatePoolResolver = output.createPathProvider(PackOutput.Target.DATA_PACK, "worldgen/template_pool");
        this.spec = loadSpec();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        JsonArray entities = spec.getAsJsonArray("entities");

        for (DungeonTier tier : DungeonTier.values()) {
            int tierId = tier.tier;

            for (JsonElement entityElement : entities) {
                String entityId = normalizeId(entityElement.getAsString());
                Identifier structureId = getStructureId(tierId, entityId);
                futures.add(saveNbt(writer, createSpawnerStructure(tier, entityId), structureResolver.file(structureId, "nbt")));
            }

            futures.add(DataProvider.saveStable(writer, createTemplatePool(tierId, entities), templatePoolResolver.json(ProceduralDungeon.of("dungeon/spawner/tier_%d".formatted(tierId)))));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private static JsonObject loadSpec() {
        Path specPath = resolveSpecPath();
        try (Reader reader = Files.newBufferedReader(specPath)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read spawner spec: " + specPath, e);
        }
    }

    private static Path resolveSpecPath() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        while (currentDirectory != null) {
            Path specPath = currentDirectory.resolve(SPEC_PATH);
            if (Files.exists(specPath)) {
                return specPath;
            }

            currentDirectory = currentDirectory.getParent();
        }

        throw new IllegalStateException("Failed to find spawner spec: " + SPEC_PATH);
    }

    private static CompletableFuture<?> saveNbt(CachedOutput writer, CompoundTag nbt, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = writeCompressed(nbt);
                writer.writeIfNeeded(path, bytes, Hashing.sha256().hashBytes(bytes));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write spawner structure: " + path, e);
            }
        });
    }

    private static byte[] writeCompressed(CompoundTag nbt) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, output);
        return output.toByteArray();
    }

    private static CompoundTag createSpawnerStructure(DungeonTier tier, String entityId) {
        CompoundTag structure = new CompoundTag();
        structure.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        structure.put("size", intList(1, 2, 1));
        structure.put("entities", new ListTag());

        ListTag palette = new ListTag();
        palette.add(createJigsawPaletteState());
        palette.add(createBlockState("minecraft:spawner"));
        structure.put("palette", palette);

        ListTag blocks = new ListTag();
        blocks.add(createBlock(0, 0, 0, 0, createJigsawBlockEntity()));
        blocks.add(createBlock(0, 1, 0, 1, createSpawnerBlockEntity(tier, entityId)));
        structure.put("blocks", blocks);

        return structure;
    }

    private static CompoundTag createJigsawPaletteState() {
        CompoundTag state = createBlockState("minecraft:jigsaw");
        CompoundTag properties = new CompoundTag();
        properties.putString("orientation", "down_north");
        state.put("Properties", properties);
        return state;
    }

    private static CompoundTag createBlockState(String name) {
        CompoundTag state = new CompoundTag();
        state.putString("Name", name);
        return state;
    }

    private static CompoundTag createBlock(int x, int y, int z, int state, CompoundTag nbt) {
        CompoundTag block = new CompoundTag();
        block.put("pos", intList(x, y, z));
        block.putInt("state", state);
        block.put("nbt", nbt);
        return block;
    }

    private static CompoundTag createJigsawBlockEntity() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("components", new CompoundTag());
        nbt.putString("joint", "aligned");
        nbt.putString("name", "minecraft:start");
        nbt.putString("pool", "minecraft:empty");
        nbt.putString("final_state", "minecraft:cobblestone");
        nbt.putInt("placement_priority", 0);
        nbt.putInt("selection_priority", 0);
        nbt.putString("id", "minecraft:jigsaw");
        nbt.putString("target", "minecraft:empty");
        return nbt;
    }

    private static CompoundTag createSpawnerBlockEntity(DungeonTier tier, String entityId) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("components", new CompoundTag());
        nbt.putInt("MaxNearbyEntities", tier.spawnerMaxNearbyEntities);
        nbt.putInt("RequiredPlayerRange", tier.spawnerRequiredPlayerRange);
        nbt.putInt("SpawnCount", tier.spawnerSpawnCount);
        nbt.put("SpawnData", createSpawnData(entityId));
        nbt.putInt("MaxSpawnDelay", tier.spawnerMaxSpawnDelay);
        nbt.putString("id", "minecraft:mob_spawner");
        nbt.putInt("SpawnRange", tier.spawnerSpawnRange);
        nbt.putInt("Delay", 0);
        nbt.putInt("MinSpawnDelay", tier.spawnerMinSpawnDelay);
        nbt.put("SpawnPotentials", new ListTag());
        return nbt;
    }

    private static CompoundTag createSpawnData(String entityId) {
        CompoundTag spawnData = new CompoundTag();
        CompoundTag entity = new CompoundTag();
        entity.putString("id", entityId);
        spawnData.put("entity", entity);
        return spawnData;
    }

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    private static JsonObject createTemplatePool(int tierId, JsonArray entities) {
        JsonObject pool = new JsonObject();
        pool.addProperty("fallback", "minecraft:empty");

        JsonArray elements = new JsonArray();
        for (JsonElement entityElement : entities) {
            JsonObject weightedElement = new JsonObject();
            weightedElement.addProperty("weight", 1);
            weightedElement.add("element", createTemplatePoolElement(tierId, normalizeId(entityElement.getAsString())));
            elements.add(weightedElement);
        }

        pool.add("elements", elements);
        return pool;
    }

    private static JsonObject createTemplatePoolElement(int tierId, String entityId) {
        JsonObject element = new JsonObject();
        element.addProperty("element_type", "minecraft:single_pool_element");
        element.addProperty("location", getStructureId(tierId, entityId).toString());
        element.addProperty("projection", "rigid");
        element.addProperty("processors", "minecraft:empty");
        return element;
    }

    private static Identifier getStructureId(int tierId, String entityId) {
        return ProceduralDungeon.of("dungeon/spawner/tier_%d/%s".formatted(tierId, path(entityId)));
    }

    private static String normalizeId(String id) {
        if (id.contains(":")) {
            return id;
        }

        return "minecraft:" + id;
    }

    private static String path(String id) {
        return Identifier.parse(id).getPath();
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Spawner Structures";
    }
}
