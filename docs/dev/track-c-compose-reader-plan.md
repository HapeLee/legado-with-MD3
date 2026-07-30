# Track C —— 声明式渲染 / Compose 阅读器迁移（执行计划）

> 本文是 MAD 收敛计划（`mad-modernization-plan.md`）中 **Track C** 的可落地展开。
> Track C 是三轨里唯一**长期**、且**依赖 A、B 产出**的一条：它把阅读器的 Canvas
> View 渲染栈渐进替换为 Compose，用声明式状态 + 本地动画状态取代大部分指令式渲染命令。
>
> 写作时点：**Track A（A1–A5）与 Track B（B1/B2）已落地**。B2 已把指令式渲染协议
> 从 ViewModel 下沉到 UI 层的 `ReadBookController`（`ReadBook.renderCallBack:
> ReaderRenderCallback`）。Track C 从这个边界继续往下走。

## 状态：已删除（2026-07-25）——本文自此为历史设计记录

> **2026-07-25**：C0–C5 的实现**已从代码库删除**。以下全文保留为设计记录与性能基线证据，
> 但**描述的代码不再存在**，不要按文中路径去找文件。
>
> **已删除**：`ComposeReaderSurface.kt`、`ComposeReaderRenderCache.kt`、`ReaderPageSnapshot.kt`、
> `ReaderRenderModel.kt`（含 `ReaderRenderStateStore`）、`ReaderRenderer.kt`（契约 + 两个实现 +
> `ReaderRenderAction`）、lab flag `LabSettings.composeRenderer` 全链（PreferKey / DataStore 映射 /
> LabConfig / 三处字符串）、`ReadBookController.publishRenderStructure()` 及其 12 个调用点、
> 单测 `ReaderRenderStateStoreTest`/`ReaderRendererVerificationTest`。
>
> **保留**：`ReaderViewport.kt`（C4 的 `ReaderViewport`/`ReaderLayoutCoordinator` 接缝——
> `ContentTextView` 与 `ReadBookController` 在用，非 Compose 专属）、
> `ReaderFirstFrame.kt`（debug 首帧探针，`tools/capture_reader_c3_first_frame.py` 依赖）。
>
> **理由**：冻结态并非零成本——每次翻页/内容更新都在 `publishStructure` 里做一遍
> 无消费方的三页快照投影。维护成本 > parity 实验台价值。若将来性能调查结论支持重启，
> 按本文设计重新实现即可。

## 原暂停决策：暂停于 C5（2026-07-21）

> **C0–C5 已落地并真机验收；此后暂停，不推进 C6–C9。**
>
> **依据**：C3 真机帧基线显示 Compose 渲染明显劣于旧 View（jank 50% vs 4.13%；50/90/95/99 = 30/42/44/53ms vs 5/8/21/65ms，见 §3 C3），且仿真卷曲/选择/滚动/自动翻页全量重写 = 高回归风险、低用户收益。故上层 MAD 计划改为「保留 `ReadView` 作渲染核心、解其自身业务耦合（Track D）」，Compose 阅读器退为**可选**。
>
> **C6–C9：暂缓（非当前主线）**。它们描述的仍是「让 Compose 成为默认并删旧栈」的路线；是否重启，取决于将来一次**专门的性能调查**结论（能否把 Compose 帧耗时/jank 拉到不劣于旧 View、且仿真卷曲有等价观感）。在此之前，**不在 overlay 上继续堆图片/选择/动画/滚动**——§4 的双栈警告在「暂停」语境下同样成立：不推进，就不该扩大双栈。
>
> 上层背景与近期主线 Track D（`ReadView` 出站解耦）设计见 `mad-modernization-plan.md`（进度快照 ⑥、Track D 节）。

## 0. 判定：为什么它必须是长期、分阶段、带开关的

- **不是全量重写**（呼应反目标）：`ChapterProvider` / `TextChapterLayout` /
  `ZhLayout` 这套**分页排版引擎**不重写，其产物（`TextChapter`→`TextPage`→
  `TextLine`→`*Column`）作为绘制数据被 Compose 复用。Track C 重写的是
  **「绘制 + 手势 + 翻页动画 + 选择」这一层**，不是排版。
