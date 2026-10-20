package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Staged dungeon jobs of one level. Each job's layout is encoded once per save, through {@link StagedDungeonJob#CODEC};
 * placing a piece only moves the job's cursor and marks this data dirty.
 *
 * <p>Format 2 (r61) replaces format 1's {@code pending_jobs} (the remaining pieces of each job, re-encoded after every
 * placement). Format 1 jobs are migrated on load and resume as after an unclean shutdown.
 */
public class StagedDungeonSaveData extends SavedData {
    static final int FORMAT_VERSION = 2;

    private static final Codec<StagedDungeonSaveData> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<StagedDungeonSaveData, T>> decode(DynamicOps<T> ops, T input) {
            return DataResult.success(Pair.of(StagedDungeonSaveData.decode(new Dynamic<>(ops, input)), ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(StagedDungeonSaveData data, DynamicOps<T> ops, T prefix) {
            return data.encode(ops, prefix);
        }
    };

    /**
     * {@code command_storage} holds arbitrary NBT, so no vanilla data fix will rewrite PD's records; PD versions its own
     * format with {@code format_version}.
     */
    public static final SavedDataType<StagedDungeonSaveData> TYPE = new SavedDataType<>(
            ProceduralDungeon.of("staged_dungeon_generation"),
            StagedDungeonSaveData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final LongSet completedStartChunks = new LongOpenHashSet();
    private final Map<UUID, StagedDungeonJob> jobs = new LinkedHashMap<>();
    /**
     * Job records that could not be read, kept verbatim so nothing is lost; each counts as a failed job.
     */
    private final List<UnreadableJob> unreadableJobs = new ArrayList<>();
    /**
     * Whether the last save was the final save of a clean stop.
     */
    private final boolean loadedAfterCleanShutdown;
    private final int migratedLegacyJobs;
    private boolean stopping;
    private boolean legacyTicketsReleased;
    int jobCursor;

    public StagedDungeonSaveData() {
        this.loadedAfterCleanShutdown = true;
        this.migratedLegacyJobs = 0;
        this.legacyTicketsReleased = true;
    }

    private StagedDungeonSaveData(
            List<Long> completedStartChunks,
            List<StagedDungeonJob> jobs,
            List<UnreadableJob> unreadableJobs,
            boolean cleanShutdown,
            int migratedLegacyJobs
    ) {
        this.completedStartChunks.addAll(completedStartChunks);
        for (StagedDungeonJob job : jobs) {
            this.jobs.putIfAbsent(job.id, job);
        }
        this.unreadableJobs.addAll(unreadableJobs);
        this.loadedAfterCleanShutdown = cleanShutdown;
        this.migratedLegacyJobs = migratedLegacyJobs;
        this.legacyTicketsReleased = migratedLegacyJobs == 0;
        if (!cleanShutdown) {
            this.jobs.values().forEach(StagedDungeonJob::rewindForUncleanShutdown);
        }
        if (!cleanShutdown || migratedLegacyJobs > 0) {
            // The next save must not claim a clean shutdown.
            setDirty();
        }
    }

    private static <T> StagedDungeonSaveData decode(Dynamic<T> input) {
        int version = input.get("format_version").asInt(1);
        List<Long> completed = input.get("completed_start_chunks").asList(chunk -> chunk.asLong(0L));
        List<StagedDungeonJob> jobs = new ArrayList<>();
        List<UnreadableJob> unreadable = new ArrayList<>();
        for (Dynamic<T> entry : input.get("jobs").asList(Function.identity())) {
            StagedDungeonJob.CODEC.parse(entry).ifSuccess(jobs::add).ifError(error -> unreadable.add(UnreadableJob.of(entry, error.message())));
        }
        for (Dynamic<T> entry : input.get("unreadable_jobs").asList(Function.identity())) {
            unreadable.add(new UnreadableJob(entry.get("record").orElseEmptyMap().convert(NbtOps.INSTANCE).getValue(),
                    entry.get("error").asString("")));
        }
        int migrated = 0;
        for (Dynamic<T> entry : input.get("pending_jobs").asList(Function.identity())) {
            DataResult<StagedDungeonJob> legacy = StagedDungeonJob.fromLegacy(entry);
            if (legacy.isSuccess()) {
                jobs.add(legacy.getOrThrow());
                migrated++;
            } else {
                unreadable.add(UnreadableJob.of(entry, legacy.error().orElseThrow().message()));
            }
        }
        for (UnreadableJob job : unreadable) {
            ProceduralDungeon.LOGGER.error("Kept an unreadable staged dungeon job as failed: {}", job.error());
        }
        if (migrated > 0) {
            ProceduralDungeon.LOGGER.warn("Migrated {} staged dungeon job(s) from format {} to format {}.", migrated, version, FORMAT_VERSION);
        }
        // Format 1 had no shutdown flag; its jobs resume like after an unclean stop.
        boolean clean = version >= FORMAT_VERSION && input.get("clean_shutdown").asBoolean(false);
        return new StagedDungeonSaveData(completed, jobs, unreadable, clean, migrated);
    }

    private <T> DataResult<T> encode(DynamicOps<T> ops, T prefix) {
        RecordBuilder<T> builder = ops.mapBuilder();
        builder.add("format_version", ops.createInt(FORMAT_VERSION));
        builder.add("clean_shutdown", ops.createBoolean(stopping));
        builder.add("completed_start_chunks", ops.createLongList(completedStartChunks.longStream()));
        List<T> encodedJobs = new ArrayList<>(jobs.size());
        for (StagedDungeonJob job : jobs.values()) {
            if (!job.options.persistent()) {
                continue;
            }
            DataResult<T> encoded = StagedDungeonJob.CODEC.encodeStart(ops, job);
            if (encoded.isError()) {
                return encoded;
            }
            encodedJobs.add(encoded.getOrThrow());
        }
        builder.add("jobs", ops.createList(encodedJobs.stream()));
        if (!unreadableJobs.isEmpty()) {
            builder.add("unreadable_jobs", ops.createList(unreadableJobs.stream().map(job -> ops.createMap(Map.of(
                    ops.createString("record"), NbtOps.INSTANCE.convertTo(ops, job.record()),
                    ops.createString("error"), ops.createString(job.error())
            )))));
        }
        return builder.build(prefix);
    }

    public boolean isComplete(long startChunk) {
        return completedStartChunks.contains(startChunk);
    }

    public void markComplete(long startChunk) {
        if (completedStartChunks.add(startChunk)) {
            setDirty();
        }
    }

    LongSet completedStartChunks() {
        return completedStartChunks;
    }

    Map<UUID, StagedDungeonJob> jobs() {
        return jobs;
    }

    Collection<StagedDungeonJob> jobList() {
        return jobs.values();
    }

    List<UnreadableJob> unreadableJobs() {
        return unreadableJobs;
    }

    boolean loadedAfterCleanShutdown() {
        return loadedAfterCleanShutdown;
    }

    int migratedLegacyJobs() {
        return migratedLegacyJobs;
    }

    /**
     * Whether the migrated jobs' leaked FORCED tickets still need releasing; true only once.
     */
    boolean takeLegacyTicketRelease() {
        if (legacyTicketsReleased) {
            return false;
        }
        legacyTicketsReleased = true;
        return true;
    }

    /**
     * Called on server stopping, before the final save: the record and every chunk are then saved together.
     */
    void markStopping() {
        stopping = true;
        setDirty();
    }

    void jobChanged(StagedDungeonJob job) {
        if (job.options.persistent()) {
            setDirty();
        }
    }

    /**
     * A record that failed to decode, with the raw tag and the decoder's message.
     */
    record UnreadableJob(Tag record, String error) {
        static <T> UnreadableJob of(Dynamic<T> record, String error) {
            return new UnreadableJob(record.convert(NbtOps.INSTANCE).getValue(), error);
        }
    }
}
