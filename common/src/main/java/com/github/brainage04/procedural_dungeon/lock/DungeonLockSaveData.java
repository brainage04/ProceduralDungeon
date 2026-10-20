package com.github.brainage04.procedural_dungeon.lock;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Lock state of one level. Format versions: 0 (no {@code format_version} field) held only chests, doors, key sources
 * and puzzle chests; 1 adds protected rooms, encounter chests and boss-key records. Version 0 data loads unchanged.
 */
public class DungeonLockSaveData extends SavedData {
    public static final int FORMAT_VERSION = 1;

    private static final Codec<LockedDoor> LOCKED_DOOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("pos").forGetter(LockedDoor::pos),
            DungeonKeyType.CODEC.fieldOf("key").forGetter(LockedDoor::key)
    ).apply(instance, LockedDoor::new));

    private static final Codec<Room> ROOM_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BoundingBox.CODEC.fieldOf("box").forGetter(Room::box),
            Codec.LONG.optionalFieldOf("door").forGetter(Room::door),
            Codec.LONG.optionalFieldOf("puzzle_chest").forGetter(Room::puzzleChest),
            Codec.LONG.listOf().optionalFieldOf("reward_chests", List.of()).forGetter(Room::rewardChests),
            Codec.BOOL.optionalFieldOf("guardian_alive", false).forGetter(Room::guardianAlive)
    ).apply(instance, Room::new));

    private static final Codec<BossKey> BOSS_KEY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("door").forGetter(BossKey::door),
            Codec.LONG.fieldOf("vault").forGetter(BossKey::vault),
            UUIDUtil.CODEC.optionalFieldOf("key_id").forGetter(BossKey::keyId)
    ).apply(instance, BossKey::new));

    private static final Codec<DungeonLockSaveData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("format_version", 0).forGetter(data -> FORMAT_VERSION),
            Codec.LONG.listOf().optionalFieldOf("locked_chests", List.of())
                    .forGetter(data -> List.copyOf(data.lockedChests)),
            LOCKED_DOOR_CODEC.listOf().optionalFieldOf("key_doors", List.of())
                    .forGetter(DungeonLockSaveData::lockedDoors),
            Codec.LONG.listOf().optionalFieldOf("key_source_chests", List.of())
                    .forGetter(data -> List.copyOf(data.keySourceChests)),
            Codec.LONG.listOf().optionalFieldOf("puzzle_chests", List.of())
                    .forGetter(data -> List.copyOf(data.puzzleChests)),
            ROOM_CODEC.listOf().optionalFieldOf("rooms", List.of())
                    .forGetter(data -> List.copyOf(data.rooms.values())),
            Codec.LONG.listOf().optionalFieldOf("completed_rooms", List.of())
                    .forGetter(data -> List.copyOf(data.completedRooms)),
            Codec.LONG.listOf().optionalFieldOf("encounter_chests", List.of())
                    .forGetter(data -> List.copyOf(data.encounterChests)),
            BOSS_KEY_CODEC.listOf().optionalFieldOf("boss_keys", List.of())
                    .forGetter(data -> List.copyOf(data.bossKeys.values()))
    ).apply(instance, DungeonLockSaveData::new));

    // PD's own format_version versions this data; no vanilla fixer touches command storage contents.
    public static final SavedDataType<DungeonLockSaveData> TYPE = new SavedDataType<>(
            ProceduralDungeon.of("dungeon_locks"),
            DungeonLockSaveData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final LongSet lockedChests = new LongOpenHashSet();
    private final Long2ObjectMap<DungeonKeyType> lockedDoors = new Long2ObjectOpenHashMap<>();
    private final LongSet keySourceChests = new LongOpenHashSet();
    private final LongSet puzzleChests = new LongOpenHashSet();
    private final Long2ObjectMap<Room> rooms = new Long2ObjectOpenHashMap<>();
    private final LongSet completedRooms = new LongOpenHashSet();
    private final LongSet encounterChests = new LongOpenHashSet();
    private final Long2ObjectMap<BossKey> bossKeys = new Long2ObjectOpenHashMap<>();
    // Derived: rooms by every chunk their box touches, so per-block checks stay a hash lookup.
    private final Long2ObjectMap<List<Room>> roomsByChunk = new Long2ObjectOpenHashMap<>();

    public DungeonLockSaveData() {}

    private DungeonLockSaveData(
            int formatVersion,
            List<Long> lockedChests,
            List<LockedDoor> lockedDoors,
            List<Long> keySourceChests,
            List<Long> puzzleChests,
            List<Room> rooms,
            List<Long> completedRooms,
            List<Long> encounterChests,
            List<BossKey> bossKeys
    ) {
        this.lockedChests.addAll(lockedChests);
        lockedDoors.forEach(door -> this.lockedDoors.put(door.pos(), door.key()));
        this.keySourceChests.addAll(keySourceChests);
        this.puzzleChests.addAll(puzzleChests);
        rooms.forEach(this::putRoom);
        this.completedRooms.addAll(completedRooms);
        this.encounterChests.addAll(encounterChests);
        bossKeys.forEach(record -> this.bossKeys.put(record.door(), record));
        if (formatVersion < FORMAT_VERSION) {
            setDirty();
        }
    }

    // ---- Chests and doors

    /**
     * Whether {@code pos} is a puzzle reward chest that its room's mechanism still holds shut.
     */
    public boolean isPuzzleLocked(long pos) {
        return puzzleChests.contains(pos);
    }

    public void addPuzzleChest(long pos) {
        if (puzzleChests.add(pos)) {
            setDirty();
        }
    }

    /**
     * Whether {@code pos} is a guarded room's reward chest whose guardian is still in the world.
     */
    public boolean isEncounterLocked(long pos) {
        return encounterChests.contains(pos);
    }

    public void addEncounterChest(long pos) {
        if (encounterChests.add(pos)) {
            setDirty();
        }
    }

    /**
     * The key needed to open the block at {@code pos}, or empty when it is not locked.
     */
    public Optional<DungeonKeyType> requiredKey(long pos) {
        if (lockedChests.contains(pos)) {
            return Optional.of(DungeonKeyType.RUSTED);
        }
        return Optional.ofNullable(lockedDoors.get(pos));
    }

    public boolean isLockedDoor(long pos) {
        return lockedDoors.containsKey(pos);
    }

    /**
     * Whether {@code pos} itself is a lock, a key source or a puzzle or encounter chest.
     */
    public boolean isLockedBlock(long pos) {
        return lockedChests.contains(pos) || lockedDoors.containsKey(pos) || puzzleChests.contains(pos) || encounterChests.contains(pos);
    }

    /**
     * Whether explosions must spare {@code pos}: a lock or a key source, as recorded (the manager also checks the key
     * source still stands).
     */
    public boolean isExplosionProtected(long pos) {
        return isLockedBlock(pos) || keySourceChests.contains(pos);
    }

    public boolean isKeySource(long pos) {
        return keySourceChests.contains(pos);
    }

    public List<Long> getLockedChests() {
        return List.copyOf(lockedChests);
    }

    public List<Long> getLockedDoors(DungeonKeyType key) {
        return lockedDoors.long2ObjectEntrySet().stream()
                .filter(entry -> entry.getValue() == key)
                .map(Long2ObjectMap.Entry::getLongKey)
                .toList();
    }

    public List<Long> getKeySourceChests() {
        return List.copyOf(keySourceChests);
    }

    public void addLockedChest(long pos) {
        if (lockedChests.add(pos)) {
            setDirty();
        }
    }

    public void addLockedDoor(long pos, DungeonKeyType key) {
        if (lockedDoors.put(pos, key) != key) {
            setDirty();
        }
    }

    public void addKeySourceChest(long pos) {
        if (keySourceChests.add(pos)) {
            setDirty();
        }
    }

    public void removeKeySource(long pos) {
        if (keySourceChests.remove(pos)) {
            setDirty();
        }
    }

    /**
     * Opens whatever lock sits at {@code pos}: a rusted, puzzle or encounter chest, a door, or a key source. A room
     * whose last lock this was stops being protected.
     */
    public void unlock(long pos) {
        boolean changed = lockedChests.remove(pos) | lockedDoors.remove(pos) != null | puzzleChests.remove(pos)
                | encounterChests.remove(pos) | keySourceChests.remove(pos) | bossKeys.remove(pos) != null;
        if (changed) {
            setDirty();
            pruneRooms();
        }
    }

    // ---- Rooms

    /**
     * Records a guarded room (once): protected while {@code door} stays locked or its guardian lives.
     */
    public void addGuardedRoom(BoundingBox box, long door, List<Long> rewardChests) {
        long key = roomKey(box);
        if (rooms.containsKey(key) || completedRooms.contains(key)) {
            return;
        }
        putRoom(new Room(box, Optional.of(door), Optional.empty(), rewardChests, true));
        setDirty();
    }

    /**
     * Records a puzzle room: protected while its reward chest is held shut.
     */
    public void addPuzzleRoom(BoundingBox box, long chest) {
        long key = roomKey(box);
        if (rooms.containsKey(key)) {
            return;
        }
        putRoom(new Room(box, Optional.empty(), Optional.of(chest), List.of(), false));
        setDirty();
    }

    public boolean isRoomCompleted(BoundingBox box) {
        return completedRooms.contains(roomKey(box));
    }

    /**
     * Ends the encounter of the guarded room holding {@code home} (a guardian's spawn point): its reward chests
     * unlock. Returns the reward chests released, or an empty list when no living encounter holds that point.
     */
    public List<Long> endEncounter(BlockPos home) {
        for (Room room : roomsAt(home)) {
            if (room.guardianAlive() && room.box().isInside(home)) {
                long key = roomKey(room.box());
                putRoom(new Room(room.box(), room.door(), room.puzzleChest(), room.rewardChests(), false));
                completedRooms.add(key);
                room.rewardChests().forEach(encounterChests::remove);
                setDirty();
                pruneRooms();
                return room.rewardChests();
            }
        }
        return List.of();
    }

    /**
     * Whether {@code pos} lies inside a room that is still protected, and the room does not also hold {@code from}
     * (a piston or a fluid source moving something inside one room is that room's own business).
     */
    public boolean isProtectedFrom(BlockPos pos, BlockPos from) {
        for (Room room : roomsAt(pos)) {
            if (room.box().isInside(pos) && (from == null || !room.box().isInside(from)) && isProtected(room)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRooms() {
        return !rooms.isEmpty();
    }

    private List<Room> roomsAt(BlockPos pos) {
        List<Room> found = roomsByChunk.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
        return found == null ? List.of() : found;
    }

    private boolean isProtected(Room room) {
        return room.guardianAlive()
                || room.door().filter(lockedDoors::containsKey).isPresent()
                || room.puzzleChest().filter(puzzleChests::contains).isPresent();
    }

    private void pruneRooms() {
        List<Room> released = new ArrayList<>();
        for (Room room : rooms.values()) {
            if (!isProtected(room)) {
                released.add(room);
            }
        }
        released.forEach(this::removeRoom);
    }

    private void putRoom(Room room) {
        long key = roomKey(room.box());
        Room previous = rooms.put(key, room);
        if (previous != null) {
            forEachChunk(previous.box(), chunk -> roomsByChunk.get(chunk).remove(previous));
        }
        forEachChunk(room.box(), chunk -> roomsByChunk.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(room));
    }

    private void removeRoom(Room room) {
        rooms.remove(roomKey(room.box()));
        forEachChunk(room.box(), chunk -> {
            List<Room> list = roomsByChunk.get(chunk);
            list.remove(room);
            if (list.isEmpty()) {
                roomsByChunk.remove(chunk);
            }
        });
        setDirty();
    }

    private static void forEachChunk(BoundingBox box, LongConsumer action) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                action.accept(ChunkPos.pack(x, z));
            }
        }
    }

    private static long roomKey(BoundingBox box) {
        return BlockPos.asLong(box.minX(), box.minY(), box.minZ());
    }

    // ---- Boss keys

    public Optional<BossKey> bossKey(long door) {
        return Optional.ofNullable(bossKeys.get(door));
    }

    public List<BossKey> bossKeys() {
        return List.copyOf(bossKeys.values());
    }

    /**
     * The boss-key record whose current key carries {@code id}.
     */
    public Optional<BossKey> bossKeyById(UUID id) {
        return bossKeys.values().stream().filter(record -> record.keyId().equals(Optional.of(id))).findFirst();
    }

    /**
     * Records that the boss door at {@code door} takes its key from {@code vault}, before that vault is placed.
     */
    public void reserveBossKey(long door, long vault) {
        if (!bossKeys.containsKey(door)) {
            bossKeys.put(door, new BossKey(door, vault, Optional.empty()));
            setDirty();
        }
    }

    /**
     * Gives the boss door at {@code door} a key from {@code vault} with a new id, retiring any earlier one.
     */
    public UUID issueBossKey(long door, long vault) {
        UUID id = UUID.randomUUID();
        bossKeys.put(door, new BossKey(door, vault, Optional.of(id)));
        setDirty();
        return id;
    }

    // ---- Purge

    /**
     * Drops every record inside {@code box}: locks, key sources, puzzle and encounter chests, rooms and boss keys.
     */
    public void forget(BoundingBox box) {
        LongPredicate inside = pos -> box.isInside(BlockPos.of(pos));
        boolean changed = lockedChests.removeIf(inside)
                | lockedDoors.keySet().removeIf(inside)
                | keySourceChests.removeIf(inside)
                | puzzleChests.removeIf(inside)
                | encounterChests.removeIf(inside)
                | bossKeys.keySet().removeIf(inside)
                | completedRooms.removeIf(inside);
        List<Room> dropped = rooms.values().stream().filter(room -> box.intersects(room.box())).toList();
        dropped.forEach(this::removeRoom);
        if (changed || !dropped.isEmpty()) {
            setDirty();
        }
    }

    private List<LockedDoor> lockedDoors() {
        return lockedDoors.long2ObjectEntrySet().stream()
                .map(entry -> new LockedDoor(entry.getLongKey(), entry.getValue()))
                .toList();
    }

    private record LockedDoor(long pos, DungeonKeyType key) {}

    /**
     * A protected room: a guarded room has a door (and reward chests until its guardian is gone), a puzzle room has
     * its reward chest.
     */
    private record Room(BoundingBox box, Optional<Long> door, Optional<Long> puzzleChest, List<Long> rewardChests, boolean guardianAlive) {}

    /**
     * The boss door at {@code door} opens only for the key carrying {@code keyId}; the vault chest at {@code vault}
     * gets a fresh key when that one is destroyed. An empty id means the vault has not been placed yet.
     */
    public record BossKey(long door, long vault, Optional<UUID> keyId) {}
}
