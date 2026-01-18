package com.github.brainage04.procedural_dungeon.util;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;

public class RegistryKeyUtils {
    public static String getKeyString(DungeonTheme theme, DungeonTier tier) {
        return "dungeon/%s/%s/tier_%s".formatted(
                theme.dimension,
                theme.asString(),
                tier.tier
        );
    }

    public static <T> RegistryKey<T> create(RegistryKey<Registry<T>> registry, String name) {
        return RegistryKey.of(registry, ProceduralDungeon.of(name));
    }
}
