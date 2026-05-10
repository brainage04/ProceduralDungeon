package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.worldgen.processor.FusedDungeonProcessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.TagValueInput;

public class VariantSinglePoolElement extends StructurePoolElement {
    private static final Identifier EMPTY_POOL = Identifier.withDefaultNamespace("empty");
    private static final Comparator<StructureTemplate.JigsawBlockInfo> HIGHEST_SELECTION_PRIORITY_FIRST =
            Comparator.comparingInt(StructureTemplate.JigsawBlockInfo::selectionPriority).reversed();

    public static final MapCodec<VariantSinglePoolElement> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    SinglePoolElement.CODEC.forGetter(element -> element.delegate),
                    Identifier.CODEC.fieldOf("variant").forGetter(element -> element.variant),
                    Codec.INT.fieldOf("spawner_tier").forGetter(element -> element.spawnerTier),
                    Codec.INT.optionalFieldOf("branch_limit", Integer.MAX_VALUE).forGetter(element -> element.branchLimit)
            ).apply(instance, VariantSinglePoolElement::new));

    private final SinglePoolElement delegate;
    private final Identifier variant;
    private final int spawnerTier;
    private final int branchLimit;
    private final Map<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> poolReplacementCache =
            new ConcurrentHashMap<>();
    private final Map<Rotation, List<StructureTemplate.JigsawBlockInfo>> jigsawCache = new ConcurrentHashMap<>();
    private final Map<Rotation, BoundingBox> boundingBoxCache = new ConcurrentHashMap<>();
    private final Map<Rotation, List<StructureTemplate.StructureBlockInfo>> dataMarkerCache = new ConcurrentHashMap<>();

    public VariantSinglePoolElement(SinglePoolElement delegate, Identifier variant, int spawnerTier, int branchLimit) {
        super(delegate.getProjection());
        this.delegate = delegate;
        this.variant = variant;
        this.spawnerTier = spawnerTier;
        this.branchLimit = branchLimit;
    }

    public Identifier templateLocation() {
        return delegate.getTemplateLocation();
    }

    public Identifier variant() {
        return variant;
    }

    public int spawnerTier() {
        return spawnerTier;
    }

    @Override
    public Vec3i getSize(StructureTemplateManager structureTemplateManager, Rotation rotation) {
        return delegate.getSize(structureTemplateManager, rotation);
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(
            StructureTemplateManager structureTemplateManager,
            BlockPos pos,
            Rotation rotation,
            RandomSource random
    ) {
        List<StructureTemplate.JigsawBlockInfo> jigsaws = shuffledTranslatedJigsaws(structureTemplateManager, pos, rotation, random);
        int expandableJigsaws = countExpandable(jigsaws);

        if (branchLimit == Integer.MAX_VALUE || pos.equals(BlockPos.ZERO)) {
            DungeonGenerationProfiler.recordJigsaws(jigsaws.size(), expandableJigsaws, jigsaws.size(), expandableJigsaws);
            return jigsaws;
        }

        long branchStart = DungeonGenerationProfiler.start();
        List<StructureTemplate.JigsawBlockInfo> limited = new ArrayList<>(jigsaws.size());
        int branches = 0;
        int keptExpandable = 0;
        for (StructureTemplate.JigsawBlockInfo info : jigsaws) {
            if (!isExpandable(info) || branches++ < branchLimit) {
                limited.add(info);
                if (isExpandable(info)) {
                    keptExpandable++;
                }
            }
        }
        if (branchStart != 0L) {
            DungeonGenerationProfiler.recordBranchLimit(System.nanoTime() - branchStart);
        }

        DungeonGenerationProfiler.recordJigsaws(jigsaws.size(), expandableJigsaws, limited.size(), keptExpandable);
        return limited;
    }

    private List<StructureTemplate.JigsawBlockInfo> shuffledTranslatedJigsaws(
            StructureTemplateManager structureTemplateManager,
            BlockPos pos,
            Rotation rotation,
            RandomSource random
    ) {
        List<StructureTemplate.JigsawBlockInfo> base = cachedJigsaws(structureTemplateManager, rotation);
        List<StructureTemplate.JigsawBlockInfo> translated = pos.equals(BlockPos.ZERO)
                ? new ArrayList<>(base)
                : translateJigsaws(base, pos);
        Util.shuffle(translated, random);
        translated.sort(HIGHEST_SELECTION_PRIORITY_FIRST);
        return translated;
    }

    private List<StructureTemplate.JigsawBlockInfo> cachedJigsaws(StructureTemplateManager structureTemplateManager, Rotation rotation) {
        return jigsawCache.computeIfAbsent(rotation, ignored -> {
            long jigsawStart = DungeonGenerationProfiler.start();
            List<StructureTemplate.JigsawBlockInfo> delegateJigsaws = structureTemplateManager
                    .getOrCreate(delegate.getTemplateLocation())
                    .getJigsaws(BlockPos.ZERO, rotation);
            if (jigsawStart != 0L) {
                DungeonGenerationProfiler.recordJigsawBlockLookup(System.nanoTime() - jigsawStart);
            }

            long replacementStart = DungeonGenerationProfiler.start();
            ArrayList<StructureTemplate.JigsawBlockInfo> replacedJigsaws = null;
            for (int i = 0; i < delegateJigsaws.size(); i++) {
                StructureTemplate.JigsawBlockInfo original = delegateJigsaws.get(i);
                StructureTemplate.JigsawBlockInfo replaced = replacePool(original);
                if (replacedJigsaws != null) {
                    replacedJigsaws.add(replaced);
                } else if (replaced != original) {
                    replacedJigsaws = new ArrayList<>(delegateJigsaws.size());
                    for (int previous = 0; previous < i; previous++) {
                        replacedJigsaws.add(delegateJigsaws.get(previous));
                    }
                    replacedJigsaws.add(replaced);
                }
            }
            if (replacementStart != 0L) {
                DungeonGenerationProfiler.recordPoolReplacement(System.nanoTime() - replacementStart);
            }

            return List.copyOf(replacedJigsaws == null ? delegateJigsaws : replacedJigsaws);
        });
    }

    private static ArrayList<StructureTemplate.JigsawBlockInfo> translateJigsaws(
            List<StructureTemplate.JigsawBlockInfo> jigsaws,
            BlockPos offset
    ) {
        ArrayList<StructureTemplate.JigsawBlockInfo> translated = new ArrayList<>(jigsaws.size());
        for (StructureTemplate.JigsawBlockInfo jigsaw : jigsaws) {
            StructureTemplate.StructureBlockInfo info = jigsaw.info();
            translated.add(jigsaw.withInfo(new StructureTemplate.StructureBlockInfo(
                    info.pos().offset(offset),
                    info.state(),
                    info.nbt()
            )));
        }
        return translated;
    }

    private static int countExpandable(List<StructureTemplate.JigsawBlockInfo> jigsaws) {
        int expandable = 0;
        for (StructureTemplate.JigsawBlockInfo jigsaw : jigsaws) {
            if (isExpandable(jigsaw)) {
                expandable++;
            }
        }
        return expandable;
    }

    private static boolean isExpandable(StructureTemplate.JigsawBlockInfo info) {
        return !info.pool().identifier().equals(EMPTY_POOL);
    }

    private StructureTemplate.JigsawBlockInfo replacePool(StructureTemplate.JigsawBlockInfo info) {
        ResourceKey<StructureTemplatePool> pool = info.pool();
        ResourceKey<StructureTemplatePool> replacement = poolReplacementCache.computeIfAbsent(pool, this::replacementPool);
        if (replacement == pool) {
            return info;
        }

        return new StructureTemplate.JigsawBlockInfo(
                info.info(),
                info.jointType(),
                info.name(),
                replacement,
                info.target(),
                info.placementPriority(),
                info.selectionPriority()
        );
    }

    private ResourceKey<StructureTemplatePool> replacementPool(ResourceKey<StructureTemplatePool> pool) {
        Identifier replacement = DungeonJigsawPoolReplacements.getReplacement(pool.identifier(), variant, spawnerTier);
        if (replacement.equals(pool.identifier())) {
            return pool;
        }

        return ResourceKey.create(Registries.TEMPLATE_POOL, replacement);
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation) {
        BoundingBox base = boundingBoxCache.computeIfAbsent(rotation, ignored -> {
            long start = DungeonGenerationProfiler.start();
            BoundingBox box = delegate.getBoundingBox(structureTemplateManager, BlockPos.ZERO, rotation);
            if (start != 0L) {
                DungeonGenerationProfiler.recordBoundingBoxLookup(System.nanoTime() - start);
            }
            return box;
        });
        return base.moved(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean place(
            StructureTemplateManager structureTemplateManager,
            WorldGenLevel world,
            StructureManager structureAccessor,
            ChunkGenerator chunkGenerator,
            BlockPos pos,
            BlockPos pivot,
            Rotation rotation,
            BoundingBox box,
            RandomSource random,
            LiquidSettings liquidSettings,
            boolean keepJigsaws
    ) {
        BoundingBox boundingBox = getBoundingBox(structureTemplateManager, pos, rotation);
        long placementStart = System.nanoTime();
        boolean placed = placeFast(
                structureTemplateManager,
                world,
                structureAccessor,
                chunkGenerator,
                pos,
                pivot,
                rotation,
                box,
                random,
                liquidSettings,
                keepJigsaws
        );
        if (DungeonGenerationProfiler.isActive()) {
            DungeonGenerationProfiler.recordPiece(variant, delegate.getTemplateLocation(), boundingBox, placed, System.nanoTime() - placementStart);
        }
        return placed;
    }

    private boolean placeFast(
            StructureTemplateManager structureTemplateManager,
            WorldGenLevel world,
            StructureManager structureAccessor,
            ChunkGenerator chunkGenerator,
            BlockPos pos,
            BlockPos pivot,
            Rotation rotation,
            BoundingBox box,
            RandomSource random,
            LiquidSettings liquidSettings,
            boolean keepJigsaws
    ) {
        StructureTemplate template = structureTemplateManager.getOrCreate(delegate.getTemplateLocation());
        StructurePlaceSettings settings = delegate.getSettings(rotation, box, liquidSettings, keepJigsaws);
        if (!canUseFastPlacement(template, settings)) {
            return delegate.place(structureTemplateManager, world, structureAccessor, chunkGenerator, pos, pivot, rotation, box, random, liquidSettings, keepJigsaws);
        }

        List<StructureTemplate.StructureBlockInfo> blocks = settings.getRandomPalette(template.palettes, pos).blocks();
        if (blocks.isEmpty() || template.getSize().getX() < 1 || template.getSize().getY() < 1 || template.getSize().getZ() < 1) {
            return false;
        }

        BoundingBox boundingBox = settings.getBoundingBox();
        try (ProblemReporter.ScopedCollector problems = new ProblemReporter.ScopedCollector(ProceduralDungeon.LOGGER)) {
            placeProcessedBlocks(
                    world,
                    settings,
                    processBlockInfos(world, pos, pivot, settings, blocks, spawnerTier),
                    boundingBox,
                    random,
                    problems
            );
        }

        List<StructureTemplate.StructureBlockInfo> dataMarkerBlocks = getDataMarkerBlocks(
                structureTemplateManager,
                template,
                pos,
                rotation
        );
        if (!dataMarkerBlocks.isEmpty()) {
            List<StructureTemplate.StructureBlockInfo> dataMarkers = StructureTemplate.processBlockInfos(
                    world,
                    pos,
                    pivot,
                    settings,
                    dataMarkerBlocks
            );
            for (StructureTemplate.StructureBlockInfo marker : dataMarkers) {
                handleDataMarker(world, marker, pos, rotation, random, box);
            }
        }
        return true;
    }

    private List<StructureTemplate.StructureBlockInfo> getDataMarkerBlocks(
            StructureTemplateManager structureTemplateManager,
            StructureTemplate template,
            BlockPos pos,
            Rotation rotation
    ) {
        if (template.palettes.size() == 1) {
            return dataMarkerCache.computeIfAbsent(
                    rotation,
                    key -> List.copyOf(delegate.getDataMarkers(structureTemplateManager, BlockPos.ZERO, key, false))
            );
        }
        return delegate.getDataMarkers(structureTemplateManager, pos, rotation, false);
    }

    private static List<StructureTemplate.StructureBlockInfo> processBlockInfos(
            WorldGenLevel world,
            BlockPos pos,
            BlockPos pivot,
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> blocks,
            int spawnerTier
    ) {
        List<StructureProcessor> processors = settings.getProcessors();
        ArrayList<StructureTemplate.StructureBlockInfo> originalBlocks = new ArrayList<>(blocks.size());
        List<StructureTemplate.StructureBlockInfo> processedBlocks = new ArrayList<>(blocks.size());
        for (StructureTemplate.StructureBlockInfo original : blocks) {
            BlockPos transformedPos = StructureTemplate.calculateRelativePosition(settings, original.pos()).offset(pos);
            CompoundTag nbt = original.nbt() == null ? null : original.nbt().copy();
            if (FusedDungeonProcessor.isSpawnerMarker(original.state(), nbt)) {
                nbt.putInt(FusedDungeonProcessor.SPAWNER_TIER_TAG, spawnerTier);
            }
            StructureTemplate.StructureBlockInfo current = new StructureTemplate.StructureBlockInfo(
                    transformedPos,
                    original.state(),
                    nbt
            );
            for (StructureProcessor processor : processors) {
                if (current == null) {
                    break;
                }
                current = processor.processBlock(world, pos, pivot, original, current, settings);
            }
            if (current != null) {
                originalBlocks.add(original);
                processedBlocks.add(current);
            }
        }

        for (StructureProcessor processor : processors) {
            processedBlocks = processor.finalizeProcessing(world, pos, pivot, originalBlocks, processedBlocks, settings);
        }
        return processedBlocks;
    }

    private static void placeProcessedBlocks(
            WorldGenLevel world,
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> blocks,
            BoundingBox boundingBox,
            RandomSource random,
            ProblemReporter.ScopedCollector problems
    ) {
        ArrayList<BlockPos> nbtPositions = null;
        for (StructureTemplate.StructureBlockInfo blockInfo : blocks) {
            nbtPositions = placeProcessedBlock(world, settings, blockInfo, boundingBox, random, problems, nbtPositions);
        }
        markBlockEntitiesChanged(world, nbtPositions);
    }

    private static ArrayList<BlockPos> placeProcessedBlock(
            WorldGenLevel world,
            StructurePlaceSettings settings,
            StructureTemplate.StructureBlockInfo blockInfo,
            BoundingBox boundingBox,
            RandomSource random,
            ProblemReporter.ScopedCollector problems,
            ArrayList<BlockPos> nbtPositions
    ) {
        BlockPos blockPos = blockInfo.pos();
        if (boundingBox != null && !boundingBox.isInside(blockPos)) {
            return nbtPositions;
        }

        BlockState state = blockInfo.state()
                .mirror(settings.getMirror())
                .rotate(settings.getRotation());
        CompoundTag nbt = blockInfo.nbt();
        if (nbt != null) {
            world.setBlock(blockPos, Blocks.BARRIER.defaultBlockState(), 820);
        }
        if (!world.setBlock(blockPos, state, 18) || nbt == null) {
            return nbtPositions;
        }

        if (nbtPositions == null) {
            nbtPositions = new ArrayList<>();
        }
        nbtPositions.add(blockPos);
        BlockEntity blockEntity = world.getBlockEntity(blockPos);
        if (blockEntity == null) {
            return nbtPositions;
        }
        if (!SharedConstants.DEBUG_STRUCTURE_EDIT_MODE && blockEntity instanceof RandomizableContainer) {
            nbt.putLong("LootTableSeed", random.nextLong());
        }
        blockEntity.loadWithComponents(TagValueInput.create(
                problems.forChild(blockEntity.problemPath()),
                world.registryAccess(),
                nbt
        ));
        return nbtPositions;
    }

    private static void markBlockEntitiesChanged(WorldGenLevel world, List<BlockPos> nbtPositions) {
        if (nbtPositions == null) {
            return;
        }
        for (BlockPos blockPos : nbtPositions) {
            BlockEntity blockEntity = world.getBlockEntity(blockPos);
            if (blockEntity != null) {
                blockEntity.setChanged();
            }
        }
    }

    private static boolean canUseFastPlacement(StructureTemplate template, StructurePlaceSettings settings) {
        return settings.getKnownShape()
                && !settings.shouldApplyWaterlogging()
                && (settings.isIgnoreEntities() || template.entityInfoList.isEmpty());
    }

    @Override
    public StructurePoolElement setProjection(StructureTemplatePool.Projection projection) {
        delegate.setProjection(projection);
        return super.setProjection(projection);
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return ModStructurePoolElementTypes.VARIANT_SINGLE_POOL_ELEMENT;
    }

    @Override
    public String toString() {
        return "VariantSingle[" + delegate + " -> " + variant + ", branches <= " + branchLimit + "]";
    }
}
