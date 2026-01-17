package com.github.brainage04.procedural_dungeon.datagen.loot_table;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.EnchantWithLevelsLootFunction;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class LootTableUtils {
    private static @NotNull CompletableFuture<RegistryEntryList<Enchantment>> getRegistryEntryListCompletableFuture(TagKey<Enchantment> options, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        return registryLookup.thenApply(wrapperLookup ->
                wrapperLookup.getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(options));
    }

    public static LootTable.Builder addEnchantedItemPool(LootTable.Builder input, Item item, TagKey<Enchantment> options, int levels, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        CompletableFuture<RegistryEntryList<Enchantment>> listFuture = getRegistryEntryListCompletableFuture(options, registryLookup);

        RegistryWrapper.WrapperLookup registries;
        RegistryEntryList<Enchantment> options1;
        try {
            registries = registryLookup.get();
            options1 = listFuture.get();
        } catch (ExecutionException | InterruptedException e) {
            return input;
        }

        return input.pool(
                LootPool.builder()
                        .with(
                                ItemEntry.builder(item)
                                        .apply(
                                                EnchantWithLevelsLootFunction.builder(
                                                        registries, ConstantLootNumberProvider.create(levels)
                                                ).options(options1)
                                        )
                        )
        );
    }

    public static LootTable.Builder addWeightedPool(LootTable.Builder input, WeightedItem[] items, int min, int max, int rolls) {
        LootPool.Builder builder = LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(rolls));

        for (WeightedItem item : items) {
            builder = builder.with(
                    ItemEntry.builder(item.item)
                            .weight(item.weight)
                            .apply(
                                    SetCountLootFunction.builder(
                                            UniformLootNumberProvider.create(min, max)
                                    )
                            )
            );
        }

        return input.pool(builder);
    }

    public static LootTable.Builder addPool(LootTable.Builder input, Item[] items, int min, int max, int rolls) {
        LootPool.Builder builder = LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(rolls));

        for (Item item : items) {
            builder = builder.with(
                    ItemEntry.builder(item)
                            .apply(
                                    SetCountLootFunction.builder(
                                            UniformLootNumberProvider.create(min, max)
                                    )
                            )
            );
        }

        return input.pool(builder);
    }

    public static LootTable.Builder addPool(LootTable.Builder input, Item item, int min, int max, int rolls) {
        return input.pool(
                LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(rolls))
                        .with(
                                ItemEntry.builder(item)
                                        .apply(
                                                SetCountLootFunction.builder(
                                                        UniformLootNumberProvider.create(min, max)
                                                )
                                        )
                        )
        );
    }

    public record WeightedItem(Item item, int weight) {}
}
