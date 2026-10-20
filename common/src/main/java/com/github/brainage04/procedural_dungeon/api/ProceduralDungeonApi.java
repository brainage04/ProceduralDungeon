package com.github.brainage04.procedural_dungeon.api;

import com.github.brainage04.procedural_dungeon.rule.DungeonSwitches;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

/**
 * The entry point for other mods. Everything here is server-side; call it on the server thread
 * unless a method says otherwise.
 */
public final class ProceduralDungeonApi {
    private ProceduralDungeonApi() {}

    /**
     * Limits natural dungeon generation to the dimensions {@code enabled} accepts, on top of the
     * {@code procedural_dungeon:natural_generation} game rule. The default accepts every dimension;
     * the last call wins, and it may come before or after the server starts. The predicate runs on
     * worldgen threads, so it must be thread-safe. Chunks that already hold a dungeon start keep
     * it.
     */
    public static void naturalGeneration(Predicate<ResourceKey<Level>> enabled) {
        DungeonSwitches.setNaturalGeneration(enabled);
    }

    /**
     * Limits the mechanics that act outside dungeons (grindstone salvage, anvil over-max levels and
     * relic essence, Soulbound) to the levels {@code enabled} accepts. Grindstone salvage also
     * needs the {@code procedural_dungeon:grindstone_salvage} game rule. The default accepts every
     * level; the last call wins.
     */
    public static void globalMechanics(Predicate<ServerLevel> enabled) {
        DungeonSwitches.setGlobalMechanics(enabled);
    }
}
