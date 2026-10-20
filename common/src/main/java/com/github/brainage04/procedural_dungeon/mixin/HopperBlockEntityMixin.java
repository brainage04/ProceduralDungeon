package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hoppers and hopper minecarts cannot pull from a locked chest, nor from a chest joined to one. */
@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {
    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void proceduralDungeon$keepLockedChestsShut(
            Level level, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos source =
                    BlockPos.containing(
                            hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
            if (DungeonLockManager.isExtractionLocked(serverLevel, source)) {
                cir.setReturnValue(false);
            }
        }
    }
}
