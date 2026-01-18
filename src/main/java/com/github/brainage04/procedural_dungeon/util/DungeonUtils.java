package com.github.brainage04.procedural_dungeon.util;

import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;

import java.util.ArrayList;
import java.util.List;

public class DungeonUtils {
    public record ThemeTierPair(DungeonTheme theme, DungeonTier tier) {}
    public record ThemeTierKeyPair(ThemeTierPair themeTierPair, String key) {}

    public static final List<String> THEME_TIER_KEYS = new ArrayList<>(12 * 5);
    public static final List<ThemeTierPair> THEME_TIER_COMBINATIONS = new ArrayList<>(12 * 5);
    public static final List<ThemeTierKeyPair> THEME_TIER_KEY_COMBINATIONS = new ArrayList<>(12 * 5);

    static {
        for (DungeonTheme theme : DungeonTheme.values()) {
            for (DungeonTier tier : DungeonTier.values()) {
                String key = RegistryKeyUtils.getKeyString(theme, tier);
                THEME_TIER_KEYS.add(key);

                ThemeTierPair themeTierPair = new ThemeTierPair(theme, tier);
                THEME_TIER_COMBINATIONS.add(themeTierPair);

                ThemeTierKeyPair themeTierKeyPair = new ThemeTierKeyPair(themeTierPair, key);
                THEME_TIER_KEY_COMBINATIONS.add(themeTierKeyPair);
            }
        }
    }
}
