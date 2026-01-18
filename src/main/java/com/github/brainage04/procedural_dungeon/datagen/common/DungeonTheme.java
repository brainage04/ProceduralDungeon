package com.github.brainage04.procedural_dungeon.datagen.common;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.util.StringUtils;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.World;

public enum DungeonTheme implements StringIdentifiable {
    COBBLESTONE(World.OVERWORLD),
    DEEPSLATE(World.OVERWORLD),
    SCULK(World.OVERWORLD),

    NETHER_WASTES(World.NETHER),
    CRIMSON_FOREST(World.NETHER),
    WARPED_FOREST(World.NETHER),
    BASALT_DELTAS(World.NETHER),
    SOUL_SAND_VALLEY(World.NETHER),
    NETHER_FORTRESS(World.NETHER),
    BASTION(World.NETHER),

    END_STONE(World.END),
    END_CITY(World.END);

    public final RegistryKey<World> dimension;

    DungeonTheme(RegistryKey<World> dimension) {
        this.dimension = dimension;
    }

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
