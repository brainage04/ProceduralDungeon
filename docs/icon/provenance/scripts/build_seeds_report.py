"""Pick the winning dungeon RNG seed and write evidence/seeds-report.json.

Winner criterion (stated, deterministic): the candidate whose silhouette is the most
square in the render image plane - smallest |projected width - projected height|
(0 = the content crop then has identical left/right and top/bottom margins).  Ties
break on the larger silhouette area, then on the candidate label.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ART = Path(__file__).resolve().parent.parent
EVIDENCE = ART / "evidence"
MARGIN_FRAC = 0.07
OUT_SIZE = 1024


def margins(sil: dict, out_size: int = OUT_SIZE) -> dict:
    width = sil["projected_width_blocks"]
    height = sil["projected_height_blocks"]
    span = max(width, height)
    side = span + 2 * round(MARGIN_FRAC * span)
    scale = out_size / side
    return {
        "left_right_px": round((side - width) / 2 * scale, 1),
        "top_bottom_px": round((side - height) / 2 * scale, 1),
        "side_px_at_1024": round(side * scale, 1),
    }


def signed(value: int) -> int:
    return value - (1 << 64) if value >= (1 << 63) else value


def round3_rendered_margins() -> dict | None:
    """Margins of the round-3 renders, read from captures-chunky's own render log."""
    path = ART.parent / "captures-chunky" / "evidence" / "chunky-render-log.json"
    if not path.exists():
        return None
    log = json.loads(path.read_text())
    crops = [entry["crop"]["margins_px_in_output"] for entry in log.get("themes", {}).values()
             if entry.get("crop")]
    if not crops:
        return None
    return {
        "themes": len(crops),
        "left": [min(c["left"] for c in crops), max(c["left"] for c in crops)],
        "right": [min(c["right"] for c in crops), max(c["right"] for c in crops)],
        "top": [min(c["top"] for c in crops), max(c["top"] for c in crops)],
        "bottom": [min(c["bottom"] for c in crops), max(c["bottom"] for c in crops)],
        "source": "captures-chunky/evidence/chunky-render-log.json",
    }


