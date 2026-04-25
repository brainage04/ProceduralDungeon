package com.github.brainage04.procedural_dungeon.command.core;

import com.github.brainage04.procedural_dungeon.command.ExampleCommand;
import com.github.brainage04.procedural_dungeon.command.GenerateDungeonCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class ModCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ExampleCommand.initialize(dispatcher);

            GenerateDungeonCommand.initialize(dispatcher);
        });
    }
}
