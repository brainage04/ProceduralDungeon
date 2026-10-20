package com.github.brainage04.procedural_dungeon.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A template palette caches its blocks per block type in a plain {@code HashMap}. Dungeon layouts
 * compile on worldgen workers while the server thread places the same templates, and both look up
 * jigsaw and structure blocks, so the cache is made concurrent: two threads filling it at once
 * threw {@code ConcurrentModificationException} and crashed the server.
 */
@Mixin(StructureTemplate.Palette.class)
public class StructureTemplatePaletteMixin {
    @Mutable @Shadow @Final private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void proceduralDungeon$concurrentCache(
            List<StructureTemplate.StructureBlockInfo> blocks, CallbackInfo ci) {
        this.cache = new ConcurrentHashMap<>();
    }
}
