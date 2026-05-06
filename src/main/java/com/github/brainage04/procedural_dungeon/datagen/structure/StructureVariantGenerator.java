package com.github.brainage04.procedural_dungeon.datagen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTheme;
import com.github.brainage04.procedural_dungeon.datagen.common.DungeonTier;
import com.github.brainage04.procedural_dungeon.util.RegistryKeyUtils;
import com.mojang.datafixers.DataFixer;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Main;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.ContentValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class StructureVariantGenerator extends FabricDynamicRegistryProvider {
    public StructureVariantGenerator(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    private static StructurePiece start() {
        return null;
    }

    private StructureTemplateManager createStructureTemplateManager(
            FabricPackOutput output,
            HolderLookup.Provider registries
    ) throws IOException, ContentValidationException {
        ResourceManager resourceManager = null; /* get from datagen bootstrap or create a SimpleResourceManager */
        Path root = output.getOutputFolder();
        LevelStorageSource levelStorage = LevelStorageSource.createDefault(root);
        LevelStorageSource.LevelStorageAccess session = levelStorage.validateAndCreateAccess("datagen_world");

        DataFixer dataFixer = DataFixers.getDataFixer();
        HolderGetter<Block> blockLookup = registries.lookupOrThrow(Registries.BLOCK);

        return new StructureTemplateManager(resourceManager, session, dataFixer, blockLookup);
    }

    @Override
    protected void configure(HolderLookup.Provider registries, Entries entries) {
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
        CompoundTag baseNbt = loadStructureNbtFromResources(baseId);
        //NbtCompound transformed = applyTierThemeTransforms(baseNbt.copy(), theme, tier);

        String key = RegistryKeyUtils.getKeyString(theme, tier);
        String outPath = "%s/start.nbt".formatted(key);

        //Path target = this.dataOutput.getPath().resolve(outPath);

        //Files.createDirectories(target.getParent());
        //NbtIo.writeCompressed(transformed, target);
    }

    private CompoundTag loadStructureNbtFromResources(Identifier id)
            throws IOException {
        String path = "/data/" + id.getNamespace()
                + "/structures/" + id.getPath() + ".nbt";

        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing structure resource: " + path);
            }
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
    }

    @Override
    public String getName() {
        return "Structure Variant Generator";
    }
}
