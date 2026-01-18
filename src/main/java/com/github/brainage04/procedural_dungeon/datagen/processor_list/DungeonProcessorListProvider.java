package com.github.brainage04.procedural_dungeon.datagen.processor_list;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.processor.*;
import net.minecraft.structure.rule.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DungeonProcessorListProvider extends FabricDynamicRegistryProvider {
    public DungeonProcessorListProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    private record BasicProcessorRule(Block input, float probability, Block output) {}

    private static StructureProcessorList create(List<StructureProcessor> rules) {
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

    private static void register(FabricDynamicRegistryProvider.Entries entries, RegistryKey<StructureProcessorList> key, List<StructureProcessor> list) {
        entries.add(key, new StructureProcessorList(list));
    }

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

    private static RegistryKey<StructureProcessorList> create(String name) {
        return RegistryKey.of(RegistryKeys.PROCESSOR_LIST, ProceduralDungeon.of(name));
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

    // todo: implement custom rule test for NBT LootTable replacements (see pinned t3chat conversation)
    private static final StructureProcessorList CHESTS = create(List.of(

    ));

    private static final StructureProcessorList COBBLESTONE = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.STONE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_STONE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CHISELED_STONE_BRICKS)
    ));

    private static final StructureProcessorList DEEPSLATE = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.POLISHED_DEEPSLATE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_DEEPSLATE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.DEEPSLATE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.DEEPSLATE)
    ));

    private static final StructureProcessorList SCULK = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.001f, Blocks.SCULK_SHRIEKER),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.001f, Blocks.REINFORCED_DEEPSLATE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SCULK_SENSOR),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SCULK_CATALYST),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.SCULK)
    ));

    private static final StructureProcessorList NETHER_WASTES = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.001f, Blocks.ANCIENT_DEBRIS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.005f, Blocks.LAVA),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.025f, Blocks.NETHER_GOLD_ORE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.025f, Blocks.NETHER_QUARTZ_ORE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.NETHERRACK)
    ));

    private static final StructureProcessorList CRIMSON_FOREST = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRIMSON_PLANKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRIMSON_NYLIUM),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRIMSON_HYPHAE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.NETHER_WART_BLOCK)
    ));

    private static final StructureProcessorList WARPED_FOREST = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.WARPED_PLANKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.WARPED_NYLIUM),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.WARPED_HYPHAE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.WARPED_WART_BLOCK)
    ));

    private static final StructureProcessorList BASALT_DELTAS = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.POLISHED_BASALT),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SMOOTH_BASALT),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.BASALT)
    ));

    private static final StructureProcessorList SOUL_SAND_VALLEY = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.SOUL_SAND),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.SOUL_SOIL)
    ));

    private static final StructureProcessorList NETHER_FORTRESS = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.RED_NETHER_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CHISELED_NETHER_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_NETHER_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.NETHER_BRICKS)
    ));

    private static final StructureProcessorList BASTION = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.01f, Blocks.GILDED_BLACKSTONE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.POLISHED_BLACKSTONE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CHISELED_POLISHED_BLACKSTONE),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.BLACKSTONE)
    ));

    private static final StructureProcessorList END_STONE = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.END_STONE_BRICKS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.END_STONE)
    ));

    private static final StructureProcessorList END_CITY = createBasic(List.of(
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.01f, Blocks.PURPLE_STAINED_GLASS),
            new BasicProcessorRule(Blocks.COBBLESTONE, 0.1f, Blocks.PURPUR_PILLAR),
            new BasicProcessorRule(Blocks.COBBLESTONE, 1f, Blocks.PURPUR_BLOCK)
    ));

    @Override
    protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {

    }

    @Override
    public String getName() {
        return "Dungeon Processor Lists";
    }
}
