package com.github.brainage04.procedural_dungeon.lock;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class DungeonLockSaveData extends SavedData {
    private static final Codec<LockedDoor> LOCKED_DOOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("pos").forGetter(LockedDoor::pos),
            DungeonKeyType.CODEC.fieldOf("key").forGetter(LockedDoor::key)
    ).apply(instance, LockedDoor::new));

    private static final Codec<DungeonLockSaveData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().optionalFieldOf("locked_chests", List.of())
                    .forGetter(DungeonLockSaveData::lockedChests),
            LOCKED_DOOR_CODEC.listOf().optionalFieldOf("key_doors", List.of())
                    .forGetter(DungeonLockSaveData::lockedDoors),
            Codec.LONG.listOf().optionalFieldOf("key_source_chests", List.of())
                    .forGetter(DungeonLockSaveData::keySourceChests)
    ).apply(instance, DungeonLockSaveData::new));

    public static final SavedDataType<DungeonLockSaveData> TYPE = new SavedDataType<>(
            ProceduralDungeon.of("dungeon_locks"),
            DungeonLockSaveData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_MAP_DATA
    );

    private final LongSet lockedChests = new LongOpenHashSet();
    private final Long2ObjectMap<DungeonKeyType> lockedDoors = new Long2ObjectOpenHashMap<>();
    private final LongSet keySourceChests = new LongOpenHashSet();

    public DungeonLockSaveData() {}

    private DungeonLockSaveData(List<Long> lockedChests, List<LockedDoor> lockedDoors, List<Long> keySourceChests) {
        this.lockedChests.addAll(lockedChests);
        lockedDoors.forEach(door -> this.lockedDoors.put(door.pos(), door.key()));
        this.keySourceChests.addAll(keySourceChests);
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

    public boolean isExplosionProtected(long pos) {
        return lockedChests.contains(pos) || lockedDoors.containsKey(pos) || keySourceChests.contains(pos);
    }

    public List<Long> getLockedChests() {
        return lockedChests();
    }

    public List<Long> getLockedDoors(DungeonKeyType key) {
        return lockedDoors.long2ObjectEntrySet().stream()
                .filter(entry -> entry.getValue() == key)
                .map(Long2ObjectMap.Entry::getLongKey)
                .toList();
    }

    public List<Long> getKeySourceChests() {
        return keySourceChests();
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

    public void unlock(long pos) {
        boolean changed = lockedChests.remove(pos) | lockedDoors.remove(pos) != null;
        if (changed) {
            setDirty();
        }
    }

    private List<Long> lockedChests() {
        return lockedChests.longStream().boxed().toList();
    }

    private List<LockedDoor> lockedDoors() {
        return lockedDoors.long2ObjectEntrySet().stream()
                .map(entry -> new LockedDoor(entry.getLongKey(), entry.getValue()))
                .toList();
    }

    private List<Long> keySourceChests() {
        return keySourceChests.longStream().boxed().toList();
    }

    private record LockedDoor(long pos, DungeonKeyType key) {}
}
