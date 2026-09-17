"""Build the radial composites for the three wedge orderings + contact sheets.

Wedge construction is unchanged from captures-chunky/scripts/build_composite.py:
20 equal 18-degree wedges, clockwise from the top, each wedge a direct pixel copy of
the same-coordinate pixels of that theme's own equal-margin Chunky crop.

Orderings (see evidence/ordering-report.json for the reported numbers):

  theme    the round-3 theme order (cobblestone, deepslate, sculk, ... end_city)
  rainbow  themes sorted by the hue angle of their own mean dungeon colour
           (per-theme colour = mean CIELAB of the pixels that differ from the
           empty-sky reference render, i.e. the dungeon only, not the sky)
  contrast a cyclic order maximising the *minimum* CIEDE2000 colour difference
           between adjacent wedges (bottleneck/maximin cycle), tie-broken by the
           summed difference around the full cycle

usage: build_composite2.py [--suffix chunky-ortho-1024.png]
"""

from __future__ import annotations

import itertools
import json
import math
import random
import sys
from pathlib import Path

PILLOW_SITE = "/nix/store/4v9j9wbzyhrlx9980ygbr812313mazy0-python3.13-pillow-12.3.0/lib/python3.13/site-packages"
if PILLOW_SITE not in sys.path:  # Pillow is not on the default path in this image
    sys.path.append(PILLOW_SITE)


from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageStat

ART = Path(__file__).resolve().parent.parent
CROPS = ART / "renders" / "crops"
FRAMES = ART / "renders" / "frames"
COMPOSITE = ART / "renders" / "composite"
EVIDENCE = ART / "evidence"
FONT = "/nix/store/2mypq3md0fwbhkhc4jb20gbpnzyncnyk-hack-font-3.003/share/fonts/truetype/Hack-Regular.ttf"
SUFFIX = "chunky-ortho-1024.png"

THEMES = [
    "cobblestone", "deepslate", "sculk", "desert_tomb", "lush", "dripstone", "frozen",
    "ocean_ruin", "amethyst_geode", "copper", "stronghold", "nether_wastes", "crimson_forest",
    "warped_forest", "basalt_deltas", "soul_sand_valley", "nether_fortress", "bastion",
    "end_stone", "end_city",
]

# ---------------------------------------------------------------- colour maths


def srgb_to_lab(rgb):
    def linear(channel: float) -> float:
        channel /= 255.0
        return channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4

    r, g, b = (linear(c) for c in rgb)
    x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / 1.00000
    z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883

    def f(t: float) -> float:
        return t ** (1 / 3) if t > 216 / 24389 else (841 / 108) * t + 4 / 29

    fx, fy, fz = f(x), f(y), f(z)
    return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))


