package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

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
    public Vec3i getSize(StructureTemplateManager structureTemplateManager, Rotation rotation) {
        return delegate.getSize(structureTemplateManager, rotation);
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(
            StructureTemplateManager structureTemplateManager,
            BlockPos pos,
            Rotation rotation,
            RandomSource random
    ) {
        return delegate.getShuffledJigsawBlocks(structureTemplateManager, pos, rotation, random)
                .stream()
                .map(this::replacePool)
                .toList();
    }

    private StructureTemplate.JigsawBlockInfo replacePool(StructureTemplate.JigsawBlockInfo info) {
        Identifier replacement = DungeonJigsawPoolReplacements.getReplacement(info.pool().identifier(), variant, spawnerTier);
        if (replacement.equals(info.pool().identifier())) {
            return info;
        }

        return new StructureTemplate.JigsawBlockInfo(
                info.info(),
                info.jointType(),
                info.name(),
                ResourceKey.create(Registries.TEMPLATE_POOL, replacement),
                info.target(),
                info.placementPriority(),
                info.selectionPriority()
        );
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation) {
        return delegate.getBoundingBox(structureTemplateManager, pos, rotation);
    }

    @Override
    public boolean place(
            StructureTemplateManager structureTemplateManager,
            WorldGenLevel world,
            StructureManager structureAccessor,
            ChunkGenerator chunkGenerator,
            BlockPos pos,
            BlockPos pivot,
            Rotation rotation,
            BoundingBox box,
            RandomSource random,
            LiquidSettings liquidSettings,
            boolean keepJigsaws
    ) {
        return delegate.place(structureTemplateManager, world, structureAccessor, chunkGenerator, pos, pivot, rotation, box, random, liquidSettings, keepJigsaws);
    }

    @Override
    public StructurePoolElement setProjection(StructureTemplatePool.Projection projection) {
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
