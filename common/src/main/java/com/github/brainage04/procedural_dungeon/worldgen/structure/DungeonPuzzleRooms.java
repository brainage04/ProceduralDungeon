package com.github.brainage04.procedural_dungeon.worldgen.structure;

import com.github.brainage04.procedural_dungeon.ProceduralDungeon;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Puzzle rooms: optional dead-end rooms whose reward chest holds vanilla rarities found nowhere else in a dungeon.
 * Each puzzle is solved with vanilla mechanics and checked by redstone built into the room. The mechanism ends in a
 * repeater that powers the block under the reward chest; the chest stays locked until that repeater is powered, so
 * breaking walls or wiring a torch next to the chest does not open it.
 *
 * <p>Pieces are placed without block updates, so {@link #finishPlacement} settles the room's redstone afterwards.
 */
public final class DungeonPuzzleRooms {
    public static final String LOOT_TABLE = "puzzle_room";
    /**
     * Data marker for an item frame holding an arrow at the given rotation, hanging on the block to the marker's south
     * (in template coordinates), e.g. {@code procedural_dungeon:puzzle_frame/3}.
     */
    public static final String FRAME_MARKER = ProceduralDungeon.MOD_ID + ":puzzle_frame/";

    private DungeonPuzzleRooms() {}

    public enum Puzzle {
        FRAMES("frames"),
        BOOKSHELVES("bookshelves"),
        TARGETS("targets"),
        CHORD("chord"),
        SLUICE("sluice"),
        CRAFTER("crafter"),
        STEALTH("stealth"),
        PARKOUR("parkour");

        private final String id;

        Puzzle(String id) {
            this.id = id;
        }

        public Identifier template() {
            return ProceduralDungeon.of("dungeon/hallway/room/puzzle/" + id);
        }

        /**
         * The room name used for theme room weights.
         */
        public String roomName() {
            return "puzzle_" + id;
        }
    }

    public static boolean isPuzzle(Identifier template) {
        for (Puzzle puzzle : Puzzle.values()) {
            if (puzzle.template().equals(template)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hangs the arrow frame of a {@link #FRAME_MARKER} marker at {@code pos} in a piece placed with {@code rotation}.
     */
    public static void hangFrame(ServerLevel level, BlockPos pos, Rotation rotation, String metadata) {
        int itemRotation = Integer.parseInt(metadata.substring(FRAME_MARKER.length()));
        level.setBlock(pos, Blocks.CAVE_AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        ItemFrame frame = new ItemFrame(level, pos, rotation.rotate(Direction.NORTH));
        frame.setItem(new ItemStack(Items.ARROW), false);
        frame.setRotation(itemRotation);
        level.addFreshEntity(frame);
    }

    /**
     * Settles the redstone of a freshly placed puzzle room inside {@code box} and locks its reward chest.
     */
    public static void finishPlacement(ServerLevel level, BoundingBox box) {
        List<BlockPos> components = new ArrayList<>();
        List<BlockPos> chests = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().isSource()) {
                // Water and lava placed without updates stay still until something ticks them.
                level.scheduleTick(pos.immutable(), state.getFluidState().getType(), state.getFluidState().getType().getTickDelay(level));
            }
            if (isRedstone(state)) {
                components.add(pos.immutable());
            } else if (state.getBlock() instanceof ChestBlock) {
                chests.add(pos.immutable());
            }
        }

        // Dust is saved without connections; shape it from its neighbours first, then let every component read its
        // inputs. Torches and repeaters that change schedule ticks, so the circuit settles over the next few ticks.
        for (BlockPos pos : components) {
            BlockState state = level.getBlockState(pos);
            BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
            if (shaped != state) {
                level.setBlock(pos, shaped, Block.UPDATE_CLIENTS);
            }
        }
        for (BlockPos pos : components) {
            level.neighborChanged(pos, level.getBlockState(pos).getBlock(), null);
        }

        for (BlockPos chest : chests) {
            if (isPuzzleChest(level, chest) && mechanismRepeater(level, chest).isPresent()) {
                DungeonLockManager.lockPuzzleChest(level, chest);
            }
        }
    }

    /**
     * Whether the mechanism under the puzzle chest at {@code chest} is powered.
     */
    public static boolean isSolved(ServerLevel level, BlockPos chest) {
        return mechanismRepeater(level, chest)
                .map(pos -> level.getBlockState(pos).getValue(RepeaterBlock.POWERED))
                .orElse(false);
    }

    /**
     * The repeater that outputs into the block under {@code chest}.
     */
    private static Optional<BlockPos> mechanismRepeater(ServerLevel level, BlockPos chest) {
        BlockPos signal = chest.below();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = signal.relative(direction);
            BlockState state = level.getBlockState(pos);
            // A repeater's facing points at its input, so it outputs into the block on the opposite side.
            if (state.is(Blocks.REPEATER) && state.getValue(RepeaterBlock.FACING) == direction) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    private static boolean isPuzzleChest(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainer container)) {
            return false;
        }
        ResourceKey<LootTable> lootTable = container.getLootTable();
        return lootTable != null
                && lootTable.identifier().getNamespace().equals(ProceduralDungeon.MOD_ID)
                && lootTable.identifier().getPath().startsWith(LOOT_TABLE);
    }

    private static boolean isRedstone(BlockState state) {
        Block block = state.getBlock();
        return block instanceof RedStoneWireBlock
                || block instanceof DiodeBlock
                || block instanceof RedstoneTorchBlock
                || block instanceof LeverBlock
                || block instanceof ButtonBlock
                || block instanceof BasePressurePlateBlock
                || block instanceof TargetBlock
                || block instanceof CopperBulbBlock
                || block instanceof PistonBaseBlock
                || block instanceof NoteBlock
                || block instanceof CrafterBlock;
    }
}
