package com.github.brainage04.procedural_dungeon.worldgen.processor;

import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonGenerationProfiler;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class ThemeShapeReplacementProcessor extends StructureProcessor {
    private static final Set<Block> STAIR_INPUTS = Set.of(
            Blocks.COBBLESTONE_STAIRS,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.MOSSY_STONE_BRICK_STAIRS
    );
    private static final Set<Block> SLAB_INPUTS = Set.of(
            Blocks.COBBLESTONE_SLAB,
            Blocks.STONE_SLAB,
            Blocks.STONE_BRICK_SLAB,
            Blocks.MOSSY_STONE_BRICK_SLAB
    );
    private static final Set<Block> WALL_INPUTS = Set.of(
            Blocks.COBBLESTONE_WALL,
            Blocks.MOSSY_STONE_BRICK_WALL
    );

    public static final MapCodec<ThemeShapeReplacementProcessor> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Identifier.CODEC.fieldOf("fallback").forGetter(processor -> processor.fallback),
                    Identifier.CODEC.optionalFieldOf("stairs").forGetter(processor -> processor.stairs),
                    Identifier.CODEC.optionalFieldOf("slab").forGetter(processor -> processor.slab),
                    Identifier.CODEC.optionalFieldOf("wall").forGetter(processor -> processor.wall)
            ).apply(instance, ThemeShapeReplacementProcessor::new));

    private final Identifier fallback;
    private final Optional<Identifier> stairs;
    private final Optional<Identifier> slab;
    private final Optional<Identifier> wall;

    public ThemeShapeReplacementProcessor(
            Identifier fallback,
            Optional<Identifier> stairs,
            Optional<Identifier> slab,
            Optional<Identifier> wall
    ) {
        this.fallback = fallback;
        this.stairs = stairs;
        this.slab = slab;
        this.wall = wall;
        block(fallback);
        stairs.ifPresent(ThemeShapeReplacementProcessor::block);
        slab.ifPresent(ThemeShapeReplacementProcessor::block);
        wall.ifPresent(ThemeShapeReplacementProcessor::block);
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader world,
            BlockPos pos,
            BlockPos pivot,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo currentBlockInfo,
            StructurePlaceSettings data
    ) {
        long start = DungeonGenerationProfiler.start();
        try {
            BlockState state = currentBlockInfo.state();
            Block block = state.getBlock();
            BlockState replacement = null;

            if (STAIR_INPUTS.contains(block)) {
                replacement = replaceStairs(state);
            } else if (SLAB_INPUTS.contains(block)) {
                replacement = replaceSlab(state);
            } else if (WALL_INPUTS.contains(block)) {
                replacement = replaceWall(state);
            }

            if (replacement == null) {
                return currentBlockInfo;
            }

            return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), replacement, currentBlockInfo.nbt());
        } finally {
            if (start != 0L) {
                DungeonGenerationProfiler.recordProcessor("procedural_dungeon:theme_shape_replacements", System.nanoTime() - start);
            }
        }
    }

    private BlockState replaceStairs(BlockState input) {
        return stairs
                .map(ThemeShapeReplacementProcessor::defaultState)
                .map(output -> copyStairProperties(input, output))
                .orElseGet(this::fallbackState);
    }

    private BlockState replaceSlab(BlockState input) {
        return slab
                .map(ThemeShapeReplacementProcessor::defaultState)
                .map(output -> copySlabProperties(input, output))
                .orElseGet(this::fallbackState);
    }

    private BlockState replaceWall(BlockState input) {
        return wall
                .map(ThemeShapeReplacementProcessor::defaultState)
                .map(output -> copyWallProperties(input, output))
                .orElseGet(this::fallbackState);
    }

    private BlockState fallbackState() {
        return defaultState(fallback);
    }

    private static BlockState defaultState(Identifier id) {
        return block(id).defaultBlockState();
    }

    private static Block block(Identifier id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("Unknown theme shape replacement block: " + id);
        }
        return BuiltInRegistries.BLOCK.getValue(id);
    }

    private static BlockState copyStairProperties(BlockState input, BlockState output) {
        output = copyProperty(input, output, StairBlock.FACING);
        output = copyProperty(input, output, StairBlock.HALF);
        output = copyProperty(input, output, StairBlock.SHAPE);
        return copyProperty(input, output, StairBlock.WATERLOGGED);
    }

    private static BlockState copySlabProperties(BlockState input, BlockState output) {
        output = copyProperty(input, output, SlabBlock.TYPE);
        return copyProperty(input, output, SlabBlock.WATERLOGGED);
    }

    private static BlockState copyWallProperties(BlockState input, BlockState output) {
        output = copyWallSide(input, output, Direction.NORTH);
        output = copyWallSide(input, output, Direction.EAST);
        output = copyWallSide(input, output, Direction.SOUTH);
        output = copyWallSide(input, output, Direction.WEST);
        output = copyProperty(input, output, WallBlock.UP);
        output = copyProperty(input, output, WallBlock.WATERLOGGED);
        return copyProperty(input, output, CrossCollisionBlock.WATERLOGGED);
    }

    private static BlockState copyWallSide(BlockState input, BlockState output, Direction direction) {
        WallSide side = input.getValue(WallBlock.PROPERTY_BY_DIRECTION.get(direction));

        if (output.hasProperty(WallBlock.PROPERTY_BY_DIRECTION.get(direction))) {
            return output.setValue(WallBlock.PROPERTY_BY_DIRECTION.get(direction), side);
        }

        if (output.hasProperty(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(direction))) {
            return output.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(direction), side != WallSide.NONE);
        }

        return output;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(
            BlockState input,
            BlockState output,
            net.minecraft.world.level.block.state.properties.Property<T> property
    ) {
        if (input.hasProperty(property) && output.hasProperty(property)) {
            return output.setValue(property, input.getValue(property));
        }

        return output;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructureProcessorTypes.THEME_SHAPE_REPLACEMENTS;
    }
}
