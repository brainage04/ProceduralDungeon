package com.github.brainage04.procedural_dungeon.datagen.loot_table;

import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.EnchantmentTags;

import java.util.concurrent.CompletableFuture;

public class DungeonLootTables {
    public static LootTable.Builder hallwayEnd(LootTier lootTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addPool(builder, Items.STICK, 4, 8, 1);

        builder = LootTableUtils.addPool(builder, Items.ROTTEN_FLESH, 2, 4, lootTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.BONE, 2, 4, lootTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.STRING, 2, 4, lootTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.SPIDER_EYE, 2, 4, lootTier.badRolls);

        builder = LootTableUtils.addWeightedPool(builder, new LootTableUtils.WeightedItem[]{
                new LootTableUtils.WeightedItem(Items.GOLDEN_APPLE, lootTier.badRolls),
                new LootTableUtils.WeightedItem(Items.ENCHANTED_GOLDEN_APPLE, lootTier.goodRolls)
        }, 1, 1, lootTier.goodRolls);
        builder = LootTableUtils.addPool(builder, lootTier.resourceItems, 1 ,2, lootTier.goodRolls);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.ARMOR_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.DAMAGE_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.MINING_EXCLUSIVE_SET, lootTier.levels, registryLookup);

        return builder;
    }

    public static LootTable.Builder hallwayLoot(LootTier lootTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addPool(builder, Items.STICK, 4, 8, 1);

        builder = LootTableUtils.addPool(builder, Items.ROTTEN_FLESH, 1, 2, lootTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.BONE, 1, 2, lootTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.STRING, 1, 2, lootTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.SPIDER_EYE, 1, 2, lootTier.badRolls);

        builder = LootTableUtils.addWeightedPool(builder, new LootTableUtils.WeightedItem[]{
                new LootTableUtils.WeightedItem(Items.GOLDEN_APPLE, lootTier.badRolls),
                new LootTableUtils.WeightedItem(Items.ENCHANTED_GOLDEN_APPLE, lootTier.goodRolls)
        }, 1, 1, lootTier.goodRolls);
        builder = LootTableUtils.addPool(builder, lootTier.resourceItems, 2, 4, lootTier.goodRolls);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.ARMOR_EXCLUSIVE_SET, lootTier.levels * 2, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.DAMAGE_EXCLUSIVE_SET, lootTier.levels * 2, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.MINING_EXCLUSIVE_SET, lootTier.levels * 2, registryLookup);

        return builder;
    }

    public static LootTable.Builder armorsmith(LootTier lootTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addEnchantedItemPool(builder, lootTier.helmet, EnchantmentTags.ARMOR_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, lootTier.chestplate, EnchantmentTags.ARMOR_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, lootTier.leggings, EnchantmentTags.ARMOR_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, lootTier.boots, EnchantmentTags.ARMOR_EXCLUSIVE_SET, lootTier.levels, registryLookup);

        return builder;
    }

    public static LootTable.Builder weaponsmith(LootTier lootTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addEnchantedItemPool(builder, lootTier.sword, EnchantmentTags.DAMAGE_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, lootTier.axe, EnchantmentTags.DAMAGE_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOW, EnchantmentTags.BOW_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.CROSSBOW, EnchantmentTags.CROSSBOW_EXCLUSIVE_SET, lootTier.levels, registryLookup);

        return builder;
    }

    public static LootTable.Builder toolsmith(LootTier lootTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addEnchantedItemPool(builder, lootTier.axe, EnchantmentTags.MINING_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, lootTier.shovel, EnchantmentTags.MINING_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, lootTier.pickaxe, EnchantmentTags.MINING_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, lootTier.hoe, EnchantmentTags.MINING_EXCLUSIVE_SET, lootTier.levels, registryLookup);

        return builder;
    }

    public static LootTable.Builder enchanter(LootTier lootTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addPool(builder, Items.EXPERIENCE_BOTTLE, 4, 8, lootTier.goodRolls);
        builder = LootTableUtils.addPool(builder, Items.LAPIS_LAZULI, 4, 8, lootTier.goodRolls);

        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.ARMOR_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.DAMAGE_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.BOW_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.CROSSBOW_EXCLUSIVE_SET, lootTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.MINING_EXCLUSIVE_SET, lootTier.levels, registryLookup);

        return builder;
    }
}