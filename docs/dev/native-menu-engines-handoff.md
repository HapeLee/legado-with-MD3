# 四主题库菜单统一入口 — 会话交接快照

> 生成：2026-11-11；最近更新：2026-11-11（第十六轮：详情页顶栏「更多」菜单）。
> 用途：**换会话后继续阶段 C/D**。
> 本文件是过程性快照；长期方案与完整实施记录以 `docs/dev/native-menu-engines-plan.md` 为准，
> fork 扩展规格以 `docs/dev/mochi-menu-extension-spec.md` 为准。
> 文档与源码冲突时以源码为准。结论并入长期方案后可删除本文件。

## 0. 一句话状态

三入口（单选 / 命令 / 自定义面板）× 四引擎的原生分支都已落地并各有 JVM 契约基线；
阶段 C 已迁移 **50 处**业务调用点（第一轮 2 处验证场景 + 8 处纯单选 + 2 处编辑页顶栏命令菜单
+ 5 处带分段线的两段式菜单 + 9 处顶栏 `dropDownMenuContent` 批量迁移 + `TocScreen` 1 处
+ 规则编辑剪贴板菜单 3 处 + Sheet action 槽命令菜单 4 处 + 「命令 + 独立开关」菜单 3 处
+ 知识 / 角色详情纯单选 3 处 + 翻页动画 / 九宫格点击区域 compact 单选 2 处
+ 导入面板「配置页 / 列表页」切换 2 处 + 替换规则顶栏 / 直链上传规则菜单 2 处
+ CloudTts 引擎页顶栏命令菜单 1 处 + RSS 排序页主菜单 2 处 + 详情页顶栏「更多」1 处），
其余 **51 个**业务调用点仍是委托态（第十六轮实测：`RoundDropdownMenu(` 55 处 − 组件定义 1
− 三入口内部 3）。**覆盖率低仍是当前最大未完成项，不是漏做。**

**2026-11-11 用户实测反馈**：观察到部分按钮与同页其它按钮样式不一致（举例：搜索按钮、
主题风格设置项），并问是否 Mochi 引擎所致。结论：**不是 Mochi 特有，是三入口把按钮所有权
交给各主题库后四引擎都存在的差异，Mochi 最显眼**；详见 §10 最后一条与 §8 第 1 条。
这是文档里已登记的「顶栏 / 设置行外观真机基线」风险**第一次被真实观察到**。
用户随后给出真机截图，并明确要求改成「点击按钮松手后才弹出菜单」，
**推翻了 `mochi-menu-extension-spec.md` §0 决策①（`trigger = Press`）**；
最终定案见 §8 第 1 条与该规格 §11（已按新决策修订）。
**第四轮（用户侧）已交付并接入 `0.2.1-menu3`**：B 方案 anchor-only `MochiDropdownMenu`，
三个 Mochi 分支改为「入口可点节点 + 锚点上报」，大圆库按钮与按下即开一并消失；
用户已在应用里调整好效果。§8 第 1 条归档为已完成，本工作区补了
`MochiAnchorMenuContractTest` 3 例锁住该结构。

2026-11-11 第一轮：用户确认「分段线可以不保留文字」，据此统一四引擎分段线语义、
按口径收口 §3.5 的前两个缺口（第三个「任意 content / 嵌套弹层」仍未修）、
新增 17 个契约用例，并迁移 §3.4 A 类全部 5 处。详见 §3.0 / §4.9。

2026-11-11 第二轮：重新盘点全部剩余下拉菜单，**发现并修正了上一版把 4 处带文字标题
菜单误归为顶栏菜单的分类错误**（见 §3.4 B'）：它们实际在列表项 / `bottomContent` 的独立
`RoundDropdownMenu` 里，trigger 不是 MoreVert。据此把可直接迁的顶栏
`dropDownMenuContent` 9 处批量迁到 `menuGroups`，并新增 `TopBarMenuIdContractTest`
静态契约。详见 §4.10。

2026-11-11 第三轮：按「动作注册表 + 纯 ID → 行为映射 + 先建基线再迁移」模式
迁完 `TocScreen`，顶栏 content 仅剩 `RssSourceScreen` 1 处刻意保留；
新增 `TocMenuActions.kt` / `TocMenuActionsTest.kt`（5 例）与 2 例静态契约。详见 §4.11。
用户随后反馈按钮样式不一致（见 §10），确认了外观真机基线的紧迫性。

2026-11-11 第四至十二轮：Mochi 交互与按钮外观由用户侧交付 `0.2.1-menu3` 并真机调好
（§8 第 1 条已归档）；随后按「同目录 / 同构」切片迁完第五轮 3 处、第六轮 4 处、
第七轮 3 处、第八轮 3 处、第九轮 2 处、第十轮 2 处（导入面板两页切换）、
第十一轮 2 处（替换规则顶栏 + 直链上传规则菜单）、第十三轮 1 处（CloudTts 引擎页顶栏）、
第十五轮 2 处（RSS 排序页主菜单 + 重定向策略降级为 children）、第十六轮 1 处
 （详情页顶栏「更多」），累计 50 处，详见 §4.12–§4.18、§4.20、§4.22、§4.23；
 第十二轮不迁调用点，只补三入口 × 四引擎的行为契约（§4.19）；
 第十四轮修按钮玻璃与图标错位（§4.21）。
第十轮同时**作废**入口「动作后保持展开」待办，并更正了四引擎 `expanded` 所有权的描述
（§8 第 4 条）。
所有改动均**未提交**，工作区就是最新状态。

## 1. 接手前必读与硬约束

- `AGENTS.md`：仓库级不变量。资料优先级 = 用户目标 > `AGENTS.md` > `.agents/skills/*` > `docs/dev/` > 源码 > 外部资料。
- 用户明确偏好：**只跑相关定向编译 / 测试，不跑全量**；本会话全程遵守。
- 最小、可回滚、可独立验证的垂直切片；**不借任务重构无关代码**；保留工作区已有修改。
- 修 bug / 重构前先建立可复现测试或可观察证据，前后保持同一行为验证通过。
- 所有文本改动至少 `git diff --check`；构建用 JDK 21。
- 减少历史违规时同步下调 `verifyConfigArchitecture` 的 baseline Map（本系列未触碰）。
- Windows CRLF：`git diff --check` 的 `LF will be replaced by CRLF` 是警告，不是错误。
- 发布形态 `core` + `comic` 双包；代码 namespace `io.legado.app`，`applicationId` 是 `io.legato.kazusa`。

## 2. 架构与三入口契约

| 入口 | 用途 | 关键参数 |
|---|---|---|
| `AppChoiceDropdown` | 单选 | `options: ImmutableList<AppChoiceOption>`、`selectedId`、`onSelect(id)`、`compact` |
| `AppActionMenu` | 命令 / 分组动作 | `groups: ImmutableList<AppActionGroup>`、`onAction(id)`、`anchor` |
| `AppAnchoredPanel` | 任意内容 / 复杂面板 | `expanded`、`onExpandedChange`、`externalTrigger`、`content: ColumnScope.(dismiss)` |

- `trigger` 一律是**纯展示内容**（见 `AppMenuTriggerScope`）：入口包装唯一可点控件，避免双重点击语义。
  `scope.modifier` 注入 COUI `couiMenuAnchor`；`scope.open()` 在 COUI 走 `anchor.open {}`。
- 四引擎：`ComposeEngine.Material / Miuix / Mochi / Coui`。
- Miuix popup host：`LocalHasMiuixPopupHost` 为 true 走 `Overlay*`，否则 `Window*`；
  HyperOS 4 玻璃走 `LocalAppGlassOverlay` + `AppGlassOverlayState`（`origin == Offset.Zero` 才同坐标系）。
- `glassPopupAnchor` 必须挂**按钮最外层节点**；挂图标会重复上报 `containerBounds`。
- COUI backdrop：`AppTheme` 页面根与 `AppModalBottomSheet` 独立窗口各一份 `ProvideCouiMenuBackdrop`。
- `AppChoiceDropdown(compact = false)` 的 Miuix 分支复用 `DropdownListSettingItem`，**`trigger` 槽不生效**；
  自定义紧凑 trigger 必须用 `compact = true`（四引擎由入口 `Box(clickable)` 包装，Miuix 弹层走
  `WindowDropdownPopup` / `OverlayDropdownPopup`）。该例外尚未写进契约文档。
- `AppAnchoredPanel(externalTrigger = true)` 是 `TopBarOverflowMenu` 的兼容路径，**新代码不要用**。

## 3. 分割线专题（口径已确认并落地）

### 3.0 已确认口径与落地状态（2026-11-11）

用户明确：**分段线可以不保留文字**。据此本轮把「带文字标题不能零损失迁」这个阻塞项解除，
并统一了模型与四引擎实现：

- `AppMenuModels.dividerBeforeAt(...)` 是分段线的**唯一判定**：显式
  `AppAction.dividerBefore`、标题信号 `AppAction.groupTitleBefore` / `AppActionGroup.title`、
  以及第 2 组起的组首自动补线，全部只表达「一条纯分段线」。四引擎都不渲染标题文本，
  KDoc 已把这两个字段的语义改为「降级提示」，调用方不得依赖文字出现。
- `AppChoiceOption.dividerBefore` 在 Material（`PillDivider()`）与 Miuix compact
  （切分 `DropdownEntry`）补齐；Miuix 默认设置行形态复用 `DropdownListSettingItem`，
  库不支持，仍显式忽略（已写进模型 KDoc）。
- **§3.5 的前两个缺口已按口径收口**（第三个「任意 content / 嵌套弹层」仍未修）：
  Material `LegacyActions` 支持 `dividerBefore`、补 `selected` 语义，并把 `children`
  平铺为缩进项而非静默丢弃；COUI 去掉 `headerText`（不再只保留第一组标题）；
  Mochi 不再传 `groupTitleBefore`，组首覆盖缺陷随之消失。
- 四引擎分段线视觉形态仍不同（Miuix 长线、其余短胶囊线），这是引擎原生差异，不是回归。
- §3.4 **A 类 5 处已全部迁到 `AppActionMenu`**；B 类继续留在 `AppAnchoredPanel` 的原因
  只剩伪子菜单 / 动态长列表 / 复杂面板，**标题文字不再是障碍**。
- 新增 `AppMenuDividerContractTest`（13 例：5 个纯函数判定 + 四引擎组合 + 两个子菜单 +
  两个单选分段线）与 `TopBarActionMenuContractTest`（4 例：四引擎顶栏声明式槽）。

