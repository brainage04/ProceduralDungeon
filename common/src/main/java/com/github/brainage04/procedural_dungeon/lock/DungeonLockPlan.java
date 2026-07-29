package com.github.brainage04.procedural_dungeon.lock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.Identifier;

public record DungeonLockPlan(
        List<Long> lockedChests,
        List<KeySource> keySources
) {
    public static final DungeonLockPlan EMPTY = new DungeonLockPlan(List.of(), List.of());

    public static final Codec<KeySource> KEY_SOURCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("pos").forGetter(KeySource::pos),
            Identifier.CODEC.fieldOf("loot_table").forGetter(KeySource::lootTable)
    ).apply(instance, KeySource::new));

    public static final Codec<DungeonLockPlan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().optionalFieldOf("locked_chests", List.of()).forGetter(DungeonLockPlan::lockedChests),
            KEY_SOURCE_CODEC.listOf().optionalFieldOf("key_sources", List.of()).forGetter(DungeonLockPlan::keySources)
    ).apply(instance, DungeonLockPlan::new));

    public DungeonLockPlan {
        lockedChests = List.copyOf(lockedChests);
        keySources = List.copyOf(keySources);
    }

    public boolean isEmpty() {
        return lockedChests.isEmpty() && keySources.isEmpty();
    }

    public record KeySource(long pos, Identifier lootTable) {}
}
