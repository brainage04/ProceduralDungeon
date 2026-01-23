package com.github.brainage04.procedural_dungeon.datagen.common;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.core.ProceduralDungeonProvider;
import com.github.brainage04.procedural_dungeon.util.StringUtils;
import net.minecraft.registry.RegistryKey;
import net.minecraft.structure.processor.StructureProcessorList;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.World;

public enum DungeonTheme implements StringIdentifiable {
    COBBLESTONE(World.OVERWORLD, ProceduralDungeonProvider.COBBLESTONE),
    DEEPSLATE(World.OVERWORLD, ProceduralDungeonProvider.DEEPSLATE),
    SCULK(World.OVERWORLD, ProceduralDungeonProvider.SCULK),
    NETHER_WASTES(World.NETHER, ProceduralDungeonProvider.NETHER_WASTES),
    CRIMSON_FOREST(World.NETHER, ProceduralDungeonProvider.CRIMSON_FOREST),
    WARPED_FOREST(World.NETHER, ProceduralDungeonProvider.WARPED_FOREST),
    BASALT_DELTAS(World.NETHER, ProceduralDungeonProvider.BASALT_DELTAS),
    SOUL_SAND_VALLEY(World.NETHER, ProceduralDungeonProvider.SOUL_SAND_VALLEY),
    NETHER_FORTRESS(World.NETHER, ProceduralDungeonProvider.NETHER_FORTRESS),
    BASTION(World.NETHER, ProceduralDungeonProvider.BASTION),
    END_STONE(World.END, ProceduralDungeonProvider.END_STONE),
    END_CITY(World.END, ProceduralDungeonProvider.END_CITY);

    public final RegistryKey<World> dimension;
    public final StructureProcessorList baseProcessorList;

    DungeonTheme(RegistryKey<World> dimension, StructureProcessorList baseProcessorList) {
        this.dimension = dimension;
        this.baseProcessorList = baseProcessorList;
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
