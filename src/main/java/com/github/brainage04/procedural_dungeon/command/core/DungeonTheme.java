package com.github.brainage04.procedural_dungeon.command.core;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.util.StringUtils;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

public enum DungeonTheme implements StringIdentifiable {
    OVERWORLD,
    NETHER,
    NETHER_FORTRESS,
    BASTION,
    THE_END,
    DEEP_DARK;

    @Override
    public String asString() {
        return this.toString().toLowerCase();
    }

    public Identifier getId() {
        return Identifier.of(ProceduralDungeon.MOD_ID, this.asString());
    }

    public Text getName() {
        return Text.literal(StringUtils.snakeCaseToHumanReadable(this.asString()));
    }
}
