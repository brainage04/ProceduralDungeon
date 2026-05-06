package com.github.brainage04.procedural_dungeon.command;

import com.github.brainage04.procedural_dungeon.command.core.ModSuggestionProviders;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class GenerateDungeonCommand {
    public static int execute(CommandSourceStack source, String themeString, int tierNumber, int depth) throws CommandSyntaxException {
        DungeonTheme theme = getTheme(themeString);
        if (theme == null) {
            source.sendFailure(Component.literal("Invalid dungeon theme!"));
            return 0;
        }

        DungeonTier tier = getTier(tierNumber);
        String key = RegistryKeyUtils.getKeyString(theme, tier);
        ResourceKey<StructureTemplatePool> poolKey = RegistryKeyUtils.create(Registries.TEMPLATE_POOL, "%s/start".formatted(key));
        var pool = source.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(poolKey);
        BlockPos pos = BlockPos.containing(source.getPosition());

        source.sendSuccess(() -> Component.literal(
                "Generating Tier %d %s dungeon at jigsaw depth %d..."
                        .formatted(tier.tier, theme.getName().getString(), depth)
        ), true);

        PlaceCommand.placeJigsaw(
                source.withSuppressedOutput(),
                pool,
                Identifier.withDefaultNamespace("start"),
                depth,
                pos
        );

        source.sendSuccess(() -> Component.literal(
                "Generated %s at %d %d %d.".formatted(key, pos.getX(), pos.getY(), pos.getZ())
        ), true);

        return depth;
    }

    public static void initialize(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("generatedungeon")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
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
            if (theme.getSerializedName().equals(value) || theme.name().equalsIgnoreCase(value)) {
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
