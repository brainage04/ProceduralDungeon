package com.github.brainage04.procedural_dungeon.enchantment;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;

public final class DungeonDamageTypes {
    /**
     * The blast of a {@link DungeonEnchantments#VOLATILE} kill. It reuses the vanilla {@code explosion} death message
     * so vanilla clients can translate it.
     */
    public static final ResourceKey<DamageType> VOLATILE_BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, ProceduralDungeon.of("volatile_blast"));

    private DungeonDamageTypes() {}

    public static void bootstrap(BootstrapContext<DamageType> context) {
        context.register(VOLATILE_BLAST, new DamageType("explosion", DamageScaling.ALWAYS, 0.1F));
    }
}
