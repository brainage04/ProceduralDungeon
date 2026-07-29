package com.github.brainage04.procedural_dungeon.datagen.core;

import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.datagen.loot_table.DungeonLootTableProvider;
import com.github.brainage04.procedural_dungeon.worldgen.processor.FusedDungeonProcessor;
import com.github.brainage04.procedural_dungeon.worldgen.processor.LootTableAndBlockEntityProcessor;
import com.github.brainage04.procedural_dungeon.worldgen.processor.ThemeShapeReplacementProcessor;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockAgeProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public class ProceduralDungeonGenerator extends FabricDynamicRegistryProvider {
    public static final List<String> BASE_STRUCTURES = List.of(
            "dungeon/start",
            "dungeon/hallway/small",
            "dungeon/hallway/medium",
            "dungeon/hallway/large",
            "dungeon/hallway/end/small",
            "dungeon/hallway/end/medium",
            "dungeon/hallway/end/large",
            "dungeon/hallway/loot/small",
            "dungeon/hallway/loot/medium",
            "dungeon/hallway/loot/large",
            "dungeon/hallway/room/armorsmith",
            "dungeon/hallway/room/enchanter",
            "dungeon/hallway/room/spawner_corridor",
            "dungeon/hallway/room/staircase_diagonal_down",
            "dungeon/hallway/room/staircase_spiral_down",
            "dungeon/hallway/room/toolsmith",
            "dungeon/hallway/room/weaponsmith",
            "dungeon/hallway/trap/dripstone",
            "dungeon/hallway/trap/lava",
            "dungeon/hallway/trap/negative_potions",
            "dungeon/hallway/trap/spawners"
    );
    private static final Set<String> THEME_SHAPE_INPUTS = Set.of(
            BuiltInRegistries.BLOCK.getKey(Blocks.COBBLESTONE_STAIRS).toString(),
            BuiltInRegistries.BLOCK.getKey(Blocks.STONE_BRICK_STAIRS).toString(),
            BuiltInRegistries.BLOCK.getKey(Blocks.MOSSY_STONE_BRICK_STAIRS).toString(),
            BuiltInRegistries.BLOCK.getKey(Blocks.COBBLESTONE_SLAB).toString(),
            BuiltInRegistries.BLOCK.getKey(Blocks.STONE_SLAB).toString(),
            BuiltInRegistries.BLOCK.getKey(Blocks.STONE_BRICK_SLAB).toString(),
            BuiltInRegistries.BLOCK.getKey(Blocks.MOSSY_STONE_BRICK_SLAB).toString(),
            BuiltInRegistries.BLOCK.getKey(Blocks.COBBLESTONE_WALL).toString(),
            BuiltInRegistries.BLOCK.getKey(Blocks.MOSSY_STONE_BRICK_WALL).toString()
    );
    private static final Map<String, TemplateCapabilities> TEMPLATE_CAPABILITIES = BASE_STRUCTURES.stream()
            .collect(Collectors.toUnmodifiableMap(structure -> structure, ProceduralDungeonGenerator::readTemplateCapabilities));

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

    private static StructureProcessorList createLootTable(DungeonTheme theme, DungeonTier tier) {
        return create(List.of(new LootTableAndBlockEntityProcessor(createLootTableReplacements(theme, tier))));
    }

    private static Map<Identifier, Identifier> createLootTableReplacements(DungeonTheme theme, DungeonTier tier) {
        return TIERED_LOOT_TABLES.stream().collect(Collectors.toUnmodifiableMap(
                DungeonLootTableProvider::getLootTableId,
                table -> DungeonLootTableProvider.getLootTableReplacementId(table, theme, tier)
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
        Set<String> generatedKeys = new HashSet<>();
        for (DungeonTheme theme : DungeonTheme.values()) {
            for (DungeonTier tier : DungeonTier.values()) {
                String key = RegistryKeyUtils.getKeyString(theme, tier);
                generateProcessorList(entries, generatedKeys, key, tier, theme, TemplateCapabilities.full());
                for (String structure : BASE_STRUCTURES) {
                    generateTemplateProcessorList(entries, generatedKeys, tier, theme, TEMPLATE_CAPABILITIES.get(structure));
                }
            }
        }
    }

    private static void generateProcessorList(
            Entries entries,
            Set<String> generatedKeys,
            String key,
            DungeonTier tier,
            DungeonTheme theme,
            TemplateCapabilities capabilities
    ) {
        addProcessorList(entries, generatedKeys, key, createFusedProcessorList(tier, theme, capabilities));
    }

    private static void generateTemplateProcessorList(
            Entries entries,
            Set<String> generatedKeys,
            DungeonTier tier,
            DungeonTheme theme,
            TemplateCapabilities capabilities
    ) {
        List<String> components = profileComponents(theme, tier.tier, capabilities);
        addProcessorList(entries, generatedKeys, profileProcessorKey(components), createFusedProcessorList(tier, theme, capabilities));
    }

    private static StructureProcessorList createFusedProcessorList(DungeonTier tier, DungeonTheme theme, TemplateCapabilities capabilities) {
        return create(List.of(new FusedDungeonProcessor(
                List.of(
                        fusedRules(DungeonTheme.bookshelfRules(), capabilities),
                        fusedRules(DungeonTheme.mineralRules(), capabilities),
                        fusedRules(DungeonTheme.airRules(), capabilities)
                ).stream().filter(rules -> !rules.isEmpty()).toList(),
                List.of(fusedRules(theme.processorRules, capabilities)).stream()
                        .filter(rules -> !rules.isEmpty())
                        .toList(),
                DungeonTheme.ageChance(),
                DungeonTheme.rotChance(),
                capabilities.hasThemeShapes() ? Optional.of(fusedShapes(theme)) : Optional.empty(),
                capabilities.hasLootNbt() ? createLootTableReplacements(theme, tier) : Map.of(),
                capabilities.hasSpawnerMarkers() ? spawnerEntities(theme) : List.of()
        )));
    }

    private static List<FusedDungeonProcessor.FusedRule> fusedRules(
            List<DungeonTheme.ProcessorRuleSpec> rules,
            TemplateCapabilities capabilities
    ) {
        return matchingRules(rules, capabilities).stream()
                .map(rule -> new FusedDungeonProcessor.FusedRule(
                        Identifier.parse(rule.input()),
                        rule.probability(),
                        Identifier.parse(rule.output())
                ))
                .toList();
    }

    private static FusedDungeonProcessor.ShapeReplacements fusedShapes(DungeonTheme theme) {
        DungeonTheme.ShapeReplacementSpec replacements = theme.shapeReplacements;
        return new FusedDungeonProcessor.ShapeReplacements(
                Identifier.parse(replacements.fallback()),
                optionalIdentifier(replacements.stairs()),
                optionalIdentifier(replacements.slab()),
                optionalIdentifier(replacements.wall())
        );
    }

    private static List<String> generateProfileComponents(
            Entries entries,
            Set<String> generatedKeys,
            DungeonTier tier,
            DungeonTheme theme,
            TemplateCapabilities capabilities
    ) {
        List<String> components = new ArrayList<>();
        addRuleComponent(entries, generatedKeys, components, "generic/bookshelf", DungeonTheme.bookshelfRules(), capabilities);
        addRuleComponent(entries, generatedKeys, components, "generic/minerals", DungeonTheme.mineralRules(), capabilities);
        addRuleComponent(entries, generatedKeys, components, "generic/air", DungeonTheme.airRules(), capabilities);

        String decayKey = "dungeon/component/generic/decay";
        addProcessorList(entries, generatedKeys, decayKey, createDecay());
        components.add(decayKey);

        if (capabilities.hasThemeShapes()) {
            String shapesKey = "dungeon/component/theme/%s/shapes".formatted(theme.getSerializedName());
            addProcessorList(entries, generatedKeys, shapesKey, createThemeShapes(theme));
            components.add(shapesKey);
        }

        addRuleComponent(entries, generatedKeys, components, "theme/%s/rules".formatted(theme.getSerializedName()), theme.processorRules, capabilities);

        if (capabilities.hasLootNbt()) {
            String lootKey = lootComponentKey(theme, tier.tier);
            addProcessorList(entries, generatedKeys, lootKey, createLootTable(theme, tier));
            components.add(lootKey);
        }

        return List.copyOf(components);
    }

    private static void addRuleComponent(
            Entries entries,
            Set<String> generatedKeys,
            List<String> components,
            String path,
            List<DungeonTheme.ProcessorRuleSpec> rules,
            TemplateCapabilities capabilities
    ) {
        List<DungeonTheme.ProcessorRuleSpec> matchingRules = matchingRules(rules, capabilities);
        if (matchingRules.isEmpty()) {
            return;
        }

        String componentKey = "dungeon/component/%s/%s".formatted(path, shortHash(canonicalRules(matchingRules)));
        addProcessorList(entries, generatedKeys, componentKey, createBasic(matchingRules));
        components.add(componentKey);
    }

    private static void addProcessorList(Entries entries, Set<String> generatedKeys, String key, StructureProcessorList structureProcessorList) {
        if (generatedKeys.add(key)) {
            entries.add(RegistryKeyUtils.create(Registries.PROCESSOR_LIST, key), structureProcessorList);
        }
    }

    public static String templateProcessorKey(String key, String structure, DungeonTheme theme, int tier) {
        TemplateCapabilities capabilities = TEMPLATE_CAPABILITIES.get(structure);
        if (capabilities == null) {
            throw new IllegalArgumentException("Unknown base structure: " + structure);
        }

        return profileProcessorKey(profileComponents(theme, tier, capabilities));
    }

    private static List<String> profileComponents(DungeonTheme theme, int tier, TemplateCapabilities capabilities) {
        List<String> components = new ArrayList<>();
        addRuleComponentKey(components, "generic/bookshelf", DungeonTheme.bookshelfRules(), capabilities);
        addRuleComponentKey(components, "generic/minerals", DungeonTheme.mineralRules(), capabilities);
        addRuleComponentKey(components, "generic/air", DungeonTheme.airRules(), capabilities);
        components.add("dungeon/component/generic/decay");
        if (capabilities.hasThemeShapes()) {
            components.add("dungeon/component/theme/%s/shapes".formatted(theme.getSerializedName()));
        }
        addRuleComponentKey(components, "theme/%s/rules".formatted(theme.getSerializedName()), theme.processorRules, capabilities);
        if (capabilities.hasLootNbt()) {
            components.add(lootComponentKey(theme, tier));
        }
        if (capabilities.hasSpawnerMarkers()) {
            components.add(spawnerComponentKey(theme));
        }

        return List.copyOf(components);
    }

    private static String lootComponentKey(DungeonTheme theme, int tier) {
        String canonical = TIERED_LOOT_TABLES.stream()
                .map(table -> "%s=%s".formatted(table, DungeonLootTableProvider.getLootTableReplacementId(table, theme, tier)))
                .collect(Collectors.joining("\n"));
        return "dungeon/component/loot/%s/tier_%d/%s".formatted(
                theme.getSerializedName(),
                tier,
                shortHash(canonical)
        );
    }

    private static String spawnerComponentKey(DungeonTheme theme) {
        return "dungeon/component/spawner/%s/%s".formatted(theme.getSerializedName(), shortHash(canonicalSpawners(theme)));
    }

    private static String canonicalSpawners(DungeonTheme theme) {
        return theme.profile.spawnerMobs().stream()
                .map(mob -> "%s|%d".formatted(normalizeEntityId(mob.entity()), mob.weight()))
                .collect(Collectors.joining("\n"));
    }

    private static List<Identifier> spawnerEntities(DungeonTheme theme) {
        ArrayList<Identifier> entities = new ArrayList<>();
        for (DungeonTheme.WeightedEntitySpec mob : theme.profile.spawnerMobs()) {
            Identifier entity = normalizeEntityId(mob.entity());
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)) {
                throw new IllegalArgumentException("Unknown dungeon spawner entity: " + entity);
            }
            for (int i = 0; i < mob.weight(); i++) {
                entities.add(entity);
            }
        }
        return List.copyOf(entities);
    }

    private static void addRuleComponentKey(
            List<String> components,
            String path,
            List<DungeonTheme.ProcessorRuleSpec> rules,
            TemplateCapabilities capabilities
    ) {
        List<DungeonTheme.ProcessorRuleSpec> matchingRules = matchingRules(rules, capabilities);
        if (!matchingRules.isEmpty()) {
            components.add("dungeon/component/%s/%s".formatted(path, shortHash(canonicalRules(matchingRules))));
        }
    }

    private static String profileProcessorKey(List<String> componentKeys) {
        return "dungeon/profile/%s".formatted(shortHash(String.join("\n", componentKeys)));
    }

    private static void addBasic(List<StructureProcessor> processors, List<DungeonTheme.ProcessorRuleSpec> rules, TemplateCapabilities capabilities) {
        List<DungeonTheme.ProcessorRuleSpec> matchingRules = matchingRules(rules, capabilities);
        if (!matchingRules.isEmpty()) {
            processors.addAll(createBasic(matchingRules).list());
        }
    }

    private static List<DungeonTheme.ProcessorRuleSpec> matchingRules(
            List<DungeonTheme.ProcessorRuleSpec> rules,
            TemplateCapabilities capabilities
    ) {
        return rules.stream()
                .filter(rule -> capabilities.hasBlock(rule.input()))
                .toList();
    }

    private static String canonicalRules(List<DungeonTheme.ProcessorRuleSpec> rules) {
        return rules.stream()
                .map(rule -> "%s|%s|%s".formatted(rule.input(), Float.toString(rule.probability()), rule.output()))
                .collect(Collectors.joining("\n"));
    }

    private static String shortHash(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 digest is unavailable", e);
        }
    }

    private static TemplateCapabilities readTemplateCapabilities(String structure) {
        String resource = "data/procedural_dungeon/structure/%s.nbt".formatted(structure);
        InputStream input = ProceduralDungeonGenerator.class.getClassLoader().getResourceAsStream(resource);
        if (input == null) {
            throw new IllegalStateException("Missing source structure resource: " + resource);
        }

        try (input) {
            CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            Set<String> paletteBlocks = new HashSet<>();
            List<String> paletteBlockList = new ArrayList<>();
            ListTag palette = tag.getListOrEmpty("palette");
            for (int i = 0; i < palette.size(); i++) {
                String block = palette.getCompoundOrEmpty(i).getString("Name").orElse(null);
                if (block != null) {
                    paletteBlocks.add(block);
                }
                paletteBlockList.add(block);
            }

            boolean hasLootNbt = false;
            boolean hasSpawnerMarkers = false;
            ListTag blocks = tag.getListOrEmpty("blocks");
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompoundOrEmpty(i);
                int stateIndex = block.getIntOr("state", -1);
                if (!block.contains("nbt") || stateIndex < 0 || stateIndex >= paletteBlockList.size()) {
                    continue;
                }

                String blockId = paletteBlockList.get(stateIndex);
                CompoundTag nbt = block.getCompoundOrEmpty("nbt");
                if (isSpawnerMarker(blockId, nbt)) {
                    hasSpawnerMarkers = true;
                } else if (isRelevantNbtBlock(blockId)) {
                    hasLootNbt = true;
                }
            }

            return new TemplateCapabilities(Set.copyOf(paletteBlocks), hasLootNbt, hasSpawnerMarkers);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read source structure resource: " + resource, e);
        }
    }

    private static boolean isSpawnerMarker(String blockId, CompoundTag nbt) {
        return blockId != null
                && blockId.equals("minecraft:spawner")
                && FusedDungeonProcessor.isSpawnerMarkerNbt(nbt);
    }

    private static boolean isRelevantNbtBlock(String blockId) {
        return blockId != null
                && !blockId.equals("minecraft:jigsaw")
                && !blockId.equals("minecraft:structure_block");
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Generator";
    }

    private static Identifier normalizeEntityId(String id) {
        return Identifier.parse(id.contains(":") ? id : "minecraft:" + id);
    }

    private record TemplateCapabilities(Set<String> paletteBlocks, boolean hasLootNbt, boolean hasSpawnerMarkers) {
        private static TemplateCapabilities full() {
            return new TemplateCapabilities(Set.of(), true, false);
        }

        private boolean hasBlock(String id) {
            return paletteBlocks.isEmpty() || paletteBlocks.contains(id);
        }

        private boolean hasThemeShapes() {
            return paletteBlocks.isEmpty() || THEME_SHAPE_INPUTS.stream().anyMatch(paletteBlocks::contains);
        }
    }
}
