package com.github.brainage04.procedural_dungeon.dungeon;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.util.StringUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
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

    public record ProcessorRuleSpec(String input, float probability, String output) {
    }

    public record ShapeReplacementSpec(String fallback, String stairs, String slab, String wall) {
    }
}
