package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerExplosion.class)
public class ServerExplosionMixin {
    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void proceduralDungeon$protectLockedBlocks(List<BlockPos> blocks, CallbackInfo ci) {
        ServerLevel level = ((ServerExplosion) (Object) this).level();
        blocks.removeIf(pos -> DungeonLockManager.isExplosionProtected(level, pos));
    }
}
