package com.github.brainage04.procedural_dungeon.datagen;

import com.github.brainage04.procedural_dungeon.datagen.core.ProceduralDungeonGenerator;
import com.github.brainage04.procedural_dungeon.datagen.enchantment.DungeonEnchantmentProvider;
import com.github.brainage04.procedural_dungeon.datagen.enchantment.DungeonEnchantmentTagProviders;
import com.github.brainage04.procedural_dungeon.datagen.loot_table.DungeonLootTableProvider;
import com.github.brainage04.procedural_dungeon.datagen.structure.DungeonStructureVariantProvider;
import com.github.brainage04.procedural_dungeon.datagen.structure.DungeonWorldgenProvider;
import com.github.brainage04.procedural_dungeon.datagen.structure.EntranceStructureProvider;
import com.github.brainage04.procedural_dungeon.datagen.structure.ProgressionRoomStructureProvider;
import com.github.brainage04.procedural_dungeon.datagen.structure.PuzzleRoomStructureProvider;
import com.github.brainage04.procedural_dungeon.enchantment.DungeonDamageTypes;
import com.github.brainage04.procedural_dungeon.enchantment.DungeonEnchantments;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;

public class ModDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator gen) {
        FabricDataGenerator.Pack pack = gen.createPack();

        pack.addProvider(DungeonLootTableProvider::new);

        pack.addProvider(ProceduralDungeonGenerator::new);

        pack.addProvider(DungeonWorldgenProvider::new);

        pack.addProvider(DungeonStructureVariantProvider::new);

        pack.addProvider(EntranceStructureProvider::new);

        pack.addProvider(ProgressionRoomStructureProvider::new);
        pack.addProvider(PuzzleRoomStructureProvider::new);

        pack.addProvider(DungeonEnchantmentProvider::new);
        pack.addProvider(DungeonEnchantmentTagProviders.ItemTagProvider::new);
        pack.addProvider(DungeonEnchantmentTagProviders.EntityTypeTagProvider::new);
        pack.addProvider(DungeonEnchantmentTagProviders.EnchantmentTagProvider::new);
        pack.addProvider(DungeonEnchantmentTagProviders.DamageTypeTagProvider::new);
    }

    @Override
    public void buildRegistry(RegistrySetBuilder registryBuilder) {
        registryBuilder.add(Registries.DAMAGE_TYPE, DungeonDamageTypes::bootstrap);
        registryBuilder.add(Registries.ENCHANTMENT, DungeonEnchantments::bootstrap);
    }
}
