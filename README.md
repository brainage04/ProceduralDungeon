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

# Rewards

The best loot is reserved for the rooms that are hardest to reach. Ordinary chests never hold enchanted golden apples, heavy cores, netherite upgrade templates, Mending, relics, or dungeon enchantments.

- **Miniboss chests:** an enchanted weapon or armour piece at 1.5× the tier's enchantment level, and one exclusive book: an affliction, bane, on-kill, or nova enchantment (below), or a vanilla enchantment one level above its normal maximum (for example Sharpness VI or Protection V). Anvils keep over-max levels when applying these books or combining items that already have them, but never raise a level past the highest one supplied.
- **Boss chests:** one **relic**, a **Victor's Feast** (a golden carrot that feeds like a feast and grants Regeneration, Strength, Resistance, and Fire Resistance), an enchanted golden apple, a weapon and armour piece at twice the tier's enchantment level, and chances at a heavy core, a netherite upgrade template, Mending, and the rarest enchantments (Annihilation, Stormcaller, Volatile, Cataclysm, Warden's Wrath, Soulbound).

Relics are named, unbreakable items built from the dungeon tier's material, with enchantments above the vanilla maximum and attribute bonuses on top of the item's own stats. Bonuses grow with the tier (`t`). Besides its signature bonuses, every relic except the Phylactery rolls one random **affix** from its pool; each relic is equally likely to drop whatever the size of its pool.

| Relic | Item | Enchantments | Bonuses |
|---|---|---|---|
| Warden's Cleaver | Axe | Sharpness 3+t | +t attack damage |
| Aegis of the Depths | Chestplate | Protection 2+t | +2t max health, +0.05t knockback resistance |
| Stridewalkers | Boots | Feather Falling 3+t | +4t% movement speed, step up full blocks |
| Crown of the Fallen | Helmet | Protection 1+t, Respiration III | +t max health, +t luck |
| Delver's Pick | Pickaxe | Efficiency 4+t, Fortune III | +10t% block break speed |
| Stormstring | Bow | Power 3+t, Stormcaller | |
| Phylactery | Heart of the Sea | | Saves you from death once when held, like a Totem of Undying |

Affix pools (an affix never repeats one of the relic's signature bonuses):

- **Armour** (Aegis, Stridewalkers, Crown): +t max health, +t max absorption, +0.5t armour, +0.5t armour toughness, +0.04t knockback resistance, +0.1t explosion knockback resistance, +2t% movement speed, +10t% sneaking speed, +3t% jump strength, +0.5 step height, +t safe fall distance, −8t% fall damage, +0.1t water movement efficiency, +t oxygen bonus, −10t% burning time, +0.5t luck.
- **Combat** (Warden's Cleaver): +0.5t attack damage, +0.1t attack speed, +0.2t attack knockback, +0.1t sweeping damage ratio, +0.25t attack reach.
- **Mining** (Delver's Pick): +0.5t block reach, +2t mining efficiency, +10t% block break speed, +0.16t submerged mining speed.
- **Mobility** (Stormstring): +2t% movement speed, +10t% sneaking speed, +3t% jump strength, +t safe fall distance.

## Salvage

- **Grindstone + book:** an enchanted item and a plain book make an enchanted book holding all of the item's enchantments except curses. The item stays in the grindstone with only its curses; one book is used, and it costs one experience level per enchantment level (at most 30).
- **Grindstone, relic alone:** grinding an item that carries relic bonuses gives a **Relic Essence** holding those bonuses. The item keeps its enchantments and base stats.
- **Anvil + Relic Essence:** combining an item with an essence moves the bonuses onto it for 10 levels. Armour bonuses fit any armour piece (and move to that piece's slot); weapon and tool bonuses fit any weapon or tool. An item carries one set of bonuses at a time, so grind an item's bonuses off before giving it new ones.

## Dungeon enchantments

These enchantments exist only in dungeon reward chests; enchanting tables, villagers, and other loot never roll them.

- **Banes** (weapons, I–V, +2.5 damage per level against their targets, exclusive with Sharpness and Smite): Bane of the Deep (wardens), Bane of the Nether (blazes, ghasts, magma cubes, piglins, hoglins, zoglins, withers, wither skeletons), Bane of the End (the dragon, endermen, endermites, shulkers), Bane of Illagers (raiders and vexes), Duelist (players).
- **Annihilation** (weapons, I–V, boss only): +2.5 damage per level against everything.
- **Afflictions** (weapons, bows, crossbows, and tridents, I–III): each hit applies an effect that lasts longer, and for most grows stronger, per level. Venom (Poison), Withering (Wither), Crippling (Slowness), Blinding (Blindness), Eclipse (Darkness), Famine (Hunger), Infestation (Infested), Updraft (Levitation), Drifting (Slow Falling), Sapping (Mining Fatigue), Vertigo (Nausea), Oozing (Oozing), Enfeebling (Weakness).
- **Stormcaller** (I–III, boss only): each hit has a 15% chance per level to call lightning. From 4 or more blocks away a real bolt strikes the target; closer in, the target takes a lightning jolt instead, so the bolt never hits the attacker.
- **Volatile** (I–III, boss only): a killing blow makes the victim explode without breaking blocks.
- **Cataclysm** (I–III, boss only): like Volatile, with a bigger blast that breaks blocks like TNT. It cannot share a weapon with Volatile.
- **Siphon** (I–II): a kill heals you 4 health per level.
- **Warding** (I–III): a kill gives you Absorption (level I–III) for 15–25 seconds.
- **Bloodlust** (I–II): a kill gives you Speed and Strength (level I–II) for 8–12 seconds.
- **Insight** (I–III): kills drop 1.5×, 2×, or 2.5× experience.
- **Novas** (I–III): a kill bursts over everything within 3, 4.5, or 6 blocks of the victim for 5, 7, or 9 seconds, with effects at the enchantment's level. Pyre sets them on fire, Plague poisons them, and Dread weakens and slows them.
- **Warden's Wrath** (I–III, boss only): every hit applies Darkness, and has a 10% chance per level to fire a sonic boom that deals 6, 8, or 10 damage through armour.
- **Soulbound** (any enchantable item, boss only): the item stays with you when you die, in the same slot. Curse of Vanishing still destroys it.

Explosions and novas never touch their wielder, the wielder's tamed pets, teammates on the wielder's scoreboard team, or players the wielder cannot hurt (friendly fire off, or PvP disabled). Everyone else in range is affected.

Dungeon items are made of vanilla items and components, so they work for unmodded clients.

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
