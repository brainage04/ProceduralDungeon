package com.github.brainage04.proceduraldungeon.command;

import com.github.brainage04.proceduraldungeon.command.arguments.DungeonTheme;
import com.github.brainage04.proceduraldungeon.command.arguments.DungeonThemeArgumentType;
import com.github.brainage04.proceduraldungeon.command.arguments.DungeonTier;
import com.github.brainage04.proceduraldungeon.command.arguments.DungeonTierArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.PlaceCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class GenerateDungeonCommand {
    public static int execute(ServerCommandSource source, DungeonTheme theme, DungeonTier tier, int depth) {
        //PlaceCommand


        source.sendFeedback(() -> Text.literal("This is an example command."), false);

        return 1;
    }

    public static void initialize(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("generatedungeon")
                .then(argument("theme", DungeonThemeArgumentType.dungeonTheme())
                        .then(argument("tier", DungeonTierArgumentType.dungeonTier())
                                .then(argument("depth", IntegerArgumentType.integer(1, 20))
                                        .executes(context ->
                                                execute(
                                                        context.getSource(),
                                                        DungeonThemeArgumentType.getDungeonTheme(context, "theme"),
                                                        DungeonTierArgumentType.getDungeonTier(context, "tier"),
                                                        IntegerArgumentType.getInteger(context, "depth")
                                                )
                                        )
                                )
                        )
                )
        );
    }
}
