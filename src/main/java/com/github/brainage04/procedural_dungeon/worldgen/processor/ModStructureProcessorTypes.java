package com.github.brainage04.procedural_dungeon.worldgen.processor;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModStructureProcessorTypes {
    private ModStructureProcessorTypes() {
    }

    public static void initialize() {
        register("replace_jigsaw_pools", ReplaceJigsawPoolProcessor.CODEC);
        register("replace_loot_tables", ReplaceLootTableProcessor.CODEC);
        register("strip_invalid_block_entity", StripInvalidBlockEntityProcessor.CODEC);
        register("loot_tables_and_block_entities", LootTableAndBlockEntityProcessor.CODEC);
        register("theme_shape_replacements", ThemeShapeReplacementProcessor.CODEC);
        register("include_processor_list", IncludeProcessorListProcessor.CODEC);
        register("fused_dungeon_processor", FusedDungeonProcessor.CODEC);
    }

    private static void register(String name, com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor> codec) {
        Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, ProceduralDungeon.of(name), codec);
    }
}
