"""Create the final capture world (fresh, only the winning dungeon will be generated).

Clones the structure of the worlds used for the search - level.dat, the canonical
tier-5 pool datapack, the game rules and the flat world-gen settings (seed 20260910) -
and drops every generated chunk file, so the 20 theme dungeons are the only content in
the saved regions.
"""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

ART = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ART / "scripts"))
import nbt  # noqa: E402

RUNTIME = ART / "runtime"
SEARCH_WORLD = RUNTIME / "saves" / "Round3 Tall Search"
FINAL_NAME = "Round3 Tall Dungeon"
WORK = ART / "work"


def keep_search_world() -> dict:
    """Move the search world out of the live saves (it holds all 38 candidate dungeons)."""
    dst = WORK / "search-world"
    if dst.exists():
        shutil.rmtree(dst)
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(SEARCH_WORLD, dst, ignore=shutil.ignore_patterns("session.lock"))
    return {"search_world_copy": str(dst.relative_to(ART))}


def make_final_world() -> dict:
    dest = RUNTIME / "saves" / FINAL_NAME
    if dest.exists():
        shutil.rmtree(dest)
    (dest / "datapacks").mkdir(parents=True)
    shutil.copy2(SEARCH_WORLD / "level.dat", dest / "level.dat")
    shutil.copytree(SEARCH_WORLD / "datapacks" / "round3-canonical-pools",
                    dest / "datapacks" / "round3-canonical-pools")
    for dimension in ("overworld", "the_nether", "the_end"):
        for sub in ("region", "entities", "poi", "data"):
            (dest / "dimensions" / "minecraft" / dimension / sub).mkdir(parents=True, exist_ok=True)
    for rel in ("data/minecraft/game_rules.dat", "data/minecraft/world_gen_settings.dat",
                "data/minecraft/random_sequences.dat"):
        target = dest / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(SEARCH_WORLD / rel, target)
    root = nbt.load(dest / "level.dat")
    data = root.value["Data"].value
    data["LevelName"] = nbt.Tag(nbt.TAG_STRING, FINAL_NAME)
    data["GameType"] = nbt.Tag(nbt.TAG_INT, 3)
    data["allowCommands"] = nbt.Tag(nbt.TAG_BYTE, 1)
    spawn = data["spawn"].value
    spawn["pos"] = nbt.Tag(nbt.TAG_LIST, (nbt.TAG_INT, [0, 200, 0]))
    nbt.dump(dest / "level.dat", root, "")
    gen = nbt.unwrap(nbt.load(dest / "data/minecraft/world_gen_settings.dat"))
    rules = nbt.unwrap(nbt.load(dest / "data/minecraft/game_rules.dat"))
    return {"world": str(dest), "name": FINAL_NAME, "seed": gen["data"]["seed"],
            "generator": gen["data"]["dimensions"]["minecraft:overworld"]["generator"]["type"],
            "rules": rules.get("data", {}).get("GameRules", rules.get("data", {})),
            "packs": ["round3-canonical-pools"]}


def make_empty_reference() -> dict:
    """Staged world for the empty-sky reference render: level.dat only, no chunks."""
    dst = ART / "worlds" / "_empty"
    if dst.exists():
        shutil.rmtree(dst)
    dst.mkdir(parents=True)
    shutil.copy2(RUNTIME / "saves" / FINAL_NAME / "level.dat", dst / "level.dat")
    return {"empty_world": str(dst.relative_to(ART))}


def main() -> int:
    report = {"search": keep_search_world(), "final": make_final_world()}
    report["empty"] = make_empty_reference()
    (ART / "evidence" / "final-world-setup.json").write_text(json.dumps(report, indent=1))
    print(json.dumps(report["final"], indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
