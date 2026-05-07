package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;

public final class DungeonJigsawPoolReplacements {
    private static final String DUNGEON_PREFIX = "dungeon/";
    private static final String HALLWAY_PREFIX = "dungeon/hallway";
    private static final String BASE_SPAWNER_POOL = "dungeon/spawner/tier_1";

    private static final List<Identifier> BASE_POOLS = List.of(
            ProceduralDungeon.of("dungeon/hallway"),
            ProceduralDungeon.of("dungeon/hallway/end"),
            ProceduralDungeon.of("dungeon/hallway/loot"),
            ProceduralDungeon.of("dungeon/hallway/room"),
            ProceduralDungeon.of("dungeon/hallway/trap"),
            ProceduralDungeon.of(BASE_SPAWNER_POOL)
    );

    private DungeonJigsawPoolReplacements() {
    }

    public static Map<Identifier, Identifier> create(String key, DungeonTier tier) {
        return BASE_POOLS.stream().collect(Collectors.toUnmodifiableMap(
                pool -> pool,
                pool -> getReplacement(pool, ProceduralDungeon.of(key), tier.tier)
        ));
    }

    static Identifier getReplacement(Identifier pool, Identifier variant, int spawnerTier) {
        if (!pool.getNamespace().equals(ProceduralDungeon.MOD_ID)) {
            return pool;
        }

        String path = pool.getPath();
        if (path.equals(HALLWAY_PREFIX) || path.startsWith(HALLWAY_PREFIX + "/")) {
            return Identifier.fromNamespaceAndPath(variant.getNamespace(), "%s/%s".formatted(variant.getPath(), path.substring(DUNGEON_PREFIX.length())));
        }

        if (path.equals(BASE_SPAWNER_POOL)) {
            return ProceduralDungeon.of("dungeon/spawner/tier_%d".formatted(spawnerTier));
        }

        return pool;
    }
}
