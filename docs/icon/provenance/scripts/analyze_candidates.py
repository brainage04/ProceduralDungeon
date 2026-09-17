"""Score the searched candidates by how square their silhouette is in the render.

Reuses the exact render camera convention from captures-chunky/scripts/chunky_render.py
(MC yaw 45 / pitch 30 -> chunky yaw = 90 - mcYaw, chunky pitch = mcPitch - 90):

    right       = (-sin(yaw), 0, -cos(yaw))
    screen_down = ( cos(yaw)cos(pitch), sin(pitch), -sin(yaw)cos(pitch) )

For every candidate the occupied voxels are read back from the saved region files and
projected onto those two axes; the ratio height/width is what the crop margins depend on
(1.0 = the content bounding box is square in the image plane).
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

ART = Path(__file__).resolve().parent.parent
EVIDENCE = ART / "evidence"
sys.path.insert(0, str(ART / "scripts"))
import anvil  # noqa: E402

MC_YAW, MC_PITCH = 45.0, 30.0
YAW = math.radians(90.0 - MC_YAW)
PITCH = math.radians(MC_PITCH - 90.0)
RIGHT = (-math.sin(YAW), 0.0, -math.cos(YAW))
DOWN = (math.cos(YAW) * math.cos(PITCH), math.sin(PITCH), -math.sin(YAW) * math.cos(PITCH))
MARGIN_FRAC = 0.07

REGION = ART / "work" / "search-world" / "dimensions" / "minecraft" / "overworld" / "region"
FLUIDS = {"minecraft:lava", "minecraft:water", "minecraft:flowing_lava", "minecraft:flowing_water"}


def silhouette(bbox, window_margin: int = 8) -> dict:
    """Project every occupied voxel of the dungeon onto the render axes."""
    x0, y0, z0, x1, y1, z1 = bbox
    lo_u = lo_v = 10**9
    hi_u = hi_v = -10**9
    count = 0
    # scan one block beyond the measured box on every side: the measurement window is
    # +/-100 blocks so the box is complete, this only guards against a clipped scan
    sx0, sy0, sz0 = x0 - window_margin, y0 - window_margin, z0 - window_margin
    sx1, sy1, sz1 = x1 + window_margin, y1 + window_margin, z1 + window_margin
    for path in sorted(REGION.glob("r.*.*.mca")):
        rx, rz = (int(part) for part in path.name.split(".")[1:3])
        if not (rx * 32 <= (sx1 >> 4) and (rx + 1) * 32 - 1 >= (sx0 >> 4)
                and rz * 32 <= (sz1 >> 4) and (rz + 1) * 32 - 1 >= (sz0 >> 4)):
            continue
        for cx, cz, nbt in anvil.iter_region_chunks(path):
            if not ((sx0 >> 4) <= cx <= (sx1 >> 4) and (sz0 >> 4) <= cz <= (sz1 >> 4)):
                continue
            for x, y, z, key in anvil.iter_chunk_blocks(nbt):
                if not (sx0 <= x <= sx1 and sy0 <= y <= sy1 and sz0 <= z <= sz1):
                    continue
                if key.split("[")[0] in FLUIDS:
                    continue  # lava/water keeps flowing after placement: not part of the layout
                u = RIGHT[0] * x + RIGHT[2] * z
                v = DOWN[0] * x + DOWN[1] * y + DOWN[2] * z
                lo_u, hi_u = min(lo_u, u), max(hi_u, u)
                lo_v, hi_v = min(lo_v, v), max(hi_v, v)
                count += 1
    if count == 0:
        return {"voxels": 0}
    width_blocks = hi_u - lo_u
    height_blocks = hi_v - lo_v
    span = max(width_blocks, height_blocks)
    side = span + 2 * round(MARGIN_FRAC * span)
    return {
        "voxels": count,
        "fluids_excluded": sorted(FLUIDS),
        "projected_width_blocks": round(width_blocks, 2),
        "projected_height_blocks": round(height_blocks, 2),
        "height_over_width": round(height_blocks / width_blocks, 4),
        "screen_bbox_world_offsets": [round(lo_u, 3), round(lo_v, 3), round(hi_u, 3), round(hi_v, 3)],
        "crop_side_blocks": side,
        "margins_blocks_lr": round((side - width_blocks) / 2, 2),
        "margins_blocks_tb": round((side - height_blocks) / 2, 2),
        "margins_px_lr_at_1024": round((side - width_blocks) / 2 / side * 1024, 1),
        "margins_px_tb_at_1024": round((side - height_blocks) / 2 / side * 1024, 1),
    }


def main() -> int:
    log = json.loads((EVIDENCE / "search-log.json").read_text())
    scored = []
    for candidate in log["candidates"]:
        if not candidate.get("bbox"):
            continue
        entry = dict(candidate)
        entry["silhouette"] = silhouette(candidate["bbox"])
        scored.append(entry)
        sil = entry["silhouette"]
        print(f"{entry['label']:>26} pieces={entry.get('pieces'):>4} size={entry.get('size')} "
              f"proj={sil.get('projected_width_blocks')}x{sil.get('projected_height_blocks')} "
              f"h/w={sil.get('height_over_width')}", flush=True)
    (EVIDENCE / "search-analysis.json").write_text(json.dumps({"candidates": scored}, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
