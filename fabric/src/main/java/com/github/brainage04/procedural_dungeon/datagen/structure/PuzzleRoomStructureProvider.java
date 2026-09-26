package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonPuzzleRooms;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonPuzzleRooms.Puzzle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import static com.github.brainage04.procedural_dungeon.datagen.structure.StructureBuilder.properties;

/**
 * Generates the puzzle room templates. Every room shares one shell (11 x 9 x 17):
 * <ul>
 *     <li>a room (x 1-9, y 2-7, z 1-10) entered through a doorway in the north wall, with its floor at y 1;</li>
 *     <li>the puzzle wall at z 11, of reinforced deepslate, holding the reward chest in a niche at (9, 2, 11);</li>
 *     <li>a sealed machine (x 1-9, y 1-7, z 12-15) of glass, behind reinforced deepslate, that checks the puzzle.</li>
 * </ul>
 * The machine is an AND gate over lanes. A lane is the column {@code x} of the machine; its input powers the block at
 * (x, 3, 14) when the lane is satisfied. An "on" lane hangs a torch on that block, so the torch lights while the lane is
 * unsatisfied; an "off" lane feeds its block straight into the gate, so it counts as unsatisfied while powered. The
 * torches and off lanes join on a dust line at y 2, z 15, which powers the block under an output torch; the output
 * torch lights, and the final repeater under the chest powers, only while every lane is satisfied.
 */
public class PuzzleRoomStructureProvider implements DataProvider {
    private static final String WALL = "minecraft:cobblestone";
    private static final String AIR = "minecraft:cave_air";
    private static final String FACE = "minecraft:reinforced_deepslate";
    private static final String FILLER = "minecraft:glass";
    private static final String SUPPORT = "minecraft:smooth_stone";
    private static final String DUST = "minecraft:redstone_wire";

    static final int X_SIZE = 11;
    static final int Y_SIZE = 9;
    static final int Z_SIZE = 17;

    private final PackOutput.PathProvider structureResolver;

