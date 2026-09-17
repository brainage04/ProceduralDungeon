"""Headless Chunky render pipeline for the round-3 "taller dungeon" theme set.

Same renderer, camera convention, canvas and crop maths as
captures-chunky/scripts/chunky_render.py (see that file's header for the verified
Chunky scene-format and camera evidence); the differences are:

  * the dungeon bounding box / camera shift / fov are read from the chosen search
    winner (evidence/seeds-report.json), not hard-coded from round 3
  * the staged worlds come from captures-dungeon2/worlds/<theme> (generated with the
    winning dungeon RNG seed), the empty-sky reference from worlds/_empty
  * the camera shift is solved from the measured world-space silhouette
    (evidence/search-analysis.json) instead of a hand-tuned constant: the shift that
    puts the content centre on the frame centre is  -(du*right + dv*screen_down)
  * the crop step pads the measured content box by one pixel on the axis whose extent has
    the odd parity, so the square crop always has equal margins within each axis (the
    captures-chunky rule silently produced a 1 px difference for the themes whose content
    width and height had different parity)

usage:
  chunky_render2.py model
  chunky_render2.py stage
  chunky_render2.py <theme|_empty|all> [--fov B] [--spp N] [--size PX] [--threads N]
"""

from __future__ import annotations

import json
import math
import shutil
import subprocess
import sys
from pathlib import Path

PILLOW_SITE = "/nix/store/4v9j9wbzyhrlx9980ygbr812313mazy0-python3.13-pillow-12.3.0/lib/python3.13/site-packages"
if PILLOW_SITE not in sys.path:  # Pillow is not on the default path in this image
    sys.path.append(PILLOW_SITE)


ART = Path(__file__).resolve().parent.parent
WORK = ART / "work"
SCENES = WORK / "scenes"
SNAPSHOTS = SCENES / "snapshots"
LOGS = WORK / "logs"
RENDERS = ART / "renders"
CROPS = RENDERS / "crops"
FRAMES = RENDERS / "frames"
EVIDENCE = ART / "evidence"

WORLDS = ART / "worlds"
JAVA = "/nix/store/b7hzgfqk2jfcbncp0vpabf73j0riqgq4-openjdk-25.0.4.1+1/bin/java"
CHUNKY_LIB = ART / "work" / "chunky-lib"
CHUNKY_CORE = "chunky-core-2.5.0-SNAPSHOT.478.g527cb4a.jar"
TEXTURES = Path("/home/thomas/01_TM/Coding/Websites/brainage04.github.io/.local-icon-variants/"
                "round3/captures-dungeon/chunky/round3-mc26.2-block-textures.zip")

MC_YAW, MC_PITCH = 45.0, 30.0
YAW = math.radians(90.0 - MC_YAW)
PITCH = math.radians(MC_PITCH - 90.0)
DISTANCE = 300.0
Y_CLIP_MAX = 319
SIZE = 1600
SPP = 64
THREADS = 20
OUT_SIZE = 1024
MARGIN_FRAC = 0.07
CHUNK_MARGIN = 2
FILL = 0.80


def config() -> dict:
    """Bounding box + silhouette of the chosen dungeon.

    The bounding box comes from the *structural* per-theme measurement of the final
    capture world (evidence/generation-log.json) when available: the search-log box
    includes lava that keeps flowing downwards, which must not extend the clip box.
    """
    report = json.loads((EVIDENCE / "seeds-report.json").read_text())
    winner = report["winner"]
    analysis = json.loads((EVIDENCE / "search-analysis.json").read_text())
    scored = next((c for c in analysis["candidates"] if c["label"] == winner["label"]), None)
    if scored is None:
        raise SystemExit(f"winner {winner['label']} missing from search-analysis.json")
    bbox = scored["bbox"]
    gen_path = EVIDENCE / "generation-log.json"
    if gen_path.exists():
        gen = json.loads(gen_path.read_text())
        measured = {tuple(entry["bbox"]) for entry in gen.get("themes", {}).values() if entry.get("bbox")}
        if len(measured) == 1:
            bbox = list(measured.pop())
    return {
        "bbox": bbox,
        "bbox_source": "evidence/generation-log.json (structural, fluids excluded)" if bbox != scored["bbox"]
                       else "evidence/search-log.json",
        "silhouette": scored["silhouette"],
        "label": winner["label"],
    }