- **必须带 feature flag 并行**：旧 `ReadView` 栈与新 Compose 栈共存，逐段达成
  parity 后再翻默认值、最后删旧栈。任何一步都可回滚。
- **验证只能在真机**：与 A/B 一样，「每条路径实时渲染正确」编译与单测都覆盖不到。

## 1. 现状渲染栈（Track C 的手术对象）

```
ReaderRenderCallback（B2：ReadBookController 实现）
  → ReadView (自定义 ViewGroup)
       ├─ PageView × 3（prev/cur/next）
       │     └─ ContentTextView（onDraw 里用 Canvas 逐 TextLine/TextColumn 画）
       ├─ PageDelegate（翻页动画/手势）
       │     ├─ CoverPageDelegate / SlidePageDelegate / FadePageDelegate
       │     ├─ ScrollPageDelegate / NoAnimPageDelegate
       │     └─ SimulationPageDelegate（仿真卷曲——最难）
       ├─ AutoPager（自动翻页）
       └─ 选择 / 光标 / TextActionMenu
分页排版（**不改，复用其输出**）：
  ChapterProvider / TextChapterLayout / ZhLayout / TextPageFactory
  → TextChapter / TextPage / TextLine / TextParagraph /
    TextColumn·ImageColumn·ButtonColumn·ReviewColumn·TextHtmlColumn
```

关键文件（`ui/book/read/page/`）：`ReadView.kt`、`PageView.kt`、
`ContentTextView.kt`、`delegate/*.kt`、`entities/**`、`provider/**`。

## 2. 目标数据流

```
                    ┌───────────── ReaderViewport（尺寸输入接缝）─────────────┐
                    │  widthPx / heightPx / density / contentPadding          │
                    │  旧 View 和 Compose 都能驱动同一个 ChapterProvider       │
                    └──────────────────────────┬─────────────────────────────┘
                                               ▼
ReaderSession.state（Track A）──┐        ChapterProvider（排版引擎，不动）
ReadBook.snapshot ─────────────┤              │ 产出可变 TextPage/TextLine/*Column
选择/朗读高亮/动画进度/viewport ┘              ▼
                                    ReaderPageSnapshot（不可变、renderer 专用）
                                               │  聚合进 ReaderRenderModel(StateFlow)
                                               ▼
                           Compose 阅读表面（Canvas + pointerInput）
                           - 分页结果 = 消费 snapshot 的只读几何，不直接读 TextPage
                           - 翻页动画 = Compose 本地动画状态（Animatable）
                           - 手势 = pointerInput
                           - 反转 upContentAwait 的 model→UI→model 往返
```

`ReaderRenderModel` 是 Track C 的地基：一个**只读**的渲染视图模型，聚合
A 的会话快照 + B 控制器持有的视图态（viewport 尺寸、动画进度、选择、朗读高亮、
预加载反馈）。它不持有可变领域对象，滚动高频字段与低频结构字段分流
（呼应 A4 的「高频 viewport / 低频结构」二流拆分——**Track C 才真正需要它**）。

两个新的地基构件（本版计划从「C2 脚注 / A4 预留」提升为独立阶段，理由见 §4）：

- **`ReaderViewport`**：把「谁给 `ChapterProvider` 定尺」从 `ContentTextView.onSizeChanged`
  唯一入口，抽成一个可由旧 View **或** Compose 驱动的输入接缝。**这不是重写分页引擎**
  ——`ChapterProvider` 仍是唯一排版者，只是它的尺寸/重排版触发不再是 View 的私产。
  跨过它，才可能「关掉底层正文 View，Compose 仍能独立分页」。
- **`ReaderPageSnapshot`**：当前 `TextPage` 是满是 `var` 的 data class（还内嵌
  `canvasRecorder` 等可变绘制缓存），跨线程被 Compose 直接消费有陈旧/竞态风险。
  快照是 renderer 专用的**不可变**投影（文本坐标 / baseline / 字体色 / 装饰 / 图片区 /
  交互区 / 选择范围），第一版可为既有对象的只读视图，不要求深拷贝全部文本数据。

