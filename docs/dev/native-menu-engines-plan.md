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
> 累计 50 处。
> 2026-11-11 用户确认「分段线可以不保留文字」，四引擎分段线语义已统一。
> 其余 **51 处**业务调用点仍是委托态（源码实测：`RoundDropdownMenu(` 55 处 − 组件定义 1
> − 三入口内部 3），覆盖率低是当前最大未完成项。
> 用户另实测发现部分按钮（搜索按钮、主题风格设置项）与同页其它按钮样式不一致。
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
   Miuix 分支按既有能力降级不染色；
   顶栏 content 只剩 `RssSourceScreen`（伪子菜单 +
   嵌套 popup，刻意保留）。带文字标题的 4 处列表项菜单 trigger 是长按 / 图标按钮，
   需单独建交互基线，**不能复用顶栏 `menuGroups` 槽**；
   Sheet / Dialog 正文的分组标题不是菜单，不在范围。
   `ReaderMoreActionsSheet` 的 `ImageStyleDropdown` / `PageAnimDropdown`
   留作纯单选簇收尾，其 `ActionSquareHost` 共享 `expanded`、方块按钮形态复杂，
   先单独建外观与行为基线。
3. ~~逐个把 18 个 `TopBarOverflowMenu` 页面从 `externalTrigger = true` 切到主入口~~
   **（2026-11-11 第十五轮实测：这条已基本完成）** 16 个列表页已改传 `menuGroups`
   走 `TopBarActionMenu`；`dropDownMenuContent` 只剩 `RssSourceScreen` 1 处（刻意保留，
   见第 2 条）。`TopBarOverflowMenu.kt:55` 的 `externalTrigger = true` 现在只服务那 1 页，
   不必再单独排期；剩余工作只是「四引擎顶栏按钮外观统一」，随第 1 条的基线一起收。
4. 给 `compact` 与 `Icon` 的 Miuix 分支补 HyperOS 4 玻璃锚点变形（复用 `MiuixGlassActions` 的
   `AppGlassOverlayState` 注册路径），并把 `AppChoiceDropdown` 默认行形态的
   「trigger 槽不生效」例外写进契约。`Icon` 与 `compact` 同属「暂不接玻璃」，
   在 HyperOS 4 玻璃顶栏上会出现「整排玻璃、只有筛选按钮没有玻璃」的错位，
   这条已是该形态唯一已知的外观缺口。
5. 三入口 × 四引擎契约测试剩余项（COUI / Miuix 玻璃路径的 ID 回调、菜单项语义树名称；
   子项回调与关闭所有权已在第十二轮收口）；在用户允许时补跑 `testAppComicDebugUnitTest` / `lint` / `assemble`，
   复核 9 个漫画 / insets 既有失败；真机验 COUI 触点、Miuix 玻璃变形、Mochi 融球滑选、
   TalkBack、根主题切换。
6. 阶段 D 清理 `glassAnchor` / `mochiAnchorMaxWidth` / `LocalGlassPopupContent` /
   `LocalUseWindowPopupForMochiMenu`。
7. 独立处理既有 lint 失败：`ui/widget/dialog/PhotoDialog.kt:86` 的 `ThreadConstraint`；
   与菜单迁移无代码交集，单独修复或更新 `lint-baseline.xml`，不要借菜单 PR 改无关代码。
