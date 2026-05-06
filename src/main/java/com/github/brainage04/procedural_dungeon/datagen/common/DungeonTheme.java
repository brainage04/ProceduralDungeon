package com.github.brainage04.procedural_dungeon.datagen.common;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.core.ProceduralDungeonGenerator;
import com.github.brainage04.procedural_dungeon.util.StringUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public enum DungeonTheme implements StringRepresentable {
    COBBLESTONE(Level.OVERWORLD, ProceduralDungeonGenerator.COBBLESTONE),
    DEEPSLATE(Level.OVERWORLD, ProceduralDungeonGenerator.DEEPSLATE),
    SCULK(Level.OVERWORLD, ProceduralDungeonGenerator.SCULK),
    NETHER_WASTES(Level.NETHER, ProceduralDungeonGenerator.NETHER_WASTES),
    CRIMSON_FOREST(Level.NETHER, ProceduralDungeonGenerator.CRIMSON_FOREST),
    WARPED_FOREST(Level.NETHER, ProceduralDungeonGenerator.WARPED_FOREST),
    BASALT_DELTAS(Level.NETHER, ProceduralDungeonGenerator.BASALT_DELTAS),
    SOUL_SAND_VALLEY(Level.NETHER, ProceduralDungeonGenerator.SOUL_SAND_VALLEY),
    NETHER_FORTRESS(Level.NETHER, ProceduralDungeonGenerator.NETHER_FORTRESS),
    BASTION(Level.NETHER, ProceduralDungeonGenerator.BASTION),
    END_STONE(Level.END, ProceduralDungeonGenerator.END_STONE),
    END_CITY(Level.END, ProceduralDungeonGenerator.END_CITY);

    public final ResourceKey<Level> dimension;
    public final StructureProcessorList baseProcessorList;

    DungeonTheme(ResourceKey<Level> dimension, StructureProcessorList baseProcessorList) {
        this.dimension = dimension;
        this.baseProcessorList = baseProcessorList;
    }

    @Override
    public String getSerializedName() {
        return this.toString().toLowerCase();
    }

    public Identifier getId() {
        return Identifier.fromNamespaceAndPath(ProceduralDungeon.MOD_ID, this.getSerializedName());
    }

    public Component getName() {
        return Component.literal(StringUtils.snakeCaseToHumanReadable(this.getSerializedName()));
    }
}
