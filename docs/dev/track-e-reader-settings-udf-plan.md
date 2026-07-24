# Track E —— 阅读正文界面「设置 + UDF」现代化改造计划

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
   > 直接封死 `UnderlineColor` 那类错配的复发。白名单是**临时**的，E1 把这些成员迁到
   > `ReadPreferenceUpdate` 后整体删除。
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

### E1 —— 按通道拆分 `ConfigUpdate`（结构性除错）

把 157 个成员按**写入目标**拆成两族：

```kotlin
// 写 readConfig.json 排版 → 必须驱动渲染副作用
sealed interface ReadStyleUpdate : ConfigUpdate {
    val actions: Set<ConfigUpdateAction>   // 抽象成员，编译器强制每个实现填
}

// 写 DataStore → 由 readPreferences StateFlow 反应式生效，天然无 actions
sealed interface ReadPreferenceUpdate : ConfigUpdate
```

- 57+ 个 `actions = emptySet()` 的菜单外观成员迁到 `ReadPreferenceUpdate`，**从类型上不再有
  actions 字段**——不是"填了空集"，是"没这个概念"。
- 剩余排版成员的 `actions` 变成抽象成员的实现，漏填 = 编译不过（比 E0 的测试更强）。

**风险**：`handleConfigUpdate` 的巨型 `when` 需相应分成两个。改动面大但**机械**、无逻辑判断。
建议分 3–4 个 commit（按 §1.3 的注释分组：文本/标题/页眉页脚/菜单）。

**验收**：`grep 'actions = emptySet()'` 归零；`handleConfigUpdate` 拆成
`handleStyleUpdate` + `handlePreferenceUpdate`，各自 ≤ 原来一半。

---

### E2 —— 给排版底座补 flow（**本轨道的核心一步**）

让 `ReadStyleGateway.state` 携带**完整排版快照**，而不是只有 items/selectedIndex/shareLayout：

```kotlin
// domain/model/settings/ReadStyleState.kt
data class ReadStyleState(
    val items: List<ReadStyleItem> = emptyList(),
    val selectedIndex: Int = 0,
    val shareLayout: Boolean = false,
    val current: ReadStyleSnapshot = ReadStyleSnapshot(),   // 新增：不可变全量排版快照
)
```

`ReadStyleSnapshot` 是纯值对象（字号/行距/间距/标题各项/页眉页脚/下划线/阴影/内边距/背景…），
由 `ReadBookStyleConfigRepository.buildState()` 从 `ReadBookConfig.durConfig`/`shareConfig` 投影。
**`publishState()` 已经在每个 mutation 后被调用**（updateCurrentStyle/applyPreset/import/delete/save
全都调），所以这一步**不需要新增触发点**——只需把 `buildState()` 多填一个字段。

然后 VM 侧：

```kotlin
init {
    viewModelScope.launch {
        readStyleGateway.state.collect { style ->
            _uiState.update {
                it.copy(
                    styleConfig = style.toStyleConfig(readSettings),
                    sheetConfig = style.toSheetConfig(readSettings),
                )
            }
        }
    }
}
```

`buildStyleConfig()` / `buildSheetConfig()` 从「裸读可变全局」改为「从不可变快照派生」，
`handleConfigUpdate` 尾部那两行手动 `_uiState.update { copy(styleConfig = …) }` **整段删除**。

> 这一步之后，「忘记重建 sheetConfig」这个 bug 类别**不再存在**——写入必经 gateway，
> gateway 必发 state，VM 必然收到。

**同时修**：`_effects.tryEmit` → `emitEffectWhenSubscribed`（[reader-config-flow-audit] 已列，
tryEmit 在无订阅者时会丢渲染 effect）。

**验收**：
- `ReadBookViewModel` 中 `ReadBookConfig.` 的直读从 93 处降到 ≤10（只剩 A 类只读转发）；
- 真机 parity：编辑排版 → 关弹层 → 重开，值正确（覆盖 2026-07-24 修复的 5 个站点 + 预设/导入/日夜切换）；
- E0 的快照完备性测试仍绿。

---

### E3 —— UI 层去全局直读

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

### E4 —— 收敛 EventBus 整数码（可与 E3 并行）

`EventBus.UP_CONFIG` 的 `ArrayList<Int>` 载荷改为直接投递 `Set<ConfigUpdateAction>`
（或干脆让 8 个生产者改调 gateway/VM 的具名方法）。整数码 0–12 的翻译表随之删除。

优先级低于 E1–E3：它是**可读性**问题，不是正确性问题（翻译表本身没查出错配）。

**验收**：`grep -rn 'EventBus.UP_CONFIG'` 只剩事件定义；VM:2084 的整数 `when` 删除。

---

### E5 —— 渲染引擎去全局读（**可后置 / 与 Track D2 同源**）

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
E0 ──► E1 ──► E2 ──┬──► E3
 ✅已落地 (类型)  (核心)  └──► E4

                    E5 ← 并入 Track D2 评估（不单独做）
```

- **E0 → E1 → E2 是主线**，三步做完，「设置乱」的结构性成因基本消除。
- E3/E4 是主线之后的清理，可并行、可分批。
- E5 不在本轨道单独推进。

**单步价值排序（若只能做一步）**：做 **E2**。它单独就能消灭"弹层显示旧值"整个 bug 类别；
E0/E1 是为了让 E2 之后不再退化。

---

## 5. 验收总表

| 阶段 | 可机器验证的判据 |
|---|---|
| E0 | ✅ 3 个测试类 / 8 条断言进 CI，四条变异实测可红 |
| E1 | `grep 'actions = emptySet()'` == 0；漏填 actions 编译不过 |
| E2 | VM 中 `ReadBookConfig.` 直读 93 → ≤10；`handleConfigUpdate` 无手动 `styleConfig` 重建；`tryEmit` 归零 |
| E3 | `sheet/` 下 `ReadBookConfig.` 直读 == 0 |
| E4 | `EventBus.UP_CONFIG` 生产者 == 0 |
| E5 | （并入 D2） |

**真机 parity 用例**（每阶段跑，用 `tools/android` 调试工具）：
编辑字号/行距/页眉页脚 → 关弹层重开值正确；切预设；导入配置；日夜切换；分享排版开关；
以上每项后正文立即重排且不闪白。

---

## 6. 与其他轨道的边界

| | Track D | Track E |
|---|---|---|
| 方向 | `ReadView` **出站**业务意图 | 设置 **入站**数据流 |
| 触碰 | `ReadView.kt` 的 `ReadBook.*`/`ReadAloud.*` 直调 | `ReadBookConfig` / `ConfigUpdate` / sheet UI |
| 依赖 | Track A（ReaderSession） | 无（可立即开始 E0/E1） |
| 交汇 | D2（入站只读输入） == E5（引擎去全局读）→ **合并做一次** |

---

*创建于 2026-07-25，同日完成 E0。基线数据（行号/计数）截至同日 HEAD。*