    public PuzzleRoomStructureProvider(FabricPackOutput output) {
        this.structureResolver = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Puzzle puzzle : Puzzle.values()) {
            futures.add(StructureBuilder.save(writer, build(puzzle), structureResolver.file(puzzle.template(), "nbt")));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    static CompoundTag build(Puzzle puzzle) {
        StructureBuilder builder = shell();
        switch (puzzle) {
            case FRAMES -> frames(builder);
            case BOOKSHELVES -> bookshelves(builder);
            case TARGETS -> targets(builder);
            case CHORD -> chord(builder);
            case SLUICE -> sluice(builder);
            case CRAFTER -> crafter(builder);
            case STEALTH -> stealth(builder);
            case PARKOUR -> parkour(builder);
        }
        return builder.build();
    }

    /**
     * Three arrows in frames; each must point straight up (rotation 7, a comparator signal of 8).
     */
    private static void frames(StructureBuilder builder) {
        int[] start = {2, 5, 0};
        int[] lanes = {2, 5, 8};
        for (int i = 0; i < lanes.length; i++) {
            int x = lanes[i];
            builder.dataMarker(x, 3, 10, DungeonPuzzleRooms.FRAME_MARKER + start[i]);
            exactSignalLane(builder, x, 7);
            onLane(builder, x);
        }
        andGate(builder);
        sign(builder, 5, 5, "Aim every", "arrow at", "the sky.");
    }

    /**
     * Three chiseled bookshelves, each missing its last volume; the last book touched on each must be the bottom-right
     * one (slot 6, the highest comparator signal a shelf gives).
     */
    private static void bookshelves(StructureBuilder builder) {
        for (int x : new int[]{2, 5, 8}) {
            CompoundTag nbt = new CompoundTag();
            ListTag items = new ListTag();
            for (int slot = 0; slot < 5; slot++) {
                items.add(item(slot, "minecraft:book", 1));
            }
            nbt.put("Items", items);
            nbt.putInt("last_interacted_slot", 0);
            nbt.putString("id", "minecraft:chiseled_bookshelf");
            builder.blockEntity(x, 3, 11, "minecraft:chiseled_bookshelf", properties(
                    "facing", "north",
                    "slot_0_occupied", "true", "slot_1_occupied", "true", "slot_2_occupied", "true",
                    "slot_3_occupied", "true", "slot_4_occupied", "true", "slot_5_occupied", "false"
            ), nbt);
            exactSignalLane(builder, x, 5);
            onLane(builder, x);
        }
        andGate(builder);
        sign(builder, 5, 5, "Every shelf", "ends with its", "last volume.");
    }

    /**
     * Three targets, each toggling the copper bulb above it; every bulb must be lit.
     */
    private static void targets(StructureBuilder builder) {
        for (int x : new int[]{2, 5, 8}) {
            builder.block(x, 3, 11, "minecraft:target", properties("power", "0"));
            builder.block(x, 4, 11, "minecraft:waxed_copper_bulb", properties("lit", "false", "powered", "false"));
            comparator(builder, x, 4, 12, "north", "compare");
            builder.block(x, 4, 13, DUST);
            builder.block(x, 4, 14, DUST);
            onLane(builder, x);
        }
        andGate(builder);
        sign(builder, 5, 5, "Light every", "lamp once.");
    }

    /**
     * Five levers, each ringing a note block; only the first, third and fourth may stay on.
     */
    private static void chord(StructureBuilder builder) {
        int[] notes = {6, 8, 10, 11, 13};
        for (int i = 0; i < 5; i++) {
            int x = 3 + i;
            builder.block(x, 3, 10, "minecraft:lever", properties("face", "wall", "facing", "north", "powered", "false"));
            builder.block(x, 3, 11, "minecraft:clay");
            builder.block(x, 4, 11, "minecraft:note_block", properties("instrument", "flute", "note", Integer.toString(notes[i]), "powered", "false"));
            repeater(builder, x, 3, 12, "north");
            repeater(builder, x, 3, 13, "north");
            if (i == 1 || i == 4) {
                offLane(builder, x);
            } else {
                onLane(builder, x);
            }
        }
        andGate(builder);
        sign(builder, 5, 5, "Silence the", "second and", "the last.");
    }

    /**
     * A stream that must reach and wash away the torch at the end of its channel: both sluice gates open, the drain
     * shut. The levers start on, so every gate is closed and the drain is covered.
     */
    private static void sluice(StructureBuilder builder) {
        // Raised trough (z 1-5, water at y 3) that drops into a floor channel (z 6-10, water at y 2). The channel is
        // smooth stone, which neither decays nor takes a theme's blocks, so no gap can open and divert the stream.
        builder.fill(1, 1, 1, 3, 1, 10, SUPPORT);
        builder.fill(1, 2, 1, 3, 3, 5, SUPPORT);
        builder.block(2, 3, 2, "minecraft:water", properties("level", "0"));
        builder.fill(2, 3, 3, 2, 3, 5, AIR);
        builder.block(2, 3, 6, AIR);
        builder.fill(1, 2, 6, 3, 2, 10, SUPPORT);
        builder.fill(2, 2, 6, 2, 2, 10, AIR);

        gate(builder, 3, 3, 4);
        gate(builder, 3, 2, 9);
        // The drain: a gap in the channel wall over a pit, covered by the head of a piston.
        builder.block(3, 1, 7, AIR);
        gate(builder, 4, 2, 7);

        // The torch at the channel end powers the face block above it while it stands.
        builder.block(2, 2, 11, "minecraft:redstone_torch", properties("lit", "true"));
        repeater(builder, 2, 3, 12, "north");
        repeater(builder, 2, 3, 13, "north");
        offLane(builder, 2);
        andGate(builder);
        sign(builder, 5, 5, "Open the", "sluices, but", "spare the drain.");
    }

    /**
     * A piston at {@code pistonX} whose head closes the cell to its west while the lever on the block to its east is on.
     * The piston is not sticky, so retracting leaves the cell open instead of pulling a wall block into it.
     */
    private static void gate(StructureBuilder builder, int pistonX, int y, int z) {
        builder.block(pistonX, y, z, "minecraft:piston", properties("facing", "west", "extended", "true"));
        builder.block(pistonX - 1, y, z, "minecraft:piston_head", properties("facing", "west", "short", "false", "type", "normal"));
        builder.block(pistonX + 1, y, z, SUPPORT);
        builder.block(pistonX + 1, y + 1, z, "minecraft:lever", properties("face", "floor", "facing", "north", "powered", "true"));
    }

    /**
     * A crafter wired to a jukebox: craft Music Disc 5 from the nine fragments in the barrel, and the disc plays.
     */
    private static void crafter(StructureBuilder builder) {
        CompoundTag crafter = new CompoundTag();
        crafter.putString("id", "minecraft:crafter");
        builder.blockEntity(5, 3, 11, "minecraft:crafter", properties("orientation", "south_up", "crafting", "false", "triggered", "false"), crafter);
        builder.block(5, 3, 10, "minecraft:stone_button", properties("face", "wall", "facing", "north", "powered", "false"));
        CompoundTag jukebox = new CompoundTag();
        jukebox.putString("id", "minecraft:jukebox");
        builder.blockEntity(5, 3, 12, "minecraft:jukebox", properties("has_record", "false"), jukebox);
        comparator(builder, 5, 3, 13, "north", "compare");
        onLane(builder, 5);
        andGate(builder);

        CompoundTag barrel = new CompoundTag();
        ListTag items = new ListTag();
        items.add(item(0, "minecraft:disc_fragment_5", 9));
        barrel.put("Items", items);
        barrel.putString("id", "minecraft:barrel");
        builder.blockEntity(3, 2, 9, "minecraft:barrel", properties("facing", "up", "open", "false"), barrel);
        sign(builder, 5, 5, "Mend the", "broken song,", "then press.");
    }

    /**
     * Sculk sensors across the floor and shriekers that can summon the Warden. The chest is unguarded by redstone:
     * reaching it quietly is the puzzle.
     */
    private static void stealth(StructureBuilder builder) {
        builder.fill(1, 1, 2, 9, 1, 9, "minecraft:sculk");
        for (int x = 2; x <= 8; x += 2) {
            for (int z = 3; z <= 7; z += 2) {
                builder.block(x, 1, z, "minecraft:sculk_sensor", properties("sculk_sensor_phase", "inactive", "power", "0", "waterlogged", "false"));
            }
        }
        builder.block(1, 1, 9, "minecraft:sculk_shrieker", properties("can_summon", "true", "shrieking", "false", "waterlogged", "false"));
        builder.block(9, 1, 9, "minecraft:sculk_shrieker", properties("can_summon", "true", "shrieking", "false", "waterlogged", "false"));
        sign(builder, 5, 5, "Tread softly.");
    }

    /**
     * A lava pit crossed on stepping stones and a slime block. The stones are smooth stone, which never decays.
     */
    private static void parkour(StructureBuilder builder) {
        builder.fill(1, 1, 3, 9, 1, 9, "minecraft:lava");
        builder.block(5, 1, 4, SUPPORT);
        builder.block(7, 1, 6, SUPPORT);
        builder.block(5, 1, 8, "minecraft:slime_block");
        sign(builder, 5, 5, "Mind the gap.");
    }

    private static StructureBuilder shell() {
        StructureBuilder builder = new StructureBuilder(X_SIZE, Y_SIZE, Z_SIZE);
        builder.fill(0, 0, 0, X_SIZE - 1, Y_SIZE - 1, 10, WALL);
        builder.fill(1, 2, 1, 9, 7, 10, AIR);
        builder.fill(4, 2, 0, 6, 4, 0, AIR);
        builder.jigsaw(5, 1, 0, "north_up", "minecraft:room", "minecraft:empty", "minecraft:empty", WALL, "rollable");

        builder.fill(0, 0, 11, X_SIZE - 1, Y_SIZE - 1, Z_SIZE - 1, FACE);
        builder.fill(1, 1, 12, 9, 7, 15, FILLER);

        CompoundTag chest = new CompoundTag();
        chest.putString("LootTable", "procedural_dungeon:" + DungeonPuzzleRooms.LOOT_TABLE);
        chest.put("components", new CompoundTag());
        chest.putString("id", "minecraft:chest");
        builder.blockEntity(9, 2, 11, "minecraft:chest", properties("facing", "north", "type", "single", "waterlogged", "false"), chest);
        builder.block(9, 3, 11, AIR);

        builder.block(3, 7, 5, "minecraft:lantern", properties("hanging", "true", "waterlogged", "false"));
        builder.block(7, 7, 5, "minecraft:lantern", properties("hanging", "true", "waterlogged", "false"));
        return builder;
    }

    /**
     * Lane input that is satisfied only while the comparator reading the face block at x reaches {@code threshold + 1}:
     * a subtracting comparator whose side input is a comparator reading a barrel filled to give {@code threshold}.
     * Uses columns x to x + 2 of the machine's first row.
     */
    private static void exactSignalLane(StructureBuilder builder, int x, int threshold) {
        comparator(builder, x, 3, 12, "north", "subtract");
        comparator(builder, x + 1, 3, 12, "east", "compare");
        // A container gives floor(1 + 14 * fullness); a barrel's 27 slots fill in whole stacks.
        int stacks = (int) Math.ceil((threshold - 1) * 27 / 14.0);
        CompoundTag barrel = new CompoundTag();
        ListTag items = new ListTag();
        for (int slot = 0; slot < stacks; slot++) {
            items.add(item(slot, "minecraft:cobblestone", 64));
        }
        barrel.put("Items", items);
        barrel.putString("id", "minecraft:barrel");
        builder.blockEntity(x + 2, 3, 12, "minecraft:barrel", properties("facing", "up", "open", "false"), barrel);
        repeater(builder, x, 3, 13, "north");
    }

    private static void onLane(StructureBuilder builder, int x) {
        builder.block(x, 3, 14, SUPPORT);
        builder.block(x, 3, 15, "minecraft:redstone_wall_torch", properties("facing", "south", "lit", "true"));
    }

    private static void offLane(StructureBuilder builder, int x) {
        builder.block(x, 3, 14, SUPPORT);
        builder.block(x, 2, 14, DUST);
    }

    private static void andGate(StructureBuilder builder) {
        builder.fill(2, 2, 15, 9, 2, 15, DUST);
        builder.block(9, 1, 14, SUPPORT);
        builder.block(9, 2, 14, DUST);
        builder.block(9, 1, 13, "minecraft:redstone_wall_torch", properties("facing", "north", "lit", "false"));
        repeater(builder, 9, 1, 12, "south");
    }

    /**
     * A repeater whose facing points at its input.
     */
    private static void repeater(StructureBuilder builder, int x, int y, int z, String facing) {
        builder.block(x, y, z, "minecraft:repeater", properties("delay", "1", "facing", facing, "locked", "false", "powered", "false"));
    }

    /**
     * A comparator whose facing points at its rear input.
     */
    private static void comparator(StructureBuilder builder, int x, int y, int z, String facing, String mode) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:comparator");
        nbt.putInt("OutputSignal", 0);
        builder.blockEntity(x, y, z, "minecraft:comparator", properties("facing", facing, "mode", mode, "powered", "false"), nbt);
    }

