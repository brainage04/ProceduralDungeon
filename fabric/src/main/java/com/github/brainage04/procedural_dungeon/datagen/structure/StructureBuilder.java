package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.google.common.hash.Hashing;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/**
 * Builds structure template NBT block by block for generated dungeon pieces.
 */
final class StructureBuilder {
    static final int MAX_STRUCTURE_BLOCK_AXIS = 48;

    private final int xSize;
    private final int ySize;
    private final int zSize;
    private final List<PaletteState> palette = new ArrayList<>();
    private final Map<PaletteState, Integer> paletteIndexes = new LinkedHashMap<>();
    private final Map<String, CompoundTag> blocks = new LinkedHashMap<>();

    StructureBuilder(int xSize, int ySize, int zSize) {
        if (xSize > MAX_STRUCTURE_BLOCK_AXIS || ySize > MAX_STRUCTURE_BLOCK_AXIS || zSize > MAX_STRUCTURE_BLOCK_AXIS) {
            throw new IllegalStateException(
                    "Generated structure exceeds the Structure Block limit: %dx%dx%d".formatted(xSize, ySize, zSize)
            );
        }
        this.xSize = xSize;
        this.ySize = ySize;
        this.zSize = zSize;
    }

    void block(int x, int y, int z, String name) {
        block(x, y, z, name, Map.of());
    }

    void block(int x, int y, int z, String name, Map<String, String> properties) {
        blockEntity(x, y, z, name, properties, null);
    }

    void blockEntity(int x, int y, int z, String name, Map<String, String> properties, CompoundTag nbt) {
        if (x < 0 || x >= xSize || y < 0 || y >= ySize || z < 0 || z >= zSize) {
            return;
        }

        putBlock(x, y, z, paletteIndex(new PaletteState(name, properties)), nbt);
    }

    void fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String name) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    block(x, y, z, name);
                }
            }
        }
    }

    void jigsaw(int x, int y, int z, String orientation, String name, String pool, String target, String finalState, String joint) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("components", new CompoundTag());
        nbt.putString("joint", joint);
        nbt.putString("name", name);
        nbt.putString("pool", pool);
        nbt.putString("final_state", finalState);
        nbt.putInt("placement_priority", 0);
        nbt.putInt("selection_priority", 0);
        nbt.putString("id", "minecraft:jigsaw");
        nbt.putString("target", target);
        blockEntity(x, y, z, "minecraft:jigsaw", properties("orientation", orientation), nbt);
    }

    /**
     * A data structure block; the placement processors remove it and the metadata is handled in code.
     */
    void dataMarker(int x, int y, int z, String metadata) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("components", new CompoundTag());
        nbt.putString("id", "minecraft:structure_block");
        nbt.putString("mode", "DATA");
        nbt.putString("metadata", metadata);
        blockEntity(x, y, z, "minecraft:structure_block", properties("mode", "data"), nbt);
    }

    CompoundTag build() {
        CompoundTag structure = new CompoundTag();
        structure.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        structure.put("size", intList(xSize, ySize, zSize));
        structure.put("entities", new ListTag());

        ListTag paletteTag = new ListTag();
        for (PaletteState state : palette) {
            paletteTag.add(state.toTag());
        }
        structure.put("palette", paletteTag);
        ListTag blockList = new ListTag();
        blocks.values().forEach(blockList::add);
        structure.put("blocks", blockList);
        return structure;
    }

    static Map<String, String> properties(String... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Properties must be provided as key/value pairs.");
        }

        Map<String, String> properties = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            properties.put(values[i], values[i + 1]);
        }
        return properties;
    }

    static CompletableFuture<?> save(CachedOutput writer, CompoundTag nbt, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                NbtIo.writeCompressed(nbt, output);
                byte[] bytes = output.toByteArray();
                writer.writeIfNeeded(path, bytes, Hashing.sha256().hashBytes(bytes));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write generated structure: " + path, e);
            }
        });
    }

    private int paletteIndex(PaletteState state) {
        Integer existing = paletteIndexes.get(state);
        if (existing != null) {
            return existing;
        }

        int index = palette.size();
        palette.add(state);
        paletteIndexes.put(state, index);
        return index;
    }

    private void putBlock(int x, int y, int z, int state, CompoundTag nbt) {
        CompoundTag block = new CompoundTag();
        block.put("pos", intList(x, y, z));
        block.putInt("state", state);
        if (nbt != null) {
            block.put("nbt", nbt);
        }
        blocks.put("%d,%d,%d".formatted(x, y, z), block);
    }

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    private record PaletteState(String name, Map<String, String> properties) {
        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            if (!properties.isEmpty()) {
                CompoundTag propertiesTag = new CompoundTag();
                properties.forEach(propertiesTag::putString);
                tag.put("Properties", propertiesTag);
            }
            return tag;
        }
    }
}
