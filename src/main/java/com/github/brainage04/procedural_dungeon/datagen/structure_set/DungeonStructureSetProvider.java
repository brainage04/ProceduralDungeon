package com.github.brainage04.procedural_dungeon.datagen.structure_set;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.DungeonUtils;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.gen.chunk.placement.*;
import net.minecraft.world.gen.structure.Structure;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DungeonStructureSetProvider extends FabricDynamicRegistryProvider {
    public DungeonStructureSetProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    private static RegistryKey<StructureSet> create(String name) {
        return RegistryKey.of(RegistryKeys.STRUCTURE_SET, ProceduralDungeon.of(name));
    }

    private static void register(Entries entries, RegistryKey<StructureSet> key, StructureSet value) {
        entries.add(key, value);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
        for (DungeonUtils.ThemeTierKeyPair themeTierKeyPair : DungeonUtils.THEME_TIER_KEY_COMBINATIONS) {
            RegistryKey<StructureSet> key = create(themeTierKeyPair.key());

            RegistryKey<Structure> structureKey = RegistryKeyUtils.create(RegistryKeys.STRUCTURE, themeTierKeyPair.key());
            RegistryEntryLookup<Structure> structureLookup = registries.getOrThrow(RegistryKeys.STRUCTURE);
            RegistryEntry<Structure> structureEntry = structureLookup.getOrThrow(structureKey);

            DungeonTier tier = themeTierKeyPair.themeTierPair().tier();

            Random random = new Random();

            register(
                    entries,
                    key,
                    new StructureSet(
                            structureEntry,
                            new RandomSpreadStructurePlacement(
                                    tier.spacing,
                                    tier.separation,
                                    SpreadType.LINEAR,
                                    random.nextInt()
                            )
                    )
            );
        }
    }

    @Override
    public String getName() {
        return "Dungeon Structure Sets";
    }
}
