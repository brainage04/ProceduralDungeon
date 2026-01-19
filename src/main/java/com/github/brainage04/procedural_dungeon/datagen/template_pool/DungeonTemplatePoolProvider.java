package com.github.brainage04.procedural_dungeon.datagen.template_pool;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.util.DungeonUtils;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.pool.*;
import net.minecraft.structure.processor.StructureProcessorList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DungeonTemplatePoolProvider extends FabricDynamicRegistryProvider {
    public DungeonTemplatePoolProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    private static RegistryKey<StructurePool> create(String name) {
        return RegistryKey.of(RegistryKeys.TEMPLATE_POOL, ProceduralDungeon.of(name));
    }

    private static void register(Entries entries, RegistryKey<StructurePool> key, StructurePool value) {
        entries.add(key, value);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
        for (DungeonUtils.ThemeTierKeyPair themeTierKeyPair : DungeonUtils.THEME_TIER_KEY_COMBINATIONS) {
            RegistryEntry<StructurePool> empty = registries.getEntryOrThrow(StructurePools.EMPTY);

            RegistryKey<StructureProcessorList> structureProcessorListKey = RegistryKey.of(RegistryKeys.PROCESSOR_LIST, ProceduralDungeon.of(themeTierKeyPair.key()));
            RegistryEntry<StructureProcessorList> structureProcessorListEntry = registries.getEntryOrThrow(structureProcessorListKey);

            String start = "%s/start".formatted(themeTierKeyPair.key());
            register(
                    entries,
                    create(start),
                    new StructurePool(
                            empty,
                            List.of(
                                    getWeightedSinglePoolElement(start, structureProcessorListEntry)
                            )
                    )
            );

            register(
                    entries,
                    create("%s/hallway".formatted(themeTierKeyPair.key())),
                    new StructurePool(
                            empty,
                            List.of(
                                    getWeightedSinglePoolElement("%s/hallway/small".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/medium".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/large".formatted(themeTierKeyPair.key()), structureProcessorListEntry)
                            )
                    )
            );

            register(
                    entries,
                    create("%s/hallway/end".formatted(themeTierKeyPair.key())),
                    new StructurePool(
                            empty,
                            List.of(
                                    getWeightedSinglePoolElement("%s/hallway/end/small".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/end/medium".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/end/large".formatted(themeTierKeyPair.key()), structureProcessorListEntry)
                            )
                    )
            );

            register(
                    entries,
                    create("%s/hallway/loot".formatted(themeTierKeyPair.key())),
                    new StructurePool(
                            empty,
                            List.of(
                                    getWeightedSinglePoolElement("%s/hallway/loot/small".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/loot/medium".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/loot/large".formatted(themeTierKeyPair.key()), structureProcessorListEntry)
                            )
                    )
            );

            register(
                    entries,
                    create("%s/hallway/room".formatted(themeTierKeyPair.key())),
                    new StructurePool(
                            empty,
                            List.of(
                                    getWeightedSinglePoolElement("%s/hallway/room/armorsmith".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/room/dropper".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/room/enchanter".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/room/spawner_corridor".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/room/staircase_diagonal_down".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/room/staircase_diagonal_up".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/room/staircase_spiral_down".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/room/staircase_spiral_up".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/room/toolsmith".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/room/weaponsmith".formatted(themeTierKeyPair.key()), structureProcessorListEntry)
                            )
                    )
            );

            register(
                    entries,
                    create("%s/hallway/trap".formatted(themeTierKeyPair.key())),
                    new StructurePool(
                            empty,
                            List.of(
                                    getWeightedSinglePoolElement("%s/hallway/trap/dripstone".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/trap/lava".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/trap/negative_potions".formatted(themeTierKeyPair.key()), structureProcessorListEntry),
                                    getWeightedSinglePoolElement("%s/hallway/trap/spawners".formatted(themeTierKeyPair.key()), structureProcessorListEntry)
                            )
                    )
            );
        }
    }

    private static @NotNull Pair<StructurePoolElement, Integer> getWeightedSinglePoolElement(String hallway, RegistryEntry<StructureProcessorList> structureProcessorListEntry) {
        return Pair.of(
                new SinglePoolElement(
                        ProceduralDungeon.of(hallway),
                        structureProcessorListEntry,
                        StructurePool.Projection.RIGID,
                        Optional.of(StructureLiquidSettings.IGNORE_WATERLOGGING)
                ), 1
        );
    }

    @Override
    public String getName() {
        return "Dungeon Template Pools";
    }
}
