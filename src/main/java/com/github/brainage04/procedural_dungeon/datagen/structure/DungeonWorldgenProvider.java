package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.core.ProceduralDungeonGenerator;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DungeonWorldgenProvider implements DataProvider {
    private static final int PLACEMENT_SPACING = DungeonTier.TIER_1.spacing;
    private static final int PLACEMENT_SEPARATION = DungeonTier.TIER_1.separation;

    private final FabricPackOutput output;
    private final PackOutput.PathProvider templatePoolResolver;
    private final PackOutput.PathProvider structureResolver;
    private final PackOutput.PathProvider structureSetResolver;

    public DungeonWorldgenProvider(FabricPackOutput output) {
        this.output = output;
        this.templatePoolResolver = output.createPathProvider(PackOutput.Target.DATA_PACK, "worldgen/template_pool");
        this.structureResolver = output.createPathProvider(PackOutput.Target.DATA_PACK, "worldgen/structure");
        this.structureSetResolver = output.createPathProvider(PackOutput.Target.DATA_PACK, "worldgen/structure_set");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        Map<Identifier, List<WeightedStructure>> structureSets = new LinkedHashMap<>();

        for (DungeonTheme theme : DungeonTheme.values()) {
            for (DungeonTier tier : DungeonTier.values()) {
                String key = RegistryKeyUtils.getKeyString(theme, tier);
                Identifier id = ProceduralDungeon.of(key);

                addTemplatePools(writer, futures, key, id, theme);
                futures.add(DataProvider.saveStable(writer, createStructureJson(key, tier, theme), structureResolver.json(id)));
                structureSets.computeIfAbsent(createStructureSetId(theme), ignored -> new ArrayList<>())
                        .add(new WeightedStructure(id, getTierWeight(tier)));
            }
        }

        for (Map.Entry<Identifier, List<WeightedStructure>> entry : structureSets.entrySet()) {
            futures.add(DataProvider.saveStable(
                    writer,
                    createStructureSetJson(entry.getKey(), entry.getValue()),
                    structureSetResolver.json(entry.getKey())
            ));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private void addTemplatePools(CachedOutput writer, List<CompletableFuture<?>> futures, String key, Identifier variantId, DungeonTheme theme) {
        int tier = getTier(variantId);
        if (!theme.dimension.equals(Level.NETHER)) {
            addTemplatePool(writer, futures, "%s/entrance".formatted(key), List.of(
                    poolElement("dungeon/entrance/tier_%d/ruined_archway".formatted(tier), variantId, 3, Integer.MAX_VALUE),
                    poolElement("dungeon/entrance/tier_%d/sunken_courtyard".formatted(tier), variantId, 3, Integer.MAX_VALUE),
                    poolElement("dungeon/entrance/tier_%d/ritual_descent".formatted(tier), variantId, 3, Integer.MAX_VALUE)
            ));
        }

        addTemplatePool(writer, futures, "%s/start".formatted(key), List.of(
                templatePoolElement(key, "dungeon/start", theme, tier, variantId, 1, startBranchLimit(variantId))
        ));

        addTemplatePool(writer, futures, "%s/hallway".formatted(key), List.of(
                templatePoolElement(key, "dungeon/hallway/small", theme, tier, variantId, 6, primaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/medium", theme, tier, variantId, 3, primaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/large", theme, tier, variantId, 1, primaryBranchLimit(variantId))
        ));

        addTemplatePool(writer, futures, "%s/hallway/end".formatted(key), List.of(
                templatePoolElement(key, "dungeon/hallway/end/small", theme, tier, variantId, 3, secondaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/end/medium", theme, tier, variantId, 2, secondaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/end/large", theme, tier, variantId, 1, secondaryBranchLimit(variantId))
        ));

        addTemplatePool(writer, futures, "%s/hallway/loot".formatted(key), List.of(
                templatePoolElement(key, "dungeon/hallway/loot/small", theme, tier, variantId, 3, secondaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/loot/medium", theme, tier, variantId, 2, secondaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/loot/large", theme, tier, variantId, 1, secondaryBranchLimit(variantId))
        ));

        addTemplatePool(writer, futures, "%s/hallway/room".formatted(key), List.of(
                templatePoolElement(
                        key,
                        "dungeon/hallway/room/armorsmith",
                        "dungeon/hallway/room/armorsmith/tier_%d".formatted(tier),
                        theme,
                        tier,
                        variantId,
                        2,
                        secondaryBranchLimit(variantId)
                ),
                templatePoolElement(key, "dungeon/hallway/room/enchanter", theme, tier, variantId, 2, secondaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/room/spawner_corridor", theme, tier, variantId, 2, secondaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/room/staircase_diagonal_down", theme, tier, variantId, 16, secondaryBranchLimit(variantId)),
                templatePoolElement(
                        key,
                        "dungeon/hallway/room/staircase_diagonal_down",
                        "dungeon/hallway/room/staircase_diagonal_up",
                        theme,
                        tier,
                        variantId,
                        1,
                        secondaryBranchLimit(variantId)
                ),
                templatePoolElement(key, "dungeon/hallway/room/staircase_spiral_down", theme, tier, variantId, 16, secondaryBranchLimit(variantId)),
                templatePoolElement(
                        key,
                        "dungeon/hallway/room/staircase_spiral_down",
                        "dungeon/hallway/room/staircase_spiral_up",
                        theme,
                        tier,
                        variantId,
                        1,
                        secondaryBranchLimit(variantId)
                ),
                templatePoolElement(
                        key,
                        "dungeon/hallway/room/toolsmith",
                        "dungeon/hallway/room/toolsmith/tier_%d".formatted(tier),
                        theme,
                        tier,
                        variantId,
                        2,
                        secondaryBranchLimit(variantId)
                ),
                templatePoolElement(
                        key,
                        "dungeon/hallway/room/weaponsmith",
                        "dungeon/hallway/room/weaponsmith/tier_%d".formatted(tier),
                        theme,
                        tier,
                        variantId,
                        2,
                        secondaryBranchLimit(variantId)
                )
        ));

        addTemplatePool(writer, futures, "%s/hallway/trap".formatted(key), List.of(
                templatePoolElement(key, "dungeon/hallway/trap/dripstone", theme, tier, variantId, 1, secondaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/trap/lava", theme, tier, variantId, 1, secondaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/trap/negative_potions", theme, tier, variantId, 1, secondaryBranchLimit(variantId)),
                templatePoolElement(key, "dungeon/hallway/trap/spawners", theme, tier, variantId, 1, secondaryBranchLimit(variantId))
        ));
    }

    private void addTemplatePool(CachedOutput writer, List<CompletableFuture<?>> futures, String path, List<JsonObject> elements) {
        JsonObject pool = new JsonObject();
        pool.addProperty("fallback", "minecraft:empty");

        JsonArray array = new JsonArray();
        for (JsonObject element : elements) {
            array.add(element);
        }
        pool.add("elements", array);

        futures.add(DataProvider.saveStable(writer, pool, templatePoolResolver.json(ProceduralDungeon.of(path))));
    }

    private static JsonObject poolElement(String structure, Identifier variantId, int weight, int branchLimit) {
        return poolElement(structure, variantId, variantId, weight, branchLimit);
    }

    private static JsonObject templatePoolElement(
            String key,
            String structure,
            DungeonTheme theme,
            int tier,
            Identifier variantId,
            int weight,
            int branchLimit
    ) {
        return poolElement(
                structure,
                ProceduralDungeon.of(ProceduralDungeonGenerator.templateProcessorKey(key, structure, theme, tier)),
                variantId,
                weight,
                branchLimit
        );
    }

    private static JsonObject templatePoolElement(
            String key,
            String processorStructure,
            String locationStructure,
            DungeonTheme theme,
            int tier,
            Identifier variantId,
            int weight,
            int branchLimit
    ) {
        return poolElement(
                locationStructure,
                ProceduralDungeon.of(ProceduralDungeonGenerator.templateProcessorKey(key, processorStructure, theme, tier)),
                variantId,
                weight,
                branchLimit
        );
    }

    private static JsonObject poolElement(String structure, Identifier processorId, Identifier variantId, int weight, int branchLimit) {
        JsonObject element = new JsonObject();
        element.addProperty("element_type", ProceduralDungeon.of("variant_single_pool_element").toString());
        element.addProperty("location", ProceduralDungeon.of(structure).toString());
        element.addProperty("processors", processorId.toString());
        element.addProperty("projection", "rigid");
        element.addProperty("override_liquid_settings", "ignore_waterlogging");
        element.addProperty("variant", variantId.toString());
        element.addProperty("spawner_tier", getTier(variantId));
        element.addProperty("branch_limit", branchLimit);

        JsonObject weightedElement = new JsonObject();
        weightedElement.add("element", element);
        weightedElement.addProperty("weight", weight);
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

    private static int startBranchLimit(Identifier variantId) {
        return switch (getTier(variantId)) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            default -> 4;
        };
    }

    private static int primaryBranchLimit(Identifier variantId) {
        return switch (getTier(variantId)) {
            case 1 -> 1;
            case 2, 3 -> 2;
            case 4 -> 3;
            default -> 4;
        };
    }

    private static int secondaryBranchLimit(Identifier variantId) {
        return switch (getTier(variantId)) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            default -> 3;
        };
    }

    private static JsonObject createStructureJson(String key, DungeonTier tier, DungeonTheme theme) {
        JsonObject structure = new JsonObject();
        structure.addProperty("type", ProceduralDungeon.of("staged_dungeon").toString());
        structure.addProperty("biomes", getBiomeTag(theme));
        structure.addProperty("step", "underground_structures");
        structure.addProperty("terrain_adaptation", "bury");
        boolean nether = theme.dimension.equals(Level.NETHER);
        boolean entrance = !nether;
        structure.addProperty("start_pool", ProceduralDungeon.of("%s/%s".formatted(key, entrance ? "entrance" : "start")).toString());
        structure.addProperty("start_jigsaw_name", "minecraft:start");
        structure.addProperty("size", tier.worldgenSize + (entrance ? 1 : 0));
        if (nether) {
            structure.add("start_height", absoluteHeight(48));
            structure.addProperty("placement_height_mode", "solid_density");
            structure.addProperty("solid_density_min_y", 24);
            structure.addProperty("solid_density_max_y", 104);
            structure.addProperty("solid_density_window", Math.max(24, Math.abs(tier.surfaceOffset) / 2));
            structure.addProperty("solid_density_step", 4);
            structure.addProperty("solid_density_horizontal_radius", 16);
        } else {
            structure.add("start_height", absoluteHeight(0));
            structure.addProperty("project_start_to_heightmap", "WORLD_SURFACE_WG");
        }
        structure.addProperty("max_distance_from_center", tier.maxDistanceFromCenter);
        structure.addProperty("use_expansion_hack", true);
        structure.addProperty("liquid_settings", "ignore_waterlogging");
        structure.add("spawn_overrides", new JsonObject());
        return structure;
    }

    private static String getBiomeTag(DungeonTheme theme) {
        if (theme.dimension.equals(Level.NETHER)) {
            return "#minecraft:is_nether";
        }

        if (theme.dimension.equals(Level.END)) {
            return "#minecraft:is_end";
        }

        return "#minecraft:is_overworld";
    }

    private static JsonObject createStructureSetJson(Identifier id, List<WeightedStructure> weightedStructures) {
        JsonArray structures = new JsonArray();
        for (WeightedStructure structure : weightedStructures) {
            JsonObject weightedStructure = new JsonObject();
            weightedStructure.addProperty("structure", structure.id().toString());
            weightedStructure.addProperty("weight", structure.weight());
            structures.add(weightedStructure);
        }

        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("spacing", PLACEMENT_SPACING);
        placement.addProperty("separation", PLACEMENT_SEPARATION);
        placement.addProperty("salt", deterministicSalt(id.toString()));

        JsonObject structureSet = new JsonObject();
        structureSet.add("structures", structures);
        structureSet.add("placement", placement);
        return structureSet;
    }

    private static Identifier createStructureSetId(DungeonTheme theme) {
        return ProceduralDungeon.of("dungeon/%s".formatted(theme.dimension.identifier().getPath()));
    }

    private static int getTierWeight(DungeonTier tier) {
        double relativeFrequency = Math.pow((double) DungeonTier.TIER_1.spacing / tier.spacing, 2);
        return Math.max(1, (int) Math.round(relativeFrequency * 144));
    }

    private static JsonObject absoluteHeight(int y) {
        JsonObject height = new JsonObject();
        height.addProperty("absolute", y);
        return height;
    }

    private static int deterministicSalt(String key) {
        return Math.floorMod(key.hashCode(), Integer.MAX_VALUE);
    }

    private record WeightedStructure(Identifier id, int weight) {}

    @Override
    public String getName() {
        return "Procedural Dungeon Worldgen";
    }
}
