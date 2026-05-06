package com.github.brainage04.procedural_dungeon.command;

import com.github.brainage04.procedural_dungeon.test.DungeonVariantSmokeTester;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TestDungeonVariantsCommand {
    public static int execute(ServerCommandSource source, int count, int spacing, long seed) throws CommandSyntaxException {
        DungeonVariantSmokeTester.Result result = DungeonVariantSmokeTester.placeRandomVariants(
                source.withSilent(),
                count,
                spacing,
                seed
        );

        source.sendFeedback(() -> Text.literal(
                "Placed %d procedural dungeon variant(s) with seed %d and spacing %d."
                        .formatted(result.attempted(), seed, spacing)
        ), true);

        return result.attempted();
    }

    public static void initialize(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("testdungeonvariants")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> execute(
                        context.getSource(),
                        DungeonVariantSmokeTester.DEFAULT_COUNT,
                        DungeonVariantSmokeTester.DEFAULT_SPACING,
                        DungeonVariantSmokeTester.DEFAULT_SEED
                ))
                .then(argument("count", IntegerArgumentType.integer(1, DungeonVariantSmokeTester.getVariantKeys().size()))
                        .executes(context -> execute(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "count"),
                                DungeonVariantSmokeTester.DEFAULT_SPACING,
                                DungeonVariantSmokeTester.DEFAULT_SEED
                        ))
                        .then(argument("spacing", IntegerArgumentType.integer(64, 1024))
                                .executes(context -> execute(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count"),
                                        IntegerArgumentType.getInteger(context, "spacing"),
                                        DungeonVariantSmokeTester.DEFAULT_SEED
                                ))
                                .then(argument("seed", LongArgumentType.longArg())
                                        .executes(context -> execute(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "count"),
                                                IntegerArgumentType.getInteger(context, "spacing"),
                                                LongArgumentType.getLong(context, "seed")
                                        ))
                                )
                        )
                )
        );
    }
}
