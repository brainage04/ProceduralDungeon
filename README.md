# ProceduralDungeon

ProceduralDungeon is a server-side only Fabric and NeoForge mod for Minecraft 26.2 that adds procedurally generated dungeons.

Every dungeon is built from jigsaw pieces in one of 20 themes and 5 difficulty tiers, with themed loot, traps, spawners, locked chests, optional miniboss rooms, and a boss room at the end of a guaranteed route. Higher tiers are rarer, larger, and more dangerous.

## Features

### Finding dungeons

Dungeons generate naturally in the Overworld, the Nether, and the End, and are built in the background over a few seconds so world generation stays smooth.

- **Overworld and End:** a ruined surface entrance marks the spot. A scaffolding column runs down its shaft into the dungeon's start room. In the End, dungeons only generate on solid ground on the outer islands, never on the central dragon island.
- **Nether:** dungeons are buried in the most solid terrain between y=24 and y=104 and have no surface entrance.

### Progression and puzzles

- **Boss room (guaranteed):** every dungeon has one boss room at the far end of its deepest route. The Boss Key waits in a chest in the boss key vault on a different branch.
- **Miniboss rooms (optional):** miniboss rooms appear randomly in place of ordinary rooms. The key for each miniboss room is hidden in one of the dungeon's ordinary loot chests.
- **Rusted Keys:** a few end-room chests are locked and need a Rusted Key, which is hidden in another loot chest of the same dungeon.
- **Puzzle rooms:** optional dead ends with vanilla redstone puzzles, parkour or stealth, and a reward chest of armour trims, pottery sherds and sometimes a music disc.

Keys are used up when they open a lock. Locked chests and doors cannot be broken or blown up until they are unlocked, and the chests holding keys are also protected from explosions.

### Rewards and salvage

The best loot is reserved for the rooms that are hardest to reach. Ordinary chests never hold enchanted golden apples, heavy cores, netherite upgrade templates, Mending, relics, or dungeon enchantments.

- **Miniboss chests:** enchanted equipment, dungeon enchantments and vanilla enchantment books above their normal maximum.
- **Boss chests:** unbreakable relics, a Victor's Feast, enchanted equipment and boss-only enchantments.
- **Salvage:** use a grindstone and a book to recover non-curse enchantments, or grind a relic alone to recover its bonuses as Relic Essence. Anvils apply the essence to another compatible item.

The boss and miniboss are placeholders for now: a wither skeleton and a vindicator.

## Getting started

- Minecraft 26.2 and Java 25 or newer.
- Fabric Loader 0.19.3 or newer with Fabric API, or NeoForge 26.2.0.88 or newer.
- Install exactly one ProceduralDungeon JAR matching your loader on the server, or in singleplayer. Players can join with an unmodded vanilla client.

## Commands

All commands need operator permission (level 2).

- `/generatedungeon <theme> <tier> <depth>` - builds a dungeon at your position without a surface entrance. The theme is one of the [theme names](docs/GAMEPLAY.md#themes), such as `procedural_dungeon:cobblestone`; tier is 1-5 and jigsaw depth is 1-20.
- `/generatedungeonstatus` - shows how many dungeons are still being built in the current dimension.
- `/dungeonlocks list|nearest|reveal` - finds locked chests; `/dungeonlocks doors …` finds locked boss and miniboss doors; `/dungeonlocks keys …` finds the chests that hold keys. `reveal` highlights matches for 30 seconds.

Diagnostic and development commands are listed in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#diagnostic-commands).

## Configuration

Two game rules, both `true` by default:

- `/gamerule procedural_dungeon:natural_generation false` stops dungeons from generating in new chunks; `/locate` stops finding them. Dungeons already generated stay.
- `/gamerule procedural_dungeon:grindstone_salvage false` gives grindstones their vanilla behaviour back.

## Using it from another mod

Other server-side mods can plan seeded dungeons, build them where they want with their own tick budget, follow the build and its keys, guardians and puzzles through events, and reset or forget them, for example for a minigame arena. They can also keep natural generation, grindstone salvage, the anvil changes and Soulbound out of their own worlds. See [docs/API.md](docs/API.md).

## Compatibility

Dungeon items are made of vanilla items and components, so they work for unmodded clients. NeoForge installations use NeoForge without Fabric API.

## Documentation

- [docs/GAMEPLAY.md](docs/GAMEPLAY.md) - structures, puzzle answers, rewards, enchantments, tiers and themes.
- [docs/API.md](docs/API.md) - the public API for other mods, and the switches.
- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) - builds, server GameTests, playtesting and diagnostic commands.
- [docs/DUNGEON_DESIGN_IDEAS.md](docs/DUNGEON_DESIGN_IDEAS.md) - dungeon design notes.
- [docs/RELEASE.md](docs/RELEASE.md) - release workflow.
- [docs/MODRINTH.md](docs/MODRINTH.md) - Modrinth publishing setup.
