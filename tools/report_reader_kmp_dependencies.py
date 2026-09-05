#!/usr/bin/env python3
"""Report the Track F KMP/CMP dependency inventory for the reader surface.

The report classifies every Kotlin file under the reader source roots by the platform
APIs it imports, then groups platform imports into the migration contract candidates
listed in `docs/dev/track-f-reader-kmp-migration-plan.md` section 3.

The classification is deliberately mechanical: it reports evidence, it does not decide.
A file marked `common-ready` still needs a non-Android compile to be believed, and a file
marked `platform-island` may still be reachable through a narrow contract.

Usage:
    python3 tools/report_reader_kmp_dependencies.py
    python3 tools/report_reader_kmp_dependencies.py --output build/reports/reader-kmp-deps.md
    python3 tools/report_reader_kmp_dependencies.py --top-n 30
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

SCAN_ROOTS = (
    "app/src/main/java/io/legado/app/ui/book/read",
    "app/src/main/java/io/legado/app/feature/reader",
)
TEST_ROOT = "app/src/test/java/io/legado/app"

IMPORT_RE = re.compile(r"^\s*import\s+([A-Za-z0-9_.]+)")

# import prefix -> (contract name, note). Only used to group evidence; the plan decides
# whether each group becomes a real contract.
#
# Order matters: the first matching prefix wins, so more specific prefixes must come first.
# `android.text.style` (custom drawing spans) is listed before `android.text` (shaping /
# HTML) because spans are a platform-island while shaping is a contract candidate.
CONTRACT_HINTS: tuple[tuple[str, str, str], ...] = (
    ("android.text.style", "platform-island", "自定义绘制 Span，随渲染器留在 Android"),
    ("android.text", "ReaderTextMeasurer / ReaderRichTextParser", "中文整形与 HTML 富文本解析"),
    ("android.graphics.Bitmap", "ReaderImageDecoder", "位图解码"),
    ("android.graphics.BitmapFactory", "ReaderImageDecoder", "位图解码"),
    ("android.graphics.Canvas", "platform-island", "nativeCanvas 逃逸绘制"),
    ("android.graphics.drawable", "ReaderImageDecoder", "Drawable 资源"),
    ("android.graphics", "platform-island", "Paint / Path / Typeface / Shader 等渲染原语"),
    ("com.caverock.androidsvg", "ReaderVectorPathParser", "SVG 下划线与装饰路径"),
    ("androidx.core.graphics", "ReaderVectorPathParser", "PathParser"),
    ("android.content", "宿主 Effect / FileAccess", "Context 与 Intent，走 Effect 回调不进共享层"),
    ("android.app", "宿主 Effect / FileAccess", "Activity / Application / 搜索"),
    ("android.net", "宿主 Effect / FileAccess", "Uri"),
    ("android.provider", "宿主 Effect / FileAccess", "文件选择"),
    ("android.speech.tts", "TTS capability", "朗读，无 TTS 的 target 显式返回不支持"),
    ("android.view", "platform-island", "View / 按键 / 窗口 / 振动"),
    ("android.widget", "platform-island", "Toast"),
    ("android.hardware", "platform-island", "传感器（自动翻页/亮度）"),
    ("android.os", "platform-island", "SystemClock / Build / 电源"),
    ("android.util", "platform-island", "LruCache / Log"),
    ("android.annotation", "platform-island", "SuppressLint 等注解"),
)

# Only this androidx import is tolerated inside otherwise-pure shared code: it is an
# annotation with a CMP equivalent, not a platform dependency.
PURE_TOLERATED_ANDROIDX = {"androidx.compose.runtime.Stable"}


def rel(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def read_imports(path: Path) -> set[str]:
    imports: set[str] = set()
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:  # pragma: no cover - defensive
        print(f"warning: cannot read {rel(path)}: {exc}", file=sys.stderr)
        return imports
    for line in text.splitlines():
        match = IMPORT_RE.match(line)
        if match:
            imports.add(match.group(1))
    return imports


def classify(imports: set[str]) -> str:
    android = {i for i in imports if i == "android" or i.startswith("android.")}
    androidx = {i for i in imports if i.startswith("androidx.")}
    if android:
        return "platform-island"
    if androidx:
        return "common-ready" if androidx <= PURE_TOLERATED_ANDROIDX else "androidx-only"
    return "common-ready"


def short_group(imp: str) -> str:
    parts = imp.split(".")
    if len(parts) <= 3:
        return imp
    return ".".join(parts[:3])


def collect(root: Path) -> list[tuple[Path, set[str]]]:
    files: list[tuple[Path, set[str]]] = []
    for scan_root in SCAN_ROOTS:
        base = root / scan_root
        if not base.is_dir():
            print(f"warning: missing scan root {rel(base)}", file=sys.stderr)
            continue
        for path in sorted(base.rglob("*.kt")):
            files.append((path, read_imports(path)))
    return files


def test_mirror(path: Path, root: Path) -> str:
    """Map a main source file to its conventional unit-test location."""
    marker = "app/src/main/java/"
    text = rel(path)
    if marker not in text:
        return "-"
    candidate = root / text.replace(marker, "app/src/test/java/", 1)
    stem = candidate.stem
    for suffix in ("Test.kt", "Tests.kt"):
        probe = candidate.with_name(f"{stem}{suffix}")
        if probe.is_file():
            return probe.relative_to(root).as_posix()
    return "-"


def build_report(root: Path, top_n: int) -> str:
    files = collect(root)
    if not files:
        return "no source files found"

    buckets: dict[str, list[Path]] = defaultdict(list)
    by_dir: dict[str, Counter] = defaultdict(Counter)
    api_hist: Counter = Counter()
    contracts: dict[str, set[str]] = defaultdict(set)
    unmatched: Counter = Counter()

    for path, imports in files:
        kind = classify(imports)
        buckets[kind].append(path)
        parent = rel(path.parent)
        by_dir[parent][kind] += 1

        android = {i for i in imports if i == "android" or i.startswith("android.")}
        for imp in android:
            api_hist[short_group(imp)] += 1
            for prefix, contract, _note in CONTRACT_HINTS:
                if imp.startswith(prefix):
                    contracts[contract].add(rel(path))
                    break
            else:
                unmatched[imp] += 1

    out: list[str] = []
    out.append("# 阅读界面 KMP/CMP 依赖清单")
    out.append("")
    out.append(f"生成：{__import__('datetime').datetime.now().isoformat(timespec='seconds')}")
    out.append("")
    out.append(
        "> 机械分类，只报证据不做判定。`common-ready` 仍需非 Android 编译才算成立；"
        "`platform-island` 也可能经窄契约进入共享层。分类口径见 "
        "`docs/dev/track-f-reader-kmp-migration-plan.md` §3。"
    )
    out.append("")

    total = len(files)
    out.append("## 1. 汇总")
    out.append("")
    out.append("| 分类 | 文件数 | 占比 |")
    out.append("|---|---|---|")
    for kind in ("common-ready", "androidx-only", "platform-island"):
        count = len(buckets[kind])
        out.append(f"| `{kind}` | {count} | {count / total:.0%} |")
    out.append(f"| **合计** | **{total}** | 100% |")
    out.append("")

    out.append("## 2. 按目录分布")
    out.append("")
    out.append("| 目录 | common-ready | androidx-only | platform-island |")
    out.append("|---|---|---|---|")
    for parent in sorted(by_dir):
        counter = by_dir[parent]
        out.append(
            f"| `{parent}` | {counter['common-ready']} | "
            f"{counter['androidx-only']} | {counter['platform-island']} |"
        )
    out.append("")

    out.append(f"## 3. `android.*` API 直方图（前 {top_n}）")
    out.append("")
    out.append("| API 组 | 引用文件数 |")
    out.append("|---|---|")
    for api, count in api_hist.most_common(top_n):
        out.append(f"| `{api}` | {count} |")
    out.append("")

    out.append("## 4. 契约候选（按 import 前缀分组）")
    out.append("")
    # Several prefixes can feed one contract, so aggregate by contract name instead of
    # emitting one section per prefix.
    contract_notes: dict[str, list[str]] = defaultdict(list)
    for _prefix, contract, note in CONTRACT_HINTS:
        if note not in contract_notes[contract]:
            contract_notes[contract].append(note)
    for contract in sorted(contracts, key=lambda c: (c == "platform-island", c)):
        files_for_contract = sorted(contracts[contract])
        out.append(f"### {contract}")
        out.append("")
        out.append("；".join(contract_notes[contract]) + "。")
        out.append("")
        out.append(f"命中 {len(files_for_contract)} 个文件：")
        out.append("")
        for item in files_for_contract:
            out.append(f"- `{item}`")
        out.append("")

    out.append("## 5. `common-ready` 文件清单")
    out.append("")
    out.append(
        "这些文件没有 `android.*` import；其中 `androidx-only` 之外的条目是 F2 的首选搬迁对象。"
    )
    out.append("")
    for path in sorted(buckets["common-ready"], key=rel):
        imports = read_imports(path)
        tolerated = sorted(imports & PURE_TOLERATED_ANDROIDX)
        suffix = f"（容忍：{', '.join(tolerated)}）" if tolerated else ""
        out.append(f"- `{rel(path)}`{suffix}")
    out.append("")

    out.append("## 6. `platform-island` 文件清单与单测对应")
    out.append("")
    out.append("| 文件 | 单测 |")
    out.append("|---|---|")
    for path in sorted(buckets["platform-island"], key=rel):
        out.append(f"| `{rel(path)}` | `{test_mirror(path, root)}` |")
    out.append("")

    if unmatched:
        out.append("## 7. 未归入已知契约的 `android.*` import")
        out.append("")
        out.append("出现在这里说明计划 §3 的契约分组尚未覆盖，需要人工判定。")
        out.append("")
        out.append("| import | 次数 |")
        out.append("|---|---|")
        for imp, count in unmatched.most_common(top_n):
            out.append(f"| `{imp}` | {count} |")
        out.append("")

    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-o", "--output", help="写入文件（默认输出到 stdout）")
    parser.add_argument("--top-n", type=int, default=25, help="直方图与未匹配清单的条目上限")
    parser.add_argument(
        "--repo-root", default=str(REPO_ROOT), help=f"仓库根目录（默认 {REPO_ROOT}）"
    )
    args = parser.parse_args()

    root = Path(args.repo_root).resolve()
    report = build_report(root, args.top_n)

    if args.output:
        target = Path(args.output)
        if not target.is_absolute():
            target = root / target
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(report, encoding="utf-8")
        print(f"wrote {target}")
    else:
        print(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
