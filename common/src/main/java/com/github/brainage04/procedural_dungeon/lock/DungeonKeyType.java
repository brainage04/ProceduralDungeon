package com.github.brainage04.procedural_dungeon.lock;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

public enum DungeonKeyType implements StringRepresentable {
    RUSTED("rusted_key", "Rusted Key"),
    MINIBOSS("miniboss_key", "Miniboss Key"),
    BOSS("boss_key", "Boss Key");

    public static final Codec<DungeonKeyType> CODEC = StringRepresentable.fromEnum(DungeonKeyType::values);

    private final String name;
    private final String displayName;

    DungeonKeyType(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    public Identifier itemId() {
        return ProceduralDungeon.of(name);
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
