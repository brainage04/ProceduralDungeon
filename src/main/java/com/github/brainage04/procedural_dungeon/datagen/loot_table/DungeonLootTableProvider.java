package com.github.brainage04.procedural_dungeon.datagen.loot_table;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.SimpleFabricLootTableProvider;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class DungeonLootTableProvider extends SimpleFabricLootTableProvider {
    private final CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup;

    public DungeonLootTableProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        super(output, registryLookup, LootContextTypes.CHEST);
        this.registryLookup = registryLookup;
    }

    private static RegistryKey<LootTable> getLootTableRegistryKey(String tableName, LootTier lootTier) {
        return RegistryKey.of(
                RegistryKeys.LOOT_TABLE,
                Identifier.of(
                        ProceduralDungeon.MOD_ID,
                        "%s/tier_%d".formatted(tableName, lootTier.ordinal() + 1)
                )
        );
    }

    @Override
    public void accept(BiConsumer<RegistryKey<LootTable>, LootTable.Builder> biConsumer) {
        for (LootTier lootTier : LootTier.values()) {
            biConsumer.accept(getLootTableRegistryKey("hallway_end", lootTier), DungeonLootTables.hallwayEnd(lootTier, registryLookup));
            biConsumer.accept(getLootTableRegistryKey("hallway_loot", lootTier), DungeonLootTables.hallwayLoot(lootTier, registryLookup));
            biConsumer.accept(getLootTableRegistryKey("armorsmith", lootTier), DungeonLootTables.armorsmith(lootTier, registryLookup));
            biConsumer.accept(getLootTableRegistryKey("weaponsmith", lootTier), DungeonLootTables.weaponsmith(lootTier, registryLookup));
            biConsumer.accept(getLootTableRegistryKey("toolsmith", lootTier), DungeonLootTables.toolsmith(lootTier, registryLookup));
            biConsumer.accept(getLootTableRegistryKey("enchanter", lootTier), DungeonLootTables.enchanter(lootTier, registryLookup));
        }
    }
}

