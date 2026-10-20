package com.github.brainage04.procedural_dungeon;

import com.github.brainage04.procedural_dungeon.api.BuildOptions;
import com.github.brainage04.procedural_dungeon.api.DungeonHandle;
import com.github.brainage04.procedural_dungeon.api.DungeonListener;
import com.github.brainage04.procedural_dungeon.api.DungeonPlan;
import com.github.brainage04.procedural_dungeon.api.DungeonRequest;
import com.github.brainage04.procedural_dungeon.api.DungeonState;
import com.github.brainage04.procedural_dungeon.api.ProceduralDungeonApi;
import com.github.brainage04.procedural_dungeon.api.impl.CompiledPlan;
import com.github.brainage04.procedural_dungeon.dungeon.DungeonTheme;
import com.github.brainage04.procedural_dungeon.guardian.DungeonGuardian;
import com.github.brainage04.procedural_dungeon.lock.DungeonKeyType;
import com.github.brainage04.procedural_dungeon.lock.DungeonLockManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.DungeonChunkTickets;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonGenerationManager;
import com.github.brainage04.procedural_dungeon.worldgen.structure.StagedDungeonPieceSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The public API: off-thread seeded plans, caller-driven builds with owned tickets, handles,
 * cancel, reset, forget, guardian scaling and the listener events.
 */
public final class ApiGameTests {
    private static final long SEED = 0xD06E_0061L;
    private static final DungeonRequest TIER_1 =
            new DungeonRequest("cobblestone", 1, 1, SEED, false);
    private static final long ADVANCE_BUDGET_NANOS = 20_000_000L;
    private static final long PLAN_CALL_LIMIT_NANOS = 5_000_000L;
    private static final Recorder RECORDER = new Recorder();

    private ApiGameTests() {}

    public static List<DungeonGameTestSuite.TestCase> tests() {
        return List.of(
                new DungeonGameTestSuite.TestCase(
                        "api_plans_compile_off_thread_from_the_request_alone",
                        ApiGameTests::apiPlansCompileOffThreadFromTheRequestAlone),
                new DungeonGameTestSuite.TestCase(
                        "api_build_follows_the_callers_budget_and_cancel_releases_its_tickets",
                        ApiGameTests::apiBuildFollowsTheCallersBudgetAndCancelReleasesItsTickets),
                new DungeonGameTestSuite.TestCase(
                        "api_reset_and_forget_rebuild_and_drop_a_dungeon",
                        ApiGameTests::apiResetAndForgetRebuildAndDropADungeon));
    }

