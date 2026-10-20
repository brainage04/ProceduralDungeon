package com.github.brainage04.procedural_dungeon;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;

public final class LayoutGameTest {
    @GameTest
    public void releasedServersReadSpecsFromTheJar(GameTestHelper helper) {
        LayoutGameTests.releasedServersReadSpecsFromTheJar(helper);
    }

    @GameTest
    public void solidDensitySamplesEachColumnOnce(GameTestHelper helper) {
        LayoutGameTests.solidDensitySamplesEachColumnOnce(helper);
    }

    @GameTest
    public void weightedTemplatesAreTriedOncePerSocket(GameTestHelper helper) {
        LayoutGameTests.weightedTemplatesAreTriedOncePerSocket(helper);
    }

    @GameTest
    public void bossKeyVaultIsOnAnotherBranch(GameTestHelper helper) {
        LayoutGameTests.bossKeyVaultIsOnAnotherBranch(helper);
    }

    @GameTest
    public void buriedProgressionRoomsAreCarvedOut(GameTestHelper helper) {
        LayoutGameTests.buriedProgressionRoomsAreCarvedOut(helper);
    }

    @GameTest(maxTicks = 1200)
    public void naturalStartsBuildInTheOverworld(GameTestHelper helper) {
        LayoutGameTests.naturalStartsBuild(helper, Level.OVERWORLD);
    }

    @GameTest(maxTicks = 1200)
    public void naturalStartsBuildInTheNether(GameTestHelper helper) {
        LayoutGameTests.naturalStartsBuild(helper, Level.NETHER);
    }

    @GameTest(maxTicks = 1200)
    public void naturalStartsBuildInTheEnd(GameTestHelper helper) {
        LayoutGameTests.naturalStartsBuild(helper, Level.END);
    }

    @GameTest
    public void terrainIsBuriedAroundEveryRoom(GameTestHelper helper) {
        LayoutGameTests.terrainIsBuriedAroundEveryRoom(helper);
    }

    @GameTest
    public void geometryCachesFollowReloadedTemplates(GameTestHelper helper) {
        LayoutGameTests.geometryCachesFollowReloadedTemplates(helper);
    }
}
