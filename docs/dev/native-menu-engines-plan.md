# 四主题库菜单统一入口：处理方案

> 状态：**三套原生分支（Mochi / COUI / Miuix）已落地，COUI backdrop 覆盖页面根与独立窗口 Sheet；
> `TopBarOverflowMenu` 的弹层已收编到 `AppAnchoredPanel`，三入口均有 JVM 契约基线；
> `compact` 定稿为「入口包装 trigger + Miuix 原生纯弹层」，阶段 C 已迁移 8 处纯单选
> （阅读器 Sheet 4 处 + 字体/书源/RSS 编辑 4 处）、2 处编辑页顶栏命令菜单、
> 5 处带分段线的两段式命令菜单（A 类）、9 处顶栏 `dropDownMenuContent` 批量迁移（A' 类）、
> `TocScreen` 1 处（注册表 + 纯映射 + 先建基线）、规则编辑剪贴板菜单 3 处、
> Sheet action 槽命令菜单 4 处、「命令 + 独立开关」菜单 3 处、
> 知识 / 角色详情纯单选 3 处、翻页动画 / 九宫格点击区域 compact 单选 2 处、
> 导入面板「配置页 / 列表页」切换 2 处、替换规则顶栏 + 直链上传规则菜单 2 处、
> CloudTts 引擎页顶栏命令菜单 1 处、RSS 排序页主菜单 2 处、详情页顶栏「更多」1 处，
> 累计 50 处（第三十轮口径；第三十一～五十一轮另见 §9，业务调用点已由 20 降至 0）。
> 2026-11-11 用户确认「分段线可以不保留文字」，四引擎分段线语义已统一。
> **截至 2026-11-13 第五十一轮，阶段 C 收尾：源码实测 `RoundDropdownMenu(` 4 处 =
> 组件定义 1 + 三入口内部 3，业务调用点 0 处**（第四十五～五十一轮只做回归修复、
> 设置项组件配色统一、编辑页布局调整、撤销库侧兜底、COUI 主题设置对齐与文档清理，
> 计数不变）。
> 2026-11-13 用户真机复核：受控玻璃锚点回归（弹层落点 / 关闭崩溃）已通过；
> 同日双变体 lint 回到 0 error（第五十轮修 COUI 胶囊 `NewApi`）。
> 业务侧迁移已完成，剩余是迁移后四引擎外观 / TalkBack / lint 复核与 §9 下一阶段的其余项。
> 历史计数（55 / 51 / 38 / 36 / 34 / 33 / 31 / 28 / 27 / 26 处）见 §9 各轮记录，
> 以本节当前值 + 源码为准。
> 用户另实测发现部分按钮（搜索按钮、主题风格设置项）与同页其它按钮样式不一致；
> **主题风格设置项已于第五十一轮统一到 `DropdownListSettingItem`**（与导航动画同组件）；
> **主题模式列表首项已改回「动态取色」并恢复系统动态取色**（均见 §9）；
> 搜索按钮仍待真机复核。
> **第四轮（用户侧）已交付 `0.2.1-menu3`**：Mochi 走 anchor-only「点击松手才弹」，
> 大圆库按钮消失，顶栏 MoreVert 穿上项目 `MochiBarIconButton`，真机效果已调整完成；
> 详见 §9 实施记录与本文下一阶段第 1 条（已归档）。
> 扩展规格见 `docs/dev/mochi-menu-extension-spec.md`，实际实施边界与验证结果见本文 §9。
> 跨会话交接快照见 `docs/dev/native-menu-engines-handoff.md`（含「有分割线的菜单」专题结论）。
> 目标是把 `RoundDropdownMenu` 的分离式槽契约拆成三个声明式入口，
> 完整保留 COUI / Miuix / Mochi / M3 各自的原生菜单效果。
>
> 依据：本仓库锁定版本的**库源码实测**（COUI `0.0.1-alpha9`、Mochi `0.2.0` 的 `-sources.jar`，
> Miuix vendored 于 `modules/miuix/`），以及 app 侧 86 处 `RoundDropdownMenu` / 320+ 处
> `RoundDropdownMenuItem` 调用点盘点。

## 0. 结论先行

报告的判断成立：`RoundDropdownMenu` 把触发器、弹层、菜单项拆成独立槽，四套库拿不到
「触发器 + 整组条目」这条完整信息链，只能用 `glassAnchor` / `mochiAnchorMaxWidth` /
`LocalGlassPopupContent` 把信息补丁式传回主题分支。**拆成三个声明式入口是正确方向**。

但有三处必须修正/补充后才能落地：

1. 报告中 `ProvideCouiMenuBackdrop`、`CouiMenuAnchor`、`MochiDropdown`、`MochiIconDropdown`、
   `MochiItem` **都真实存在于当前依赖版本**。此前「仓库内 0 命中」只是 app 源码从未引用过
   二进制依赖里的类，不代表库没有。方案可以直接用这些 API，无需升版本。
2. COUI 的 backdrop 是**窗口/页面级**能力（`ProvideCouiMenuBackdrop { modifier -> ... }` 要挂在
   内容根节点），不是菜单组件内部能补的；第一轮必须先铺宿主。
3. 「整份菜单交给主题分支」隐含一个硬约束：**触发器必须由新组件自己渲染**，
   `expanded` 不能再由业务层持有并驱动。四套原生菜单的触点、变形、按住滑选都依赖这一点。
   只换菜单项数据模型而不改触发器归属，拿不到任何新效果。

不建议一次性替换 86 处调用点：按垂直切片先做两个场景，旧组件保留为兼容底座。

---

## 1. 当前版本真实 API（实测）

### COUI 0.0.1-alpha9 — `com/itos/coui/menu/`

```kotlin
@Composable fun <T> Menu(
    modifier, expanded, onDismissRequest,
    items: List<CouiMenuItem<T>>, onItemClick: (CouiMenuItem<T>) -> Unit,
    style = CouiMenuDefaults.style(), offset, properties,
    anchor: CouiMenuAnchor? = null,          // 触点定位
    headerText: String? = null, headerTextAlign, onSpotlightDiagnostic,
)

data class CouiMenuItem<T>(
    val value: T, val text: String, val icon: ImageVector? = null,
    val selected: Boolean = false, val enabled: Boolean = true,
    val checked: Boolean? = null,            // 独立开关勾选，与 selected 分离
    val dividerBefore: Boolean = false, val iconTint: Color? = null,
    val children: List<CouiMenuItem<T>> = emptyList(),   // 两级（`require` 只支持两级）
)

@Composable fun rememberCouiMenuAnchor(): CouiMenuAnchor
fun Modifier.couiMenuAnchor(anchor: CouiMenuAnchor): Modifier   // 挂在触发器上，只观察触点
fun CouiMenuAnchor.open(onOpen: () -> Unit)                     // 从原 onClick 调用
@Composable fun ProvideCouiMenuBackdrop(content: @Composable (Modifier) -> Unit)
```

- 比报告描述更强：原生支持 `checked`、`dividerBefore`、两级 `children`，所以 `AppActionMenu`
  的分组/勾选/子菜单在 COUI 分支无需自绘。
- 硬门控：现有 `CouiSettingMenu.kt:19-38` 的 `canUseCouiSettingMenu()` 要求 Android 14+
  且 `CouiShadowRenderer()` 可构造，否则要降级到 M3 渲染，不能静默空白。

### Mochi 0.2.0 — `ui/feice/mochi/basic/`

```kotlin
@Immutable class MochiItem(
    val label: String, val destructive: Boolean = false,
    val icon: (@Composable (tint: Color) -> Unit)? = null,
)                                            // 无 enabled / 无 checked / 无 children

@Composable fun MochiDropdown(
    expanded, onExpandedChange, items: ImmutableList<MochiItem>,
    onItemClick: (Int) -> Unit, modifier, enabled,
    selectedIndex: Int = -1, colors, behavior, physics, motion,
    menuWidth, buttonPadding, buttonMinWidth, buttonMinHeight,
    onClick: (() -> Unit)? = null,
    buttonContent: @Composable RowScope.() -> Unit,   // 库自己渲染/接管按钮
)
@Composable fun MochiIconDropdown(..., icon: @Composable () -> Unit)  // 圆形图标版
@Composable fun MochiPopoverButton(
    expanded, onExpandedChange, popover: @Composable ColumnScope.() -> Unit,
    ..., buttonContent: @Composable RowScope.() -> Unit,
)
@Composable fun MochiPopoverBox(..., anchor: @Composable () -> Unit)  // 分离式锚点

data class MochiDropdownBehavior(
    openMode: MochiOpenMode = Detach, trigger: MochiTrigger = Press,  // Press | LongPress
    pressStyle, itemLayout, hapticProfile, align, dismissDelayMillis,
)
```

- `MochiItem` 的能力与报告一致：只有标题、图标、危险色；选中靠 `selectedIndex`。
- **自定义面板应优先用 `MochiPopoverButton`**（`DropdownCore` 的 `content != null` 分支，
  按钮与面板共享形变），报告建议的 `MochiPopoverBox` 正是当前 `AppMochiPopover` 用的分离式
  容器，会丢掉「按钮变成面板」的开合形变。
- `trigger = Press` 意味着按下即开、按住滑到选项松手提交，与现有「点击按钮展开」语义不同。

### Miuix 0.9.4-SNAPSHOT（vendored 源码）

```kotlin
data class DropdownItem(
    text, enabled = true, selected = false, onClick, icon, summary,
    children: List<DropdownItem>? = null,
)
data class DropdownEntry(val items: List<DropdownItem>, val enabled: Boolean = true)
```

| 触发器形态 | 常规 | HyperOS 4 玻璃 |
|---|---|---|
| 设置行（单选） | `WindowDropdownPreference` / `OverlayDropdownPreference` | `GlassDropdownPreference` |
| 图标（命令） | `WindowIconDropdownMenu` / `OverlayIconDropdownMenu` | `GlassIconDropdownMenu`（`BoxScope` 扩展，需 `GlassPopupAnchor` + `anchorContent` + `backdrop`） |
| 带子菜单 | `WindowIconCascadingDropdownMenu`（深度上限 2） | `GlassIconDropdownMenu`（一层 `children`，自带返回头） |

- 注意：`*DropdownPreference` 的渲染链 `DropdownEntriesContent.kt` **忽略 `children`**，
  子菜单只能走 IconCascading / Glass 系列。
- `GlassIconDropdownMenu` 是 `BoxScope` 扩展，面板要与 `GlassIconButton` 锚点同处一个
  root `BoxScope`；app 侧对应 `AppGlassOverlayState.register` 的内容层。`glassPopupAnchor`
  必须挂按钮最外层节点（库靠 `modifier.foldIn` 取回按钮材质），挂图标会重复上报
  `containerBounds`。§9 记录了 `AppActionMenu` 的实际接入方式。

### M3

当前 M3 分支用的是 `DropdownMenu` + **自绘 `Surface` 行**（`RoundDropdownMenuItem.kt:203-271`），
并未使用 `DropdownMenuItem`。改用 `DropdownMenuItem` 会改变行高/间距/水波纹，属于可察觉的外观变化。

---

## 2. 三条不可回避的设计约束

1. **触发器归属**：COUI 触点（`couiMenuAnchor`）、Miuix 玻璃变形（`glassPopupAnchor`）、
   Mochi 按住滑选（`buttonContent`）都要求触发器与条目在同一组件。新入口必须自己渲染触发器，
   把 `expanded` 收为内部状态（对外只留 `enabled` 与可选 `onExpandedChange` 观察）。
2. **宿主能力前置**：COUI `ProvideCouiMenuBackdrop` 挂页面/Sheet 内容根；Miuix 玻璃依赖
   `LocalAppGlassOverlay` + `LocalMiuixBarBackdrop`；Mochi 依赖 `LocalMochiBackdrop`。
   这些不是菜单组件内部能补的。
3. **能力不对等要显式建模**：四库效果不同，统一契约只声明「最小公共语义 + 可选能力」，
   缺口必须走声明式降级（capability/unsupported），不能静默丢条目或伪造勾选态。

---

## 3. 三入口契约（建议签名）

```kotlin
@Stable data class AppChoiceOption(
    val id: String, val label: String, val enabled: Boolean = true,
    val icon: (@Composable () -> Unit)? = null,
)

@Stable data class AppAction(
    val id: String, val label: String,
    val enabled: Boolean = true,
    val checked: Boolean? = null,          // null = 普通命令；非 null = 可勾选开关
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val dividerBefore: Boolean = false,
    val icon: (@Composable () -> Unit)? = null,
    val children: ImmutableList<AppAction> = persistentListOf(),   // 上限两级，由契约断言
)

@Stable data class AppActionGroup(
    val id: String, val title: String? = null,
    val actions: ImmutableList<AppAction>,
)

enum class AppMenuAnchor { Row, Icon }   // 决定 Miuix 用 Preference 还是 IconDropdown

/** 主题分支把各自的锚点 Modifier 与打开动作注入触发器。 */
@Stable interface AppMenuTriggerScope {
    val modifier: Modifier
    fun open()
}

@Composable fun AppChoiceDropdown(
    options: ImmutableList<AppChoiceOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,             // 稳定 ID，不是索引
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    anchor: AppMenuAnchor = AppMenuAnchor.Row,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
)

@Composable fun AppActionMenu(
    groups: ImmutableList<AppActionGroup>,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
)

@Composable fun AppAnchoredPanel(
    expanded: Boolean,                      // 搜索框/长列表仍需外部控制开合
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    trigger: @Composable AppMenuTriggerScope.() -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
)
```

要点：
- `id: String` 是唯一跨库契约；Mochi / Miuix 按索引回调，由分支内部做 `id ↔ index` 转换，
  COUI 直接把 `id` 放进 `CouiMenuItem<T>.value`，M3 用 `when(id)`。
- `AppMenuTriggerScope.modifier` 是四种锚点的统一注入点：COUI = `couiMenuAnchor`、
  Miuix 玻璃 = `glassPopupAnchor`、Mochi = 空（按钮由库渲染）、M3 = 空。
  `open()` 在 COUI 分支内部走 `anchor.open { expanded = true }` 才能保留触点。
- `AppAnchoredPanel` 不自动 dismiss，沿用旧契约（`dismiss()` = `onExpandedChange(false)`）。
- 关键提醒：**混有 `PillHeaderDivider` / `PillDivider` / 伪子菜单的混合菜单若一律塞进
  `AppAnchoredPanel`，这些场景仍然拿不到原生效果**。三入口做完后要按第 6 节阶段 C 把它们
  逐步改造成 `AppActionMenu` 的 `AppActionGroup` + `children`，否则「完整保留四库效果」只覆盖
  一小部分页面。
- **2026-11-11 落地状态**：产品确认「分段线可以不保留文字」，`AppMenuModels.dividerBeforeAt`
  统一四引擎分段线判定（标题信号只作纯线），A 类 5 处 `PillDivider()` 菜单已按
  `AppActionGroup` 迁移；伪子菜单 / 复杂面板仍留在 `AppAnchoredPanel`。详见
  `docs/dev/native-menu-engines-handoff.md` §3.0 与本文 §9。
- **2026-11-11 第二轮落地**：11 处顶栏 `dropDownMenuContent` 里 9 处已按 `AppActionGroup`
  迁移，只剩 `RssSourceScreen`（伪子菜单 + 嵌套 popup，刻意保留）与 `TocScreen`
  （按 `currentPage` 整体切换菜单，待建基线）。复核还纠正了一处分类：带文字标题的
  4 处 `PillHeaderDivider` 在**列表项 / `bottomContent` 上独立的 `RoundDropdownMenu`** 里，
  trigger 是长按或图标按钮，**不是顶栏菜单，不能复用 `menuGroups` 槽**；
  Sheet / Dialog 正文里的分组标题则根本不是下拉菜单。详见交接快照 §3.4 B'。
- **2026-11-11 第三轮落地 + 外观风险实测**：`TocScreen` 按「动作注册表 + 纯
  ID → 行为映射 + 先建基线再迁移」完成，顶栏 content 仅剩 `RssSourceScreen` 刻意保留。
  用户随后观察到按钮样式不一致（搜索按钮 / 主题风格设置项）：**不是 Mochi 引擎特有**，
  而是 `trigger` 作为纯展示内容被四套主题库各自包装成按钮，不复用项目
  `TopBarButton` / `SettingItem` 的引擎适配与行样式；Mochi 因库按钮形变最显眼。
  这是文档里「顶栏 / 设置行外观真机基线」风险的首次真实显现，详见交接快照 §10、
  本文 §9 下一阶段第 1 条。

---

## 4. 入口 × 库映射与能力缺口

| | COUI | Miuix | Mochi | M3 |
|---|---|---|---|---|
| `AppChoiceDropdown` Row | `Menu<CouiMenuItem<String>>` + `CouiMenuAnchor` + backdrop | `Window/OverlayDropdownPreference`；HyperOS4 `GlassDropdownPreference` | `MochiDropdown`，`selectedIndex` 映射 | `DropdownMenu` + 菜单行 |
| `AppChoiceDropdown` Icon | 同上 | `Window/OverlayIconDropdownMenu`；HyperOS4 `GlassIconDropdownMenu` | `MochiIconDropdown` | `DropdownMenu` |
| `AppActionMenu` | `CouiMenuItem` 直接映射 `checked`/`dividerBefore`/`children` | `DropdownEntry` 分组；有 children 走 `WindowIconCascadingDropdownMenu`；玻璃走 `GlassIconDropdownMenu(entries)` | `MochiIconDropdown` + `MochiItem` | `DropdownMenu` + 自绘分组/分隔/勾选行 |
| `AppAnchoredPanel` | 无任意内容原生菜单 → M3 `DropdownMenu` + `CouiMenuDefaults` 形状/配色 + backdrop（**声明式降级**） | `WindowListPopup`+`ListPopupColumn`；HyperOS4 `GlassDropdownPopup`（现 `glassAnchor` 路径） | `MochiPopoverButton`（trigger 版）/ `MochiPopoverBox`（兼容重载） | `DropdownMenu` |
| 拿到的原生效果 | 触点定位、按压预选、材质背景、两级子菜单动画 | spinner 动画、玻璃锚点变形、级联子菜单 | 融球/弹性开合、按住滑选、按钮变面板 | 标准 M3 弹层 |
| 明确缺口 | Android < 14 或 `CouiShadowRenderer` 不可用 → 降级 M3；三级子菜单不支持 | Preference 渲染链忽略 `children`；逐项禁用在 spinner 上靠 alpha+吞选择 | `MochiItem` 无 `enabled`/`checked`/分组/子菜单（见决策 2） | 无滑选、无锚点变形 |

---

## 5. 第一轮必须一起做的宿主改动

- 在 `AppTheme` / `AppScaffold` 与 `AppModalBottomSheet` 的 COUI 分支各包一层
  `ProvideCouiMenuBackdrop { modifier -> ... }`，把 `modifier` 挂到内容根节点（页面与 Sheet
  各自一份）。用单参数版（默认 `isolateContent = true`）；`false` 版对透明/缩放/分数位移有
  栅格化风险，库源码已注明。
- `AppActionMenu` 的 Miuix 玻璃分支复用 `TopBarOverflowMenu.kt:33-58` 的
  `rememberGlassPopupAnchor()` 契约，借此把 `GlassMenuAnchor` 从公共 API 移除。
- **保留 `LocalUseWindowPopupForMochiMenu`**：`ThemeConfigScreen.kt:293-295` 用它强制菜单走独立
  窗口，避免切换根主题时销毁 DecorView 子视图。换成库托管的 popup 后必须验证这条路径，
  必要时在新入口里保留「强制独立窗口 popup」逃生分支。
