package com.github.brainage04.procedural_dungeon.dungeon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the theme and tier specs from the mod jar. A development checkout can set the {@value #SOURCE_SPECS_PROPERTY}
 * system property to read them from the nearest {@code common/src/main/resources/procedural_dungeon/} folder above the
 * working directory instead, so spec edits apply without rebuilding the jar.
 */
public final class DungeonSpecLoader {
    public static final String SOURCE_SPECS_PROPERTY = "procedural_dungeon.source_specs";

    private static final Gson GSON = new Gson();
    private static final String RESOURCE_ROOT = "/procedural_dungeon/";
    private static final Path SOURCE_ROOT = Path.of("common/src/main/resources/procedural_dungeon");

    private DungeonSpecLoader() {
    }

    static JsonObject load(String fileName) {
        return load(fileName, Path.of("").toAbsolutePath());
    }

    /**
     * Loads {@code fileName} as a server started in {@code workingDirectory} would.
     */
    public static JsonObject load(String fileName, Path workingDirectory) {
        Path specPath = Boolean.getBoolean(SOURCE_SPECS_PROPERTY) ? resolveSourcePath(fileName, workingDirectory) : null;
        if (specPath != null) {
            try (Reader reader = Files.newBufferedReader(specPath)) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read dungeon spec: " + specPath, e);
            }
        }

        String resourcePath = RESOURCE_ROOT + fileName;
        try (Reader reader = new InputStreamReader(
                DungeonSpecLoader.class.getResourceAsStream(resourcePath),
                StandardCharsets.UTF_8
        )) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (NullPointerException e) {
            throw new IllegalStateException("Failed to find dungeon spec resource: " + resourcePath, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read dungeon spec resource: " + resourcePath, e);
        }
    }

    private static Path resolveSourcePath(String fileName, Path workingDirectory) {
        Path currentDirectory = workingDirectory.toAbsolutePath();
        while (currentDirectory != null) {
            Path specPath = currentDirectory.resolve(SOURCE_ROOT).resolve(fileName);
            if (Files.exists(specPath)) {
                return specPath;
            }

            currentDirectory = currentDirectory.getParent();
        }

        return null;
    }
}
