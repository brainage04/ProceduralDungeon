package com.github.brainage04.procedural_dungeon.lock;

import com.github.brainage04.procedural_dungeon.guardian.DungeonGuardian;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonChunkLedger;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonPuzzleRooms;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

public final class DungeonLockManager {
    private static final AtomicLong PLAN_ENTRIES_VISITED = new AtomicLong();
    /**
     * Ledger key marking a chunk whose ledger holds its lock state (no block position packs to it: its x lies far
     * outside any world border).
     */
    private static final long LEDGER_MANAGED = Long.MIN_VALUE;
    private static boolean initialized;

    private DungeonLockManager() {}

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
    }

    public static InteractionResult useBlock(Player player, ServerLevel level, BlockPos pos) {
        DungeonLockSaveData data = existingData(level);
        if (data == null) {
            return InteractionResult.PASS;
        }
        BlockPos connected = connectedPos(level, pos);
        // A chest placed beside a locked chest joins it; opening the new half would open the locked one too.
        BlockPos lockPos = connected != null && data.isLockedBlock(connected.asLong()) && !data.isLockedBlock(pos.asLong()) ? connected : pos;

        if (data.isPuzzleLocked(lockPos.asLong())) {
            if (!DungeonPuzzleRooms.isSolved(level, lockPos)) {
                displayMessage(player, "The room's mechanism holds this chest shut.");
                return InteractionResult.FAIL;
            }
            unlock(level, data, lockPos);
            flushToLedger(level, data);
            displayMessage(player, "The mechanism releases the chest.");
            return InteractionResult.PASS;
        }
        if (data.isEncounterLocked(lockPos.asLong())) {
            displayMessage(player, "The room's guardian still holds this chest shut.");
            return InteractionResult.FAIL;
        }
        Optional<DungeonKeyType> requiredKey = data.requiredKey(lockPos.asLong());
        if (requiredKey.isEmpty()) {
            return InteractionResult.PASS;
        }

        DungeonKeyType key = requiredKey.get();
        boolean door = data.isLockedDoor(lockPos.asLong());
        // A boss door with a key record opens only for the key carrying the record's current id.
        Optional<DungeonLockSaveData.BossKey> bossKey = door && key == DungeonKeyType.BOSS
                ? data.bossKey(lowerDoorHalf(level, lockPos).asLong())
                : Optional.empty();
        if (bossKey.isPresent() ? !consumeBoundKey(player, bossKey.get().keyId()) : !consumeKey(player, key)) {
            displayMessage(player, "This lock needs a %s.".formatted(key.displayName()));
            return InteractionResult.FAIL;
        }

        unlock(level, data, lockPos);
        flushToLedger(level, data);
        displayMessage(player, "Unlocked with a %s.".formatted(key.displayName()));
        if (door) {
            openDoor(level, lockPos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static boolean canBreak(Player player, ServerLevel level, BlockPos pos) {
        DungeonLockSaveData data = existingData(level);
        if (data == null) {
            return true;
        }
        if (data.isPuzzleLocked(pos.asLong())) {
            displayMessage(player, "The room's mechanism holds this chest shut.");
            return false;
        }
        if (data.isEncounterLocked(pos.asLong())) {
            displayMessage(player, "The room's guardian still holds this chest shut.");
            return false;
        }
        Optional<DungeonKeyType> requiredKey = data.requiredKey(pos.asLong());
        if (requiredKey.isPresent()) {
            displayMessage(player, "This lock needs a %s.".formatted(requiredKey.get().displayName()));
            return false;
        }
        if (data.isProtectedFrom(pos, null)) {
            displayMessage(player, "A ward holds this room together while it is sealed.");
            return false;
        }
        return true;
    }

    /**
     * Whether something outside a protected room may not change the block at {@code pos} (pistons, fluids): true when
     * {@code pos} lies in a protected room that does not also hold {@code from}.
     */
    public static boolean isProtectedFrom(ServerLevel level, BlockPos pos, BlockPos from) {
        DungeonLockSaveData data = existingData(level);
        return data != null && data.hasRooms() && data.isProtectedFrom(pos, from);
    }

    /**
     * Whether a hopper may not pull from the container at {@code pos}: a locked, puzzle or encounter chest, or a chest
     * joined to one.
     */
    public static boolean isExtractionLocked(ServerLevel level, BlockPos pos) {
        DungeonLockSaveData data = existingData(level);
        if (data == null) {
            return false;
        }
        if (data.isLockedBlock(pos.asLong())) {
            return true;
        }
        BlockPos connected = connectedPos(level, pos);
        return connected != null && data.isLockedBlock(connected.asLong());
    }

    /**
     * Applies the plan's entries inside {@code pieceBox}. Placement calls this right after writing the piece (or its
     * part in one chunk), so the positions are loaded; entries in unloaded chunks are left for their own placement
     * rather than loading those chunks here.
     */
    public static void applyPlanForPiece(ServerLevel level, DungeonLockPlan plan, BoundingBox pieceBox) {
        if (plan.isEmpty()) {
            return;
        }

        DungeonLockSaveData data = data(level);
        plan.forEachInChunks(pieceBox, entry -> {
            PLAN_ENTRIES_VISITED.incrementAndGet();
            switch (entry) {
                case DungeonLockPlan.LockedChest chest -> {
                    BlockPos pos = BlockPos.of(chest.pos());
                    if (pieceBox.isInside(pos) && level.isLoaded(pos)) {
                        addLockedChest(level, data, pos);
                    }
                }
                case DungeonLockPlan.KeySource keySource -> applyKeySource(level, data, plan, keySource, pieceBox);
                case DungeonLockPlan.Door door -> applyDoor(level, data, plan, door, pieceBox);
                case DungeonLockPlan.GuardedRoom room -> applyRoom(level, data, room, pieceBox);
            }
        });
        flushToLedger(level, data);
    }

    private static void applyKeySource(ServerLevel level, DungeonLockSaveData data, DungeonLockPlan plan,
                                       DungeonLockPlan.KeySource keySource, BoundingBox pieceBox) {
        BlockPos pos = BlockPos.of(keySource.pos());
        if (!pieceBox.isInside(pos) || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RandomizableContainer container)) {
            return;
        }
        data.addKeySourceChest(keySource.pos());
        ResourceKey<LootTable> lootTable = ResourceKey.create(Registries.LOOT_TABLE, keySource.lootTable());
        container.setLootTable(lootTable, level.getSeed() ^ pos.asLong() ^ 0x5EED5EEDL);
        ((BlockEntity) container).setChanged();
        Optional<Long> bossDoor = plan.bossDoor();
        if (DungeonLockPlan.isBossKeyVault(keySource) && bossDoor.isPresent()) {
            // Roll the vault now, so its Boss Key exists from the start and can carry the door's key id.
            container.unpackLootTable(null);
            UUID id = data.issueBossKey(bossDoor.get(), keySource.pos());
            boolean bound = false;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (DungeonKeyType.BOSS.matches(stack)) {
                    if (bound) {
                        container.setItem(slot, ItemStack.EMPTY);
                    } else {
                        DungeonKeyType.bind(stack, id, level.dimension());
                        bound = true;
                    }
                }
            }
            if (!bound) {
                DungeonBossKeys.deliver(level, BlockPos.of(keySource.pos()), DungeonKeyType.BOSS.createStack(id, level.dimension()));
            }
            ((BlockEntity) container).setChanged();
        }
    }

    private static void applyDoor(ServerLevel level, DungeonLockSaveData data, DungeonLockPlan plan,
                                  DungeonLockPlan.Door door, BoundingBox pieceBox) {
        BlockPos lower = BlockPos.of(door.pos());
        if (!pieceBox.isInside(lower) || !level.isLoaded(lower) || !(level.getBlockState(lower).getBlock() instanceof DoorBlock)) {
            return;
        }
        if (door.lock().isEmpty()) {
            openDoor(level, lower);
            return;
        }
        data.addLockedDoor(lower.asLong(), door.lock().get());
        data.addLockedDoor(lower.above().asLong(), door.lock().get());
        if (door.lock().get() == DungeonKeyType.BOSS && data.bossKey(lower.asLong()).isEmpty()) {
            // The vault may be placed later; until it is, no key opens this door.
            plan.keySources().stream()
                    .filter(DungeonLockPlan::isBossKeyVault)
                    .findFirst()
                    .ifPresent(vault -> data.reserveBossKey(lower.asLong(), vault.pos()));
        }
    }

    private static void applyRoom(ServerLevel level, DungeonLockSaveData data, DungeonLockPlan.GuardedRoom room, BoundingBox pieceBox) {
        if (!room.box().intersects(pieceBox) || data.isRoomCompleted(room.box())) {
            return;
        }
        data.addGuardedRoom(room.box(), room.door(), room.rewardChests());
        for (long chest : room.rewardChests()) {
            BlockPos pos = BlockPos.of(chest);
            if (pieceBox.isInside(pos) && level.isLoaded(pos)) {
                data.addEncounterChest(chest);
                BlockPos connected = connectedPos(level, pos);
                if (connected != null) {
                    data.addEncounterChest(connected.asLong());
                }
            }
        }
    }

    /**
     * How many lock-plan entries {@link #applyPlanForPiece} has looked at since the server started; a diagnostic of
     * the plan's per-chunk index.
     */
    public static long planEntriesVisited() {
        return PLAN_ENTRIES_VISITED.get();
    }

    /**
     * Holds a puzzle room's reward chest shut until {@link DungeonPuzzleRooms#isSolved} reports its mechanism solved.
     */
    public static void lockPuzzleChest(ServerLevel level, BlockPos pos) {
        DungeonLockSaveData data = data(level);
        data.addPuzzleChest(pos.asLong());
        flushToLedger(level, data);
    }

    /**
     * Protects the puzzle room in {@code box} as a whole while its reward chest at {@code chest} stays locked.
     */
    public static void protectPuzzleRoom(ServerLevel level, BoundingBox box, BlockPos chest) {
        data(level).addPuzzleRoom(box, chest.asLong());
    }

    /**
     * A guardian left the world for good (killed, or discarded such as on Peaceful): its room's reward chests open.
     */
    public static void onGuardianRemoved(ServerLevel level, Mob guardian) {
        DungeonLockSaveData data = existingData(level);
        if (data != null) {
            data.endEncounter(DungeonGuardian.home(guardian).orElse(guardian.blockPosition()));
            flushToLedger(level, data);
        }
    }

    /**
     * Drops every lock record inside {@code box}: locks, key sources, puzzle and encounter chests, protected rooms and
     * boss-key records.
     */
    public static void forget(ServerLevel level, BoundingBox box) {
        DungeonLockSaveData data = existingData(level);
        if (data != null) {
            data.forget(box);
            flushToLedger(level, data);
        }
    }

    public static boolean isPuzzleLocked(ServerLevel level, BlockPos pos) {
        DungeonLockSaveData data = existingData(level);
        return data != null && data.isPuzzleLocked(pos.asLong());
    }

    public static boolean isEncounterLocked(ServerLevel level, BlockPos pos) {
        DungeonLockSaveData data = existingData(level);
        return data != null && data.isEncounterLocked(pos.asLong());
    }

    public static boolean isDoorLocked(ServerLevel level, BlockPos pos) {
        DungeonLockSaveData data = existingData(level);
        return data != null && data.isLockedDoor(pos.asLong());
    }

    public static boolean isExplosionProtected(ServerLevel level, BlockPos pos) {
        DungeonLockSaveData data = existingData(level);
        if (data == null) {
            return false;
        }
        if (data.isLockedBlock(pos.asLong())) {
            return true;
        }
        if (data.isKeySource(pos.asLong())) {
            if (level.getBlockEntity(pos) instanceof Container) {
                return true;
            }
            // The key chest was broken or replaced; whatever stands there now is not protected.
            data.removeKeySource(pos.asLong());
            flushToLedger(level, data);
        }
        return data.hasRooms() && data.isProtectedFrom(pos, null);
    }

    /**
     * Copies the lock state of every changed position into its chunk's ledger entry, so the chunk file carries its own
     * locks and saves them together with its blocks. Chunks hold their own positions in the same server-thread step as
     * the change; {@link #forget} may load the chunks of a box it clears.
     */
    private static void flushToLedger(ServerLevel level, DungeonLockSaveData data) {
        LongSet changed = data.drainChangedPositions();
        for (long pos : changed) {
            BlockPos block = BlockPos.of(pos);
            LevelChunk chunk = level.getChunk(block.getX() >> 4, block.getZ() >> 4);
            CompoundTag entry = data.ledgerEntry(pos);
            if (entry == null) {
                DungeonChunkLedger.removeLockEntry(chunk, pos);
            } else {
                DungeonChunkLedger.putLockEntry(chunk, pos, entry);
            }
            if (!DungeonChunkLedger.lockEntries(chunk).containsKey(LEDGER_MANAGED)) {
                DungeonChunkLedger.putLockEntry(chunk, LEDGER_MANAGED, new CompoundTag());
            }
        }
    }

    /**
     * A chunk loaded. Its ledger is the record saved together with its blocks, so it decides the lock state of its
     * positions: a door unlocked before the chunk was saved stays unlocked even if the level's lock index was saved
     * earlier, and a door whose chunk came back locked is locked again. A chunk from before the ledger held locks gets
     * its entries copied in from the index.
     */
    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        Map<Long, CompoundTag> entries = DungeonChunkLedger.lockEntries(chunk);
        long chunkKey = chunk.getPos().pack();
        DungeonLockSaveData data = existingData(level);
        if (entries.containsKey(LEDGER_MANAGED)) {
            if (data == null && entries.size() == 1) {
                return;
            }
            Map<Long, CompoundTag> positions = new HashMap<>(entries);
            positions.remove(LEDGER_MANAGED);
            (data == null ? data(level) : data).replaceChunk(chunkKey, positions);
        } else if (data != null && data.hasPositionsIn(chunkKey)) {
            for (long pos : data.positionsIn(chunkKey)) {
                CompoundTag entry = data.ledgerEntry(pos);
                if (entry != null) {
                    DungeonChunkLedger.putLockEntry(chunk, pos, entry);
                }
            }
            DungeonChunkLedger.putLockEntry(chunk, LEDGER_MANAGED, new CompoundTag());
        }
    }

    /**
     * The level's lock data for writing; created on first use.
     */
    static DungeonLockSaveData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DungeonLockSaveData.TYPE);
    }

    /**
     * The level's lock data, or {@code null} when no dungeon ever locked anything there; read paths never create it.
     */
    static DungeonLockSaveData existingData(ServerLevel level) {
        return level.getDataStorage().get(DungeonLockSaveData.TYPE);
    }

    private static void addLockedChest(ServerLevel level, DungeonLockSaveData data, BlockPos pos) {
        data.addLockedChest(pos.asLong());
        BlockPos connectedPos = connectedPos(level, pos);
        if (connectedPos != null) {
            data.addLockedChest(connectedPos.asLong());
        }
    }

    private static void unlock(ServerLevel level, DungeonLockSaveData data, BlockPos pos) {
        BlockPos connectedPos = connectedPos(level, pos);
        data.unlock(pos.asLong());
        if (connectedPos != null) {
            data.unlock(connectedPos.asLong());
        }
    }

    /**
     * The other block of a double chest or of a door, or {@code null} for single blocks.
     */
    private static BlockPos connectedPos(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)) {
            return null;
        }
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return null;
        }
        return ChestBlock.getConnectedBlockPos(pos, state);
    }

    private static BlockPos lowerDoorHalf(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    private static void openDoor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(null, level, state, pos, true);
        }
    }

    private static boolean consumeKey(Player player, DungeonKeyType type) {
        return consumeFirst(player, type::matches);
    }

    /**
     * Uses up the Boss Key carrying {@code id}; an empty id (vault not placed yet) matches no key.
     */
    private static boolean consumeBoundKey(Player player, Optional<UUID> id) {
        return id.isPresent() && consumeFirst(player, stack -> DungeonKeyType.BOSS.matches(stack)
                && DungeonKeyType.keyId(stack).equals(id));
    }

    private static boolean consumeFirst(Player player, Predicate<ItemStack> key) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!key.test(stack)) {
                continue;
            }

            if (!player.isCreative()) {
                stack.shrink(1);
                inventory.setChanged();
            }
            return true;
        }
        return false;
    }

    static void displayMessage(Player player, String message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal(message));
        }
    }
}
