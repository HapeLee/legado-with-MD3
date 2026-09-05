# Track F —— 阅读界面 KMP/CMP 迁移计划

> **状态：F0/F1 完成；F2 的共享核心已落地验证，app 仅保留明确的 UI/平台适配边界。**
> **F0**（2026-09-05）产出见 `track-f0-reader-kmp-baseline.md`。
> **F1**（2026-09-05）`build-logic` + `smoke:kmp-probe` 已落地，KMP 双 target 编译与 commonTest 验证通过。
> **F2**（2026-09-05）`:feature:reader:core` 有 54 个 `commonMain` 源文件、62 个 `commonTest` 测试文件；Desktop 与 Android host 各 300 项测试通过，`:app` 编译与 `testAppDebugUnitTest`/`verifyConfigArchitecture` 通过。
> pageestimate 平台实现已下沉 `data/reader/pageestimate/` 并删除对应 UI DAO 基线。
> app 侧仅保留 `ReaderSelection`（Compose UI state）和三个 Android/domain 适配器；它们不是待复制进 commonMain 的遗留副本。
> 写作时点：2026-09-05。文中"当前事实"均经核实；与旧文档冲突处已在 §2.3 显式指出。
>
> **范围一句话**：把阅读界面的**业务状态、排版模型与配置**推进 `commonMain`，把**文本测量**契约化；
> **渲染器本体继续留在 Android**，直到真机 parity 证据授权再动。

---

## 1. 目标与非目标

### 目标

1. 让 `feature/reader/core` 的 44 个纯 Kotlin 文件获得真实的 `commonMain` 身份 —— 由一个非 Android
   target **编译验证**，而不是靠"看起来没 import Android"来声称可移植。
2. 把阅读排版模型的消费方（朗读服务、页面估算、配置）与平台实现之间的边界，从"包名约定"升级为
   **Gradle 模块边界 + 编译期门禁**。
3. 为"将来是否共享渲染器"提供可决策的证据（parity 与性能），而不是先做再验证。

### 非目标（明确不做）

- **不把 `ReaderCanvasSurface` 当作 KMP 前置条件**。项目纪律（`.agents/skills/legado-kmp-migration/SKILL.md`
  "Non-negotiable boundaries"）明确写着：*Do not make Compose reader replacement a KMP prerequisite;
  share render models while allowing the Android renderer to remain specialized*。
- **不照搬 `shutiao/legado` 的形态**。那个 fork 已把阅读界面放进 `sharedUiMain` 并跑了四端，但它自带
  鸿蒙目标、CPF fork 工具链和一套不同的历史；本仓库应先取得**自己的**编译与 parity 证据。见 §7。
- 不同时替换数据库、网络、导航、DI 和 UI 技术栈。
- 不为了"架构完整"创建空模块。F1 之前不新建任何 `feature/reader` 的 `api/impl`。

---

## 2. 当前事实基线

### 2.1 代码分布（2026-09-05 核实）

| 路径 | 文件数 | 含 `android`/`androidx` import | 含 `android.*`（非 androidx） |
|---|---|---|---|
| `ui/book/read/`（含子目录） | 100 | 80 | 33 |
| ├ `sheet/` | 38 | — | 3 |
| ├ `pageestimate/` | 8 | — | 1 |
| ├ `config/` | 7 | — | 6 |
| └ `page/`（`entities` + `provider`） | 2 | — | 0 |
| `feature/reader/core/` | 44 | **2**（均仅 `androidx.compose.runtime.Stable`） | 0 |
| `feature/reader/platform/` | 6 | 6 | 6 |
| `feature/reader/legacy/` | 5 | — | — |

`ui/book/read/` 中 `android.*` 的引用分布（按出现次数）：

```text
Context 18 · Uri 8 · android.text.style 7 · Paint 5 · Canvas 5 · Intent 4
OpenableColumns 3 · drawable 3 · View 2 · KeyEvent 2 · speech.tts 2 · SystemClock 2
Path 2 · Toast/WindowManager/WindowInsets/ViewTreeObserver/HapticFeedbackConstants/Gravity
LruCache/Log/TextPaint/Build/SensorManager/SensorEventListener 各 1
```

### 2.2 渲染栈的真实形态（关键）

阅读正文当前**只有一条渲染路径**，不存在 View/Compose 双栈：

- `ui/book/read/ReadBookRouteScreen.kt` → `feature/reader/ReaderCanvasSurface.kt`（唯一调用方）。
- `ReadView.kt` 与 `ContentTextView.kt` **在代码库中已不存在**；`composeRenderer` lab flag
  **也无任何引用**。也就是说 Track C 文档描述的"View 默认 / Compose 可选"格局已经不成立。
- 绘制方式：`androidx.compose.foundation.Canvas` 的 `DrawScope`，但**文字与位图会逃逸到
  Android native canvas** —— `drawIntoCanvas { it.nativeCanvas.drawText(...) }`、
  `canvas.drawBitmap(...)`。`ReaderCanvasSurface.kt` 内 `android.graphics` 出现 27 次，
  其中 6 处直接 `Paint(...)`。
- 文本整形与分页样式来自 `feature/reader/platform/AndroidReaderTextShaper.kt`
  与 `ReaderAndroidPaginationStyle.kt`（`android.text.*`）。

**这条事实决定了 F5 的技术内容**：共享渲染器的实质动作，是把 `nativeCanvas.drawText`
换成 CMP 的 `DrawScope.drawText(TextLayoutResult)`，并把 `AndroidReaderTextShaper` 从
`android.text` 切到 CMP 文本测量。不是"把 View 换成 Compose"——Compose 早已是唯一栈。

### 2.3 与既有文档的出入（按 AGENTS.md 要求显式指出）

| 文档 | 文档说法 | 当前代码事实 | 处理 |
|---|---|---|---|
| `mad-modernization-plan.md` §方向修订 | "保留成熟的 `ReadView` 作为渲染核心，MAD ≠ Compose" | `ReadView` 已不存在，渲染已是 Compose Canvas | **以代码为准**。该节结论对当前代码失效，但不反向修改本文档之外的历史记录 |
| `mad-modernization-plan.md` §Track C | "C0–C5 冻结为 lab flag 下的可选渲染器" | flag 与产物均已删除（2026-07-25） | 同上；Track C 现为纯历史记录 |
| `track-c3-reader-parity-baseline.md` | C3 真机数据显示 Compose 帧耗时/jank 劣于旧 View | 旧 View 已不存在，该对比的**基线侧已消失** | 结论**不可直接用于**否决 F5；F5 若启动需**重新采集**当前栈的 parity 与性能基线 |
| `kmp-cmp-modernization.md` §4 | 阅读器渲染 = "Android 专业岛 / 不承诺跨平台" | 一致，但**未反映**渲染已 Compose 化 | 本文档推进时同步更新能力矩阵 |

