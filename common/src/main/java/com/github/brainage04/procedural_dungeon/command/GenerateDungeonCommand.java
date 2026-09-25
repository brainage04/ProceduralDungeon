package com.github.brainage04.procedural_dungeon.command;

import com.github.brainage04.procedural_dungeon.command.core.ModSuggestionProviders;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayoutCompiler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Optional;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class GenerateDungeonCommand {
    public static int execute(CommandSourceStack source, Identifier themeId, int tierNumber, int depth) throws CommandSyntaxException {
        DungeonTheme theme = getTheme(themeId);
        if (theme == null) {
            source.sendFailure(Component.literal("Invalid dungeon theme!"));
            return 0;
        }

        DungeonTier tier = getTier(tierNumber);
        String key = RegistryKeyUtils.getKeyString(theme, tier);
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkPos chunkPos = ChunkPos.containing(pos);

        source.sendSuccess(() -> Component.literal(
                "Scheduling Tier %d %s dungeon at jigsaw depth %d..."
                        .formatted(tier.tier, theme.getName().getString(), depth)
        ), true);

        Optional<StagedDungeonLayout> layout = compileLayout(source.getLevel(), theme, tier, depth, pos);
        if (layout.isEmpty()) {
            source.sendFailure(Component.literal("Failed to compile dungeon layout."));
            return 0;
        }

        StagedDungeonGenerationManager.enqueue(
                source.getLevel(),
                chunkPos,
                layout.get().pieces(),
                LiquidSettings.IGNORE_WATERLOGGING,
                layout.get().lockPlan()
        );

        DungeonLockPlan lockPlan = layout.get().lockPlan();
        source.sendSuccess(() -> Component.literal(
                "Scheduled %s at %d %d %d with %d pieces, %d boss door(s), %d miniboss door(s), %d locked chest(s), and %d key chest(s)."
                        .formatted(
                                key,
                                pos.getX(),
                                pos.getY(),
                                pos.getZ(),
                                layout.get().pieces().size(),
                                lockPlan.lockedDoorCount(DungeonKeyType.BOSS),
                                lockPlan.lockedDoorCount(DungeonKeyType.MINIBOSS),
                                lockPlan.lockedChests().size(),
                                lockPlan.keySources().size()
                        )
        ), true);

        return depth;
    }

    /**
     * Compiles the layout {@code /generatedungeon} would place from {@code pos}, without an entrance.
     */
    public static Optional<StagedDungeonLayout> compileLayout(ServerLevel level, DungeonTheme theme, DungeonTier tier, int depth, BlockPos pos) {
        String key = RegistryKeyUtils.getKeyString(theme, tier);
        ResourceKey<StructureTemplatePool> poolKey = RegistryKeyUtils.create(Registries.TEMPLATE_POOL, "%s/start".formatted(key));
        var pool = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(poolKey);
        var chunkGenerator = level.getChunkSource().getGenerator();
        Structure.GenerationContext generationContext = new Structure.GenerationContext(
                level.registryAccess(),
                chunkGenerator,
                chunkGenerator.getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getStructureManager(),
                level.getSeed(),
                ChunkPos.containing(pos),
                level,
                ignored -> true
        );
        return StagedDungeonLayoutCompiler.compile(
                generationContext,
                pool,
                Optional.of(Identifier.withDefaultNamespace("start")),
                depth,
                pos,
                Optional.empty(),
                new JigsawStructure.MaxDistance(tier.maxDistanceFromCenter),
                LiquidSettings.IGNORE_WATERLOGGING
        );
    }

    public static void initialize(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("generatedungeon")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("theme", IdentifierArgument.id())
                        .suggests(SuggestionProviders.cast(ModSuggestionProviders.DUNGEON_THEMES))
                        .then(argument("tier", IntegerArgumentType.integer(1, 5))
                                .then(argument("depth", IntegerArgumentType.integer(1, 20))
                                        .executes(context ->
                                                execute(
                                                        context.getSource(),
                                                        IdentifierArgument.getId(context, "theme"),
                                                        IntegerArgumentType.getInteger(context, "tier"),
                                                        IntegerArgumentType.getInteger(context, "depth")
                                                )
                                        )
                                )
                        )
                )
        );
        dispatcher.register(literal("generatedungeonstatus")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> status(context.getSource()))
        );
    }

    private static int status(CommandSourceStack source) {
        StagedDungeonGenerationManager.Status status = StagedDungeonGenerationManager.status(source.getLevel());
        source.sendSuccess(() -> Component.literal(
                "Staged dungeon jobs: %d, pending pieces: %d."
                        .formatted(status.jobs(), status.pendingPieces())
        ), true);
        return status.pendingPieces();
    }

    private static DungeonTheme getTheme(Identifier id) {
        for (DungeonTheme theme : DungeonTheme.values()) {
            if (theme.getId().equals(id)
                    || theme.getSerializedName().equals(id.getPath())
                    || theme.name().equalsIgnoreCase(id.getPath())) {
                return theme;
            }
        }

        return null;
    }

    private static DungeonTier getTier(int tierNumber) {
        for (DungeonTier tier : DungeonTier.values()) {
            if (tier.tier == tierNumber) {
                return tier;
            }
        }

        throw new IllegalArgumentException("Unsupported dungeon tier: " + tierNumber);
    }
}
