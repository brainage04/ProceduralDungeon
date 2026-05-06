package com.github.brainage04.procedural_dungeon.util;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;

public class RegistryKeyUtils {
    public static String getKeyString(DungeonTheme theme, DungeonTier tier) {
        return "dungeon/%s/%s/tier_%s".formatted(
                theme.dimension.identifier().getPath(),
                theme.getSerializedName(),
                tier.tier
        );
    }

    public static <T> ResourceKey<T> create(ResourceKey<Registry<T>> registry, String name) {
        return ResourceKey.create(registry, ProceduralDungeon.of(name));
    }
}