> 文档与源码不一致时先指出差异，不为了让实现符合过期文档而静默改代码（AGENTS.md）。
> 上表不修改任何历史文档；能力矩阵随 F2 落地时一并更新。

### 2.4 已有的有利条件

- **测试基线扎实**：`app/src/test/.../feature/reader/` 有 **68 个**测试文件，覆盖分页、断行、
  选区、手势策略、翻页几何、下划线 run、背景图九宫格等；`ui/book/read/` 另有 **19 个**（含
  `ReadBookCallbackBoundaryTest`、`ReadBookDomainSplitBoundaryTest`、`ReaderConfigSnapshotInvariantTest`）。
  这些是 F2 的 characterization test 基础，迁移时**直接搬**即可，不需从零补。
- **真机 parity 工具已存在**：`tools/capture_reader_c3_baseline.py`、
  `tools/capture_reader_c3_first_frame.py`、`tools/verify_reader_c4_detached.py`。F5 可复用。
- **配置层已 UDF 化**（Track E + R4.1–R4.7）：`ReadSettings`（DataStore，响应式）与
  `ReadBookConfig.Config`（不可变值字段）已分离，共享 UiState 的输入侧阻力小。
- **渲染与业务已解耦**（Track A/B/D）：`ReadBook` 快照化、`ReaderSession`、渲染控制器在 UI 层，
  `feature/reader/core` 不反向依赖 ViewModel。

### 2.5 历史债基线（`build.gradle.kts` 的 `verifyConfigArchitecture`）

| 基线项 | 文件 | 当前值 |
|---|---|---|
| `legacyPreferenceCallBaseline` | `ui/book/read/ReadBookViewModel.kt` | **2** |
| `legacyDaoInjectionBaseline` | `ui/book/read/ReadBookViewModel.kt` | **0**（R2.1 已清零，保留 0 值条目盯守） |
| `legacyUiDaoAccessBaseline` | `ui/book/read/ReadBookController.kt` | **3** |
| ~~`legacyUiDaoAccessBaseline`~~ | ~~`ui/book/read/pageestimate/ExactChapterPageCountStore.kt`~~ | **已删除**（F2 step4 平台实现下沉 `data/reader/pageestimate/`，DAO 访问离开 ui 层，基线条目随之删除） |

规则：只降不升。F2 step4 已把 `ExactChapterPageCountStore` 的 Room 实现移至
`app/.../data/reader/pageestimate/`，`ui/book/read/pageestimate/` 目录随之清空，
对应基线条目已删除。`ReadBookController.kt` 的 3 仍冻结盯守。

### 2.6 工程前置现状

- **无 `build-logic`、无 convention plugin、无任何 KMP 模块** —— `kmp-cmp-modernization.md`
  的 Phase 1 尚未开始。
- 因此 **F1 是 F2 的硬前置**：在没有 KMP 模块和编译门禁之前，把 `core` 搬进 `commonMain`
  只是换个目录名，不产生任何可验证的边界（skill：*"移动目录不等于完成跨平台迁移"*）。

---

## 3. 依赖清单（逐类判定）

### A. `common-ready` —— 现有代码即可编译进 `commonMain`

| 内容 | 位置 | 备注 |
|---|---|---|
| 排版模型 | `feature/reader/core/model/` 全部 13 个 | `ReaderPage`/`ReaderRect`/`ReaderTextStyle`/`ReaderUnderlineRun`/`ReaderNineSliceLayout` 等纯 data class |
| 分页与断行 | `core/layout/` | `ReaderPaginator`、`ChineseLineBreaker`、`ReaderLayoutMath`、`ReaderChapterBlockMeasurer` |
| 手势策略 | `core/gesture/` | `ReaderTapAction`、`ReaderMainAxisPolicy`、`PullBookmarkGesture` |
| 选区 | `core/selection/` | 搜索、坐标、边界、生命周期、菜单锚点与词边界契约已共享；`ReaderSelection` 因 Compose `@Stable` 暂留 app |
| 翻页几何 | `core/transition/` | `PageCurlGeometry`、`ReaderAutoPagePolicy`、`ReaderScrollState` |
| 导航/朗读/搜索 | `core/navigation/`、`core/readaloud/`、`core/selection/ReaderSearchMatcher` | |
| 标题分段 | `core/source/` | `ReaderTitleSegmentation` |
| 页面估算 | `ui/book/read/pageestimate/`（8 文件） | 已迁：6 纯 + 2 契约进 commonMain；2 平台实现下沉 `data/reader/pageestimate/` |
| 分页方向枚举 | `ui/book/read/page/entities/` | **据证暂缓**（见 §5-F2 step4 末） |

判定依据：`core/` 44 个文件中仅 2 个含 Android-adjacent import，且都只是
`androidx.compose.runtime.Stable` 注解 —— CMP 下同名注解可用，或退化为普通 `data class` 约定。

### B. `contract-needed` —— 需要窄接口 + Android 实现

| 能力 | 当前实现 | 契约形态 | 说明 |
|---|---|---|---|
| **文本测量/整形** | `platform/AndroidReaderTextShaper.kt`（`android.text`） | **`ReaderTextShaper` 契约已存在**（`fun interface`，已消费） | **不需新建**。F3 改为审计 + 补口径测试，见 §5-F3 |
| **字体加载** | 字体文件读取 | `ReaderFontSource` 接口 | 返回 `ByteArray`/抽象 source，不暴露 `File`/`Typeface` |
| **图片尺寸** | `legacy/LegacyReaderChapterPaginator.kt` → `ImageProvider.getImageSize` | **`ReaderImageDimensionsResolver` 已存在并消费** | 已在 shared 排版边界返回 `ReaderImageDimensions?`；`null` 保持现有占位图语义。不要在没有共享像素消费者时提前建立 `ReaderImageDecoder` |
| **图片解码/缓存** | `platform/ReaderTextBackgroundLoader.kt`（`BitmapFactory`） | 暂不抽取 | 返回 `Bitmap` 且直接服务 Android Canvas；只有共享 renderer 出现真实像素消费时才以领域图像值另立契约 |
| **SVG / Path 解析** | `com.caverock.androidsvg.SVG`、`androidx.core.graphics.PathParser` | `ReaderVectorPathParser` 接口 | 下划线样式与装饰依赖 |
| **HTML → 富文本** | `platform/AndroidReaderHtmlSourceResolver.kt`（`Html`/`HtmlCompat`） | **`ReaderHtmlSourceResolver` 已存在并消费** | 输出 `ReaderHtmlParagraph` / inline source，不由共享层持有 `Spanned`；已有 Android adapter 测试 |
| 文件/URI 选择 | `Uri` 8 处、`OpenableColumns` 3 处 | 已有 `FileAccess` 契约待立（Phase 3） | 走 `Effect` 回调到宿主，不进共享层 |
| TTS 朗读 | `android.speech.tts` 2 处 | 平台能力，显式建模 | 无 TTS 的 target 返回 `Unsupported`，禁止空实现 |

