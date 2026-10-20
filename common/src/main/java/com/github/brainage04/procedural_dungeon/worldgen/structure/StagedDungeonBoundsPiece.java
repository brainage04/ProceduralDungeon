package com.github.brainage04.procedural_dungeon.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The box of one staged dungeon piece, kept in the structure start so terrain adaptation shapes the
 * ground around every room instead of one box around the whole layout. It places nothing; the
 * staged generation job builds the piece.
 */
public class StagedDungeonBoundsPiece extends StructurePiece {
    public StagedDungeonBoundsPiece(BoundingBox boundingBox) {
        super(ModStructureTypes.STAGED_DUNGEON_BOUNDS, 0, boundingBox);
    }

    public StagedDungeonBoundsPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructureTypes.STAGED_DUNGEON_BOUNDS, tag);
    }

    @Override
    protected void addAdditionalSaveData(
            StructurePieceSerializationContext context, CompoundTag tag) {}

    @Override
    public void postProcess(
            WorldGenLevel world,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pivot) {}
}
