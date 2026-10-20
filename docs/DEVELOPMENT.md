# Development

`./gradlew build` creates one production JAR per loader under `build/libs`; Fabric data generation and production GameTests remain available, and NeoForge GameTests run on a NeoForge server against the release JAR with `./gradlew :neoforge:runProductionServerGameTest`.

`./gradlew :fabric:runPlaytest` starts a Fabric development client for playtesting. It replaces the world `procedural_dungeon_playtest` with a fresh one, builds a dungeon of a random theme and tier, puts you in its start room in survival, and gives you gear for the tier: enchanted armour, sword, bow, pickaxe and axe, a shield, food, golden apples, torches, blocks, and a grindstone, anvil, books, and 30 levels for trying the salvage. `-PplaytestTheme=<theme>` and `-PplaytestTier=<1-5>` pick them instead, and `/playtest [theme] [tier]` builds another dungeon in the same world.

Run both loaders' server GameTests with `./gradlew runAllGameTests`. Client GameTests are disabled because the mod has no client fixtures.

`./gradlew :fabric:publishAllPublicationsToLocalRepository` publishes the Fabric JAR to `build/local-repo` for mods that compile against the [public API](API.md).

Theme and tier specs (`themes.json`, `tiers.json`) are read from the jar. A development run started with `-Dprocedural_dungeon.source_specs=true` reads them from the nearest `common/src/main/resources/procedural_dungeon/` above its working directory instead, so spec edits apply without a rebuild.

## Diagnostic commands

These commands need operator permission (level 2). Use a disposable world: they place structures, change force-loaded chunks, reload datapacks, or write structure files.

- `/testdungeonvariants` - places a grid of dungeon variants.
- `/benchmarkdungeons reload|preload|clearpreload|generation` - benchmarks resource reloads and generation, or manages the benchmark's force-loaded chunks.
- `/structuregallery build [spacing]` - places a gallery of structure templates.
- `/structuregallery save` - copies edited gallery structures back into source folders when run from a development checkout.
- `/dungeondebug jobs` - lists the staged builds of the current dimension with their state (running, failed, cancelled, complete), progress and failure reason; unreadable saved records are listed as failed.
- `/dungeondebug retry <job>` - runs a failed build again from the piece that failed (for example after fixing a missing template).
- `/dungeondebug cancel <job>` - stops a running build and releases its chunks; placed rooms stay.
- `/dungeondebug releaseforced` - for worlds upgraded from PD 1.0.0: removes the forced chunk tickets old versions left behind inside current builds and built natural dungeons, and lists any other level-32 forced chunks (not released, since their owner is unknown). Vanilla `/forceload` chunks (level 31) are never touched.
- `/dungeondebug build <theme> <tier> <depth>` - like `/generatedungeon`, but the build's record is kept after it completes, for `verify`.
- `/dungeondebug saveprogress` / `savechunks` - write only the build records, or only the chunks, of the current dimension; used with `kill -9` to test crash recovery.
- `/dungeondebug verify` - checks every kept build: every placed (piece, chunk) part has its marker, no dungeon guardian or frame exists twice, and every planned lock is on a closed, locked door. Loads chunks synchronously; development only.

## Staged building and crash safety

- A build stores its layout once and a cursor; its record is written with the level's normal saves.
- Each piece is pasted one chunk at a time; the chunk gets a `procedural_dungeon:placed` marker (a Fabric data attachment or NeoForge attachment) in the same step, so the chunk file holds the blocks and the marker together.
- After a clean stop a build resumes from its cursor; after a crash it walks every piece again and skips every part whose marker is present, so looted chests, opened doors, killed guardians and player edits are never undone. Guardians and puzzle frames carry `procedural_dungeon.job/piece/marker` tags and are never spawned twice.
- Builds load chunks with Procedural Dungeon's own ticket (load only, never saved, shared between builds); at most 4 builds per dimension hold chunks at once, each keeping about 16 chunks ahead of its cursor.
- A chunk where players spent more than a minute while the build waited for it is left alone, which protects player builds from a delayed build.
- The time budget is half of what the last 20 ticks left of the tick-rate target, measured after Procedural Dungeon's own work, 2-20 ms, shared round-robin across dimensions and builds. API builds with a caller-driven budget are left to their caller.
- `/generatedungeon` and API plans compile their layout on a worldgen worker, as natural generation does, so they do not stall the tick.
- `placeSynchronously` (benchmarks, `/testdungeonvariants`) generates chunks on the server thread and is for operators only; the playtest uses the staged path.
