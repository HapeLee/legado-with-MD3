#!/usr/bin/env python3
"""扫描所有 composeResources：数组条目里有没有 `@string/…` 这类**间接引用**。

用法：
    python tools/check-resource-indirection.py          # 只检查
    python tools/check-resource-indirection.py --fix    # 展开（值取 :app 同语言 → 回落默认）

为什么要单独一个检查（真实 bug，且**静默**）：
CMP 的 composeResources 是打进 assets 的资源，**不参与 Android 资源解析**。`:app` 的
`<string-array>` 里可以写 `<item>@string/home</item>`（Android 会逐项按语言解析），但照抄到
共享层后，界面会**原样显示 `"@string/home"`** —— 而编译通过、逐字比对也能"通过"（因为
`:app` 的原始 token 就是这个），只有真机/desktop 渲染时才看得见。
本仓既有约定（M5-8b / M5-9b）：**展开成解析后的字面量**，且每个语言目录都要写。
"""
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8")

REF_ITEM = re.compile(r"<item>(@(string|array|plurals)/([^<]+))</item>")
SRC_RES = pathlib.Path("app/src/main/res")
FIX = "--fix" in sys.argv


def read_strings(loc: str) -> dict[str, str]:
    out: dict[str, str] = {}
    d = SRC_RES / loc
    if not d.is_dir():
        return out
    for f in sorted(d.glob("*.xml")):
        t = f.read_text(encoding="utf-8", errors="ignore")
        for m in re.finditer(
            r'<string(?![-\w])[^>]*\bname="([A-Za-z0-9_]+)"[^>]*>(.*?)</string>', t, re.S
        ):
            out.setdefault(m.group(1), m.group(2))
    return out


def read_arrays(loc: str) -> dict[str, list[str]]:
    out: dict[str, list[str]] = {}
    d = SRC_RES / loc
    if not d.is_dir():
        return out
    for f in sorted(d.glob("*.xml")):
        t = f.read_text(encoding="utf-8", errors="ignore")
        for m in re.finditer(
            r'<(?:string-)?array(?![-\w])[^>]*\bname="([A-Za-z0-9_]+)"[^>]*>(.*?)</(?:string-)?array>',
            t, re.S,
        ):
            out.setdefault(m.group(1), re.findall(r"<item>(.*?)</item>", m.group(2), re.S))
    return out


defaults = (read_strings("values"), read_arrays("values"))
found = 0
fixed = 0
unresolved: list[str] = []

for root in sorted(pathlib.Path(".").rglob("composeResources")):
    if "build" in root.parts:
        continue
    for f in sorted(root.rglob("*.xml")):
        locale = f.parent.name
        t = f.read_text(encoding="utf-8")
        hits = REF_ITEM.findall(t)
        if not hits:
            continue
        found += len(hits)
        print("{}  ({}): {} 项间接引用".format(f.as_posix(), locale, len(hits)))
        for full, kind, key in hits:
            print("     <item>{}</item>".format(full))
        if not FIX:
            continue
        s_loc = read_strings(locale) or {}
        a_loc = read_arrays(locale) or {}

        def repl(m: re.Match) -> str:
            global fixed
            kind, key = m.group(2), m.group(3)
            if kind == "string":
                val = s_loc.get(key, defaults[0].get(key))
            elif kind == "array":
                items = a_loc.get(key, defaults[1].get(key))
                val = items[0] if items and len(items) == 1 else None
            else:
                val = None
            if val is None:
                unresolved.append("{} {}/{}".format(locale, kind, key))
                return m.group(0)
            fixed += 1
            return "<item>{}</item>".format(val)

        t2 = REF_ITEM.sub(repl, t)
        if t2 != t:
            f.write_text(t2, encoding="utf-8")

print()
if FIX:
    print("展开 {} 项（发现 {} 项）".format(fixed, found))
    print("无法解析:", unresolved or "无")
else:
    print("共发现 {} 项。加 --fix 可展开。".format(found))

bad = 0
for root in sorted(pathlib.Path(".").rglob("composeResources")):
    if "build" in root.parts:
        continue
    for f in sorted(root.rglob("*.xml")):
        try:
            ET.parse(f)
        except ET.ParseError as exc:
            bad += 1
            print("  ❌ {}: {}".format(f, exc))
print("XML 校验:", "全部通过" if bad == 0 else "{} 个失败".format(bad))
sys.exit(1 if (found and not FIX) else 0)
