# Mochi 菜单 fork 扩展规格

> 用途：交给 Mochi fork 维护者照此实现；本仓库据此落地 `AppChoiceDropdown` / `AppActionMenu` /
> `AppAnchoredPanel` 的 Mochi 分支。
> 状态：**fork 已交付 `0.2.1-menu3`（`mavenLocal`），规格中的 P0/P1/P2 均已实现并接入本仓库**；
> 实际实施边界与验证结果见 `docs/dev/native-menu-engines-plan.md` §9。
>
> **2026-11-11 第二轮：定案走 §11.3 的 B（anchor-only）**，不采用 A（`trigger = Click` +
> `buttonStyle = Bare`）。fork 交付 `0.2.1-menu2`：新增 `MochiDropdownMenu(anchorBounds, …)`，
> `MochiOverlay` 从 `DropdownCore` 里拆出来（`buttonContent` 可空、`singleBlob` 只画面板本体）。
> 本仓库三个 Mochi 分支已按 §11.4 走 B 接入；`trigger` 纯展示契约不变，因此 Mochi 下顶栏
> MoreVert 是裸图标，要回到 `TopBarActionButton` 外观需另做入口扩展，见 plan §9「下一阶段」。
>
> 已定决策：① `trigger = Press`（按下即开 + 按住滑选）；② 能力缺口走 fork 扩展，不在应用层
> 用自绘降级；③ `AppAnchoredPanel` 改为组件渲染触发器，Mochi 分支用 `MochiPopoverButton`。
>
> **2026-11-11 决策变更（见 §11）**：用户看过真机截图后要求改成「点击按钮松手后才弹出菜单」，
> ① 据此修订为「库默认仍是 `Press`，新增 `Click` 模式，本仓库 Mochi 分支默认走 `Click`」；
> 用户明确接受放弃按住滑选 / 按钮变面板形变 / 融球高亮。②③ 不变。
> 实施时请同步更新 §4.5、§6、§10 中「以 Press 为准」的表述，不要只改代码。

## 1. 基线

- 依赖：`io.github.feice11:mochi-compose:0.2.0`（`gradle/libs.versions.toml:103,281`），
  另有 `mochi-ios:0.2.0`、`app/mochi-compat.gradle` 的字节码改写。
- 本文所有行号来自该版本 `-sources.jar` 解压后的源码（共 170 个 `.kt`），包名前缀
  `ui.feice.mochi.`。核心文件：
  `basic/MochiDropdown.kt`、`basic/MochiDropdownDefaults.kt`、`basic/MochiPopover.kt`、
  `basic/MochiContextMenu.kt`、`basic/menu/{MenuEngine,MenuList,DropdownOverlay,DropdownGeometry}.kt`、
  `gesture/PressSlideGesture.kt`、`overlay/InWindowOverlay.kt`、`basic/MochiCheckbox.kt`、
  `list/MochiList.kt`。
- 本仓库目前**没有**任何 `MochiItem` / `MochiDropdown` / `MochiIconDropdown` 调用点，
  唯一使用的 Mochi 弹层原语是 `MochiPopoverBox`（`app/.../mochi/MochiPopover.kt:28,66`）。
  因此 P0 扩展对现有调用方几乎零风险。

---

## 2. P0-0 阻塞项：DecorView 浮层与 `select` 时序冲突（必须先定方案）

**证据链**

- `overlay/InWindowOverlay.kt:40-94`：浮层不是独立 `Popup` 窗口，而是
  `decor.addView(overlay, MATCH_PARENT)` / `onDispose { decor.removeView(overlay) }`，
  注释 `:32-34` 明确「不用 Popup，Popup 会被系统限制在状态栏/导航栏之间」。
- `basic/menu/MenuEngine.kt:139-146`：
  ```kotlin
  select = { i ->
      currentHaptics.confirm()
      currentOnItemClick(i)                       // ← 先回调业务
      scope.launch { delay(currentDismissDelay); currentOnExpandedChange(false) }  // ← 150ms 后才关
  }
  ```
- `app/src/main/java/io/legado/app/ui/config/themeConfig/ThemeConfigScreen.kt:293-295`：
  主题风格选择现在用 `CompositionLocalProvider(LocalUseWindowPopupForMochiMenu provides true)`
  强制菜单走独立窗口，注释写明「切换根主题会销毁菜单，避免测量期间移除 DecorView 子视图」。

**影响**：第一轮的验证场景就是「主题风格选择」。换成 `MochiDropdown` 后，选中项会立刻
`onIntent(SetComposeEngine)` 重建根主题，而菜单的 DecorView 子 View 还要 150ms 才移除 —— 正是
现有规避要防的路径。Press 语义下也无法靠「先关菜单再切主题」在应用层绕过，因为回调在关闭之前。

