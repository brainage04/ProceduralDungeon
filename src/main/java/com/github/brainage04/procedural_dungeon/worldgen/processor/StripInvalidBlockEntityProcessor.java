package com.github.brainage04.procedural_dungeon.worldgen.processor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class StripInvalidBlockEntityProcessor extends StructureProcessor {
    public static final StripInvalidBlockEntityProcessor INSTANCE = new StripInvalidBlockEntityProcessor();
    public static final MapCodec<StripInvalidBlockEntityProcessor> CODEC = MapCodec.unit(INSTANCE);

    private StripInvalidBlockEntityProcessor() {
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader world,
            BlockPos pos,
            BlockPos pivot,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo currentBlockInfo,
            StructurePlaceSettings data
    ) {
        if (currentBlockInfo.nbt() == null || currentBlockInfo.state().getBlock() instanceof EntityBlock) {
            return currentBlockInfo;
        }

        return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), currentBlockInfo.state(), null);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructureProcessorTypes.STRIP_INVALID_BLOCK_ENTITY;
    }
}
