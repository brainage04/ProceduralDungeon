package com.github.brainage04.procedural_dungeon.dungeon;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.util.StringUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;

public enum DungeonTheme implements StringRepresentable {
    COBBLESTONE,
    DEEPSLATE,
    SCULK,
    DESERT_TOMB,
    LUSH,
    DRIPSTONE,
    FROZEN,
    OCEAN_RUIN,
    AMETHYST_GEODE,
    COPPER,
    STRONGHOLD,
    NETHER_WASTES,
    CRIMSON_FOREST,
    WARPED_FOREST,
    BASALT_DELTAS,
    SOUL_SAND_VALLEY,
    NETHER_FORTRESS,
    BASTION,
    END_STONE,
    END_CITY;

    public String id;
    public ResourceKey<Level> dimension;
    public List<ProcessorRuleSpec> processorRules;
    public ShapeReplacementSpec shapeReplacements;
    public GenerationProfile profile;

    private static final Set<String> VALID_TRAPS = Set.of("dripstone", "lava", "negative_potions", "spawners");
    private static final Set<String> VALID_ROOMS = Set.of(
            "armorsmith",
            "enchanter",
            "spawner_corridor",
            "staircase_diagonal_down",
            "staircase_diagonal_up",
            "staircase_spiral_down",
            "staircase_spiral_up",
            "toolsmith",
            "weaponsmith"
    );
    private static List<ProcessorRuleSpec> bookshelfRules;
    private static List<ProcessorRuleSpec> mineralRules;
    private static List<ProcessorRuleSpec> airRules;
    private static float ageChance;
    private static float rotChance;

