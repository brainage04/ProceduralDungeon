package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.mojang.datafixers.DataFixer;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.block.Block;
import net.minecraft.datafixer.Schemas;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.Main;
import net.minecraft.structure.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.path.SymlinkValidationException;
import net.minecraft.world.level.storage.LevelStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class StructureVariantGenerator extends FabricDynamicRegistryProvider {
    public StructureVariantGenerator(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    private static StructurePiece start() {
        return null;
    }

    private StructureTemplateManager createStructureTemplateManager(
            FabricDataOutput output,
            RegistryWrapper.WrapperLookup registries
    ) throws IOException, SymlinkValidationException {
        ResourceManager resourceManager = null; /* get from datagen bootstrap or create a SimpleResourceManager */
        Path root = output.getPath();
        LevelStorage levelStorage = LevelStorage.create(root);
        LevelStorage.Session session = levelStorage.createSession("datagen_world");

        DataFixer dataFixer = Schemas.getFixer();
        RegistryEntryLookup<Block> blockLookup = registries.getOrThrow(RegistryKeys.BLOCK);

        return new StructureTemplateManager(resourceManager, session, dataFixer, blockLookup);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
        for (DungeonTheme theme : DungeonTheme.values()) {
            for (DungeonTier tier : DungeonTier.values()) {
                try {
                    processVariant(theme, tier);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void processVariant(DungeonTheme theme, DungeonTier tier) throws IOException {
        Identifier baseId = ProceduralDungeon.of("dungeon/start");
        NbtCompound baseNbt = loadStructureNbtFromResources(baseId);
        //NbtCompound transformed = applyTierThemeTransforms(baseNbt.copy(), theme, tier);

        String key = RegistryKeyUtils.getKeyString(theme, tier);
        String outPath = "%s/start.nbt".formatted(key);

        //Path target = this.dataOutput.getPath().resolve(outPath);

        //Files.createDirectories(target.getParent());
        //NbtIo.writeCompressed(transformed, target);
    }

    private NbtCompound loadStructureNbtFromResources(Identifier id)
            throws IOException {
        String path = "/data/" + id.getNamespace()
                + "/structures/" + id.getPath() + ".nbt";

        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing structure resource: " + path);
            }
            return NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
        }
    }

    @Override
    public String getName() {
        return "Structure Variant Generator";
    }
}
