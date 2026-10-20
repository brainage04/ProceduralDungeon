package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps a progression door shut and standing until its key has unlocked it. */
@Mixin(DoorBlock.class)
public class DoorBlockMixin {
    /** Redstone must not open a locked door. */
    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void proceduralDungeon$holdLockedDoor(BlockState state, Level level, BlockPos pos, Block source,
                                                 Orientation orientation, boolean moved, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel && DungeonLockManager.isDoorLocked(serverLevel, pos)) {
            ci.cancel();
        }
    }

    /** Losing the block underneath (or the other half) must not pop a locked door off. */
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void proceduralDungeon$keepLockedDoorStanding(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                                         Direction direction, BlockPos neighborPos, BlockState neighborState,
                                                         RandomSource random, CallbackInfoReturnable<BlockState> cir) {
        if (!cir.getReturnValue().is(state.getBlock()) && level instanceof ServerLevel serverLevel
                && DungeonLockManager.isDoorLocked(serverLevel, pos)) {
            cir.setReturnValue(state);
        }
    }
}
