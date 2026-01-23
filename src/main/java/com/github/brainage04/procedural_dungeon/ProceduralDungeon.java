package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.command.core.ModCommands;
import com.github.brainage04.procedural_dungeon.datagen.processor_list.ModRuleBlockEntityModifierTypes;
import net.fabricmc.api.ModInitializer;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProceduralDungeon implements ModInitializer {
    public static final String MOD_ID = "procedural_dungeon";
    public static final String MOD_NAME = "Procedural Dungeon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static Identifier of(String namespace) {
        return Identifier.of(MOD_ID, namespace);
    }

	@Override
	public void onInitialize() {
        LOGGER.info("{} initialising...", MOD_NAME);

        // datagen
        ModRuleBlockEntityModifierTypes.initialize();

        ModCommands.initialize();

        LOGGER.info("{} initialised.", MOD_NAME);
	}
}