> 注意口径的实际影响面：本轮 A 类 5 处原本用的就是**无文字**的 `PillDivider()`，
> 「文字可以不保留」真正解锁的是 §3.4 B' 那 4 处 `PillHeaderDivider(title)` 菜单
> （它们不是顶栏菜单，入口与 `menuGroups` 槽无关，见 §3.4 B'）。
> 统一分段线语义同时顺带修掉了 §3.5 的三个缺口，使 A 类迁移不再受「Material 忽略
> `dividerBefore`、只在多 group 时画标题」的限制；最终五处统一采用多 group 表达。

### 3.1 两类语义，不要混为一谈

- `PillDivider()`：纯视觉分段线，无文字。Miuix 画 `fillMaxWidth(0.8f)` 长线，
  其余三引擎画 `widthFraction = 0.2f` 的胶囊短线（`divider/PillDivider.kt`）。
- `PillHeaderDivider(title)`：「圆点 + 文字 + 圆点」的**分组 / 上下文标题**，文字常来自动态数据
  （源名、`quick_jump`、本地书选项、`全部/隐藏`），无引擎分支（`divider/PillHeaderDivider.kt`）。

`RoundDropdownMenuItem` 本身**没有** `dividerBefore` / 分组参数；旧菜单全靠调用方在 `ColumnScope` 里手画。

### 3.2 四引擎能力矩阵（统一前盘点，源码 `menu/AppActionMenu.kt`）

| 分支 | `dividerBefore` | `groupTitleBefore` | `AppActionGroup.title` | `children` |
|---|---|---|---|---|
| Mochi `MochiActions` | 组内任意位置 + 组首自动补线（最完整） | 仅非组首生效；组首被 `group.title` 无条件覆盖 | 组首小标题 | 一层，回调按扁平 index |
| COUI `CouiActions` | 组内任意位置 + 组首自动补线 | **完全忽略** | 仅 `groups.firstOrNull()?.title` 作 `headerText`，其余组丢 | 一层 |
| Miuix `toMiuixEntries` | 与标题一起降级为切换 `DropdownEntry` 桶（库画组间线） | **文本丢失** | **完全未读取** | 一层，`Cascading` 变体 |
| Material `LegacyActions` | **忽略** | **忽略** | 每组画 `PillHeaderDivider`（唯一完整保留标题文本） | **忽略且静默丢子项** |

一句话：**没有任何分支同时保住「任意位置纯分割线 + 任意位置标题文本 + 子菜单」**。
`AppChoiceOption.dividerBefore` 同理，只在 Mochi / COUI 生效，Miuix / Material 忽略。

> 上表是**统一前的能力盘点**，保留作设计依据。2026-11-11 起业务侧按 §3.0 口径不再依赖
> 标题文本，契约只保证「分段线可见 + 条目与稳定 ID 不丢 + 子项不丢」；`children` 在 Material
> 平铺为缩进项、其余三引擎走各自原生子菜单。当前行为以 `AppMenuDividerContractTest` 与源码为准。

### 3.3 生产盘点（迁移前只读快照，2026-11-11）

> 以下数字是**两轮迁移前**的盘点。A 类 5 处迁移后，菜单 content 内自绘 `PillDivider`
> 由 14 处减到 9 处（删掉 4 处 `dropDownMenuContent` + 1 处 `HomeScreen` 独立菜单）；
> A' 类第二轮又删掉 `RssScreen` 的 1 处，现为 **8 处**。这 6 处都改由
> `AppActionGroup` 组间补线表达。`PillHeaderDivider` 的 5 处数量未变，
> 其中只有 `TocScreen.kt:487` 在顶栏 content 里，其余 4 处见 §3.4 B'。

- 菜单 content 内自绘分割线 **19 处 / 11 个文件**（14 处 `PillDivider` + 5 处 `PillHeaderDivider`）；
  另有 11 处在 Sheet / Dialog / LazyColumn，与菜单迁移无关。
- 14 处 `PillDivider` 里 8 处是 `dropDownMenuContent = { dismiss -> ... }`，链路
  `ListScaffold` → `DynamicTopAppBar` → `TopBarOverflowMenu` → `AppAnchoredPanel(externalTrigger = true)`，
  **其实已经跑在新入口里**，只是 content 仍是手画；另 6 处在 `HomeScreen.kt:470` 与
  `ReadBookMenuBarFloating.kt:234,293,339,348,363`。
- `AppActionGroup` 盘点时生产调用 3 处（`BookshelfScreen`、`BookSourceEditScreen`、
  `RssSourceEditScreen`），全部只给 `id + actions`。
- 盘点时 **`dividerBefore` / `groupTitleBefore` / `AppActionGroup.title` / `children` 生产零使用**。
  本轮 A 类 5 处迁移后，「多 group 触发组间分段线」已成为真实菜单在用的路径；显式
  `dividerBefore` / `groupTitleBefore` / `group.title` / `children` 仍无生产调用方，
  只由 `AppMenuDividerContractTest` 的 13 个用例验证。

### 3.4 迁移分类

- **A 可迁 `AppActionMenu`：本轮 5 处已全部迁移**（见 §4.9）。原本只圈定「静态命令 +
  一条纯分段线」，实际连 `RssFavoritesScreen` 的动态分组列表、`BookSourceScreen` 的
  7 个带 `selected` 排序项、`HomeScreen` 的动态页面列表也一并按 `AppActionGroup` 表达。
  五处统一用**多个 `AppActionGroup`**（四引擎都靠组间补线），未用显式 `dividerBefore`。
  `AppActionGroup` 经 `ListScaffold` / `RuleListScaffold` → `DynamicTopAppBar` →
  `TopBarActionMenu` 的新声明式槽进入 `AppActionMenu`；`dropDownMenuContent` 旧槽保持不变，
  其余调用点零改动。
- **A' 顶栏 `dropDownMenuContent` 批量迁移（2026-11-11 第二轮，本轮 9 处）**：见 §4.10。
  11 个顶栏 `dropDownMenuContent` 调用点里 9 处是纯命令 / 动态分组列表，直接复用
  `menuGroups` + `onMenuAction`；动态分组统一用 `group:` 稳定前缀编码 ID
  （`"group:"` = 全部，`"group:$name"` = 具体分组），勾选态改用 `AppAction.selected`。
  `TocScreen` 随后在第三轮按「注册表 + 纯映射 + 先建基线」模式迁移（见 §4.11），
  因此顶栏 `dropDownMenuContent` **只剩 1 处**：`RssSourceScreen.kt:293`
  （伪子菜单 + 嵌套 `RoundDropdownMenu` + 「分组管理」不 dismiss，归入 B 类**刻意保留**）。
  另 `ReadBookMenuBarFloating.kt` 的 5 条 `PillDivider` 在阅读器浮层里，不走顶栏。
- **B 仍须留在 `AppAnchoredPanel`**（或先补实现）：
  - 伪子菜单（点击不 dismiss 外层、就地展开第二个 popup）：
    `RssSourceScreen.kt:298-331`、`ReadBookMenuBarFloating.kt:296-321`。
  - 复杂面板：动态长列表 + `trailingIcon` 勾选 + 滚动定位、5 条分段线、多个 `isSelected` 开关。
  该入口 `content` 在四引擎**原样透传**（Mochi 走 `MochiPopoverButton`、`dismiss` 换 `LocalMochiDismiss`；
  其余走 `RoundDropdownMenu(content = content)`），手画的 `PillDivider` / `PillHeaderDivider` 都保留。
- **B' 带文字标题的列表项菜单（口径已解除，但入口完全不同，务必别当成顶栏菜单）**：
  `RssScreen.kt:348`（`source.sourceName`）、`ExploreScreen.kt:435`（`item.bookSourceName`）、
  `TocScreen.kt:588`（`quick_jump`）、`BookshelfScreen.kt:825`（`all / hide`）都在
  **列表项或 `bottomContent` 上独立的 `RoundDropdownMenu`** 里，trigger 是卡片长按、
  `SmallToggleButton` 或 `AppTabRowIconButton`，**不是顶栏 MoreVert，不能复用 `menuGroups` 槽**。
  要拿原生效果需把 `AppActionMenu` 包在各自的 trigger 上，会改变长按 / 展开交互形态，
  必须单独建外观与行为基线。唯一落在顶栏 `dropDownMenuContent` 里的
  `TocScreen.kt:487`（`local_book_options`）已在第三轮迁移，标题文字按口径降级为
  组间纯分段线——**这是「文字消失」第一次真实发生在生产菜单上**，需真机确认可接受。
  另：`BookshelfConfigSheet.kt` 5 处 + `AddCustomModuleDialog.kt` 1 处的 `PillHeaderDivider`
  是 Sheet / Dialog 正文的分组标题，**根本不是下拉菜单**，不在迁移范围。
- **C 与本专题无关**：11 处非菜单调用保持现状。

### 3.5 三个明确能力缺口（1 / 2 已处理，3 未修）

1. **分组表达四引擎不一致 / 标题文本丢失 — 已按 §3.0 口径收口**：标题文字不再渲染，
   `dividerBeforeAt` 统一四引擎的分段线判定；Material 补 `dividerBefore` 与 `selected` 语义，
   并把 `children` 平铺为缩进项，不再静默丢 ID；COUI 去掉只认第一组的 `headerText`。
2. **Mochi 组首覆盖缺陷 — 已消失**：Mochi 分支不再传 `groupTitleBefore` / `group.title`，
   原 `if (isGroupStart) group.title else action.groupTitleBefore` 整段删除。
3. **`AppActionMenu` 无任意 content / 嵌套弹层能力 — 仍未修**：只有一层 `children`，
   `onAction` 统一收起，无法承载嵌套 popup、动态长列表、搜索框；只能靠 `AppAnchoredPanel`。
   本轮新增的 `TopBarActionMenu` 声明式槽只服务纯命令菜单，B 类仍走旧 content 槽。

> 本轮按原建议的顺序执行：**先补** `AppMenuDividerContractTest`（四引擎 × 纯分段线 /
> 标题降级 / 一层子菜单）与 `TopBarActionMenuContractTest`（四引擎顶栏槽基线），**再迁移** A 类 5 处。
> 仍未覆盖：Mochi / COUI 的 `children` 原生路径在 JVM 下只验证组合不崩，未断言子 ID 回调
> （Miuix 级联、Material 平铺已断言）；显式 `dividerBefore` / `groupTitleBefore` /
> `group.title` 仍无生产调用方，只由契约用例验证。

## 4. 已完成清单（按时间）

1. **能力铺底**：`AppMenuModels.kt`（稳定 ID 契约、trigger scope）、三入口、COUI backdrop
   （`AppTheme` 页面根 + `AppModalBottomSheet` 独立窗口）、`SettingItem.interactive` 消除双重点击语义。
