package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.worldgen.processor.ModStructureProcessorTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructureTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructurePoolElementTypes;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProceduralDungeon {
    public static final String MOD_ID = "procedural_dungeon";
    public static final String MOD_NAME = "Procedural Dungeon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static Identifier of(String namespace) {
        return Identifier.fromNamespaceAndPath(MOD_ID, namespace);
    }

	public static void initialize() {
        LOGGER.info("{} initialising...", MOD_NAME);

        DungeonLockManager.initialize();


        LOGGER.info("{} initialised.", MOD_NAME);
	}
}
