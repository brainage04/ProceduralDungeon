package com.github.brainage04.procedural_dungeon.item;

import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    private static final Map<DungeonKeyType, Supplier<? extends Item>> KEYS = new EnumMap<>(DungeonKeyType.class);

    private ModItems() {}

    public static Item createKey(DungeonKeyType type) {
        return new DungeonKeyItem(type, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, type.itemId()))
                .rarity(switch (type) {
                    case RUSTED -> Rarity.COMMON;
                    case MINIBOSS -> Rarity.UNCOMMON;
                    case BOSS -> Rarity.RARE;
                })
                .stacksTo(64));
    }

    public static void registerKey(DungeonKeyType type, Supplier<? extends Item> supplier) {
        if (KEYS.putIfAbsent(type, Objects.requireNonNull(supplier)) != null) {
            throw new IllegalStateException(type.displayName() + " was registered twice");
        }
    }

    public static Item key(DungeonKeyType type) {
        Supplier<? extends Item> key = KEYS.get(type);
        if (key == null) {
            throw new IllegalStateException(type.displayName() + " was accessed before loader registration");
        }
        return key.get();
    }

    public static void initialize() {}
}
