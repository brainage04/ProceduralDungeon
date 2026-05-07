package com.github.brainage04.procedural_dungeon.datagen;

import com.github.brainage04.procedural_dungeon.datagen.loot_table.DungeonLootTableProvider;
import com.github.brainage04.procedural_dungeon.datagen.core.ProceduralDungeonGenerator;
import com.github.brainage04.procedural_dungeon.datagen.structure.DungeonWorldgenProvider;
import com.github.brainage04.procedural_dungeon.datagen.structure.SpawnerStructureProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class ModDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator gen) {
        FabricDataGenerator.Pack pack = gen.createPack();

        pack.addProvider(DungeonLootTableProvider::new);

        pack.addProvider(ProceduralDungeonGenerator::new);

        pack.addProvider(DungeonWorldgenProvider::new);

        pack.addProvider(SpawnerStructureProvider::new);
    }
}
