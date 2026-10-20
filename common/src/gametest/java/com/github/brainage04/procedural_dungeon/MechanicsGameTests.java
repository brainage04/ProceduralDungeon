package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.api.ProceduralDungeonApi;
import com.github.brainage04.procedural_dungeon.enchantment.DungeonEnchantments;
import com.github.brainage04.procedural_dungeon.reward.RelicEssence;
import com.github.brainage04.procedural_dungeon.rule.DungeonGameRules;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.stream.Stream;

/**
 * The switches that keep Procedural Dungeon's world-wide behaviour out of places that should not
 * have it: natural generation, grindstone salvage, the anvil changes and Soulbound, and the removed
 * legacy datapack functions.
 */
public final class MechanicsGameTests {
    private static final int NATURAL_START_SEARCH_REGIONS = 16;

    private MechanicsGameTests() {}

    public static List<DungeonGameTestSuite.TestCase> tests() {
        return List.of(
                new DungeonGameTestSuite.TestCase(
                        "natural_generation_switches_stop_new_dungeon_starts",
                        MechanicsGameTests::naturalGenerationSwitchesStopNewDungeonStarts),
                new DungeonGameTestSuite.TestCase(
                        "grindstone_salvage_game_rule_restores_the_vanilla_grindstone",
                        MechanicsGameTests::grindstoneSalvageGameRuleRestoresTheVanillaGrindstone),
                new DungeonGameTestSuite.TestCase(
                        "global_mechanics_scope_turns_off_salvage_anvils_and_soulbound",
                        MechanicsGameTests::globalMechanicsScopeTurnsOffSalvageAnvilsAndSoulbound),
                new DungeonGameTestSuite.TestCase(
                        "legacy_datapack_functions_are_gone",
                        MechanicsGameTests::legacyDatapackFunctionsAreGone));
    }

    /**
     * An Overworld chunk that starts a natural dungeon starts none while the game rule is off, or
     * while the API predicate rejects the dimension, and starts it again once both allow it.
     */
    public static void naturalGenerationSwitchesStopNewDungeonStarts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        RegistryAccess access = level.registryAccess();
        Holder<StructureSet> set =
                access.lookupOrThrow(Registries.STRUCTURE_SET)
                        .getOrThrow(
                                ResourceKey.create(
                                        Registries.STRUCTURE_SET,
                                        ProceduralDungeon.of("dungeon/overworld")));
        BiomeSource biomes =
                MultiNoiseBiomeSource.createFromPreset(
                        access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        ChunkGenerator generator =
                new NoiseBasedChunkGenerator(
                        biomes,
                        access.lookupOrThrow(Registries.NOISE_SETTINGS)
                                .getOrThrow(NoiseGeneratorSettings.OVERWORLD));
        long seed = 0x5EED_D06EL;
        RandomState randomState =
                RandomState.create(access, NoiseGeneratorSettings.OVERWORLD, seed);
        ChunkGeneratorStructureState state =
                ChunkGeneratorStructureState.createForFlat(
                        randomState, seed, biomes, Stream.of(set));
        RandomSpreadStructurePlacement placement =
                (RandomSpreadStructurePlacement) set.value().placement();
        ResourceKey<Level> overworld = Level.OVERWORLD;

        ChunkPos start = null;
        for (int region = 0; region < NATURAL_START_SEARCH_REGIONS && start == null; region++) {
            ChunkPos candidate = placement.getPotentialStructureChunk(seed, region, 0);
            if (startsDungeon(level, generator, state, candidate, overworld)) {
                start = candidate;
            }
        }
        helper.assertTrue(
                start != null,
                "One of %d Overworld candidate chunks must start a natural dungeon"
                        .formatted(NATURAL_START_SEARCH_REGIONS));

        boolean ruleBefore = server.getGameRules().get(DungeonGameRules.NATURAL_GENERATION);
        try {
            server.getGameRules().set(DungeonGameRules.NATURAL_GENERATION, false, server);
            helper.assertTrue(
                    !startsDungeon(level, generator, state, start, overworld),
                    "With procedural_dungeon:natural_generation false, no dungeon may start");
            server.getGameRules().set(DungeonGameRules.NATURAL_GENERATION, true, server);

            ProceduralDungeonApi.naturalGeneration(dimension -> dimension != overworld);
            helper.assertTrue(
                    !startsDungeon(level, generator, state, start, overworld),
                    "A dimension the API predicate rejects must start no dungeon");
            helper.assertTrue(
                    startsDungeon(level, generator, state, start, Level.NETHER),
                    "The predicate must only reject the dimensions it names");
        } finally {
            ProceduralDungeonApi.naturalGeneration(dimension -> true);
            server.getGameRules().set(DungeonGameRules.NATURAL_GENERATION, ruleBefore, server);
        }
        helper.assertTrue(
                startsDungeon(level, generator, state, start, overworld),
                "Once both switches allow it, the chunk must start its dungeon again");
        helper.succeed();
    }

