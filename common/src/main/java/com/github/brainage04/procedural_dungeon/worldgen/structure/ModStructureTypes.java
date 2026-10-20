package com.github.brainage04.procedural_dungeon.worldgen.structure;

import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class ModStructureTypes {
    public static final String STAGED_DUNGEON_MARKER_NAME = "staged_dungeon_marker";
    public static final String STAGED_DUNGEON_BOUNDS_NAME = "staged_dungeon_bounds";

    public static StructureType<StagedDungeonStructure> STAGED_DUNGEON;
    public static StructurePieceType STAGED_DUNGEON_MARKER;
    public static StructurePieceType STAGED_DUNGEON_BOUNDS;

    private ModStructureTypes() {}

    /**
     * Registers the structure type and both piece types; {@code pieceRegistrar} returns the registered piece type.
     */
    public static void registerAll(
            BiConsumer<String, StructureType<StagedDungeonStructure>> structureRegistrar,
            PieceRegistrar pieceRegistrar
    ) {
        structureRegistrar.accept("staged_dungeon", () -> StagedDungeonStructure.CODEC);
        setStagedDungeonMarker(pieceRegistrar.register(STAGED_DUNGEON_MARKER_NAME, StagedDungeonMarkerPiece::new));
        setStagedDungeonBounds(pieceRegistrar.register(STAGED_DUNGEON_BOUNDS_NAME, StagedDungeonBoundsPiece::new));
    }

    public static void setStagedDungeon(StructureType<StagedDungeonStructure> type) {
        STAGED_DUNGEON = Objects.requireNonNull(type);
    }

    public static void setStagedDungeonMarker(StructurePieceType type) {
        STAGED_DUNGEON_MARKER = Objects.requireNonNull(type);
    }

    public static void setStagedDungeonBounds(StructurePieceType type) {
        STAGED_DUNGEON_BOUNDS = Objects.requireNonNull(type);
    }

    @FunctionalInterface
    public interface PieceRegistrar {
        StructurePieceType register(String name, StructurePieceType type);
    }
}
