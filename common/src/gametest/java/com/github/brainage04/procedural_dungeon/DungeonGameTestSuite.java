package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.command.GenerateDungeonCommand;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.enchantment.DungeonEnchantments;
import com.github.brainage04.procedural_dungeon.guardian.DungeonGuardian;
import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockPlan;
import com.github.brainage04.procedural_dungeon.reward.RelicEssence;
import com.github.brainage04.procedural_dungeon.test.DungeonVariantSmokeTester;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonProgressionRooms;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonPuzzleRooms;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayout;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonLayoutCompiler;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonPieceSpec;
import com.github.brainage04.procedural_dungeon.worldgen.structure.VariantSinglePoolElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class DungeonGameTestSuite {
    private static final List<DungeonTheme> LAYOUT_THEMES = List.of(
            DungeonTheme.COBBLESTONE,
            DungeonTheme.SCULK,
            DungeonTheme.NETHER_WASTES,
            DungeonTheme.END_CITY
    );
    private static final List<BlockPos> LAYOUT_ORIGINS = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(1024, 40, -2048),
            new BlockPos(-4096, 90, 3072)
    );

    private DungeonGameTestSuite() {}

    public static List<TestCase> tests() {
        return List.of(
                new TestCase("variant_catalog_is_stable_and_complete", DungeonGameTestSuite::variantCatalogIsStableAndComplete),
                new TestCase("seeded_loot_selection_is_repeatable_and_tier_bounded", DungeonGameTestSuite::seededLootSelectionIsRepeatableAndTierBounded),
                new TestCase("every_layout_has_a_boss_room_and_a_boss_key_vault", DungeonGameTestSuite::everyLayoutHasABossRoomAndABossKeyVault),
                new TestCase("guarded_door_opens_only_with_its_key", DungeonGameTestSuite::guardedDoorOpensOnlyWithItsKey),
                new TestCase("placed_boss_room_is_guarded_locked_and_stocked", DungeonGameTestSuite::placedBossRoomIsGuardedLockedAndStocked),
                new TestCase("trial_spawners_eject_tiered_dungeon_loot", DungeonGameTestSuite::trialSpawnersEjectTieredDungeonLoot),
                new TestCase("surface_entrances_lead_down_into_a_full_dungeon", DungeonGameTestSuite::surfaceEntrancesLeadDownIntoAFullDungeon),
                new TestCase("dungeon_enchantments_are_reward_only_and_take_effect", DungeonGameTestSuite::dungeonEnchantmentsAreRewardOnlyAndTakeEffect),
                new TestCase("boss_rewards_are_exclusive_and_relics_keep_base_stats", DungeonGameTestSuite::bossRewardsAreExclusiveAndRelicsKeepBaseStats),
                new TestCase("over_max_books_apply_through_anvils", DungeonGameTestSuite::overMaxBooksApplyThroughAnvils),
                new TestCase("enchantment_blasts_spare_allies", DungeonGameTestSuite::enchantmentBlastsSpareAllies),
                new TestCase("kill_enchantments_reward_the_killer_and_burst_on_enemies", DungeonGameTestSuite::killEnchantmentsRewardTheKillerAndBurstOnEnemies),
                new TestCase("soulbound_items_survive_death", DungeonGameTestSuite::soulboundItemsSurviveDeath),
                new TestCase("grindstones_salvage_books_and_essence_that_anvils_reapply", DungeonGameTestSuite::grindstonesSalvageBooksAndEssenceThatAnvilsReapply),
                new TestCase("puzzle_rooms_place_whole_and_hold_their_chests", DungeonGameTestSuite::puzzleRoomsPlaceWholeAndHoldTheirChests),
                new TestCase("frame_puzzle_opens_when_every_arrow_points_up", DungeonGameTestSuite::framePuzzleOpensWhenEveryArrowPointsUp),
                new TestCase("bookshelf_puzzle_opens_when_every_shelf_ends_on_its_last_slot", DungeonGameTestSuite::bookshelfPuzzleOpensWhenEveryShelfEndsOnItsLastSlot),
                new TestCase("target_puzzle_opens_when_every_bulb_is_lit", DungeonGameTestSuite::targetPuzzleOpensWhenEveryBulbIsLit),
                new TestCase("chord_puzzle_opens_only_for_its_chord", DungeonGameTestSuite::chordPuzzleOpensOnlyForItsChord),
                new TestCase("sluice_puzzle_opens_when_the_stream_reaches_the_torch", DungeonGameTestSuite::sluicePuzzleOpensWhenTheStreamReachesTheTorch),
                new TestCase("crafter_puzzle_opens_when_the_disc_plays", DungeonGameTestSuite::crafterPuzzleOpensWhenTheDiscPlays)
        );
    }

    public static void variantCatalogIsStableAndComplete(GameTestHelper helper) {
        List<String> variants = DungeonVariantSmokeTester.getVariantKeys();
        helper.assertTrue(variants.size() == DungeonTheme.values().length * DungeonTier.values().length,
                "Every theme/tier pair must have one deterministic variant key");
        helper.assertTrue(variants.stream().distinct().count() == variants.size(), "Dungeon variant keys must be unique");
        helper.assertTrue(variants.getFirst().endsWith("tier_1"), "Variant catalog must start with tier 1");
        helper.assertTrue(variants.getLast().endsWith("tier_5"), "Variant catalog must end with tier 5");
        helper.succeed();
    }

    public static void seededLootSelectionIsRepeatableAndTierBounded(GameTestHelper helper) {
        List<DungeonTier> first = lootSequence(0xD06E0L);
        List<DungeonTier> second = lootSequence(0xD06E0L);
        helper.assertTrue(first.equals(second), "A fixed seed must select the same loot tiers");
        helper.assertTrue(first.stream().allMatch(tier -> tier.ordinal() >= 0 && tier.ordinal() < DungeonTier.values().length),
                "Loot selection must always return a defined dungeon tier");
        helper.succeed();
    }

    /**
     * The boss room and its key are guaranteed: every compiled layout has one boss room behind a boss-key door and
     * one vault whose chest holds the boss key, and every locked miniboss door has a chest planned for its key.
     */
    public static void everyLayoutHasABossRoomAndABossKeyVault(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (DungeonTheme theme : LAYOUT_THEMES) {
            for (DungeonTier tier : DungeonTier.values()) {
                for (BlockPos origin : LAYOUT_ORIGINS) {
                    String label = "%s tier %d at %s".formatted(theme.getSerializedName(), tier.tier, origin.toShortString());
                    Optional<StagedDungeonLayout> compiled = GenerateDungeonCommand.compileLayout(level, theme, tier, tier.size, origin);
                    helper.assertTrue(compiled.isPresent(), "Layout must compile: " + label);
                    StagedDungeonLayout layout = compiled.get();
                    DungeonLockPlan plan = layout.lockPlan();

                    List<StagedDungeonPieceSpec> bossRooms = pieces(layout, DungeonProgressionRooms.BOSS_ROOM);
                    List<StagedDungeonPieceSpec> vaults = pieces(layout, DungeonProgressionRooms.BOSS_KEY_VAULT);
                    helper.assertTrue(bossRooms.size() == 1, "Exactly one boss room: " + label);
                    helper.assertTrue(vaults.size() == 1, "Exactly one boss key vault: " + label);

                    BoundingBox bossBox = bossRooms.getFirst().boundingBox();
                    List<DungeonLockPlan.Door> bossDoors = plan.doors().stream()
                            .filter(door -> door.lock().equals(Optional.of(DungeonKeyType.BOSS)))
                            .toList();
                    helper.assertTrue(bossDoors.size() == 1 && bossBox.isInside(BlockPos.of(bossDoors.getFirst().pos())),
                            "The boss room door must need the boss key: " + label);

                    BoundingBox vaultBox = vaults.getFirst().boundingBox();
                    Identifier vaultLoot = ProceduralDungeon.of("boss_key_vault/tier_%d".formatted(tier.tier));
                    helper.assertTrue(plan.keySources().stream().anyMatch(source ->
                                    vaultBox.isInside(BlockPos.of(source.pos())) && source.lootTable().equals(vaultLoot)),
                            "The vault chest must hold the boss key: " + label);

                    long minibossKeys = plan.keySources().stream()
                            .filter(source -> source.lootTable().getPath().contains("/miniboss_key_source/"))
                            .count();
                    helper.assertTrue(minibossKeys == plan.lockedDoorCount(DungeonKeyType.MINIBOSS),
                            "Every locked miniboss door needs one key chest: " + label);
                    helper.assertTrue(plan.doors().size() - bossDoors.size() == pieces(layout, DungeonProgressionRooms.MINIBOSS_ROOM).size(),
                            "Every miniboss room must plan its door: " + label);
                }
            }
        }
        helper.succeed();
    }

    public static void guardedDoorOpensOnlyWithItsKey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos upper = lower.above();
        level.setBlock(lower, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(upper, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        DungeonLockManager.applyPlanForPiece(
                level,
                new DungeonLockPlan(List.of(), List.of(), List.of(new DungeonLockPlan.Door(lower.asLong(), Optional.of(DungeonKeyType.BOSS)))),
                new BoundingBox(lower)
        );

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(DungeonLockManager.useBlock(player, level, upper) == InteractionResult.FAIL,
                "A boss door must refuse a player without keys");
        helper.assertTrue(!DungeonLockManager.canBreak(player, level, lower) && !DungeonLockManager.canBreak(player, level, upper),
                "Both halves of a locked door must be unbreakable");

        player.getInventory().add(DungeonKeyType.RUSTED.createStack());
        player.getInventory().add(DungeonKeyType.MINIBOSS.createStack());
        ItemStack forgedBossKey = new ItemStack(DungeonKeyType.BOSS.item());
        forgedBossKey.set(DataComponents.ITEM_NAME, Component.literal(DungeonKeyType.BOSS.displayName()));
        player.getInventory().add(forgedBossKey);
        helper.assertTrue(DungeonLockManager.useBlock(player, level, lower) == InteractionResult.FAIL,
                "A boss door must refuse rusted and miniboss keys, and a plain key merely named like a boss key");
        helper.assertTrue(player.getInventory().countItem(DungeonKeyType.BOSS.item()) == 1,
                "The forged key must not be consumed");
        helper.assertTrue(keyCount(player, DungeonKeyType.RUSTED) == 1
                        && keyCount(player, DungeonKeyType.MINIBOSS) == 1,
                "A refused key must not be consumed");
        helper.assertTrue(!level.getBlockState(lower).getValue(DoorBlock.OPEN), "A refused door must stay shut");

        player.getInventory().add(DungeonKeyType.BOSS.createStack());
        helper.assertTrue(DungeonLockManager.useBlock(player, level, upper) == InteractionResult.SUCCESS,
                "The boss key must unlock the boss door");
        helper.assertTrue(keyCount(player, DungeonKeyType.BOSS) == 0, "Unlocking consumes the boss key");
        helper.assertTrue(level.getBlockState(lower).getValue(DoorBlock.OPEN) && level.getBlockState(upper).getValue(DoorBlock.OPEN),
                "An unlocked door opens");
        helper.assertTrue(DungeonLockManager.canBreak(player, level, lower) && DungeonLockManager.canBreak(player, level, upper),
                "An unlocked door is no longer protected");
        helper.succeed();
    }

    public static void placedBossRoomIsGuardedLockedAndStocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonTier tier = DungeonTier.TIER_3;
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(2048, 0, 2048);
        StagedDungeonLayout layout = GenerateDungeonCommand.compileLayout(level, DungeonTheme.COBBLESTONE, tier, tier.size, origin)
                .orElseThrow(() -> new AssertionError("Layout must compile"));
        StagedDungeonPieceSpec bossRoom = pieces(layout, DungeonProgressionRooms.BOSS_ROOM).getFirst();
        StagedDungeonPieceSpec vault = pieces(layout, DungeonProgressionRooms.BOSS_KEY_VAULT).getFirst();
        place(level, layout, bossRoom);
        place(level, layout, vault);

        BoundingBox bossBox = bossRoom.boundingBox();
        List<Mob> guardians = level.getEntitiesOfClass(Mob.class, AABB.of(bossBox), mob -> mob.entityTags().contains(DungeonGuardian.TAG));
        helper.assertTrue(guardians.size() == 1, "The boss room must spawn one guardian, found " + guardians.size());
        Mob boss = guardians.getFirst();
        helper.assertTrue(boss.getMaxHealth() == (float) DungeonGuardian.BOSS.maxHealth(tier.tier) && boss.isPersistenceRequired(),
                "The boss must have tier-scaled health and never despawn");

        BlockPos door = BlockPos.of(layout.lockPlan().doors().stream()
                .filter(planned -> planned.lock().equals(Optional.of(DungeonKeyType.BOSS)))
                .findFirst().orElseThrow().pos());
        helper.assertTrue(level.getBlockState(door).is(Blocks.IRON_DOOR) && level.getBlockState(door.above()).is(Blocks.IRON_DOOR),
                "The boss room door must be placed");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(DungeonLockManager.useBlock(player, level, door) == InteractionResult.FAIL, "The boss room door must be locked");

        List<ResourceKey<LootTable>> bossLoot = chestLoot(level, bossBox);
        helper.assertTrue(bossLoot.size() == 2 && bossLoot.stream().allMatch(loot -> loot.identifier().getPath().startsWith("boss_room/")),
                "The boss room must hold two boss loot chests: " + bossLoot);
        helper.assertTrue(chestLoot(level, vault.boundingBox()).equals(List.of(ResourceKey.create(
                        Registries.LOOT_TABLE, ProceduralDungeon.of("boss_key_vault/tier_%d".formatted(tier.tier))))),
                "The vault chest must hold the tiered boss key loot");

        boss.discard();
        helper.succeed();
    }

    public static void trialSpawnersEjectTieredDungeonLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonTier tier = DungeonTier.TIER_4;
        ResourceKey<StructureTemplatePool> roomPool = RegistryKeyUtils.create(
                Registries.TEMPLATE_POOL, "%s/hallway/room".formatted(RegistryKeyUtils.getKeyString(DungeonTheme.DEEPSLATE, tier)));
        StructurePoolElement corridor = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(roomPool).value()
                .getShuffledTemplates(RandomSource.create(0L)).stream()
                .filter(element -> element instanceof VariantSinglePoolElement variant
                        && variant.templateLocation().getPath().equals("dungeon/hallway/room/spawner_corridor"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The room pool must contain the spawner corridor"));

        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(-2048, 0, 2048);
        BoundingBox box = corridor.getBoundingBox(level.getStructureManager(), origin, Rotation.NONE);
        StagedDungeonGenerationManager.placeSynchronously(
                level,
                List.of(new StagedDungeonPieceSpec(corridor, origin, Rotation.NONE, box, 0)),
                LiquidSettings.IGNORE_WATERLOGGING
        );

        Identifier normal = ProceduralDungeon.of("trial_spawner/tier_%d".formatted(tier.tier));
        Identifier ominous = ProceduralDungeon.of("trial_spawner/ominous/tier_%d".formatted(tier.tier));
        int spawners = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity spawner) {
                spawners++;
                helper.assertTrue(ejectedLoot(spawner.getTrialSpawner().normalConfig().lootTablesToEject().unwrap()).equals(List.of(normal)),
                        "Normal trial spawner rewards must use " + normal);
                helper.assertTrue(ejectedLoot(spawner.getTrialSpawner().ominousConfig().lootTablesToEject().unwrap()).equals(List.of(ominous)),
                        "Ominous trial spawner rewards must use " + ominous);
            }
        }
        helper.assertTrue(spawners == 2, "The spawner corridor must place two trial spawners, found " + spawners);
        helper.succeed();
    }

    /**
     * Natural Overworld and End dungeons start at a surface entrance; the start room must hang below its shaft, and
     * the dungeon must grow from there to its boss room instead of stopping at the entrance.
     */
    public static void surfaceEntrancesLeadDownIntoAFullDungeon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (DungeonTheme theme : List.of(DungeonTheme.COBBLESTONE, DungeonTheme.END_CITY)) {
            for (DungeonTier tier : DungeonTier.values()) {
                String label = "%s tier %d".formatted(theme.getSerializedName(), tier.tier);
                ResourceKey<StructureTemplatePool> entrancePool = RegistryKeyUtils.create(
                        Registries.TEMPLATE_POOL, "%s/entrance".formatted(RegistryKeyUtils.getKeyString(theme, tier)));
                var generator = level.getChunkSource().getGenerator();
                // A layout that cannot fit its boss room is dropped, and a few tier 5 spots are; so each tier is
                // checked at the first of a handful of spots that compiles.
                Optional<StagedDungeonLayout> compiled = Optional.empty();
                for (int attempt = 0; attempt < 8 && compiled.isEmpty(); attempt++) {
                    BlockPos origin = new BlockPos(tier.tier * 512 + attempt * 1024, 150, -tier.tier * 512 - attempt * 2048);
                    Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                            generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                            level.getSeed(), ChunkPos.containing(origin), level, ignored -> true);
                    compiled = StagedDungeonLayoutCompiler.compile(
                            context,
                            level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(entrancePool),
                            Optional.of(Identifier.withDefaultNamespace("start")),
                            tier.worldgenSize,
                            origin,
                            Optional.empty(),
                            new JigsawStructure.MaxDistance(tier.maxDistanceFromCenter),
                            LiquidSettings.IGNORE_WATERLOGGING
                    );
                }
                helper.assertTrue(compiled.isPresent(), "Entrance layouts must compile: " + label);
                StagedDungeonLayout layout = compiled.get();
                BoundingBox entrance = layout.pieces().getFirst().boundingBox();
                List<StagedDungeonPieceSpec> startRooms = pieces(layout, ProceduralDungeon.of("dungeon/start_shaft"));
                helper.assertTrue(startRooms.size() == 1 && startRooms.getFirst().boundingBox().maxY() < entrance.minY(),
                        "The start room must hang below the entrance: " + label);
                helper.assertTrue(pieces(layout, DungeonProgressionRooms.BOSS_ROOM).size() == 1,
                        "The dungeon below an entrance must reach its boss room: " + label);
            }
        }
        helper.succeed();
    }

    /**
     * Dungeon enchantments exist only as dungeon rewards: no vanilla source (enchanting table, trading, random loot,
     * mob equipment, enchant-with-levels loot) may roll them. Their effects must reach the victim.
     */
    public static void dungeonEnchantmentsAreRewardOnlyAndTakeEffect(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.RegistryLookup<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<TagKey<Enchantment>> vanillaSources = List.of(
                EnchantmentTags.IN_ENCHANTING_TABLE,
                EnchantmentTags.ON_RANDOM_LOOT,
                EnchantmentTags.TRADEABLE,
                EnchantmentTags.ON_TRADED_EQUIPMENT,
                EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT,
                DungeonEnchantments.LOOT_DAMAGE_OPTIONS
        );
        for (ResourceKey<Enchantment> key : DungeonEnchantments.all()) {
            Holder<Enchantment> enchantment = enchantments.get(key).orElseThrow(() -> new AssertionError("Missing enchantment " + key));
            for (TagKey<Enchantment> source : vanillaSources) {
                helper.assertTrue(!enchantment.is(source), key.identifier() + " must not be obtainable through " + source.location());
            }
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // Undead are immune to poison, so the victim is a pillager.
        Pillager venomTarget = helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new BlockPos(1, 1, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(enchantments, Items.IRON_SWORD, key("venom"), 1));
        player.attack(venomTarget);
        helper.assertTrue(venomTarget.hasEffect(MobEffects.POISON), "Venom must poison the victim");

        ItemStack netherBane = enchanted(enchantments, Items.IRON_SWORD, key("bane_of_the_nether"), 5);
        Blaze blaze = helper.spawnWithNoFreeWill(EntityTypes.BLAZE, new BlockPos(3, 1, 1));
        DamageSource source = level.damageSources().playerAttack(player);
        helper.assertTrue(
                EnchantmentHelper.modifyDamage(level, netherBane, blaze, source, 1.0F) >= 13.5F
                        && EnchantmentHelper.modifyDamage(level, netherBane, venomTarget, source, 1.0F) == 1.0F,
                "Bane of the Nether V must add 12.5 damage against nether mobs and nothing against others"
        );

        helper.succeed();
    }

    /**
     * Enchanted golden apples, heavy cores, netherite templates, Mending, and relics come only from boss chests, every
     * boss chest holds one relic, and a relic's bonus adds to the base item's stats instead of replacing them.
     */
    public static void bossRewardsAreExclusiveAndRelicsKeepBaseStats(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.getServer().reloadableRegistries().lookup();
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<String> bossOnly = List.of(
                "minecraft:enchanted_golden_apple",
                "minecraft:heavy_core",
                "minecraft:netherite_upgrade_smithing_template",
                "minecraft:mending",
                "minecraft:unbreakable",
                "minecraft:death_protection"
        );
        registries.lookupOrThrow(Registries.LOOT_TABLE).listElementIds()
                .filter(key -> key.identifier().getNamespace().equals(ProceduralDungeon.MOD_ID))
                .filter(key -> !key.identifier().getPath().startsWith("boss_room/"))
                .forEach(key -> {
                    String json = LootTable.DIRECT_CODEC.encodeStart(ops, level.getServer().reloadableRegistries().getLootTable(key))
                            .getOrThrow().toString();
                    for (String item : bossOnly) {
                        helper.assertTrue(!json.contains(item), key.identifier() + " must not contain boss-only " + item);
                    }
                });

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .create(LootContextParamSets.CHEST);
        for (DungeonTier tier : DungeonTier.values()) {
            LootTable bossRoom = level.getServer().reloadableRegistries().getLootTable(
                    ResourceKey.create(Registries.LOOT_TABLE, ProceduralDungeon.of("boss_room/tier_%d".formatted(tier.tier))));
            boolean sawAxe = false;
            RandomSource random = RandomSource.create(tier.tier);
            for (int roll = 0; roll < 64; roll++) {
                List<ItemStack> loot = bossRoom.getRandomItems(params, random);
                List<ItemStack> relics = loot.stream()
                        .filter(stack -> stack.has(DataComponents.UNBREAKABLE) || stack.has(DataComponents.DEATH_PROTECTION))
                        .toList();
                helper.assertTrue(relics.size() == 1, "Every tier %d boss chest must hold one relic: %s".formatted(tier.tier, loot));
                helper.assertTrue(loot.stream().anyMatch(stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE)),
                        "Every boss chest must hold an enchanted golden apple");
                ItemStack relic = relics.getFirst();
                if (relic.is(tier.axe)) {
                    sawAxe = true;
                    double damage = relic.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                            .compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
                    double baseDamage = new ItemStack(tier.axe).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                            .compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
                    helper.assertTrue(damage == baseDamage + tier.tier,
                            "The Warden's Cleaver must add %d attack damage to its base %s, got %s".formatted(tier.tier, baseDamage, damage));
                }
            }
            helper.assertTrue(sawAxe, "64 tier %d boss chests must include a Warden's Cleaver".formatted(tier.tier));
        }
        helper.succeed();
    }

    /**
     * An over-max reward book keeps its level on an anvil, but anvils never raise a level past the highest one supplied.
     */
    public static void overMaxBooksApplyThroughAnvils(GameTestHelper helper) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> sharpness = enchantments.getOrThrow(Enchantments.SHARPNESS);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, storedEnchantment(sharpness, 6));
        helper.assertTrue(anvil(player, new ItemStack(Items.IRON_SWORD), book).getEnchantments().getLevel(sharpness) == 6,
                "A Sharpness VI book must give a sword Sharpness VI");

        ItemStack sixSword = enchanted(enchantments, Items.IRON_SWORD, Enchantments.SHARPNESS, 6);
        helper.assertTrue(anvil(player, sixSword, book).getEnchantments().getLevel(sharpness) == 6,
                "Combining two Sharpness VI must not reach VII");

        ItemStack fiveSword = enchanted(enchantments, Items.IRON_SWORD, Enchantments.SHARPNESS, 5);
        ItemStack fiveBook = new ItemStack(Items.ENCHANTED_BOOK);
        fiveBook.set(DataComponents.STORED_ENCHANTMENTS, storedEnchantment(sharpness, 5));
        helper.assertTrue(anvil(player, fiveSword, fiveBook).getEnchantments().getLevel(sharpness) == 5,
                "Vanilla maximums still cap ordinary combinations");
        helper.succeed();
    }

    /**
     * Volatile and Cataclysm blasts hurt strangers but spare the wielder's teammates and pets; only Cataclysm breaks
     * blocks.
     */
    public static void enchantmentBlastsSpareAllies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.RegistryLookup<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("pd_allies_" + helper.absolutePos(BlockPos.ZERO).asLong());
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        try {
            for (ResourceKey<Enchantment> key : List.of(DungeonEnchantments.VOLATILE, DungeonEnchantments.CATACLYSM)) {
                // Neighbouring tests build right up to this 8-block area, so everything stays near its middle, where
                // their blocks cannot shield the stranger from the blast. The Cataclysm row runs last so its bigger
                // blast cannot skew the first.
                int z = key == DungeonEnchantments.VOLATILE ? 2 : 5;
                Pig doomed = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(4, 1, z));
                Pig stranger = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, z));
                Pig teammate = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(5, 1, z));
                Wolf pet = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(4, 1, z + 1));
                scoreboard.addPlayerToTeam(teammate.getScoreboardName(), team);
                pet.tame(player);
                BlockPos wall = new BlockPos(4, 2, z - 1);
                helper.setBlock(wall, Blocks.DIRT);
                doomed.setHealth(0.1F);
                player.snapTo(helper.absoluteVec(new Vec3(4.5, 1.0, z + 2.5)));
                player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(enchantments, Items.IRON_SWORD, key, 3));
                float playerHealth = player.getHealth();
                player.attack(doomed);

                String name = key.identifier().getPath();
                helper.assertTrue(doomed.isDeadOrDying(), "The %s victim must die from the hit".formatted(name));
                helper.assertTrue(stranger.getHealth() < stranger.getMaxHealth(), "A %s kill must blast nearby strangers".formatted(name));
                helper.assertTrue(teammate.getHealth() == teammate.getMaxHealth(), "A %s blast must spare the wielder's teammates".formatted(name));
                helper.assertTrue(pet.getHealth() == pet.getMaxHealth(), "A %s blast must spare the wielder's pets".formatted(name));
                helper.assertTrue(player.getHealth() == playerHealth, "A %s blast must spare its wielder".formatted(name));
                boolean wallStands = helper.getBlockState(wall).is(Blocks.DIRT);
                helper.assertTrue(wallStands == (key == DungeonEnchantments.VOLATILE),
                        "Only Cataclysm may break blocks; %s left the wall %s".formatted(name, wallStands ? "standing" : "broken"));
            }
        } finally {
            scoreboard.removePlayerTeam(team);
        }
        helper.succeed();
    }

    /**
     * Siphon, Warding, and Bloodlust reward the killer; the novas hit enemies around the victim, but not the killer's
     * pets or anything out of range; Warden's Wrath darkens every victim; Insight multiplies experience.
     */
    public static void killEnchantmentsRewardTheKillerAndBurstOnEnemies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.RegistryLookup<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // Instant effects land on the killer's next tick, which a mock player never takes.
        killWith(helper, player, enchanted(enchantments, Items.IRON_SWORD, DungeonEnchantments.SIPHON, 2), new BlockPos(0, 1, 1));
        helper.assertTrue(effectAmplifier(player, MobEffects.INSTANT_HEALTH) == 1, "Siphon II must give the killer Instant Health II");
        killWith(helper, player, enchanted(enchantments, Items.IRON_SWORD, DungeonEnchantments.WARDING, 3), new BlockPos(0, 1, 3));
        helper.assertTrue(effectAmplifier(player, MobEffects.ABSORPTION) == 2, "Warding III must give Absorption III");
        killWith(helper, player, enchanted(enchantments, Items.IRON_SWORD, DungeonEnchantments.BLOODLUST, 2), new BlockPos(0, 1, 5));
        helper.assertTrue(effectAmplifier(player, MobEffects.SPEED) == 1 && effectAmplifier(player, MobEffects.STRENGTH) == 1,
                "Bloodlust II must give Speed II and Strength II");

        for (DungeonEnchantments.Nova nova : DungeonEnchantments.NOVAS) {
            int z = 1 + 2 * DungeonEnchantments.NOVAS.indexOf(nova);
            // Pillagers are neither undead nor fire-immune, so every nova can take hold on them.
            Pillager near = helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new BlockPos(3, 1, z));
            Pillager far = helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new BlockPos(7, 1, z));
            Wolf pet = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(1, 1, z + 1));
            pet.tame(player);
            killWith(helper, player, enchanted(enchantments, Items.IRON_SWORD, nova.key(), 3), new BlockPos(0, 1, z));
            String name = nova.name();
            helper.assertTrue(struck(near, nova), name + " III must hit an enemy 3 blocks away");
            helper.assertTrue(!struck(far, nova), name + " III (radius 6) must not reach an enemy 7 blocks away");
            helper.assertTrue(!struck(pet, nova), name + " must spare the killer's pets");
        }

        Pillager target = helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new BlockPos(5, 1, 7));
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(enchantments, Items.IRON_SWORD, DungeonEnchantments.WARDENS_WRATH, 1));
        player.attack(target);
        helper.assertTrue(target.hasEffect(MobEffects.DARKNESS), "Warden's Wrath must darken its victim");

        Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(7, 1, 7));
        player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(enchantments, Items.IRON_SWORD, DungeonEnchantments.INSIGHT, 3));
        helper.assertTrue(EnchantmentHelper.processMobExperience(level, player, pig, 10) == 25, "Insight III must multiply experience by 2.5");
        helper.succeed();
    }

    /**
     * Soulbound items, held or worn, come back with the respawned player; everything else drops.
     */
    public static void soulboundItemsSurviveDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.RegistryLookup<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
        player.getInventory().setItem(3, enchanted(enchantments, Items.IRON_SWORD, DungeonEnchantments.SOULBOUND, 1));
        player.getInventory().setItem(4, new ItemStack(Items.DIRT, 16));
        player.setItemSlot(EquipmentSlot.HEAD, enchanted(enchantments, Items.IRON_HELMET, DungeonEnchantments.SOULBOUND, 1));
        Vec3 deathPos = player.position();

        // A mock player's client never finishes loading, which makes it invulnerable, so it dies directly.
        player.setHealth(0.0F);
        player.die(level.damageSources().genericKill());
        ServerPlayer respawned = level.getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        try {
            helper.assertTrue(respawned.getInventory().getItem(3).is(Items.IRON_SWORD), "A soulbound sword must stay in its slot");
            helper.assertTrue(respawned.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "A soulbound helmet must stay worn");
            helper.assertTrue(respawned.getInventory().getItem(4).isEmpty(), "Ordinary items must not come back");
            helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(deathPos, deathPos).inflate(4.0),
                    item -> item.getItem().is(Items.DIRT)).isEmpty(), "Ordinary items must drop where the player died");
        } finally {
            level.getServer().getPlayerList().remove(respawned);
        }
        helper.succeed();
    }

    /**
     * A grindstone moves an item's enchantments onto a book for experience and grinds a relic's bonuses into an
     * essence, leaving the items otherwise intact; an anvil puts the essence onto another armour piece.
     */
    public static void grindstonesSalvageBooksAndEssenceThatAnvilsReapply(GameTestHelper helper) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> sharpness = enchantments.getOrThrow(Enchantments.SHARPNESS);
        Holder<Enchantment> vanishing = enchantments.getOrThrow(Enchantments.VANISHING_CURSE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack sword = enchanted(enchantments, Items.IRON_SWORD, Enchantments.SHARPNESS, 6);
        sword.enchant(vanishing, 1);
        GrindstoneMenu grindstone = new GrindstoneMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
        grindstone.getSlot(0).set(sword);
        grindstone.getSlot(1).set(new ItemStack(Items.BOOK, 3));
        helper.assertTrue(grindstone.getSlot(1).mayPlace(new ItemStack(Items.BOOK)), "A grindstone must accept a plain book");
        ItemStack book = grindstone.getSlot(2).getItem();
        ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertTrue(book.is(Items.ENCHANTED_BOOK) && stored.getLevel(sharpness) == 6 && stored.getLevel(vanishing) == 0,
                "An item and a book must make a book of the item's non-curse enchantments, got " + book);
        player.experienceLevel = 5;
        helper.assertTrue(!grindstone.getSlot(2).mayPickup(player), "Extracting Sharpness VI must cost 6 levels");
        player.experienceLevel = 10;
        grindstone.clicked(2, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(grindstone.getCarried().is(Items.ENCHANTED_BOOK), "Taking the result must give the book");
        ItemStack stripped = grindstone.getSlot(0).getItem();
        helper.assertTrue(stripped.is(Items.IRON_SWORD) && stripped.getEnchantments().getLevel(sharpness) == 0
                        && stripped.getEnchantments().getLevel(vanishing) == 1,
                "The item must stay, keeping only its curses: " + stripped);
        helper.assertTrue(grindstone.getSlot(1).getItem().getCount() == 2 && player.experienceLevel == 4,
                "Extraction must use one book and 6 levels");

        ItemStack relic = new ItemStack(Items.IRON_CHESTPLATE);
        relic.set(DataComponents.ATTRIBUTE_MODIFIERS, relic.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .withModifierAdded(Attributes.MAX_HEALTH,
                        new AttributeModifier(ProceduralDungeon.of("relic/test/max_health"), 4.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.CHEST));
        double baseArmor = armorOf(new ItemStack(Items.IRON_CHESTPLATE), EquipmentSlot.CHEST);
        GrindstoneMenu essenceGrindstone = new GrindstoneMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
        essenceGrindstone.getSlot(0).set(relic);
        essenceGrindstone.clicked(2, 0, ContainerInput.PICKUP, player);
        ItemStack essence = essenceGrindstone.getCarried();
        ItemStack ground = essenceGrindstone.getSlot(0).getItem();
        helper.assertTrue(RelicEssence.isEssence(essence), "Grinding a relic alone must give a Relic Essence, got " + essence);
        helper.assertTrue(!RelicEssence.hasBonuses(ground) && armorOf(ground, EquipmentSlot.CHEST) == baseArmor,
                "The relic must lose its bonuses but keep its base armour");

        helper.assertTrue(RelicEssence.applyTo(new ItemStack(Items.IRON_SWORD), essence).isEmpty(), "Armour essence must not fit a sword");
        ItemStack helmet = anvil(player, new ItemStack(Items.IRON_HELMET), essence);
        double health = helmet.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.MAX_HEALTH, 20.0, EquipmentSlot.HEAD);
        helper.assertTrue(health == 24.0, "Armour essence must move its bonus onto a helmet's head slot, got " + health);
        ItemStack twice = RelicEssence.applyTo(helmet, essence).orElseThrow(() -> new AssertionError("Essences must stack on one item"));
        double stacked = twice.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.MAX_HEALTH, 20.0, EquipmentSlot.HEAD);
        helper.assertTrue(stacked == 28.0, "Two essences must add both bonuses, got " + stacked);
        helper.succeed();
    }

    /**
     * Every puzzle room places whole in any rotation: machine rooms hold their chest shut and start unsolved, the
     * parkour and stealth rooms leave theirs open. Their rarities (trims, sherds, discs) are found in no other chest.
     */
    public static void puzzleRoomsPlaceWholeAndHoldTheirChests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<PlacedPuzzle> placed = new ArrayList<>();
        for (DungeonPuzzleRooms.Puzzle puzzle : DungeonPuzzleRooms.Puzzle.values()) {
            for (Rotation rotation : List.of(Rotation.NONE, Rotation.CLOCKWISE_180)) {
                placed.add(placePuzzle(helper, puzzle, rotation));
            }
        }

        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds()
                .filter(key -> key.identifier().getNamespace().equals(ProceduralDungeon.MOD_ID))
                .filter(key -> !key.identifier().getPath().startsWith(DungeonPuzzleRooms.LOOT_TABLE + "/"))
                .forEach(key -> {
                    String json = LootTable.DIRECT_CODEC.encodeStart(ops, level.getServer().reloadableRegistries().getLootTable(key))
                            .getOrThrow().toString();
                    for (String rarity : List.of("_armor_trim_smithing_template", "_pottery_sherd", "minecraft:music_disc_")) {
                        helper.assertTrue(!json.contains(rarity), key.identifier() + " must not contain puzzle rarity " + rarity);
                    }
                });

        helper.runAfterDelay(10, () -> {
            for (PlacedPuzzle room : placed) {
                String label = room.puzzle() + " " + room.rotation();
                boolean machine = room.puzzle() != DungeonPuzzleRooms.Puzzle.PARKOUR && room.puzzle() != DungeonPuzzleRooms.Puzzle.STEALTH;
                helper.assertTrue(level.getBlockState(room.chest()).is(Blocks.CHEST), label + " must hold a reward chest");
                helper.assertTrue(DungeonLockManager.isPuzzleLocked(level, room.chest()) == machine,
                        label + (machine ? " must hold its chest shut" : " must leave its chest open"));
                helper.assertTrue(!room.solved(), label + " must start unsolved");
            }
            PlacedPuzzle stealth = placed.stream().filter(room -> room.puzzle() == DungeonPuzzleRooms.Puzzle.STEALTH).findFirst().orElseThrow();
            helper.assertTrue(level.getBlockState(stealth.at(1, 1, 9)).getValue(SculkShriekerBlock.CAN_SUMMON),
                    "The stealth room's shriekers must be able to summon the Warden");
            PlacedPuzzle parkour = placed.stream().filter(room -> room.puzzle() == DungeonPuzzleRooms.Puzzle.PARKOUR).findFirst().orElseThrow();
            helper.assertTrue(level.getBlockState(parkour.at(5, 1, 5)).is(Blocks.LAVA), "The parkour room's pit must be lava");
            placed.forEach(PlacedPuzzle::release);
            helper.succeed();
        });
    }

    /**
     * Arrows in frames, placed in a rotated room, must all point up (frame rotation 7) to open the chest.
     */
    public static void framePuzzleOpensWhenEveryArrowPointsUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlacedPuzzle room = placePuzzle(helper, DungeonPuzzleRooms.Puzzle.FRAMES, Rotation.CLOCKWISE_90);
        // Entities join a freshly loaded chunk a few ticks after it loads.
        helper.runAfterDelay(20, () -> framesPlaced(helper, level, room));
    }

    private static void framesPlaced(GameTestHelper helper, ServerLevel level, PlacedPuzzle room) {
        List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, AABB.of(room.box()));
        helper.assertTrue(frames.size() == 3, "The frame puzzle must hang three frames, found " + frames.size());
        for (ItemFrame frame : frames) {
            helper.assertTrue(frame.getDirection() == Rotation.CLOCKWISE_90.rotate(Direction.NORTH) && frame.getItem().is(Items.ARROW),
                    "Frames must hold arrows and turn with the room");
        }
        frames.get(0).setRotation(7);
        frames.get(1).setRotation(7);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!room.solved(), "Two of three arrows must not open the chest");
            frames.get(2).setRotation(7);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(room.solved(), "Three upward arrows must open the chest");
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                helper.assertTrue(DungeonLockManager.useBlock(player, level, room.chest()) == InteractionResult.PASS
                        && !DungeonLockManager.isPuzzleLocked(level, room.chest()), "A solved puzzle must release its chest");
                room.release();
                helper.succeed();
            });
        });
    }

    /**
     * Each chiseled bookshelf's last touched slot must be its last one (bottom right).
     */
    public static void bookshelfPuzzleOpensWhenEveryShelfEndsOnItsLastSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlacedPuzzle room = placePuzzle(helper, DungeonPuzzleRooms.Puzzle.BOOKSHELVES, Rotation.NONE);
        List<ChiseledBookShelfBlockEntity> shelves = List.of(2, 5, 8).stream()
                .map(x -> (ChiseledBookShelfBlockEntity) level.getBlockEntity(room.at(x, 3, 11)))
                .toList();
        shelves.forEach(shelf -> shelf.setItem(5, new ItemStack(Items.BOOK)));
        shelves.get(1).removeItem(0, 1);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!room.solved(), "A shelf last touched on another slot must keep the chest shut");
            shelves.get(1).setItem(0, new ItemStack(Items.BOOK));
            shelves.get(1).removeItem(5, 1);
            shelves.get(1).setItem(5, new ItemStack(Items.BOOK));
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(room.solved(), "Every shelf ending on its last slot must open the chest");
                room.release();
                helper.succeed();
            });
        });
    }

    /**
     * Arrows toggle the copper bulb over each target; all three lit opens the chest.
     */
    public static void targetPuzzleOpensWhenEveryBulbIsLit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlacedPuzzle room = placePuzzle(helper, DungeonPuzzleRooms.Puzzle.TARGETS, Rotation.NONE);
        shootAt(level, room, 2);
        shootAt(level, room, 5);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(level.getBlockState(room.at(2, 4, 11)).getValue(CopperBulbBlock.LIT), "A hit target must light its bulb");
            helper.assertTrue(!room.solved(), "Two lit bulbs must not open the chest");
            shootAt(level, room, 8);
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(room.solved(), "Three lit bulbs must open the chest");
                room.release();
                helper.succeed();
            });
        });
    }

    /**
     * Only the first, third, and fourth levers on (the second and last off) open the chest.
     */
    public static void chordPuzzleOpensOnlyForItsChord(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlacedPuzzle room = placePuzzle(helper, DungeonPuzzleRooms.Puzzle.CHORD, Rotation.CLOCKWISE_180);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int x = 3; x <= 7; x++) {
            use(level, player, room.at(x, 3, 10));
        }
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!room.solved(), "Every lever on must keep the chest shut");
            use(level, player, room.at(4, 3, 10));
            use(level, player, room.at(7, 3, 10));
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(room.solved(), "The chord (first, third, fourth) must open the chest");
                room.release();
                helper.succeed();
            });
        });
    }

    /**
     * With the drain open the stream never reaches the torch; with both gates open and the drain shut it washes the
     * torch away and opens the chest.
     */
    public static void sluicePuzzleOpensWhenTheStreamReachesTheTorch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlacedPuzzle room = placePuzzle(helper, DungeonPuzzleRooms.Puzzle.SLUICE, Rotation.NONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(level, player, room.at(4, 4, 4));
        use(level, player, room.at(4, 3, 9));
        use(level, player, room.at(5, 3, 7));
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(level.getBlockState(room.at(2, 2, 11)).is(Blocks.REDSTONE_TORCH), "An open drain must divert the stream from the torch");
            helper.assertTrue(!room.solved(), "The torch standing must keep the chest shut");
            use(level, player, room.at(5, 3, 7));
            helper.runAfterDelay(120, () -> {
                helper.assertTrue(!level.getBlockState(room.at(2, 2, 11)).is(Blocks.REDSTONE_TORCH), "The stream must wash the torch away");
                helper.assertTrue(room.solved(), "Washing the torch away must open the chest");
                room.release();
                helper.succeed();
            });
        });
    }

    /**
     * Crafting Music Disc 5 in the crafter sends the disc into the jukebox, which opens the chest.
     */
    public static void crafterPuzzleOpensWhenTheDiscPlays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlacedPuzzle room = placePuzzle(helper, DungeonPuzzleRooms.Puzzle.CRAFTER, Rotation.NONE);
        CrafterBlockEntity crafter = (CrafterBlockEntity) level.getBlockEntity(room.at(5, 3, 11));
        for (int slot = 0; slot < 9; slot++) {
            crafter.setItem(slot, new ItemStack(Items.DISC_FRAGMENT_5));
        }
        use(level, helper.makeMockPlayer(GameType.SURVIVAL), room.at(5, 3, 10));
        helper.runAfterDelay(20, () -> {
            JukeboxBlockEntity jukebox = (JukeboxBlockEntity) level.getBlockEntity(room.at(5, 3, 12));
            helper.assertTrue(jukebox.getTheItem().is(Items.MUSIC_DISC_5), "The crafted disc must land in the jukebox");
            helper.assertTrue(room.solved(), "A playing disc must open the chest");
            room.release();
            helper.succeed();
        });
    }

    private static final AtomicInteger PUZZLE_SLOTS = new AtomicInteger();
    private static final int PUZZLE_RUN_X = 12288 + 1024 * new Random().nextInt(4096);

    private static PlacedPuzzle placePuzzle(GameTestHelper helper, DungeonPuzzleRooms.Puzzle puzzle, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        // Sculk dungeons are the only ones whose room pool includes the stealth room.
        ResourceKey<StructureTemplatePool> poolKey = RegistryKeyUtils.create(
                Registries.TEMPLATE_POOL, RegistryKeyUtils.getKeyString(DungeonTheme.SCULK, DungeonTier.TIER_1) + "/hallway/room");
        StructurePoolElement element = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(poolKey).value()
                .getShuffledTemplates(RandomSource.create(0)).stream()
                .filter(candidate -> candidate instanceof VariantSinglePoolElement variant && variant.templateLocation().equals(puzzle.template()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The sculk room pool must offer " + puzzle));
        // Far from the test grid, each room in its own chunks, so one test releasing its chunks never stops another's.
        // The test world is kept between runs, so every run builds in fresh chunks, away from the last run's frames.
        int slot = PUZZLE_SLOTS.getAndIncrement();
        BlockPos origin = new BlockPos(PUZZLE_RUN_X + 64 * (slot % 16), 100, 12288 + 64 * (slot / 16));
        BoundingBox box = element.getBoundingBox(level.getStructureManager(), origin, rotation);
        forceChunks(level, box, true);
        element.place(level.getStructureManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
                origin, origin, rotation, box, RandomSource.create(0), LiquidSettings.IGNORE_WATERLOGGING, false);
        return new PlacedPuzzle(level, puzzle, origin, rotation, box);
    }

    private static void forceChunks(ServerLevel level, BoundingBox box, boolean forced) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                level.setChunkForced(x, z, forced);
            }
        }
    }

    private static void use(ServerLevel level, Player player, BlockPos pos) {
        level.getBlockState(pos).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void shootAt(ServerLevel level, PlacedPuzzle room, int x) {
        Vec3 from = Vec3.atCenterOf(room.at(x, 3, 8));
        Vec3 to = Vec3.atCenterOf(room.at(x, 3, 11));
        Arrow arrow = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
        Vec3 direction = to.subtract(from);
        arrow.shoot(direction.x, direction.y, direction.z, 2.0F, 0.0F);
        level.addFreshEntity(arrow);
    }

    private record PlacedPuzzle(ServerLevel level, DungeonPuzzleRooms.Puzzle puzzle, BlockPos origin, Rotation rotation, BoundingBox box) {
        BlockPos at(int x, int y, int z) {
            return StructureTemplate.calculateRelativePosition(new StructurePlaceSettings().setRotation(rotation), new BlockPos(x, y, z)).offset(origin);
        }

        BlockPos chest() {
            return at(9, 2, 11);
        }

        boolean solved() {
            return DungeonPuzzleRooms.isSolved(level, chest());
        }

        void release() {
            forceChunks(level, box, false);
        }
    }

    private static void killWith(GameTestHelper helper, Player player, ItemStack weapon, BlockPos pos) {
        Pig victim = helper.spawnWithNoFreeWill(EntityTypes.PIG, pos);
        victim.setHealth(0.1F);
        player.snapTo(helper.absoluteVec(Vec3.atBottomCenterOf(pos)));
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        player.attack(victim);
        helper.assertTrue(victim.isDeadOrDying(), "The victim must die from the hit");
    }

    private static int effectAmplifier(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    private static boolean struck(LivingEntity entity, DungeonEnchantments.Nova nova) {
        return nova.ignites() ? entity.getRemainingFireTicks() > 0 : nova.effects().stream().allMatch(entity::hasEffect);
    }

    private static double armorOf(ItemStack stack, EquipmentSlot slot) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0.0, slot);
    }

    private static ItemStack anvil(Player player, ItemStack input, ItemStack addition) {
        AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(0).set(input);
        menu.getSlot(1).set(addition);
        menu.createResult();
        return menu.getSlot(2).getItem();
    }

    private static ItemEnchantments storedEnchantment(Holder<Enchantment> enchantment, int level) {
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(enchantment, level);
        return stored.toImmutable();
    }

    private static ItemStack enchanted(HolderLookup.RegistryLookup<Enchantment> enchantments, Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(enchantments.getOrThrow(key), level);
        return stack;
    }

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, ProceduralDungeon.of(name));
    }

    private static int keyCount(Player player, DungeonKeyType type) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (type.matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void place(ServerLevel level, StagedDungeonLayout layout, StagedDungeonPieceSpec piece) {
        StagedDungeonGenerationManager.placeSynchronously(level, List.of(piece), LiquidSettings.IGNORE_WATERLOGGING);
        DungeonLockManager.applyPlanForPiece(level, layout.lockPlan(), piece.boundingBox());
    }

    private static List<StagedDungeonPieceSpec> pieces(StagedDungeonLayout layout, Identifier template) {
        return layout.pieces().stream()
                .filter(piece -> piece.element() instanceof VariantSinglePoolElement variant && variant.templateLocation().equals(template))
                .toList();
    }

    private static List<ResourceKey<LootTable>> chestLoot(ServerLevel level, BoundingBox box) {
        List<ResourceKey<LootTable>> lootTables = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.getLootTable() != null) {
                lootTables.add(chest.getLootTable());
            }
        }
        return lootTables;
    }

    private static List<Identifier> ejectedLoot(List<Weighted<ResourceKey<LootTable>>> lootTables) {
        return lootTables.stream().map(weighted -> weighted.value().identifier()).toList();
    }

    private static List<DungeonTier> lootSequence(long seed) {
        Random random = new Random(seed);
        List<DungeonTier> result = new ArrayList<>();
        for (DungeonTier tier : DungeonTier.values()) for (int roll = 0; roll < 32; roll++) result.add(tier.randomLootTier(random));
        return result;
    }

    public record TestCase(String path, Consumer<GameTestHelper> function) {}
}
