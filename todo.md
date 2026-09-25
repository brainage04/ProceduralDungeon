# ProceduralDungeon todo

## Progression and content

- [x] Guarantee a navigable generated route to a boss-key source and boss room.
- [x] Add optional miniboss rooms with their own keys and loot.
- [x] Define trial-spawner loot.
- [ ] Replace the placeholder boss (Dungeon Warden) and miniboss (Dungeon Sentinel) with real encounters.
- [ ] Add more room templates.
- [ ] Add puzzle rooms built from vanilla mechanics, each with a reward chest.
- [ ] Give puzzle, miniboss, and boss rooms rewards found nowhere else, ranked puzzle < miniboss < boss; today every item in them also appears in ordinary chests.
- [ ] Review and overhaul every loot table (tiers, rooms, traps, trial spawners) together.

## Validation and tuning

- [x] Test natural generation in default Overworld, Nether, and End world types rather than only superflat or command-driven fixtures.
- [x] Make `/locate structure` fast enough not to trip the server watchdog.
- [ ] Playtest and tune traps, structure density, loot, difficulty, and progression.
- [ ] Run a focused code review and cleanup after the functional work.

## Release

- [x] Populate `.modrinth/project.json`.
- [ ] Review the release icons and assets, and publish the next release.
