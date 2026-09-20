#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核对某个 CMP 模块 `composeResources` 的文案与 `:app` 的 Android res 是否**逐字一致**。

用法：

    python tools/verify-compose-resources.py <模块路径> <CMP资源包名> [--apk <apk路径>]

例：

    python tools/verify-compose-resources.py feature/replacerules io.legado.app.feature.replacerules.res
    python tools/verify-compose-resources.py feature/replacerules io.legado.app.feature.replacerules.res \
        --apk app/build/outputs/apk/app/debug/*.apk

取数来源（自动择一）：

  1. 不传 `--apk`：读模块构建中间产物
     `<模块>/build/generated/compose/resourceGenerator/preparedResources/commonMain/composeResources/values*/*.cvr`
  2. 传 `--apk`：从 APK 里 `assets/composeResources/<CMP资源包名>/values*/*.cvr` 取
     —— 这是**打包后**形态，证据最强（tagrules / replacerules 两片都用它下结论）。

比对基准：`app/src/main/res/values*/strings.xml` 里的同名条目，但**在运行期层比对**：
`:app` 侧先展开 aapt2 会解释的 Java 风格转义（`\\uXXXX`、`\\n`、`\\t`、`\\'`、`\\"`、`\\\\`），
因为 `.cvr` 里存的是**已解码**的文本，而源 XML 里可能写的是**字面**转义
（M5-1c-2 实测：`about_description` 的原文就是字面的 `\\u3000\\u3000`）。
源 XML 文本一致 **不等于** 运行期一致 —— 见 `android_unescape()` 的注释。

三项检查：
  ① 四个语言目录齐全（composeResources 不参与 Android 资源合并，缺目录只会静默回落）；
  ② 每个语言的 key 集合与 **`:app` 对应语言**一致 —— 判据是「与 :app 一致」，**不是**
     「各语言彼此一致」：源 res 本身就缺某条时，Android 资源合并与 CMP 都会按 qualifier
     回落到默认 `values`，两者行为一致才算等价（`feature/txttocrules` 的 zh-rHK/zh-rTW
     合法地比 values 少 3 条）；
  ③ 与 `:app` 同名条目**运行期逐字一致**。

为什么解 `.cvr` 而不是读 `composeResources` 的源 XML：源 XML 只证明「我写的是什么」，
证明不了「实际生成/打包进去的是什么」。而且「四个语言目录是否齐全」只有在产物里才看得见
—— `composeResources` **不参与 Android 资源合并**，漏一个语言目录不会报错，只会静默回落到
默认语言；只有逐个语言目录去数才能发现。

`.cvr` 格式（Compose resources 插件的资源索引，纯文本）：

    version:0
    string|<资源名>|<UTF-8 文本的 base64>
    ...

