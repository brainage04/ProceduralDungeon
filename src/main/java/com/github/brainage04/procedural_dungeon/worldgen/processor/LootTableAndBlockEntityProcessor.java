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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class LootTableAndBlockEntityProcessor extends StructureProcessor {
    public static final MapCodec<LootTableAndBlockEntityProcessor> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC)
                            .fieldOf("loot_table_replacements")
                            .forGetter(processor -> processor.lootTableReplacements)
            ).apply(instance, LootTableAndBlockEntityProcessor::new));

    private final Map<Identifier, Identifier> lootTableReplacements;

    public LootTableAndBlockEntityProcessor(Map<Identifier, Identifier> lootTableReplacements) {
        this.lootTableReplacements = lootTableReplacements;
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

            if (!(currentBlockInfo.state().getBlock() instanceof EntityBlock)) {
                return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), currentBlockInfo.state(), null);
            }

            String oldLootTable = nbt.getString("LootTable").orElse(null);
            if (oldLootTable == null) {
                return currentBlockInfo;
            }

            Identifier oldId = Identifier.tryParse(oldLootTable);
            if (oldId == null) {
                return currentBlockInfo;
            }

            Identifier newId = lootTableReplacements.get(oldId);
            if (newId == null) {
                return currentBlockInfo;
            }

            CompoundTag copy = nbt.copy();
            copy.putString("LootTable", newId.toString());
            copy.putLong("LootTableSeed", data.getRandom(currentBlockInfo.pos()).nextLong());
            return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), currentBlockInfo.state(), copy);
        } finally {
            if (start != 0L) {
                DungeonGenerationProfiler.recordProcessor("procedural_dungeon:loot_tables_and_block_entities", System.nanoTime() - start);
            }
        }
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructureProcessorTypes.LOOT_TABLES_AND_BLOCK_ENTITIES;
    }
}