## 3. 分阶段（每阶段独立可验证、可回滚）

> **本版排序修订（吸收一次外部审阅）**：原稿 C3 直接做「手势 + 翻页动画」，但当前
> C2 是一层**仍由旧 `ReadView` 驱动分页**的 overlay。在这层 overlay 上继续堆动画/图片/
> 选择，会把「旧 View 定尺分页 + Compose 再画一遍」焊成**永久双栈**——每加一个功能都要
> 两边同步。故把**「解除 View 尺寸依赖（`ReaderViewport`）」与「不可变 `ReaderPageSnapshot`」
> 提前**，作为一切复杂渲染之前的闸门；**翻页动画与复杂交互整体顺延**。本轨的核心里程碑
> 因此不是「Compose 又支持了一种绘制」，而是：
>
> > **关掉底层正文 `ReadView` 后，Compose 仍能独立完成分页与静态阅读。**
>
> 跨过这个节点之后做图片/选择/动画，才是在建设新架构；否则只是在扩大双栈。
> 代价要如实记账：viewport 前置是「高风险、低可演示」的一步（见 §4），先啃它意味着在拿到
> 更多可视 parity 之前先处理最硬的接缝——这是有意的取舍，不是顺手。

### C0. Renderer Contract（渲染者边界）——**已随 C3 落地**
- 定义一个显式的渲染者接口，让「旧 Canvas 渲染」与「Compose 渲染」藏在**同一个边界**
  后面，flag 只切换实现、而非叠加：
  ```kotlin
  interface ReaderRenderer {
      fun render(snapshot: ReaderPageSnapshot)   // 类型见 C5，C0 立边界时可先用占位
  }
  // LegacyRenderer（包 ContentTextView/delegate）、ComposeRenderer 各实现之
  ```
- **目的**：堵住 `ComposeReaderSurface` 直接去戳 `ReadBookController` 各方法、慢慢又耦合
  回来的路。有了这条边界，C7「Legacy 或 Compose 二选一」才是换实现，不是拆线。
- **诚实标注顺序**：C1/C2 **已落地**时并未走这条边界（overlay 直连 store/controller）。
  故 C0 是**现在补的契约**，落地时机并入 C3 稳定化——不是一个「更早做过」的阶段，而是把
  既有 overlay 的隐性耦合收敛成接口。接口的 `snapshot` 化签名依赖 C5，C0 先立
  「两个 renderer 一个边界、flag 切实现」这件事，参数类型到 C5 再定型。
- 验收：`ComposeReaderSurface` 经 `ReaderRenderer` 实现被调用，`grep` 证明它不再直接
  引用 controller 的渲染方法；flag 关时旧路径逐字节不变。

### C1. 抽取只读 `ReaderRenderModel`（无行为变化）——**已落地**（`e3f935479`）
- 在 `ReadBookController` / `ReaderSession` 之上，把当前散落在 `ReadView` 里的
  视图态（cur/prev/next `TextPage`、`durPageIndex`、viewport 尺寸、选择区间、
  朗读高亮 span、动画进度、loading）收敛成一个 `StateFlow<ReaderRenderModel>`。
- 旧 `ReadView` **继续**是唯一渲染者；C1 只是**并行暴露**状态，不接管绘制。
- 验收：编译 + 无行为变化；新 flow 的值与旧视图态逐字段一致（可加临时断言/日志）。

### C2. Compose 静态渲染表面（feature flag，默认关）——**已落地（overlay 版）**
- 实验室开关 `LabSettings.composeRenderer`（默认关，DataStore 持久化）。
- 新增 `ComposeReaderSurface`（Compose `Canvas` + `drawIntoCanvas` → native
  `drawText`），从 `ReaderRenderModel` 渲染**当前页**：复用排版引擎已算好的坐标
  （`TextColumn.start` / `TextLine.lineBase`）与 `ChapterProvider.contentPaint/
  titlePaint`，文字位置/字号/字色与旧 `ReadView` 像素一致。三区点击翻页/呼出菜单。
- 生产者：`ReadBookController` 收集 `ReadBook.snapshot`→`publishSession`，
  渲染回调末尾 `publishStructure` 当前页，喂给 C1 的 `ReaderRenderStateStore`。

