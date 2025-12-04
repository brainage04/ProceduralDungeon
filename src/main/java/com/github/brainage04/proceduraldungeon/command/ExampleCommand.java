package com.github.brainage04.proceduraldungeon.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class ExampleCommand {
    public static int execute(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("This is an example command."), false);

        return 1;
    }

    public static void initialize(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("example")
                .executes(context ->
                        execute(
                                context.getSource()
                        )
                )
        );
    }
}
