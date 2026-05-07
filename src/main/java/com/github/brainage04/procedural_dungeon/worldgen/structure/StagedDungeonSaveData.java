package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class StagedDungeonSaveData extends SavedData {
    private static final Codec<StagedDungeonSaveData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().optionalFieldOf("completed_start_chunks", List.of())
                    .forGetter(StagedDungeonSaveData::completedStartChunks)
    ).apply(instance, StagedDungeonSaveData::new));

    public static final SavedDataType<StagedDungeonSaveData> TYPE = new SavedDataType<>(
            ProceduralDungeon.of("staged_dungeon_generation"),
            StagedDungeonSaveData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_FORCED_CHUNKS
    );

    private final LongSet completedStartChunks = new LongOpenHashSet();

    public StagedDungeonSaveData() {}

    private StagedDungeonSaveData(List<Long> completedStartChunks) {
        this.completedStartChunks.addAll(completedStartChunks);
    }

    public boolean isComplete(long startChunk) {
        return completedStartChunks.contains(startChunk);
    }

    public void markComplete(long startChunk) {
        if (completedStartChunks.add(startChunk)) {
            setDirty();
        }
    }

    private List<Long> completedStartChunks() {
        return completedStartChunks.longStream().boxed().toList();
    }
}
