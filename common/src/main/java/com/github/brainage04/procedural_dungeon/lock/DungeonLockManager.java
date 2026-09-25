package com.github.brainage04.procedural_dungeon.lock;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

public final class DungeonLockManager {
    private static boolean initialized;

    private DungeonLockManager() {}

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
    }

    public static InteractionResult useBlock(Player player, ServerLevel level, BlockPos pos) {
        DungeonLockSaveData data = data(level);
        Optional<DungeonKeyType> requiredKey = data.requiredKey(pos.asLong());
        if (requiredKey.isEmpty()) {
            return InteractionResult.PASS;
        }

        DungeonKeyType key = requiredKey.get();
        if (!consumeKey(player, key)) {
            displayMessage(player, "This lock needs a %s.".formatted(key.displayName()));
            return InteractionResult.FAIL;
        }

        boolean door = data.isLockedDoor(pos.asLong());
        unlock(level, data, pos);
        displayMessage(player, "Unlocked with a %s.".formatted(key.displayName()));
        if (door) {
            openDoor(level, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static boolean canBreak(Player player, ServerLevel level, BlockPos pos) {
        Optional<DungeonKeyType> requiredKey = data(level).requiredKey(pos.asLong());
        if (requiredKey.isEmpty()) {
            return true;
        }

        displayMessage(player, "This lock needs a %s.".formatted(requiredKey.get().displayName()));
        return false;
    }

    public static void applyPlanForPiece(ServerLevel level, DungeonLockPlan plan, BoundingBox pieceBox) {
        if (plan.isEmpty()) {
            return;
        }

        DungeonLockSaveData data = data(level);
        for (long packedPos : plan.lockedChests()) {
            BlockPos pos = BlockPos.of(packedPos);
            if (pieceBox.isInside(pos)) {
                addLockedChest(level, data, pos);
            }
        }

        for (DungeonLockPlan.KeySource keySource : plan.keySources()) {
            BlockPos pos = BlockPos.of(keySource.pos());
            if (!pieceBox.isInside(pos)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RandomizableContainer container) {
                data.addKeySourceChest(keySource.pos());
                ResourceKey<LootTable> lootTable = ResourceKey.create(Registries.LOOT_TABLE, keySource.lootTable());
                container.setLootTable(lootTable, level.getSeed() ^ pos.asLong() ^ 0x5EED5EEDL);
                blockEntity.setChanged();
            }
        }

        for (DungeonLockPlan.Door door : plan.doors()) {
            BlockPos lower = BlockPos.of(door.pos());
            if (!pieceBox.isInside(lower) || !(level.getBlockState(lower).getBlock() instanceof DoorBlock)) {
                continue;
            }

            if (door.lock().isPresent()) {
                data.addLockedDoor(lower.asLong(), door.lock().get());
                data.addLockedDoor(lower.above().asLong(), door.lock().get());
            } else {
                openDoor(level, lower);
            }
        }
    }

    public static boolean isExplosionProtected(ServerLevel level, BlockPos pos) {
        return data(level).isExplosionProtected(pos.asLong());
    }

    private static DungeonLockSaveData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DungeonLockSaveData.TYPE);
    }

    private static void addLockedChest(ServerLevel level, DungeonLockSaveData data, BlockPos pos) {
        data.addLockedChest(pos.asLong());
        BlockPos connectedPos = connectedPos(level, pos);
        if (connectedPos != null) {
            data.addLockedChest(connectedPos.asLong());
        }
    }

    private static void unlock(ServerLevel level, DungeonLockSaveData data, BlockPos pos) {
        data.unlock(pos.asLong());
        BlockPos connectedPos = connectedPos(level, pos);
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

    private static void openDoor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(null, level, state, pos, true);
        }
    }

    private static boolean consumeKey(Player player, DungeonKeyType type) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!type.matches(stack)) {
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

    private static void displayMessage(Player player, String message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal(message));
        }
    }
}
