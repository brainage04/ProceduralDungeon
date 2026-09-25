package com.github.brainage04.procedural_dungeon.reward;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.enchantment.DungeonEnchantments;
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
     * The relics a boss chest of {@code tier} can hold, one of which is picked per chest. Their base material is the
     * dungeon tier's own equipment and their bonuses grow with the tier.
     */
    public static List<Reward> bossRelics(DungeonTier tier, HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        int t = tier.tier;
        return List.of(
                relic("wardens_cleaver", "Warden's Cleaver", "Pried from the grip of a dungeon's warden.", tier.axe, t,
                        Map.of(enchantments.getOrThrow(Enchantments.SHARPNESS), 3 + t),
                        List.of(new Bonus(Attributes.ATTACK_DAMAGE, t, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.MAINHAND))),
                relic("aegis_of_the_depths", "Aegis of the Depths", "It has turned aside worse than you.", tier.chestplate, t,
                        Map.of(enchantments.getOrThrow(Enchantments.PROTECTION), 2 + t),
                        List.of(
                                new Bonus(Attributes.MAX_HEALTH, 2.0 * t, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST),
                                new Bonus(Attributes.KNOCKBACK_RESISTANCE, 0.05 * t, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.CHEST)
                        )),
                relic("stridewalkers", "Stridewalkers", "Every stair is a single step.", tier.boots, t,
                        Map.of(enchantments.getOrThrow(Enchantments.FEATHER_FALLING), 3 + t),
                        List.of(
                                new Bonus(Attributes.MOVEMENT_SPEED, 0.04 * t, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, EquipmentSlotGroup.FEET),
                                new Bonus(Attributes.STEP_HEIGHT, 0.6, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET)
                        )),
                relic("crown_of_the_fallen", "Crown of the Fallen", "Fortune favours whoever wears it next.", tier.helmet, t,
                        Map.of(
                                enchantments.getOrThrow(Enchantments.PROTECTION), 1 + t,
                                enchantments.getOrThrow(Enchantments.RESPIRATION), 3
                        ),
                        List.of(
                                new Bonus(Attributes.MAX_HEALTH, t, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.HEAD),
                                new Bonus(Attributes.LUCK, t, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.HEAD)
                        )),
                relic("stormstring", "Stormstring", "Every arrow carries the storm.", Items.BOW, t,
                        Map.of(
                                enchantments.getOrThrow(Enchantments.POWER), 3 + t,
                                enchantments.getOrThrow(DungeonEnchantments.STORMCALLER), Math.min(3, (t + 1) / 2)
                        ),
                        List.of()),
                phylactery()
        );
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

    private static Reward relic(
            String id,
            String name,
            String lore,
            Item item,
            int tier,
            Map<Holder.Reference<Enchantment>, Integer> enchantmentLevels,
            List<Bonus> bonuses
    ) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantmentLevels.forEach(enchantments::set);

        List<Reward.AttributeBonus> attributeBonuses = bonuses.stream()
                .map(bonus -> new Reward.AttributeBonus(
                        ProceduralDungeon.of("relic/%s/%s".formatted(id, attributeName(bonus.attribute()))),
                        bonus.attribute(),
                        bonus.amount(),
                        bonus.operation(),
                        bonus.slot()
                ))
                .toList();

        DataComponentMap components = named(name, lore, Rarity.EPIC)
                .set(DataComponents.LORE, new ItemLore(List.of(
                        lore(lore),
                        lore("Relic of a tier %d dungeon".formatted(tier)).copy().withStyle(ChatFormatting.DARK_PURPLE)
                )))
                .set(DataComponents.ENCHANTMENTS, enchantments.toImmutable())
                .set(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .build();
        return new Reward(item, components, attributeBonuses, 1);
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

    private record Bonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation, EquipmentSlotGroup slot) {}
}
