package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fluids cannot flow from outside into a protected dungeon room; fluids inside a room (the sluice puzzle) flow freely.
 */
@Mixin(FlowingFluid.class)
public class FlowingFluidMixin {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void proceduralDungeon$stayOutOfProtectedRooms(LevelAccessor level, BlockPos pos, BlockState state, Direction direction,
                                                          FluidState fluid, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel
                && DungeonLockManager.isProtectedFrom(serverLevel, pos, pos.relative(direction.getOpposite()))) {
            ci.cancel();
        }
    }
}
