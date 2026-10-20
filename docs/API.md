# Public API

Other server-side mods build dungeons through `com.github.brainage04.procedural_dungeon.api`. Everything outside that package (and `api.impl`, which is not API) can change without notice. `ProceduralDungeonApi.VERSION` is 1; a change that breaks callers raises it.

## Depending on Procedural Dungeon

Procedural Dungeon stays a standalone mod; an integrating mod treats it as an optional dependency.

1. Publish the Fabric JAR from a Procedural Dungeon checkout next to yours: `./gradlew :fabric:publishAllPublicationsToLocalRepository`. It writes `io.github.brainage04.procedural_dungeon:procedural_dungeon-fabric:<mod_version>` to `build/local-repo`, with a POM that lists no dependencies.
2. In your mod, find it with the workspace-dependencies plugin and compile against it only:

   ```gradle
   workspaceDependencies {
       siblingMaven("ProceduralDungeon") {
           coordinate.set("io.github.brainage04.procedural_dungeon:procedural_dungeon-fabric:1.0.0")
       }
   }
   dependencies {
       compileOnly "io.github.brainage04.procedural_dungeon:procedural_dungeon-fabric:1.0.0"
   }
   ```

3. Declare it optional: `"suggests": {"procedural_dungeon": "*"}` in `fabric.mod.json`, or a `type="optional"` dependency in `neoforge.mods.toml`.
4. Keep every class that names a Procedural Dungeon type in one package, and load it only when the mod is present (`FabricLoader.getInstance().isModLoaded("procedural_dungeon")`, or `ModList.get().isLoaded(...)` on NeoForge).

## Planning and building

```java
DungeonRequest request = new DungeonRequest("cobblestone", 3, 6, matchSeed, false);
ProceduralDungeonApi.plan(server, request).thenAccept(plan -> plan.ifPresent(compiled -> {
    DungeonHandle dungeon = ProceduralDungeonApi.build(
            level, compiled, origin, new BuildOptions(true, 1.5, false));
    // Each tick, from your own budget:
    long used = dungeon.advance(4_000_000L);
}));
```

- `plan(server, request)` resolves the theme's pool on the server thread and compiles on a worldgen worker, as natural generation does; the future completes on the server thread. It is empty when no layout fits (try another seed) and throws `IllegalArgumentException` for an unknown theme, a tier outside 1-5 or a depth outside 1-20.
- A plan depends only on the request. The same request always gives the same layout, and one plan can be built any number of times: each build is the same dungeon moved to its origin. `plan.bounds()` is relative to the origin.
- `origin` is the start room's entrance socket, or with `surfaceEntrance` the foot of the entrance shaft, which belongs at the surface. `build` throws when the moved dungeon does not fit the level's height.
- A build has its own id, its own chunk tickets (non-persistent, released when it finishes or is cancelled) and its own lock records. Pieces are placed once their chunks are loaded.

`BuildOptions`:

| Option | Meaning |
|---|---|
| `callerDrivesBudget` | Procedural Dungeon's tick never places this build; call `advance(budgetNanos)` yourself. Lets one budget cover every arena. |
| `guardianHealthScale` | Multiplies the boss and miniboss guardians' maximum health when they spawn, for example by player count. |
| `persistent` | The build survives a restart and resumes. Use `false` in worlds you throw away. |

`BuildOptions.DEFAULT` is Procedural Dungeon's own budget, tier health and persistence.

## Following a build

`DungeonHandle` is a live view: `state()` (`RUNNING`, `FAILED`, `CANCELLED`, `COMPLETE`), `placedPieces()`, `totalPieces()`, `failure()`, `bounds()`, `startSpawn()` (standing room in the start room once the build is complete) and `bossDoor()`. `ProceduralDungeonApi.handle(level, id)` finds a build again, also after a restart for persistent builds.

`ProceduralDungeonApi.listen(listener)` adds a `DungeonListener`. Every method is a no-op by default and runs on the server thread right after the change:

| Event | When |
|---|---|
| `progressed(dungeon, placed, total)` | a piece was placed |
| `completed(dungeon)` | every piece is placed; after a crash the build re-checks its pieces and this can fire again |
| `failed(dungeon, reason)` | a piece could not be placed and the build stopped |
| `cancelled(dungeon)` | `cancel()` stopped the build |
| `guardianSpawned(dungeon, mob, boss)` | a boss or miniboss spawned, after the health scale |
| `guardianKilled(dungeon, mob, killer, boss)` | a guardian died; `killer` is the credited player or null |
| `unlocked(dungeon, player, key, pos)` | a key opened a door or chest (`key` is `rusted_key`, `miniboss_key` or `boss_key`) |
| `puzzleSolved(dungeon, chest)` | a solved puzzle room's chest was opened |
| `keyDestroyed(dungeon, key)` | a key was destroyed (lava, the void, despawning) |

Only API builds raise events; natural and `/generatedungeon` dungeons raise none. A listener that throws is logged and the others still run.

## Stopping, reusing and cleaning up

- `dungeon.cancel()` stops placing and releases the build's tickets. Placed blocks, locks and guardians stay.
- `ProceduralDungeonApi.forget(level, box)` drops everything Procedural Dungeon keeps inside the box: builds, placement markers, lock, puzzle and key records, and its tagged entities (guardians, puzzle frames) in loaded chunks. Blocks stay, so clear or reuse the area afterwards.
- `ProceduralDungeonApi.reset(dungeon)` builds the same plan in the same place again under a new id with the same options: it forgets the old bounds and re-places every piece, which refills chests, relocks doors and respawns guardians. It costs a placement pass, not a compile.

For an arena in a shared dimension, also clear the area's blocks, items, arrows and XP orbs, and its scheduled block and fluid ticks (`level.getBlockTicks().clearArea(box)`, `level.getFluidTicks().clearArea(box)`). A dimension that is deleted after each match needs none of this.

## Switches

| Switch | Default | Effect |
|---|---|---|
| `/gamerule procedural_dungeon:natural_generation` | `true` | Dungeons start in newly generated chunks, and `/locate` finds them. Chunks that already hold a dungeon start keep it. |
| `/gamerule procedural_dungeon:grindstone_salvage` | `true` | A grindstone turns an enchanted item and a book into an enchanted book, and a relic into Relic Essence. |
| `ProceduralDungeonApi.naturalGeneration(Predicate<ResourceKey<Level>>)` | every dimension | Natural generation only in the dimensions the predicate accepts, on top of the game rule. It runs on worldgen threads, so it must be thread-safe. |
| `ProceduralDungeonApi.globalMechanics(Predicate<ServerLevel>)` | every level | Grindstone salvage, the anvil's over-max levels and Relic Essence, and Soulbound apply only in the accepted levels. |

The predicates can be set before or after the server starts, and the last call wins. The other dungeon enchantments act only through items that come from dungeon loot, so they are not switched.