**建议解法（推荐 A，不需要改库）**

- A. 应用层挂起提交：`AppChoiceDropdown` 在主题风格选择场景把选中值记为 pending，
  在 `onExpandedChange(false)`（库关闭菜单时）之后再派发 `SetComposeEngine`。
  `AppMenuTriggerScope` 需要能观察到关闭事件；这条由本仓库实现，fork 无需改动。
- B. 库侧加性可选参数：`commitAfterDismiss: Boolean = false`。为 `true` 时 `select` 先
  `currentOnExpandedChange(false)`、等关闭动画结束再 `currentOnItemClick(i)`；默认保持
  0.2.0 时序，不影响其它调用方。

**需要 fork 确认**：若 fork 打算把 `InWindowOverlay` 改成独立窗口 `Popup`，请一并说明
（会改变 glass backdrop 取样与 edge-to-edge 行为，`InWindowOverlay.kt:52-69` 的
`LocalGlassSources` 组合链依赖同窗口）。我们按现状设计，因此**默认不要求改 overlay 宿主**。

---

## 3. P0 扩展 API 增量

只追加、不改类型。全部参数追加在函数签名**末尾并带默认值**。

```kotlin
// basic/MochiDropdownDefaults.kt:148-152 —— 现为 @Immutable public class（非 data class）
@Immutable
public class MochiItem(
    public val label: String,
    public val destructive: Boolean = false,
    public val icon: (@Composable (tint: Color) -> Unit)? = null,
    // ↓ 新增
    public val id: String? = null,                    // 稳定 ID，供 key() 与子菜单定位
    public val enabled: Boolean = true,              // 逐项禁用
    public val checked: Boolean? = null,             // null = 普通项；true/false = 独立开关勾选态
    public val dividerBefore: Boolean = false,       // 该行顶边画 1dp 分隔线，不额外占高
    public val groupTitleBefore: String? = null,     // 该行上方的小标题，不额外占高
    public val children: ImmutableList<MochiItem> = persistentListOf(),  // 一层，P1 启用
)
```

```kotlin
// 新增（P1，子菜单用）
public data class MochiMenuSelection(
    val parentIndex: Int?,      // null = 顶层项
    val index: Int,             // 在所在层级 items 中的下标
    val itemId: String?,
    val parentId: String?,
)

// basic/MochiDropdown.kt:303-321 与 :505-521，末尾追加可选参数（不要动 onItemClick 的 (Int)）
onChildClick: ((parentIndex: Int, childIndex: Int) -> Unit)? = null,
submenuBackLabel: String? = null,
commitAfterDismiss: Boolean = false,   // 见 §2-B；不做则为 false
```

```kotlin
// basic/MochiPopover.kt —— AppAnchoredPanel 用（P1）
public val LocalMochiDismiss: ProvidableCompositionLocal<() -> Unit>
```

**明确不新增/不修改**：

- 不改 `onItemClick: (Int) -> Unit` 的类型与语义（仍为所在层级 `items` 下标）。
- 不改 `items: ImmutableList<MochiItem>`、`expanded` / `onExpandedChange` 的位置与类型。
- 不改 `selectedIndex: Int`（单选仍由调用方持有）。
- 不引入 `sections` 重载替换 `items`（见 §8 兼容红线）。

---

## 4. P0 改动点（逐项，含精确行号）

### 4.1 逐项禁用 `enabled`

索引几何必须保持「每项等高、`items.size` 行数 = 可交互项数」，禁用项仍占一行。

