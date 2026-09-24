#!/usr/bin/env python3
"""切片前置勘察：把一个目录里的每个 Kotlin 文件的依赖按「模块 + 源集」定性。

用法：
    python tools/audit-slice-deps.py app/src/main/java/io/legado/app/ui/config/themeConfig
    python tools/audit-slice-deps.py app/src/main/java/io/legado/app/ui/config        # 整个子树

为什么要有这个工具（三次踩坑换来的，见 slice-checklist）：

  ① **只索引大写符号（类）不够**：本仓库的 `:app` 私有耦合大量来自**顶层函数与扩展属性**
     （`postEvent` / `toastOnUi` / `getCompatDrawable` / `externalFiles` / `inputStream` …），
     它们在 import 里是小写。⇒ 本工具索引 class/interface/object/typealias + fun（含
     `fun Type.name(`）+ val/var（含扩展属性）。
  ② **必须区分源集**：定义在 `androidMain` / Android-only 模块（如 `:core:ui`）的符号，
     共享层的 `commonMain` **看不到**。⇒ 判定标准是「定义落在 `<模块>/src/commonMain`」。
  ③ **`:app` 也要进索引**：`:app` 的路径是 `app/src/main/...`（**没有模块段**），按
     `<模块>/src/<源集>` 形态写正则会一条都匹配不上 ⇒ `ThemePackageManager` / `SavedTheme`
     这类真阻塞会被静默漏报。⇒ 本工具显式处理 `:app`，且**「查不到」会报出来**而不是跳过。
  ④ 顺带列出平台/第三方 import（`android.` / `java.` / `androidx.`(非 compose) / 其它非
     `io.legado.app` 的顶层包），因为它们同样进不了 `commonMain` —— 但**第三方 artifact
     是否可用属于目标模块的 build 文件**，需人工核对（见 M5-15b：`miuix-preference` 没有
     desktop 变体）。
"""

from __future__ import annotations

import pathlib
import re
import sys
from collections import defaultdict

sys.stdout.reconfigure(encoding="utf-8")

ROOT = pathlib.Path(".")
SKIP_PARTS = {"build", ".git", ".gradle", ".idea", "tmp", "node_modules"}

# 顶层声明（本仓库风格：顶层声明不缩进）
PREFIX = (
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+|expect\s+|actual\s+|open\s+|abstract\s+|sealed\s+|"
    r"data\s+|value\s+|enum\s+|annotation\s+|const\s+|override\s+|suspend\s+|inline\s+|"
    r"operator\s+|infix\s+|external\s+|tailrec\s+|lateinit\s+)*"
)
DECL_PATTERNS = [
    re.compile(PREFIX + r"(?:class|interface|object|typealias)\s+(\w+)", re.M),
    re.compile(PREFIX + r"fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?\[\]]+\.)?(\w+)\s*\(", re.M),
    re.compile(PREFIX + r"(?:val|var)\s+(?:[\w.<>?\[\]]+\.)?(\w+)\s*[:=]", re.M),
]
IMPORT = re.compile(r"^import\s+([\w.]+)", re.M)
PKG = re.compile(r"^package\s+([\w.]+)", re.M)

LOC_MULTI = re.compile(r"^(app|core|feature|domain|data|host|smoke)/([^/]+)/src/([^/]+)/")
LOC_APP = re.compile(r"^app/src/([^/]+)/")

# 平台/第三方：进不了 commonMain（判断第三方 artifact 是否可用仍需看目标模块的 build 文件）
PLATFORM = re.compile(
    r"^(?:android\.|java\.|javax\.|"
    r"androidx\.(?!compose\.(?:foundation|material|runtime|ui|animation)|lifecycle\.)|"
    r"com\.google\.gson|org\.apache\.|okhttp3\.|okio\.|retrofit2\.)"
)


def locate(path: pathlib.Path) -> str | None:
    posix = path.as_posix()
    m = LOC_APP.match(posix)
    if m:
        return "app:main:{}".format(m.group(1))
    m = LOC_MULTI.match(posix)
    if not m:
        return None
    return "{}:{}:{}".format(m.group(1), m.group(2), m.group(3))


