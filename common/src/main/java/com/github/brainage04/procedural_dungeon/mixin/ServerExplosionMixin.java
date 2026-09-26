package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.enchantment.DungeonAllies;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
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

    // NeoForge moves the vanilla body into an overload taking the affected blocks.
    @WrapOperation(
            method = {"hurtEntities()V", "hurtEntities(Ljava/util/List;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;ignoreExplosion(Lnet/minecraft/world/level/Explosion;)Z")
    )
    private boolean proceduralDungeon$spareAllies(Entity entity, Explosion explosion, Operation<Boolean> original) {
        return original.call(entity, explosion) || DungeonAllies.sparedByCurrentBlast(entity);
    }
}
