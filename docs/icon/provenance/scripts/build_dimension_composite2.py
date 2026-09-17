"""Build the dimension-grouped radial composites (equal thirds + proportional) + legend.

Wedge construction is unchanged from build_composite2.py: every pixel of the ring is a
direct copy of the same-coordinate pixel of that theme's own equal-margin Chunky crop
(renders/crops/<theme>-chunky-ortho-1024.png), so all 20 wedges show the identical
dungeon geometry at the identical scale.  The round-3-order ring stays the primary
composite and is not rebuilt here.

Grouping (reported in evidence/ordering-report.json -> "dimension_grouping"):

  sector order, clockwise   Overworld -> End -> Nether
  placement                 Overworld top-left, End top-right, Nether bottom
  within a sector           a documented biome progression per dimension, see GROUPS:
                            Overworld  biome layer depth, shallowest first
                            Nether     ashen floor -> fungal forests -> volcanic -> built
                            End        natural biome -> structure

Layouts:

  equal_thirds   120 deg per sector, every theme of a sector gets 120/count degrees
                 (Overworld 10.909, Nether 17.143, End 60); sector bisectors 300/60/180
                 deg, the directions the group names refer to
  proportional   sector widths proportional to the theme counts (11/7/2 -> 198/126/36
                 deg, so every theme keeps the 18 deg wedge width of the primary ring);
                 the packing is rotated by the angle that minimises the largest
                 deviation of the three bisectors from the equal-thirds directions
                 (solved below, not hand-tuned)

usage: build_dimension_composite2.py [--suffix chunky-ortho-1024.png]
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

PILLOW_SITE = "/nix/store/4v9j9wbzyhrlx9980ygbr812313mazy0-python3.13-pillow-12.3.0/lib/python3.13/site-packages"
if PILLOW_SITE not in sys.path:  # Pillow is not on the default path in this image
    sys.path.append(PILLOW_SITE)


from PIL import Image, ImageDraw, ImageFont  # noqa: E402

ART = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ART / "scripts"))
import build_composite2 as base  # noqa: E402  (reuse label(), source(), theme_colour())

CROPS = ART / "renders" / "crops"
COMPOSITE = ART / "renders" / "composite"
EVIDENCE = ART / "evidence"
FONT = base.FONT
SUFFIX = base.SUFFIX
RING_PX = 1024

# ------------------------------------------------------------------ the grouping
# ("theme", "biome the theme is built from", "layer / progression step")
GROUPS = {
    "overworld": {
        "label": "Overworld",
        "placement": "top-left",
        "sort_key": "biome layer depth, shallowest first (surface biome -> sea floor -> "
                    "cave layer -> stone layer -> ore/geode -> deep stone -> deep dark)",
        "themes": [
            ("desert_tomb", "desert", "surface layer, y 63-70"),
            ("frozen", "snowy plains / ice spikes", "surface layer, y 62-70"),
            ("ocean_ruin", "ocean ruin on the sea floor", "sea floor, y 40-62"),
            ("lush", "lush caves", "cave layer, y 0-40"),
            ("dripstone", "dripstone caves", "cave layer, y -10-40"),
            ("cobblestone", "stone layer (mountains, mineshafts)", "stone layer, y 0-60"),
            ("copper", "copper ore", "ore layer, dense below y 48"),
            ("amethyst_geode", "amethyst geode", "deep cave layer, y -60-30"),
            ("stronghold", "stronghold (stone brick)", "deep layer, y -40-0"),
            ("deepslate", "deepslate layer", "deep layer, y -64-0"),
            ("sculk", "deep dark (sculk)", "bottom layer, y -64 to -1"),
        ],
    },
    "nether": {
        "label": "Nether",
        "placement": "bottom",
        "sort_key": "named nether progression: barren wastes -> ashen valley -> warm fungal "
                    "forest -> cold fungal forest -> volcanic deltas -> built fortifications",
        "themes": [
            ("nether_wastes", "nether wastes", "barren baseline wasteland"),
            ("soul_sand_valley", "soul sand valley", "ashen floor, bone fossils"),
            ("crimson_forest", "crimson forest", "warm fungal forest"),
            ("warped_forest", "warped forest", "cold fungal forest"),
            ("basalt_deltas", "basalt deltas", "volcanic, basalt columns"),
            ("nether_fortress", "nether fortress", "built: nether brick"),
            ("bastion", "bastion remnant", "built: blackstone"),
        ],
    },
    "end": {
        "label": "End",
        "placement": "top-right",
        "sort_key": "named end progression: natural biome -> structure",
        "themes": [
            ("end_stone", "the end (outer islands)", "natural biome, end stone"),
            ("end_city", "end city", "built: purpur and end stone brick"),
        ],
    },
}

GROUP_ORDER = ("overworld", "end", "nether")  # clockwise on the ring
TARGETS = {"overworld": 300.0, "end": 60.0, "nether": 180.0}  # equal-thirds bisectors
LUT_STEPS = 36000  # 0.01 deg angle buckets


def themes_of(group: str) -> list[str]:
    return [theme for theme, _, _ in GROUPS[group]["themes"]]


def widths_equal_thirds() -> dict[str, float]:
    return {group: 360.0 / len(GROUP_ORDER) for group in GROUP_ORDER}


def widths_proportional() -> dict[str, float]:
    total = sum(len(themes_of(group)) for group in GROUP_ORDER)
    assert total == 20, total
    return {group: 360.0 * len(themes_of(group)) / total for group in GROUP_ORDER}


def pack(widths: dict[str, float], start: float,
         first: str = GROUP_ORDER[0]) -> dict[str, tuple[float, float]]:
    """Contiguous sectors clockwise from start; spans are [start, start+width).

    The sectors keep the cyclic order of GROUP_ORDER; `first` only selects which sector
    owns the given start angle.
    """
    order = GROUP_ORDER[GROUP_ORDER.index(first):] + GROUP_ORDER[:GROUP_ORDER.index(first)]
    spans, angle = {}, start
    for group in order:
        spans[group] = (angle % 360.0, widths[group])
        angle += widths[group]
    return spans


def bisector(span: tuple[float, float]) -> float:
    return (span[0] + span[1] / 2) % 360.0


def circ_diff(a: float, b: float) -> float:
    """Signed smallest difference a - b on the circle, in (-180, 180]."""
    return (a - b + 180.0) % 360.0 - 180.0


def solve_start(widths: dict[str, float], step: float = 0.02) -> dict:
    """Rotation whose packing minimises the worst bisector deviation from TARGETS.

    Objective (minimax, ties broken by the summed squared deviation) is scanned on a
    fixed 0.02 deg grid so the result is reproducible; no hand-tuned constant.
    """
    best = None
    steps = int(round(360.0 / step))
    for index in range(steps):
        start = index * step
        spans = pack(widths, start)
        deviations = {g: round(circ_diff(bisector(spans[g]), TARGETS[g]), 6) for g in GROUP_ORDER}
        key = (round(max(abs(d) for d in deviations.values()), 6),
               round(sum(d * d for d in deviations.values()), 6))
        if best is None or key < best["key"]:
            best = {"key": key, "start_deg": round(start, 4), "spans": spans,
                    "deviations_deg": deviations}
    spans = {g: (round(s, 4), round(w, 4)) for g, (s, w) in best["spans"].items()}
    bisectors = {g: round(bisector(spans[g]), 4) for g in GROUP_ORDER}
    wedge = {g: {theme: round(spans[g][1] / len(themes_of(g)), 4) for theme in themes_of(g)}
             for g in GROUP_ORDER}
    return {
        "start_deg": best["start_deg"],
        "spans_deg": {g: list(spans[g]) for g in GROUP_ORDER},
        "bisectors_deg": bisectors,
        "deviation_from_equal_thirds_deg": best["deviations_deg"],
        "max_abs_deviation_deg": best["key"][0],
        "sum_squared_deviation_deg2": best["key"][1],
        "wedge_degrees": wedge,
    }


def layout_equal_thirds() -> dict:
    widths = widths_equal_thirds()
    spans = pack(widths, 360.0 - widths["overworld"])  # Overworld starts at 240 deg
    spans = {g: (round(s, 4), round(w, 4)) for g, (s, w) in spans.items()}
    return {
        "rule": "three 120 deg sectors; inside a sector every theme gets an equal share "
                "of the sector (proportional to the theme count)",
        "spans_deg": {g: list(spans[g]) for g in GROUP_ORDER},
        "bisectors_deg": {g: round(bisector(spans[g]), 4) for g in GROUP_ORDER},
        "sector_degrees": {g: spans[g][1] for g in GROUP_ORDER},
        "wedge_degrees": {g: {theme: round(spans[g][1] / len(themes_of(g)), 4)
                              for theme in themes_of(g)} for g in GROUP_ORDER},
    }


def deviation_profile(spans: dict[str, tuple[float, float]]) -> dict:
    """Bisector directions of a packing and their deviation from TARGETS."""
    deviations = {g: round(circ_diff(bisector(spans[g]), TARGETS[g]), 4) for g in GROUP_ORDER}
    return {
        "bisectors_deg": {g: round(bisector(spans[g]), 4) for g in GROUP_ORDER},
        "deviation_deg": deviations,
        "max_abs_deviation_deg": round(max(abs(v) for v in deviations.values()), 4),
    }


def layout_proportional() -> dict:
    widths = widths_proportional()
    solved = solve_start(widths)
    solved["rule"] = ("sector widths proportional to the theme counts (11/7/2 of 360 deg); "
                      "contiguous in the same clockwise order as the equal-thirds ring, "
                      "rotated to the minimax-optimal bisector placement")
    solved["sector_degrees"] = {g: round(widths[g], 4) for g in GROUP_ORDER}
    # the two obvious anchors, reported so the choice of criterion is auditable: the
    # bisector gaps of an unequal packing are fixed at (w_i + w_j)/2, so no rotation can
    # put all three bisectors on the equal-thirds directions at the same time
    solved["anchoring_alternatives"] = {
        "nether_bisector_anchored_at_180_deg": deviation_profile(
            pack(widths, 180.0 - widths["nether"] / 2, first="nether")),
        "overworld_bisector_anchored_at_300_deg": deviation_profile(
            pack(widths, 300.0 - widths["overworld"] / 2)),
    }
    return solved


# ------------------------------------------------------------------ ring


def ring_lut(spans: dict[str, tuple[float, float]]) -> list[str]:
    """Theme name for every angle bucket (0 deg = up, clockwise)."""
    lut = []
    for index in range(LUT_STEPS):
        angle = index * 360.0 / LUT_STEPS
        for group in GROUP_ORDER:
            start, width = spans[group]
            offset = (angle - start) % 360.0
            if offset < width:
                themes = themes_of(group)
                lut.append(themes[min(int(offset / (width / len(themes))), len(themes) - 1)])
                break
        else:  # pragma: no cover - the widths sum to 360, so every angle is covered
            raise AssertionError(f"angle {angle} is not inside any sector")
    return lut


def build_ring(spans: dict[str, tuple[float, float]], out: Path, suffix: str = SUFFIX,
               size: int = RING_PX) -> Path:
    images, sources = {}, {}
    for group in GROUP_ORDER:
        for theme in themes_of(group):
            image = Image.open(base.source(theme, suffix)).convert("RGB")
            if image.size != (size, size):
                image = image.resize((size, size), Image.LANCZOS)
            images[theme] = image
            sources[theme] = image.load()
    lut = ring_lut(spans)
    canvas = Image.new("RGB", (size, size))
    pixels = canvas.load()
    centre = size / 2
    for y in range(size):
        dy = y + 0.5 - centre
        for x in range(size):
            angle = math.degrees(math.atan2(x + 0.5 - centre, -dy)) % 360.0
            pixels[x, y] = sources[lut[int(angle * LUT_STEPS / 360.0) % LUT_STEPS]][x, y]
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out)
    return out


# ------------------------------------------------------------------ legend


def group_colour(colours: dict, group: str) -> tuple[int, int, int]:
    themes = themes_of(group)
    return tuple(int(round(sum(colours[t]["rgb"][c] for t in themes) / len(themes)))
                 for c in range(3))


def schematic(spans: dict[str, tuple[float, float]], colours: dict, size: int = 300,
              title: str = "proportional ring") -> Image.Image:
    """Pie diagram of the sector spans, labelled with the group name and its width.

    Labels of narrow sectors are placed outside the circle and clamped to the canvas, so
    no text is ever cut off by the image border.
    """
    scale = 4  # draw big, downsample for smooth edges
    big = size * scale
    image = Image.new("RGB", (big, big), (18, 21, 27))
    draw = ImageDraw.Draw(image)
    pad = 8 * scale
    for group in GROUP_ORDER:
        start, width = spans[group]
        draw.pieslice((pad, pad, big - pad, big - pad), start - 90.0, start + width - 90.0,
                      fill=group_colour(colours, group))
    draw.ellipse((pad, pad, big - pad, big - pad), outline=(12, 14, 18), width=2 * scale)
    for group in GROUP_ORDER:
        start = spans[group][0]
        draw.line((big / 2 + math.sin(math.radians(start)) * (big / 2 - pad),
                   big / 2 - math.cos(math.radians(start)) * (big / 2 - pad),
                   big / 2, big / 2), fill=(12, 14, 18), width=2 * scale)
    image = image.resize((size, size), Image.LANCZOS)
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(FONT, 14)
    small = ImageFont.truetype(FONT, 12)
    radius = size / 2 - pad / scale
    for group in GROUP_ORDER:
        start, width = spans[group]
        angle = math.radians(bisector((start, width)))
        text = f"{GROUPS[group]['label']} {width:.0f} deg"
        box = draw.textbbox((0, 0), text, font=font)
        text_w, text_h = box[2] - box[0], box[3] - box[1]
        inside = width >= 60
        reach = radius * (0.55 if inside else 1.0)
        cx = size / 2 + math.sin(angle) * reach
        cy = size / 2 - math.cos(angle) * reach
        x, y = cx - text_w / 2, cy - text_h / 2
        if not inside:  # keep the label on the canvas and reach it with a leader line
            x = min(max(2.0, x), size - text_w - 2.0)
            y = min(max(2.0, y), size - text_h - 2.0)
            draw.line((size / 2 + math.sin(angle) * (radius + 1),
                       size / 2 - math.cos(angle) * (radius + 1),
                       x + text_w / 2, y + text_h / 2), fill=(238, 241, 247), width=1)
        draw.text((x, y), text, fill=(255, 255, 255) if inside else (238, 241, 247),
                  font=font, align="center", stroke_width=3 if inside else 2,
                  stroke_fill=(12, 14, 18))
    draw.text((6, size - 18), title, fill=(160, 168, 180), font=small)
    return image


def build_legend(layouts: dict, colours: dict, out: Path, preview: int = 300) -> Path:
    padding, gap = 20, 24
    title_h = 122
    caption_h = 24
    header_h = 56
    row_h = 78
    thumb = 62
    footer_h = 46
    columns = 3
    width = 1360
    col_w = (width - 2 * padding - (columns - 1) * gap) // columns
    rows = max(len(themes_of(group)) for group in GROUP_ORDER)
    height = (padding + title_h + preview + caption_h + 18 + header_h + rows * row_h
              + footer_h + padding)
    sheet = Image.new("RGB", (width, height), (18, 21, 27))
    draw = ImageDraw.Draw(sheet)
    title = ImageFont.truetype(FONT, 24)
    subtitle = ImageFont.truetype(FONT, 14)
    header = ImageFont.truetype(FONT, 18)
    small = ImageFont.truetype(FONT, 12)
    name = ImageFont.truetype(FONT, 15)

    def draw_fitting(text: str, font, limit: float, xy, fill) -> None:
        measured = draw.textlength(text, font=font)
        if measured > limit:
            raise SystemExit(f"legend text does not fit ({measured:.0f} > {limit:.0f} px): {text!r}")
        draw.text(xy, text, fill=fill, font=font)

    draw_fitting("ProceduralDungeon - 20 themes grouped by dimension, then by biome",
                 title, width - 2 * padding, (padding, padding), (238, 241, 247))
    lines = [
        "sector order clockwise: Overworld (top-left) -> End (top-right) -> Nether (bottom)",
        "inside a sector: the documented biome progression of that dimension (listed per group)",
        "primary composite unchanged: proceduraldungeon-all20-radial-theme.png "
        "(round-3 theme order, 20 x 18 deg wedges)",
    ]
    for index, line in enumerate(lines):
        draw_fitting(line, subtitle, width - 2 * padding, (padding, padding + 36 + 20 * index),
                     (186, 194, 206))

    y = padding + title_h
    for index, key in enumerate(("equal_thirds", "proportional")):
        image = Image.open(ART / layouts[key]["path"]).convert("RGB").resize((preview, preview), Image.LANCZOS)
        x = padding + index * (preview + gap)
        sheet.paste(image, (x, y))
        caption = (f"equal thirds: {layouts['equal_thirds']['sector_degrees']['overworld']:.0f} deg "
                   f"per sector"
                   if key == "equal_thirds" else
                   "proportional: "
                   f"{layouts['proportional']['sector_degrees']['overworld']:.0f}/"
                   f"{layouts['proportional']['sector_degrees']['end']:.0f}/"
                   f"{layouts['proportional']['sector_degrees']['nether']:.0f} deg per sector")
        draw_fitting(caption, small, preview + gap - 8, (x, y + preview + 6), (206, 213, 224))
    sheet.paste(schematic(layouts["proportional"]["spans_deg"], colours, preview),
                (padding + 2 * (preview + gap), y))

    y += preview + caption_h + 18
    row_limit = col_w - thumb - 10
    for index, group in enumerate(GROUP_ORDER):
        x = padding + index * (col_w + gap)
        info = GROUPS[group]
        draw_fitting(f"{info['label'].upper()} - {len(themes_of(group))} themes",
                     header, row_limit, (x, y), (238, 241, 247))
        draw_fitting(f"sector {layouts['proportional']['sector_degrees'][group]:.0f} deg proportional | "
                     f"{layouts['equal_thirds']['sector_degrees'][group]:.0f} deg equal thirds",
                     small, col_w, (x, y + 24), (160, 168, 180))
        draw_fitting(f"centre {layouts['proportional']['bisectors_deg'][group]:.0f} deg, "
                     f"{info['placement']}",
                     small, col_w, (x, y + 38), (160, 168, 180))
        for row, (theme, biome, layer) in enumerate(info["themes"]):
            ry = y + header_h + row * row_h
            image = Image.open(base.source(theme)).convert("RGB").resize((thumb, thumb), Image.LANCZOS)
            sheet.paste(image, (x, ry))
            draw.rectangle((x, ry, x + thumb - 1, ry + thumb - 1), outline=(60, 66, 76))
            draw_fitting(f"{row + 1}. {base.label(theme)}", name, row_limit,
                         (x + thumb + 10, ry + 2), (238, 241, 247))
            draw_fitting(f"{biome} | {layer}", small, col_w, (x + thumb + 10, ry + 22),
                         (186, 194, 206))
            draw_fitting(f"{layouts['equal_thirds']['wedge_degrees'][group][theme]:.2f} deg equal thirds | "
                         f"{layouts['proportional']['wedge_degrees'][group][theme]:.2f} deg proportional",
                         small, col_w, (x + thumb + 10, ry + 40), (140, 148, 160))
            draw_fitting(f"hue {colours[theme]['hue_deg']:.0f} deg, mean rgb {tuple(colours[theme]['rgb'])}",
                         small, col_w, (x + thumb + 10, ry + 56), (140, 148, 160))

    draw_fitting("every wedge is a direct pixel copy of the same-coordinate pixels of that theme's "
                 "own equal-margin Chunky crop (1024 px); no blending, no scaling",
                 small, width - 2 * padding, (padding, height - footer_h + 6), (160, 168, 180))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return out


# ------------------------------------------------------------------ verification


def verify_ring(path: Path, spans: dict[str, tuple[float, float]], suffix: str,
                samples: int = 50000, seed: int = 20260914) -> dict:
    """Prove the ring is a direct pixel copy: re-derive the owner of sampled pixels.

    Deterministic (fixed seed) sampling over the whole square; every sample must equal
    the same-coordinate pixel of the owner theme's own crop.
    """
    import random

    with Image.open(path) as image:
        ring = image.convert("RGB")
        size = ring.size[0]
        ring_pixels = ring.load()
        owners = {theme: Image.open(base.source(theme, suffix)).convert("RGB").load()
                  for group in GROUP_ORDER for theme in themes_of(group)}
        rng = random.Random(seed)
        lut = ring_lut(spans)
        centre = size / 2
        mismatches = []
        for _ in range(samples):
            x, y = rng.randrange(size), rng.randrange(size)
            angle = math.degrees(math.atan2(x + 0.5 - centre, -(y + 0.5 - centre))) % 360.0
            theme = lut[int(angle * LUT_STEPS / 360.0) % LUT_STEPS]
            if ring_pixels[x, y] != owners[theme][x, y]:
                mismatches.append([x, y, theme, list(ring_pixels[x, y]), list(owners[theme][x, y])])
        return {
            "check": "sampled pixels must equal the same-coordinate pixel of the owner theme's crop",
            "samples": samples,
            "seed": seed,
            "mismatches": len(mismatches),
            "first_mismatches": mismatches[:5],
            "direct_pixel_copies": not mismatches,
        }


def verify_wedges(spans: dict[str, tuple[float, float]]) -> dict:
    """Check the ring's angle -> theme table against the documented wedge degrees.

    Exact (bucket resolution 0.01 deg): for every theme the buckets the ring assigns to it
    must be exactly the interval the report claims for it, inside its sector.
    """
    lut = ring_lut(spans)
    step = 360.0 / LUT_STEPS
    rows, failures = {}, []
    for group in GROUP_ORDER:
        start, width = spans[group]
        themes = themes_of(group)
        wedge = width / len(themes)
        for index, theme in enumerate(themes):
            buckets = [k for k, owner in enumerate(lut) if owner == theme]
            offsets = sorted((k * step - start) % 360.0 for k in buckets)
            measured = [round(offsets[0], 4), round(offsets[-1] + step, 4)]
            expected = [round(index * wedge, 4), round((index + 1) * wedge, 4)]
            inside = (abs(measured[0] - expected[0]) <= step + 1e-9
                      and abs(measured[1] - expected[1]) <= step + 1e-9)
            rows[theme] = {
                "sector": group,
                "wedge_degrees": round(wedge, 4),
                "measured_offset_deg": measured,
                "measured_bucket_count": len(buckets),
                "expected_offset_deg": expected,
                "inside_documented_wedge": inside,
            }
            if not inside:
                failures.append(f"{theme}: buckets cover {measured} but the report claims {expected}")
    if failures:
        raise SystemExit("wedge verification failed: " + "; ".join(failures))
    return {
        "check": "the ring's angle -> theme buckets are exactly the documented wedge intervals",
        "bucket_step_deg": step,
        "themes": rows,
        "failures": failures,
    }


def pixel_profile(path: Path, suffix: str, radius: int = 200, step_deg: float = 0.2) -> dict:
    """Informational: which themes are visible where, read back from the finished ring.

    A sample counts only when exactly one crop has that pixel value at that coordinate;
    samples with 0 or several matching crops are counted as ambiguous (sky pixels are
    identical in every crop, so an unambiguous sample is a visible structure pixel).
    Samples of the same theme are merged, so a run is the angular range in which that
    theme's pixels were uniquely identifiable.
    """
    with Image.open(path) as image:
        ring = image.convert("RGB")
        size = ring.size[0]
        ring_pixels = ring.load()
    crops = {theme: Image.open(base.source(theme, suffix)).convert("RGB").load()
             for group in GROUP_ORDER for theme in themes_of(group)}
    centre = size / 2
    runs, ambiguous, seen = [], 0, set()
    for index in range(int(round(360.0 / step_deg))):
        angle = round(index * step_deg, 4)
        x = int(round(centre + radius * math.sin(math.radians(angle))))
        y = int(round(centre - radius * math.cos(math.radians(angle))))
        value = ring_pixels[x, y]
        owners = [theme for theme, pixels in crops.items() if pixels[x, y] == value]
        if len(owners) != 1:
            ambiguous += 1
            continue
        seen.update(owners)
        if runs and runs[-1]["theme"] == owners[0]:
            runs[-1]["end_deg"] = angle
        else:
            runs.append({"theme": owners[0], "start_deg": angle, "end_deg": angle})
    return {
        "radius_px": radius,
        "step_deg": step_deg,
        "ambiguous_samples": ambiguous,
        "themes_seen": sorted(seen),
        "themes_seen_count": len(seen),
        "runs_deg": [r for r in runs if r["end_deg"] - r["start_deg"] >= 0.4],
    }


def verify_crops(suffix: str, log_path: Path) -> dict:
    """Every crop must exist, be RING_PX square and have equal margins within each axis."""
    themes = json.loads(log_path.read_text())["themes"]
    rows, failures = {}, []
    for group in GROUP_ORDER:
        for theme in themes_of(group):
            entry = themes.get(theme, {})
            crop = entry.get("crop")
            if not crop:
                failures.append(f"{theme}: no crop in {log_path.name}")
                continue
            margins = crop["margins_px_in_crop"]
            equal = margins["left"] == margins["right"] and margins["top"] == margins["bottom"]
            path = CROPS / f"{theme}-{suffix}"
            with Image.open(path) as image:
                size = list(image.size)
            rows[theme] = {
                "output": str(path.relative_to(ART)),
                "output_size": size,
                "crop_side_px": crop["crop_side_px"],
                "content_wh_px": crop["content_wh_px"],
                "margins_px_in_crop": margins,
                "margins_equal_within_axis": equal,
                "margins_equal_between_themes": None,
                "l_r_margin_equal_across_themes": None,
                "t_b_margin_equal_across_themes": None,
                "crop_clamped_to_frame": crop["crop_clamped_to_frame"],
            }
            if not equal:
                failures.append(f"{theme}: margins not equal within an axis: {margins}")
            if size != [RING_PX, RING_PX]:
                failures.append(f"{theme}: crop is {size}, expected {[RING_PX, RING_PX]}")
    sides = sorted({row["crop_side_px"] for row in rows.values()})
    lr = sorted({(row["margins_px_in_crop"]["left"], row["margins_px_in_crop"]["right"])
                 for row in rows.values()})
    tb = sorted({(row["margins_px_in_crop"]["top"], row["margins_px_in_crop"]["bottom"])
                 for row in rows.values()})
    for row in rows.values():
        row["margins_equal_between_themes"] = len(lr) == 1 and len(tb) == 1
        row["l_r_margin_equal_across_themes"] = len(lr) == 1
        row["t_b_margin_equal_across_themes"] = len(tb) == 1
    summary = {
        "checked": len(rows),
        "expected": 20,
        "all_margins_equal_within_axis": not any(not row["margins_equal_within_axis"] for row in rows.values()),
        "crop_side_px_values": sides,
        "identical_crop_side_across_themes": len(sides) == 1,
        "left_right_margins_px_values": [list(v) for v in lr],
        "top_bottom_margins_px_values": [list(v) for v in tb],
        "themes_padded_by_one_pixel": sorted(
            theme for theme, entry in themes.items()
            if entry.get("crop") and any(entry["crop"].get("square_crop_padding_px", [0, 0]))),
        "note": ("left == right and top == bottom for every theme.  The square side differs by "
                 "at most 2 px between themes (1470/1472 of the 1600 px frame, i.e. a 0.13% scale "
                 "difference) because the measured content box moves by one pixel with the render "
                 "noise at the silhouette edge - the approved run had the same 1256-1257 x "
                 "1290-1292 px spread.  Themes whose measured width and height have different "
                 "parity are padded by one pixel on the odd axis (chunky_render2.crop_equal_margins) "
                 "so that the margin pair of that axis stays exactly equal."),
        "failures": failures,
    }
    if failures:
        raise SystemExit("crop verification failed: " + "; ".join(failures))
    return {"summary": summary, "themes": rows}


# ------------------------------------------------------------------ report


def main(argv: list[str]) -> int:
    suffix = SUFFIX
    if "--suffix" in argv:
        suffix = argv[argv.index("--suffix") + 1]
    missing = [theme for group in GROUP_ORDER for theme in themes_of(group)
               if not base.source(theme, suffix).exists()]
    if missing:
        raise SystemExit(f"missing crops: {missing}")

    verification = verify_crops(suffix, EVIDENCE / "chunky-render-log.json")
    colours = {theme: base.theme_colour(theme) for group in GROUP_ORDER for theme in themes_of(group)}

    equal_thirds = layout_equal_thirds()
    proportional = layout_proportional()
    layouts = {
        "equal_thirds": equal_thirds,
        "proportional": proportional,
    }
    names = {
        "equal_thirds": "proceduraldungeon-all20-radial-dimension-equalthirds.png",
        "proportional": "proceduraldungeon-all20-radial-dimension-proportional.png",
    }
    for key in ("equal_thirds", "proportional"):
        spans = {g: (layouts[key]["spans_deg"][g][0], layouts[key]["spans_deg"][g][1])
                 for g in GROUP_ORDER}
        path = build_ring(spans, COMPOSITE / names[key], suffix=suffix)
        layouts[key]["path"] = str(path.relative_to(ART))
        layouts[key]["path_relative_to_round3"] = "captures-dungeon2/" + str(path.relative_to(ART))
        layouts[key]["size_px"] = RING_PX
        layouts[key]["self_check"] = verify_ring(path, spans, suffix)
        if not layouts[key]["self_check"]["direct_pixel_copies"]:
            raise SystemExit(f"{key}: ring is not a direct pixel copy of the crops")
        layouts[key]["wedge_check"] = verify_wedges(spans)
        layouts[key]["pixel_profile"] = pixel_profile(path, suffix)

    legend = build_legend(layouts, colours, COMPOSITE / "proceduraldungeon-all20-dimension-legend.png")

    render_log = json.loads((EVIDENCE / "chunky-render-log.json").read_text())
    render = render_log.get("render", {})
    settings = {
        "renderer": render_log.get("method"),
        "render_size_px": render.get("size_px"),
        "samples_per_pixel": render.get("spp"),
        "render_threads": render.get("threads"),
        "crop_out_size_px": RING_PX,
        "crop_margin_fraction": render_log.get("model", {}).get("margin_frac"),
        "crop_rule": "square crop around the measured dungeon content box, equal left/right "
                     "and equal top/bottom margins, LANCZOS resize to 1024 px",
        "projection": render_log.get("camera", {}).get("projectionMode"),
        "fov_blocks": render_log.get("camera", {}).get("fov_blocks"),
        "camera_position": render_log.get("camera", {}).get("position"),
        "camera_yaw_pitch_deg": [render_log.get("camera", {}).get("mc_yaw_deg"),
                                 render_log.get("camera", {}).get("mc_pitch_deg")],
        "sun": {"altitude_deg": render_log.get("camera", {}).get("sun_altitude_deg"),
                "azimuth_deg": render_log.get("camera", {}).get("sun_azimuth_deg"),
                "clouds": render_log.get("camera", {}).get("clouds")},
        "worlds": "worlds/<theme> staged into work/worlds/<theme> by chunky_render2.py stage",
        "note": "re-rendered with the settings of the approved run - no resolution or sample "
                "reduction: 1600 px / 64 spp / 8 render threads, identical camera, fov and 7% "
                "crop margins, so the two new rings stay pixel-comparable with the primary ring. "
                "The 21 frames (20 themes + the empty-sky reference) took about 48 minutes of wall "
                "clock instead of the ~10 minutes of the approved run, because the shared Chunky "
                "CPU quota was tightened from 8 to 4 cores (CPUWeight 50 -> 20) while the batch was "
                "running; the thread count was not raised and no process left the render cgroup.",
        "evidence": "evidence/chunky-render-log.json (per theme: command, content box, crop box, "
                    "margins); evidence/chunky-render-log-approved-run.json (the pruned "
                    "approved-run log, kept for provenance)",
    }

    grouping = {
        "primary": "renders/composite/proceduraldungeon-all20-radial-theme.png "
                   "(round-3 theme order) remains the primary composite and was not rebuilt",
        "sector_order_clockwise": list(GROUP_ORDER),
        "sector_placement": {g: GROUPS[g]["placement"] for g in GROUP_ORDER},
        "sort_key": {
            "outer": "dimension group (minecraft:overworld 11 themes, minecraft:nether 7, "
                     "minecraft:the_end 2)",
            "inner": "documented biome progression per dimension, stated per group below; "
                     "no measured/derived value is used, so the order does not depend on the "
                     "render noise",
            "tie_break": "none needed - every theme has a distinct rank",
        },
        "groups": {
            g: {
                "label": GROUPS[g]["label"],
                "placement": GROUPS[g]["placement"],
                "theme_count": len(themes_of(g)),
                "biome_sort_key": GROUPS[g]["sort_key"],
                "sector_degrees": {"equal_thirds": equal_thirds["sector_degrees"][g],
                                   "proportional": proportional["sector_degrees"][g]},
                "sector_centre_deg": {"equal_thirds": equal_thirds["bisectors_deg"][g],
                                      "proportional": proportional["bisectors_deg"][g]},
                "themes": [
                    {"index": index + 1, "theme": theme, "label": base.label(theme),
                     "biome": biome, "layer": layer,
                     "wedge_degrees": {"equal_thirds": equal_thirds["wedge_degrees"][g][theme],
                                       "proportional": proportional["wedge_degrees"][g][theme]},
                     "mean_rgb": colours[theme]["rgb"],
                     "lab_hue_deg": colours[theme]["hue_deg"],
                     "lab_chroma": colours[theme]["chroma"]}
                    for index, (theme, biome, layer) in enumerate(GROUPS[g]["themes"])
                ],
            }
            for g in GROUP_ORDER
        },
        "layouts": layouts,
        "legend": str(legend.relative_to(ART)),
        "construction": "identical to build_composite2.py: ring pixels are direct copies of the "
                        "same-coordinate pixels of each theme's own equal-margin Chunky crop, "
                        "0 deg = up, angles increase clockwise",
        "crop_margin_verification": verification,
        "render": settings,
        "theme_colours_note": "mean_rgb / lab_hue_deg / lab_chroma are measured on the re-rendered "
                              "frames with the same metric as ordering-report.json -> "
                              "theme_colours (mask = pixels differing from the empty-sky "
                              "reference render)",
    }

    report_path = EVIDENCE / "ordering-report.json"
    report = json.loads(report_path.read_text()) if report_path.exists() else {}
    report["dimension_grouping"] = grouping
    report_path.write_text(json.dumps(report, indent=1))
    print(json.dumps({
        "equal_thirds": {g: list(equal_thirds["spans_deg"][g]) for g in GROUP_ORDER},
        "proportional": {g: list(proportional["spans_deg"][g]) for g in GROUP_ORDER},
        "proportional_bisectors_deg": proportional["bisectors_deg"],
        "proportional_max_deviation_deg": proportional["max_abs_deviation_deg"],
        "images": [layouts["equal_thirds"]["path"], layouts["proportional"]["path"],
                   str(legend.relative_to(ART))],
        "margin_verification": verification["summary"],
    }, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
