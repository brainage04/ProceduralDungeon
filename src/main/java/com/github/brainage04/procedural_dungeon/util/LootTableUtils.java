package com.github.brainage04.procedural_dungeon.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SetPotionFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

public class LootTableUtils {
    public static LootTable.Builder addEnchantedItemPool(LootTable.Builder input, Item item, TagKey<Enchantment> options, int levels, CompletableFuture<HolderLookup.Provider> registryLookup) {
        return addWeightedEnchantedItemPool(input, new WeightedItem[]{new WeightedItem(item, 1)}, options, levels, registryLookup);
    }

    public static LootTable.Builder addWeightedEnchantedItemPool(LootTable.Builder input, WeightedItem[] items, TagKey<Enchantment> options, int levels, CompletableFuture<HolderLookup.Provider> registryLookup) {
        WeightedEnchantedItem[] enchantedItems = new WeightedEnchantedItem[items.length];
        for (int i = 0; i < items.length; i++) {
            enchantedItems[i] = new WeightedEnchantedItem(items[i].item, items[i].weight, options);
        }
        return addWeightedEnchantedItemPool(input, enchantedItems, levels, 1, 1, registryLookup);
    }

    public static LootTable.Builder addWeightedEnchantedItemPool(
            LootTable.Builder input,
            WeightedEnchantedItem[] items,
            int levels,
            int minRolls,
            int maxRolls,
            CompletableFuture<HolderLookup.Provider> registryLookup
    ) {
        HolderLookup.Provider registries;
        try {
            registries = registryLookup.get();
        } catch (ExecutionException e) {
            return input;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return input;
        }

        Map<TagKey<Enchantment>, HolderSet<Enchantment>> enchantmentOptions = new HashMap<>();
        LootPool.Builder builder = LootPool.lootPool()
                .setRolls(minRolls == maxRolls ? ConstantValue.exactly(minRolls) : UniformGenerator.between(minRolls, maxRolls));
        for (WeightedEnchantedItem item : items) {
            HolderSet<Enchantment> options;
            try {
                options = enchantmentOptions.computeIfAbsent(item.enchantments, key ->
                        registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key));
            } catch (IllegalStateException e) {
                return input;
            }
            builder = builder.add(
                    LootItem.lootTableItem(item.item)
                            .setWeight(item.weight)
                            .apply(
                                    EnchantWithLevelsFunction.enchantWithLevels(
                                            registries, ConstantValue.exactly(levels)
                                    ).withOptions(options)
                            )
            );
        }

