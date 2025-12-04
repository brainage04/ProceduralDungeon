package com.github.brainage04.proceduraldungeon;

import com.github.brainage04.proceduraldungeon.command.core.ModCommands;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProceduralDungeon implements ModInitializer {
    public static final String MOD_ID = "proceduraldungeon";
    public static final String MOD_NAME = "ProceduralDungeon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        LOGGER.info("{} initialising...", MOD_NAME);

        ModCommands.initialize();

        LOGGER.info("{} initialised.", MOD_NAME);
	}
}