package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.util.RandomSource;

public class StagedDungeonMarkerPiece extends StructurePiece {
    private final ChunkPos startChunk;
    private final List<StagedDungeonPieceSpec> pieces;
    private final LiquidSettings liquidSettings;
    private final DungeonLockPlan lockPlan;

    public StagedDungeonMarkerPiece(
            ChunkPos startChunk,
            BoundingBox boundingBox,
            List<StagedDungeonPieceSpec> pieces,
            LiquidSettings liquidSettings,
            DungeonLockPlan lockPlan
    ) {
        super(ModStructureTypes.STAGED_DUNGEON_MARKER, 0, boundingBox);
        this.startChunk = startChunk;
        this.pieces = List.copyOf(pieces);
        this.liquidSettings = liquidSettings;
        this.lockPlan = lockPlan;
    }

    public StagedDungeonMarkerPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructureTypes.STAGED_DUNGEON_MARKER, tag);
        this.startChunk = new ChunkPos(tag.getIntOr("start_chunk_x", 0), tag.getIntOr("start_chunk_z", 0));
        this.liquidSettings = tag.read("liquid_settings", LiquidSettings.CODEC).orElse(LiquidSettings.IGNORE_WATERLOGGING);
        this.lockPlan = tag.read("lock_plan", DungeonLockPlan.CODEC).orElse(DungeonLockPlan.EMPTY);

        var ops = StagedDungeonPieceSpec.ops(context.registryAccess());
        ListTag pieceTags = tag.getListOrEmpty("pieces");
        List<StagedDungeonPieceSpec> loadedPieces = new ArrayList<>(pieceTags.size());
        pieceTags.forEach(pieceTag -> {
            if (pieceTag instanceof CompoundTag pieceCompound) {
                loadedPieces.add(StagedDungeonPieceSpec.load(ops, pieceCompound));
            }
        });
        this.pieces = List.copyOf(loadedPieces);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("start_chunk_x", startChunk.x());
        tag.putInt("start_chunk_z", startChunk.z());
        tag.store("liquid_settings", LiquidSettings.CODEC, liquidSettings);
        tag.store("lock_plan", DungeonLockPlan.CODEC, lockPlan);

        var ops = StagedDungeonPieceSpec.ops(context.registryAccess());
        ListTag pieceTags = new ListTag();
        for (StagedDungeonPieceSpec piece : pieces) {
            pieceTags.add(piece.save(ops));
        }
        tag.put("pieces", pieceTags);
    }

    @Override
    public void postProcess(
            WorldGenLevel world,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pivot
    ) {
        StagedDungeonGenerationManager.enqueueFromWorldgenMarker(world.getLevel(), startChunk, pieces, liquidSettings, lockPlan);
    }
}
