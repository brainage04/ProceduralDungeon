package com.github.brainage04.procedural_dungeon.datagen.common;

public enum SpawnerMob {
    ZOMBIE("minecraft:zombie"),
    HUSK("minecraft:husk"),
    SPIDER("minecraft:spider"),
    CAVE_SPIDER("minecraft:cave_spider"),
    SKELETON("minecraft:skeleton"),
    WITHER_SKELETON("minecraft:wither_skeleton"),
    STRAY("minecraft:stray"),
    BOGGED("minecraft:bogged");

    public final String entityId;

    SpawnerMob(String entityId) {
        this.entityId = entityId;
    }
}