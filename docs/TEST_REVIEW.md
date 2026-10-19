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

## Coverage gaps and additions

Added shared server cases, registered for Fabric and NeoForge:

- `doubleChestUnlocksBothHalvesWithOneKey`: both halves resist breaking and explosions, either half unlocks both, and reopening does not spend a second key.
- `lockRecordsSurviveSaveAndReload`: chest, boss, miniboss, puzzle and key-source records survive the production codec; unlocked records stay unlocked after another save.
- `commandsRejectInvalidArgumentsAndNonOperators`: operator restrictions, unknown themes, argument limits and no queued work after rejection.
- `generationCommandFinishesItsQueuedDungeon`: the real command enqueues a dungeon, the loader's server tick hook completes it, and progression doors are placed and protected.

Remaining gaps:

- Lock diagnostic commands: distance sorting, list limits, marker appearance, 30-second expiry and shutdown cleanup.
- Full save-and-restart during queued generation, chunk-ticket release and repeated integrated-server sessions. The generation case covers completion in a running server, not process restart.
- Natural biome selection and terrain-height decisions in default Overworld, Nether and End worlds; current entrance tests compile layouts against fixtures.
- Breaking and explosion protection through the actual loader interaction events and explosion mixins. Lock-manager behaviour is covered directly.
- Grindstone shift-click, the 30-level cap and creative extraction; Soulbound with keep-inventory and Curse of Vanishing.
- Projectile afflictions, Stormcaller distance/chance and Warden's Wrath sonic-boom chance; existing combat tests cover melee Venom, Nether bane, on-kill effects and ally-safe blasts.
- Diagnostic benchmark, variant-grid and structure-gallery commands, which change disposable development worlds or source files.

There are no gamerules, user configuration screen or mod-owned client rendering. The vanilla appearance of item lore, puzzle clues and reveal markers still needs a human visual check.
