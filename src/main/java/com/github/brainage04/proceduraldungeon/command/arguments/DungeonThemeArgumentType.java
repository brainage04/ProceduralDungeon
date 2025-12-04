package com.github.brainage04.proceduraldungeon.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Arrays;
import java.util.Collection;

public class DungeonThemeArgumentType implements ArgumentType<DungeonTheme> {
    private DungeonThemeArgumentType() {

    }

    public static DungeonThemeArgumentType dungeonTheme() {
        return new DungeonThemeArgumentType();
    }

    public static DungeonTheme getDungeonTheme(final CommandContext<?> context, final String name) {
        return context.getArgument(name, DungeonTheme.class);
    }

    @Override
    public DungeonTheme parse(final StringReader reader) throws CommandSyntaxException {
        return Enum.valueOf(DungeonTheme.class, reader.readString());
    }

    @Override
    public String toString() {
        return "dungeonTheme()";
    }

    @Override
    public Collection<String> getExamples() {
        return Arrays.stream(DungeonTheme.values()).map(Enum::toString).toList();
    }
}