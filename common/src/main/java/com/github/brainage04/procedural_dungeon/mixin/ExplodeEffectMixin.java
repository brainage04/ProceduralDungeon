package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.enchantment.DungeonAllies;
import com.github.brainage04.procedural_dungeon.enchantment.DungeonDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.ExplodeEffect;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks which entity wields a dungeon explosion enchantment while its blast resolves, so the blast can spare that
 * entity's allies; the effect's own damage source does not carry the wielder.
 */
@Mixin(ExplodeEffect.class)
public class ExplodeEffectMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void proceduralDungeon$markOwner(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position, CallbackInfo ci) {
        boolean sparesAllies = ((ExplodeEffect) (Object) this).damageType()
                .map(type -> type.is(DungeonDamageTypes.SPARES_ALLIES))
                .orElse(false);
        if (sparesAllies) {
            DungeonAllies.beginBlast(item.owner());
        }
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void proceduralDungeon$clearOwner(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position, CallbackInfo ci) {
        DungeonAllies.endBlast();
    }
}