def view_axes():
    forward = (math.cos(YAW) * math.sin(PITCH), -math.cos(PITCH), -math.sin(YAW) * math.sin(PITCH))
    right = (-math.sin(YAW), 0.0, -math.cos(YAW))
    screen_down = (math.cos(YAW) * math.cos(PITCH), math.sin(PITCH), -math.sin(YAW) * math.cos(PITCH))
    return right, screen_down, forward


def camera_center(box) -> tuple[float, float, float]:
    x0, y0, z0, x1, y1, z1 = box
    return ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)


def camera_shift(box, silhouette) -> tuple[float, float, float]:
    """World offset that centres the measured silhouette on the frame centre.

    In a parallel projection the image coordinates of a world point X are
    u = right.(X - P), v = down.(X - P) with P = centre - forward*distance + shift,
    so moving the camera by +du*right moves the content by -du.  Centring the
    silhouette (u_centre, v_centre) therefore needs shift = +du*right + dv*down.
    """
    right, screen_down, _ = view_axes()
    cx, cy, cz = camera_center(box)
    u0, v0, u1, v1 = silhouette["screen_bbox_world_offsets"]
    du = (u0 + u1) / 2 - (right[0] * cx + right[2] * cz)
    dv = (v0 + v1) / 2 - (screen_down[0] * cx + screen_down[1] * cy + screen_down[2] * cz)
    return (du * right[0] + dv * screen_down[0],
            du * right[1] + dv * screen_down[1],
            du * right[2] + dv * screen_down[2])


def camera_position(box, silhouette, distance: float = DISTANCE) -> tuple[float, float, float]:
    cx, cy, cz = camera_center(box)
    _, _, (dx, dy, dz) = view_axes()
    shift = camera_shift(box, silhouette)
    return (cx - dx * distance + shift[0], cy - dy * distance + shift[1], cz - dz * distance + shift[2])


def default_fov(box, silhouette, fill: float = FILL) -> float:
    width = silhouette["projected_width_blocks"]
    height = silhouette["projected_height_blocks"]
    return round(max(width, height) / fill, 2)


