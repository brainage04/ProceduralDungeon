package com.github.brainage04.proceduraldungeon.command.core;

import com.github.brainage04.proceduraldungeon.command.ExampleCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class ModCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ExampleCommand.initialize(dispatcher);
        });
    }
}
