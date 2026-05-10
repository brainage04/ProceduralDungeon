package com.github.brainage04.procedural_dungeon.lock;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class DungeonLockSaveData extends SavedData {
    private static final Codec<DungeonLockSaveData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().optionalFieldOf("locked_chests", List.of())
                    .forGetter(DungeonLockSaveData::lockedChests),
            Codec.LONG.listOf().optionalFieldOf("locked_doors", List.of())
                    .forGetter(DungeonLockSaveData::lockedDoors)
    ).apply(instance, DungeonLockSaveData::new));

    public static final SavedDataType<DungeonLockSaveData> TYPE = new SavedDataType<>(
            ProceduralDungeon.of("dungeon_locks"),
            DungeonLockSaveData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_MAP_DATA
    );

    private final LongSet lockedChests = new LongOpenHashSet();
    private final LongSet lockedDoors = new LongOpenHashSet();

    public DungeonLockSaveData() {}

    private DungeonLockSaveData(List<Long> lockedChests, List<Long> lockedDoors) {
        this.lockedChests.addAll(lockedChests);
        this.lockedDoors.addAll(lockedDoors);
    }

    public boolean isLocked(long pos) {
        return lockedChests.contains(pos) || lockedDoors.contains(pos);
    }

    public boolean isLockedChest(long pos) {
        return lockedChests.contains(pos);
    }

    public List<Long> getLockedChests() {
        return lockedChests();
    }

    public List<Long> getLockedDoors() {
        return lockedDoors();
    }

    public boolean addLockedChest(long pos) {
        if (lockedChests.add(pos)) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean addLockedDoor(long pos) {
        if (lockedDoors.add(pos)) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean unlock(long pos) {
        boolean changed = lockedChests.remove(pos) | lockedDoors.remove(pos);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    private List<Long> lockedChests() {
        return lockedChests.longStream().boxed().toList();
    }

    private List<Long> lockedDoors() {
        return lockedDoors.longStream().boxed().toList();
    }
}
