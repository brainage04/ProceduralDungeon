package com.github.brainage04.procedural_dungeon.datagen.processor_list;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.RuleBlockEntityModifier;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.RuleBlockEntityModifierType;

public class ReplaceLootByOldTableModifier implements RuleBlockEntityModifier {
    // oldLootTable -> newLootTable
    public static final MapCodec<ReplaceLootByOldTableModifier> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC)
                 .fieldOf("replacements")
                 .forGetter(mod -> mod.replacements)
        ).apply(instance, ReplaceLootByOldTableModifier::new));

    private final Map<Identifier, Identifier> replacements;

    public ReplaceLootByOldTableModifier(Map<Identifier, Identifier> replacements) {
        this.replacements = replacements;
    }

    @Override
    public CompoundTag apply(RandomSource random, CompoundTag nbt) {
        if (nbt == null) return null;

        String oldLootTable = nbt.getString("LootTable").orElse(null);
        if (oldLootTable == null) return nbt;

        Identifier oldId = Identifier.tryParse(oldLootTable);
        if (oldId == null) return nbt;

        Identifier newId = replacements.get(oldId);
        if (newId == null) return nbt;

        CompoundTag copy = nbt.copy();
        copy.putString("LootTable", newId.toString());
        copy.putLong("LootTableSeed", random.nextLong());

        return copy;
    }

    @Override
    public RuleBlockEntityModifierType<?> getType() {
        return ModRuleBlockEntityModifierTypes.REPLACE_LOOT_BY_OLD_TABLE;
    }
}