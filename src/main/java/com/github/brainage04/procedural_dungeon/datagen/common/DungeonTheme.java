package com.github.brainage04.procedural_dungeon.datagen.common;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.core.ProceduralDungeonGenerator;
import com.github.brainage04.procedural_dungeon.util.StringUtils;
import net.minecraft.registry.RegistryKey;
import net.minecraft.structure.processor.StructureProcessorList;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.World;

public enum DungeonTheme implements StringIdentifiable {
    COBBLESTONE(World.OVERWORLD, ProceduralDungeonGenerator.COBBLESTONE),
    DEEPSLATE(World.OVERWORLD, ProceduralDungeonGenerator.DEEPSLATE),
    SCULK(World.OVERWORLD, ProceduralDungeonGenerator.SCULK),
    NETHER_WASTES(World.NETHER, ProceduralDungeonGenerator.NETHER_WASTES),
    CRIMSON_FOREST(World.NETHER, ProceduralDungeonGenerator.CRIMSON_FOREST),
    WARPED_FOREST(World.NETHER, ProceduralDungeonGenerator.WARPED_FOREST),
    BASALT_DELTAS(World.NETHER, ProceduralDungeonGenerator.BASALT_DELTAS),
    SOUL_SAND_VALLEY(World.NETHER, ProceduralDungeonGenerator.SOUL_SAND_VALLEY),
    NETHER_FORTRESS(World.NETHER, ProceduralDungeonGenerator.NETHER_FORTRESS),
    BASTION(World.NETHER, ProceduralDungeonGenerator.BASTION),
    END_STONE(World.END, ProceduralDungeonGenerator.END_STONE),
    END_CITY(World.END, ProceduralDungeonGenerator.END_CITY);

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
