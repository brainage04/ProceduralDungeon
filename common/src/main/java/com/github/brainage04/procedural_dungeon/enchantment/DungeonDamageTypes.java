package com.github.brainage04.procedural_dungeon.enchantment;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;

public final class DungeonDamageTypes {
    /**
     * The blast of a {@link DungeonEnchantments#VOLATILE} or {@link DungeonEnchantments#CATACLYSM} kill. It reuses the
     * vanilla {@code explosion} death message so vanilla clients can translate it.
     */
    public static final ResourceKey<DamageType> DUNGEON_BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, ProceduralDungeon.of("dungeon_blast"));
    /**
     * Enchantment explosions of these types never touch their wielder or the wielder's allies; see {@link DungeonAllies}.
     */
    public static final TagKey<DamageType> SPARES_ALLIES = TagKey.create(Registries.DAMAGE_TYPE, ProceduralDungeon.of("spares_allies"));

    private DungeonDamageTypes() {}

    public static void bootstrap(BootstrapContext<DamageType> context) {
        context.register(DUNGEON_BLAST, new DamageType("explosion", DamageScaling.ALWAYS, 0.1F));
    }
}
