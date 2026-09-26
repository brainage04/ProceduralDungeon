package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.enchantment.DungeonKillEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
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
        }
    }
}
