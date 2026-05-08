package com.github.brainage04.procedural_dungeon.worldgen.processor;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

public class ModStructureProcessorTypes {
    public static final StructureProcessorType<ReplaceJigsawPoolProcessor> REPLACE_JIGSAW_POOLS =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_PROCESSOR,
                    ProceduralDungeon.of("replace_jigsaw_pools"),
                    () -> ReplaceJigsawPoolProcessor.CODEC
            );

    public static final StructureProcessorType<ReplaceLootTableProcessor> REPLACE_LOOT_TABLES =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_PROCESSOR,
                    ProceduralDungeon.of("replace_loot_tables"),
                    () -> ReplaceLootTableProcessor.CODEC
            );

    public static final StructureProcessorType<StripInvalidBlockEntityProcessor> STRIP_INVALID_BLOCK_ENTITY =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_PROCESSOR,
                    ProceduralDungeon.of("strip_invalid_block_entity"),
                    () -> StripInvalidBlockEntityProcessor.CODEC
            );

    public static final StructureProcessorType<LootTableAndBlockEntityProcessor> LOOT_TABLES_AND_BLOCK_ENTITIES =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_PROCESSOR,
                    ProceduralDungeon.of("loot_tables_and_block_entities"),
                    () -> LootTableAndBlockEntityProcessor.CODEC
            );

    public static final StructureProcessorType<ThemeShapeReplacementProcessor> THEME_SHAPE_REPLACEMENTS =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_PROCESSOR,
                    ProceduralDungeon.of("theme_shape_replacements"),
                    () -> ThemeShapeReplacementProcessor.CODEC
            );

    public static final StructureProcessorType<IncludeProcessorListProcessor> INCLUDE_PROCESSOR_LIST =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_PROCESSOR,
                    ProceduralDungeon.of("include_processor_list"),
                    () -> IncludeProcessorListProcessor.CODEC
            );

    public static void initialize() {
        // load class and run static init
    }
}
