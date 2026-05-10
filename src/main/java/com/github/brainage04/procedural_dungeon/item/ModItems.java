package com.github.brainage04.procedural_dungeon.item;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Identifier RUSTED_KEY_ID = ProceduralDungeon.of("rusted_key");
    public static final Item RUSTED_KEY = register(
            RUSTED_KEY_ID,
            new RustedKeyItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, RUSTED_KEY_ID))
                    .stacksTo(64))
    );

    private ModItems() {}

    public static void initialize() {}

    private static Item register(Identifier id, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }
}
