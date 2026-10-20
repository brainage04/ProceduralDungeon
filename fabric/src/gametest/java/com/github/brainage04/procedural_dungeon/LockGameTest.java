package com.github.brainage04.procedural_dungeon;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registrations of {@link LockGameTests}; NeoForge registers the same functions by name. */
public final class LockGameTest {
    @GameTest(maxTicks = 100)
    public void lockedChestsStayShutThroughTheLoaderUseHook(GameTestHelper helper) {
        LockGameTests.lockedChestsStayShutThroughTheLoaderUseHook(helper);
    }

    @GameTest(maxTicks = 100)
    public void sealedBossRoomsResistDiggingAndKeepTheirDoor(GameTestHelper helper) {
        LockGameTests.sealedBossRoomsResistDiggingAndKeepTheirDoor(helper);
    }

    @GameTest(maxTicks = 100)
    public void pistonsFluidsAndBlastsCannotOpenASealedRoom(GameTestHelper helper) {
        LockGameTests.pistonsFluidsAndBlastsCannotOpenASealedRoom(helper);
    }

    @GameTest(maxTicks = 100)
    public void puzzleMachineryResistsDiggingUntilSolved(GameTestHelper helper) {
        LockGameTests.puzzleMachineryResistsDiggingUntilSolved(helper);
    }

    @GameTest(maxTicks = 100)
    public void hoppersAndJoinedChestsCannotDrainLockedChests(GameTestHelper helper) {
        LockGameTests.hoppersAndJoinedChestsCannotDrainLockedChests(helper);
    }

    @GameTest(maxTicks = 100)
    public void brokenKeyChestsLoseExplosionProtection(GameTestHelper helper) {
        LockGameTests.brokenKeyChestsLoseExplosionProtection(helper);
    }

    @GameTest(maxTicks = 100)
    public void lockReadsDoNotCreateLockData(GameTestHelper helper) {
        LockGameTests.lockReadsDoNotCreateLockData(helper);
    }

    @GameTest(maxTicks = 100)
    public void lockDataIsVersionedAndOldRecordsLoad(GameTestHelper helper) {
        LockGameTests.lockDataIsVersionedAndOldRecordsLoad(helper);
    }

    @GameTest(maxTicks = 100)
    public void lockPlansApplyByChunk(GameTestHelper helper) {
        LockGameTests.lockPlansApplyByChunk(helper);
    }

    @GameTest(maxTicks = 100)
    public void bossLootWaitsForTheGuardian(GameTestHelper helper) {
        LockGameTests.bossLootWaitsForTheGuardian(helper);
    }

    @GameTest(maxTicks = 200)
    public void bossKeyBurntInLavaIsReissuedAndRetired(GameTestHelper helper) {
        LockGameTests.bossKeyBurntInLavaIsReissuedAndRetired(helper);
    }

    @GameTest(maxTicks = 100)
    public void storedAndMovedBossKeysAreNotReissued(GameTestHelper helper) {
        LockGameTests.storedAndMovedBossKeysAreNotReissued(helper);
    }

    @GameTest(maxTicks = 800)
    public void clearedAndCreativeDeletedBossKeysAreReissued(GameTestHelper helper) {
        LockGameTests.clearedAndCreativeDeletedBossKeysAreReissued(helper);
    }

    @GameTest(maxTicks = 100)
    public void operatorsCanReissueALostBossKey(GameTestHelper helper) {
        LockGameTests.operatorsCanReissueALostBossKey(helper);
    }

    @GameTest(maxTicks = 100)
    public void doorLocksFollowTheirChunkNotTheLockIndex(GameTestHelper helper) {
        LockGameTests.doorLocksFollowTheirChunkNotTheLockIndex(helper);
    }
}
