# Test review, 2026-10-19

## Frozen rubric

- Keep: exercises observable production behaviour or a nontrivial catalogue or algorithm invariant with assertions that can detect a real regression. A real Minecraft world, real menu, or mock player using production game logic is not a mock echo.
- Redundant: all meaningful assertions and input cases are covered by another named retained test. Partial overlap, another loader, another puzzle mechanic, or another rotation does not qualify.
- Tautological: only checks a literal or constant, copied implementation, mock echo, or registration without meaningful production behaviour.
- Keep uncertain cases.

The automated review used this rubric unchanged for all 21 shared server GameTests. Every test was classified keep; none was flagged for manual removal. There are no unit tests or client GameTests. Fabric wrappers and NeoForge test-instance resources run the shared cases on their respective loaders; they are not redundant tests.

## Classifications

All entries below are keep:

- `variantCatalogIsStableAndComplete`
- `seededLootSelectionIsRepeatableAndTierBounded`
- `everyLayoutHasABossRoomAndABossKeyVault`
- `guardedDoorOpensOnlyWithItsKey`
- `placedBossRoomIsGuardedLockedAndStocked`
- `trialSpawnersEjectTieredDungeonLoot`
- `surfaceEntrancesLeadDownIntoAFullDungeon`
- `dungeonEnchantmentsAreRewardOnlyAndTakeEffect`
- `bossRewardsAreExclusiveAndRelicsKeepBaseStats`
- `overMaxBooksApplyThroughAnvils`
- `enchantmentBlastsSpareAllies`
- `killEnchantmentsRewardTheKillerAndBurstOnEnemies`
- `soulboundItemsSurviveDeath`
- `grindstonesSalvageBooksAndEssenceThatAnvilsReapply`
- `puzzleRoomsPlaceWholeAndHoldTheirChests`
- `framePuzzleOpensWhenEveryArrowPointsUp`
- `bookshelfPuzzleOpensWhenEveryShelfEndsOnItsLastSlot`
- `targetPuzzleOpensWhenEveryBulbIsLit`
- `chordPuzzleOpensOnlyForItsChord`
- `sluicePuzzleOpensWhenTheStreamReachesTheTorch`
- `crafterPuzzleOpensWhenTheDiscPlays`

No tests were removed.
