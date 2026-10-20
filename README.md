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

Keys are used up when they open a lock. Locked chests and doors cannot be broken or blown up until they are unlocked, hoppers cannot pull items out of them, and the chests holding keys are also protected from explosions.

### Locks and guarded rooms

- **Guarded rooms are protected whole.** While a boss or miniboss room's door is locked, or its guardian is alive, no block in the room's box can be broken by players, explosions or pistons, and fluids cannot flow into it from outside. The door survives losing the block under it. The rest of the dungeon stays open to survival play.
- **Puzzle rooms** with a locked chest are protected the same way until the solved chest is first opened, so the mechanism and its walls cannot be mined. Pistons and water inside the room still work.
- **Boss and miniboss loot waits for the guardian.** The reward chests in a guarded room stay locked until that room's guardian dies (or is otherwise removed from the world, such as on Peaceful).
- **Double chests:** a chest placed next to a locked chest does not open the locked half.

### The Boss Key

Each dungeon has exactly one valid Boss Key at a time, and a destroyed key is replaced.

- **Identity.** When the boss key vault is placed, its chest is filled at once and its Boss Key gets a unique id, which is stored with the boss door's lock record. The door opens only for the key carrying the record's current id. Boss Keys from other dungeons, copies of an old key and keys from before this version (no id) do not open it. (Dungeons placed before this version keep their old rule: any Boss Key opens them.)
- **What counts as destroyed.** The key is destroyed when:
  - its dropped item is removed from the world while still holding the key: burnt by lava or fire, broken by a cactus or an explosion, fallen into the void, despawned after five minutes, or `/kill`ed. A shulker box or bundle destroyed that way destroys the key inside it.
  - `/clear` removes it from a player.
  - a creative-mode player deletes it from their inventory: it left an inventory slot through the creative screen and, 30 seconds later, is not back in that player's inventory, ender chest, open container or cursor, and was not dropped from the creative screen meanwhile. The creative screen's only exits are another slot, a drop or deletion, so this is exact for it. Should a key ever leave by another route the server cannot see, it is reissued falsely; that is safe, because the old id retires and only the new key opens the door.
- **What does not count.** Picking up, hopper and mob pickup, merging, chunk unloading and changing dimension move the key and keep it valid. A key in a chest, an ender chest, an offline player's inventory or an unloaded chunk is never reissued. Detection runs only on those removal paths; nothing polls inventories.
- **Reissue.** When the key carrying the current id is destroyed, the record draws a new id and puts one new Boss Key in the vault chest; if the chest is gone or full, the key is dropped at the vault with no despawn timer. Players within 64 blocks of the boss door or the vault are told. The old id is retired, so a copy that turns up later (a dupe, or an item restored from a backup) opens nothing, and destroying it does not trigger another reissue.
- **Retirement.** Opening the boss door uses up the key and retires the record; nothing is reissued after that.
- **Not covered:** operator commands that delete a container without dropping its contents (`/setblock`, `/fill`), a creative player breaking an item frame that holds the key, and keys lost to a crash rollback or a corrupted player or chunk file. None of these reissues the key on its own; the remedy is `/dungeon bosskey reissue <pos>` (operators), which reissues the key of the boss door nearest to `<pos>` (within 256 blocks) exactly as a destroyed key would be: a new key in the vault, the old id retired.

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
- `/dungeon bosskey reissue <pos>` - replaces the Boss Key of the dungeon whose boss door is nearest to `<pos>` (within 256 blocks): one new key goes into the boss key vault and the old key stops opening the door. Use it when a key was lost without being destroyed in game, for example to a crash rollback or a corrupted file.

Diagnostic and development commands are listed in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#diagnostic-commands).

## Configuration

No configuration file or gamerules yet.

## Compatibility

Dungeon items are made of vanilla items and components, so they work for unmodded clients. NeoForge installations use NeoForge without Fabric API.

## Documentation

- [docs/GAMEPLAY.md](docs/GAMEPLAY.md) - structures, puzzle answers, rewards, enchantments, tiers and themes.
- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) - builds, server GameTests, playtesting and diagnostic commands.
- [docs/DUNGEON_DESIGN_IDEAS.md](docs/DUNGEON_DESIGN_IDEAS.md) - dungeon design notes.
- [docs/RELEASE.md](docs/RELEASE.md) - release workflow.
- [docs/MODRINTH.md](docs/MODRINTH.md) - Modrinth publishing setup.
