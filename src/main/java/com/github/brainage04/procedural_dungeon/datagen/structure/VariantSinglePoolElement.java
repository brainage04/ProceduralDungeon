package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.structure.pool.StructurePoolElementType;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

import java.util.List;

public class VariantSinglePoolElement extends StructurePoolElement {
    public static final MapCodec<VariantSinglePoolElement> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    SinglePoolElement.CODEC.forGetter(element -> element.delegate),
                    Identifier.CODEC.fieldOf("variant").forGetter(element -> element.variant),
                    Codec.INT.fieldOf("spawner_tier").forGetter(element -> element.spawnerTier)
            ).apply(instance, VariantSinglePoolElement::new));

    private final SinglePoolElement delegate;
    private final Identifier variant;
    private final int spawnerTier;

    public VariantSinglePoolElement(SinglePoolElement delegate, Identifier variant, int spawnerTier) {
        super(delegate.getProjection());
        this.delegate = delegate;
        this.variant = variant;
        this.spawnerTier = spawnerTier;
    }

    @Override
    public Vec3i getStart(StructureTemplateManager structureTemplateManager, BlockRotation rotation) {
        return delegate.getStart(structureTemplateManager, rotation);
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getStructureBlockInfos(
            StructureTemplateManager structureTemplateManager,
            BlockPos pos,
            BlockRotation rotation,
            Random random
    ) {
        return delegate.getStructureBlockInfos(structureTemplateManager, pos, rotation, random)
                .stream()
                .map(this::replacePool)
                .toList();
    }

    private StructureTemplate.JigsawBlockInfo replacePool(StructureTemplate.JigsawBlockInfo info) {
        Identifier replacement = DungeonJigsawPoolReplacements.getReplacement(info.pool().getValue(), variant, spawnerTier);
        if (replacement.equals(info.pool().getValue())) {
            return info;
        }

        return new StructureTemplate.JigsawBlockInfo(
                info.info(),
                info.jointType(),
                info.name(),
                RegistryKey.of(RegistryKeys.TEMPLATE_POOL, replacement),
                info.target(),
                info.placementPriority(),
                info.selectionPriority()
        );
    }

    @Override
    public BlockBox getBoundingBox(StructureTemplateManager structureTemplateManager, BlockPos pos, BlockRotation rotation) {
        return delegate.getBoundingBox(structureTemplateManager, pos, rotation);
    }

    @Override
    public boolean generate(
            StructureTemplateManager structureTemplateManager,
            StructureWorldAccess world,
            StructureAccessor structureAccessor,
            ChunkGenerator chunkGenerator,
            BlockPos pos,
            BlockPos pivot,
            BlockRotation rotation,
            BlockBox box,
            Random random,
            StructureLiquidSettings liquidSettings,
            boolean keepJigsaws
    ) {
        return delegate.generate(structureTemplateManager, world, structureAccessor, chunkGenerator, pos, pivot, rotation, box, random, liquidSettings, keepJigsaws);
    }

    @Override
    public StructurePoolElement setProjection(StructurePool.Projection projection) {
        delegate.setProjection(projection);
        return super.setProjection(projection);
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return ModStructurePoolElementTypes.VARIANT_SINGLE_POOL_ELEMENT;
    }

    @Override
    public String toString() {
        return "VariantSingle[" + delegate + " -> " + variant + "]";
    }
}