    static {
        JsonObject spec = DungeonSpecLoader.load("themes.json");
        JsonObject generic = spec.getAsJsonObject("generic");
        bookshelfRules = rules(generic.getAsJsonArray("bookshelf"));
        mineralRules = rules(generic.getAsJsonArray("minerals"));
        airRules = rules(generic.getAsJsonArray("air"));
        ageChance = generic.getAsJsonObject("decay").get("age").getAsFloat();
        rotChance = generic.getAsJsonObject("decay").get("rot").getAsFloat();

        JsonArray themes = spec.getAsJsonArray("themes");
        if (themes.size() != values().length) {
            throw new IllegalStateException("Theme spec count does not match DungeonTheme count");
        }

        for (JsonElement element : themes) {
            JsonObject themeSpec = element.getAsJsonObject();
            DungeonTheme theme = getById(themeSpec.get("name").getAsString());
            theme.id = themeSpec.get("name").getAsString();
            theme.dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(themeSpec.get("dimension").getAsString()));
            theme.processorRules = rules(themeSpec.getAsJsonArray("rules"));
            theme.shapeReplacements = shapes(themeSpec.getAsJsonObject("shapes"));
            theme.profile = profile(theme, themeSpec.getAsJsonObject("profile"));
        }
    }

    private static DungeonTheme getById(String id) {
        for (DungeonTheme theme : values()) {
            if (theme.name().equalsIgnoreCase(id)) {
                return theme;
            }
        }

        throw new IllegalArgumentException("Unsupported dungeon theme: " + id);
    }

    public static List<ProcessorRuleSpec> bookshelfRules() {
        return bookshelfRules;
    }

    public static List<ProcessorRuleSpec> mineralRules() {
        return mineralRules;
    }

    public static List<ProcessorRuleSpec> airRules() {
        return airRules;
    }

    public static float ageChance() {
        return ageChance;
    }

    public static float rotChance() {
        return rotChance;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Identifier getId() {
        return Identifier.fromNamespaceAndPath(ProceduralDungeon.MOD_ID, this.getSerializedName());
    }

    public Component getName() {
        return Component.literal(StringUtils.snakeCaseToHumanReadable(this.getSerializedName()));
    }

    private static List<ProcessorRuleSpec> rules(JsonArray specs) {
        return specs.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(spec -> new ProcessorRuleSpec(
                        spec.get("input").getAsString(),
                        spec.get("probability").getAsFloat(),
                        spec.get("output").getAsString()
                ))
                .toList();
    }

    private static ShapeReplacementSpec shapes(JsonObject spec) {
        return new ShapeReplacementSpec(
                spec.get("fallback").getAsString(),
                optionalString(spec, "stairs"),
                optionalString(spec, "slab"),
                optionalString(spec, "wall")
        );
    }

    private static String optionalString(JsonObject spec, String key) {
        return spec.has(key) ? spec.get(key).getAsString() : null;
    }

    private static GenerationProfile profile(DungeonTheme theme, JsonObject spec) {
        if (spec == null) {
            throw new IllegalStateException("Missing generation profile for dungeon theme: " + theme.name());
        }

        GenerationProfile profile = new GenerationProfile(
                trapWeights(spec.getAsJsonArray("traps")),
                optionalWeightMap(spec.getAsJsonObject("room_weights"), VALID_ROOMS, "room"),
                weightedEntities(spec.getAsJsonArray("spawner_mobs")),
                weightedItems(spec.getAsJsonArray("loot_flavour"))
        );
        validateProfile(theme, profile);
        return profile;
    }

    private static void validateProfile(DungeonTheme theme, GenerationProfile profile) {
        if ((theme == FROZEN || theme == OCEAN_RUIN) && profile.allowsTrap("lava")) {
            throw new IllegalStateException("Theme must not allow lava traps: " + theme.getSerializedName());
        }
        if (profile.allowsTrap("spawners") && profile.spawnerMobs().isEmpty()) {
            throw new IllegalStateException("Theme allows spawner traps but has no spawner mobs: " + theme.getSerializedName());
        }
    }

    private static Map<String, Integer> trapWeights(JsonArray specs) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalStateException("Theme profile must declare at least one trap");
        }
        LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
        for (JsonElement element : specs) {
            JsonObject spec = element.getAsJsonObject();
            String name = spec.get("name").getAsString();
            if (!VALID_TRAPS.contains(name)) {
                throw new IllegalArgumentException("Unsupported dungeon trap: " + name);
            }
            int weight = spec.has("weight") ? spec.get("weight").getAsInt() : 1;
            if (weight > 0) {
                weights.put(name, weight);
            }
        }
        return Map.copyOf(weights);
    }

    private static Map<String, Integer> optionalWeightMap(JsonObject spec, Set<String> validKeys, String label) {
        if (spec == null) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
        for (String key : spec.keySet()) {
            if (!validKeys.contains(key)) {
                throw new IllegalArgumentException("Unsupported dungeon " + label + ": " + key);
            }
            int weight = spec.get(key).getAsInt();
            if (weight >= 0) {
                weights.put(key, weight);
            }
        }
        return Map.copyOf(weights);
    }

    private static List<WeightedEntitySpec> weightedEntities(JsonArray specs) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalStateException("Theme profile must declare at least one spawner mob");
        }
        return specs.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(spec -> new WeightedEntitySpec(
                        spec.get("entity").getAsString(),
                        positiveWeight(spec, "entity")
                ))
                .toList();
    }

    private static List<WeightedItemSpec> weightedItems(JsonArray specs) {
        if (specs == null) {
            return List.of();
        }
        return specs.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(spec -> new WeightedItemSpec(
                        spec.get("item").getAsString(),
                        positiveWeight(spec, "loot flavour item")
                ))
                .toList();
    }

    private static int positiveWeight(JsonObject spec, String label) {
        int weight = spec.has("weight") ? spec.get("weight").getAsInt() : 1;
        if (weight <= 0) {
            throw new IllegalArgumentException("Dungeon " + label + " weight must be positive: " + spec);
        }
        return weight;
    }

    public record ProcessorRuleSpec(String input, float probability, String output) {
    }

    public record ShapeReplacementSpec(String fallback, String stairs, String slab, String wall) {
    }

    public record GenerationProfile(
            Map<String, Integer> trapWeights,
            Map<String, Integer> roomWeights,
            List<WeightedEntitySpec> spawnerMobs,
            List<WeightedItemSpec> lootFlavour
    ) {
        public boolean allowsTrap(String trap) {
            return trapWeight(trap) > 0;
        }

        public int trapWeight(String trap) {
            return trapWeights.getOrDefault(trap, 0);
        }

        public int roomWeight(String room, int fallback) {
            return roomWeights.getOrDefault(room, fallback);
        }
    }

    public record WeightedEntitySpec(String entity, int weight) {
    }

    public record WeightedItemSpec(String item, int weight) {
    }
}
