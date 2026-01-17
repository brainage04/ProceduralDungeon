package com.github.brainage04.procedural_dungeon.util;

import org.jetbrains.annotations.Nullable;

public class EnumUtils {
    public static @Nullable <T extends Enum<T>> T getEnumValue(Class<T> enumClass, String value) {
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