| # | 文件 / 函数 / 行 | 改法 |
|---|---|---|
| 1 | `gesture/PressSlideGesture.kt:149-184` `MochiController` | 新增 `var isEnabled: (Int) -> Boolean = { true }`；`track(p)`（`:170-174`）改为 `pointer = p; touchOnScreen = p; val h = hitTest(p); hover = if (h >= 0 && isEnabled(h)) h else -1`。**这是唯一源头**：`hover = -1` 会让 `MenuList.kt:133-144` 的 `itemGlow` / `hlPress`、`MenuList.kt:145-167` 的高亮块、`MenuList.kt:184-206` 的 `drawBehind` 自动不画按压形变。 |
| 2 | `basic/menu/MenuEngine.kt:131-146` `rememberMochiEngine` | 新增 `isEnabled: (Int) -> Boolean` 形参（`rememberUpdatedState` 包裹），在 `callbacks.select` 开头兜底 `if (!currentIsEnabled(i)) return`。覆盖语义点击路径与任何手势路径。 |
| 3 | `basic/MochiDropdown.kt:397-407` `DropdownCore` | 把 `isEnabled = { i -> items.getOrNull(i)?.enabled != false }` 注入 `rememberMochiEngine`，并赋给 `controller.isEnabled`。`MochiDropdown` / `MochiIconDropdown` 公开签名不用改。 |
| 4 | `basic/menu/DropdownOverlay.kt:520-585` `MochiOverlay` 面板手势 | 把 `:529` 的 `callbacks.panelPressed(true)` 移到 `:530 track` **之后**，并加 `&& controller.hover >= 0`（否则禁用行仍触发面板级发光）；`:580-583` 释放处 `if (h >= 0 && isEnabled(h)) callbacks.select(h) else controller.hover = -1`；自动滚循环 `:267` 的 `track` 后同样走新 `track` 逻辑。 |
| 5 | `basic/menu/MenuList.kt:229-285` `MochiList` | 行 `.semantics`（`:248-254`）改为 `if (item.enabled) { role = Role.Button; onClick { onSelect(i); true } } else disabled()`（补 `import androidx.compose.ui.semantics.disabled`）；`:233-234` 的 `color` 禁用时降到 0.38f alpha；`LiquidRow`（`:297-314`）加 `enabled` 形参，对 label/icon 降透明度。 |
| 6 | `basic/MochiContextMenu.kt:164-180` | 与新 `track` 同步（共享 controller，避免长按预览仍提交禁用项）。 |
| 7 | `gesture/PressSlideGesture.kt:277-296` `mochiButtonGesture` | 无需改：`cb.select(h)` 的条件是 `h >= 0 && moved`，`track` 过滤后自然不提交。 |

**不需要改**：`DropdownGeometry.kt` 的 `computeTarget` / `MochiPx`、`computeFrame`、
`DropdownScroll.kt`、`MenuEngine.kt:113` 的 `itemCount`、滚动条 —— 几何与滚动完全不变。

### 4.2 勾选 `checked`

`MenuList.kt:227` 的 `val radio = selectedIndex >= 0` 是**菜单级**「是否预留勾列」，不是逐项属性。

- `MenuList.kt:227` → `val hasCheckColumn = selectedIndex >= 0 || items.any { it.checked != null }`，
  并把 `LiquidRow`（`:298`）的 `radio` 形参改名/复用（`:300` 的 `SelectionIndent` 依赖它）。
- `MenuList.kt:232` 附近新增 `val showCheck = selected || item.checked == true`，替换 `:267 / :281 / :302`
  三处对勾条件；`:234` 的 `color` **仍只由 `selected` 决定**（`onSelectedColor` 不应用于 checked-only 行）。
- 视觉：`selected` 保持 `MochiGlyphs.Check`（`basic/parts/MochiGlyphs.kt`）；`checked` 建议用
  `basic/MochiCheckbox.kt:60-85` 的 `MochiCheckbox(checked, onCheckedChange = null, enabled = item.enabled)`
  纯展示态，与单选对勾区分。
- `MenuList.kt:248-254` 行语义补 `Role.Checkbox` / `stateDescription` / `toggleable`（P1-4）。
- `MenuEngine.kt:139-146` 从不写 `selectedIndex`，checked 完全由调用方持有，**无需改引擎**。

### 4.3 分组标题与分隔线（属性式，不额外占行）

- `dividerBefore` → 在 `MenuList.kt:229-285` 的行 `Modifier.drawBehind` 顶边画 1dp
  `style.outlineVariant`（或 `MochiTheme.colorScheme.outlineVariant`），**不改变行高**。
- `groupTitleBefore` → 同行的行内上部小字（`MochiTheme.textStyles.footnote`），叠在
  `ItemLabel` 上方或压缩其空间，仍不占额外行高。
- 可复用 `list/MochiList.kt:256` `MochiListSectionHeader` 的**颜色/字体取值**，但**不要**直接用
  `list/MochiList.kt:221` 的 `Modifier.mochiListDivider(inset)` —— 它依赖 `layoutId(ListItemInfo)` 的
  另一套布局协议，不能塞进 `basic/menu/MenuList.kt` 的等高行模型。
- 这样 `DropdownGeometry.kt:218,226`、`DropdownOverlay.kt:334,341,659,666`、`MenuEngine.kt:113`
  里所有 `items.size * itemHeight` 公式零改动，`onItemClick(Int)` 语义原封不动。

### 4.4 稳定 ID

