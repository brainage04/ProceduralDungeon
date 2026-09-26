package com.github.brainage04.procedural_dungeon.reward;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.enchantment.DungeonEnchantments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Boss-room rewards found nowhere else: named, unbreakable relics whose enchantments exceed vanilla maximums and whose
 * attribute bonuses stack on top of the base item's own. Everything is vanilla item components, so vanilla clients
 * render them. Attribute bonuses are appended to the base item's own modifiers when the loot rolls.
 */
public final class DungeonRelics {
    private DungeonRelics() {}

    /**
     * Every relic a boss chest of {@code tier} can hold, one of which is picked per chest. Their base material is the
     * dungeon tier's own equipment and their bonuses grow with the tier. Each relic also rolls one random affix from
     * its pool; every relic is equally likely whatever the size of its pool.
     */
    public static List<Reward> bossRelics(DungeonTier tier, HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        int t = tier.tier;
        List<List<Reward>> relics = List.of(
                relic("wardens_cleaver", "Warden's Cleaver", "Pried from the grip of a dungeon's warden.", tier.axe, t,
                        Map.of(enchantments.getOrThrow(Enchantments.SHARPNESS), 3 + t),
                        List.of(new Bonus(Attributes.ATTACK_DAMAGE, t, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.MAINHAND)),
                        COMBAT_AFFIXES, EquipmentSlotGroup.MAINHAND),
                relic("aegis_of_the_depths", "Aegis of the Depths", "It has turned aside worse than you.", tier.chestplate, t,
                        Map.of(enchantments.getOrThrow(Enchantments.PROTECTION), 2 + t),
                        List.of(
                                new Bonus(Attributes.MAX_HEALTH, 2.0 * t, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST),
                                new Bonus(Attributes.KNOCKBACK_RESISTANCE, 0.05 * t, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST)
                        ),
                        ARMOR_AFFIXES, EquipmentSlotGroup.CHEST),
                relic("stridewalkers", "Stridewalkers", "Every stair is a single step.", tier.boots, t,
                        Map.of(enchantments.getOrThrow(Enchantments.FEATHER_FALLING), 3 + t),
                        List.of(
                                new Bonus(Attributes.MOVEMENT_SPEED, 0.04 * t, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, EquipmentSlotGroup.FEET),
                                new Bonus(Attributes.STEP_HEIGHT, 0.6, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET)
                        ),
                        ARMOR_AFFIXES, EquipmentSlotGroup.FEET),
                relic("crown_of_the_fallen", "Crown of the Fallen", "Fortune favours whoever wears it next.", tier.helmet, t,
                        Map.of(
                                enchantments.getOrThrow(Enchantments.PROTECTION), 1 + t,
                                enchantments.getOrThrow(Enchantments.RESPIRATION), 3
                        ),
                        List.of(
                                new Bonus(Attributes.MAX_HEALTH, t, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.HEAD),
                                new Bonus(Attributes.LUCK, t, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.HEAD)
                        ),
                        ARMOR_AFFIXES, EquipmentSlotGroup.HEAD),
                relic("delvers_pick", "Delver's Pick", "It remembers every seam it ever opened.", tier.pickaxe, t,
                        Map.of(
                                enchantments.getOrThrow(Enchantments.EFFICIENCY), 4 + t,
                                enchantments.getOrThrow(Enchantments.FORTUNE), 3
                        ),
                        List.of(new Bonus(Attributes.BLOCK_BREAK_SPEED, 0.1 * t, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, EquipmentSlotGroup.MAINHAND)),
                        MINING_AFFIXES, EquipmentSlotGroup.MAINHAND),
                relic("stormstring", "Stormstring", "Every arrow carries the storm.", Items.BOW, t,
                        Map.of(
                                enchantments.getOrThrow(Enchantments.POWER), 3 + t,
                                enchantments.getOrThrow(DungeonEnchantments.STORMCALLER), Math.min(3, (t + 1) / 2)
                        ),
                        List.of(),
                        MOBILITY_AFFIXES, EquipmentSlotGroup.MAINHAND),
                List.of(phylactery())
        );

        int weightPerRelic = relics.stream().mapToInt(List::size).reduce(1, DungeonRelics::lcm);
        List<Reward> weighted = new ArrayList<>();
        for (List<Reward> variants : relics) {
            for (Reward variant : variants) {
                weighted.add(new Reward(variant.item(), variant.components(), variant.bonuses(), weightPerRelic / variants.size()));
            }
        }
        return List.copyOf(weighted);
    }

