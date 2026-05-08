package com.github.brainage04.procedural_dungeon.worldgen.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SequencedPriorityIterator;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class StagedDungeonLayoutCompiler {
    private static final int OCCUPANCY_BUCKET_SIZE = 32;

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
        Optional<StagedDungeonLayout> layout = compileWithBoundingBoxes(
                context,
                startPool,
                startJigsawName,
                maxDepth,
                startPos,
                useExpansionHack,
                projectStartToHeightmap,
                maxDistanceFromCenter
        );
        if (compileStart != 0L) {
            DungeonGenerationProfiler.recordGraphExpansion(System.nanoTime() - compileStart);
        }
        return layout;
    }

    private static Optional<StagedDungeonLayout> compileWithBoundingBoxes(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<Identifier> startJigsawName,
            int maxDepth,
            BlockPos startPos,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            JigsawStructure.MaxDistance maxDistanceFromCenter
    ) {
        Registry<StructureTemplatePool> pools = context.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        StructureTemplateManager templateManager = context.structureTemplateManager();
        RandomSource random = context.random();
        Rotation startRotation = Rotation.getRandom(random);
        StructurePoolElement startElement = startPool.value().getRandomTemplate(random);
        if (startElement == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        }

        Optional<BlockPos> namedStartJigsawPos = startJigsawName
                .flatMap(name -> findNamedJigsaw(startElement, name, startPos, startRotation, templateManager, random));
        if (startJigsawName.isPresent() && namedStartJigsawPos.isEmpty()) {
            return Optional.empty();
        }

        BlockPos startJigsawPos = namedStartJigsawPos.orElse(startPos);
        BlockPos startOffset = startJigsawPos.subtract(startPos);
        BlockPos piecePos = startPos.subtract(startOffset);
        BoundingBox startBox = startElement.getBoundingBox(templateManager, piecePos, startRotation);
        int locatorX = (startBox.maxX() + startBox.minX()) / 2;
        int locatorZ = (startBox.maxZ() + startBox.minZ()) / 2;
        int locatorY = projectStartToHeightmap
                .map(type -> startPos.getY() + context.chunkGenerator().getFirstFreeHeight(
                        locatorX,
                        locatorZ,
                        type,
                        context.heightAccessor(),
                        context.randomState()
                ))
                .orElse(piecePos.getY());
        int startGroundY = startBox.minY() + startElement.getGroundLevelDelta();
        int startMoveY = locatorY - startGroundY;
        if (startMoveY != 0) {
            piecePos = piecePos.offset(0, startMoveY, 0);
            startBox = startBox.moved(0, startMoveY, 0);
        }

        if (!fitsWorldHeight(context.heightAccessor(), startBox)) {
            return Optional.empty();
        }

        BlockPos locator = new BlockPos(locatorX, locatorY + startOffset.getY(), locatorZ);
        AllowedBounds allowedBounds = AllowedBounds.create(locator, maxDistanceFromCenter, context.heightAccessor());
        BoxOccupancy occupancy = new BoxOccupancy();
        ArrayList<StagedDungeonPieceSpec> pieces = new ArrayList<>();
        StagedDungeonPieceSpec startPiece = new StagedDungeonPieceSpec(
                startElement,
                piecePos,
                startRotation,
                startBox,
                startElement.getGroundLevelDelta(),
                true
        );
        pieces.add(startPiece);
        occupancy.add(startBox);

        SequencedPriorityIterator<PendingPiece> queue = new SequencedPriorityIterator<>();
        expandChildren(context, pools, templateManager, random, pieces, occupancy, queue, startPiece, 0,
                maxDepth, useExpansionHack, allowedBounds);
        while (queue.hasNext()) {
            PendingPiece pending = queue.next();
            expandChildren(context, pools, templateManager, random, pieces, occupancy, queue, pending.piece(), pending.depth(),
                    maxDepth, useExpansionHack, allowedBounds);
        }

        if (pieces.isEmpty()) {
            return Optional.empty();
        }

        BoundingBox boundingBox = pieces.stream()
                .map(StagedDungeonPieceSpec::boundingBox)
                .reduce(BoundingBox::encapsulating)
                .orElseThrow();
        return Optional.of(new StagedDungeonLayout(context.chunkPos(), locator, boundingBox, List.copyOf(pieces)));
    }

    private static Optional<BlockPos> findNamedJigsaw(
            StructurePoolElement element,
            Identifier name,
            BlockPos pos,
            Rotation rotation,
            StructureTemplateManager templateManager,
            RandomSource random
    ) {
        for (StructureTemplate.JigsawBlockInfo jigsaw : element.getShuffledJigsawBlocks(templateManager, pos, rotation, random)) {
            if (name.equals(jigsaw.name())) {
                return Optional.of(jigsaw.info().pos());
            }
        }
        return Optional.empty();
    }

    private static void expandChildren(
            Structure.GenerationContext context,
            Registry<StructureTemplatePool> pools,
            StructureTemplateManager templateManager,
            RandomSource random,
            List<StagedDungeonPieceSpec> pieces,
            BoxOccupancy occupancy,
            SequencedPriorityIterator<PendingPiece> queue,
            StagedDungeonPieceSpec parent,
            int depth,
            int maxDepth,
            boolean useExpansionHack,
            AllowedBounds allowedBounds
    ) {
        StructurePoolElement parentElement = parent.element();
        BlockPos parentPos = parent.position();
        Rotation parentRotation = parent.rotation();
        boolean parentRigid = parentElement.getProjection() == StructureTemplatePool.Projection.RIGID;
        int parentMinY = parent.boundingBox().minY();

        for (StructureTemplate.JigsawBlockInfo sourceJigsaw : parentElement.getShuffledJigsawBlocks(templateManager, parentPos, parentRotation, random)) {
            DungeonGenerationProfiler.recordGraphSourceJigsaw();
            StructureTemplate.StructureBlockInfo sourceInfo = sourceJigsaw.info();
            Direction sourceFacing = JigsawBlock.getFrontFacing(sourceInfo.state());
            BlockPos sourcePos = sourceInfo.pos();
            BlockPos attachmentPos = sourcePos.relative(sourceFacing);
            int sourceDeltaY = sourcePos.getY() - parentMinY;
            Optional<Holder.Reference<StructureTemplatePool>> poolHolder = pools.get(PoolAliasLookup.EMPTY.lookup(sourceJigsaw.pool()));
            if (poolHolder.isEmpty() || isUnexpectedEmptyPool(poolHolder.get())) {
                DungeonGenerationProfiler.recordGraphRejectedEmptyPool();
                continue;
            }

            Holder<StructureTemplatePool> fallback = poolHolder.get().value().getFallback();
            if (isUnexpectedEmptyPool(fallback)) {
                DungeonGenerationProfiler.recordGraphRejectedEmptyFallback();
                continue;
            }

            ArrayList<StructurePoolElement> candidates = new ArrayList<>();
            if (depth != maxDepth) {
                candidates.addAll(poolHolder.get().value().getShuffledTemplates(random));
            }
            candidates.addAll(fallback.value().getShuffledTemplates(random));

            int placementPriority = sourceJigsaw.placementPriority();
            boolean acceptedForSource = false;
            boolean sawCandidate = false;
            boolean sawAttachMatch = false;
            boolean rejectedByOutOfBounds = false;
            boolean rejectedByCollision = false;
            for (StructurePoolElement candidate : candidates) {
                if (candidate == EmptyPoolElement.INSTANCE) {
                    break;
                }
                sawCandidate = true;
                DungeonGenerationProfiler.recordGraphCandidateElement();
                String candidateTemplate = candidateTemplate(candidate);
                DungeonGenerationProfiler.recordGraphCandidateTemplate(candidateTemplate);

                for (Rotation rotation : Rotation.getShuffled(random)) {
                    List<StructureTemplate.JigsawBlockInfo> targetJigsaws = candidate.getShuffledJigsawBlocks(
                            templateManager,
                            BlockPos.ZERO,
                            rotation,
                            random
                    );
                    BoundingBox baseBox = candidate.getBoundingBox(templateManager, BlockPos.ZERO, rotation);
                    int expansionHeight = expansionHeight(context, pools, templateManager, useExpansionHack, baseBox, targetJigsaws);

                    for (StructureTemplate.JigsawBlockInfo targetJigsaw : targetJigsaws) {
                        if (!JigsawBlock.canAttach(sourceJigsaw, targetJigsaw)) {
                            continue;
                        }
                        sawAttachMatch = true;
                        DungeonGenerationProfiler.recordGraphAttachMatch();
                        DungeonGenerationProfiler.recordGraphCandidateTemplateAttachMatch(candidateTemplate);

                        CandidatePlacement placement = createCandidatePlacement(
                                context,
                                parent,
                                parentRigid,
                                sourceInfo,
                                sourceFacing,
                                sourceDeltaY,
                                attachmentPos,
                                candidate,
                                rotation,
                                targetJigsaw,
                                expansionHeight,
                                templateManager
                        );
                        if (!allowedBounds.containsDeflated(placement.boundingBox())) {
                            rejectedByOutOfBounds = true;
                            DungeonGenerationProfiler.recordGraphRejectedOutOfBounds();
                            DungeonGenerationProfiler.recordGraphCandidateTemplateRejectedOutOfBounds(candidateTemplate);
                            continue;
                        }
                        if (occupancy.intersectsDeflated(placement.boundingBox())) {
                            rejectedByCollision = true;
                            DungeonGenerationProfiler.recordGraphRejectedCollision();
                            DungeonGenerationProfiler.recordGraphCandidateTemplateRejectedCollision(candidateTemplate);
                            continue;
                        }

                        StagedDungeonPieceSpec piece = new StagedDungeonPieceSpec(
                                candidate,
                                placement.position(),
                                rotation,
                                placement.boundingBox(),
                                placement.groundLevelDelta()
                        );
                        pieces.add(piece);
                        occupancy.add(placement.boundingBox());
                        DungeonGenerationProfiler.recordGraphAcceptedPiece();
                        DungeonGenerationProfiler.recordGraphCandidateTemplateAccepted(candidateTemplate);

                        if (depth + 1 <= maxDepth) {
                            queue.add(new PendingPiece(piece, depth + 1), placementPriority);
                        }
                        acceptedForSource = true;
                        break;
                    }

                    if (acceptedForSource) {
                        break;
                    }
                }

                if (acceptedForSource) {
                    break;
                }
            }

            if (!acceptedForSource) {
                if (!sawCandidate) {
                    DungeonGenerationProfiler.recordGraphRejectedNoCandidate();
                } else if (!sawAttachMatch) {
                    DungeonGenerationProfiler.recordGraphRejectedNoAttach();
                } else if (rejectedByCollision) {
                    DungeonGenerationProfiler.recordGraphSourceTerminalCollision();
                } else if (rejectedByOutOfBounds) {
                    DungeonGenerationProfiler.recordGraphSourceTerminalOutOfBounds();
                }
            }
        }
    }

    private static String candidateTemplate(StructurePoolElement element) {
        if (element instanceof VariantSinglePoolElement variantElement) {
            return variantElement.templateLocation().toString();
        }
        return element.toString();
    }

    private static boolean isUnexpectedEmptyPool(Holder<StructureTemplatePool> pool) {
        return pool.value().size() == 0 && !pool.unwrapKey().map(key -> key.identifier().equals(Identifier.withDefaultNamespace("empty"))).orElse(false);
    }

    private static int expansionHeight(
            Structure.GenerationContext context,
            Registry<StructureTemplatePool> pools,
            StructureTemplateManager templateManager,
            boolean useExpansionHack,
            BoundingBox baseBox,
            List<StructureTemplate.JigsawBlockInfo> targetJigsaws
    ) {
        if (!useExpansionHack || baseBox.getYSpan() > 16) {
            return 0;
        }

        int max = 0;
        for (StructureTemplate.JigsawBlockInfo info : targetJigsaws) {
            StructureTemplate.StructureBlockInfo blockInfo = info.info();
            if (!baseBox.isInside(blockInfo.pos().relative(JigsawBlock.getFrontFacing(blockInfo.state())))) {
                continue;
            }

            Optional<Holder.Reference<StructureTemplatePool>> pool = pools.get(PoolAliasLookup.EMPTY.lookup(info.pool()));
            int directSize = pool.map(holder -> holder.value().getMaxSize(templateManager)).orElse(0);
            int fallbackSize = pool.map(holder -> holder.value().getFallback().value().getMaxSize(templateManager)).orElse(0);
            max = Math.max(max, Math.max(directSize, fallbackSize));
        }
        return max;
    }

    private static CandidatePlacement createCandidatePlacement(
            Structure.GenerationContext context,
            StagedDungeonPieceSpec parent,
            boolean parentRigid,
            StructureTemplate.StructureBlockInfo sourceInfo,
            Direction sourceFacing,
            int sourceDeltaY,
            BlockPos attachmentPos,
            StructurePoolElement candidate,
            Rotation rotation,
            StructureTemplate.JigsawBlockInfo targetJigsaw,
            int expansionHeight,
            StructureTemplateManager templateManager
    ) {
        BlockPos targetPos = targetJigsaw.info().pos();
        BlockPos candidatePos = attachmentPos.subtract(targetPos);
        BoundingBox candidateBox = candidate.getBoundingBox(templateManager, candidatePos, rotation);
        boolean candidateRigid = candidate.getProjection() == StructureTemplatePool.Projection.RIGID;
        int targetDeltaY = targetPos.getY();
        int connectionOffsetY = sourceDeltaY - targetDeltaY + sourceFacing.getStepY();
        int targetY;
        if (parentRigid && candidateRigid) {
            targetY = parent.boundingBox().minY() + connectionOffsetY;
        } else {
            targetY = context.chunkGenerator().getFirstFreeHeight(
                    sourceInfo.pos().getX(),
                    sourceInfo.pos().getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(),
                    context.randomState()
            ) - targetDeltaY;
        }

        int moveY = targetY - candidateBox.minY();
        BoundingBox movedBox = candidateBox.moved(0, moveY, 0);
        BlockPos movedPos = candidatePos.offset(0, moveY, 0);
        if (expansionHeight > 0) {
            int height = Math.max(expansionHeight + 1, movedBox.maxY() - movedBox.minY());
            movedBox.encapsulate(new BlockPos(movedBox.minX(), movedBox.minY() + height, movedBox.minZ()));
        }

        int groundLevelDelta = candidateRigid
                ? parent.groundLevelDelta() - connectionOffsetY
                : candidate.getGroundLevelDelta();
        return new CandidatePlacement(movedPos, movedBox, groundLevelDelta);
    }

    private static boolean fitsWorldHeight(LevelHeightAccessor heightAccessor, BoundingBox box) {
        return box.minY() >= heightAccessor.getMinY() && box.maxY() <= heightAccessor.getMaxY();
    }

    private record PendingPiece(StagedDungeonPieceSpec piece, int depth) {}

    private record CandidatePlacement(BlockPos position, BoundingBox boundingBox, int groundLevelDelta) {}

    private record AllowedBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static AllowedBounds create(BlockPos center, JigsawStructure.MaxDistance maxDistance, LevelHeightAccessor heightAccessor) {
            return new AllowedBounds(
                    center.getX() - maxDistance.horizontal(),
                    Math.max(center.getY() - maxDistance.vertical(), heightAccessor.getMinY()),
                    center.getZ() - maxDistance.horizontal(),
                    center.getX() + maxDistance.horizontal(),
                    Math.min(center.getY() + maxDistance.vertical(), heightAccessor.getMaxY()),
                    center.getZ() + maxDistance.horizontal()
            );
        }

        private boolean containsDeflated(BoundingBox box) {
            return box.minX() + 0.25 >= minX
                    && box.minY() + 0.25 >= minY
                    && box.minZ() + 0.25 >= minZ
                    && box.maxX() + 0.75 <= maxX + 1.0
                    && box.maxY() + 0.75 <= maxY + 1.0
                    && box.maxZ() + 0.75 <= maxZ + 1.0;
        }
    }

    private static final class BoxOccupancy {
        private final Map<Long, List<BoundingBox>> buckets = new HashMap<>();

        private void add(BoundingBox box) {
            for (int bucketX = bucket(box.minX()); bucketX <= bucket(box.maxX()); bucketX++) {
                for (int bucketZ = bucket(box.minZ()); bucketZ <= bucket(box.maxZ()); bucketZ++) {
                    buckets.computeIfAbsent(pack(bucketX, bucketZ), ignored -> new ArrayList<>()).add(box);
                }
            }
        }

        private boolean intersectsDeflated(BoundingBox candidate) {
            for (int bucketX = bucket(candidate.minX()); bucketX <= bucket(candidate.maxX()); bucketX++) {
                for (int bucketZ = bucket(candidate.minZ()); bucketZ <= bucket(candidate.maxZ()); bucketZ++) {
                    List<BoundingBox> nearby = buckets.get(pack(bucketX, bucketZ));
                    if (nearby == null) {
                        continue;
                    }
                    for (BoundingBox box : nearby) {
                        if (intersectsDeflated(box, candidate)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static boolean intersectsDeflated(BoundingBox occupied, BoundingBox candidate) {
            return occupied.minX() < candidate.maxX() + 0.75
                    && occupied.maxX() + 1.0 > candidate.minX() + 0.25
                    && occupied.minY() < candidate.maxY() + 0.75
                    && occupied.maxY() + 1.0 > candidate.minY() + 0.25
                    && occupied.minZ() < candidate.maxZ() + 0.75
                    && occupied.maxZ() + 1.0 > candidate.minZ() + 0.25;
        }

        private static int bucket(int coordinate) {
            return Math.floorDiv(coordinate, OCCUPANCY_BUCKET_SIZE);
        }

        private static long pack(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }
}
