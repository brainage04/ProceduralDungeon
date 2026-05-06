package com.github.brainage04.procedural_dungeon.command.core;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Arrays;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.resources.Identifier;

public class ModSuggestionProviders {
    public static final SuggestionProvider<SharedSuggestionProvider> DUNGEON_THEMES = SuggestionProviders.register(
            Identifier.fromNamespaceAndPath(ProceduralDungeon.MOD_ID, "dungeon_themes"),
            (context, builder) -> SharedSuggestionProvider.suggestResource(
                    Arrays.stream(DungeonTheme.values()),
                    builder,
                    DungeonTheme::getId,
                    DungeonTheme::getName
            )
    );

}