- `MenuList.kt:230` `key(i)` → `key(item.id ?: i)`。
- `MochiMenuSelection.itemId` / `parentId` 由 `MochiItem.id` 填充（P1 子菜单）。
- 应用层的 `id ↔ index` 映射由本仓库统一入口维护，库侧**不要求**把回调改成 id。

### 4.5 Press 的语义点击修正

`basic/MochiDropdown.kt:429-446`：当前 `onClick` 语义动作是
`if (c != null) c() else onExpandedChange(!expanded)`。Press 模式下若传了 `onClick`，
无障碍/键盘「点击」按钮会执行业务点击而**打不开菜单**。

- 改为：`trigger == Press` 时按钮语义 `onClick` 恒为 `onExpandedChange(true)`；
  仅 `trigger == LongPress && onClick != null` 时才执行 `onClick`。
- `onLongClick` 语义的安装条件不要依赖 `onClick != null`（`:437-442`）。
- **不需要新增「无触点程序化打开」API**：`expanded` 本就完全外部受控
  （`MenuEngine.kt:178-204` 只依赖 `expanded`），外部置 `true` 时形变从锚点矩形起、
  `pressScale = 1`、`pull = Offset.Zero`，显示/关闭/`BackHandler` 全部正常
  （`DropdownOverlay.kt:293,306-307,354-371`）。`AppMenuTriggerScope.open()` 直接置位即可。

---

## 5. P1：一层子菜单 + 返回头

现状：`DropdownOverlay.kt` 只有 `MochiOverlay`（`:189-214`，`internal`），**没有** `DropdownOverlay(...)`
函数；单层裁剪 Box（`:496-503`）+ 单个 `pointerInput`（`:520-585`）+ 单个 `MochiList`（`:609-657`）；
`content != null` 与 `items` 互斥（`:587-589`）。树内无 back header / divider 组件，
`header: String?`（`:209`，渲染 `:591-607`）是唯一 header 原语，`headerPx` 已参与几何（`:309-319`）。

最小路径：

1. `DropdownCore`（`MochiDropdown.kt:353-501`）新增 `var submenu by remember { mutableIntStateOf(-1) }`；
   `activeItems = if (submenu >= 0) items[submenu].children else items`；`engine.Overlay(...)`
   （`:487-500`）传 `activeItems`，子菜单层 `selectedIndex = -1`。
2. `MenuEngine.kt:113` 的 `controller.itemCount = itemCount` 是组合期静态值 → 改由
   `DropdownOverlay.kt:337-343` 的 `SideEffect` 按 `activeItems.size` 每帧写入；切层时
   `scroller.snapTo(0f)`、`controller.hover = -1`、`controller.pointer = null`、`effects.snapTo(0f)`。
3. `MochiOverlay` 新增 `submenuTitle: String?` / `onBack: (() -> Unit)?`；`:334 / :341 / :610 / :659 / :666`
   的 `items.size` 换成 `activeItems.size`；用 `key(submenu) { MochiList(...) }` 重置显影；
   把 `:591-607` 的 `header` 改造成可点返回头（箭头 + `onBack` 语义），`headerPx` 已算好。
4. 索引不压缩进一个 `Int`：`:623` 的 `onSelect` 改为
   `if (activeItems[i].children.isNotEmpty()) onOpenSubmenu(i) else onCommit(i)`；
   顶层带 children 的行**只切页、不 `callbacks.select`**（否则 `MenuEngine.kt:142-145` 会延迟关菜单）。
   子项提交走新增 `onChildClick(parentIndex, childIndex)` 或 `MochiMenuSelection`。
5. 返回：`MenuEngine.kt:226` 已有 `BackHandler(enabled = expanded)`；在 `MochiOverlay` 内
   （`InWindowOverlay` 更内层、注册更晚）加 `BackHandler(enabled = submenu >= 0) { onBack() }`，
   注意两者顺序；返回时重置 `hover/pointer/effects`（参照 `MenuEngine.kt:178-183`）。
6. **不要**把第二个 `MochiList` 塞进 `content`：`hasContent = true`（`:492`）会改变
   `:565` 的 `pointerInput` 消费规则，子菜单会失去按住滑选。

---

## 6. P1：`LocalMochiDismiss`（`AppAnchoredPanel` 用）

`MochiPopoverButton`（`MochiPopover.kt:137-175`）→ `DropdownCore(content = popover)` →
`PanelContent`（`DropdownOverlay.kt:701-730`）；`MochiPopoverBox`（`:190-204`）→
`PopoverOverlay`（`:276-443`）。两处 `popover: @Composable ColumnScope.() -> Unit` 都**拿不到
dismiss**（只有 `:426` 提供 `LocalContentColor`，`onDismiss` 只用于点面板外）。

