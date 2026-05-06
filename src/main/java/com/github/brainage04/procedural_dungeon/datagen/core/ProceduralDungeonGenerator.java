package com.github.brainage04.procedural_dungeon.datagen.core;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.datagen.loot_table.DungeonLootTableProvider;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.ReplaceJigsawPoolProcessor;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.ReplaceLootByOldTableModifier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.structure.pool.StructurePools;
import net.minecraft.structure.processor.*;
import net.minecraft.structure.rule.*;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureTerrainAdaptation;
import net.minecraft.world.gen.YOffset;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.SpreadType;
import net.minecraft.world.gen.heightprovider.ConstantHeightProvider;
import net.minecraft.world.gen.structure.DimensionPadding;
import net.minecraft.world.gen.structure.JigsawStructure;
import net.minecraft.world.gen.structure.Structure;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class ProceduralDungeonGenerator extends FabricDynamicRegistryProvider {
    public ProceduralDungeonGenerator(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    public static StructureProcessorList createLootTable(DungeonTier tier) {
        return create(List.of(
                new RuleStructureProcessor(List.of(
                        new StructureProcessorRule(
                                new BlockMatchRuleTest(Blocks.CHEST),
                                AlwaysTrueRuleTest.INSTANCE,
                                AlwaysTruePosRuleTest.INSTANCE,
                                Blocks.CHEST.getDefaultState(),
                                new ReplaceLootByOldTableModifier(Map.ofEntries(
                                        Map.entry(
                                                DungeonLootTableProvider.getLootTableId("hallway_end"),
                                                DungeonLootTableProvider.getLootTableId("hallway_end", tier)
                                        ),
                                        Map.entry(
                                                DungeonLootTableProvider.getLootTableId("hallway_loot"),
                                                DungeonLootTableProvider.getLootTableId("hallway_loot", tier)
                                        ),
                                        Map.entry(
                                                DungeonLootTableProvider.getLootTableId("armorsmith"),
                                                DungeonLootTableProvider.getLootTableId("armorsmith", tier)
                                        ),
                                        Map.entry(
                                                DungeonLootTableProvider.getLootTableId("weaponsmith"),
                                                DungeonLootTableProvider.getLootTableId("weaponsmith", tier)
                                        ),
                                        Map.entry(
                                                DungeonLootTableProvider.getLootTableId("toolsmith"),
                                                DungeonLootTableProvider.getLootTableId("toolsmith", tier)
                                        ),
                                        Map.entry(
                                                DungeonLootTableProvider.getLootTableId("enchanter"),
                                                DungeonLootTableProvider.getLootTableId("enchanter", tier)
                                        )
                                ))
                        )
                ))
        ));
    }

    private static Pair<StructurePoolElement, Integer> getWeightedSinglePoolElement(String name, RegistryEntry<StructureProcessorList> structureProcessorListEntry) {
        return Pair.of(
                new SinglePoolElement(
                        Either.left(ProceduralDungeon.of(name)),
                        structureProcessorListEntry,
                        StructurePool.Projection.RIGID,
                        Optional.of(StructureLiquidSettings.IGNORE_WATERLOGGING)
                ), 1
        );
    }

    private record BasicProcessorRule(Block input, float probability, Block output) {}

    public static StructureProcessorList create(List<StructureProcessor> rules) {
        return new StructureProcessorList(rules);
    }

    private static StructureProcessorList createBasic(List<BasicProcessorRule> rules) {
        List<StructureProcessor> list = new ArrayList<>(rules.size());

        for (BasicProcessorRule rule : rules) {
            list.add(new RuleStructureProcessor(List.of(
                    new StructureProcessorRule(
                            new RandomBlockMatchRuleTest(rule.input, rule.probability),
                            AlwaysTrueRuleTest.INSTANCE,
                            rule.output.getDefaultState()
                    )
            )));
        }

        return create(list);
    }

    private static final StructureProcessorList DUNGEON_BOOKSHELF = createBasic(List.of(
            new BasicProcessorRule(Blocks.BOOKSHELF, 0.3f, Blocks.CHISELED_BOOKSHELF),
            new BasicProcessorRule(Blocks.OAK_LOG, 0.3f, Blocks.STRIPPED_OAK_LOG)
    ));

    private static final StructureProcessorList DUNGEON_MINERALS = createBasic(List.of(
            new BasicProcessorRule(Blocks.COPPER_BLOCK, 0.1f, Blocks.RAW_COPPER_BLOCK),
            new BasicProcessorRule(Blocks.COPPER_BLOCK, 0.1f, Blocks.COPPER_ORE),
            new BasicProcessorRule(Blocks.COPPER_BLOCK, 0.1f, Blocks.DEEPSLATE_COPPER_ORE),

            new BasicProcessorRule(Blocks.IRON_BLOCK, 0.1f, Blocks.RAW_IRON_BLOCK),
            new BasicProcessorRule(Blocks.IRON_BLOCK, 0.1f, Blocks.IRON_ORE),
            new BasicProcessorRule(Blocks.IRON_BLOCK, 0.1f, Blocks.DEEPSLATE_IRON_ORE),

            new BasicProcessorRule(Blocks.GOLD_BLOCK, 0.1f, Blocks.RAW_GOLD_BLOCK),
            new BasicProcessorRule(Blocks.GOLD_BLOCK, 0.1f, Blocks.GOLD_ORE),
            new BasicProcessorRule(Blocks.GOLD_BLOCK, 0.1f, Blocks.DEEPSLATE_GOLD_ORE),

            new BasicProcessorRule(Blocks.DIAMOND_BLOCK, 0.15f, Blocks.DIAMOND_ORE),
            new BasicProcessorRule(Blocks.DIAMOND_BLOCK, 0.15f, Blocks.DEEPSLATE_DIAMOND_ORE),

            new BasicProcessorRule(Blocks.LAPIS_BLOCK, 0.15f, Blocks.LAPIS_ORE),
            new BasicProcessorRule(Blocks.LAPIS_BLOCK, 0.15f, Blocks.DEEPSLATE_LAPIS_ORE)
    ));

    private static final StructureProcessorList GENERIC_AIR = createBasic(List.of(
            new BasicProcessorRule(Blocks.AIR, 0.01f, Blocks.COBWEB)
    ));

    private static final StructureProcessorList GENERIC_DECAY = create(List.of(
            new BlockAgeStructureProcessor(0.4f),
            new BlockRotStructureProcessor(0.98f)
    ));

    public static final StructureProcessorList CHESTS_TIER_1 = createLootTable(DungeonTier.TIER_1);
    public static final StructureProcessorList CHESTS_TIER_2 = createLootTable(DungeonTier.TIER_2);
    public static final StructureProcessorList CHESTS_TIER_3 = createLootTable(DungeonTier.TIER_3);
    public static final StructureProcessorList CHESTS_TIER_4 = createLootTable(DungeonTier.TIER_4);
    public static final StructureProcessorList CHESTS_TIER_5 = createLootTable(DungeonTier.TIER_5);

    public static final StructureProcessorList COBBLESTONE = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.STONE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_STONE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CHISELED_STONE_BRICKS)
    ));

    public static final StructureProcessorList DEEPSLATE = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.POLISHED_DEEPSLATE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_DEEPSLATE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.DEEPSLATE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.DEEPSLATE)
    ));

    public static final StructureProcessorList SCULK = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.001f, Blocks.SCULK_SHRIEKER),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.001f, Blocks.REINFORCED_DEEPSLATE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SCULK_SENSOR),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SCULK_CATALYST),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.SCULK)
    ));

    public static final StructureProcessorList NETHER_WASTES = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.001f, Blocks.ANCIENT_DEBRIS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.005f, Blocks.LAVA),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.025f, Blocks.NETHER_GOLD_ORE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.025f, Blocks.NETHER_QUARTZ_ORE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.NETHERRACK)
    ));

    public static final StructureProcessorList CRIMSON_FOREST = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRIMSON_PLANKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRIMSON_NYLIUM),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRIMSON_HYPHAE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.NETHER_WART_BLOCK)
    ));

    public static final StructureProcessorList WARPED_FOREST = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.WARPED_PLANKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.WARPED_NYLIUM),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.WARPED_HYPHAE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.WARPED_WART_BLOCK)
    ));

    public static final StructureProcessorList BASALT_DELTAS = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.POLISHED_BASALT),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SMOOTH_BASALT),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.BASALT)
    ));

    public static final StructureProcessorList SOUL_SAND_VALLEY = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SOUL_SAND),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.SOUL_SOIL)
    ));

    public static final StructureProcessorList NETHER_FORTRESS = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.RED_NETHER_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CHISELED_NETHER_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_NETHER_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.NETHER_BRICKS)
    ));

    public static final StructureProcessorList BASTION = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.01f, Blocks.GILDED_BLACKSTONE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.POLISHED_BLACKSTONE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CHISELED_POLISHED_BLACKSTONE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.BLACKSTONE)
    ));

    public static final StructureProcessorList END_STONE = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.END_STONE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.END_STONE)
    ));

    public static final StructureProcessorList END_CITY = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.01f, Blocks.PURPLE_STAINED_GLASS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.PURPUR_PILLAR),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.PURPUR_BLOCK)
    ));

    @Override
    protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
        for (DungeonTheme theme : DungeonTheme.values()) {
            for (DungeonTier tier : DungeonTier.values()) {
                String key = RegistryKeyUtils.getKeyString(theme, tier);

                // structure set -> structure -> structure pool -> processor list
                StructureProcessorList processorList = generateProcessorList(entries, key, tier, theme);
                StructurePool startPool = generateTemplatePools(entries, key, processorList);
                Structure startStructure = generateStructure(entries, key, tier, startPool);
                generateStructureSet(entries, key, tier, theme, startStructure);
            }
        }
    }

    private static void generateStructureSet(Entries entries, String key, DungeonTier tier, DungeonTheme theme, Structure start) {
        if (tier.spacing <= tier.separation) {
            throw new IllegalArgumentException("Spacing (%d) must be larger than separation (%d)".formatted(tier.spacing, tier.separation));
        }

        Random random = new Random();

        entries.add(
                RegistryKeyUtils.create(RegistryKeys.STRUCTURE_SET, key),
                new StructureSet(
                        RegistryEntry.of(start),
                        new RandomSpreadStructurePlacement(
                                tier.spacing,
                                tier.separation,
                                SpreadType.LINEAR,
                                random.nextInt(0, Integer.MAX_VALUE)
                        )
                )
        );

    }

    private static Structure generateStructure(Entries entries, String key, DungeonTier tier, StructurePool startPool) {
        RegistryEntryLookup<Biome> biomeLookup = entries.getLookup(RegistryKeys.BIOME);

        JigsawStructure startStructure = new JigsawStructure(
                new Structure.Config.Builder(
                        biomeLookup.getOrThrow(BiomeTags.STRONGHOLD_HAS_STRUCTURE)
                )
                        .step(GenerationStep.Feature.UNDERGROUND_STRUCTURES)
                        .terrainAdaptation(StructureTerrainAdaptation.BURY)
                        .build(),
                RegistryEntry.of(startPool),
                Optional.of(Identifier.ofVanilla("start")),
                tier.size,
                ConstantHeightProvider.create(YOffset.fixed(0)),
                true,
                Optional.of(Heightmap.Type.WORLD_SURFACE_WG),
                new JigsawStructure.MaxDistanceFromCenter(116),
                List.of(),
                new DimensionPadding(0, 0),
                StructureLiquidSettings.IGNORE_WATERLOGGING
        );
        var structureKey = RegistryKeyUtils.create(RegistryKeys.STRUCTURE, key);
        entries.add(structureKey, startStructure);

        return startStructure;
    }

    private static StructurePool generateTemplatePools(Entries entries, String key, StructureProcessorList structureProcessorList) {
        RegistryEntryLookup<StructurePool> structurePoolLookup =
                entries.getLookup(RegistryKeys.TEMPLATE_POOL);
        RegistryEntry<StructurePool> empty =
                structurePoolLookup.getOrThrow(StructurePools.EMPTY);
        RegistryEntry<StructureProcessorList> structureProcessorListEntry =
                RegistryEntry.of(structureProcessorList);

        String startKey = "%s/start".formatted(key);
        StructurePool startStructure = new StructurePool(
                empty,
                List.of(
                        getWeightedSinglePoolElement("dungeon/start", structureProcessorListEntry)
                )
        );
        var startPoolKey = RegistryKeyUtils.create(RegistryKeys.TEMPLATE_POOL, startKey);
        entries.add(startPoolKey, startStructure);

        StructurePool hallway = new StructurePool(
                empty,
                List.of(
                        getWeightedSinglePoolElement("dungeon/hallway/small", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/medium", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/large", structureProcessorListEntry)
                )
        );
        entries.add(RegistryKeyUtils.create(RegistryKeys.TEMPLATE_POOL, "%s/hallway".formatted(key)), hallway);

        StructurePool hallwayEnd = new StructurePool(
                empty,
                List.of(
                        getWeightedSinglePoolElement("dungeon/hallway/end/small", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/end/medium", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/end/large", structureProcessorListEntry)
                )
        );
        entries.add(RegistryKeyUtils.create(RegistryKeys.TEMPLATE_POOL, "%s/hallway/end".formatted(key)), hallwayEnd);

        StructurePool hallwayLoot = new StructurePool(
                empty,
                List.of(
                        getWeightedSinglePoolElement("dungeon/hallway/loot/small", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/loot/medium", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/loot/large", structureProcessorListEntry)
                )
        );
        entries.add(RegistryKeyUtils.create(RegistryKeys.TEMPLATE_POOL, "%s/hallway/loot".formatted(key)), hallwayLoot);

        StructurePool hallwayRoom = new StructurePool(
                empty,
                List.of(
                        getWeightedSinglePoolElement("dungeon/hallway/room/armorsmith", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/room/enchanter", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/room/spawner_corridor", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/room/staircase_diagonal_down", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/room/staircase_diagonal_up", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/room/staircase_spiral_down", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/room/staircase_spiral_up", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/room/toolsmith", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/room/weaponsmith", structureProcessorListEntry)
                )
        );
        entries.add(RegistryKeyUtils.create(RegistryKeys.TEMPLATE_POOL, "%s/hallway/room".formatted(key)), hallwayRoom);

        StructurePool hallwayTrap = new StructurePool(
                empty,
                List.of(
                        getWeightedSinglePoolElement("dungeon/hallway/trap/dripstone", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/trap/lava", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/trap/negative_potions", structureProcessorListEntry),
                        getWeightedSinglePoolElement("dungeon/hallway/trap/spawners", structureProcessorListEntry)
                )
        );
        entries.add(RegistryKeyUtils.create(RegistryKeys.TEMPLATE_POOL, "%s/hallway/trap".formatted(key)), hallwayTrap);

        return startStructure;
    }

    private static StructureProcessorList generateProcessorList(Entries entries, String key, DungeonTier tier, DungeonTheme theme) {
        StructureProcessorList structureProcessorList = new StructureProcessorList(
                Stream.of(
                        DUNGEON_BOOKSHELF,
                        DUNGEON_MINERALS,
                        GENERIC_AIR,
                        GENERIC_DECAY,
                        tier.getBaseProcessorList(),
                        theme.baseProcessorList,
                        createJigsawPoolReplacements(key, tier)
                ).flatMap(structureProcessorList1 -> structureProcessorList1.getList().stream()).toList()
        );

        var processorListKey = RegistryKeyUtils.create(RegistryKeys.PROCESSOR_LIST, key);
        entries.add(processorListKey, structureProcessorList);

        return structureProcessorList;
    }

    private static StructureProcessorList createJigsawPoolReplacements(String key, DungeonTier tier) {
        return create(List.of(new ReplaceJigsawPoolProcessor(Map.ofEntries(
                Map.entry(ProceduralDungeon.of("dungeon/hallway"), ProceduralDungeon.of("%s/hallway".formatted(key))),
                Map.entry(ProceduralDungeon.of("dungeon/hallway/end"), ProceduralDungeon.of("%s/hallway/end".formatted(key))),
                Map.entry(ProceduralDungeon.of("dungeon/hallway/loot"), ProceduralDungeon.of("%s/hallway/loot".formatted(key))),
                Map.entry(ProceduralDungeon.of("dungeon/hallway/room"), ProceduralDungeon.of("%s/hallway/room".formatted(key))),
                Map.entry(ProceduralDungeon.of("dungeon/hallway/trap"), ProceduralDungeon.of("%s/hallway/trap".formatted(key))),
                Map.entry(ProceduralDungeon.of("dungeon/spawner/tier_1"), ProceduralDungeon.of("dungeon/spawner/tier_%d".formatted(tier.tier)))
        ))));
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Generator";
    }
}
