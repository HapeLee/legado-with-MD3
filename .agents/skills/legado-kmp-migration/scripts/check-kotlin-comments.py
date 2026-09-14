#!/usr/bin/env python3
"""Kotlin 注释体检：抓「注释里写了通配 / 正则字面量 ⇒ 注释结构被破坏」。

为什么需要它
------------
**Kotlin 的块注释是可嵌套的**（`/* /* */ */` 合法）。于是在 KDoc 里写 MIME / glob / 正则
字面量会同时踩两个雷：

    /**                        ← 注释开始
     * 产出 `*/*` / `text/*`    ← `*/` 提前闭合注释；`/*` 又开了一层
     */
    internal fun f() { ... }   ← 编译报一片 "Expecting a top level declaration"

M1-3n 实录：`FilePickerSheet.kt` 的 KDoc 因此让 `:core:designsystem:compileKotlinDesktop` 失败，
而报错行号落在**函数体里**（真凶在 ~30 行之前），肉眼完全指不到。

⚠️ **同名序列出现在字符串字面量里完全合法**（`types.add("*/*")` 一直没问题），
所以判据必须是**注释上下文**——朴素全文件 grep 会把字符串里的命中一起报出来，噪音淹没信号。

判定规则（只报这四条，正常代码零输出）
--------------------------------------
1. 注释内又开一层（`/*`，depth ≥ 2）—— 几乎总是无意的字面量；
2. `*/` 越界（depth 变负）—— 括号不配平；
3. EOF 时 depth ≠ 0 —— 后面的代码被吞进注释；
4. **KDoc（`/**`）在同一行被 `*/` 提前闭合后仍有正文**——典型症状就是 `*/*` 提前收尾。

实现注意（两条都会产生**假阳性**，都实测过）：

1. **块注释内部要短路掉「字符串 / 行注释 / 原始字符串」的识别**。英文散文里的撇号
   （`layout's`）与 URL 里的 `//` 都不是字面量，不短路就会吞掉注释关闭符、在 EOF 报
   「仍有 N 层注释未闭合」（M1-3p 实测，见 `depth > 0` 那一支的注释）。
2. **字符串字面量要记住起始引号，只认同一种收尾**。Kotlin 的 `"..."` 里可以合法出现 `'`
   （`"LazyVerticalGrid's width should be…"`）。按「遇到任一引号即收尾」写，这类字符串会提前
   结束、后续引号相位反转，最后把正文里的块注释整段吞掉、报出「`*/` 越界」
   （M1-3p 实测：`core/ui/.../lazylist/VerticalFastScroller.kt`）。

用法
----

    # 默认：扫 git 里所有已改动 / 未跟踪的 .kt
    python .agents/skills/legado-kmp-migration/scripts/check-kotlin-comments.py

    # 指定文件或目录（目录建议加 -r）
    python .../check-kotlin-comments.py core/designsystem/src/commonMain -r

退出码：0 = 干净；1 = 有发现。
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

SKIP_DIRS = {"build", ".gradle", ".git", ".idea", ".kotlin", "node_modules", ".workbuddy"}


def changed_kotlin_files() -> list[Path]:
    out = subprocess.run(
        ["git", "status", "--porcelain", "-uall"],
        capture_output=True, text=True, check=False,
    ).stdout
    files: list[Path] = []
    for line in out.splitlines():
        path = line[3:]
        if " -> " in path:
            path = path.split(" -> ")[-1]
        if path.endswith(".kt"):
            files.append(Path(path))
    return files


def walk_kt(root: Path) -> list[Path]:
    return [p for p in root.rglob("*.kt") if not any(part in SKIP_DIRS for part in p.parts)]


def scan(path: Path) -> tuple[list[tuple[int, str]], int]:
    """返回 (发现列表, EOF 时未闭合深度)。发现只含上面四条规则命中的行。"""
    src = path.read_text(encoding="utf-8", errors="replace")
    finds: list[tuple[int, str]] = []
    i, line, n = 0, 1, len(src)
    depth = 0
    kdoc_depth = 0          # 当前处于 KDoc 的层深（0 = 不在 KDoc 里）
    kdoc_depth_when_opened = 0
    in_line = in_raw = False
    str_quote = ""          # 字符串/字符字面量的起始引号（"" = 不在字面量里）

    while i < n:
        c = src[i]

        if c == "\n":
            line += 1
            in_line = False
            i += 1
            continue
        if in_line:
            i += 1
            continue
        if in_raw:
            if src.startswith('"""', i):
                in_raw = False
                i += 3
                continue
            i += 1
            continue
        if str_quote:
            if c == "\\":
                i += 2
                continue
            # **只认同一种引号收尾**：Kotlin 的 `"..."` 里可以合法出现 `'`
            # （`"LazyVerticalGrid's width"`），`'...'` 里也可以出现 `"`。早先按「遇到任一
            # 引号即收尾」写，会让这类字符串提前结束、后续引号相位反转，最终把正文里的块注释
            # 整段吞掉并报出「`*/` 越界」假阳性（M1-3p 实测：`VerticalFastScroller.kt`）。
            if c == str_quote:
                str_quote = ""
            i += 1
            continue

        if src.startswith("/*", i):
            depth += 1
            if depth >= 2:
                finds.append((line, "注释内又开一层（`/*`）——几乎总是无意的通配/正则字面量"))
            if depth == 1 and src.startswith("/**", i):
                kdoc_depth, kdoc_depth_when_opened = 1, 1
            elif kdoc_depth:
                kdoc_depth = depth
            i += 2
            continue

        if src.startswith("*/", i):
            depth -= 1
            if depth < 0:
                finds.append((line, "`*/` 越界 —— 注释开闭不配平"))
                depth = 0
            if kdoc_depth and depth < kdoc_depth_when_opened:
                rest = src[i + 2: src.find("\n", i) if src.find("\n", i) != -1 else n]
                if rest.strip():
                    finds.append((line, "KDoc 在这一行被提前闭合，后面还有正文（典型症状：`*/*`）"))
                kdoc_depth = 0
            i += 2
            continue

        if depth > 0:
            # 块注释内部没有「字符串 / 行注释 / 原始字符串」语义：里面的引号、`//`、`"""` 全是
            # 普通文字。不短路会吞掉注释关闭符并报**假阳性**——M1-3p 实测：KDoc 里的英文撇号
            # （`layout's content.`）被当成字符串起点后，扫描器再也看不到 `*/`，于是在 EOF 报
            # 「仍有 1 层注释未闭合」，指着一份完全合法的注释。
            i += 1
            continue

        if src.startswith("//", i):
            in_line = True
            i += 2
            continue
        if src.startswith('"""', i):
            in_raw = True
            i += 3
            continue
        if c in "\"'":
            str_quote = c
            i += 1
            continue
        i += 1

    return finds, depth


