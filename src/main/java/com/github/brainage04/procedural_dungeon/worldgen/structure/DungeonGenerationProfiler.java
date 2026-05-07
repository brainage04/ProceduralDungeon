package com.github.brainage04.procedural_dungeon.worldgen.structure;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonGenerationProfiler {
    private static final ThreadLocal<Run> CURRENT = new ThreadLocal<>();

    private DungeonGenerationProfiler() {}

    public static void begin() {
        CURRENT.set(new Run());
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    public static Snapshot finish() {
        Run run = CURRENT.get();
        CURRENT.remove();
        return run == null ? Snapshot.EMPTY : run.snapshot();
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

    public static void recordPiece(Identifier variant, BoundingBox boundingBox, boolean placed) {
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

        private Snapshot snapshot() {
            return new Snapshot(
                    pieces,
                    placedPieces,
                    failedPieces,
                    boundingBoxVolume,
                    jigsaws,
                    expandableJigsaws,
                    keptJigsaws,
                    keptExpandableJigsaws
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
            int keptExpandableJigsaws
    ) {
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0, 0, 0);

        public int prunedExpandableJigsaws() {
            return Math.max(0, expandableJigsaws - keptExpandableJigsaws);
        }

        public double averagePieceVolume() {
            return pieces == 0 ? 0.0 : (double) boundingBoxVolume / pieces;
        }
    }
}
