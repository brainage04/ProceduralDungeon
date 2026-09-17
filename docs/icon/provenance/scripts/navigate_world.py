"""Blind-but-verified navigation from the title screen into the singleplayer world.

`--quickPlaySingleplayer` does not fire on this client build, so the world is opened
by double-clicking the first entry of the world list; success is proved by the client
log's own integrated-server banner ("Time elapsed:") exactly like
captures-dungeon/scripts/load_world.py did.
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
sys.path.insert(0, str(Path(__file__).resolve().parent))
import drive_search as _D  # noqa: E402

DISPLAY = _D.DISPLAY
LOG = _D.LOG
LOADED = re.compile(r"Server thread/INFO\]: Time elapsed:|Server thread/INFO\]: Preparing level")


def run(cmd, **kw):
    return subprocess.run(cmd, check=True, capture_output=True, text=True,
                          env={**os.environ, "DISPLAY": DISPLAY}, **kw).stdout


def window() -> str:
    ids = [line.strip() for line in run(["xdotool", "search", "--name", "Minecraft"]).splitlines() if line.strip()]
    return ids[-1]


def log_from(offset: int) -> tuple[str, int]:
    with LOG.open("r", errors="replace") as handle:
        handle.seek(offset)
        text = handle.read()
        return text, handle.tell()


def main() -> int:
    win = window()
    run(["xdotool", "windowfocus", "--sync", win])
    offset = LOG.stat().st_size
    run(["xdotool", "key", "Tab"])
    time.sleep(0.4)
    run(["xdotool", "key", "Return"])
    time.sleep(2.5)
    attempts = []
    for y in (120, 150, 180, 210, 240, 90, 270, 100):
        run(["xdotool", "mousemove", "--sync", "960", str(y)])
        time.sleep(0.3)
        run(["xdotool", "click", "--repeat", "2", "--delay", "120", "1"])
        deadline = time.time() + 40
        text = ""
        while time.time() < deadline:
            chunk, offset = log_from(offset)
            text += chunk
            if LOADED.search(text):
                break
            time.sleep(0.5)
        hit = bool(LOADED.search(text))
        attempts.append({"y": y, "loaded": hit, "tail": text[-300:] if hit else ""})
        print(json.dumps(attempts[-1]), flush=True)
        if hit:
            (ART / "evidence" / "world-load-navigation.json").write_text(
                json.dumps({"window": win, "attempts": attempts}, indent=2))
            return 0
        run(["xdotool", "key", "Escape"])
        time.sleep(0.8)
        run(["xdotool", "key", "Escape"])
        time.sleep(0.8)
        run(["xdotool", "key", "Tab"])
        time.sleep(0.4)
        run(["xdotool", "key", "Return"])
        time.sleep(2.0)
    raise SystemExit("world never loaded")


if __name__ == "__main__":
    raise SystemExit(main())
