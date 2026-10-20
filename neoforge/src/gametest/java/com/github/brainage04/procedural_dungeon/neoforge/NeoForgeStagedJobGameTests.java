package com.github.brainage04.procedural_dungeon.neoforge;

import com.github.brainage04.procedural_dungeon.DungeonGameTestSuite;
import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.StagedJobGameTests;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** NeoForge registration of {@link StagedJobGameTests}; the test instances are data files. */
@EventBusSubscriber(modid = ProceduralDungeon.MOD_ID)
public final class NeoForgeStagedJobGameTests {
    private NeoForgeStagedJobGameTests() {}

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        for (DungeonGameTestSuite.TestCase test : StagedJobGameTests.tests()) {
            event.register(
                    BuiltInRegistries.TEST_FUNCTION.key(),
                    Identifier.fromNamespaceAndPath(ProceduralDungeon.MOD_ID, test.path()),
                    test::function);
        }
    }
}
