package com.github.brainage04.procedural_dungeon.datagen.core;

import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.datagen.loot_table.DungeonLootTableProvider;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.ReplaceJigsawPoolProcessor;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.ReplaceLootTableProcessor;
import com.github.brainage04.procedural_dungeon.datagen.structure.DungeonJigsawPoolReplacements;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockAgeProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

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

    public static StructureProcessorList create(List<StructureProcessor> rules) {
        return new StructureProcessorList(rules);
    }

    private static StructureProcessorList createBasic(List<DungeonTheme.ProcessorRuleSpec> rules) {
        List<StructureProcessor> list = new ArrayList<>(rules.size());

        for (DungeonTheme.ProcessorRuleSpec rule : rules) {
            list.add(new RuleProcessor(List.of(
                    new ProcessorRule(
                            new RandomBlockMatchTest(block(rule.input()), rule.probability()),
                            AlwaysTrueTest.INSTANCE,
                            block(rule.output()).defaultBlockState()
                    )
            )));
        }

        return create(list);
    }

    private static StructureProcessorList createDecay() {
        return create(List.of(
                new BlockAgeProcessor(DungeonTheme.ageChance()),
                new BlockRotProcessor(DungeonTheme.rotChance())
        ));
    }

    private static Block block(String id) {
        Identifier identifier = Identifier.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(identifier)) {
            throw new IllegalArgumentException("Unknown processor rule block: " + id);
        }
        return BuiltInRegistries.BLOCK.getValue(identifier);
    }

    @Override
    protected void configure(HolderLookup.Provider registries, Entries entries) {
        for (DungeonTheme theme : DungeonTheme.values()) {
            for (DungeonTier tier : DungeonTier.values()) {
                String key = RegistryKeyUtils.getKeyString(theme, tier);
                generateProcessorList(entries, key, tier, theme);
            }
        }
    }

    private static void generateProcessorList(Entries entries, String key, DungeonTier tier, DungeonTheme theme) {
        StructureProcessorList structureProcessorList = new StructureProcessorList(
                Stream.of(
                        createBasic(DungeonTheme.bookshelfRules()),
                        createBasic(DungeonTheme.mineralRules()),
                        createBasic(DungeonTheme.airRules()),
                        createDecay(),
                        createLootTable(tier),
                        createBasic(theme.processorRules),
                        createJigsawPoolReplacements(key, tier)
                ).flatMap(structureProcessorList1 -> structureProcessorList1.list().stream()).toList()
        );

        var processorListKey = RegistryKeyUtils.create(Registries.PROCESSOR_LIST, key);
        entries.add(processorListKey, structureProcessorList);
    }

    private static StructureProcessorList createJigsawPoolReplacements(String key, DungeonTier tier) {
        return create(List.of(new ReplaceJigsawPoolProcessor(DungeonJigsawPoolReplacements.create(key, tier))));
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Generator";
    }
}
