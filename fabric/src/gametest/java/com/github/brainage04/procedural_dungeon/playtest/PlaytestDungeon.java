package com.github.brainage04.procedural_dungeon.playtest;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.command.GenerateDungeonCommand;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonPieceSpec;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a whole dungeon at once and drops a player into its start room with gear for its tier.
 */
final class PlaytestDungeon {
    /**
     * Each build goes further along x, so dungeons never overlap.
     */
    private static final AtomicInteger BUILDS = new AtomicInteger();
    private static final int SPACING = 1024;
    private static final int ATTEMPTS = 8;

    private PlaytestDungeon() {}

    static boolean start(ServerPlayer player, Optional<DungeonTheme> requestedTheme, Optional<DungeonTier> requestedTier) {
        RandomSource random = RandomSource.create();
        DungeonTheme theme = requestedTheme.orElseGet(() -> DungeonTheme.values()[random.nextInt(DungeonTheme.values().length)]);
        DungeonTier tier = requestedTier.orElseGet(() -> DungeonTier.values()[random.nextInt(DungeonTier.values().length)]);
        ServerLevel level = player.level().getServer().getLevel(theme.dimension);
        if (level == null) {
            player.sendSystemMessage(Component.literal("Playtest: dimension %s is missing.".formatted(theme.dimension.identifier())));
            return false;
        }

        String label = "Tier %d %s".formatted(tier.tier, theme.getName().getString());
        player.sendSystemMessage(Component.literal("Playtest: building a %s dungeon...".formatted(label)));
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int build = BUILDS.incrementAndGet();
            BlockPos origin = new BlockPos(build * SPACING, startHeight(level), SPACING * (attempt % 2 == 0 ? 1 : -1));
            Optional<StagedDungeonLayout> layout = GenerateDungeonCommand.compileLayout(level, theme, tier, tier.worldgenSize, origin);
            if (layout.isEmpty()) {
                continue;
            }

            long start = System.nanoTime();
            StagedDungeonGenerationManager.placeSynchronously(level, layout.get().pieces(), LiquidSettings.IGNORE_WATERLOGGING);
            for (StagedDungeonPieceSpec piece : layout.get().pieces()) {
                DungeonLockManager.applyPlanForPiece(level, layout.get().lockPlan(), piece.boundingBox());
            }
            ProceduralDungeon.LOGGER.info("Playtest: placed {} ({} pieces) at {} in {} ms",
                    label, layout.get().pieces().size(), origin.toShortString(), (System.nanoTime() - start) / 1_000_000);

            BlockPos spawn = standingSpot(level, layout.get().pieces().getFirst().boundingBox());
            player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(spawn), Vec3.ZERO, player.getYRot(), 0.0F, TeleportTransition.DO_NOTHING));
            equip(player, tier, random);
            ProceduralDungeon.LOGGER.info("Playtest: {} is in {} at {} (standing on {}, head in {}), wearing {} and holding {}",
                    player.getScoreboardName(), level.dimension().identifier(), player.blockPosition().toShortString(),
                    level.getBlockState(spawn.below()), level.getBlockState(spawn.above()),
                    player.getItemBySlot(EquipmentSlot.CHEST), player.getInventory().getItem(0));
            player.sendSystemMessage(Component.literal(
                    "Playtest: %s, %d rooms. The boss key vault and boss room are somewhere in here. /playtest [theme] [tier] builds another."
                            .formatted(label, layout.get().pieces().size())));
            return true;
        }
        player.sendSystemMessage(Component.literal("Playtest: no %s layout fit after %d tries.".formatted(label, ATTEMPTS)));
        return false;
    }

    /**
     * Deep enough that dungeons, which build downwards from their start room, stay above the bottom of the world.
     */
    private static int startHeight(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD ? 20 : 64;
    }

    /**
     * An open two-block space with solid ground inside {@code box}, searched outwards from its centre.
     */
    private static BlockPos standingSpot(ServerLevel level, BoundingBox box) {
        BlockPos center = box.getCenter();
        for (int radius = 0; radius <= Math.max(box.getXSpan(), box.getZSpan()) / 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int y = box.maxY() - 1; y > box.minY(); y--) {
                        BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                        if (box.isInside(pos)
                                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                                && level.getFluidState(pos).isEmpty()) {
                            return pos;
                        }
                    }
                }
            }
        }
        return center;
    }

    private static void equip(ServerPlayer player, DungeonTier tier, RandomSource random) {
        player.setGameMode(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        player.setItemSlot(EquipmentSlot.HEAD, enchanted(player, tier.helmet, tier, random));
        player.setItemSlot(EquipmentSlot.CHEST, enchanted(player, tier.chestplate, tier, random));
        player.setItemSlot(EquipmentSlot.LEGS, enchanted(player, tier.leggings, tier, random));
        player.setItemSlot(EquipmentSlot.FEET, enchanted(player, tier.boots, tier, random));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

        inventory.add(enchanted(player, tier.sword, tier, random));
        inventory.add(enchanted(player, Items.BOW, tier, random));
        inventory.add(enchanted(player, tier.pickaxe, tier, random));
        inventory.add(enchanted(player, tier.axe, tier, random));
        inventory.add(new ItemStack(Items.TORCH, 64));
        inventory.add(new ItemStack(Items.COOKED_BEEF, 32));
        inventory.add(new ItemStack(Items.GOLDEN_APPLE, 2 * tier.tier));
        inventory.add(new ItemStack(Items.COBBLESTONE, 64));
        inventory.add(new ItemStack(Items.ARROW, 64));
        inventory.add(new ItemStack(Items.WATER_BUCKET));
        // For trying the grindstone and anvil salvage on rewards.
        inventory.add(new ItemStack(Items.GRINDSTONE));
        inventory.add(new ItemStack(Items.ANVIL));
        inventory.add(new ItemStack(Items.BOOK, 16));

        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.setExperienceLevels(30);
    }

    /**
     * {@code item} enchanted at the tier's enchantment level from the enchanting-table pool.
     */
    private static ItemStack enchanted(ServerPlayer player, Item item, DungeonTier tier, RandomSource random) {
        var access = player.level().registryAccess();
        return EnchantmentHelper.enchantItem(
                random,
                new ItemStack(item),
                tier.levels,
                access,
                access.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE)
        );
    }
}