        return input.withPool(builder);
    }

    public static LootTable.Builder addDistinctWeightedEnchantedItemPool(
            LootTable.Builder input,
            WeightedEnchantedItem[] items,
            int levels,
            CompletableFuture<HolderLookup.Provider> registryLookup
    ) {
        List<WeightedEnchantedItem[]> itemGroups = new ArrayList<>();
        for (WeightedEnchantedItem item : items) {
            itemGroups.add(new WeightedEnchantedItem[]{item});
        }
        return addDistinctWeightedEnchantedItemGroups(input, itemGroups, levels, registryLookup);
    }

    public static LootTable.Builder addDistinctWeightedEnchantedItemGroups(
            LootTable.Builder input,
            List<WeightedEnchantedItem[]> itemGroups,
            int levels,
            CompletableFuture<HolderLookup.Provider> registryLookup
    ) {
        HolderLookup.Provider registries;
        try {
            registries = registryLookup.get();
        } catch (ExecutionException e) {
            return input;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return input;
        }

        if (itemGroups.isEmpty()) {
            return input;
        }

        Map<TagKey<Enchantment>, HolderSet<Enchantment>> enchantmentOptions = new HashMap<>();
        int singleWeightTotal = 0;
        int pairWeightTotal = 0;
        for (WeightedEnchantedItem[] group : itemGroups) {
            for (WeightedEnchantedItem item : group) {
                singleWeightTotal += item.weight;
            }
        }
        for (int firstGroup = 0; firstGroup < itemGroups.size(); firstGroup++) {
            for (int secondGroup = firstGroup + 1; secondGroup < itemGroups.size(); secondGroup++) {
                for (WeightedEnchantedItem first : itemGroups.get(firstGroup)) {
                    for (WeightedEnchantedItem second : itemGroups.get(secondGroup)) {
                        pairWeightTotal += first.weight * second.weight;
                    }
                }
            }
        }

        LootPool.Builder builder = LootPool.lootPool().setRolls(ConstantValue.exactly(1));
        for (WeightedEnchantedItem[] group : itemGroups) {
            for (WeightedEnchantedItem item : group) {
                LootTable table = inlineTable(enchantmentOptions, registries, levels, item);
                if (table == null) {
                    return input;
                }
                builder = builder.add(NestedLootTable.inlineLootTable(table).setWeight(scaledWeight(item.weight, singleWeightTotal)));
            }
        }

        if (pairWeightTotal > 0) {
            for (int firstGroup = 0; firstGroup < itemGroups.size(); firstGroup++) {
                for (int secondGroup = firstGroup + 1; secondGroup < itemGroups.size(); secondGroup++) {
                    for (WeightedEnchantedItem first : itemGroups.get(firstGroup)) {
                        for (WeightedEnchantedItem second : itemGroups.get(secondGroup)) {
                            LootTable table = inlineTable(enchantmentOptions, registries, levels, first, second);
                            if (table == null) {
                                return input;
                            }
                            builder = builder.add(NestedLootTable.inlineLootTable(table)
                                    .setWeight(scaledWeight(first.weight * second.weight, pairWeightTotal)));
                        }
                    }
                }
            }
        }

        return input.withPool(builder);
    }

    private static int scaledWeight(int weight, int total) {
        if (total <= 0) {
            return 1;
        }
        return Math.max(1, Math.round(weight * 1000.0F / total));
    }

    private static LootTable inlineTable(
            Map<TagKey<Enchantment>, HolderSet<Enchantment>> enchantmentOptions,
            HolderLookup.Provider registries,
            int levels,
            WeightedEnchantedItem... items
    ) {
        LootTable.Builder table = LootTable.lootTable();
        for (WeightedEnchantedItem item : items) {
            LootPoolEntryContainer.Builder<?> entry = enchantedEntry(enchantmentOptions, registries, levels, item);
            if (entry == null) {
                return null;
            }
            table = table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(entry));
        }
        return table.build();
    }

    private static LootPoolEntryContainer.Builder<?> enchantedEntry(
            Map<TagKey<Enchantment>, HolderSet<Enchantment>> enchantmentOptions,
            HolderLookup.Provider registries,
            int levels,
            WeightedEnchantedItem item
    ) {
        HolderSet<Enchantment> options;
        try {
            options = enchantmentOptions.computeIfAbsent(item.enchantments, key ->
                    registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key));
        } catch (IllegalStateException e) {
            return null;
        }
        return LootItem.lootTableItem(item.item)
                .apply(EnchantWithLevelsFunction.enchantWithLevels(registries, ConstantValue.exactly(levels)).withOptions(options));
    }

    public static LootTable.Builder addEnchantedBookPool(
            LootTable.Builder input,
            WeightedEnchantment[] enchantments,
            int tier,
            int tierCount,
            int minRolls,
            int maxRolls,
            CompletableFuture<HolderLookup.Provider> registryLookup
    ) {
        HolderLookup.Provider registries;
        HolderLookup.RegistryLookup<Enchantment> registry;
        try {
            registries = registryLookup.get();
            registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to look up enchantment registry", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while looking up enchantment registry", e);
        }

        LootPool.Builder builder = LootPool.lootPool()
                .setRolls(rolls(minRolls, maxRolls));
        int curatedWeight = 0;
        for (WeightedEnchantment enchantment : enchantments) {
            Holder<Enchantment> holder = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, enchantment.id));
            curatedWeight += enchantment.weight;
            builder = builder.add(
                    LootItem.lootTableItem(Items.BOOK)
                            .setWeight(enchantment.weight)
                            .apply(new SetEnchantmentsFunction.Builder()
                                    .withEnchantment(holder, ConstantValue.exactly(bookLevel(holder.value(), tier, tierCount))))
            );
        }
        builder = builder.add(
                LootItem.lootTableItem(Items.BOOK)
                        .setWeight(curatedWeight)
                        .apply(EnchantWithLevelsFunction.enchantWithLevels(registries, ConstantValue.exactly(tier * 10)))
        );

        return input.withPool(builder);
    }

    public static LootTable.Builder addEnchantedBookPool(
            LootTable.Builder input,
            WeightedEnchantment[] enchantments,
            int tier,
            int tierCount,
            int rolls,
            CompletableFuture<HolderLookup.Provider> registryLookup
    ) {
        return addEnchantedBookPool(input, enchantments, tier, tierCount, rolls, rolls, registryLookup);
    }

    private static int bookLevel(Enchantment enchantment, int tier, int tierCount) {
        if (tierCount <= 1 || enchantment.getMinLevel() == enchantment.getMaxLevel()) {
            return enchantment.getMinLevel();
        }

        int range = enchantment.getMaxLevel() - enchantment.getMinLevel();
        return enchantment.getMinLevel() + Math.round((tier - 1) * range / (float) (tierCount - 1));
    }

    public static LootTable.Builder addWeightedPool(LootTable.Builder input, WeightedItem[] items, int min, int max, int rolls) {
        return addWeightedPool(input, items, min, max, rolls, rolls);
    }

    public static LootTable.Builder addWeightedPool(LootTable.Builder input, WeightedItem[] items, int min, int max, int minRolls, int maxRolls) {
        LootPool.Builder builder = LootPool.lootPool()
                .setRolls(rolls(minRolls, maxRolls));

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
        return addPool(input, items, min, max, rolls, rolls);
    }

    public static LootTable.Builder addPool(LootTable.Builder input, Item[] items, int min, int max, int minRolls, int maxRolls) {
        LootPool.Builder builder = LootPool.lootPool()
                .setRolls(rolls(minRolls, maxRolls));

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
        return addPool(input, item, min, max, rolls, rolls);
    }

    public static LootTable.Builder addPool(LootTable.Builder input, Item item, int min, int max, int minRolls, int maxRolls) {
        return input.withPool(
                LootPool.lootPool()
                        .setRolls(rolls(minRolls, maxRolls))
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

    public static LootTable.Builder addChancePool(LootTable.Builder input, Item item, double chance) {
        int precision = 10000;
        int itemWeight = (int) Math.round(chance * precision);
        if (itemWeight <= 0) {
            return input;
        }
        if (itemWeight >= precision) {
            return addPool(input, item, 1, 1, 1);
        }

        return input.withPool(
                LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(item).setWeight(itemWeight))
                        .add(EmptyLootItem.emptyItem().setWeight(precision - itemWeight))
        );
    }

    private static net.minecraft.world.level.storage.loot.providers.number.NumberProvider rolls(int minRolls, int maxRolls) {
        return minRolls == maxRolls ? ConstantValue.exactly(minRolls) : UniformGenerator.between(minRolls, maxRolls);
    }

    public static LootTable.Builder addPotionPool(LootTable.Builder input, Item item, Identifier potionId, CompletableFuture<HolderLookup.Provider> registryLookup) {
        Holder<Potion> potion;
        try {
            potion = registryLookup.get()
                    .lookupOrThrow(Registries.POTION)
                    .getOrThrow(ResourceKey.create(Registries.POTION, potionId));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to look up potion: " + potionId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while looking up potion: " + potionId, e);
        }

        return input.withPool(
                LootPool.lootPool()
                        .add(
                                LootItem.lootTableItem(item)
                                        .apply(SetPotionFunction.setPotion(potion))
                        )
        );
    }

    public record WeightedItem(Item item, int weight) {}

    public record WeightedEnchantedItem(Item item, int weight, TagKey<Enchantment> enchantments) {}

    public record WeightedEnchantment(Identifier id, int weight) {}
}
