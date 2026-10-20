package com.github.brainage04.procedural_dungeon.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.github.brainage04.procedural_dungeon.command.core.ModSuggestionProviders;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobOptions;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager.JobView;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonMarkerPiece;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonSaveData;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonVerifier;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import it.unimi.dsi.fastutil.longs.LongIterator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Operator and soak-test tools for staged dungeon jobs: {@code /dungeondebug
 * jobs|retry|cancel|releaseforced} and the crash-window helpers {@code
 * build|saveprogress|savechunks|verify}.
 */
public final class DungeonDebugCommand {
    private DungeonDebugCommand() {}

    public static void initialize(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("dungeondebug")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(literal("jobs").executes(context -> jobs(context.getSource())))
                        .then(
                                literal("retry")
                                        .then(
                                                argument("job", StringArgumentType.word())
                                                        .executes(
                                                                context ->
                                                                        retry(
                                                                                context.getSource(),
                                                                                job(context)))))
                        .then(
                                literal("cancel")
                                        .then(
                                                argument("job", StringArgumentType.word())
                                                        .executes(
                                                                context ->
                                                                        cancel(
                                                                                context.getSource(),
                                                                                job(context)))))
                        .then(
                                literal("releaseforced")
                                        .executes(context -> releaseForced(context.getSource())))
                        .then(
                                literal("build")
                                        .then(
                                                argument("theme", IdentifierArgument.id())
                                                        .suggests(
                                                                SuggestionProviders.cast(
                                                                        ModSuggestionProviders
                                                                                .DUNGEON_THEMES))
                                                        .then(
                                                                argument(
                                                                                "tier",
                                                                                IntegerArgumentType
                                                                                        .integer(
                                                                                                1,
                                                                                                5))
                                                                        .then(
                                                                                argument(
                                                                                                "depth",
                                                                                                IntegerArgumentType
                                                                                                        .integer(
                                                                                                                1,
                                                                                                                20))
                                                                                        .executes(
                                                                                                context ->
                                                                                                        build(
                                                                                                                context
                                                                                                                        .getSource(),
                                                                                                                IdentifierArgument
                                                                                                                        .getId(
                                                                                                                                context,
                                                                                                                                "theme"),
                                                                                                                IntegerArgumentType
                                                                                                                        .getInteger(
                                                                                                                                context,
                                                                                                                                "tier"),
                                                                                                                IntegerArgumentType
                                                                                                                        .getInteger(
                                                                                                                                context,
                                                                                                                                "depth")))))))
                        .then(
                                literal("saveprogress")
                                        .executes(context -> saveProgress(context.getSource())))
                        .then(
                                literal("savechunks")
                                        .executes(context -> saveChunks(context.getSource())))
                        .then(literal("verify").executes(context -> verify(context.getSource()))));
    }

    private static UUID job(CommandContext<CommandSourceStack> context) {
        return UUID.fromString(StringArgumentType.getString(context, "job"));
    }

    private static int jobs(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<JobView> jobs = StagedDungeonGenerationManager.jobs(level);
        List<String> unreadable = StagedDungeonGenerationManager.unreadableJobs(level);
        source.sendSuccess(
                () ->
                        Component.literal(
                                "Staged dungeon jobs: %d, unreadable records: %d."
                                        .formatted(jobs.size(), unreadable.size())),
                false);
        for (JobView job : jobs) {
            source.sendSuccess(
                    () ->
                            Component.literal(
                                    "%s %s %d/%d %s%s"
                                            .formatted(
                                                    job.id(),
                                                    job.state(),
                                                    job.placedPieces(),
                                                    job.totalPieces(),
                                                    job.bounds(),
                                                    job.failure() == null
                                                            ? ""
                                                            : " - " + job.failure())),
                    false);
        }
        for (String error : unreadable) {
            source.sendSuccess(() -> Component.literal("unreadable: " + error), false);
        }
        return jobs.size() + unreadable.size();
    }

    private static int retry(CommandSourceStack source, UUID id) {
        if (!StagedDungeonGenerationManager.retry(source.getLevel(), id)) {
            source.sendFailure(Component.literal("No failed job " + id + " in this level."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Retrying " + id + "."), true);
        return 1;
    }

    private static int cancel(CommandSourceStack source, UUID id) {
        if (!StagedDungeonGenerationManager.cancel(source.getLevel(), id)) {
            source.sendFailure(Component.literal("No running job " + id + " in this level."));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal("Cancelled " + id + "; its chunk tickets are released."),
                true);
        return 1;
    }

    /**
     * Releases the level-32 FORCED tickets PD added before r61 inside current jobs and built
     * natural dungeons; lists the other level-32 forced chunks for the operator.
     */
    private static int releaseForced(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<BoundingBox> known = new ArrayList<>(StagedDungeonGenerationManager.jobBounds(level));
        LongIterator starts = StagedDungeonGenerationManager.completedStartChunks(level).iterator();
        while (starts.hasNext()) {
            ChunkPos pos = ChunkPos.unpack(starts.nextLong());
            ChunkAccess chunk = level.getChunk(pos.x(), pos.z(), ChunkStatus.STRUCTURE_STARTS);
            for (StructureStart start : chunk.getAllStarts().values()) {
                if (start.isValid()
                        && start.getPieces().stream()
                                .anyMatch(StagedDungeonMarkerPiece.class::isInstance)) {
                    known.add(start.getBoundingBox());
                }
            }
        }
        StagedDungeonGenerationManager.LegacyTicketReport report =
                StagedDungeonGenerationManager.releaseLegacyForcedTickets(level, known);
        source.sendSuccess(
                () ->
                        Component.literal(
                                "Released %d PD forced ticket(s) inside %d known dungeon(s)."
                                        .formatted(report.released(), known.size())),
                true);
        for (ChunkPos pos : report.unknown()) {
            source.sendSuccess(
                    () ->
                            Component.literal(
                                    "Level-32 forced ticket outside known dungeons (not released): chunk %d %d"
                                            .formatted(pos.x(), pos.z())),
                    false);
        }
        return report.released();
    }

    /** Schedules a dungeon whose record stays after completion, so {@code verify} can check it. */
    private static int build(
            CommandSourceStack source, Identifier themeId, int tierNumber, int depth) {
        DungeonTheme theme = GenerateDungeonCommand.getTheme(themeId);
        if (theme == null) {
            source.sendFailure(Component.literal("Invalid dungeon theme!"));
            return 0;
        }
        DungeonTier tier = GenerateDungeonCommand.getTier(tierNumber);
        BlockPos pos = BlockPos.containing(source.getPosition());
        var layout =
                GenerateDungeonCommand.compileLayout(source.getLevel(), theme, tier, depth, pos);
        if (layout.isEmpty()) {
            source.sendFailure(Component.literal("Failed to compile dungeon layout."));
            return 0;
        }
        var result =
                StagedDungeonGenerationManager.enqueue(
                        source.getLevel(),
                        layout.get(),
                        LiquidSettings.IGNORE_WATERLOGGING,
                        JobOptions.DEFAULT);
        if (!result.accepted()) {
            source.sendFailure(Component.literal("Rejected: " + result.rejection()));
            return 0;
        }
        source.sendSuccess(
                () ->
                        Component.literal(
                                "Building %s with %d pieces in %s."
                                        .formatted(
                                                result.id(),
                                                layout.get().pieces().size(),
                                                layout.get().boundingBox())),
                true);
        return layout.get().pieces().size();
    }

    /** Writes only this level's staged job record, so the record is ahead of the chunks on disk. */
    private static int saveProgress(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        StagedDungeonSaveData data = level.getDataStorage().get(StagedDungeonSaveData.TYPE);
        if (data == null) {
            source.sendFailure(Component.literal("This level has no staged dungeon record."));
            return 0;
        }
        CompoundTag tag = new CompoundTag();
        tag.put(
                "data",
                StagedDungeonSaveData.TYPE
                        .codec()
                        .encodeStart(
                                level.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                                data)
                        .getOrThrow());
        NbtUtils.addCurrentDataVersion(tag);
        Path file =
                StagedDungeonSaveData.TYPE
                        .id()
                        .withSuffix(".dat")
                        .resolveAgainst(
                                DimensionType.getStorageFolder(
                                                level.dimension(),
                                                level.getServer().getWorldPath(LevelResource.ROOT))
                                        .resolve("data"));
        try {
            FileUtil.createDirectoriesSafe(file.getParent());
            NbtIo.writeCompressed(tag, file);
        } catch (IOException exception) {
            source.sendFailure(
                    Component.literal("Could not write " + file + ": " + exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Wrote " + file + "."), true);
        return 1;
    }

    /**
     * Saves this level's chunks only (not the staged job record), so the chunks are ahead of the
     * record on disk.
     */
    private static int saveChunks(CommandSourceStack source) {
        source.getLevel().getChunkSource().save(true);
        source.sendSuccess(
                () ->
                        Component.literal(
                                "Saved the chunks of "
                                        + source.getLevel().dimension().identifier()
                                        + "."),
                true);
        return 1;
    }

    private static int verify(CommandSourceStack source) {
        List<StagedDungeonVerifier.Report> reports =
                StagedDungeonVerifier.verifyAll(source.getLevel());
        int failures = 0;
        for (StagedDungeonVerifier.Report report : reports) {
            if (!report.ok()) {
                failures++;
            }
            source.sendSuccess(() -> Component.literal(report.toString()), false);
        }
        int checked = reports.size();
        int failed = failures;
        source.sendSuccess(
                () ->
                        Component.literal(
                                "Verified %d job(s): %d failed.".formatted(checked, failed)),
                false);
        return failures == 0 ? Math.max(1, checked) : 0;
    }
}
