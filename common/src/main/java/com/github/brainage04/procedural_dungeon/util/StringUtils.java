package com.github.brainage04.procedural_dungeon.util;

public class StringUtils {
    public static String snakeCaseToHumanReadable(String input) {
        StringBuilder output = new StringBuilder();

        char[] charArray = input.toCharArray();

        for (int i = 0; i < charArray.length; i++) {
            char c = charArray[i];

            if (i == 0) {
                output.append(Character.toUpperCase(c));
                continue;
            }

            if (c == '_') {
                if (i == charArray.length - 1) continue;

                output.append(' ');
                output.append(charArray[i + 1]);
                i++;
            }
        }

        return output.toString();
    }
}
