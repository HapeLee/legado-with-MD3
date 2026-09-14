#!/usr/bin/env python3
"""判「某个 :core:ui 组件能不能进 commonMain」的传递闭包三分法。

用法（仓库根目录）::

    # 单文件
    python .agents/skills/legado-kmp-migration/scripts/portability-triage.py \
        core/ui/src/main/kotlin/io/legado/app/ui/widget/components/text/AppText.kt

    # 一次看一批（Seed 之间会合并去重）
    python .../portability-triage.py $(find core/ui/src/main -name '*.kt')

输出每个闭包成员一行：

    ok    path                 # 三个维度都干净 —— 可以机械搬
    HARD  path  HARD[原因]     # 维度 1/2 命中 —— android/jvm import、R、越界 io.legado.app.*
    ok    path  ext-dep[包名]  # 干净，但需要额外模块依赖（如 :core:model）

外加三段必须看的：

- **同包兄弟引用**：同包顶层声明互相可见、**不需要 import**，而闭包只顺 import 走 ⇒
  这类依赖原本完全不出现（M1-3j / M1-3k 各误判一次）。现已单独列出。
- **种子结论**：闭包里只要有一个 HARD，种子就搬不动——即使种子自身那行写着 `ok`。
- 一份「未解析 import」清单（含 `Type.member` 形式的成员访问，必须人工确认）。

三个判定维度（只查前两个会漏，见 references/slice-checklist.md）：

1. `android.*` / `java.*` / `javax.*` / `kotlin.jvm.*` import，**外加一组 Android-only 的
   Compose 符号**（见下）；
2. 越界 `io.legado.app.*`（只允许 `ui.*`；`domain.model.*` = `:core:model` 是 legal 的）
   以及 Android 资源 id（`io.legado.app.core.ui.R`）；
3. **依赖库的同一个 API 是否也在非 Android 源集里** —— 本脚本看不见这个维度。
   `ExperimentalMaterialApi`（material2）与 CMP material3 的 expressive 系列都能通过 1、2，
   却会在 `compileKotlinDesktop` 上炸。**最终判据是编译非 Android 目标，不是读 import。**

### 维度 1 的 Android-only Compose 符号（不是 `android.*`，靠 import 前缀查不到）

`androidx.compose.ui.*` 是 KMP 包，绝大多数成员 desktop 也有；只有少数几个是 Android 独有的。
下面的清单是**解 CMP 制品实测**得到的（`ui-desktop` / `material3-desktop`，CMP 1.12.0 线），
不是推测——清单外的一律按可用处理，避免误杀：

| 符号 | desktop | 备注 |
|---|---|---|
| `androidx.compose.ui.platform.LocalContext` | ❌ | 无桌面对应物 |
| `androidx.compose.ui.platform.LocalConfiguration` | ❌ | 要用系统字体缩放请取 `LocalDensity.current.fontScale` |
| `androidx.compose.ui.platform.LocalResources` | ❌ | |
| `androidx.compose.ui.viewinterop.AndroidView` | ❌ | `viewinterop` 包存在，但没有 `AndroidView` |
| `androidx.compose.ui.res.stringResource` | ❌ | **同名不同包**：CMP 的 `org.jetbrains.compose.resources.stringResource` 可用（M1-3j 起本仓在用），按 import 溯源区分 |
| `androidx.compose.ui.res.dimensionResource` | ❌ | CMP 1.12.0 **没有** dimen 支持（实测解 jar），仍属 android-only |
| `androidx.compose.ui.res.vectorResource` | ❌ | 同上，CMP 侧 `org.jetbrains.compose.resources.vectorResource` 可用 |
| `androidx.compose.ui.res.painterResource` | ✅ | **`ui.res` 包整体存在**，只是成员不全 |
| `LocalInspectionMode` / `LocalView` / `LocalLayoutDirection` / | ✅ | 常被误判成 Android-only |
| `LocalTextStyle` / `LocalClipboardManager` / `LocalClipboard` / `LocalUriHandler` | ✅ | |

判定顺序很重要：`stringResource(R.string.x)` 真正的硬阻塞是 `R.string.x`（维度 2），
`stringResource` 自己是第二个理由。两者都要报，但**别把 `painterResource` 一起误杀**。

⚠️ **同名不同包（M1-3l 修）**：上面的 `stringResource` / `vectorResource` 一行只写了「符号名」，
而 CMP 的资源库提供了**同名的合法入口** `org.jetbrains.compose.resources.*`——M1-3j 起本仓
已用它替代 android-only 的 `androidx.compose.ui.res.*`。只按符号名扫全文会把
`Res.string.*` 的合法用法误报成 Android-only（`ReorderAccessibility.kt` 搬进 designsystem 后
就被误报过）。判据只能是「这个简单名从哪个包 import 来的」⇒ [classify] 里做一次 import 溯源
（[cmp_resource_symbols]），命中 CMP 包就不再算阻断。`dimensionResource` 无 CMP 对应物，
不受影响。

### foundation 侧也有同类成员（M1-3k 补测）

同样是「不是 `android.*`、也不是 `R`」的一族，桌面侧根本没有实现：

| 符号 | desktop | 备注 |
|---|---|---|
| `Modifier.systemGestureExclusion()` | ❌ | 底层是 `View.setSystemGestureExclusionRects`；`lazylist/VerticalFastScroller.kt` 因它搬不动 |
| `Modifier.excludeFromSystemGesture()` / `preferKeepClear()` | ❌ | 同一族 |
| `WindowInsets.statusBarsIgnoringVisibility` 及同族 `*IgnoringVisibility` | ❌ | |
| `WindowInsets.isImeVisible` / `isTappableElementVisible` / `areNavigationBarsVisible` | ❌ | 模式要求 `.name`，避免撞局部变量 |
| `WindowInsets.imeAnimationSource` / `imeAnimationTarget` | ❌ | |
| `WindowInsets.ime` / `.tappableElement` / `.captionBar` / `.waterfall` / `.safeDrawing` | ✅ | **可用**，别误杀 |
| `Modifier.{ime,navigationBars,statusBars,systemBars}Padding()` | ✅ | `WindowInsetsPadding_skikoKt` 里有，是 `expect/actual` |

⚠️ **比类名会误杀**：`WindowInsetsPadding_androidKt` / `WindowInsets_androidKt` 与
`WindowInsetsPadding_skikoKt` / `WindowInsets_notMobileKt` 是 `expect/actual` 配对，成员一致。
只有**成员级**比对（差集取名字 → 再去 desktop jar 里逐个找该符号证伪）才可信。
方法细节见 `ANDROID_ONLY_COMPOSE` 上方的注释。
"""