> **实现取舍：overlay，而非「替换 ReadView」。** 原稿设想 flag 开时用 Compose 表面
> 替换 `ContentTextView`。落地时核实到关键耦合：**分页尺寸只由
> `ContentTextView.onSizeChanged → ChapterProvider.upViewSize` 驱动**，`UP_CONFIG`
> 重排版事件也由 View 层消费。若直接抽掉 `ReadView`，需把 `ChapterProvider` 定尺、
> 重排版触发、`UP_CONFIG` 处理全部改由 Compose 侧驱动——风险大且当前无法真机核。
> 故 C2 采用**叠加**：旧 `ReadView` 仍充当引擎（分页/内容加载/换章/手势），
> `ComposeReaderSurface` 不透明覆盖其上只负责画像素 + 点击翻页。这样 flag 关时
> 逐字节不变、可随时回退，且验证面最小。「真正替换 `ReadView`」下沉到 **C4（`ReaderViewport`
> 接缝）**——本版计划把它从这条脚注提升为独立的核心里程碑。

- **已知缺口（后续阶段）**：翻页动画（overlay 覆盖了 ReadView 的动画）、文字选择、
  `ImageColumn`/`ButtonColumn`/`ReviewColumn`、下划线与背景色/背景图、双页、滚动模式。
- 验收（真机）：flag 开，单页正文文字与旧栈位置/字号/字色一致；点击左右/中央
  分别上一页/下一页/呼出菜单;关闭开关即回旧栈。

### C3. 稳住当前 overlay + 建立 parity 基线（不加新阅读功能）——**已完成**

> 代码稳定化已落地：补齐 `ReaderRenderer` 边界，结构状态改为在旧 View 处理完
> 渲染命令后发布，并修复 `pointerInput(Unit)` 的陈旧回调捕获。真机证据及采集脚本见
> [`track-c3-reader-parity-baseline.md`](track-c3-reader-parity-baseline.md)。

> **2026-07-21 真机结果（Samsung SM-S9310，《斗破苍穹》，20 页往返）**：稳定性检查通过——
> Legacy / Compose 均回到同一正文页，无空白页、旧页闪回或章节错位。但静态 parity **未通过**：
> Compose 缺旧页眉/页脚，正文颜色/高亮与旧 Canvas 不一致；全屏 MAE 25.77、差异像素 22.72%。
> 帧基线：Legacy 50/90/95/99 = 5/8/21/65ms（jank 4.13%），Compose = 30/42/44/53ms
> （jank 50.00%）。两路径当前动画语义不同（Legacy 有翻页动画，Compose 无动画直接换页），仅作 C7 对照基线，不直接比较优劣。

> **结论**：已完成 C3 的「采集/冻结」工作，但未满足「普通文字页基本一致」的验收。继续之前先解决静态画面差异；不跨阶段进入 C4–C8。

> **2026-07-21 第二轮修正**：补齐按列颜色/搜索强调/字体/字号/API 35 letter spacing 与
> `ContentTextView` 内容偏移后，单页全屏 MAE 降至 0.023、差异像素降至 0.113%，当前普通
> 文字测试页已基本一致。采集脚本也增加了 route 冷启动、ADB 超时、前进页变化断言和五轮往返；
> 完整五轮复采后，Legacy / Compose 均回到起点，终点全屏差异仅 0.0127%，未见空白页、旧页
> 闪回或章节错位。随后补采系统衬线字体 + 增大 3 级字号、明确的第 8→9 章边界、各 5 次
> 冷启动首个非空正文帧，以及 GPU overdraw；四项均已有证据，详见
> [`track-c3-reader-parity-baseline.md`](track-c3-reader-parity-baseline.md) §8，C3 正式放行。
- **先不堆功能**，把 C2 的 overlay 收口，为后续所有阶段建立可对比的地基：
  - 旧渲染与 Compose 渲染的**截图对比**（普通文字页、换字体/字号、换章、快速翻页）。
  - 清理遗漏的状态发布入口（`publishSession`/`publishStructure` 覆盖是否有漏发）。
  - 复核 `@Stable` 使用、`pointerInput` 回调捕获是否闭包了陈旧值。
  - 建立性能基线（首帧、翻页帧耗时、overdraw），供 C7 停止双绘后对照。
