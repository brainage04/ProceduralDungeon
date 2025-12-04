package com.github.brainage04.proceduraldungeon.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Arrays;
import java.util.Collection;

public class DungeonTierArgumentType implements ArgumentType<DungeonTier> {
    private DungeonTierArgumentType() {

    }

    public static DungeonTierArgumentType dungeonTier() {
        return new DungeonTierArgumentType();
    }

    public static DungeonTier getDungeonTier(final CommandContext<?> context, final String name) {
        return context.getArgument(name, DungeonTier.class);
    }

    @Override
    public DungeonTier parse(final StringReader reader) throws CommandSyntaxException {
        return Enum.valueOf(DungeonTier.class, reader.readString());
    }

    @Override
    public String toString() {
        return "dungeonTier()";
    }

    @Override
    public Collection<String> getExamples() {
        return Arrays.stream(DungeonTier.values()).map(Enum::toString).toList();
    }
}