# Development

`./gradlew build` creates one production JAR per loader under `build/libs`; Fabric data generation and production GameTests remain available, and NeoForge GameTests run on a NeoForge server against the release JAR with `./gradlew :neoforge:runProductionServerGameTest`.

`./gradlew :fabric:runPlaytest` starts a Fabric development client for playtesting. It replaces the world `procedural_dungeon_playtest` with a fresh one, builds a dungeon of a random theme and tier, puts you in its start room in survival, and gives you gear for the tier: enchanted armour, sword, bow, pickaxe and axe, a shield, food, golden apples, torches, blocks, and a grindstone, anvil, books, and 30 levels for trying the salvage. `-PplaytestTheme=<theme>` and `-PplaytestTier=<1-5>` pick them instead, and `/playtest [theme] [tier]` builds another dungeon in the same world.

Run both loaders' server GameTests with `./gradlew runAllGameTests`. Client GameTests are disabled because the mod has no client fixtures.

## Diagnostic commands

These commands need operator permission (level 2). Use a disposable world: they place structures, change force-loaded chunks, reload datapacks, or write structure files.

- `/testdungeonvariants` - places a grid of dungeon variants.
- `/benchmarkdungeons reload|preload|clearpreload|generation` - benchmarks resource reloads and generation, or manages the benchmark's force-loaded chunks.
- `/structuregallery build [spacing]` - places a gallery of structure templates.
- `/structuregallery save` - copies edited gallery structures back into source folders when run from a development checkout.
