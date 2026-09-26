package com.github.brainage04.procedural_dungeon.playtest;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import java.io.IOException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * On the first title screen, replaces the playtest world with a fresh one and opens it, so launching the playtest client
 * goes straight into the game.
 */
public class ProceduralDungeonPlaytestClient implements ClientModInitializer {
    private static final String WORLD_ID = "procedural_dungeon_playtest";
    private static boolean opened;

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!opened && screen instanceof TitleScreen) {
                opened = true;
                client.execute(() -> openFreshWorld(client));
            }
        });
    }

    private static void openFreshWorld(Minecraft client) {
        LevelStorageSource levels = client.getLevelSource();
        if (levels.levelExists(WORLD_ID)) {
            try (LevelStorageSource.LevelStorageAccess access = levels.createAccess(WORLD_ID)) {
                access.deleteLevel();
            } catch (IOException exception) {
                ProceduralDungeon.LOGGER.error("Could not delete the previous playtest world", exception);
            }
        }
        LevelSettings settings = new LevelSettings(
                "Dungeon Playtest",
                GameType.SURVIVAL,
                new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false),
                true,
                WorldDataConfiguration.DEFAULT
        );
        client.createWorldOpenFlows().createFreshLevel(
                WORLD_ID,
                settings,
                WorldOptions.defaultWithRandomSeed(),
                WorldPresets::createNormalWorldDimensions,
                new TitleScreen()
        );
    }
}
