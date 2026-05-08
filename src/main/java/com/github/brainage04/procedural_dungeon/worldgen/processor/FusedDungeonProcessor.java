package com.github.brainage04.procedural_dungeon.worldgen.processor;

import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonGenerationProfiler;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class FusedDungeonProcessor extends StructureProcessor {
    private static final Map<Block, Shape> INPUT_SHAPES = inputShapes();
    private static final BlockState[] NON_MOSSY_SLAB_REPLACEMENTS = new BlockState[] {
            Blocks.STONE_SLAB.defaultBlockState(),
            Blocks.STONE_BRICK_SLAB.defaultBlockState()
    };
    private static final BlockState CRACKED_STONE_BRICKS = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY_STONE_BRICKS = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY_STONE_BRICK_SLAB = Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState();
    private static final BlockState CRYING_OBSIDIAN = Blocks.CRYING_OBSIDIAN.defaultBlockState();

    public static final MapCodec<FusedDungeonProcessor> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    FusedRule.CODEC.listOf().listOf()
                            .fieldOf("pre_rule_groups")
                            .forGetter(processor -> processor.preRuleGroups),
                    FusedRule.CODEC.listOf().listOf()
                            .fieldOf("post_rule_groups")
                            .forGetter(processor -> processor.postRuleGroups),
                    Codec.FLOAT.fieldOf("age_chance")
                            .forGetter(processor -> processor.ageChance),
                    Codec.FLOAT.fieldOf("rot_chance")
                            .forGetter(processor -> processor.rotChance),
                    ShapeReplacements.CODEC.optionalFieldOf("theme_shapes")
                            .forGetter(processor -> processor.shapeReplacements),
                    Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC)
                            .optionalFieldOf("loot_table_replacements", Map.of())
                            .forGetter(processor -> processor.lootTableReplacements)
            ).apply(instance, FusedDungeonProcessor::new));

    private final List<List<FusedRule>> preRuleGroups;
    private final List<List<FusedRule>> postRuleGroups;
    private final float ageChance;
    private final float rotChance;
    private final Optional<ShapeReplacements> shapeReplacements;
    private final Map<Identifier, Identifier> lootTableReplacements;
    private final List<IndexedRuleGroup> preIndexedRuleGroups;
    private final List<IndexedRuleGroup> postIndexedRuleGroups;
    private final ShapeStates shapeStates;
    private final Map<String, String> lootTableReplacementStrings;
    private final boolean hasPreRules;
    private final boolean hasPostRules;
    private final boolean hasShapes;
    private final boolean hasLoot;

    public FusedDungeonProcessor(
            List<List<FusedRule>> preRuleGroups,
            List<List<FusedRule>> postRuleGroups,
            float ageChance,
            float rotChance,
            Optional<ShapeReplacements> shapeReplacements,
            Map<Identifier, Identifier> lootTableReplacements
    ) {
        this.preRuleGroups = preRuleGroups.stream()
                .map(List::copyOf)
                .toList();
        this.postRuleGroups = postRuleGroups.stream()
                .map(List::copyOf)
                .toList();
        this.ageChance = ageChance;
        this.rotChance = rotChance;
        this.shapeReplacements = shapeReplacements;
        this.lootTableReplacements = Map.copyOf(lootTableReplacements);
        this.preIndexedRuleGroups = this.preRuleGroups.stream()
                .map(IndexedRuleGroup::new)
                .toList();
        this.postIndexedRuleGroups = this.postRuleGroups.stream()
                .map(IndexedRuleGroup::new)
                .toList();
        this.shapeStates = shapeReplacements.map(ShapeStates::new).orElse(null);
        this.lootTableReplacementStrings = lootTableReplacements.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> entry.getKey().toString(),
                entry -> entry.getValue().toString()
        ));
        this.hasPreRules = !this.preIndexedRuleGroups.isEmpty();
        this.hasPostRules = !this.postIndexedRuleGroups.isEmpty();
        this.hasShapes = this.shapeStates != null;
        this.hasLoot = !this.lootTableReplacementStrings.isEmpty();
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
        StructureTemplate.StructureBlockInfo result = currentBlockInfo;
        if (hasPreRules) {
            result = applyRuleGroups(result, preIndexedRuleGroups);
        }
        result = applyAge(result, data.getRandom(result.pos()));
        result = applyRot(result, data.getRandom(result.pos()));
        if (result == null) {
            return null;
        }
        if (hasShapes) {
            result = applyThemeShapes(result);
        }
        if (hasPostRules) {
            result = applyRuleGroups(result, postIndexedRuleGroups);
        }
        if (result.nbt() == null && result.state().isAir() && world.getBlockState(result.pos()).isAir()) {
            return null;
        }
        return hasLoot ? applyLootAndBlockEntity(result, data) : result;
    }

    private StructureTemplate.StructureBlockInfo applyRuleGroups(
            StructureTemplate.StructureBlockInfo blockInfo,
            List<IndexedRuleGroup> ruleGroups
    ) {
        StructureTemplate.StructureBlockInfo result = blockInfo;
        for (IndexedRuleGroup group : ruleGroups) {
            result = group.apply(result);
        }
        return result;
    }

    private StructureTemplate.StructureBlockInfo applyAge(
            StructureTemplate.StructureBlockInfo blockInfo,
            RandomSource random
    ) {
        BlockState state = blockInfo.state();
        BlockState replacement = null;
        if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.STONE) || state.is(Blocks.CHISELED_STONE_BRICKS)) {
            replacement = maybeReplaceFullStoneBlock(random);
        } else if (state.is(BlockTags.STAIRS)) {
            replacement = maybeReplaceStairs(state, random);
        } else if (state.is(BlockTags.SLABS)) {
            replacement = maybeReplaceSlab(state, random);
        } else if (state.is(BlockTags.WALLS)) {
            replacement = maybeReplaceWall(state, random);
        } else if (state.is(Blocks.OBSIDIAN)) {
            replacement = maybeReplaceObsidian(random);
        }

        return replacement == null
                ? blockInfo
                : new StructureTemplate.StructureBlockInfo(blockInfo.pos(), replacement, blockInfo.nbt());
    }

    private StructureTemplate.StructureBlockInfo applyRot(
            StructureTemplate.StructureBlockInfo blockInfo,
            RandomSource random
    ) {
        return random.nextFloat() <= rotChance ? blockInfo : null;
    }

    private BlockState maybeReplaceFullStoneBlock(RandomSource random) {
        if (random.nextFloat() >= 0.5F) {
            return null;
        }

        BlockState[] nonMossy = new BlockState[] {
                CRACKED_STONE_BRICKS,
                randomFacingStairs(random, Blocks.STONE_BRICK_STAIRS)
        };
        BlockState[] mossy = new BlockState[] {
                MOSSY_STONE_BRICKS,
                randomFacingStairs(random, Blocks.MOSSY_STONE_BRICK_STAIRS)
        };
        return randomBlock(random, nonMossy, mossy);
    }

    private BlockState maybeReplaceStairs(BlockState state, RandomSource random) {
        if (random.nextFloat() >= 0.5F) {
            return null;
        }

        BlockState[] mossy = new BlockState[] {
                Blocks.MOSSY_STONE_BRICK_STAIRS.withPropertiesOf(state),
                MOSSY_STONE_BRICK_SLAB
        };
        return randomBlock(random, NON_MOSSY_SLAB_REPLACEMENTS, mossy);
    }

    private BlockState maybeReplaceSlab(BlockState state, RandomSource random) {
        return random.nextFloat() < ageChance ? Blocks.MOSSY_STONE_BRICK_SLAB.withPropertiesOf(state) : null;
    }

    private BlockState maybeReplaceWall(BlockState state, RandomSource random) {
        return random.nextFloat() < ageChance ? Blocks.MOSSY_STONE_BRICK_WALL.withPropertiesOf(state) : null;
    }

    private static BlockState maybeReplaceObsidian(RandomSource random) {
        return random.nextFloat() < 0.15F ? CRYING_OBSIDIAN : null;
    }

    private static BlockState randomFacingStairs(RandomSource random, Block block) {
        return block.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random))
                .setValue(StairBlock.HALF, Util.getRandom(Half.values(), random));
    }

    private BlockState randomBlock(RandomSource random, BlockState[] nonMossy, BlockState[] mossy) {
        return random.nextFloat() < ageChance ? randomBlock(random, mossy) : randomBlock(random, nonMossy);
    }

    private static BlockState randomBlock(RandomSource random, BlockState[] states) {
        return states[random.nextInt(states.length)];
    }

    private StructureTemplate.StructureBlockInfo applyThemeShapes(StructureTemplate.StructureBlockInfo blockInfo) {
        long start = DungeonGenerationProfiler.start();
        try {
            BlockState state = blockInfo.state();
            Shape shape = INPUT_SHAPES.get(state.getBlock());
            if (shape == null) {
                return blockInfo;
            }

            BlockState replacement = switch (shape) {
                case STAIR -> shapeStates.replaceStairs(state);
                case SLAB -> shapeStates.replaceSlab(state);
                case WALL -> shapeStates.replaceWall(state);
            };

            return new StructureTemplate.StructureBlockInfo(blockInfo.pos(), replacement, blockInfo.nbt());
        } finally {
            if (start != 0L) {
                DungeonGenerationProfiler.recordProcessor("procedural_dungeon:theme_shape_replacements", System.nanoTime() - start);
            }
        }
    }

    private StructureTemplate.StructureBlockInfo applyLootAndBlockEntity(
            StructureTemplate.StructureBlockInfo blockInfo,
            StructurePlaceSettings data
    ) {
        long start = DungeonGenerationProfiler.start();
        try {
            CompoundTag nbt = blockInfo.nbt();
            if (nbt == null) {
                return blockInfo;
            }

            if (!(blockInfo.state().getBlock() instanceof EntityBlock)) {
                return new StructureTemplate.StructureBlockInfo(blockInfo.pos(), blockInfo.state(), null);
            }

            String oldLootTable = nbt.getString("LootTable").orElse(null);
            if (oldLootTable == null) {
                return blockInfo;
            }

            String newLootTable = lootTableReplacementStrings.get(oldLootTable);
            if (newLootTable == null) {
                return blockInfo;
            }

            CompoundTag copy = nbt.copy();
            copy.putString("LootTable", newLootTable);
            copy.putLong("LootTableSeed", data.getRandom(blockInfo.pos()).nextLong());
            return new StructureTemplate.StructureBlockInfo(blockInfo.pos(), blockInfo.state(), copy);
        } finally {
            if (start != 0L) {
                DungeonGenerationProfiler.recordProcessor("procedural_dungeon:loot_tables_and_block_entities", System.nanoTime() - start);
            }
        }
    }

    private static Map<Block, Shape> inputShapes() {
        IdentityHashMap<Block, Shape> shapes = new IdentityHashMap<>();
        shapes.put(Blocks.COBBLESTONE_STAIRS, Shape.STAIR);
        shapes.put(Blocks.STONE_BRICK_STAIRS, Shape.STAIR);
        shapes.put(Blocks.MOSSY_STONE_BRICK_STAIRS, Shape.STAIR);
        shapes.put(Blocks.COBBLESTONE_SLAB, Shape.SLAB);
        shapes.put(Blocks.STONE_SLAB, Shape.SLAB);
        shapes.put(Blocks.STONE_BRICK_SLAB, Shape.SLAB);
        shapes.put(Blocks.MOSSY_STONE_BRICK_SLAB, Shape.SLAB);
        shapes.put(Blocks.COBBLESTONE_WALL, Shape.WALL);
        shapes.put(Blocks.MOSSY_STONE_BRICK_WALL, Shape.WALL);
        return Collections.unmodifiableMap(shapes);
    }

    private static BlockState defaultState(Identifier id) {
        return block(id).defaultBlockState();
    }

    private static Block block(Identifier id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("Unknown dungeon processor block: " + id);
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
        return ModStructureProcessorTypes.FUSED_DUNGEON_PROCESSOR;
    }

    public record FusedRule(Identifier input, float probability, Identifier output) {
        public static final Codec<FusedRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("input").forGetter(FusedRule::input),
                Codec.FLOAT.fieldOf("probability").forGetter(FusedRule::probability),
                Identifier.CODEC.fieldOf("output").forGetter(FusedRule::output)
        ).apply(instance, FusedRule::new));
    }

    public record ShapeReplacements(
            Identifier fallback,
            Optional<Identifier> stairs,
            Optional<Identifier> slab,
            Optional<Identifier> wall
    ) {
        public static final Codec<ShapeReplacements> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("fallback").forGetter(ShapeReplacements::fallback),
                Identifier.CODEC.optionalFieldOf("stairs").forGetter(ShapeReplacements::stairs),
                Identifier.CODEC.optionalFieldOf("slab").forGetter(ShapeReplacements::slab),
                Identifier.CODEC.optionalFieldOf("wall").forGetter(ShapeReplacements::wall)
        ).apply(instance, ShapeReplacements::new));
    }

    private record IndexedRuleGroup(Map<Block, List<CompiledRule>> rulesByInput) {
        private IndexedRuleGroup(List<FusedRule> rules) {
            this(indexRules(rules));
        }

        private StructureTemplate.StructureBlockInfo apply(StructureTemplate.StructureBlockInfo blockInfo) {
            List<CompiledRule> rules = rulesByInput.get(blockInfo.state().getBlock());
            if (rules == null) {
                return blockInfo;
            }

            long seed = Mth.getSeed(blockInfo.pos());
            for (int i = 0; i < rules.size(); i++) {
                CompiledRule rule = rules.get(i);
                if (unitFloat(seed, i) < rule.probability()) {
                    return new StructureTemplate.StructureBlockInfo(blockInfo.pos(), rule.outputState(), blockInfo.nbt());
                }
            }

            return blockInfo;
        }

        private static Map<Block, List<CompiledRule>> indexRules(List<FusedRule> rules) {
            IdentityHashMap<Block, List<CompiledRule>> grouped = new IdentityHashMap<>();
            for (FusedRule rule : rules) {
                grouped.computeIfAbsent(block(rule.input()), ignored -> new ArrayList<>())
                        .add(new CompiledRule(rule.probability(), defaultState(rule.output())));
            }
            grouped.replaceAll((ignored, value) -> List.copyOf(value));
            return Collections.unmodifiableMap(grouped);
        }
    }

    private record CompiledRule(float probability, BlockState outputState) {
    }

    private static float unitFloat(long seed, int salt) {
        long mixed = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed = mixed ^ (mixed >>> 31);
        return (mixed >>> 40) * 0x1.0p-24F;
    }

    private record ShapeStates(
            BlockState fallbackState,
            BlockState stairsState,
            BlockState slabState,
            BlockState wallState
    ) {
        private ShapeStates(ShapeReplacements replacements) {
            this(
                    defaultState(replacements.fallback()),
                    replacements.stairs().map(FusedDungeonProcessor::defaultState).orElse(null),
                    replacements.slab().map(FusedDungeonProcessor::defaultState).orElse(null),
                    replacements.wall().map(FusedDungeonProcessor::defaultState).orElse(null)
            );
        }

        private BlockState replaceStairs(BlockState input) {
            return stairsState == null ? fallbackState : copyStairProperties(input, stairsState);
        }

        private BlockState replaceSlab(BlockState input) {
            return slabState == null ? fallbackState : copySlabProperties(input, slabState);
        }

        private BlockState replaceWall(BlockState input) {
            return wallState == null ? fallbackState : copyWallProperties(input, wallState);
        }
    }

    private enum Shape {
        STAIR,
        SLAB,
        WALL
    }
}
