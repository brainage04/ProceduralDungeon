package com.github.brainage04.procedural_dungeon;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registrations of {@link LockGameTests}; NeoForge registers the same functions by name. */
public final class LockGameTest {
    @GameTest(maxTicks = 100)
    public void lockedChestsStayShutThroughTheLoaderUseHook(GameTestHelper helper) {
        LockGameTests.lockedChestsStayShutThroughTheLoaderUseHook(helper);
    }
}
