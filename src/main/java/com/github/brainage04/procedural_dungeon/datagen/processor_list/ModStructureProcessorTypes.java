package com.github.brainage04.procedural_dungeon.datagen.processor_list;

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

    public static void initialize() {
        // load class and run static init
    }
}
