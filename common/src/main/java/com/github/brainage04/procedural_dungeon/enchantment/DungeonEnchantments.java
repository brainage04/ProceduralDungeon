package com.github.brainage04.procedural_dungeon.enchantment;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.predicates.DamageSourcePredicate;
import net.minecraft.advancements.predicates.DistancePredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.predicates.NbtPredicate;
import net.minecraft.advancements.predicates.TagPredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.predicates.entity.EntityTypePredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.ApplyMobEffect;
import net.minecraft.world.item.enchantment.effects.DamageEntity;
import net.minecraft.world.item.enchantment.effects.DamageImmunity;
import net.minecraft.world.item.enchantment.effects.ExplodeEffect;
import net.minecraft.world.item.enchantment.effects.PlaySoundEffect;
import net.minecraft.world.item.enchantment.effects.SpawnParticlesEffect;
import net.minecraft.world.item.enchantment.effects.SummonEntityEffect;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.DamageSourceCondition;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.EnchantmentLevelProvider;
import net.minecraft.world.phys.Vec3;

/**
 * Enchantments only found in dungeon reward chests. They are plain data-driven enchantments, so vanilla clients receive
 * them through registry sync; their names are literal text because clients have no translations for them. None of
 * them are in the enchanting-table, trading, or random-loot tags.
 */
public final class DungeonEnchantments {
    public static final TagKey<Item> AFFLICTION_ENCHANTABLE = TagKey.create(Registries.ITEM, ProceduralDungeon.of("enchantable/affliction"));
    public static final TagKey<EntityType<?>> SENSITIVE_TO_BANE_OF_THE_DEEP = entityTag("sensitive_to_bane_of_the_deep");
    public static final TagKey<EntityType<?>> SENSITIVE_TO_BANE_OF_THE_NETHER = entityTag("sensitive_to_bane_of_the_nether");
    public static final TagKey<EntityType<?>> SENSITIVE_TO_BANE_OF_THE_END = entityTag("sensitive_to_bane_of_the_end");
    public static final TagKey<EntityType<?>> SENSITIVE_TO_BANE_OF_ILLAGERS = entityTag("sensitive_to_bane_of_illagers");
    /**
     * The vanilla damage enchantments that enchant-with-levels loot may roll; unlike {@code #minecraft:exclusive_set/damage}
     * it leaves out the dungeon banes, so those stay reward-only.
     */
    public static final TagKey<Enchantment> LOOT_DAMAGE_OPTIONS = TagKey.create(Registries.ENCHANTMENT, ProceduralDungeon.of("loot_options/damage"));
    public static final TagKey<DamageType> VOLATILE_IMMUNE = TagKey.create(Registries.DAMAGE_TYPE, ProceduralDungeon.of("volatile_immune"));

    public static final List<Bane> BANES = List.of(
            new Bane(key("bane_of_the_deep"), "Bane of the Deep", Optional.of(SENSITIVE_TO_BANE_OF_THE_DEEP)),
            new Bane(key("bane_of_the_nether"), "Bane of the Nether", Optional.of(SENSITIVE_TO_BANE_OF_THE_NETHER)),
            new Bane(key("bane_of_the_end"), "Bane of the End", Optional.of(SENSITIVE_TO_BANE_OF_THE_END)),
            new Bane(key("bane_of_illagers"), "Bane of Illagers", Optional.of(SENSITIVE_TO_BANE_OF_ILLAGERS)),
            new Bane(key("duelist"), "Duelist", Optional.empty())
    );
    public static final ResourceKey<Enchantment> ANNIHILATION = key("annihilation");

    public static final List<Affliction> AFFLICTIONS = List.of(
            new Affliction(key("venom"), "Venom", MobEffects.POISON, 3.0F, 1.0F, true),
            new Affliction(key("withering"), "Withering", MobEffects.WITHER, 3.0F, 1.0F, true),
            new Affliction(key("crippling"), "Crippling", MobEffects.SLOWNESS, 2.0F, 1.0F, true),
            new Affliction(key("blinding"), "Blinding", MobEffects.BLINDNESS, 1.5F, 1.0F, false),
            new Affliction(key("eclipse"), "Eclipse", MobEffects.DARKNESS, 3.0F, 2.0F, false),
            new Affliction(key("famine"), "Famine", MobEffects.HUNGER, 5.0F, 3.0F, true),
            new Affliction(key("infestation"), "Infestation", MobEffects.INFESTED, 5.0F, 5.0F, false),
            new Affliction(key("updraft"), "Updraft", MobEffects.LEVITATION, 0.5F, 0.5F, true),
            new Affliction(key("drifting"), "Drifting", MobEffects.SLOW_FALLING, 3.0F, 2.0F, false),
            new Affliction(key("sapping"), "Sapping", MobEffects.MINING_FATIGUE, 5.0F, 3.0F, true),
            new Affliction(key("vertigo"), "Vertigo", MobEffects.NAUSEA, 4.0F, 2.0F, false),
            new Affliction(key("oozing"), "Oozing", MobEffects.OOZING, 10.0F, 5.0F, false)
    );