from __future__ import annotations

import os
import re
import sys
from collections import deque

def _discover_roots() -> list[str]:
    """除写死的两处外，再按 glob 补上全仓的模块源码根。

    写死的那两处是 M1-3j~M1-3t 的主战场。M1-3v 起战线移到 `feature/*`（首个是
    `feature/tagrules`），而 `resolve_seed` 与「同包兄弟」检测**都靠这份索引**
    ⇒ 不补的话，拿 Feature 文件当种子会直接 `!! 找不到：feature/...`，脚本用不了。

    用 glob 而不是再抄一份模块清单：模块会增删，清单会漂。

    **刻意不收 `app/src`**：`app` 里的 `io.legado.app.ui.theme` 等包与 `core:ui` /
    `core:designsystem` 同名，收进来会让 core 侧文件凭空多出指向 app 的「同包兄弟」边
    （Kotlin 同包跨模块确实可见，但对「这段代码能不能进 commonMain」是噪声）。
    `build-logic` 同理排除——它是构建脚本，不是应用代码。
    """
    import glob

    # ⚠️ **只收生产侧共享代码根**（`src/main` = Android library 模块、`src/commonMain` =
    # 已 KMP 化的模块），**刻意不收** `androidMain` / `desktopMain` / 各 `*Test`：
    #   - `androidMain` 里住着共享契约的 **Android actual**（如 `JsonCodec.android.kt`），
    #     它们天生带 `java.*` ⇒ 一旦进索引与闭包，就会把「用的是 commonMain 的 expect」
    #     误报成「种子搬不动」（脚本的结论是「闭包内任一 HARD ⇒ 搬不动」）；
    #   - 测试源集不是 Feature 的生产依赖。
    patterns = (
        "*/src/main/kotlin",          # smoke/* 之类一层模块的 Android library
        "*/*/src/main/kotlin",        # core/xxx、feature/xxx、modules/xxx
        "*/src/commonMain/kotlin",    # 一层模块的 KMP commonMain
        "*/*/src/commonMain/kotlin",  # core/xxx、feature/xxx 的 KMP commonMain
    )
    found: list[str] = []
    for pattern in patterns:
        for path in sorted(glob.glob(pattern)):
            if not os.path.isdir(path):
                continue
            norm = path.replace("\\", "/")
            if norm.split("/")[0] in ("build-logic", "app"):
                continue
            if norm not in found:
                found.append(norm)
    return found



