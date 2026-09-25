package com.github.brainage04.procedural_dungeon.item;

import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class DungeonKeyItem extends Item {
    private final DungeonKeyType type;

    public DungeonKeyItem(DungeonKeyType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(type.displayName());
    }
}
