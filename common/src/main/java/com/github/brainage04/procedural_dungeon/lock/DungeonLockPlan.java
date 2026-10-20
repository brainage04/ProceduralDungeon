package com.github.brainage04.procedural_dungeon.lock;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Lock layout of one compiled dungeon.
 *
 * @param lockedChests chests that need a {@link DungeonKeyType#RUSTED} key
 * @param keySources   chests whose loot table is replaced so they hold a key
 * @param doors        doors of guarded rooms, by lower-half position; a door without a lock is opened on placement
 * @param rooms        boss and miniboss rooms: protected while their door is locked or their guardian lives, and
 *                     holding reward chests that open only once the guardian is gone
 */
public record DungeonLockPlan(
        List<Long> lockedChests,
        List<KeySource> keySources,
        List<Door> doors,
        List<GuardedRoom> rooms
) {
    public static final DungeonLockPlan EMPTY = new DungeonLockPlan(List.of(), List.of(), List.of(), List.of());
    private static final String BOSS_KEY_VAULT_LOOT = "boss_key_vault/";

    public static final Codec<KeySource> KEY_SOURCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("pos").forGetter(KeySource::pos),
            Identifier.CODEC.fieldOf("loot_table").forGetter(KeySource::lootTable)
    ).apply(instance, KeySource::new));

    public static final Codec<Door> DOOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("pos").forGetter(Door::pos),
            DungeonKeyType.CODEC.optionalFieldOf("lock").forGetter(Door::lock)
    ).apply(instance, Door::new));

    public static final Codec<GuardedRoom> ROOM_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BoundingBox.CODEC.fieldOf("box").forGetter(GuardedRoom::box),
            Codec.LONG.fieldOf("door").forGetter(GuardedRoom::door),
            Codec.LONG.listOf().optionalFieldOf("reward_chests", List.of()).forGetter(GuardedRoom::rewardChests)
    ).apply(instance, GuardedRoom::new));

    public static final Codec<DungeonLockPlan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().optionalFieldOf("locked_chests", List.of()).forGetter(DungeonLockPlan::lockedChests),
            KEY_SOURCE_CODEC.listOf().optionalFieldOf("key_sources", List.of()).forGetter(DungeonLockPlan::keySources),
            DOOR_CODEC.listOf().optionalFieldOf("doors", List.of()).forGetter(DungeonLockPlan::doors),
            ROOM_CODEC.listOf().optionalFieldOf("rooms", List.of()).forGetter(DungeonLockPlan::rooms)
    ).apply(instance, DungeonLockPlan::new));

    public DungeonLockPlan {
        lockedChests = List.copyOf(lockedChests);
        keySources = List.copyOf(keySources);
        doors = List.copyOf(doors);
        rooms = List.copyOf(rooms);
    }

    public boolean isEmpty() {
        return lockedChests.isEmpty() && keySources.isEmpty() && doors.isEmpty() && rooms.isEmpty();
    }

    public long lockedDoorCount(DungeonKeyType type) {
        return doors.stream().filter(door -> door.lock().equals(Optional.of(type))).count();
    }

    /**
     * This plan shifted by ({@code dx}, {@code dy}, {@code dz}), for a plan compiled at one origin and built at another.
     */
    public DungeonLockPlan moved(int dx, int dy, int dz) {
        return new DungeonLockPlan(
                lockedChests.stream().map(pos -> offset(pos, dx, dy, dz)).toList(),
                keySources.stream().map(source -> new KeySource(offset(source.pos(), dx, dy, dz), source.lootTable())).toList(),
                doors.stream().map(door -> new Door(offset(door.pos(), dx, dy, dz), door.lock())).toList(),
                rooms.stream().map(room -> new GuardedRoom(
                        room.box().moved(dx, dy, dz),
                        offset(room.door(), dx, dy, dz),
                        room.rewardChests().stream().map(pos -> offset(pos, dx, dy, dz)).toList()
                )).toList()
        );
    }

    private static long offset(long pos, int dx, int dy, int dz) {
        return BlockPos.offset(pos, dx, dy, dz);
    }

    /**
     * The boss door's lower half, if this plan locks one with the boss key.
     */
    public Optional<Long> bossDoor() {
        return doors.stream().filter(door -> door.lock().equals(Optional.of(DungeonKeyType.BOSS))).map(Door::pos).findFirst();
    }

    public static boolean isBossKeyVault(KeySource source) {
        return source.lootTable().getNamespace().equals(ProceduralDungeon.MOD_ID)
                && source.lootTable().getPath().startsWith(BOSS_KEY_VAULT_LOOT);
    }

    /**
     * Visits the plan's entries that lie in the chunks {@code box} touches. Placement applies a plan once per piece
     * (or per piece and chunk), so scanning the whole plan each time would cost pieces × entries.
     */
    public void forEachInChunks(BoundingBox box, Consumer<Entry> visitor) {
        Long2ObjectMap<List<Entry>> index = CHUNK_INDEX.getUnchecked(this);
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                List<Entry> entries = index.get(ChunkPos.pack(x, z));
                if (entries != null) {
                    entries.forEach(visitor);
                }
            }
        }
    }

    // Records cannot hold derived state, so the index lives beside the plan. Weak keys compare by identity, so a lookup
    // never hashes the plan's lists.
    private static final LoadingCache<DungeonLockPlan, Long2ObjectMap<List<Entry>>> CHUNK_INDEX = CacheBuilder.newBuilder()
            .weakKeys()
            .build(CacheLoader.from(DungeonLockPlan::buildChunkIndex));

    private static Long2ObjectMap<List<Entry>> buildChunkIndex(DungeonLockPlan plan) {
        Long2ObjectMap<List<Entry>> index = new Long2ObjectOpenHashMap<>();
        for (long chest : plan.lockedChests) {
            addToChunk(index, chest, new LockedChest(chest));
        }
        for (KeySource source : plan.keySources) {
            addToChunk(index, source.pos(), source);
        }
        for (Door door : plan.doors) {
            addToChunk(index, door.pos(), door);
        }
        for (GuardedRoom room : plan.rooms) {
            BoundingBox box = room.box();
            for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
                for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                    index.computeIfAbsent(ChunkPos.pack(x, z), key -> new ArrayList<>()).add(room);
                }
            }
        }
        return index;
    }

    private static void addToChunk(Long2ObjectMap<List<Entry>> index, long packedPos, Entry entry) {
        BlockPos pos = BlockPos.of(packedPos);
        index.computeIfAbsent(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4), key -> new ArrayList<>()).add(entry);
    }

    /**
     * One lock-plan entry, as visited by {@link #forEachInChunks}.
     */
    public sealed interface Entry permits LockedChest, KeySource, Door, GuardedRoom {}

    public record LockedChest(long pos) implements Entry {}

    public record KeySource(long pos, Identifier lootTable) implements Entry {}

    public record Door(long pos, Optional<DungeonKeyType> lock) implements Entry {}

    /**
     * A boss or miniboss room.
     *
     * @param box          the room's whole box, walls included
     * @param door         the lower half of the room's door
     * @param rewardChests chests inside the room, locked until the room's guardian is gone
     */
    public record GuardedRoom(BoundingBox box, long door, List<Long> rewardChests) implements Entry {
        public GuardedRoom {
            rewardChests = List.copyOf(rewardChests);
        }
    }
}
