package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;

public class ModStructurePoolElementTypes {
    public static final StructurePoolElementType<VariantSinglePoolElement> VARIANT_SINGLE_POOL_ELEMENT =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_POOL_ELEMENT,
                    ProceduralDungeon.of("variant_single_pool_element"),
                    () -> VariantSinglePoolElement.CODEC
            );

    public static void initialize() {
        // load class and run static init
    }
}