def delta_e00(lab1, lab2) -> float:
    """CIEDE2000 colour difference (Sharma et al. implementation)."""
    l1, a1, b1 = lab1
    l2, a2, b2 = lab2
    c1, c2 = math.hypot(a1, b1), math.hypot(a2, b2)
    c_bar = (c1 + c2) / 2
    g = 0.5 * (1 - math.sqrt(c_bar**7 / (c_bar**7 + 25**7))) if c_bar > 0 else 0.0
    a1p, a2p = (1 + g) * a1, (1 + g) * a2
    c1p, c2p = math.hypot(a1p, b1), math.hypot(a2p, b2)
    h1p = math.degrees(math.atan2(b1, a1p)) % 360 if (a1p or b1) else 0.0
    h2p = math.degrees(math.atan2(b2, a2p)) % 360 if (a2p or b2) else 0.0

    dlp = l2 - l1
    dcp = c2p - c1p
    if c1p * c2p == 0:
        dhp = 0.0
    elif abs(h2p - h1p) <= 180:
        dhp = h2p - h1p
    elif h2p - h1p > 180:
        dhp = h2p - h1p - 360
    else:
        dhp = h2p - h1p + 360
    dhp_angle = 2 * math.sqrt(c1p * c2p) * math.sin(math.radians(dhp) / 2)

    l_bar = (l1 + l2) / 2
    c_bar_p = (c1p + c2p) / 2
    if c1p * c2p == 0:
        h_bar_p = h1p + h2p
    elif abs(h1p - h2p) <= 180:
        h_bar_p = (h1p + h2p) / 2
    elif h1p + h2p < 360:
        h_bar_p = (h1p + h2p + 360) / 2
    else:
        h_bar_p = (h1p + h2p - 360) / 2

    t = (1
         - 0.17 * math.cos(math.radians(h_bar_p - 30))
         + 0.24 * math.cos(math.radians(2 * h_bar_p))
         + 0.32 * math.cos(math.radians(3 * h_bar_p + 6))
         - 0.20 * math.cos(math.radians(4 * h_bar_p - 63)))
    dtheta = 30 * math.exp(-(((h_bar_p - 275) / 25) ** 2))
    rc = 2 * math.sqrt(c_bar_p**7 / (c_bar_p**7 + 25**7)) if c_bar_p > 0 else 0.0
    sl = 1 + (0.015 * (l_bar - 50) ** 2) / math.sqrt(20 + (l_bar - 50) ** 2)
    sc = 1 + 0.045 * c_bar_p
    sh = 1 + 0.015 * c_bar_p * t
    rt = -math.sin(math.radians(2 * dtheta)) * rc
    return math.sqrt((dlp / sl) ** 2 + (dcp / sc) ** 2 + (dhp_angle / sh) ** 2
                     + rt * (dcp / sc) * (dhp_angle / sh))


def theme_colour(theme: str) -> dict:
    """Mean CIELAB of the *dungeon* pixels of this theme's frame (sky excluded).

    Identical mask rule to the crop's content box: a pixel counts when the sum of the
    absolute channel differences against the empty-sky reference exceeds 6.
    """
    with Image.open(FRAMES / f"{theme}.png") as frame, Image.open(FRAMES / "_empty.png") as ref:
        frame = frame.convert("RGB")
        reference = ref.convert("RGB")
        diff = ImageChops.difference(frame, reference)
        r, g, b = diff.split()
        total_channels = ImageChops.add(ImageChops.add(r, g), b)
        mask = total_channels.point(lambda v: 255 if v > 6 else 0)
        stats = ImageStat.Stat(frame, mask)
        count = mask.histogram()[255]
        mean_rgb = tuple(stats.mean)
    lab = srgb_to_lab(mean_rgb)
    chroma = math.hypot(lab[1], lab[2])
    hue = math.degrees(math.atan2(lab[2], lab[1])) % 360
    return {"rgb": [round(v, 2) for v in mean_rgb], "lab": [round(v, 3) for v in lab],
            "chroma": round(chroma, 3), "hue_deg": round(hue, 2), "pixels": count}


# ---------------------------------------------------------------- orderings


def theme_order() -> list[str]:
    return list(THEMES)


def rainbow_order(colours: dict) -> list[str]:
    """Sort by own hue angle, tie-broken by chroma (more saturated first) then name."""
    def key(theme: str):
        c = colours[theme]
        return (round(c["hue_deg"], 1), -c["chroma"], theme)

    return sorted(THEMES, key=key)


def cycle_score(order: list[str], colours: dict) -> tuple[float, float]:
    deltas = [delta_e00(colours[order[i]]["lab"], colours[order[(i + 1) % len(order)]]["lab"])
              for i in range(len(order))]
    return min(deltas), sum(deltas)


