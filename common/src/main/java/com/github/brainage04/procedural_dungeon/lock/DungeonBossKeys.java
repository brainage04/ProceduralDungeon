package com.github.brainage04.procedural_dungeon.lock;

import com.github.brainage04.procedural_dungeon.api.impl.DungeonEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps exactly one valid Boss Key per boss door and replaces a destroyed one. The door's lock
 * record holds the id of its current key ({@link DungeonLockSaveData.BossKey}); the hooks below
 * report the ways a key can leave the world, and when the current key is among them the record
 * draws a new id and the vault gets one new key. See the README.
 */
public final class DungeonBossKeys {
    /**
     * How long a key taken out of a creative inventory slot may stay out of sight before it counts
     * as deleted.
     */
    public static final int CREATIVE_GRACE_TICKS = 600;

    /** How far from a boss door {@code /dungeon bosskey reissue} looks for it. */
    public static final int REISSUE_RANGE = 256;

    private static final double NOTICE_RANGE = 64.0;

    private static final Map<UUID, PendingCreativeLoss> PENDING_CREATIVE = new HashMap<>();

    private DungeonBossKeys() {}

    /**
     * An item entity is leaving the world. Destroying reasons (damage, the void, despawning, {@code
     * /kill}) destroy a key still in its stack; pickup, hoppers and merging empty the stack first,
     * and unloading or changing dimension do not destroy.
     */
    public static void onItemEntityRemoved(ItemEntity entity, Entity.RemovalReason reason) {
        if (!reason.shouldDestroy() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = entity.getItem();
        if (!stack.isEmpty()) {
            destroyed(level.getServer(), stack);
        }
    }

    /**
     * Snapshot of the bound key ids in a container, for comparing before and after a deletion such
     * as {@code /clear}.
     */
    public static Set<UUID> boundIds(Container container) {
        Set<UUID> ids = new HashSet<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            forEachBoundKey(
                    container.getItem(slot), key -> DungeonKeyType.keyId(key).ifPresent(ids::add));
        }
        return ids;
    }

    /** Keys whose ids were in {@code before} and are no longer in the container were deleted. */
    public static void deletedFrom(MinecraftServer server, Set<UUID> before, Container container) {
        if (before.isEmpty()) {
            return;
        }
        Set<UUID> after = boundIds(container);
        for (UUID id : before) {
            if (!after.contains(id)) {
                destroyedId(server, id);
            }
        }
    }

    /**
     * A creative player's inventory slot changed through the creative screen. A key that leaves a
     * slot may be on the screen's cursor on its way to another slot, so it counts as deleted only
     * if it neither comes back nor gets dropped within {@link #CREATIVE_GRACE_TICKS}.
     */
    public static void onCreativeSlot(ServerPlayer player, ItemStack previous, ItemStack next) {
        forEachBoundKey(next, key -> DungeonKeyType.keyId(key).ifPresent(PENDING_CREATIVE::remove));
        Set<UUID> kept = new HashSet<>();
        forEachBoundKey(next, key -> DungeonKeyType.keyId(key).ifPresent(kept::add));
        forEachBoundKey(
                previous,
                key ->
                        DungeonKeyType.keyId(key)
                                .ifPresent(
                                        id -> {
                                            if (!kept.contains(id)) {
                                                PENDING_CREATIVE.put(
                                                        id,
                                                        new PendingCreativeLoss(
                                                                player.getUUID(),
                                                                player.level()
                                                                                .getServer()
                                                                                .overworld()
                                                                                .getGameTime()
                                                                        + CREATIVE_GRACE_TICKS));
                                            }
                                        }));
    }

    /**
     * Settles creative removals whose grace period ended: a key not back in the player's inventory,
     * ender chest, open container or cursor counts as deleted.
     */
    public static void tick(MinecraftServer server) {
        if (PENDING_CREATIVE.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<UUID> lost = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, PendingCreativeLoss>> it =
                        PENDING_CREATIVE.entrySet().iterator();
                it.hasNext(); ) {
            Map.Entry<UUID, PendingCreativeLoss> entry = it.next();
            if (entry.getValue().deadline() > now) {
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getValue().player());
            if (player == null || !holds(player, entry.getKey())) {
                lost.add(entry.getKey());
            }
        }
        lost.forEach(id -> destroyedId(server, id));
    }

    public static void clear() {
        PENDING_CREATIVE.clear();
    }

    private static boolean holds(ServerPlayer player, UUID id) {
        if (boundIds(player.getInventory()).contains(id)
                || boundIds(player.getEnderChestInventory()).contains(id)) {
            return true;
        }
        Set<UUID> open = new HashSet<>();
        for (Slot slot : player.containerMenu.slots) {
            forEachBoundKey(slot.getItem(), key -> DungeonKeyType.keyId(key).ifPresent(open::add));
        }
        forEachBoundKey(
                player.containerMenu.getCarried(),
                key -> DungeonKeyType.keyId(key).ifPresent(open::add));
        return open.contains(id);
    }

