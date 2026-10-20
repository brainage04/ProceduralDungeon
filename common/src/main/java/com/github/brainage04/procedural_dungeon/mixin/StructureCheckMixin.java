package com.github.brainage04.procedural_dungeon.mixin;

import com.github.brainage04.procedural_dungeon.rule.DungeonSwitches;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonStructure;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps {@code /locate} and explorer maps from pointing at natural dungeons that {@link
 * DungeonSwitches#naturalGeneration} would not start in ungenerated chunks.
 */
@Mixin(StructureCheck.class)
public class StructureCheckMixin {
    @Shadow @Final private ResourceKey<Level> dimension;

    @Inject(method = "canCreateStructure", at = @At("HEAD"), cancellable = true)
    private void proceduralDungeon$naturalGeneration(
            ChunkPos pos, Structure structure, CallbackInfoReturnable<Boolean> cir) {
        if (structure instanceof StagedDungeonStructure
                && !DungeonSwitches.naturalGeneration(this.dimension)) {
            cir.setReturnValue(false);
        }
    }
}
