package com.github.brainage04.procedural_dungeon.worldgen.processor;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;

public final class ModStructureProcessorTypes {
    private ModStructureProcessorTypes() {}

    public static void registerAll(BiConsumer<String, MapCodec<? extends StructureProcessor>> registrar) {
        registrar.accept("replace_jigsaw_pools", ReplaceJigsawPoolProcessor.CODEC);
        registrar.accept("replace_loot_tables", ReplaceLootTableProcessor.CODEC);
        registrar.accept("strip_invalid_block_entity", StripInvalidBlockEntityProcessor.CODEC);
        registrar.accept("loot_tables_and_block_entities", LootTableAndBlockEntityProcessor.CODEC);
        registrar.accept("theme_shape_replacements", ThemeShapeReplacementProcessor.CODEC);
        registrar.accept("include_processor_list", IncludeProcessorListProcessor.CODEC);
        registrar.accept("fused_dungeon_processor", FusedDungeonProcessor.CODEC);
    }
}