### C. `platform-island` —— 保留在 Android 侧

- `feature/reader/ReaderCanvasSurface.kt`（`nativeCanvas` 逃逸绘制，27 处 `android.graphics`）
- `feature/reader/platform/` 全部 6 个文件
- `ui/book/read/config/*Span.kt`（6 个 `android.text.style` 自定义 Span）
- `ui/book/read/` 中的 `Context`/`Intent`/`Toast`/`WindowInsets`/`SensorManager`/`HapticFeedback`
- 朗读服务链路 `ui/book/readaloud/`（`android.speech.tts`）
- `ReadAiDelegate`、`MarkingDelegate`、`PhotoSheet` 等强系统耦合的 Delegate/Sheet

### D. `unknown` —— 需 PoC 或官方文档核对后再设计

| 项 | 疑问 | 验证方式 |
|---|---|---|
| CMP `DrawScope.drawText` 的中文断行/标点挤压是否可复刻当前 `ChineseLineBreaker` 行为 | 换行点口径是否一致 | F5 前的独立 PoC + 对照 `ReaderPaginatorTest` 用例 |
| 自定义字体（`fontPath`）在 CMP 各 target 的加载 | 是否覆盖 Android/iOS/Desktop | PoC 覆盖三端各一个字体 |
| `ReaderNineSliceLayout` 的九宫格背景在 CMP 下是否有对应绘制原语 | 是否需自绘 | PoC |

---

## 4. 目标接缝

**唯一接缝：`feature/reader/core` 与 `feature/reader/platform` 之间。**

```
                    ┌─────────────────────────────────────┐
   Android 宿主      │ ui/book/read/  (ReadBookRouteScreen │
   :app             │  / ViewModel / Delegate / Sheet)    │
                    └──────────────┬──────────────────────┘
                                   │ 调用
                    ┌──────────────▼──────────────────────┐
   shared (新建)     │ feature/reader/core   ← 目标：commonMain│
                    │  模型 / 分页 / 断行 / 手势 / 选区     │
                    └──────────────┬──────────────────────┘
                                   │ ReaderTextMeasurer 等窄接口（DI 注入）
                    ┌──────────────▼──────────────────────┐
   Android          │ platform/ AndroidReaderTextShaper    │
                    │  / 图片解码 / 字体 / HTML / SVG       │
                    └─────────────────────────────────────┘
```

选择理由：

1. 这条缝的**两侧已经是干净的一对多关系** —— `core/` 不 import `platform/`，`platform/` 只实现
   `core/` 声明的抽象（本次核实：`platform/*.kt` 全部 import `core.layout.*` / `core.model.*`）。
2. `core/` 的调用方边界清楚且**跨 UI 与服务**（`model/ReadBook.kt`、`service/BaseReadAloudService.kt`、
   `service/HttpReadAloudService.kt`、`ui/book/readaloud/player/ReadAloudPlayerCoordinator.kt`），
   证明它早已不是"某个页面的私有代码"。
3. 它是**风险最低、收益最先可验证**的一条：搬完即可由非 Android target 编译证明。

**不采用的接缝**（及原因）：

- 以 `ui/book/read` 整体为单位搬：100 文件里 33 个含 `android.*`，一次性切会同时动渲染、
  手势、服务、文件选择、TTS 五个风险维度，违反"一次只改变一个主要风险维度"。
- 以 `ReaderCanvasSurface` 为单位：它是 platform-island，且缺少当前栈的 parity 基线（§2.3）。

---

## 5. 分阶段计划

### F0 —— 冻结现状（无代码移动）

> **已完成（2026-09-05，revision `9ce52559c`）。产出见
> [`track-f0-reader-kmp-baseline.md`](track-f0-reader-kmp-baseline.md)。**
> 本阶段只新增 `tools/report_reader_kmp_dependencies.py` 与两份文档，未改动产品代码。

**动作**

1. 用脚本固化 §2.1 的依赖清单为 CI artifact（报告，不做门禁）。
2. 为 `feature/reader/core` 补齐 characterization test 缺口：现有 68 个测试按目录核对覆盖率，
   `core/model` 与 `core/layout` 应无未覆盖的公开纯函数。
3. 记录 `ReaderCanvasSurface` 当前行为清单作为 F5 的 parity 对照项：字体/字号、行距、字间距、
   段落缩进、标题、页眉页脚、图片、下划线、选中高亮、翻页动画、首帧耗时。
   **不重新采集**（F5 启动时采），但先固定采集条件（机型、构建变体、测试书、章节位置），
   沿用 `track-c3-reader-parity-baseline.md` §1 的格式。

**退出条件**：依赖清单可重跑；`core` 纯函数测试覆盖无缺口；parity 对照项清单落盘。

**实际结果**

- 依赖清单：157 个文件，`common-ready` 70 / `androidx-only` 44 / `platform-island` 43；
  **`feature/reader/core` 44 个文件全部 `common-ready`**；所有 `android.*` import 均归入
  已知契约分组，无未匹配项。
- 覆盖核对：**44/44 有覆盖**（4 个为间接覆盖，已逐项核实），**F2 可直接搬，不需补
  characterization test**。
- **计划修正**：核对中发现**文本测量契约 `ReaderTextShaper` 已存在且已被消费**，
  F3 由"定义契约"改为"审计 + 补口径测试"，详见 §5-F3 的改写。
- parity：14 项对照项与采集条件已落盘；另记录 C3 采集脚本因 lab flag 删除而**无法按原方式工作**，
  F5 需改为对比两个构建。

**回滚点**：仅新增脚本与文档，无需回滚。

**门禁**：G0。

---

### F1 —— KMP 基础设施（本仓库 Phase 1 的最小切片）✅ 已落地

> 严格说这是 `kmp-cmp-modernization.md` 的 Phase 1，但它是 F2 的硬前置，故纳入本轨道。
> 落地时点：2026-09-05。验证记录见下。

**动作**

1. 建 `build-logic/convention`，只定义**一种**模块类型：`legado.kmp.library`
   （Kotlin Multiplatform + Android target + 一个 JVM/Desktop target）。
   不为 iOS/鸿蒙预留 —— 没有真实 target 就不建空配置。
