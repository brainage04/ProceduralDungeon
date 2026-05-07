package com.github.brainage04.procedural_dungeon.datagen.loot_table;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.LootTableUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.SimpleFabricLootTableSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class DungeonLootTableProvider extends SimpleFabricLootTableSubProvider {
    private static final Gson GSON = new Gson();
    private static final Path SPEC_PATH = Path.of("src/main/datagen/procedural_dungeon/loot_tables.json");
    private static final String[] TABLE_ORDER = {
            "hallway_end",
            "hallway_loot",
            "armorsmith",
            "weaponsmith",
            "toolsmith",
            "enchanter",
            "hallway/trap/negative_potions"
    };

    private final CompletableFuture<HolderLookup.Provider> registryLookup;
    private final JsonObject lootTableSpecs;

    public DungeonLootTableProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup, LootContextParamSets.CHEST);
        this.registryLookup = registryLookup;
        this.lootTableSpecs = loadLootTableSpecs();
    }

    public static Identifier getLootTableId(String tableName) {
        return ProceduralDungeon.of(tableName);
    }

    public static Identifier getLootTableId(String tableName, DungeonTier tier) {
        return ProceduralDungeon.of("%s/tier_%d".formatted(tableName, tier.tier));
    }

    private static ResourceKey<LootTable> getLootTableRegistryKey(String tableName, DungeonTier tier) {
        return ResourceKey.create(Registries.LOOT_TABLE, getLootTableId(tableName, tier));
    }

    private static JsonObject loadLootTableSpecs() {
        Path specPath = resolveSpecPath();
        try (Reader reader = Files.newBufferedReader(specPath)) {
            return GSON.fromJson(reader, JsonObject.class).getAsJsonObject("tables");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read loot table spec: " + specPath, e);
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

        throw new IllegalStateException("Failed to find loot table spec: " + SPEC_PATH);
    }

    private static LootTable.Builder fromSpec(JsonArray specs, DungeonTier dungeonTier, CompletableFuture<HolderLookup.Provider> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        for (JsonElement element : specs) {
            builder = applySpec(builder, element.getAsJsonObject(), dungeonTier, registryLookup);
        }

        return builder;
    }

    private static LootTable.Builder applySpec(LootTable.Builder builder, JsonObject spec, DungeonTier dungeonTier, CompletableFuture<HolderLookup.Provider> registryLookup) {
        if (spec.has("each")) {
            int[] count = countRange(spec);
            int rolls = intValue(spec.get("rolls"), dungeonTier);
            for (JsonElement item : spec.getAsJsonArray("each")) {
                builder = LootTableUtils.addPool(builder, item(item.getAsString()), count[0], count[1], rolls);
            }
            return builder;
        }

        if (spec.has("items")) {
            int[] count = countRange(spec);
            int rolls = intValue(spec.get("rolls"), dungeonTier);
            JsonArray items = spec.getAsJsonArray("items");
            LootTableUtils.WeightedItem[] weightedItems = new LootTableUtils.WeightedItem[items.size()];

            for (int i = 0; i < items.size(); i++) {
                JsonObject itemSpec = items.get(i).getAsJsonObject();
                weightedItems[i] = new LootTableUtils.WeightedItem(
                        item(itemSpec.get("item").getAsString()),
                        intValue(itemSpec.get("weight"), dungeonTier)
                );
            }

            return LootTableUtils.addWeightedPool(builder, weightedItems, count[0], count[1], rolls);
        }

        if (spec.has("tierItems")) {
            int[] count = countRange(spec);
            return LootTableUtils.addPool(builder, tierItems(spec.get("tierItems").getAsString(), dungeonTier), count[0], count[1], intValue(spec.get("rolls"), dungeonTier));
        }

        if (spec.has("tierEquipment")) {
            TagKey<Enchantment> enchant = enchantTag(spec.get("enchant").getAsString());
            int levels = intValue(spec.get("levels"), dungeonTier);
            for (JsonElement equipment : spec.getAsJsonArray("tierEquipment")) {
                builder = LootTableUtils.addEnchantedItemPool(builder, tierItem(equipment.getAsString(), dungeonTier), enchant, levels, registryLookup);
            }
            return builder;
        }

        if (spec.has("enchantedBooks")) {
            int levels = intValue(spec.get("levels"), dungeonTier);
            for (JsonElement enchant : spec.getAsJsonArray("enchantedBooks")) {
                builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, enchantTag(enchant.getAsString()), levels, registryLookup);
            }
            return builder;
        }

        if (spec.has("splashPotionsByTier")) {
            JsonArray potions = spec.getAsJsonArray("splashPotionsByTier").get(dungeonTier.tier - 1).getAsJsonArray();
            for (JsonElement potion : potions) {
                builder = LootTableUtils.addPotionPool(builder, Items.SPLASH_POTION, Identifier.parse(potion.getAsString()), registryLookup);
            }
            return builder;
        }

        if (spec.has("enchant")) {
            return LootTableUtils.addEnchantedItemPool(
                    builder,
                    item(spec.get("item").getAsString()),
                    enchantTag(spec.get("enchant").getAsString()),
                    intValue(spec.get("levels"), dungeonTier),
                    registryLookup
            );
        }

        if (spec.has("item")) {
            int[] count = countRange(spec);
            return LootTableUtils.addPool(builder, item(spec.get("item").getAsString()), count[0], count[1], intValue(spec.get("rolls"), dungeonTier));
        }

        throw new IllegalArgumentException("Unsupported loot table spec: " + spec);
    }

    private static int[] countRange(JsonObject spec) {
        JsonElement count = spec.get("count");

        if (count.isJsonArray()) {
            JsonArray range = count.getAsJsonArray();
            return new int[]{range.get(0).getAsInt(), range.get(1).getAsInt()};
        }

        int exactCount = count.getAsInt();
        return new int[]{exactCount, exactCount};
    }

    private static Item item(String id) {
        Identifier identifier = Identifier.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(identifier)) {
            throw new IllegalArgumentException("Unknown loot table item: " + id);
        }
        return BuiltInRegistries.ITEM.getValue(identifier);
    }

    private static Item[] tierItems(String key, DungeonTier dungeonTier) {
        return switch (key) {
            case "resources" -> dungeonTier.resourceItems;
            default -> new Item[]{tierItem(key, dungeonTier)};
        };
    }

    private static Item tierItem(String key, DungeonTier dungeonTier) {
        return switch (key) {
            case "helmet" -> dungeonTier.helmet;
            case "chestplate" -> dungeonTier.chestplate;
            case "leggings" -> dungeonTier.leggings;
            case "boots" -> dungeonTier.boots;
            case "sword" -> dungeonTier.sword;
            case "axe" -> dungeonTier.axe;
            case "shovel" -> dungeonTier.shovel;
            case "pickaxe" -> dungeonTier.pickaxe;
            case "hoe" -> dungeonTier.hoe;
            default -> throw new IllegalArgumentException("Unknown tier item: " + key);
        };
    }

    private static TagKey<Enchantment> enchantTag(String key) {
        return switch (key) {
            case "armor" -> EnchantmentTags.ARMOR_EXCLUSIVE;
            case "damage" -> EnchantmentTags.DAMAGE_EXCLUSIVE;
            case "bow" -> EnchantmentTags.BOW_EXCLUSIVE;
            case "crossbow" -> EnchantmentTags.CROSSBOW_EXCLUSIVE;
            case "mining" -> EnchantmentTags.MINING_EXCLUSIVE;
            default -> throw new IllegalArgumentException("Unknown enchantment tag alias: " + key);
        };
    }

    private static int intValue(JsonElement element, DungeonTier dungeonTier) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }

        String expression = element.getAsString().replace(" ", "");
        if (!expression.startsWith("$")) {
            return Integer.parseInt(expression);
        }

        String[] parts = expression.substring(1).split("\\*", 2);
        int value = switch (parts[0]) {
            case "goodRolls" -> dungeonTier.goodRolls;
            case "badRolls" -> dungeonTier.badRolls;
            case "levels" -> dungeonTier.levels;
            default -> throw new IllegalArgumentException("Unknown tier variable: " + parts[0]);
        };

        if (parts.length == 2) {
            value *= Integer.parseInt(parts[1]);
        }

        return value;
    }

    @Override
    public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> biConsumer) {
        for (DungeonTier dungeonTier : DungeonTier.values()) {
            for (String tableName : TABLE_ORDER) {
                biConsumer.accept(
                        getLootTableRegistryKey(tableName, dungeonTier),
                        fromSpec(lootTableSpecs.getAsJsonArray(tableName), dungeonTier, registryLookup)
                );
            }
        }
    }
}
