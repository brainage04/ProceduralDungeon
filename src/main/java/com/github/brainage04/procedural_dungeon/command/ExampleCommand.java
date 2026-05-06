package com.github.brainage04.procedural_dungeon.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public class ExampleCommand {
    public static int execute(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("This is an example command."), false);

        return 1;
    }

    public static void initialize(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("example")
                .executes(context ->
                        execute(
                                context.getSource()
                        )
                )
        );
    }
}