    /**
     * A waxed sign on the puzzle wall, facing into the room.
     */
    private static void sign(StructureBuilder builder, int x, int y, String... lines) {
        ListTag messages = new ListTag();
        for (int i = 0; i < 4; i++) {
            messages.add(StringTag.valueOf(i < lines.length ? lines[i] : ""));
        }
        CompoundTag front = new CompoundTag();
        front.put("messages", messages);
        front.putString("color", "black");
        front.putBoolean("has_glowing_text", false);
        CompoundTag back = front.copy();
        ListTag blank = new ListTag();
        for (int i = 0; i < 4; i++) {
            blank.add(StringTag.valueOf(""));
        }
        back.put("messages", blank);
        CompoundTag nbt = new CompoundTag();
        nbt.put("front_text", front);
        nbt.put("back_text", back);
        nbt.putBoolean("is_waxed", true);
        nbt.putString("id", "minecraft:sign");
        builder.blockEntity(x, y, 10, "minecraft:oak_wall_sign", properties("facing", "north", "waterlogged", "false"), nbt);
    }

    private static CompoundTag item(int slot, String id, int count) {
        CompoundTag item = new CompoundTag();
        item.putByte("Slot", (byte) slot);
        item.putString("id", id);
        item.putInt("count", count);
        return item;
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Puzzle Rooms";
    }
}
