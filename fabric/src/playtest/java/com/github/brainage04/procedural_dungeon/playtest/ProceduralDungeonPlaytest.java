package com.github.brainage04.procedural_dungeon.playtest;

import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Arrays;
import java.util.Optional;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Development-only playtest mode. The first time a player joins a world, it builds a dungeon of a random theme and
 * tier (or those given by the {@code procedural_dungeon.playtest.theme} and {@code procedural_dungeon.playtest.tier}
 * system properties), puts the player in its start room, and hands out gear for the tier. {@code /playtest [theme]
 * [tier]} builds another one.
 */
public class ProceduralDungeonPlaytest implements ModInitializer {
    private static final String STARTED_TAG = "procedural_dungeon_playtest";

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player.addTag(STARTED_TAG)) {
                server.execute(() -> PlaytestDungeon.start(player, property("theme").map(ProceduralDungeonPlaytest::theme), property("tier").map(tier -> DungeonTier.values()[Integer.parseInt(tier) - 1])));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("playtest")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> run(context.getSource(), Optional.empty(), Optional.empty()))
                .then(argument("theme", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(DungeonTheme.values()).map(DungeonTheme::getSerializedName), builder))
                        .executes(context -> run(context.getSource(), Optional.of(StringArgumentType.getString(context, "theme")), Optional.empty()))
                        .then(argument("tier", IntegerArgumentType.integer(1, DungeonTier.values().length))
                                .executes(context -> run(
                                        context.getSource(),
                                        Optional.of(StringArgumentType.getString(context, "theme")),
                                        Optional.of(IntegerArgumentType.getInteger(context, "tier"))
                                ))))
        ));
    }

    private static int run(CommandSourceStack source, Optional<String> theme, Optional<Integer> tier) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<DungeonTheme> chosenTheme = theme.map(ProceduralDungeonPlaytest::theme);
        if (theme.isPresent() && chosenTheme.isEmpty()) {
            source.sendFailure(Component.literal("Unknown dungeon theme: " + theme.get()));
            return 0;
        }
        return PlaytestDungeon.start(player, chosenTheme, tier.map(value -> DungeonTier.values()[value - 1])) ? 1 : 0;
    }

    private static DungeonTheme theme(String name) {
        return Arrays.stream(DungeonTheme.values())
                .filter(theme -> theme.getSerializedName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static Optional<String> property(String name) {
        return Optional.ofNullable(System.getProperty("procedural_dungeon.playtest." + name)).filter(value -> !value.isBlank());
    }
}
