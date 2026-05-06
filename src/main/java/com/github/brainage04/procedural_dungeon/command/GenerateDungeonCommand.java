package com.github.brainage04.procedural_dungeon.command;

import com.github.brainage04.procedural_dungeon.command.core.ModSuggestionProviders;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.PlaceCommand;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class GenerateDungeonCommand {
    public static int execute(ServerCommandSource source, String themeString, int tierNumber, int depth) throws CommandSyntaxException {
        DungeonTheme theme = getTheme(themeString);
        if (theme == null) {
            source.sendError(Text.literal("Invalid dungeon theme!"));
            return 0;
        }

        DungeonTier tier = getTier(tierNumber);
        String key = RegistryKeyUtils.getKeyString(theme, tier);
        RegistryKey<StructurePool> poolKey = RegistryKeyUtils.create(RegistryKeys.TEMPLATE_POOL, "%s/start".formatted(key));
        var pool = source.getRegistryManager().getOrThrow(RegistryKeys.TEMPLATE_POOL).getOrThrow(poolKey);
        BlockPos pos = BlockPos.ofFloored(source.getPosition());

        source.sendFeedback(() -> Text.literal(
                "Generating Tier %d %s dungeon at jigsaw depth %d..."
                        .formatted(tier.tier, theme.getName().getString(), depth)
        ), true);

        PlaceCommand.executePlaceJigsaw(
                source.withSilent(),
                pool,
                Identifier.ofVanilla("start"),
                depth,
                pos
        );

        source.sendFeedback(() -> Text.literal(
                "Generated %s at %d %d %d.".formatted(key, pos.getX(), pos.getY(), pos.getZ())
        ), true);

        return depth;
    }

    public static void initialize(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("generatedungeon")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("theme", StringArgumentType.word())
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

    private static DungeonTheme getTheme(String value) {
        for (DungeonTheme theme : DungeonTheme.values()) {
            if (theme.asString().equals(value) || theme.name().equalsIgnoreCase(value)) {
                return theme;
            }
        }

        return null;
    }

    private static DungeonTier getTier(int tierNumber) {
        for (DungeonTier tier : DungeonTier.values()) {
            if (tier.tier == tierNumber) {
                return tier;
            }
        }

        throw new IllegalArgumentException("Unsupported dungeon tier: " + tierNumber);
    }
}
