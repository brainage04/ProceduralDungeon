package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.command.core.ModCommands;
import com.github.brainage04.procedural_dungeon.command.StructureGalleryCommand;
import com.github.brainage04.procedural_dungeon.item.ModItems;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.worldgen.processor.ModStructureProcessorTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructureTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructurePoolElementTypes;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProceduralDungeon implements ModInitializer {
    public static final String MOD_ID = "procedural_dungeon";
    public static final String MOD_NAME = "Procedural Dungeon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static Identifier of(String namespace) {
        return Identifier.fromNamespaceAndPath(MOD_ID, namespace);
    }

	@Override
	public void onInitialize() {
        LOGGER.info("{} initialising...", MOD_NAME);

        ModItems.initialize();
        DungeonLockManager.initialize();

        // Register runtime types referenced by generated worldgen JSON.
        ModStructureProcessorTypes.initialize();
        ModStructurePoolElementTypes.initialize();
        ModStructureTypes.initialize();
        StagedDungeonGenerationManager.initialize();
        StructureGalleryCommand.initializeAutobuild();

        ModCommands.initialize();

        LOGGER.info("{} initialised.", MOD_NAME);
	}
}