- 验收（真机）：flag 开时，普通文字页与旧渲染基本一致，**无明显空白页、旧页闪回、
  章节错位**。这一步只冻结现状，不推进架构。

### C4. 抽出 `ReaderViewport` 接缝——让 Compose 尺寸能驱动分页（**已落地，真机验收通过**）

> **2026-07-21 实施记录**：已新增 `ReaderViewport` / `ReaderPadding` /
> `ReaderLayoutMode` 与 `ReaderLayoutController`，旧 `ContentTextView` 和 Compose
> `snapshotFlow` 测量统一经 `ReaderLayoutCoordinator` 驱动 `ChapterProvider`；控制器按有效正文
> 尺寸去重，避免 View/Compose 对同一尺寸重复触发分页。`UP_CONFIG(12)` 的重排版已改经该接缝，
> `upContentAwait` 先等待 viewport 就绪，并新增 `layoutResults` revision 流作为 C7 删除旧
> suspend 往返的雏形。Kotlin 编译、debug assemble、新旧渲染状态与 viewport 接缝单测通过。
>
> **2026-07-21 真机验收（Samsung SM-S9310）**：Legacy / Compose 各完成两轮、每轮 5 页
> 往返，均回到起点；普通页全屏差异像素 0.0127%，返回页差异 0%。Compose 模式从竖屏
> 5/9 重排为横屏 6/11，横屏连续翻至 10/11 后恢复竖屏落在对应内容的 8/9，未见空白、
> 旧页闪回、错章或崩溃。另用临时验收构建把 legacy surface 约束为 `0×0`，Compose 仍能
> 独立生成正文并翻页，确认分页路径不经过 `ContentTextView` layout；采证后已撤销临时改动。
> 旧层完全不绘制时背景与页眉页脚缺失，属于 C6 静态阅读闭环的既知范围，不影响 C4
> “Compose viewport 可独立驱动分页”的验收结论。
>
> **C4 回归防护补强**：`awaitViewport()` 改为最多等待 2 秒，未测量时返回 `null` 并继续旧
> 内容更新路径，避免后台加载 / 纯 TTS / View 未测量时无限挂起；单测覆盖无 viewport 超时。
> Debug Compose 同时新增 `readerDetachLegacySurface=true` Intent 验收入口与
> `tools/verify_reader_c4_detached.py`，会断言 legacy surface 确实为 `0×0`、Compose 首个正文帧
> 出现且点击后页面变化。正式 committed 路径已在 SM-S9310 复测通过，0×0 结论不再依赖
> 一次性临时构建。
- 把 `ChapterProvider` 的定尺入口从 `ContentTextView.onSizeChanged → upViewSize`
  唯一来源，抽成一个显式接缝：
  ```kotlin
  enum class ReaderLayoutMode { PAGED, SCROLL, DOUBLE_PAGE }

  data class ReaderViewport(
      val widthPx: Int, val heightPx: Int,
      val density: Float, val contentPadding: ReaderPadding,
      val mode: ReaderLayoutMode,   // 预留：C6 双页/滚动会改布局，现在留位不实现
  )
  interface ReaderLayoutController { fun updateViewport(v: ReaderViewport) }
  ```
  旧 `ContentTextView` 和 Compose 表面都调用它；`UP_CONFIG` 重排版触发同样经此接缝，
  不再是 View 层私产。**红线**：`ChapterProvider` 仍是唯一排版者，这里只搬「谁定尺 /
  谁触发重排版」，**不重写分页内核**。
  > `mode` 现在只有 `PAGED` 一条路径；留字段是为了 C6 的双页/滚动不必再引一个并列的
  > `ReaderModeController` + `ChapterProvider` 特判，避免接缝二次碎裂。
