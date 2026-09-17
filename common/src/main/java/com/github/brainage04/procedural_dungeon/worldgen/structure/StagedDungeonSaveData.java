package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class StagedDungeonSaveData extends SavedData {
    private static final Codec<StagedDungeonSaveData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().optionalFieldOf("completed_start_chunks", List.of())
                    .forGetter(StagedDungeonSaveData::completedStartChunks),
            CompoundTag.CODEC.listOf().optionalFieldOf("pending_jobs", List.of())
                    .forGetter(StagedDungeonSaveData::pendingJobs)
    ).apply(instance, StagedDungeonSaveData::new));

    public static final SavedDataType<StagedDungeonSaveData> TYPE = new SavedDataType<>(
            ProceduralDungeon.of("staged_dungeon_generation"),
            StagedDungeonSaveData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_FORCED_CHUNKS
    );

    private final LongSet completedStartChunks = new LongOpenHashSet();
    private List<CompoundTag> pendingJobs;
    private Map<Long, StagedDungeonGenerationManager.Job> runtimeJobs;

    public StagedDungeonSaveData() {
        this(List.of(), List.of());
    }

    private StagedDungeonSaveData(List<Long> completedStartChunks, List<CompoundTag> pendingJobs) {
        this.completedStartChunks.addAll(completedStartChunks);
        this.pendingJobs = pendingJobs.stream().map(CompoundTag::copy).toList();
    }

    public boolean isComplete(long startChunk) {
        return completedStartChunks.contains(startChunk);
    }

    public void markComplete(long startChunk) {
        if (completedStartChunks.add(startChunk)) {
            setDirty();
        }
    }

    Map<Long, StagedDungeonGenerationManager.Job> jobs(ServerLevel level) {
        if (runtimeJobs == null) {
            runtimeJobs = new LinkedHashMap<>();
            for (CompoundTag tag : pendingJobs) {
                try {
                    StagedDungeonGenerationManager.Job job =
                            StagedDungeonGenerationManager.Job.load(level, tag);
                    runtimeJobs.putIfAbsent(job.startChunk().pack(), job);
                } catch (RuntimeException exception) {
                    ProceduralDungeon.LOGGER.error("Discarding unreadable staged dungeon job.", exception);
                }
            }
        }
        return runtimeJobs;
    }

    void syncJobs(ServerLevel level) {
        pendingJobs = jobs(level).values().stream()
                .map(job -> job.save(level))
                .toList();
        setDirty();
    }

    private List<Long> completedStartChunks() {
        return completedStartChunks.longStream().boxed().toList();
    }

    private List<CompoundTag> pendingJobs() {
        return pendingJobs;
    }
}
