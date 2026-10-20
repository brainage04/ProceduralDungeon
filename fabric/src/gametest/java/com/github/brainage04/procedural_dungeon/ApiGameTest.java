package com.github.brainage04.procedural_dungeon;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class ApiGameTest {
    @GameTest(maxTicks = 100)
    public void apiPlansCompileOffThreadFromTheRequestAlone(GameTestHelper helper) {
        ApiGameTests.apiPlansCompileOffThreadFromTheRequestAlone(helper);
    }

    @GameTest(maxTicks = 400)
    public void apiBuildFollowsTheCallersBudgetAndCancelReleasesItsTickets(GameTestHelper helper) {
        ApiGameTests.apiBuildFollowsTheCallersBudgetAndCancelReleasesItsTickets(helper);
    }

    @GameTest(maxTicks = 400)
    public void apiResetAndForgetRebuildAndDropADungeon(GameTestHelper helper) {
        ApiGameTests.apiResetAndForgetRebuildAndDropADungeon(helper);
    }
}
