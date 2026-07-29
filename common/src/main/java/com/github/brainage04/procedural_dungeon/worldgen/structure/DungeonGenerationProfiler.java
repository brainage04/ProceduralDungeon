package com.github.brainage04.procedural_dungeon.worldgen.structure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonGenerationProfiler {
    private static final ThreadLocal<Run> CURRENT = new ThreadLocal<>();
    private static volatile int activeRuns;

    private DungeonGenerationProfiler() {}

    public static synchronized void begin() {
        if (CURRENT.get() == null) {
            activeRuns++;
        }
        CURRENT.set(new Run());
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    public static long start() {
        if (activeRuns == 0) {
            return 0L;
        }

        return isActive() ? System.nanoTime() : 0L;
    }

    public static synchronized Snapshot finish() {
        Run run = CURRENT.get();
        if (run != null) {
            CURRENT.remove();
            activeRuns = Math.max(0, activeRuns - 1);
        }
        return run == null ? Snapshot.EMPTY : run.snapshot();
    }

    public static void recordLayoutStubSetup(long nanos) {
        Run run = CURRENT.get();
        if (run != null) {
            run.layoutStubSetupNanos += nanos;
        }
    }

    public static void recordGraphExpansion(long nanos) {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphExpansionNanos += nanos;
        }
    }

    public static void recordSolidDensity(long nanos) {
        Run run = CURRENT.get();
        if (run != null) {
            run.solidDensityNanos += nanos;
        }
    }

    public static void recordJigsawBlockLookup(long nanos) {
        Run run = CURRENT.get();
        if (run != null) {
            run.jigsawBlockLookupNanos += nanos;
        }
    }

    public static void recordPoolReplacement(long nanos) {
        Run run = CURRENT.get();
        if (run != null) {
            run.poolReplacementNanos += nanos;
        }
    }

    public static void recordBranchLimit(long nanos) {
        Run run = CURRENT.get();
        if (run != null) {
            run.branchLimitNanos += nanos;
        }
    }

    public static void recordBoundingBoxLookup(long nanos) {
        Run run = CURRENT.get();
        if (run != null) {
            run.boundingBoxLookupNanos += nanos;
        }
    }

    public static void recordJigsaws(int total, int expandable, int kept, int keptExpandable) {
        Run run = CURRENT.get();
        if (run == null) {
            return;
        }

        run.jigsaws += total;
        run.expandableJigsaws += expandable;
        run.keptJigsaws += kept;
        run.keptExpandableJigsaws += keptExpandable;
    }

    public static void recordGraphSourceJigsaw() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphSourceJigsaws++;
        }
    }

    public static void recordGraphCandidateElement() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphCandidateElements++;
        }
    }

    public static void recordGraphCandidateTemplate(String template) {
        GraphCandidateCount.Mutable count = graphCandidateCount(template);
        if (count != null) {
            count.attempts++;
        }
    }

    public static void recordGraphCandidateTemplateAttachMatch(String template) {
        GraphCandidateCount.Mutable count = graphCandidateCount(template);
        if (count != null) {
            count.attachMatches++;
        }
    }

    public static void recordGraphCandidateTemplateRejectedOutOfBounds(String template) {
        GraphCandidateCount.Mutable count = graphCandidateCount(template);
        if (count != null) {
            count.outOfBounds++;
        }
    }

    public static void recordGraphCandidateTemplateRejectedCollision(String template) {
        GraphCandidateCount.Mutable count = graphCandidateCount(template);
        if (count != null) {
            count.collision++;
        }
    }

    public static void recordGraphCandidateTemplateAccepted(String template) {
        GraphCandidateCount.Mutable count = graphCandidateCount(template);
        if (count != null) {
            count.accepted++;
        }
    }

    public static void recordGraphAttachMatch() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphAttachMatches++;
        }
    }

    public static void recordGraphAcceptedPiece() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphAcceptedPieces++;
        }
    }

    public static void recordGraphRejectedEmptyPool() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphRejectedEmptyPool++;
        }
    }

    public static void recordGraphRejectedEmptyFallback() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphRejectedEmptyFallback++;
        }
    }

    public static void recordGraphRejectedNoCandidate() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphRejectedNoCandidate++;
        }
    }

    public static void recordGraphRejectedNoAttach() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphRejectedNoAttach++;
        }
    }

    public static void recordGraphRejectedOutOfBounds() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphRejectedOutOfBounds++;
        }
    }

    public static void recordGraphRejectedCollision() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphRejectedCollision++;
        }
    }

    public static void recordGraphSourceTerminalOutOfBounds() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphSourceTerminalOutOfBounds++;
        }
    }

    public static void recordGraphSourceTerminalCollision() {
        Run run = CURRENT.get();
        if (run != null) {
            run.graphSourceTerminalCollision++;
        }
    }

    public static void recordPiece(Identifier variant, Identifier template, BoundingBox boundingBox, boolean placed, long placementNanos) {
        Run run = CURRENT.get();
        if (run == null) {
            return;
        }

        run.pieces++;
        if (placed) {
            run.placedPieces++;
        } else {
            run.failedPieces++;
        }
        run.boundingBoxVolume += volume(boundingBox);
        run.piecePlacementNanos += placementNanos;
        run.maxPiecePlacementNanos = Math.max(run.maxPiecePlacementNanos, placementNanos);

        PieceCount.Mutable pieceCount = run.pieceCounts.computeIfAbsent(template.toString(), PieceCount.Mutable::new);
        pieceCount.total++;
        if (placed) {
            pieceCount.placed++;
        } else {
            pieceCount.failed++;
        }
    }

    public static void recordProcessor(String id, long nanos) {
        Run run = CURRENT.get();
        if (run == null) {
            return;
        }

        ProcessorTiming.Mutable timing = run.processorTimings.computeIfAbsent(id, ignored -> new ProcessorTiming.Mutable(id));
        timing.calls++;
        timing.nanos += nanos;
    }

    private static GraphCandidateCount.Mutable graphCandidateCount(String template) {
        Run run = CURRENT.get();
        if (run == null) {
            return null;
        }
        return run.graphCandidateCounts.computeIfAbsent(template, GraphCandidateCount.Mutable::new);
    }

    private static long volume(BoundingBox boundingBox) {
        return (long) boundingBox.getXSpan() * boundingBox.getYSpan() * boundingBox.getZSpan();
    }

    private static final class Run {
        private int pieces;
        private int placedPieces;
        private int failedPieces;
        private long boundingBoxVolume;
        private int jigsaws;
        private int expandableJigsaws;
        private int keptJigsaws;
        private int keptExpandableJigsaws;
        private int graphSourceJigsaws;
        private int graphCandidateElements;
        private int graphAttachMatches;
        private int graphAcceptedPieces;
        private int graphRejectedEmptyPool;
        private int graphRejectedEmptyFallback;
        private int graphRejectedNoCandidate;
        private int graphRejectedNoAttach;
        private int graphRejectedOutOfBounds;
        private int graphRejectedCollision;
        private int graphSourceTerminalOutOfBounds;
        private int graphSourceTerminalCollision;
        private long layoutStubSetupNanos;
        private long graphExpansionNanos;
        private long solidDensityNanos;
        private long jigsawBlockLookupNanos;
        private long poolReplacementNanos;
        private long branchLimitNanos;
        private long boundingBoxLookupNanos;
        private long piecePlacementNanos;
        private long maxPiecePlacementNanos;
        private final Map<String, ProcessorTiming.Mutable> processorTimings = new LinkedHashMap<>();
        private final Map<String, PieceCount.Mutable> pieceCounts = new LinkedHashMap<>();
        private final Map<String, GraphCandidateCount.Mutable> graphCandidateCounts = new LinkedHashMap<>();

        private Snapshot snapshot() {
            return new Snapshot(
                    pieces,
                    placedPieces,
                    failedPieces,
                    boundingBoxVolume,
                    jigsaws,
                    expandableJigsaws,
                    keptJigsaws,
                    keptExpandableJigsaws,
                    graphSourceJigsaws,
                    graphCandidateElements,
                    graphAttachMatches,
                    graphAcceptedPieces,
                    graphRejectedEmptyPool,
                    graphRejectedEmptyFallback,
                    graphRejectedNoCandidate,
                    graphRejectedNoAttach,
                    graphRejectedOutOfBounds,
                    graphRejectedCollision,
                    graphSourceTerminalOutOfBounds,
                    graphSourceTerminalCollision,
                    layoutStubSetupNanos,
                    graphExpansionNanos,
                    solidDensityNanos,
                    jigsawBlockLookupNanos,
                    poolReplacementNanos,
                    branchLimitNanos,
                    boundingBoxLookupNanos,
                    piecePlacementNanos,
                    maxPiecePlacementNanos,
                    processorTimings.values().stream()
                            .map(ProcessorTiming.Mutable::snapshot)
                            .toList(),
                    pieceCounts.values().stream()
                            .map(PieceCount.Mutable::snapshot)
                            .toList(),
                    graphCandidateCounts.values().stream()
                            .map(GraphCandidateCount.Mutable::snapshot)
                            .toList()
            );
        }
    }

    public record Snapshot(
            int pieces,
            int placedPieces,
            int failedPieces,
            long boundingBoxVolume,
            int jigsaws,
            int expandableJigsaws,
            int keptJigsaws,
            int keptExpandableJigsaws,
            int graphSourceJigsaws,
            int graphCandidateElements,
            int graphAttachMatches,
            int graphAcceptedPieces,
            int graphRejectedEmptyPool,
            int graphRejectedEmptyFallback,
            int graphRejectedNoCandidate,
            int graphRejectedNoAttach,
            int graphRejectedOutOfBounds,
            int graphRejectedCollision,
            int graphSourceTerminalOutOfBounds,
            int graphSourceTerminalCollision,
            long layoutStubSetupNanos,
            long graphExpansionNanos,
            long solidDensityNanos,
            long jigsawBlockLookupNanos,
            long poolReplacementNanos,
            long branchLimitNanos,
            long boundingBoxLookupNanos,
            long piecePlacementNanos,
            long maxPiecePlacementNanos,
            List<ProcessorTiming> processorTimings,
            List<PieceCount> pieceCounts,
            List<GraphCandidateCount> graphCandidateCounts
    ) {
        public static final Snapshot EMPTY = new Snapshot(
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(),
                List.of(),
                List.of()
        );

        public int prunedExpandableJigsaws() {
            return Math.max(0, expandableJigsaws - keptExpandableJigsaws);
        }

        public double averagePieceVolume() {
            return pieces == 0 ? 0.0 : (double) boundingBoxVolume / pieces;
        }
    }

    public record ProcessorTiming(String id, int calls, long nanos) {
        private static final class Mutable {
            private final String id;
            private int calls;
            private long nanos;

            private Mutable(String id) {
                this.id = id;
            }

            private ProcessorTiming snapshot() {
                return new ProcessorTiming(id, calls, nanos);
            }
        }
    }

    public record PieceCount(String template, int total, int placed, int failed) {
        private static final class Mutable {
            private final String template;
            private int total;
            private int placed;
            private int failed;

            private Mutable(String template) {
                this.template = template;
            }

            private PieceCount snapshot() {
                return new PieceCount(template, total, placed, failed);
            }
        }
    }

    public record GraphCandidateCount(String template, int attempts, int attachMatches, int outOfBounds, int collision, int accepted) {
        private static final class Mutable {
            private final String template;
            private int attempts;
            private int attachMatches;
            private int outOfBounds;
            private int collision;
            private int accepted;

            private Mutable(String template) {
                this.template = template;
            }

            private GraphCandidateCount snapshot() {
                return new GraphCandidateCount(template, attempts, attachMatches, outOfBounds, collision, accepted);
            }
        }
    }
}