- `RoundDropdownMenu` / `RoundDropdownMenuLazy` / `RoundDropdownMenuItem` 保留并标
  `@Deprecated`，作为渐进迁移底座。`RoundDropdownMenuLazy` 生产代码零调用，但
  `RoundDropdownMenuLazyTest.kt` 约束了 `LazyMenuViewport` 的 intrinsic 测量契约，不要在第一轮删。

---

## 6. 分阶段落地

| 阶段 | 内容 | 可独立验证的方式 |
|---|---|---|
| A 能力铺底 | 宿主 backdrop 注入；三入口契约模型 + 分派骨架（先只实现 M3 分支，其余委托旧组件） | 契约单测：`id → 回调`、禁用不触发、关闭所有权 |
| B 第一轮验证 | 四套分支各实现一遍；接入两个场景 | 见下 |
| C 分簇迁移 | 纯单选约 25 处 → 纯命令约 30 处 → 混合/自定义约 30 处；每簇一个 PR | 每簇回归测试 |
| D 清理 | 移除 `glassAnchor` / `mochiAnchorMaxWidth` / `LocalGlassPopupContent` / `LocalUseWindowPopupForMochiMenu`，删旧组件 | 全量测试 + 真机 |

### 第一轮两个场景

1. **主题风格选择** — `ui/config/themeConfig/ThemeConfigScreen.kt:285-306`
   改为 `AppChoiceDropdown`。必须保留 `unavailableValues` / `onUnavailableSelected` 语义：
   COUI 用 `enabled`、Miuix 用 alpha + 吞选择并给提示、Mochi 过滤或提示、M3 压灰仍可点并说明原因。
   同时验证切换根主题时 Mochi 的 popup 销毁路径不崩。
2. **书架更多** — `ui/main/bookshelf/BookshelfScreen.kt:695-793`
   12 个命令改为 `AppActionMenu` 的稳定 ID 列表；`TopBarOverflowMenu` 变薄壳，玻璃锚点由
   Miuix 分支接管。COUI 同时验证 `ProvideCouiMenuBackdrop` 与 `CouiMenuAnchor` 的触点定位。

`AppAnchoredPanel` 第一轮只做「旧槽归位」：把 `TopBarOverflowMenu.kt:49` 的委托路径收编，
不改行为、不改测试。

---

## 7. 验证矩阵

- 现有回归：`MochiOverlaysAndBarsTest.kt`（玻璃宿主、单选禁用、dismiss、长列表、dialog 内下拉）、
  `CouiFloatingAndMenusTest.kt`、`RoundDropdownMenuLazyTest.kt`。
- 新增契约测试：每个入口 × 4 引擎，断言 ID 回调、禁用项、checked/selected、关闭所有权、
  语义树（`selected` / `disabled` / 可本地化名称）。
- 编译与门禁：
  `.\gradlew.bat :app:compileAppCoreDebugKotlin :app:compileAppComicDebugKotlin`
  `.\gradlew.bat testAppCoreDebugUnitTest testAppComicDebugUnitTest lintAppCoreDebug lintAppComicDebug verifyConfigArchitecture assembleAppCoreDebug assembleAppComicDebug --continue --no-configuration-cache`
  以及所有文本改动的 `git diff --check`。
- **JVM 测试覆盖不了、必须真机**：COUI 触点定位与材质（Android 14+）、Miuix 玻璃锚点变形
  （API 33+）、Mochi 按住滑选/融球（API 33+ 才有 AGSL）、TalkBack、切换根主题时的 popup 路径。
- **真机外观基线**：2026-11-11 已取得第一批（书源管理顶栏 MoreVert × 四引擎，4 张截图），
  条目齐全 / 分组位置 / 无标题文字三项通过，记为 1/4 页面；细节与 3 个待确认点见
  handoff §10「已核对的真机外观」。仍缺书架、阅读器设置、书源 / RSS 编辑、CloudTts 引擎页。

---

## 8. 已定决策与剩余待确认项

已定：

1. **Mochi 用 `trigger = Press`** —— 按下即开 + 按住滑选，接受交互手感变化。
2. **能力缺口走 fork 扩展**，不在应用层用自绘降级；扩展清单见
   `docs/dev/mochi-menu-extension-spec.md`。
3. **`AppAnchoredPanel` 改为组件渲染触发器**，Mochi 分支用 `MochiPopoverButton`；
   现有分离式槽保留为兼容重载，不作主入口。
4. **第一轮范围**：骨架 + 两个验证场景，不全量迁移 86 处调用点。

已确认（2026-11-11）：

- fork 发布 `0.2.1-menu1` 到 `mavenLocal`，`settings.gradle` 增加 `mavenLocal()`，
  `app/mochi-compat.gradle` 的两处文件名常量改为前缀匹配（走扩展规格 §9 的方案 A）。
- **P0-0 阻塞项由库侧解决**：fork 提供 `commitAfterDismiss`，主题风格选择以
  `commitAfterDismiss = true` 接入，等浮层从 DecorView 摘除后再提交根主题切换；
  原先的 `CompositionLocalProvider(LocalUseWindowPopupForMochiMenu provides true)` 已移除。
- `trigger = Press`、`MochiItem` 的逐项禁用/勾选/分组/分隔线/一层子菜单、
  `LocalMochiDismiss` 均已由 fork 实现。

第一轮实施记录见 §9；阶段 C/D 仍按调用点分簇推进。

## 9. 第一轮实施记录

### 已落地

- 新增 `ui/widget/components/menu/`：`AppMenuModels.kt`（稳定 ID 契约、`AppMenuTriggerScope`）、
  `AppChoiceDropdown.kt`、`AppActionMenu.kt`、`AppAnchoredPanel.kt`。三个入口都由组件自己渲染
  触发器，业务只提供 ID 与回调。
- **Mochi 分支走 fork 原生 API**：`MochiDropdown` / `MochiIconDropdown` / `MochiPopoverButton`，
  `MochiItem` 映射 `enabled` / `checked` / `dividerBefore` / `groupTitleBefore` / `children`，
  子项走 `onChildClick`。
- **主题风格选择**（`ThemeConfigScreen.kt`）接入 `AppChoiceDropdown`，`commitAfterDismiss = true`，
  `AppChoiceOption` 由 `composeEngine` 的 entries/values 生成；旧的独立窗口 popup 规避移除。
- **书架更多**（`BookshelfScreen.kt`）接入 `AppActionMenu`，12 个命令改为稳定 ID 列表，
  由 `onAction` 的 `when(id)` 分派；旧的 `showTopBarMenu` 状态与 `TopBarOverflowMenu` 调用移除。
- **P0 trigger 契约**：`trigger` 统一为纯展示内容，`SettingItem` 新增 `interactive` 开关
  （默认 `true` 保持既有调用方行为），各分支包装出唯一可点控件并声明标题文本语义。
  消除了 Mochi 库按钮的 `onClick` 与 `SettingItem` 的 `combinedClickable` 双重点击语义，
  也避免真实按压时两套手势相争。新增 `AppMenuTriggerContractTest` 5 个用例。
- **COUI 原生分支**：`AppChoiceDropdown` / `AppActionMenu` 走 `Menu` + `CouiMenuAnchor`；
  `AppActionMenu` 把 `checked` / `dividerBefore` / `children` 直接映射到 `CouiMenuItem`，
  分组用 `dividerBefore` + `headerText` 表达。`canUseCouiSettingMenu()`（Android 14+ 且
  阴影渲染器可用）不满足时降级 M3。`AppTheme` 的 COUI 页面内容根注入
  `ProvideCouiMenuBackdrop`，菜单玻璃材质有正确的采样源。
- **Miuix 原生命令分支**：`AppActionMenu` 的 `groups` 映射为 `DropdownEntry` / `DropdownItem`，
  稳定 ID 走 `onClick`。有 popup host 用 `OverlayIconDropdownMenu`（无 host 的 window 级弹层
  用 `WindowIconDropdownMenu`），含 `children` 切 `Cascading` 变体；HyperOS 4 且
  `AppGlassOverlayState.origin == Offset.Zero` 时用 `GlassIconDropdownMenu` +
  `GlassIconButton` + `glassPopupAnchor`，面板注册进 overlay 的 root `BoxScope` 以共享坐标根。
  库自己渲染 `IconButton` 与 `expanded`，因此常规分支的 `AppMenuTriggerScope.open()` 是 no-op。
  显式降级：`checked == true` 与 `selected` 合并为 `selected`；分组标题 / `dividerBefore`
  退化为组间分隔线；`destructive` 不体现；`GlassIconButton` 无 `enabled` 参数，禁用靠吞点击。
- **COUI backdrop 覆盖独立窗口**：`AppModalBottomSheet` 的 M3 弹层分支在
  `ComposeEngine.Coui && ThemeResolver.isCouiSupported()` 时，把 Sheet 内容根包进
  `ProvideCouiMenuBackdrop { modifier -> Box(modifier, propagateMinConstraints = true) }`，
  与 `AppTheme` 页面根同构，Sheet 内 COUI 菜单不再采样主窗口。
- **Miuix 原生分支 JVM 契约**：新增 `AppMiuixMenuContractTest` 6 个用例，覆盖无 host
  （`Window*`）与 `AppScaffold` 内（`Overlay*`）两条路径的稳定 ID 回调、逐项禁用、
  `checked` / `selected` 降级映射、级联子菜单子 ID、trigger 单可点节点。
- **`TopBarOverflowMenu` 弹层收编**：`AppAnchoredPanel` 新增 `externalTrigger` 兼容路径，
  调用方按钮外观完全保留，弹层分派统一到新入口。`TopBarOverflowMenu` 不再直接调用
  `RoundDropdownMenu`，改为通过 `scope.open()` / `scope.modifier` 接入；玻璃锚点
  `GlassMenuAnchor` 仍由顶栏自己维护。新增 `AppAnchoredPanelContractTest` 5 个用例覆盖
  主入口（Material / Mochi / Miuix）与兼容路径的开合、dismiss、单可点节点。
- **阶段 C 起步：调用点盘点 + 紧凑形态**：全库 `RoundDropdownMenu(` 88 行匹配、56 个业务文件、
  85 个调用点；严格纯单选 12 处，业务侧只有 2 处传 `mochiAnchorMaxWidth` 兼容补丁。
  盘点发现**形态矛盾**：旧调用点的 trigger 都是自带 `onClick` 的自定义紧凑按钮
  （`SmallPlainButton` / `GlassCard` / `CompactTextProcessTool` / `NormalCard`），
  而 `AppChoiceDropdown` 的 Miuix 分支会渲染整行 `DropdownListSettingItem` 并忽略 `trigger`；
  直接迁移会把紧凑卡片变成设置行。为此给 `AppChoiceDropdown` 增加 `compact` 参数，
  默认（设置行）形态保持不变；该形态暂不接 HyperOS 4 玻璃锚点变形。
- **compact 形态定稿**：四套分支统一由入口用 `Box(clickable)` 包装纯展示 `trigger`，
  Miuix 弹层走 `WindowDropdownPopup` / `OverlayDropdownPopup`（有 popup host 用后者），
  由 `ListPopupLayout` 依调用点自动定位，不需要显式锚点 bounds。这样带文字、方卡或
  任意尺寸的自定义 trigger 都能**零外观变化**迁移；库仍负责 spinner 动画、
  `DropdownItem.selected` 勾选态与逐项禁用。第一版曾用 `WindowIconDropdownMenu`
  （库渲染图标按钮），会把卡片塞进 48dp 按钮，已替换。
- **阶段 C 已迁移 4 处**：`CharsetConfigSheet`（字符集）、`ClickActionConfigSheet`
  （点击区域动作）、`TextProcessingSheet`（替换规则过滤）、`GlobalThemePage`
  （翻页动画速度）。四处都删掉本地 `expanded` 状态，业务回调不变：`ReadBook.setCharset`、
  `readSettingsRepository.setClickAction`、`onFilterChange`、`ConfigUpdate.PageAnimSpeed`。
  `GlassCard` / `NormalCard` 的 `onClick` 本来就可空，省略即纯展示；
  `CompactTextProcessTool` 补了 `modifier` 并把 `onClick` 改为可空（原调用方仍传 `onClick`，
  行为不变）。`AppChoiceCompactContractTest` 4 个用例覆盖 Material 不受影响、
  Miuix `Window`/`Overlay` 的稳定 ID 回调与选中态、trigger 单可点节点。
- **阶段 C 再迁移 4 处**：`FontSelectGrid`（字体排序）、
  `BookSourceEditScreen.BookSourceOptions`（书源类型）、
  `RssSourceEditScreen.RssEditOptions`（订阅类型 + 文章布局）全部改为
  `AppChoiceDropdown(compact = true)`，业务回调分别仍是
  `OtherSettingsGateway.update { fontSort }`、`BookSourceEditIntent.SetSourceType`、
  `RssSourceEditIntent.SetType/SetArticleStyle`，稳定 ID 用枚举/数组下标的字符串；
  三处都删掉本地 `expanded` 与 `RoundDropdownMenu`。
  `SourceEditOptionCard` 的 `onClick` 由必填函数改为**必填可空**（传 `null` 即纯展示 trigger），
  刻意不带默认值，让 `modifier` 保持为首个可选参数以符合 Compose `ModifierParameter` 规则；
  `RssEditOptions` 的单行压缩代码展开为常规排版，开关卡片仍按位置传 lambda，行为不变。
  `FontSelectGrid` 的排序 trigger 用 `Box(48.dp, Center)` 包住纯展示 `Icon`，
  对齐原 `IconButton` 的触摸区与图标居中外观。
  两个编辑页的 `BookSourceEditMenu` / `RssEditMenu` 原本是命令菜单，已在下一批切到
  `AppActionMenu`。新增 `AppChoiceCompactContractTest` 2 个用例，用带文字的卡片 trigger
  覆盖 Material / Miuix Window 下「trigger 文本不被库按钮吞掉、单可点节点、稳定 ID 回调」。
- **阶段 C 命令菜单簇起步**：`BookSourceEditMenu`（10 个动作）与 `RssEditMenu`（9 个动作）
  改为 `AppActionMenu`，顶栏 MoreVert 按钮由入口渲染（与 `BookshelfScreen` 既有样本一致），
  `trigger` 只给纯展示 `AppIcon`。两处各建一份 `internal enum` 动作注册表
  （稳定 ID + `@StringRes` 文案），UI 从它生成 `AppAction`，`onAction` 经纯函数
  `bookSourceEditIntentFor` / `rssSourceEditIntentFor` 映射到 `Intent`，未知 ID 不产生动作。
  书源菜单的 `auto_complete` 保留旧 `isSelected = state.autoComplete` 语义（映射为
  `AppAction.selected`）；RSS 菜单原本无勾选态，不新增。
  菜单开合改为入口自持：`BookSourceEditRoute` 删除 `menuExpanded` 状态与 Screen 的两个参数，
  `RssSourceEditScreen` 删除本地 `menu` 状态。两个 Screen 不再引用 `RoundDropdownMenu*`。
  新增 `BookSourceEditMenuActionsTest` / `RssSourceEditMenuActionsTest` 共 8 个用例，
  迁移前先跑通、迁移后复跑通过，覆盖 ID 唯一性与稳定顺序、逐项 Intent 映射、未知 ID 无动作。
- **分段线口径统一与 A 类迁移（2026-11-11）**：用户确认「分段线可以不保留文字」后，
  `AppMenuModels.dividerBeforeAt` 成为四引擎共用的分段线唯一判定：
  `dividerBefore` / `groupTitleBefore` / `group.title` / 多 group 组首一律降级为纯分段线，
  四引擎都不渲染标题文本。`AppActionMenu` 的 Material 分支补 `PillDivider()`、`selected`
  语义与 `children` 平铺（不再静默丢子项），COUI 去掉只认第一组的 `headerText`，
  Mochi 去掉组首标题覆盖；`AppChoiceDropdown` 的 Material / Miuix compact 支持
  `AppChoiceOption.dividerBefore`（Miuix 默认设置行形态因库不支持仍忽略，已写进 KDoc）。
  按「先补契约、再迁移」的顺序：新增 `AppMenuDividerContractTest` 13 例与
  `TopBarActionMenuContractTest` 4 例；`TopBarOverflowMenu` 新增 `TopBarActionMenu`
  声明式入口，`DynamicTopAppBar` / `ListScaffold` / `RuleListScaffold` 新增可选
  `menuGroups` + `onMenuAction` 槽，旧 `dropDownMenuContent` 调用点零改动。
  A 类 5 处（`BookshelfManageScreen` / `ReplaceRuleScreen` / `BookSourceScreen` /
  `RssFavoritesScreen` / `HomeScreen`）全部改为「两个 `AppActionGroup` + 稳定 ID 分派」，
  删除手画 `PillDivider()`、`SortMenuItem` 与 `showPageMenu` 等本地状态。
  这 5 处的 MoreVert 按钮改由入口渲染，四引擎实际外观待真机复核。
- **顶栏 `dropDownMenuContent` 批量迁移（2026-11-11 第二轮）**：重新盘点 11 个顶栏
  content 调用点后，9 处纯命令 / 动态分组菜单迁到 `menuGroups` + `onMenuAction`
  （`RuleSubScreen`、`HighlightTagRuleScreen`、`DictRuleScreen`、`TxtRuleScreen`、
  `RssScreen`、`ExploreScreen`、`ExploreMainRoute`、`RemoteBookScreen`、`ImportBookScreen`）。
  动态分组统一用 `group:` 稳定前缀编码 ID（`"group:"` = 全部），自绘 `leadingIcon` /
  `trailingIcon` 改 `AppAction.icon` / `AppAction.selected`；`RssScreen` 原有的一条
  `PillDivider()` 用两个 `AppActionGroup` 表达，其余保持单组、不引入新线。
  新增 `TopBarMenuIdContractTest` 3 例静态契约（不再手画 content、每个字面量 ID 都有分派、
  动态分组保持 `group:` 编解码），锁住 `when(id)` 分派最易出现的「拼错 ID 静默无动作」。
  保留 `RssSourceScreen`（伪子菜单 + 嵌套 popup + 不 dismiss 项）为顶栏 content 仅剩的 1 处。
- **`TocScreen` 迁移（2026-11-11 第三轮）**：按「动作注册表 + 纯 ID → 行为映射 +
  先建基线再迁移」完成。新增 `TocMenuActions.kt`（`TocMenuAction` 10 项 +
  `TocMenuBehavior` sealed + 纯函数 `tocMenuBehaviorFor`）与 `TocMenuActionsTest`
  5 例，迁移前先跑通；UI 侧按 `pagerState.currentPage` 构造两套 `menuGroups`，
  动态参数（书名 / 书源 / 时间戳文件名）仍在执行时按 state 计算，映射保持纯函数。
  原 1 条 `PillDivider` + 1 条 `PillHeaderDivider` 用 2–3 个 `AppActionGroup` 表达
  （非本地书 2 组、本地书 3 组、书签页单组），结构一一对应；本地书组标题文字按口径丢弃，
  这是「文字消失」首次发生在生产菜单。`TopBarMenuIdContractTest` 增 2 例，
  断言 `TocScreen` 已离开 content 槽且全仓顶栏 content 只剩 `RssSourceScreen.kt`。
