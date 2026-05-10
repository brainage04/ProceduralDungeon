package com.github.brainage04.procedural_dungeon.command;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockSaveData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class DungeonLocksCommand {
    private static final String REVEAL_MARKER_TAG = "procedural_dungeon.lock_reveal";
    private static final long REVEAL_TICKS = 20L * 30L;
    private static final float MARKER_SCALE = 0.94F;
    private static final float MARKER_INSET = (1.0F - MARKER_SCALE) * 0.5F;
    private static final List<RevealMarker> REVEAL_MARKERS = new ArrayList<>();
    private static boolean revealTickRegistered;

    private DungeonLocksCommand() {}

    public static void initialize(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!revealTickRegistered) {
            revealTickRegistered = true;
            ServerTickEvents.END_SERVER_TICK.register(server -> tickRevealMarkers());
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearRevealMarkers());
        }

        dispatcher.register(literal("dungeonlocks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("list")
                        .executes(context -> list(context.getSource(), Target.LOCKED_CHEST, 10))
                        .then(argument("limit", IntegerArgumentType.integer(1, 100))
                                .executes(context -> list(
                                        context.getSource(),
                                        Target.LOCKED_CHEST,
                                        IntegerArgumentType.getInteger(context, "limit")
                                ))
                        )
                )
                .then(literal("nearest")
                        .executes(context -> nearest(context.getSource(), Target.LOCKED_CHEST))
                )
                .then(literal("reveal")
                        .executes(context -> reveal(context.getSource(), Target.LOCKED_CHEST, 128))
                        .then(argument("radius", IntegerArgumentType.integer(1, 512))
                                .executes(context -> reveal(
                                        context.getSource(),
                                        Target.LOCKED_CHEST,
                                        IntegerArgumentType.getInteger(context, "radius")
                                ))
                        )
                )
                .then(literal("keys")
                        .then(literal("list")
                                .executes(context -> list(context.getSource(), Target.KEY_SOURCE_CHEST, 10))
                                .then(argument("limit", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> list(
                                                context.getSource(),
                                                Target.KEY_SOURCE_CHEST,
                                                IntegerArgumentType.getInteger(context, "limit")
                                        ))
                                )
                        )
                        .then(literal("nearest")
                                .executes(context -> nearest(context.getSource(), Target.KEY_SOURCE_CHEST))
                        )
                        .then(literal("reveal")
                                .executes(context -> reveal(context.getSource(), Target.KEY_SOURCE_CHEST, 128))
                                .then(argument("radius", IntegerArgumentType.integer(1, 512))
                                        .executes(context -> reveal(
                                                context.getSource(),
                                                Target.KEY_SOURCE_CHEST,
                                                IntegerArgumentType.getInteger(context, "radius")
                                        ))
                                )
                        )
                )
        );
    }

    private static int list(CommandSourceStack source, Target target, int limit) {
        List<BlockPos> positions = sortedPositions(source.getLevel(), source.getPosition(), target);
        int shown = Math.min(limit, positions.size());
        source.sendSuccess(() -> Component.literal(
                "%s in %s: %d. Showing %d."
                        .formatted(target.label(), source.getLevel().dimension().identifier(), positions.size(), shown)
        ), false);

        Vec3 origin = source.getPosition();
        for (int i = 0; i < shown; i++) {
            int index = i + 1;
            BlockPos pos = positions.get(i);
            double distance = Math.sqrt(pos.distToCenterSqr(origin));
            source.sendSuccess(() -> Component.literal(
                    "%d. %d %d %d (%.1f blocks)"
                    .formatted(index, pos.getX(), pos.getY(), pos.getZ(), distance)
            ), false);
        }
        return positions.size();
    }

    private static int nearest(CommandSourceStack source, Target target) {
        BlockPos nearest = nearest(source.getLevel(), source.getPosition(), target);
        if (nearest == null) {
            source.sendFailure(Component.literal("No %s found in this dimension.".formatted(target.label().toLowerCase())));
            return 0;
        }

        double distance = Math.sqrt(nearest.distToCenterSqr(source.getPosition()));
        source.sendSuccess(() -> Component.literal(
                "Nearest %s: %d %d %d (%.1f blocks)"
                        .formatted(target.singularLabel(), nearest.getX(), nearest.getY(), nearest.getZ(), distance)
        ), false);
        mark(source.getLevel(), nearest);
        return 1;
    }

    private static int reveal(CommandSourceStack source, Target target, int radius) {
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();
        double maxDistanceSqr = radius * radius;
        int matched = 0;
        int spawned = 0;
        clearTaggedRevealMarkers(level);
        for (BlockPos pos : positions(level, target)) {
            if (pos.distToCenterSqr(origin) > maxDistanceSqr) {
                continue;
            }
            matched++;
            if (mark(level, pos)) {
                spawned++;
            }
        }

        int finalMatched = matched;
        int finalSpawned = spawned;
        source.sendSuccess(() -> Component.literal(
                finalSpawned == finalMatched
                        ? "Revealed %d %s within %d blocks for 30 seconds."
                        .formatted(finalMatched, target.label().toLowerCase(), radius)
                        : "Revealed %d %s within %d blocks for 30 seconds (%d marker(s) spawned in loaded chunks)."
                        .formatted(finalMatched, target.label().toLowerCase(), radius, finalSpawned)
        ), false);
        return matched;
    }

    private static List<BlockPos> sortedPositions(ServerLevel level, Vec3 origin, Target target) {
        return positions(level, target).stream()
                .sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(origin)))
                .toList();
    }

    private static BlockPos nearest(ServerLevel level, Vec3 origin, Target target) {
        return sortedPositions(level, origin, target).stream().findFirst().orElse(null);
    }

    private static List<BlockPos> positions(ServerLevel level, Target target) {
        DungeonLockSaveData data = level.getDataStorage().computeIfAbsent(DungeonLockSaveData.TYPE);
        List<Long> positions = switch (target) {
            case LOCKED_CHEST -> data.getLockedChests();
            case KEY_SOURCE_CHEST -> data.getKeySourceChests();
        };
        return positions.stream()
                .map(BlockPos::of)
                .toList();
    }

    private static boolean mark(ServerLevel level, BlockPos pos) {
        removeExistingMarker(level, pos);

        Display.BlockDisplay marker = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        marker.setPos(pos.getX(), pos.getY(), pos.getZ());
        marker.setBlockState(markerBlockState(level, pos));
        marker.setTransformation(new Transformation(
                new Vector3f(MARKER_INSET, MARKER_INSET, MARKER_INSET),
                new Quaternionf(),
                new Vector3f(MARKER_SCALE, MARKER_SCALE, MARKER_SCALE),
                new Quaternionf()
        ));
        marker.setWidth(1.0F);
        marker.setHeight(1.0F);
        marker.noPhysics = true;
        marker.setInvulnerable(true);
        marker.setSilent(true);
        marker.setGlowingTag(true);
        marker.addTag(REVEAL_MARKER_TAG);
        if (level.addFreshEntity(marker)) {
            REVEAL_MARKERS.add(new RevealMarker(level, pos, marker, level.getGameTime() + REVEAL_TICKS));
            return true;
        }
        return false;
    }

    private static BlockState markerBlockState(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            return state;
        }
        return Blocks.CHEST.defaultBlockState();
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

    private static void clearRevealMarkers() {
        for (RevealMarker marker : REVEAL_MARKERS) {
            marker.entity().discard();
        }
        REVEAL_MARKERS.clear();
    }

    private static void clearTaggedRevealMarkers(ServerLevel level) {
        List<Entity> existingMarkers = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.entityTags().contains(REVEAL_MARKER_TAG)) {
                existingMarkers.add(entity);
            }
        }
        for (Entity entity : existingMarkers) {
            entity.discard();
        }
        REVEAL_MARKERS.removeIf(marker -> marker.level() == level);
    }

    private enum Target {
        LOCKED_CHEST("Locked chests", "locked chest"),
        KEY_SOURCE_CHEST("Rusted Key chests", "Rusted Key chest");

        private final String label;
        private final String singularLabel;

        Target(String label, String singularLabel) {
            this.label = label;
            this.singularLabel = singularLabel;
        }

        private String label() {
            return label;
        }

        private String singularLabel() {
            return singularLabel;
        }
    }

    private record RevealMarker(ServerLevel level, BlockPos pos, Entity entity, long expiresAt) {}
}
