package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.google.common.hash.Hashing;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

public class EntranceStructureProvider implements DataProvider {
    private static final int MAX_STRUCTURE_BLOCK_AXIS = 48;
    private static final int MAX_ENTRANCE_DEPTH = 40;

    private final PackOutput.PathProvider structureResolver;

    public EntranceStructureProvider(FabricPackOutput output) {
        this.structureResolver = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (DungeonTier tier : DungeonTier.values()) {
            futures.add(saveNbt(writer, hatch(tier), structurePath(tier, "hatch")));
            futures.add(saveNbt(writer, well(tier), structurePath(tier, "well")));
            futures.add(saveNbt(writer, staircase(tier), structurePath(tier, "staircase")));
            futures.add(saveNbt(writer, shrine(tier), structurePath(tier, "shrine")));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private Path structurePath(DungeonTier tier, String name) {
        Identifier id = ProceduralDungeon.of("dungeon/entrance/tier_%d/%s".formatted(tier.tier, name));
        return structureResolver.file(id, "nbt");
    }

    private static CompoundTag hatch(DungeonTier tier) {
        int depth = entranceDepth(tier);
        int size = 5;
        int surfaceY = depth;
        StructureBuilder builder = new StructureBuilder(size, depth + 5, size);
        int center = 2;

        carveVerticalShaft(builder, center, center, surfaceY, 1);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                builder.block(x, surfaceY - 1, z, "minecraft:cobblestone");
            }
        }
        for (int z = 1; z <= 3; z++) {
            builder.block(1, surfaceY, z, "minecraft:cobblestone");
            builder.block(3, surfaceY, z, "minecraft:cobblestone");
            builder.block(z, surfaceY, 1, "minecraft:cobblestone");
            builder.block(z, surfaceY, 3, "minecraft:cobblestone");
        }
        builder.block(center, surfaceY, center, "minecraft:oak_trapdoor", properties("half", "bottom", "open", "true", "facing", "north", "waterlogged", "false"));
        for (int y = 1; y < surfaceY; y++) {
            builder.block(center, y, center - 1, "minecraft:ladder", properties("facing", "south", "waterlogged", "false"));
        }

        builder.startJigsaw(center, surfaceY + 1, center);
        builder.dungeonJigsaw(center, 0, center);
        return builder.build();
    }

