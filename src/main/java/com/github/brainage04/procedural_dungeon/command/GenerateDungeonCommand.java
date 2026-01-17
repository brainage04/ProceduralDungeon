package com.github.brainage04.procedural_dungeon.command;

import com.github.brainage04.procedural_dungeon.command.core.DungeonTheme;
import com.github.brainage04.procedural_dungeon.command.core.ModSuggestionProviders;
import com.github.brainage04.procedural_dungeon.util.EnumUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class GenerateDungeonCommand {
    public static int execute(ServerCommandSource source, String themeString, int tier, int depth) {
        DungeonTheme theme = EnumUtils.getEnumValue(DungeonTheme.class, themeString);
        if (theme == null) {
            source.sendError(Text.literal("Invalid dungeon theme!"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal(
                "Generating Tier %d %s dungeon at depth %d...".formatted(tier, theme.getName(), depth)
        ), true);

        // todo: implement custom PlaceCommand that lets me replace:
        //  - chest loot tables
        //  - the block processor


        source.sendFeedback(() -> Text.literal("Dungeon generated."), true);

        return 1;
    }

    public static void initialize(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("generatedungeon")
                .then(argument("theme", StringArgumentType.string())
                        .suggests(SuggestionProviders.cast(ModSuggestionProviders.DUNGEON_THEMES))
                        .then(argument("tier", IntegerArgumentType.integer(1, 5))
                                .then(argument("depth", IntegerArgumentType.integer(1, 20))
                                        .executes(context ->
                                                execute(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "theme"),
                                                        IntegerArgumentType.getInteger(context, "tier"),
                                                        IntegerArgumentType.getInteger(context, "depth")
                                                )
                                        )
                                )
                        )
                )
        );
    }
}
