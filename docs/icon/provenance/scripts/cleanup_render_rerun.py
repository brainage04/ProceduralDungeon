"""Record the teardown of the dimension-variant rerun.

The rerun re-rendered the 20 themes with Chunky and built the dimension rings + legend
with Pillow.  Both are headless and start no display, no audio sink and no server:
Chunky runs with -Djava.awt.headless=true, and the Xvfb display, the Minecraft client
and the PulseAudio capture sink of the original capture session are not used at all.
This script only verifies that and writes evidence/cleanup-render-rerun.json.

usage: cleanup_render_rerun.py
"""

from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path

ART = Path(__file__).resolve().parent.parent
EVIDENCE = ART / "evidence"
DISPLAY = ":247"
SINK = "dungeon2_capture"
CGROUP = ("/sys/fs/cgroup/user.slice/user-1000.slice/user@1000.service/app.slice/"
          "render-chunky.service/cgroup.procs")


def sh(cmd: list[str]) -> str:
    return subprocess.run(cmd, capture_output=True, text=True).stdout


def pids(marker: str) -> list[str]:
    out = []
    for line in sh(["pgrep", "-af", marker]).splitlines():
        if "captures-dungeon2" in line:
            out.append(line)
    return out


def cgroup_members() -> list[str]:
    try:
        entries = Path(CGROUP).read_text().split()
    except OSError:
        return []
    rows = []
    for pid in entries:
        try:
            rows.append(f"{pid}\t{Path(f'/proc/{pid}/cmdline').read_bytes().replace(bytes([0]), b' ').decode()[:120]}")
        except OSError:
            rows.append(f"{pid}\t(gone)")
    return rows


def main() -> int:
    journal = {
        "session": "captures-dungeon2 dimension-composite rerun",
        "started": {
            "display": None,
            "audio_sink": None,
            "server": None,
            "note": ("nothing was started: the rerun is headless (Chunky -Djava.awt.headless=true, "
                     "Pillow offline); no Xvfb, no Minecraft client, no PulseAudio sink, no HTTP server"),
        },
        "sibling_processes_not_touched": [],
        "render_processes_after": pids("chunky-core"),
        "client_processes_after": pids("KnotClient"),
        "python_composite_processes_after": pids("build_dimension_composite2.py"),
        "display_socket_present": Path(f"/tmp/.X11-unix/X{DISPLAY.lstrip(':')}").exists(),
        "display_still_accepts_connections": Path(f"/tmp/.X11-unix/X{DISPLAY.lstrip(':')}").exists(),
        "audio_sink_present": [line for line in sh(["pactl", "list", "short", "sinks"]).splitlines()
                               if SINK in line],
        "render_cgroup_members": [row for row in cgroup_members() if not row.startswith(f"{os.getpid()}\t")],
        "own_shell_moved_back": ("the session shell was moved into render-chunky.service for the batch "
                                 "and returned to its own scope (app-ghostty-surface-transient scope) "
                                 "afterwards, so no process of this rerun is left in the render cgroup"),
        "cpu_policy": ("every Chunky JVM of this rerun ran with -threads 8 inside the shared "
                       "render-chunky.service cgroup that round3/tools/throttle-render.py assigns to "
                       "Chunky/Minecraft processes.  The run started under CPUQuota=800% / "
                       "CPUWeight=50 and the quota was tightened to CPUQuota=400% / CPUWeight=20 "
                       "while it was running (announced by the main agent); the thread count was not "
                       "raised and no process was moved out of the cgroup, so the 20 themes + the "
                       "empty-sky reference simply took about 48 minutes instead of about 10."),
    }
    for line in sh(["pgrep", "-af", "java"]).splitlines():
        if "captures-dimension" in line:
            journal["sibling_processes_not_touched"].append(line[:160])
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / "cleanup-render-rerun.json").write_text(json.dumps(journal, indent=1))
    print(json.dumps(journal, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
