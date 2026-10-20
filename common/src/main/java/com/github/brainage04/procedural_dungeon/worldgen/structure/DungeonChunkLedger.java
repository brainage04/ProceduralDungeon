package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-chunk record of what staged dungeon jobs wrote into the chunk, saved with the chunk itself (a
 * Fabric data attachment or a NeoForge attachment, both called {@code procedural_dungeon:placed}).
 * The marker for a (job, piece) pair is set in the same server-thread step as the piece's blocks in
 * this chunk, so the chunk file always holds both or neither: a resumed job skips every marked part
 * instead of pasting it again.
 *
 * <p>The lock-entry map holds lock state by block position, for the lock manager.
 */
public final class DungeonChunkLedger {
    public static final String ATTACHMENT_NAME = "placed";

    private static final Codec<Map<String, CompoundTag>> LOCK_ENTRIES_CODEC =
            Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC);
    public static final MapCodec<Data> MAP_CODEC =
            RecordCodecBuilder.mapCodec(
                    instance ->
                            instance.group(
                                            Codec.unboundedMap(
                                                            UUIDUtil.STRING_CODEC,
                                                            Codec.INT.listOf())
                                                    .optionalFieldOf("placed", Map.of())
                                                    .forGetter(Data::encodedPlaced),
                                            LOCK_ENTRIES_CODEC
                                                    .optionalFieldOf("lock_entries", Map.of())
                                                    .forGetter(Data::encodedLockEntries))
                                    .apply(instance, Data::decode));
    public static final Codec<Data> CODEC = MAP_CODEC.codec();

    private static Backend backend;

    private DungeonChunkLedger() {}

    /** Called once by each loader with its attachment storage. */
    public static void setBackend(Backend storage) {
        backend = storage;
    }

    /** Piece indices of {@code job} already written into {@code chunk}. */
    public static IntSet placed(LevelChunk chunk, UUID job) {
        IntSet pieces = data(chunk).placed().get(job);
        return pieces == null ? IntSets.EMPTY_SET : IntSets.unmodifiable(pieces);
    }

    public static boolean isPlaced(LevelChunk chunk, UUID job, int piece) {
        IntSet pieces = data(chunk).placed().get(job);
        return pieces != null && pieces.contains(piece);
    }

    /**
     * Records that {@code piece} of {@code job} is written into {@code chunk}; marks the chunk
     * unsaved.
     */
    public static void markPlaced(LevelChunk chunk, UUID job, int piece) {
        Data data = data(chunk);
        IntSet existing = data.placed().get(job);
        if (existing != null && existing.contains(piece)) {
            return;
        }
        Map<UUID, IntSet> placed = new HashMap<>(data.placed());
        IntArraySet pieces = existing == null ? new IntArraySet(1) : new IntArraySet(existing);
        pieces.add(piece);
        placed.put(job, pieces);
        store(chunk, new Data(placed, data.lockEntries()));
    }

    /** Drops every marker of {@code job} from {@code chunk}. */
    public static void forget(LevelChunk chunk, UUID job) {
        Data data = data(chunk);
        if (!data.placed().containsKey(job)) {
            return;
        }
        Map<UUID, IntSet> placed = new HashMap<>(data.placed());
        placed.remove(job);
        store(chunk, new Data(placed, data.lockEntries()));
    }

    /** Drops one piece's marker; test hook for chunks whose save lost the piece. */
    public static void unmark(LevelChunk chunk, UUID job, int piece) {
        Data data = data(chunk);
        IntSet existing = data.placed().get(job);
        if (existing == null || !existing.contains(piece)) {
            return;
        }
        Map<UUID, IntSet> placed = new HashMap<>(data.placed());
        IntArraySet pieces = new IntArraySet(existing);
        pieces.remove(piece);
        if (pieces.isEmpty()) {
            placed.remove(job);
        } else {
            placed.put(job, pieces);
        }
        store(chunk, new Data(placed, data.lockEntries()));
    }

    public static Optional<CompoundTag> lockEntry(LevelChunk chunk, long pos) {
        CompoundTag entry = data(chunk).lockEntries().get(pos);
        return entry == null ? Optional.empty() : Optional.of(entry.copy());
    }

    public static Map<Long, CompoundTag> lockEntries(LevelChunk chunk) {
        return data(chunk).lockEntries();
    }

    public static void putLockEntry(LevelChunk chunk, long pos, CompoundTag entry) {
        Data data = data(chunk);
        Map<Long, CompoundTag> entries = new HashMap<>(data.lockEntries());
        entries.put(pos, entry.copy());
        store(chunk, new Data(data.placed(), entries));
    }

    public static void removeLockEntry(LevelChunk chunk, long pos) {
        Data data = data(chunk);
        if (!data.lockEntries().containsKey(pos)) {
            return;
        }
        Map<Long, CompoundTag> entries = new HashMap<>(data.lockEntries());
        entries.remove(pos);
        store(chunk, new Data(data.placed(), entries));
    }

    private static Data data(LevelChunk chunk) {
        Data data = backend().get(chunk);
        return data == null ? Data.EMPTY : data;
    }

    private static void store(LevelChunk chunk, Data data) {
        backend().set(chunk, data.isEmpty() ? null : data);
    }

    private static Backend backend() {
        return Objects.requireNonNull(
                backend, "The loader did not register the procedural_dungeon:placed attachment");
    }

    /**
     * Loader storage of the attachment. Implementations mark the chunk unsaved on {@link #set};
     * {@code null} removes it.
     */
    public interface Backend {
        Data get(LevelChunk chunk);

        void set(LevelChunk chunk, Data data);
    }

    /** Immutable attachment value; every change stores a new instance. */
    public record Data(Map<UUID, IntSet> placed, Map<Long, CompoundTag> lockEntries) {
        public static final Data EMPTY = new Data(Map.of(), Map.of());

        public Data {
            placed = Map.copyOf(placed);
            lockEntries = Map.copyOf(lockEntries);
        }

        public boolean isEmpty() {
            return placed.isEmpty() && lockEntries.isEmpty();
        }

        private static Data decode(
                Map<UUID, List<Integer>> placed, Map<String, CompoundTag> lockEntries) {
            Map<UUID, IntSet> pieces = new HashMap<>(placed.size());
            placed.forEach((job, indices) -> pieces.put(job, new IntArraySet(indices)));
            Map<Long, CompoundTag> entries = new HashMap<>(lockEntries.size());
            lockEntries.forEach((pos, entry) -> entries.put(Long.parseLong(pos), entry));
            return new Data(pieces, entries);
        }

        private Map<UUID, List<Integer>> encodedPlaced() {
            Map<UUID, List<Integer>> encoded = new HashMap<>(placed.size());
            placed.forEach(
                    (job, pieces) ->
                            encoded.put(job, pieces.intStream().sorted().boxed().toList()));
            return encoded;
        }

        private Map<String, CompoundTag> encodedLockEntries() {
            Map<String, CompoundTag> encoded = new HashMap<>(lockEntries.size());
            lockEntries.forEach((pos, entry) -> encoded.put(Long.toString(pos), entry));
            return encoded;
        }
    }
}
