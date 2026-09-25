package com.github.brainage04.procedural_dungeon.lock;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Dungeon keys are vanilla trial keys carrying a name and a hidden {@value #KEY_TAG} tag, so vanilla clients can hold
 * them. The extra components also stop them from opening trial chamber vaults, which require an untouched key.
 */
public enum DungeonKeyType implements StringRepresentable {
    RUSTED("rusted_key", "Rusted Key", "Opens a locked dungeon chest", Items.TRIAL_KEY, Rarity.UNCOMMON),
    MINIBOSS("miniboss_key", "Miniboss Key", "Opens a miniboss room door", Items.TRIAL_KEY, Rarity.RARE),
    BOSS("boss_key", "Boss Key", "Opens the boss room door", Items.OMINOUS_TRIAL_KEY, Rarity.EPIC);

    public static final StringRepresentable.EnumCodec<DungeonKeyType> CODEC = StringRepresentable.fromEnum(DungeonKeyType::values);
    private static final String KEY_TAG = "procedural_dungeon_key";

    private final String name;
    private final String displayName;
    private final String description;
    private final Item item;
    private final Rarity rarity;

    DungeonKeyType(String name, String displayName, String description, Item item, Rarity rarity) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.item = item;
        this.rarity = rarity;
    }

    public String displayName() {
        return displayName;
    }

    public Item item() {
        return item;
    }

    /**
     * The components that turn a plain {@link #item()} into this key.
     */
    public DataComponentMap components() {
        return DataComponentMap.builder()
                .set(DataComponents.ITEM_NAME, Component.literal(displayName))
                .set(DataComponents.RARITY, rarity)
                .set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal(description).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))
                )))
                .set(DataComponents.CUSTOM_DATA, CustomData.of(identityTag()))
                .build();
    }

    public ItemStack createStack() {
        ItemStack stack = new ItemStack(item);
        stack.applyComponents(components());
        return stack;
    }

    public boolean matches(ItemStack stack) {
        if (!stack.is(item)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.matchedBy(identityTag());
    }

    private CompoundTag identityTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_TAG, name);
        return tag;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
