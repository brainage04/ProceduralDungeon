package com.github.brainage04.procedural_dungeon;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registration of {@link StagedJobGameTests}. */
public final class StagedJobFabricGameTest {
    @GameTest(maxTicks = 600)
    public void jobRecordsEncodeEachPieceOncePerSave(GameTestHelper helper) {
        StagedJobGameTests.jobRecordsEncodeEachPieceOncePerSave(helper);
    }

    @GameTest(maxTicks = 600)
    public void sharedChunksKeepTheirTicketUntilTheLastJob(GameTestHelper helper) {
        StagedJobGameTests.sharedChunksKeepTheirTicketUntilTheLastJob(helper);
    }

    @GameTest(maxTicks = 600)
    public void stagedJobsNeverForceLoadChunks(GameTestHelper helper) {
        StagedJobGameTests.stagedJobsNeverForceLoadChunks(helper);
    }

    @GameTest(maxTicks = 300)
    public void aFailedPieceFailsTheJobAndSkipsItsLocks(GameTestHelper helper) {
        StagedJobGameTests.aFailedPieceFailsTheJobAndSkipsItsLocks(helper);
    }

    @GameTest(maxTicks = 100)
    public void unreadableJobRecordsAreKeptAsFailed(GameTestHelper helper) {
        StagedJobGameTests.unreadableJobRecordsAreKeptAsFailed(helper);
    }

    @GameTest(maxTicks = 300)
    public void twoJobsInOneChunkBothRun(GameTestHelper helper) {
        StagedJobGameTests.twoJobsInOneChunkBothRun(helper);
    }

    @GameTest(maxTicks = 200, environment = "procedural_dungeon:staged_budget")
    public void roundRobinServesEveryLevelUnderATightBudget(GameTestHelper helper) {
        StagedJobGameTests.roundRobinServesEveryLevelUnderATightBudget(helper);
    }

    @GameTest(maxTicks = 300)
    public void aPiecePlacesTheChunksThatAreLoaded(GameTestHelper helper) {
        StagedJobGameTests.aPiecePlacesTheChunksThatAreLoaded(helper);
    }

    @GameTest(maxTicks = 900)
    public void replayAfterChunksSavedFirstRevertsNothing(GameTestHelper helper) {
        StagedJobGameTests.replayAfterChunksSavedFirstRevertsNothing(helper);
    }

    @GameTest(maxTicks = 900)
    public void replayAfterProgressSavedFirstRestoresLostRooms(GameTestHelper helper) {
        StagedJobGameTests.replayAfterProgressSavedFirstRestoresLostRooms(helper);
    }

    @GameTest(maxTicks = 900)
    public void replayAfterProgressSavedFirstKeepsASavedGuardian(GameTestHelper helper) {
        StagedJobGameTests.replayAfterProgressSavedFirstKeepsASavedGuardian(helper);
    }

    @GameTest(maxTicks = 600)
    public void replayPlacesOnlyTheMissingHalfOfAPiece(GameTestHelper helper) {
        StagedJobGameTests.replayPlacesOnlyTheMissingHalfOfAPiece(helper);
    }

    @GameTest(maxTicks = 900)
    public void replayWithATaggedGuardianSpawnsNoSecond(GameTestHelper helper) {
        StagedJobGameTests.replayWithATaggedGuardianSpawnsNoSecond(helper);
    }

    @GameTest(maxTicks = 300)
    public void delayedJobsLeavePlayerBuildsAlone(GameTestHelper helper) {
        StagedJobGameTests.delayedJobsLeavePlayerBuildsAlone(helper);
    }

    @GameTest(maxTicks = 100)
    public void levelsWithoutJobsKeepNoStagedData(GameTestHelper helper) {
        StagedJobGameTests.levelsWithoutJobsKeepNoStagedData(helper);
    }

    @GameTest(maxTicks = 100)
    public void legacyJobRecordsMigrateAndReleaseTheirTickets(GameTestHelper helper) {
        StagedJobGameTests.legacyJobRecordsMigrateAndReleaseTheirTickets(helper);
    }

    @GameTest(maxTicks = 100)
    public void onlyACleanStopResumesFromTheCursor(GameTestHelper helper) {
        StagedJobGameTests.onlyACleanStopResumesFromTheCursor(helper);
    }

    @GameTest(maxTicks = 100)
    public void aNewSessionRestartsTheTickWindow(GameTestHelper helper) {
        StagedJobGameTests.aNewSessionRestartsTheTickWindow(helper);
    }
}
