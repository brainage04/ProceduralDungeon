package com.github.brainage04.procedural_dungeon.datagen.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public enum DungeonTier {
    TIER_1,
    TIER_2,
    TIER_3,
    TIER_4,
    TIER_5;

    public int tier;
    public int size;
    public int spacing;
    public int separation;
    public Item[] resourceItems;
    public Item helmet;
    public Item chestplate;
    public Item leggings;
    public Item boots;
    public Item sword;
    public Item axe;
    public Item shovel;
    public Item pickaxe;
    public Item hoe;
    public int goodRolls;
    public int badRolls;
    public int levels;
    public int spawnerMaxNearbyEntities;
    public int spawnerMinSpawnDelay;
    public int spawnerMaxSpawnDelay;
    public int spawnerRequiredPlayerRange;
    public int spawnerSpawnCount;
    public int spawnerSpawnRange;

    static {
        JsonArray tiers = DungeonSpecLoader.load("tiers.json").getAsJsonArray("tiers");
        if (tiers.size() != values().length) {
            throw new IllegalStateException("Tier spec count does not match DungeonTier count");
        }

        for (JsonElement element : tiers) {
            JsonObject spec = element.getAsJsonObject();
            DungeonTier tier = getById(spec.get("id").getAsInt());
            tier.load(spec);
        }
    }

    public static DungeonTier getById(int id) {
        for (DungeonTier tier : values()) {
            if (tier.ordinal() + 1 == id) {
                return tier;
            }
        }

        throw new IllegalArgumentException("Unsupported dungeon tier: " + id);
    }

    private void load(JsonObject spec) {
        this.tier = spec.get("id").getAsInt();
        this.size = spec.get("size").getAsInt();
        this.spacing = spec.get("spacing").getAsInt();
        this.separation = spec.get("separation").getAsInt();

        JsonObject loot = spec.getAsJsonObject("loot");
        this.resourceItems = items(loot.getAsJsonArray("resources"));
        this.goodRolls = loot.get("goodRolls").getAsInt();
        this.badRolls = loot.get("badRolls").getAsInt();
        this.levels = loot.get("levels").getAsInt();

        JsonObject equipment = loot.getAsJsonObject("equipment");
        this.helmet = item(equipment.get("helmet").getAsString());
        this.chestplate = item(equipment.get("chestplate").getAsString());
        this.leggings = item(equipment.get("leggings").getAsString());
        this.boots = item(equipment.get("boots").getAsString());
        this.sword = item(equipment.get("sword").getAsString());
        this.axe = item(equipment.get("axe").getAsString());
        this.shovel = item(equipment.get("shovel").getAsString());
        this.pickaxe = item(equipment.get("pickaxe").getAsString());
        this.hoe = item(equipment.get("hoe").getAsString());

        JsonObject spawner = spec.getAsJsonObject("spawner");
        this.spawnerMaxNearbyEntities = spawner.get("maxNearbyEntities").getAsInt();
        this.spawnerMinSpawnDelay = spawner.get("minSpawnDelay").getAsInt();
        this.spawnerMaxSpawnDelay = spawner.get("maxSpawnDelay").getAsInt();
        this.spawnerRequiredPlayerRange = spawner.get("requiredPlayerRange").getAsInt();
        this.spawnerSpawnCount = spawner.get("spawnCount").getAsInt();
        this.spawnerSpawnRange = spawner.get("spawnRange").getAsInt();
    }

    private static Item[] items(JsonArray ids) {
        Item[] items = new Item[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            items[i] = item(ids.get(i).getAsString());
        }
        return items;
    }

    private static Item item(String id) {
        Identifier identifier = Identifier.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(identifier)) {
            throw new IllegalArgumentException("Unknown tier item: " + id);
        }
        return BuiltInRegistries.ITEM.getValue(identifier);
    }
}
