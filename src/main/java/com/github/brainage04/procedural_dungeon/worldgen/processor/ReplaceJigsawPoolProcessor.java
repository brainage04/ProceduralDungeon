package com.github.brainage04.procedural_dungeon.worldgen.processor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class ReplaceJigsawPoolProcessor extends StructureProcessor {
    public static final MapCodec<ReplaceJigsawPoolProcessor> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC)
                            .fieldOf("replacements")
                            .forGetter(processor -> processor.replacements)
            ).apply(instance, ReplaceJigsawPoolProcessor::new));

    private final Map<Identifier, Identifier> replacements;

    public ReplaceJigsawPoolProcessor(Map<Identifier, Identifier> replacements) {
        this.replacements = replacements;
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
        if (!currentBlockInfo.state().is(Blocks.JIGSAW) || currentBlockInfo.nbt() == null) {
            return currentBlockInfo;
        }

        CompoundTag copy = currentBlockInfo.nbt().copy();
        boolean changed = replacePool(copy, "pool");
        changed |= replacePool(copy, "target_pool");

        if (!changed) {
            return currentBlockInfo;
        }

        return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), currentBlockInfo.state(), copy);
    }

    private boolean replacePool(CompoundTag nbt, String key) {
        String oldPool = nbt.getString(key).orElse(null);
        if (oldPool == null) {
            return false;
        }

        Identifier oldId = Identifier.tryParse(oldPool);
        if (oldId == null) {
            return false;
        }

        Identifier newId = replacements.get(oldId);
        if (newId == null) {
            return false;
        }

        nbt.putString(key, newId.toString());
        return true;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructureProcessorTypes.REPLACE_JIGSAW_POOLS;
    }
}
