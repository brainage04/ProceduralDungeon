package com.github.brainage04.procedural_dungeon.datagen.processor_list;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.google.common.collect.ImmutableList;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.processor.*;
import net.minecraft.structure.rule.AlwaysTrueRuleTest;
import net.minecraft.structure.rule.RandomBlockMatchRuleTest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class DungeonProcessorListProvider extends FabricDynamicRegistryProvider {
    public DungeonProcessorListProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    // dungeon-specific
    private static final RegistryKey<StructureProcessorList> DUNGEON_BOOKSHELF = create("dungeon/bookshelf");
    private static final RegistryKey<StructureProcessorList> DUNGEON_MINERALS = create("dungeon/minerals");

    // general purpose
    private static final RegistryKey<StructureProcessorList> GENERIC_AIR = create("generic/air");
    private static final RegistryKey<StructureProcessorList> GENERIC_DECAY = create("generic/decay");

    // dungeon themes
    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_OVERWORLD_COBBLESTONE = create("dungeon/theme/overworld/cobblestone");
    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_OVERWORLD_DEEPSLATE = create("dungeon/theme/overworld/deepslate");
    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_OVERWORLD_SCULK = create("dungeon/theme/overworld/sculk");

    private static final List<RegistryKey<StructureProcessorList>> OVERWORLD_THEMES = List.of(
            DUNGEON_THEME_OVERWORLD_COBBLESTONE,
            DUNGEON_THEME_OVERWORLD_DEEPSLATE,
            DUNGEON_THEME_OVERWORLD_SCULK
    );

    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_NETHER_NETHER_WASTES = create("dungeon/theme/nether/nether_wastes");
    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_NETHER_CRIMSON_FOREST = create("dungeon/theme/nether/crimson_forest");
    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_NETHER_WARPED_FOREST = create("dungeon/theme/nether/warped_forest");
    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_NETHER_BASALT_DELTAS = create("dungeon/theme/nether/basalt_deltas");
    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_NETHER_SOUL_SAND_VALLEY = create("dungeon/theme/nether/soul_sand_valley");
    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_NETHER_NETHER_FORTRESS = create("dungeon/theme/nether/nether_fortress");
    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_NETHER_BASTION = create("dungeon/theme/nether/bastion");

    private static final List<RegistryKey<StructureProcessorList>> NETHER_THEMES = List.of(
            DUNGEON_THEME_NETHER_NETHER_WASTES,
            DUNGEON_THEME_NETHER_CRIMSON_FOREST,
            DUNGEON_THEME_NETHER_WARPED_FOREST,
            DUNGEON_THEME_NETHER_BASALT_DELTAS,
            DUNGEON_THEME_NETHER_SOUL_SAND_VALLEY,
            DUNGEON_THEME_NETHER_NETHER_FORTRESS,
            DUNGEON_THEME_NETHER_BASTION
    );

    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_END_END_STONE = create("dungeon/theme/end/end_stone");
    private static final RegistryKey<StructureProcessorList> DUNGEON_THEME_END_END_CITY = create("dungeon/theme/end/end_city");

    private static final List<RegistryKey<StructureProcessorList>> END_THEMES = List.of(
            DUNGEON_THEME_END_END_STONE,
            DUNGEON_THEME_END_END_CITY
    );

    private static final List<RegistryKey<StructureProcessorList>> ALL_THEMES = Stream.of(
            OVERWORLD_THEMES,
            NETHER_THEMES,
            END_THEMES
    ).flatMap(Collection::stream).toList();

    private static RegistryKey<StructureProcessorList> create(String name) {
        return RegistryKey.of(RegistryKeys.PROCESSOR_LIST, ProceduralDungeon.of(name));
    }

    private static void register(FabricDynamicRegistryProvider.Entries entries, RegistryKey<StructureProcessorList> key, List<StructureProcessor> list) {
        entries.add(key, new StructureProcessorList(list));
    }

    private record BasicProcessorRule(Block input, float probability, Block output) {}

    private static void registerBasic(FabricDynamicRegistryProvider.Entries entries, RegistryKey<StructureProcessorList> key, List<BasicProcessorRule> rules) {
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

        register(entries, key, list);
    }

    private static void registerDungeon(FabricDynamicRegistryProvider.Entries entries) {
        registerBasic(entries, DUNGEON_BOOKSHELF, ImmutableList.of(
                new BasicProcessorRule(Blocks.BOOKSHELF, 0.2f, Blocks.CHISELED_BOOKSHELF),
                new BasicProcessorRule(Blocks.BOOKSHELF, 0.2f, Blocks.OAK_PLANKS),
                new BasicProcessorRule(Blocks.OAK_LOG, 0.4f, Blocks.STRIPPED_OAK_LOG)
        ));

        registerBasic(entries, DUNGEON_MINERALS, ImmutableList.of(
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
    }

    private static void registerGeneric(FabricDynamicRegistryProvider.Entries entries) {
        registerBasic(entries, GENERIC_AIR, ImmutableList.of(
                new BasicProcessorRule(Blocks.AIR, 0.01f, Blocks.COBWEB)
        ));

        register(entries, GENERIC_DECAY, ImmutableList.of(
                new BlockAgeStructureProcessor(0.4f),
                new BlockRotStructureProcessor(0.98f)
        ));
    }

    private static void registerOverworld(FabricDynamicRegistryProvider.Entries entries) {
        registerBasic(entries, DUNGEON_THEME_OVERWORLD_COBBLESTONE, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.STONE_BRICKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_STONE_BRICKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CHISELED_STONE_BRICKS)
        ));

        registerBasic(entries, DUNGEON_THEME_OVERWORLD_DEEPSLATE, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.POLISHED_DEEPSLATE),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_DEEPSLATE_BRICKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.DEEPSLATE_BRICKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.DEEPSLATE)
        ));

        registerBasic(entries, DUNGEON_THEME_OVERWORLD_SCULK, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.001f, Blocks.SCULK_SHRIEKER),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.001f, Blocks.REINFORCED_DEEPSLATE),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SCULK_SENSOR),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SCULK_CATALYST),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.SCULK)
        ));
    }

    private static void registerNether(FabricDynamicRegistryProvider.Entries entries) {
        registerBasic(entries, DUNGEON_THEME_NETHER_NETHER_WASTES, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.001f, Blocks.ANCIENT_DEBRIS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.005f, Blocks.LAVA),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.025f, Blocks.NETHER_GOLD_ORE),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.025f, Blocks.NETHER_QUARTZ_ORE),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.NETHERRACK)
        ));

        registerBasic(entries, DUNGEON_THEME_NETHER_CRIMSON_FOREST, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRIMSON_PLANKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRIMSON_NYLIUM),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRIMSON_HYPHAE),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.NETHER_WART_BLOCK)
        ));

        registerBasic(entries, DUNGEON_THEME_NETHER_WARPED_FOREST, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.WARPED_PLANKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.WARPED_NYLIUM),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.WARPED_HYPHAE),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.WARPED_WART_BLOCK)
        ));

        registerBasic(entries, DUNGEON_THEME_NETHER_BASALT_DELTAS, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.POLISHED_BASALT),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SMOOTH_BASALT),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.BASALT)
        ));

        registerBasic(entries, DUNGEON_THEME_NETHER_SOUL_SAND_VALLEY, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SOUL_SAND),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.SOUL_SOIL)
        ));

        registerBasic(entries, DUNGEON_THEME_NETHER_NETHER_FORTRESS, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.RED_NETHER_BRICKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CHISELED_NETHER_BRICKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_NETHER_BRICKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.NETHER_BRICKS)
        ));

        registerBasic(entries, DUNGEON_THEME_NETHER_BASTION, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.01f, Blocks.GILDED_BLACKSTONE),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.POLISHED_BLACKSTONE),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CHISELED_POLISHED_BLACKSTONE),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.BLACKSTONE)
        ));
    }

    private static void registerEnd(FabricDynamicRegistryProvider.Entries entries) {
        registerBasic(entries, DUNGEON_THEME_END_END_STONE, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.END_STONE_BRICKS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.END_STONE)
        ));

        registerBasic(entries, DUNGEON_THEME_END_END_CITY, ImmutableList.of(
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.01f, Blocks.PURPLE_STAINED_GLASS),
                new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.PURPUR_PILLAR),
                new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.PURPUR_BLOCK)
        ));
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
        registerDungeon(entries);
        registerGeneric(entries);
        registerOverworld(entries);
        registerNether(entries);
        registerEnd(entries);
    }

    @Override
    public String getName() {
        return "Dungeon Processor Lists";
    }
}