- **在本阶段后半段就开始拆 `upContentAwait`**（不要留到 C7 才动）：一旦 Compose 驱动
  viewport，若 `upContentAwait` 仍等旧 View 的 layout 完成回调，就埋了一个「Compose 表面
  看似独立、实际仍反向等 View 排版」的隐形旧架构。C4 后半把它改成
  `LayoutResultFlow`（排版完成信号）+ `snapshotFlow` 等测量完成的雏形；C7 只做最终删除。
- 验收（真机）：底层旧 `ReadView` 可设为**不可见、甚至不参与布局**，Compose 仍能正确
  生成页面；屏幕旋转 / 横竖屏 / 窗口尺寸变化能重新分页；且此路径**不经过** `ContentTextView`
  的 layout 回调。
  > 注意：这一步**不删** `ReadView`，只解除它作为「分页生命线」的作用。跨过它，Compose
  > 才从 overlay 变成能独立分页的渲染面。

### C5. 引入不可变 `ReaderPageSnapshot`（renderer 只消费快照）——**已落地，真机验收通过**

> **2026-07-21 实施记录**：新增 `ReaderPageSnapshot` / `ReaderElement` 及旧排版对象适配器，
> 在 `ReaderRenderStateStore.publishStructure` 边界把三页可变 `TextPage/TextLine/*Column`
> 投影为不可变绘制值；快照已包含文字坐标与完整画笔样式、装饰、图片区、交互区、选区和
> revision，不携带排版逻辑、反向对象引用或 Canvas recorder。`ComposeReaderSurface` 只消费
> 快照并重建私有 `TextPaint`，已无 `ChapterProvider`、旧页面实体或旧 View 绘制状态访问。
> 单测覆盖旧行列对象在发布后继续突变不会污染既有快照，重新发布才生成更高 revision。
>
> **真机验收（Samsung SM-S9310）**：debug 构建安装后，以 legacy surface `0×0` 的 committed
> detach 路径启动 Compose 阅读器，首个非空正文帧 1611ms 出现；点击下一页后画面变化
> 17.76%，正文无空白、错位或崩溃。聚焦单测、`compileAppDebugKotlin` 与
> `installAppDebug` 均通过。
- 把旧引擎输出（可变 `TextPage/TextLine/*Column`）转换为 renderer 专用的不可变数据：
  文本位置 / baseline / 字体色 / 装饰（下划线、背景）/ 图片区 / 交互区 / 选择范围。
  ```kotlin
  data class ReaderPageSnapshot(
      val chapterIndex: Int, val pageIndex: Int,
      val width: Int, val height: Int,
      val elements: ImmutableList<ReaderElement>, val revision: Long,
  )
  sealed interface ReaderElement {
      data class Text(/* 坐标、文本、样式 */) : ReaderElement
      data class Image(/* 坐标、图片引用 */) : ReaderElement
  }
  ```
- 第一版可为既有对象的**只读视图**，不要求深拷贝全部文本；但 renderer 不得再直接读
  `ChapterProvider` 或可变页面对象。呼应 A4 的「高频 viewport / 低频结构」二流拆分——
  滚动位置高频、结构快照低频，避免把 60fps tick 挂到 equality 快照。
- **护栏：别让 snapshot 长成第二个 `TextPage`。** snapshot 只描述**绘制**（坐标/样式/
  区域），不承载排版逻辑、不回挂可变对象、不塞 `canvasRecorder` 这类缓存。保持「排版
  描述」与「绘制描述」在概念上可分（未来若真需要 Web/View 多 renderer，可再把它拆成
  `PageLayoutSnapshot → RenderSnapshot` 两层）。**当前只有一个 Compose renderer，故不预先
  引入两层类型**（避免为未上路的多端渲染造抽象）——只在此立「不可反向依赖排版对象」的
  纪律，让将来分层仍可能。
- 验收：Compose renderer 只消费 snapshot，`grep` 证明其不再访问 `ChapterProvider` /
  旧 View 的绘制状态。

