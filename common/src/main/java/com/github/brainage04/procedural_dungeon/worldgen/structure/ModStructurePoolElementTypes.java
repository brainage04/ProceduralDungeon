package com.github.brainage04.procedural_dungeon.worldgen.structure;

import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;

public final class ModStructurePoolElementTypes {
    public static StructurePoolElementType<VariantSinglePoolElement> VARIANT_SINGLE_POOL_ELEMENT;

    private ModStructurePoolElementTypes() {}

    public static void registerAll(BiConsumer<String, StructurePoolElementType<VariantSinglePoolElement>> registrar) {
        registrar.accept("variant_single_pool_element", () -> VariantSinglePoolElement.CODEC);
    }

    public static void setVariantSinglePoolElement(StructurePoolElementType<VariantSinglePoolElement> type) {
        VARIANT_SINGLE_POOL_ELEMENT = Objects.requireNonNull(type);
    }
}