    /**
     * A guaranteed boss-room food: a golden carrot that feeds like a feast and grants combat buffs that scale with tier.
     */
    public static Reward victorsFeast(DungeonTier tier) {
        int seconds = 30 + 15 * tier.tier;
        Consumable consumable = Consumable.builder()
                .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                        new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1),
                        new MobEffectInstance(MobEffects.STRENGTH, 20 * seconds, 0),
                        new MobEffectInstance(MobEffects.RESISTANCE, 20 * seconds, 0),
                        new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * seconds, 0)
                )))
                .build();
        DataComponentMap components = named("Victor's Feast", "Eaten only by those who beat the dungeon.", Rarity.EPIC)
                .set(DataComponents.FOOD, new FoodProperties(10, 20.0F, true))
                .set(DataComponents.CONSUMABLE, consumable)
                .build();
        return new Reward(Items.GOLDEN_CARROT, components);
    }

    private static Reward phylactery() {
        DataComponentMap components = named("Phylactery", "Hold it, and death lets go of you once.", Rarity.EPIC)
                .set(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING)
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .build();
        return new Reward(Items.HEART_OF_THE_SEA, components);
    }

    /**
     * One variant of the relic per affix in {@code affixes} that does not repeat a signature bonus.
     */
    private static List<Reward> relic(
            String id,
            String name,
            String lore,
            Item item,
            int tier,
            Map<Holder.Reference<Enchantment>, Integer> enchantmentLevels,
            List<Bonus> bonuses,
            List<Affix> affixes,
            EquipmentSlotGroup slot
    ) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantmentLevels.forEach(enchantments::set);

        DataComponentMap components = named(name, lore, Rarity.EPIC)
                .set(DataComponents.LORE, new ItemLore(List.of(
                        lore(lore),
                        lore("Relic of a tier %d dungeon".formatted(tier)).copy().withStyle(ChatFormatting.DARK_PURPLE)
                )))
                .set(DataComponents.ENCHANTMENTS, enchantments.toImmutable())
                .set(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .build();

        List<Reward.AttributeBonus> signature = bonuses.stream()
                .map(bonus -> bonus.toReward("relic/%s/%s".formatted(id, attributeName(bonus.attribute()))))
                .toList();
        return affixes.stream()
                .filter(affix -> bonuses.stream().noneMatch(bonus -> bonus.attribute().equals(affix.attribute())))
                .map(affix -> {
                    List<Reward.AttributeBonus> all = new ArrayList<>(signature);
                    all.add(affix.at(tier, slot).toReward("relic/%s/affix/%s".formatted(id, attributeName(affix.attribute()))));
                    return new Reward(item, components, List.copyOf(all), 1);
                })
                .toList();
    }

    private static int lcm(int a, int b) {
        int x = a;
        int y = b;
        while (y != 0) {
            int r = x % y;
            x = y;
            y = r;
        }
        return a / x * b;
    }

    private static DataComponentMap.Builder named(String name, String lore, Rarity rarity) {
        return DataComponentMap.builder()
                .set(DataComponents.ITEM_NAME, Component.literal(name))
                .set(DataComponents.RARITY, rarity)
                .set(DataComponents.LORE, new ItemLore(List.of(lore(lore))));
    }

    private static Component lore(String text) {
        return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY));
    }

    private static String attributeName(Holder<Attribute> attribute) {
        return attribute.unwrapKey().map(ResourceKey::identifier).orElseThrow().getPath();
    }

    private record Bonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation, EquipmentSlotGroup slot) {
        Reward.AttributeBonus toReward(String path) {
            return new Reward.AttributeBonus(ProceduralDungeon.of(path), attribute, amount, operation, slot);
        }
    }

    /**
     * A random relic bonus worth {@code flat + perTier * tier}.
     */
    private record Affix(Holder<Attribute> attribute, double flat, double perTier, AttributeModifier.Operation operation) {
        Bonus at(int tier, EquipmentSlotGroup slot) {
            return new Bonus(attribute, flat + perTier * tier, operation, slot);
        }
    }

    private static Affix add(Holder<Attribute> attribute, double perTier) {
        return new Affix(attribute, 0.0, perTier, AttributeModifier.Operation.ADD_VALUE);
    }

    private static Affix scale(Holder<Attribute> attribute, double perTier) {
        return new Affix(attribute, 0.0, perTier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static final List<Affix> ARMOR_AFFIXES = List.of(
            add(Attributes.MAX_HEALTH, 1.0),
            add(Attributes.MAX_ABSORPTION, 1.0),
            add(Attributes.ARMOR, 0.5),
            add(Attributes.ARMOR_TOUGHNESS, 0.5),
            add(Attributes.KNOCKBACK_RESISTANCE, 0.04),
            add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 0.1),
            scale(Attributes.MOVEMENT_SPEED, 0.02),
            scale(Attributes.SNEAKING_SPEED, 0.1),
            scale(Attributes.JUMP_STRENGTH, 0.03),
            new Affix(Attributes.STEP_HEIGHT, 0.5, 0.0, AttributeModifier.Operation.ADD_VALUE),
            add(Attributes.SAFE_FALL_DISTANCE, 1.0),
            scale(Attributes.FALL_DAMAGE_MULTIPLIER, -0.08),
            add(Attributes.WATER_MOVEMENT_EFFICIENCY, 0.1),
            add(Attributes.OXYGEN_BONUS, 1.0),
            scale(Attributes.BURNING_TIME, -0.1),
            add(Attributes.LUCK, 0.5)
    );
    private static final List<Affix> COMBAT_AFFIXES = List.of(
            add(Attributes.ATTACK_DAMAGE, 0.5),
            add(Attributes.ATTACK_SPEED, 0.1),
            add(Attributes.ATTACK_KNOCKBACK, 0.2),
            add(Attributes.SWEEPING_DAMAGE_RATIO, 0.1),
            add(Attributes.ENTITY_INTERACTION_RANGE, 0.25)
    );
    private static final List<Affix> MINING_AFFIXES = List.of(
            add(Attributes.BLOCK_INTERACTION_RANGE, 0.5),
            add(Attributes.MINING_EFFICIENCY, 2.0),
            scale(Attributes.BLOCK_BREAK_SPEED, 0.1),
            add(Attributes.SUBMERGED_MINING_SPEED, 0.16)
    );
    private static final List<Affix> MOBILITY_AFFIXES = List.of(
            scale(Attributes.MOVEMENT_SPEED, 0.02),
            scale(Attributes.SNEAKING_SPEED, 0.1),
            scale(Attributes.JUMP_STRENGTH, 0.03),
            add(Attributes.SAFE_FALL_DISTANCE, 1.0)
    );
}
