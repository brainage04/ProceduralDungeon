package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlanner;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SequencedPriorityIterator;
import net.minecraft.util.Util;
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
    private static final String TRAP_POOL_SUFFIX = "/hallway/trap";
    private static final String TRAP_TEMPLATE_PREFIX = "dungeon/hallway/trap/";
    /**
     * Progression rooms attach to free room sockets and to free hallway ends, never to the start room.
     */
    private static final Set<Identifier> PROGRESSION_SOCKET_TARGETS = Set.of(
            Identifier.withDefaultNamespace("room"),
            Identifier.withDefaultNamespace("end")
    );
    private static final Identifier HALLWAY_SOCKET_TARGET = Identifier.withDefaultNamespace("start");
    private static final int MAX_HALLWAY_EXTENSIONS = 4;
    private static final long MIN_KEY_VAULT_DISTANCE_SQR = 32L * 32L;

    private StagedDungeonLayoutCompiler() {}

    public static Optional<StagedDungeonLayout> compile(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<Identifier> startJigsawName,
            int maxDepth,
            BlockPos startPos,
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
        Map<StagedDungeonPieceSpec, Integer> depths = new IdentityHashMap<>();
        StagedDungeonPieceSpec startPiece = new StagedDungeonPieceSpec(
                startElement,
                piecePos,
                startRotation,
                startBox,
                startElement.getGroundLevelDelta(),
                true
        );
        pieces.add(startPiece);
        depths.put(startPiece, 0);
        occupancy.add(startBox);

        SequencedPriorityIterator<PendingPiece> queue = new SequencedPriorityIterator<>();
        expandChildren(context, pools, templateManager, random, pieces, depths, occupancy, queue, startPiece, 0,
                maxDepth, allowedBounds);
        while (queue.hasNext()) {
            PendingPiece pending = queue.next();
            expandChildren(context, pools, templateManager, random, pieces, depths, occupancy, queue, pending.piece(), pending.depth(),
                    maxDepth, allowedBounds);
        }

        if (!attachProgressionRooms(context, pools, templateManager, random, pieces, depths, occupancy, allowedBounds)) {
            return Optional.empty();
        }

        BoundingBox boundingBox = pieces.stream()
                .map(StagedDungeonPieceSpec::boundingBox)
                .reduce(BoundingBox::encapsulating)
                .orElseThrow();
        StagedDungeonLayout baseLayout = new StagedDungeonLayout(context.chunkPos(), locator, boundingBox, List.copyOf(pieces));
        DungeonLockPlan lockPlan = DungeonLockPlanner.create(baseLayout, templateManager, random);
        return Optional.of(new StagedDungeonLayout(context.chunkPos(), locator, boundingBox, baseLayout.pieces(), lockPlan));
    }

    /**
     * Attaches the boss room to the deepest free room or hallway-end socket and the boss key vault to the deepest free
     * socket away from it. Both rooms are dead ends reached through ordinary hallways, so every compiled layout has a
     * walkable route to the key and to the boss door. When a small layout has no free socket that fits, spare hallway
     * sockets of the start room grow extra hallways first; a layout that still has no room is rejected.
     */
    private static boolean attachProgressionRooms(
            Structure.GenerationContext context,
            Registry<StructureTemplatePool> pools,
            StructureTemplateManager templateManager,
            RandomSource random,
            List<StagedDungeonPieceSpec> pieces,
            Map<StagedDungeonPieceSpec, Integer> depths,
            BoxOccupancy occupancy,
            AllowedBounds allowedBounds
    ) {
        if (!(pieces.getFirst().element() instanceof VariantSinglePoolElement start)) {
            return true;
        }

        Optional<Holder.Reference<StructureTemplatePool>> bossPool =
                pools.get(DungeonProgressionRooms.pool(start.variant(), DungeonProgressionRooms.BOSS_ROOM_POOL));
        Optional<Holder.Reference<StructureTemplatePool>> vaultPool =
                pools.get(DungeonProgressionRooms.pool(start.variant(), DungeonProgressionRooms.BOSS_KEY_VAULT_POOL));
        if (bossPool.isEmpty() || vaultPool.isEmpty()) {
            ProceduralDungeon.LOGGER.warn("Dungeon variant {} has no boss room or boss key vault pool", start.variant());
            return false;
        }

        LayoutGrowth growth = new LayoutGrowth(context, pools, templateManager, random, pieces, depths, occupancy, allowedBounds);
        Optional<StagedDungeonPieceSpec> bossRoom = growth.attachProgressionRoom(bossPool.get(), sockets -> sockets);
        if (bossRoom.isEmpty()) {
            return false;
        }

        BlockPos bossCenter = bossRoom.get().boundingBox().getCenter();
        return growth.attachProgressionRoom(vaultPool.get(), sockets -> {
            ArrayList<RoomSocket> farFirst = new ArrayList<>(sockets.size());
            sockets.stream()
                    .filter(socket -> horizontalDistanceSqr(socket.attachmentPos(), bossCenter) >= MIN_KEY_VAULT_DISTANCE_SQR)
                    .forEach(farFirst::add);
            sockets.stream()
                    .filter(socket -> horizontalDistanceSqr(socket.attachmentPos(), bossCenter) < MIN_KEY_VAULT_DISTANCE_SQR)
                    .forEach(farFirst::add);
            return farFirst;
        }).isPresent();
    }

    private static long horizontalDistanceSqr(BlockPos first, BlockPos second) {
        long dx = first.getX() - second.getX();
        long dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private record LayoutGrowth(
            Structure.GenerationContext context,
            Registry<StructureTemplatePool> pools,
            StructureTemplateManager templateManager,
            RandomSource random,
            List<StagedDungeonPieceSpec> pieces,
            Map<StagedDungeonPieceSpec, Integer> depths,
            BoxOccupancy occupancy,
            AllowedBounds allowedBounds
    ) {
        private Optional<StagedDungeonPieceSpec> attachProgressionRoom(
                Holder<StructureTemplatePool> pool,
                UnaryOperator<List<RoomSocket>> socketOrder
        ) {
            List<StructurePoolElement> rooms = pool.value().getShuffledTemplates(random);
            for (int extensions = 0; ; extensions++) {
                Optional<StagedDungeonPieceSpec> room = attachToFirstSocket(
                        socketOrder.apply(sockets(PROGRESSION_SOCKET_TARGETS)), socket -> rooms, false);
                if (room.isPresent() || extensions == MAX_HALLWAY_EXTENSIONS) {
                    return room;
                }
                if (attachToFirstSocket(sockets(Set.of(HALLWAY_SOCKET_TARGET)), this::socketPoolTemplates, true).isEmpty()) {
                    return Optional.empty();
                }
            }
        }

        private List<StructurePoolElement> socketPoolTemplates(RoomSocket socket) {
            return pools.get(PoolAliasLookup.EMPTY.lookup(socket.jigsaw().pool()))
                    .map(pool -> pool.value().getShuffledTemplates(random))
                    .orElse(List.of());
        }

        /**
         * Every horizontal socket with one of {@code targets}, deepest first; sockets at the same depth are in random
         * order. Jigsaws at BlockPos.ZERO bypass the branch limit, so sockets skipped during expansion are offered too;
         * occupied sockets are rejected later by the collision check.
         */
        private List<RoomSocket> sockets(Set<Identifier> targets) {
            ArrayList<RoomSocket> sockets = new ArrayList<>();
            for (StagedDungeonPieceSpec piece : pieces) {
                for (StructureTemplate.JigsawBlockInfo jigsaw : piece.element().getShuffledJigsawBlocks(
                        templateManager, BlockPos.ZERO, piece.rotation(), random)) {
                    if (!targets.contains(jigsaw.target())
                            || !JigsawBlock.getFrontFacing(jigsaw.info().state()).getAxis().isHorizontal()) {
                        continue;
                    }
                    StructureTemplate.StructureBlockInfo info = jigsaw.info();
                    StructureTemplate.JigsawBlockInfo placed = jigsaw.withInfo(new StructureTemplate.StructureBlockInfo(
                            info.pos().offset(piece.position()),
                            info.state(),
                            info.nbt()
                    ));
                    sockets.add(new RoomSocket(piece, placed, depths.getOrDefault(piece, 0)));
                }
            }
            Util.shuffle(sockets, random);
            sockets.sort(Comparator.comparingInt(RoomSocket::depth).reversed());
            return sockets;
        }

        /**
         * Places the first candidate that fits any socket, in socket order. Progression rooms only need an opposite
         * facing entrance; hallway extensions must also match the socket's jigsaw names.
         */
        private Optional<StagedDungeonPieceSpec> attachToFirstSocket(
                List<RoomSocket> sockets,
                Function<RoomSocket, List<StructurePoolElement>> candidatesForSocket,
                boolean matchJigsawNames
        ) {
            for (RoomSocket socket : sockets) {
                StagedDungeonPieceSpec parent = socket.parent();
                StructureTemplate.StructureBlockInfo sourceInfo = socket.jigsaw().info();
                Direction sourceFacing = JigsawBlock.getFrontFacing(sourceInfo.state());
                int sourceDeltaY = sourceInfo.pos().getY() - parent.boundingBox().minY();
                boolean parentRigid = parent.element().getProjection() == StructureTemplatePool.Projection.RIGID;

                for (StructurePoolElement candidate : candidatesForSocket.apply(socket)) {
                    for (Rotation rotation : Rotation.getShuffled(random)) {
                        List<StructureTemplate.JigsawBlockInfo> targetJigsaws =
                                candidate.getShuffledJigsawBlocks(templateManager, BlockPos.ZERO, rotation, random);
                        for (StructureTemplate.JigsawBlockInfo targetJigsaw : targetJigsaws) {
                            boolean attaches = matchJigsawNames
                                    ? JigsawBlock.canAttach(socket.jigsaw(), targetJigsaw)
                                    : JigsawBlock.getFrontFacing(targetJigsaw.info().state()) == sourceFacing.getOpposite();
                            if (!attaches) {
                                continue;
                            }

                            CandidatePlacement placement = createCandidatePlacement(context, parent, parentRigid, sourceInfo,
                                    sourceFacing, sourceDeltaY, socket.attachmentPos(), candidate, rotation, targetJigsaw,
                                    templateManager);
                            if (!allowedBounds.containsDeflated(placement.boundingBox())
                                    || occupancy.intersectsDeflated(placement.boundingBox(), false)) {
                                continue;
                            }

                            StagedDungeonPieceSpec piece = new StagedDungeonPieceSpec(candidate, placement.position(), rotation,
                                    placement.boundingBox(), placement.groundLevelDelta());
                            pieces.add(piece);
                            depths.put(piece, socket.depth() + 1);
                            occupancy.add(placement.boundingBox());
                            return Optional.of(piece);
                        }
                    }
                }
            }
            return Optional.empty();
        }
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
            Map<StagedDungeonPieceSpec, Integer> depths,
            BoxOccupancy occupancy,
            SequencedPriorityIterator<PendingPiece> queue,
            StagedDungeonPieceSpec parent,
            int depth,
            int maxDepth,
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
            candidates.sort(StagedDungeonLayoutCompiler::compareVerticalPreference);

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
                                templateManager
                        );
                        if (!allowedBounds.containsDeflated(placement.boundingBox())) {
                            rejectedByOutOfBounds = true;
                            DungeonGenerationProfiler.recordGraphRejectedOutOfBounds();
                            DungeonGenerationProfiler.recordGraphCandidateTemplateRejectedOutOfBounds(candidateTemplate);
                            continue;
                        }
                        boolean allowContainedOverlap = allowsContainedOverlap(sourceJigsaw, parent.element());
                        if (occupancy.intersectsDeflated(placement.boundingBox(), allowContainedOverlap)) {
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
                        depths.put(piece, depth + 1);
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

    private static boolean allowsContainedOverlap(StructureTemplate.JigsawBlockInfo sourceJigsaw, StructurePoolElement parentElement) {
        return isTrapPool(sourceJigsaw.pool().identifier()) || isTrapTemplate(parentElement);
    }

    private static boolean isTrapPool(Identifier pool) {
        String path = pool.getPath();
        return path.equals("dungeon/hallway/trap") || path.endsWith(TRAP_POOL_SUFFIX);
    }

    private static boolean isTrapTemplate(StructurePoolElement element) {
        return element instanceof VariantSinglePoolElement variantElement && isTrapTemplate(variantElement.templateLocation());
    }

    private static boolean isTrapTemplate(Identifier template) {
        return template.getNamespace().equals(ProceduralDungeon.MOD_ID)
                && template.getPath().startsWith(TRAP_TEMPLATE_PREFIX);
    }

    private static String candidateTemplate(StructurePoolElement element) {
        if (element instanceof VariantSinglePoolElement variantElement) {
            return variantElement.templateLocation().toString();
        }
        return element.toString();
    }

    private static int compareVerticalPreference(StructurePoolElement first, StructurePoolElement second) {
        return Integer.compare(verticalPreference(first), verticalPreference(second));
    }

    private static int verticalPreference(StructurePoolElement element) {
        if (element == EmptyPoolElement.INSTANCE) {
            return 2;
        }
        return isUpStairTemplate(element) ? 1 : 0;
    }

    private static boolean isUpStairTemplate(StructurePoolElement element) {
        return element instanceof VariantSinglePoolElement variantElement
                && variantElement.templateLocation().getPath().startsWith("dungeon/hallway/room/staircase_")
                && variantElement.templateLocation().getPath().endsWith("_up");
    }

    private static boolean isUnexpectedEmptyPool(Holder<StructureTemplatePool> pool) {
        return pool.value().size() == 0 && !pool.unwrapKey().map(key -> key.identifier().equals(Identifier.withDefaultNamespace("empty"))).orElse(false);
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

        int groundLevelDelta = candidateRigid
                ? parent.groundLevelDelta() - connectionOffsetY
                : candidate.getGroundLevelDelta();
        return new CandidatePlacement(movedPos, movedBox, groundLevelDelta);
    }

    private static boolean fitsWorldHeight(LevelHeightAccessor heightAccessor, BoundingBox box) {
        return box.minY() >= heightAccessor.getMinY() && box.maxY() <= heightAccessor.getMaxY();
    }

    private record PendingPiece(StagedDungeonPieceSpec piece, int depth) {}

    private record RoomSocket(StagedDungeonPieceSpec parent, StructureTemplate.JigsawBlockInfo jigsaw, int depth) {
        private BlockPos attachmentPos() {
            return jigsaw.info().pos().relative(JigsawBlock.getFrontFacing(jigsaw.info().state()));
        }
    }

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

        private boolean intersectsDeflated(BoundingBox candidate, boolean allowContainedOverlap) {
            for (int bucketX = bucket(candidate.minX()); bucketX <= bucket(candidate.maxX()); bucketX++) {
                for (int bucketZ = bucket(candidate.minZ()); bucketZ <= bucket(candidate.maxZ()); bucketZ++) {
                    List<BoundingBox> nearby = buckets.get(pack(bucketX, bucketZ));
                    if (nearby == null) {
                        continue;
                    }
                    for (BoundingBox box : nearby) {
                        if (allowContainedOverlap && contains(box, candidate)) {
                            continue;
                        }
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

        private static boolean contains(BoundingBox occupied, BoundingBox candidate) {
            return occupied.minX() <= candidate.minX()
                    && occupied.minY() <= candidate.minY()
                    && occupied.minZ() <= candidate.minZ()
                    && occupied.maxX() >= candidate.maxX()
                    && occupied.maxY() >= candidate.maxY()
                    && occupied.maxZ() >= candidate.maxZ();
        }

        private static int bucket(int coordinate) {
            return Math.floorDiv(coordinate, OCCUPANCY_BUCKET_SIZE);
        }

        private static long pack(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }
}
