"""Generate the 20 theme dungeons at the winning RNG seed in the final capture world.

Per theme (theme order of the round-3 series):
  * clear the previous dungeon's bounding box (plus 6 blocks) and kill the dropped items
  * teleport to the winning chunk, run `/generatedungeon <theme> 5 20`, wait for the
    staged job to drain
  * force a save through the pause menu, measure the occupied bounding box from the
    saved regions and copy the region files that contain the dungeon into
    captures-dungeon2/worlds/<theme>/ (level.dat alongside)

The winning position is read from evidence/seeds-report.json.
"""

from __future__ import annotations

import json
import re
import shutil
import sys
import time
from pathlib import Path

ART = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ART / "scripts"))
import anvil  # noqa: E402
import drive_search as _D  # noqa: E402

LOG = _D.LOG
EVIDENCE = ART / "evidence"
WORLDS = ART / "worlds"
REGION = ART / "runtime" / "saves" / "Round3 Tall Dungeon" / "dimensions" / "minecraft" / "overworld" / "region"
WORLD = REGION.parents[3]  # <world>/dimensions/minecraft/overworld/region
THEMES = [
    "cobblestone", "deepslate", "sculk", "desert_tomb", "lush", "dripstone", "frozen",
    "ocean_ruin", "amethyst_geode", "copper", "stronghold", "nether_wastes", "crimson_forest",
    "warped_forest", "basalt_deltas", "soul_sand_valley", "nether_fortress", "bastion",
    "end_stone", "end_city",
]
# clip box for the clearing fill: centred on the winning dungeon (the round-3 box was
# centred on the origin, which no longer is where the dungeon sits)
CLEAR_HALF = 136
CLEAR_BOX = (-CLEAR_HALF, -64, -CLEAR_HALF, CLEAR_HALF, 319, CLEAR_HALF)


def winner() -> dict:
    report = json.loads((EVIDENCE / "seeds-report.json").read_text())
    return report["winner"]


def box_prepare_clear(box, clear_box=None, margin: int = 6):
    if not box:
        return None
    clear_box = clear_box or CLEAR_BOX
    x0, y0, z0, x1, y1, z1 = box[:6]
    return (max(x0 - margin, clear_box[0]), max(y0 - margin, clear_box[1]), max(z0 - margin, clear_box[2]),
            min(x1 + margin, clear_box[3]), min(y1 + margin, clear_box[4]), min(z1 + margin, clear_box[5]))


def clear_command(box) -> str:
    x0, y0, z0, x1, y1, z1 = box
    return f"/fill {x0} {y0} {z0} {x1} {y1} {z1} minecraft:air"


FLUIDS = {"minecraft:lava", "minecraft:water", "minecraft:flowing_lava", "minecraft:flowing_water"}


def measure_bbox(window) -> dict | None:
    """Structural bounding box of the theme dungeon inside `window`.

    Fluids are excluded (lava keeps flowing downwards long after placement, so the
    fluid extent grows with wall-clock time and is not part of the layout); their
    extent is reported separately.
    """
    x0, y0, z0, x1, y1, z1 = window
    structural = [10**9, 10**9, 10**9, -10**9, -10**9, -10**9]
    occupied = list(structural)
    blocks = fluids = 0
    for path in sorted(REGION.glob("r.*.*.mca")):
        rx, rz = (int(part) for part in path.name.split(".")[1:3])
        if not (rx * 32 <= (x1 >> 4) and (rx + 1) * 32 - 1 >= (x0 >> 4)
                and rz * 32 <= (z1 >> 4) and (rz + 1) * 32 - 1 >= (z0 >> 4)):
            continue
        for cx, cz, nbt in anvil.iter_region_chunks(path):
            if not ((x0 >> 4) <= cx <= (x1 >> 4) and (z0 >> 4) <= cz <= (z1 >> 4)):
                continue
            for x, y, z, key in anvil.iter_chunk_blocks(nbt):
                if not (x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1):
                    continue
                occupied = [min(occupied[0], x), min(occupied[1], y), min(occupied[2], z),
                            max(occupied[3], x), max(occupied[4], y), max(occupied[5], z)]
                if key.split("[")[0] in FLUIDS:
                    fluids += 1
                    continue
                blocks += 1
                structural = [min(structural[0], x), min(structural[1], y), min(structural[2], z),
                              max(structural[3], x), max(structural[4], y), max(structural[5], z)]
    if blocks == 0:
        return None
    return {"bbox": structural, "blocks": blocks, "fluid_blocks": fluids, "occupied_bbox": occupied,
            "size": [structural[3] - structural[0] + 1, structural[4] - structural[1] + 1,
                     structural[5] - structural[2] + 1]}


