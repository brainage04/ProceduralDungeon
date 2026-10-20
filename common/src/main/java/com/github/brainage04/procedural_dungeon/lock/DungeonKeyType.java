package com.github.brainage04.procedural_dungeon.lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

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
    private static final String KEY_ID_TAG = "procedural_dungeon_key_id";
    private static final String DUNGEON_TAG = "procedural_dungeon_key_dungeon";

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

    /**
     * A key bound to one lock record: {@code id} is the record's current key id and {@code dungeon} the dimension that
     * holds the record, so the key can be traced back wherever it is destroyed.
     */
    public ItemStack createStack(UUID id, ResourceKey<Level> dungeon) {
        ItemStack stack = createStack();
        bind(stack, id, dungeon);
        return stack;
    }

    /**
     * Stamps {@code stack} with a key id and the dimension of its lock record.
     */
    public static void bind(ItemStack stack, UUID id, ResourceKey<Level> dungeon) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.store(KEY_ID_TAG, UUIDUtil.CODEC, id);
            tag.store(DUNGEON_TAG, Level.RESOURCE_KEY_CODEC, dungeon);
        });
    }

    public boolean matches(ItemStack stack) {
        if (!stack.is(item)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.matchedBy(identityTag());
    }

    /**
     * The id {@link #bind} stamped on a key, or empty for an unbound key or any other item.
     */
    public static Optional<UUID> keyId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? Optional.empty() : data.copyTag().read(KEY_ID_TAG, UUIDUtil.CODEC);
    }

    /**
     * The dimension whose lock record a bound key belongs to.
     */
    public static Optional<ResourceKey<Level>> keyDungeon(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? Optional.empty() : data.copyTag().read(DUNGEON_TAG, Level.RESOURCE_KEY_CODEC);
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
