"""Build the captures-dungeon2 working runtime: own gameDir + own search world.

  * the gameDir is a copy of the proven captures-dungeon runtime (mods, options,
    config, assets pointers) with the saves replaced by a fresh flat world that
    keeps the *same* world seed 20260910 and the canonical tier-5 pool datapack
  * every generated chunk file, the staged-dungeon save data and the player data
    are dropped so the search starts from an empty world
"""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

ART = Path(__file__).resolve().parent.parent
ROUND3 = ART.parent
SRC = ROUND3 / "captures-dungeon"
SRC_RUNTIME = SRC / "runtime"
SRC_WORLD = SRC_RUNTIME / "saves" / "Round3 Canonical Dungeon"
RUNTIME = ART / "runtime"
WORLDS = ART / "worlds"

sys.path.insert(0, str(ART / "scripts"))
import nbt  # noqa: E402

WORLD_NAME = "Round3 Tall Search"
SEED = 20260910


def copy_runtime() -> dict:
    if RUNTIME.exists():
        shutil.rmtree(RUNTIME)
    shutil.copytree(SRC_RUNTIME, RUNTIME, ignore=shutil.ignore_patterns("saves", "logs", "crash-reports"))
    (RUNTIME / "saves").mkdir(parents=True)
    (RUNTIME / "logs").mkdir(parents=True)
    return {"gameDir": str(RUNTIME)}


def make_world(name: str = WORLD_NAME, level_name: str | None = None) -> dict:
    dest = RUNTIME / "saves" / name
    if dest.exists():
        shutil.rmtree(dest)
    (dest / "datapacks").mkdir(parents=True)
    shutil.copy2(SRC_WORLD / "level.dat", dest / "level.dat")
    # data packs (canonical pools) are what makes all themes use one geometry
    shutil.copytree(SRC_WORLD / "datapacks" / "round3-canonical-pools",
                    dest / "datapacks" / "round3-canonical-pools")
    # directory skeleton for the dimensions: 26.2 only writes chunks where the
    # per-dimension directories already exist
    for dimension in ("overworld", "the_nether", "the_end"):
        for sub in ("region", "entities", "poi", "data"):
            (dest / "dimensions" / "minecraft" / dimension / sub).mkdir(parents=True, exist_ok=True)
    (dest / "data" / "minecraft").mkdir(parents=True, exist_ok=True)
    for rel in ("data/minecraft/game_rules.dat", "data/minecraft/world_gen_settings.dat",
                "data/minecraft/random_sequences.dat"):
        shutil.copy2(SRC_WORLD / rel, dest / rel)
    root = nbt.load(dest / "level.dat")
    data = root.value["Data"].value
    data["LevelName"] = nbt.Tag(nbt.TAG_STRING, level_name or name)
    data["GameType"] = nbt.Tag(nbt.TAG_INT, 3)  # spectator
    data["allowCommands"] = nbt.Tag(nbt.TAG_BYTE, 1)
    spawn = data["spawn"].value
    spawn["pos"] = nbt.Tag(nbt.TAG_LIST, (nbt.TAG_INT, [0, 200, 0]))
    nbt.dump(dest / "level.dat", root, "")
    gen = nbt.unwrap(nbt.load(dest / "data/minecraft/world_gen_settings.dat"))
    return {"world": str(dest), "level_name": level_name or name,
            "seed": gen["data"]["seed"], "generator": gen["data"]["dimensions"]["minecraft:overworld"]["generator"]["type"],
            "packs": ["round3-canonical-pools"]}


def main() -> int:
    report = {"runtime": copy_runtime(), "world": make_world()}
    (ART / "evidence" / "runtime-setup.json").write_text(json.dumps(report, indent=1))
    print(json.dumps(report, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