2. 建 **一个 smoke 模块**（例如 `:smoke:kmp-probe`），只放 1–2 个纯值类型 + 一个 `commonTest`，
   用于验证：convention plugin 可用、Android 与 JVM 双 target 编译通过、`commonTest` 能跑。
3. 核对当前 AGP / Kotlin / Compose 组合下的 KMP Android library plugin 行为，
   **通过后才固化进 convention plugin**（路线图 Phase 1 的明确要求）。

**落地形态（已验证）**

- `build-logic/`：`LegadoKmpLibraryConventionPlugin`（id `legado.kmp.library`），Kotlin 2.4.10 + AGP 9.2.1，
  仅配 `KotlinMultiplatformAndroidLibraryTarget`(compileSdk 37/minSdk 26/JVM 21) + `jvm("desktop")`，
  `commonTest` 依赖 `kotlin("test")`。注释明确写"Additional targets belong in a separate,
  evidence-backed convention once a product capability matrix requires them" —— 符合 AGENTS.md 纪律。
- `smoke/kmp-probe/`：`commonMain` 一个 `KmpProbe` 纯值类 + `commonTest` 一个断言。
  `build.gradle.kts` 只有 `id("legado.kmp.library")` 一行。
- `settings.gradle`：`includeBuild("build-logic")` + `include ':smoke:kmp-probe'`。
- `.github/workflows/verify.yml`：CI 已加 `:smoke:kmp-probe:` 的 metadata/android/desktop 编译与双 target 测试。

**退出条件**

- ✅ smoke 模块的 Android + JVM target 编译通过，`commonTest` 通过
  （`compileCommonMainKotlinMetadata` + `compileAndroidMain` + `compileKotlinDesktop` + `desktopTest` + `testAndroidHostTest` 全部 BUILD SUCCESSFUL）。
- ⚠️ `:app` 产物不变 —— 本地验证受 **Gradle 9.6.1 transforms cache 在 Windows 上的句柄泄漏**阻塞
  （`transforms/.internal/locks/*.lock` 反复"拒绝访问"，与本次代码改动无关；smoke 模块因 transform 少而通过）。
  需在锁问题解决后补跑 `:app:assembleAppDebug`，或直接依赖 CI 验证。
- 依赖违规有可读错误（convention plugin 形态保证）。

**验证命令**（任务名在模块创建后才存在，以实际生成为准）

```powershell
.\gradlew.bat :app:compileAppDebugKotlin
.\gradlew.bat testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
# 加上 smoke 模块自身的 commonTest 与双 target 编译任务
```

**回滚点**：删除 smoke 模块与 convention plugin 引用，`:app` 不受影响。

**门禁**：G0 + G1（新 Gradle 模块）。

---

### F2 —— `feature/reader/core` 迁入 `commonMain`（主体切片）

> **进度：共享核心子切片已落地并验证 ✅**（2026-09-05）。
> app 侧有意保留 Compose UI state 与 Android/domain adapter；不把它们误记为未完成的 commonMain 迁移。

**动作**

1. 新建 KMP 模块 `:feature:reader:core`（应用 F1 的 `legado.kmp.library`）。包名保持
   `io.legado.app.feature.reader.core`，**不改包名** —— 让模块提升与概念重组解耦（skill step 5）。
2. 迁入顺序（按依赖自下而上，一次一个 PR）：
   `core/model` → `core/layout` → `core/source` → `core/selection` → `core/gesture`
   → `core/transition` → `core/navigation` → `core/readaloud` → `core/style` → `core/accessibility`。
3. `ReaderSelection` 的 `@Stable` 保留在 app，直到 shared 模块有真实共享 Compose UI 消费方并完成
   Compose runtime 的独立 PoC；不得为移动目录删除稳定性语义。
4. 迁 `ui/book/read/pageestimate/`（8 文件 + 6 测试）与 `page/entities/`。
   此时**下调** `legacyUiDaoAccessBaseline` 中 `ExactChapterPageCountStore.kt` 的 3。
5. 原有 `app/src/main/.../feature/reader/core/` 在最后一个调用方切换后删除；
   未切净前保留并在 PR 中写明移除条件。

**`legacy/` 的处理**：`feature/reader/legacy/`（5 文件）含 `LegacyReaderChapterPaginator` 等，
其命名指向已删除的旧栈。F2 **只搬不重命名**，重命名单独出 PR（纯移动与逻辑修改分开）。

**core/model 子切片落地记录（2026-09-05）**

- 模块 `:feature:reader:core` 已建：`feature/reader/core/build.gradle.kts`（仅 `id("legado.kmp.library")`）；
  `settings.gradle` 已 include；`app/build.gradle.kts` 已加 `implementation(project(":feature:reader:core"))`。
- 13 个源文件从 `app/src/main/.../core/model/` 迁入 `feature/reader/core/src/commonMain/`，包名不变。
  core/model **内部零互相依赖**（13 个独立值类），迁移无排序约束。
- `ReaderBookmarkBadge.kt` 的 `@Stable`（`androidx.compose.runtime.Stable`）已移除并加迁移注释：
  本模块 commonMain 未依赖 Compose runtime，待后续子切片引入 CMP compose runtime 后恢复。
  计划授权"若 target 组合不支持，改为普通"。
- 14 个测试从 `app/src/test/.../core/model/` 迁入 `feature/reader/core/src/commonTest/`，
  JUnit 4 import 批量替换为 `kotlin.test.*`。
  `ReaderBookmarkPageTest.kt` **保留在 :app**（引用 `data.entities.Bookmark`、`navigation.ReaderPageNavigator`、`model.ReaderBookmarkState`，跨模块不可搬）。
- ✅ `:feature:reader:core` 的 `compileCommonMainKotlinMetadata` + `compileAndroidMain` + `compileKotlinDesktop` + `desktopTest` + `testAndroidHostTest` 全部 BUILD SUCCESSFUL。
  双 target 各 43 个测试，0 failures / 0 errors。
- ✅ Android classes.jar 确认包含全部 commonMain 类（`:app` 可解析）。
- ⚠️ `:app:compileAppDebugKotlin` 失败于 KSP Glide `IllegalArgumentException: different roots`
  （Gradle 9.6.1 transforms cache 跨盘 C:\ vs D:\ bug），**基线代码同样失败**，与 F2 改动无关。待 CI 验证。

**source + style + layout + navigation 子切片落地记录（2026-09-05）**

- **迁移顺序按依赖拓扑**：source(2) + style(2) → layout(6) → navigation(2)。
  source/style 只依赖已迁的 model；layout 依赖 model+source+style；navigation 只依赖 model。
  实际一次 PR 全部迁入，因为依赖链在同一次编译中即可解析。
