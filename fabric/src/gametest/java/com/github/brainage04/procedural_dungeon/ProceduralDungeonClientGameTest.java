package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.command.GenerateDungeonCommand;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class ProceduralDungeonClientGameTest implements FabricClientGameTest {
    private static final BlockPos GENERATION_ORIGIN = new BlockPos(0, 64, 0);
    private static final int SEARCH_RADIUS = 80;

    @Override
    public void runTest(ClientGameTestContext context) {
        Properties serverProperties = ClientGameTestServers.flatServerProperties();
        AtomicReference<GeneratedDungeon> generated = new AtomicReference<>();

        ClientGameTestServers.withDedicatedServer(context, serverProperties, "Procedural Dungeon generated fixture GameTest", server -> { try {
            server.runOnServer(minecraftServer -> scheduleDungeon(minecraftServer.overworld(), minecraftServer));
            ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
            context.waitTicks(240);
            server.runOnServer(minecraftServer -> generated.set(assertGeneratedDungeon(
                    minecraftServer.overworld(), minecraftServer, minecraftServer.getPlayerList().getPlayers().getFirst())));
            context.waitTicks(20);
            GeneratedDungeon dungeon = generated.get();
            if (dungeon == null) {
                throw new AssertionError("Generated dungeon inspection state was not captured");
            }
        
            ClientGameTestRecorder.startRecording(context);
            ClientGameTestRecorder.showStep(
                    context,
                    "dungeon.generated",
                    "Generated Tier 3 deepslate dungeon",
                    "This chamber was placed through the production staged dungeon generator"
            );
            context.waitTicks(45);
            server.runOnServer(minecraftServer -> positionPlayer(
                    minecraftServer.overworld(),
                    minecraftServer.getPlayerList().getPlayers().getFirst(),
                    dungeon.chest()
            ));
            context.waitTicks(20);
            ClientGameTestRecorder.showStep(
                    context,
                    "dungeon.loot",
                    "Generated loot room",
                    "The visible chest was generated from the selected dungeon layout"
            );
            context.waitTicks(45);
            server.runOnServer(minecraftServer -> positionPlayer(
                    minecraftServer.overworld(),
                    minecraftServer.getPlayerList().getPlayers().getFirst(),
                    dungeon.spawner()
            ));
            context.waitTicks(20);
            ClientGameTestRecorder.showStep(
                    context,
                    "dungeon.difficulty",
                    "Generated dungeon threat",
                    "The visible spawner is configured under hard server difficulty"
            );
            context.waitTicks(45);
        } finally {
            ;
        } });
    }

    private static void scheduleDungeon(ServerLevel level, MinecraftServer server) {
        server.setDifficulty(Difficulty.HARD, true);
        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atBottomCenterOf(GENERATION_ORIGIN))
                .withPermission(PermissionSet.ALL_PERMISSIONS)
                .withSuppressedOutput();
        try {
            int depth = GenerateDungeonCommand.execute(source, DungeonTheme.DEEPSLATE.getId(), 3, 6);
            if (depth != 6) {
                throw new AssertionError("Production dungeon command did not schedule the requested depth");
            }
        } catch (Exception exception) {
            throw new AssertionError("Production dungeon generation command failed", exception);
        }
    }

    private static GeneratedDungeon assertGeneratedDungeon(ServerLevel level, MinecraftServer server, ServerPlayer player) {
        StagedDungeonGenerationManager.Status status = StagedDungeonGenerationManager.status(level);
        if (status.jobs() != 0 || status.pendingPieces() != 0) {
            throw new AssertionError("Staged dungeon generation did not finish: " + status);
        }
        BlockPos chest = findBlockEntity(level, ChestBlockEntity.class);
        BlockPos spawner = findBlockEntity(level, SpawnerBlockEntity.class);
        if (chest == null) {
            throw new AssertionError("Generated dungeon did not contain a loot chest");
        }
        if (spawner == null) {
            throw new AssertionError("Generated dungeon did not contain a difficulty spawner");
        }
        if (server.getWorldData().getDifficulty() != Difficulty.HARD) {
            throw new AssertionError("Generated dungeon must be inspected under hard difficulty");
        }
        if (!level.getBlockState(chest).is(Blocks.CHEST) || !level.getBlockState(spawner).is(Blocks.SPAWNER)) {
            throw new AssertionError("Generated loot and difficulty markers must retain their visible blocks");
        }

        positionPlayer(level, player, chest);
        return new GeneratedDungeon(chest, spawner);
    }

    private static void positionPlayer(ServerLevel level, ServerPlayer player, BlockPos target) {
        BlockPos camera = findCameraPosition(level, target);
        player.setGameMode(GameType.CREATIVE);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(camera.getX() + 0.5D, camera.getY(), camera.getZ() + 0.5D);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
    }

    private static BlockPos findCameraPosition(ServerLevel level, BlockPos target) {
        for (Direction direction : new Direction[] {
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST
        }) {
            for (int distance = 3; distance >= 2; distance--) {
                BlockPos camera = target.relative(direction, distance);
                if (!level.getBlockState(camera).isAir() || !level.getBlockState(camera.above()).isAir()) {
                    continue;
                }

                boolean clearView = true;
                for (int step = 1; step < distance; step++) {
                    BlockPos between = target.relative(direction, step);
                    if (!level.getBlockState(between).isAir() || !level.getBlockState(between.above()).isAir()) {
                        clearView = false;
                        break;
                    }
                }
                if (clearView) {
                    return camera;
                }
            }
        }
        throw new AssertionError("Generated marker has no unobstructed camera position at " + target);
    }

    private static BlockPos findBlockEntity(ServerLevel level, Class<?> type) {
        for (BlockPos pos : BlockPos.betweenClosed(
                GENERATION_ORIGIN.offset(-SEARCH_RADIUS, -48, -SEARCH_RADIUS),
                GENERATION_ORIGIN.offset(SEARCH_RADIUS, 48, SEARCH_RADIUS)
        )) {
            if (type.isInstance(level.getBlockEntity(pos))) {
                return pos.immutable();
            }
        }
        return null;
    }

    private record GeneratedDungeon(BlockPos chest, BlockPos spawner) {
    }
}