- **Mochi 走 anchor-only（2026-11-11，用户定案 B 方案）**：fork 新增
  `MochiDropdownMenu(anchorBounds, …)` 并把 `MochiOverlay` 从 `DropdownCore` 里拆出来
  （浮层不再依赖库按钮，`buttonContent` 可空、`singleBlob` 只画面板本体），发布
  `0.2.1-menu2` 到 `mavenLocal`，本仓库 `libs.versions.toml` 同步。三个入口的 Mochi 分支
  全部改成与 `CouiChoice` / `CouiActions` 同构的「`Box(clickable)` + `boundsInWindow()`
  锚点上报」：`MochiChoice` → `MochiDropdownMenu`，`MochiActions` → `MochiDropdownMenu`
  （`AppMenuAnchor` 只用于面板宽度），`AppAnchoredPanel` → `AppMochiPopover`。
  `trigger` 仍是纯展示内容、`AppMenuTriggerScope` 契约不变；`commitAfterDismiss`、
  `selectedIndex`、`onChildClick`、逐项禁用 / 勾选 / 分隔线语义全部保留。
  `AppAnchoredPanelContractTest` 的 Mochi 用例从 `performTouchInput`（Press 手势）改为普通
  `performClick`，并断言 `expanded` 真的置位。
- **顶栏按钮壳与收起遮罩（同日第二轮，`0.2.1-menu3`）**：第一版 Mochi 分支只包
  `Box(clickable)`，顶栏 ⋯ 变成没有底色的裸图标（订阅页与发现页同排按钮不一致，用户截图确认）。
  改为按 `AppMenuAnchor` 分：`Icon` 穿项目 `MochiBarIconButton`（= `TopBarActionButton` 在
  Mochi 下的实现），`Row` 仍由 trigger 自绘。同时发现面板收起最后一段会缩成和按钮一样大的
  实心圆盖在真按钮上（锚点不再由库隐藏，就露出了这层「圆形遮罩」），fork 里让
  `singleBlob` 的面板本体在收起最后 20% 淡出（`smoothstep(0f, 0.2f, progress)`，只在
  `!expanded` 时生效，展开的形变不变）。**两条都未真机复核，也未跑测试（用户要求等界面定了再跑）。**

- **规则编辑剪贴板菜单 3 处（2026-11-11 第五轮）**：从 74 处独立 `RoundDropdownMenu`
  里选出逐行同构的 `RuleEditSheet` / `TagGroupRuleEditSheet` /
  `HighlightTagRuleEditSheet`，统一为单个 `AppActionGroup` +
  `"copy_rule"` / `"paste_rule"` 稳定 ID 分派；删除 `showMenu` 与手画菜单，
  按钮交给入口渲染（复用第四轮用户调好的 Mochi `MochiBarIconButton`），
  `startAction` 关闭按钮保持不变。新增 `RuleClipboardMenuContractTest` 2 例静态契约。
  `ReplaceEditScreen` / `DirectLinkUploadBottomSheet` 形态不同，留下一批。
  双变体编译 `BUILD SUCCESSFUL`；`menu.*` + `topbar.*` 定向 9 类 / 49 用例 0 失败；
  `git diff --check` 与 IDE 诊断通过。

- **Sheet action 槽命令菜单 4 处（2026-11-11 第六轮）**：沿用第五轮同构形态，
  `SourceLoginSheets` / `GroupManageSheet` / `HighlightRuleConfigSheet` /
  `CloudTtsScreen`（HTTP TTS 编辑器）的 `startAction` / `endAction` 手画菜单统一改为
  单个 `AppActionGroup` + 稳定 ID 分派，按钮交给入口渲染；剪贴板 `scope.launch`、
  `showImportSheet` 等副作用逐条保留并在点击时求值。`CloudTtsScreen` 同文件另外 7 处
  形态不同的下拉刻意不动，用棘轮断言锁住。带展开态高亮的 `CompactToolIconBox` 与顶栏
  `TopBarActionButton` 也刻意排除（需各自先建基线）。新增 `SheetActionMenuContractTest`
  3 例，迁移前 3 例如期为红；双变体编译 `BUILD SUCCESSFUL`；
  `menu.*` + `topbar.*` 定向 **10 类 / 52 用例 0 失败**；
  `git diff --check` 与 IDE 诊断通过。

- **「命令 + 独立开关」菜单 3 处 + `checked` 能力补齐（2026-11-11 第七轮）**：
  `ContentEditSheet` / `ChangeChapterSourceSheet` / `ChangeSourceSheet` 的
  `isSelected` 开关是「点击取反的独立勾选」，映射 `AppAction.checked` 而非单选。
  迁移前发现 `AppActionMenu.LegacyActions`（Material）只读 `selected`、静默丢 `checked`，
  先补齐 `isSelected` 与 `semantics.selected`（含 `children`）为
  `selected || checked == true`，与 Miuix 降级口径一致，并写进契约。三处改为单个
  `AppActionGroup` + 稳定 ID 分派，列表项 `dropdownContent` 的另一套菜单保留。
  `BookSourceScreen` / `RssSourceImportDialog` 的开关项点击后不 dismiss（菜单保持展开），
  与入口「先 dismiss 再回调」冲突，本轮刻意排除（**第十轮已改用导入面板配置页解决，
  入口不需要该能力，见本节第十轮记录**）。
  新增 `ToggleActionMenuContractTest` 3 例，迁移前 3 例如期为红；双变体编译
  `BUILD SUCCESSFUL`；`menu.*` + `topbar.*` 定向 **11 类 / 55 用例 0 失败**；
  `git diff --check` 与 IDE 诊断通过。开关态在 Mochi / COUI 下由高亮变原生勾选圈，
  属预期外观变化，未真机复核。

- **知识 / 角色详情纯单选 3 处（2026-11-11 第八轮）**：按「同目录、同构」取
  `ui/book/knowledge/` 下 `KnowledgeTypeSelector` / `ProfileVoiceTraitDropdown` /
  `ProfileRoleDropdown`，三处都是 `Box { GlassCard(onClick); RoundDropdownMenu }`。
  统一改 `AppChoiceDropdown(compact = true)`：`GlassCard` 去掉 `onClick` 变纯展示，
  `modifier = this.modifier` 挂锚点，入口包装唯一可点节点；`options` 由常量表 /
  参数列表 / `ALL_ROLES` 构造 `AppChoiceOption`。`compact` 必须开，否则 Miuix
  默认行形态忽略 `trigger`。角色选择器的「再次点选已选项则清空」保留在 `onSelect`。
  `GlobalThemePage.kt:402`（设置行 `TinySettingItem`）与 `MangaReaderSheets.kt:468`
  （`NormalCard` 方块）形态不同，留下一批。新增 `KnowledgeChoiceMenuContractTest`
  3 例，迁移前 3 例如期为红；双变体编译 `BUILD SUCCESSFUL`；
  `menu.*` + `topbar.*` 定向 **12 类 / 58 用例 0 失败**；
  `git diff --check` 与 IDE 诊断通过。菜单项新增原生选中态，未真机复核。

- **翻页动画 / 九宫格点击区域 compact 单选 2 处（2026-11-11 第九轮）**：
  `GlobalThemePage` 的 `TinySettingItem` 设置行（`Row.weight(1f)`）与
  `MangaReaderSheets` 的 `NormalCard` 方块统一改 `AppChoiceDropdown(compact = true)`，
  trigger 去掉 `onClick` 纯展示，`modifier = this.modifier` 承接锚点、外层尺寸走入口
  `modifier` 参数；`onSelect` 把 ID 转回 Int 并保留原有 `setPageAnim(-1)` / `onPick`。
  无障碍名称用已有的 `R.string.page_anim` / `R.string.manga_reader_click_areas`，
  不用当前值。`MangaSourceActionSquare` 共用 expanded 的菜单刻意保留并用棘轮锁住。
  新增 `CompactChoiceMenuContractTest` 3 例，迁移前 3 例如期为红；双变体编译
  `BUILD SUCCESSFUL`；`menu.*` + `topbar.*` 定向 **13 类 / 61 用例 0 失败**；
  `git diff --check` 与 IDE 诊断通过。**收敛判断**：`compact = true` 纯单候选已收完
  （共 5 处），后续剩余调用点不再有可直接套模板的零状态簇。

- **导入面板「配置页 / 列表页」两页切换 2 处（2026-11-11 第十轮）**：
  `BookSourceScreen` 与 `RssSourceImportDialog` 的导入选项原本是**混合菜单**——命令项
  （自定义分组、选择导入状态）点击后 `dismiss()`，3 个 keep 开关点击后**不** `dismiss()`，
  靠菜单保持展开实现连续勾选。`AppActionMenu` 四分支统一「先收起再回调」，
  若为此加「动作后保持展开」就必须落到 `AppAction` 级字段（同一菜单里命令项要关、
  开关项不关），为一个只服务 2 个页面的分支扩展三入口契约不划算，
  且下拉菜单里塞持久多选开关本身也不符合 M3 惯例。
  **改为换 UI 形态**：`BatchImportDialog` 增加可选 `configContent`，顶栏出现
  「配置 ↔ 列表」切换按钮；配置页放常驻 `SwitchSettingItem`（直接回传开关新值，
  不再 `!旧值` 翻转）与 `SettingItem` 命令行，并隐藏全选 / 导入按钮、垂直滚动；
  列表页行为完全不变。两处混合菜单与 `ImportOption` 包装一并删除，
  `BookSourceScreen` 只剩行内 `BookSourceItemMenu` 一处 `RoundDropdownMenu`。
  新增 `BatchImportConfigPageContractTest` 3 例（迁移前 3 例如期为红）。
  双变体编译 `BUILD SUCCESSFUL`；`menu.*` + `topbar.*` + `importComponents.*`
  定向 **14 类 / 64 用例 0 失败**；`git diff --check` 与 IDE 诊断通过。
  **结论：入口不需要「动作后保持展开」能力，该待办作废**；四引擎 `expanded` 所有权
  见 handoff §8 第 4 条的实测更正。配置页布局、滚动与四引擎外观**未真机复核**。

- **替换规则顶栏命令菜单 + 直链上传规则菜单 2 处（2026-11-11 第十一轮）**：
  `ReplaceEditScreen` 顶栏 MoreVert 原为 `TopBarActionButton(onClick) + RoundDropdownMenu`
  两项命令（复制 / 粘贴规则，旧项无图标）；`DirectLinkUploadBottomSheet` 的 `endAction`
  原为 `Box { MediumTonalButton; RoundDropdownMenu }` 三项命令（导入默认预设 / 复制 / 粘贴，
  都带 `leadingIcon`）。分别改为 `AppActionMenu` + 单个 `AppActionGroup` + 稳定 ID
  （`copy_rule` / `paste_rule`；`import_default_rule` / `copy_direct_link_rule` /
  `paste_direct_link_rule`），旧 `leadingIcon` 转 `AppAction.icon`，规则编辑页保持无图标
  以免顺带改变外观；删除 `showMenu` 与手画 trigger，按钮交给入口渲染。
  三个副作用逐条保留且在点击时求值：`context.selector(state.directRulePresets)`、
  `sendToClip(GSON.toJson(...))`、`getClipText()` + `runCatching` 的 toast 兜底。
  **刻意保留**：`ReplaceEditScreen.GroupSelector` 是 `ExposedDropdownMenuBox` +
  `PrimaryEditable` 锚点的**可编辑组合框**（文本框 + 分组建议），`AppChoiceDropdown`
  只能选不能输入，会丢掉自由填分组的能力，故整文件留 1 处 `RoundDropdownMenu` 并用棘轮锁住。
  新增 `ReplaceDirectLinkMenuContractTest` 3 例（结构性 / 每个 ID 有声明与分派 /
  组合框棘轮），迁移前 3 例如期为红。双变体编译 `BUILD SUCCESSFUL`；
  `menu.*` + `topbar.*` + `importComponents.*` 定向 **15 类 / 67 用例 0 失败**；
  `git diff --check` 与 IDE 诊断通过。四引擎下按钮外观与组合框**未真机复核**。

- **三入口 × 四引擎契约收口（2026-11-11 第十二轮）**：补上反向清单里仍缺行为覆盖的两项。
  新增 `MenuChildrenCallbackContractTest` 4 例：① `children` 子项回调——Mochi 在真实弹层里
  点父项 → 子项，断言回传**子项稳定 ID** 而非父项；② 关闭所有权——Material / Miuix 单层菜单
  选完自动收起（Mochi 子项用例顺带验证），业务侧不持 `expanded`。
  COUI 的二级弹层在 Robolectric 下点父项后不出现（父项点击直接收起），无法用语义查询驱动，
  只能退化为源码契约（`CouiMenuItem(value = child.id, children = ...)` +
  `onAction(item.value)`）并写进测试 KDoc，**COUI 二级菜单外观仍需真机复核**。
  另核对两处「例外」早已写进 KDoc、无需重复建测试：`AppChoiceDropdown` 的 Miuix 默认行形态
  忽略 `trigger` 槽（类头 KDoc），`AppChoiceOption.dividerBefore` 在 Miuix 默认行形态被忽略
  （模型字段 KDoc）。
  `menu.*` + `topbar.*` + `importComponents.*` 定向 **16 类 / 71 用例 0 失败**；
  双变体编译 `BUILD SUCCESSFUL`；`git diff --check` 与 IDE 诊断通过。
  仍未覆盖：COUI / Miuix **玻璃（HyperOS 4）** 路径的 ID 回调（需 `AppGlassOverlayState`
  与 HyperOS 4 环境）、菜单项的语义树名称与 TalkBack 朗读顺序（真机）。

- **CloudTts 引擎页顶栏命令菜单 1 处（2026-11-11 第十三轮）**：旧实现是
  `Box { TopBarActionButton(onClick = { expanded = true }); RoundDropdownMenu }` 四项纯命令
  （导入 TTS / 导出文件 / 复制 URL / 清除缓存），改为 `AppActionMenu` + 单个
  `AppActionGroup` + 稳定 ID（`import_tts` / `export_tts_file` / `copy_tts_url` /
  `clear_tts_cache`），删除 `expanded` 与手画按钮，`showHttpTtsImportSheet = true` 与三个
  `CloudTtsIntent` 逐条保留、点击时求值。同文件第六轮的 `speak_engine` 编辑器菜单不动。
  **刻意保留同文件 6 处下拉**，并把理由写进契约：区域菜单是**混合菜单**
  （`regionOptions` 选项 + `cloud_tts_custom_region_action` 命令项会清空 region 并展开输入框），
  `AppChoiceDropdown` 只能选不能带命令项；provider / model / style / role / format 是动态单选，
  但 trigger 是 `MediumTonalButton`，**没有纯展示变体**，换入口需要先定「纯展示按钮」形态，
  属外观决策，需先建四引擎外观基线，本轮不做。
  新增 `CloudTtsTopBarMenuContractTest` 3 例（顶栏结构性 / 四个 ID 声明与分派 /
  同文件剩余 6 处下拉 + 自定义区域命令项棘轮），迁移前 3 例如期为红；
  `SheetActionMenuContractTest` 的 CloudTts 棘轮同步 7 → 6。
  双变体编译 `BUILD SUCCESSFUL`；`menu.*` + `topbar.*` + `importComponents.*`
  定向 **17 类 / 74 用例 0 失败**；`git diff --check` 与 IDE 诊断通过。
  Mochi / COUI / Miuix 顶栏按钮外观**未真机复核**（与前几轮同一风险）。

- **触发按钮玻璃底修复（2026-11-11 第十四轮）**：真机基线（书源管理顶栏 × 四引擎）暴露
  两个按钮问题。① COUI ⋮ 缺玻璃：`CouiActions` 用普通 `IconButton`，绕过
  `rememberCouiCapsuleRenderer()` + `CouiTopAppBarButton` 的玻璃胶囊 → 改走原生胶囊按钮，
  非原生环境保留 `IconButton` 兜底；② Miuix HyperOS 4 单帧错位：玻璃门禁改为跟随顶栏的
  `LocalMiuixGlassTopBar`（与 `TopBarActionButton` 同判定）；图标往左上飞的真机特征定位到
  `GlassTransformPopup` 用未上报的 `anchor.contentBounds`（退回整颗按钮的 `containerBounds`）
  定位图标副本 → 按钮内容外包 `Box(Modifier.glassPopupAnchorContent(anchor))` 修正。
  新增 `MenuTriggerGlassContractTest` 2 例锁「走哪条分支」；玻璃观感与错位是否消失**待真机复测**。

- **RSS 排序页顶栏主菜单 2 处（2026-11-11 第十五轮）**：`RssSortScreen` 旧实现是
  `TopBarActionButton + RoundDropdownMenu`（8 项），末项「重定向策略」再开一个二级
  `RoundDropdownMenu`（`RedirectPolicy.entries` 单选，选完还要手动关主菜单）。
  改为 `TopBarActionMenu` + 单个 `AppActionGroup`：子项 ID = `redirect_` +
  `RedirectPolicy.name`，父项降级为 `AppAction.children`（Mochi / COUI / Miuix 原生级联，
  Material 平铺不丢 ID），选中态用 `AppAction.checked`，`showMainMenu` / `showRedirectMenu`
  与两处手画菜单一起删除，收起交给入口。
  **刻意保留**：底部分组切换菜单挂在 `SmallToggleButton` 上（库无非可点的纯展示形态），
  仍用 `RoundDropdownMenu`，棘轮锁住。
  新增 `RssSortMenuContractTest` 3 例。本轮只跑了 `compileAppCoreDebugKotlin`
  与该测试类（用户要求控制测试成本），未跑整组 / 双变体。真实二级菜单展开与选中态
  **待真机复核**。

- **详情页顶栏「更多」1 处（2026-11-11 第十六轮）**：`BookInfoScreen` 的
  `BookInfoOverflowMenu`（内部 `RoundDropdownMenu`，19 个条件项 + 5 处 `isSelected`）
  折叠进 `TopBarActionMenu`：条目 ID = `book_` + `BookInfoMenuAction.name`，
  分派用 `BookInfoMenuAction.entries.firstOrNull` 反查，避免手写 ID 与枚举漂移；
  条件可见性（自定义按钮 / 在书架 / 本地书 / 有登录 / 有书源 / 私有锁）与勾选态逐条保留。
  删除 `BookInfoOverflowMenu` 整个函数与本地 `expanded` 状态，收起交给入口。
  新增 `BookInfoMenuContractTest` 3 例（入口结构 / 19 项与枚举绑定 / 5 处勾选态来源）。
  本轮只跑 `compileAppCoreDebugKotlin` 与该测试类。真机条件分支与勾选渲染**待复核**；
  同文件另一处菜单（非顶栏）保持原样。

### 中间态边界（不是完成态，勿写成"四套已完整"）

- **调用点覆盖率仍然很低**：累计 50 处拿到原生入口（主题风格、书架更多、
  阅读器 Sheet 4 处、字体/书源/RSS 编辑 4 处、两个编辑页顶栏命令菜单 2 处、
  A 类带分段线菜单 5 处、A' 类顶栏 content 菜单 9 处、`TocScreen` 1 处、
  规则编辑剪贴板菜单 3 处、Sheet action 槽命令菜单 4 处、
  「命令 + 独立开关」菜单 3 处、知识 / 角色详情纯单选 3 处、
  翻页动画 / 九宫格点击区域 compact 单选 2 处、导入面板配置页 2 处、
  替换规则顶栏 + 直链规则菜单 2 处、CloudTts 引擎页顶栏命令菜单 1 处、
  RSS 排序页主菜单 2 处、详情页顶栏「更多」1 处），
  其余 51 个业务调用点、320+ 处 `RoundDropdownMenuItem` 尚未迁移。这是计划内的
  渐进中间态，也是当前最大的未完成项，不是漏做。旧 `RoundDropdownMenu` 本身已按引擎分派
  （Miuix `WindowListPopup`/玻璃、Mochi `AppMochiPopover`、COUI 形状、M3 弹层），
  缺的是「触发器 + 整组条目」联动带来的原生完整效果。