def chunk_list(box) -> list[list[int]]:
    x0, _, z0, x1, _, z1 = box
    return [[cx, cz]
            for cx in range(x0 // 16 - CHUNK_MARGIN, x1 // 16 + CHUNK_MARGIN + 1)
            for cz in range(z0 // 16 - CHUNK_MARGIN, z1 // 16 + CHUNK_MARGIN + 1)]


def classpath() -> str:
    jars = [CHUNKY_CORE, "commons-math3-3.2.jar", "fastutil-8.4.4.jar", "gson-2.9.0.jar",
            "lz4-java-1.8.0.jar", "maven-artifact-3.9.9.jar", "plexus-utils-3.5.1.jar"]
    missing = [j for j in jars if not (CHUNKY_LIB / j).is_file()]
    if missing:
        raise SystemExit(f"missing chunky libraries: {missing}")
    return ":".join(str(CHUNKY_LIB / j) for j in jars)


def scene_document(name: str, world: Path, fov: float, size: int, spp: int, cfg: dict) -> dict:
    box = cfg["bbox"]
    px, py, pz = camera_position(box, cfg["silhouette"])
    return {
        "sdfVersion": 10,
        "name": name,
        "width": size,
        "height": size,
        "yClipMin": box[1],
        "yClipMax": Y_CLIP_MAX,
        "exposure": 1.0,
        "postprocess": "GAMMA",
        "outputMode": "PNG",
        "spp": 0,
        "sppTarget": spp,
        "renderTime": 0,
        "rayDepth": 5,
        "pathTrace": True,
        "dumpFrequency": 0,
        "saveSnapshots": False,
        "emittersEnabled": True,
        "emitterIntensity": 1.0,
        "sunEnabled": True,
        "sunSamplingStrategy": "FAST",
        "stillWater": False,
        "biomeColorsEnabled": True,
        "transparentSky": False,
        "fog": {"mode": "UNIFORM", "uniformDensity": 0.0, "skyFogDensity": 0.0,
                "layers": [], "fastFog": True},
        "world": {"path": str(world), "dimension": "minecraft:overworld"},
        "camera": {
            "name": "camera",
            "position": {"x": round(px, 4), "y": round(py, 4), "z": round(pz, 4)},
            "orientation": {"roll": 0.0, "pitch": PITCH, "yaw": YAW},
            "projectionMode": "PARALLEL",
            "fov": round(fov, 4),
            "dof": "Infinity",
            "focalOffset": 20.0,
            "shift": {"x": 0.0, "y": 0.0},
        },
        "sun": {"altitude": 0.8726646259971648, "azimuth": 3.9269908169872414, "intensity": 1.25,
                "color": {"red": 1.0, "green": 1.0, "blue": 1.0}, "drawTexture": True},
        "sky": {"skyLight": 1.0, "mode": "SIMULATED", "cloudsEnabled": False},
        "materials": {},
        "chunkList": chunk_list(box),
    }


def clean(name: str) -> None:
    for suffix in (".dump", ".dump.backup", ".octree2", ".octree2.backup", ".json.backup"):
        (SCENES / f"{name}{suffix}").unlink(missing_ok=True)
    for png in SNAPSHOTS.glob(f"{name}*.png"):
        png.unlink()


def render_command(name: str, spp: int, threads: int) -> list[str]:
    return [JAVA, "-Xmx6G", f"-Dchunky.home={WORK / 'chunky-home'}", "-cp", classpath(),
            "-Djava.awt.headless=true", "se.llbit.chunky.main.Chunky",
            "-texture", str(TEXTURES), "-render", str(SCENES / f"{name}.json"),
            "-reload-chunks", "-f", "-target", str(spp), "-threads", str(threads)]


def render(name: str, spp: int, threads: int = 20, timeout: int = 5400) -> dict:
    LOGS.mkdir(parents=True, exist_ok=True)
    SNAPSHOTS.mkdir(parents=True, exist_ok=True)
    before = {p.name for p in SNAPSHOTS.glob(f"{name}*.png")}
    command = render_command(name, spp, threads)
    proc = subprocess.run(command, capture_output=True, text=True, cwd=str(ART), timeout=timeout)
    text = proc.stdout + proc.stderr
    (LOGS / f"{name}.log").write_text(text)
    fresh = [p for p in SNAPSHOTS.glob(f"{name}*.png") if p.name not in before]
    entry = {
        "command": " ".join(command),
        "returncode": proc.returncode,
        "chunksLoaded": len([l for l in text.splitlines() if "Loading chunks:" in l]),
        "errors": sorted({l.strip() for l in text.splitlines()
                          if "Exception" in l or l.startswith("Failed")})[:4],
    }
    if not fresh:
        return entry | {"snapshot": None}
    shot = max(fresh, key=lambda p: p.stat().st_mtime)
    return entry | {"snapshot": shot}


def _bands(path: Path):
    from PIL import Image

    with Image.open(path) as im:
        return im.convert("RGB").load(), im.size


def content_bbox(frame: Path, reference: Path, threshold: int = 6):
    """Bounding box of pixels that differ from the empty-sky reference render.

    Same rule as captures-chunky (sum of the absolute channel differences > 6), but
    evaluated with PIL's C loops instead of a Python pixel loop.
    """
    from PIL import Image, ImageChops

    with Image.open(frame) as f, Image.open(reference) as r:
        difference = ImageChops.difference(f.convert("RGB"), r.convert("RGB"))
        red, green, blue = difference.split()
        summed = ImageChops.add(ImageChops.add(red, green), blue)
        mask = summed.point(lambda v: 255 if v > threshold else 0)
        box = mask.getbbox()
        # getbbox returns exclusive right/lower bounds; the crop maths uses inclusive ones
        return (box[0], box[1], box[2] - 1, box[3] - 1) if box else None


def crop_equal_margins(frame: Path, box, out: Path, margin_frac: float = MARGIN_FRAC,
                       out_size: int = OUT_SIZE) -> dict:
    """Square crop with equal left/right and equal top/bottom margins.

    A square window centred on the content box gives equal margins within an axis only
    when (side - width) and (side - height) are both even, i.e. when the width and the
    height have the same parity.  The content mask can disagree by one pixel from theme
    to theme (render noise on the silhouette edge), so the axis whose measured extent has
    the odd parity is padded by one pixel first: without this the margin pair of that
    theme is off by one pixel and `margins_equal_in_crop` is false.
    """
    from PIL import Image

    x0, y0, x1, y1 = box
    measured_w, measured_h = x1 - x0 + 1, y1 - y0 + 1
    padding_px = [0, 0]
    if (measured_w - measured_h) % 2:
        if measured_w % 2:
            x1 += 1
            padding_px[0] = 1
        else:
            y1 += 1
            padding_px[1] = 1
    w, h = x1 - x0 + 1, y1 - y0 + 1
    span = max(w, h)
    side = span + 2 * int(round(margin_frac * span))
    for _ in range(2):
        if (side - w) % 2 == 0 and (side - h) % 2 == 0:
            break
        side += 1
    with Image.open(frame) as im:
        im = im.convert("RGB")
        fw, fh = im.size
        clamped = False
        if side > min(fw, fh):
            side = min(fw, fh)
            clamped = True
        left = x0 - (side - w) // 2
        top = y0 - (side - h) // 2
        left = max(0, min(fw - side, left))
        top = max(0, min(fh - side, top))
        cropped = im.crop((left, top, left + side, top + side))
        if out_size and side != out_size:
            cropped = cropped.resize((out_size, out_size), Image.LANCZOS)
        out.parent.mkdir(parents=True, exist_ok=True)
        cropped.save(out)
    left_margin = x0 - left
    top_margin = y0 - top
    right_margin = (left + side - 1) - x1
    bottom_margin = (top + side - 1) - y1
    scale = out_size / side if out_size else 1.0
    return {
        "crop_box": [left, top, left + side, top + side],
        "crop_side_px": side,
        "crop_clamped_to_frame": clamped,
        "content_wh_px": [measured_w, measured_h],
        "crop_content_wh_px": [w, h],
        "square_crop_padding_px": padding_px,
        "content_aspect_h_over_w": round(measured_h / measured_w, 4),
        "margins_px_in_crop": {"left": left_margin, "right": right_margin,
                               "top": top_margin, "bottom": bottom_margin},
        "margins_equal_in_crop": left_margin == right_margin and top_margin == bottom_margin,
        "margins_px_in_output": {k: round(v * scale, 2) for k, v in
                                 (("left", left_margin), ("right", right_margin),
                                  ("top", top_margin), ("bottom", bottom_margin))},
        "output": str(out),
        "output_size": out_size,
    }


def stage() -> list[str]:
    """Copy the saved region files of every generated theme into the render work dir."""
    themes = sorted(p.name for p in WORLDS.iterdir() if p.is_dir() and not p.name.startswith("_"))
    for theme in themes + ["_empty"]:
        src = WORLDS / theme
        dst = WORK / "worlds" / theme
        if dst.exists():
            shutil.rmtree(dst)
        (dst / "region").mkdir(parents=True)
        if (src / "level.dat").exists():
            shutil.copy2(src / "level.dat", dst / "level.dat")
        for mca in sorted((src / "region").glob("*.mca")):
            shutil.copy2(mca, dst / "region" / mca.name)
    return themes


def main(argv: list[str]) -> int:
    cfg = config()
    fov = default_fov(cfg["bbox"], cfg["silhouette"])
    box = cfg["bbox"]
    model = {
        "winner": cfg["label"],
        "bbox": box,
        "silhouette_blocks": [cfg["silhouette"]["projected_width_blocks"],
                              cfg["silhouette"]["projected_height_blocks"]],
        "camera_center": [round(v, 3) for v in camera_center(box)],
        "camera_shift": [round(v, 4) for v in camera_shift(box, cfg["silhouette"])],
        "camera_position": [round(v, 4) for v in camera_position(box, cfg["silhouette"])],
        "fov_blocks": fov,
        "chunk_list_entries": len(chunk_list(box)),
        "y_clip": [box[1], Y_CLIP_MAX],
        "size_px": SIZE,
        "spp": SPP,
        "out_size": OUT_SIZE,
        "margin_frac": MARGIN_FRAC,
    }
    if len(argv) < 2 or argv[1] == "model":
        print(json.dumps(model, indent=1))
        return 0

    jobs, opts = [], {}
    rest = argv[1:]
    i = 0
    while i < len(rest):
        if rest[i].startswith("--"):
            opts[rest[i][2:]] = rest[i + 1]
            i += 2
        else:
            jobs.append(rest[i])
            i += 1
    fov = float(opts["fov"]) if "fov" in opts else fov
    spp = int(opts["spp"]) if "spp" in opts else SPP
    size = int(opts["size"]) if "size" in opts else SIZE
    threads = int(opts["threads"]) if "threads" in opts else THREADS
    if jobs == ["stage"]:
        print(json.dumps(stage()))
        return 0

    log_path = EVIDENCE / "chunky-render-log.json"
    log = json.loads(log_path.read_text()) if log_path.exists() else {}
    log["method"] = "Chunky 2.5.0-SNAPSHOT.478.g527cb4a standalone, PARALLEL (orthographic) projection"
    log["model"] = model
    log["camera"] = {
        "mc_yaw_deg": MC_YAW, "mc_pitch_deg": MC_PITCH,
        "chunky_yaw_rad": YAW, "chunky_pitch_rad": PITCH,
        "position": model["camera_position"],
        "camera_shift": model["camera_shift"],
        "target_center": model["camera_center"],
        "distance_blocks": DISTANCE,
        "projectionMode": "PARALLEL",
        "fov_blocks": fov,
        "sun_altitude_deg": 50, "sun_azimuth_deg": 225,
        "clouds": False, "yClipMin": box[1], "yClipMax": Y_CLIP_MAX,
        "drawSunTexture": True,
    }
    log["render"] = {"size_px": size, "spp": spp, "threads": threads,
                     "chunkList_entries": len(chunk_list(box))}

    if jobs == ["all"]:
        jobs = sorted(p.name for p in (WORK / "worlds").iterdir()
                      if p.is_dir() and not p.name.startswith("_"))
    for theme in jobs:
        world = WORK / "worlds" / theme
        # resume support: an already rendered frame is re-measured instead of re-rendered
        resumed = theme != "_empty" and (FRAMES / f"{theme}.png").exists()
        if resumed:
            # keep what the last real render recorded for this theme (command, exit code,
            # loaded chunk count) and only refresh the measurement of the existing frame
            entry = dict(log.get("themes", {}).get(theme, {}))
            entry["resumed_from"] = str((FRAMES / f"{theme}.png").relative_to(ART))
            raw_log = LOGS / f"{theme}.log"
            if raw_log.exists():
                text = raw_log.read_text()
                entry["render_log"] = str(raw_log.relative_to(ART))
                if "chunksLoaded" not in entry:  # resume of a pass that overwrote them
                    entry["chunksLoaded"] = len([line for line in text.splitlines()
                                                 if "Loading chunks:" in line])
                    entry["errors"] = sorted({line.strip() for line in text.splitlines()
                                              if "Exception" in line
                                              or line.startswith("Failed")})[:4]
                    entry["command"] = " ".join(render_command(theme, spp, threads))
                    entry["render_details_rebuilt"] = (
                        "command/errors/chunksLoaded rebuilt from the saved renderer log and the "
                        "settings below; the exit code of the original run was not recorded")
            shot = FRAMES / f"{theme}.png"
            print(json.dumps({theme: "frame already rendered, re-measuring"}), flush=True)
        else:
            (SCENES / f"{theme}.json").write_text(
                json.dumps(scene_document(theme, world, fov, size, spp, cfg), indent=1))
            clean(theme)
            entry = render(theme, spp, threads=threads)
            shot = entry.pop("snapshot", None)
        entry.update({"fov_blocks": fov, "size_px": size, "spp": spp, "threads": threads})
        if shot:
            entry["frame"] = str(shot.relative_to(ART))
            if shot.resolve() != (FRAMES / f"{theme}.png").resolve():
                shutil.copy2(shot, FRAMES / f"{theme}.png")
            entry["frame_copy"] = str((FRAMES / f"{theme}.png").relative_to(ART))
        if shot and (FRAMES / "_empty.png").exists() and theme != "_empty":
            box_px = content_bbox(FRAMES / f"{theme}.png", FRAMES / "_empty.png")
            if box_px is None:
                entry["content_bbox"] = None
                entry["error"] = "render identical to empty-sky reference"
            else:
                entry["content_bbox"] = list(box_px)
                entry["content_touches_frame_edge"] = (
                    box_px[0] == 0 or box_px[1] == 0 or box_px[2] == size - 1 or box_px[3] == size - 1)
                out = CROPS / f"{theme}-chunky-ortho-{OUT_SIZE}.png"
                entry["crop"] = crop_equal_margins(FRAMES / f"{theme}.png", box_px, out)
        log.setdefault("themes", {})[theme] = entry
        print(json.dumps({theme: {k: entry[k] for k in ("chunksLoaded", "frame", "content_bbox",
                                                        "content_touches_frame_edge") if k in entry}}), flush=True)
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text(json.dumps(log, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
