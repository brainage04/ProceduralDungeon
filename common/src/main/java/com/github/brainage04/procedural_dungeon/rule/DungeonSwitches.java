package com.github.brainage04.procedural_dungeon.rule;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Decides where Procedural Dungeon's world-wide behaviour applies: the {@link DungeonGameRules} an
 * operator sets, narrowed by the predicates another mod installs through the public API.
 *
 * <p>Natural generation is asked on worldgen threads, so the predicates and the server are volatile
 * and the predicates must be thread-safe.
 */
public final class DungeonSwitches {
    private static volatile Predicate<ResourceKey<Level>> naturalGeneration = dimension -> true;
    private static volatile Predicate<ServerLevel> globalMechanics = level -> true;
    private static volatile @Nullable MinecraftServer server;

    private DungeonSwitches() {}

    public static void setNaturalGeneration(Predicate<ResourceKey<Level>> enabled) {
        naturalGeneration = Objects.requireNonNull(enabled);
    }

    public static void setGlobalMechanics(Predicate<ServerLevel> enabled) {
        globalMechanics = Objects.requireNonNull(enabled);
    }

    /** Called before the server creates its levels, so worldgen can read the game rules. */
    public static void serverStarting(MinecraftServer starting) {
        server = starting;
    }

    public static void serverStopped(MinecraftServer stopped) {
        if (server == stopped) {
            server = null;
        }
    }

    /** Whether new chunks of {@code dimension} may start a natural dungeon, or locate one. */
    public static boolean naturalGeneration(ResourceKey<Level> dimension) {
        MinecraftServer current = server;
        if (current != null && !current.getGameRules().get(DungeonGameRules.NATURAL_GENERATION)) {
            return false;
        }
        return naturalGeneration.test(dimension);
    }

    /**
     * Whether the mechanics that act outside dungeons (grindstone salvage, anvil over-max levels
     * and relic essence, Soulbound) apply in {@code level}. A client-side level, which only
     * predicts the server's menus, always answers true.
     */
    public static boolean globalMechanics(@Nullable Level level) {
        return !(level instanceof ServerLevel serverLevel) || globalMechanics.test(serverLevel);
    }

    /** Grindstone salvage needs its game rule as well as the global-mechanics scope. */
    public static boolean grindstoneSalvage(@Nullable Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return serverLevel.getGameRules().get(DungeonGameRules.GRINDSTONE_SALVAGE)
                && globalMechanics.test(serverLevel);
    }
}