- **Miuix 默认行形态仍是委托态**：`compact = false` 时仍复用 `DropdownListSettingItem`。
  自定义卡片按钮现在可以靠 `compact` 零变化迁移；严格纯单选里只剩
  `ReaderMoreActionsSheet` 的 `ImageStyleDropdown` / `PageAnimDropdown` 未迁
  （`ActionSquareHost` 用一个共享 `expanded`，且方块按钮形态复杂，需单独建基线）。
- **顶栏 / 设置行按钮外观已开始变化（用户已实测确认）**：两个编辑页、书架、
  A 类 5 处、A' 类 9 处与 `TocScreen` 共 18 处的 MoreVert 不再由 `TopBarActionButton`
  渲染，改由 `AppActionMenu` 的四引擎分支渲染；主题风格等设置项则由
  `AppChoiceDropdown` 的库按钮接管整行 trigger。这是拿到 Miuix / Mochi / COUI
  原生按钮效果的前提，但库按钮不读项目 `TopBarButton.kt` / `SettingItem` 的引擎适配，
  与同页项目自绘按钮混排必然不一致（四引擎都有，Mochi 最显眼）。
  18 个 `TopBarOverflowMenu` 页面仍走 `externalTrigger = true` 兼容路径，
  两种 MoreVert 之间也会互相不一致。**全部未真机复核，且用户已观察到该现象**。
- **Mochi 不再渲染菜单按钮（Miuix / COUI / Material 不变）**：三个 Mochi 分支都改成
  入口包可点节点 + 锚点上报，弹层走 fork 的 anchor-only 入口，库按钮与大圆一并消失，
  开合变成「点击松手后」，按下即开和按住滑选不再存在。`AppActionMenu` 的 `Icon` 锚点
  重新穿上项目 `MochiBarIconButton` 那层壳（与同排搜索按钮同款圆底），`Row` 锚点
  （设置行 / 自绘整行）仍由 trigger 自己画。COUI 的 `IconButton`、Material 的 M3
  `IconButton`、Miuix 的库按钮还各自不同，要四引擎统一成项目自绘按钮需另做
  「触发器即按钮」模式。**Mochi 的外观 / 交互全部未真机复核**。
- **`AppAnchoredPanel` 的 Mochi 主入口已换成 `AppMochiPopover`**：B 类面板不再有库按钮、
  按下即开和按钮变面板形变，和 18 个顶栏页面走的 `externalTrigger` 路径共用同一个分离式
  面板容器，`LocalMochiDismiss` 由面板自己提供。`externalTrigger = true` 兼容路径不变。
- 旧 `RoundDropdownMenu` / `RoundDropdownMenuItem` / `RoundDropdownMenuLazy` 全部保留，未标废弃；
  旧补丁参数 `glassAnchor` / `mochiAnchorMaxWidth` / `LocalGlassPopupContent` /
  `LocalUseWindowPopupForMochiMenu` 仍在。
- 玻璃分支的 `anchorContent` 直接复用纯展示 `trigger` 槽，过渡态里会短暂多一个带
  `contentDescription` 的图标节点；尚未做 TalkBack 实测。

### 验证结果

- `:app:compileAppCoreDebugKotlin`、`:app:compileAppComicDebugKotlin` 多轮 `BUILD SUCCESSFUL`，
  `mochi-compat.gradle` 的 transform 在 `0.2.1-menu1` 上正常命中。
- 早期 96 个定向用例中 2 个失败，均为改动触发的基线变化：
  - `AppThemeEngineTest.themePickerShowsCouiAsUnavailableBelowAndroid14` 是**真实回退**：
    `MaterialChoice` 漏了不可用档位的 `disabled` 语义，已修复。
  - `MochiOverlaysAndBarsTest.switchingFromMochiToMaterialUsesASeparatePopupWindow` 是
    **有意的行为变更**：Mochi 浮层不再是 Compose `Popup`。已改名为
    `switchingFromMochiToMaterialCommitsAfterMenuDismisses`，改为验证选项可达、
    菜单关闭后主题值提交且不崩；当前值断言移到纯展示 trigger 的文本节点。
- P0 的 5 个契约用例 + 2 个既有行为基线共 7 个用例 `BUILD SUCCESSFUL`。
- Miuix 原生分支 + Sheet backdrop 那一轮定向验证：5 个相关类共 **76 个用例、
  0 failures / 0 errors**，双变体编译通过，`verifyConfigArchitecture` / `git diff --check` 通过。
- 上一轮（补契约用例 + `TopBarOverflowMenu` 收编）**只跑定向**：7 个相关类共
  **87 个用例、0 failures / 0 errors**，双变体编译通过，`verifyConfigArchitecture` /
  `git diff --check` / lint 均通过。级联子菜单在 unmerged tree 里有展开过渡副本，
  用例显式取首个可见节点。收编后既有的顶栏玻璃菜单回归用例仍通过。
- 阶段 C 起步那一轮（首个调用点）**只跑定向**：7 个相关类共 **81 个用例、
  0 failures / 0 errors**，`SheetGlobalConfigReadTest` 源码静态扫描通过。
- 本轮（compact 定稿 + 3 处迁移）仍**只跑定向**：双变体 Kotlin 编译 `BUILD SUCCESSFUL`；
  `--tests` 过滤 8 个相关类（`menu.*` 全部契约类、`MochiOverlaysAndBarsTest`、
  `SheetGlobalConfigReadTest`、`ReadBookSheetStackTest`、
  `ConfigUpdateActionsInvariantTest`）共 **84 个用例、0 failures / 0 errors**。
  既有 `AppChoiceCompactContractTest` 4 个用例在弹层换实现后仍全通过，
  证明稳定 ID 回调、选中态、trigger 单可点节点没有回退。
  `verifyConfigArchitecture`、`git diff --check`、lint 均通过；
  编译期只有一条既有的 `BasicTextField` deprecation 提示，与本次改动无关。
- 此前全量 `testAppCoreDebugUnitTest` 跑过 3010 个用例：9 个失败集中在漫画文件 URI
  （`URISyntaxException: file://C:%5C...`，Windows 路径编码）与 `AppScaffoldInsetsTest`
  的状态栏高度断言，与菜单/设置行语义无代码交集。`testAppComicDebugUnitTest`、
  `lint` / `assemble` 按用户要求**未跑**。另发现 `appComicDebug` 的 `R.jar` 增量产物缺失，
  `--rerun` 重建后已恢复。
- 本轮（4 处调用点迁移 + 卡片 trigger 契约）仍**只跑定向**：双变体 Kotlin 编译
  `BUILD SUCCESSFUL`；`--tests` 过滤 8 个相关类共 **86 个用例、0 failures / 0 errors**
  （含新增 2 个卡片 trigger 用例）；`verifyConfigArchitecture`、`git diff --check` 通过。
  `:app:lintAppCoreDebug` **失败但非本轮改动**：唯一 error 是既有
  `PhotoDialog.kt:86` 的 `ThreadConstraint`（`@WorkerThread` 与 UI 线程冲突），
  该文件本轮未改、也不在 `lint-baseline.xml` 中；首次 lint 曾暴露本轮
  `SourceEditOptionCard` 的 `ModifierParameter` warning，改为必填可空 `onClick` 后
  warning 数 33 → 32、该条消失，复跑报告中本轮 3 个源文件零条目。
  `lintAppComicDebug` / 全量单测 / assemble 按定向策略未跑。
- 本轮（命令菜单簇）仍**只跑定向**：双变体 Kotlin 编译 `BUILD SUCCESSFUL`；
  12 个相关类共 **103 个用例、0 failures / 0 errors**（含新增 8 个 ID → Intent 基线用例）；
  `verifyConfigArchitecture`、`git diff --check` 通过。`:app:lintAppCoreDebug` 仍因既有
  `PhotoDialog.kt:86` 的 `ThreadConstraint` 失败，warning 数维持 32、本轮 4 个改动文件
  在报告中零条目，说明没有引入新 lint 问题。`lintAppComicDebug` / 全量单测 /
  assemble 按定向策略未跑。
- 真机效果始终未验证：COUI 触点定位与材质（Android 14+）、Miuix 玻璃锚点变形
  （API 33+）、Mochi 融球滑选与带卡片 trigger 的按钮形变、TalkBack、根主题切换、
  Sheet 内 COUI 菜单采样、`TopBarOverflowMenu` 收编后的顶栏外观、
  8 处迁移后紧凑卡片/图标在四引擎下的实际布局（含 `FontSelectGrid` 的 48dp 对齐）、
  两个编辑页顶栏 MoreVert 按钮改由入口渲染后的外观。
- 本轮（分段线口径 + A 类 5 处）仍**只跑定向**：双变体 Kotlin 编译 `BUILD SUCCESSFUL`
  （仅既有 `RemoteBookScreen` `VisualTransformation` deprecation 提示）；14 个相关类共
  **120 个用例、0 failures / 0 errors**（含新增 17 例）；`verifyConfigArchitecture`、
  `git diff --check` 通过。`:app:lintAppCoreDebug` 仍因既有 `PhotoDialog.kt:86`
  的 `ThreadConstraint` 失败，`1 error / 32 warnings` 与上一轮持平，lint 报告中本轮
  改动文件命中数 0。`testAppComicDebugUnitTest` / `lintAppComicDebug` / 全量单测 /
  assemble 按定向策略未跑。5 处菜单的业务副作用未建独立 ID → 行为基线单测。
- 第二轮（顶栏 content 9 处）同样**只跑定向**：双变体 Kotlin 编译 `BUILD SUCCESSFUL`；
  15 个相关类共 **123 个用例、0 failures / 0 errors**（新增 `TopBarMenuIdContractTest` 3 例）；
  `verifyConfigArchitecture`、`git diff --check` 通过。`:app:lintAppCoreDebug` 仍是既有
  `PhotoDialog.kt:86` 的 `ThreadConstraint`，`1 error / 32 warnings` 与上一轮持平，
  lint 报告里 32 个改动的 Kotlin 文件命中数 0。两轮 14 处迁移菜单的业务副作用
  仍未建独立 ID → 行为基线单测，静态契约只保证 ID 声明与分派一致。
- 第三轮（`TocScreen`）同样**只跑定向**，但按「先基线后迁移」执行：
  `TocMenuActionsTest` 5 例在改 UI 之前先跑通，迁移后再复跑通过；
  双变体 Kotlin 编译 `BUILD SUCCESSFUL`；19 个相关类共 **138 个用例、
  0 failures / 0 errors**；`verifyConfigArchitecture`、`git diff --check` 通过。
  `:app:lintAppCoreDebug` **本轮未复跑**（用户取消），最近一次完整结果仍是既有
  `1 error / 32 warnings`、改动 Kotlin 文件命中数 0，第三轮 lint 结论待补跑确认。
- Mochi anchor-only 那一轮：fork 侧 `:mochi-compose:compileReleaseKotlin`、
  `:example:compileDebugKotlin`、`spotlessCheck` 均通过，新增
  `MochiDropdownMenuTest`（4 例：窗口→屏幕坐标换算、选项可见、收起不渲染、点项回调下标）
  全通过；本仓库 `:app:compileAppCoreDebugKotlin` `BUILD SUCCESSFUL`，
  13 个相关类共 **121 个用例、0 failures / 0 errors**
  （`ui.legado.app.ui.widget.components.menu.*` 35 例、`MochiOverlaysAndBarsTest` 51 例、
  `TopBarActionMenuContractTest` 4 例、`CouiFloatingAndMenusTest` 8 例、
  `AppThemeEngineTest` 11 例、`TopBarMenuIdContractTest` 5 例、`TocMenuActionsTest` 5 例、
  `RoundDropdownMenuLazyTest` 1 例、`SheetGlobalConfigReadTest` 1 例）。
  `AppChoiceDropdown` 的 Mochi 分支换实现后，主题风格选择（`commitAfterDismiss = true`）
  与书架 / 顶栏菜单全部照常；`lintAppCoreDebug` / `compileAppComicDebugKotlin` 本轮未跑，
  真机（Mochi 面板从锚点长出的位置、点击松手才弹、TalkBack）仍未验证。

### 下一阶段

1. **按钮外观一致性 —— 已完成（第四轮，用户侧交付 `0.2.1-menu3`）**：B 方案
   anchor-only `MochiDropdownMenu` 已把 `MochiOverlay` 从 `DropdownCore` 拆出；
   三个 Mochi 分支改为「入口唯一可点节点 + `boundsInWindow()` 锚点上报」，
   库按钮与大圆消失、开合变成**点击松手后**，按下即开与按住滑选按用户决策放弃。
   `Icon` 锚点穿项目 `MochiBarIconButton`（与同排搜索按钮同款），`Row` 锚点仍由
   trigger 自绘；fork 侧另修了收起末段圆形遮罩淡出。**真机效果由用户调整完成**。
   本工作区补 `MochiAnchorMenuContractTest` 3 例锁住结构与依赖升级路径，
   双变体编译 + 定向 19 类 / 138 用例通过。
   遗留（不再阻塞其它簇）：四引擎 × 页面的外观基线（截图 / 语义树 / 尺寸）与
   COUI / Miuix / Material 的按钮统一；`TopBarOverflowMenu.kt:55` 仍固定
   `externalTrigger = true`，18 个页面间接走兼容路径，收编见第 3 条。
