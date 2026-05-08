package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

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
        if (!DungeonGenerationProfiler.isActive()) {
            return delegate.place(structureTemplateManager, world, structureAccessor, chunkGenerator, pos, pivot, rotation, box, random, liquidSettings, keepJigsaws);
        }

        BoundingBox boundingBox = getBoundingBox(structureTemplateManager, pos, rotation);
        long placementStart = System.nanoTime();
        boolean placed = delegate.place(structureTemplateManager, world, structureAccessor, chunkGenerator, pos, pivot, rotation, box, random, liquidSettings, keepJigsaws);
        DungeonGenerationProfiler.recordPiece(variant, delegate.getTemplateLocation(), boundingBox, placed, System.nanoTime() - placementStart);
        return placed;
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
