package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.DataOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.DataWriter;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DungeonWorldgenProvider implements DataProvider {
    private final FabricDataOutput output;
    private final DataOutput.PathResolver templatePoolResolver;
    private final DataOutput.PathResolver structureResolver;
    private final DataOutput.PathResolver structureSetResolver;

    public DungeonWorldgenProvider(FabricDataOutput output) {
        this.output = output;
        this.templatePoolResolver = output.getResolver(DataOutput.OutputType.DATA_PACK, "worldgen/template_pool");
        this.structureResolver = output.getResolver(DataOutput.OutputType.DATA_PACK, "worldgen/structure");
        this.structureSetResolver = output.getResolver(DataOutput.OutputType.DATA_PACK, "worldgen/structure_set");
    }

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        List<CompletableFuture<?>> futures = new ArrayList<>();

        for (DungeonTheme theme : DungeonTheme.values()) {
            for (DungeonTier tier : DungeonTier.values()) {
                String key = RegistryKeyUtils.getKeyString(theme, tier);
                Identifier id = ProceduralDungeon.of(key);

                addTemplatePools(writer, futures, key, id);
                futures.add(DataProvider.writeToPath(writer, createStructureJson(key, tier, theme), structureResolver.resolveJson(id)));
                futures.add(DataProvider.writeToPath(writer, createStructureSetJson(key, tier), structureSetResolver.resolveJson(id)));
            }
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private void addTemplatePools(DataWriter writer, List<CompletableFuture<?>> futures, String key, Identifier variantId) {
        addTemplatePool(writer, futures, "%s/start".formatted(key), List.of(
                poolElement("dungeon/start", variantId)
        ));

        addTemplatePool(writer, futures, "%s/hallway".formatted(key), List.of(
                poolElement("dungeon/hallway/small", variantId),
                poolElement("dungeon/hallway/medium", variantId),
                poolElement("dungeon/hallway/large", variantId)
        ));

        addTemplatePool(writer, futures, "%s/hallway/end".formatted(key), List.of(
                poolElement("dungeon/hallway/end/small", variantId),
                poolElement("dungeon/hallway/end/medium", variantId),
                poolElement("dungeon/hallway/end/large", variantId)
        ));

        addTemplatePool(writer, futures, "%s/hallway/loot".formatted(key), List.of(
                poolElement("dungeon/hallway/loot/small", variantId),
                poolElement("dungeon/hallway/loot/medium", variantId),
                poolElement("dungeon/hallway/loot/large", variantId)
        ));

        addTemplatePool(writer, futures, "%s/hallway/room".formatted(key), List.of(
                poolElement("dungeon/hallway/room/armorsmith", variantId),
                poolElement("dungeon/hallway/room/enchanter", variantId),
                poolElement("dungeon/hallway/room/spawner_corridor", variantId),
                poolElement("dungeon/hallway/room/staircase_diagonal_down", variantId),
                poolElement("dungeon/hallway/room/staircase_diagonal_up", variantId),
                poolElement("dungeon/hallway/room/staircase_spiral_down", variantId),
                poolElement("dungeon/hallway/room/staircase_spiral_up", variantId),
                poolElement("dungeon/hallway/room/toolsmith", variantId),
                poolElement("dungeon/hallway/room/weaponsmith", variantId)
        ));

        addTemplatePool(writer, futures, "%s/hallway/trap".formatted(key), List.of(
                poolElement("dungeon/hallway/trap/dripstone", variantId),
                poolElement("dungeon/hallway/trap/lava", variantId),
                poolElement("dungeon/hallway/trap/negative_potions", variantId),
                poolElement("dungeon/hallway/trap/spawners", variantId)
        ));
    }

    private void addTemplatePool(DataWriter writer, List<CompletableFuture<?>> futures, String path, List<JsonObject> elements) {
        JsonObject pool = new JsonObject();
        pool.addProperty("fallback", "minecraft:empty");

        JsonArray array = new JsonArray();
        for (JsonObject element : elements) {
            array.add(element);
        }
        pool.add("elements", array);

        futures.add(DataProvider.writeToPath(writer, pool, templatePoolResolver.resolveJson(ProceduralDungeon.of(path))));
    }

    private static JsonObject poolElement(String structure, Identifier variantId) {
        JsonObject element = new JsonObject();
        element.addProperty("element_type", ProceduralDungeon.of("variant_single_pool_element").toString());
        element.addProperty("location", ProceduralDungeon.of(structure).toString());
        element.addProperty("processors", variantId.toString());
        element.addProperty("projection", "rigid");
        element.addProperty("override_liquid_settings", "ignore_waterlogging");
        element.addProperty("variant", variantId.toString());
        element.addProperty("spawner_tier", getTier(variantId));

        JsonObject weightedElement = new JsonObject();
        weightedElement.add("element", element);
        weightedElement.addProperty("weight", 1);
        return weightedElement;
    }

    private static int getTier(Identifier variantId) {
        String path = variantId.getPath();
        int index = path.lastIndexOf("tier_");
        if (index < 0) {
            throw new IllegalArgumentException("Variant id does not end in a tier path: " + variantId);
        }

        return Integer.parseInt(path.substring(index + "tier_".length()));
    }

    private static JsonObject createStructureJson(String key, DungeonTier tier, DungeonTheme theme) {
        JsonObject structure = new JsonObject();
        structure.addProperty("type", "minecraft:jigsaw");
        structure.addProperty("biomes", getBiomeTag(theme));
        structure.addProperty("step", "underground_structures");
        structure.addProperty("terrain_adaptation", "bury");
        structure.addProperty("start_pool", ProceduralDungeon.of("%s/start".formatted(key)).toString());
        structure.addProperty("start_jigsaw_name", "minecraft:start");
        structure.addProperty("size", tier.size);
        structure.add("start_height", absoluteHeight(0));
        structure.addProperty("project_start_to_heightmap", "WORLD_SURFACE_WG");
        structure.addProperty("max_distance_from_center", 116);
        structure.addProperty("use_expansion_hack", true);
        structure.addProperty("liquid_settings", "ignore_waterlogging");
        structure.add("spawn_overrides", new JsonObject());
        return structure;
    }

    private static String getBiomeTag(DungeonTheme theme) {
        if (theme.dimension.equals(World.NETHER)) {
            return "#minecraft:is_nether";
        }

        if (theme.dimension.equals(World.END)) {
            return "#minecraft:is_end";
        }

        return "#minecraft:is_overworld";
    }

    private static JsonObject createStructureSetJson(String key, DungeonTier tier) {
        JsonObject weightedStructure = new JsonObject();
        weightedStructure.addProperty("structure", ProceduralDungeon.of(key).toString());
        weightedStructure.addProperty("weight", 1);

        JsonArray structures = new JsonArray();
        structures.add(weightedStructure);

        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("spacing", tier.spacing);
        placement.addProperty("separation", tier.separation);
        placement.addProperty("salt", deterministicSalt(key));

        JsonObject structureSet = new JsonObject();
        structureSet.add("structures", structures);
        structureSet.add("placement", placement);
        return structureSet;
    }

    private static JsonObject absoluteHeight(int y) {
        JsonObject height = new JsonObject();
        height.addProperty("absolute", y);
        return height;
    }

    private static int deterministicSalt(String key) {
        return Math.floorMod(key.hashCode(), Integer.MAX_VALUE);
    }

    @Override
    public String getName() {
        return "Procedural Dungeon Worldgen";
    }
}
