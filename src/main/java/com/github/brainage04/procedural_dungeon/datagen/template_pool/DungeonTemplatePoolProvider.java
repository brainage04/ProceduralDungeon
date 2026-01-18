package com.github.brainage04.procedural_dungeon.datagen.template_pool;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.util.DungeonUtils;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.pool.StructurePool;

import java.util.concurrent.CompletableFuture;

public class DungeonTemplatePoolProvider extends FabricDynamicRegistryProvider {
    public DungeonTemplatePoolProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    private static RegistryKey<StructurePool> create(String name) {
        return RegistryKey.of(RegistryKeys.TEMPLATE_POOL, ProceduralDungeon.of(name));
    }

    private static void register(Entries entries, RegistryKey<StructurePool> key, StructurePool value) {
        entries.add(key, value);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
        for (DungeonUtils.ThemeTierKeyPair themeTierKeyPair : DungeonUtils.THEME_TIER_KEY_COMBINATIONS) {
            RegistryKey<StructurePool> key = create(themeTierKeyPair.key());



            register(
                    entries,
                    key,
                    new StructurePool(

                    )
            );
        }
    }

    @Override
    public String getName() {
        return "Dungeon Template Pools";
    }
}
