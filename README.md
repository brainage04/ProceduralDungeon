# About
Adds a procedurally generated dungeon to Minecraft.

The structures that can spawn within this dungeon are as follows:
- todo: guaranteed path to boss key (north)
- todo: guaranteed path to boss room (south)
- 3 different hallway lengths:
    - Small: 2 hallway rooms + 1 hallway end room
    - Medium: 3 hallway rooms + 1 hallway end room
    - Large: 4 hallway rooms + 1 hallway end room
- 9 different hallway rooms:
    - Toolsmith
    - Armorsmith
    - Weaponsmith
    - Enchanter
    - Spawner Corridor (2 Pillager Trial Spawners) - todo: modify trial spawner loot
    - 2 Spiral Staircases (up + down, branches off into 2 hallways + 2 hallway rooms)
    - 2 Traditional Staircases (up + down, ends with a hallway)
- 4 different hallway traps:
    - Dripstone
    - Lava
    - Negative Potions
    - Spawners
- 3 different hallway end rooms (branches off into 2 hallway rooms + 1 hallway loot room):
    - Small: 1 Monster Spawner (Zombie)
    - Medium: 3 Monster Spawners (Zombie, Skeleton, Spider)
    - Large: 12 Monster Spawners (2x Zombie, 2x Husk, 2x Skeleton, 1x Stray, 1x Bogged, 2x Spider, 2x Cave Spider)
- 3 different hallway loot rooms:
    - Small: Small pile of blocks, 1 chest
    - Medium: Medium pile of blocks, 2 chests
    - Large: Large pile of blocks, 4 chests

# Tiers
Loot tiers:
1. Leather/Wood
2. Copper
3. Iron
4. Diamond
5. Netherite/Gold

Spawner tiers:
1. MaxNearbyEntites=4, MinSpawnDelay=200, MaxSpawnDelay=800, RequiredPlayerRange=16, SpawnCount=1, SpawnRange=4
2. MaxNearbyEntites=8, MinSpawnDelay=175, MaxSpawnDelay=650, RequiredPlayerRange=20, SpawnCount=2, SpawnRange=5
3. MaxNearbyEntites=12, MinSpawnDelay=150, MaxSpawnDelay=500, RequiredPlayerRange=24, SpawnCount=3, SpawnRange=6
4. MaxNearbyEntites=16, MinSpawnDelay=125, MaxSpawnDelay=350, RequiredPlayerRange=28, SpawnCount=4, SpawnRange=7
5. MaxNearbyEntites=20, MinSpawnDelay=100, MaxSpawnDelay=200, RequiredPlayerRange=32, SpawnCount=5, SpawnRange=8

# Themes
Dungeon themes:
1. Overworld (Cobblestone)
2. Nether (Netherrack)
3. Nether Fortress (Nether Bricks)
4. Bastion (Blackstone)
5. The End (End Stone)
6. The Deep Dark (Sculk)

# Generating a Dungeon
Command usage: `/generatedungeon <tier> <theme> <depth>`

Tier must range from 1-5

Theme must be one of the following:
- `overworld`
- `nether`
- `nether_fortress`
- `bastion`
- `the_end`
- `deep_dark`

Depth must range from 1-20