# 会被索引的源码根（package → 声明）。前面的写死、后面的自动发现，去重在
# `_discover_roots` 内做（重复 walk 只会让 `collect_files` 返回重复路径）。
ROOTS = [
    "core/ui/src/main/kotlin",
    "core/designsystem/src/commonMain/kotlin",
] + [r for r in _discover_roots()
     if r not in ("core/ui/src/main/kotlin", "core/designsystem/src/commonMain/kotlin")]

# 顶层声明的粗略正则；`Type.member` 形式的 import 解析不了，会进未解析清单。
# 第一组 = 可见性/修饰符列表（用来把 `private` 声明从「同包兄弟」索引里剔掉），
# 第二组 = 声明种类，第三组 = 名字。
DECL = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"((?:public |internal |private |abstract |open |sealed |data |value |annotation "
    r"|expect |actual |external |inline |fun interface |enum |companion )*)"
    r"\b(fun|class|object|interface|val|var|typealias)\s+"
    r"(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)",
    re.M,
)

HARD_IMPORT = re.compile(r"^import (android\.|java\.|javax\.|kotlin\.jvm\.)")
RESOURCE_ID = re.compile(r"^import io\.legado\.app\.(core\.ui|)\.?R\b")
LEGAL_APP_PREFIX = "io.legado.app.ui."

# `androidx.compose.*` 里 desktop 侧确实缺失的成员。**不是** import 前缀能覆盖的——它们既不是
# `android.*` 也不是 `R`，只查前两者会静默放行（M1-3i 踩过：47 个文件里混着 6 个真阻塞和 3 类误报）。
#
# 实测方法（M1-3k 固化）：**必须做成员级比对，不能比类名**。
#   1. 取 `org.jetbrains.compose.foundation:foundation-desktop` + `foundation-layout-desktop` 的
#      jar，与 `androidx.compose.foundation:foundation-android` + `foundation-layout-android` 的
#      aar 解包，类路径做差集；
#   2. 差集里的 `*_androidKt` 用 `javap` 列出公开成员，得到「Android 侧有、桌面侧类不存在」的名字；
#   3. 逐个在 desktop jar 的 class 里找该符号**证伪**——差集里的类名极不可靠：
#      `WindowInsetsPadding_androidKt` 与 `WindowInsetsPadding_skikoKt`、
#      `WindowInsets_androidKt` 与 `WindowInsets_notMobileKt` 是 **`expect/actual` 配对**，
#      成员一模一样。只看类名的差集会一次性误杀 12 个成员
#      （`navigationBarsPadding` / `imePadding` / `statusBarsPadding` / `WindowInsets.ime` /
#      `tappableElement` / `captionBar` / `waterfall` / `safeDrawing` … **全都是可用的**）。
# 只列「已实测缺失」的；拿不准的一律不放进来，宁可少报也不要误杀导致误判组件不可搬。
#
# 模式一律要求 `\.name`（成员访问）或 `name(`，避免撞上同名**局部变量**
# （`feature/replacerules/ReplaceEditScreen.kt` 里就有 `val isImeVisible = …`）。
ANDROID_ONLY_COMPOSE = [
    (re.compile(r"\bLocalContext\b"), "LocalContext"),
    (re.compile(r"\bLocalConfiguration\b"), "LocalConfiguration"),
    (re.compile(r"\bLocalResources\b"), "LocalResources"),
    (re.compile(r"\bAndroidView\b"), "AndroidView"),
    (re.compile(r"\bstringResource\s*[({]"), "stringResource"),
    (re.compile(r"\bdimensionResource\s*[({]"), "dimensionResource"),
    (re.compile(r"\bvectorResource\s*[({]"), "vectorResource"),
    # —— foundation：触摸/手势排除族（M1-3k 实测；`lazylist/VerticalFastScroller.kt` 因此搬不动）
    (re.compile(r"\.systemGestureExclusion\s*\("), "systemGestureExclusion"),
    (re.compile(r"\.excludeFromSystemGesture\s*\("), "excludeFromSystemGesture"),
    (re.compile(r"\.preferKeepClear\s*\("), "preferKeepClear"),
    # —— foundation.layout：`WindowInsets.Companion` 上「带可见性/动画」的属性（M1-3k 实测）
    #    注意：同一族的 `ime` / `tappableElement` / `captionBar` / `waterfall` / `safeDrawing`
    #    以及 `*Padding()` 全都有 desktop 实现，**不要**加进来。
    (
        re.compile(
            r"\.(statusBarsIgnoringVisibility|navigationBarsIgnoringVisibility"
            r"|systemBarsIgnoringVisibility|captionBarIgnoringVisibility"
            r"|tappableElementIgnoringVisibility)\b"
        ),
        "WindowInsets.*IgnoringVisibility",
    ),
    (
        re.compile(r"\.(isImeVisible|isTappableElementVisible|areNavigationBarsVisible)\b"),
        "WindowInsets.is*Visible",
    ),
    (re.compile(r"\.(imeAnimationSource|imeAnimationTarget)\b"), "WindowInsets.imeAnimation*"),
]


