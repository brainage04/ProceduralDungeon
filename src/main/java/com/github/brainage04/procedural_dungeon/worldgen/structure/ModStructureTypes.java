package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class ModStructureTypes {
    public static final StructureType<StagedDungeonStructure> STAGED_DUNGEON =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_TYPE,
                    ProceduralDungeon.of("staged_dungeon"),
                    () -> StagedDungeonStructure.CODEC
            );

    public static final StructurePieceType STAGED_DUNGEON_MARKER =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_PIECE,
                    ProceduralDungeon.of("staged_dungeon_marker"),
                    StagedDungeonMarkerPiece::new
            );

    private ModStructureTypes() {}

    public static void initialize() {
        // load class and run static init
    }
}
