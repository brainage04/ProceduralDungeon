package com.github.brainage04.procedural_dungeon.datagen.loot_table;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.LootTableUtils;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.SimpleFabricLootTableProvider;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class DungeonLootTableProvider extends SimpleFabricLootTableProvider {
    private final CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup;

    public DungeonLootTableProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        super(output, registryLookup, LootContextTypes.CHEST);
        this.registryLookup = registryLookup;
    }

    private static RegistryKey<LootTable> getLootTableRegistryKey(String tableName, DungeonTier tier) {
        return RegistryKey.of(
                RegistryKeys.LOOT_TABLE,
                Identifier.of(
                        ProceduralDungeon.MOD_ID,
                        "%s/tier_%d".formatted(tableName, tier.tier)
                )
        );
    }

    public static LootTable.Builder hallwayEnd(DungeonTier dungeonTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addPool(builder, Items.STICK, 4, 8, 1);

        builder = LootTableUtils.addPool(builder, Items.ROTTEN_FLESH, 2, 4, dungeonTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.BONE, 2, 4, dungeonTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.STRING, 2, 4, dungeonTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.SPIDER_EYE, 2, 4, dungeonTier.badRolls);

        builder = LootTableUtils.addWeightedPool(builder, new LootTableUtils.WeightedItem[]{
                new LootTableUtils.WeightedItem(Items.GOLDEN_APPLE, dungeonTier.badRolls),
                new LootTableUtils.WeightedItem(Items.ENCHANTED_GOLDEN_APPLE, dungeonTier.goodRolls)
        }, 1, 1, dungeonTier.goodRolls);
        builder = LootTableUtils.addPool(builder, dungeonTier.resourceItems, 1 ,2, dungeonTier.goodRolls);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.ARMOR_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.DAMAGE_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.MINING_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);

        return builder;
    }

    public static LootTable.Builder hallwayLoot(DungeonTier dungeonTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addPool(builder, Items.STICK, 4, 8, 1);

        builder = LootTableUtils.addPool(builder, Items.ROTTEN_FLESH, 1, 2, dungeonTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.BONE, 1, 2, dungeonTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.STRING, 1, 2, dungeonTier.badRolls);
        builder = LootTableUtils.addPool(builder, Items.SPIDER_EYE, 1, 2, dungeonTier.badRolls);

        builder = LootTableUtils.addWeightedPool(builder, new LootTableUtils.WeightedItem[]{
                new LootTableUtils.WeightedItem(Items.GOLDEN_APPLE, dungeonTier.badRolls),
                new LootTableUtils.WeightedItem(Items.ENCHANTED_GOLDEN_APPLE, dungeonTier.goodRolls)
        }, 1, 1, dungeonTier.goodRolls);
        builder = LootTableUtils.addPool(builder, dungeonTier.resourceItems, 2, 4, dungeonTier.goodRolls);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.ARMOR_EXCLUSIVE_SET, dungeonTier.levels * 2, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.DAMAGE_EXCLUSIVE_SET, dungeonTier.levels * 2, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.MINING_EXCLUSIVE_SET, dungeonTier.levels * 2, registryLookup);

        return builder;
    }

    public static LootTable.Builder armorsmith(DungeonTier dungeonTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addEnchantedItemPool(builder, dungeonTier.helmet, EnchantmentTags.ARMOR_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, dungeonTier.chestplate, EnchantmentTags.ARMOR_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, dungeonTier.leggings, EnchantmentTags.ARMOR_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, dungeonTier.boots, EnchantmentTags.ARMOR_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);

        return builder;
    }

    public static LootTable.Builder weaponsmith(DungeonTier dungeonTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addEnchantedItemPool(builder, dungeonTier.sword, EnchantmentTags.DAMAGE_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, dungeonTier.axe, EnchantmentTags.DAMAGE_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOW, EnchantmentTags.BOW_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.CROSSBOW, EnchantmentTags.CROSSBOW_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);

        return builder;
    }

    public static LootTable.Builder toolsmith(DungeonTier dungeonTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addEnchantedItemPool(builder, dungeonTier.axe, EnchantmentTags.MINING_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, dungeonTier.shovel, EnchantmentTags.MINING_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, dungeonTier.pickaxe, EnchantmentTags.MINING_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, dungeonTier.hoe, EnchantmentTags.MINING_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);

        return builder;
    }

    public static LootTable.Builder enchanter(DungeonTier dungeonTier, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        LootTable.Builder builder = new LootTable.Builder();

        builder = LootTableUtils.addPool(builder, Items.EXPERIENCE_BOTTLE, 4, 8, dungeonTier.goodRolls);
        builder = LootTableUtils.addPool(builder, Items.LAPIS_LAZULI, 4, 8, dungeonTier.goodRolls);

        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.ARMOR_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.DAMAGE_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.BOW_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.CROSSBOW_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);
        builder = LootTableUtils.addEnchantedItemPool(builder, Items.BOOK, EnchantmentTags.MINING_EXCLUSIVE_SET, dungeonTier.levels, registryLookup);

        return builder;
    }

    @Override
    public void accept(BiConsumer<RegistryKey<LootTable>, LootTable.Builder> biConsumer) {
        for (DungeonTier dungeonTier : DungeonTier.values()) {
            biConsumer.accept(getLootTableRegistryKey("hallway_end", dungeonTier), hallwayEnd(dungeonTier, registryLookup));
            biConsumer.accept(getLootTableRegistryKey("hallway_loot", dungeonTier), hallwayLoot(dungeonTier, registryLookup));
            biConsumer.accept(getLootTableRegistryKey("armorsmith", dungeonTier), armorsmith(dungeonTier, registryLookup));
            biConsumer.accept(getLootTableRegistryKey("weaponsmith", dungeonTier), weaponsmith(dungeonTier, registryLookup));
            biConsumer.accept(getLootTableRegistryKey("toolsmith", dungeonTier), toolsmith(dungeonTier, registryLookup));
            biConsumer.accept(getLootTableRegistryKey("enchanter", dungeonTier), enchanter(dungeonTier, registryLookup));
        }
    }
}

