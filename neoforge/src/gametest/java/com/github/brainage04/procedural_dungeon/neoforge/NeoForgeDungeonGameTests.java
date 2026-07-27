package com.github.brainage04.procedural_dungeon.neoforge;

import com.github.brainage04.procedural_dungeon.DungeonGameTestSuite;
import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = ProceduralDungeon.MOD_ID)
public final class NeoForgeDungeonGameTests {
    private NeoForgeDungeonGameTests() {}

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        for (DungeonGameTestSuite.TestCase test : DungeonGameTestSuite.tests()) {
            event.register(BuiltInRegistries.TEST_FUNCTION.key(), Identifier.fromNamespaceAndPath(ProceduralDungeon.MOD_ID, test.path()), test::function);
        }
    }
}
