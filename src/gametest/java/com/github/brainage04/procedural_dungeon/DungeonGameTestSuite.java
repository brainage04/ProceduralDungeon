package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.test.DungeonVariantSmokeTester;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;

public final class DungeonGameTestSuite {
    private DungeonGameTestSuite() {}

    public static List<TestCase> tests() {
        return List.of(
                new TestCase("variant_catalog_is_stable_and_complete", DungeonGameTestSuite::variantCatalogIsStableAndComplete),
                new TestCase("seeded_loot_selection_is_repeatable_and_tier_bounded", DungeonGameTestSuite::seededLootSelectionIsRepeatableAndTierBounded)
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

    private static List<DungeonTier> lootSequence(long seed) {
        Random random = new Random(seed);
        List<DungeonTier> result = new ArrayList<>();
        for (DungeonTier tier : DungeonTier.values()) for (int roll = 0; roll < 32; roll++) result.add(tier.randomLootTier(random));
        return result;
    }

    public record TestCase(String path, Consumer<GameTestHelper> function) {}
}
