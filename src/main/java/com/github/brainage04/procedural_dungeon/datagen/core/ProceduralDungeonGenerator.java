package com.github.brainage04.procedural_dungeon.datagen.core;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.datagen.loot_table.DungeonLootTableProvider;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.ReplaceJigsawPoolProcessor;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.ReplaceLootTableProcessor;
import com.github.brainage04.procedural_dungeon.datagen.structure.DungeonJigsawPoolReplacements;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockAgeProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.PosAlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProceduralDungeonGenerator extends FabricDynamicRegistryProvider {
    private static final List<String> TIERED_LOOT_TABLES = List.of(
            "hallway_end",
            "hallway_loot",
            "armorsmith",
            "weaponsmith",
            "toolsmith",
            "enchanter",
            "hallway/trap/negative_potions"
    );

    public ProceduralDungeonGenerator(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    public static StructureProcessorList createLootTable(DungeonTier tier) {
        return create(List.of(new ReplaceLootTableProcessor(createLootTableReplacements(tier))));
    }

    private static Map<Identifier, Identifier> createLootTableReplacements(DungeonTier tier) {
        return TIERED_LOOT_TABLES.stream().collect(Collectors.toUnmodifiableMap(
                DungeonLootTableProvider::getLootTableId,
                table -> DungeonLootTableProvider.getLootTableId(table, tier)
        ));
    }

    private record BasicProcessorRule(Block input, float probability, Block output) {}

    public static StructureProcessorList create(List<StructureProcessor> rules) {
        return new StructureProcessorList(rules);
    }

    private static StructureProcessorList createBasic(List<BasicProcessorRule> rules) {
        List<StructureProcessor> list = new ArrayList<>(rules.size());

        for (BasicProcessorRule rule : rules) {
            list.add(new RuleProcessor(List.of(
                    new ProcessorRule(
                            new RandomBlockMatchTest(rule.input, rule.probability),
                            AlwaysTrueTest.INSTANCE,
                            rule.output.defaultBlockState()
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
            new BlockAgeProcessor(0.4f),
            new BlockRotProcessor(0.98f)
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
    protected void configure(HolderLookup.Provider registries, Entries entries) {
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
                ).flatMap(structureProcessorList1 -> structureProcessorList1.list().stream()).toList()
        );

        var processorListKey = RegistryKeyUtils.create(Registries.PROCESSOR_LIST, key);
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
