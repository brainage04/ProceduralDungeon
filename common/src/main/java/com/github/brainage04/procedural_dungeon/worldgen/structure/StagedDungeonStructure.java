package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

public class StagedDungeonStructure extends Structure {
    public static final MapCodec<StagedDungeonStructure> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
                    Identifier.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(structure -> structure.startJigsawName),
                    Codec.intRange(0, 20).fieldOf("size").forGetter(structure -> structure.maxDepth),
                    HeightProvider.CODEC.fieldOf("start_height").forGetter(structure -> structure.startHeight),
                    Codec.BOOL.optionalFieldOf("use_expansion_hack", false).forGetter(structure -> structure.useExpansionHack),
                    Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(structure -> structure.projectStartToHeightmap),
                    PlacementHeightMode.CODEC.optionalFieldOf("placement_height_mode", PlacementHeightMode.HEIGHTMAP_OFFSET).forGetter(structure -> structure.placementHeightMode),
                    Codec.INT.optionalFieldOf("solid_density_min_y", 24).forGetter(structure -> structure.solidDensityMinY),
                    Codec.INT.optionalFieldOf("solid_density_max_y", 104).forGetter(structure -> structure.solidDensityMaxY),
                    Codec.INT.optionalFieldOf("solid_density_window", 24).forGetter(structure -> structure.solidDensityWindow),
                    Codec.INT.optionalFieldOf("solid_density_step", 4).forGetter(structure -> structure.solidDensityStep),
                    Codec.INT.optionalFieldOf("solid_density_horizontal_radius", 16).forGetter(structure -> structure.solidDensityHorizontalRadius),
                    JigsawStructure.MaxDistance.CODEC.fieldOf("max_distance_from_center").forGetter(structure -> structure.maxDistanceFromCenter),
                    LiquidSettings.CODEC.optionalFieldOf("liquid_settings", LiquidSettings.IGNORE_WATERLOGGING).forGetter(structure -> structure.liquidSettings)
            ).apply(instance, StagedDungeonStructure::new));

    private final Holder<StructureTemplatePool> startPool;
    private final Optional<Identifier> startJigsawName;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final PlacementHeightMode placementHeightMode;
    private final int solidDensityMinY;
    private final int solidDensityMaxY;
    private final int solidDensityWindow;
    private final int solidDensityStep;
    private final int solidDensityHorizontalRadius;
    private final JigsawStructure.MaxDistance maxDistanceFromCenter;
    private final LiquidSettings liquidSettings;

    public StagedDungeonStructure(
            StructureSettings settings,
            Holder<StructureTemplatePool> startPool,
            Optional<Identifier> startJigsawName,
            int maxDepth,
            HeightProvider startHeight,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            PlacementHeightMode placementHeightMode,
            int solidDensityMinY,
            int solidDensityMaxY,
            int solidDensityWindow,
            int solidDensityStep,
            int solidDensityHorizontalRadius,
            JigsawStructure.MaxDistance maxDistanceFromCenter,
            LiquidSettings liquidSettings
    ) {
        super(settings);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.placementHeightMode = placementHeightMode;
        this.solidDensityMinY = solidDensityMinY;
        this.solidDensityMaxY = solidDensityMaxY;
        this.solidDensityWindow = solidDensityWindow;
        this.solidDensityStep = solidDensityStep;
        this.solidDensityHorizontalRadius = solidDensityHorizontalRadius;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.liquidSettings = liquidSettings;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int y = startHeight.sample(
                context.random(),
                new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor())
        );
        if (placementHeightMode == PlacementHeightMode.SOLID_DENSITY) {
            long solidDensityStart = DungeonGenerationProfiler.start();
            y = selectSolidDensityY(context, chunkPos, y);
            if (solidDensityStart != 0L) {
                DungeonGenerationProfiler.recordSolidDensity(System.nanoTime() - solidDensityStart);
            }
        }
        BlockPos startPos = new BlockPos(chunkPos.getMinBlockX(), y, chunkPos.getMinBlockZ());

        Optional<StagedDungeonLayout> layout = StagedDungeonLayoutCompiler.compile(
                context,
                startPool,
                startJigsawName,
                maxDepth,
                startPos,
                useExpansionHack,
                projectStartToHeightmap,
                maxDistanceFromCenter,
                liquidSettings
        );

        if (layout.isEmpty()) {
            return Optional.empty();
        }

        StagedDungeonLayout generatedLayout = layout.get();
        StagedDungeonMarkerPiece marker = new StagedDungeonMarkerPiece(
                chunkPos,
                generatedLayout.boundingBox(),
                generatedLayout.pieces(),
                liquidSettings,
                generatedLayout.lockPlan()
        );
        return Optional.of(new GenerationStub(generatedLayout.locator(), builder -> builder.addPiece(marker)));
    }

    @Override
    public StructureType<?> type() {
        return ModStructureTypes.STAGED_DUNGEON;
    }

    private int selectSolidDensityY(GenerationContext context, ChunkPos chunkPos, int fallbackY) {
        int minY = Math.max(context.heightAccessor().getMinY() + 8, solidDensityMinY);
        int maxY = Math.min(context.heightAccessor().getMaxY() - solidDensityWindow - 8, solidDensityMaxY);
        if (minY > maxY) {
            return fallbackY;
        }

        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();
        int radius = Math.max(0, solidDensityHorizontalRadius);
        int columnStep = Math.max(4, radius / 2);
        int yStep = Math.max(1, solidDensityStep);

        int bestY = fallbackY;
        int bestScore = Integer.MIN_VALUE;
        for (int candidateY = minY; candidateY <= maxY; candidateY += yStep) {
            int score = solidDensityScore(context, centerX, centerZ, radius, columnStep, candidateY, solidDensityWindow);
            if (score > bestScore) {
                bestScore = score;
                bestY = candidateY;
            }
        }

        return bestY;
    }

    private static int solidDensityScore(
            GenerationContext context,
            int centerX,
            int centerZ,
            int radius,
            int columnStep,
            int minY,
            int height
    ) {
        int score = 0;
        for (int x = centerX - radius; x <= centerX + radius; x += columnStep) {
            for (int z = centerZ - radius; z <= centerZ + radius; z += columnStep) {
                NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
                for (int y = minY; y < minY + height; y++) {
                    BlockState state = column.getBlock(y);
                    if (!state.isAir() && state.getFluidState().isEmpty()) {
                        score++;
                    }
                }
            }
        }

        return score;
    }

    public enum PlacementHeightMode {
        HEIGHTMAP_OFFSET("heightmap_offset"),
        SOLID_DENSITY("solid_density");

        public static final Codec<PlacementHeightMode> CODEC = Codec.STRING.xmap(
                PlacementHeightMode::fromId,
                PlacementHeightMode::id
        );

        private final String id;

        PlacementHeightMode(String id) {
            this.id = id;
        }

        private String id() {
            return id;
        }

        private static PlacementHeightMode fromId(String id) {
            for (PlacementHeightMode mode : values()) {
                if (mode.id.equals(id)) {
                    return mode;
                }
            }

            throw new IllegalArgumentException("Unsupported placement height mode: " + id);
        }
    }
}
