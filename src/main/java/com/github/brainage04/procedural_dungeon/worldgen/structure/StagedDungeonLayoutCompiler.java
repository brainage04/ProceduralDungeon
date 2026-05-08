package com.github.brainage04.procedural_dungeon.worldgen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

public final class StagedDungeonLayoutCompiler {
    private StagedDungeonLayoutCompiler() {}

    public static Optional<StagedDungeonLayout> compile(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<Identifier> startJigsawName,
            int maxDepth,
            BlockPos startPos,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            JigsawStructure.MaxDistance maxDistanceFromCenter,
            LiquidSettings liquidSettings
    ) {
        long compileStart = DungeonGenerationProfiler.start();
        Optional<Structure.GenerationStub> stub = JigsawPlacement.addPieces(
                context,
                startPool,
                startJigsawName,
                maxDepth,
                startPos,
                useExpansionHack,
                projectStartToHeightmap,
                maxDistanceFromCenter,
                PoolAliasLookup.EMPTY,
                JigsawStructure.DEFAULT_DIMENSION_PADDING,
                liquidSettings
        );
        if (compileStart != 0L) {
            DungeonGenerationProfiler.recordLayoutStubSetup(System.nanoTime() - compileStart);
        }
        if (stub.isEmpty()) {
            return Optional.empty();
        }

        long extractionStart = DungeonGenerationProfiler.start();
        ArrayList<StagedDungeonPieceSpec> pieces = new ArrayList<>();
        for (StructurePiece piece : stub.get().getPiecesBuilder().build().pieces()) {
            if (piece instanceof PoolElementStructurePiece poolPiece) {
                pieces.add(new StagedDungeonPieceSpec(
                        poolPiece.getElement(),
                        poolPiece.getPosition(),
                        poolPiece.getRotation(),
                        poolPiece.getBoundingBox(),
                        poolPiece.getGroundLevelDelta()
                ));
            }
        }
        if (pieces.isEmpty()) {
            if (extractionStart != 0L) {
                DungeonGenerationProfiler.recordGraphExpansion(System.nanoTime() - extractionStart);
            }
            return Optional.empty();
        }

        BoundingBox boundingBox = pieces.stream()
                .map(StagedDungeonPieceSpec::boundingBox)
                .reduce(BoundingBox::encapsulating)
                .orElseThrow();
        if (extractionStart != 0L) {
            DungeonGenerationProfiler.recordGraphExpansion(System.nanoTime() - extractionStart);
        }
        return Optional.of(new StagedDungeonLayout(
                context.chunkPos(),
                stub.get().position(),
                boundingBox,
                List.copyOf(pieces)
        ));
    }
}
