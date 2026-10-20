package com.github.brainage04.procedural_dungeon.neoforge;

import com.github.brainage04.procedural_dungeon.DungeonGameTestSuite;
import com.github.brainage04.procedural_dungeon.LockGameTests;
import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import java.util.stream.Stream;
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
        Stream.concat(DungeonGameTestSuite.tests().stream(), LockGameTests.tests().stream()).forEach(test ->
                event.register(BuiltInRegistries.TEST_FUNCTION.key(), Identifier.fromNamespaceAndPath(ProceduralDungeon.MOD_ID, test.path()), test::function));
    }
}
