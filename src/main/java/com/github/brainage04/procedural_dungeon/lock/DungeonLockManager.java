package com.github.brainage04.procedural_dungeon.lock;

import com.github.brainage04.procedural_dungeon.item.ModItems;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
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

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(world instanceof ServerLevel level)) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            DungeonLockSaveData data = data(level);
            if (!data.isLocked(pos.asLong())) {
                return InteractionResult.PASS;
            }

            if (!consumeKey(player)) {
                displayMessage(player, "This lock needs a Rusted Key.");
                return InteractionResult.FAIL;
            }

            unlock(level, data, pos);
            displayMessage(player, "Unlocked with a Rusted Key.");
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level)) {
                return true;
            }
            if (!data(level).isLocked(pos.asLong())) {
                return true;
            }

            displayMessage(player, "This lock needs a Rusted Key.");
            return false;
        });
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
    }

    public static void registerLockedDoor(ServerLevel level, BlockPos pos) {
        data(level).addLockedDoor(pos.asLong());
    }

    public static boolean isExplosionProtected(ServerLevel level, BlockPos pos) {
        return data(level).isExplosionProtected(pos.asLong());
    }

    private static DungeonLockSaveData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DungeonLockSaveData.TYPE);
    }

    private static void addLockedChest(ServerLevel level, DungeonLockSaveData data, BlockPos pos) {
        data.addLockedChest(pos.asLong());
        BlockPos connectedPos = connectedChestPos(level, pos);
        if (connectedPos != null) {
            data.addLockedChest(connectedPos.asLong());
        }
    }

    private static void unlock(ServerLevel level, DungeonLockSaveData data, BlockPos pos) {
        data.unlock(pos.asLong());
        BlockPos connectedPos = connectedChestPos(level, pos);
        if (connectedPos != null) {
            data.unlock(connectedPos.asLong());
        }
    }

    private static BlockPos connectedChestPos(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)) {
            return null;
        }
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return null;
        }
        return ChestBlock.getConnectedBlockPos(pos, state);
    }

    private static boolean consumeKey(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(ModItems.RUSTED_KEY)) {
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
