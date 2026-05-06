package com.github.brainage04.procedural_dungeon.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec3;

public class DungeonVariantGameTests {
    @GameTest(maxTicks = 200, setupTicks = 0, skyAccess = true)
    public void generateRandomDungeonVariants(GameTestHelper context) {
        BlockPos anchor = context.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(anchor.getX(), 80, anchor.getZ());
        CommandSourceStack source = context.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withLevel(context.getLevel())
                .withPosition(Vec3.atBottomCenterOf(origin))
                .withPermission(PermissionSet.ALL_PERMISSIONS)
                .withSuppressedOutput();

        try {
            DungeonVariantSmokeTester.placeRandomVariants(source, origin, 8, DungeonVariantSmokeTester.DEFAULT_SPACING, DungeonVariantSmokeTester.DEFAULT_SEED);
            context.succeed();
        } catch (CommandSyntaxException e) {
            throw context.assertionException("Failed to place dungeon variant: %s", e.getMessage());
        } catch (RuntimeException e) {
            throw context.assertionException("Unexpected dungeon variant test failure: %s", e.getMessage());
        }
    }
}
