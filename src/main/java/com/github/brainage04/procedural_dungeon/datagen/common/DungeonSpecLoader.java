package com.github.brainage04.procedural_dungeon.datagen.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

final class DungeonSpecLoader {
    private static final Gson GSON = new Gson();
    private static final String RESOURCE_ROOT = "/procedural_dungeon/";
    private static final Path SOURCE_ROOT = Path.of("src/main/resources/procedural_dungeon");

    private DungeonSpecLoader() {
    }

    static JsonObject load(String fileName) {
        Path specPath = resolveSourcePath(fileName);
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

    private static Path resolveSourcePath(String fileName) {
        Path currentDirectory = Path.of("").toAbsolutePath();
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