    /**
     * {@code plan} returns before compiling and costs the calling tick under 5 ms; the same request
     * gives the same layout, another seed another one.
     */
    public static void apiPlansCompileOffThreadFromTheRequestAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long start = System.nanoTime();
        CompletableFuture<Optional<DungeonPlan>> first =
                ProceduralDungeonApi.plan(level.getServer(), TIER_1);
        long elapsed = System.nanoTime() - start;
        helper.assertTrue(
                !first.isDone(), "plan must return before the layout is compiled and published");
        helper.assertTrue(
                elapsed < PLAN_CALL_LIMIT_NANOS,
                "plan must cost the calling tick under 5 ms, took %.2f ms"
                        .formatted(elapsed / 1_000_000.0));
        helper.assertTrue(
                ProceduralDungeonApi.themes().contains(TIER_1.theme())
                        && ProceduralDungeonApi.themes().size() == DungeonTheme.values().length,
                "themes() must list every theme plan accepts");
        CompletableFuture<Optional<DungeonPlan>> again =
                ProceduralDungeonApi.plan(level.getServer(), TIER_1);
        CompletableFuture<Optional<DungeonPlan>> other =
                ProceduralDungeonApi.plan(
                        level.getServer(),
                        new DungeonRequest("cobblestone", 1, 1, SEED + 1, false));
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(
                            first.isDone() && again.isDone() && other.isDone(),
                            "Plans must complete");
                    DungeonPlan plan = first.join().orElseThrow();
                    helper.assertTrue(
                            plan.totalPieces() > 1 && plan.request().equals(TIER_1),
                            "A tier 1 plan must have pieces and keep its request");
                    helper.assertTrue(
                            describe(plan).equals(describe(again.join().orElseThrow())),
                            "The same request must give the same layout");
                    helper.assertTrue(
                            !describe(plan).equals(describe(other.join().orElseThrow())),
                            "Another seed must give another layout");
                });
    }

    /**
     * A caller-driven build waits for {@code advance}, completes with every piece placed and locked
     * at its origin and reports progress and completion; a second build holds its own tickets until
     * {@code cancel} releases every one of them.
     */
    public static void apiBuildFollowsTheCallersBudgetAndCancelReleasesItsTickets(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RECORDER.listen();
        CompletableFuture<Optional<DungeonPlan>> planned =
                ProceduralDungeonApi.plan(level.getServer(), TIER_1);
        BlockPos origin = helper.absolutePos(new BlockPos(12288, 32, 12288));
        BlockPos cancelledOrigin = helper.absolutePos(new BlockPos(-12288, 32, 12288));
        DungeonHandle[] built = new DungeonHandle[2];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(planned.isDone(), "The plan must complete"))
                .thenExecute(
                        () -> {
                            DungeonPlan plan = planned.join().orElseThrow();
                            preload(
                                    level,
                                    plan.bounds()
                                            .moved(origin.getX(), origin.getY(), origin.getZ()));
                            built[0] =
                                    ProceduralDungeonApi.build(
                                            level,
                                            plan,
                                            origin,
                                            new BuildOptions(true, 1.0, false));
                            helper.assertTrue(
                                    built[0].bounds()
                                            .equals(
                                                    plan.bounds()
                                                            .moved(
                                                                    origin.getX(),
                                                                    origin.getY(),
                                                                    origin.getZ())),
                                    "A build's bounds must be the plan's moved to its origin");
                        })
                .thenIdle(3)
                .thenExecute(
                        () ->
                                helper.assertTrue(
                                        built[0].placedPieces() == 0
                                                && built[0].state() == DungeonState.RUNNING,
                                        "Procedural Dungeon's tick must not place a caller-driven"
                                                + " build"))
                .thenWaitUntil(
                        () -> {
                            built[0].advance(ADVANCE_BUDGET_NANOS);
                            helper.assertTrue(built[0].complete(), "advance must finish the build");
                        })
                .thenExecute(
                        () -> {
                            DungeonHandle handle = built[0];
                            helper.assertTrue(
                                    handle.placedPieces() == handle.totalPieces(),
                                    "A complete build must have placed every piece");
                            helper.assertTrue(
                                    RECORDER.completed.contains(handle.id()),
                                    "Listeners must hear that the build completed");
                            helper.assertTrue(
                                    RECORDER.progress.getOrDefault(handle.id(), 0)
                                            == handle.totalPieces(),
                                    "Listeners must hear every placed piece");
                            helper.assertTrue(
                                    StagedDungeonGenerationManager.heldChunks(level, handle.id())
                                            == 0,
                                    "A complete build must hold no chunk tickets");
                            BlockPos door = handle.bossDoor().orElseThrow();
                            helper.assertTrue(
                                    level.getBlockState(door).is(Blocks.IRON_DOOR)
                                            && DungeonLockManager.isDoorLocked(level, door),
                                    "The boss door must be placed and locked");
                            BlockPos spawn = handle.startSpawn();
                            helper.assertTrue(
                                    level.getBlockState(spawn).isAir()
                                            && level.getBlockState(spawn.above()).isAir()
                                            && !level.getBlockState(spawn.below()).isAir(),
                                    "startSpawn must be standing room, got "
                                            + spawn.toShortString());
                            helper.assertTrue(
                                    ProceduralDungeonApi.handle(level, handle.id())
                                            .map(DungeonHandle::complete)
                                            .orElse(false),
                                    "handle must find the build by id");

                            ServerPlayer player = helper.makeMockServerPlayerInLevel();
                            try {
                                player.getInventory().add(DungeonKeyType.BOSS.createStack());
                                DungeonLockManager.useBlock(player, level, door);
                                helper.assertTrue(
                                        RECORDER.unlocked.contains(handle.id()),
                                        "Listeners must hear the boss door unlock");
                            } finally {
                                level.getServer().getPlayerList().remove(player);
                            }

                            built[1] =
                                    ProceduralDungeonApi.build(
                                            level,
                                            planned.join().orElseThrow(),
                                            cancelledOrigin,
                                            new BuildOptions(true, 1.0, false));
                            built[1].advance(ADVANCE_BUDGET_NANOS);
                            helper.assertTrue(
                                    StagedDungeonGenerationManager.heldChunks(level, built[1].id())
                                            > 0,
                                    "A build waiting for chunks must hold tickets");
                            built[1].cancel();
                            helper.assertTrue(
                                    built[1].state() == DungeonState.CANCELLED
                                            && RECORDER.cancelled.contains(built[1].id()),
                                    "cancel must stop the build and tell listeners");
                            helper.assertTrue(
                                    StagedDungeonGenerationManager.heldChunks(level, built[1].id())
                                            == 0,
                                    "cancel must release every ticket of the build");
                            BoundingBox bounds = built[1].bounds();
                            for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
                                for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                                    helper.assertTrue(
                                            !DungeonChunkTickets.hasTicket(
                                                    level, ChunkPos.pack(x, z)),
                                            "No Procedural Dungeon ticket may stay in a cancelled"
                                                    + " build's chunks");
                                }
                            }
                        })
                .thenExecute(
                        () -> {
                            for (DungeonHandle handle : built) {
                                ProceduralDungeonApi.forget(level, handle.bounds());
                            }
                        })
                .thenSucceed();
    }

    /**
     * {@code reset} places the same dungeon again under a new id, restoring a broken block, and
     * applies the guardian health scale; {@code forget} drops the build, its locks and its
     * guardians.
     */
    public static void apiResetAndForgetRebuildAndDropADungeon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RECORDER.listen();
        CompletableFuture<Optional<DungeonPlan>> planned =
                ProceduralDungeonApi.plan(level.getServer(), TIER_1);
        BlockPos origin = helper.absolutePos(new BlockPos(12288, 32, -12288));
        DungeonHandle[] handles = new DungeonHandle[2];
        BlockPos[] broken = new BlockPos[1];
        BoundingBox[] oldBounds = new BoundingBox[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(planned.isDone(), "The plan must complete"))
                .thenExecute(
                        () -> {
                            DungeonPlan plan = planned.join().orElseThrow();
                            preload(
                                    level,
                                    plan.bounds()
                                            .moved(origin.getX(), origin.getY(), origin.getZ()));
                            handles[0] =
                                    ProceduralDungeonApi.build(
                                            level,
                                            plan,
                                            origin,
                                            new BuildOptions(true, 2.0, false));
                        })
                .thenWaitUntil(
                        () -> {
                            handles[0].advance(ADVANCE_BUDGET_NANOS);
                            helper.assertTrue(handles[0].complete(), "The build must complete");
                        })
                .thenExecute(
                        () -> {
                            Mob boss = boss(level, handles[0].bounds());
                            helper.assertTrue(
                                    boss != null
                                            && boss.getMaxHealth()
                                                    == DungeonGuardian.BOSS.maxHealth(1) * 2.0,
                                    "guardianHealthScale 2 must double the boss's health");
                            helper.assertTrue(
                                    RECORDER.bossSpawns.contains(handles[0].id()),
                                    "Listeners must hear the boss spawn");
                            broken[0] = handles[0].bossDoor().orElseThrow().below();
                            level.setBlock(broken[0], Blocks.AIR.defaultBlockState(), 3);
                            oldBounds[0] = handles[0].bounds();
                            handles[1] = ProceduralDungeonApi.reset(handles[0]);
                            helper.assertTrue(
                                    !handles[1].id().equals(handles[0].id())
                                            && ProceduralDungeonApi.handle(level, handles[0].id())
                                                    .isEmpty(),
                                    "reset must replace the build with a new id");
                            helper.assertTrue(
                                    boss.isRemoved(), "reset must remove the old guardians");
                        })
                .thenWaitUntil(
                        () -> {
                            handles[1].advance(ADVANCE_BUDGET_NANOS);
                            helper.assertTrue(
                                    handles[1].complete(), "The reset build must complete");
                        })
                .thenExecute(
                        () -> {
                            DungeonHandle handle = handles[1];
                            BoundingBox bounds = handle.bounds();
                            helper.assertTrue(
                                    !level.getBlockState(broken[0]).isAir(),
                                    "reset must place the broken block again");
                            helper.assertTrue(
                                    bounds.equals(oldBounds[0]),
                                    "reset must build in the same place");
                            BlockPos door = handle.bossDoor().orElseThrow();
                            helper.assertTrue(
                                    DungeonLockManager.isDoorLocked(level, door),
                                    "reset must lock the boss door again");
                            helper.assertTrue(
                                    boss(level, bounds) != null, "reset must spawn a boss again");
                            ProceduralDungeonApi.forget(level, bounds);
                            helper.assertTrue(
                                    ProceduralDungeonApi.handle(level, handle.id()).isEmpty(),
                                    "forget must drop the build");
                            helper.assertTrue(
                                    !DungeonLockManager.isDoorLocked(level, door),
                                    "forget must drop the lock records");
                            helper.assertTrue(
                                    boss(level, bounds) == null,
                                    "forget must remove the guardians");
                        })
                .thenSucceed();
    }

    private static @Nullable Mob boss(ServerLevel level, BoundingBox bounds) {
        return level
                .getEntitiesOfClass(
                        Mob.class,
                        AABB.of(bounds),
                        mob ->
                                DungeonGuardian.of(mob).map(DungeonGuardian::boss).orElse(false)
                                        && !mob.isRemoved())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static void preload(ServerLevel level, BoundingBox bounds) {
        // GameTest servers do not wait for real time, so asynchronous chunk loads would eat the
        // tick deadline; load the build's chunks up front.
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                level.getChunk(x, z);
            }
        }
    }

    private static List<String> describe(DungeonPlan plan) {
        List<String> pieces = new ArrayList<>();
        for (StagedDungeonPieceSpec piece : ((CompiledPlan) plan).layout().pieces()) {
            pieces.add(
                    piece.element()
                            + "@"
                            + piece.position().toShortString()
                            + "/"
                            + piece.rotation());
        }
        return pieces;
    }

    /** Records events per build id; tests run on the server thread. */
    private static final class Recorder implements DungeonListener {
        private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();
        private final List<UUID> completed = new ArrayList<>();
        private final List<UUID> cancelled = new ArrayList<>();
        private final List<UUID> unlocked = new ArrayList<>();
        private final List<UUID> bossSpawns = new ArrayList<>();
        private boolean listening;

        void listen() {
            if (!listening) {
                listening = true;
                ProceduralDungeonApi.listen(this);
            }
        }

        @Override
        public void progressed(DungeonHandle dungeon, int placedPieces, int totalPieces) {
            progress.put(dungeon.id(), placedPieces);
        }

        @Override
        public void completed(DungeonHandle dungeon) {
            completed.add(dungeon.id());
        }

        @Override
        public void cancelled(DungeonHandle dungeon) {
            cancelled.add(dungeon.id());
        }

        @Override
        public void guardianSpawned(DungeonHandle dungeon, Mob guardian, boolean boss) {
            if (boss) {
                bossSpawns.add(dungeon.id());
            }
        }

        @Override
        public void unlocked(DungeonHandle dungeon, ServerPlayer player, String key, BlockPos pos) {
            unlocked.add(dungeon.id());
        }
    }
}
