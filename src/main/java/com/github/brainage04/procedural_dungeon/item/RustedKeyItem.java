package com.github.brainage04.procedural_dungeon.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RustedKeyItem extends Item {
    public RustedKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Rusted Key");
    }
}
