package com.github.brainage04.procedural_dungeon.command;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class StructureGalleryCommand {
    private static final String AUTOBUILD_PROPERTY = "procedural_dungeon.structureGallery.autobuild";
    private static final String SHUTDOWN_AFTER_BUILD_PROPERTY = "procedural_dungeon.structureGallery.shutdownAfterBuild";
    private static final String MARKER_FILE = "procedural_dungeon_structure_gallery_built.marker";
    private static final List<Path> SOURCE_STRUCTURE_ROOTS = List.of(
            Path.of("src/main/resources/data/procedural_dungeon/structure"),
            Path.of("src/main/generated/data/procedural_dungeon/structure")
    );
    private static final int DEFAULT_Y = -60;
    private static final int DEFAULT_SPACING = 4;
    private static final int CLEAR_MARGIN = 8;
    private static final int CLEAR_HEIGHT_EXTRA = 8;
    private static final int STRUCTURE_BLOCK_MAX_AXIS = 48;

    public static void initialize(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("structuregallery")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("build")
                        .executes(context -> build(context.getSource(), DEFAULT_SPACING))
                        .then(argument("spacing", IntegerArgumentType.integer(0, 256))
                                .executes(context -> build(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "spacing")
                                ))
                        )
                )
                .then(literal("save")
                        .executes(context -> save(context.getSource()))
                )
        );
    }

    public static void initializeAutobuild() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!Boolean.getBoolean(AUTOBUILD_PROPERTY)) {
                return;
            }

            Path marker = markerPath(server);
            if (Files.exists(marker)) {
                int expectedStructures = baseStructureSourcePaths().size();
                Optional<Integer> markerCount = readMarkerCount(marker);
                if (markerCount.isPresent() && markerCount.get() == expectedStructures) {
                    ProceduralDungeon.LOGGER.info("Structure gallery already exists; skipping autobuild.");
                    shutdownAfterBuild(server);
                    return;
                }

                ProceduralDungeon.LOGGER.info(
                        "Structure gallery marker is stale; rebuilding gallery for {} structures.",
                        expectedStructures
                );
            }

            try {
                BuildResult result = build(server.overworld(), DEFAULT_SPACING);
                writeMarker(marker, result);
                ProceduralDungeon.LOGGER.info(
                        "Built structure gallery with {} structures at spacing {}.",
                        result.count(),
                        result.spacing()
                );
                shutdownAfterBuild(server);
            } catch (Exception e) {
                ProceduralDungeon.LOGGER.error("Failed to autobuild structure gallery.", e);
            }
        });
    }

    private static int build(CommandSourceStack source, int spacing) {
        try {
            BuildResult result = build(source.getLevel(), spacing);
            writeMarker(markerPath(source.getServer()), result);

            source.sendSuccess(() -> Component.literal(
                    "Built structure gallery with %d structures at Y=%d and spacing %d."
                            .formatted(result.count(), DEFAULT_Y, result.spacing())
            ), true);
            return result.count();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to build structure gallery: " + e.getMessage()));
            ProceduralDungeon.LOGGER.error("Failed to build structure gallery.", e);
            return 0;
        }
    }

    private static BuildResult build(ServerLevel world, int spacing) {
        StructureTemplateManager templateManager = world.getStructureManager();
        List<TemplateEntry> templates = baseTemplates(templateManager);
        if (templates.isEmpty()) {
            throw new IllegalStateException("No base procedural dungeon templates found.");
        }

        BlockPos base = new BlockPos(0, DEFAULT_Y, 0);
        int columns = (int) Math.ceil(Math.sqrt(templates.size()));
        int[] columnWidths = new int[columns];
        int[] rowDepths = new int[(int) Math.ceil(templates.size() / (double) columns)];

        for (int i = 0; i < templates.size(); i++) {
            Vec3i size = templates.get(i).template().getSize();
            int col = i % columns;
            int row = i / columns;
            columnWidths[col] = Math.max(columnWidths[col], cellWidth(size.getX()));
            rowDepths[row] = Math.max(rowDepths[row], cellWidth(size.getZ()));
        }

        for (int i = 0; i < templates.size(); i++) {
            TemplateEntry entry = templates.get(i);
            int col = i % columns;
            int row = i / columns;
            BlockPos origin = base.offset(
                    offsetForIndex(columnWidths, col, spacing) + CLEAR_MARGIN,
                    0,
                    offsetForIndex(rowDepths, row, spacing) + CLEAR_MARGIN
            );
            placeGalleryCell(world, origin, entry);
        }

        return new BuildResult(templates.size(), spacing);
    }

    private static void placeGalleryCell(ServerLevel world, BlockPos origin, TemplateEntry entry) {
        Vec3i size = entry.template().getSize();
        ProceduralDungeon.LOGGER.info(
                "Placing gallery structure '{}' at {} with dimensions {}x{}x{}.",
                entry.id(),
                origin.toShortString(),
                size.getX(),
                size.getY(),
                size.getZ()
        );
        if (size.getX() > STRUCTURE_BLOCK_MAX_AXIS || size.getY() > STRUCTURE_BLOCK_MAX_AXIS || size.getZ() > STRUCTURE_BLOCK_MAX_AXIS) {
            ProceduralDungeon.LOGGER.warn(
                    "Gallery structure '{}' exceeds the vanilla Structure Block axis limit of {} blocks: {}x{}x{}.",
                    entry.id(),
                    STRUCTURE_BLOCK_MAX_AXIS,
                    size.getX(),
                    size.getY(),
                    size.getZ()
            );
        }
        BlockPos clearMin = origin.offset(-CLEAR_MARGIN, -1, -CLEAR_MARGIN);
        BlockPos clearMax = origin.offset(
                Math.max(size.getX(), 1) + CLEAR_MARGIN,
                Math.max(size.getY(), 1) + CLEAR_HEIGHT_EXTRA,
                Math.max(size.getZ(), 1) + CLEAR_MARGIN
        );
        loadChunks(world, clearMin, clearMax);
        fill(world, clearMin, clearMax, Blocks.AIR.defaultBlockState());

        BlockPos floorMin = origin.offset(-2, -1, -2);
        BlockPos floorMax = origin.offset(Math.max(size.getX(), 1) + 1, -1, Math.max(size.getZ(), 1) + 1);
        fill(world, floorMin, floorMax, Blocks.SMOOTH_STONE.defaultBlockState());

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING)
                .setFinalizeEntities(true);
        entry.template().placeInWorld(world, origin, origin, settings, world.getRandom(), 3);

        placeStructureBlock(world, origin.offset(-2, 0, -2), origin, entry.id(), size);
    }

    private static int offsetForIndex(int[] sizes, int index, int spacing) {
        int offset = 0;
        for (int i = 0; i < index; i++) {
            offset += sizes[i] + spacing;
        }

        return offset;
    }

    private static int cellWidth(int structureAxisSize) {
        return Math.max(structureAxisSize, 1) + (CLEAR_MARGIN * 2);
    }

    private static void placeStructureBlock(
            ServerLevel world,
            BlockPos blockPos,
            BlockPos structureOrigin,
            Identifier id,
            Vec3i size
    ) {
        BlockState state = Blocks.STRUCTURE_BLOCK.defaultBlockState().setValue(StructureBlock.MODE, StructureMode.SAVE);
        world.setBlock(blockPos, state, 3);

        BlockEntity blockEntity = world.getBlockEntity(blockPos);
        if (!(blockEntity instanceof StructureBlockEntity structureBlock)) {
            throw new IllegalStateException("Failed to create Structure Block at " + blockPos.toShortString());
        }

        structureBlock.setMode(StructureMode.SAVE);
        structureBlock.setStructureName(id);
        structureBlock.setStructurePos(structureOrigin.subtract(blockPos));
        structureBlock.setStructureSize(size);
        structureBlock.setMirror(Mirror.NONE);
        structureBlock.setRotation(Rotation.NONE);
        structureBlock.setIgnoreEntities(false);
        structureBlock.setStrict(false);
        structureBlock.setIntegrity(1.0F);
        structureBlock.setSeed(0L);
        structureBlock.setChanged();
        world.sendBlockUpdated(blockPos, state, state, 3);
    }

    private static int save(CommandSourceStack source) {
        try {
            SaveResult result = saveChangedStructures(source.getServer());
            source.sendSuccess(() -> Component.literal(
                    "Saved %d structure file(s) from the gallery world back to source structure folders."
                            .formatted(result.saved())
            ), true);

            if (result.missing() > 0) {
                source.sendSuccess(() -> Component.literal(
                        "Skipped %d base structure(s) that have not been saved from a Structure Block yet."
                                .formatted(result.missing())
                ), false);
            }

            return result.saved();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to save gallery structures: " + e.getMessage()));
            ProceduralDungeon.LOGGER.error("Failed to save gallery structures.", e);
            return 0;
        }
    }

    private static SaveResult saveChangedStructures(MinecraftServer server) throws IOException {
        Path generatedRoot = server.getWorldPath(LevelResource.GENERATED_DIR)
                .resolve(ProceduralDungeon.MOD_ID)
                .resolve("structures");
        Map<Identifier, Path> sourceStructures = baseStructureSourcePaths();

        int saved = 0;
        int missing = 0;
        for (Map.Entry<Identifier, Path> entry : sourceStructures.entrySet()) {
            Identifier id = entry.getKey();
            Path source = generatedRoot.resolve(id.getPath() + ".nbt");
            if (!Files.exists(source)) {
                missing++;
                continue;
            }

            Path destination = entry.getValue();
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            saved++;
        }

        return new SaveResult(saved, missing);
    }

    private static List<TemplateEntry> baseTemplates(StructureTemplateManager templateManager) {
        List<Identifier> ids = new ArrayList<>(baseStructureSourcePaths().keySet());
        List<TemplateEntry> templates = new ArrayList<>();

        for (Identifier id : ids) {
            Optional<StructureTemplate> template = templateManager.get(id);
            if (template.isPresent()) {
                templates.add(new TemplateEntry(id, template.get()));
            } else {
                ProceduralDungeon.LOGGER.warn("Skipping missing base structure template '{}'.", id);
            }
        }

        return templates;
    }

    private static Map<Identifier, Path> baseStructureSourcePaths() {
        Map<Identifier, Path> structures = new LinkedHashMap<>();
        for (Path root : SOURCE_STRUCTURE_ROOTS) {
            Path sourceRoot = resolveProjectPath(root);
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }

            for (Identifier id : baseStructureIds(sourceRoot)) {
                structures.putIfAbsent(id, sourceRoot.resolve(id.getPath() + ".nbt"));
            }
        }

        if (structures.isEmpty()) {
            throw new IllegalStateException("Could not find any procedural dungeon source structure directories.");
        }
        return structures;
    }

    private static List<Identifier> baseStructureIds(Path sourceRoot) {
        try (var paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .map(sourceRoot::relativize)
                    .map(StructureGalleryCommand::toStructureId)
                    .sorted(Comparator.comparing(Identifier::toString))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list source structures under " + sourceRoot, e);
        }
    }

    private static Identifier toStructureId(Path relativePath) {
        String path = relativePath.toString().replace('\\', '/');
        path = path.substring(0, path.length() - ".nbt".length());
        return Identifier.fromNamespaceAndPath(ProceduralDungeon.MOD_ID, path);
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }

            current = current.getParent();
        }

        return Path.of("").toAbsolutePath().resolve(relativePath);
    }

    private static void loadChunks(ServerLevel world, BlockPos min, BlockPos max) {
        ChunkPos minChunk = new ChunkPos(min.getX() >> 4, min.getZ() >> 4);
        ChunkPos maxChunk = new ChunkPos(max.getX() >> 4, max.getZ() >> 4);
        for (int x = minChunk.x(); x <= maxChunk.x(); x++) {
            for (int z = minChunk.z(); z <= maxChunk.z(); z++) {
                world.getChunk(x, z);
            }
        }
    }

    private static void fill(ServerLevel world, BlockPos min, BlockPos max, BlockState state) {
        BlockPos.betweenClosed(min, max).forEach(pos -> world.setBlock(pos, state, 2));
    }

    private static Path markerPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(MARKER_FILE);
    }

    private static void writeMarker(Path marker, BuildResult result) throws IOException {
        Path parent = marker.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(marker, "Built %d structures at spacing %d.%n".formatted(result.count(), result.spacing()));
    }

    private static void shutdownAfterBuild(MinecraftServer server) {
        if (!Boolean.getBoolean(SHUTDOWN_AFTER_BUILD_PROPERTY)) {
            return;
        }

        ProceduralDungeon.LOGGER.info("Saving structure gallery world and stopping preparation server.");
        server.saveEverything(false, true, true);
        server.halt(false);
    }

    private static Optional<Integer> readMarkerCount(Path marker) {
        try {
            String content = Files.readString(marker).trim();
            if (!content.startsWith("Built ")) {
                return Optional.empty();
            }

            int end = content.indexOf(" structures");
            if (end < 0) {
                return Optional.empty();
            }

            return Optional.of(Integer.parseInt(content.substring("Built ".length(), end)));
        } catch (IOException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private record TemplateEntry(Identifier id, StructureTemplate template) {
    }

    private record BuildResult(int count, int spacing) {
    }

    private record SaveResult(int saved, int missing) {
    }
}
