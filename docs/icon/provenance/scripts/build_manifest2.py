"""Write captures-dungeon2 manifest.json, blockers.json and cleanup.json.

The manifest lists every delivered artifact with the round-3 contract fields
{project, label, path, method, source, notes}, `path` relative to round3/.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
from pathlib import Path

ART = Path(__file__).resolve().parent.parent
ROUND3 = ART.parent
EVIDENCE = ART / "evidence"
THEMES = [
    "cobblestone", "deepslate", "sculk", "desert_tomb", "lush", "dripstone", "frozen",
    "ocean_ruin", "amethyst_geode", "copper", "stronghold", "nether_wastes", "crimson_forest",
    "warped_forest", "basalt_deltas", "soul_sand_valley", "nether_fortress", "bastion",
    "end_stone", "end_city",
]


def rel(path: Path) -> str:
    return str(path.relative_to(ROUND3))


def entry(label: str, path: Path, method: str, source: str, notes: str = "") -> dict:
    return {"project": "ProceduralDungeon", "label": label, "path": rel(path),
            "method": method, "source": source, "notes": notes}


def main() -> int:
    manifest = []
    evidence_method = "Machine written evidence captured while producing the images"
    render_method = "Chunky 2.5.0-SNAPSHOT.478.g527cb4a, PARALLEL projection, equal-margin crop"

    manifest.append(entry("Seed search log: every candidate's RNG seed, bbox, size and timing",
                          EVIDENCE / "search-log.json", evidence_method, "captures-dungeon2 session"))
    manifest.append(entry("Seed search analysis: render-plane silhouette, squareness and margins per candidate",
                          EVIDENCE / "search-analysis.json", evidence_method, "captures-dungeon2 session"))
    manifest.append(entry("Seed report: candidates tried, bounding boxes, margins, winner and structural findings",
                          EVIDENCE / "seeds-report.json", evidence_method, "captures-dungeon2 session"))
    manifest.append(entry("Effective dungeon RNG seed per candidate, cross-checked against a JVM run of setLargeFeatureSeed",
                          EVIDENCE / "rng-seedcheck-java.txt", evidence_method, "captures-dungeon2 session"))
    manifest.append(entry("Per-theme generation log: piece counts, seconds, bounding boxes, copied regions",
                          EVIDENCE / "generation-log.json", evidence_method, "captures-dungeon2 session"))
    manifest.append(entry("Identical-geometry verification across the 20 themes (piece counts, silhouettes, voxel diff)",
                          EVIDENCE / "geometry-verification.json", evidence_method, "captures-dungeon2 session"))
    manifest.append(entry("Chunky render log: camera model, content boxes, crop boxes and measured margins per theme",
                          EVIDENCE / "chunky-render-log.json", evidence_method, "captures-dungeon2 session",
                          "re-render of the 20 themes + the empty-sky reference with the approved settings "
                          "(1600 px, 64 spp, 8 render threads); the pruned approved-run log is kept separately"))
    manifest.append(entry("Chunky render log of the approved run (provenance copy, frames were pruned)",
                          EVIDENCE / "chunky-render-log-approved-run.json", evidence_method,
                          "captures-dungeon2 session",
                          "describes the crops the primary composite was built from; kept because the re-render "
                          "rewrites evidence/chunky-render-log.json"))
    manifest.append(entry("Ordering report: theme colours, the three round-3 wedge orderings, the contrast "
                          "objective result and the dimension grouping (sector order, biome sort keys, "
                          "per-sector theme lists, sector angles and crop-margin verification)",
                          EVIDENCE / "ordering-report.json", evidence_method, "captures-dungeon2 session"))
    manifest.append(entry("Step-by-step search driver log",
                          EVIDENCE / "search-progress.log", evidence_method, "captures-dungeon2 session"))
    for name, label in (("search-batch.log", "Search run stdout (35 candidates)"),
                        ("analysis-batch.log", "Candidate silhouette analysis stdout"),
                        ("themes-batch.log", "20-theme generation stdout"),
                        ("render-batch.log", "20-theme Chunky render stdout (re-render of the 20 themes)"),
                        ("render-batch-approved-run.log", "20-theme Chunky render stdout of the approved run "
                                                          "(provenance copy)")):
        if (EVIDENCE / name).exists():
            manifest.append(entry(label, EVIDENCE / name, evidence_method, "captures-dungeon2 session"))
    manifest.append(entry("World-load navigation record for the isolated client",
                          EVIDENCE / "world-load-navigation.json", evidence_method, "captures-dungeon2 session"))
    manifest.append(entry("Runtime/isolated-environment setup record",
                          EVIDENCE / "runtime-setup.json", evidence_method, "captures-dungeon2 session"))
    manifest.append(entry("Final capture world setup record",
                          EVIDENCE / "final-world-setup.json", evidence_method, "captures-dungeon2 session"))
    if (EVIDENCE / "cleanup.json").exists():
        manifest.append(entry("Runtime cleanup record: isolated sink unloaded, client and Xvfb stopped",
                              EVIDENCE / "cleanup.json", evidence_method, "captures-dungeon2 session"))
    if (EVIDENCE / "cleanup-render-rerun.json").exists():
        manifest.append(entry("Cleanup record of the dimension-composite rerun: no display, sink or server "
                              "started, render processes gone, CPU policy honoured",
                              EVIDENCE / "cleanup-render-rerun.json", evidence_method,
                              "captures-dungeon2 session"))
    for theme in THEMES:
        regions = ART / "worlds" / theme / "region"
        if regions.exists():
            manifest.append(entry(f"{theme}: saved dungeon regions of the rendered world copy", regions,
                                  "Minecraft 26.2 region files written by the isolated client capture",
                                  "captures-dungeon2 session"))
    search_world = ART / "work" / "search-world"
    if search_world.exists():
        manifest.append(entry("Search world: the 35 candidate dungeons of the seed search (region data)",
                              search_world / "dimensions" / "minecraft" / "overworld" / "region",
                              "Minecraft 26.2 region files of the seed-search world",
                              "captures-dungeon2 session"))

    for theme in THEMES:
        crop = ART / "renders" / "crops" / f"{theme}-chunky-ortho-1024.png"
        if crop.exists():
            manifest.append(entry(f"{theme}: equal-margin Chunky crop (1024 px)", crop, render_method,
                                  "captures-dungeon2 session",
                                  "re-rendered from worlds/<theme> with the settings of the approved run"))
    for theme in THEMES:
        frame = ART / "renders" / "frames" / f"{theme}.png"
        if frame.exists():
            manifest.append(entry(f"{theme}: full 1600 px Chunky frame (uncropped)", frame, render_method,
                                  "captures-dungeon2 session"))
    empty = ART / "renders" / "frames" / "_empty.png"
    if empty.exists():
        manifest.append(entry("Empty-sky reference frame used for the content bounding boxes", empty, render_method,
                              "captures-dungeon2 session"))

    for name, label in (("theme", "Radial composite, round-3 theme order"),
                        ("rainbow", "Radial composite, rainbow order by theme colour"),
                        ("contrast", "Radial composite, max-contrast cycle order")):
        path = ART / "renders" / "composite" / f"proceduraldungeon-all20-radial-{name}.png"
        if path.exists():
            manifest.append(entry(label, path,
                                  "20 equal 18-degree wedges, clockwise from the top, direct pixel copies",
                                  "captures-dungeon2 session"))
    for name, label in (("proceduraldungeon-all20-contact-sheet.png",
                         "Contact sheet of all 20 themes in theme order"),
                        ("proceduraldungeon-all20-ordering-comparison.png",
                         "Side-by-side comparison of the three wedge orderings")):
        path = ART / "renders" / "composite" / name
        if path.exists():
            manifest.append(entry(label, path, "Labelled PIL contact sheet", "captures-dungeon2 session"))
    for name, label in (
            ("proceduraldungeon-all20-radial-dimension-equalthirds.png",
             "Dimension composite, equal thirds (Overworld 120 deg top-left, End 120 deg top-right, "
             "Nether 120 deg bottom; 10.909/17.143/60 deg wedges inside a sector)"),
            ("proceduraldungeon-all20-radial-dimension-proportional.png",
             "Dimension composite, sectors proportional to the theme counts (198/126/36 deg, every "
             "theme an 18 deg wedge; minimax-rotated bisector placement)"),
            ("proceduraldungeon-all20-dimension-legend.png",
             "Legend for the dimension grouping: sector diagram, both rings and the per-group theme "
             "order with biome, layer and wedge widths")):
        path = ART / "renders" / "composite" / name
        if path.exists():
            manifest.append(entry(label, path,
                                  "Rotationally sliced wedge ring, direct pixel copies of the 20 "
                                  "equal-margin Chunky crops (same construction as the primary composite)",
                                  "captures-dungeon2 session",
                                  "ordered by the dimension grouping in evidence/ordering-report.json"))

    for script in sorted((ART / "scripts").glob("*.py")):
        manifest.append(entry(f"Script: {script.name}", script,
                              "Reproducible pipeline script", "captures-dungeon2 session"))
    for script in sorted((ART / "scripts").glob("*.sh")):
        manifest.append(entry(f"Script: {script.name}", script,
                              "Reproducible pipeline script", "captures-dungeon2 session"))

    (ART / "manifest.json").write_text(json.dumps({"entries": manifest}, indent=1))

    report = json.loads((EVIDENCE / "seeds-report.json").read_text()) if (EVIDENCE / "seeds-report.json").exists() else {}
    winner = report.get("winner", {})
    blockers = []
    rendered = report.get("rendered_margins", {})
    if winner and winner.get("squareness_error_blocks", 1) > 0:
        blockers.append({
            "project": "ProceduralDungeon",
            "requirement": ("content bounding box exactly square, so that the left/right and the top/bottom crop "
                            "margins are identical to each other (not only equal within each axis)"),
            "status": (f"not reached by any of the {len(report.get(chr(99)+chr(97)+chr(110)+chr(100)+chr(105)+chr(100)+chr(97)+chr(116)+chr(101)+chr(115), []))} measured candidates; the closest one was rendered.  Within each axis "
                       "the margins are exactly equal (left == right, top == bottom)"),
            "evidence": [
                f"winner {winner.get('label')}: projected {winner.get('projected_width_blocks')} x "
                f"{winner.get('projected_height_blocks')} blocks, |width - height| = {winner.get('squareness_error_blocks')} blocks",
                f"predicted margins at 1024 px: left/right {winner.get('margins_px_at_1024', {}).get('left_right_px')}, "
                f"top/bottom {winner.get('margins_px_at_1024', {}).get('top_bottom_px')}",
                (f"rendered margins over {rendered.get('themes')} themes: left {rendered.get('left')}, "
                 f"right {rendered.get('right')}, top {rendered.get('top')}, bottom {rendered.get('bottom')}"
                 if rendered else "rendered margins: see evidence/chunky-render-log.json"),
                "evidence/seeds-report.json lists every candidate's projected size and margins; the search can be "
                "extended by adding candidates (scripts/drive_search.py candidates())",
            ],
        })
    grouping = json.loads((EVIDENCE / "ordering-report.json").read_text()).get("dimension_grouping") \
        if (EVIDENCE / "ordering-report.json").exists() else None
    if grouping:
        proportional = grouping["layouts"]["proportional"]
        alternatives = proportional["anchoring_alternatives"]
        blockers.append({
            "project": "ProceduralDungeon",
            "requirement": ("dimension sectors placed exactly on the named directions (Overworld top-left, "
                            "End top-right, Nether bottom) in the proportional ring as well"),
            "status": ("not reachable by construction; the minimax rotation that comes closest is used, so in "
                       "the proportional ring the sector centres sit 15-21 deg off the directions the equal-"
                       "thirds ring hits exactly"),
            "evidence": [
                "the bisector gap of two adjacent sectors is fixed at (width_i + width_j)/2, so the three "
                "bisectors can only be 120 deg apart (the equal-thirds directions) when all sectors are 120 deg; "
                "with 198/36/126 deg the gaps are 117/81/162 deg",
                f"chosen rotation: start {proportional['start_deg']} deg -> bisectors "
                f"{proportional['bisectors_deg']}, deviations "
                f"{proportional['deviation_from_equal_thirds_deg']}, worst case "
                f"{proportional['max_abs_deviation_deg']} deg",
                f"alternative anchor 'nether bisector at 180': worst case "
                f"{alternatives['nether_bisector_anchored_at_180_deg']['max_abs_deviation_deg']} deg; "
                f"alternative anchor 'overworld bisector at 300': worst case "
                f"{alternatives['overworld_bisector_anchored_at_300_deg']['max_abs_deviation_deg']} deg",
                "the equal-thirds ring (proceduraldungeon-all20-radial-dimension-equalthirds.png) places all "
                "three sectors exactly on the named directions; both rings are delivered",
            ],
        })
    (ART / "blockers.json").write_text(json.dumps(blockers, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