def read(path: str) -> str:
    with open(path, encoding="utf-8", errors="replace") as handle:
        return handle.read()


# CMP 资源库提供的、与 Android 侧**同名**的成员。M1-3j 起本仓用
# `org.jetbrains.compose.resources.*` 替代 android-only 的 `androidx.compose.ui.res.*`：
# **同名不同包**，静态看符号名完全一样 ⇒ 判据只能是「从哪个包 import 的」。
# 实测来源：解 `components-resources-desktop-1.12.0.jar`，逐个 `javap` 确认存在。
CMP_RESOURCES_PACKAGE = "org.jetbrains.compose.resources"
CMP_RESOURCE_SYMBOLS = {
    "stringResource",       # StringResourcesKt
    "vectorResource",       # ImageResourcesKt
    "painterResource",      # ImageResourcesKt（本来就没在 ANDROID_ONLY_COMPOSE 里）
    "imageResource",        # ImageResourcesKt
    "pluralStringResource",  # PluralStringResourcesKt
    "stringArrayResource",  # StringArrayResourcesKt
    # ⚠️ `dimensionResource` **不在**此列：CMP 1.12.0 的 components-resources 没有 dimen 支持
    # （解 jar 实测无 `dimen*` 成员）⇒ 仍属 android-only，继续报。
}


def cmp_resource_symbols(text: str) -> set[str]:
    """本文件从 `org.jetbrains.compose.resources` 导入的简单名。

    通配符 `import org.jetbrains.compose.resources.*` 视作提供了全部
    [CMP_RESOURCE_SYMBOLS]（否则会被误报）。
    """
    names: set[str] = set()
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped.startswith("import "):
            continue
        fqn = stripped[len("import "):].split(" as ")[0].strip()
        if fqn == CMP_RESOURCES_PACKAGE + ".*":
            return set(CMP_RESOURCE_SYMBOLS)
        if fqn.startswith(CMP_RESOURCES_PACKAGE + "."):
            names.add(fqn.rsplit(".", 1)[-1])
    return names


BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")


def strip_comments(text: str) -> str:
    """扫符号前必须去掉注释。

    这些文件的 KDoc 会**主动解释**为什么不用某个 Android-only API（`AppDensity.kt` 里就写着
    「不读 Android 的 `LocalConfiguration`」），全文搜索会把它当成真实使用而误报 HARD。
    注释里出现的符号名不构成依赖。
    """
    return LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", text))


def collect_files() -> list[str]:
    found: list[str] = []
    for root in ROOTS:
        if not os.path.isdir(root):
            continue
        for dirpath, _dirnames, filenames in os.walk(root):
            found.extend(
                os.path.join(dirpath, name).replace("\\", "/")
                for name in filenames
                if name.endswith(".kt")
            )
    return found


def build_symbol_index(files: list[str]) -> tuple[dict, dict]:
    """返回 (all_index, sibling_index)。

    - `all_index`：全部顶层声明，用于解析 import（搬动中符号会分散在两侧）；
    - `sibling_index`：**剔除 `private` 声明**后的一份，只用于「同包兄弟」检测——
      Kotlin 的 `private` 顶层声明即使同包也不可见，拿它当依赖是误报。
    """
    all_index: dict[tuple[str, str], set[str]] = {}
    sibling_index: dict[tuple[str, str], set[str]] = {}
    for path in files:
        text = strip_comments(read(path))
        match = re.search(r"^package\s+([\w.]+)", text, re.M)
        if not match:
            continue
        package = match.group(1)
        for decl in DECL.finditer(text):
            modifiers, _kind, name = decl.group(1), decl.group(2), decl.group(3)
            all_index.setdefault((package, name), set()).add(path)
            if "private" not in modifiers:
                sibling_index.setdefault((package, name), set()).add(path)
    return all_index, sibling_index


