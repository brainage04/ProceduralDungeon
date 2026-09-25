package com.github.brainage04.procedural_dungeon.reward;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;

/**
 * An {@code item} carrying the {@code components} that make it a dungeon reward, drawn with {@code weight} from its
 * pool. {@code bonuses} are added on top of the item's own attribute modifiers when the loot is rolled.
 */
public record Reward(Item item, DataComponentMap components, List<AttributeBonus> bonuses, int weight) {
    public Reward(Item item, DataComponentMap components) {
        this(item, components, List.of(), 1);
    }

    public record AttributeBonus(Identifier id, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation, EquipmentSlotGroup slot) {}
}