2. **四引擎原生分支**：Mochi fork `0.2.1-menu1`（`MochiDropdown` / `MochiIconDropdown` /
   `MochiPopoverButton`、`MochiItem` 的 enabled/checked/divider/groupTitle/children）、
   COUI `Menu` + `CouiMenuAnchor`、Miuix `Window/OverlayIconDropdownMenu` + `Cascading` +
   HyperOS 4 `GlassIconDropdownMenu`、M3 沿用自绘菜单行。
3. **第一轮两个验证场景**：`ThemeConfigScreen` 主题风格选择（`AppChoiceDropdown`，
   `commitAfterDismiss = true`）、`BookshelfScreen` 书架更多（12 个命令 → `AppActionMenu`）。
4. **契约用例**：`AppMenuTriggerContractTest` 5、`AppMiuixMenuContractTest` 6、
   `AppAnchoredPanelContractTest` 5、`AppChoiceCompactContractTest` 6（含 2 个卡片 trigger 用例）。
5. **`TopBarOverflowMenu` 收编**：弹层统一到 `AppAnchoredPanel(externalTrigger = true)`，
   18 个顶栏页面的按钮外观完全保留。
6. **`compact` 形态定稿**：入口包装纯展示 trigger + Miuix 原生纯弹层；
   初版用 `WindowIconDropdownMenu` 会挤压卡片 trigger，已替换。
7. **阶段 C 纯单选 8 处**：`CharsetConfigSheet`、`ClickActionConfigSheet`、
   `TextProcessingSheet`、`GlobalThemePage`、`FontSelectGrid`（排序）、
   `BookSourceEditScreen`（书源类型）、`RssSourceEditScreen`（类型 + 布局）。
8. **阶段 C 命令菜单 2 处**：`BookSourceEditMenu`（10 个动作）、`RssEditMenu`（9 个动作）
   切到 `AppActionMenu`。各建一份 `internal enum` 动作注册表（稳定 ID + `@StringRes` 文案），
   UI 从它生成 `AppAction`，`onAction` 经纯函数 `bookSourceEditIntentFor` /
   `rssSourceEditIntentFor` 映射 `Intent`，未知 ID 不产生动作；菜单开合收归入口
   （`BookSourceEditRoute` 删 `menuExpanded`、RSS 删本地 `menu`）。
   顶栏 MoreVert 按钮改由入口渲染。新增 8 个 ID → Intent 基线用例，**迁移前先跑通、迁移后复跑通过**。
9. **分段线口径落地 + A 类 5 处迁移（本轮）**：按用户「分段线可以不保留文字」的口径，
   `AppMenuModels.dividerBeforeAt` 统一四引擎分段线判定，标题信号一律降级为纯线；
   补 Material `dividerBefore` / `selected` 语义 / `children` 平铺，去掉 COUI `headerText`
   与 Mochi 组首标题覆盖，补 `AppChoiceDropdown` 的 Material / Miuix compact 分段线支持。
   新增 `AppMenuDividerContractTest`（13 例）与 `TopBarActionMenuContractTest`（4 例）后，
   给 `TopBarOverflowMenu` 加 `TopBarActionMenu` 声明式入口，并由
   `DynamicTopAppBar` / `ListScaffold` / `RuleListScaffold` 新增可选 `menuGroups` + `onMenuAction`
   槽透传（旧 `dropDownMenuContent` 调用点零改动）。迁移 `BookshelfManageScreen`、
   `ReplaceRuleScreen`、`BookSourceScreen`、`RssFavoritesScreen`、`HomeScreen` 五处，
   全部用「两个 `AppActionGroup` + 稳定 ID 分派」表达原 `PillDivider()`；
   `BookSourceScreen.SortMenuItem` 与 `HomeScreen.showPageMenu` 等自绘/本地状态一并删除。
   迁移后这 5 个页面的 MoreVert 按钮改由入口渲染（与书架 / 两个编辑页一致）。
10. **顶栏 `dropDownMenuContent` 批量迁移 9 处（本轮）**：重新盘点 11 个顶栏 content 调用点后，
   把 9 处纯命令 / 动态分组菜单迁到 `menuGroups` + `onMenuAction`：
   `RuleSubScreen`、`HighlightTagRuleScreen`、`DictRuleScreen`、`TxtRuleScreen`
   （1–2 项规则导入 / 排序）、`RssScreen`、`ExploreScreen`、`ExploreMainRoute`
   （动态分组选择，`group:` 稳定前缀编码 ID）、`RemoteBookScreen`、`ImportBookScreen`
   （排序勾选，原自绘 `trailingIcon` 改 `AppAction.selected`）。
   `RssScreen` 原有一条 `PillDivider()`，用两个 `AppActionGroup` 表达；其余保持单组、不引入新线。
   新增 `TopBarMenuIdContractTest`（3 例静态契约：不再手画 content、每个字面量 ID 都有分派、
   动态分组保持 `group:` 编解码）。保留 `RssSourceScreen`（伪子菜单 + 嵌套 popup + 不 dismiss 项）
   与 `TocScreen`（按当前页整体切换菜单 + 动态 route）为顶栏 content 的仅剩 2 处。
   这 9 处 MoreVert 同样改由入口渲染。
11. **`TocScreen` 顶栏菜单迁移 + 行为基线（第三轮）**：这是顶栏 content 里最复杂的一处
   （按 `pagerState.currentPage` 整体切换两套菜单、3 项 `isSelected`、
   动态构造 `ReplaceEditRoute` 与导出文件名）。**先建基线再迁移**：新增
   `TocMenuActions.kt`（`TocMenuAction` 10 项注册表 + `TocMenuBehavior` sealed +
   纯函数 `tocMenuBehaviorFor`）与 `TocMenuActionsTest`（5 例，迁移前先跑通），
   UI 侧 `dropDownMenuContent` 换成按当前页构造的 `menuGroups` + `onMenuAction`，
   动态参数（书名 / 书源 / 时间戳文件名）仍在执行时按 state 计算，映射保持纯函数。
   原来的 1 条 `PillDivider` + 1 条 `PillHeaderDivider` 用 2–3 个 `AppActionGroup`
   表达（非本地书 2 组 1 条线、本地书 3 组 2 条线），书签页单组无分割线，结构一一对应；
   本地书组标题文字按口径丢弃。`TopBarMenuIdContractTest` 增 2 例，
   断言 `TocScreen` 已离开 content 槽、且全仓顶栏 content 只剩 `RssSourceScreen.kt`。

12. **规则编辑剪贴板菜单 3 处迁移（2026-11-11 第五轮）**：从 §8 第 4 条的 74 处独立
   `RoundDropdownMenu` 里选出结构完全同构的 3 处 Sheet：`RuleEditSheet`、
   `TagGroupRuleEditSheet`、`HighlightTagRuleEditSheet`。旧实现都是
   `Box { MediumTonalButton(onClick = showMenu); RoundDropdownMenu { 复制 / 粘贴 } }`，
   迁到单个 `AppActionGroup` + `"copy_rule"` / `"paste_rule"` 稳定 ID 分派，
   自绘 `leadingIcon` 改 `AppAction.icon`，删除 `showMenu` 本地状态。
   **按钮改由入口渲染**（Mochi 下即第四轮用户调好的 `MochiBarIconButton`），
   避免「完整按钮 trigger 再被入口包一层」导致按钮套按钮；`startAction` 的关闭按钮不变。
   粘贴仍是 `scope.launch { onPaste()?.let { ... } }`，各处字段赋值逐一保留。
   新增 `RuleClipboardMenuContractTest`（2 例静态契约：三处已离开手画菜单 / 自带 trigger，
   两个 ID 都有声明与分派分支）。`ReplaceEditScreen`（Screen 且另有一个分组下拉）与
   `DirectLinkUploadBottomSheet`（3 项且含 `context.selector` 二级选择）形态不同，
   **留下一批**。

13. **Sheet action 槽命令菜单 4 处迁移（2026-11-11 第六轮）**：沿用第五轮的同构形态，
   从 `AppModalBottomSheet` 的 `startAction` / `endAction` 里选 4 处
   「单图标 trigger + 纯静态命令 + 零状态零勾选」：`SourceLoginSheets`（3 项登录头 / 日志）、
   `GroupManageSheet`（4 项新建 / 同步）、`HighlightRuleConfigSheet`（4 项高亮规则）、
   `CloudTtsScreen` 的 HTTP TTS 编辑器（2 项复制 / 粘贴）。统一改为单个 `AppActionGroup` +
   稳定 ID 分派，删除 `menuExpanded` / `showMenu` / `expanded` 与手画的
   `Box { MediumTonalButton; RoundDropdownMenu }`，按钮交给入口渲染；
   `leadingIcon` 转 `AppAction.icon`，剪贴板 `scope.launch`、`showImportSheet` 等副作用
   逐条保留且仍在点击时求值。`CloudTtsScreen` 同文件另外 7 处形态不同的下拉
   （单选 / 动态列表 / 表单字段）**刻意不动**。刻意排除 `AllBookmarkScreen` 的顶栏
   `TopBarActionButton` 与 `ReaderBookSheet` 的 `CompactToolIconBox(selected = menuExpanded)`
   ——后者带展开态高亮，入口自持 `expanded` 后外部拿不到，需单独建行为基线。
   新增 `SheetActionMenuContractTest`（3 例：四处已离开手画菜单 / 每个 ID 有声明与分派分支 /
   `CloudTtsScreen` 剩余 7 处下拉的棘轮）；迁移前先跑该契约，3 例如期为红。

14. **「命令 + 独立开关」菜单 3 处迁移 + `checked` 能力补齐（2026-11-11 第七轮）**：
   `ContentEditSheet`（重置 + 正文开关）、`ChangeChapterSourceSheet`、
   `ChangeSourceSheet`（各 4 项校验 / 加载开关，后者另带 1 项书源管理命令）。
   旧实现的 `isSelected` 是**点击取反的独立开关**，应映射 `AppAction.checked`，
   不是 `AppChoiceDropdown` 的 `selectedId` 单选。**先补缺口再迁移**：发现
   `AppActionMenu.LegacyActions`（Material）只读 `action.selected`、静默丢掉 `checked`，
   而 Mochi / COUI / Miuix 三支都已映射；补齐 `isSelected` 与 `semantics.selected`
   （含 `children`）为 `selected || checked == true`，与 Miuix 的降级口径一致。
   三处统一为单个 `AppActionGroup` + 稳定 ID 分派，删除 `menuExpanded` /
   `showOptionsMenu` 与手画菜单；`ChangeSourceSheet` 的 `Row` 里第二个设置按钮、
   `if (!state.showToc)` 条件、列表项 `dropdownContent` 的另一套菜单全部保留。
   新增 `ToggleActionMenuContractTest`（3 例：Material `checked` 映射 / 三处已离开手画菜单
   且用 `checked =` / 每个 ID 有声明与分派分支），迁移前 3 例如期为红。
   **刻意排除**：`BookSourceScreen` 与 `RssSourceImportDialog` 的开关项
   点击后**不调用 `dismiss()`**（菜单保持展开以便连续切换），与入口「先 dismiss 再
   `onAction`」语义冲突。**该冲突已在第十轮解决**：不扩入口，改为导入面板
   「配置 ↔ 列表」两页切换（§4.17），「动作后保持展开」能力不再需要。
   另：`GlobalThemePage.kt:402` 是纯单选 + 设置文字行 trigger，属 `AppChoiceDropdown`
   簇，不与本批混做。Mochi / COUI 下开关态由旧 `isSelected` 高亮变原生勾选圈，
   属拿到原生效果的预期外观变化，**未真机复核**。

