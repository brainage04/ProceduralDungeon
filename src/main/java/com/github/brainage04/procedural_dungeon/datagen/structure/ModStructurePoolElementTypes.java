package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.pool.StructurePoolElementType;

public class ModStructurePoolElementTypes {
    public static final StructurePoolElementType<VariantSinglePoolElement> VARIANT_SINGLE_POOL_ELEMENT =
            Registry.register(
                    Registries.STRUCTURE_POOL_ELEMENT,
                    ProceduralDungeon.of("variant_single_pool_element"),
                    () -> VariantSinglePoolElement.CODEC
            );

    public static void initialize() {
        // load class and run static init
    }
}
