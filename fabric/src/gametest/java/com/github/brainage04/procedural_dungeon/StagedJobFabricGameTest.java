package com.github.brainage04.procedural_dungeon;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registration of {@link StagedJobGameTests}. */
public final class StagedJobFabricGameTest {
    @GameTest(
            maxTicks = 600,
            environment = "procedural_dungeon:staged_job_records_encode_each_piece_once_per_save")
    public void jobRecordsEncodeEachPieceOncePerSave(GameTestHelper helper) {
        StagedJobGameTests.jobRecordsEncodeEachPieceOncePerSave(helper);
    }

    @GameTest(
            maxTicks = 600,
            environment =
                    "procedural_dungeon:staged_shared_chunks_keep_their_ticket_until_the_last_job")
    public void sharedChunksKeepTheirTicketUntilTheLastJob(GameTestHelper helper) {
        StagedJobGameTests.sharedChunksKeepTheirTicketUntilTheLastJob(helper);
    }

    @GameTest(
            maxTicks = 600,
            environment = "procedural_dungeon:staged_staged_jobs_never_force_load_chunks")
    public void stagedJobsNeverForceLoadChunks(GameTestHelper helper) {
        StagedJobGameTests.stagedJobsNeverForceLoadChunks(helper);
    }

    @GameTest(
            maxTicks = 300,
            environment =
                    "procedural_dungeon:staged_a_failed_piece_fails_the_job_and_skips_its_locks")
    public void aFailedPieceFailsTheJobAndSkipsItsLocks(GameTestHelper helper) {
        StagedJobGameTests.aFailedPieceFailsTheJobAndSkipsItsLocks(helper);
    }

    @GameTest(
            maxTicks = 100,
            environment = "procedural_dungeon:staged_unreadable_job_records_are_kept_as_failed")
    public void unreadableJobRecordsAreKeptAsFailed(GameTestHelper helper) {
        StagedJobGameTests.unreadableJobRecordsAreKeptAsFailed(helper);
    }

    @GameTest(
            maxTicks = 300,
            environment = "procedural_dungeon:staged_two_jobs_in_one_chunk_both_run")
    public void twoJobsInOneChunkBothRun(GameTestHelper helper) {
        StagedJobGameTests.twoJobsInOneChunkBothRun(helper);
    }

    @GameTest(maxTicks = 200, environment = "procedural_dungeon:staged_budget")
    public void roundRobinServesEveryLevelUnderATightBudget(GameTestHelper helper) {
        StagedJobGameTests.roundRobinServesEveryLevelUnderATightBudget(helper);
    }

    @GameTest(
            maxTicks = 300,
            environment = "procedural_dungeon:staged_a_piece_places_the_chunks_that_are_loaded")
    public void aPiecePlacesTheChunksThatAreLoaded(GameTestHelper helper) {
        StagedJobGameTests.aPiecePlacesTheChunksThatAreLoaded(helper);
    }

    @GameTest(
            maxTicks = 900,
            environment =
                    "procedural_dungeon:staged_replay_after_chunks_saved_first_reverts_nothing")
    public void replayAfterChunksSavedFirstRevertsNothing(GameTestHelper helper) {
        StagedJobGameTests.replayAfterChunksSavedFirstRevertsNothing(helper);
    }

    @GameTest(
            maxTicks = 900,
            environment =
                    "procedural_dungeon:staged_replay_after_progress_saved_first_restores_lost_rooms")
    public void replayAfterProgressSavedFirstRestoresLostRooms(GameTestHelper helper) {
        StagedJobGameTests.replayAfterProgressSavedFirstRestoresLostRooms(helper);
    }

    @GameTest(
            maxTicks = 900,
            environment =
                    "procedural_dungeon:staged_replay_after_progress_saved_first_keeps_a_saved_guardian")
    public void replayAfterProgressSavedFirstKeepsASavedGuardian(GameTestHelper helper) {
        StagedJobGameTests.replayAfterProgressSavedFirstKeepsASavedGuardian(helper);
    }

    @GameTest(
            maxTicks = 600,
            environment =
                    "procedural_dungeon:staged_replay_places_only_the_missing_half_of_a_piece")
    public void replayPlacesOnlyTheMissingHalfOfAPiece(GameTestHelper helper) {
        StagedJobGameTests.replayPlacesOnlyTheMissingHalfOfAPiece(helper);
    }

    @GameTest(
            maxTicks = 900,
            environment =
                    "procedural_dungeon:staged_replay_with_a_tagged_guardian_spawns_no_second")
    public void replayWithATaggedGuardianSpawnsNoSecond(GameTestHelper helper) {
        StagedJobGameTests.replayWithATaggedGuardianSpawnsNoSecond(helper);
    }

    @GameTest(
            maxTicks = 300,
            environment = "procedural_dungeon:staged_delayed_jobs_leave_player_builds_alone")
    public void delayedJobsLeavePlayerBuildsAlone(GameTestHelper helper) {
        StagedJobGameTests.delayedJobsLeavePlayerBuildsAlone(helper);
    }

    @GameTest(
            maxTicks = 100,
            environment = "procedural_dungeon:staged_levels_without_jobs_keep_no_staged_data")
    public void levelsWithoutJobsKeepNoStagedData(GameTestHelper helper) {
        StagedJobGameTests.levelsWithoutJobsKeepNoStagedData(helper);
    }

    @GameTest(
            maxTicks = 100,
            environment =
                    "procedural_dungeon:staged_legacy_job_records_migrate_and_release_their_tickets")
    public void legacyJobRecordsMigrateAndReleaseTheirTickets(GameTestHelper helper) {
        StagedJobGameTests.legacyJobRecordsMigrateAndReleaseTheirTickets(helper);
    }

    @GameTest(
            maxTicks = 100,
            environment = "procedural_dungeon:staged_only_a_clean_stop_resumes_from_the_cursor")
    public void onlyACleanStopResumesFromTheCursor(GameTestHelper helper) {
        StagedJobGameTests.onlyACleanStopResumesFromTheCursor(helper);
    }

    @GameTest(
            maxTicks = 100,
            environment = "procedural_dungeon:staged_a_new_session_restarts_the_tick_window")
    public void aNewSessionRestartsTheTickWindow(GameTestHelper helper) {
        StagedJobGameTests.aNewSessionRestartsTheTickWindow(helper);
    }

    @GameTest(
            maxTicks = 100,
            environment =
                    "procedural_dungeon:staged_new_job_records_reach_the_disk_before_any_piece")
    public void newJobRecordsReachTheDiskBeforeAnyPiece(GameTestHelper helper) {
        StagedJobGameTests.newJobRecordsReachTheDiskBeforeAnyPiece(helper);
    }
}
