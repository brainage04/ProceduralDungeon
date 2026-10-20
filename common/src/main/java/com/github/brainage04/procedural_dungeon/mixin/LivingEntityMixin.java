package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.api.impl.DungeonEvents;
import com.github.brainage04.procedural_dungeon.enchantment.DungeonKillEffects;
import com.github.brainage04.procedural_dungeon.guardian.DungeonGuardian;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    // After NeoForge's cancellable death event, so cancelled deaths trigger nothing.
    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;handleKillingBlow()V"))
    private void proceduralDungeon$killEffects(DamageSource source, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity) (Object) this;
        if (victim.level() instanceof ServerLevel level) {
            DungeonKillEffects.onKill(level, victim, source);
            if (victim instanceof Mob mob) {
                DungeonGuardian.of(mob).ifPresent(guardian -> DungeonEvents.guardianKilled(level, mob,
                        victim.getKillCredit() instanceof ServerPlayer player ? player : null, guardian.boss()));
            }
        }
    }
}