- **依赖拓扑发现**：
  - `ReaderChapterBlockMeasurer`(layout) 依赖 source(4 类型) + style(3 类型) —— 必须先迁 source+style。
  - `ReaderPaginator`(layout) 依赖 model(7 类型) —— model 已迁，无阻塞。
  - `ReaderTextShaper`(layout) 是 `fun interface`，**零依赖**，是 F3 契约的现成原型。
  - navigation 只依赖 model，独立可迁。
- **平台依赖处理**：
  - `selection`（6 文件）暂不迁 —— 含 `java.text.BreakIterator`、`java.util.Locale`、`androidx.compose.runtime.Stable`，需契约化处理（F3 范畴）。
  - 其余子包（accessibility/gesture/readaloud/transition）待后续子切片评估。
- **测试处理**：
  - `ReaderChapterBlockMeasurerTest` 用 `kotlinx.coroutines.runBlocking` —— commonTest 加 `kotlinx-coroutines-test` 依赖。
  - `ReaderIndentTest` 用 JUnit 的 `assertEquals(expected, actual, delta)` 三参数形式 —— kotlin.test 不支持，改为 `assertTrue(abs(expected - actual) < 1e-5f)`。
  - `ChineseLineBreakerTest` 用 `assertArrayEquals` —— kotlin.test 无此 API，改为 `assertTrue(array.contentEquals(other))`。
  - `ReaderDoublePageTest` + `ReaderLongImageScrollTest` 引用未迁的 selection/transition —— 保留在 :app（恢复 JUnit import）。
  - `ReaderTitleSegmentationTest` 引用 `ui.book.read.page.provider.TitleStyleParser` —— 保留在 :app。
- **:app 编译修复**：跨模块 smart cast 问题 3 处（`ReaderCanvasSurface.kt` 2 处 + `ReadBookController.kt` 1 处），
  因为 `ReaderPage`/`ReaderElement.Text` 的属性现在跨模块，Kotlin 不再自动 smart cast public API 属性。
  修复：用局部变量显式捕获非空值或 `!!`。
- **环境修复**：Gradle 缓存路径从默认 `C:\Users\www13\.gradle` 改为 `-Dgradle.user.home=D:/Android/.gradle`，
  消除了 transforms cache 跨盘（C:\ vs D:\）导致的锁问题和 KSP Glide `different roots` 问题。
- ✅ `:feature:reader:core` 双 target 编译 + commonTest 全过：**145 个测试，0 failures / 0 errors**（desktopTest + testAndroidHostTest 各 145）。
- ✅ `:app:compileAppDebugKotlin` BUILD SUCCESSFUL（用 D 盘 cache）。
- **累计（该子切片结束时）**：commonMain 25 源文件（model 13 + source 2 + style 2 + layout 6 + navigation 2），commonTest 33 测试。
  这是后续子切片的起点，不是当前总数。

**F2 后续子切片落地记录（2026-09-05）**

- `transition`（5 源 / 6 测试）、`gesture` 中纯策略（4 源 / 4 测试）、`accessibility`（1 源 / 1 测试）
  迁入 common；Android `PageAnim` 常量映射保留为 app 测试，避免 shared 依赖 Android 常量。
- `ReaderPageViewportLayout` 与 `ReaderVisibleTextPositionPolicy` 已共享；依赖 `ReaderSelection` 的
  `selectionBounds` 保留为 app 的 `ReaderSelectionViewportAdapter`，Canvas 调用语义不变。
- `ReaderReadAloudChapter` 已共享并输出 `ReaderSpeechParagraph`；app 的
  `ReaderReadAloudSpeechAdapter` 显式映射回既有 `CanonicalSpeechParagraph`，避免 core → app domain 反向依赖。
- selection 中的搜索、边界合并、坐标映射、生命周期与菜单锚点已共享。
  `ReaderWordBoundaryResolver` 是 shared 契约，Android 的 `BreakIterator` 实现留在 app，结果显式包含
  `Unsupported`；参考 `D:/Project/shutiao/legado` 的逐字扫描方案与当前 Android 对连续中文、弯引号、连字符的
  行为不一致，未接入生产路径。
- 已迁纯测试随源码迁入；`ReaderDoublePageSelectionTest`、书签快照、标题 View-parser parity、Android adapter
  tests 保留 app。`ReaderDoublePageTest` 的无选区部分和 `ReaderLongImageScrollTest` 现为 commonTest。
- CI 已执行 `:feature:reader:core` 的 metadata、Android、Desktop 编译以及 Desktop/Android-host 测试，并上传报告。
- **当前总计**：44 个 commonMain 源文件、53 个 commonTest 测试文件；Desktop 与 Android-host 各 252 项测试，
  均为 0 failures / 0 errors。`:app:compileAppDebugKotlin` 与选区/适配器目标测试通过。
- **有意留在 app 的 4 个文件**：`ReaderSelection.kt`（Compose `@Stable` UI state）、
  `AndroidReaderWordBoundaryResolver.kt`（JDK/Locale）、`ReaderSelectionViewportAdapter.kt`、
  `ReaderReadAloudSpeechAdapter.kt`。后两者分别连接 shared 状态与 app UI/domain，保留条件是对应
  Android consumer 仍存在。

> **历史记录修正**：上方早期子切片所写“selection / accessibility / gesture / readaloud / transition 待处理”、
> “145 测试”和“25/33 文件”均是当时快照；以本节的当前总计和保留边界为准。

**pageestimate 平台实现下沉 + 基线下调记录（2026-09-05）**

step 4 原文要求“迁 `ui/book/read/pageestimate/`（8 文件 + 6 测试）与 `page/entities`，
此时下调 `ExactChapterPageCountStore.kt` 的 3”。本次按证据收尾：

- **8 文件已迁 commonMain**：`ChapterPageEstimator`、`ExactChapterPageCountStore`（interface+data）、
  `HeuristicPageEstimator`、`PageEstimateCalibration`（data）、`PageEstimateConfig`、
  `PageEstimateMetrics`、`WholeBookPageCoordinator`、`WholeBookPageIndex`，包名
  `io.legado.app.feature.reader.core.pageestimate`。6 测试迁 commonTest。
- **`WholeBookPageCoordinator` 契约化 `Dispatchers.IO`**：构造注入
  `ioDispatcher: CoroutineDispatcher`，app 侧 `ReadBook` 传 `IO`，测试传 `Unconfined`。
  commonTest 加 `kotlinx-coroutines-test`。
