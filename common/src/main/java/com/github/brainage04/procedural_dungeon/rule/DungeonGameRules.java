package com.github.brainage04.procedural_dungeon.rule;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;

import java.util.function.BiConsumer;

/**
 * The server-wide switches an operator sets with {@code /gamerule}. Mods that embed Procedural
 * Dungeon narrow them per dimension through {@link DungeonSwitches}.
 */
public final class DungeonGameRules {
    /**
     * {@code procedural_dungeon:natural_generation}: dungeons generate in new chunks. Chunks that
     * already hold a dungeon start keep it.
     */
    public static final GameRule<Boolean> NATURAL_GENERATION = bool(GameRuleCategory.SPAWNING);

    /**
     * {@code procedural_dungeon:grindstone_salvage}: a grindstone turns an enchanted item and a
     * book into an enchanted book, and a dungeon item into relic essence.
     */
    public static final GameRule<Boolean> GRINDSTONE_SALVAGE = bool(GameRuleCategory.PLAYER);

    private DungeonGameRules() {}

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(ProceduralDungeon.of("natural_generation"), NATURAL_GENERATION);
        registry.accept(ProceduralDungeon.of("grindstone_salvage"), GRINDSTONE_SALVAGE);
    }

    private static GameRule<Boolean> bool(GameRuleCategory category) {
        return new GameRule<>(
                category,
                GameRuleType.BOOL,
                BoolArgumentType.bool(),
                GameRuleTypeVisitor::visitBoolean,
                Codec.BOOL,
                value -> value ? 1 : 0,
                true,
                FeatureFlagSet.of());
    }
}