15. **知识 / 角色详情纯单选 3 处迁移（2026-11-11 第八轮）**：按「同目录、同构」原则
   取 `ui/book/knowledge/` 下 `BookKnowledgeDetailScreen` 的 `KnowledgeTypeSelector`
   与 `BookCharacterDetailScreen` 的 `ProfileVoiceTraitDropdown` / `ProfileRoleDropdown`。
   三处旧实现逐行同构：`Box { GlassCard(onClick = showDropdown); RoundDropdownMenu { ... } }`，
   选项是常量表 / 参数列表，选中值显示在卡片上。统一改为
   `AppChoiceDropdown(compact = true)`：`GlassCard` 去掉 `onClick` 变纯展示 trigger，
   锚点 `modifier = this.modifier` 挂到卡片根节点，由入口包装唯一可点节点；
   `options` 由 `KNOWLEDGE_TYPE_LABELS` / `options: List<String>` /
   `BookCharacterProfile.ALL_ROLES` 构造 `AppChoiceOption` + `toImmutableList()`。
   **`compact = true` 是必须的**：默认行形态在 Miuix 下由 `DropdownListSettingItem`
   渲染整行、`trigger` 槽不生效。`ProfileRoleDropdown` 的「再次点选已选项则清空」
   语义保留在 `onSelect` 里（`if (id == selectedRole) "" else id`）。
   新增 `KnowledgeChoiceMenuContractTest`（3 例：两文件已离开手画菜单 / 每个选择器有
   `selectedId` + `onSelect` / 反选语义未丢），迁移前 3 例如期为红。
   **刻意留下一批**：`GlobalThemePage.kt:402`（`TinySettingItem` 设置行 trigger，
   同文件 `:419` 已有 `AppChoiceDropdown(compact = true)` 模板）与
   `MangaReaderSheets.kt:468`（`NormalCard` 方块 trigger），形态不同不混做。
   迁移后菜单项新增原生选中态（旧实现无 `isSelected`），属预期外观变化，**未真机复核**。

16. **翻页动画 / 九宫格点击区域 compact 单选 2 处迁移（2026-11-11 第九轮）**：
   `GlobalThemePage` 的翻页动画（`TinySettingItem` 设置行，位于 `Row` 的
   `Modifier.weight(1f)` 中；同文件翻页速度已是 `AppChoiceDropdown(compact = true)`
   现成模板）与 `MangaReaderSheets` 的 `MangaClickAreaCell`（`NormalCard` 方块）。
   两处统一改为 `compact = true`：`TinySettingItem` / `NormalCard` 去掉 `onClick`
   保持纯展示，`modifier = this.modifier` 承接锚点，外层尺寸由入口 `modifier` 参数
   传入（`weight(1f)` / `fillMaxSize()`），避免包装层吃掉布局约束。
   `options` 由 `pageAnimEntries` / `MangaClickActionEntries` 构造
   `AppChoiceOption(id = 值.toString(), label = ...)`，`onSelect` 把 ID 转回 `Int`
   并保留 `ReadBook.book?.setPageAnim(-1)` + `ConfigUpdate.PageAnim` / `onPick`。
   无障碍名称用 `R.string.page_anim` 与 `R.string.manga_reader_click_areas`
   （values 与 values-zh-rCN 均已存在），不拿当前选中值当名称。
   新增 `CompactChoiceMenuContractTest`（3 例：两处已离开手画菜单 / 每个选择器有
   `selectedId` + `compact = true` + 锚点 / 本地化稳定名称），迁移前 3 例如期为红。
   **棘轮**：`MangaReaderSheets.kt:183` 的 `MangaSourceActionSquare` 菜单共用
   `ReaderMenuActionSquare` 的 expanded、形态不同，**刻意不动**；契约断言整文件
   `RoundDropdownMenu(` 只剩 1 处，防止顺手改掉。
   **收敛判断**：`AppChoiceDropdown(compact = true)` 的纯单候选已收完
   （§4.15 三处 + §4.16 两处），后续不再有可直接套模板的零状态簇。
   点击涟漪从组件自身移到入口 `Box(clickable)`，语义树位置可能变化，**未真机复核**。

17. **导入面板「配置页 / 列表页」两页切换 2 处（2026-11-11 第十轮）**：
   `BookSourceScreen` / `RssSourceImportDialog` 的导入选项原是混合菜单——命令项
   （自定义分组、选择导入状态）`dismiss()`，3 个 keep 开关不 `dismiss()` 以支持连续勾选，
   `AppActionMenu` 四分支统一「先收起再回调」，硬迁会让用户每改一项都要重开菜单。
   **换 UI 形态而非补入口能力**：`BatchImportDialog` 增加可选 `configContent`，
   顶栏出现「配置 ↔ 列表」切换按钮（`Icons.Default.Settings`）；配置页常驻
   `SwitchSettingItem`（直接回传新值，不再 `!旧值` 翻转）+ `SettingItem` 命令行，
   隐藏全选与导入按钮并垂直滚动；列表页行为不变。
   两处混合菜单与 `ImportOption` 包装删除，`BookSourceScreen` 只剩行内
   `BookSourceItemMenu` 一处 `RoundDropdownMenu`（棘轮断言锁住）。
   新增 `BatchImportConfigPageContractTest`（3 例：`configContent` 存在 / 两处已离开
   混合菜单 / 配置页 3 个常驻开关且开关方向正确），迁移前 3 例如期为红。
   双变体编译 `BUILD SUCCESSFUL`；`menu.*` + `topbar.*` + `importComponents.*`
   定向 **14 类 / 64 用例 0 失败**；`git diff --check` 与 IDE 诊断通过。
   **入口「动作后保持展开」待办随之作废**（§8 第 4 条含四引擎 `expanded` 所有权的
   实测更正）。配置页布局 / 滚动 / 四引擎外观**未真机复核**。

18. **替换规则顶栏命令菜单 + 直链上传规则菜单 2 处（2026-11-11 第十一轮）**：
   `ReplaceEditScreen` 顶栏 MoreVert 原是 `TopBarActionButton(onClick) + RoundDropdownMenu`
   两项（复制 / 粘贴规则，无图标）；`DirectLinkUploadBottomSheet` 的 `endAction` 原是
   `Box { MediumTonalButton; RoundDropdownMenu }` 三项（导入默认预设 / 复制 / 粘贴，
   都带 `leadingIcon`）。统一改 `AppActionMenu` + 单个 `AppActionGroup` + 稳定 ID
   （`copy_rule` / `paste_rule`；`import_default_rule` / `copy_direct_link_rule` /
   `paste_direct_link_rule`），旧 `leadingIcon` 转 `AppAction.icon`，规则编辑页保持无图标
   以免顺带改变外观；删 `showMenu` 与手画 trigger，按钮交给入口渲染。
   三个副作用逐条保留且点击时求值：`context.selector(state.directRulePresets)`、
   `sendToClip(GSON.toJson(...))`、`getClipText()` + `runCatching` 的 toast 兜底。
   **刻意保留**：`ReplaceEditScreen.GroupSelector` 是 `ExposedDropdownMenuBox` +
   `PrimaryEditable` 锚点的可编辑组合框（文本框 + 分组建议），`AppChoiceDropdown`
   只能选不能输入，会丢自由填分组能力，整文件留 1 处 `RoundDropdownMenu` 并用棘轮锁住。
   新增 `ReplaceDirectLinkMenuContractTest`（3 例：结构性 / 每个 ID 有声明与分派 /
   组合框与可编辑锚点棘轮），迁移前 3 例如期为红。双变体编译 `BUILD SUCCESSFUL`；
   `menu.*` + `topbar.*` + `importComponents.*` 定向 **15 类 / 67 用例 0 失败**；
   `git diff --check` 与 IDE 诊断通过。四引擎按钮外观与组合框**未真机复核**。

19. **三入口 × 四引擎契约收口（2026-11-11 第十二轮）**：补上反向清单里缺行为覆盖的两项。
   新增 `MenuChildrenCallbackContractTest` 4 例：`children` 子项回调（Mochi 真实弹层
   点父项 → 子项，断言回传子 ID 且菜单自行收起）与关闭所有权（Material / Miuix 单层菜单
   选完自动收起，业务侧不持 `expanded`）。COUI 二级弹层在 Robolectric 下不出现
   （父项点击直接收起），退化为源码契约 `CouiMenuItem(value = child.id, children = ...)`
   + `onAction(item.value)`，并写进测试 KDoc；**COUI 二级菜单外观仍需真机复核**。
   两处「例外」核对后确认已在 KDoc：`AppChoiceDropdown` 的 Miuix 默认行形态忽略
   `trigger` 槽、`AppChoiceOption.dividerBefore` 在 Miuix 默认行形态被忽略，故不重复建测试。
   定向 **16 类 / 71 用例 0 失败**；双变体编译 `BUILD SUCCESSFUL`；`git diff --check`
   与 IDE 诊断通过。仍未覆盖：COUI / Miuix 玻璃（HyperOS 4）路径的 ID 回调、
   语义树名称与 TalkBack 顺序（真机）。

20. **CloudTts 引擎页顶栏命令菜单 1 处（2026-11-11 第十三轮）**：旧实现是
   `Box { TopBarActionButton(onClick); RoundDropdownMenu }` 四项纯命令（导入 TTS /
   导出文件 / 复制 URL / 清除缓存），改 `AppActionMenu` + 单组 + 稳定 ID
   （`import_tts` / `export_tts_file` / `copy_tts_url` / `clear_tts_cache`），
   删 `expanded` 与手画按钮，`showHttpTtsImportSheet = true` 与三个 `CloudTtsIntent`
   逐条保留、点击时求值。**刻意保留同文件 6 处**：区域菜单是混合菜单
   （选项 + `cloud_tts_custom_region_action` 会清空 region 并展开输入框），
   `AppChoiceDropdown` 只能选不能带命令项；provider / model / style / role / format
   是动态单选，但 trigger 为 `MediumTonalButton`（无纯展示变体），换入口要先定
   「纯展示按钮」形态，属外观决策。
   新增 `CloudTtsTopBarMenuContractTest`（3 例：顶栏结构性 / 四个 ID 声明与分派 /
   同文件剩余 6 处 + 自定义区域命令项棘轮），迁移前 3 例如期为红；
   `SheetActionMenuContractTest` 的 CloudTts 棘轮同步 7 → 6。
   双变体编译 `BUILD SUCCESSFUL`；定向 **17 类 / 74 用例 0 失败**；
   `git diff --check` 与 IDE 诊断通过。四引擎顶栏按钮外观**未真机复核**。