def main() -> int:
    ap = argparse.ArgumentParser(description="Kotlin 注释体检")
    ap.add_argument("paths", nargs="*", help="文件或目录；缺省用 git 改动过的 .kt")
    ap.add_argument("-r", "--recursive", action="store_true", help="目录参数递归处理")
    args = ap.parse_args()

    if args.paths:
        files: list[Path] = []
        for raw in args.paths:
            p = Path(raw)
            if p.is_dir():
                files.extend(walk_kt(p) if args.recursive else p.glob("*.kt"))
            elif p.suffix == ".kt":
                files.append(p)
    else:
        files = changed_kotlin_files()

    if not files:
        print("没有 Kotlin 文件需要检查。")
        return 0

    bad = 0
    for f in files:
        if not f.exists():
            continue
        finds, depth = scan(f)
        if not finds and depth == 0:
            continue
        bad += 1
        print(f"⚠ {f}")
        for ln, msg in finds:
            print(f"    L{ln}: {msg}")
        if depth != 0:
            print(f"    EOF 时仍有 {depth} 层注释未闭合 —— 后面的代码被吞进注释了")
    if bad:
        print(f"\n{len(files)} 个文件里 {bad} 个有问题。")
        print("修法：注释里不要出现 `*/` / `/*`。把通配或正则字面量改写成描述文字，")
        print("或拆成两个独立 code span（中间隔一个反引号），别让两个符号相邻。")
        return 1
    print(f"{len(files)} 个 Kotlin 文件注释结构正常。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