    private static CompoundTag well(DungeonTier tier) {
        int depth = entranceDepth(tier);
        int size = 7;
        int surfaceY = depth;
        StructureBuilder builder = new StructureBuilder(size, depth + 6, size);
        int center = 3;

        carveVerticalShaft(builder, center, center, surfaceY, 2);
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                if (x == 1 || x == 5 || z == 1 || z == 5) {
                    builder.block(x, surfaceY, z, "minecraft:cobblestone_wall", properties("east", "none", "north", "none", "south", "none", "up", "true", "waterlogged", "false", "west", "none"));
                }
            }
        }
        for (int y = surfaceY + 1; y <= surfaceY + 3; y++) {
            builder.block(1, y, 1, "minecraft:cobblestone_wall", properties("east", "none", "north", "none", "south", "none", "up", "true", "waterlogged", "false", "west", "none"));
            builder.block(5, y, 1, "minecraft:cobblestone_wall", properties("east", "none", "north", "none", "south", "none", "up", "true", "waterlogged", "false", "west", "none"));
            builder.block(1, y, 5, "minecraft:cobblestone_wall", properties("east", "none", "north", "none", "south", "none", "up", "true", "waterlogged", "false", "west", "none"));
            builder.block(5, y, 5, "minecraft:cobblestone_wall", properties("east", "none", "north", "none", "south", "none", "up", "true", "waterlogged", "false", "west", "none"));
        }
        for (int x = 1; x <= 5; x++) {
            builder.block(x, surfaceY + 4, 1, "minecraft:cobblestone");
            builder.block(x, surfaceY + 4, 5, "minecraft:cobblestone");
        }
        builder.block(center, surfaceY - 1, center, "minecraft:water", properties("level", "0"));

        builder.startJigsaw(center, surfaceY + 1, center);
        builder.dungeonJigsaw(center, 0, center);
        return builder.build();
    }

    private static CompoundTag staircase(DungeonTier tier) {
        int depth = entranceDepth(tier);
        int size = 7;
        int surfaceY = depth;
        StructureBuilder builder = new StructureBuilder(size, depth + 5, size);
        int center = 3;

        carveVerticalShaft(builder, center, center, surfaceY, 2);
        for (int y = 1; y <= surfaceY; y++) {
            int phase = Math.floorMod(surfaceY - y, 4);
            int x = switch (phase) {
                case 0 -> center;
                case 1 -> center + 1;
                case 2 -> center;
                default -> center - 1;
            };
            int z = switch (phase) {
                case 0 -> center - 1;
                case 1 -> center;
                case 2 -> center + 1;
                default -> center;
            };
            String facing = switch (phase) {
                case 0 -> "south";
                case 1 -> "west";
                case 2 -> "north";
                default -> "east";
            };
            builder.block(x, y - 1, z, "minecraft:cobblestone_stairs", properties("facing", facing, "half", "bottom", "shape", "straight", "waterlogged", "false"));
        }

        for (int x = 1; x <= 5; x++) {
            builder.block(x, surfaceY, 1, "minecraft:cobblestone");
            builder.block(x, surfaceY, 5, "minecraft:cobblestone");
        }
        for (int z = 1; z <= 5; z++) {
            builder.block(1, surfaceY, z, "minecraft:cobblestone");
            builder.block(5, surfaceY, z, "minecraft:cobblestone");
        }
        builder.startJigsaw(center, surfaceY + 1, center);
        builder.dungeonJigsaw(center, 0, center);
        return builder.build();
    }

    private static CompoundTag shrine(DungeonTier tier) {
        int depth = entranceDepth(tier);
        int size = 9;
        int surfaceY = depth;
        StructureBuilder builder = new StructureBuilder(size, depth + 8, size);
        int center = 4;

        carveVerticalShaft(builder, center, center, surfaceY, 2);
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                builder.block(x, surfaceY, z, "minecraft:cobblestone");
            }
        }
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                builder.block(x, surfaceY + 1, z, "minecraft:air");
            }
        }
        for (int x : List.of(1, 7)) {
            for (int z : List.of(1, 7)) {
                for (int y = surfaceY + 1; y <= surfaceY + 4; y++) {
                    builder.block(x, y, z, "minecraft:cobblestone_wall", properties("east", "none", "north", "none", "south", "none", "up", "true", "waterlogged", "false", "west", "none"));
                }
            }
        }
        for (int x = 2; x <= 6; x++) {
            builder.block(x, surfaceY + 5, 1, "minecraft:cobblestone_slab", properties("type", "bottom", "waterlogged", "false"));
            builder.block(x, surfaceY + 5, 7, "minecraft:cobblestone_slab", properties("type", "bottom", "waterlogged", "false"));
        }
        for (int z = 2; z <= 6; z++) {
            builder.block(1, surfaceY + 5, z, "minecraft:cobblestone_slab", properties("type", "bottom", "waterlogged", "false"));
            builder.block(7, surfaceY + 5, z, "minecraft:cobblestone_slab", properties("type", "bottom", "waterlogged", "false"));
        }
        builder.block(center, surfaceY + 1, center, "minecraft:chiseled_stone_bricks");
        builder.block(center, surfaceY + 2, center, "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));

        builder.startJigsaw(center, surfaceY + 1, center);
        builder.dungeonJigsaw(center, 0, center);
        return builder.build();
    }

    private static void carveVerticalShaft(StructureBuilder builder, int centerX, int centerZ, int surfaceY, int radius) {
        for (int y = 0; y <= surfaceY; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    builder.block(x, y, z, "minecraft:air");
                }
            }
            for (int x = centerX - radius - 1; x <= centerX + radius + 1; x++) {
                builder.block(x, y, centerZ - radius - 1, "minecraft:cobblestone");
                builder.block(x, y, centerZ + radius + 1, "minecraft:cobblestone");
            }
            for (int z = centerZ - radius - 1; z <= centerZ + radius + 1; z++) {
                builder.block(centerX - radius - 1, y, z, "minecraft:cobblestone");
                builder.block(centerX + radius + 1, y, z, "minecraft:cobblestone");
            }
        }
    }

    private static int entranceDepth(DungeonTier tier) {
        return Math.min(Math.abs(tier.surfaceOffset), MAX_ENTRANCE_DEPTH);
    }

    private static CompletableFuture<?> saveNbt(CachedOutput writer, CompoundTag nbt, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = writeCompressed(nbt);
                writer.writeIfNeeded(path, bytes, Hashing.sha256().hashBytes(bytes));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write entrance structure: " + path, e);
            }
        });
    }

    private static byte[] writeCompressed(CompoundTag nbt) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, output);
        return output.toByteArray();
    }

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    private static Map<String, String> properties(String... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Properties must be provided as key/value pairs.");
        }

        Map<String, String> properties = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            properties.put(values[i], values[i + 1]);
        }
        return properties;
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Entrance Structures";
    }

    private static class StructureBuilder {
        private final int xSize;
        private final int ySize;
        private final int zSize;
        private final List<PaletteState> palette = new ArrayList<>();
        private final Map<PaletteState, Integer> paletteIndexes = new LinkedHashMap<>();
        private final Map<String, CompoundTag> blocks = new LinkedHashMap<>();

        private StructureBuilder(int xSize, int ySize, int zSize) {
            this.xSize = xSize;
            this.ySize = ySize;
            this.zSize = zSize;
        }

        private void block(int x, int y, int z, String name) {
            block(x, y, z, name, Map.of());
        }

        private void block(int x, int y, int z, String name, Map<String, String> properties) {
            if (x < 0 || x >= xSize || y < 0 || y >= ySize || z < 0 || z >= zSize) {
                return;
            }

            putBlock(x, y, z, paletteIndex(new PaletteState(name, properties)), null);
        }

        private void startJigsaw(int x, int y, int z) {
            putBlock(
                    x,
                    y,
                    z,
                    paletteIndex(new PaletteState("minecraft:jigsaw", properties("orientation", "down_north"))),
                    jigsawNbt("minecraft:start", "minecraft:empty", "minecraft:empty", "minecraft:air")
            );
        }

        private void dungeonJigsaw(int x, int y, int z) {
            putBlock(
                    x,
                    y,
                    z,
                    paletteIndex(new PaletteState("minecraft:jigsaw", properties("orientation", "down_north"))),
                    jigsawNbt("procedural_dungeon:entrance", "procedural_dungeon:dungeon/start", "minecraft:start", "minecraft:cobblestone")
            );
        }

        private CompoundTag build() {
            if (xSize > MAX_STRUCTURE_BLOCK_AXIS || ySize > MAX_STRUCTURE_BLOCK_AXIS || zSize > MAX_STRUCTURE_BLOCK_AXIS) {
                throw new IllegalStateException(
                        "Generated entrance structure exceeds the Structure Block limit: %dx%dx%d"
                                .formatted(xSize, ySize, zSize)
                );
            }

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
            blocks.put("%d,%d,%d".formatted(x, y, z), createBlock(x, y, z, state, nbt));
        }

        private static CompoundTag createBlock(int x, int y, int z, int state, CompoundTag nbt) {
            CompoundTag block = new CompoundTag();
            block.put("pos", intList(x, y, z));
            block.putInt("state", state);
            if (nbt != null) {
                block.put("nbt", nbt);
            }
            return block;
        }

        private static CompoundTag jigsawNbt(String name, String pool, String target, String finalState) {
            CompoundTag nbt = new CompoundTag();
            nbt.put("components", new CompoundTag());
            nbt.putString("joint", "aligned");
            nbt.putString("name", name);
            nbt.putString("pool", pool);
            nbt.putString("final_state", finalState);
            nbt.putInt("placement_priority", 0);
            nbt.putInt("selection_priority", 0);
            nbt.putString("id", "minecraft:jigsaw");
            nbt.putString("target", target);
            return nbt;
        }
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
