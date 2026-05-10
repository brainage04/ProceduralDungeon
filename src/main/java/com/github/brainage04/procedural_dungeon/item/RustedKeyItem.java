package com.github.brainage04.procedural_dungeon.item;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RustedKeyItem extends SimplePolymerItem {
    public RustedKeyItem(Properties properties) {
        super(properties, Items.TRIPWIRE_HOOK);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Rusted Key");
    }
}
