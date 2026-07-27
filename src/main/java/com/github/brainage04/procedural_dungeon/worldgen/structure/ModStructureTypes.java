package com.github.brainage04.procedural_dungeon.worldgen.structure;

import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class ModStructureTypes {
    public static StructureType<StagedDungeonStructure> STAGED_DUNGEON;
    public static StructurePieceType STAGED_DUNGEON_MARKER;

    private ModStructureTypes() {}

    public static void registerAll(
            BiConsumer<String, StructureType<StagedDungeonStructure>> structureRegistrar,
            BiConsumer<String, StructurePieceType> pieceRegistrar
    ) {
        structureRegistrar.accept("staged_dungeon", () -> StagedDungeonStructure.CODEC);
        pieceRegistrar.accept("staged_dungeon_marker", StagedDungeonMarkerPiece::new);
    }

    public static void setStagedDungeon(StructureType<StagedDungeonStructure> type) {
        STAGED_DUNGEON = Objects.requireNonNull(type);
    }

    public static void setStagedDungeonMarker(StructurePieceType type) {
        STAGED_DUNGEON_MARKER = Objects.requireNonNull(type);
    }
}
