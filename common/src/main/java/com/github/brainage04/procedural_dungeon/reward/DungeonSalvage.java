package com.github.brainage04.procedural_dungeon.reward;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Grindstone salvage on top of the vanilla recipes:
 * <ul>
 *     <li>an enchanted item and a plain book make an enchanted book of the item's enchantments; the item keeps
 *     its curses and loses the rest, and the book costs one experience level per enchantment level (at most 30);</li>
 *     <li>an item carrying dungeon bonuses, alone, makes a {@link RelicEssence}; the item keeps everything else.</li>
 * </ul>
 * The inputs are never destroyed: taking the result strips the item in place.
 */
public final class DungeonSalvage {
    private static final int MAX_BOOK_COST = 30;

    private DungeonSalvage() {}

    public static Optional<Grind> grind(ItemStack first, ItemStack second) {
        if (first.isEmpty() != second.isEmpty()) {
            int slot = first.isEmpty() ? 1 : 0;
            ItemStack item = slot == 0 ? first : second;
            if (item.getCount() == 1 && RelicEssence.hasBonuses(item)) {
                return Optional.of(new Grind(RelicEssence.extract(item), 0, slot, -1));
            }
            return Optional.empty();
        }
        int bookSlot = first.is(Items.BOOK) ? 0 : second.is(Items.BOOK) ? 1 : -1;
        if (bookSlot < 0) {
            return Optional.empty();
        }
        int itemSlot = 1 - bookSlot;
        ItemStack item = itemSlot == 0 ? first : second;
        if (item.getCount() != 1 || item.is(Items.BOOK) || item.is(Items.ENCHANTED_BOOK)) {
            return Optional.empty();
        }
        ItemEnchantments.Mutable extracted = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        int levels = 0;
        for (var entry : item.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            if (!enchantment.is(EnchantmentTags.CURSE)) {
                extracted.set(enchantment, entry.getIntValue());
                levels += entry.getIntValue();
            }
        }
        if (levels == 0) {
            return Optional.empty();
        }
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, extracted.toImmutable());
        return Optional.of(new Grind(book, Math.min(MAX_BOOK_COST, levels), itemSlot, bookSlot));
    }

    /**
     * Replaces the grindstone's input and result slots with ones that also accept salvage inputs and settle salvage
     * results, deferring to the vanilla slots otherwise.
     */
    public static void wrapGrindstoneSlots(NonNullList<Slot> slots, Container inputs, ContainerLevelAccess access) {
        for (int index = 0; index < 2; index++) {
            slots.set(index, new InputSlot(slots.get(index)));
        }
        slots.set(2, new ResultSlot(slots.get(2), inputs, access));
    }

    /**
     * A salvage result costing {@code cost} levels. {@code itemSlot} is the item being stripped; {@code bookSlot} is the
     * consumed book, or -1 when the result is an essence.
     */
    public record Grind(ItemStack result, int cost, int itemSlot, int bookSlot) {
        public boolean affordable(Player player) {
            return player.hasInfiniteMaterials() || player.experienceLevel >= cost;
        }

        public void settle(Container inputs, Player player) {
            ItemStack item = inputs.getItem(itemSlot);
            if (bookSlot < 0) {
                inputs.setItem(itemSlot, RelicEssence.strip(item));
            } else {
                ItemStack stripped = item.copy();
                ItemEnchantments remaining = EnchantmentHelper.updateEnchantments(
                        stripped, enchantments -> enchantments.removeIf(enchantment -> !enchantment.is(EnchantmentTags.CURSE)));
                int repairCost = 0;
                for (int i = 0; i < remaining.size(); i++) {
                    repairCost = AnvilMenu.calculateIncreasedRepairCost(repairCost);
                }
                stripped.set(DataComponents.REPAIR_COST, repairCost);
                inputs.setItem(itemSlot, stripped);
                inputs.removeItem(bookSlot, 1);
                if (!player.hasInfiniteMaterials()) {
                    player.giveExperienceLevels(-cost);
                }
            }
        }
    }

    private static class InputSlot extends Slot {
        private final Slot vanilla;

        InputSlot(Slot vanilla) {
            super(vanilla.container, vanilla.getContainerSlot(), vanilla.x, vanilla.y);
            this.vanilla = vanilla;
            this.index = vanilla.index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return vanilla.mayPlace(stack) || stack.is(Items.BOOK) || RelicEssence.hasBonuses(stack);
        }
    }

    private static class ResultSlot extends Slot {
        private final Slot vanilla;
        private final Container inputs;
        private final ContainerLevelAccess access;

        ResultSlot(Slot vanilla, Container inputs, ContainerLevelAccess access) {
            super(vanilla.container, vanilla.getContainerSlot(), vanilla.x, vanilla.y);
            this.vanilla = vanilla;
            this.inputs = inputs;
            this.access = access;
            this.index = vanilla.index;
        }

        private Optional<Grind> current() {
            return grind(inputs.getItem(0), inputs.getItem(1));
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return current().map(grind -> grind.affordable(player)).orElseGet(() -> vanilla.mayPickup(player));
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            Optional<Grind> grind = current();
            if (grind.isEmpty()) {
                vanilla.onTake(player, carried);
                return;
            }
            grind.get().settle(inputs, player);
            access.execute((level, pos) -> level.levelEvent(1042, pos, 0));
        }
    }
}