IDENT_UPPER = re.compile(r"\b([A-Z]\w*)\b")
IDENT_CALL = re.compile(r"\b([a-z]\w*)\s*\(")


def same_package_refs(path: str, sibling_index) -> set[str]:
    """本文件**不靠 import**就用到、但定义在**同包另一个文件**里的顶层声明。

    这是静态闭包扫描的结构性盲区（M1-3j / M1-3k 各踩一次）：
    Kotlin 同包的顶层声明互相可见，**不需要 import**，而 `ui_dependencies()` 只顺着
    `import` 走 ⇒ 这类依赖在闭包里完全不出现，文件被判 `ok`。
    实测代价：M1-3j 的 `SearchBar.kt`（→ 同包 `AppDenseTextField`）、
    M1-3k 的 `lazylist/LazyList.kt`（→ 同包 `VerticalFastScroller.kt`，26KB 且带
    Android-only `systemGestureExclusion`）都被误判成可搬，**只有编译才暴露**。

    只取「大写开头的标识符」与「小写调用位置 `name(`」两类，再用
    `sibling_index[(同包, name)]` 反查，把误报压到最低（`Box` / `remember` 这类
    在 ROOTS 里没有同名声明，自然落空）。
    """
    text = strip_comments(read(path))
    match = re.search(r"^package\s+([\w.]+)", text, re.M)
    if not match:
        return set()
    package = match.group(1)
    own = {decl.group(3) for decl in DECL.finditer(text)}
    imported = set()
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped.startswith("import "):
            continue
        fqn = stripped[len("import "):].strip()
        if " as " in fqn:
            imported.add(fqn.split(" as ")[1].strip())
        else:
            imported.add(fqn.rsplit(".", 1)[-1])
    refs: set[str] = set()
    for name in set(IDENT_UPPER.findall(text)) | set(IDENT_CALL.findall(text)):
        if name in own or name in imported:
            continue
        for hit in sibling_index.get((package, name), ()):
            if hit != path:
                refs.add(hit)
    return refs


def ui_dependencies(path: str, index, unresolved: list[str]) -> set[str]:
    """本文件 import 的 `io.legado.app.ui.*` 符号落在哪些文件上。"""
    result: set[str] = set()
    for line in read(path).splitlines():
        stripped = line.strip()
        if not stripped.startswith("import "):
            continue
        if stripped.endswith(".*"):
            unresolved.append(stripped)
            continue
        fqn = stripped[len("import "):].split(" as ")[0].strip()
        if not fqn.startswith(LEGAL_APP_PREFIX):
            continue
        package, symbol = fqn.rsplit(".", 1)
        hit = index.get((package, symbol))
        if hit:
            result |= hit
        else:
            unresolved.append(fqn)
    return result


def classify(path: str) -> tuple[list[str], list[str]]:
    hard: list[str] = []
    external: set[str] = set()
    # 去注释后再判：KDoc 里常引用「不要用的那个 Android API」，那是说明不是依赖。
    text = strip_comments(read(path))
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped.startswith("import "):
            continue
        target = stripped[len("import "):].split(" as ")[0].strip()
        if HARD_IMPORT.match(stripped):
            hard.append(target)
        elif RESOURCE_ID.match(stripped):
            hard.append("R")
        elif target.startswith("io.legado.app.") and not target.startswith(LEGAL_APP_PREFIX):
            external.add(target.rsplit(".", 1)[0])
    # 维度 1 的补充：Android-only 的 Compose 成员。按符号扫全文（不只是 import 行）——
    # 它们可能经同包简写或多层转发出现。
    # 例外：该简单名若已从 CMP 资源包导入，就是「同名不同包」的合法入口，不算阻断。
    cmp_names = cmp_resource_symbols(text)
    for pattern, name in ANDROID_ONLY_COMPOSE:
        if name in cmp_names:
            continue
        if pattern.search(text):
            hard.append(f"compose-android-only:{name}")
    return sorted(set(hard)), sorted(external)


