package com.github.brainage04.procedural_dungeon;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;


public final class ProceduralDungeonGameTest {
    @GameTest
    public void variantCatalogIsStableAndComplete(GameTestHelper helper) {
        DungeonGameTestSuite.variantCatalogIsStableAndComplete(helper);
    }

    @GameTest
    public void seededLootSelectionIsRepeatableAndTierBounded(GameTestHelper helper) {
        DungeonGameTestSuite.seededLootSelectionIsRepeatableAndTierBounded(helper);
    }

    @GameTest
    public void everyLayoutHasABossRoomAndABossKeyVault(GameTestHelper helper) {
        DungeonGameTestSuite.everyLayoutHasABossRoomAndABossKeyVault(helper);
    }

    @GameTest
    public void guardedDoorOpensOnlyWithItsKey(GameTestHelper helper) {
        DungeonGameTestSuite.guardedDoorOpensOnlyWithItsKey(helper);
    }

    @GameTest
    public void placedBossRoomIsGuardedLockedAndStocked(GameTestHelper helper) {
        DungeonGameTestSuite.placedBossRoomIsGuardedLockedAndStocked(helper);
    }

    @GameTest
    public void trialSpawnersEjectTieredDungeonLoot(GameTestHelper helper) {
        DungeonGameTestSuite.trialSpawnersEjectTieredDungeonLoot(helper);
    }

    @GameTest
    public void surfaceEntrancesLeadDownIntoAFullDungeon(GameTestHelper helper) {
        DungeonGameTestSuite.surfaceEntrancesLeadDownIntoAFullDungeon(helper);
    }

    @GameTest
    public void dungeonEnchantmentsAreRewardOnlyAndTakeEffect(GameTestHelper helper) {
        DungeonGameTestSuite.dungeonEnchantmentsAreRewardOnlyAndTakeEffect(helper);
    }

    @GameTest
    public void bossRewardsAreExclusiveAndRelicsKeepBaseStats(GameTestHelper helper) {
        DungeonGameTestSuite.bossRewardsAreExclusiveAndRelicsKeepBaseStats(helper);
    }

    @GameTest
    public void overMaxBooksApplyThroughAnvils(GameTestHelper helper) {
        DungeonGameTestSuite.overMaxBooksApplyThroughAnvils(helper);
    }

    @GameTest
    public void enchantmentBlastsSpareAllies(GameTestHelper helper) {
        DungeonGameTestSuite.enchantmentBlastsSpareAllies(helper);
    }

    @GameTest
    public void killEnchantmentsRewardTheKillerAndBurstOnEnemies(GameTestHelper helper) {
        DungeonGameTestSuite.killEnchantmentsRewardTheKillerAndBurstOnEnemies(helper);
    }

    @GameTest
    public void soulboundItemsSurviveDeath(GameTestHelper helper) {
        DungeonGameTestSuite.soulboundItemsSurviveDeath(helper);
    }

    @GameTest
    public void grindstonesSalvageBooksAndEssenceThatAnvilsReapply(GameTestHelper helper) {
        DungeonGameTestSuite.grindstonesSalvageBooksAndEssenceThatAnvilsReapply(helper);
    }

    @GameTest(maxTicks = 100)
    public void puzzleRoomsPlaceWholeAndHoldTheirChests(GameTestHelper helper) {
        DungeonGameTestSuite.puzzleRoomsPlaceWholeAndHoldTheirChests(helper);
    }

    @GameTest(maxTicks = 100)
    public void framePuzzleOpensWhenEveryArrowPointsUp(GameTestHelper helper) {
        DungeonGameTestSuite.framePuzzleOpensWhenEveryArrowPointsUp(helper);
    }

    @GameTest(maxTicks = 100)
    public void bookshelfPuzzleOpensWhenEveryShelfEndsOnItsLastSlot(GameTestHelper helper) {
        DungeonGameTestSuite.bookshelfPuzzleOpensWhenEveryShelfEndsOnItsLastSlot(helper);
    }

    @GameTest(maxTicks = 150)
    public void targetPuzzleOpensWhenEveryBulbIsLit(GameTestHelper helper) {
        DungeonGameTestSuite.targetPuzzleOpensWhenEveryBulbIsLit(helper);
    }

    @GameTest(maxTicks = 100)
    public void chordPuzzleOpensOnlyForItsChord(GameTestHelper helper) {
        DungeonGameTestSuite.chordPuzzleOpensOnlyForItsChord(helper);
    }

    @GameTest(maxTicks = 400)
    public void sluicePuzzleOpensWhenTheStreamReachesTheTorch(GameTestHelper helper) {
        DungeonGameTestSuite.sluicePuzzleOpensWhenTheStreamReachesTheTorch(helper);
    }

    @GameTest(maxTicks = 100)
    public void crafterPuzzleOpensWhenTheDiscPlays(GameTestHelper helper) {
        DungeonGameTestSuite.crafterPuzzleOpensWhenTheDiscPlays(helper);
    }
}