- 在 `PanelContent` 的 `Column`（`:711-729`）与 `PopoverOverlay` 的 `Column`（`:427-438`）
  外包 `CompositionLocalProvider(LocalMochiDismiss provides onExpandedChangeFalse)`。
- 加性、源码兼容；签名不动。
- 本仓库 `AppAnchoredPanel` 的 `content(dismiss)` 槽直接读这个 Local，
  不再靠闭包捕获（也更符合「组件渲染触发器」的新形态）。

`MochiPopoverButton` 的 `trigger = Press` 生效但 `items = persistentListOf()`（`:156`），
面板内无选项可滑选 —— 符合 `AppAnchoredPanel` 预期（按下即展开任意内容）。

---

## 7. P1：键盘 / 无障碍一致性

`InWindowOverlay` 明确「不抢焦点」（`MenuEngine.kt:225` 注释），树内 `MochiOverlay` 无
`FocusRequester` / `onKeyEvent` / `dismiss` 语义；目前每行只有 `Role.Button` + `onClick`。

- 在 `DropdownOverlay.kt:496-520` 的内容 Box 上补 `isTraversalGroup`、`paneTitle`、
  `focusRequester`、Escape 关闭与 `dismiss` 语义；可直接照抄本仓库
  `app/.../components/mochi/MochiOverlayAccessibility.kt:48-67` 的实现。
- 可选加性 API：`rememberMochiDropdownState()`（`open(fromKeyboard)` / `close()` / `select(i)`），
  内部复用 `MenuEngine.kt:131-173` 的 `MochiCallbacks`。

---

## 8. 兼容红线（P0 内禁止）

1. 不改 `onItemClick: (Int) -> Unit`，不改 `items: ImmutableList<MochiItem>`，
   不改 `selectedIndex: Int`，不改 `expanded` / `onExpandedChange` 的位置与类型。
2. `MochiItem` 是 `public class`（非 data class）且 `@Immutable`：新增字段必须是不可变 `val`、
   全部带默认值、追加在构造参数末尾。**二进制不兼容**（fork 全量重编译无影响）；
   若 fork 以 AAR 形式发布给未重编译模块，需保留旧构造重载或 `@JvmOverloads` 兼容层。
3. 本仓库唯一在用的 `MochiPopoverBox(expanded, onExpandedChange, width=, contentPadding=, popover=, anchor=)`
   （`app/.../mochi/MochiPopover.kt:66-82`）前两个是**位置参数**：任何新参数必须追加在 `anchor` 之后。
4. 不引入可变行高的独立 section 模型替换 `items`：会同时波及
   `DropdownGeometry.kt:150-164/205-240`、`MenuList.kt:154/175/209-240`、
   `DropdownOverlay.kt:334/341/346/610/625/629/659/666`、`DropdownScroll.kt:26-118`、
   `MenuEngine.kt:113`、`MochiController.hitTest`。若确要做，只能作为**新增重载**，
   并把「行序号 → 项下标」映射放在 `DropdownOverlay.kt:623 ↔ MenuEngine.kt:139` 之间，
   保持对外 `onItemClick(Int)` 仍是项下标。
5. `app/mochi-compat.gradle` 见下节。

---

## 9. 接入约束：`mochi-compat.gradle`（需要你确认发布形态）

`app/mochi-compat.gradle:40` **硬编码** `if (input.name != 'mochi-compose-0.2.0.aar') { 原样返回 }`，
`:49` 输出同名 AAR，`:46-48` 要求 `GlassPipelineKt.class` 中的 Backdrop 1.x 调用
**恰好命中一次**，否则构建直接抛异常。原因是工程用 Backdrop 2.0.1，而 Mochi 0.2.0
的玻璃路径仍调旧签名（`docs/dev/mochi-theme-integration.md:53-59`）。

因此 fork 发布时二选一：

- **A（改动小）**：发布新版本（如 `0.2.0-legado1`），我方同步更新 `mochi-compat.gradle`
  的两处文件名常量，并重新跑 `MochiGlassCompatibilityTest` 验证命中次数仍为 1。
- **B（更干净）**：fork 直接把 `GlassPipelineKt` 改为 Backdrop 2.x 的 Compose `RenderEffect` 调用，
  我方移除整个 compat transform 与 `MochiBackdropCompat`。但这属于依赖行为变更，
  需按 `docs/dev/mochi-theme-integration.md:58` 重新审查，不能只是放宽匹配规则。

请告知选哪种、以及新版本号/坐标，我好安排本仓库侧的版本与脚本改动。

---

