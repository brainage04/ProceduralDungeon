package com.github.brainage04.procedural_dungeon.neoforge;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.command.DungeonLocksCommand;
import com.github.brainage04.procedural_dungeon.command.StructureGalleryCommand;
import com.github.brainage04.procedural_dungeon.command.core.ModCommands;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.item.ModItems;
import com.github.brainage04.procedural_dungeon.worldgen.processor.ModStructureProcessorTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructurePoolElementTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.ModStructureTypes;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonStructure;
import com.github.brainage04.procedural_dungeon.worldgen.structure.VariantSinglePoolElement;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import com.mojang.serialization.MapCodec;

@Mod(ProceduralDungeon.MOD_ID)
@EventBusSubscriber(modid = ProceduralDungeon.MOD_ID)
public final class ProceduralDungeonNeoForge {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ProceduralDungeon.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends StructureProcessor>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, ProceduralDungeon.MOD_ID);
    private static final DeferredRegister<StructurePoolElementType<?>> POOL_ELEMENTS = DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, ProceduralDungeon.MOD_ID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ProceduralDungeon.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, ProceduralDungeon.MOD_ID);
    private static final DeferredHolder<Item, Item> RUSTED_KEY = ITEMS.register("rusted_key", ModItems::createRustedKey);
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
            PIECES.register("staged_dungeon_marker", () -> com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonMarkerPiece::new);

    public ProceduralDungeonNeoForge(IEventBus modBus) {
        ITEMS.register(modBus);
        PROCESSORS.register(modBus);
        POOL_ELEMENTS.register(modBus);
        STRUCTURES.register(modBus);
        PIECES.register(modBus);
        ModStructureProcessorTypes.registerAll((name, codec) -> PROCESSORS.register(name, () -> codec));
        ModItems.registerRustedKey(RUSTED_KEY::get);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            ModStructurePoolElementTypes.setVariantSinglePoolElement(VARIANT_SINGLE_POOL_ELEMENT.get());
            ModStructureTypes.setStagedDungeon(STAGED_DUNGEON.get());
            ModStructureTypes.setStagedDungeonMarker(STAGED_DUNGEON_MARKER.get());
            ProceduralDungeon.initialize();
        }));
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var result = DungeonLockManager.useBlock(event.getEntity(), level, event.getPos());
            if (result.consumesAction()) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
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
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        StructureGalleryCommand.initializeAutobuild(event.getServer());
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        DungeonLocksCommand.clearRevealMarkers();
    }
}
