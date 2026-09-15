#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""统计撰定后各测试任务的用例数，用于和棘轮基线比对。

为什么要有它：每片 KMP/CMP 搬迁都要求「只搬文件 ⇒ 用例数与基线逐字一致」。Gradle 的
BUILD SUCCESSFUL 只能证明没失败，**证明不了没少跑**——「悄悄少跑 19 个用例但全绿」
是完全可以穿过 `BUILD SUCCESSFUL` 的事故。这里直接从 surefire XML 取
`tests/failures/errors/skipped`，不解析 Gradle 输出。

用法（干净重建并跑完全量验证集后）：

    python tools/count-test-results.py

当前基线：主验证集 **712**、全量 **910**（详见 .workbuddy/memory/topics/gates-and-verification.md）。
"""
import pathlib
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent

# 显示名 -> (结果 XML 目录, 是否计入「主验证集」)
RESULT_DIRS = {
    "app            (testAppDebugUnitTest)": ("app/build/test-results/testAppDebugUnitTest", True),
    # designsystem / tagrules 的 `commonTest` 在两个目标上各跑一遍；统一取
    # `testAndroidHostTest`（KMP android 目标的主机测任务名）。⚠️ 模块转 KMP 后
    # 老 Android library 的 `testDebugUnitTest` 不再产出，别改回去。
    "designsystem   (testAndroidHostTest)": ("core/designsystem/build/test-results/testAndroidHostTest", True),
    "viewmodel      (testAndroidHostTest)": ("core/viewmodel/build/test-results/testAndroidHostTest", True),
    "tagrules       (testAndroidHostTest)": ("feature/tagrules/build/test-results/testAndroidHostTest", True),
    # M1-3x：`ReplaceRuleStateTest`（原住 `:app/src/test`）随模块转 KMP 搬进本模块的
    # `androidHostTest`。app 的 635 相应降到 633、本模块 +2 ⇒ **主验证集合计仍是 708**，
    # 只是分布变了（这是有意的归属调整，不是用例增减）。
    "replacerules   (testAndroidHostTest)": ("feature/replacerules/build/test-results/testAndroidHostTest", True),
    # M2-2：`core:data` 的 commonTest 与 desktopTest 加 4 —— 删掉 2 个已失去被测对象的
    # `BigDataStoreProvider` 用例（未安装/安装后读回），新增 6 例 `RuleDataFileStoreDesktopTest`
    # （真文件系统 + **硬编码 MD5 向量**钉住「路径布局与迁移前逐字节一致」，那是既有用户数据
    # 能否读回的关键）。之所以要在桌面端真落盘验证：新实现是共享层 object，commonTest 里
    # 没有可用的 `RuleDataStorage` actual，只有 `desktopTest` 才跑得到真 IO。
    "core:data      (desktopTest)": ("core/data/build/test-results/desktopTest", False),
    # M2-3：`core:platform` 的 commonTest 挂在 `SymmetricCrypto` 原语上重写 —— 原 2 例测的是
    # `SymmetricCryptoProvider` 的 install/uninstall（被测对象已删除），换成 7 例对原语本身的
    # 契约测试（硬编码 AES 密文向量，由 openssl 与 node 两个独立实现交叉确认）。该文件同时跑在
    # `testAndroidHostTest` 与 `desktopTest` 上，但**只统计 desktopTest**（口径与其它模块一致）。
    "core:platform  (desktopTest)": ("core/platform/build/test-results/desktopTest", False),
    # M1-4：desktop host 的主路径证据。**计入主验证集**——它是本仓第一个越过「能编译」的
    # desktop 断言，少跑就没人发现。基线与 app 侧一样靠它兜底。
    # M1-4b 加 2 例（两目的地导航 + entry 级 VM 释放）；M2-1 加 1 例（desktop 的
    # ImportJsonEditor 显式不支持，防它被改成静默降级）。
    "host:desktop    (desktopTest)": ("host/desktop/build/test-results/desktopTest", True),
    # M1-4 工具链探针（CMP UI 测试能否在 desktop 跑）。只进全量：证明过一次即可，
    # 不必每次主验证都跑。
    "compose-probe   (desktopTest)": ("smoke/compose-desktop-probe/build/test-results/desktopTest", False),
    # M3-1：`:data:rules`（替换规则域的实现与映射器）自带的等价性基线。口径与其它 data
    # 层模块一致（`core:data` / `core:platform` 都只取 `desktopTest`，见上），故**不进主集**。
    # 用例是 `ReplaceRuleMapperTest`：实体 ↔ 领域模型逐字段往返、默认值集合、只按 id 判等的
    # 语义、`isValid()` / `getValidTimeoutMillisecond()` 与实体行为一致。
    # M3-2 起同一模块还装高亮标签规则域的 `HighlightTagRuleMapperTest`（6 例）、M3-3 再加
    # 字典规则域的 `DictRuleMapperTest`（6 例）、M3-4 再加 TXT 目录规则域的
    # `TxtTocRuleMapperTest`（7 例），故本目录计数为**四片之和**（7 + 6 + 6 + 7 = 26）。
    "data:rules      (desktopTest)": ("data/rules/build/test-results/desktopTest", False),
}

BASELINE_MAIN = 712
# M2-3：877 → 882（`core:platform` 的 SymmetricCryptoContractTest 2 → 7 例）。主验证集不变。
# M2-4：882 → 891（净 +9 = -2 +11）。`Logger` / `LoggerProvider` 契约删除 ⇒ 随契约走的
# `LoggerContractTest` 2 例失去被测对象（同 M2-2 删 `BigDataStoreProvider` 用例的处理）；
# 同时给下沉共享层的新实现补契约测试：`ConcurrentRateRegistryTest` 5 例（core:data）、
# `AppLogStoreContractTest` 6 例（core:platform desktopTest）。**主验证集仍 712**
# （两个模块都在非主集一侧）。新增用例已做变异验证：改 trim 上界 / 改 `add` 顺序 /
# 交换 accessLimit 与 interval 都会让它们变红。
# M3-1：891 → 898（`:data:rules` 新增 `ReplaceRuleMapperTest` 7 例）。主验证集不变（仍 712）。
# 新模块的用例是**新增**而非搬迁，故基线必须上调；已做变异验证：把映射器里的
# `timeoutMillisecond` 去掉、把 `excludeScope` 写成 `scope`、把 `order` 映射删掉，
# 三种变异都会让 `ReplaceRuleMapperTest` 变红。
# M3-2：898 → 904（`:data:rules` 新增 `HighlightTagRuleMapperTest` 6 例）。主验证集**仍是 712**
# ——本片只搬实现与类型、不改 `:app` / Feature 的用例句，故 app 633 与 tagrules 15 逐字不变。
# 同样做了变异验证：把 `enabled` 写死为 `true`、把 `order` 错映射成 `id`、把 `title` 与
# `pattern` 对调，三种变异都会让 `HighlightTagRuleMapperTest` 变红。
# M3-3：904 → 910（`:data:rules` 新增 `DictRuleMapperTest` 6 例）。主验证集**仍是 712**
# ——本片只搬实现与类型、不改 `:app` / Feature 的用例句（`:app` 的 `DictViewModel` 那处改动
# 是「同步阻塞换 suspend」，用例数不变；`feature:dict` 模块级测试本来就是零）。
# 同样做了变异验证：把 `enabled` 写死为 `true`、把 `sortNumber` 错映射、把集合映射的
# `urlRule` 与 `showRule` 对调，三种变异都会让 `DictRuleMapperTest` 变红。
# M3-4：910 → 917（`:data:rules` 新增 `TxtTocRuleMapperTest` 7 例 —— 比前几片多一条
# 「`example` 的 `null` 原样穿过映射」）。主验证集**仍是 712**——本片只搬实现与类型，
# `:app` 侧 `TxtTocRuleDeserializerTest`（6 例，测实体级键名提升）逐字未改，Feature 侧
# 模块级测试本来就是零。
# 同样做了变异验证：把 `example` 的 `null` 归一成 `""`、把 `serialNumber` 的默认值改成 0、
# 把集合映射的 `chapterRule` 与 `volumeRule` 对调，三种变异都会让 `TxtTocRuleMapperTest` 变红。
BASELINE_ALL = 917


def tally(d: pathlib.Path):
    tests = failures = errors = skipped = 0
    files = sorted(d.glob("*.xml")) if d.is_dir() else []
    if not files:
        return 0, 0, 0, 0, 0
    for f in files:
        root = ET.parse(f).getroot()
        tests += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
    return tests, failures, errors, skipped, len(files)


def main() -> int:
    main_total = all_total = 0
    bad = []
    missing = []
    for label, (rel, in_main) in RESULT_DIRS.items():
        t, f, e, s, n = tally(ROOT / rel)
        if n == 0:
            missing.append(label)
            print(f"{label:38s} *** 没有结果 XML，是不是没跑？")
            continue
        if in_main:
            main_total += t
        all_total += t
        flag = ""
        if f or e:
            bad.append(label)
            flag = f"  <<< fail={f} err={e}"
        print(f"{label:38s} tests={t:5d}  fail={f}  err={e}  skip={s}  files={n}{flag}")

    print("-" * 78)
    m_ok = "OK" if main_total == BASELINE_MAIN else f"偏离 {main_total - BASELINE_MAIN:+d}"
    a_ok = "OK" if all_total == BASELINE_ALL else f"偏离 {all_total - BASELINE_ALL:+d}"
    print(f"主验证集合计 = {main_total:5d}  （基线 {BASELINE_MAIN}） {m_ok}")
    print(f"全量合计     = {all_total:5d}  （基线 {BASELINE_ALL}） {a_ok}")

    ok = True
    if missing:
        print(f"\n!!! {len(missing)} 个结果目录为空 —— 计数不可信")
        ok = False
    if bad:
        print(f"!!! 存在失败用例: {', '.join(bad)}")
        ok = False
    if main_total != BASELINE_MAIN or all_total != BASELINE_ALL:
        print("!!! 与基线不一致：若是有意变更请写进提交文案并做变异验证，否则查回退")
        ok = False
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
