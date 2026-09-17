"""Drive the isolated :247 client to search dungeon RNG seeds for a TALLER dungeon.

The layout RNG is `WorldgenRandom` seeded with `setLargeFeatureSeed(worldSeed, chunkX, chunkZ)`
(structure `Structure.GenerationContext` constructor, verified in
captures/generation-seed-bytecode.txt), so the dungeon RNG seed is fully determined by the
world seed 20260910 plus the chunk the command runs in.  `/execute positioned <x> <y> <z> run
generatedungeon <theme> 5 20` therefore evaluates an independent RNG realization per
candidate chunk, which is exactly the seed search the task asks for.

For every candidate: teleport, generate the cobblestone theme, wait for the staged job to
drain, force a save through the pause menu (the session has no /save-all permission) and
measure the occupied bounding box straight out of the saved region files.

Command delivery is clipboard based, exactly like captures-dungeon/scripts/drive_themes.py.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ART = Path(__file__).resolve().parent.parent
DISPLAY = os.environ.get("CAPTURE_DISPLAY", ":247")
GAMEDIR = Path(os.environ.get("CAPTURE_GAMEDIR", str(ART / "runtime")))
LOG = ART / "logs" / "latest.log"
WORLD = GAMEDIR / "saves" / "Round3 Tall Search"
REGION = WORLD / "dimensions" / "minecraft" / "overworld" / "region"
EVIDENCE = ART / "evidence"
PROBE = "/time set noon"
PROBE_PATTERN = r"noon|time to 6000"
REGION_FILES_GLOB = "r.*.*.mca"

sys.path.insert(0, str(ART / "scripts"))
import anvil  # noqa: E402

# ---------------------------------------------------------------- candidate seeds

WORLD_SEED = 20260910
TIER, DEPTH, THEME = 5, 20, "cobblestone"
START_Y = 200


def candidates() -> list[dict]:
    """Chunk positions (RNG seeds) plus tier/depth/start-height controls.

    Every candidate sits on its own 192-block lattice cell (dungeons span +-72
    blocks) so the neighbours can never be confused with the dungeon measured.
    """
    out = []
    # RNG realizations: one per chunk along +x, cobblestone tier 5 depth 20
    for cx in (0, 12, 24, 36, 48, 60, 72, 84, 96, 108, 120, 132, 144, 156, 168, 180):
        out.append({"chunk_x": cx, "chunk_z": 0, "start_y": START_Y})
    # independent RNG realizations in +z
    for cx, cz in ((12, 12), (24, 12), (36, 12), (48, 12), (60, 12), (72, 12),
                   (12, 24), (24, 24), (36, 24), (48, 24)):
        out.append({"chunk_x": cx, "chunk_z": cz, "start_y": START_Y})
    # start-height controls: identical RNG (chunk 0,0), different placement height
    out.append({"chunk_x": 0, "chunk_z": 0, "start_y": 150, "control": "start height 150 (RNG of chunk 0,0)"})
    out.append({"chunk_x": 0, "chunk_z": 0, "start_y": 250, "control": "start height 250 (RNG of chunk 0,0)"})
    # shape controls: tier / depth sweep on fresh RNG realizations
    out.append({"chunk_x": 60, "chunk_z": 24, "start_y": START_Y, "tier": 5, "depth": 16,
                "control": "tier 5 depth 16"})
    out.append({"chunk_x": 72, "chunk_z": 24, "start_y": START_Y, "tier": 5, "depth": 12,
                "control": "tier 5 depth 12"})
    out.append({"chunk_x": 84, "chunk_z": 24, "start_y": START_Y, "tier": 5, "depth": 8,
                "control": "tier 5 depth 8"})
    out.append({"chunk_x": 96, "chunk_z": 24, "start_y": START_Y, "tier": 4, "depth": 20,
                "control": "tier 4 depth 20"})
    out.append({"chunk_x": 108, "chunk_z": 24, "start_y": START_Y, "tier": 4, "depth": 16,
                "control": "tier 4 depth 16"})
    out.append({"chunk_x": 120, "chunk_z": 24, "start_y": START_Y, "tier": 3, "depth": 20,
                "control": "tier 3 depth 20"})
    out.append({"chunk_x": 132, "chunk_z": 24, "start_y": START_Y, "tier": 3, "depth": 14,
                "control": "tier 3 depth 14"})
    return out


def position(candidate: dict) -> tuple[float, float, float]:
    return (candidate["chunk_x"] * 16 + 0.5, float(candidate["start_y"]), candidate["chunk_z"] * 16 + 0.5)


def effective_seed(world_seed: int, chunk_x: int, chunk_z: int) -> int:
    """`WorldgenRandom.setLargeFeatureSeed(seed, x, z)` -> internal 48-bit state, per bytecode.

    LegacyRandomSource.setSeed(s) = (s ^ 0x5DEECE66D) & ((1<<48)-1);
    next(bits) advances the state and returns the top `bits`; nextLong combines two
    next(32) calls with Java's sign-extending long arithmetic.
    """
    mask48 = (1 << 48) - 1
    state = (world_seed ^ 0x5DEECE66D) & mask48

    def advance() -> int:
        nonlocal state
        state = (state * 0x5DEECE66D + 0xB) & mask48
        return state

    def next_bits(bits: int) -> int:
        return advance() >> (48 - bits)

    def signed32(value: int) -> int:
        return value - (1 << 32) if value >= (1 << 31) else value

    def next_long() -> int:
        high = signed32(next_bits(32))
        low = signed32(next_bits(32))
        return (high * (1 << 32) + low) & ((1 << 64) - 1)

    first = next_long()
    second = next_long()
    return ((chunk_x * first) ^ (chunk_z * second) ^ world_seed) & ((1 << 64) - 1)


# ---------------------------------------------------------------- client driver


def run(cmd: list[str], **kw) -> str:
    env = {**os.environ, "DISPLAY": DISPLAY}
    return subprocess.run(cmd, check=True, capture_output=True, text=True, env=env, **kw).stdout


def press(key: str) -> None:
    run(["xdotool", "key", "--clearmodifiers", key])


def set_clipboard(text: str) -> None:
    env = {**os.environ, "DISPLAY": DISPLAY}
    subprocess.run(["xclip", "-selection", "clipboard", "-silent"], input=text, text=True,
                   check=True, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)


def window_id() -> str:
    ids = [line.strip() for line in run(["xdotool", "search", "--name", "Minecraft"]).splitlines() if line.strip()]
    if not ids:
        raise SystemExit("no Minecraft window found")
    return ids[-1]


class Driver:
    def __init__(self) -> None:
        self.win = window_id()
        run(["xdotool", "windowfocus", "--sync", self.win])
        self.offset = LOG.stat().st_size if LOG.exists() else 0
        self.progress = EVIDENCE / "search-progress.log"
        self.last_unmatched = ""
        self.say(f"driver attached to window {self.win}")

    def say(self, message: str) -> None:
        stamp = time.strftime("%H:%M:%S")
        with self.progress.open("a") as handle:
            handle.write(f"[{stamp}] {message}\n")
        print(f"[{stamp}] {message}", flush=True)

    def log_since(self) -> str:
        if not LOG.exists():
            return ""
        with LOG.open("r", errors="replace") as handle:
            handle.seek(self.offset)
            text = handle.read()
            self.offset = handle.tell()
        return text

    def wait_for(self, pattern: str, timeout: float) -> str | None:
        regex = re.compile(pattern)
        collected = ""
        deadline = time.time() + timeout
        while time.time() < deadline:
            collected += self.log_since()
            if regex.search(collected):
                return collected
            time.sleep(0.4)
        self.last_unmatched = collected
        return None

    def recover_screen(self) -> None:
        press("Escape")
        time.sleep(0.4)
        press("Tab")
        time.sleep(0.35)
        press("Return")
        time.sleep(1.2)

    def pause_save(self, timeout: float = 600.0) -> str:
        for attempt in range(1, 4):
            press("Escape")
            text = self.wait_for(r"Saving chunks for level", timeout=timeout)
            if text is not None:
                time.sleep(1.0)
                self.log_since()
                press("Escape")
                time.sleep(1.0)
                return text
            press("Escape")
            time.sleep(1.0)
        raise SystemExit("world never saved")

    def paste_command(self, text: str, settle: float = 1.0) -> None:
        set_clipboard(text)
        run(["xdotool", "windowfocus", "--sync", self.win])
        press("t")
        time.sleep(settle)
        press("ctrl+v")
        time.sleep(settle)
        press("Return")
        time.sleep(0.9)

    def quick_command(self, command: str, pattern: str, timeout: float = 120.0) -> str | None:
        """Send a command without re-probing the channel; None when it did not answer."""
        self.paste_command(command)
        text = self.wait_for(pattern, timeout)
        if text is None or re.search(r"<IconCapture>", text):
            return None
        return text

    def ensure_channel(self) -> None:
        for attempt in range(1, 7):
            self.paste_command(PROBE)
            text = self.wait_for(PROBE_PATTERN, timeout=20)
            if text and not re.search(r"<IconCapture>", text):
                return
            if text:
                self.recover_screen()
                continue
            self.recover_screen()
        raise SystemExit("cannot reach the game chat input")

    def send_without_feedback(self, command: str) -> None:
        for attempt in range(1, 4):
            self.ensure_channel()
            self.paste_command(command)
            time.sleep(2.5)
            self.log_since()
            self.paste_command(PROBE)
            if self.wait_for(PROBE_PATTERN, timeout=15):
                time.sleep(0.6)
                self.log_since()
                return
        raise SystemExit(f"command never delivered: {command}")

    def command_and_wait(self, command: str, pattern: str, timeout: float = 300.0) -> str:
        self.ensure_channel()
        for attempt in range(1, 4):
            self.paste_command(command)
            text = self.wait_for(pattern, timeout=timeout)
            if text is not None and re.search(
                r"Expected whitespace|Unknown or incomplete command|Incorrect argument|Unknown command", text
            ):
                self.recover_screen()
                continue
            if text is not None:
                if re.search(r"<IconCapture>", text):
                    self.recover_screen()
                    continue
                return text
            unmatched = self.last_unmatched or ""
            tail = " / ".join(line.strip() for line in unmatched.strip().splitlines()[-2:])
            self.say(f"   !! no match for {pattern} after {timeout:.0f}s, last: {tail[:160]}")
        raise SystemExit(f"command never confirmed: {command}")


# ---------------------------------------------------------------- measurement


def measure_bbox(window) -> dict | None:
    """Occupied bounding box inside `window` (x0,y0,z0,x1,y1,z1), from the saved regions."""
    x0, y0, z0, x1, y1, z1 = window
    minx = miny = minz = 10**9
    maxx = maxy = maxz = -10**9
    count = 0
    cx0, cx1 = x0 >> 4, x1 >> 4
    cz0, cz1 = z0 >> 4, z1 >> 4
    for path in sorted(REGION.glob(REGION_FILES_GLOB)):
        rx, rz = (int(part) for part in path.name.split(".")[1:3])
        if not (rx * 32 <= cx1 and (rx + 1) * 32 - 1 >= cx0 and rz * 32 <= cz1 and (rz + 1) * 32 - 1 >= cz0):
            continue
        for cx, cz, nbt in anvil.iter_region_chunks(path):
            if not (cx0 <= cx <= cx1 and cz0 <= cz <= cz1):
                continue
            for x, y, z, _key in anvil.iter_chunk_blocks(nbt):
                if not (x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1):
                    continue
                count += 1
                minx, maxx = min(minx, x), max(maxx, x)
                miny, maxy = min(miny, y), max(maxy, y)
                minz, maxz = min(minz, z), max(maxz, z)
    if count == 0:
        return None
    return {"bbox": [minx, miny, minz, maxx, maxy, maxz], "blocks": count,
            "size": [maxx - minx + 1, maxy - miny + 1, maxz - minz + 1]}


def search_bbox(candidate: dict) -> dict | None:
    x, y, z = position(candidate)
    half = 100
    window = (int(x) - half, 0, int(z) - half, int(x) + half, 320, int(z) + half)
    return measure_bbox(window)


# ---------------------------------------------------------------- commands


def setup(driver: Driver) -> None:
    path = EVIDENCE / "search-log.json"
    state = json.loads(path.read_text()) if path.exists() else {"candidates": []}
    if state.get("setupDone"):
        return
    driver.command_and_wait("/gamerule advance_time false", r"advance_time is now set to: false")
    driver.command_and_wait("/gamerule advance_weather false", r"advance_weather is now set to: false")
    driver.command_and_wait("/gamerule spawn_monsters false", r"spawn_monsters")
    driver.command_and_wait("/gamerule random_tick_speed 0", r"random_tick_speed")
    driver.command_and_wait("/time set noon", r"noon|time to 6000")
    driver.command_and_wait("/weather clear", r"weather to clear|Set the weather")
    driver.send_without_feedback("/gamemode spectator")
    driver.command_and_wait("/gamerule max_block_modifications 100000000", r"set to: 100000000")
    state["setupDone"] = True
    path.write_text(json.dumps(state, indent=1))
    driver.say("setup complete")


def run_candidate(driver: Driver, candidate: dict) -> dict:
    x, y, z = position(candidate)
    started = time.time()
    entry = dict(candidate)
    entry["world_seed"] = WORLD_SEED
    entry["rng_seed"] = effective_seed(WORLD_SEED, candidate["chunk_x"], candidate["chunk_z"])
    entry["position"] = [x, y, z]
    driver.command_and_wait(f"/tp @s {x:.1f} {y:.1f} {z:.1f} 0 0",
                            r"Teleported .* to %.6f, %.6f, %.6f" % (x, y, z), timeout=120)
    time.sleep(2.0)
    tier = candidate.get("tier", TIER)
    depth = candidate.get("depth", DEPTH)
    entry["tier"], entry["depth"] = tier, depth
    text = driver.command_and_wait(f"/generatedungeon {THEME} {tier} {depth}",
                                   rf"Scheduling Tier {tier}", timeout=180)
    match = re.search(r"Scheduled .* with (\d+) pieces", text)
    if not match:
        text = driver.wait_for(r"Scheduled .* with (\d+) pieces", 900) or text
        match = re.search(r"Scheduled .* with (\d+) pieces", text)
    if not match:
        raise SystemExit(f"dungeon never scheduled at {x} {y} {z}")
    entry["pieces"] = int(match.group(1))
    while True:
        text = driver.quick_command("/generatedungeonstatus",
                                    r"Staged dungeon jobs: (\d+), pending pieces: (\d+)", timeout=60)
        if text is None:
            text = driver.command_and_wait("/generatedungeonstatus",
                                           r"Staged dungeon jobs: (\d+), pending pieces: (\d+)", timeout=300)
        status = list(re.finditer(r"Staged dungeon jobs: (\d+), pending pieces: (\d+)", text))[-1]
        if status.group(1) == "0" and status.group(2) == "0":
            break
        time.sleep(2)
    driver.pause_save()
    measured = search_bbox(candidate)
    entry["seconds"] = round(time.time() - started, 1)
    if measured:
        entry.update(measured)
    return entry


def main(argv: list[str]) -> int:
    path = EVIDENCE / "search-log.json"
    state = json.loads(path.read_text()) if path.exists() else {"candidates": []}
    driver = Driver()
    driver.ensure_channel()
    setup(driver)
    todo = candidates()
    if len(argv) > 1 and argv[1] != "all":
        wanted = {int(a) for a in argv[1:]}
        todo = [c for i, c in enumerate(todo) if i in wanted]
    for index, candidate in enumerate(todo):
        label = (f"cx{candidate['chunk_x']}cz{candidate['chunk_z']}y{candidate['start_y']}"
                 f"t{candidate.get('tier', TIER)}d{candidate.get('depth', DEPTH)}")
        if any(c.get("label") == label for c in state["candidates"]):
            driver.say(f"{label}: already measured, skipping")
            continue
        entry = run_candidate(driver, candidate)
        entry["label"] = label
        state["candidates"].append(entry)
        path.write_text(json.dumps(state, indent=1))
        driver.say(f"{label}: {json.dumps({k: entry.get(k) for k in ('pieces', 'size', 'seconds')})}")
    driver.say("search batch done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