退出码：0 = 全部一致；1 = 有缺失/不一致；2 = 用法错误或取数失败。
"""

from __future__ import annotations

import argparse
import base64
import pathlib
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent

# 与仓库现状一致：4 个语言目录。composeResources 必须**自带全部**，不能只放默认值。
LANG_DIRS = ["values", "values-zh-rCN", "values-zh-rHK", "values-zh-rTW"]


def _b64_text(s: str) -> str:
    """解 `.cvr` 条目的 base64 文本。

    ⚠️ 部分条目的 base64 **省略了 padding**（如 `TW92ZSBkb3du`）⇒ 先补到 4 的倍数，
    否则 `b64decode` 会抛 `binascii.Error: Incorrect padding`。这条坑在 M1-3t 记过。
    """
    return base64.b64decode(s + "=" * (-len(s) % 4)).decode("utf-8")


def parse_cvr_text(text: str) -> dict[str, str]:
    """解析 `.cvr` 文本，返回 {资源名: 文本}。只收 `string|` 行。"""
    out: dict[str, str] = {}
    for line in text.splitlines():
        if not line or line.startswith("version:"):
            continue
        parts = line.split("|", 2)
        if len(parts) != 3 or parts[0] != "string":
            continue
        out[parts[1]] = _b64_text(parts[2])
    return out


def parse_cvr(path: pathlib.Path) -> dict[str, str]:
    return parse_cvr_text(path.read_text(encoding="utf-8", errors="strict"))


def parse_cvr_bytes(raw: bytes) -> dict[str, str]:
    return parse_cvr_text(raw.decode("utf-8"))


def parse_android_strings(path: pathlib.Path) -> dict[str, str]:
    """解析 Android strings.xml，返回 {资源名: 文本}（拼回所有子节点文本）。"""
    out: dict[str, str] = {}
    tree = ET.parse(path)
    for el in tree.getroot():
        if el.tag != "string":
            continue
        name = el.get("name")
        if not name:
            continue
        out[name] = "".join(el.itertext())
    return out


# aapt2 会解释的 Java 风格转义。顺序重要：`\\` 要先于 `\u` 之外的单字符转义处理，
# 所以用一个「发现反斜杠后看下一个字符」的单遍扫描，而不是连续 replace。
_AAPT2_SIMPLE = {
    "n": "\n",
    "t": "\t",
    "r": "\r",
    "'": "'",
    '"': '"',
    "\\": "\\",
    "0": "\0",
}


def android_unescape(raw: str) -> str:
    """把 aapt2 会在编译期展开的转义展开，得到**运行期**真正拿到的字符串。

    ⚠️ 为什么必须做这一步（M5-1c-2 实测）：本仓库的 `app/src/main/res/values*/strings.xml` 里
    **真的**用了 Java 风格转义。`about_description` 的原文是字面的 `\\u3000\\u3000`（全角空格），
    aapt2 会把它解码成 U+3000；而 CMP 的资源生成器**也**解码（实测 `.cvr` 里就是 UTF-8 的
    `E3 80 80`）。于是不展开 `:app` 侧就会得到**假差异**：
    `cmp = '\\u3000'`（真字符）对 `app = '\\\\u3000'`（字面反斜杠 + u3000）。

    换句话说：**源 XML 文本一致 ≠ 运行期一致**，比对必须发生在运行期这一层。CMP 生成器与 aapt2
    对 `\\uXXXX`、`\\n`、`\\t`、`\\'`、`\\"`、`\\\\` 的处理实测一致；不认识的转义（如 `\\d` 正则
    里常见的写法）原样保留，aapt2 同样只在 `\\u` 与上表字符上动手。
    """
    if "\\" not in raw:
        return raw
    out: list[str] = []
    i, n = 0, len(raw)
    while i < n:
        c = raw[i]
        if c != "\\" or i + 1 >= n:
            out.append(c)
            i += 1
            continue
        nxt = raw[i + 1]
        if nxt == "u" and i + 6 <= n:
            hexs = raw[i + 2:i + 6]
            try:
                out.append(chr(int(hexs, 16)))
                i += 6
                continue
            except ValueError:
                pass
        if nxt in _AAPT2_SIMPLE:
            out.append(_AAPT2_SIMPLE[nxt])
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def load_module(module: pathlib.Path, ns: str, apk: pathlib.Path | None):
    """返回 (lang -> {name: text}, 描述字符串)。"""
    if apk is not None:
        import zipfile

        with zipfile.ZipFile(apk) as zf:
            prefix = f"assets/composeResources/{ns}/"
            names = [n for n in zf.namelist() if n.startswith(prefix) and n.endswith(".cvr")]
            if not names:
                print(f"!! APK 里找不到 {prefix}**/*.cvr —— CMP 资源没被装进去？")
                sys.exit(2)
            result: dict[str, dict[str, str]] = {}
            for n in names:
                # .../values-zh-rCN/strings.commonMain.cvr -> values-zh-rCN
                lang = n[len(prefix):].split("/")[0]
                result[lang] = parse_cvr_bytes(zf.read(n))
        return result, f"APK {apk.name} 的 {prefix}"

    base = (
        module
        / "build/generated/compose/resourceGenerator/preparedResources"
        / "commonMain/composeResources"
    )
    if not base.is_dir():
        print(f"!! 中间产物目录不存在：{base}")
        print("   先跑该模块的 desktop/android 编译或直接传 --apk。")
        sys.exit(2)
    result = {}
    for d in sorted(base.iterdir()):
        if not d.is_dir():
            continue
        for f in d.glob("*.cvr"):
            result.setdefault(d.name, {}).update(parse_cvr(f))
    return result, f"中间产物 {base}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("module", help="模块路径，如 feature/replacerules")
    ap.add_argument("namespace", help="CMP 资源包名，如 io.legado.app.feature.replacerules.res")
    ap.add_argument("--apk", default=None, help="APK 路径（给了就从 APK 取，证据最强）")
    args = ap.parse_args()

    module = ROOT / args.module
    apk = None
    if args.apk:
        import glob

        hits = glob.glob(args.apk)
        if not hits:
            print(f"!! --apk 没匹配到文件：{args.apk}")
            return 2
        apk = pathlib.Path(hits[0])

    if not module.is_dir():
        print(f"!! 模块目录不存在：{module}")
        return 2

    cmp_by_lang, src_desc = load_module(module, args.namespace, apk)
    print(f"取数来源：{src_desc}")
    print(f"语言目录：{sorted(cmp_by_lang)}")

    ok = True

    # ① 四个语言目录必须齐全
    missing_lang = [l for l in LANG_DIRS if l not in cmp_by_lang]
    if missing_lang:
        ok = False
        print(f"\n!! 缺语言目录：{missing_lang}")
        print("   composeResources 不参与 Android 资源合并，缺目录会静默回落默认语言。")

    # :app 的 Android res —— ②③ 的共同基准
    app_res = ROOT / "app/src/main/res"
    app_by_lang: dict[str, dict[str, str]] = {}
    for lang in LANG_DIRS:
        f = app_res / lang / "strings.xml"
        if f.is_file():
            app_by_lang[lang] = parse_android_strings(f)

    # ② 每个语言目录的 key 集合必须与 **:app 对应语言** 一致（缺翻译是静默的）。
    #    基准取「本模块全部 key」∩「:app 该语言实际有的 key」：:app 源 res 本身缺某条时
    #    （Android 资源合并会回落到默认 `values`），composeResources 也应当缺——CMP 同样按
    #    qualifier 回落，两者行为才一致。
    #    ⚠️ **不能**改成「各语言 key 集合必须彼此相同」：`feature/txttocrules` 的
    #    `zh-rHK`/`zh-rTW` 就合法地比 `values` 少 3 条（模块 res 与 `:app` res 都缺，
    #    属既有回落行为，迁移前后一致）。判据是「与 :app 一致」，不是「语言之间一致」。
    #    ⚠️ 但**反向**（模块有、`:app` 无）在 M5-1c-3 起是**正常且期望**的状态：页面迁进
    #    Feature 后 `:app` 侧那份副本就成了死资源，会被删掉（本仓要求「只移除本次改动产生的
    #    无用资源」）。把它判为失败会让脚本在每次删副本后永久报噪音，反而掩盖真问题。
    #    ⇒ 拆成两类：**缺失**（`:app` 还在用、模块没搬）= 硬错误；**已下线**（模块搬了、
    #    `:app` 副本已删）= 只记录。后者的值等价性必须在**删副本之前**跑一次本脚本证明。
    all_module_keys: set[str] = set()
    for d in cmp_by_lang.values():
        all_module_keys |= set(d)
    retired: dict[str, list[str]] = {}
    for lang in LANG_DIRS:
        if lang not in cmp_by_lang:
            continue
        keys = set(cmp_by_lang[lang])
        expected = set(app_by_lang.get(lang, {})) & all_module_keys
        if expected - keys:
            ok = False
            print(f"\n!! {lang} 缺条目（:app 有而 composeResources 无）：{sorted(expected - keys)}")
        if keys - expected:
            retired[lang] = sorted(keys - expected)
    if retired:
        print("\n-- 已下线（composeResources 有、:app 副本已随迁移删除）：正常态，不判失败")
        for lang in sorted(retired):
            print(f"   {lang}: {len(retired[lang])} 条 {retired[lang]}")

    # ③ 与 :app Android res 的同名条目逐条比对（**运行期**层：`:app` 侧先展开 aapt2 的转义）
    #    「已下线」的条目不进分母——它们没有可比对象，计入只会让 x/y 失真。
    total = same = 0
    mismatches: list[str] = []
    for lang in LANG_DIRS:
        mine = cmp_by_lang.get(lang, {})
        theirs = app_by_lang.get(lang, {})
        for name, text in sorted(mine.items()):
            if name not in theirs:
                continue
            total += 1
            want = android_unescape(theirs[name])
            if want == text:
                same += 1
            else:
                ok = False
                extra = ""
                if want != theirs[name]:
                    extra = (
                        f"\n     app（展开转义后）: {want!r}"
                        f"\n     app（源 XML 原文）: {theirs[name]!r}"
                    )
                mismatches.append(f"{lang}/{name}:\n     cmp : {text!r}\n     app : {theirs[name]!r}{extra}")

    print(f"\n逐字比对：{same}/{total} 与 :app 同名条目一致")
    if mismatches:
        print(f"差异/记录 {len(mismatches)} 条：")
        for m in mismatches:
            print("  - " + m)

    print("\n结论：" + ("一致 ✅" if ok else "有差异 ❌"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
