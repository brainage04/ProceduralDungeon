# About

ProceduralDungeon adds procedurally generated dungeons to Minecraft. Every dungeon is built from jigsaw pieces in one of 20 themes and 5 difficulty tiers, with themed loot, traps, spawners, locked chests, optional miniboss rooms, and a boss room at the end of a guaranteed route.

## Loaders and builds

ProceduralDungeon supports both Fabric and NeoForge on Minecraft 26.2. `./gradlew build` creates one production JAR per loader under `build/libs`; Fabric data generation and production GameTests remain available, and NeoForge GameTests run with `./gradlew runNeoForgeGameTests`.

## Installing

ProceduralDungeon is server-side only: install it on the server (or in singleplayer) and players can join with an unmodded vanilla client. Install exactly one ProceduralDungeon JAR matching your loader—Fabric or NeoForge. Fabric installations also require Fabric API; NeoForge installations use NeoForge without Fabric API.

## Migrating from older releases

Remove the old JAR before switching loaders; the mod ID remains `procedural_dungeon`, so existing world data and datapack paths under that namespace are retained. Older releases registered their own key items; those keys disappear from existing worlds, and newly generated dungeons use the trial-key-based keys described below.

# Finding dungeons

Dungeons generate naturally in the Overworld, the Nether, and the End, and are built in the background over a few seconds so world generation stays smooth.

- **Overworld and End:** a ruined surface entrance (ruined archway, sunken courtyard, or ritual descent) marks the spot. A scaffolding column runs down its shaft into the dungeon's start room. In the End, dungeons only generate on solid ground on the outer islands (the End highlands and midlands, like End cities), never on the central dragon island.
- **Nether:** dungeons are buried in the most solid terrain between y=24 and y=104 and have no surface entrance.

Higher tiers are rarer, larger, and more dangerous.

# Progression

- **Boss room (guaranteed):** every dungeon has one boss room at the far end of its deepest route, sealed by an iron door in a reinforced deepslate frame. The door only opens with a **Boss Key**, which waits in a chest in the **boss key vault**, a small side room placed on a different branch. Inside the boss room are the boss and two boss loot chests.
- **Miniboss rooms (optional):** miniboss rooms appear randomly in place of ordinary rooms. Each one is guarded by a miniboss, holds one miniboss loot chest, and sits behind an iron door that opens with a **Miniboss Key**. The key for each miniboss room is hidden in one of the dungeon's ordinary loot chests.
- **Rusted Keys:** a few end-room chests are locked and need a **Rusted Key**, which is hidden in another loot chest of the same dungeon.

Keys are trial keys (the Boss Key is an ominous trial key) with their own name and a hidden tag, so renaming an ordinary trial key does not make a dungeon key, and dungeon keys do not open trial chamber vaults. Keys are used up when they open a lock. Locked chests and doors cannot be broken or blown up until they are unlocked, and the chests holding keys are also protected from explosions.

The boss and miniboss are placeholders for now:

| Guardian | Mob | Health | Gear |
|---|---|---|---|
| Dungeon Warden (boss) | Wither skeleton | 60 + 40 per tier | Tier sword and full tier armour |
| Dungeon Sentinel (miniboss) | Vindicator | 30 + 15 per tier | Tier sword |

# Structures

Hallways branch from the start room in three lengths:
- Small: 2 hallway rooms + 1 hallway end room
- Medium: 3 hallway rooms + 1 hallway end room
- Large: 4 hallway rooms + 1 hallway end room

Hallway rooms:
- Toolsmith, Armorsmith, and Weaponsmith (armour stands and a chest of tier equipment)
- Enchanter
- Spawner Corridor (2 Pillager Trial Spawners that reward tiered dungeon loot, with better rewards when ominous)
- Spiral Staircases (up and down; branch into 2 hallways and 2 hallway rooms)
- Diagonal Staircases (up and down; end with a hallway)
- Miniboss Room (see [Progression](#progression))

Hallway traps (varies by theme):
- Dripstone
- Lava
- Negative Potions
- Spawners

Hallway end rooms, which branch into 2 hallway rooms and 1 hallway loot room:
- Small: 2 monster spawners
- Medium: 4 monster spawners
- Large: 8 monster spawners

Spawner mobs depend on the theme.

Hallway loot rooms:
- Small: 1 chest
- Medium: 2 chests
- Large: 4 chests

# Tiers

Loot tiers:
1. Leather/Wood
2. Copper
3. Iron
4. Diamond
5. Netherite/Gold

Each tier mostly rolls its own material, with smaller chances for the neighbouring tiers.

Spawner tiers:
1. MaxNearbyEntities=4, MinSpawnDelay=200, MaxSpawnDelay=800, RequiredPlayerRange=16, SpawnCount=1, SpawnRange=4
2. MaxNearbyEntities=8, MinSpawnDelay=175, MaxSpawnDelay=650, RequiredPlayerRange=20, SpawnCount=2, SpawnRange=5
3. MaxNearbyEntities=12, MinSpawnDelay=150, MaxSpawnDelay=500, RequiredPlayerRange=24, SpawnCount=3, SpawnRange=6
4. MaxNearbyEntities=16, MinSpawnDelay=125, MaxSpawnDelay=350, RequiredPlayerRange=28, SpawnCount=4, SpawnRange=7
5. MaxNearbyEntities=20, MinSpawnDelay=100, MaxSpawnDelay=200, RequiredPlayerRange=32, SpawnCount=5, SpawnRange=8

# Themes

Each theme restyles the building blocks, picks its own spawner mobs and allowed traps, and adds themed items to the loot.

- **Overworld:** `cobblestone`, `deepslate`, `sculk`, `desert_tomb`, `lush`, `dripstone`, `frozen`, `ocean_ruin`, `amethyst_geode`, `copper`, `stronghold`
- **Nether:** `nether_wastes`, `crimson_forest`, `warped_forest`, `basalt_deltas`, `soul_sand_valley`, `nether_fortress`, `bastion`
- **End:** `end_stone`, `end_city`

# Commands

All commands need operator permission (level 2).

- `/generatedungeon <theme> <tier> <depth>` builds a dungeon at your position without a surface entrance. The theme is one of the names above (for example `procedural_dungeon:cobblestone`), the tier ranges from 1-5, and the depth (how many jigsaw steps the dungeon grows from its start room) ranges from 1-20.
- `/generatedungeonstatus` shows how many dungeons are still being built in the current dimension.
- `/dungeonlocks list|nearest|reveal` finds locked chests; `/dungeonlocks doors …` finds locked boss and miniboss doors; `/dungeonlocks keys …` finds the chests that hold keys. `reveal` highlights matches for 30 seconds.
