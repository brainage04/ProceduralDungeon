package com.github.brainage04.procedural_dungeon.api.impl;

import com.github.brainage04.procedural_dungeon.api.DungeonHandle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

/**
 * Hooks the lock, puzzle and guardian code calls on the server thread. Each finds the API build
 * that contains the position and tells the listeners; positions outside API builds do nothing.
 */
public final class DungeonEvents {
    private DungeonEvents() {}

    /** Applies the build's guardian health scale, then reports the spawn. */
    public static void guardianSpawned(ServerLevel level, Mob guardian, boolean boss) {
        ApiDungeons.at(level, guardian.blockPosition())
                .ifPresent(
                        dungeon -> {
                            scaleHealth(dungeon, guardian);
                            ApiDungeons.dispatch(
                                    listener -> listener.guardianSpawned(dungeon, guardian, boss));
                        });
    }

    public static void guardianKilled(
            ServerLevel level, Mob guardian, @Nullable ServerPlayer killer, boolean boss) {
        ApiDungeons.at(level, guardian.blockPosition())
                .ifPresent(
                        dungeon ->
                                ApiDungeons.dispatch(
                                        listener ->
                                                listener.guardianKilled(
                                                        dungeon, guardian, killer, boss)));
    }

    public static void unlocked(ServerLevel level, Player player, String key, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ApiDungeons.at(level, pos)
                .ifPresent(
                        dungeon ->
                                ApiDungeons.dispatch(
                                        listener ->
                                                listener.unlocked(
                                                        dungeon, serverPlayer, key, pos)));
    }

    public static void puzzleSolved(ServerLevel level, BlockPos chest) {
        ApiDungeons.at(level, chest)
                .ifPresent(
                        dungeon ->
                                ApiDungeons.dispatch(
                                        listener -> listener.puzzleSolved(dungeon, chest)));
    }

    /** {@code pos} is where the key was lost, which finds the dungeon it belonged to. */
    public static void keyDestroyed(ServerLevel level, BlockPos pos, String key) {
        ApiDungeons.at(level, pos)
                .ifPresent(
                        dungeon ->
                                ApiDungeons.dispatch(
                                        listener -> listener.keyDestroyed(dungeon, key)));
    }

    private static void scaleHealth(DungeonHandle dungeon, Mob guardian) {
        double scale =
                ApiDungeons.options(dungeon)
                        .map(options -> options.guardianHealthScale())
                        .orElse(1.0);
        AttributeInstance maxHealth = guardian.getAttribute(Attributes.MAX_HEALTH);
        if (scale != 1.0 && maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() * scale);
            guardian.setHealth(guardian.getMaxHealth());
        }
    }
}
