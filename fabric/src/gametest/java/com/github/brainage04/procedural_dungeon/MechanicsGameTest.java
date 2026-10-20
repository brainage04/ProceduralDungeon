package com.github.brainage04.procedural_dungeon;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class MechanicsGameTest {
    @GameTest(maxTicks = 400)
    public void naturalGenerationSwitchesStopNewDungeonStarts(GameTestHelper helper) {
        MechanicsGameTests.naturalGenerationSwitchesStopNewDungeonStarts(helper);
    }

    @GameTest
    public void grindstoneSalvageGameRuleRestoresTheVanillaGrindstone(GameTestHelper helper) {
        MechanicsGameTests.grindstoneSalvageGameRuleRestoresTheVanillaGrindstone(helper);
    }

    @GameTest
    public void globalMechanicsScopeTurnsOffSalvageAnvilsAndSoulbound(GameTestHelper helper) {
        MechanicsGameTests.globalMechanicsScopeTurnsOffSalvageAnvilsAndSoulbound(helper);
    }

    @GameTest
    public void legacyDatapackFunctionsAreGone(GameTestHelper helper) {
        MechanicsGameTests.legacyDatapackFunctionsAreGone(helper);
    }
}
