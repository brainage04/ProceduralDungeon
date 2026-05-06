package com.github.brainage04.procedural_dungeon.datagen.processor_list;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

import java.util.Map;

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
    public StructureTemplate.StructureBlockInfo process(
            WorldView world,
            BlockPos pos,
            BlockPos pivot,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo currentBlockInfo,
            StructurePlacementData data
    ) {
        if (!currentBlockInfo.state().isOf(Blocks.JIGSAW) || currentBlockInfo.nbt() == null) {
            return currentBlockInfo;
        }

        NbtCompound copy = currentBlockInfo.nbt().copy();
        boolean changed = replacePool(copy, "pool");
        changed |= replacePool(copy, "target_pool");

        if (!changed) {
            return currentBlockInfo;
        }

        return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), currentBlockInfo.state(), copy);
    }

    private boolean replacePool(NbtCompound nbt, String key) {
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