- **2 个平台实现下沉到 data 层**：`RoomExactChapterPageCountStore`（Room DAO）与
  `LocalPageEstimateCalibrationStore`（SharedPreferences）从
  `app/.../ui/book/read/pageestimate/` 移到
  `app/.../data/reader/pageestimate/`，包名 `io.legado.app.data.reader.pageestimate`。
  AGENTS.md “领域、数据、平台能力不能塞入 Feature UI 包” —— Room/prefs 存储属数据层，
  下沉后 `ui/book/read/pageestimate/` 目录清空。
- **基线下调**：`legacyUiDaoAccessBaseline` 中
  `io/legado/app/ui/book/read/pageestimate/ExactChapterPageCountStore.kt to 3` 条目删除。
  棘轮逻辑（`build.gradle.kts` 181-183）要求“文件移出 ui/ 后必须删除对应基线条目”，
  本次正是这条触发的下调。新路径在 `data/` 下，不受 `legacyUiDaoAccessBaseline`（仅扫 `ui/`）约束。
- **`ReadBook.kt`**：两处 import 从 `ui.book.read.pageestimate` 改为 `data.reader.pageestimate`，
  `WholeBookPageCoordinator(scope = this, ioDispatcher = IO, ...)` 注入不变。
- **测试迁移残留修复**：`HeuristicPageEstimatorTest` 两处 `assertTrue("msg", cond)` 是
  JUnit 顺序（message 在前），kotlin.test 是 `assertTrue(cond, msg)`，改为
  `assertTrue(cond, "msg")`。此前 `compileCommonMainKotlinMetadata` 通过但双 target 测试未跑，
  该 bug 潜伏至此。其余 `assertEquals(expected, actual)` 两参数形式两边一致，无需改。
- ✅ `:feature:reader:core` 双 target（desktopTest + testAndroidHostTest）BUILD SUCCESSFUL。
- ✅ `:app:compileAppDebugKotlin`、`testAppDebugUnitTest`、`verifyConfigArchitecture` 全过。

**`page/entities` 据证暂缓（偏离 step 4 原文）**

step 4 原文把 `ui/book/read/page/entities/`（`PageDirection`、`TitleSegment`）与 pageestimate
并列要求迁入 commonMain。本次核查后**暂缓**，依据：

- `PageDirection`（`NONE/PREV/NEXT`）与 `TitleSegment` 在 commonMain/commonTest **零引用**，
  只被 :app 消费（`ReadBookController`、`ReadBookRouteScreen`、`MainActivity`、`TitleStyleParser`）。
- `TitleSegment` 的唯一消费方 `TitleStyleParser` 本就要留 :app 做 parity
  （`ReaderTitleSegmentation` 已在 commonMain `core/source/` 提供等价能力）。
- §3-A 把它列为 `common-ready` 的依据是“无 android import”，不是“有 shared 消费方”。
  按 AGENTS.md“commonMain 只容纳经过依赖审计、能被至少一个非 Android 目标编译验证的代码”
  与“不为架构完整创建无调用方抽象”，以及本计划 F6 自己的“搬它是纯粹的目录整理，不产生收益”，
  无 shared 消费方时机械搬家不产生跨平台价值。
- `PageDirection` 与 commonMain `transition/ReaderTurnDirection`（`PREVIOUS/NEXT`，无 `NONE`）
  并行但不等价；待 F6 出现真实 shared reader route 或共享翻页契约时再迁，并届时合并两者口径。

**这是对 step 4 原文的证据性偏离，已在此记录；不为符合原文而做无消费方的目录搬迁。**

**退出条件**

- 所有已迁 commonTest 通过。
- **至少一个非 Android target 编译通过**（G2 的核心，不是可选项）。
- Android 端同一批行为测试仍通过。
- `app/src/main` 中不再存在可共享 core 的重复实现；仅允许记录在上文的 UI state 与平台/domain adapter。

**验证命令**

```powershell
.\gradlew.bat :app:compileAppDebugKotlin
.\gradlew.bat testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
# 加上 :feature:reader:core 的 commonTest、metadata 编译、JVM target 编译任务
```

**回滚点**：每个子 PR 可独立 revert；`:app` 始终可编译可发布。

**门禁**：G0 + G1 + G2（首个 `commonMain` 变化，按路线图"随首个 KMP 模块启用"）。

---

### F3 —— 平台能力契约化（文本测量：**契约已存在**，改为审计与补测）

> **F0 核实（2026-09-05）改写了本阶段**。文本测量的契约**不需要新建** ——
> `feature/reader/core/layout/ReaderTextShaper.kt` 已经是一个 `fun interface`，且被分页链路
> 实际消费。本阶段从"定义契约"改为"**审计完备性 + 补口径测试 + 收敛注入点**"，
> 工作量与风险都显著低于初版估计。

**现状**

```kotlin
// core/layout/ReaderTextShaper.kt —— 无 Android import，已是 common-ready
fun interface ReaderTextShaper {
    fun shape(text: String): GlyphClusters
    val fontBounds: ReaderFontBounds? get() = null
    val fontLineMetrics: ReaderFontLineMetrics? get() = null
}

// core/layout/ReaderChapterBlockMeasurer.kt:13-14
fun interface ReaderTextShaperFactory { fun create(style: ReaderTextStyle): ReaderTextShaper }

// core/layout/ReaderLayoutMath.kt:3,5 —— 字形簇切分已在共享层，有 ReaderLayoutMathTest
data class GlyphClusters(val text: List<String>, val widthsPx: List<Float>)
fun clusterGlyphs(text: String, measuredWidthsPx: FloatArray, start: Int = 0): GlyphClusters
```

- 唯一实现：`platform/AndroidReaderTextShaper(paint: TextPaint)`。
- 消费方：`core/layout/ReaderChapterBlockMeasurer`（`bodyShaper`/`titleShaper`）、
  `legacy/LegacyReaderChapterPaginator`（注入 Android 实现）。
- `FloatArray` 是 `kotlin.FloatArray`，在 `commonMain` 合法，**不是**平台泄漏。

**为什么这个契约比参照样本更适合跨平台**：它返回**字形簇**而非逐字 `FloatArray`，
把 emoji / 组合字符 / 连字的切分留给平台实现，共享层只见 `List<String>` + `List<Float>`。

**F3 首个子切片完成记录（2026-09-05）**：未扩展 API、未新增 target、未改 DI 或替换
`TextPaint`。新增 shared contract tests 固定以下语义：组合附加符归入前一字形簇；ZWJ 等零宽
连接控制符保持独立簇；`start` 从传入测量数组的偏移读取；`ReaderFontBounds` 的行高/基线计算；
`ReaderFontLineMetrics` 的正值 ascent/descent 口径与默认值。Android adapter contract test 固定
`TextPaint.FontMetrics` 到这两组 shared metrics 的映射、空串输出和组合字符输出守恒。

