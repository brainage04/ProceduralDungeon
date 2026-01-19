package com.github.brainage04.procedural_dungeon.datagen.core;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import net.minecraft.structure.rule.blockentity.RuleBlockEntityModifierType;

public class ModRuleBlockEntityModifierTypes {
    public static final RuleBlockEntityModifierType<ReplaceLootByOldTableModifier> REPLACE_LOOT_BY_OLD_TABLE =
            RuleBlockEntityModifierType.register(
                    ProceduralDungeon.of("replace_loot_by_old_table"),
                    ReplaceLootByOldTableModifier.CODEC
            );

    public static void initialize() {
        // load class and run static init
    }
}