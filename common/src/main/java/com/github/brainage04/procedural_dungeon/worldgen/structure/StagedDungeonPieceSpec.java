package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

public record StagedDungeonPieceSpec(
        StructurePoolElement element,
        BlockPos position,
        Rotation rotation,
        BoundingBox boundingBox,
        int groundLevelDelta,
        boolean startPiece
) {
    private static final Codec<Rotation> ROTATION_CODEC = Rotation.CODEC.withAlternative(
            Codec.STRING.xmap(Rotation::valueOf, Rotation::name)
    );

    /**
     * Encodes pool elements, so it needs registry ops (SavedData and structure pieces provide them).
     */
    public static final Codec<StagedDungeonPieceSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StructurePoolElement.CODEC.fieldOf("element").forGetter(piece -> {
                DungeonGenerationProfiler.recordPieceEncode();
                return piece.element();
            }),
            Codec.INT.optionalFieldOf("x", 0).forGetter(piece -> piece.position().getX()),
            Codec.INT.optionalFieldOf("y", 0).forGetter(piece -> piece.position().getY()),
            Codec.INT.optionalFieldOf("z", 0).forGetter(piece -> piece.position().getZ()),
            ROTATION_CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(StagedDungeonPieceSpec::rotation),
            BoundingBox.CODEC.fieldOf("bounding_box").forGetter(StagedDungeonPieceSpec::boundingBox),
            Codec.INT.optionalFieldOf("ground_level_delta").forGetter(piece -> Optional.of(piece.groundLevelDelta())),
            Codec.BOOL.optionalFieldOf("start_piece", false).forGetter(StagedDungeonPieceSpec::startPiece)
    ).apply(instance, (element, x, y, z, rotation, box, groundLevelDelta, startPiece) -> new StagedDungeonPieceSpec(
            element,
            new BlockPos(x, y, z),
            rotation,
            box,
            groundLevelDelta.orElseGet(element::getGroundLevelDelta),
            startPiece
    )));

    public StagedDungeonPieceSpec(
            StructurePoolElement element,
            BlockPos position,
            Rotation rotation,
            BoundingBox boundingBox,
            int groundLevelDelta
    ) {
        this(element, position, rotation, boundingBox, groundLevelDelta, false);
    }
}
