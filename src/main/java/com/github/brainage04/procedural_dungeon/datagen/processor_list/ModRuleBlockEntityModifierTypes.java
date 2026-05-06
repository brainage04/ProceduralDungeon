package com.github.brainage04.procedural_dungeon.datagen.processor_list;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.rule.blockentity.RuleBlockEntityModifierType;

public class ModRuleBlockEntityModifierTypes {
    public static final RuleBlockEntityModifierType<ReplaceLootByOldTableModifier> REPLACE_LOOT_BY_OLD_TABLE =
            Registry.register(
                    Registries.RULE_BLOCK_ENTITY_MODIFIER,
                    ProceduralDungeon.of("replace_loot_by_old_table"),
                    () -> ReplaceLootByOldTableModifier.CODEC
            );

    public static void initialize() {
        // load class and run static init
    }
}