def display(path: str) -> str:
    return re.sub(
        r"^core/(ui/src/main|designsystem/src/commonMain)/kotlin/io/legado/app/ui/",
        "",
        path,
    )


def resolve_seed(seed: str, files: list[str]) -> str | None:
    """把用户给的路径归一化。

    搬动进行中时同一个组件可能刚被移到 designsystem，而习惯性写的还是旧的
    `core/ui/...` 路径；此时按键（相对 ui/ 的路径后缀）回退查找，避免用错文件。
    """
    candidate = seed.replace("\\", "/").lstrip("./")
    if candidate in files:
        return candidate
    key = re.sub(r"^core/[\w./-]*?/kotlin/", "", candidate)
    matches = [path for path in files if path.endswith("/" + key) or path == key]
    if len(matches) == 1:
        return matches[0]
    return None


def closure_of(seed: str, deps_of: dict[str, set[str]]) -> set[str]:
    seen = {seed}
    queue: deque[str] = deque([seed])
    while queue:
        current = queue.popleft()
        for dependency in deps_of.get(current, ()):
            if dependency not in seen:
                seen.add(dependency)
                queue.append(dependency)
    return seen


def main(argv: list[str]) -> int:
    raw_seeds = argv[1:]
    if not raw_seeds:
        print(__doc__)
        return 2

    files = collect_files()
    index, sibling_index = build_symbol_index(files)

    seeds: list[str] = []
    for raw in raw_seeds:
        resolved = resolve_seed(raw, files)
        if resolved is None:
            print(f"!! 找不到：{raw}", file=sys.stderr)
            return 2
        if resolved != raw.replace("\\", "/").lstrip("./"):
            print(f"·· {raw} -> {resolved}")
        seeds.append(resolved)

    seen: set[str] = set()
    order: list[str] = []
    unresolved: list[str] = []
    deps_of: dict[str, set[str]] = {}
    sibling_edges: dict[str, set[str]] = {}
    queue: deque[str] = deque(seeds)
    while queue:
        current = queue.popleft()
        if current in seen:
            continue
        seen.add(current)
        order.append(current)
        # 两条边都算：显式 import + 同包兄弟（后者无 import，是扫描的结构性盲区）。
        siblings = same_package_refs(current, sibling_index)
        if siblings:
            sibling_edges[current] = siblings
        deps = ui_dependencies(current, index, unresolved) | siblings
        deps_of[current] = deps
        for dependency in deps:
            if dependency not in seen:
                queue.append(dependency)

    verdict: dict[str, list[str]] = {}
    print(f"=== 闭包 {len(order)} 个文件 ===")
    for path in sorted(order):
        hard, external = classify(path)
        verdict[path] = hard
        tag = "HARD " if hard else "ok   "
        extra = f"  HARD[{','.join(hard)}]" if hard else ""
        extra += f"  ext-dep[{','.join(external)}]" if external else ""
        print(tag + display(path) + extra)

    if sibling_edges:
        print("\n=== 同包兄弟引用（无 import；`ui_dependencies` 扫不到，必须人工看） ===")
        for source, targets in sorted(sibling_edges.items()):
            names = ", ".join(sorted(display(target) for target in targets))
            print(f"  {display(source)} -> {names}")

    # 一个种子只要闭包里出现 HARD，它就搬不动——即使种子自身的那一行是 `ok`。
    # （M1-3j 的 `GlassCard`、M1-3k 的 `lazylist/LazyList.kt` 都是这样被误读的。）
    print("\n=== 种子结论（闭包内任一 HARD 都阻塞它） ===")
    for seed in seeds:
        members = closure_of(seed, deps_of)
        blocked = sorted(path for path in members if verdict[path])
        if not blocked:
            print(f"  可搬   {display(seed)}")
            continue
        reasons = "; ".join(
            f"{display(path)} [{','.join(verdict[path])}]" for path in blocked
        )
        print(f"  阻塞   {display(seed)}  ← {reasons}")

    if unresolved:
        print("\n=== 未解析 import（需人工确认；含 Type.member 形式） ===")
        for item in sorted(set(unresolved)):
            print("  " + item)
    print(
        "\n提醒：以上全部 `ok` 也只覆盖维度 1、2（HARD 与同包兄弟已含）。"
        "维度 3（依赖库 API 的源集可用性）必须靠 compileKotlinDesktop 验证。"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