### C6. 完成无动画阅读闭环（按风险从低到高）——**暂缓（见顶部状态）**
在 snapshot 上把静态阅读功能补齐，**全程不碰翻页动画**：
1. 普通文本 + 标题
2. 背景色 / 背景图、下划线、朗读高亮 span
3. `ImageColumn` / `ButtonColumn` / `ReviewColumn` 等列类型
4. **双页模式**（提前）
5. 三区点击 / 交互区命中
6. 长按选择、光标、选区高亮、`TextActionMenu`（`TextActionSelectionMenu` 已是 Compose）
7. 滚动模式（60fps 热路径，验证高频 viewport 分流）
   > **双页为何提前到选择之前**：双页本质不是 renderer 功能，而是 **page window 管理**
   > ——它验证 snapshot 是否支持多页、viewport（`mode=DOUBLE_PAGE`）是否正确、prev/cur/next
   > 页缓存是否合理，比长按选择/菜单更贴近架构核心。滚动仍垫底（真正的性能模式）。
- 验收（真机）：无动画模式下，主要阅读功能可**完全脱离旧 renderer** 工作。

### C7. 停止双重绘制 + 反转 `upContentAwait`——**暂缓（见顶部状态）**
- 让 feature flag 变成 **Legacy renderer 或 Compose renderer 二选一**，而不再是
  「Legacy renderer + Compose overlay」叠加。旧 renderer 仍可短期保留作回退，
  但**不与 Compose 同时绘制**。
- **最终删除 `upContentAwait`**（拆解已在 C4 后半开始）：C4 已把它改成
  `LayoutResultFlow` + `snapshotFlow` 等测量完成的雏形；C7 在切成单栈后做收尾——
  删掉 `ReaderRenderCallback.upContentAwait` 这个 `suspend` 往返，退化为纯状态更新，
  模型不再挂起协程反向等 View 排版。若 C4 后半未能完全脱开旧 View layout 回调，C7 必须
  在这里补齐，不允许带着隐形往返翻默认值。
- 验收（真机）：Compose 模式下没有旧正文层参与绘制，overdraw 与帧耗时**不劣于**
  现有 overlay（对照 C3 基线）。

### C8. 翻页动画（垫底，Simulation 最后）——**暂缓（见顶部状态）**
- 翻页动画用 Compose 本地动画状态（`Animatable`/`updateTransition`），只消费 snapshot
  与 transition state，**不再深入改分页内核**：
  ```kotlin
  interface ReaderPageTransition {
      fun draw(previous: ReaderPageSnapshot?, current: ReaderPageSnapshot,
               next: ReaderPageSnapshot?, progress: Float)
  }
  ```
- 逐个替换 `PageDelegate` 子类，**从易到难**：`NoAnim` → `Cover` → `Slide` → `Fade` →
  `Scroll` → **`Simulation`（仿真卷曲，最后做，涉及曲面/阴影，最高风险）**。
- 每个 transition 迁完即在 flag 下与旧栈对比。放到最后的理由：动画同时牵涉当前/上/下页、
  手势进度、页面缓存、中途取消、快速连击、章节边界、预加载、方向变化——页面数据与
  viewport 未稳定前提前做，必然大量返工。
  > **Simulation 兜底**：仿真卷曲是唯一可能「Compose 做不出等价观感」的点；若代价过高，
  > 可保留它为唯一的旧栈回退项，而非阻塞整轨。

### C9. 翻默认值 + 删旧栈——**暂缓（见顶部状态）**
- Parity 全绿后把 flag 默认打开；回归窗口后删除
  `LegacyReaderRenderController` 承接的指令式渲染方法体、`ReadView`/`PageView`/
  `ContentTextView`/`delegate/*`。`ReaderRenderCallback` 收敛为「发布渲染状态」
  而非「下达渲染命令」。

## 4. 顺序纪律与风险

- **先跨 C4，再做复杂渲染**：核心里程碑是「关掉旧 `ReadView` 后 Compose 仍能独立
  分页 + 静态阅读」。在此之前，overlay 仍靠旧 View 定尺——**任何在 overlay 上做的
  图片/选择/动画都是双栈投资**，早晚要在 C4 之后重做。这是本版把 viewport 解耦
  （C4）与不可变快照（C5）提到动画前面的唯一理由。