2. 按目录分批推进 51 处独立 `RoundDropdownMenu` 业务调用点（第十六轮后实测，
   设置行单选、各编辑页、阅读器浮层）；可直接套模板的零状态簇已收完，
   剩余每类需先建行为 / 外观基线。导入面板 2 处混合菜单已在第十轮改成常驻配置页，
   **入口不再需要「动作后保持展开」能力**，该缺口从剩余覆盖率里移除；
   替换规则顶栏与直链上传规则菜单已在第十一轮迁完，其中 `GroupSelector` 的
   可编辑组合框（文本框 + 建议列表）**刻意保留**，别按纯选择器处理；
   `CloudTtsScreen` 顶栏命令菜单已在第十三轮迁完；**第十七轮**把其余 5 处纯单选
   （服务商 / 模型 / 风格 / 角色 / 格式）迁到 `AppChoiceDropdown(compact = true)`，
   trigger 形态定为 `MediumTonalButton(onClick = null)` 纯展示
   （`SeriesButton` 相应支持可空 `onClick`，与 `SourceEditOptionCard(onClick = null)` /
   `GlassCard(onClick = null)` 同一约定；外观与迁移前的 Tonal 按钮一致）。
   区域菜单是混合菜单（选项 + 「自定义区域」命令 + 随后输入框），**刻意保留**。
   **第十八轮实测**：全仓 `RoundDropdownMenu(` 文本命中 50，扣除组件定义 1 与三入口内部 3，
   业务调用点 **46 处**（第十七轮减 5 后）。可零外观变化迁移的顶栏纯命令菜单当时只剩
   `AllBookmarkScreen` 1 处，已在第十八轮迁完（46 → **45**）。该轮同时确认两条约束：
   - `BookCacheManageScreen` / `BookshelfManageScreen` / `RssSortScreen` 等剩余顶栏图标菜单是
     **单选**，而 `AppChoiceDropdown` 当时只有 `Row` 形态、没有 `AppMenuAnchor.Icon` 分支
     （§3 伪代码写了 `anchor` 参数、代码没跟上，属文档超前于实现）；
   - 阅读器那一批（`ReadBookMenuBarTitle` / `ReadBookMenuBarFloating` / `ReaderBookSheet` /
     `RssReadRouteScreen`）普遍是外部受控 `expanded` 的共用组件、`CompactToolIconBox(selected)`
     高亮或伪子菜单，均非零形态变化，需先建基线；
   **第十九轮**补齐了 `AppChoiceDropdown` 的 `AppMenuAnchor.Icon`（§3 的 `anchor` 名实相符）：
   Mochi 穿 `MochiBarIconButton`（= `TopBarActionButton` 的 Mochi 实现）、COUI 穿
   `CouiTopAppBarButton`（无原生工具栏时退 `IconButton`）、Miuix 走
   `Window/OverlayIconDropdownMenu` 由库渲染 Icon 按钮并复用同一份 `DropdownEntry` 构建、
   Material 退 `IconButton`；`Icon` 优先于 `compact`，Miuix 不再落到设置行。
   首批调用方 `BookCacheManageScreen` 与 `BookshelfManageScreen` 的分组筛选（**45 → 43**）。
   `RssSortScreen` 暂缓：它的 trigger 是 `SmallToggleButton(checked = showGroupMenu)`，
   展开态高亮在纯展示 trigger 里无法保留，属形态变化；`TocScreen` 卷菜单同理且是混合菜单。
   **第二十轮**补齐 `AppActionMenu` 的 `AppMenuAnchor.Row`（此前只有 Mochi 真正生效，
   COUI / Miuix / Material 都无条件渲染图标按钮，列表项里那层壳会变）：`Row` 在四分支都由
   trigger 自己画壳，Miuix 改走 `Window/OverlayDropdownPopup` 以绕开
   `Icon*DropdownMenu` 的库按钮包装；新增 `title` 参数供 `Row` 形态声明可访问名称。
   同时 `SmallPlainButton` 支持 `onClick = null`（与 `MediumTonalButton` / `SourceEditOptionCard`
   同一约定，`SeriesButton` 底座已支持），`selected` 仍可表达业务态。
   迁移 `BookSourceScreen` 书源行动作、`CastPoolWidgets` 分组动作、`CastRoleEditing`
   角色行动作（**43 → 40**）；`CastRoleEditing` 的「删除」改为 `destructive = true`，
   Miuix 分支按既有能力降级不染色。
   同一轮继续：`SmallTonalButton` / `MediumPlainButton` 也支持 `onClick = null`；
   迁移 `BookshelfManageScreen` 行级「更多」菜单（删掉 `moreMenuBookUrl` 单例展开状态，
   行内菜单天然互斥）与 `AudioPlayScreen` 浮层顶栏的更多菜单（唤醒锁 / 媒体控制用
   `checked` 表达开关态而不是 `selected`），**40 → 38**。
   暂缓：`ThemeListContent` 的「原地翻页 + 锁宽」伪子菜单（`AppActionMenu` 的
   `children` 不等价，会改变交互）；`SelectionItemCard` 的 `dropdownContent` 槽
   （调用方提供任意内容，改 `groups` 会破 API）。
   **第二十一轮**：`CloudTtsScreen` 的区域混合菜单（第十七轮刻意保留的那处）改用
   `AppActionMenu(anchor = Row)`——`AppChoiceDropdown` 只能选、不能带命令项，而
   `AppActionMenu` 能在同一组里同时承载 `checked` 选项与「自定义区域」命令，该文件手画菜单归零；
   `HomepageModuleManageSheet` 顶栏分组筛选迁到 `AppChoiceDropdown(compact = true)`，
   空串代表「全部」以免 nullable 渗进 `AppChoiceOption.id`（**38 → 36**）。
   本轮确认两条新阻塞：
   - `VideoPlayerScreen` 的 `menuExpanded` 不只控制菜单，还参与「播放时自动隐藏控制栏」的
     `LaunchedEffect`；`AppActionMenu` 不暴露展开态（Miuix 分支由库自持），迁移会回退该行为；
   - `SettingItem` / `TinySettingItems` 虽有 `interactive = false` 的纯展示约定，但
     `dropdownMenu` 是任意内容槽（`(onDismiss) -> Unit`），改成 `groups` 属破坏性 API 变更，
     需要单独一轮连同调用方一起改；`SettingItem` 的 `dropdownMenu` 全仓只有
     `CompactSettingItems` 一个调用方。
   **第二十二轮**：设置行下拉的锚点统一到**右侧取值区**。`TinySettingItems` 原先只有 Mochi
   把菜单塞进取值 Chip（`useValueAnchor`），其余引擎挂在整行 `Box` 末尾、弹层贴行左端；
   现已四引擎统一。`SettingItem` 本来就把 `menu()` 放在 `trailing` 槽，保持不动；它的
   `useOptionAnchor` 特例（Mochi 且 `option != null`）是因为取值文本渲染在标题下方、
   锚点必须跟随取值，不是遗漏。
   真正迁移这两处仍需要给 `AppChoiceDropdown` 增加「**受控弹层模式**」
   （`expanded` + `onDismissRequest`，只渲染弹层、不包 trigger）：设置行的锚点在 trailing
   取值区，不能由入口把整行包成可点节点。四引擎的弹层容器本身都是受控的
   （Miuix `show`、Mochi `expanded`、COUI `Menu`、M3 `DropdownMenu`），该模式可表达；
   `MiuixIconChoice` 的库 Icon 弹层不支持受控，受控模式下需退回 popup 形态。
   顶栏 content 只剩 `RssSourceScreen`（伪子菜单 +
   嵌套 popup，刻意保留）。带文字标题的 4 处列表项菜单 trigger 是长按 / 图标按钮，
   需单独建交互基线，**不能复用顶栏 `menuGroups` 槽**；
   Sheet / Dialog 正文的分组标题不是菜单，不在范围。
   `ReaderMoreActionsSheet` 的 `ImageStyleDropdown` / `PageAnimDropdown`
   留作纯单选簇收尾，其 `ActionSquareHost` 共享 `expanded`、方块按钮形态复杂，
   先单独建外观与行为基线。
   **第二十三轮**：`AppActionMenu` 补齐与 `AppChoiceDropdown` 同语义的**受控弹层模式**
   （`embeddedExpanded` + `onDismissRequest`）。四分支都只读外部状态、跳过 trigger 节点；
   Mochi / COUI 分别把 `onGloballyPositioned` / `couiMenuAnchor` 从 trigger 挪到调用方容器，
   Miuix 受控优先走 `MiuixRowActions` 的 popup 形态（库 `Icon*DropdownMenu` 自持 `expanded`）。
   `VideoPlayerScreen` 顶栏菜单（`menuExpanded` 还驱动自动隐藏控制栏）与
   `ReaderBookSheet` 目录页菜单（`CompactToolIconBox(selected = menuExpanded)`）迁到受控模式，
   触发按钮与展开态高亮都由页面保留，条目改为「枚举稳定 ID + 纯映射」，**36 → 34 处文本命中
   / 32 → 30 处业务调用点**（`ReaderBookSheet` 仍有书签页 1 处旧菜单）。
   新增 `ActionMenuControlledContractTest`（9 例：三引擎行为 + 四分支结构 + 受控选择收口）
   与 `ControlledMenuMigrationContractTest`（3 例：受控参数、ID 声明/分派、棘轮）。
   行为测试暴露并顺手修掉既有 `AppChoiceDropdown` 受控模式的收口缺口：M3 `DropdownMenu` 与
   COUI `Menu` 都不会在选中后自动回调 `onDismissRequest`，此前设置行下拉在受控模式下选完不关；
   现由选中路径显式通知（非受控模式为空实现，行为不变），Miuix compact 三条 popup 路径同样显式收口。
   未验证：COUI 在 Robolectric 下退回 Material 分支，COUI 原生 `Menu` 的受控触点、
   Miuix HyperOS 4 玻璃弹层在受控模式（`anchorContent = {}`）的 morph、真机外观与 TalkBack。
   下一轮候选：入口支持长按触发（解锁 4 处长按需先建交互基线），或先补四引擎 × 5 类 trigger
   的外观基线再动「按钮所有权」迁移（余下 30 处多数属于该簇）。
   **第二十四轮**：结论修正——4 处长按需先建交互基线**不需要给入口新增能力**。盘点确认它们
   都是 `combinedClickable(onLongClick = { showMenu = true })`、锚点固定取容器矩形，
   没有一处依赖触点 `Offset` / `popupPositionProvider` / `offset{}`；第二十三轮的受控弹层模式
   本就不渲染 trigger、不包可点节点，长按节点留在调用方即可等价表达，且**不改变按钮渲染所有权**。
   因此本轮先迁零外观损失的一处：`BookInfoScreen` 书名长按菜单（2 项纯命令、单一 `Box` 锚点、
   展开/折叠动态文案），改为「常量稳定 ID + `bookInfoTitleMenuGroups()` + 受控 `AppActionMenu`」，
   `Modifier.matchParentSize()` 保持原锚点矩形，**34 → 33 处文本命中 / 30 → 29 处业务调用点**。
   新增 `LongPressMenuContractTest`（9 例）：Material / Miuix / Mochi 三引擎的
   「单击不误开、长按才开、选中经 `onDismissRequest` 收口」，以及受控入口不抢占长按手势、
   书名菜单结构与 ID 分派、剩余 3 处待迁移边界的棘轮。这条 JVM 交互基线取代了此前
   「4 处长按需先建交互基线」里可自动验证的部分。
   顺手修掉 `BatchImportConfigPageContractTest` 的过期棘轮：`f648f9302` 已把
   `BookSourceScreen` 最后一处旧菜单迁走，断言仍期望 1，现下调为 0 并注明轮次。
   另外 3 处**刻意暂缓**，因为迁移会带来外观损失、必须先有四引擎外观基线：
   - `RssScreen` / `ExploreScreen`：`PillHeaderDivider(title = 源名)` 的标题文字会丢
     （`AppActionGroup.title` 按已定案只触发分段线、四引擎都不渲染文本）；删除项的
     `destructive` 在 Material `LegacyActions` 是否落色未复核；`ExploreScreen` 还要在
     `trailingContent` 补一层 `Box` 才有 BoxScope 锚点。
   - `MainScreen` 的 `BookshelfRailGroupMenu`：`expanded` 跨 Route/Menu 两层透传、两处独立
     state 与 `if (destination == Bookshelf && showGroupMenu)` 门控都要拆；纯单选的 Check 图标
     在 Material 会降级为 `isSelected` 高亮。
   下一轮候选：补四引擎 × 5 类 trigger 的真机外观基线（含上述标题文字 / destructive 落色结论），
   再动「按钮所有权」迁移；或单独处理 `MainScreen` 的结构改造。COUI 在 Robolectric 下退回
   Material 分支，原生 `Menu` 的受控触点、四引擎长按后弹层锚点矩形与 TalkBack 仍未真机复核。
    **第二十五轮（真机回归修复）**：第二十三/二十四轮的受控模式在 COUI 上**菜单打不开**
    （播放页顶栏、目录页工具条、书名长按三处全中）。根因不是定位而是**命中测试**：
   `CouiMenuAnchor.kt` 的 `couiMenuAnchor` 内含 `pointerInput`，把它挂在
   `Modifier.matchParentSize()` 的覆盖容器上，该容器就成为命中路径里唯一的兄弟节点，
   下面那颗调用方触发按钮**再也收不到 DOWN/UP**（JVM 探针实测：同层覆盖层带 `pointerInput`
   时下层 `Button.onClick` 为 0 次）。上一轮「锚点必须挂容器否则弹层跑到窗口 (0,0)」的结论也错：
   `CouiMenuAnchor.positionInWindow` 只在 `open()` 记下触点后才有坐标，受控模式永无触点；
   而 `Popup` 的 `anchorBounds` 实测就是**父容器矩形**（`ControlledMenuAnchorContractTest`
   锁定，398×800 窗口下 `IntRect(100,200,148,248)` 与容器 bounds 一致），所以传 `anchor`
   只会得到 null、定位其实一直走的父容器矩形。
    修复：受控分支不再给容器挂 `couiMenuAnchor`，`Menu` 传 `anchor = null`
   （COUI KDoc 原文「不提供时沿用父布局定位」），`AppActionMenu` / `AppChoiceDropdown` 同改；
   定位行为不变，按钮点击恢复。代价是受控菜单失去 COUI 的触点锚定（不再从手指长出），
   与键盘 / 无障碍触发时的既有回退一致，已写进入口 KDoc。
    `ActionMenuControlledContractTest` 增 `controlledEntriesNeverPutTouchAnchorOnCallerContainer`
    （两入口源码结构棘轮，Robolectric 测不到 COUI 行为）；`SettingItemAnchorContractTest`
    的反向断言同步翻转。仍未真机复核：受控菜单在 COUI 的实际落点（容器左下 vs 触点）、
    Miuix HyperOS 4 玻璃受控 morph、TalkBack。
    **第二十六轮**：受控路径修好后继续迁移「零外观损失」的一簇——**两项纯命令 + 调用方自持
    外观/点击语义**的两处 ⋮ 菜单：
    - `ReaderBookSheet` 书签页导出（JSON / Markdown），工具按钮 `CompactToolIconBox(selected)` 的
      展开态高亮留在页面；
    - `NavIconManageSheet` 导航图标卡（替换 / 删除），卡片**未设图标时点击是直接选图**，
      这条分支不能并进开菜单。
    两处都是「常量稳定 ID + `xxxMenuGroups()` + 受控 `AppActionMenu`」，导出后缀仍按所选命令区分。
    `ReaderBookSheet` 因此归零（目录页 + 书签页都迁完），棘轮从 1 下调到 0。
    新增 `TwoItemCommandMenuContractTest`（4 例）：结构 / `trigger = {}` / 高亮与点击语义保留 /
    ID 声明与分派 / 棘轮。**29 → 27 处业务调用点**（文本命中 33 → 31）。
    其余 27 处各自带至少一项外观风险，迁移前必须先有基线：分组标题文字（`RssScreen` /
    `ExploreScreen` / `RssSourceScreen` / `TocScreen` 卷管理）、`destructive` 红字
    （`BubbleConfigScreen` 删除气泡、`LegacyActions` 目前不透传 `RoundDropdownMenuItem` 的
    `color`）、二级菜单「原地换菜单」语义（`RssReadRouteScreen` 重定向策略）、动态文案与勾选
    （`BookshelfScreen` 两处分组菜单、`RssReadRouteScreen` 朗读/停止）、锁定宽度的原地翻页
    （`ThemeListContent`）、外部注入 slot（`SelectionItemCard`、`ExploreKindMultiTypeItem`）、
    阅读器「更多」动作格（`MangaReaderSheets`、`ReadBookMenuBar*`、`ReaderMoreActionsSheet`）。
    下一轮候选：先补 `LegacyActions` 的 `destructive` 落色（解锁气泡删除等 3 处），或先建
   `PillHeaderDivider(title)` 的口径结论（解锁 RSS/发现 4 处）。
   **第二十七轮**：`PillHeaderDivider(title)` 的口径定案 + `destructive` 落地。
   口径（产品确认）：**菜单内当标题 → 直接去掉文字**（原生菜单本就不渲染分组标题，页面上另有
   常驻信息）；**纯分割 → 换成组间原生分段线**（`AppActionGroup` 自带）；**页面级分节标题**
   （书架外观设置 5 处、「添加自定义模块」对话框 1 处）标题文字是唯一分节标识，保留不动。
   按此迁 3 处：
   - `RssScreen` 源长按菜单：去掉 `PillHeaderDivider(source.sourceName)`，5 项命令；
   - `ExploreScreen` 源长按菜单：去掉 `PillHeaderDivider(item.bookSourceName)`，6 项命令，
     `trailingContent` 补 `Box` 才有 `matchParentSize` 锚点；
   - `TocScreen` 卷管理：去掉 `PillHeaderDivider(quick_jump)`，卷跳转项独立成第二组
     （组间自动补线），ID 用 `jump_volume_<state.items 下标>` 往返，跳转仍走
     `listState.animateScrollToItem`。
   两处长按菜单的删除项从「调用点写死 `color = error`」改成 `AppAction.destructive`：
   `LegacyActions`（Material）现在把 `destructive` 落成 `RoundDropdownMenuItem(color = error)`，
   经 contentColor 同时染文字与 leadingIcon；`CouiActions` 落到 `CouiMenuItem(iconTint = error)`；
   Mochi 走原生 `MochiItem(destructive)`；**Miuix 的 `DropdownItem` 没有危险色通道**（旧菜单里
   `color` 也被忽略，非新增回退）。
   已知差异：① COUI 原生菜单项只有图标能变红，文字保持菜单默认色（旧菜单在 COUI 下是 M3
   容器 + 红字）；② 菜单项图标沿用入口默认 24dp，旧菜单 `MenuItemIcon` 是 18dp（与第二十三/二十四轮
   已迁菜单一致）；③ 「快速跳转」标题文字消失（按口径）。**28 处文本命中 / 24 处业务调用点**。
   新增 `MenuHeadingDividerContractTest`（5 例）：三处结构与 `trigger = {}`、页面上仍保留源名与
   卷跳转、删除项走 `destructive`、ID 声明与分派、页面级分节标题未被误删。
   `LongPressMenuContractTest` 冻结边界从「已迁 1 / 待 3」改成「长按簇只剩 `MainScreen`」。
   下一轮候选：`BubbleConfigScreen` 气泡删除（`destructive` 已可用，仅差菜单项图标与四项命令），
   然后是 `RssSourceScreen` 导入子菜单（原地换菜单语义）与 `BookshelfScreen` 两处分组菜单
   （勾选态 + 动态分组前缀已有所需 API）。
   **第二十八轮**：最后一处手画顶栏菜单（`RssSourceScreen`）迁完，顺带把顶栏按钮壳收到一处。
   口径（产品确认）：**导入三项平铺到第一层**（和书源页 `BookSourceScreen` 一致，不再保留「点进去
   换一层」的伪子菜单）；**过滤项与分组项显示当前选中**。因此：
   - 菜单改 `menuGroups` 三段（命令 / 过滤 / 动态分组），`onMenuAction` 用 `when` 分派；
     导入项 = `import_on_line` / `import_local` / `import_default_rule`，分组项 ID 为
     `group:<名>`，「全部」用 `selected = state.groupFilter == null`；
   - 状态要能标选中：基类只存过滤**展示名**，新增 `RssSourceUiState.groupFilter` +
     `RssSourceViewModel.groupFilterKey`（`_groupFilter` 的只读流）并入 `combine`；
   - 「分组管理」原来是硬编码中文 `"分组管理"`，改 `R.string.group_manage`。
   **按钮壳统一**：`AppActionMenu` 的 `Icon` 锚点不再自绘裸 `IconButton`，新增
   `TopBarActionSurface`（`TopBarButton.kt`）—— 与 `TopBarActionButton` 共用
   `currentTopBarButtonStyle` / `SeriesButton` / 液体玻璃 / `mergedDivider` 合并态 / COUI 胶囊渲染器 /
   Mochi `MochiBarIconButton`；Mochi、COUI、Material 三个分支都改用它，Miuix 仍由
   `Icon*DropdownMenu` 自己渲染按钮（非玻璃即 `MiuixIconButton`，玻璃走 `MiuixGlassActions`）。
   副作用：已迁顶栏菜单（书源、书架、两个编辑页、RSS 排序）在 Material 下的 ⋮ 从裸 `IconButton`
   变回 `SeriesButton` 外观并重新参与合并态——这是「四引擎顶栏按钮外观统一」那条待办的收口方向。
   **27 处文本命中 / 23 处业务调用点**；`TopBarMenuIdContractTest` 的
   `onlyPseudoSubmenuKeepsTopBarContent` 改为 `noTopBarContentMenusRemain`（业务代码里
   `dropDownMenuContent = {` 归零），`MIGRATED` 加入本页。
   新增 `RssSourceMenuContractTest`（5 例）：声明式结构 / 导入平铺（每 ID 恰好声明+分派各一次）/
   过滤与分组标选中 / 状态透出过滤键 / 三引擎共用按钮壳且 Miuix 库分支未被误改；
   `MenuTriggerGlassContractTest`、`MochiAnchorMenuContractTest` 的旧断言改为「入口调壳、壳里有实现」。
   仍未真机复核：Material 下 ⋮ 的新外观与合并态、COUI 胶囊、Miuix 玻璃 morph、导入三项平铺后的
   菜单高度与滚动。
   **第二十九轮**：气泡包列表项（`BubbleConfigScreen.BubbleEntryRow`）的 ⋮ 菜单迁移。
   这处第二十七轮之前迁不了，卡的就是 `destructive` 没有落色通道——硬迁会让「删除」丢红字。
   现在调用点的 `color = LegadoTheme.colorScheme.error` 换成 `AppAction.destructive`，四项命令
   改稳定 ID（`bubble_apply` / `bubble_edit` / `bubble_export_zip` / `bubble_delete`），
   内置包只显示「应用」的门控留在 `bubbleEntryMenuGroups(isBuiltin)` 里；⋮ 换成 **SmallButton 系列**
   的 `SmallPlainButton`（→ `SeriesButton`，32dp 容器 / 18dp 图标，与列表项 trailing 图标按钮同几何；
   原来的 `AppIconButton` 是 M3/Miuix `IconButton` 直通、不属 series，在这类列表里偏大），
   开合受控（`embeddedExpanded = menuExpanded`），锚点取那层 `Box`。
   已知差异：删除项没有图标，COUI 的 `iconTint` 无对象可染 → **COUI 上删除项不再有红字**；
   Material 经 `RoundDropdownMenuItem(color = error)` 保住红字，Mochi 走原生 `destructive`，
   Miuix 一直无机型通道。**26 处文本命中 / 22 处业务调用点**。
   新增 `BubbleEntryMenuContractTest`（4 例）：受控结构与 ID、内置包门控、删除项走
   `destructive` 且调用点不再写死 error 色、入口的 destructive 落色能力不退化。
   下一轮候选：`BookshelfScreen` 两处分组菜单（勾选态 + 动态 `group:` 前缀都已具备，卡的是
   `expanded` 跨 Route/Menu 两层透传）与 `MainScreen` 书架分组菜单（同一形态）。
   **第三十轮**：书架分组菜单两处入口收敛。原先以为要拆 `expanded` 跨 Route/Menu 两层透传，
   实测它已经统一在 `BookshelfOverlay.GroupMenu` 上（`showGroupMenu = activeOverlay == ...`），
   真正的活只是把两处各自手画的菜单合并：
   - 新增 `BookshelfGroupMenu`（受控 `AppActionMenu`，`embeddedExpanded = expanded`、
     `trigger = {}`），两处各留一个 `Box(Modifier.matchParentSize())` 做锚点；顶栏那处原来就在
     `AppTabRowIconButton` 旁边，编辑态那处原来挂在摘要卡外层 `Box`，落点不变；
   - 当前分组用 `AppAction.selected` 表达（替代 `trailingIcon = Check`），ID 用
     `group:<groupId>` 往返；
   - 搜索态第二组 = `全部` + 已隐藏分组，原「全部 / 隐藏」标题按口径降级为组间线
     （`R.string.hide` 与 `PillHeaderDivider` 在本文件归零）；
   - 两处点击语义不同（编辑态还要 `SetInFolderRoot(false)`、非搜索态也要 `ChangeGroup`），
     继续放在各自的 `onSelect` 里，组件只负责 ID → `BookGroupUi` 还原。
   **24 处文本命中 / 20 处业务调用点**；`MenuHeadingDividerContractTest` 里「菜单内标题只剩
   书架 1 处」改为 0。新增 `BookshelfGroupMenuContractTest`（6 例）：单受控组件 + 两处调用、
   两个锚点容器、`selected` 与稳定 ID、标题降级、搜索态第二组、两处点击语义保留。
   下一轮候选：`MainScreen` 书架 Rail 分组菜单（结构近似，注释里提到它有 `expanded` 跨层与门控
   两处 state），然后是阅读器「更多」动作格（`MangaReaderSheets` / `ReadBookMenuBar*` /
   `ReaderMoreActionsSheet`，5+2+2 处，属高行为风险区）。
   **第三十一轮**：`MainScreen` 书架 Rail 分组菜单收尾。核查结论与上一轮预判相反：两处
   `showGroupMenu` 分属互斥布局分支（Miuix 底栏 / 宽 `WideNavigationRail`）内的局部 `remember`，
   不应合并进 `BookshelfOverlay.GroupMenu`；真正的冗余只有那层恒真的 `expanded` —— 两处调用点
   都已用 `if (destination == Bookshelf && showGroupMenu)` 门控组合。
   - `BookshelfRailGroupMenuRoute` / `BookshelfRailGroupMenu` 不再接收 `expanded`，弹层恒以
     `embeddedExpanded = true` 渲染，开合仍由页面状态 + `onDismissRequest` 收口；
   - 顺带修掉一个取消风险：四引擎受控弹层都是先 `onDismissRequest` 再 `onAction`，组件随即被
     移出组合，原先 `rememberCoroutineScope()` 里挂起的 `onBeforeSelectGroup()`（`scrollToPage`）
     会被连同协程取消，`ChangeGroup` 白点；改用导航栏宿主传入的 `CoroutineScope`。
   **23 处文本命中 / 19 处业务调用点**（上一轮此处的 24 / 20 漏记了 `MainScreen` 那 1 处）。
   `LongPressMenuContractTest` 里
   「`MainScreen` 仍留 1 处旧菜单」的棘轮下调为 0；新增 `MainScreenRailGroupMenuContractTest`
   （7 例）：单受控组件 + 两处调用、`expanded` 不再下行且门控保留、两个锚点容器、`selected`
   与稳定 ID、关闭回到调用方、点击语义留在调用点、选中跑在外部 scope 上。
   下一轮候选：阅读器「更多」动作格（`ReaderMoreActionsSheet` 5 处 / `ReadBookMenuBar*` 4 处 /
   `MangaReaderSheets` 1 处），迁前需先建行为基线。
   **第三十二轮**：阅读器动作格菜单（`ReaderMoreActionsSheet` 5 处 + `MangaReaderSheets` 1 处）
   按「先基线、再迁移」处理。先新增 `ReaderActionMenuContractTest`（当时 5 例）把现状钉住：
   三个命令分支的 intent、图片样式四项 + 选中态、翻页动画十项 + `ReadBook.book?.setPageAnim()`
   副作用、动作格「更多」通道与漫画长按 intent；确认基线跑绿后才动组件。
   - 六处全部改为受控 `AppActionMenu`（`embeddedExpanded = expanded`、`trigger = {}`、
     `Modifier.matchParentSize()` 锚在动作格矩形上），条目改用稳定 ID：
     `reader_more_change_book_source` / `reader_more_refresh_dur` 这类命令项直连一个 intent，
     `reader_more_image_style:<style>` 与 `reader_more_page_anim:<value>` 由 ID 回解成选项值；
   - 图片样式、翻页动画的选中态从 `isSelected` 改为 `AppAction.selected`；
   - 翻页动画的副作用顺序保持「先写回书籍、再通知页面」；
   - 漫画方块的长按动作只有一条，`longClickIntent == null` 时连弹层都不组合。
   **17 处文本命中 / 13 处业务调用点**（−6）。`CompactChoiceMenuContractTest` 里
   「`MangaSourceActionSquare` 仍用旧菜单」的棘轮下调为 0；`ReaderActionMenuContractTest`
   补了结构契约（共 6 例）。
   下一处：`ReadBookMenuBarFloating` / `ReadBookMenuBarTitle` 共 4 处 overflow 菜单——条目多、
   含 `titleBarCompact` 等条件项、`PillDivider` 分组与嵌套「图片样式」子菜单，需要先补四引擎
   真机外观基线，再按顶栏 overflow 形态单独设计 ID 表。
   **第三十三轮**：只做 `ReadBookMenuBarTitle` 的两处共用菜单（都满足「点完就关」的入口语义）：
   - `ReadMenuSourceDropdown`（登录 / 章节付费 / 编辑书源 / 禁用书源，三处入口共用）改为受控
     `AppActionMenu`；登录与付费是条件项，在构造 `actions` 时就过滤，不渲染空位；
   - `ReadMenuLongPressDropdown`（换源 2 项 / 刷新 2 项，顶栏 + 底栏 + 浮动图标三处共用）同样
     改为受控；`menu == null` 时不再组合弹层——旧实现这种情况下会开出一个空菜单；
   - 顺手删掉只服务旧菜单的 `ChangeSourceMenuItems` / `RefreshMenuItems` 与 `MenuItemIcon` 依赖；
   - 6 个调用点各自补 `modifier = Modifier.matchParentSize()`，锚点仍是按钮所在的 `Box`。
   **15 处文本命中 / 11 处业务调用点**（−2）。新增 `ReadMenuDropdownContractTest`（4 例）：
   书源菜单的条件项与四条分派、长按菜单的两个变体与四个 intent、两处受控结构 + 旧子组件归零、
   六处调用点的锚点实参。
   同轮核查 `ReadBookMenuBarFloating` 的 overflow 菜单（巨型菜单 35 项 + 内嵌图片样式子菜单）
   时发现它**没有调用方**：`private fun OverflowDropdownMenu` 自 2026-07-31（`7e435c1ea`）
   引入起就没被任何地方调用，`git log -S` 也只有这一个提交。既然不可达，直接删除而不迁移，
   顺带清掉它独占的 `MenuItemIcon` / `RoundDropdownMenu` / `PillDivider` 与 20 条图标 import。
   原先担心的阻塞（`reSegment` / `delRubyTag` / `delHTag` 三项「点击不关菜单」，与入口
   「先 dismiss 再 onAction」冲突）随之消失——`AppActionMenu` 仍然不需要「动作后保持展开」的
   能力，第七轮 `ToggleActionMenuContractTest` 的结论继续成立。
   **13 处文本命中 / 9 处业务调用点**（−2）；`ReadMenuDropdownContractTest` 增加第 5 例
   `deadFloatingOverflowMenuStaysDeleted`，防止这段无调用方代码被重新长出来。
   剩余 9 处：`BookInfoEditScreen` / `ReplaceEditScreen`（`ExposedDropdownMenuBox` 内的纯单选，
   应走 `AppChoiceDropdown` 而不是 `AppActionMenu`，换 trigger 会改观感，需真机基线）、
   `ThemeListContent`（原地翻页换菜单，属「不关菜单」形态）、`RssSortScreen`、
   `RssReadRouteScreen` 2 处、`FontSelectSheet`、`SelectionItemCard`、`ExploreKindMultiTypeItem`。
   **第三十四轮**：`RssReadRouteScreen` 顶栏「更多」菜单。这处是「主菜单 + 独立子菜单」形态：
   重定向策略由一个主菜单项打开 `showRedirectMenu`，选中后要同时关两层菜单，并用
   `trailingIcon = Check` 画当前值。
   - 合成一个受控 `AppActionMenu`（`embeddedExpanded = showMenu`、`trigger = {}`、锚在「更多」
     按钮的 `Box` 上），`showRedirectMenu` 删除；
   - 重定向策略改为 `AppAction.children`，当前值走 `AppAction.selected`，ID 用
     `rss_read_menu_redirect:<policy.name>` 回解；Material 无级联会平铺成同级项，属已知降级；
   - 「朗读 / 停止朗读」的文案与图标仍随 `isSpeaking` 变化，ID 固定为
     `rss_read_menu_read_aloud`；
   - 踩到一个坑值得记：`buildList` / `map` 的 lambda 不是 `@Composable`，`stringResource`
     必须在外面先求值；否则编译器报的是 `Expecting ')'` 这类语法错误，看起来像括号不配对。
   **11 处文本命中 / 7 处业务调用点**（−2）。新增 `RssReadMenuContractTest`（4 例）：单受控
   弹层 + 锚点、六个命令项回调与两个条件项、策略子项与 selected、不再手画 Check。
   剩余 7 处：`BookInfoEditScreen` / `ReplaceEditScreen`（`ExposedDropdownMenuBox` 纯单选，应走
   `AppChoiceDropdown`，需真机基线）、`ThemeListContent`（原地翻页换菜单）、`RssSortScreen`
   （`TopBarActionMenu` 形态，已有 `RssSortMenuContractTest` 守着）、`FontSelectSheet`、
   `SelectionItemCard`、`ExploreKindMultiTypeItem`。
   **第三十五轮**：`FontSelectSheet` 的系统字体菜单（`startAction` 槽里的按钮 + 菜单）。
   - 改为受控 `AppActionMenu`，用 `Box` 包住 `MediumTonalButton` 做锚点；条目由传入的
     `Array<String>` 生成，ID 用序号（字体名会重名），回解后按 `typefaces.indices` 校验；
   - 选中后仍是「先 `onSelectSystemTypeface(index)`，再 `onDismissRequest()` 关整个 sheet」；
   - **已知差异**：旧实现用 `ProvideAppDensity` 包住菜单项，避免系统大字号/高密度把菜单项撑大；
     受控弹层的内容由组件内部渲染，这层包裹用不上了。真机若出现字号异常，应在 `AppActionMenu`
     侧补密度钩子，而不是在调用点另开菜单。
   **9 处文本命中 / 5 处业务调用点**（−1）。新增 `FontTypefaceMenuContractTest`（3 例）。

   **第三十六轮**：`RssSortScreen` 底部的分组切换菜单——`RssSortMenuContractTest` 里长期保留的
   那处棘轮（`SmallToggleButton(checked = showGroupMenu)` 既是开关又是入口）。
   - 按钮仍只负责写 `showGroupMenu`，弹层改由 `embeddedExpanded` 驱动，`trigger = {}`；
   - 分组项 ID 用序号（分组标题会重名），当前分组由 `AppAction.selected` 表达，不再手画
     `Icon(Icons.Default.Check)`，顺带清掉 `size` / `Icon` / `Check` 三个导入；
   - 选中后 `scope.launch { pagerState.animateScrollToPage(index) }` 不变。
   `groupSwitcherKeepsRatchet` 下调为 0 并改名 `groupSwitcherIsControlledMenu`。
   **9 处文本命中 / 5 处业务调用点**（−1）。
   剩余 5 处：`BookInfoEditScreen` / `ReplaceEditScreen`（`ExposedDropdownMenuBox` 纯单选，应走
   `AppChoiceDropdown`）、`ThemeListContent`（原地翻页换菜单）、`SelectionItemCard`
   （`dropdownContent` 插槽，属组件 API，改动会影响所有用到它的卡片）、
   `ExploreKindMultiTypeItem`。
   **第三十七轮**：`ExploreKindMultiTypeItem` 的分类选择菜单（`showSelector` + `chars` 动态列表）。
   - 改为受控 `AppActionMenu`，锚点是已有的 `Box(modifier)`；ID 用序号（选项文本可能重复），
     回解后按 `chars.indices` 校验；
   - 选中后的 `state.updateValue(...)` + `onRunAction()` / `state.executeAction(kind.action)`
     与 `if (selected != option)` 去重逐条保留；
   - 旧实现**没有**选中态标记，迁移时也不额外加 `selected`，避免顺手改观感。
   **8 处文本命中 / 4 处业务调用点**（−1）。新增 `ExploreKindSelectorMenuContractTest`（2 例）。
   剩余 4 处业务调用点：`BookInfoEditScreen` / `ReplaceEditScreen`（`ExposedDropdownMenuBox`
   可编辑组合框，`AppChoiceDropdown` 只能选不能输入，硬迁会丢自由填写能力）、
   `ThemeListContent`（点「网格列数」原地翻页换菜单，属「不关菜单」形态）、
   `SelectionItemCard`（`dropdownContent` 插槽是组件 API，改动会波及所有用到该卡片的列表）。
   ⚠️ 这 4 处**都不是套模板能收的**：前两处要改交互或补能力，第四处改的是公共组件契约。
   下一轮建议先挑一类建行为基线 + 四引擎真机外观基线，而不是继续减计数。
   **第三十八轮**：`ThemeListContent` 顶行 ⋮ 的「原地翻页」菜单（三选一取 B 类）。迁前先建
   行为基线：`ThemeListMenuContractTest` 5 例钉住三条命令项的 dismiss→intent、「网格列数」
   只翻页不 dismiss、2..6 列数项的选中与写回、关闭时翻页位与冻结宽度归零；既有
   `ThemeListSelectionTest`（⋮ → 管理主题 → 勾选 → 分享）作 Compose 交互基线，跑绿后才动组件。
   - 收编为 `AppAnchoredPanel(externalTrigger = true)` + 自定义 content：入口只接管弹层分派，
     content 自己决定是否 `dismiss()`，因此保留「不关菜单」的翻页语义，**不需要**给
     `AppActionMenu` 补 keepOpen（第十轮 / 第三十三轮结论继续成立）；
   - ⋮ 按钮仍由本页用 `SmallTonalButton` 渲染，点击先回到一级菜单再 `open()`；
     `externalTrigger` 路径的 `modifier` 仍传宽度锁定 + `onSizeChanged`，一二级菜单共用
     冻结宽度、关闭即作废的行为不变；
   - `onExpandedChange(false)` 一处收口 `menuExpanded` / 翻页位 / 冻结宽度三个重置，
     等价于旧 `onDismissRequest`；三个命令项与五个列数项的回调逐条保留。
   **7 处文本命中 / 3 处业务调用点**（−1）。`ThemeListMenuContractTest` 补结构契约共 6 例
   （手画容器归零、单入口、既有 trigger、无 keepOpen），`menu.*` 定向全绿，
   `ThemeListSelectionTest` 1 例通过。
   取舍：选 `AppAnchoredPanel` 自定义 content 而不是受控 `AppActionMenu` 或第十轮式
   「拆独立面板」，是为了零交互变化、零按钮外观变化，且不动阅读器面板布局；代价是
   content 里仍是 `RoundDropdownMenuItem` 自绘行，四引擎原生条目效果不因此提升。
   剩余 3 处：`BookInfoEditScreen` / `ReplaceEditScreen`（`ExposedDropdownMenuBox` 可编辑
   组合框，需真机外观基线 + 保留自由输入）、`SelectionItemCard`（`dropdownContent` 公共
   契约，6 个调用点）。四引擎真机外观、TalkBack、lint 仍未复核。
   **第三十九轮**：`SelectionItemCard` 的 `dropdownContent` 公共插槽（三选一取 C 类）。先盘点
   6 个调用点（`ChangeSourceSheet` / `RuleSubScreen` / `RssFavoritesScreen` /
   `ReplaceRuleScreen` / `AiAutoGroupSheet` / `ChangeChapterSourceSheet`），结论是插槽签名
   `@Composable (ColumnScope.(onDismiss) -> Unit)?` 与 `AppAnchoredPanel.content` 同构，
   **不需要改公共 API**。新增 `SelectionItemCardMenuContractTest` 4 例先钉住插槽、两处转发、
   6 个调用点与 dismiss 语义；既有
   `MochiOverlaysAndBarsTest.hyperOs4RuleItemMenuUsesButtonParentAsGlassAnchor`
   作 Miuix 玻璃锚点交互基线，跑绿后才动组件。
   - 卡片内部改为 `AppAnchoredPanel(externalTrigger = true)`，`content = dropdownContent`
     直接复用插槽，`onExpandedChange = { showMenu = it }` 一处收口开合；`dismiss()` 仍由
     调用点触发，等价于旧 `dropdownContent { showMenu = false }`；
   - ⋮ 仍由卡片用 `SmallPlainButton` 渲染（外观与 `moreContentDescription` 不变），点击改为
     `open()`；`SelectionItemCard` / `SelectionItemCardContent` / `ReorderableSelectionItem`
     三个对外签名与两处转发逐字不动，6 个调用点零改动。
   **6 处文本命中 / 2 处业务调用点**（−1）。双变体编译 `BUILD SUCCESSFUL`，
   `menu.*` 定向全绿、玻璃锚点 1 例通过，`git diff --check` 干净。
   取舍：不套 `AppActionMenu`（插槽里的菜单项形态各异、由调用点自绘），也不改公共 API，
   只把弹层容器分派收编；代价与第三十八轮相同，四引擎原生条目效果不因此提升。
   剩余 2 处：`BookInfoEditScreen.BookTypeDropdown` / `ReplaceEditScreen.GroupSelector`
   （`ExposedDropdownMenuBox`，需四引擎真机外观基线且 `GroupSelector` 自由输入必须保留）。
   四引擎真机外观、TalkBack、lint 仍未复核。
   **第四十轮**：最后两处 `ExposedDropdownMenuBox` **只建行为基线、不动组件**。新增
   `ExposedDropdownMenuBaselineContractTest` 5 例，钉住：两处各 1 处手画弹层、
   `PrimaryEditable` 锚点 + `TrailingIcon`、书类型只读文本框与 `TextFieldState` 标签同步、
   `GroupSelector` 的 `value/onValueChange` 自由输入、两处选中即回填并收起；反向棘轮断言
   两处都**没有**被换成 `AppChoiceDropdown`（只能选不能输入），与第十一轮起
   `ReplaceDirectLinkMenuContractTest.groupSelectorKeepsEditableComboBox` 同口径。
   `menu.*` 定向全绿，**6 处文本命中 / 2 处业务调用点不变**。
   暂不迁移的原因：换 `AppChoiceDropdown(compact = true)` 会改 trigger 外观且丢自由输入；
   沿用第三十八/三十九轮的 `AppAnchoredPanel(externalTrigger = true)` 会让
   `ExposedDropdownMenuBox` 的 `menuAnchor` 与入口的 `expanded` 变成双层状态，弹层落点
   不再由 Material 的 exposed 定位决定。两种改法都需先有四引擎真机外观基线。
   **待用户提供的真机基线**：Material / COUI / Miuix / Mochi 四引擎下分别打开
   「书籍信息编辑 → 书类型」与「替换规则编辑 → 分组」，各取收起态 + 展开态截图；
   观察 trigger（输入框底色/圆角/标签、下拉箭头、只读 vs 光标）、弹层（是否贴输入框
   下沿、宽度是否等于输入框、背景/圆角/行高/选中态）、交互（点输入框/箭头展开、
   点条目收起、`GroupSelector` 输入不在 `allGroups` 里的新分组名后保存并重开是否保留、
   软键盘）。待用户决策：① 是否允许 `BookTypeDropdown` 改 trigger 形态；
   ② 是否接受「trigger 逐字不动、只收编弹层分派」但弹层落点未真机验证的风险；
   ③ `GroupSelector` 自由输入的验收口径。真机外观、TalkBack、lint 仍未复核。
   **第四十一轮**：用户确认四引擎**现状**外观全部正常，并指出下拉行不是各引擎原生 item；
   `GroupSelector` 的验收口径收窄为**页面内自由输入**，不需要「退出后重进仍保留分组」
   （那是 ViewModel / 持久化职责）。第四十轮里第 ③ 项待办以此为准，基线测试无需改
   （未断言跨页面持久化）。本轮只研究 `BookTypeDropdown` 的处理办法、不动组件，
   计数仍 **6 处文本命中 / 2 处业务调用点**。
   - 现状核实：`RoundDropdownMenu` 容器按引擎分派（Miuix `WindowListPopup`、Mochi
     `AppMochiPopover`、COUI/Material `DropdownMenu`），但行统一是 `RoundDropdownMenuItem`
     的四套**自绘**实现（Miuix `MiuixText`+Row，Mochi / COUI / Material 各自 `Text`/`Surface`），
     所以「不是原生 item」成立；要拿原生行必须走声明式三入口。
   - 方案 1（保真收编，第三十八/三十九轮同构）：`AppAnchoredPanel(externalTrigger = true)`
     只换容器，行仍自绘 → **不解决原生 item**，且 `menuAnchor` 与入口 `expanded` 双层状态。
   - 方案 2（`AppChoiceDropdown` 默认 / `Icon` 形态）：四引擎原生行（COUI `CouiMenuItem`、
     Miuix `DropdownItem` / HyperOS4 玻璃行、Mochi `MochiItem`），但 trigger 变成设置行 /
     图标按钮，输入框、标签同步与展开箭头全部消失，外观改动最大。
   - 方案 3（`AppChoiceDropdown` 受控 `embeddedExpanded` + `trigger = {}` + 调用方
     `matchParentSize()` 锚点）：**推荐**。保留 `ExposedDropdownMenuBox` + `AppTextField`
     作为 trigger 逐字不动，弹层换四引擎原生行；ID 用 `BookInfoEditType.name`，回解后按
     枚举校验。代价：受控模式失去 COUI 触点锚定，弹层宽度/落点改由各库决定；
     `ExposedDropdownMenuBoxScope` 不提供 `BoxScope`，需在 content 内再嵌一层 `Box`
     才能用 `matchParentSize()`，`menuAnchor` 是否仍被 Material 正确测量需编译 + 真机确认。
   - 方案 4（改用 Material3 原生 `ExposedDropdownMenu`）：四引擎统一退成 M3 风格，
     违反「完整保留四套原生效果」，排除。
   - 方案 5（改公共 `RoundDropdownMenuItem`，按引擎委托库原生行）：波及全仓 320+ 处
     调用点，属根因级重构、不是本切片，排除。
   - 共同边界：Material 分支无论走哪个入口都是 `RoundDropdownMenuItem` 自绘行
     （`MaterialChoice` / `LegacyActions` 现状如此），M3 引擎没有「库原生 item」可拿。
   下一步需用户从方案 2/3 里定一个，再按「先补结构契约 → 迁移 → 复核迁移后四引擎外观」
   收掉 `BookTypeDropdown`（6/2 → 5/1）；`GroupSelector` 仍不能换纯选择器（页面内自由
   输入必须保留），留到再下一轮。TalkBack、lint 仍未复核。
   **第四十二轮**：用户追问「把按钮移出输入框、放在旁边能否规避方案 ③ 的代价」。结论：
   **能规避大部分技术代价，但把风险换成外观/交互改动**；本轮仍不动组件，计数 6/2。
   - 形态：`Row { AppTextField(state, readOnly, label, weight(1f)); AppChoiceDropdown(
     options, selectedId, onSelect, title, compact = true / anchor = Icon, trigger = { 纯展示按钮 }) }`，
     去掉 `ExposedDropdownMenuBox` 与 `menuAnchor`。
   - 可规避：不再有 `menuAnchor` + 入口 `expanded` 双层状态；不必把 `AppTextField` 塞进
     `trigger` 槽（语义树仍是单可点节点，`AppChoiceIconAnchorContractTest` 已覆盖）；
     可用**非受控**入口 ⇒ COUI `couiAnchor.open` 触点锚定恢复、Miuix 玻璃 morph 从按钮
     长出、Mochi 锚点面板；不再需要 `embeddedExpanded` / `matchParentSize()` / 嵌套 `Box`
     （`ExposedDropdownMenuBoxScope` 不提供 `BoxScope` 的问题消失）。
   - 仍拿到 COUI `CouiMenuItem` / Miuix `DropdownItem` / Mochi `MochiItem` 原生行；
     Material 分支依旧 `RoundDropdownMenuItem` 自绘（M3 没有库原生 item）。
   - 新增代价：输入框变窄、箭头从框内移出、**点输入框不再能展开**（原来点框内任意处
     都开菜单）、只读框与按钮的无障碍名称/当前值要重新组织；弹层落点从「框下方」变成
     「按钮下方」，宽度由各库定（`RoundDropdownMenu` 现状本就是内容自适应，并不贴输入框宽）；
     迁移后四引擎外观仍需复核。
   - 锚点取舍：`anchor = Icon` 由各库渲染按钮壳（Mochi `MochiBarIconButton`、COUI
     `CouiTopAppBarButton`、Miuix `Window/OverlayIconDropdownMenu`、Material `IconButton`），
     四引擎按钮外观不同；`compact = true`（Row）由入口包同一个项目自绘
     `SmallTonalButton(onClick = null)`，外观统一且 `MiuixCompactChoice` 已走
     `MiuixGlassMenuHost`。推荐后者。
   - ⚠️ 文档差异：`AppChoiceDropdown` KDoc 与本文「下一阶段」第 4 条仍写「`Icon` 与
     `compact` 同属暂不接玻璃」，但源码 `MiuixCompactChoice` 已有玻璃分支；以源码为准，
     该文案待单独更正，本轮不改。
   需用户确认：是否接受「点输入框不再展开 + 旁边多一个按钮」这一观感/交互改动。确认后
   按「先补结构契约 → 迁移 → 迁移后四引擎复核」收掉 `BookTypeDropdown`（6/2 → 5/1）。
   **第四十三轮**：用户定案「`BookTypeDropdown` 直接用 `SettingItem`，替换净化的分组框
   在输入框旁放 Medium 系列按钮」。本轮先落第一处，计数 **6/2 → 5/1**。
   - `BookTypeDropdown` 改为 `SettingItem(choiceOptions = bookTypes.map { (type, label) ->
     AppChoiceOption(id = type.name, label = label) }.toImmutableList(),
     choiceSelectedId = selectedType.name, onChoiceSelect = { id -> ... })`；标题沿用
     `R.string.book_type_label`，`option` 显示当前类型名，保留原 8.dp 外边距，函数签名
     与调用点不变。
   - 删掉 `ExposedDropdownMenuBox` / `menuAnchor` / `TrailingIcon` /
     `rememberTextFieldState` + `LaunchedEffect` 标签同步 / 手画 `RoundDropdownMenu`；
     `SettingItem` 内部用受控 `AppChoiceDropdown(embeddedExpanded = showMenu)` +
     `matchParentSize()` 锚点，四引擎原生单选行（Material 分支仍项目自绘）。
   - ID 用枚举名、回解按 `bookTypes` 值校验，不依赖列表下标；`map` lambda 非
     @Composable，标签由调用点现成 String 提供，坑①不会发生。
   - 下调第四十轮棘轮：`ExposedDropdownMenuBaselineContractTest` 改 5 例（只管
     `GroupSelector`，并断言编辑页已无手画容器）；新增 `BookTypeSettingItemContractTest`
     4 例（声明式形态、稳定 ID、非 Composable 构造、组件级原生行来源）。
   验证：双变体编译 `BUILD SUCCESSFUL`；`menu.*` 定向全绿（新契约 4 例、基线 5 例
   0 失败）；`git diff --check` 干净；3 个改动文件 0 诊断。
   下一片按用户定案：`GroupSelector` 保留 `AppTextField` 自由输入，去掉 exposed 锚点，
   在输入框旁放一个 **Medium 系列纯展示按钮**（`MediumPlainButton(onClick = null,
   modifier = this.modifier, ...)`，由入口包唯一可点节点）作为 `AppChoiceDropdown` 的
   trigger；自由输入值不在 `allGroups` 时 `selectedId` 落空，已核实 Mochi `MochiList`
   用 `selectedIndex in items.indices`、库多处用 -1 表示无选中，不会崩。现有「管理分组」
   `MediumPlainButton` 保留。迁移后四引擎外观、TalkBack、lint 仍未复核。
   **第四十四轮（阶段 C 收尾）**：`GroupSelector` 按用户定案改造，计数 **5/1 → 4/0**。
   - 去掉 `ExposedDropdownMenuBox` / `menuAnchor` / `TrailingIcon` / 手画
     `RoundDropdownMenu`；`AppTextField(value = currentGroup,
     onValueChange = onGroupChange)` 连同 label / placeholder("默认") /
     backgroundColor / `weight(1f)` 逐条保留，页面内自由输入能力不变。
   - 输入框旁新增 `AppChoiceDropdown(compact = true, title = R.string.group)`，
     trigger 用 `MediumPlainButton(onClick = null, modifier = this.modifier,
     icon = AppIcons.ExpandMore, contentDescription = R.string.expand)` 纯展示，
     由入口包唯一可点节点；透传 `this.modifier` 是 COUI 触点锚点的必要条件
     （`SettingItemAnchorContractTest.pureDisplayTriggersForwardScopeModifier`）。
   - ID 用带前缀的序号（`GROUP_OPTION_ID_PREFIX` + index），回解 `removePrefix` →
     `toIntOrNull` → `getOrNull` → `onGroupChange`；`selectedId` 用
     `indexOfFirst(...).takeIf { it >= 0 }`，用户自由输入的新分组不在 `allGroups`
     时落空（第四十三轮已核实 Mochi 以 `selectedIndex = -1` 表示无选中，不崩不误高亮）。
   - 现有「管理分组」`MediumPlainButton(onClick = onManageClick)` 保留在右侧。
   - 下调两处棘轮：`ReplaceDirectLinkMenuContractTest.groupSelectorKeepsEditableComboBox`
     改为 `groupSelectorKeepsFreeTextInputWithChoiceTrigger`（手画容器 0 处、无
     `PrimaryEditable`、自由输入仍在）；`ExposedDropdownMenuBaselineContractTest` 改为
     5 例阶段 C 收尾契约（两文件 0 处手画、自由输入、Medium 纯展示 trigger、
     稳定 ID + 边界校验、管理按钮）。
   验证：双变体编译 `BUILD SUCCESSFUL`；`menu.*` 定向全绿（收尾契约 5 例、
   下调后的 `ReplaceDirectLinkMenuContractTest` 3 例、`BookTypeSettingItemContractTest` 4 例
   均 0 失败）；`git diff --check` 干净；3 个改动文件 0 诊断。全仓
   `RoundDropdownMenu(` = 4 处（组件定义 1 + 三入口内部 3），**业务调用点归零**。
   一处测试小坑：Kotlin 原始字符串仍会插值 `$`，断言字面量 ID 模板需用 `${'$'}` 转义。
   未验证：迁移后四引擎展开态外观（输入框变窄、点输入框不再展开、弹层改锚到按钮）、
   TalkBack、lint；`GroupSelector` 按用户口径不验「退出后重进仍保留分组」。
   **第四十五轮（第四十三轮回归修复，计数仍 4/0）**：用户真机反馈「书籍信息编辑的书类型
   下拉没跟随主题、弹窗飘到左上角」。先补 `choiceAnchorAndSurfaceFollowEditPage` 契约、
   跑出 2 例红建立证据，再修，跑回 5 例全绿。
   - 根因 1：`SettingItem` 的 `trailing` 槽是 `Box(contentAlignment = Center)`，里面只有
     `trailingContent?.invoke()` 与 `menu()`；编辑页用法没传 `trailingContent`，非 COUI 下
     `trailingOption` 为 null、无 `expandContent`，Box 没有撑尺寸的可见子节点。
     `menu()` 里的 `AppChoiceDropdown(modifier = Modifier.matchParentSize())` 因此是 0 尺寸，
     受控弹层（M3 `DropdownMenu` / COUI `Menu(anchor = null)` / Mochi
     `onGloballyPositioned` / Miuix popup）拿到窗口原点 → 左上角。
   - 根因 2：`SettingCard` 默认 `color ?: MaterialTheme.colorScheme.surfaceContainerLow`，
     `option` 取值文本写死 `MaterialTheme.colorScheme.primary`；`SettingItem` 是设置页组件，
     单独塞进 `BookInfoEditContent` 的 `Column`，与 `AppTextField` 的
     `LegadoTheme.colorScheme.surfaceInput` 不一致 → 「没跟随主题」。
   - 修复**只改调用点、不动公共组件**：`color = LegadoTheme.colorScheme.surfaceInput`；
     去掉 `option`，改由 `trailingContent` 用 `AppText`（`LegadoTheme.colorScheme.onSurface`）
     + `AppIcon(AppIcons.ExpandMore)` 显示当前值与箭头。trailing Box 因此有确定尺寸，且
     `option = null` ⇒ `useOptionAnchor = false`、`trailingOption = null`，四引擎锚点统一
     落在右侧取值区。
   - 契约：`BookTypeSettingItemContractTest` 新增回归用例共 5 例（trailingContent、页面
     底色、箭头、不再用 option 渲染取值），并更新「显示值跟随 selectedType」断言。
   验证：修复前 2 例红；修复后双变体编译 + `menu.*` 定向 `BUILD SUCCESSFUL`（5 例
   0 失败），`git diff --check` 干净、2 个改动文件 0 诊断，计数仍 4/0。
   ⚠️ 长期陷阱：`SettingItem(choiceOptions = ...)` 依赖调用点给 `trailing` / `option`
   一个可见节点；设置页里另两处（`ListSettingItem` / `CompactSettingItems`）有分组上下文
   所以正常。非设置页复用该组件时必须补 `trailingContent`，否则受控弹层锚点退化。
   仍需用户真机复核：`SettingCard` 的 4.dp 圆角、`titleMedium` 字号与编辑页其它字段
   是否协调；若仍不搭，再考虑换成 `AppChoiceDropdown(compact)` + 编辑页风格 trigger。
   TalkBack、lint 未复核。
   **第四十六轮（设置项组件配色统一，计数仍 4/0）**：用户追问「为什么设置页的这些没有
   问题」，并要求改用 `LegadoTheme.colorScheme` 系列色。
   - 更精确的解答（更正第四十五轮「有分组上下文所以正常」的笼统说法）：
     · `CompactSettingItems` 的 `SettingItem` 传了 `trailingContent = valueChip`（TextCard），
       `trailing` Box 天然有尺寸 ⇒ 受控弹层锚点正常；
     · `ListSettingItem` 传了非空 `option = currentEntry`：COUI 下 `trailingOption` 渲染
       取值文本、Mochi 下 `useOptionAnchor = true` 把 `menu()` 挂到 supporting 的取值文本
       Box，两处都有可见节点；Miuix 走库原生 spinner 分支（`when` 里只有
       Material / Mochi / Coui 才用 `SettingItem`），根本不经过这条路径；
     · 设置页整页是 `SettingCard` + 分组上下文（`LocalMochiSettingsGroup` /
       `LocalCouiSettingsGroup` / `SplicedColumnDivider`），所有行同一套底色，视觉自洽；
       编辑页只有一行 `SettingItem` 夹在 `AppTextField`(`surfaceInput`) / `NormalCard`
       之间，两套取色并排才显眼。编辑页的 `Column` 还带
       `verticalScroll + imePadding + consumeWindowInsets`，0 尺寸锚点更易退化到窗口原点。
     · ⚠️ 但 `ListSettingItem` 在 **Material 分支**下同样是「`option` 在 supporting、
       `trailing` 空 Box」，理论上也有锚点退化隐患，只是没被观察到；本轮未改它
       （会影响全仓设置页），列为后续项。
   - 改动：`SettingItem` 4 处、`CompactSettingItems` 3 处、`ClickableSettingItem` 1 处
     `MaterialTheme.colorScheme.*` → `LegadoTheme.colorScheme.*` 同名系列色
     （`onSurfaceVariant` / `onSurface` / `primary` / `surfaceContainerLow` /
     `surfaceContainer`），删掉 3 个不再使用的 `MaterialTheme` import。
     `LegadoColorScheme` 已提供全部同名色，不需要新增色值。
   - 契约：新增 `SettingItemColorContractTest` 2 例（组件族不再混用 M3 取色、替换落到
     同名系列色），先跑出 2 例红再修。
   验证：双变体编译 + `menu.*` 定向 `BUILD SUCCESSFUL`（新契约 2 例 0 失败），
   `git diff --check` 干净、4 个改动文件 0 诊断，settingItem 包残留 0 处，计数仍 4/0。
   ⚠️ 影响面：这是**全仓设置页配色变化**（M3 默认取色 → 项目主题色），需真机复核四
   引擎下设置页与编辑页观感；TalkBack、lint 未复核。
   **第四十七轮（编辑页布局调整，计数仍 4/0）**：用户要求书类型下拉不再与封面同一行，
   改放到「固定书籍类型」上方。
   - `BookTypeDropdown` 调用点从封面 `Row` 右侧的 `Column`（原为两个按钮 +
     `Spacer(4.dp)` + 下拉）移到 `BookKnowledgeEditCard` 之后的整行，紧跟
     `SwitchSettingItem(R.string.fixed_book_type)` 上方，两者之间 `Spacer(8.dp)`；
     封面行内那个 `Spacer(4.dp)` 一并删掉。
   - `SettingItem` 的 `modifier = Modifier.padding(horizontal = 8.dp)` 去掉：那是为封面
     右侧窄列留的缩进，独立整行后会比下方设置项左右各内缩 8dp；现在由
     `SettingCard.fillMaxWidth()` 撑满，与 `SwitchSettingItem` 左边缘对齐。
   - 契约：`BookTypeSettingItemContractTest` 加 `bookTypeDropdownSitsAboveFixedTypeSwitch`
     共 6 例（封面 `CoilBookCover` 与知识卡片之间不再有书类型、调用点位于知识卡片之后
     且早于 `R.string.fixed_book_type`、两者间 8.dp 间距），并把「保留原有外边距」断言
     反向改为「不得保留」。先跑出 2 例红再改。
   验证：双变体编译 + `menu.*` 定向 `BUILD SUCCESSFUL`（6 例 0 失败），
   `git diff --check` 干净、2 个改动文件 0 诊断，计数仍 4/0。
   ⚠️ `SettingItem` 现在是整行宽度，四引擎下需真机复核卡片宽度 / 与固定书籍类型开关的
   间距；TalkBack、lint 未复核。
   **第四十八轮（受控玻璃锚点回归定位与修复，计数仍 4/0）**：用户实测 OnePlus /
   Android 17 / SDK 37 下，在「发现 / 订阅」打开源长按菜单后关闭会抛
   `IllegalArgumentException: Corner size in Px can't be negative(-0.33347267…)`，
   且弹窗一直在窗口左上角；要求定位是哪次修改引入的。
   - 根因（两个现象同源）：`MiuixGlassMenuHost` → `GlassIconDropdownMenu` →
     `GlassTransformPopup` 取 `startRect = anchor.containerBounds`、
     `startRadius = anchor.cornerRadius`；两者都由 `Modifier.glassPopupAnchor` 的
     `onGloballyPositioned` 写入，默认是 `Rect.Zero` / `0.dp`。
     `AppActionMenu.MiuixRowActions` 支持 `embeddedExpanded` 后，把带锚点的 trigger Box
     收进 `if (embeddedExpanded == null)`，受控分支仍注册 host，面板于是从 (0,0) morph；
     收起时 `transformFrame` 的 `startRadius + (endRadius - startRadius) * sizeFraction`
     在 startRadius=0、spring 回弹让 sizeFraction 略小于 0 时得到负圆角，
     `MeasuredCorner.toPx()` 未钳制，`RoundedCornerShape` 直接抛异常。
   - 引入点：`2c4d0856b`（2026-11-12 01:29）新增 host 与玻璃 morph 路径，同时给
     `AppChoiceDropdown` 受控分支加了 `containerAnchor`，但当时 `MiuixRowActions`
     只支持非受控（`show = expanded && enabled`），缺陷潜伏；工作区未提交的
     「`AppActionMenu` 受控模式」改造让 `MiuixRowActions` 读 `isOpen`、跳过 trigger Box
     却没补容器锚点，叠加第二十七轮把 `RssScreen` / `ExploreScreen` 长按菜单迁成
     `AppActionMenu(embeddedExpanded = …)`，首次命中「受控 + 玻璃 + Row」。
     迁移前这两页走 `RoundDropdownMenu` 的 `GlassDropdownPopup` +
     `anchorBounds.translate(-origin)`，有真实父矩形，所以「之前不会这样」。
   - 修复（两处，不改任何业务调用点）：`MiuixRowActions` 增加
     `containerAnchor = if (glass && embeddedExpanded != null)
     Modifier.glassPopupAnchor(anchor, cornerRadius = 12.dp)`，容器改
     `Box(modifier.then(containerAnchor))`，与 `AppChoiceDropdown` 同口径；vendored
     `GlassPopupSurface.MeasuredCorner.toPx()` 加 `coerceAtLeast(0f)` 作形状边界兜底，
     动画插值本身不改（以后再有入口漏挂锚点只会位置错，不会再崩）。
   - 契约：新增 `ControlledGlassAnchorContractTest` 3 例（受控容器发布 bounds + 起点
     圆角、两入口同口径、库侧钳制且插值仍在）。先定位到崩溃数值与插值公式吻合，
     再改源码，用例锁住修复后形态。
   验证：`:app:compileAppCoreDebugKotlin` / `:app:compileAppComicDebugKotlin` /
   `:app:testAppCoreDebugUnitTest --tests '…menu.*'` 同批 `BUILD SUCCESSFUL`（新增 3 例
   0 失败，含 vendored `miuix-glass:compileAndroidMain`），`git diff --check` 干净、
   3 个改动文件 0 诊断，计数仍 4/0。
   ⚠️ 未复核：真机上「发现 / 订阅」弹层位置与收起动画、其余受控 `AppActionMenu`
   （阅读器 Sheet / 字体 / 目录 / 视频页等）的玻璃锚点尺寸；`MiuixGlassMenuHost`
   仍不减 `origin`，当前 `AppGlassOverlayLayer` 位于 root 原点（与 `RoundDropdownMenu`
   的 `origin == Offset.Zero` 条件一致），若以后 overlay 带偏移需统一处理。
   `testAppComicDebugUnitTest` / lint / assemble / TalkBack 未跑（第四十九轮已补跑，见下）。
   **第四十九轮（撤销库侧兜底 + 主验证集 + 文档清理，计数仍 4/0）**：用户真机复核
   「发现 / 订阅」弹层落点与关闭崩溃均通过，决定撤掉库侧兜底，并授权补跑主验证集、
   清理过时文档。
   - 撤销：`modules/miuix` 是 gitlink submodule（`160000 ea1b935d`），第四十八轮加在
     `GlassPopupSurface.MeasuredCorner.toPx()` 的 `coerceAtLeast(0f)` 只落在子仓工作树，
     主仓 `git diff --stat` 仅显示 `modules/miuix | 0`，提交后 CI / 他人 clone 拿不到。
     已恢复 vendored 原版、子仓工作树干净；唯一防线是 `AppActionMenu` 受控容器
     `containerAnchor` 写入的正起点圆角（12.dp），插值不再越过 0。
   - 契约：`ControlledGlassAnchorContractTest` 第三例改为
     `glassPanelKeepsVendoredRadiusUnpatched`，反向断言 `MeasuredCorner` 内不含
     `coerceAtLeast`、正断言仍用上游原式与插值公式。第一版按整文件断言会误报
     （同文件阴影 outset 本就有 4 处 `coerceAtLeast(0f)`），已收紧到该类。
   - 文档清理：`AppChoiceDropdown.kt` 三处 KDoc 的「compact 暂不接玻璃」改成
     「compact 已走 `MiuixGlassMenuHost`、只剩 Icon 未接」；本文下一阶段第 4 条同步更正；
     `native-menu-engines-handoff.md` / `-next.md` 两份快照标为已归档（51 处、8/4
     口径与「下一轮三选一」全部作废，指向本文顶部与 §9）。
   验证（主验证集，双变体）：
   - 全量单测：首次 core `3261 tests / 10 failed / 4 skipped`、comic
     `3294 / 10 failed / 1 skipped`；修掉本轮契约误报后 core 复跑
     `3261 / 9 failed / 4 skipped`，两变体 `ControlledGlassAnchorContractTest` 3 例全绿。
     剩余 9 个失败集中在 `MangaExportWriterTest` / `MangaExportRepositoryTest` /
     `LocalMangaLoaderTest` / `MochiOverlaysAndBarsTest`，均为文档已登记的
     漫画 / insets 既有失败（`URISyntaxException`、overflow-tabs `ScrollToIndex`），
     与菜单锚点无代码交集，未在本轮处理。
   - `verifyConfigArchitecture` 通过。
   - `assembleAppCoreDebug` / `assembleAppComicDebug` 成功，两变体 debug APK 均产出
     （2026-11-13）；`lintAppCoreDebug` / `lintAppComicDebug` 各 1 error 失败：唯一
     未过滤错误是 `NewApi`（`AppChoiceDropdown.kt:515` `rememberCouiCapsuleRenderer()`
     要求 API 34、min 26），见下一阶段第 7 条。
   - `git diff --check` 干净；改动文件 0 诊断；子仓工作树干净。
   ⚠️ 仍待：其余受控 `AppActionMenu` 的玻璃锚点尺寸、四引擎外观 / 设置页配色真机基线、
   TalkBack；阶段 D 清理；上述 9 个既有单测失败与 1 个 lint 错误需单独开片
   （lint 错误已于第五十轮修掉，见下）。
   **第五十轮（修 COUI 胶囊 `NewApi`，计数仍 4/0）**：接第四十九轮全量 lint 的唯一
   未过滤错误，单独开片处理；不加 `@SuppressLint`、不放宽 `lint-baseline.xml`。
   - 根因：`rememberCouiCapsuleRenderer()` 与 `CouiTopAppBarButton()` 都标
     `@RequiresApi(UPSIDE_DOWN_CAKE)`（34），minSdk 26。`CouiChoice` 的 Icon 分支
     把渲染器构造写在 `ThemeResolver.isCouiSupported()` 守卫之外（无条件调用），
     且库按钮的调用条件只有 `capsule != null` —— lint 不把后者折算成 SDK 检查。
     第一次只把构造移进守卫分支后，`remember...` 的报错消失，但
     `CouiTopAppBarButton` 在 527 行继续报 `NewApi`，说明**两个 API 34 点都要各自
     落在可见守卫里**。
   - 修复（只改 `AppChoiceDropdown.kt` 的 Icon 分支，行为不变）：构造改成
     `val capsule = if (isCouiSupported() && LocalAppCouiNativeToolbar.current)
     rememberCouiCapsuleRenderer() else null`；库按钮条件补成
     `if (capsule != null && ThemeResolver.isCouiSupported())`。
     `ThemeResolver.isCouiSupported()` 带 `@ChecksSdkIntAtLeast(api = 34)`，
     与 `TopBarButton` 的既有形态同口径；低版本与 renderer 为空时仍回退 `IconButton`。
     守卫重复求值只为让 lint 直接看见，不引入新状态。
   - 契约：新增 `CouiCapsuleApiGuardContractTest` 2 例（两个 API 34 点都在守卫内、
     守卫是 lint 可识别的 SDK 检查且没有 `@SuppressLint`）。
   验证：双变体编译 + `menu.*` 定向单测 `BUILD SUCCESSFUL`（3m17s，新契约 2 例
   0 失败）；`lintAppCoreDebug` / `lintAppComicDebug` 分别单变体跑（避开并行时的
   `lintAnalyze` OOM），均 `BUILD SUCCESSFUL`：core `0 error / 32 warnings`、
   comic `0 error / 46 warnings`，baseline 过滤项不变；`git diff --check` 干净、
   改动文件 0 诊断、子仓工作树干净。
   ⚠️ 未验证：API 26~33 设备上 COUI 主题的实际回退外观（应落到 `IconButton`）、
   API 34+ 胶囊按钮真机观感；第四十九轮的 9 个既有单测失败、TalkBack、
   四引擎外观基线、阶段 D 清理仍未处理。
   ⚠️ 环境：双变体 lint 并行跑时 `lintAnalyzeAppComicDebug` 出现过一次
   `OutOfMemoryError`（Kotlin UAST 分析），改成分变体单独跑即通过；属构建内存问题，
   不是代码问题，CI 若并行需留足内存。
   **第五十一轮（COUI 主题设置对齐 Miuix + 主题风格项形态统一 + 首项恢复动态取色，
   计数仍 4/0）**：用户要求 COUI 的主题风格相关设置与 Miuix 一致、去掉主题模式
   区域顶部的示例组件，并调查「主题风格」设置项为何与同页其它菜单设置项形态不同。
   用户定案：关闭「自定义主题色」时**与 Miuix 一致隐藏**配色列表（开启时显示 14 项）；
   「主题风格」项与同页下拉项统一到 `DropdownListSettingItem`。随后用户复核首项并追加
   定案：**首项恒为「动态取色」，名字与行为都改** —— 开启开关后必须真正走系统动态
   取色，关闭开关时才由 COUI 内置预设接管。
   - 调查结论：三条口径分别是 `R.string.theme`（配色 / 主题模式）、
     `R.string.theme_mode`（深浅模式）、`R.string.compose_engine`（主题风格）。
     同页下拉项（如「导航动画」）走 `DropdownListSettingItem` →
     `SettingItem(choiceOptions)`；主题风格项因第一轮 P0-0 阻塞（Mochi 浮层挂在
     DecorView，切换根主题必须等浮层收起再提交）需要 `commitAfterDismiss = true`，
     当时包装器没有该参数，于是直接拿顶层 `AppChoiceDropdown` 包一个
     `SettingItem(interactive = false)` 纯展示 trigger，绕过了设置项组件族 ——
     这是形态不一致的来源，**不是引擎差异**。
   - 二次复核（用户以「导航动画是正常的设置项」举例）：改成直接调
     `SettingItem(choiceOptions)` 仍不一致 —— `SwitchSettingItem` /
     `ClickableSettingItem` / `DropdownListSettingItem` 这些**包装器**都会先画
     `SplicedColumnDivider()`，底层 `SettingItem` 不会；且 Miuix 下包装器走
     `GlassDropdownPreference` / `OverlaySpinnerPreference` 库原生行，自绘
     `SettingItem` 不是。故最终改为走 `DropdownListSettingItem`，并把
     `commitAfterDismiss` 加到包装器上透传。
   - 首项根因：`themes_item` 首项本就是「动态取色」（`appTheme == "0"` →
     `AppThemeMode.Dynamic`），但 `ThemeConfigScreen` 两处把 COUI 下的 `value == "0"`
     改名成 `R.string.coui_default_palette`，`AppTheme.kt` 的
     `useCouiDefaultColors = Coui && Dynamic` 与 `ThemeEngine.getColorScheme` 的
     COUI + Dynamic 特例又把它实际替换成 `CouiPresets` 固定色板、`useDynamicColor`
     置 false —— 不只是名字，选中拿到的也不是系统动态取色。
   - 改动（`ThemeConfigScreen.kt`）：新增 `isCouiEngine` 与
     `useBuiltInPaletteEngine = isMiuixEngine || isCouiEngine`，统一门控 `ThemeCard`
     预览卡（COUI 不再显示）、「自定义主题色」开关（COUI 可见）、14 项配色列表
     （关闭开关时隐藏）与 `appTheme == "12"` 的自定义颜色入口；下拉列表与
     `ThemeColorSelector` 都去掉首项替换，直接用 `visibleThemes.map { it.first }`
     和 `label`，4 份 `values*/strings.xml` 的 `coui_default_palette` 一并删除。
   - 改动（`ThemeEngine.kt` / `AppTheme.kt`）：`getColorScheme` 新增
     `useBuiltInPalette: Boolean = false`，COUI 预设分支由它控制；`AppTheme` 把
     `useCouiDefaultColors` 改成 `useBuiltInPalette = Coui && !themeSettings.useMiuixMonet
     && appThemeMode == Dynamic`，同时用于 `useDynamicColor`、两处 `remember` 缓存键、
     `ProvideCouiTheme(useDefaultColors = …)` 与 `ThemeEngine` 入参。**保留
     `appThemeMode == Dynamic` 这个限定**：既有
     `AppThemeEngineTest.couiDefaultUsesNativeColorsAndKeepsExplicitPaletteOverrides`
     要求 `appTheme == "12"` 的显式配色覆盖仍能穿透 COUI 预设。
     `ProvideColorSchemeOverride`（书籍详情按封面取色等局部覆盖）仍用单参数版
     `ProvideCouiTheme`，刻意尊重传入的 colorScheme，本次未改。
   - 改动（`SettingItem.kt` / `ListSettingItem.kt`）：`SettingItem` 新增
     `choiceCommitAfterDismiss: Boolean = false` 并透传给受控
     `AppChoiceDropdown(commitAfterDismiss = …)`；`DropdownListSettingItem` 新增
     `commitAfterDismiss: Boolean = false`，在 Material / Mochi / COUI 分支透传成
     `choiceCommitAfterDismiss`。两个默认值让全仓 30+ 既有调用方保持不变；
     Miuix 原生 spinner / 无 popup host 的 tiny 分支不涉及 DecorView 浮层，忽略该参数。
     主题风格项改用 `DropdownListSettingItem(displayEntries = entries,
     entryValues = values, unavailableValues = …, commitAfterDismiss = true)`，
     删掉手工构造的 `AppChoiceOption` 列表、`SettingItem` 直调与两个 import；
     低于 Android 14 的 COUI 不可用档位与「收起后再切根主题」行为都保留。
   - 用户复核后决定**直接删除「文字动画示例」行**（二次复核查出的 Miuix 双重
     内边距就来自它绕过包装器、直调底层 `SettingItem`：`SettingCard` 的
     `BasicComponent` 默认 `insideMargin = 16.dp` 再套一层 M3 `ListItem` 内边距，
     而同页其它行走 `SwitchPreference` / `ArrowPreference` /
     `GlassDropdownPreference` 只有一层）：删掉 `TextAnimationSettings` 里的预览行、
     `ExtendedSettingsSearchTest` 的示例点击断言与 4 份 `values*/strings.xml`
     各 4 条专属文案，保留「文字动画等级」下拉项与 `SettingsSearchCatalog` 的
     `theme.text_animation_level` 条目；顺带清掉 `widthIn` / `mutableStateOf` /
     `rememberSaveable` / `SplicedColumnDivider` / `SettingItem` /
     `AnimatedTextLine` 六个无用 import。契约新增 `textAnimationPreviewRowIsRemoved`。
   - 文案：`miuix_monet` / `miuix_monet_summary` 在 4 份 `values*/strings.xml`
     通用化（「自定义主题色」/「为主题风格使用自定义配色…」），不再点名 Miuix，
     否则 COUI 开关下会显示「为 Miuix…」。资源 key 不变，不动调用点。
   - 契约：`ThemeConfigCouiThemeContractTest` 共 9 例（两引擎共用内置配色门控、
     首项保持动态取色且不再有 COUI 默认替换、主题风格项与导航动画同走
     `DropdownListSettingItem` 且不再直调底层 `SettingItem`、`SettingItem` 与
     `DropdownListSettingItem` 两级透传 `commitAfterDismiss`、包装器仍画拼接分隔线、
     示例行已删除且等级项保留 / 四份专属文案清零、四份文案不再点名 Miuix，
     以及内置配色开关口径 / 缓存键 / 动态取色路径）；
     `AppThemeEngineTest` 新增 `couiCustomColorSwitchRestoresDynamicFirstTheme`
     共 12 例，实测开启开关后 `LegadoTheme.useDynamicColor == true` 且
     `MaterialTheme.colorScheme.primary` / `CouiTheme.colorScheme` 不再是
     `CouiPresets.Light`，关掉开关后回到预设（同时验证缓存键生效）；
     `ExtendedSettingsSearchTest` 删掉示例点击断言后 12 例全绿。
   验证：双变体 Kotlin 编译 + `testAppCoreDebugUnitTest` 定向 `themeConfig.*` /
   `AppThemeEngineTest` / `menu.*` / `feature.settings.search.*` +
   `assembleAppCoreDebug` 同批 `BUILD SUCCESSFUL`（3m43s）；契约 XML
   `tests="9" failures="0"`、`AppThemeEngineTest` `tests="12" failures="0"`、
   `ExtendedSettingsSearchTest` `tests="12" failures="0"`；
   `verifyConfigArchitecture` 随测试任务通过；`git diff --check` 干净、
   改动文件 IDE 0 诊断。上一轮 `lintAppCoreDebug` 已
   `0 error / 32 warnings`，本轮未重跑 lint / 全量单测 / comic 变体
   （本轮无新增 Android API 调用，lint 风险面不变）。
   ⚠️ 未复核：真机上 COUI / Miuix 主题设置页外观（预览卡消失、开关落点、配色列表
   隐藏 / 显示、首项「动态取色」选中后的实际取色、主题风格行与导航动画是否一致）、
   **Miuix 原生 spinner 选中后立刻切根主题是否会与弹层收起动画冲突**、
   TalkBack 语义、低版本 COUI 回退、Android 12~13 下动态取色回退到 GR 色板的观感；
   第四十九轮的 9 个漫画 / insets 既有单测失败、`lint-baseline.xml` 19 条旧条目、
   阶段 D 清理、搜索按钮样式差异仍未处理。