    /**
     * Every bound key in {@code stack} was destroyed, including keys inside shulker boxes and
     * bundles.
     */
    public static void destroyed(MinecraftServer server, ItemStack stack) {
        List<ItemStack> keys = new ArrayList<>();
        forEachBoundKey(stack, keys::add);
        for (ItemStack key : keys) {
            UUID id = DungeonKeyType.keyId(key).orElseThrow();
            Optional<ServerLevel> dungeon = DungeonKeyType.keyDungeon(key).map(server::getLevel);
            if (dungeon.isEmpty() || !destroyedIn(dungeon.get(), id)) {
                destroyedId(server, id);
            }
        }
    }

    /**
     * The key carrying {@code id} is gone. If it is a boss door's current key, the door's record
     * draws a new id and the vault gets one new key; a retired id changes nothing.
     */
    private static void destroyedId(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (destroyedIn(level, id)) {
                return;
            }
        }
    }

    private static boolean destroyedIn(ServerLevel level, UUID id) {
        DungeonLockSaveData data = DungeonLockManager.existingData(level);
        Optional<DungeonLockSaveData.BossKey> record =
                data == null ? Optional.empty() : data.bossKeyById(id);
        record.ifPresent(
                current -> {
                    reissue(level, data, current);
                    // The door lies inside the dungeon's bounds, which is how the API finds it.
                    DungeonEvents.keyDestroyed(
                            level,
                            BlockPos.of(current.door()),
                            DungeonKeyType.BOSS.getSerializedName());
                });
        return record.isPresent();
    }

    /**
     * Operator remedy for a key lost where no hook could see it (a crash rollback, a corrupted
     * file): reissues the key of the issued boss door nearest to {@code near}, within {@link
     * #REISSUE_RANGE}. Returns the vault that got it.
     */
    public static Optional<BlockPos> reissueNear(ServerLevel level, BlockPos near) {
        DungeonLockSaveData data = DungeonLockManager.existingData(level);
        if (data == null) {
            return Optional.empty();
        }
        Optional<DungeonLockSaveData.BossKey> record =
                data.bossKeys().stream()
                        .filter(candidate -> candidate.keyId().isPresent())
                        .filter(
                                candidate ->
                                        BlockPos.of(candidate.door())
                                                .closerThan(near, REISSUE_RANGE))
                        .min(
                                Comparator.comparingDouble(
                                        candidate -> BlockPos.of(candidate.door()).distSqr(near)));
        record.ifPresent(current -> reissue(level, data, current));
        return record.map(current -> BlockPos.of(current.vault()));
    }

    private static void reissue(
            ServerLevel level, DungeonLockSaveData data, DungeonLockSaveData.BossKey record) {
        UUID id = data.issueBossKey(record.door(), record.vault());
        BlockPos vault = BlockPos.of(record.vault());
        deliver(level, vault, DungeonKeyType.BOSS.createStack(id, level.dimension()));
        BlockPos door = BlockPos.of(record.door());
        Component notice =
                Component.literal(
                        "The Boss Key was destroyed. A new one waits in the boss key vault.");
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(door, NOTICE_RANGE)
                    || player.blockPosition().closerThan(vault, NOTICE_RANGE)) {
                player.sendSystemMessage(notice);
            }
        }
    }

    /**
     * Puts {@code key} into the container at {@code pos}, or drops it there with no despawn timer
     * when the container is gone or full.
     */
    static void deliver(ServerLevel level, BlockPos pos, ItemStack key) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (container.getItem(slot).isEmpty()) {
                    container.setItem(slot, key);
                    container.setChanged();
                    return;
                }
            }
        }
        Vec3 at = Vec3.atCenterOf(pos);
        ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, key, 0.0, 0.0, 0.0);
        entity.setUnlimitedLifetime();
        level.addFreshEntity(entity);
    }

    private static void forEachBoundKey(ItemStack stack, Consumer<ItemStack> action) {
        if (stack.isEmpty()) {
            return;
        }
        if (DungeonKeyType.BOSS.matches(stack)) {
            if (DungeonKeyType.keyId(stack).isPresent()) {
                action.accept(stack);
            }
            return;
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.nonEmptyItemCopyStream().forEach(inner -> forEachBoundKey(inner, action));
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            bundle.itemCopyStream().forEach(inner -> forEachBoundKey(inner, action));
        }
    }

    private record PendingCreativeLoss(UUID player, long deadline) {}
}
