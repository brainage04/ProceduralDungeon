package com.github.brainage04.procedural_dungeon.datagen.core;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.datagen.loot_table.DungeonLootTableProvider;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.ReplaceJigsawPoolProcessor;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.ReplaceLootByOldTableModifier;
import com.github.brainage04.procedural_dungeon.datagen.structure.DungeonJigsawPoolReplacements;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.processor.*;
import net.minecraft.structure.rule.*;

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
                generateProcessorList(entries, key, tier, theme);
            }
        }
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
        return create(List.of(new ReplaceJigsawPoolProcessor(DungeonJigsawPoolReplacements.create(key, tier))));
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Generator";
    }
}
