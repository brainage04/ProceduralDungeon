package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.guardian.DungeonGuardian;
import com.github.brainage04.procedural_dungeon.lock.DungeonBossKeys;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every way an entity leaves the world ends here: a destroyed Boss Key item gets reissued, and a guardian gone for
 * good releases its room's reward chests.
 */
@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void proceduralDungeon$onRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!reason.shouldDestroy() || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (entity instanceof ItemEntity item) {
            DungeonBossKeys.onItemEntityRemoved(item, reason);
        } else if (entity instanceof Mob mob && mob.entityTags().contains(DungeonGuardian.TAG)) {
            DungeonLockManager.onGuardianRemoved(level, mob);
        }
    }
}
