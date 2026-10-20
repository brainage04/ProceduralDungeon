package com.github.brainage04.procedural_dungeon.api.impl;

import com.github.brainage04.procedural_dungeon.api.DungeonHandle;
import com.github.brainage04.procedural_dungeon.api.DungeonState;
import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonPieceSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;
import java.util.UUID;

/** A live view of one API build's job record in {@link StagedDungeonGenerationManager}. */
public record ApiDungeonHandle(ServerLevel level, UUID id) implements DungeonHandle {
    @Override
    public BoundingBox bounds() {
        return view().bounds();
    }

    @Override
    public BlockPos startSpawn() {
        BoundingBox box = startPiece(layout()).boundingBox();
        BlockPos middle = box.getCenter();
        if (state() == DungeonState.COMPLETE) {
            for (int y = box.minY() + 1; y < box.maxY(); y++) {
                BlockPos feet = new BlockPos(middle.getX(), y, middle.getZ());
                if (standable(feet)) {
                    return feet;
                }
            }
        }
        return middle;
    }

    @Override
    public Optional<BlockPos> bossDoor() {
        return layout().lockPlan().doors().stream()
                .filter(door -> door.lock().equals(Optional.of(DungeonKeyType.BOSS)))
                .map(door -> BlockPos.of(door.pos()))
                .findFirst();
    }

    @Override
    public int placedPieces() {
        return view().placedPieces();
    }

    @Override
    public int totalPieces() {
        return view().totalPieces();
    }

    @Override
    public DungeonState state() {
        return ApiDungeons.state(view().state());
    }

    @Override
    public Optional<String> failure() {
        return Optional.ofNullable(view().failure());
    }

    @Override
    public long advance(long budgetNanos) {
        ApiDungeons.requireServerThread(level);
        if (!view().options().callerDrivesBudget()) {
            return 0L;
        }
        return StagedDungeonGenerationManager.advance(level, id, budgetNanos);
    }

    @Override
    public void cancel() {
        ApiDungeons.requireServerThread(level);
        StagedDungeonGenerationManager.cancel(level, id);
    }

    private StagedDungeonGenerationManager.JobView view() {
        return StagedDungeonGenerationManager.job(level, id)
                .orElseThrow(() -> new IllegalStateException("Dungeon " + id + " was forgotten"));
    }

    private StagedDungeonLayout layout() {
        return StagedDungeonGenerationManager.layout(level, id)
                .orElseThrow(() -> new IllegalStateException("Dungeon " + id + " was forgotten"));
    }

    private static StagedDungeonPieceSpec startPiece(StagedDungeonLayout layout) {
        return layout.pieces().stream()
                .filter(StagedDungeonPieceSpec::startPiece)
                .findFirst()
                .orElse(layout.pieces().getFirst());
    }

    private boolean standable(BlockPos feet) {
        BlockPos floor = feet.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above())
                        .getCollisionShape(level, feet.above())
                        .isEmpty();
    }
}
