package com.github.brainage04.procedural_dungeon.enchantment;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Keeps {@link DungeonEnchantments#SOULBOUND} items through death: they are held back from the death drop, stay in the
 * dead player's inventory, and move into the same slots of the respawned player.
 */
public final class DungeonSoulbound {
    private DungeonSoulbound() {}

    public static boolean isSoulbound(ItemStack stack) {
        for (var enchantment : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).keySet()) {
            if (enchantment.is(DungeonEnchantments.SOULBOUND)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes every soulbound stack from {@code inventory}, keyed by slot, so the death drop skips them.
     */
    public static Map<Integer, ItemStack> holdBack(Inventory inventory) {
        Map<Integer, ItemStack> held = new HashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && isSoulbound(stack)) {
                held.put(slot, stack);
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        return held;
    }

    public static void putBack(Inventory inventory, Map<Integer, ItemStack> held) {
        held.forEach(inventory::setItem);
    }

    /**
     * Moves the soulbound stacks a dead player kept into the respawned player's empty slots.
     */
    public static void transfer(Inventory from, Inventory to) {
        for (int slot = 0; slot < from.getContainerSize(); slot++) {
            ItemStack stack = from.getItem(slot);
            if (!stack.isEmpty() && isSoulbound(stack) && to.getItem(slot).isEmpty()) {
                to.setItem(slot, stack);
                from.setItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
