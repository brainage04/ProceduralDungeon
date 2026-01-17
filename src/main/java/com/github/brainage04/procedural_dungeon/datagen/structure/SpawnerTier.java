package com.github.brainage04.procedural_dungeon.datagen.structure;

public enum SpawnerTier {
    TIER_1(1, 4, 200, 800, 16, 1, 4),
    TIER_2(2, 8, 175, 650, 20, 2, 5),
    TIER_3(3, 12, 150, 500, 24, 3, 6),
    TIER_4(4, 16, 125, 350, 28, 4, 7),
    TIER_5(5, 20, 100, 200, 32, 5, 8);

    public final int id;
    public final int maxNearbyEntities;
    public final int minSpawnDelay;
    public final int maxSpawnDelay;
    public final int requiredPlayerRange;
    public final int spawnCount;
    public final int spawnRange;

    SpawnerTier(int id, int maxNearbyEntities, int minSpawnDelay, int maxSpawnDelay, int requiredPlayerRange, int spawnCount, int spawnRange) {
        this.id = id;
        this.maxNearbyEntities = maxNearbyEntities;
        this.minSpawnDelay = minSpawnDelay;
        this.maxSpawnDelay = maxSpawnDelay;
        this.requiredPlayerRange = requiredPlayerRange;
        this.spawnCount = spawnCount;
        this.spawnRange = spawnRange;
    }
}