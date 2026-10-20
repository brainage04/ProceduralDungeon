package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A piston outside a protected dungeon room cannot push, pull or break blocks inside it; the room's own pistons (the
 * sluice puzzle's gates) still work.
 */
@Mixin(PistonStructureResolver.class)
public class PistonStructureResolverMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private BlockPos pistonPos;
    @Shadow @Final private Direction pushDirection;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private List<BlockPos> toDestroy;

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void proceduralDungeon$spareProtectedRooms(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (BlockPos pos : this.toPush) {
            if (DungeonLockManager.isProtectedFrom(serverLevel, pos, this.pistonPos)
                    || DungeonLockManager.isProtectedFrom(serverLevel, pos.relative(this.pushDirection), this.pistonPos)) {
                cir.setReturnValue(false);
                return;
            }
        }
        for (BlockPos pos : this.toDestroy) {
            if (DungeonLockManager.isProtectedFrom(serverLevel, pos, this.pistonPos)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
