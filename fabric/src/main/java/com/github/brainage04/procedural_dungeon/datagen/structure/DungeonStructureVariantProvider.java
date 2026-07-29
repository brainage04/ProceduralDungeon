package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.google.common.hash.Hashing;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class DungeonStructureVariantProvider implements DataProvider {
    private static final List<String> SMITH_ROOMS = List.of("armorsmith", "toolsmith", "weaponsmith");

    private final PackOutput.PathProvider structureResolver;

    public DungeonStructureVariantProvider(FabricPackOutput output) {
        this.structureResolver = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (String room : SMITH_ROOMS) {
            for (DungeonTier tier : DungeonTier.values()) {
                CompoundTag nbt = readStructure("dungeon/hallway/room/%s".formatted(room));
                equipArmorStands(nbt, room, tier);
                futures.add(saveNbt(writer, nbt, structurePath("dungeon/hallway/room/%s/tier_%d".formatted(room, tier.tier))));
            }
        }

        futures.add(saveNbt(
                writer,
                staircaseUpVariant(readStructure("dungeon/hallway/room/staircase_diagonal_down")),
                structurePath("dungeon/hallway/room/staircase_diagonal_up")
        ));
        futures.add(saveNbt(
                writer,
                staircaseUpVariant(readStructure("dungeon/hallway/room/staircase_spiral_down")),
                structurePath("dungeon/hallway/room/staircase_spiral_up")
        ));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private Path structurePath(String path) {
        return structureResolver.file(ProceduralDungeon.of(path), "nbt");
    }

    private static CompoundTag readStructure(String path) {
        String resource = "data/procedural_dungeon/structure/%s.nbt".formatted(path);
        InputStream input = DungeonStructureVariantProvider.class.getClassLoader().getResourceAsStream(resource);
        if (input == null) {
            throw new IllegalStateException("Missing source structure resource: " + resource);
        }

        try (input) {
            return NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read source structure: " + resource, e);
        }
    }

    private static void equipArmorStands(CompoundTag structure, String room, DungeonTier tier) {
        ListTag entities = structure.getListOrEmpty("entities");
        int armorStand = 0;
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entityInfo = entities.getCompoundOrEmpty(i);
            CompoundTag entity = entityInfo.getCompoundOrEmpty("nbt");
            if (!entity.getStringOr("id", "").equals("minecraft:armor_stand")) {
                continue;
            }

            Random random = new Random(31L * room.hashCode() + 17L * tier.tier + armorStand);
            entity.remove("UUID");
            entity.remove("ArmorItems");
            entity.remove("HandItems");
            entity.put("equipment", equipment(room, tier, random));
            if (!room.equals("armorsmith")) {
                entity.putByte("ShowArms", (byte) 1);
            }
            armorStand++;
        }
    }

    private static CompoundTag equipment(String room, DungeonTier tier, Random random) {
        CompoundTag equipment = new CompoundTag();
        if (room.equals("armorsmith")) {
            List<String> slots = new ArrayList<>(List.of("feet", "legs", "chest", "head"));
            int itemCount = 1 + random.nextInt(2);
            for (int i = 0; i < itemCount; i++) {
                String slot = slots.remove(random.nextInt(slots.size()));
                putStack(equipment, slot, armorItem(slot, tier, random));
            }
        } else if (room.equals("toolsmith")) {
            List<String> toolSlots = new ArrayList<>(List.of("axe", "shovel", "pickaxe", "hoe"));
            putStack(equipment, "mainhand", toolItem(toolSlots.remove(random.nextInt(toolSlots.size())), tier, random));
            if (random.nextBoolean()) {
                putStack(equipment, "offhand", toolItem(toolSlots.remove(random.nextInt(toolSlots.size())), tier, random));
            }
        } else if (room.equals("weaponsmith")) {
            List<String> weaponSlots = new ArrayList<>(List.of("sword", "axe"));
            putStack(equipment, "mainhand", weaponItem(weaponSlots.remove(random.nextInt(weaponSlots.size())), tier, random));
            if (random.nextBoolean()) {
                putStack(equipment, "offhand", weaponItem(weaponSlots.remove(random.nextInt(weaponSlots.size())), tier, random));
            }
        }
        return equipment;
    }

    private static Item armorItem(String slot, DungeonTier tier, Random random) {
        DungeonTier itemTier = tier.randomLootTier(random);
        return switch (slot) {
            case "head" -> itemTier.helmet;
            case "chest" -> itemTier.chestplate;
            case "legs" -> itemTier.leggings;
            case "feet" -> itemTier.boots;
            default -> throw new IllegalArgumentException("Unknown armor slot: " + slot);
        };
    }

    private static Item toolItem(String tool, DungeonTier tier, Random random) {
        DungeonTier itemTier = tier.randomLootTier(random);
        return switch (tool) {
            case "axe" -> itemTier.axe;
            case "shovel" -> itemTier.shovel;
            case "pickaxe" -> itemTier.pickaxe;
            case "hoe" -> itemTier.hoe;
            default -> throw new IllegalArgumentException("Unknown tool: " + tool);
        };
    }

    private static Item weaponItem(String weapon, DungeonTier tier, Random random) {
        DungeonTier itemTier = tier.randomLootTier(random);
        return switch (weapon) {
            case "sword" -> itemTier.sword;
            case "axe" -> itemTier.axe;
            default -> throw new IllegalArgumentException("Unknown weapon: " + weapon);
        };
    }

    private static void putStack(CompoundTag equipment, String slot, Item item) {
        if (item != null) {
            equipment.put(slot, stack(item));
        }
    }

    private static CompoundTag stack(Item item) {
        CompoundTag tag = new CompoundTag();
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        tag.putString("id", id.toString());
        tag.putInt("count", 1);
        return tag;
    }

    private static CompoundTag staircaseUpVariant(CompoundTag structure) {
        CompoundTag copy = structure.copy();
        int splitY = copy.getListOrEmpty("size").getIntOr(1, 0) / 2;
        ListTag palette = copy.getListOrEmpty("palette");
        ListTag blocks = copy.getListOrEmpty("blocks");
        List<CompoundTag> bottomJigsaws = new ArrayList<>();
        List<CompoundTag> topJigsaws = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompoundOrEmpty(i);
            if (!isJigsaw(block, palette)) {
                continue;
            }
            if (block.getListOrEmpty("pos").getIntOr(1, 0) < splitY) {
                bottomJigsaws.add(block);
            } else {
                topJigsaws.add(block);
            }
        }

        Comparator<CompoundTag> byHorizontalPosition = Comparator
                .comparingInt((CompoundTag block) -> block.getListOrEmpty("pos").getIntOr(0, 0))
                .thenComparingInt(block -> block.getListOrEmpty("pos").getIntOr(2, 0));
        bottomJigsaws.sort(byHorizontalPosition);
        topJigsaws.sort(byHorizontalPosition);
        if (bottomJigsaws.size() != topJigsaws.size()) {
            throw new IllegalStateException("Cannot create staircase up variant with unmatched jigsaw halves");
        }

        for (int i = 0; i < bottomJigsaws.size(); i++) {
            swapJigsaw(bottomJigsaws.get(i), topJigsaws.get(i));
        }
        return copy;
    }

    private static boolean isJigsaw(CompoundTag block, ListTag palette) {
        int state = block.getIntOr("state", -1);
        if (state < 0 || state >= palette.size()) {
            return false;
        }
        return palette.getCompoundOrEmpty(state).getStringOr("Name", "").equals("minecraft:jigsaw");
    }

    private static void swapJigsaw(CompoundTag first, CompoundTag second) {
        int firstState = first.getIntOr("state", -1);
        CompoundTag firstNbt = first.getCompound("nbt").map(CompoundTag::copy).orElse(null);

        first.putInt("state", second.getIntOr("state", -1));
        second.getCompound("nbt").ifPresentOrElse(
                nbt -> first.put("nbt", nbt.copy()),
                () -> first.remove("nbt")
        );

        second.putInt("state", firstState);
        if (firstNbt == null) {
            second.remove("nbt");
        } else {
            second.put("nbt", firstNbt);
        }
    }

    private static CompletableFuture<?> saveNbt(CachedOutput writer, CompoundTag nbt, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = writeCompressed(nbt);
                writer.writeIfNeeded(path, bytes, Hashing.sha256().hashBytes(bytes));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write generated dungeon structure: " + path, e);
            }
        });
    }

    private static byte[] writeCompressed(CompoundTag nbt) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, output);
        return output.toByteArray();
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Structure Variants";
    }
}
