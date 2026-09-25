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
}
