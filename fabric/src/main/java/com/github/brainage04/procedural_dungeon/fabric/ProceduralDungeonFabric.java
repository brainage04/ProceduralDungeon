package com.github.brainage04.procedural_dungeon.fabric;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.command.DungeonLocksCommand;
import com.github.brainage04.procedural_dungeon.command.StructureGalleryCommand;
import com.github.brainage04.procedural_dungeon.command.core.ModCommands;
import com.github.brainage04.procedural_dungeon.lock.DungeonBossKeys;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.rule.DungeonGameRules;
import com.github.brainage04.procedural_dungeon.rule.DungeonSwitches;
import com.github.brainage04.procedural_dungeon.worldgen.processor.ModStructureProcessorTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonChunkLedger;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonChunkTickets;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructurePoolElementTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructureTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ProceduralDungeonFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ModStructureProcessorTypes.registerAll((name, codec) -> Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, ProceduralDungeon.of(name), codec));
        ModStructurePoolElementTypes.registerAll((name, type) -> ModStructurePoolElementTypes.setVariantSinglePoolElement(
                Registry.register(BuiltInRegistries.STRUCTURE_POOL_ELEMENT, ProceduralDungeon.of(name), type)));
        ModStructureTypes.registerAll(
                (name, type) -> ModStructureTypes.setStagedDungeon(Registry.register(BuiltInRegistries.STRUCTURE_TYPE, ProceduralDungeon.of(name), type)),
                (name, type) -> ModStructureTypes.setStagedDungeonMarker(Registry.register(BuiltInRegistries.STRUCTURE_PIECE, ProceduralDungeon.of(name), type)));
        Registry.register(BuiltInRegistries.TICKET_TYPE, ProceduralDungeon.of(DungeonChunkTickets.TICKET_NAME), DungeonChunkTickets.TICKET_TYPE);
        AttachmentType<DungeonChunkLedger.Data> placed = AttachmentRegistry.create(
                ProceduralDungeon.of(DungeonChunkLedger.ATTACHMENT_NAME), builder -> builder.persistent(DungeonChunkLedger.CODEC));
        DungeonChunkLedger.setBackend(new DungeonChunkLedger.Backend() {
            @Override
            public DungeonChunkLedger.Data get(LevelChunk chunk) {
                return ((AttachmentTarget) chunk).getAttached(placed);
            }

            @Override
            public void set(LevelChunk chunk, DungeonChunkLedger.Data data) {
                ((AttachmentTarget) chunk).setAttached(placed, data);
            }
        });
        DungeonGameRules.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        ProceduralDungeon.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ModCommands.register(dispatcher));
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> DungeonLockManager.onChunkLoad(level, chunk));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> world instanceof ServerLevel level
                ? DungeonLockManager.useBlock(player, level, hitResult.getBlockPos())
                : net.minecraft.world.InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !(world instanceof ServerLevel level)
                || DungeonLockManager.canBreak(player, level, pos));
        ServerTickEvents.START_SERVER_TICK.register(server -> StagedDungeonGenerationManager.beginServerTick());
        ServerTickEvents.END_SERVER_TICK.register(StagedDungeonGenerationManager::runServerTick);
        ServerTickEvents.END_SERVER_TICK.register(server -> DungeonLocksCommand.tickRevealMarkers());
        ServerTickEvents.END_SERVER_TICK.register(DungeonBossKeys::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DungeonLocksCommand.clearRevealMarkers());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DungeonBossKeys.clear());
        ServerLifecycleEvents.SERVER_STOPPING.register(StagedDungeonGenerationManager::serverStopping);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> StagedDungeonGenerationManager.resetSession());
        ServerLifecycleEvents.SERVER_STARTED.register(StructureGalleryCommand::initializeAutobuild);
        ServerLifecycleEvents.SERVER_STARTING.register(DungeonSwitches::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(DungeonSwitches::serverStopped);
    }
}
