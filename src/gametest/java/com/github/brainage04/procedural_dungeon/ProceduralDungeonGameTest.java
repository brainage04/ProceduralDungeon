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
}