def contrast_order(colours: dict, seed: int = 20260913, iterations: int = 120_000) -> dict:
    """Maximise (min adjacent dE00, total dE00) of the wedge cycle.

    Bottleneck-then-total objective, solved by simulated annealing over 2-opt moves
    with a fixed seed so the result is reproducible.
    """
    rng = random.Random(seed)
    best = list(THEMES)
    best_score = cycle_score(best, colours)
    start_cycle = cycle_score(best, colours)

    for _ in range(40):
        order = list(THEMES)
        rng.shuffle(order)
        score = cycle_score(order, colours)
        temperature = 6.0
        for step in range(iterations // 40):
            i, j = sorted(rng.sample(range(len(order)), 2))
            if j - i < 1:
                continue
            candidate = order[:i] + order[i:j + 1][::-1] + order[j + 1:]
            cand_score = cycle_score(candidate, colours)
            gain = (cand_score[0] - score[0]) * 100 + (cand_score[1] - score[1])
            if gain >= 0 or rng.random() < math.exp(gain / max(temperature, 1e-6)):
                order, score = candidate, cand_score
            temperature *= 0.99995
            if score > best_score:
                best, best_score = list(order), score
    return {"order": best, "min_adjacent_delta_e00": round(best_score[0], 4),
            "total_cycle_delta_e00": round(best_score[1], 4),
            "theme_order_score": {"min_adjacent_delta_e00": round(start_cycle[0], 4),
                                  "total_cycle_delta_e00": round(start_cycle[1], 4)}}


# ---------------------------------------------------------------- images


def label(theme: str) -> str:
    return " ".join(part.capitalize() for part in theme.split("_"))


def source(theme: str, suffix: str = SUFFIX) -> Path:
    return CROPS / f"{theme}-{suffix}"


def build_radial(order: list[str], out: Path, size: int = 1024) -> Path:
    images = [Image.open(source(theme)).convert("RGB") for theme in order]
    images = [im if im.size == (size, size) else im.resize((size, size), Image.LANCZOS) for im in images]
    canvas = Image.new("RGB", (size, size))
    cx = cy = size / 2
    pixels = canvas.load()
    sources = [im.load() for im in images]
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            angle = math.degrees(math.atan2(dx, -dy)) % 360  # 0 deg = up, clockwise
            index = int(angle // (360 / len(images))) % len(images)
            pixels[x, y] = sources[index][x, y]
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out)
    return out


def build_contact_sheet(order: list[str], out: Path, title: str, cell: int = 320,
                        columns: int = 5, suffix: str = SUFFIX) -> Path:
    rows = math.ceil(len(order) / columns)
    padding = 12
    header = 46
    footer = 26
    width = columns * (cell + padding) + padding
    height = header + rows * (cell + padding + footer) + padding
    sheet = Image.new("RGB", (width, height), (18, 21, 27))
    draw = ImageDraw.Draw(sheet)
    title_font = ImageFont.truetype(FONT, 22)
    label_font = ImageFont.truetype(FONT, 18)
    draw.text((padding, 12), title, fill=(238, 241, 247), font=title_font)
    for index, theme in enumerate(order):
        row, column = divmod(index, columns)
        x = padding + column * (cell + padding)
        y = header + row * (cell + padding + footer)
        image = Image.open(source(theme, suffix)).convert("RGB")
        side = min(image.size)
        left = (image.width - side) // 2
        top = (image.height - side) // 2
        sheet.paste(image.crop((left, top, left + side, top + side)).resize((cell, cell), Image.LANCZOS), (x, y))
        draw.text((x, y + cell + 4), f"{index + 1}. {label(theme)}", fill=(206, 213, 224), font=label_font)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return out


def build_ordering_sheet(paths: list[tuple[str, Path]], out: Path, size: int = 512) -> Path:
    padding = 14
    header = 50
    width = len(paths) * (size + padding) + padding
    height = header + size + 34 + padding
    sheet = Image.new("RGB", (width, height), (18, 21, 27))
    draw = ImageDraw.Draw(sheet)
    title_font = ImageFont.truetype(FONT, 24)
    label_font = ImageFont.truetype(FONT, 17)
    draw.text((padding, 12), "ProceduralDungeon - 20 x 18deg wedge orderings "
                            "(theme order | rainbow by theme colour | max-contrast cycle)",
              fill=(238, 241, 247), font=title_font)
    for index, (name, path) in enumerate(paths):
        x = padding + index * (size + padding)
        y = header
        image = Image.open(path).convert("RGB").resize((size, size), Image.LANCZOS)
        sheet.paste(image, (x, y))
        draw.text((x, y + size + 6), name, fill=(206, 213, 224), font=label_font)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return out


def main(argv: list[str]) -> int:
    suffix = SUFFIX
    if "--suffix" in argv:
        suffix = argv[argv.index("--suffix") + 1]
    missing = [t for t in THEMES if not source(t, suffix).exists()]
    if missing:
        raise SystemExit(f"missing crops: {missing}")

    colours = {theme: theme_colour(theme) for theme in THEMES}
    contrast_stats = contrast_order(colours)
    orderings = {
        "theme": theme_order(),
        "rainbow": rainbow_order(colours),
        "contrast": contrast_stats["order"],
    }

    outputs = {}
    for name, order in orderings.items():
        path = build_radial(order, COMPOSITE / f"proceduraldungeon-all20-radial-{name}.png")
        outputs[name] = {"order": order, "path": str(path.relative_to(ART)),
                         "cycle": {
                             "min_adjacent_delta_e00": round(cycle_score(order, colours)[0], 4),
                             "total_cycle_delta_e00": round(cycle_score(order, colours)[1], 4)}}

    outputs["theme"]["path"] = str(build_radial(orderings["theme"],
                                                COMPOSITE / "proceduraldungeon-all20-radial-composite.png")
                                   .relative_to(ART))

    sheet = build_contact_sheet(
        orderings["theme"], COMPOSITE / "proceduraldungeon-all20-contact-sheet.png",
        "ProceduralDungeon - all 20 themes, identical dungeon, Chunky orthographic renders "
        "(whole dungeon, no ground, clouds off)")
    comparison = build_ordering_sheet(
        [("theme order", Path(outputs["theme"]["path"])),
         ("rainbow by theme colour (hue)", Path(outputs["rainbow"]["path"])),
         (f"max-contrast cycle (min dE00 {contrast_stats['min_adjacent_delta_e00']})",
          Path(outputs["contrast"]["path"]))],
        COMPOSITE / "proceduraldungeon-all20-ordering-comparison.png")

    report = {
        "construction": "20 equal 18-degree wedges, clockwise from the top, direct pixel copies",
        "input_suffix": suffix,
        "colour_metric": ("per theme: mean sRGB of the frame pixels that differ from the empty-sky "
                          "reference render (dungeon only), converted to CIELAB; hue = atan2(b*, a*); "
                          "adjacency objective uses CIEDE2000"),
        "theme_colours": colours,
        "orderings": outputs,
        "contrast_objective": {
            "definition": ("maximise the minimum CIEDE2000 difference between adjacent wedges in the "
                           "cyclic order, tie-broken by the summed difference around the cycle"),
            "search": "simulated annealing over 2-opt moves, 40 restarts, fixed seed 20260913",
            "result": {"order": contrast_stats["order"],
                       "min_adjacent_delta_e00": contrast_stats["min_adjacent_delta_e00"],
                       "total_cycle_delta_e00": contrast_stats["total_cycle_delta_e00"]},
            "same_objective_theme_order": contrast_stats["theme_order_score"],
            "same_objective_rainbow_order": {
                "min_adjacent_delta_e00": round(cycle_score(orderings["rainbow"], colours)[0], 4),
                "total_cycle_delta_e00": round(cycle_score(orderings["rainbow"], colours)[1], 4)},
        },
        "contact_sheet": str(sheet.relative_to(ART)),
        "ordering_comparison": str(comparison.relative_to(ART)),
        "delta_e00_selfcheck": {
            "pair": "Lab(50, 2.6772, -79.7751) vs Lab(50, 0, -82.7485)",
            "expected": 2.0425,
            "computed": round(delta_e00((50, 2.6772, -79.7751), (50, 0, -82.7485)), 4),
        },
    }
    (EVIDENCE / "ordering-report.json").write_text(json.dumps(report, indent=1))
    print(json.dumps({k: outputs[k]["order"] for k in outputs}, indent=1))
    print(json.dumps(report["contrast_objective"]["result"], indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
