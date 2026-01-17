package com.github.brainage04.procedural_dungeon.datagen.structure;

public enum SpawnerMob {
    ZOMBIE("zombie", "minecraft:zombie"),
    HUSK("husk", "minecraft:husk"),
    SPIDER("spider", "minecraft:spider"),
    CAVE_SPIDER("cave_spider", "minecraft:cave_spider"),
    SKELETON("skeleton", "minecraft:skeleton"),
    WITHER_SKELETON("wither_skeleton", "minecraft:wither_skeleton"),
    STRAY("stray", "minecraft:stray"),
    BOGGED("bogged", "minecraft:bogged");

    public final String id;
    public final String entityId;

    SpawnerMob(String id, String entityId) {
        this.id = id;
        this.entityId = entityId;
    }
}