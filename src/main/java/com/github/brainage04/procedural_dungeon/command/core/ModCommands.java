package com.github.brainage04.procedural_dungeon.command.core;

import com.github.brainage04.procedural_dungeon.command.BenchmarkDungeonCommand;
import com.github.brainage04.procedural_dungeon.command.DungeonLocksCommand;
import com.github.brainage04.procedural_dungeon.command.GenerateDungeonCommand;
import com.github.brainage04.procedural_dungeon.command.StructureGalleryCommand;
import com.github.brainage04.procedural_dungeon.command.TestDungeonVariantsCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

public class ModCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        GenerateDungeonCommand.initialize(dispatcher);
        TestDungeonVariantsCommand.initialize(dispatcher);
        BenchmarkDungeonCommand.initialize(dispatcher);
        StructureGalleryCommand.initialize(dispatcher);
        DungeonLocksCommand.initialize(dispatcher);
    }
}