## 10. 验收清单

**fork 侧**（建议新增 `MochiMenuExtensionTest`）

- `enabled = false` 的项：按住滑过不产生高亮/发光、松手不回调 `onItemClick`、语义树含 `disabled()`、
  仍占一行且 `items.size` 与面板高度不变。
- `checked = true/false` 与 `selectedIndex >= 0` 并存：勾列只预留一次，`onSelectedColor` 只作用于
  `selected` 行；`MochiCheckbox` 展示态随 `enabled` 变灰。
- `dividerBefore` / `groupTitleBefore`：面板高度、`maxScroll`、`computeTarget` 与 0.2.0 完全一致。
- `trigger = Press`：按下即开、按住滑到项松手提交、滑出面板取消；外部 `expanded = true`
  （无触点、键盘/无障碍）能正常打开与关闭；`onClick != null` 时语义点击仍打开菜单。
- 子菜单：父项只切页不提交、返回头/BackHandler 能回顶层、子项 `onChildClick` 索引正确、
  切层后 `hover/pointer/scroll/effects` 已重置、子菜单内仍可滑选。
- `LocalMochiDismiss`：在 `MochiPopoverButton` 与 `MochiPopoverBox` 的 popover 内都能关闭。
- 现有 0.2.0 调用方式（尤其 `MochiPopoverBox` 位置参数）编译通过。

**本仓库侧**（fork 到位后）

- 按 `docs/dev/native-menu-engines-plan.md` 阶段 A/B 落地，第一轮验证主题风格选择与书架更多。
- 回归 `MochiOverlaysAndBarsTest`、`CouiFloatingAndMenusTest`、`RoundDropdownMenuLazyTest`、
  `MochiGlassCompatibilityTest`。
- `.\gradlew.bat :app:compileAppCoreDebugKotlin :app:compileAppComicDebugKotlin` + 主验证集 +
  `git diff --check`。
- 真机：API 33+ 的融球/按住滑选、DecorView 浮层在切换根主题时的销毁路径、TalkBack。

---

## 11. P2（2026-11-11 修订）：Mochi 打开方式与按钮外观对齐

### 11.1 用户二次反馈：决策变更

用户看到真机截图（Mochi 下两个被库套成**大圆**的按钮：白底放大镜、粉底竖三点，
尺寸与底色都不是项目 `TopBarActionButton` 的形态）后明确提出：
**要改成和其它引擎一样，点击按钮松手后才弹出菜单**。

这**推翻了 §0 已定决策①**（`trigger = Press` 按下即开 + 按住滑选）。
这不是降级，是产品决策变更；用户明确接受放弃按住滑选、按钮变面板形变、融球高亮。
§0 决策②仍成立：能力缺口走 fork 扩展，**不在应用层自绘降级**。

> 本节上一版定案（「不需要换菜单形式、保留 Press、只加 `Bare` 参数」）**作废**，以本节为准。
> 现在需要**交互 + 外观一起改**：只去掉按钮外壳不改打开方式，仍会在按下瞬间展开；
> 只改 `trigger` 不去外壳，两个大圆仍在。

下表是改造前的「按钮归属」现状，便于定位要动的位置；三者本来就不同，
所以「改成和其它菜单一样」指的是**打开方式与按钮外观对齐**，不是照抄某个引擎的内部结构。

| 引擎 | 按钮由谁渲染 | 弹层 |
| --- | --- | --- |
| COUI | 入口 `Box(clickable + couiMenuAnchor)` / `IconButton`，trigger 原样展示 | `Menu(anchor = couiAnchor)` |
| Miuix `AppActionMenu` | 库渲染**标准** `IconButton`，自持 `expanded` | `Overlay/WindowIcon*DropdownMenu` |
| Miuix `AppChoiceDropdown` | 默认 `DropdownListSettingItem` 整行；`compact` 用入口 `Box(clickable)` | 库 / 入口 |
| Mochi | **库把整个 trigger 塞进 `buttonContent` / `icon`，外面再套带背景、圆角、最小尺寸与按压形变的按钮** | `MochiDropdown` / `MochiIconDropdown` / `MochiPopoverButton` |

差异的本质不是菜单形式，而是 **Mochi 是唯一给任意 trigger（可能是一整张 `SettingItem`
卡片）再套一层按钮外壳的引擎**：`AppChoiceDropdown.kt:233-248` 把
`SettingItem(interactive = false)` 传给 `MochiDropdown.buttonContent`，
`AppActionMenu.kt:152-174` 把图标传给 `MochiIconDropdown.icon` / `MochiDropdown.buttonContent`，
`AppAnchoredPanel.kt:51-61` 同理。这些库按钮**不读**本仓库 `TopBarButton.kt` 的
`TopBarButtonStyle` / `SeriesButton` / `MochiBarIconButton` 适配，也不参与
`SettingItem` 的 `SettingCard` / `SplicedColumnDivider` 行样式。

