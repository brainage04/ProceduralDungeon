package com.github.brainage04.procedural_dungeon.lock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Lock layout of one compiled dungeon.
 *
 * @param lockedChests chests that need a {@link DungeonKeyType#RUSTED} key
 * @param keySources   chests whose loot table is replaced so they hold a key
 * @param doors        doors of guarded rooms, by lower-half position; a door without a lock is opened on placement
 */
public record DungeonLockPlan(
        List<Long> lockedChests,
        List<KeySource> keySources,
        List<Door> doors
) {
    public static final DungeonLockPlan EMPTY = new DungeonLockPlan(List.of(), List.of(), List.of());

    public static final Codec<KeySource> KEY_SOURCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("pos").forGetter(KeySource::pos),
            Identifier.CODEC.fieldOf("loot_table").forGetter(KeySource::lootTable)
    ).apply(instance, KeySource::new));

    public static final Codec<Door> DOOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("pos").forGetter(Door::pos),
            DungeonKeyType.CODEC.optionalFieldOf("lock").forGetter(Door::lock)
    ).apply(instance, Door::new));

    public static final Codec<DungeonLockPlan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().optionalFieldOf("locked_chests", List.of()).forGetter(DungeonLockPlan::lockedChests),
            KEY_SOURCE_CODEC.listOf().optionalFieldOf("key_sources", List.of()).forGetter(DungeonLockPlan::keySources),
            DOOR_CODEC.listOf().optionalFieldOf("doors", List.of()).forGetter(DungeonLockPlan::doors)
    ).apply(instance, DungeonLockPlan::new));

    public DungeonLockPlan {
        lockedChests = List.copyOf(lockedChests);
        keySources = List.copyOf(keySources);
        doors = List.copyOf(doors);
    }

    public boolean isEmpty() {
        return lockedChests.isEmpty() && keySources.isEmpty() && doors.isEmpty();
    }

    public long lockedDoorCount(DungeonKeyType type) {
        return doors.stream().filter(door -> door.lock().equals(Optional.of(type))).count();
    }

    public record KeySource(long pos, Identifier lootTable) {}

    public record Door(long pos, Optional<DungeonKeyType> lock) {}
}