21. **按钮玻璃与 Miuix 单帧错位（2026-11-11 第十四轮）**：真机反馈 COUI 下 ⋮ 缺玻璃底、
   Miuix HyperOS 4 单帧错位。COUI 侧 `CouiActions` 原本用普通 `IconButton`，绕过
   `rememberCouiCapsuleRenderer()` + `CouiTopAppBarButton` 的玻璃胶囊，改为原生胶囊按钮，
   非原生环境仍用 `IconButton` 兜底。Miuix 侧把 `MiuixActions` 的玻璃门禁从
   `isMiuixHyperOs4Enabled()` 改为跟随顶栏的 `LocalMiuixGlassTopBar`（与
   `TopBarActionButton` / `TopBarOverflowMenu` 同判定）。**单帧错位**的进一步真机信息
   （打开首帧 / 收起末帧、只有图标往左上飞、玻璃底不动）定位到 `GlassTransformPopup`：
   图标副本按 `anchor.contentBounds` 落位，未上报时退回整颗按钮的 `containerBounds`，
   而 `MiuixGlassIconButton` 自己不发布内容边界 → 副本落在按钮左上角，端点帧就跳一下。
   修法：按钮内容外包 `Box(Modifier.glassPopupAnchorContent(anchor))`。
   新增 `MenuTriggerGlassContractTest` 2 例锁分支选择。
   只跑 `compileAppCoreDebugKotlin`（用户要求控制测试成本）；玻璃观感与错位是否消失**待真机复测**。

22. **RSS 排序页顶栏主菜单 2 处（2026-11-11 第十五轮）**：`RssSortScreen` 旧实现是
   `TopBarActionButton + RoundDropdownMenu` 8 项，末项「重定向策略」再开二级
   `RoundDropdownMenu`（`RedirectPolicy.entries` 单选，选完还要手动关主菜单）。
   改 `TopBarActionMenu` + 单组：子项 ID = `redirect_` + `RedirectPolicy.name`
   （前缀常量 `REDIRECT_PREFIX`），父项降级为 `AppAction.children`（Mochi / COUI / Miuix
   原生级联，Material 平铺不丢 ID），选中态用 `AppAction.checked`；`showMainMenu` /
   `showRedirectMenu` 与两处手画菜单删除，收起交给入口。
   **刻意保留**：底部分组切换菜单挂在 `SmallToggleButton` 上（库无非可点的纯展示形态），
   仍用 `RoundDropdownMenu`，棘轮锁住。新增 `RssSortMenuContractTest` 3 例。
   本轮只跑 `compileAppCoreDebugKotlin` 与该测试类（用户要求控制测试成本），
   未跑整组 / 双变体；二级菜单真机展开与选中态**待复核**。

23. **详情页顶栏「更多」1 处（2026-11-11 第十六轮）**：`BookInfoScreen.BookInfoOverflowMenu`
   （内部 `RoundDropdownMenu`，19 个条件项 + 5 处 `isSelected`）折叠进 `TopBarActionMenu`：
   ID = `book_` + `BookInfoMenuAction.name`，分派用 `BookInfoMenuAction.entries.firstOrNull`
   反查（避免手写 ID 与枚举漂移）；条件可见性与勾选态逐条保留，本地 `expanded` 与整个
   旧函数删除。新增 `BookInfoMenuContractTest` 3 例（入口结构 / 19 项绑定 / 5 处勾选态来源）。
   本轮只跑 `compileAppCoreDebugKotlin` 与该测试类。真机条件分支与勾选渲染**待复核**。

## 5. 当前工作区未提交改动

已跟踪文件的修改（`git diff --shortstat`：**55 文件，+2064 / -1786**；不含未跟踪新增文件）：

- 依赖 / 构建：`settings.gradle`、`gradle/libs.versions.toml`、`app/mochi-compat.gradle`（fork + `mavenLocal()`）。
  ⚠️ **版本口径漂移**：`libs.versions.toml` 当前是 `mochi = "0.2.1-menu4"`，
  而本文件与 `mochi-menu-extension-spec.md` 的历史记录写的是 `menu3`/`menu1`（当时版本）。
  以 `libs.versions.toml` 为准；后续轮次引用 Mochi 能力时请先看实际解析到的版本。
- 宿主 / 组件：`AppTheme.kt`、`AppModalBottomSheet.kt`、`SettingItem.kt`、
  `TopBarOverflowMenu.kt`（本轮加 `TopBarActionMenu`）、`FontSelectGrid.kt`，
  以及本轮新增声明式槽的 `DynamicTopAppBar.kt`、`ListScaffold.kt`、`RuleListScaffold.kt`；
  第十轮给 `BatchImportDialog` 加可选 `configContent`（`importComponents/ImportComponents.kt`）。
- 第一轮与阶段 C 调用点：`ThemeConfigScreen.kt`、`BookshelfScreen.kt`、
  `CharsetConfigSheet.kt`、`ClickActionConfigSheet.kt`、`GlobalThemePage.kt`、
  `TextProcessingSheet.kt`、`BookSourceEditScreen.kt`、`BookSourceEditRoute.kt`、
  `RssSourceEditScreen.kt`，以及第一轮 A 类 5 处：`BookshelfManageScreen.kt`、
  `ReplaceRuleScreen.kt`、`BookSourceScreen.kt`、`RssFavoritesScreen.kt`、`HomeScreen.kt`；
  本轮 A' 类 9 处：`RuleSubScreen.kt`、`HighlightTagRuleScreen.kt`、`DictRuleScreen.kt`、
  `TxtRuleScreen.kt`、`RssScreen.kt`、`ExploreScreen.kt`、`ExploreMainRoute.kt`、
  `RemoteBookScreen.kt`、`ImportBookScreen.kt`；第三轮 `TocScreen.kt`；
  第五轮 `RuleEditSheet.kt`、`TagGroupRuleEditSheet.kt`、
  `HighlightTagRuleEditSheet.kt`；第六轮 `SourceLoginSheets.kt`、
  `GroupManageSheet.kt`、`HighlightRuleConfigSheet.kt`、`CloudTtsScreen.kt`；
  第七轮 `ContentEditSheet.kt`、`ChangeChapterSourceSheet.kt`、
  `ChangeSourceSheet.kt`（另改未跟踪目录下的 `AppActionMenu.kt`，见下）；
  第八轮 `BookKnowledgeDetailScreen.kt`、`BookCharacterDetailScreen.kt`；
  第九轮 `MangaReaderSheets.kt`（`GlobalThemePage.kt` 已在第一轮清单内，本轮再改）；
  第十轮 `ImportComponents.kt`（新增 `configContent`）、`BookSourceScreen.kt`、
  `RssSourceImportDialog.kt`；第十一轮 `ReplaceEditScreen.kt`、
  `DirectLinkUploadBottomSheet.kt`；第十三轮 `CloudTtsScreen.kt`
  （第六轮已改过同一文件，本轮再改顶栏）；第十五轮 `RssSortScreen.kt`；
  第十六轮 `BookInfoScreen.kt`。
- 测试：`MochiOverlaysAndBarsTest.kt`（既有基线的两处有意改名 / 断言调整）、
  `TopBarMenuIdContractTest.kt`（`app/src/test/.../topbar/` 为未跟踪目录，见下）。

未跟踪（新增，不计入上面的 diff 统计）：

- `app/src/main/java/io/legado/app/ui/widget/components/menu/`（4 个入口 / 模型文件；
  本轮改动 `AppMenuModels.kt`、`AppActionMenu.kt`、`AppChoiceDropdown.kt`）。
- `app/src/test/java/io/legado/app/ui/widget/components/menu/`（契约测试；历轮陆续加入
  `AppMenuDividerContractTest.kt`、`ToggleActionMenuContractTest.kt`、
  `KnowledgeChoiceMenuContractTest.kt`、`CompactChoiceMenuContractTest.kt`、
  `ReplaceDirectLinkMenuContractTest.kt`、`MenuChildrenCallbackContractTest.kt`）。
- `app/src/test/java/io/legado/app/ui/widget/components/topbar/`
  （`TopBarActionMenuContractTest.kt` 4 例、`TopBarMenuIdContractTest.kt` 3 例静态契约）。
- `app/src/test/java/io/legado/app/ui/widget/components/importComponents/`
  （`BatchImportConfigPageContractTest.kt` 3 例，第十轮）。
- `app/src/test/java/io/legado/app/ui/book/source/edit/`、`app/src/test/java/io/legado/app/ui/rss/`
  （两个 ID → Intent 基线类）。
- `app/src/main/java/io/legado/app/ui/book/toc/TocMenuActions.kt`、
  `app/src/test/java/io/legado/app/ui/book/toc/TocMenuActionsTest.kt`
  （第三轮的动作注册表与纯映射基线）。
- `docs/dev/native-menu-engines-plan.md`、`docs/dev/mochi-menu-extension-spec.md`、
  本文件 `docs/dev/native-menu-engines-handoff.md`。

## 6. 验证口径与精确命令

全程 PowerShell、仓库根目录、只跑定向：

```powershell
# 双变体 Kotlin 编译（同时触发 verifyConfigArchitecture）
.\gradlew.bat :app:compileAppCoreDebugKotlin :app:compileAppComicDebugKotlin --no-configuration-cache --console=plain

# 最近一轮的完整定向测试集（19 类 / 138 用例 / 0 失败）
.\gradlew.bat :app:testAppCoreDebugUnitTest `
  --tests "io.legado.app.ui.widget.components.menu.*" `
  --tests "io.legado.app.ui.widget.components.topbar.*" `
  --tests "io.legado.app.ui.book.source.edit.*" `
  --tests "io.legado.app.ui.rss.source.edit.*" `
  --tests "io.legado.app.ui.book.toc.*" `
  --tests "*MochiOverlaysAndBarsTest" --tests "*CouiFloatingAndMenusTest" `
  --tests "*TopBarActionButtonInteractionTest" --tests "*SheetGlobalConfigReadTest" `
  --tests "*ReadBookSheetStackTest" --tests "*ConfigUpdateActionsInvariantTest" `
  --no-configuration-cache --console=plain

