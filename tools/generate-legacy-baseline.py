#!/usr/bin/env python3
"""重新生成 legacy 架构基线 gradle/architecture/legacy-baseline.txt（M0-2）。

用途：批量清理 legacy 债之后重新冻结。**这会抹平棘轮**（新基线 = 当前真实计数），
所以只在「一批债已被清掉、旧基线满屏报红」时经评审使用；日常减少应手工下调对应条目。

口径必须与 gradle task CheckLegacyArchitectureTask 保持一致：
  * 只扫生产源集（源集名含 test 的跳过）；
  * 区域 = <模块或 app>/<源集>/<包目录>，目录级聚合；
  * 全局门面以 import 锚定（appDb 等同名构造参数不算债）；
  * core Provider 逐行统计，跳过注释行，并排除定义文件自身的 object 声明。

用法：
    python tools/generate-legacy-baseline.py
"""
import collections
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "gradle", "architecture", "legacy-baseline.txt")

# (源根相对路径, 区域前缀模块名, 源集名)
APP_SOURCES = [("app/src/main/java", "app", "main")]

PATS = {
    "appCtx": re.compile(r"^import splitties\.init\.appCtx$", re.M),
    "appDb": re.compile(r"^import io\.legado\.app\.data\.appDb$", re.M),
    "gson": re.compile(r"^import io\.legado\.app\.utils\.GSON$", re.M),
    "legacyHelp": re.compile(r"^import io\.legado\.app\.help\.[A-Za-z0-9_.]+$", re.M),
    "legacyBase": re.compile(r"^import io\.legado\.app\.base\.[A-Za-z0-9_.]+$", re.M),
    "legacyNaming": re.compile(
        r"^import io\.legado\.app\.[A-Za-z0-9_.]*\.[A-Za-z0-9_]*(?:Help|Utils)$", re.M),
}
PROVIDER = re.compile(
    r"\b(Clipboard|CookieStore|ImportJsonEditor|KeyValueStore|Logger|SourceRuntime"
    r"|SymmetricCrypto|Toaster|BigDataStore)Provider\b")
PROVIDER_DECL = re.compile(r"\bobject\s+([A-Za-z0-9_]*Provider)\b")
ORDER = ["appCtx", "appDb", "gson", "legacyHelp", "legacyBase", "legacyNaming", "coreProvider"]


def discover_modules():
    modules = []
    for group in ("core", "feature", "modules", "smoke"):
        g = os.path.join(ROOT, group)
        if not os.path.isdir(g):
            continue
        for name in sorted(os.listdir(g)):
            path = os.path.join(g, name)
            if os.path.isfile(os.path.join(path, "build.gradle.kts")):
                modules.append((group + "/" + name, path))
            elif os.path.isfile(os.path.join(path, "core", "build.gradle.kts")):
                modules.append((group + "/" + name + "/core", os.path.join(path, "core")))
    return modules


def collect_files():
    files = []

    def walk(base, prefix):
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [d for d in dirnames if d not in ("build", ".git")]
            for name in filenames:
                if name.endswith(".kt"):
                    rel = os.path.relpath(os.path.join(dirpath, name), base).replace("\\", "/")
                    files.append((prefix + "/" + os.path.dirname(rel), os.path.join(dirpath, name)))

    for rel, module, source_set in APP_SOURCES:
        walk(os.path.join(ROOT, rel), "%s/%s" % (module, source_set))
    for module_path, module_dir in discover_modules():
        src = os.path.join(module_dir, "src")
        if not os.path.isdir(src):
            continue
        for source_set in sorted(os.listdir(src)):
            if "test" in source_set.lower():
                continue
            for kotlin_root in ("kotlin", "java"):
                root = os.path.join(src, source_set, kotlin_root)
                if os.path.isdir(root):
                    walk(root, "%s/%s" % (module_path, source_set))
    return files


def main():
    files = collect_files()
    counts = collections.defaultdict(collections.Counter)
    for area, path in files:
        text = open(path, encoding="utf-8", errors="ignore").read()
        for category, pattern in PATS.items():
            hits = len(pattern.findall(text))
            if hits:
                counts[category][area] += hits
        declared = set(PROVIDER_DECL.findall(text))
        provider_hits = 0
        for line in text.splitlines():
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
                continue
            provider_hits += sum(
                1 for name in PROVIDER.findall(line) if name + "Provider" not in declared)
        if provider_hits:
            counts["coreProvider"][area] += provider_hits

    lines = [
        "# M0-2 legacy 架构基线（冻结值 = 生成时的真实计数）",
        "# 格式: <category>|<区域>|<计数>；区域 = <模块或 app>/<源集>/<包目录>",
        "# 只统计生产源集（含 test 的源集不扫）；目录级聚合，目录内新增即越界。",
        "# 由 gradle task checkLegacyArchitecture 消费；报告见 "
        "docs/dev/legacy-architecture-report.md",
        "# 重新生成: python tools/generate-legacy-baseline.py（会抹平棘轮，需评审）",
    ]
    for category in ORDER:
        for area in sorted(counts[category]):
            lines.append("%s|%s|%d" % (category, area, counts[category][area]))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")
    print("scanned production kt files:", len(files))
    print("baseline entries:", sum(len(counts[c]) for c in ORDER))
    print("totals:", {c: sum(counts[c].values()) for c in ORDER})
    print("written:", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
