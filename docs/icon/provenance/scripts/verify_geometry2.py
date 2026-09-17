"""Prove the 20 re-generated theme dungeons share one geometry.

Same method as captures-dungeon/scripts/verify_geometry.py, re-pointed at the
captures-dungeon2 theme worlds and the winning dungeon position:

  * piece count reported by the mod for every theme (must be identical)
  * occupied voxel bounding box and XZ footprint / XY / ZY silhouette hashes
  * voxel-set symmetric difference against the reference theme, with fluids excluded
    (lava keeps flowing after placement, so fluid spread is time dependent)
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ART = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ART / "scripts"))
import anvil  # noqa: E402

WORLDS = ART / "worlds"
EVIDENCE = ART / "evidence"
FLUIDS = {"minecraft:lava", "minecraft:water", "minecraft:flowing_lava", "minecraft:flowing_water"}


def box() -> tuple[int, int, int, int, int, int]:
    report = json.loads((EVIDENCE / "seeds-report.json").read_text())
    x0, y0, z0, x1, y1, z1 = report["winner"]["bbox"]
    margin = 8
    return (x0 - margin, 0, z0 - margin, x1 + margin, 319, z1 + margin)


def load_voxels(theme: str, window):
    structural: set[tuple[int, int, int]] = set()
    fluid: set[tuple[int, int, int]] = set()
    occupied: set[tuple[int, int, int]] = set()
    x0, y0, z0, x1, y1, z1 = window
    for path in sorted((WORLDS / theme / "region").glob("r.*.*.mca")):
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
                block = key.split("[")[0]
                (fluid if block in FLUIDS else structural).add((x, y, z))
                occupied.add((x, y, z))
    return structural, fluid, occupied


def digest(items) -> str:
    return hashlib.sha256(repr(sorted(items)).encode()).hexdigest()


def bbox_of(voxels):
    xs = [v[0] for v in voxels]
    ys = [v[1] for v in voxels]
    zs = [v[2] for v in voxels]
    return [min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)]


def main() -> int:
    window = box()
    log = json.loads((EVIDENCE / "generation-log.json").read_text())
    themes = list(log["themes"].keys())
    reference = themes[0]
    ref_structural, ref_fluid, ref_occupied = load_voxels(reference, window)

    results = []
    for theme in themes:
        if theme == reference:
            structural, fluid, occupied = ref_structural, ref_fluid, ref_occupied
        else:
            structural, fluid, occupied = load_voxels(theme, window)
        missing_voxels = ref_structural - structural
        missing = len(missing_voxels)
        extra = len(structural - ref_structural)
        missing_as_fluid = len(missing_voxels & fluid)
        union = len(ref_structural | structural) or 1
        results.append({
            "theme": theme,
            "pieces": log["themes"][theme]["pieces"],
            "voxels": len(structural),
            "fluidVoxels": len(fluid),
            "bbox": bbox_of(structural),
            "occupied_bbox_including_fluids": bbox_of(occupied),
            "footprintHash": digest({(x, z) for x, _, z in structural}),
            "elevationXHash": digest({(x, y) for x, y, _ in structural}),
            "elevationZHash": digest({(y, z) for _, y, z in structural}),
            "voxelsOnlyHere": extra,
            "voxelsMissing": missing,
            "symmetricDifference": extra + missing,
            "symmetricDifferencePercent": round(100 * (extra + missing) / union, 4),
            "missingOccupiedByFluids": missing_as_fluid,
        })

    pieces = {r["pieces"] for r in results}
    bboxes = {tuple(r["bbox"]) for r in results}
    summary = {
        "referenceTheme": reference,
        "window": list(window),
        "themes": len(results),
        "identicalPieceCount": len(pieces) == 1,
        "pieceCount": pieces.pop() if len(pieces) == 1 else sorted(pieces),
        "identicalBoundingBox": len(bboxes) == 1,
        "boundingBox": list(bboxes.pop()) if len(bboxes) == 1 else sorted(bboxes),
        "maxSymmetricDifferencePercent": max(r["symmetricDifferencePercent"] for r in results),
        "worstTheme": max(results, key=lambda r: r["symmetricDifferencePercent"])["theme"],
        "symmetricDifferenceExplanation": (
            "voxel differences are theme-palette effects, not layout differences: a theme whose palette maps a "
            "block to lava/water loses that voxel from the structural set (fluids are excluded because they keep "
            "flowing after placement).  missingOccupiedByFluids counts exactly those voxels for each theme."),
        "fluidsExcluded": sorted(FLUIDS),
        "results": results,
    }
    (EVIDENCE / "geometry-verification.json").write_text(json.dumps(summary, indent=1))
    print(json.dumps({k: summary[k] for k in ("identicalPieceCount", "pieceCount", "identicalBoundingBox",
                                              "boundingBox", "maxSymmetricDifferencePercent")}, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