    public static final ResourceKey<Enchantment> STORMCALLER = key("stormcaller");
    public static final ResourceKey<Enchantment> VOLATILE = key("volatile");

    /**
     * Beyond this horizontal distance a summoned lightning bolt cannot reach the attacker.
     */
    private static final double SAFE_LIGHTNING_DISTANCE = 4.0;

    private DungeonEnchantments() {}

    public static List<ResourceKey<Enchantment>> all() {
        List<ResourceKey<Enchantment>> keys = new ArrayList<>();
        BANES.forEach(bane -> keys.add(bane.key()));
        keys.add(ANNIHILATION);
        AFFLICTIONS.forEach(affliction -> keys.add(affliction.key()));
        keys.add(STORMCALLER);
        keys.add(VOLATILE);
        return List.copyOf(keys);
    }

    public static void bootstrap(BootstrapContext<Enchantment> context) {
        HolderGetter<Item> items = context.lookup(Registries.ITEM);
        HolderGetter<Enchantment> enchantments = context.lookup(Registries.ENCHANTMENT);
        HolderGetter<EntityType<?>> entityTypes = context.lookup(Registries.ENTITY_TYPE);
        HolderGetter<DamageType> damageTypes = context.lookup(Registries.DAMAGE_TYPE);
        HolderSet<Item> weapons = items.getOrThrow(ItemTags.WEAPON_ENCHANTABLE);
        HolderSet<Item> meleeWeapons = items.getOrThrow(ItemTags.MELEE_WEAPON_ENCHANTABLE);
        HolderSet<Item> afflictionWeapons = items.getOrThrow(AFFLICTION_ENCHANTABLE);

        for (Bane bane : BANES) {
            EntityPredicate.Builder target = bane.sensitiveTo()
                    .map(tag -> EntityPredicate.Builder.entity().entityType(EntityTypePredicate.of(entityTypes, tag)))
                    .orElseGet(() -> EntityPredicate.Builder.entity().of(entityTypes, EntityTypes.PLAYER));
            register(context, bane.key(), bane.name(), Enchantment.enchantment(Enchantment.definition(
                            weapons, meleeWeapons, 1, 5,
                            Enchantment.dynamicCost(5, 8), Enchantment.dynamicCost(25, 8), 4, EquipmentSlotGroup.MAINHAND))
                    .exclusiveWith(enchantments.getOrThrow(EnchantmentTags.DAMAGE_EXCLUSIVE))
                    .withEffect(
                            EnchantmentEffectComponents.DAMAGE,
                            new AddValue(LevelBasedValue.perLevel(2.5F)),
                            LootItemEntityPropertyCondition.hasProperties(EntityTarget.THIS, target)
                    ));
        }

        // Smite's potency against every target: the rarest reward, only in boss chests.
        register(context, ANNIHILATION, "Annihilation", Enchantment.enchantment(Enchantment.definition(
                        weapons, meleeWeapons, 1, 5,
                        Enchantment.dynamicCost(20, 10), Enchantment.dynamicCost(50, 10), 8, EquipmentSlotGroup.MAINHAND))
                .exclusiveWith(enchantments.getOrThrow(EnchantmentTags.DAMAGE_EXCLUSIVE))
                .withEffect(EnchantmentEffectComponents.DAMAGE, new AddValue(LevelBasedValue.perLevel(2.5F))));

        for (Affliction affliction : AFFLICTIONS) {
            LevelBasedValue duration = LevelBasedValue.perLevel(affliction.baseSeconds(), affliction.secondsPerLevel());
            LevelBasedValue amplifier = affliction.amplifierScales() ? LevelBasedValue.perLevel(0.0F, 1.0F) : LevelBasedValue.constant(0.0F);
            register(context, affliction.key(), affliction.name(), Enchantment.enchantment(Enchantment.definition(
                            afflictionWeapons, afflictionWeapons, 1, 3,
                            Enchantment.dynamicCost(10, 10), Enchantment.dynamicCost(40, 10), 4, EquipmentSlotGroup.MAINHAND))
                    .withEffect(
                            EnchantmentEffectComponents.POST_ATTACK,
                            EnchantmentTarget.ATTACKER,
                            EnchantmentTarget.VICTIM,
                            new ApplyMobEffect(HolderSet.direct(affliction.effect()), duration, duration, amplifier, amplifier)
                    ));
        }

        LootItemCondition.Builder stormChance = LootItemRandomChanceCondition.randomChance(
                EnchantmentLevelProvider.forEnchantmentLevel(LevelBasedValue.perLevel(0.15F)));
        LootItemCondition.Builder attackerClear = LootItemEntityPropertyCondition.hasProperties(
                EntityTarget.ATTACKER,
                EntityPredicate.Builder.entity().distance(DistancePredicate.horizontal(MinMaxBounds.Doubles.atLeast(SAFE_LIGHTNING_DISTANCE)))
        );
        register(context, STORMCALLER, "Stormcaller", Enchantment.enchantment(Enchantment.definition(
                        afflictionWeapons, afflictionWeapons, 1, 3,
                        Enchantment.dynamicCost(15, 10), Enchantment.dynamicCost(45, 10), 8, EquipmentSlotGroup.MAINHAND))
                // From a distance the bolt is real; up close it would strike the attacker too, so it becomes a
                // targeted jolt with the same damage type.
                .withEffect(
                        EnchantmentEffectComponents.POST_ATTACK,
                        EnchantmentTarget.ATTACKER,
                        EnchantmentTarget.VICTIM,
                        AllOf.entityEffects(
                                new SummonEntityEffect(HolderSet.direct(EntityTypes.LIGHTNING_BOLT.builtInRegistryHolder()), false),
                                new PlaySoundEffect(List.of(SoundEvents.TRIDENT_THUNDER), ConstantFloat.of(5.0F), ConstantFloat.of(1.0F))
                        ),
                        AllOfCondition.allOf(stormChance, attackerClear)
                )
                .withEffect(
                        EnchantmentEffectComponents.POST_ATTACK,
                        EnchantmentTarget.ATTACKER,
                        EnchantmentTarget.VICTIM,
                        AllOf.entityEffects(
                                new DamageEntity(
                                        LevelBasedValue.perLevel(3.0F, 1.0F),
                                        LevelBasedValue.perLevel(5.0F, 1.0F),
                                        damageTypes.getOrThrow(DamageTypes.LIGHTNING_BOLT)
                                ),
                                new SpawnParticlesEffect(
                                        ParticleTypes.ELECTRIC_SPARK,
                                        SpawnParticlesEffect.inBoundingBox(),
                                        SpawnParticlesEffect.inBoundingBox(),
                                        SpawnParticlesEffect.fixedVelocity(ConstantFloat.of(0.5F)),
                                        SpawnParticlesEffect.fixedVelocity(ConstantFloat.of(0.5F)),
                                        ConstantFloat.of(1.0F)
                                ),
                                new PlaySoundEffect(List.of(SoundEvents.TRIDENT_THUNDER), ConstantFloat.of(0.6F), ConstantFloat.of(1.6F))
                        ),
                        AllOfCondition.allOf(stormChance, InvertedLootItemCondition.invert(attackerClear))
                ));

        CompoundTag dead = new CompoundTag();
        dead.putFloat("Health", 0.0F);
        register(context, VOLATILE, "Volatile", Enchantment.enchantment(Enchantment.definition(
                        afflictionWeapons, afflictionWeapons, 1, 3,
                        Enchantment.dynamicCost(15, 10), Enchantment.dynamicCost(45, 10), 8, EquipmentSlotGroup.MAINHAND))
                // Kills detonate the victim. Blocks are untouched, and the wielder is immune to the blast; other
                // players nearby are not.
                .withEffect(
                        EnchantmentEffectComponents.POST_ATTACK,
                        EnchantmentTarget.ATTACKER,
                        EnchantmentTarget.VICTIM,
                        new ExplodeEffect(
                                false,
                                Optional.of(damageTypes.getOrThrow(DungeonDamageTypes.VOLATILE_BLAST)),
                                Optional.empty(),
                                Optional.empty(),
                                new Vec3(0.0, 0.5, 0.0),
                                LevelBasedValue.perLevel(1.5F, 0.5F),
                                false,
                                ExplosionInteraction.NONE,
                                ParticleTypes.EXPLOSION,
                                ParticleTypes.EXPLOSION_EMITTER,
                                WeightedList.of(),
                                SoundEvents.GENERIC_EXPLODE
                        ),
                        LootItemEntityPropertyCondition.hasProperties(EntityTarget.THIS, EntityPredicate.Builder.entity().nbt(new NbtPredicate(dead)))
                )
                .withEffect(
                        EnchantmentEffectComponents.DAMAGE_IMMUNITY,
                        DamageImmunity.INSTANCE,
                        DamageSourceCondition.hasDamageSource(DamageSourcePredicate.Builder.damageType()
                                .tag(TagPredicate.is(VOLATILE_IMMUNE))
                                .tag(TagPredicate.isNot(DamageTypeTags.BYPASSES_INVULNERABILITY)))
                ));
    }

    private static void register(BootstrapContext<Enchantment> context, ResourceKey<Enchantment> key, String name, Enchantment.Builder builder) {
        Enchantment built = builder.build(key.identifier());
        context.register(key, new Enchantment(Component.literal(name), built.definition(), built.exclusiveSet(), built.effects()));
    }

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, ProceduralDungeon.of(name));
    }

    private static TagKey<EntityType<?>> entityTag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, ProceduralDungeon.of(name));
    }

    /**
     * Smite-strength bonus damage against {@code sensitiveTo}, or against players when empty.
     */
    public record Bane(ResourceKey<Enchantment> key, String name, Optional<TagKey<EntityType<?>>> sensitiveTo) {}

    /**
     * Applies {@code effect} on every hit for {@code baseSeconds + secondsPerLevel * (level - 1)}; the amplifier is
     * {@code level - 1} when {@code amplifierScales}, otherwise 0.
     */
    public record Affliction(
            ResourceKey<Enchantment> key,
            String name,
            Holder<MobEffect> effect,
            float baseSeconds,
            float secondsPerLevel,
            boolean amplifierScales
    ) {}
}
