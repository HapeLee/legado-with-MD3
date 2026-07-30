# Track E —— 阅读正文界面「设置 + UDF」现代化改造计划

> **状态（2026-07-30）：本轨道已全部走完。** E0 ✅ → E1 ❌ 撤销 → E2 ✅ → E3 ✅ → E4 ✅ → E5 ✅（并入 R4.1）。
> 验收总表见 §5。本文档的**事实基线（§1）是 2026-07-25 的快照，已不代表当前代码**——读它是为了理解
> 每条债的由来与当时的判断，不要照着里面的行号去改代码。E5 之后还追加了配置底座的所有权反转
> （R4.1–R4.7），由来见 [mad-modernization-plan.md](mad-modernization-plan.md) 的 Track E 节。

> **定位**：本轨道**不改渲染方式**（不 Compose 化正文、不动 `ReadView` 的绘制/手势/动画），只解决
> 阅读正文界面**配置的数据流**问题。与 [Track D](mad-modernization-plan.md#track-d)（`ReadView`
> 出站业务解耦）正交、可并行：D 管「View 怎么把业务意图发出去」，E 管「设置怎么流进来」。
>
> **前置背景**：[reader-settings-apply-channels]、[reader-config-flow-audit]（两条 memory）已记录
> 三条应用通道与 2026-07-24 的最小修复。本计划把那些点状修复升级成结构性收敛。

---

## 1. 事实基线（本轮逐条核实，均带行号）

### 1.1 两个存储底座，语义完全不同

| 底座 | 内容 | 存储 | 是否响应式 |
|---|---|---|---|
| **A. `ReadSettings`** | 101 个字段（手势/亮度/菜单外观/键位/朗读…） | DataStore | ✅ `preferencesFlow` → 真 `StateFlow`（[ReadSettingsRepository.kt:29-33](../../app/src/main/java/io/legado/app/data/repository/ReadSettingsRepository.kt#L29)） |
| **B. `ReadBookConfig.Config`** | 排版预设（字号/行距/标题/页眉页脚/下划线/背景…），即 `readConfig.json` | JSON 文件 + 内存 `ArrayList<Config>` | ❌ **可变全局单例，无 flow**（[ReadBookConfig.kt:31,77,103](../../app/src/main/java/io/legado/app/help/config/ReadBookConfig.kt#L31)） |

`ReadBookConfig` 目前是**混合门面**：A 类字段是 `get() = readSettings.x` 只读转发（:182-243，干净），
B 类字段是 `var x get/set config.x`（:310+，可变）。同一个对象两种语义，是"乱"的第一层来源。

### 1.2 底座 B 无 flow ⇒ UDF 是手工模拟的

`ReadStyleGateway.state` 只暴露 `items / selectedIndex / shareLayout` 三项
（[ReadStyleState.kt](../../app/src/main/java/io/legado/app/domain/model/settings/ReadStyleState.kt)），
**不含任何排版字段**。于是 VM 只能在每次写入后**手动重建**两份快照：

- `buildStyleConfig()`（[ReadBookViewModel.kt:2290](../../app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt#L2290)）—— 从可变全局裸读 ~25 字段
- `buildSheetConfig()`（:2322）—— 从可变全局裸读 ~50 字段

**失败模式是结构性的**：任何新增的写入路径只要忘了重建，弹层就显示旧值。2026-07-24 的
`sheetConfig 不重建` 就是这个类别的实例——修了 5 个站点，但**下一个新站点仍然会犯同样的错**，
因为编译器管不着。

### 1.3 渲染副作用靠 157 条手写映射表

`ConfigUpdate` 有 **157 个成员**，每个手写 `actions: Set<ConfigUpdateAction>`（13 种动作）
（[ReadBookContract.kt:1006-1558](../../app/src/main/java/io/legado/app/ui/book/read/ReadBookContract.kt#L1006)）。

- 手写 ⇒ 错配即「改了不生效」。已确认两例并已修：`StatusIconDark`（缺 `UpdateSystemUi`）、
  `UnderlineColor`（缺 `UpdateContent+InvalidateTextPage+SubmitRenderTask`）。
- **57+ 个成员 `actions = emptySet()`**（菜单外观类：`MenuBlurRadius`/`MenuIconStyle`/
  `FloatingBottomBar`…）。它们写 DataStore、走 Compose 重组通道，和排版类成员**通道完全不同**，
  却混在同一个 sealed interface 里，靠"手写空集"表达"我不走这条路"。

### 1.4 UI 层直读可变全局

| 文件 | `ReadBookConfig.*` 直读 | `remember { mutableXStateOf(ReadBookConfig.x) }` 本地镜像 |
|---|---:|---:|
| `sheet/HeaderFooterPage.kt` | 69 | 29 |
| `sheet/TextTitleSheet.kt` | — | 18 |
| `sheet/SystemMenuPage.kt` | 4 | 7 |
| `sheet/CustomTipTarget.kt` | 13 | — |

这是**真正的 UDF 断链源**：Compose 从可变全局 seed 本地状态，重开弹层重新 seed。上游一旦
不经这些控件改值（预设、导入、日夜切换、另一处弹层），控件就显示陈旧值。

### 1.5 EventBus 整数码仍在

`postEvent(EventBus.UP_CONFIG, arrayListOf(整数码))` —— 8 个生产者，VM 在
[:2084](../../app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt#L2084) 把整数翻回
`ConfigUpdateAction`。生产者：`ApplyReadSettingUseCase`×2、`ChapterProvider:1111`、`ReadBook:304`、
`ThemeConfigStore`×2、`ReadAloudPlayerCoordinator:146`。

### 1.6 写入面已经不差（重要，别推倒）

排版写入**已经**收敛到 `ReadStyleGateway.updateCurrentStyle(ReadStyleMutation)` 的类型化键
（`ReadStyleIntKey`/`FloatKey`/`BooleanKey`/`StringKey`/`ColorKey`，
[ReadStyleMutation.kt](../../app/src/main/java/io/legado/app/domain/gateway/ReadStyleMutation.kt)），
全库对 `ReadBookConfig.x = v` 的直接写入**只剩** `ReadBookStyleConfigRepository` 一个文件。
**Track E 不重做写入面**，只补"写完之后怎么让所有人看到新值"。

### 1.7 附带确认的两个缺陷

- `ReadSettingsRepository.update{}` 的 `toGatewayPrefMap` 只覆盖 **45/101** 字段（:550-596）。
  > **更正（E0 落地时核实）**：这**不是现存 bug**，是有意设计——`ReadSettingsGateway.update`
  > 的 KDoc 明确写了「仅持久化 gateway 实现映射声明的 45 个配置键，其余字段仍须通过对应的
  > 遗留 Repository setter 写入」。全库只有 4 个 `update{}` 调用点（ThemeConfigViewModel:141、
  > ReadBookViewModel:265/5237、MangaMenu:284），写的都是**已在 map 内**的字段。
  > 真实风险是**latent**：将来有人对那 56 个字段之一 `update{ copy(x=…) }` 会被静默丢弃。
  > 因此 E0 不去凑满 101，而是把「走不通 `update{}` 的字段集合」冻成基线做双向棘轮。
- 排版引擎 `ChapterProvider`(62 处)/`PageView`(54)/`TextChapterLayout`(11)/`TextLine`(12)
  在排版与绘制时**直接裸读** `ReadBookConfig` ——渲染输入不是参数，是全局。

### 1.8 规模参考

`ReadBookViewModel.kt` 6546 行；`ReadBookContract.kt` 1558 行（Intent 265 / UiState 91 字段 / Effect 74）。

---

## 2. 目标与反目标

**目标**：排版设置有**唯一可信来源 + 一条派生链**，"改了不生效 / 弹层显示旧值"从
「靠人记得补一行」降级为「编译期或测试期挡住」。

**反目标（明确不做）**

- ❌ 不把正文渲染 Compose 化（Track C 已冻结，见 [track-c-compose-reader-plan.md](track-c-compose-reader-plan.md)）。
- ❌ 不为 UDF 把瞬时控件状态（触摸/动画进度/每帧 invalidate）塞进 `StateFlow`。
- ❌ 不重做已经干净的写入面（§1.6）。
- ❌ 不做"一次性大重构"：157 个 `ConfigUpdate` 成员必须能分批迁移，每批可单独真机验收。
- ❌ 不追求把 `ReadBookConfig` 一次性删掉——它是 27 个文件的读入口，E5 之前保留门面。

---

## 3. 阶段划分

### E0 —— 防回归地基 ✅ 已落地（2026-07-25）

纯新增测试，产品代码零改动。三个文件、8 条断言，全部采用本仓既有的**双向棘轮**惯例
（新增违规报红 / 基线修好了不下调也报红）。

1. **`ConfigUpdateActionsInvariantTest`**（`ui/book/read/`）—— 反射遍历 `ConfigUpdate`
   全部 157 个成员并实例化，断言 `actions` 要么非空、要么类名在 `NO_RENDER_EFFECT`
   白名单（52 条）里；反向断言白名单无失效条目；外加一条「反射确实枚举到成员」防假绿。
   > 直接封死 `UnderlineColor` 那类错配的复发。原计划由 E1 迁族后删除白名单，
   > **E1 已撤销**，故白名单转为长期资产——它就是「这 52 项确认不需要渲染副作用」的备案。
2. **`ReaderConfigSnapshotInvariantTest`**（`ui/book/read/`）—— 反射取
   `ReadSheetConfigUiState`(50 字段) / `ReadBookStyleConfig`(25 字段) 的构造参数名，
   源码扫描 `buildSheetConfig()` / `buildStyleConfig()` 的具名实参，断言无遗漏。
   > 用源码扫描是因为两个函数是 VM 的 private 成员、VM 需大量 Koin 依赖才能构造。
   > **E2 之后应改写**：那时它们变成 `ReadStyleSnapshot` 上的纯函数，可以直接实例断言。
3. **`ReadSettingsGatewayCoverageTest`**（`data/repository/`）—— **行为性**判定：逐字段
   变异 `ReadSettings` 并观察 `toGatewayPrefMap()` 输出是否随之变化，把「走不通
   `update{}` 的 56 个字段」冻成基线。**不**断言 101 == 45（那会推翻 §1.7 的既定设计）。

**变异验证**（每条都实测过能变红，非空跑）：白名单少一条 → 测试 1 红；白名单多一条 →
测试 2 红；给 `ReadSheetConfigUiState` 加字段不赋值 → 测试 3 红；给 `ReadSettings`
加字段不接线 → 测试 4 红。四条报错都直接给出字段名与修法。

**现状**：全量单测 285 条绿（E0 前 277）。

---

### E1 —— ~~按通道拆分 `ConfigUpdate`~~ ❌ 已撤销（2026-07-25，前提不成立）

> **撤销理由（E0 之后核实）**：本阶段原写的两条依据都是错的。
>
> 1. **「漏填 actions 编译不过」今天就已成立**——`ConfigUpdate` 的 `val actions:
>    Set<ConfigUpdateAction>` **已经是抽象成员**（ReadBookContract.kt:1028），每个实现
>    必须 override。E1 号称新增的编译期保证，是既有的。
> 2. **「按写入目标分族 ⇒ 一族天然无 actions」不成立**——两条轴正交。实测交叉表：
>
>    | | 有渲染副作用 | 无渲染副作用 |
>    |---|---:|---:|
>    | 写 readConfig.json 排版 | 80 | 1（`StyleName`） |
>    | 写 DataStore | **25** | 51 |
>
>    那 25 个（`StyleSelect`/`ShareLayout`/`MenuBgColor`/`HideStatusBar`/`TextFullJustify`…）
>    写 DataStore 但**确实需要**命令式渲染副作用。按写入目标分族会把它们塞进一个
>    「没有 actions 概念」的族里，直接坏掉。
>
> **剩余收益不足以支撑改动**：只剩「少写 52 个 `emptySet()` 字面量 + 劈开大 `when`」，
> 而 157 个成员逐个归类的误判风险是实打实的（归错一个 = 悄悄丢掉它的渲染副作用）。
> 真正的失效模式（actions 填空/填错）已由 E0 的 `ConfigUpdateActionsInvariantTest` 覆盖。
>
> **若将来仍想收敛**：正确的切法不是分族，而是让 actions **可推导**——把
> `ConfigUpdateAction` 与「哪些渲染资源被这项设置影响」建立声明式映射，从设置本身推出
> 副作用集。那是独立议题，不在 Track E 范围内。

---

### E2 —— 排版快照改由 gateway 统一驱动 ✅ 已落地（2026-07-25，方案已调整）

#### 实际落地的方案（与原计划的差异，先说清楚）

原计划写的是「让 `ReadStyleState` 携带 60 字段的不可变 `ReadStyleSnapshot`，VM 从快照纯函数
派生」。**实际落地的是更小的方案**：加一个 `revision` 计数器让 flow 能发得出去，VM 收到通知后
仍用原来的 `buildStyleConfig()`/`buildSheetConfig()` 派生。

**为什么改**：60 字段的投影要把 `curTextColor()`/`curUnderlineColor()` 这类**模式相关的解析
逻辑**整体搬进 repository，是 ~200 行的机械映射 + 逐字段核对风险；而它对「弹层显示旧值」这个
实际症状的贡献，与 20 行的 revision 方案**完全相同**。按 CLAUDE.md 的 Simplicity First，
先取小的。

**关键前提（原计划和外部审计都没写出来的那一步）**：`ReadStyleState` 只投影
items/selectedIndex/shareLayout 三项，「只改了字号」不会让这三项变化 ——
而 `MutableStateFlow` 在新值与旧值**相等时不更新、不通知订阅方**。所以单纯让 VM
`collect(gateway.state)` 是**无效**的，绝大多数排版编辑根本发不出去。必须先加
`revision` 破解判等。这条已由 `ReadStyleStateRevisionTest` 固定。

#### 改动

- `ReadStyleState` 新增 `revision: Long`，`ReadBookStyleConfigRepository` 每次
  `buildState()` 用 `AtomicLong` 递增。
- `ReadBookViewModel.collectReadStyle()`：collect gateway 的 state，**统一重建两份快照**。
- `ReadStyleGateway` 新增 `notifyModeChanged()`：日夜/墨水屏切换时排版值没变、但解析后的
  生效值变了，经 gateway 重新发布，走同一条派生链。
- 删除 6 处已被 collector 覆盖的手工重建（`DeleteCurrentReadStyleConfig`、
  `ApplyPresetTheme`、背景图 ×2、导入配置、`ProgressBarBehavior`）。
- `handleConfigUpdate` 尾部：捕获 `styleMutation`，只在「没走 gateway」时手工重建，
  且**两份一起**重建。

#### 顺带修掉的三个同类别活 bug

E2 之前 `sheetConfig` 全项目**只有 1 处**重建（`syncFromReadBook`），于是：

1. 编辑排版（字号/行距/下划线/页眉页脚…）后重开弹层显示旧值 —— 主症状。
2. **日夜切换后** `sheetConfig` 里的 `textColor`/`titleColor`/`underlineColor`/
   `textShadowColor` 仍是白天色（它们都按当前模式解析）。
3. **`ChineseConverterType`** 本身就在 `sheetConfig` 里，却只重建 `styleConfig` → 一直显示旧值。
4. `EventBus.UP_CONFIG` 路径（主题变更改变解析后的颜色）同样只重建 `styleConfig`。

> 这三条都是 [reader-config-flow-audit] 那次排查的延伸。**注意**：该 memory 记的
> 「2026-07-24 已实施最小修复」**从未提交**——2026-07-21 到 2026-07-25 之间仓库无任何提交，
> 三个 bug（含两处 action 错配）到今天都还是活的，已在本轮一并修掉。

#### 验收：达成情况

| 原定判据 | 结果 |
|---|---|
| `handleConfigUpdate` 无手动 `styleConfig` 重建 | ✅ 改为仅在非 gateway 路径重建，且两份一起 |
| 手工重建站点收敛 | ✅ styleConfig 13 → 5（含 collector 自身）、sheetConfig 1 → 4 |
| 「新增写入路径忘了重建」失效类别消失 | ✅ 对**走 gateway 的排版写入**成立 |
| VM 中 `ReadBookConfig.` 直读 93 → ≤10 | ⚠️ **判据作废（2026-07-30 改判）**，见下 |
| `tryEmit` 归零 | ❌ 未做（改 effect 发射语义是独立风险，另行评估） |

**「VM 直读 ≤10」这条判据 2026-07-30 改判为作废，不再作为待办追**：复核发现 VM 现存的 109 处
`ReadBookConfig.` 几乎全部集中在 `buildStyleConfig()`/`buildSheetConfig()`
（`ReadBookViewModel.kt:1683-1796`）——**这正是「一处读全局、其余读快照」的正确形态**，把它压到 10
以下只能靠把同一批字段搬去别处再读一遍，是为了让计数变好看而制造间接层。同理
`ReadBookStyleConfigRepository`（108 处）与 `ReadStyleRepository`（17 处）属 repository 内部，合规。
**当时判定「未达成」是因为把 grep 计数当成了债的度量**——真靶子从来是渲染层，而它已由 R4.1 处理
（`PageView` / 四个绘制实体的直读归零）。

留下的真实缺口只有一条：`buildStyleConfig()`/`buildSheetConfig()` 仍在**派生时刻裸读可变全局**。
这不影响正确性（`publishState()` happens-before collector 读取，读到的必是新值），但意味着派生
不是纯函数、不可单测 → `ReaderConfigSnapshotInvariantTest` 只能继续做源码扫描。**R4 之后这条的
性价比进一步下降**：配置侧已经权威（值字段 `val`、写入经 gateway），派生读到的必是不可变实例。

真机验收（待用户执行）：编辑字号/行距/页眉页脚 → 关弹层重开值正确；切预设；导入配置；
**日夜切换后弹层颜色**；分享排版开关；简繁转换。

---

### E3 —— UI 层去全局直读 ✅ 已落地（R1.2 + R4.6）

> **落地方式与原计划的差异**：前三个文件按下面的原则改成受控（`637e62cb1` R1.2）。第四轮收尾时发现
> 剩下的 4 处（`tipNames`/`tipValues`/`getHeaderModes`/`getFooterModes`）**不是配置状态、是静态选项表**，
> 此前靠护栏白名单放行——`60029d657`（R4.6）把它们搬到唯一消费方 `HeaderFooterPage.kt` 底部，
> 白名单 `ALLOWED_MEMBERS` 整个删除。同时**堵上护栏的一个洞**：`import ...ReadBookConfig.tipNames`
> 之后成员可裸写，按 `ReadBookConfig\.` 找的正则一个都看不见 → 需单独抓 import。
> 顺带把 `remember { tipNames }` 换成 `stringArrayResource`，修掉语言切换后选项不刷新。

把 §1.4 的四个文件改成 `state + onIntent` 纯受控：

1. `HeaderFooterPage.kt`（69 直读 / 29 镜像）—— 最大，建议单独一轮。
2. `TextTitleSheet.kt`（18 镜像）
3. `SystemMenuPage.kt`（4 / 7）
4. `CustomTipTarget.kt`（13 直读）

镜像状态的处理原则：
- 纯展示值 → 直接读 `state.sheetConfig.x`，删掉 `remember`；
- 拖动中的滑块等确需本地暂存的 → 保留 `remember`，但**必须带 key**（`remember(config.x)`），
  上游变化能重新 seed。

**验收**：`grep -rn 'ReadBookConfig\.' app/src/main/java/io/legado/app/ui/book/read/sheet/` 归零；
弹层重开显示新值的真机用例通过。

---

### E4 —— 收敛 EventBus 整数码 ✅ 已落地（`3b38615e5`，即 R1.3）

`EventBus.UP_CONFIG` 的 `ArrayList<Int>` 载荷改为直接投递 `Set<ConfigUpdateAction>`
（或干脆让 8 个生产者改调 gateway/VM 的具名方法）。整数码 0–12 的翻译表随之删除。

优先级低于 E2/E3：它是**可读性**问题，不是正确性问题（翻译表本身没查出错配）。

**验收**：`grep -rn 'EventBus.UP_CONFIG'` 只剩事件定义；VM:2084 的整数 `when` 删除。
→ ✅ 达成，改用 typed `ReadConfigUpdateBus`（`ui/book/read/ReadConfigUpdateBus.kt`，`buffer=64`）。

---

### E5 —— 渲染引擎去全局读 ✅ 已落地（并入 R4.1，`8c51dfc6f`）

> **实际落地方案与下面的原计划不同，差异值得记下**：没有让引擎「吃传入的 `ReadStyleSnapshot`」
> （那要改一大批函数签名、且热路径多一层对象传参），而是**在引擎侧建两个绘制期快照**——
> `ChapterProvider.RenderStyle`（10 字段）+ `TipStyleProvider.TipStyle`（33 字段），由 `upRenderStyle()`/
> `upTipStyle()` 在配置变更时重建，绘制实体改读快照字段。
>
> **先量后动的收获**：原计划按 grep 计数列的靶子数被严重高估——`PageView` 70 处里 41 处是
> `tipChapterTitle` 这类**常量**（页眉页脚项的类型 ID，已搬去 `constant/ReadTipType.kt`）；
> `ChapterProvider` 61 处里 9 处在**注释掉的死代码**里（`067d229b0` 删了 671 行块注释），
> 剩下的全在 `upStyle`/`upThemeColors`/`getPaints` 里，**本来就是「一处读全局、发布快照」的正确形态**。
> 真靶子只有 `TextLine`/`TextColumn`/`TextHtmlColumn`/`TextPage` 在 `draw()` 里的逐个直读——
> 代价不只是分层：`ReadBookConfig.underline` 要走 `config → durConfig → getConfig(styleSelect)`，
> 而 **`getConfig` 是 `@Synchronized`**，等于每行每列每帧抢一次全局配置的监视器锁。
>
> **顺带修掉的两个真实问题**：`loadTypeface(headerFont)` 是主线程按路径读字体文件、`PageView` 三实例
> 一次样式变更读三次（现按 `cachedFontPath` 缓存）；`ChapterProvider.linePaint` 的 `by lazy` 首次取用
> 即定型，改下划线粗细/字体不反映到朗读高亮线，而 `upThemeColors()` 会就地改它的 color →
> 「颜色跟得上、其余永远是首帧那份」（`067d229b0` 改为由 `upStyle()` 重建）。
>
> **遗留**：绘制热路径仍经另一个可变全局门面 `ReadConfig` 读 `useUnderline`/`optimizeRender`/`isEInkMode`，
> 且现有护栏对它不设防。见主计划「待续 ③」。

`ChapterProvider`(62)/`PageView`(54)/`TextChapterLayout`(11)/`TextLine`(12) 改为吃传入的
`ReadStyleSnapshot`（E2 已经造好），而非排版时裸读全局。

**明确标为可选**：
- 收益是"排版可测试 + 渲染输入显式"，不是修 bug；
- 成本高、触及热路径（`ChapterProvider` 在排版每一行时读配置，改成对象传参需注意分配开销）；
- 与 Track D2（`ReadView` 入站只读输入）目标重合，**应合并评估，不要各做一遍**。

建议：E2 落地后**先停**，等 Track D1 完成、D2 立项时把 E5 并入 D2 一起做。

---

## 4. 推荐顺序与依赖

```
E0 ──►  ✗E1  ──► E2 ──┬──► E3 ✅
 ✅已落地  已撤销  ✅已落地  └──► E4 ✅

                       E5 ✅ ← 并入 R4.1（不单独做）
```

- **E0 → E2 是主线**（E1 已撤销，见上），两步做完，「设置乱」的结构性成因基本消除。
- E3/E4 是主线之后的清理，可并行、可分批。
- E5 不在本轨道单独推进。

> **全部阶段已于 2026-07-30 走完**（E1 撤销、E5 并入 R4.1）。本节保留为历史执行顺序记录。

**单步价值排序（若只能做一步）**：做 **E2**。它单独就能消灭"弹层显示旧值"整个 bug 类别；
E0 是为了让 E2 之后不再退化。

---

## 5. 验收总表

| 阶段 | 可机器验证的判据 | 结果（2026-07-30） |
|---|---|---|
| E0 | 3 个测试类 / 8 条断言进 CI，四条变异实测可红 | ✅ 已落地 |
| ~~E1~~ | —— | ❌ 已撤销：前提不成立（actions 本就抽象；两条轴正交） |
| E2 | 手工重建站点收敛、失效类别消失 | ✅ 达成（「直读未降」一条已于 2026-07-30 改判作废，见 E2 节） |
| E3 | `sheet/` 下 `ReadBookConfig.` 直读 == 0 | ✅ **达成**（R1.2 去镜像状态 + R4.6 把最后 4 处静态选项表搬到 `HeaderFooterPage.kt`，护栏 `ALLOWED_MEMBERS` 白名单整个删除） |
| E4 | `EventBus.UP_CONFIG` 生产者 == 0 | ✅ **达成**（R1.3 整数码退役，改 typed `ReadConfigUpdateBus`） |
| E5 | （并入 D2） | ✅ **达成**（并入 R4.1：`ChapterProvider.RenderStyle` + `TipStyleProvider.TipStyle` 两个绘制期快照，`PageView` 与四个绘制实体的 `ReadBookConfig` 引用归零） |

**Track E 到此结束。** 追加的 R4.1–R4.7（配置底座所有权反转）已在
[mad-modernization-plan.md](mad-modernization-plan.md) 的 Track E 节说明由来：E5 要让渲染引擎吃只读快照时，
暴露出「`ReadBookConfig` 既是状态本体又是写入口、`getConfig` 还是 `@Synchronized`」的更深问题。

> **护栏的已知洞（2026-07-30 审查）**：E3 的 `sheet/` 护栏堵法是完整的（成员 import / alias / 通配全抓），
> 但**同款堵法没有回移植**到 `verifyConfigArchitecture` 的写入检查和 `ChapterProviderMetricsInvariantTest`，
> 那两处仍可被成员 import 绕过。另外 R4.1 的渲染层护栏只匹配 `ReadBookConfig`，对 `ReadConfig`
> （另一个可变全局门面，绘制热路径仍在读）完全不设防。详见主计划「待续 ③」。

**真机 parity 用例**（每阶段跑，用 `tools/android` 调试工具）：
编辑字号/行距/页眉页脚 → 关弹层重开值正确；切预设；导入配置；日夜切换；分享排版开关；
以上每项后正文立即重排且不闪白。

---

## 6. 与其他轨道的边界

| | Track D | Track E |
|---|---|---|
| 方向 | `ReadView` **出站**业务意图 | 设置 **入站**数据流 |
| 触碰 | `ReadView.kt` 的 `ReadBook.*`/`ReadAloud.*` 直调 | `ReadBookConfig` / `ConfigUpdate` / sheet UI |
| 依赖 | Track A（ReaderSession） | 无 |
| 交汇 | D2（入站只读输入） == E5（引擎去全局读）→ **合并做一次** |

---

*创建于 2026-07-25，同日完成 E0。基线数据（行号/计数）截至同日 HEAD。*
*2026-07-30 更新：E2–E5 达成情况、E2 一条判据改判作废、护栏已知洞。全轨道结束。*
