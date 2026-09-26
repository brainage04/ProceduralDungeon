package com.github.brainage04.procedural_dungeon.enchantment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Applies the {@link DungeonEnchantments#NOVAS} when a living entity dies to a weapon carrying them.
 */
public final class DungeonKillEffects {
    private DungeonKillEffects() {}

    public static void onKill(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity killer)) {
            return;
        }
        ItemStack weapon = source.getWeaponItem();
        if (weapon == null || weapon.isEmpty()) {
            weapon = killer.getMainHandItem();
        }
        ItemEnchantments enchantments = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (DungeonEnchantments.Nova nova : DungeonEnchantments.NOVAS) {
            int enchantmentLevel = levelOf(enchantments, nova);
            if (enchantmentLevel > 0) {
                burst(level, victim, killer, nova, enchantmentLevel);
            }
        }
    }

    private static int levelOf(ItemEnchantments enchantments, DungeonEnchantments.Nova nova) {
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(nova.key())) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private static void burst(ServerLevel level, LivingEntity victim, LivingEntity killer, DungeonEnchantments.Nova nova, int enchantmentLevel) {
        double radius = DungeonEnchantments.Nova.radius(enchantmentLevel);
        int seconds = DungeonEnchantments.Nova.seconds(enchantmentLevel);
        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                victim.getBoundingBox().inflate(radius),
                entity -> entity != victim
                        && entity.isAlive()
                        && !(entity instanceof ArmorStand)
                        && entity.distanceToSqr(victim) <= radius * radius
                        && !DungeonAllies.spares(killer, entity)
        )) {
            if (nova.ignites()) {
                target.igniteForSeconds(seconds);
            }
            for (var effect : nova.effects()) {
                target.addEffect(new MobEffectInstance(effect, seconds * 20, enchantmentLevel - 1), killer);
            }
        }
        int particles = (int) (radius * radius * 6);
        level.sendParticles(nova.particle(), victim.getX(), victim.getY(0.5), victim.getZ(), particles, radius / 2, 0.5, radius / 2, 0.02);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), nova.sound(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
