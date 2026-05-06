package com.github.brainage04.procedural_dungeon.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class DungeonVariantGameTests {
    @GameTest(maxTicks = 200, setupTicks = 0, skyAccess = true)
    public void generateRandomDungeonVariants(TestContext context) {
        BlockPos anchor = context.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos origin = new BlockPos(anchor.getX(), 80, anchor.getZ());
        ServerCommandSource source = context.getWorld()
                .getServer()
                .getCommandSource()
                .withWorld(context.getWorld())
                .withPosition(Vec3d.ofBottomCenter(origin))
                .withLevel(4)
                .withSilent();

        try {
            DungeonVariantSmokeTester.placeRandomVariants(source, origin, 8, DungeonVariantSmokeTester.DEFAULT_SPACING, DungeonVariantSmokeTester.DEFAULT_SEED);
            context.complete();
        } catch (CommandSyntaxException e) {
            throw context.createError("Failed to place dungeon variant: %s", e.getMessage());
        } catch (RuntimeException e) {
            throw context.createError("Unexpected dungeon variant test failure: %s", e.getMessage());
        }
    }
}