3. ~~逐个把 18 个 `TopBarOverflowMenu` 页面从 `externalTrigger = true` 切到主入口~~
   **（2026-11-11 第十五轮实测：这条已基本完成）** 16 个列表页已改传 `menuGroups`
   走 `TopBarActionMenu`；`dropDownMenuContent` 只剩 `RssSourceScreen` 1 处（刻意保留，
   见第 2 条）。`TopBarOverflowMenu.kt:55` 的 `externalTrigger = true` 现在只服务那 1 页，
   不必再单独排期；剩余工作只是「四引擎顶栏按钮外观统一」，随第 1 条的基线一起收。
4. Miuix `Icon` 形态的 HyperOS 4 玻璃锚点变形（第四十九轮更正）：`compact` **早已接入**
   （`MiuixCompactChoice` 的 `MiuixGlassMenuHost` 分支），本条原先把两者并列写成
   「暂不接玻璃」，与源码不符；`AppChoiceDropdown.kt` 的 KDoc 已同步更正。剩余只有
   `Icon`（`MiuixIconChoice` 仍走库自持 `Window/OverlayIconDropdownMenu`，要接玻璃需库
   提供受控 Icon 弹层），先真机确认「整排玻璃、只有筛选按钮没有玻璃」的错位是否仍可见
   再决定做不做。另需把 `AppChoiceDropdown` 默认行形态的「trigger 槽不生效」例外写进契约。
