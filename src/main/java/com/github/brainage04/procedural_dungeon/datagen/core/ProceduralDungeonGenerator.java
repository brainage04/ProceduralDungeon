package com.github.brainage04.procedural_dungeon.datagen.core;

import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.datagen.loot_table.DungeonLootTableProvider;
import com.github.brainage04.procedural_dungeon.worldgen.processor.LootTableAndBlockEntityProcessor;
import com.github.brainage04.procedural_dungeon.worldgen.processor.ThemeShapeReplacementProcessor;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static StructureProcessorList createLootTable(DungeonTier tier) {
        return create(List.of(new LootTableAndBlockEntityProcessor(createLootTableReplacements(tier))));
    }

    private static Map<Identifier, Identifier> createLootTableReplacements(DungeonTier tier) {
        return TIERED_LOOT_TABLES.stream().collect(Collectors.toUnmodifiableMap(
                DungeonLootTableProvider::getLootTableId,
                table -> DungeonLootTableProvider.getLootTableId(table, tier)
        ));
    }

    private static StructureProcessorList create(List<StructureProcessor> rules) {
        return new StructureProcessorList(rules);
    }

    private static StructureProcessorList createBasic(List<DungeonTheme.ProcessorRuleSpec> rules) {
        List<ProcessorRule> processorRules = new ArrayList<>(rules.size());

        for (DungeonTheme.ProcessorRuleSpec rule : rules) {
            processorRules.add(new ProcessorRule(
                    new RandomBlockMatchTest(block(rule.input()), rule.probability()),
                    AlwaysTrueTest.INSTANCE,
                    block(rule.output()).defaultBlockState()
            ));
        }

        return create(List.of(new RuleProcessor(processorRules)));
    }

    private static StructureProcessorList createDecay() {
        return create(List.of(
                new BlockAgeProcessor(DungeonTheme.ageChance()),
                new BlockRotProcessor(DungeonTheme.rotChance())
        ));
    }

    private static StructureProcessorList createThemeShapes(DungeonTheme theme) {
        DungeonTheme.ShapeReplacementSpec replacements = theme.shapeReplacements;
        return create(List.of(new ThemeShapeReplacementProcessor(
                Identifier.parse(replacements.fallback()),
                optionalIdentifier(replacements.stairs()),
                optionalIdentifier(replacements.slab()),
                optionalIdentifier(replacements.wall())
        )));
    }

    private static Optional<Identifier> optionalIdentifier(String id) {
        return id == null ? Optional.empty() : Optional.of(Identifier.parse(id));
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
                        createThemeShapes(theme),
                        createBasic(theme.processorRules),
                        createLootTable(tier)
                ).flatMap(structureProcessorList1 -> structureProcessorList1.list().stream()).toList()
        );

        var processorListKey = RegistryKeyUtils.create(Registries.PROCESSOR_LIST, key);
        entries.add(processorListKey, structureProcessorList);
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Generator";
    }
}
