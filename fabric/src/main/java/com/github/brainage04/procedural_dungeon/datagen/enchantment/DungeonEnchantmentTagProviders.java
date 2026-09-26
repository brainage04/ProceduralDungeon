package com.github.brainage04.procedural_dungeon.datagen.enchantment;

import com.github.brainage04.procedural_dungeon.enchantment.DungeonDamageTypes;
import com.github.brainage04.procedural_dungeon.enchantment.DungeonEnchantments;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Tags the dungeon enchantments rely on. They are deliberately left out of the enchanting table, trading, and random
 * loot tags so dungeon reward chests stay their only source.
 */
public final class DungeonEnchantmentTagProviders {
    private DungeonEnchantmentTagProviders() {}

    public static class ItemTagProvider extends FabricTagsProvider<Item> {
        public ItemTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, Registries.ITEM, registries);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            // Vanilla tags are absent from the datagen lookup, so they are referenced as optional; they always exist at runtime.
            builder(DungeonEnchantments.AFFLICTION_ENCHANTABLE)
                    .addOptionalTag(ItemTags.WEAPON_ENCHANTABLE)
                    .addOptionalTag(ItemTags.BOW_ENCHANTABLE)
                    .addOptionalTag(ItemTags.CROSSBOW_ENCHANTABLE)
                    .addOptionalTag(ItemTags.TRIDENT_ENCHANTABLE);
        }
    }

    public static class EntityTypeTagProvider extends FabricTagsProvider<EntityType<?>> {
        public EntityTypeTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, Registries.ENTITY_TYPE, registries);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            builder(DungeonEnchantments.SENSITIVE_TO_BANE_OF_THE_DEEP).add(key(EntityTypes.WARDEN));
            builder(DungeonEnchantments.SENSITIVE_TO_BANE_OF_THE_NETHER).add(
                    key(EntityTypes.BLAZE),
                    key(EntityTypes.GHAST),
                    key(EntityTypes.MAGMA_CUBE),
                    key(EntityTypes.PIGLIN),
                    key(EntityTypes.PIGLIN_BRUTE),
                    key(EntityTypes.ZOMBIFIED_PIGLIN),
                    key(EntityTypes.HOGLIN),
                    key(EntityTypes.ZOGLIN),
                    key(EntityTypes.WITHER),
                    key(EntityTypes.WITHER_SKELETON)
            );
            builder(DungeonEnchantments.SENSITIVE_TO_BANE_OF_THE_END).add(
                    key(EntityTypes.ENDER_DRAGON),
                    key(EntityTypes.ENDERMAN),
                    key(EntityTypes.ENDERMITE),
                    key(EntityTypes.SHULKER)
            );
            builder(DungeonEnchantments.SENSITIVE_TO_BANE_OF_ILLAGERS)
                    .addOptionalTag(EntityTypeTags.RAIDERS)
                    .add(key(EntityTypes.VEX));
        }

        private static ResourceKey<EntityType<?>> key(EntityType<?> type) {
            return type.builtInRegistryHolder().key();
        }
    }

    public static class EnchantmentTagProvider extends FabricTagsProvider<Enchantment> {
        public EnchantmentTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, Registries.ENCHANTMENT, registries);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            builder(DungeonEnchantments.LOOT_DAMAGE_OPTIONS).add(
                    Enchantments.SHARPNESS,
                    Enchantments.SMITE,
                    Enchantments.BANE_OF_ARTHROPODS,
                    Enchantments.IMPALING,
                    Enchantments.DENSITY,
                    Enchantments.BREACH
            );
            var damageExclusive = builder(EnchantmentTags.DAMAGE_EXCLUSIVE).add(DungeonEnchantments.ANNIHILATION);
            DungeonEnchantments.BANES.forEach(bane -> damageExclusive.add(bane.key()));
            builder(DungeonEnchantments.DETONATION_EXCLUSIVE).add(DungeonEnchantments.VOLATILE, DungeonEnchantments.CATACLYSM);
        }
    }

    public static class DamageTypeTagProvider extends FabricTagsProvider<DamageType> {
        public DamageTypeTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, Registries.DAMAGE_TYPE, registries);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            builder(DungeonDamageTypes.SPARES_ALLIES).add(DungeonDamageTypes.DUNGEON_BLAST);
            builder(DamageTypeTags.IS_EXPLOSION).add(DungeonDamageTypes.DUNGEON_BLAST);
        }
    }
}
