package com.github.brainage04.procedural_dungeon.fabric;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.command.DungeonLocksCommand;
import com.github.brainage04.procedural_dungeon.command.StructureGalleryCommand;
import com.github.brainage04.procedural_dungeon.command.core.ModCommands;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.worldgen.processor.ModStructureProcessorTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructurePoolElementTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructureTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

public final class ProceduralDungeonFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ModStructureProcessorTypes.registerAll((name, codec) -> Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, ProceduralDungeon.of(name), codec));
        ModStructurePoolElementTypes.registerAll((name, type) -> ModStructurePoolElementTypes.setVariantSinglePoolElement(
                Registry.register(BuiltInRegistries.STRUCTURE_POOL_ELEMENT, ProceduralDungeon.of(name), type)));
        ModStructureTypes.registerAll(
                (name, type) -> ModStructureTypes.setStagedDungeon(Registry.register(BuiltInRegistries.STRUCTURE_TYPE, ProceduralDungeon.of(name), type)),
                (name, type) -> ModStructureTypes.setStagedDungeonMarker(Registry.register(BuiltInRegistries.STRUCTURE_PIECE, ProceduralDungeon.of(name), type)));
        ProceduralDungeon.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ModCommands.register(dispatcher));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> world instanceof ServerLevel level
                ? DungeonLockManager.useBlock(player, level, hitResult.getBlockPos())
                : net.minecraft.world.InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !(world instanceof ServerLevel level)
                || DungeonLockManager.canBreak(player, level, pos));
        ServerTickEvents.START_SERVER_TICK.register(server -> StagedDungeonGenerationManager.beginServerTick());
        ServerTickEvents.END_SERVER_TICK.register(StagedDungeonGenerationManager::runServerTick);
        ServerTickEvents.END_SERVER_TICK.register(server -> DungeonLocksCommand.tickRevealMarkers());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DungeonLocksCommand.clearRevealMarkers());
        ServerLifecycleEvents.SERVER_STARTED.register(StructureGalleryCommand::initializeAutobuild);
    }
}
