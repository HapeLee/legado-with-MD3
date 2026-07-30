#!/usr/bin/env python3
"""Capture repeatable Track C3 reader parity evidence from one Android device.

The script toggles the existing Lab Compose renderer flag through the app UI, restores the
previous Lab settings on exit, opens one book, captures Legacy/Compose pairs, and records
`gfxinfo framestats`. It intentionally does not install an APK or create reader data.
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as element_tree
import zlib
from datetime import datetime
from pathlib import Path


PACKAGE_DEFAULT = "io.legato.kazusa.debug"
MAIN_ACTIVITY_CLASS = "io.legado.app.ui.main.MainActivity"
LAB_ROUTE = "settings/lab_config"
READ_ROUTE = "book/read"
LAB_LABELS = ("Enable Lab", "启用实验室", "啟用實驗室")
COMPOSE_LABELS = (
    "Compose Reader Rendering",
    "Compose 阅读渲染",
    "Compose 閱讀渲染",
)
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


class BaselineError(RuntimeError):
    pass


class Adb:
    def __init__(self, serial: str | None):
        self.prefix = ["adb"] + (["-s", serial] if serial else [])

    def run(
        self,
        *args: str,
        capture: bool = True,
        timeout_seconds: float = 15.0,
    ) -> str:
        completed = subprocess.run(
            [*self.prefix, *args],
            check=True,
            stdout=subprocess.PIPE if capture else None,
            stderr=subprocess.PIPE,
            timeout=timeout_seconds,
        )
        return completed.stdout.decode("utf-8", errors="replace") if capture else ""

    def bytes(self, *args: str, timeout_seconds: float = 15.0) -> bytes:
        return subprocess.run(
            [*self.prefix, *args],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout_seconds,
        ).stdout


def connected_serial(requested: str | None) -> str:
    if requested:
        return requested
    output = subprocess.run(
        ["adb", "devices"], check=True, stdout=subprocess.PIPE, text=True
    ).stdout.splitlines()[1:]
    devices = [line.split("\t", 1)[0] for line in output if "\tdevice" in line]
    if len(devices) != 1:
        raise BaselineError("请用 --serial 指定唯一已连接且处于 device 状态的真机")
    return devices[0]


def screen_size(adb: Adb) -> tuple[int, int]:
    output = adb.run("shell", "wm", "size")
    match = re.search(r"(\d+)x(\d+)", output)
    if not match:
        raise BaselineError(f"无法解析屏幕尺寸：{output.strip()}")
    return int(match.group(1)), int(match.group(2))


def require_unlocked(adb: Adb) -> None:
    trust = adb.run("shell", "dumpsys", "trust")
    current_user = re.search(
        r"\(current\):.*?deviceLocked=(\d)",
        trust,
        flags=re.DOTALL,
    )
    if current_user is not None and current_user.group(1) == "1":
        raise BaselineError("真机已锁定；请解锁并保持屏幕常亮后重试")


def parse_bounds(raw: str) -> tuple[int, int]:
    values = [int(value) for value in re.findall(r"-?\d+", raw)]
    if len(values) != 4:
        raise BaselineError(f"无法解析控件 bounds：{raw}")
    return (values[0] + values[2]) // 2, (values[1] + values[3]) // 2


def dump_ui(adb: Adb) -> element_tree.Element:
    remote = "/sdcard/codex-reader-c3-window.xml"
    adb.run("shell", "uiautomator", "dump", remote)
    return element_tree.fromstring(adb.bytes("exec-out", "cat", remote))


def find_toggle(root: element_tree.Element, labels: tuple[str, ...]) -> element_tree.Element:
    parents = {child: parent for parent in root.iter() for child in parent}
    label_nodes = [node for node in root.iter("node") if node.attrib.get("text") in labels]
    for label in label_nodes:
        node: element_tree.Element | None = label
        while node is not None:
            if node.attrib.get("checkable") == "true":
                return node
            node = parents.get(node)
    names = ", ".join(labels)
    raise BaselineError(f"UI dump 中找不到可切换的设置：{names}")


def open_route(adb: Adb, package: str, route: str, extra: tuple[str, str] | None = None) -> None:
    command = [
        # MainActivity only consumes startRoute while establishing its navigation state. Reusing
        # the existing instance can leave the previous screen visible while the script clicks it.
        "shell", "am", "start", "-S", "-W", "-n", f"{package}/{MAIN_ACTIVITY_CLASS}",
        "--es", "startRoute", route,
    ]
    if extra is not None:
        command.extend(["--es", extra[0], extra[1]])
    adb.run(*command)


def switch_state(adb: Adb, labels: tuple[str, ...], target: bool, timeout: float = 6.0) -> bool:
    toggle = find_toggle(dump_ui(adb), labels)
    raw_state = toggle.attrib.get("checked")
    if raw_state not in ("true", "false"):
        raise BaselineError(f"设置未暴露 checked 状态：{labels[0]}")
    previous = raw_state == "true"
    if previous == target:
        return previous
    adb.run("shell", "input", "tap", *(str(value) for value in parse_bounds(toggle.attrib["bounds"])))
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        time.sleep(0.2)
        current = find_toggle(dump_ui(adb), labels).attrib.get("checked") == "true"
        if current == target:
            return previous
    raise BaselineError(f"切换 {labels[0]} 到 {target} 超时")


def set_compose_renderer(adb: Adb, package: str, enabled: bool) -> tuple[bool, bool]:
    open_route(adb, package, LAB_ROUTE)
    lab_was_enabled = switch_state(adb, LAB_LABELS, True)
    compose_was_enabled = switch_state(adb, COMPOSE_LABELS, enabled)
    return lab_was_enabled, compose_was_enabled


def restore_lab_state(adb: Adb, package: str, original: tuple[bool, bool] | None) -> None:
    if original is None:
        return
    lab_enabled, compose_enabled = original
    open_route(adb, package, LAB_ROUTE)
    switch_state(adb, LAB_LABELS, True)
    switch_state(adb, COMPOSE_LABELS, compose_enabled)
    switch_state(adb, LAB_LABELS, lab_enabled)


def capture(adb: Adb, path: Path) -> None:
    path.write_bytes(adb.bytes("exec-out", "screencap", "-p"))


def png_rows(path: Path) -> tuple[int, int, int, list[bytearray]]:
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise BaselineError(f"不是 PNG：{path}")
    pos = len(PNG_SIGNATURE)
    width = height = bit_depth = color_type = None
    compressed = bytearray()
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(
                ">IIBBBBB", payload
            )
            if bit_depth != 8 or color_type not in (2, 6) or compression or filtering or interlace:
                raise BaselineError(f"不支持的 PNG 格式：{path}")
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            break
    if width is None or height is None or color_type is None:
        raise BaselineError(f"PNG 缺少 IHDR：{path}")
    channels = 4 if color_type == 6 else 3
    stride = width * channels
    raw = zlib.decompress(compressed)
    rows: list[bytearray] = []
    offset = 0
    for _ in range(height):
        filter_type = raw[offset]
        offset += 1
        row = bytearray(raw[offset:offset + stride])
        offset += stride
        previous = rows[-1] if rows else bytearray(stride)
        for index, value in enumerate(row):
            left = row[index - channels] if index >= channels else 0
            up = previous[index]
            up_left = previous[index - channels] if index >= channels else 0
            if filter_type == 1:
                row[index] = (value + left) & 0xFF
            elif filter_type == 2:
                row[index] = (value + up) & 0xFF
            elif filter_type == 3:
                row[index] = (value + ((left + up) // 2)) & 0xFF
            elif filter_type == 4:
                p = left + up - up_left
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - up_left)
                row[index] = (value + (left if pa <= pb and pa <= pc else up if pb <= pc else up_left)) & 0xFF
            elif filter_type != 0:
                raise BaselineError(f"不支持的 PNG filter：{filter_type}")
        rows.append(row)
    return width, height, channels, rows


def compare_pngs(legacy: Path, compose: Path, crop: tuple[int, int, int, int] | None) -> dict[str, float | int | list[int]]:
    lw, lh, lc, legacy_rows = png_rows(legacy)
    cw, ch, cc, compose_rows = png_rows(compose)
    if (lw, lh) != (cw, ch):
        raise BaselineError("Legacy 与 Compose 截图尺寸不同，无法比较")
    left, top, right, bottom = crop or (0, 0, lw, lh)
    if not (0 <= left < right <= lw and 0 <= top < bottom <= lh):
        raise BaselineError(f"无效裁剪区域：{crop}")
    total_difference = 0
    changed_pixels = 0
    pixels = (right - left) * (bottom - top)
    for y in range(top, bottom):
        legacy_row, compose_row = legacy_rows[y], compose_rows[y]
        for x in range(left, right):
            legacy_offset, compose_offset = x * lc, x * cc
            differences = [abs(legacy_row[legacy_offset + channel] - compose_row[compose_offset + channel]) for channel in range(3)]
            total_difference += sum(differences)
            if max(differences) > 8:
                changed_pixels += 1
    return {
        "crop": [left, top, right, bottom],
        "meanAbsoluteChannelDifference": total_difference / (pixels * 3),
        "changedPixelCount": changed_pixels,
        "changedPixelPercent": changed_pixels * 100 / pixels,
    }


def parse_crop(value: str | None) -> tuple[int, int, int, int] | None:
    if value is None:
        return None
    values = [int(item) for item in value.split(",")]
    if len(values) != 4:
        raise argparse.ArgumentTypeError("--crop 采用 left,top,right,bottom")
    return tuple(values)  # type: ignore[return-value]


def parse_framestats(path: Path) -> dict[str, int | float]:
    text = path.read_text(encoding="utf-8")

    def required(pattern: str, label: str) -> str:
        match = re.search(pattern, text)
        if not match:
            raise BaselineError(f"无法从 {path.name} 解析 {label}")
        return match.group(1)

    return {
        "totalFrames": int(required(r"Total frames rendered:\s+(\d+)", "总帧数")),
        "jankyFrames": int(required(r"Janky frames:\s+(\d+)", "卡顿帧数")),
        "jankyPercent": float(required(r"Janky frames:\s+\d+\s+\(([\d.]+)%\)", "卡顿率")),
        "p50Ms": int(required(r"50th percentile:\s+(\d+)ms", "P50")),
        "p90Ms": int(required(r"90th percentile:\s+(\d+)ms", "P90")),
        "p95Ms": int(required(r"95th percentile:\s+(\d+)ms", "P95")),
        "p99Ms": int(required(r"99th percentile:\s+(\d+)ms", "P99")),
    }


def run_mode(adb: Adb, args: argparse.Namespace, output: Path, mode: str, size: tuple[int, int]) -> dict[str, object]:
    set_compose_renderer(adb, args.package, mode == "compose")
    book_extra = ("bookUrl", args.book_url) if args.book_url else None
    open_route(adb, args.package, READ_ROUTE, book_extra)
    time.sleep(args.settle_seconds)
    visible_texts = {node.attrib.get("text") for node in dump_ui(adb).iter("node")}
    if any(label in visible_texts for label in (*LAB_LABELS, *COMPOSE_LABELS)):
        raise BaselineError(f"{mode} 模式未进入阅读页，仍停留在实验室设置")
    plain = output / f"plain-page-{mode}.png"
    capture(adb, plain)

    adb.run("shell", "dumpsys", "gfxinfo", args.package, "reset")
    next_x, next_y = int(size[0] * args.next_x_ratio), int(size[1] * args.tap_y_ratio)
    prev_x, prev_y = int(size[0] * args.prev_x_ratio), int(size[1] * args.tap_y_ratio)
    forward = output / f"forward-turn-{mode}.png"
    forward_difference: dict[str, float | int | list[int]] | None = None
    for round_index in range(args.rounds):
        for _ in range(args.turns):
            adb.run("shell", "input", "tap", str(next_x), str(next_y))
            time.sleep(args.tap_delay)
        if round_index == 0:
            time.sleep(args.direction_settle_seconds)
            capture(adb, forward)
            forward_difference = compare_pngs(plain, forward, args.crop)
            if forward_difference["changedPixelPercent"] < 1.0:
                raise BaselineError(f"{mode} 模式前进 {args.turns} 页后画面未发生有效变化")
        for _ in range(args.turns):
            adb.run("shell", "input", "tap", str(prev_x), str(prev_y))
            time.sleep(args.tap_delay)
    time.sleep(args.settle_seconds)
    rapid = output / f"rapid-turn-{mode}.png"
    capture(adb, rapid)
    return_difference = compare_pngs(plain, rapid, args.crop)
    if return_difference["changedPixelPercent"] > 1.0:
        raise BaselineError(
            f"{mode} 模式完成 {args.rounds} 轮往返后未回到起点："
            f"差异像素 {return_difference['changedPixelPercent']:.2f}%"
        )
    framestats = output / f"{mode}-framestats.txt"
    framestats.write_text(
        adb.run("shell", "dumpsys", "gfxinfo", args.package, "framestats"), encoding="utf-8"
    )
    return {
        "plain": plain.name,
        "forwardTurn": forward.name,
        "rapidTurn": rapid.name,
        "plainVsForward": forward_difference,
        "plainVsRapidTurn": return_difference,
        "frameStats": parse_framestats(framestats),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--book-url", help="书架中可打开的 bookUrl；省略时自动使用最后阅读的书")
    parser.add_argument("--serial", help="adb device serial；未指定时要求仅连接一台设备")
    parser.add_argument("--package", default=PACKAGE_DEFAULT)
    parser.add_argument("--turns", type=int, default=20)
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--tap-delay", type=float, default=0.18)
    parser.add_argument("--settle-seconds", type=float, default=1.5)
    parser.add_argument("--direction-settle-seconds", type=float, default=1.0)
    parser.add_argument("--next-x-ratio", type=float, default=0.84)
    parser.add_argument("--prev-x-ratio", type=float, default=0.16)
    parser.add_argument("--tap-y-ratio", type=float, default=0.50)
    parser.add_argument("--crop", type=parse_crop, help="正文比较区域：left,top,right,bottom")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    if args.turns < 1:
        parser.error("--turns 必须大于 0")
    if args.rounds < 1:
        parser.error("--rounds 必须大于 0")

    serial = connected_serial(args.serial)
    adb = Adb(serial)
    require_unlocked(adb)

    output = args.out or Path("build/reader-c3-baseline") / datetime.now().strftime("%Y%m%d-%H%M%S")
    try:
        output.mkdir(parents=True, exist_ok=False)
    except FileExistsError as error:
        raise BaselineError(f"输出目录已存在：{output}；请换一个 --out 路径") from error
    original: tuple[bool, bool] | None = None
    try:
        original = set_compose_renderer(adb, args.package, False)
        size = screen_size(adb)
        artifacts = {mode: run_mode(adb, args, output, mode, size) for mode in ("legacy", "compose")}
        comparisons = {
            scenario: compare_pngs(
                output / artifacts["legacy"][scenario], output / artifacts["compose"][scenario], args.crop
            )
            for scenario in ("plain", "rapidTurn")
        }
        metadata = {
            "serial": serial,
            "package": args.package,
            "bookUrl": args.book_url,
            "screen": list(size),
            "turns": args.turns,
            "rounds": args.rounds,
            "crop": args.crop,
            "artifacts": artifacts,
            "comparisons": comparisons,
            "device": adb.run("shell", "getprop").splitlines(),
        }
        (output / "result.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps({"output": str(output), "comparisons": comparisons}, ensure_ascii=False, indent=2))
        return 0
    finally:
        try:
            restore_lab_state(adb, args.package, original)
        except Exception as error:  # Preserve the evidence even if settings restoration fails.
            print(f"警告：未能还原实验室设置：{error}", file=sys.stderr)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (BaselineError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        print(f"C3 基线采集失败：{error}", file=sys.stderr)
        raise SystemExit(2)
