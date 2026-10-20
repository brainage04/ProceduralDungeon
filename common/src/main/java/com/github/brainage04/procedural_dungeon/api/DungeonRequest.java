package com.github.brainage04.procedural_dungeon.api;

/**
 * What to plan. The layout depends only on these values, never on where the dungeon is built.
 *
 * @param theme theme name as {@code /generatedungeon} accepts it, for example {@code "cobblestone"}
 *     or {@code "procedural_dungeon:sculk"}
 * @param tier loot and difficulty tier, 1 to 5
 * @param depth jigsaw depth, 1 to 20; larger dungeons have more rooms
 * @param seed layout seed; the same request always gives the same plan
 * @param surfaceEntrance start with the theme's surface entrance shaft instead of the start room
 */
public record DungeonRequest(
        String theme, int tier, int depth, long seed, boolean surfaceEntrance) {}