def regions_for(window) -> list[str]:
    """Region files whose chunk range intersects `window` (x0,y0,z0,x1,y1,z1)."""
    x0, _, z0, x1, _, z1 = window
    names = []
    for path in sorted(REGION.glob("r.*.*.mca")):
        rx, rz = (int(part) for part in path.name.split(".")[1:3])
        if (rx * 32 <= (x1 >> 4) and (rx + 1) * 32 - 1 >= (x0 >> 4)
                and rz * 32 <= (z1 >> 4) and (rz + 1) * 32 - 1 >= (z0 >> 4)):
            names.append(path.name)
    return names


def main(argv: list[str]) -> int:
    win = winner()
    position = win["position"]
    x, y, z = position
    clear_box = (int(x) - CLEAR_HALF, -64, int(z) - CLEAR_HALF,
                 int(x) + CLEAR_HALF, 319, int(z) + CLEAR_HALF)
    window = (int(x) - 100, 0, int(z) - 100, int(x) + 100, 320, int(z) + 100)
    only = argv[1:] if len(argv) > 1 else THEMES
    path = EVIDENCE / "generation-log.json"
    state = json.loads(path.read_text()) if path.exists() else {"themes": {}, "winner": win}
    driver = _D.Driver()
    driver.ensure_channel()
    if not state.get("setupDone"):
        driver.command_and_wait("/time set noon", r"noon|time to 6000")
        # /gamemode sends no chat feedback when the game mode is already set, so it is
        # proved by the channel probe that follows it (same call as round 3 used)
        driver.send_without_feedback("/gamemode spectator")
        state["setupDone"] = True
        path.write_text(json.dumps(state, indent=1))
    x, y, z = position
    r = 7  # force-load 15x15 chunks around the dungeon (spans +-5 chunks; forceload caps at 256)
    chunk_x, chunk_z = int(x) >> 4, int(z) >> 4
    driver.command_and_wait(f"/forceload add {(chunk_x - r) * 16} {(chunk_z - r) * 16} "
                            f"{(chunk_x + r) * 16 + 15} {(chunk_z + r) * 16 + 15}",
                            r"chunk.*force load|force load.*chunk|Added .*chunk", timeout=300)
    previous_box = None
    for theme in THEMES:
        if theme not in only:
            continue
        entry = state["themes"].get(theme, {})
        if entry.get("done"):
            previous_box = box_prepare_clear(entry.get("bbox"), clear_box)
            driver.say(f"{theme}: already generated, skipping")
            continue
        started = time.time()
        driver.command_and_wait(f"/tp @s {x:.1f} {y:.1f} {z:.1f} 0 0",
                                r"Teleported .* to %.6f, %.6f, %.6f" % (x, y, z), timeout=120)
        if previous_box:
            driver.command_and_wait(clear_command(previous_box), r"Successfully filled|No blocks were filled", timeout=900)
            driver.command_and_wait("/kill @e[type=!minecraft:player]", r"Killed \d+ entit|No entity", timeout=180)
        text = driver.command_and_wait(f"/generatedungeon {theme} 5 20", r"Scheduling Tier 5", timeout=180)
        match = re.search(r"Scheduled .* with (\d+) pieces", text)
        if not match:
            text = driver.wait_for(r"Scheduled .* with (\d+) pieces", 900) or text
            match = re.search(r"Scheduled .* with (\d+) pieces", text)
        if not match:
            raise SystemExit(f"{theme}: dungeon never scheduled")
        pieces = int(match.group(1))
        while True:
            text = driver.command_and_wait("/generatedungeonstatus",
                                           r"Staged dungeon jobs: (\d+), pending pieces: (\d+)", timeout=300)
            status = list(re.finditer(r"Staged dungeon jobs: (\d+), pending pieces: (\d+)", text))[-1]
            if status.group(1) == "0" and status.group(2) == "0":
                break
            time.sleep(2)
        driver.pause_save()
        measured = measure_bbox(window)
        out_dir = WORLDS / theme
        (out_dir / "region").mkdir(parents=True, exist_ok=True)
        copied = regions_for(window)
        for name in copied:
            shutil.copy2(REGION / name, out_dir / "region" / name)
        shutil.copy2(WORLD / "level.dat", out_dir / "level.dat")
        entry = {"theme": theme, "pieces": pieces, "seconds": round(time.time() - started, 1),
                 "bbox": measured["bbox"] if measured else None,
                 "blocks": measured["blocks"] if measured else 0,
                 "size": measured["size"] if measured else None,
                 "regions": copied, "position": position, "rng_seed": win.get("rng_seed"),
                 "done": True}
        state["themes"][theme] = entry
        path.write_text(json.dumps(state, indent=1))
        driver.say(f"{theme}: {json.dumps({k: entry[k] for k in ('pieces', 'size', 'seconds')})}")
        previous_box = box_prepare_clear(measured["bbox"] if measured else None, clear_box)
    driver.say("theme generation done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
