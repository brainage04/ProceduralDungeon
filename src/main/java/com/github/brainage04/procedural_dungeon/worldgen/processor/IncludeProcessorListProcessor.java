package com.github.brainage04.procedural_dungeon.worldgen.processor;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class IncludeProcessorListProcessor extends StructureProcessor {
    public static final MapCodec<IncludeProcessorListProcessor> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceKey.codec(Registries.PROCESSOR_LIST)
                            .listOf()
                            .fieldOf("processors")
                            .forGetter(processor -> processor.processorLists)
            ).apply(instance, IncludeProcessorListProcessor::new));

    private final List<ResourceKey<StructureProcessorList>> processorLists;
    private volatile List<StructureProcessor> cachedProcessors;

    public IncludeProcessorListProcessor(List<ResourceKey<StructureProcessorList>> processorLists) {
        this.processorLists = List.copyOf(processorLists);
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader world,
            BlockPos pos,
            BlockPos pivot,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo currentBlockInfo,
            StructurePlaceSettings data
    ) {
        StructureTemplate.StructureBlockInfo result = currentBlockInfo;
        for (StructureProcessor processor : resolveProcessors(world)) {
            if (result == null) {
                return null;
            }
            result = processor.processBlock(world, pos, pivot, originalBlockInfo, result, data);
        }
        return result;
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor world,
            BlockPos pos,
            BlockPos pivot,
            List<StructureTemplate.StructureBlockInfo> originalBlockInfos,
            List<StructureTemplate.StructureBlockInfo> processedBlockInfos,
            StructurePlaceSettings data
    ) {
        List<StructureTemplate.StructureBlockInfo> result = processedBlockInfos;
        for (StructureProcessor processor : resolveProcessors(world)) {
            result = processor.finalizeProcessing(world, pos, pivot, originalBlockInfos, result, data);
        }
        return result;
    }

    private List<StructureProcessor> resolveProcessors(LevelReader world) {
        List<StructureProcessor> processors = cachedProcessors;
        if (processors == null) {
            var registry = world.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
            List<StructureProcessor> resolved = new ArrayList<>();
            Deque<ResourceKey<StructureProcessorList>> stack = new ArrayDeque<>();
            for (ResourceKey<StructureProcessorList> key : processorLists) {
                flatten(registry, key, stack, resolved);
            }
            processors = List.copyOf(resolved);
            cachedProcessors = processors;
        }
        return processors;
    }

    private static void flatten(
            net.minecraft.core.Registry<StructureProcessorList> registry,
            ResourceKey<StructureProcessorList> processorList,
            Deque<ResourceKey<StructureProcessorList>> stack,
            List<StructureProcessor> output
    ) {
        if (stack.contains(processorList)) {
            throw new IllegalStateException("Recursive processor list include: " + stack + " -> " + processorList);
        }

        stack.push(processorList);
        try {
            for (StructureProcessor processor : registry.getValueOrThrow(processorList).list()) {
                if (processor instanceof IncludeProcessorListProcessor include) {
                    for (ResourceKey<StructureProcessorList> nestedKey : include.processorLists) {
                        flatten(registry, nestedKey, stack, output);
                    }
                } else {
                    output.add(processor);
                }
            }
        } finally {
            stack.pop();
        }
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructureProcessorTypes.INCLUDE_PROCESSOR_LIST;
    }
}