# 静态门禁 / Android lint / 文本
.\gradlew.bat verifyConfigArchitecture --no-configuration-cache --console=plain
.\gradlew.bat :app:lintAppCoreDebug --no-configuration-cache --console=plain
git diff --check
```

第十至十二轮（导入面板、替换规则 / 直链规则菜单、契约收口）用的最小定向集：

```powershell
.\gradlew.bat :app:testAppCoreDebugUnitTest `
  --tests "io.legado.app.ui.widget.components.importComponents.*" `
  --tests "io.legado.app.ui.widget.components.menu.*" `
  --tests "io.legado.app.ui.widget.components.topbar.*" `
  --tests "*MochiOverlaysAndBarsTest" --no-configuration-cache --console=plain
```

统计用例数（PowerShell，读 test-results XML）：

```powershell
$files = Get-ChildItem "app/build/test-results/testAppCoreDebugUnitTest" -Filter "TEST-*.xml" -Recurse
$t=0; $f=0; $e=0
foreach($x in $files){ $doc=[xml](Get-Content $x.FullName); $t += [int]$doc.testsuite.tests; $f += [int]$doc.testsuite.failures; $e += [int]$doc.testsuite.errors }
Write-Output "classes=$($files.Count) tests=$t failures=$f errors=$e"
```

**最近一轮结果**：`:app:compileAppCoreDebugKotlin` / `:app:compileAppComicDebugKotlin`
双变体 `BUILD SUCCESSFUL`（只有既有的 `RemoteBookScreen` `VisualTransformation` deprecation 提示）；
19 类 **138 用例、0 failures / 0 errors**（三轮累计新增 27 例：分段线契约 13 +
顶栏槽 4 + 静态契约 5 + 目录菜单基线 5）；`verifyConfigArchitecture`、
`git diff --check`、IDE 静态诊断均通过。
`lintAppCoreDebug` **本轮未复跑**（用户取消了该次执行）；最近一次完整结果（第二轮）仍是
既有 1 个 error：`1 error / 32 warnings`，与再上一轮持平，lint XML 报告里 32 个改动的
Kotlin 文件命中数 **0**。第三轮只改 `TocScreen.kt` 与新增两个纯逻辑/测试文件，
编译与定向单测均通过，但 lint 结论待补跑确认。
按用户偏好未跑 `testAppComicDebugUnitTest` / `lintAppComicDebug` / `assemble` / 全量单测。

**第四轮（Mochi `0.2.1-menu3` 接入）验证**：双变体 Kotlin 编译 `BUILD SUCCESSFUL`；
新 fork 的三个入口改动未造成契约回归，定向 **19 类 / 138 用例 / 0 failures / 0 errors**；
`git diff --check` 通过。新增 `MochiAnchorMenuContractTest` **3 例单独跑通**
（三入口 anchor-only 结构、顶栏 `MochiBarIconButton` 壳、依赖升级不再硬编码 AAR 文件名），
这 3 例**不含在 138 的口径里**——完整定向集重跑被用户取消。
`lintAppCoreDebug` 仍未复跑，最近一次完整结论仍是既有 `1 error / 32 warnings`。

**第五 / 六轮验证**：第五轮双变体编译 `BUILD SUCCESSFUL`，`menu.*` + `topbar.*` 定向
9 类 / 49 用例 0 失败；第六轮同口径 **10 类 / 52 用例、0 failures / 0 errors**
（新增 `SheetActionMenuContractTest` 3 例），双变体 Kotlin 编译 `BUILD SUCCESSFUL`
（仅既有 `Icons.Filled.PlaylistAdd` / `PasswordVisualTransformation` deprecation 提示）。
`git diff --check` 通过（只有既有 4 个文件的 LF→CRLF warning），5 个改动文件 IDE 诊断 0 条。
按用户偏好仍未跑 `lintAppCoreDebug` / `testAppComicDebugUnitTest` / `assemble` / 全量单测，
四引擎 × 页面外观基线与真机效果也未复核。

**第七轮验证**：新增 `ToggleActionMenuContractTest` 3 例，迁移前 3 例如期为红、迁移后转绿；
`menu.*` + `topbar.*` 定向 **11 类 / 55 用例、0 failures / 0 errors**；
双变体 Kotlin 编译 `BUILD SUCCESSFUL`，`verifyConfigArchitecture` 一并执行通过；
`git diff --check` 通过（只有既有 4 个文件的 LF→CRLF warning），5 个改动文件 IDE 诊断 0 条。
`lintAppCoreDebug` / 真机外观 / TalkBack 仍未复核。

**第八轮验证**：新增 `KnowledgeChoiceMenuContractTest` 3 例，迁移前 3 例如期为红、
迁移后转绿；`menu.*` + `topbar.*` 定向 **12 类 / 58 用例、0 failures / 0 errors**；
双变体 Kotlin 编译 `BUILD SUCCESSFUL`，`verifyConfigArchitecture` 一并执行通过；
`git diff --check` 通过（只有既有 4 个文件的 LF→CRLF warning），3 个改动文件 IDE 诊断 0 条。
`lintAppCoreDebug` / 真机外观 / TalkBack 仍未复核。

**第九轮验证**：新增 `CompactChoiceMenuContractTest` 3 例，迁移前 3 例如期为红、
迁移后转绿；`menu.*` + `topbar.*` 定向 **13 类 / 61 用例、0 failures / 0 errors**；
双变体 Kotlin 编译 `BUILD SUCCESSFUL`，`verifyConfigArchitecture` 一并执行通过；
`git diff --check` 通过（只有既有 4 个文件的 LF→CRLF warning），3 个改动文件 IDE 诊断 0 条。
`lintAppCoreDebug` / 真机外观 / TalkBack 仍未复核。

**第十轮验证**：新增 `BatchImportConfigPageContractTest` 3 例。首次复跑时
`rssSourceImportUsesConfigPageInsteadOfMixedMenu` 仍为红——它把 `ImportGroupDialog` 里
「新增分组」的开关也算进配置页（实际 4 处而非 3 处）；改为只截 `configContent` 那段
lambda 计数、另加「配置页 3 + 分组对话框 1 = 4」棘轮后转绿。随后
`menu.*` + `topbar.*` + `importComponents.*` 定向 **14 类 / 64 用例、0 failures / 0 errors**
（连 `MochiOverlaysAndBarsTest` 51 例一起跑是 15 类 / 115 例）；
双变体 Kotlin 编译 `BUILD SUCCESSFUL`，`verifyConfigArchitecture` 一并执行通过。
`lintAppCoreDebug` / 配置页真机布局与滚动 / TalkBack 仍未复核。

**第十一轮验证**：新增 `ReplaceDirectLinkMenuContractTest` 3 例，迁移前 3 例如期为红
（结构性断言、ID 声明与分派、组合框棘轮全部命中旧代码），迁移后转绿。
`menu.*` + `topbar.*` + `importComponents.*` 定向 **15 类 / 67 用例、0 failures / 0 errors**
（连 `MochiOverlaysAndBarsTest` 51 例一起跑是 16 类 / 118 例）；
双变体 Kotlin 编译 `BUILD SUCCESSFUL`，`verifyConfigArchitecture` 一并执行通过。
`lintAppCoreDebug` / 四引擎按钮外观 / `GroupSelector` 组合框真机表现仍未复核。

**第十二轮验证**：新增 `MenuChildrenCallbackContractTest` 4 例（Mochi 子项回调 + 收起、
Material / Miuix 关闭所有权、COUI 子 ID 结构契约）；COUI 二级弹层在 Robolectric 里
点父项后不出现，故该引擎只留源码断言（见 §4.19）。
`menu.*` + `topbar.*` + `importComponents.*` 定向 **16 类 / 71 用例、0 failures / 0 errors**
（连 `MochiOverlaysAndBarsTest` 51 例一起跑是 17 类 / 122 例）；
双变体 Kotlin 编译 `BUILD SUCCESSFUL`，`verifyConfigArchitecture` 一并执行通过；
`git diff --check` 通过。`lintAppCoreDebug` / COUI 二级菜单外观 / 玻璃路径 / TalkBack 未复核。

**第十五轮验证（按用户要求只跑最小集）**：新增 `RssSortMenuContractTest` 3 例，通过；
`:app:compileAppCoreDebugKotlin` `BUILD SUCCESSFUL`。**未跑**整组定向、双变体编译、
`verifyConfigArchitecture`、lint。二级菜单真机展开 / 选中态未复核。

**第十六轮验证（同样只跑最小集）**：新增 `BookInfoMenuContractTest` 3 例，通过；
`:app:compileAppCoreDebugKotlin` `BUILD SUCCESSFUL`。条件分支与勾选渲染未上真机。

**第十三轮验证**：新增 `CloudTtsTopBarMenuContractTest` 3 例，迁移前 3 例如期为红；
`SheetActionMenuContractTest` 的 CloudTts 棘轮 7 → 6 后仍绿。
`menu.*` + `topbar.*` + `importComponents.*` 定向 **17 类 / 74 用例、0 failures / 0 errors**
（连 `MochiOverlaysAndBarsTest` 51 例一起跑是 18 类 / 125 例）；
双变体 Kotlin 编译 `BUILD SUCCESSFUL`，`verifyConfigArchitecture` 一并执行通过；
`git diff --check` 通过。`lintAppCoreDebug` / 四引擎顶栏按钮外观 / 同文件 6 处表单下拉未复核。

## 7. 已知失败与既有债（与本系列无关，不要顺手"修好"）

- **`:app:lintAppCoreDebug` 失败**：唯一 error 是
  `app/src/main/java/io/legado/app/ui/widget/dialog/PhotoDialog.kt:86` 的
  `ThreadConstraint`（`@WorkerThread` 与 UI 线程冲突）。该文件本系列从未改动、不在
  `lint-baseline.xml` 中。已登记为独立待办，需单独修复或更新 baseline。
  lint 报告另有 32 warnings 与大量 baseline 过滤项。
- **历史全量单测**：`testAppCoreDebugUnitTest` 曾跑 3010 用例，9 个失败集中在漫画文件 URI
  （`URISyntaxException: file://C:%5C...`，Windows 路径编码）与 `AppScaffoldInsetsTest`
  状态栏高度断言，与菜单 / 设置行语义无代码交集。`testAppComicDebugUnitTest` / `assemble`
  按用户要求**未跑**。
- `appComicDebug` 的 `R.jar` 增量产物曾缺失，`--rerun` 重建后恢复。
- 编译期只有既有的 `BasicTextField` / `VisualTransformation` deprecation 提示
  （`RemoteBookScreen.kt:536`），与改动无关。
