package com.github.brainage04.procedural_dungeon.command.core;

import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.util.Identifier;

import java.util.Arrays;

public class ModSuggestionProviders {
    public static final SuggestionProvider<CommandSource> DUNGEON_THEMES = SuggestionProviders.register(
            Identifier.ofVanilla("dungeon_themes"),
            (context, builder) -> CommandSource.suggestFromIdentifier(
                    Arrays.stream(DungeonTheme.values()),
                    builder,
                    DungeonTheme::getId,
                    DungeonTheme::getName
            )
    );

}
