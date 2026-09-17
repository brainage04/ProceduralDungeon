"""Stop the isolated resources this session used and record the teardown.

  * the Minecraft client and its Xvfb display are hub-managed processes
    (dungeon2-client / dungeon2-xvfb) - stopped by the caller, verified here
  * the PulseAudio null sink "dungeon2_capture" is unloaded here
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import time
from pathlib import Path

ART = Path(__file__).resolve().parent.parent
EVIDENCE = ART / "evidence"
SINK = "dungeon2_capture"
DISPLAY = ":247"


def sh(cmd: list[str]) -> str:
    return subprocess.run(cmd, capture_output=True, text=True).stdout


def main() -> int:
    time.sleep(2)
    journal = {"sink": SINK, "display": DISPLAY, "steps": []}

    sinks = sh(["pactl", "list", "short", "sinks"])
    journal["sink_before"] = [line for line in sinks.splitlines() if SINK in line]
    inputs = sh(["pactl", "list", "short", "sink-inputs"])
    journal["sink_inputs_before"] = inputs.strip().splitlines()
    module_ids = [line.split("\t")[0] for line in sh(["pactl", "list", "short", "modules"]).splitlines()
                  if SINK in line]
    journal["module_ids"] = module_ids
    for module_id in module_ids:
        sh(["pactl", "unload-module", module_id])
    time.sleep(1)
    journal["sink_after"] = [line for line in sh(["pactl", "list", "short", "sinks"]).splitlines()
                             if SINK in line]

    journal["client_processes"] = [line for line in sh(["pgrep", "-af", "KnotClient"]).splitlines()
                                   if "captures-dungeon2" in line]
    journal["xvfb_processes"] = [line for line in sh(["pgrep", "-af", "Xvfb"]).splitlines()
                                 if DISPLAY in line]
    journal["display_socket_present"] = Path(f"/tmp/.X11-unix/X{DISPLAY.lstrip(':')}").exists()
    journal["display_still_accepts_connections"] = bool(journal["display_socket_present"])
    (EVIDENCE / "cleanup.json").write_text(json.dumps(journal, indent=1))
    print(json.dumps(journal, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
