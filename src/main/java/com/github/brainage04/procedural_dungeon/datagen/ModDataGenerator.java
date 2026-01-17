package com.github.brainage04.procedural_dungeon.datagen;

import com.github.brainage04.procedural_dungeon.datagen.loot_table.DungeonLootTableProvider;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.DungeonProcessorLists;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.registry.RegistryBuilder;
import net.minecraft.registry.RegistryKeys;

public class ModDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator gen) {
        FabricDataGenerator.Pack pack = gen.createPack();
        pack.addProvider(DungeonLootTableProvider::new);
    }

    @Override
    public void buildRegistry(RegistryBuilder registryBuilder) {
        registryBuilder.addRegistry(RegistryKeys.PROCESSOR_LIST, DungeonProcessorLists::bootstrap);
    }
}
