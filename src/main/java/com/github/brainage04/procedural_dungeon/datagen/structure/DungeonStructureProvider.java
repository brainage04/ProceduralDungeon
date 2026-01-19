package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.util.DungeonUtils;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureTerrainAdaptation;
import net.minecraft.world.gen.YOffset;
import net.minecraft.world.gen.heightprovider.ConstantHeightProvider;
import net.minecraft.world.gen.structure.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DungeonStructureProvider extends FabricDynamicRegistryProvider {
    public DungeonStructureProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    public static RegistryKey<Structure> create(String name) {
        return RegistryKey.of(RegistryKeys.STRUCTURE, ProceduralDungeon.of(name));
    }

    private void register(Entries entries, RegistryKey<Structure> key, JigsawStructure value) {
        entries.add(key, value);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
        RegistryEntryLookup<Biome> biomeLookup = registries.getOrThrow(RegistryKeys.BIOME);
        RegistryEntryLookup<StructurePool> templatePoolLookup = registries.getOrThrow(RegistryKeys.TEMPLATE_POOL);

        for (DungeonUtils.ThemeTierKeyPair themeTierKeyPair : DungeonUtils.THEME_TIER_KEY_COMBINATIONS) {
            String start = "%s/start".formatted(themeTierKeyPair.key());
            register(
                    entries,
                    create(start),
                    new JigsawStructure(
                            new Structure.Config.Builder(
                                    biomeLookup.getOrThrow(BiomeTags.STRONGHOLD_HAS_STRUCTURE)
                            )
                                    .step(GenerationStep.Feature.UNDERGROUND_STRUCTURES)
                                    .terrainAdaptation(StructureTerrainAdaptation.BURY)
                                    .build(),
                            templatePoolLookup.getOrThrow(
                                    RegistryKeyUtils.create(RegistryKeys.TEMPLATE_POOL, themeTierKeyPair.key())
                            ),
                            Optional.of(Identifier.ofVanilla("start")),
                            themeTierKeyPair.themeTierPair().tier().size,
                            ConstantHeightProvider.create(YOffset.fixed(0)),
                            true,
                            Optional.of(Heightmap.Type.WORLD_SURFACE_WG),
                            new JigsawStructure.MaxDistanceFromCenter(116),
                            List.of(),
                            new DimensionPadding(0, 0),
                            StructureLiquidSettings.IGNORE_WATERLOGGING
                    )
            );
        }
    }

    @Override
    public String getName() {
        return "Dungeon Structures";
    }
}