    /** With the game rule off, a grindstone is vanilla again: no book salvage, no essence. */
    public static void grindstoneSalvageGameRuleRestoresTheVanillaGrindstone(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        HolderLookup.RegistryLookup<Enchantment> enchantments =
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ContainerLevelAccess grindstoneAt =
                ContainerLevelAccess.create(level, helper.absolutePos(BlockPos.ZERO));

        helper.assertTrue(
                grind(player, grindstoneAt, enchanted(enchantments, Items.IRON_SWORD, 3))
                        .is(Items.ENCHANTED_BOOK),
                "With the game rule on, an enchanted sword and a book must make a book");
        boolean ruleBefore = server.getGameRules().get(DungeonGameRules.GRINDSTONE_SALVAGE);
        try {
            server.getGameRules().set(DungeonGameRules.GRINDSTONE_SALVAGE, false, server);
            GrindstoneMenu vanilla = new GrindstoneMenu(0, player.getInventory(), grindstoneAt);
            helper.assertTrue(
                    !vanilla.getSlot(1).mayPlace(new ItemStack(Items.BOOK)),
                    "With the game rule off, a grindstone must refuse a plain book");
            ItemStack result =
                    grind(player, grindstoneAt, enchanted(enchantments, Items.IRON_SWORD, 3));
            helper.assertTrue(
                    !result.is(Items.ENCHANTED_BOOK),
                    "With the game rule off, a sword and a book must not make a book, got "
                            + result);
            GrindstoneMenu relicGrindstone =
                    new GrindstoneMenu(0, player.getInventory(), grindstoneAt);
            relicGrindstone.getSlot(0).set(relic());
            ItemStack ground = relicGrindstone.getSlot(2).getItem();
            helper.assertTrue(
                    !RelicEssence.isEssence(ground),
                    "With the game rule off, a relic must not grind into essence, got " + ground);
        } finally {
            server.getGameRules().set(DungeonGameRules.GRINDSTONE_SALVAGE, ruleBefore, server);
        }
        helper.succeed();
    }

    /**
     * A level the global-mechanics predicate rejects gets the vanilla grindstone, the vanilla anvil
     * cap, no relic essence on anvils and no Soulbound.
     */
    public static void globalMechanicsScopeTurnsOffSalvageAnvilsAndSoulbound(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.RegistryLookup<Enchantment> enchantments =
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> sharpness = enchantments.getOrThrow(Enchantments.SHARPNESS);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ContainerLevelAccess grindstoneAt =
                ContainerLevelAccess.create(level, helper.absolutePos(BlockPos.ZERO));
        ItemStack sixBook = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable six = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        six.set(sharpness, 6);
        sixBook.set(DataComponents.STORED_ENCHANTMENTS, six.toImmutable());

        helper.assertTrue(
                anvil(player, new ItemStack(Items.IRON_SWORD), sixBook)
                                .getEnchantments()
                                .getLevel(sharpness)
                        == 6,
                "In scope, a Sharpness VI book must give Sharpness VI");
        ServerPlayer dying = helper.makeMockServerPlayerInLevel();
        ServerPlayer respawned = null;
        ProceduralDungeonApi.globalMechanics(scoped -> scoped != level);
        try {
            ItemStack result =
                    grind(player, grindstoneAt, enchanted(enchantments, Items.IRON_SWORD, 3));
            helper.assertTrue(
                    !result.is(Items.ENCHANTED_BOOK),
                    "Out of scope, a grindstone must not salvage a book, got " + result);
            int carried =
                    anvil(player, new ItemStack(Items.IRON_SWORD), sixBook)
                            .getEnchantments()
                            .getLevel(sharpness);
            helper.assertTrue(
                    carried <= 5, "Out of scope, an anvil must cap Sharpness at V, got " + carried);
            ItemStack essence = RelicEssence.extract(relic());
            ItemStack helmet = anvil(player, new ItemStack(Items.IRON_HELMET), essence);
            helper.assertTrue(
                    !RelicEssence.hasBonuses(helmet),
                    "Out of scope, an anvil must not apply relic essence, got " + helmet);

            dying.snapTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
            dying.getInventory()
                    .setItem(
                            3,
                            enchanted(
                                    enchantments,
                                    Items.IRON_SWORD,
                                    DungeonEnchantments.SOULBOUND,
                                    1));
            Vec3 deathPos = dying.position();
            // A mock player's client never finishes loading, which makes it invulnerable.
            dying.setHealth(0.0F);
            dying.die(level.damageSources().genericKill());
            respawned =
                    level.getServer()
                            .getPlayerList()
                            .respawn(dying, false, Entity.RemovalReason.KILLED);
            helper.assertTrue(
                    respawned.getInventory().getItem(3).isEmpty(),
                    "Out of scope, a soulbound sword must not come back");
            helper.assertTrue(
                    !level.getEntitiesOfClass(
                                    ItemEntity.class,
                                    new AABB(deathPos, deathPos).inflate(4.0),
                                    item -> item.getItem().is(Items.IRON_SWORD))
                            .isEmpty(),
                    "Out of scope, a soulbound sword must drop where the player died");
        } finally {
            ProceduralDungeonApi.globalMechanics(scoped -> true);
            level.getServer().getPlayerList().remove(respawned != null ? respawned : dying);
        }
        helper.succeed();
    }

