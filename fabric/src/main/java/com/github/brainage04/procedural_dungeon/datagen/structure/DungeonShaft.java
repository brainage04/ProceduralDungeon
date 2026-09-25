package com.github.brainage04.procedural_dungeon.datagen.structure;

import java.util.Map;

/**
 * The vertical shaft that joins a surface entrance to the start room buried below it.
 */
final class DungeonShaft {
    /**
     * The start room variant used below entrances: a ceiling jigsaw named {@link #JIGSAW_NAME} and a scaffolding
     * column from its floor up through the ceiling hole.
     */
    static final String START_TEMPLATE = "dungeon/start_shaft";
    static final String JIGSAW_NAME = "procedural_dungeon:shaft";
    static final String SCAFFOLDING = "minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]";
    static final Map<String, String> SCAFFOLDING_PROPERTIES = StructureBuilder.properties(
            "bottom", "false",
            "distance", "0",
            "waterlogged", "false"
    );

    private DungeonShaft() {}
}
