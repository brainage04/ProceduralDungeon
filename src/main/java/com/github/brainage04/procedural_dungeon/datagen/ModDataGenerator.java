package com.github.brainage04.procedural_dungeon.datagen;

import com.github.brainage04.procedural_dungeon.datagen.loot_table.DungeonLootTableProvider;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.DungeonProcessorListProvider;
import com.github.brainage04.procedural_dungeon.datagen.structure.DungeonStructureProvider;
import com.github.brainage04.procedural_dungeon.datagen.structure_set.DungeonStructureSetProvider;
import com.github.brainage04.procedural_dungeon.datagen.template_pool.DungeonTemplatePoolProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class ModDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator gen) {
        FabricDataGenerator.Pack pack = gen.createPack();
        pack.addProvider(DungeonLootTableProvider::new);
        pack.addProvider(DungeonProcessorListProvider::new);
        pack.addProvider(DungeonStructureProvider::new);
        pack.addProvider(DungeonStructureSetProvider::new);
        pack.addProvider(DungeonTemplatePoolProvider::new);
    }
}
