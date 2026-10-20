package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lock, protection and encounter tests. They drive the same entry points a real player reaches (the server's
 * right-click and block-break paths), so a loader hook that lets an action through fails here.
 */
public final class LockGameTests {
    private static final ResourceKey<LootTable> CHEST_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE, ProceduralDungeon.of("hallway_end/key_source/cobblestone/tier_1"));

    private LockGameTests() {}

    public static List<DungeonGameTestSuite.TestCase> tests() {
        return List.of(
                new DungeonGameTestSuite.TestCase("locked_chests_stay_shut_through_the_loader_use_hook",
                        LockGameTests::lockedChestsStayShutThroughTheLoaderUseHook)
        );
    }

    /**
     * A right-click goes through {@code ServerPlayerGameMode.useItemOn}, which fires the loader's use hook (Fabric's
     * {@code UseBlockCallback}, NeoForge's {@code RightClickBlock}). A refused lock must keep vanilla from opening it.
     */
    public static void lockedChestsStayShutThroughTheLoaderUseHook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rusted = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos puzzle = helper.absolutePos(new BlockPos(3, 1, 1));
        for (BlockPos pos : List.of(rusted, puzzle)) {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            ((ChestBlockEntity) level.getBlockEntity(pos)).setLootTable(CHEST_LOOT, 1L);
        }
        DungeonLockManager.applyPlanForPiece(level, new DungeonLockPlan(List.of(rusted.asLong()), List.of(), List.of()),
                new BoundingBox(rusted));
        // No mechanism repeater sits under this chest, so its puzzle can never read as solved.
        DungeonLockManager.lockPuzzleChest(level, puzzle);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            for (BlockPos pos : List.of(rusted, puzzle)) {
                rightClick(player, level, pos);
                helper.assertTrue(player.containerMenu == player.inventoryMenu,
                        "A refused lock must not open the chest at " + pos.toShortString());
                helper.assertTrue(((ChestBlockEntity) level.getBlockEntity(pos)).getLootTable() != null,
                        "A refused lock must leave the chest's loot unrolled at " + pos.toShortString());
            }
            helper.assertTrue(DungeonLockManager.isPuzzleLocked(level, puzzle), "The puzzle chest must stay locked");

            player.getInventory().setItem(0, DungeonKeyType.RUSTED.createStack());
            rightClick(player, level, rusted);
            helper.assertTrue(player.containerMenu instanceof ChestMenu, "The matching key must open the chest through the same hook");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    static void rightClick(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.closeContainer();
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
}