- 旧 `RoundDropdownMenu` / `RoundDropdownMenuItem` / `RoundDropdownMenuLazy` 全部保留、未标废弃；
  旧补丁参数 `glassAnchor` / `mochiAnchorMaxWidth` / `LocalGlassPopupContent` /
  `LocalUseWindowPopupForMochiMenu` 仍在（阶段 D 才清理）。

## 8. 下一步建议（按优先级）

三轮已完成「补用例 → 修缺口 → 迁 A 类 5 处 → 迁顶栏 content 9 处 → 迁 `TocScreen`」；
第四轮（用户侧）完成 Mochi 交互与按钮外观改造；第五至十一轮按同构切片迁完 19 处
（累计 46 处）。**第 1 条已交付，后续从第 2 条起。**

1. **按钮外观一致性 —— 已完成（2026-11-11 第四轮，用户侧接入并调好真机效果）**：
   fork 交付 `0.2.1-menu3`（B 方案 anchor-only `MochiDropdownMenu`，把 `MochiOverlay`
   从 `DropdownCore` 拆出），本仓库 `libs.versions.toml` → `mochi = "0.2.1-menu3"`、
   `settings.gradle` 加只对 `io.github.feice11.*` 生效的 `mavenLocal`、
   `mochi-compat.gradle` 改按 `mochi-compose-` 前缀匹配（升级不再改脚本）。
   三个 Mochi 分支全部改为「入口唯一可点节点 + `boundsInWindow()` 锚点上报」：
   `MochiChoice` / `MochiActions` → `MochiDropdownMenu`，`AppAnchoredPanel` →
   `AppMochiPopover`；库按钮与大圆消失、开合变成**点击松手后**，按下即开与按住滑选放弃。
   `Icon` 锚点穿项目 `MochiBarIconButton`（与同排搜索按钮同款），`Row` 锚点由 trigger 自绘；
   fork 侧另修了收起末段的圆形遮罩淡出。详见 `docs/dev/mochi-menu-extension-spec.md` §11
   与 `docs/dev/native-menu-engines-plan.md` §9。
   本工作区已补 `MochiAnchorMenuContractTest`（3 例静态契约）锁住三入口结构、
   顶栏按钮壳与依赖升级路径；双变体编译 + 定向 19 类 / 138 用例通过（见 §6）。
   **仍缺**：四引擎 × 页面的外观基线（截图 / 语义树 / 尺寸）与 COUI / Miuix / Material
   的按钮统一；**不要在没有外观基线的情况下继续改按钮代码**。
   ~~完成基线后把 18 个 `TopBarOverflowMenu` 页面从 `externalTrigger = true` 切主入口~~
   **2026-11-11 第十五轮实测更正**：这条已基本完成——16 个列表页改传 `menuGroups` 走
   `TopBarActionMenu`，`dropDownMenuContent` 只剩 `RssSourceScreen` 1 处（刻意保留，见第 2 条）。
   `TopBarOverflowMenu.kt:55` 的 `externalTrigger = true` 现在只服务那 1 页，
   不必再单独排期；剩余只是四引擎顶栏按钮外观统一，随第 1 条基线一起收。
2. **顶栏 `dropDownMenuContent` 只剩 1 处**：`TocScreen` 已迁完（§4.11）；
   `RssSourceScreen.kt:293` 的伪子菜单 + 嵌套 popup + 不 dismiss 项属 §3.5 缺口 3，
   **刻意保留**，除非先补 `AppActionMenu` 的任意 content / 嵌套弹层能力，
   不要硬塞进一层 `children`。
3. **§3.4 B' 的 4 处列表项菜单**（上一版误归为顶栏，现已修正）：`RssScreen.kt:348`、
   `ExploreScreen.kt:435`、`TocScreen.kt:588`、`BookshelfScreen.kt:825`。文字口径已解除，
   但它们的 trigger 是卡片长按 / 图标按钮，要拿原生效果必须把 `AppActionMenu` 包到各自
   trigger 上，**会改变长按等交互形态**，需单独建外观 + 行为基线后再动。
4. **51 处独立 `RoundDropdownMenu` 业务调用点（2026-11-11 第十六轮后源码实测）**：
   `RoundDropdownMenu(` 文本命中 55 处，扣除组件定义 1 处、三入口内部实现 3 处
   （`AppAnchoredPanel` 2 处 + `AppActionMenu.LegacyActions` 1 处）即为 51 处业务调用点；
   口径沿革：第五轮前 74 → 71 → 67 → 64 → 61 → 第九轮后 59 → 第十轮后 57 →
   第十一轮后 55 → 第十三轮后 54 → 第十五轮后 52 → 第十六轮后 51（**更正**：第十三/十四
   轮写的 55 是多算 1 处，实际当时为 54，已按当前源码重算）。
   旧文档「79+」是更早的估计。第五至十六轮累计迁完 23 处
   （§4.12–§4.18、§4.20、§4.22、§4.23）：
   规则编辑剪贴板 3、Sheet action 槽命令 4、「命令 + 独立开关」3、
   知识 / 角色详情纯单选 3、compact 纯单选 2、导入面板两页切换 2、
   替换规则顶栏 / 直链规则菜单 2、CloudTts 引擎页顶栏 1、RSS 排序页主菜单 2、
   详情页顶栏「更多」1。
   **已作废的待办**：`BookSourceScreen` / `RssSourceImportDialog` 的混合菜单原先被认为
   需要入口支持「动作后保持展开」，第十轮改成 `BatchImportDialog` 的
   「配置 ↔ 列表」两页切换（§4.17）后不再需要，**不要为它扩展 `AppAction`**。
   **实测更正（推翻本文上一版此处 ⚠️）**：四引擎的 `expanded` 并非都由库自持——
   COUI 的 `expanded` 就在入口自己手里（`AppActionMenu.kt` 的 `onItemClick` 显式
   `expanded = false` 后才回调），Material 分支的 `dismiss()` 也是入口自己调；
   Miuix 弹层自带 `collapseOnSelection: Boolean`（`CascadingListPopupLayout` 的默认值就是
   `true`），本仓库 `AppActionMenu.kt` 5 处 + `AppChoiceDropdown.kt` 2 处全部显式传
   `true`——改成按项传值即可，前三个引擎技术上都能做到「点项不收起」。
   真正有成本的是 Mochi（点项经 `onExpandedChange` 收起）。所以放弃该能力的理由是
   「为 2 个页面在三个入口 × 四分支上扩展 `AppAction` 级字段不划算」，
   **不是「技术不可行」**，上一版那句「未必能从外部阻止收起」不准确。
   **收敛判断**：可直接套三入口模板的「纯静态选项 + 单 trigger + 零状态」簇已收完；
   剩余 51 处大多各自带行为约束——动态列表 / 表单字段（`CloudTtsScreen` 顶栏已迁，
   剩 6 处动态单选 / 混合菜单；`BookInfoEditScreen`）、共用 expanded 的方块菜单（`ReaderMoreActionsSheet`、
   `MangaSourceActionSquare`）、长按 trigger 的列表项菜单（4 处）、
   被多页复用的通用设置组件（`TinySettingItems` / `SettingItem`）、
   顶栏 / 工具按钮（`AllBookmarkScreen`、`ReaderBookSheet`，后者还带展开态高亮）。
   **这些不宜继续按静态模板批量套**，每类要先建各自的行为或外观基线。
   `ReplaceEditScreen.GroupSelector` 是可编辑组合框（`ExposedDropdownMenuBox` +
   `PrimaryEditable`），`AppChoiceDropdown` 只能选不能输入，**刻意保留**（§4.18）。
   ⚠️ 这批迁移会改变各自 trigger 按钮的渲染所有者，与第四轮刚定好的按钮样式耦合，
   动手前先确认外观基线覆盖这些页面。Sheet / Dialog 正文里的 `PillHeaderDivider`
   不是菜单，别算进来。
5. **给 `compact` 的 Miuix 分支补 HyperOS 4 玻璃锚点变形**（复用 `MiuixGlassActions` 的
   `AppGlassOverlayState` 注册路径），并把「默认行形态 `trigger` 槽不生效」写进契约。
6. **`ReaderMoreActionsSheet` 纯单选收尾**：`ImageStyleDropdown` / `PageAnimDropdown` 共用
   `ActionSquareHost` 的一个 `expanded`，方块按钮形态复杂，需单独建外观 / 行为基线。
7. **契约测试剩余项（第十二轮已收口大部分）**：`children` 子 ID 回调（Mochi 行为 +
   COUI 结构）、关闭所有权已由 `MenuChildrenCallbackContractTest` 锁住；
   「`trigger` 槽不生效」与「Miuix 默认行形态忽略 `dividerBefore`」核对后确认早已在 KDoc，
   不再重复建测试。**仍缺**：COUI / Miuix **玻璃路径**的 ID 回调（需 `AppGlassOverlayState`
   与 HyperOS 4 环境，只能真机验）与菜单项语义树名称 / TalkBack 顺序。
8. 用户允许时补跑 `testAppComicDebugUnitTest` / `lintAppComicDebug` / `assemble`，复核 9 个既有失败。
9. **阶段 D**：清理旧补丁参数与 locals，删旧组件（`RoundDropdownMenuLazy` 生产零调用但
   `RoundDropdownMenuLazyTest` 约束 intrinsic 测量契约，别在第一轮删）；届时再评估
   `groupTitleBefore` / `group.title` 这两个只作分段线信号的字段是否可以直接从模型移除。

## 9. 关键文件索引

| 主题 | 路径（`app/src/main/java/io/legado/app/` 下省略前缀） |
|---|---|
| 三入口 / 模型 | `ui/widget/components/menu/{AppMenuModels,AppChoiceDropdown,AppActionMenu,AppAnchoredPanel}.kt` |
| 分段线判定 | `ui/widget/components/menu/AppMenuModels.kt`（`dividerBeforeAt`） |
| 顶栏声明式槽 | `ui/widget/components/topbar/{TopBarOverflowMenu,DynamicTopAppBar}.kt`（`TopBarActionMenu` + `menuGroups`）、`ui/widget/components/list/ListScaffold.kt`、`ui/widget/components/rules/RuleListScaffold.kt` |
| 旧组件 | `ui/widget/components/menuItem/{RoundDropdownMenu,RoundDropdownMenuItem}.kt` |
| 分割线 | `ui/widget/components/divider/{PillDivider,PillHeaderDivider}.kt` |
| 顶栏收编 | `ui/widget/components/topbar/TopBarOverflowMenu.kt`、`TopBarButton.kt` |
| 玻璃宿主 | `ui/widget/components/AppGlassOverlay.kt`、`ui/theme/AppTheme.kt` |
| COUI backdrop | `ui/theme/AppTheme.kt`、`ui/widget/components/modalBottomSheet/AppModalBottomSheet.kt` |
| 命令菜单样本 | `ui/main/bookshelf/BookshelfScreen.kt:698-753`、两个 `*SourceEditScreen.kt` |
| 注册表模式 | `ui/book/source/edit/BookSourceEditScreen.kt`（`BookSourceEditMenuAction`）、`ui/rss/source/edit/RssSourceEditScreen.kt`（`RssSourceEditMenuAction`）、`ui/book/toc/TocMenuActions.kt`（`TocMenuAction` + `TocMenuBehavior`） |
| 契约测试 | `app/src/test/java/io/legado/app/ui/widget/components/menu/`（含 `AppMenuDividerContractTest`）、`.../topbar/`（`TopBarActionMenuContractTest`、`TopBarMenuIdContractTest`）、`.../ui/book/source/edit/`、`.../ui/rss/source/edit/`、`.../ui/book/toc/TocMenuActionsTest` |
| 长期方案 | `docs/dev/native-menu-engines-plan.md`（§9 是实施记录）、`docs/dev/mochi-menu-extension-spec.md` |

