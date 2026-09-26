# ProceduralDungeon todo

## Progression and content

- [x] Guarantee a navigable generated route to a boss-key source and boss room.
- [x] Add optional miniboss rooms with their own keys and loot.
- [x] Define trial-spawner loot.
- [ ] Replace the placeholder boss (Dungeon Warden) and miniboss (Dungeon Sentinel) with real encounters.
- [ ] Add more room templates.
- [ ] Add a ghost-ally on-kill enchantment: an invisible SparringBots bot (Fabric-only optional dependency) fighting mobs for the killer. Waits for the SparringBots session to commit, then needs mob targeting and an ally API in SparringBots.
- [x] Add puzzle rooms built from vanilla mechanics, each with a reward chest of vanilla rarities (armour trims, music discs, pottery sherds) found nowhere else.
- [x] Give miniboss and boss rooms exclusive rewards: dungeon enchantments, over-max books, relics, and boss-only signature items.
- [x] Add on-kill and Warden-themed enchantments, Soulbound, relic affixes, and grindstone/anvil salvage for enchantments and relic bonuses.
- [ ] Review and overhaul the remaining loot tables (tiers, ordinary rooms, traps, trial spawners) together.

## Validation and tuning

- [x] Test natural generation in default Overworld, Nether, and End world types rather than only superflat or command-driven fixtures.
- [x] Make `/locate structure` fast enough not to trip the server watchdog.
- [ ] Playtest and tune traps, structure density, loot, difficulty, and progression.
- [ ] Run a focused code review and cleanup after the functional work.

## Release

- [x] Populate `.modrinth/project.json`.
- [ ] Review the release icons and assets, and publish the next release.
