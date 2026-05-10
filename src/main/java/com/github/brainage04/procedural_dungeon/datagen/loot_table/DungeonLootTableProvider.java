package com.github.brainage04.procedural_dungeon.datagen.loot_table;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class DungeonLootTableProvider extends SimpleFabricLootTableSubProvider {
    private static final Gson GSON = new Gson();
    private static final Path SPEC_PATH = Path.of("src/main/datagen/procedural_dungeon/loot_tables.json");
    private static final String[] NON_TIERED_TABLE_ORDER = {
            "starter_loot"
    };
    private static final String[] TIERED_TABLE_ORDER = {
            "hallway_end",
            "hallway_end/key_source",
            "hallway_loot",
            "hallway_loot/key_source",
            "armorsmith",
            "weaponsmith",
            "toolsmith",
            "enchanter",
            "hallway/trap/negative_potions"
    };
    private static final Set<String> THEME_FLAVOURED_TABLES = Set.of(
            "hallway_end",
            "hallway_end/key_source",
            "hallway_loot",
            "hallway_loot/key_source",
            "enchanter"
    );

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

    public static Identifier getLootTableId(String tableName, DungeonTheme theme, DungeonTier tier) {
        return ProceduralDungeon.of("%s/%s/tier_%d".formatted(tableName, theme.getSerializedName(), tier.tier));
    }

    public static Identifier getLootTableReplacementId(String tableName, DungeonTheme theme, int tier) {
        return getLootTableReplacementId(tableName, theme, DungeonTier.values()[tier - 1]);
    }

    public static Identifier getLootTableReplacementId(String tableName, DungeonTheme theme, DungeonTier tier) {
        return THEME_FLAVOURED_TABLES.contains(tableName) && !theme.profile.lootFlavour().isEmpty()
                ? getLootTableId(tableName, theme, tier)
                : getLootTableId(tableName, tier);
    }

    private static ResourceKey<LootTable> getLootTableRegistryKey(String tableName) {
        return ResourceKey.create(Registries.LOOT_TABLE, getLootTableId(tableName));
    }

    private static ResourceKey<LootTable> getLootTableRegistryKey(String tableName, DungeonTier tier) {
        return ResourceKey.create(Registries.LOOT_TABLE, getLootTableId(tableName, tier));
    }

    private static ResourceKey<LootTable> getLootTableRegistryKey(String tableName, DungeonTheme theme, DungeonTier tier) {
        return ResourceKey.create(Registries.LOOT_TABLE, getLootTableId(tableName, theme, tier));
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

    private static LootTable.Builder fromSpec(JsonObject allSpecs, JsonArray specs, DungeonTier dungeonTier, CompletableFuture<HolderLookup.Provider> registryLookup) {
        return fromSpec(allSpecs, specs, dungeonTier, registryLookup, null, null);
    }

    private static LootTable.Builder fromSpec(
            JsonObject allSpecs,
            JsonArray specs,
            DungeonTier dungeonTier,
            CompletableFuture<HolderLookup.Provider> registryLookup,
            DungeonTheme theme,
            String tableName
    ) {
        LootTable.Builder builder = new LootTable.Builder();

        for (JsonElement element : specs) {
            builder = applySpec(allSpecs, builder, element.getAsJsonObject(), dungeonTier, registryLookup);
        }

        return theme == null ? builder : addThemeFlavour(builder, theme, tableName);
    }

    private static LootTable.Builder applySpec(JsonObject allSpecs, LootTable.Builder builder, JsonObject spec, DungeonTier dungeonTier, CompletableFuture<HolderLookup.Provider> registryLookup) {
        if (spec.has("copy")) {
            for (JsonElement element : allSpecs.getAsJsonArray(spec.get("copy").getAsString())) {
                builder = applySpec(allSpecs, builder, element.getAsJsonObject(), dungeonTier, registryLookup);
            }
            return builder;
        }

        if (spec.has("each")) {
            int[] count = countRange(spec);
            int[] rolls = rollsRange(spec, dungeonTier);
            for (JsonElement item : spec.getAsJsonArray("each")) {
                builder = LootTableUtils.addPool(builder, item(item.getAsString()), count[0], count[1], rolls[0], rolls[1]);
            }
            return builder;
        }

        if (spec.has("items")) {
            int[] count = countRange(spec);
            int[] rolls = rollsRange(spec, dungeonTier);
            JsonArray items = spec.getAsJsonArray("items");
            LootTableUtils.WeightedItem[] weightedItems = new LootTableUtils.WeightedItem[items.size()];

            for (int i = 0; i < items.size(); i++) {
                JsonObject itemSpec = items.get(i).getAsJsonObject();
                weightedItems[i] = new LootTableUtils.WeightedItem(
                        item(itemSpec.get("item").getAsString()),
                        intValue(itemSpec.get("weight"), dungeonTier)
                );
            }

            return LootTableUtils.addWeightedPool(builder, weightedItems, count[0], count[1], rolls[0], rolls[1]);
        }

        if (spec.has("tierItems")) {
            int[] count = countRange(spec);
            int[] rolls = rollsRange(spec, dungeonTier);
            return LootTableUtils.addWeightedPool(
                    builder,
                    weightedTierItems(spec.get("tierItems").getAsString(), dungeonTier),
                    count[0],
                    count[1],
                    rolls[0],
                    rolls[1]
            );
        }

        if (spec.has("tierEquipment")) {
            TagKey<Enchantment> enchant = enchantTag(spec.get("enchant").getAsString());
            int levels = intValue(spec.get("levels"), dungeonTier);
            int[] rolls = rollsRange(spec, dungeonTier);
            List<LootTableUtils.WeightedEnchantedItem> items = new ArrayList<>();
            List<LootTableUtils.WeightedEnchantedItem[]> itemGroups = new ArrayList<>();
            for (JsonElement equipment : spec.getAsJsonArray("tierEquipment")) {
                LootTableUtils.WeightedItem[] weightedItems = weightedTierItems(equipment.getAsString(), dungeonTier);
                LootTableUtils.WeightedEnchantedItem[] enchantedItems = enchantedItems(weightedItems, enchant);
                itemGroups.add(enchantedItems);
                addEnchantedItems(items, enchantedItems);
            }
            if (spec.has("distinct") && spec.get("distinct").getAsBoolean()) {
                return LootTableUtils.addDistinctWeightedEnchantedItemGroups(
                        builder,
                        itemGroups,
                        levels,
                        registryLookup
                );
            }
            return LootTableUtils.addWeightedEnchantedItemPool(
                    builder,
                    items.toArray(LootTableUtils.WeightedEnchantedItem[]::new),
                    levels,
                    rolls[0],
                    rolls[1],
                    registryLookup
            );
        }

        if (spec.has("equipmentChoices")) {
            int levels = intValue(spec.get("levels"), dungeonTier);
            int[] rolls = rollsRange(spec, dungeonTier);
            List<LootTableUtils.WeightedEnchantedItem> items = new ArrayList<>();
            List<LootTableUtils.WeightedEnchantedItem[]> itemGroups = new ArrayList<>();
            for (JsonElement choiceElement : spec.getAsJsonArray("equipmentChoices")) {
                JsonObject choice = choiceElement.getAsJsonObject();
                TagKey<Enchantment> enchant = enchantTag(choice.get("enchant").getAsString());
                if (choice.has("tierEquipment")) {
                    for (JsonElement equipment : choice.getAsJsonArray("tierEquipment")) {
                        LootTableUtils.WeightedItem[] weightedItems = weightedTierItems(equipment.getAsString(), dungeonTier);
                        LootTableUtils.WeightedEnchantedItem[] enchantedItems = enchantedItems(weightedItems, enchant);
                        itemGroups.add(enchantedItems);
                        addEnchantedItems(items, enchantedItems);
                    }
                } else if (choice.has("item")) {
                    LootTableUtils.WeightedEnchantedItem enchantedItem = new LootTableUtils.WeightedEnchantedItem(
                            item(choice.get("item").getAsString()),
                            choice.has("weight") ? intValue(choice.get("weight"), dungeonTier) : 100,
                            enchant
                    );
                    items.add(enchantedItem);
                    itemGroups.add(new LootTableUtils.WeightedEnchantedItem[]{enchantedItem});
                } else {
                    throw new IllegalArgumentException("Unsupported equipment choice: " + choice);
                }
            }
            if (spec.has("distinct") && spec.get("distinct").getAsBoolean()) {
                return LootTableUtils.addDistinctWeightedEnchantedItemGroups(
                        builder,
                        itemGroups,
                        levels,
                        registryLookup
                );
            }
            return LootTableUtils.addWeightedEnchantedItemPool(
                    builder,
                    items.toArray(LootTableUtils.WeightedEnchantedItem[]::new),
                    levels,
                    rolls[0],
                    rolls[1],
                    registryLookup
            );
        }

        if (spec.has("netheriteUpgradeSmithingTemplate")) {
            return LootTableUtils.addChancePool(builder, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, smithingTemplateChance(dungeonTier));
        }

        if (spec.has("enchantedBooks")) {
            JsonArray enchantments = spec.getAsJsonArray("enchantedBooks");
            LootTableUtils.WeightedEnchantment[] weightedEnchantments = new LootTableUtils.WeightedEnchantment[enchantments.size()];
            for (int i = 0; i < enchantments.size(); i++) {
                JsonObject enchantment = enchantments.get(i).getAsJsonObject();
                weightedEnchantments[i] = new LootTableUtils.WeightedEnchantment(
                        Identifier.parse(enchantment.get("enchantment").getAsString()),
                        intValue(enchantment.get("weight"), dungeonTier)
                );
            }
            int[] rolls = rollsRange(spec, dungeonTier);
            return LootTableUtils.addEnchantedBookPool(
                    builder,
                    weightedEnchantments,
                    dungeonTier.tier,
                    DungeonTier.values().length,
                    rolls[0],
                    rolls[1],
                    registryLookup
            );
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
            int[] rolls = rollsRange(spec, dungeonTier);
            return LootTableUtils.addPool(builder, item(spec.get("item").getAsString()), count[0], count[1], rolls[0], rolls[1]);
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

    private static int[] rollsRange(JsonObject spec, DungeonTier dungeonTier) {
        if (!spec.has("rolls")) {
            return new int[]{1, 1};
        }

        JsonElement rolls = spec.get("rolls");
        if (rolls.isJsonArray()) {
            JsonArray range = rolls.getAsJsonArray();
            return new int[]{range.get(0).getAsInt(), range.get(1).getAsInt()};
        }

        int exactRolls = intValue(rolls, dungeonTier);
        return new int[]{exactRolls, exactRolls};
    }

    private static double smithingTemplateChance(DungeonTier dungeonTier) {
        int totalWeight = 0;
        int netheriteWeight = 0;
        for (DungeonTier.WeightedTier weightedTier : dungeonTier.weightedLootTiers()) {
            totalWeight += weightedTier.weight();
            if (weightedTier.tier() == DungeonTier.TIER_5) {
                netheriteWeight += weightedTier.weight();
            }
        }
        if (netheriteWeight <= 0 || totalWeight <= 0) {
            return 0.0D;
        }

        double netheriteItemChance = netheriteWeight / (double) totalWeight;
        double oneItemChestChance = netheriteItemChance;
        double twoItemChestChance = 1.0D - Math.pow(1.0D - netheriteItemChance, 2.0D);
        return (oneItemChestChance + twoItemChestChance) * 0.05D;
    }

    private static Item item(String id) {
        Identifier identifier = Identifier.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(identifier)) {
            throw new IllegalArgumentException("Unknown loot table item: " + id);
        }
        return BuiltInRegistries.ITEM.getValue(identifier);
    }

    private static LootTable.Builder addThemeFlavour(LootTable.Builder builder, DungeonTheme theme, String tableName) {
        List<DungeonTheme.WeightedItemSpec> flavour = theme.profile.lootFlavour();
        if (flavour.isEmpty()) {
            return builder;
        }

        LootTableUtils.WeightedItem[] items = new LootTableUtils.WeightedItem[flavour.size()];
        for (int i = 0; i < flavour.size(); i++) {
            DungeonTheme.WeightedItemSpec item = flavour.get(i);
            items[i] = new LootTableUtils.WeightedItem(item(item.item()), item.weight());
        }

        if (tableName.startsWith("hallway_end")) {
            return LootTableUtils.addWeightedPool(builder, items, 1, 2, 1, 1);
        }
        if (tableName.equals("enchanter")) {
            return LootTableUtils.addWeightedPool(builder, items, 1, 1, 0, 1);
        }
        return LootTableUtils.addWeightedPool(builder, items, 1, 2, 0, 1);
    }

    private static LootTableUtils.WeightedItem[] weightedTierItems(String key, DungeonTier dungeonTier) {
        List<LootTableUtils.WeightedItem> items = new ArrayList<>();
        for (DungeonTier.WeightedTier weightedTier : dungeonTier.weightedLootTiers()) {
            addDistributedWeight(items, tierItems(key, weightedTier.tier()), weightedTier.weight());
        }
        return items.toArray(LootTableUtils.WeightedItem[]::new);
    }

    private static void addEnchantedItems(
            List<LootTableUtils.WeightedEnchantedItem> output,
            LootTableUtils.WeightedItem[] items,
            TagKey<Enchantment> enchant
    ) {
        addEnchantedItems(output, enchantedItems(items, enchant));
    }

    private static void addEnchantedItems(
            List<LootTableUtils.WeightedEnchantedItem> output,
            LootTableUtils.WeightedEnchantedItem[] items
    ) {
        output.addAll(List.of(items));
    }

    private static LootTableUtils.WeightedEnchantedItem[] enchantedItems(
            LootTableUtils.WeightedItem[] items,
            TagKey<Enchantment> enchant
    ) {
        LootTableUtils.WeightedEnchantedItem[] enchantedItems = new LootTableUtils.WeightedEnchantedItem[items.length];
        for (int i = 0; i < items.length; i++) {
            LootTableUtils.WeightedItem item = items[i];
            enchantedItems[i] = new LootTableUtils.WeightedEnchantedItem(item.item(), item.weight(), enchant);
        }
        return enchantedItems;
    }

    private static void addDistributedWeight(List<LootTableUtils.WeightedItem> output, Item[] items, int weight) {
        int baseWeight = weight / items.length;
        int remainder = weight % items.length;
        for (int i = 0; i < items.length; i++) {
            int itemWeight = baseWeight + (i < remainder ? 1 : 0);
            if (itemWeight > 0) {
                output.add(new LootTableUtils.WeightedItem(items[i], itemWeight));
            }
        }
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
        for (String tableName : NON_TIERED_TABLE_ORDER) {
            biConsumer.accept(
                    getLootTableRegistryKey(tableName),
                    fromSpec(lootTableSpecs, lootTableSpecs.getAsJsonArray(tableName), DungeonTier.TIER_1, registryLookup)
            );
        }

        for (DungeonTier dungeonTier : DungeonTier.values()) {
            for (String tableName : TIERED_TABLE_ORDER) {
                biConsumer.accept(
                        getLootTableRegistryKey(tableName, dungeonTier),
                        fromSpec(lootTableSpecs, lootTableSpecs.getAsJsonArray(tableName), dungeonTier, registryLookup)
                );
            }

            for (DungeonTheme theme : DungeonTheme.values()) {
                for (String tableName : THEME_FLAVOURED_TABLES) {
                    biConsumer.accept(
                            getLootTableRegistryKey(tableName, theme, dungeonTier),
                            fromSpec(lootTableSpecs, lootTableSpecs.getAsJsonArray(tableName), dungeonTier, registryLookup, theme, tableName)
                    );
                }
            }
        }
    }
}