## 10. 未验证风险（全部真机 / 人工）

### 已核对的真机外观（第一批，2026-11-11，书源管理顶栏 MoreVert × 四引擎）

用户提供 4 张 `BookSourceScreen` 顶栏菜单真机截图（19:40:51 / 19:41:04 / 19:41:15 / 19:41:28）。
按视觉特征判断（**对应关系待用户确认**）：图 1 = Mochi、图 2 = Material、图 3 = COUI、
图 4 = Miuix。

| 维度 | 核对结果 |
|---|---|
| 12 项是否齐全 | ✅ 四张都完整给出「分组管理 → 反序」，无丢项 / 无重复 |
| 分组标题文字 | ✅ 四张都没有分组标题字样（符合「分段线可以不保留文字」口径） |
| 分段线 | ⚠️ Material / Mochi / Miuix 能看到细线；COUI 用**逐项卡片间距**表达分组，看不到线 |
| 选中态 | 各库原生不同：Mochi 圆形勾选徽标、Material 主色文字 + 勾、COUI 细勾无高亮、Miuix 主色胶囊高亮 |
| 面板形态 | Material / Mochi / Miuix 是单块圆角面板，COUI 是「每项一张卡片」——属库原生，非本系列引入 |
| 长菜单高度 | ⚠️ 图 1、图 3 的最后一项贴到屏幕底边；图 2 面板底边可见但只到「智能排序」；图 4 底边可见 |

**结论**：「条目齐全 / 分组位置 / 无标题文字」三项符合契约，外观基线记为 **1/4 页面通过**。
**按钮问题（同日真机反馈，第十四轮处理）**：COUI 下 ⋮ 缺玻璃底、Miuix HyperOS 4 下有单帧错位。
前者根因明确——`CouiActions` 用普通 `IconButton`，绕过了 `rememberCouiCapsuleRenderer()` +
`CouiTopAppBarButton` 的玻璃胶囊；已改走原生胶囊按钮（非原生环境保留 `IconButton` 兜底）。
Miuix 侧把玻璃门禁从 `isMiuixHyperOs4Enabled()` 改成跟随顶栏的 `LocalMiuixGlassTopBar`。
单帧错位的真机补充信息是「打开首帧 / 收起末帧，**图标**往左上飞，玻璃底不动」，据此定位到
`GlassTransformPopup` 用 `anchor.contentBounds`（未上报时退回整颗按钮的 `containerBounds`）
定位过渡里那份图标副本：`MiuixGlassIconButton` 自己不发布内容边界，副本被放在按钮左上角，
端点帧就表现为图标跳一下。修法是在按钮内容外包一层
`Box(Modifier.glassPopupAnchorContent(anchor))`，副本按图标真实矩形落位。**待真机复测。**
待确认：① 四张图的引擎对应关系；② 图 1 / 图 3 底部是否**可上下滑动**（能滑则只是缺滚动提示，
不算缺陷；不能滑则需给面板加高度上限——Miuix 有 `maxHeight` 参数，Mochi / COUI 需确认库能力）；
③ COUI「逐项卡片 + 无选中高亮」是否接受，若要统一需在 COUI 侧自定义 item，成本较高。
未覆盖页面：书架、阅读器设置、书源 / RSS 编辑、CloudTts 引擎页（第二批）。


- COUI 触点定位与材质（Android 14+）、Miuix 玻璃锚点变形（API 33+）、
  Mochi 融球滑选与带卡片 trigger 的按钮形变。
- TalkBack（尤其玻璃分支 `anchorContent` 复用 trigger 时短暂多一个带 `contentDescription` 的图标节点）、
  root 主题切换时的 popup 路径、Sheet 内 COUI 菜单采样。
- **顶栏外观（两轮累计 17 处，最大真机风险）**：两个编辑页、书架、A 类 5 处、A' 类 9 处的
  MoreVert 均已改由 `AppActionMenu` 渲染；四引擎下的按钮尺寸、Material 合并胶囊、
  COUI / Mochi 按钮形变、Miuix 玻璃锚点全部未真机复核。
  ~~18 个页面仍走兼容路径~~（第十五轮实测更正：16 个列表页已走 `menuGroups` →
  `TopBarActionMenu`，只有 `RssSourceScreen` 1 页仍走 `externalTrigger = true`，
  该页外观随其余复杂面板一起未验证）。
- 8 处紧凑卡片 / 图标 trigger 在四引擎下的实际布局（含 `FontSelectGrid` 的 48dp 对齐），
  Mochi `MochiDropdown` 把卡片放进库按钮后的尺寸与按压形变。
- 分割线迁移后的视觉一致性：Miuix 长线 vs 其它三引擎短胶囊线，四引擎组间线的间距 / 粗细差异。
  A 类 5 处原本就没有文字，本轮**没有**发生「文字消失」；真正会出现可感知文字变化的是
  B' 4 处 `PillHeaderDivider(title)` 菜单，迁移前建议按页面再确认一次。
- 两轮共 14 处迁移菜单的业务副作用（下载/导出、文件选择器 launcher、QR 扫描、
  排序 + snackbar、动态分组切换、pager 滚动、规则导入、书源管理跳转、远程书籍排序等）
  只由编译、`TopBarActionMenuContractTest` 的槽契约与 `TopBarMenuIdContractTest` 的
  **静态 ID 一致性契约**间接保证。静态契约只能发现「ID 声明了但分派漏写 / 拼错」，
  **不能证明动作正确**；仍未为每处单独建 ID → 行为基线单测，需手工 / 真机走一遍。
- 迁移中把自绘 `leadingIcon`（`MenuItemIcon`、18dp 排序图标）与 `trailingIcon`
  （条件 Check）换成 `AppAction.icon` / `AppAction.selected`，四引擎的图标尺寸、tint、
  Check 位置由各库自绘，与旧自绘行不像素等价；同样未真机复核。
- **按钮样式不一致（2026-11-11 用户实测确认，非 Mochi 特有）**：用户观察到「搜索按钮、
  主题风格设置项」与同页其它按钮样式不同。**机制**：`AppMenuTriggerScope`
  （`AppMenuModels.kt:69-93`）把 `trigger` 定义为纯展示内容、不自带点击语义，
  按钮由四引擎各自包装 —— Mochi 走 `MochiDropdown.buttonContent` / `MochiIconDropdown.icon`
  的**库按钮**（`AppChoiceDropdown.kt:233-248`、`AppActionMenu.kt:152-174`），
  Miuix 默认甚至完全忽略 trigger、改渲染 `DropdownListSettingItem`
  （`AppChoiceDropdown.kt:100-118`），COUI / Material 用 `Box(clickable)` 或 M3 `IconButton`。
  这些库按钮**不读** `TopBarButton.kt` 的 `TopBarButtonStyle` / `SeriesButton` /
  Mochi `MochiBarIconButton` 适配，也不参与 `TopBarActionsRow` 合并胶囊、
  `SettingItem` 的 `SettingCard` / `SplicedColumnDivider` 行样式。
  **两个具体来源**：① 主题风格设置项 `ThemeConfigScreen.kt:287-318` 用
  `AppChoiceDropdown` + `SettingItem(interactive = false)` trigger，Mochi 下整张卡片被塞进
  库按钮的 `buttonContent`，多一层库按钮圆角 / 背景 / 按压形变，与同页裸 `SettingItem` 不搭；
  ② 按代码顶栏搜索按钮走 `TopBarActionButton`（`TopBarButton.kt:246-426`，四引擎均有
  专门适配，Mochi 有 44dp 对齐的 `MochiBarIconButton`），MoreVert 走 `AppActionMenu` 库按钮；
  **但用户截图里放大镜与竖三点两个都是大圆**，不能排除搜索入口也来自库按钮或另一套
  大圆组件，仅凭截图无法确定各自来源（上一版推断已按截图修正，见规格 §11.1）。
  搜索输入框本体 `SearchBar.kt:111-158`、搜索框尾部排序按钮（`FontSelectGrid` /
  `CharsetConfigSheet` 的 `AppChoiceDropdown(compact = true)`）又是第三套来源。
  仍在 `externalTrigger = true` 兼容路径上的顶栏溢出按钮（现仅 `RssSourceScreen`）还是老外观，
  所以两种 MoreVert 之间也会互相不一致。
  **四引擎都有该差异，Mochi 最显眼**（库按钮带 Press 滑选与按钮变面板形变）；
  fork 是外部 AAR `0.2.1-menu1`，仓库内没有 Mochi 库按钮源码。
  迁移前就存在的引擎差异（`TopBarButton` 四分支、`CompactSearchField` 无分支、
  `SettingItem` 的 COUI / Mochi 分组）与本系列无关，需分开判断。
  **定案（用户二次反馈后修订）**：用户提供了 Mochi 真机截图，确认两个按钮被库套成
  **大圆**（白底放大镜、粉底竖三点），并明确要求改成「点击松手才弹菜单」，即放弃
  `trigger = Press` 按下即开 + 按住滑选 + 按钮变面板形变。因此**交互与外观要一起改**，
  详见 `docs/dev/mochi-menu-extension-spec.md` §11（已修订，推翻 spec §0 决策①）：
  主方案 `trigger = Click` + `buttonStyle = Bare`；备选 anchor-only 重载让按钮 100% 归 App，
  Mochi 分支结构对齐 COUI。fork 到位后按 §11.4 消费，本工作区先不动代码。
  **仍未确认**：截图对应的具体页面 / 这两个按钮各自的组件来源，以及四引擎对比的外观基线；
  fork 改动落地后仍需按 §8 第 1 条补真机截图与语义树验收。
