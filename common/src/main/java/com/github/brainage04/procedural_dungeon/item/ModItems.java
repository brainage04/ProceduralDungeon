package com.github.brainage04.procedural_dungeon.item;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Identifier RUSTED_KEY_ID = ProceduralDungeon.of("rusted_key");
    private static Supplier<? extends Item> rustedKey;

    private ModItems() {}

    public static Item createRustedKey() {
        return new RustedKeyItem(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, RUSTED_KEY_ID))
                .stacksTo(64));
    }

    public static void registerRustedKey(Supplier<? extends Item> supplier) {
        if (rustedKey != null) {
            throw new IllegalStateException("Rusted Key was registered twice");
        }
        rustedKey = Objects.requireNonNull(supplier);
    }

    public static Item rustedKey() {
        if (rustedKey == null) {
            throw new IllegalStateException("Rusted Key was accessed before loader registration");
        }
        return rustedKey.get();
    }

    public static void initialize() {}
}
