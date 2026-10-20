package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.rule.DungeonSwitches;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonStructure;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Starts no natural dungeon where {@link DungeonSwitches#naturalGeneration} turns them off. */
@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void proceduralDungeon$naturalGeneration(
            StructureSet.StructureSelectionEntry entry,
            StructureManager structureManager,
            RegistryAccess registryAccess,
            RandomState randomState,
            StructureTemplateManager templateManager,
            long seed,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            SectionPos sectionPos,
            ResourceKey<Level> dimension,
            CallbackInfoReturnable<Boolean> cir) {
        if (entry.structure().value() instanceof StagedDungeonStructure
                && !DungeonSwitches.naturalGeneration(dimension)) {
            cir.setReturnValue(false);
        }
    }
}
