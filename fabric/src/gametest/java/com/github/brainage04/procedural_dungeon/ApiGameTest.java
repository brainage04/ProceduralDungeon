package com.github.brainage04.procedural_dungeon;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric registration of {@link ApiGameTests}. Each test runs in its own environment, so its build
 * never waits behind other tests' jobs for one of the active-job slots.
 */
public final class ApiGameTest {
    @GameTest(
            maxTicks = 100,
            environment = "procedural_dungeon:api_plans_compile_off_thread_from_the_request_alone")
    public void apiPlansCompileOffThreadFromTheRequestAlone(GameTestHelper helper) {
        ApiGameTests.apiPlansCompileOffThreadFromTheRequestAlone(helper);
    }

    @GameTest(
            maxTicks = 400,
            environment =
                    "procedural_dungeon:api_build_follows_the_callers_budget_and_cancel_releases_its_tickets")
    public void apiBuildFollowsTheCallersBudgetAndCancelReleasesItsTickets(GameTestHelper helper) {
        ApiGameTests.apiBuildFollowsTheCallersBudgetAndCancelReleasesItsTickets(helper);
    }

    @GameTest(
            maxTicks = 400,
            environment = "procedural_dungeon:api_reset_and_forget_rebuild_and_drop_a_dungeon")
    public void apiResetAndForgetRebuildAndDropADungeon(GameTestHelper helper) {
        ApiGameTests.apiResetAndForgetRebuildAndDropADungeon(helper);
    }
}
