package com.github.brainage04.procedural_dungeon.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

public class LootTableUtils {
    private static @NotNull CompletableFuture<HolderSet<Enchantment>> getRegistryEntryListCompletableFuture(TagKey<Enchantment> options, CompletableFuture<HolderLookup.Provider> registryLookup) {
        return registryLookup.thenApply(wrapperLookup ->
                wrapperLookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(options));
    }

    public static LootTable.Builder addEnchantedItemPool(LootTable.Builder input, Item item, TagKey<Enchantment> options, int levels, CompletableFuture<HolderLookup.Provider> registryLookup) {
        CompletableFuture<HolderSet<Enchantment>> listFuture = getRegistryEntryListCompletableFuture(options, registryLookup);

        HolderLookup.Provider registries;
        HolderSet<Enchantment> options1;
        try {
            registries = registryLookup.get();
            options1 = listFuture.get();
        } catch (ExecutionException | InterruptedException e) {
            return input;
        }

        return input.withPool(
                LootPool.lootPool()
                        .add(
                                LootItem.lootTableItem(item)
                                        .apply(
                                                EnchantWithLevelsFunction.enchantWithLevels(
                                                        registries, ConstantValue.exactly(levels)
                                                ).withOptions(options1)
                                        )
                        )
        );
    }

    public static LootTable.Builder addWeightedPool(LootTable.Builder input, WeightedItem[] items, int min, int max, int rolls) {
        LootPool.Builder builder = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(rolls));

        for (WeightedItem item : items) {
            builder = builder.add(
                    LootItem.lootTableItem(item.item)
                            .setWeight(item.weight)
                            .apply(
                                    SetItemCountFunction.setCount(
                                            UniformGenerator.between(min, max)
                                    )
                            )
            );
        }

        return input.withPool(builder);
    }

    public static LootTable.Builder addPool(LootTable.Builder input, Item[] items, int min, int max, int rolls) {
        LootPool.Builder builder = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(rolls));

        for (Item item : items) {
            builder = builder.add(
                    LootItem.lootTableItem(item)
                            .apply(
                                    SetItemCountFunction.setCount(
                                            UniformGenerator.between(min, max)
                                    )
                            )
            );
        }

        return input.withPool(builder);
    }

    public static LootTable.Builder addPool(LootTable.Builder input, Item item, int min, int max, int rolls) {
        return input.withPool(
                LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(rolls))
                        .add(
                                LootItem.lootTableItem(item)
                                        .apply(
                                                SetItemCountFunction.setCount(
                                                        UniformGenerator.between(min, max)
                                                )
                                        )
                        )
        );
    }

    public record WeightedItem(Item item, int weight) {}
}
