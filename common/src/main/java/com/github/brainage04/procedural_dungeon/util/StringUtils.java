package com.github.brainage04.procedural_dungeon.util;

public class StringUtils {
    /**
     * {@code nether_wastes} becomes {@code Nether Wastes}.
     */
    public static String snakeCaseToHumanReadable(String input) {
        StringBuilder output = new StringBuilder(input.length());
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (c == '_') {
                if (!output.isEmpty()) {
                    output.append(' ');
                }
                capitalizeNext = true;
            } else {
                output.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
        }
        return output.toString().stripTrailing();
    }
}
