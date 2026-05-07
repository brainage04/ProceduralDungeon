package com.github.brainage04.procedural_dungeon.command.core;

import com.github.brainage04.procedural_dungeon.command.BenchmarkDungeonCommand;
import com.github.brainage04.procedural_dungeon.command.GenerateDungeonCommand;
import com.github.brainage04.procedural_dungeon.command.StructureGalleryCommand;
import com.github.brainage04.procedural_dungeon.command.TestDungeonVariantsCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class ModCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GenerateDungeonCommand.initialize(dispatcher);

            TestDungeonVariantsCommand.initialize(dispatcher);

            BenchmarkDungeonCommand.initialize(dispatcher);

            StructureGalleryCommand.initialize(dispatcher);
        });
    }
}
