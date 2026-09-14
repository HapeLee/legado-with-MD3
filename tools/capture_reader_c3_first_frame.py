#!/usr/bin/env python3
"""Capture cold route-to-first-content draw timings for both reader renderers."""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from datetime import datetime
from pathlib import Path

sys.dont_write_bytecode = True

from capture_reader_c3_baseline import (  # noqa: E402
    Adb,
    BaselineError,
    MAIN_ACTIVITY_CLASS,
    PACKAGE_DEFAULT,
    READ_ROUTE,
    connected_serial,
    require_unlocked,
    restore_lab_state,
    set_compose_renderer,
)


FIRST_FRAME_EXTRA = "readerFirstFrameStartedAtNanos"
FRAME_PATTERN = re.compile(
    r"renderer=(legacy|compose(?:-canvas)?) (?:phase=(loading|content) )?durationMs=([\d.]+)"
)


def elapsed_realtime_nanos(adb: Adb) -> int:
    seconds = float(adb.run("shell", "cat", "/proc/uptime").split()[0])
    return int(seconds * 1_000_000_000)


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, int((len(ordered) - 1) * fraction + 0.5))
    return ordered[index]


def capture_mode(adb: Adb, package: str, mode: str, runs: int) -> dict[str, object]:
    set_compose_renderer(adb, package, mode == "compose")
    durations: list[float] = []
    launches: list[str] = []
    for _ in range(runs):
        adb.run("logcat", "-c")
        started_at = elapsed_realtime_nanos(adb)
        launches.append(
            adb.run(
                "shell", "am", "start", "-S", "-W",
                "-n", f"{package}/{MAIN_ACTIVITY_CLASS}",
                "--es", "startRoute", READ_ROUTE,
                "--el", FIRST_FRAME_EXTRA, str(started_at),
            )
        )
        deadline = time.monotonic() + 10.0
        duration: float | None = None
        while time.monotonic() < deadline:
            logs = adb.run("logcat", "-d", "-s", "ReaderFirstFrame:I", "*:S")
            matches = FRAME_PATTERN.findall(logs)
            matching = [
                float(value)
                for renderer, phase, value in matches
                if (renderer == mode or renderer.startswith(f"{mode}-"))
                and phase in ("", "content")
            ]
            if matching:
                duration = matching[-1]
                break
            time.sleep(0.2)
        if duration is None:
            raise BaselineError(f"{mode} 冷启动未观察到首个非空正文帧")
        durations.append(duration)
    return {
        "durationsMs": durations,
        "p50Ms": percentile(durations, 0.50),
        "p90Ms": percentile(durations, 0.90),
        "launches": launches,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial")
    parser.add_argument("--package", default=PACKAGE_DEFAULT)
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    if args.runs < 1:
        parser.error("--runs 必须大于 0")

    serial = connected_serial(args.serial)
    adb = Adb(serial)
    require_unlocked(adb)
    output = args.out or Path("build/reader-c3-first-frame") / datetime.now().strftime("%Y%m%d-%H%M%S")
    output.mkdir(parents=True, exist_ok=False)
    original: tuple[bool, bool] | None = None
    try:
        original = set_compose_renderer(adb, args.package, False)
        modes = {
            mode: capture_mode(adb, args.package, mode, args.runs)
            for mode in ("legacy", "compose")
        }
        result = {"serial": serial, "runs": args.runs, "modes": modes}
        (output / "result.json").write_text(
            json.dumps(result, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        print(json.dumps({"output": str(output), **result}, ensure_ascii=False, indent=2))
        return 0
    finally:
        restore_lab_state(adb, args.package, original)


if __name__ == "__main__":
    raise SystemExit(main())