补充（**上一版推断已按截图修正**）：之前判断「搜索按钮走项目自绘
`TopBarActionButton`、没有变，差异来自旁边的 MoreVert」，但用户截图里
**放大镜和竖三点两个都是大圆**，说明至少不能排除搜索入口也走了库按钮或另一套大圆组件。
仅凭截图无法确认这两个控件各自来自哪个组件 / 页面，需按 §11.5 真机验收时定位；
**不要基于上一版推断缩小改造范围** —— 三个 Mochi 菜单组件都应支持 `Click`。

### 11.2 目标与库内依据

**目标**

1. 打开方式：点击松手（`ACTION_UP`）后才 `expanded = true`；按下不预展开、不启动滑选手势。
2. 按钮外观：与同页项目自绘按钮一致，不再有库的大圆 / 状态色 / 按压形变外壳。
3. 弹层：仍是 Mochi 原生 `MochiOverlay`（`MochiItem`、分段线、selected / checked、
   子菜单、动画与密度），**不能退回自绘列表**。
4. `Press` 作为库默认值保留、旧调用方零回归；新行为是本仓库 Mochi 分支的默认值。

**库内已有依据**

- §4.5：`expanded` 完全外部受控，外部置 `true` 时形变从锚点矩形起、
  `pressScale = 1`、`pull = Offset.Zero`，显示 / 关闭 / `BackHandler` 全部正常
  —— 「松手后置位」这条路库已经支持，**不用新增程序化打开 API**。
- §4.5 / §6：库内已有 `trigger` 枚举（至少 `Press` / `LongPress`），
  `MochiPopoverButton` 已暴露该参数；新增 `Click` 值是自然延伸。
- `MochiPopoverBox`（本仓库 `AppMochiPopover` 已在用）证明库里已有
  「锚点 + 弹层」分离式实现，`MochiOverlay` 可以脱离按钮独立渲染。

### 11.3 两条实现路径（都要求加性、默认值 = 现状）

**主方案 A：`trigger = Click` + `buttonStyle = Bare`**（改动最小）

- `trigger` 枚举新增 `Click`：按钮走普通 `clickable`（松手触发
  `onExpandedChange(true)`），**不安装** `mochiButtonGesture` / `PressSlideGesture`，
  不启动按钮变面板形变；§4.5 的语义点击修正扩展到 `Click`
  （语义 `onClick` 同样恒为 `onExpandedChange(true)`）。
- 新增 `buttonStyle: MochiMenuButtonStyle = MochiMenuButtonStyle.Default`，
  `Bare` 值：不画外层按钮的背景 / 圆角 / 最小尺寸 / padding，
  尺寸与外观由 `buttonContent` 决定，只保留点击语义。
- 若 `MochiDropdown` / `MochiIconDropdown` 公开签名尚未暴露 `trigger`，先把它透传出来。
- **三个组件都要支持**：`MochiDropdown`、`MochiIconDropdown`、`MochiPopoverButton`。
  漏掉 `MochiPopoverButton`，`AppAnchoredPanel` 的 B 类复杂面板仍会「按下即开」，
  同一应用混两种交互。

**备选 B：anchor-only 重载**（更干净，按钮 100% 归 App）——**已定案并交付（`0.2.1-menu2`）**

```kotlin
MochiDropdownMenu(
    expanded, onExpandedChange,
    items, onItemClick,
    selectedIndex = -1, ...,
    anchorBounds: IntRect,   // 或 anchor: @Composable () -> Unit
)
```

把 `MochiOverlay` 从 `DropdownCore` 拆出来独立渲染；App 侧 Mochi 分支改成与 COUI 同构的
`Box(clickable { scope.open() })` + 锚点上报（复用 `AppMochiPopover` 的
`Layout + onGloballyPositioned`），按钮直接用项目 `TopBarActionButton`。
上一版担心的「Press 连续手势需从外部锚点启动、库要注入初始 pointer 位置」这一代价
**已随决策变更消失**，B 现在完全可行。

> 取舍：A 改动最小、保留库按钮节点；B 更贴合用户「和其它菜单一样」的诉求，
> 且按钮外观问题一次解决。若 fork 内部拆 `MochiOverlay` 不费劲，建议直接走 B。

### 11.4 本仓库侧怎么改（fork 到位后）