    /**
     * The old datapack UI ran player selectors every tick and offered a config that never existed.
     */
    public static void legacyDatapackFunctionsAreGone(GameTestHelper helper) {
        var functions = helper.getLevel().getServer().getFunctions();
        for (String tag : List.of("tick", "load")) {
            helper.assertTrue(
                    functions.getTag(Identifier.withDefaultNamespace(tag)).stream()
                            .noneMatch(
                                    function ->
                                            function.id()
                                                    .getNamespace()
                                                    .equals(ProceduralDungeon.MOD_ID)),
                    "The minecraft:%s function tag must not run Procedural Dungeon functions"
                            .formatted(tag));
        }
        for (Identifier name : functions.getFunctionNames()) {
            helper.assertTrue(
                    !name.getNamespace().equals(ProceduralDungeon.MOD_ID),
                    "Procedural Dungeon must ship no functions, found " + name);
        }
        helper.succeed();
    }

    private static boolean startsDungeon(
            ServerLevel level,
            ChunkGenerator generator,
            ChunkGeneratorStructureState state,
            ChunkPos pos,
            ResourceKey<Level> dimension) {
        ProtoChunk chunk =
                new ProtoChunk(
                        pos,
                        UpgradeData.EMPTY,
                        level,
                        PalettedContainerFactory.create(level.registryAccess()),
                        null);
        generator.createStructures(
                level.registryAccess(),
                state,
                level.structureManager(),
                chunk,
                level.getStructureManager(),
                dimension);
        return chunk.getAllStarts().entrySet().stream()
                .anyMatch(
                        entry ->
                                entry.getKey() instanceof StagedDungeonStructure
                                        && entry.getValue().isValid());
    }

    private static ItemStack grind(
            Player player, ContainerLevelAccess access, ItemStack enchantedItem) {
        GrindstoneMenu menu = new GrindstoneMenu(0, player.getInventory(), access);
        menu.getSlot(0).set(enchantedItem);
        menu.getSlot(1).set(new ItemStack(Items.BOOK));
        return menu.getSlot(2).getItem();
    }

    private static ItemStack anvil(Player player, ItemStack input, ItemStack addition) {
        AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(0).set(input);
        menu.getSlot(1).set(addition);
        menu.createResult();
        return menu.getSlot(2).getItem();
    }

    private static ItemStack relic() {
        ItemStack relic = new ItemStack(Items.IRON_CHESTPLATE);
        relic.set(
                DataComponents.ATTRIBUTE_MODIFIERS,
                relic.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                        .withModifierAdded(
                                Attributes.MAX_HEALTH,
                                new AttributeModifier(
                                        ProceduralDungeon.of("relic/test/max_health"),
                                        4.0,
                                        AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.CHEST));
        return relic;
    }

    private static ItemStack enchanted(
            HolderLookup.RegistryLookup<Enchantment> enchantments, Item item, int sharpness) {
        return enchanted(enchantments, item, Enchantments.SHARPNESS, sharpness);
    }

    private static ItemStack enchanted(
            HolderLookup.RegistryLookup<Enchantment> enchantments,
            Item item,
            ResourceKey<Enchantment> key,
            int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(enchantments.getOrThrow(key), level);
        return stack;
    }
}
