package com.github.brainage04.procedural_dungeon.neoforge;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.command.DungeonLocksCommand;
import com.github.brainage04.procedural_dungeon.command.StructureGalleryCommand;
import com.github.brainage04.procedural_dungeon.command.core.ModCommands;
import com.github.brainage04.procedural_dungeon.lock.DungeonBossKeys;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.rule.DungeonGameRules;
import com.github.brainage04.procedural_dungeon.rule.DungeonSwitches;
import com.github.brainage04.procedural_dungeon.worldgen.processor.ModStructureProcessorTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructurePoolElementTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructureTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonStructure;
import com.github.brainage04.procedural_dungeon.worldgen.structure.VariantSinglePoolElement;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonChunkLedger;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonChunkTickets;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import com.mojang.serialization.MapCodec;

@Mod(ProceduralDungeon.MOD_ID)
@EventBusSubscriber(modid = ProceduralDungeon.MOD_ID)
public final class ProceduralDungeonNeoForge {
    private static final DeferredRegister<MapCodec<? extends StructureProcessor>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, ProceduralDungeon.MOD_ID);
    private static final DeferredRegister<StructurePoolElementType<?>> POOL_ELEMENTS = DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, ProceduralDungeon.MOD_ID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ProceduralDungeon.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, ProceduralDungeon.MOD_ID);
    private static final DeferredRegister<GameRule<?>> GAME_RULES = DeferredRegister.create(Registries.GAME_RULE, ProceduralDungeon.MOD_ID);
    private static final DeferredHolder<StructurePoolElementType<?>, StructurePoolElementType<VariantSinglePoolElement>> VARIANT_SINGLE_POOL_ELEMENT =
            POOL_ELEMENTS.<StructurePoolElementType<VariantSinglePoolElement>>register(
                    "variant_single_pool_element",
                    () -> () -> VariantSinglePoolElement.CODEC
            );
    private static final DeferredHolder<StructureType<?>, StructureType<StagedDungeonStructure>> STAGED_DUNGEON =
            STRUCTURES.<StructureType<StagedDungeonStructure>>register(
                    "staged_dungeon",
                    () -> () -> StagedDungeonStructure.CODEC
            );
    private static final DeferredHolder<StructurePieceType, StructurePieceType> STAGED_DUNGEON_MARKER =
            PIECES.register(ModStructureTypes.STAGED_DUNGEON_MARKER_NAME, () -> com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonMarkerPiece::new);
    private static final DeferredHolder<StructurePieceType, StructurePieceType> STAGED_DUNGEON_BOUNDS =
            PIECES.register(ModStructureTypes.STAGED_DUNGEON_BOUNDS_NAME, () -> com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonBoundsPiece::new);
    private static final DeferredRegister<TicketType> TICKET_TYPES = DeferredRegister.create(Registries.TICKET_TYPE, ProceduralDungeon.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ProceduralDungeon.MOD_ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<DungeonChunkLedger.Data>> PLACED =
            ATTACHMENT_TYPES.register(DungeonChunkLedger.ATTACHMENT_NAME, () -> AttachmentType.builder(() -> DungeonChunkLedger.Data.EMPTY)
                    .serialize(DungeonChunkLedger.MAP_CODEC, data -> !data.isEmpty())
                    .build());

    public ProceduralDungeonNeoForge(IEventBus modBus) {
        PROCESSORS.register(modBus);
        POOL_ELEMENTS.register(modBus);
        STRUCTURES.register(modBus);
        PIECES.register(modBus);
        TICKET_TYPES.register(DungeonChunkTickets.TICKET_NAME, () -> DungeonChunkTickets.TICKET_TYPE);
        TICKET_TYPES.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
        DungeonChunkLedger.setBackend(new DungeonChunkLedger.Backend() {
            @Override
            public DungeonChunkLedger.Data get(LevelChunk chunk) {
                return chunk.getExistingDataOrNull(PLACED.get());
            }

            @Override
            public void set(LevelChunk chunk, DungeonChunkLedger.Data data) {
                if (data == null) {
                    chunk.removeData(PLACED.get());
                } else {
                    chunk.setData(PLACED.get(), data);
                }
            }
        });
        GAME_RULES.register(modBus);
        DungeonGameRules.register((id, rule) -> GAME_RULES.register(id.getPath(), () -> rule));
        ModStructureProcessorTypes.registerAll((name, codec) -> PROCESSORS.register(name, () -> codec));
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            ModStructurePoolElementTypes.setVariantSinglePoolElement(VARIANT_SINGLE_POOL_ELEMENT.get());
            ModStructureTypes.setStagedDungeon(STAGED_DUNGEON.get());
            ModStructureTypes.setStagedDungeonMarker(STAGED_DUNGEON_MARKER.get());
            ModStructureTypes.setStagedDungeonBounds(STAGED_DUNGEON_BOUNDS.get());
            ProceduralDungeon.initialize();
        }));
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void serverAboutToStart(ServerAboutToStartEvent event) {
        DungeonSwitches.serverStarting(event.getServer());
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        DungeonSwitches.serverStopped(event.getServer());
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var result = DungeonLockManager.useBlock(event.getEntity(), level, event.getPos());
            // A refusal is FAIL, which does not consume the action; any non-PASS result must stop vanilla's interaction.
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void chunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            DungeonLockManager.onChunkLoad(level, event.getChunk());
        }
    }

    @SubscribeEvent
    public static void breakBlock(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && !DungeonLockManager.canBreak(event.getPlayer(), level, event.getPos())) {
            event.setNotifyClient(true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Pre event) {
        StagedDungeonGenerationManager.beginServerTick();
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        StagedDungeonGenerationManager.runServerTick(event.getServer());
        DungeonLocksCommand.tickRevealMarkers();
        DungeonBossKeys.tick(event.getServer());
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        StagedDungeonGenerationManager.resetSession();
        StructureGalleryCommand.initializeAutobuild(event.getServer());
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        DungeonLocksCommand.clearRevealMarkers();
        DungeonBossKeys.clear();
        StagedDungeonGenerationManager.serverStopping(event.getServer());
    }
}
