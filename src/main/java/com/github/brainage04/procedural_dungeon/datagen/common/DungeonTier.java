package com.github.brainage04.procedural_dungeon.datagen.common;

import com.github.brainage04.procedural_dungeon.datagen.core.ProceduralDungeonProvider;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.structure.processor.StructureProcessorList;

public enum DungeonTier {
    TIER_1(1, 8, 32, 16,
            new Item[]{Items.LEATHER, Items.OAK_PLANKS},
            Items.LEATHER_HELMET,
            Items.LEATHER_CHESTPLATE,
            Items.LEATHER_LEGGINGS,
            Items.LEATHER_BOOTS,
            Items.WOODEN_SWORD,
            Items.WOODEN_AXE,
            Items.WOODEN_SHOVEL,
            Items.WOODEN_PICKAXE,
            Items.WOODEN_HOE,
            1, 5, 10),
    TIER_2(2, 10, 40, 20,
            new Item[]{Items.COPPER_INGOT},
            Items.COPPER_HELMET,
            Items.COPPER_CHESTPLATE,
            Items.COPPER_LEGGINGS,
            Items.COPPER_BOOTS,
            Items.COPPER_SWORD,
            Items.COPPER_AXE,
            Items.COPPER_SHOVEL,
            Items.COPPER_PICKAXE,
            Items.COPPER_HOE,
            2, 4, 15),
    TIER_3(3, 12, 48, 24,
            new Item[]{Items.IRON_INGOT},
            Items.IRON_HELMET,
            Items.IRON_CHESTPLATE,
            Items.IRON_LEGGINGS,
            Items.IRON_BOOTS,
            Items.IRON_SWORD,
            Items.IRON_AXE,
            Items.IRON_SHOVEL,
            Items.IRON_PICKAXE,
            Items.IRON_HOE,
            3, 3, 20),
    TIER_4(4, 14, 56, 28,
            new Item[]{Items.DIAMOND},
            Items.DIAMOND_HELMET,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS,
            Items.DIAMOND_SWORD,
            Items.DIAMOND_AXE,
            Items.DIAMOND_SHOVEL,
            Items.DIAMOND_PICKAXE,
            Items.DIAMOND_HOE,
            4, 2, 25),
    TIER_5(5, 16, 64, 32,
            new Item[]{Items.NETHERITE_SCRAP, Items.GOLD_INGOT},
            Items.NETHERITE_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS,
            Items.NETHERITE_SWORD,
            Items.NETHERITE_AXE,
            Items.NETHERITE_SHOVEL,
            Items.NETHERITE_PICKAXE,
            Items.NETHERITE_HOE,
            5, 1, 30);

    public final int tier;
    public final int size;
    public final int spacing;
    public final int separation;
    public final Item[] resourceItems;
    public final Item helmet;
    public final Item chestplate;
    public final Item leggings;
    public final Item boots;
    public final Item sword;
    public final Item axe;
    public final Item shovel;
    public final Item pickaxe;
    public final Item hoe;
    public final int goodRolls;
    public final int badRolls;
    public final int levels;

    DungeonTier(int tier, int size, int spacing, int separation, Item[] resourceItems, Item helmet, Item chestplate, Item leggings, Item boots, Item sword, Item axe, Item shovel, Item pickaxe, Item hoe, int goodRolls, int badRolls, int levels) {
        this.tier = tier;
        this.size = size;
        this.spacing = spacing;
        this.separation = separation;
        this.resourceItems = resourceItems;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.sword = sword;
        this.axe = axe;
        this.shovel = shovel;
        this.pickaxe = pickaxe;
        this.hoe = hoe;
        this.goodRolls = goodRolls;
        this.badRolls = badRolls;
        this.levels = levels;
    }

    public StructureProcessorList getBaseProcessorList() {
        return switch (this) {
            case TIER_1 -> ProceduralDungeonProvider.CHESTS_TIER_1;
            case TIER_2 -> ProceduralDungeonProvider.CHESTS_TIER_2;
            case TIER_3 -> ProceduralDungeonProvider.CHESTS_TIER_3;
            case TIER_4 -> ProceduralDungeonProvider.CHESTS_TIER_4;
            case TIER_5 -> ProceduralDungeonProvider.CHESTS_TIER_5;
        };
    }
}