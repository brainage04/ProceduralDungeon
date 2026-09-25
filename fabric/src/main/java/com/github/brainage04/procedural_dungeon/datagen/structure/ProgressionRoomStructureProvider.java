package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.guardian.DungeonGuardian;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonProgressionRooms;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import static com.github.brainage04.procedural_dungeon.datagen.structure.StructureBuilder.properties;

/**
 * Generates the boss room, boss key vault, and miniboss room templates. Each room is a dead end entered through one
 * "minecraft:room" jigsaw on its north wall: miniboss rooms join the ordinary room pool, while the layout compiler
 * attaches the boss room and vault to free room sockets or hallway ends.
 */
public class ProgressionRoomStructureProvider implements DataProvider {
    private static final String WALL = "minecraft:cobblestone";

    private final PackOutput.PathProvider structureResolver;

    public ProgressionRoomStructureProvider(FabricPackOutput output) {
        this.structureResolver = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        return CompletableFuture.allOf(
                save(writer, DungeonProgressionRooms.BOSS_ROOM, bossRoom()),
                save(writer, DungeonProgressionRooms.BOSS_KEY_VAULT, bossKeyVault()),
                save(writer, DungeonProgressionRooms.MINIBOSS_ROOM, minibossRoom())
        );
    }

    private CompletableFuture<?> save(CachedOutput writer, Identifier template, CompoundTag nbt) {
        return StructureBuilder.save(writer, nbt, structureResolver.file(template, "nbt"));
    }

    private static CompoundTag bossRoom() {
        StructureBuilder builder = shell(13, 9, 15);
        for (int x = 1; x <= 11; x++) {
            for (int z = 1; z <= 13; z++) {
                builder.block(x, 0, z, (x + z) % 2 == 0 ? "minecraft:stone_bricks" : WALL);
            }
        }
        builder.block(6, 0, 7, "minecraft:chiseled_stone_bricks");
        for (int[] pillar : List.of(new int[]{2, 3}, new int[]{10, 3}, new int[]{2, 10}, new int[]{10, 10})) {
            builder.fill(pillar[0], 1, pillar[1], pillar[0], 7, pillar[1], "minecraft:stone_bricks");
            builder.block(pillar[0], 1, pillar[1], "minecraft:chiseled_stone_bricks");
        }
        for (int[] lantern : List.of(new int[]{6, 4}, new int[]{6, 10}, new int[]{3, 7}, new int[]{9, 7})) {
            hangingLantern(builder, lantern[0], 7, lantern[1], "minecraft:soul_lantern");
        }

        builder.fill(4, 1, 12, 8, 1, 13, "minecraft:chiseled_stone_bricks");
        chest(builder, 5, 2, 13, "boss_room");
        builder.block(6, 2, 13, "minecraft:gold_block");
        chest(builder, 7, 2, 13, "boss_room");

        builder.dataMarker(6, 1, 7, DungeonGuardian.BOSS.marker());
        guardedEntrance(builder, 6);
        return builder.build();
    }

    private static CompoundTag bossKeyVault() {
        StructureBuilder builder = shell(7, 7, 7);
        builder.fill(2, 1, 0, 4, 3, 0, "minecraft:air");
        builder.block(3, 1, 4, "minecraft:chiseled_stone_bricks");
        chest(builder, 3, 2, 4, "boss_key_vault");
        builder.block(1, 1, 4, "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));
        builder.block(5, 1, 4, "minecraft:lantern", properties("hanging", "false", "waterlogged", "false"));
        hangingLantern(builder, 3, 5, 2, "minecraft:lantern");
        entranceJigsaw(builder, 3);
        return builder.build();
    }

    private static CompoundTag minibossRoom() {
        StructureBuilder builder = shell(11, 7, 13);
        for (int[] pillar : List.of(new int[]{2, 3}, new int[]{8, 3}, new int[]{2, 9}, new int[]{8, 9})) {
            builder.fill(pillar[0], 1, pillar[1], pillar[0], 5, pillar[1], "minecraft:stone_bricks");
        }
        hangingLantern(builder, 5, 5, 4, "minecraft:lantern");
        hangingLantern(builder, 5, 5, 9, "minecraft:lantern");
        chest(builder, 5, 1, 11, "miniboss_room");
        builder.dataMarker(5, 1, 7, DungeonGuardian.MINIBOSS.marker());
        guardedEntrance(builder, 5);
        return builder.build();
    }

    /**
     * A closed room: solid walls, floor, and ceiling around an air interior.
     */
    private static StructureBuilder shell(int xSize, int ySize, int zSize) {
        StructureBuilder builder = new StructureBuilder(xSize, ySize, zSize);
        builder.fill(0, 0, 0, xSize - 1, ySize - 1, zSize - 1, WALL);
        builder.fill(1, 1, 1, xSize - 2, ySize - 2, zSize - 2, "minecraft:air");
        return builder;
    }

    /**
     * Fills the 3x3 doorway above the entrance jigsaw with a reinforced deepslate frame around a locked iron door.
     */
    private static void guardedEntrance(StructureBuilder builder, int centerX) {
        builder.fill(centerX - 1, 1, 0, centerX - 1, 3, 0, "minecraft:reinforced_deepslate");
        builder.fill(centerX + 1, 1, 0, centerX + 1, 3, 0, "minecraft:reinforced_deepslate");
        builder.block(centerX, 3, 0, "minecraft:reinforced_deepslate");
        builder.block(centerX, 1, 0, "minecraft:iron_door", door("lower"));
        builder.block(centerX, 2, 0, "minecraft:iron_door", door("upper"));
        entranceJigsaw(builder, centerX);
    }

    private static void entranceJigsaw(StructureBuilder builder, int centerX) {
        builder.jigsaw(centerX, 0, 0, "north_up", "minecraft:room", "minecraft:empty", "minecraft:empty", WALL, "rollable");
    }

    private static void chest(StructureBuilder builder, int x, int y, int z, String lootTable) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("LootTable", "procedural_dungeon:" + lootTable);
        nbt.put("components", new CompoundTag());
        nbt.putString("id", "minecraft:chest");
        builder.blockEntity(x, y, z, "minecraft:chest", properties("facing", "north", "type", "single", "waterlogged", "false"), nbt);
    }

    private static void hangingLantern(StructureBuilder builder, int x, int y, int z, String lantern) {
        builder.block(x, y, z, lantern, properties("hanging", "true", "waterlogged", "false"));
    }

    private static Map<String, String> door(String half) {
        return properties("facing", "south", "half", half, "hinge", "left", "open", "false", "powered", "false");
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Progression Rooms";
    }
}
