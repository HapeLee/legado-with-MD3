#!/usr/bin/env python3
"""Verify that Compose pagination works while the legacy reader surface is laid out at 0x0."""

from __future__ import annotations

import argparse
import json
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
    capture,
    compare_pngs,
    connected_serial,
    require_unlocked,
    restore_lab_state,
    screen_size,
    set_compose_renderer,
)


DETACH_EXTRA = "readerDetachLegacySurface"
FIRST_FRAME_EXTRA = "readerFirstFrameStartedAtNanos"


def elapsed_realtime_nanos(adb: Adb) -> int:
    seconds = float(adb.run("shell", "cat", "/proc/uptime").split()[0])
    return int(seconds * 1_000_000_000)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial")
    parser.add_argument("--package", default=PACKAGE_DEFAULT)
    parser.add_argument("--settle-seconds", type=float, default=2.0)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()

    serial = connected_serial(args.serial)
    adb = Adb(serial)
    require_unlocked(adb)
    output = args.out or Path("build/reader-c4-detached") / datetime.now().strftime("%Y%m%d-%H%M%S")
    output.mkdir(parents=True, exist_ok=False)
    original: tuple[bool, bool] | None = None
    try:
        original = set_compose_renderer(adb, args.package, True)
        adb.run("logcat", "-c")
        started_at = elapsed_realtime_nanos(adb)
        adb.run(
            "shell", "am", "start", "-S", "-W",
            "-n", f"{args.package}/{MAIN_ACTIVITY_CLASS}",
            "--es", "startRoute", READ_ROUTE,
            "--ez", DETACH_EXTRA, "true",
            "--el", FIRST_FRAME_EXTRA, str(started_at),
        )
        time.sleep(args.settle_seconds)
        before = output / "before.png"
        after = output / "after.png"
        capture(adb, before)
        width, height = screen_size(adb)
        adb.run("shell", "input", "tap", str(int(width * 0.84)), str(int(height * 0.5)))
        time.sleep(args.settle_seconds)
        capture(adb, after)
        difference = compare_pngs(before, after, None)
        if difference["changedPixelPercent"] < 1.0:
            raise BaselineError("legacy surface 为 0x0 时，Compose 点击后画面未发生有效变化")
        logs = adb.run("logcat", "-d", "-s", "ReaderDetachedLegacy:I", "ReaderFirstFrame:I", "*:S")
        if "legacySurface=0x0" not in logs:
            raise BaselineError("未观察到 committed detach 分支日志")
        if "renderer=compose" not in logs:
            raise BaselineError("未观察到 Compose 首个非空正文帧")
        result = {"serial": serial, "difference": difference, "logs": logs.strip().splitlines()}
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
