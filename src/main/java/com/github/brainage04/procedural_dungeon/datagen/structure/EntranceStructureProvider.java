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
    private static final int MAX_DECORATIVE_ENTRANCE_DEPTH = 22;

    private final PackOutput.PathProvider structureResolver;

    public EntranceStructureProvider(FabricPackOutput output) {
        this.structureResolver = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (DungeonTier tier : DungeonTier.values()) {
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

    private static CompoundTag ruinedArchway(DungeonTier tier) {
        int depth = decorativeEntranceDepth(tier);
        int surfaceY = depth;
        int size = entranceFootprint(tier, 13);
        StructureBuilder builder = new StructureBuilder(size, depth + 10, size);
        int center = size / 2;
        int radius = surfaceRadius(tier);

        carveVerticalShaft(builder, center, center, surfaceY, 2);
        buildBrokenSurface(builder, center - radius, center + radius, surfaceY, center - radius, center + radius, tier.tier * 13);

        int westX = center - radius + 2;
        int eastX = center + radius - 1;
        int northZ = center - radius + 2;
        int southZ = center + radius - 2;
        for (int z = northZ; z <= southZ; z++) {
            if (z != center + 1) {
                buildLowRuinWall(builder, westX, surfaceY + 1, z, unevenHeight(tier, z, 2));
            }
            if (z % 3 != 0) {
                buildLowRuinWall(builder, eastX, surfaceY + 1, z, unevenHeight(tier, z + 3, 1));
            }
        }

        for (int y = surfaceY + 1; y <= surfaceY + 4 + tier.tier / 2; y++) {
            builder.block(westX, y, northZ, "minecraft:stone_bricks");
            if (y < surfaceY + 4 + tier.tier / 2) {
                builder.block(eastX, y, northZ + 1, pickStone(y + tier.tier));
            }
            builder.block(westX + 1, y, southZ, pickStone(y + 7));
            if (tier.tier >= 3 || y <= surfaceY + 3) {
                builder.block(eastX, y, southZ - 1, "minecraft:mossy_cobblestone");
            }
        }
        for (int x = westX + 1; x <= eastX - 1; x++) {
            if (x != center + tier.tier % 2) {
                builder.block(x, surfaceY + 5 + tier.tier / 2, northZ, pickStone(x + tier.tier));
            }
        }
        if (tier.tier >= 4) {
            for (int x = center - 2; x <= center + 3; x++) {
                builder.block(x, surfaceY + 4, southZ, "minecraft:stone_brick_slab", properties("type", "top", "waterlogged", "false"));
            }
        }

        scatterRubble(builder, center, surfaceY + 1, center, radius, tier.tier, 11);
        placeTierSkulls(builder, center - radius + 2, surfaceY + 1, center + radius - 2, tier, "east");
        builder.block(center - radius + 3, surfaceY + 1, center - radius + 4, "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));
        if (tier.tier >= 3) {
            builder.block(center + radius - 4, surfaceY + 1, center + 2, "minecraft:soul_lantern", properties("hanging", "false", "waterlogged", "false"));
        }

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
        int size = entranceFootprint(tier, 15);
        StructureBuilder builder = new StructureBuilder(size, depth + 9, size);
        int center = size / 2;
        int radius = surfaceRadius(tier) + 1;

        carveVerticalShaft(builder, center, center, surfaceY, 2);
        buildBrokenSurface(builder, center - radius, center + radius, surfaceY, center - radius, center + radius, tier.tier * 19);

        int min = center - radius;
        int max = center + radius;
        for (int x = min + 1; x <= max - 1; x++) {
            if (x % 4 != tier.tier % 3) {
                buildLowRuinWall(builder, x, surfaceY + 1, min + 1, unevenHeight(tier, x, 1));
            }
            if (x % 5 != 1) {
                buildLowRuinWall(builder, x, surfaceY + 1, max - 2, unevenHeight(tier, x + 4, 1));
            }
        }
        for (int z = min + 2; z <= max - 3; z++) {
            if (z % 4 != 0) {
                buildLowRuinWall(builder, min + 2, surfaceY + 1, z, unevenHeight(tier, z + 9, 1));
            }
            if (z % 5 != 2 || tier.tier >= 4) {
                buildLowRuinWall(builder, max - 1, surfaceY + 1, z, unevenHeight(tier, z + 2, 1));
            }
        }

        int ring = 2 + tier.tier / 2;
        for (int x = center - ring; x <= center + ring; x++) {
            builder.block(x, surfaceY + 1, center - ring, "minecraft:stone_brick_slab", properties("type", "bottom", "waterlogged", "false"));
            if (x != center + ring - 1) {
                builder.block(x, surfaceY + 1, center + ring, "minecraft:stone_brick_slab", properties("type", "bottom", "waterlogged", "false"));
            }
        }
        for (int z = center - ring + 1; z <= center + ring - 1; z++) {
            builder.block(center - ring, surfaceY + 1, z, "minecraft:stone_brick_slab", properties("type", "bottom", "waterlogged", "false"));
            if (z != center - 1) {
                builder.block(center + ring, surfaceY + 1, z, "minecraft:stone_brick_slab", properties("type", "bottom", "waterlogged", "false"));
            }
        }

        builder.block(center, surfaceY - 1, center, "minecraft:water", properties("level", "0"));
        builder.block(center + 1, surfaceY - 1, center, "minecraft:water", properties("level", "0"));
        builder.block(center - 1, surfaceY + 1, center + 2, "minecraft:stone_bricks");
        builder.block(min + 3, surfaceY + 1, min + 4, "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));
        builder.block(max - 4, surfaceY + 1, center + 3, tier.tier >= 4 ? "minecraft:soul_lantern" : "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));
        scatterRubble(builder, center, surfaceY + 1, center, radius, tier.tier + 1, 23);
        placeTierSkulls(builder, center - tier.tier, surfaceY + 1, min + 3, tier, "south");

        builder.startJigsaw(center, surfaceY + 1, center);
        builder.dungeonJigsaw(center, 0, center);
        return builder.build();
    }

    private static CompoundTag ritualDescent(DungeonTier tier) {
        int depth = decorativeEntranceDepth(tier);
        int surfaceY = depth;
        int size = entranceFootprint(tier, 15);
        StructureBuilder builder = new StructureBuilder(size, depth + 10, size);
        int center = size / 2;
        int radius = surfaceRadius(tier) + 1;

        carveVerticalShaft(builder, center, center, surfaceY, 2);
        buildBrokenSurface(builder, center - radius, center + radius, surfaceY, center - radius, center + radius, tier.tier * 29);

        int inner = 3 + tier.tier / 2;
        int outer = Math.min(radius - 2, inner + 2 + tier.tier / 2);
        for (int offset = -outer; offset <= outer; offset++) {
            if (Math.abs(offset) > 1 || tier.tier >= 3) {
                builder.block(center + offset, surfaceY + 1, center - outer, "minecraft:cobbled_deepslate_wall", wall());
            }
            if (offset != -outer + 1) {
                builder.block(center + offset, surfaceY + 1, center + outer, "minecraft:cobbled_deepslate_wall", wall());
            }
            if (offset % 3 != 0) {
                builder.block(center - outer, surfaceY + 1, center + offset, "minecraft:cobbled_deepslate_wall", wall());
            }
            if (offset != outer - 2) {
                builder.block(center + outer, surfaceY + 1, center + offset, "minecraft:cobbled_deepslate_wall", wall());
            }
        }
        for (int[] pillar : List.of(
                new int[]{center - outer, center - outer},
                new int[]{center - outer + 1, center + outer},
                new int[]{center + outer, center - outer + 2},
                new int[]{center + outer - 1, center + outer - 1}
        )) {
            for (int y = surfaceY + 1; y <= surfaceY + 3 + tier.tier / 2; y++) {
                builder.block(pillar[0], y, pillar[1], y == surfaceY + 4 ? "minecraft:chiseled_stone_bricks" : "minecraft:deepslate_bricks");
            }
            builder.block(pillar[0], surfaceY + 4 + tier.tier / 2, pillar[1], "minecraft:soul_lantern", properties("hanging", "false", "waterlogged", "false"));
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
        for (int offset = -inner; offset <= inner; offset++) {
            if (Math.abs(offset) == inner || Math.floorMod(offset + tier.tier, 3) == 0) {
                builder.block(center + offset, surfaceY + 1, center - inner, "minecraft:deepslate_tile_slab", properties("type", "bottom", "waterlogged", "false"));
                builder.block(center - inner, surfaceY + 1, center + offset, "minecraft:deepslate_tile_slab", properties("type", "bottom", "waterlogged", "false"));
            }
        }
        scatterRubble(builder, center, surfaceY + 1, center, radius, tier.tier + 2, 37);
        placeTierSkulls(builder, center + inner + 1, surfaceY + 1, center - inner, tier, "west");
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

    private static int decorativeEntranceDepth(DungeonTier tier) {
        return Math.min(12 + tier.tier * 2, MAX_DECORATIVE_ENTRANCE_DEPTH);
    }

    private static int entranceFootprint(DungeonTier tier, int baseSize) {
        return Math.min(baseSize + tier.tier * 4, MAX_STRUCTURE_BLOCK_AXIS);
    }

    private static int surfaceRadius(DungeonTier tier) {
        return 4 + tier.tier * 2;
    }

    private static int unevenHeight(DungeonTier tier, int seed, int minimum) {
        return minimum + Math.floorMod(seed * 7 + tier.tier * 3, 2 + tier.tier / 2);
    }

    private static void buildBrokenSurface(StructureBuilder builder, int minX, int maxX, int y, int minZ, int maxZ, int seed) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int edgeDistance = Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
                int noise = Math.floorMod(x * 31 + z * 17 + seed, 11);
                if (edgeDistance > 0 || noise < 7) {
                    builder.block(x, y, z, pickStone(x * 31 + z + seed));
                }
                if (edgeDistance == 1 && noise < 2) {
                    builder.block(x, y + 1, z, "minecraft:stone_brick_slab", properties("type", "bottom", "waterlogged", "false"));
                }
            }
        }
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

    private static void scatterRubble(StructureBuilder builder, int centerX, int y, int centerZ, int radius, int tier, int seed) {
        int count = 8 + tier * 8;
        for (int i = 0; i < count; i++) {
            int x = centerX - radius + 1 + Math.floorMod(seed + i * 5 + tier * 3, Math.max(1, radius * 2 - 2));
            int z = centerZ - radius + 1 + Math.floorMod(seed * 2 + i * 7 + tier, Math.max(1, radius * 2 - 2));
            if (Math.abs(x - centerX) <= 2 && Math.abs(z - centerZ) <= 2) {
                continue;
            }

            int noise = Math.floorMod(x * 19 + z * 23 + seed + i, 10);
            if (noise < 5) {
                builder.block(x, y, z, "minecraft:cobblestone_slab", properties("type", noise < 2 ? "top" : "bottom", "waterlogged", "false"));
            } else if (noise < 8) {
                builder.block(x, y, z, pickStone(noise + x + z));
            } else {
                builder.block(x, y, z, "minecraft:cobblestone_wall", wall());
            }
        }
    }

    private static void placeTierSkulls(StructureBuilder builder, int startX, int y, int startZ, DungeonTier tier, String direction) {
        int dx = switch (direction) {
            case "east" -> 1;
            case "west" -> -1;
            default -> 0;
        };
        int dz = switch (direction) {
            case "south" -> 1;
            case "north" -> -1;
            default -> 0;
        };
        int rotation = switch (direction) {
            case "east" -> 4;
            case "south" -> 8;
            case "west" -> 12;
            default -> 0;
        };

        for (int i = 0; i < tier.tier; i++) {
            builder.block(
                    startX + dx * i,
                    y,
                    startZ + dz * i,
                    "minecraft:skeleton_skull",
                    properties("rotation", Integer.toString(Math.floorMod(rotation + i, 16)))
            );
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