- **走 A**：`MochiChoice` / `MochiActions` / `AppAnchoredPanel` 的 Mochi 分支各加
  `trigger = Click` + `buttonStyle = Bare`；`AppMenuTriggerScope.open()` 仍可用
  （`expanded` 外部受控）。三入口结构不变，只加参数。
- **走 B（已实现）**：Mochi 分支改成 `Box(clickable { scope.open() })` +
  `MochiDropdownMenu(anchorBounds)`，结构对齐 `CouiChoice` / `CouiActions`；
  `AppMenuTriggerScope` 契约不变。实测落地差异：因为 trigger 仍是**纯展示内容**
  （两个契约用例断言全树只有 1 个 `hasClickAction` 节点），按钮不能塞
  `TopBarActionButton`（它自带 clickable，会变成 2 个节点），所以 Mochi 下顶栏
  MoreVert 是裸图标。要拿回项目顶栏按钮外观，需要给入口加「触发器即按钮」模式，
  见 plan §9「下一阶段」第 1 条。
- 两条路径都**不改** `items` / `onItemClick(Int)` / `selectedIndex` /
  `commitAfterDismiss` / `expanded`，也不改 `AppMenuTriggerScope` 里
  trigger 为纯展示内容的约定。
- 18 个 `externalTrigger = true` 旧顶栏按钮本来就是点击松手打开，不受影响。
- B 类复杂面板（`AppAnchoredPanel` 的 `content`）随 `MochiPopoverButton` 一并改 `Click`。

### 11.5 红线与验收

沿用 §3 / §8 / §9，另加：

- **加性、默认值 = 现状**：`trigger` 默认仍为 `Press`，`buttonStyle` 默认 `Default`；
  §10 验收清单里 Press 相关用例继续跑，旧行为不能被新分支破坏。
- `MochiPopoverBox` 若动，遵守 §8 第 3 条（新参数只能追加在位置参数 `anchor` 之后）。
- 不改 `InWindowOverlay` 的 DecorView 宿主与 `composeEngine == Mochi` 门控（§3 第 3 条）。
- `Click` 模式验收：**按下不展开、松手才展开**；无按住滑选 / 按钮变面板形变；
  语义点击（TalkBack / 键盘）也能打开；`BackHandler`、`LocalMochiDismiss`、
  外部 `expanded = true`、`selectedIndex`、`commitAfterDismiss`、子菜单返回头全部正常；
  弹层视觉（分段线、勾选、子菜单、密度）与 Press 模式一致。
- 发布形态按 §9 二选一；新版本号同步 `app/mochi-compat.gradle` 两处文件名常量，
  复跑 `MochiGlassCompatibilityTest` 确认命中次数仍为 1。
- 真机验收：四引擎 ×「主题风格设置项 / 顶栏 MoreVert / 顶栏搜索按钮 / 设置行」对比截图
  与语义树；确认 Mochi 下按钮**不再是截图里的大圆**、与搜索按钮同一套外观，
  且松手才弹、按下无预展开。
- 文档同步：§0 决策①、§4.5、§6、§10 验收清单中「以 Press 为准」的表述，
  以及 `docs/dev/mochi-theme-integration.md:30-31,45,73-74` 的「分离式菜单契约」描述，
  都要改成「默认 Press、可选 Click」。
- 本仓库侧回归：定向 19 类 / 138 用例（含 `MochiOverlaysAndBarsTest`、
  `AppMenuDividerContractTest`、`TopBarActionMenuContractTest`、`TopBarMenuIdContractTest`、
  `TocMenuActionsTest`）+ 双变体编译 + `git diff --check`。

> 本规格**已由用户在另一个工作区实现并接入本仓库**（`0.2.1-menu3`，方案 B anchor-only），
> 真机效果也由用户调整完成。实施与验证记录见
> `docs/dev/native-menu-engines-plan.md` §9、交接快照 §8 第 1 条（已归档为已完成）。
> 本工作区已补 `MochiAnchorMenuContractTest`（3 例静态契约）锁住三入口结构、
> 顶栏按钮壳与依赖升级路径。

---

## 12. 我需要的最小交付物

1. fork 分支/提交（或 diff），含 §3 的 API 增量与 §4 的 P0 改动；P1 可分批。
2. 发布形态确认（§9 的 A 或 B）、新版本号与 Maven 坐标。
3. §2 的选择：是否保持 `InWindowOverlay` 的 DecorView 宿主（默认保持）；
   若做 `commitAfterDismiss`，告知参数名与关闭完成的判定时机。
4. 一份简短的「与 0.2.0 行为差异」说明，便于本仓库写迁移回归用例。