5. 三入口 × 四引擎契约测试剩余项（COUI / Miuix 玻璃路径的 ID 回调、菜单项语义树名称；
   子项回调与关闭所有权已在第十二轮收口）；在用户允许时补跑 `testAppComicDebugUnitTest` / `lint` / `assemble`，
   复核 9 个漫画 / insets 既有失败；真机验 COUI 触点、Miuix 玻璃变形、Mochi 融球滑选、
   TalkBack、根主题切换。
6. 阶段 D 清理 `glassAnchor` / `mochiAnchorMaxWidth` / `LocalGlassPopupContent` /
   `LocalUseWindowPopupForMochiMenu`。
7. 独立处理剩余 lint 失败：`ui/widget/dialog/PhotoDialog.kt:86` 的 `ThreadConstraint`
   已被 `lint-baseline.xml` 过滤，与菜单迁移无代码交集，单独修复或更新 baseline。
   ~~`AppChoiceDropdown.kt` 的 COUI 胶囊 `NewApi`~~ **（第五十轮已修）**：两个
   `@RequiresApi(34)` 调用点各自补上 `ThemeResolver.isCouiSupported()` 可见守卫，
   双变体 lint 已回到 0 error，详见 §9 第五十轮。另注意 lint 提示 baseline 里有
   19 条「列了但已找不到」的旧条目，后续可单独清理，不要借菜单 PR 改。
