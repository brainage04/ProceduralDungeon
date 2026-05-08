package com.github.brainage04.procedural_dungeon.worldgen.processor;

import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonGenerationProfiler;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class ReplaceLootTableProcessor extends StructureProcessor {
    public static final MapCodec<ReplaceLootTableProcessor> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC)
                            .fieldOf("replacements")
                            .forGetter(processor -> processor.replacements)
            ).apply(instance, ReplaceLootTableProcessor::new));

    private final Map<Identifier, Identifier> replacements;

    public ReplaceLootTableProcessor(Map<Identifier, Identifier> replacements) {
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
        long start = DungeonGenerationProfiler.start();
        try {
            CompoundTag nbt = currentBlockInfo.nbt();
            if (nbt == null) {
                return currentBlockInfo;
            }

            String oldLootTable = nbt.getString("LootTable").orElse(null);
            if (oldLootTable == null) {
                return currentBlockInfo;
            }

            Identifier oldId = Identifier.tryParse(oldLootTable);
            if (oldId == null) {
                return currentBlockInfo;
            }

            Identifier newId = replacements.get(oldId);
            if (newId == null) {
                return currentBlockInfo;
            }

            CompoundTag copy = nbt.copy();
            copy.putString("LootTable", newId.toString());
            copy.putLong("LootTableSeed", data.getRandom(currentBlockInfo.pos()).nextLong());
            return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), currentBlockInfo.state(), copy);
        } finally {
            if (start != 0L) {
                DungeonGenerationProfiler.recordProcessor("procedural_dungeon:replace_loot_tables", System.nanoTime() - start);
            }
        }
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructureProcessorTypes.REPLACE_LOOT_TABLES;
    }
}
