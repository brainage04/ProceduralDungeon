package com.github.brainage04.procedural_dungeon.reward;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

/**
 * Relic Essence: the attribute bonuses of a relic (or of an item an essence was applied to), ground off on a
 * grindstone and carried to another item on an anvil. An essence of armour bonuses fits any armour piece; an essence of
 * held bonuses fits any weapon or tool. An item carries at most one set of dungeon bonuses at a time.
 *
 * <p>An essence is a vanilla echo shard: its bonuses live in custom data so they never apply while it is carried, and
 * its lore lists them for vanilla clients.
 */
public final class RelicEssence {
    public static final int APPLY_COST = 10;
    private static final String TAG = ProceduralDungeon.MOD_ID + "_essence";

    private RelicEssence() {}

    public enum Kind {
        ARMOR("Apply to any armour piece on an anvil"),
        HELD("Apply to any weapon or tool on an anvil");

        private final String hint;

        Kind(String hint) {
            this.hint = hint;
        }
    }

    /**
     * The dungeon bonuses on {@code stack}: modifiers in the mod's namespace, which only relics and essences add.
     */
    public static List<ItemAttributeModifiers.Entry> bonuses(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.modifier().id().getNamespace().equals(ProceduralDungeon.MOD_ID))
                .toList();
    }

    public static boolean hasBonuses(ItemStack stack) {
        return !bonuses(stack).isEmpty();
    }

    public static boolean isEssence(ItemStack stack) {
        return stack.is(Items.ECHO_SHARD) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(TAG);
    }

    /**
     * An essence holding {@code source}'s bonuses.
     */
    public static ItemStack extract(ItemStack source) {
        List<ItemAttributeModifiers.Entry> bonuses = bonuses(source);
        Kind kind = bonuses.stream().allMatch(entry -> isArmorGroup(entry.slot())) ? Kind.ARMOR : Kind.HELD;

        CompoundTag essence = new CompoundTag();
        essence.putString("kind", kind.name());
        essence.put("bonuses", ItemAttributeModifiers.CODEC.encodeStart(NbtOps.INSTANCE, new ItemAttributeModifiers(bonuses)).getOrThrow());
        CompoundTag data = new CompoundTag();
        data.put(TAG, essence);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(kind.hint).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
        bonuses.forEach(entry -> lore.add(describe(entry)));

        ItemStack stack = new ItemStack(Items.ECHO_SHARD);
        stack.set(DataComponents.ITEM_NAME, Component.literal("Relic Essence"));
        stack.set(DataComponents.RARITY, Rarity.EPIC);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    /**
     * {@code source} without its dungeon bonuses; its base stats stay.
     */
    public static ItemStack strip(ItemStack source) {
        ItemStack stripped = source.copy();
        List<ItemAttributeModifiers.Entry> kept = source.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .modifiers().stream()
                .filter(entry -> !entry.modifier().id().getNamespace().equals(ProceduralDungeon.MOD_ID))
                .toList();
        stripped.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(kept));
        return stripped;
    }

    /**
     * {@code target} with {@code essence}'s bonuses moved onto the slot it is worn or held in, or empty when the essence
     * does not fit it or it already carries dungeon bonuses.
     */
    public static Optional<ItemStack> applyTo(ItemStack target, ItemStack essence) {
        if (!isEssence(essence) || target.getCount() != 1 || isEssence(target) || hasBonuses(target)) {
            return Optional.empty();
        }
        CompoundTag data = essence.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty(TAG);
        Kind kind = Kind.valueOf(data.getStringOr("kind", Kind.HELD.name()));
        Optional<EquipmentSlotGroup> slot = targetSlot(target, kind);
        if (slot.isEmpty()) {
            return Optional.empty();
        }
        List<ItemAttributeModifiers.Entry> bonuses = data.get("bonuses") == null
                ? List.of()
                : ItemAttributeModifiers.CODEC.parse(NbtOps.INSTANCE, data.get("bonuses")).getOrThrow().modifiers();

        ItemAttributeModifiers modifiers = target.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (int i = 0; i < bonuses.size(); i++) {
            ItemAttributeModifiers.Entry bonus = bonuses.get(i);
            String attribute = bonus.attribute().unwrapKey().orElseThrow().identifier().getPath();
            // Ids are unique per slot, so the same essence bonus on two worn items never collides.
            AttributeModifier modifier = new AttributeModifier(
                    ProceduralDungeon.of("essence/%s/%d_%s".formatted(slot.get().getSerializedName(), i, attribute)),
                    bonus.modifier().amount(),
                    bonus.modifier().operation()
            );
            modifiers = modifiers.withModifierAdded(bonus.attribute(), modifier, slot.get());
        }
        ItemStack result = target.copy();
        result.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
        return Optional.of(result);
    }

    private static Optional<EquipmentSlotGroup> targetSlot(ItemStack target, Kind kind) {
        Equippable equippable = target.get(DataComponents.EQUIPPABLE);
        boolean armor = equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        return switch (kind) {
            case ARMOR -> armor ? Optional.of(EquipmentSlotGroup.bySlot(equippable.slot())) : Optional.empty();
            case HELD -> !armor && target.has(DataComponents.MAX_DAMAGE) ? Optional.of(EquipmentSlotGroup.MAINHAND) : Optional.empty();
        };
    }

    private static boolean isArmorGroup(EquipmentSlotGroup group) {
        return group == EquipmentSlotGroup.HEAD || group == EquipmentSlotGroup.CHEST || group == EquipmentSlotGroup.LEGS
                || group == EquipmentSlotGroup.FEET || group == EquipmentSlotGroup.ARMOR;
    }

    private static Component describe(ItemAttributeModifiers.Entry entry) {
        AttributeModifier modifier = entry.modifier();
        double amount = modifier.amount();
        double shown = modifier.operation() == AttributeModifier.Operation.ADD_VALUE ? amount : amount * 100.0;
        String key = "attribute.modifier.%s.%d".formatted(amount >= 0 ? "plus" : "take", modifier.operation().id());
        return Component.translatable(
                        key,
                        ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(Math.abs(shown)),
                        Component.translatable(entry.attribute().value().getDescriptionId())
                )
                .withStyle(style -> style.withItalic(false))
                .withStyle(entry.attribute().value().getStyle(amount > 0));
    }
}
