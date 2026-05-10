package com.github.brainage04.procedural_dungeon.command;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockSaveData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class DungeonLocksCommand {
    private static final long REVEAL_TICKS = 20L * 30L;
    private static final List<RevealMarker> REVEAL_MARKERS = new ArrayList<>();
    private static boolean revealTickRegistered;

    private DungeonLocksCommand() {}

    public static void initialize(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!revealTickRegistered) {
            revealTickRegistered = true;
            ServerTickEvents.END_SERVER_TICK.register(server -> tickRevealMarkers());
        }

        dispatcher.register(literal("dungeonlocks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("list")
                        .executes(context -> list(context.getSource(), 10))
                        .then(argument("limit", IntegerArgumentType.integer(1, 100))
                                .executes(context -> list(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "limit")
                                ))
                        )
                )
                .then(literal("nearest")
                        .executes(context -> nearest(context.getSource()))
                )
                .then(literal("reveal")
                        .executes(context -> reveal(context.getSource(), 128))
                        .then(argument("radius", IntegerArgumentType.integer(1, 512))
                                .executes(context -> reveal(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius")
                                ))
                        )
                )
        );
    }

    private static int list(CommandSourceStack source, int limit) {
        List<BlockPos> lockedChests = sortedLockedChests(source.getLevel(), source.getPosition());
        int shown = Math.min(limit, lockedChests.size());
        source.sendSuccess(() -> Component.literal(
                "Locked chests in %s: %d. Showing %d."
                        .formatted(source.getLevel().dimension().identifier(), lockedChests.size(), shown)
        ), false);

        Vec3 origin = source.getPosition();
        for (int i = 0; i < shown; i++) {
            int index = i + 1;
            BlockPos pos = lockedChests.get(i);
            double distance = Math.sqrt(pos.distToCenterSqr(origin));
            source.sendSuccess(() -> Component.literal(
                    "%d. %d %d %d (%.1f blocks)"
                            .formatted(index, pos.getX(), pos.getY(), pos.getZ(), distance)
            ), false);
        }
        return lockedChests.size();
    }

    private static int nearest(CommandSourceStack source) {
        BlockPos nearest = nearestLockedChest(source.getLevel(), source.getPosition());
        if (nearest == null) {
            source.sendFailure(Component.literal("No locked chests found in this dimension."));
            return 0;
        }

        double distance = Math.sqrt(nearest.distToCenterSqr(source.getPosition()));
        source.sendSuccess(() -> Component.literal(
                "Nearest locked chest: %d %d %d (%.1f blocks)"
                        .formatted(nearest.getX(), nearest.getY(), nearest.getZ(), distance)
        ), false);
        mark(source.getLevel(), nearest);
        return 1;
    }

    private static int reveal(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();
        double maxDistanceSqr = radius * radius;
        int revealed = 0;
        for (BlockPos pos : lockedChests(level)) {
            if (pos.distToCenterSqr(origin) > maxDistanceSqr) {
                continue;
            }
            mark(level, pos);
            revealed++;
        }

        int finalRevealed = revealed;
        source.sendSuccess(() -> Component.literal(
                "Revealed %d locked chest(s) within %d blocks."
                        .formatted(finalRevealed, radius)
        ), false);
        return revealed;
    }

    private static List<BlockPos> sortedLockedChests(ServerLevel level, Vec3 origin) {
        return lockedChests(level).stream()
                .sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(origin)))
                .toList();
    }

    private static BlockPos nearestLockedChest(ServerLevel level, Vec3 origin) {
        return sortedLockedChests(level, origin).stream().findFirst().orElse(null);
    }

    private static List<BlockPos> lockedChests(ServerLevel level) {
        DungeonLockSaveData data = level.getDataStorage().computeIfAbsent(DungeonLockSaveData.TYPE);
        return data.getLockedChests().stream()
                .map(BlockPos::of)
                .toList();
    }

    private static void mark(ServerLevel level, BlockPos pos) {
        removeExistingMarker(level, pos);

        ArmorStand marker = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        marker.setInvisible(true);
        marker.setNoBasePlate(true);
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.setSilent(true);
        marker.setGlowingTag(true);
        marker.addTag("procedural_dungeon.lock_reveal");
        if (level.addFreshEntity(marker)) {
            REVEAL_MARKERS.add(new RevealMarker(level, pos, marker, level.getGameTime() + REVEAL_TICKS));
        }
    }

    private static void tickRevealMarkers() {
        Iterator<RevealMarker> iterator = REVEAL_MARKERS.iterator();
        while (iterator.hasNext()) {
            RevealMarker marker = iterator.next();
            if (!marker.entity().isAlive() || marker.level().getGameTime() >= marker.expiresAt()) {
                marker.entity().discard();
                iterator.remove();
            }
        }
    }

    private static void removeExistingMarker(ServerLevel level, BlockPos pos) {
        Iterator<RevealMarker> iterator = REVEAL_MARKERS.iterator();
        while (iterator.hasNext()) {
            RevealMarker marker = iterator.next();
            if (marker.level() == level && marker.pos().equals(pos)) {
                marker.entity().discard();
                iterator.remove();
            }
        }
    }

    private record RevealMarker(ServerLevel level, BlockPos pos, ArmorStand entity, long expiresAt) {}
}
