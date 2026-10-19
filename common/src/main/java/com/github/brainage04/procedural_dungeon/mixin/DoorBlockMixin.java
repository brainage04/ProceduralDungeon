package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps redstone from opening a progression door before its key has unlocked it. */
@Mixin(DoorBlock.class)
public class DoorBlockMixin {
    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void proceduralDungeon$holdLockedDoor(BlockState state, Level level, BlockPos pos, Block source,
                                                 Orientation orientation, boolean moved, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel && DungeonLockManager.isDoorLocked(serverLevel, pos)) {
            ci.cancel();
        }
    }
}