审计结论：现有 `shape`、`fontBounds`、`fontLineMetrics` 与 `ReaderTextShaperFactory` 足以表达当前
`ReaderChapterBlockMeasurer` 的输入、输出、单位（px）和“无精确度量则 null”的语义，**无需 API
扩展**。Android 注入点仍仅在 `legacy/LegacyReaderChapterPaginator`；第二实现和非 Android 真实
文本宿主均不存在，因此不创建伪实现。

**范本对照补充（2026-09-05）**：`D:\Project\shutiao\legado` 的 `TextMeasurer` 与
`BookImageLoader` 证明“普通接口 + 平台实现”是有效方向，但它们同时已有 Desktop/iOS/鸿蒙宿主、Skia 与
Coil 消费链。本仓库不复制其全局 provider registry：`ReaderTextShaper`、`ReaderHtmlSourceResolver` 和
`ReaderImageDimensionsResolver` 已是具真实 shared caller 的窄契约，Android 实现/调用方也已有测试。
`ReaderTextBackgroundLoader` 仍返回 Android `Bitmap` 给 Canvas，没有第二宿主或 shared 像素消费者；在此
之前新增 decoder 契约只会制造无调用方抽象。

**另一个关键设计（避免了一整类口径问题）**：`AndroidReaderTextShaper` 内部
`TextPaint(paint).apply { letterSpacing = 0f }` —— 字间距从 Paint 剥离，由分页层插入。
这**绕开了** `shutiao/legado` 里最头疼的 letterSpacing 跨平台口径问题（含 API 35+
`getTextWidths` 砍掉首尾半格那段差异）。**本项目不需要做那套补偿。**

**动作**

1. 审计契约完备性：对照未来 target 的实际需求，确认 `shape` / `fontBounds` /
   `fontLineMetrics` 三件套是否够用；**有缺口才补，不为对称而补**。
2. **补口径测试**（本阶段主要工作量），放 `commonTest`：
   - `clusterGlyphs` 边界：空串、纯组合字符、代理对（emoji）、串首/串尾零宽字符。
   - `fontLineMetrics` 的 `leading` 参与行高、`ascentPx` 取 `-it.ascent` 的符号口径。
   - `fontBounds` 与 `fontLineMetrics` 的一致性 —— 两者 `baselineOffsetPx` 算法不同
     （前者 `heightPx - descentPx`，后者 `height - it.descent`），需固定预期。
3. 收敛注入点：确认 `ReaderTextShaperFactory` 的 Android 实现是否只在
   `legacy/LegacyReaderChapterPaginator` 注入；F2 搬迁时明确工厂归属。
4. 为 `ReaderFontSource`、`ReaderImageDecoder` 立契约 —— **这两个确实还没有**，可拆独立 PR。

**下一执行切片（推荐）**

先做 `ReaderTextShaper` 的**行为契约审计与 commonTest 补测**，不新增 target、不替换 Android
`TextPaint`、不改 DI：

1. 以 `ReaderChapterBlockMeasurer` 的现有调用为边界，列出 `shape`、`fontBounds`、
   `fontLineMetrics` 的输入、输出、空值和单位语义。
2. 给 `clusterGlyphs`、行高/基线计算补纯 Kotlin characterization tests；Android adapter 仅补一组
   contract test，证明其输出满足共享分页规则。
3. 若测试显示现有三件套足够，记录“无 API 扩展”；只有可观察缺口才增加字段或新契约。

这比立刻把 `ReaderSelection` 搬进 shared 更优先：后者需要一条共享 Compose UI 消费路径和 Compose
runtime PoC，目前不存在；前者已有真实 Android adapter 和 shared caller，能直接降低 F3 风险。

**不做**：不实现第二个 target 的 `ReaderTextShaper`（没有真实 target 就不写）。

**退出条件**：新增口径测试通过；共享层无平台类型；契约审计结论已记录（含"够用/缺什么"）。
首个子切片已满足；后续仅在出现真实非 Android 文本宿主时，为其实现同一契约并补 adapter parity。

**门禁**：G0 + G3。

---

### F4 —— 配置与状态共享

**首个子切片完成记录（2026-09-05）**：`ReaderRenderUiState` 经审计后**不能整体迁入**：其
`background.drawable` 是 Android `Drawable`，而 `ReaderSessionViewModel` 还负责入口动画期间的热路径
提交，二者都留在 app。已将其中唯一平台无关、具真实调用方的只读部分抽为
`core/navigation/ReaderRenderState`：`ReaderPageWindow` + `paginationError`。app 的
`ReaderRenderUiState` 组合该 shared snapshot 与 `ReaderBackgroundState`，保留原有只读访问器，
所以 `ReadBookRouteScreen` 的消费和会话发布时机不变。

共享模型在 Desktop / Android host 下由 commonTest 覆盖默认空窗口、错误与预置页面引用；Android 侧
`ReaderSessionViewModelTest` 保持验证入口未 settle 时缓存错误、背景与页面窗口立即发布、settle 后整体提交
的行为。没有触碰 DataStore、`ReadBookViewModel` 偏好调用或 DAO，因此没有可下调的历史基线。

**第二个子切片完成记录（2026-09-05）**：原 `app/model/ReadBook.kt` 的
`LegacyReaderSnapshot` 同样只含书籍标识、章节位置/数量和本地标记等标量；它已由
`ReaderSession.state` 实际公开，适合成为 shared 会话值。已迁为
`core/navigation/ReaderSessionSnapshot`，app 保留 `typealias LegacyReaderSnapshot`，避免本次
边界提取牵连遗留调用方命名。新增 commonTest 固定默认值和完整发布快照；不移动
`ReaderSession`/`LegacyReaderSession`，因为其 `StateFlow` 所有权、回调槽位和 `ReadBook` 命令仍是
Android 迁移桥。没有新增 DI、平台接口或第二状态副本。

**动作**

1. 把 `ReaderRenderUiState`、排版快照等**只读状态模型**迁入共享层（前提是 F2/F3 已让它们的
   字段类型平台无关）。
2. `ReadSettings`（DataStore）**留在平台侧**，共享层只见领域值 —— 不引入 `*SettingsUpdate`
   分发类型（AGENTS.md 明令禁止）。
3. 若此阶段改动了 `ReadBookViewModel` 的旧偏好调用点，**同步下调** `legacyPreferenceCallBaseline`
   的 2；若动到 `ReadBookController` 的 DAO 直连，同步下调其 3。