- **C4 的代价要如实记账**：viewport 解耦是「高风险、低可演示」的一步——它没有新的
  可炫耀 parity，产出是「旧 View 可以关了」这种结构性成果。原稿把它推后正是因为
  风险大 + 当时无法真机核；本版不否认这个风险，只是判断**不先付这笔账，后面的功能
  都建在流沙上**。若真机验证阶段发现 `UP_CONFIG`/重排版触发难以脱离 View，允许把 C4
  拆成更小的子步，但不允许跳过它去做 C6/C8。
- **Renderer 边界优先（C0）**：没有 `ReaderRenderer` 契约，`ComposeReaderSurface` 会慢慢
  直连各 controller，C7 的「二选一」就退化成拆线而非换实现。C0 概念上最前，但因 C1/C2
  已落地未走此边界，实际并入 C3 补齐。
- **`upContentAwait` 拆解不等 C7**：C4 让 Compose 驱动 viewport 的同时就要开始拆这个
  `suspend` 往返，否则「Compose 看似独立、实则反向等旧 View layout 回调」的隐形旧架构会
  一直潜伏到 C7 才暴露。C7 只做最终删除与兜底。
- **先 C1 后其余**：没有稳定的 `ReaderRenderModel`，后面每个 Compose 组件都会
  各自去戳全局单例，变成「Compose 皮的分布式单体」。（C1 已落地。）
- **排版引擎不动**：`ChapterProvider` 一旦被顺手重写，风险与工期都失控。C4 只搬
  「谁定尺 / 谁触发重排版」的接缝，**不是**造新的分页引擎——`ReaderViewport` 是输入
  层，不是 `ChapterProvider` 的替代。
- **动画（C8）最后做、Simulation 垫底**：仿真卷曲是唯一可能「Compose 做不出等价
  观感」的点；若代价过高，可保留其为唯一的旧栈回退项，而非阻塞整轨。
- **性能**：滚动模式是 60fps 热路径，`ReaderRenderModel`/`ReaderPageSnapshot` 必须
  分流高频 viewport 与低频结构（A4 预留、C5 兑现），避免把滚动 tick 挂到 equality 快照上。
- **验证边界**：全程真机 parity；每阶段留可截图对比的 flag 开关，C3 的截图/性能基线
  是后续所有阶段的对照物。

## 5. 与 ViewModel 拆分的关系

Track C 完成前，`upContentAwait`/页面排版完成/翻页动画/选择取消/局部重绘/
page recorder 回收这些职责**进渲染控制器/分页引擎，不进 ReaderSession 或 VM**
（呼应主计划「ViewModel 拆分」的第三类）。C9 删旧栈后，这些职责随之消失或变为
渲染状态发布，VM 不再与渲染有任何关系。

## 6. 一句话

**立 `ReaderRenderer` 契约（C0，随 C3 补）→ 抽只读 `ReaderRenderModel`（C1，已落地）→
flag 下用 Compose 画静态页 overlay（C2，已落地）→ 稳住 overlay + 建 parity/性能基线
（C3）→ 抽 `ReaderViewport`（含 `mode` 预留）让 Compose 尺寸驱动分页、旧 `ReadView` 可关、
并开始拆 `upContentAwait`（C4，核心里程碑）→ 引入不可变 `ReaderPageSnapshot`（C5）→
无动画阅读闭环含图片/双页/点击/选择/滚动（C6）→ 停止双绘 + 删 `upContentAwait`（C7）→
翻页动画、仿真卷曲垫底（C8）→ 翻默认、删旧栈（C9）。**
排版引擎全程不动，每阶段带 flag、可回滚、真机 parity。核心不是「Compose 又多画一种
东西」，而是**先跨过「关掉旧正文 View、Compose 独立分页」这个节点，后面才是建设新
架构，而非扩大双栈**。

> **注（2026-07-21）**：以上 C6–C9 描述的是「Compose 默认化 + 删旧栈」的完整路线，**现已暂缓**（见顶部状态）。当前落点是 C5：C0–C5 冻结为 flag 下可选渲染器，阅读渲染的近期主线转为 Track D（保留 `ReadView`、解其自身业务耦合，见 `mad-modernization-plan.md`）。C6–C9 仅在将来专门的性能调查支持「Compose 不劣于旧 View」后才重启。
