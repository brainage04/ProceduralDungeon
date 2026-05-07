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
    private static final int MAX_DECORATIVE_ENTRANCE_DEPTH = 22;

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
            futures.add(saveNbt(writer, ruinedArchway(tier), structurePath(tier, "ruined_archway")));
            futures.add(saveNbt(writer, sunkenCourtyard(tier), structurePath(tier, "sunken_courtyard")));
            futures.add(saveNbt(writer, ritualDescent(tier), structurePath(tier, "ritual_descent")));
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

    private static CompoundTag ruinedArchway(DungeonTier tier) {
        int depth = decorativeEntranceDepth(tier);
        int surfaceY = depth;
        StructureBuilder builder = new StructureBuilder(13, depth + 9, 13);
        int center = 6;

        carveVerticalShaft(builder, center, center, surfaceY, 2);
        buildRoughFloor(builder, 2, 10, surfaceY, 2, 10);

        for (int z = 2; z <= 10; z++) {
            builder.block(3, surfaceY + 1, z, pickStone(z));
            builder.block(9, surfaceY + 1, z, pickStone(z + 1));
        }
        for (int y = surfaceY + 1; y <= surfaceY + 5; y++) {
            builder.block(3, y, 3, "minecraft:stone_bricks");
            builder.block(3, y, 9, "minecraft:stone_bricks");
            builder.block(9, y, 3, "minecraft:stone_bricks");
            builder.block(9, y, 9, "minecraft:stone_bricks");
        }
        for (int x = 4; x <= 8; x++) {
            builder.block(x, surfaceY + 6, 3, "minecraft:stone_bricks");
            builder.block(x, surfaceY + 6, 9, "minecraft:mossy_cobblestone");
        }

        builder.block(2, surfaceY + 2, 6, "minecraft:cobblestone_wall", wall());
        builder.block(10, surfaceY + 2, 6, "minecraft:cobblestone_wall", wall());
        builder.block(4, surfaceY + 1, 4, "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));
        builder.block(8, surfaceY + 1, 8, "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));

        for (int y = 1; y < surfaceY; y++) {
            builder.block(center, y, center - 2, "minecraft:ladder", properties("facing", "south", "waterlogged", "false"));
        }

        builder.startJigsaw(center, surfaceY + 1, center);
        builder.dungeonJigsaw(center, 0, center);
        return builder.build();
    }

    private static CompoundTag sunkenCourtyard(DungeonTier tier) {
        int depth = decorativeEntranceDepth(tier);
        int surfaceY = depth;
        StructureBuilder builder = new StructureBuilder(15, depth + 8, 15);
        int center = 7;

        carveVerticalShaft(builder, center, center, surfaceY, 2);
        buildRoughFloor(builder, 2, 12, surfaceY, 2, 12);

        for (int x = 2; x <= 12; x++) {
            buildLowRuinWall(builder, x, surfaceY + 1, 2, x % 4 == 0 ? 2 : 1);
            buildLowRuinWall(builder, x, surfaceY + 1, 12, x % 5 == 0 ? 3 : 1);
        }
        for (int z = 3; z <= 11; z++) {
            buildLowRuinWall(builder, 2, surfaceY + 1, z, z % 4 == 1 ? 2 : 1);
            buildLowRuinWall(builder, 12, surfaceY + 1, z, z % 5 == 2 ? 3 : 1);
        }

        for (int x = 5; x <= 9; x++) {
            builder.block(x, surfaceY + 1, 5, "minecraft:stone_brick_slab", properties("type", "bottom", "waterlogged", "false"));
            builder.block(x, surfaceY + 1, 9, "minecraft:stone_brick_slab", properties("type", "bottom", "waterlogged", "false"));
        }
        for (int z = 6; z <= 8; z++) {
            builder.block(5, surfaceY + 1, z, "minecraft:stone_brick_slab", properties("type", "bottom", "waterlogged", "false"));
            builder.block(9, surfaceY + 1, z, "minecraft:stone_brick_slab", properties("type", "bottom", "waterlogged", "false"));
        }

        builder.block(center, surfaceY - 1, center, "minecraft:water", properties("level", "0"));
        builder.block(4, surfaceY + 1, 4, "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));
        builder.block(10, surfaceY + 1, 10, "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));

        builder.startJigsaw(center, surfaceY + 1, center);
        builder.dungeonJigsaw(center, 0, center);
        return builder.build();
    }

    private static CompoundTag ritualDescent(DungeonTier tier) {
        int depth = decorativeEntranceDepth(tier);
        int surfaceY = depth;
        StructureBuilder builder = new StructureBuilder(15, depth + 8, 15);
        int center = 7;

        carveVerticalShaft(builder, center, center, surfaceY, 2);
        buildRoughFloor(builder, 3, 11, surfaceY, 3, 11);

        for (int offset = -4; offset <= 4; offset++) {
            builder.block(center + offset, surfaceY + 1, center - 4, "minecraft:cobbled_deepslate_wall", wall());
            builder.block(center + offset, surfaceY + 1, center + 4, "minecraft:cobbled_deepslate_wall", wall());
            builder.block(center - 4, surfaceY + 1, center + offset, "minecraft:cobbled_deepslate_wall", wall());
            builder.block(center + 4, surfaceY + 1, center + offset, "minecraft:cobbled_deepslate_wall", wall());
        }
        for (int[] pillar : List.of(
                new int[]{3, 3},
                new int[]{3, 11},
                new int[]{11, 3},
                new int[]{11, 11}
        )) {
            for (int y = surfaceY + 1; y <= surfaceY + 4; y++) {
                builder.block(pillar[0], y, pillar[1], y == surfaceY + 4 ? "minecraft:chiseled_stone_bricks" : "minecraft:deepslate_bricks");
            }
            builder.block(pillar[0], surfaceY + 5, pillar[1], "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));
        }

        for (int y = 1; y <= surfaceY; y++) {
            int ring = Math.floorMod(surfaceY - y, 4);
            int x = switch (ring) {
                case 0 -> center;
                case 1 -> center + 1;
                case 2 -> center;
                default -> center - 1;
            };
            int z = switch (ring) {
                case 0 -> center - 1;
                case 1 -> center;
                case 2 -> center + 1;
                default -> center;
            };
            String facing = switch (ring) {
                case 0 -> "south";
                case 1 -> "west";
                case 2 -> "north";
                default -> "east";
            };
            builder.block(x, y - 1, z, "minecraft:deepslate_brick_stairs", properties("facing", facing, "half", "bottom", "shape", "straight", "waterlogged", "false"));
        }

        builder.block(center, surfaceY + 1, center, "minecraft:chiseled_deepslate");
        builder.startJigsaw(center, surfaceY + 2, center);
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

    private static int decorativeEntranceDepth(DungeonTier tier) {
        return Math.min(12 + tier.tier * 2, MAX_DECORATIVE_ENTRANCE_DEPTH);
    }

    private static void buildRoughFloor(StructureBuilder builder, int minX, int maxX, int y, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                builder.block(x, y, z, pickStone(x * 31 + z));
            }
        }
    }

    private static void buildLowRuinWall(StructureBuilder builder, int x, int y, int z, int height) {
        for (int offset = 0; offset < height; offset++) {
            builder.block(x, y + offset, z, pickStone(x * 17 + y * 7 + z + offset));
        }
    }

    private static String pickStone(int value) {
        return switch (Math.floorMod(value, 5)) {
            case 0 -> "minecraft:mossy_cobblestone";
            case 1 -> "minecraft:cracked_stone_bricks";
            case 2 -> "minecraft:stone_bricks";
            default -> "minecraft:cobblestone";
        };
    }

    private static Map<String, String> wall() {
        return properties("east", "none", "north", "none", "south", "none", "up", "true", "waterlogged", "false", "west", "none");
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
