package com.github.brainage04.procedural_dungeon.datagen.enchantment;

import com.github.brainage04.procedural_dungeon.enchantment.DungeonDamageTypes;
import com.github.brainage04.procedural_dungeon.enchantment.DungeonEnchantments;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Writes the entries {@link DungeonEnchantments#bootstrap} and {@link DungeonDamageTypes#bootstrap} add to the
 * datagen registries.
 */
public class DungeonEnchantmentProvider extends FabricDynamicRegistryProvider {
    public DungeonEnchantmentProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void configure(HolderLookup.Provider registries, Entries entries) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        DungeonEnchantments.all().forEach(key -> entries.add(enchantments.getOrThrow(key)));
        entries.add(registries.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DungeonDamageTypes.VOLATILE_BLAST));
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Enchantments";
    }
}
