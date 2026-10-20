package com.github.brainage04.procedural_dungeon.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.github.brainage04.procedural_dungeon.lock.DungeonBossKeys;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Operator tools for a dungeon's running state. {@code /dungeon bosskey reissue <pos>} replaces the
 * Boss Key of the dungeon whose boss door is nearest to {@code pos}: the remedy for a key lost
 * where no hook could see it, such as a crash rollback or a corrupted player file.
 */
public final class DungeonCommand {
    private DungeonCommand() {}

    public static void initialize(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("dungeon")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(
                                literal("bosskey")
                                        .then(
                                                literal("reissue")
                                                        .then(
                                                                argument(
                                                                                "dungeon",
                                                                                BlockPosArgument
                                                                                        .blockPos())
                                                                        .executes(
                                                                                context ->
                                                                                        reissue(
                                                                                                context
                                                                                                        .getSource(),
                                                                                                BlockPosArgument
                                                                                                        .getBlockPos(
                                                                                                                context,
                                                                                                                "dungeon")))))));
    }

    private static int reissue(CommandSourceStack source, BlockPos near) {
        return DungeonBossKeys.reissueNear(source.getLevel(), near)
                .map(
                        vault -> {
                            source.sendSuccess(
                                    () ->
                                            Component.literal(
                                                    "Reissued the Boss Key into the vault at %s; the old key no longer opens the door."
                                                            .formatted(vault.toShortString())),
                                    true);
                            return 1;
                        })
                .orElseGet(
                        () -> {
                            source.sendFailure(
                                    Component.literal(
                                            "No locked boss door within %d blocks of %s."
                                                    .formatted(
                                                            DungeonBossKeys.REISSUE_RANGE,
                                                            near.toShortString())));
                            return 0;
                        });
    }
}