def main() -> int:
    log = json.loads((EVIDENCE / "search-log.json").read_text())
    analysis = json.loads((EVIDENCE / "search-analysis.json").read_text())
    by_label = {c["label"]: c for c in analysis["candidates"]}

    candidates = []
    for entry in log["candidates"]:
        scored = by_label.get(entry["label"])
        if not scored:
            continue
        sil = scored["silhouette"]
        candidates.append({
            "label": entry["label"],
            "world_seed": entry.get("world_seed"),
            "chunk_x": entry["chunk_x"],
            "chunk_z": entry["chunk_z"],
            "start_y": entry["start_y"],
            "tier": entry.get("tier", 5),
            "depth": entry.get("depth", 20),
            "theme_for_measurement": "cobblestone",
            "rng_seed": signed(entry["rng_seed"]),
            "pieces": entry.get("pieces"),
            "bbox": entry.get("bbox"),
            "size_blocks": entry.get("size"),
            "non_air_blocks": entry.get("blocks"),
            "projected_width_blocks": sil["projected_width_blocks"],
            "projected_height_blocks": sil["projected_height_blocks"],
            "height_over_width": sil["height_over_width"],
            "margins_px_at_1024": margins(sil),
            "control": entry.get("control", ""),
            "seconds": entry.get("seconds"),
        })

    for c in candidates:
        c["squareness_error_blocks"] = round(abs(c["projected_width_blocks"] - c["projected_height_blocks"]), 3)
    # a candidate measured while another dungeon of the same world sits inside its
    # measurement window cannot be scored: the silhouette is a union of two layouts
    contaminated: set[str] = set()
    overlaps = []
    for i, a in enumerate(candidates):
        for b in candidates[i + 1:]:
            ax0, ay0, az0, ax1, ay1, az1 = a["bbox"]
            bx0, by0, bz0, bx1, by1, bz1 = b["bbox"]
            if ax0 <= bx1 and bx0 <= ax1 and az0 <= bz1 and bz0 <= az1 and ay0 <= by1 and by0 <= ay1:
                contaminated.update((a["label"], b["label"]))
                overlaps.append([a["label"], b["label"]])
    for c in candidates:
        c["measurable"] = c["label"] not in contaminated
    scoreable = [c for c in candidates if c["measurable"]]
    ranked = sorted(scoreable, key=lambda c: (c["squareness_error_blocks"],
                                              -(c["projected_width_blocks"] * c["projected_height_blocks"]),
                                              c["label"]))
    winner = dict(ranked[0])
    winner["position"] = [winner["chunk_x"] * 16 + 0.5, float(winner["start_y"]), winner["chunk_z"] * 16 + 0.5]

    heights = [c["size_blocks"][1] for c in candidates if c.get("size_blocks")]
    footprint = [c["size_blocks"][0] for c in candidates if c.get("size_blocks")]
    report = {
        "method": {
            "world_seed": 20260910,
            "rng_derivation": ("the command builds Structure.GenerationContext(seed=level seed, chunkPos=chunk of "
                               "the command position), whose WorldgenRandom is seeded with "
                               "setLargeFeatureSeed(seed, chunkX, chunkZ) - captures/generation-seed-bytecode.txt; "
                               "a candidate is therefore an independent layout RNG realized at "
                               "/execute position chunk (cx, cz)"),
            "command_per_candidate": "/tp @s <cx*16+0.5> <start_y> <cz*16+0.5> 0 0 then /generatedungeon cobblestone <tier> <depth>",
            "measurement": ("after /generatedungeonstatus drains the staged job the world is saved through the pause "
                            "menu and the occupied voxels are read back from the saved region files inside a "
                            "+-100 block window (scripts/analyze_candidates.py)"),
            "control_group": ("chunk (0,0) at start heights 150/200/250 was meant to isolate the placement height, but "
                              "those three dungeons share one chunk and therefore overlap in the saved world: their "
                              "measurements are unions of two/three layouts and are reported as contaminated and "
                              "excluded from the ranking (evidence: overlapping_dungeons).  The tier/depth candidates "
                              "use their own chunk each and are clean."),
            "winner_criterion": ("smallest |projected width - projected height| of the dungeon silhouette in the "
                                 "render image plane (MC yaw 45 / pitch 30); the crop margins are equal exactly when "
                                 "that difference is 0"),
        },
        "baseline_round3": {
            "description": ("round-3 dungeon (chunk 0,0, y=200, tier 5 depth 20): the three same-chunk control "
                            "dungeons generated later in this world overlap it, so its re-measurement here is a union; "
                            "the honest baseline is the round-3 render log itself"),
            "candidate": next((c for c in candidates if c["label"].startswith("cx0cz0y200")), None),
            "rendered_margins": round3_rendered_margins(),
        },
        "candidates": candidates,
        "ranking": [c["label"] for c in ranked],
        "overlapping_dungeons": overlaps,
        "contaminated_candidates": sorted(contaminated),
        "winner": winner,
        "findings": {
            "vertical_extent_blocks": {"min": min(heights), "max": max(heights)} if heights else None,
            "footprint_blocks_x": {"min": min(footprint), "max": max(footprint)} if footprint else None,
            "vertical_bound": ("the jigsaw expansion is bounded by JigsawStructure.MaxDistance(horizontal, vertical) "
                               "= tier.maxDistanceFromCenter for both axes (tiers.json tier 5 = 72; MaxDistance(int) "
                               "sets both fields, verified with javap on the 26.2 merged jar), so the vertical extent "
                               "is capped at 145 blocks around the placement height"),
            "top_level": ("the canonical room pool weights the downward staircases 16+16 against 1+1 upward, so most "
                          "layouts sink to the bottom bound (~77-80 blocks tall); seeds whose graph also climbs "
                          "above the start reach 90-135 blocks, which is what the search selects"),
            "margins_definition": ("crop margins are computed by the same rule as captures-chunky "
                                   "(square crop of span + 2*7% margin, rescaled to 1024 px)"),
        },
    }
    render_log_path = EVIDENCE / "chunky-render-log.json"
    if render_log_path.exists():
        render_log = json.loads(render_log_path.read_text())
        crops = {theme: entry["crop"] for theme, entry in render_log.get("themes", {}).items()
                 if entry.get("crop")}
        if crops:
            def spread(key):
                values = [c["margins_px_in_output"][key] for c in crops.values()]
                return {"min": min(values), "max": max(values)}
            content = [c["content_wh_px"] for c in crops.values()]
            report["rendered_margins"] = {
                "themes": len(crops),
                "content_wh_px": {"min": [min(c[0] for c in content), min(c[1] for c in content)],
                                  "max": [max(c[0] for c in content), max(c[1] for c in content)]},
                "left": spread("left"), "right": spread("right"),
                "top": spread("top"), "bottom": spread("bottom"),
                "per_theme": {theme: c["margins_px_in_output"] for theme, c in sorted(crops.items())},
                "equality": ("left/right and top/bottom are equal up to the 1 px integer parity of the content "
                             "extents (a square crop can only give identical margins when the content width and "
                             "height have the same parity); the crop is never clamped to the frame, so the margins "
                             "are exact"),
            }
    (EVIDENCE / "seeds-report.json").write_text(json.dumps(report, indent=1))
    print(json.dumps({"winner": winner["label"], "h/w": winner["height_over_width"],
                      "margins_px_at_1024": winner["margins_px_at_1024"],
                      "candidates": len(candidates)}, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