**退出条件**：`verifyConfigArchitecture` 通过且相关基线已下调；Android 行为测试通过。首个状态模型
子切片已完成；后续只审计同样满足“纯值 + 真实 shared caller”的排版快照，设置、Drawable 和状态宿主不在范围内。

**门禁**：G0。

---

### F5 —— 渲染器去 Android 逃逸（**可选，需 G5 人工审批**）

> 本阶段不承诺执行。启动前提是 F0 的 parity 对照项清单 + 当前栈性能基线已采集完成。

**动作**

1. 用 `tools/capture_reader_c3_baseline.py` 的既有方法**重新采集当前栈**（Compose + nativeCanvas）
   的 parity 截图与 `gfxinfo` 性能数据 —— 作为**唯一有效基线**（旧的"View vs Compose"对比已失效，§2.3）。
2. 独立 PoC：把 `nativeCanvas.drawText` 换成 `DrawScope.drawText(TextLayoutResult)`，验证 §3-D
   的三个 unknown（中文断行口径、自定义字体、九宫格背景）。
3. PoC 通过 parity 与性能比对后，才进入正式替换；否则**停在 PoC**，把结论写回本文档。

**退出条件**：真机 parity 通过；帧耗时/jank 相对 F5 基线无劣化；翻页动画、选区、自动翻页
逐项人工验证。

**门禁**：**G5（强制人工审批）**。

---

### F6 —— 外围 UI 共享（最后评估）

`ui/book/read/sheet/`（38 文件，仅 3 个含 `android.*`）看起来是最像"可共享 UI"的一块，
但**它的共享价值取决于是否存在第二个宿主**。在 Desktop/iOS 成为正式产品 target 之前
（路线图 Phase 7），搬它是纯粹的目录整理，不产生收益。

**启动条件**：出现第二个真实宿主，或产品明确承诺某个 target。

---

## 6. 门禁汇总

| 阶段 | 门禁 | 关键验证 |
|---|---|---|
| F0 | G0 | unit test、lint、`verifyConfigArchitecture`、`assembleAppDebug`、`git diff --check` |
| F1 | G0 + G1 | convention plugin 可用、模块依赖图无禁止方向、Android 产物不变 |
| F2 | G0 + G1 + **G2** | `commonTest` 通过、**非 Android target 编译通过**、公共 API 无平台类型 |
| F3 | G0 + G3 | adapter contract test（成功/失败/取消语义）、口径对齐测试 |
| F4 | G0 | 基线同步下调 |
| F5 | G0 + **G5** | 真机 parity、性能基线、release/noR8 或专项验证、**人工审批** |
| F6 | G0 + G4 | 目标平台 smoke、Effect/导航 parity |

能力状态一律按 **compile / contract-test / smoke / package / release-ready** 分级记录，
不合并成单一"支持"标记。

---

## 7. 与 `shutiao/legado` 的关系：借鉴什么、不借鉴什么

同仓库的另一个 KMP fork（`D:\Project\shutiao\legado`）已把阅读界面放进 `sharedUiMain`，
并跑通 Android / Desktop / iOS / 鸿蒙四端。它证明了**渲染器可共享**这件事技术上成立，
是 F5 最有价值的外部证据。但本轨道**不复制它的形态**：

| 维度 | shutiao/legado | 本仓库采用 | 理由 |
|---|---|---|---|
| 文本测量 | `TextMeasurer` 接口 + 四平台实现 | **借鉴**（F3） | 与本项目"优先普通接口 + DI"的纪律一致；其 letterSpacing 口径对齐注释可直接参考 |
| 绘制原语 | `DrawScope.drawText(layoutResult)` | **作为 F5 目标** | 需先过本项目自己的 parity/性能基线 |
| 模块形态 | 单一 `shared` 大模块 + 17 个 source set | 按 Feature 拆分模块 | 本项目路线图以垂直 Feature 为单位，避免通用 dumping-ground 模块 |
| 鸿蒙 target | 支持，需 CPF fork 工具链 | **不引入** | 本项目能力矩阵无鸿蒙目标；引入会带走 Kotlin 2.2.21/CMP 1.9.2 版本回退与 Room schema 派生等复杂度 |
| 中间 source set | `sharedUiMain`、`jvmAndAndroidMain`、`skikoUiMain` 等 17 个 | 需要时再加，且需同时验证 Gradle/IDE/消费方 | 路线图明确：自定义中间 source set 需验证消费方 target，不能先建 |
| 一次性搬完整阅读界面 | 已完成 | **不采用** | 违反"一次只改变一个主要风险维度"；且缺少本仓库自己的中间态证据 |

---

## 8. 风险与未验证项

| 风险 | 等级 | 说明与应对 |
|---|---|---|
| **F1 未启动导致 F2 空转** | 高 | 无 KMP 模块与编译门禁时，搬 `core` 只是改目录。F1 是硬前置，不可跳过 |
| **旧 parity 结论被误用** | 高 | C3 的"Compose 劣于 View"基线侧（View）已不存在。F5 必须重新采集，不得引用旧结论否决或放行 |
| 中文断行口径在 CMP 下不一致 | 中 | §3-D 列为 unknown，F5 PoC 优先验证；对照 `ReaderPaginatorTest` 与 `ChineseLineBreakerTest` 现有用例 |
| `ReaderSelection` 的 `@Stable` 共享时机不明 | 低 | 当前保留 app；只有共享 Compose UI 有真实消费者时才做 Compose runtime PoC，不删除稳定性语义换取目录迁移 |
| 迁移期间 Android 行为回归 | 中 | 每阶段保留 G0；F2 的 68+ 个测试随代码迁移，不重写 |
| `legacy/` 命名与已删除旧栈冲突 | 低 | F2 只搬不重命名，重命名独立 PR |
| 基线被抬高以让迁移通过 | 中 | 门禁规则：只降不升。F4 明确列出需同步下调的 3 个条目 |

**本计划尚未验证**：Desktop 仅达到 compile + common contract-test 证据，尚无 reader host smoke、打包或发布就绪证据；
CMP 文本测量与当前 `android.text` 整形的逐字一致性（F3 验证）；渲染器替换后的真机性能（F5 验证）。

---

## 9. 交付检查（每阶段 PR 必附）

- [ ] 前后依赖变化（新增/删除的 import 与模块边）
- [ ] 行为不变量与对应测试（列出测试文件名）
- [ ] `expect/actual` 数量净变化（本轨道预期为 **0**，F3 只用普通接口）
- [ ] 精确验证命令与结果
- [ ] 能力矩阵状态变化（compile / contract-test / smoke / package / release-ready 分别记录）
- [ ] 历史债基线的同步下调（涉及哪几个文件、从几降到几）
- [ ] 未验证项与回滚路径