def build_index() -> tuple[dict[str, set[str]], dict[str, set[str]]]:
    """返回 (FQN 索引, 简单名索引)。

    ⚠️ **主索引用 FQN**：本仓存在同名不同包的符号（`AppModalBottomSheet` 在 `:app` 与
    designsystem 各有一个）⇒ 按简单名解析必然出现假阳性。简单名索引只作回退，且回退时会标注。
    """
    by_fqn: dict[str, set[str]] = defaultdict(set)
    by_simple: dict[str, set[str]] = defaultdict(set)
    for p in ROOT.rglob("*.kt"):
        if any(part in SKIP_PARTS for part in p.parts):
            continue
        loc = locate(p)
        if loc is None:
            continue
        try:
            text = p.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        m = PKG.search(text)
        pkg = m.group(1) if m else None
        names: set[str] = set()
        for pat in DECL_PATTERNS:
            names |= set(pat.findall(text))
        for name in names:
            by_simple[name].add(loc)
            if pkg:
                by_fqn["{}.{}".format(pkg, name)].add(loc)
    return by_fqn, by_simple


def verdict(locs: set[str]) -> str:
    """按**定义位置**给符号定性。

    ⚠️ 简单名会撞车：本仓存在「同名但不同包」的符号（如 `AppModalBottomSheet` 在 `:app` 与
    `designsystem/commonMain` 各有一个）。此时不能一律判成 `:app` 私有 —— 要看**该文件 import
    的 FQN**。所以这里返回 `both` 并提示人工核对，而不是给出一个看似确定的结论。
    """
    has_app = any(l.startswith("app:") for l in locs)
    has_common = any(":commonMain" in l for l in locs)
    if has_app and has_common:
        return "both"
    if has_app:
        return "app"
    if has_common:
        return "shared"
    return "non-common"


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    target = pathlib.Path(sys.argv[1])
    if not target.exists():
        print("目录不存在:", target)
        return 2

    by_fqn, by_simple = build_index()
    files = sorted(target.rglob("*.kt"))
    print("=== 勘察 {} （{} 个文件）===".format(target.as_posix(), len(files)))
    totals = {"lines": 0, "app": 0, "non-common": 0, "unresolved": 0}
    for p in files:
        text = p.read_text(encoding="utf-8", errors="ignore")
        lines = text.splitlines()
        totals["lines"] += len(lines)
        app_deps: list[str] = []
        both_deps: list[str] = []
        non_common: list[str] = []
        unresolved: list[str] = []
        platform: list[str] = []
        for imp in sorted(set(IMPORT.findall(text))):
            if imp.startswith("io.legado.app."):
                symbol = imp.split(".")[-1]
                if symbol == "R":
                    continue
                locs = by_fqn.get(imp)
                exact = locs is not None
                if locs is None:
                    locs = by_simple.get(symbol)
                if locs is None:
                    unresolved.append(symbol)
                    continue
                v = verdict(locs)
                entry = "{} [{}]{}".format(
                    symbol, ",".join(sorted(locs)),
                    "" if exact else " (简单名回退，需核对)",
                )
                if v == "app":
                    app_deps.append(entry)
                elif v == "both":
                    both_deps.append(entry)
                elif v == "non-common":
                    non_common.append(entry)
            elif "compose" not in imp and PLATFORM.match(imp):
                platform.append(imp)
        totals["app"] += len(app_deps)
        totals["non-common"] += len(non_common)
        totals["unresolved"] += len(unresolved)
        print("  --- {} ({} 行) ---".format(p.relative_to(target).as_posix(), len(lines)))
        if app_deps:
            print("      ⛔ :app 私有 ：{}".format(app_deps))
        if both_deps:
            print("      🔍 同名两处（看 import 的 FQN 定）：{}".format(both_deps))
        if non_common:
            print("      ⚠️ 非 commonMain：{}".format(non_common))
        if unresolved:
            print("      ？ 未定位   ：{}".format(sorted(set(unresolved))))
        if platform:
            print("      平台/第三方：{}".format(sorted(set(platform))))
        if not (app_deps or both_deps or non_common or unresolved or platform):
            print("      ✅ 无 :app 私有依赖、无平台 import")

    print()
    print("小计：{} 行；:app 私有 {} 处 / 非 commonMain {} 处 / 未定位 {} 处".format(
        totals["lines"], totals["app"], totals["non-common"], totals["unresolved"]))
    print("说明：「？未定位」= 该符号在本仓顶层声明里找不到（可能是嵌套声明、也真的可能是"
          "外部/生成代码）—— **必须人工确认**，别当它不存在（M5-15a 的教训）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
