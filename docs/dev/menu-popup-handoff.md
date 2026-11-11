# 会话交接：设置项下拉菜单受控弹层 → 各引擎原生化

> 仓库：`d:/Project/legado-with-MD3`，分支未核对，**所有改动均未提交、未暂存**。
> 交接时间：2026-11-11；2026-11-12 续作核对并推进了 §5.5 的一部分，见 §7。
> 下一个会话先读本文件，再按「下一步」推进。

## 0. 用户偏好（务必遵守）

- **测试尽量少跑，只跑受影响的用例**（用户原话：“测试尽量少跑或者只跑影响的测试，要不然太慢”）。
  用 `--tests` 过滤，不要动不动跑 `testAppCoreDebugUnitTest` 全量或主验证集。
- 用户会切引擎真机观察（COUI / Miuix / Mochi），反馈往往是现象描述（“在左侧”“没有圆角”“不算完全原生”），
  需要先定位到具体组件与分支再改，不要凭现象猜引擎。
- 交付说明要写：改了什么、验证命令与结果、**未验证风险**（真机/性能/其它平台）。

## 1. 本轮目标与完成情况

把设置项下拉从「自绘 `RoundDropdownMenu`」统一到 `AppChoiceDropdown` 的**受控弹层**
（`embeddedExpanded` / `onDismissRequest`），并让四套引擎在每种锚点形态下都落到各自原生面板。

已完成：

1. **受控弹层与锚点**：`SettingItem` / `TinySettingItems` / `ListSettingItem` / `CompactSettingItems`
   统一由行自己持有开合，菜单放在右侧取值区；菜单 lambda 为 `@Composable BoxScope.() -> Unit`，
   `AppChoiceDropdown` 传 `modifier = Modifier.matchParentSize()`。
2. **COUI 左侧锚点**：反编译 `coui-0.0.1-alpha9.aar` 确认
   `CouiMenuAnchor.getPositionInWindow()` 在无锚点节点 / 无 clickPosition 时回退 `Offset.Zero`
   → 弹层跑到窗口左上角。`CouiChoice` 受控分支已把 `modifier.couiMenuAnchor(couiAnchor)` 挂到容器上。
   三个设置行包装器不再提前 `return` 走 `CouiSettingMenu`（其 `Menu` 用整行 bounds → 行左端展开），
   `CouiSettingMenu` 函数已删除，只保留 `canUseCouiSettingMenu()`。
3. **Miuix 玻璃**：`isMiuixGlassMenuAvailable()`（`menuItem/RoundDropdownMenu.kt`）是唯一判定，
   绑 `AppScaffold` 的玻璃设施：`LocalAppGlassOverlay` + `LocalHasMiuixPopupHost` + SDK 33+
   + `useHyperOs4Components`。新 `menuItem/MiuixGlassMenuHost.kt` 把弹层注册进坐标根，
   三个锚点形态共用（顶栏 Icon / 列表 Row / 受控容器）。
4. **图标过渡动画**：`Modifier.glassPopupAnchor` 内部是
   `.graphicsLayer { alpha = if (anchor.contentHidden) 0f else 1f }`（**布尔瞬时**切换），
   过渡中位移/缩放的图标副本由 `GlassIconDropdownMenu(anchorContent = ...)` 画。
   所以 Row / 非受控 compact 必须传 `anchorContent = { scope.trigger() }`；受控模式没有 trigger 节点，留空。
5. **触发形状裁剪**：`menu/TriggerShape.kt` 的 `Modifier.clipTriggerShape(shape)` 是两个入口共用实现。
   `AppActionMenu` 新增 `triggerShape: Shape = CircleShape`（Row 触发惯例是圆形/胶囊），
   Mochi / COUI / Miuix / Material 四个 Row 包装节点全部裁剪，修掉“按钮没有圆角 / 水波纹溢出圆角”。
   `AppChoiceDropdown` 的 `triggerShape: Shape? = null` 需调用方显式给（阅读菜单翻页动画两张卡传 12dp）。

## 2. 工作区改动清单（原 12 改 + 4 新；两轮续作后 18 改 + 5 代码新增，均未提交）

修改：

| 文件 | 作用 |
|---|---|
| `app/.../ui/widget/components/menu/AppChoiceDropdown.kt` | 四引擎入口、受控模式、triggerShape、Miuix glass host 接线 |
| `app/.../ui/widget/components/menu/AppActionMenu.kt` | 四引擎 Row 分支、MiuixGlassActions/RowActions、triggerShape |
| `app/.../ui/widget/components/menuItem/RoundDropdownMenu.kt` | 唯一玻璃判定 `isMiuixGlassMenuAvailable()`；玻璃面板（无 morph） |
| `app/.../ui/widget/components/settingItem/SettingItem.kt` | 受控菜单、BoxScope 菜单 lambda、matchParentSize |
| `app/.../ui/widget/components/settingItem/TinySettingItems.kt` | 取值 Chip 内挂受控菜单（已去掉 row 间接层与整行 Box） |
| `app/.../ui/widget/components/settingItem/ListSettingItem.kt` | 去掉 COUI 提前 return |
| `app/.../ui/widget/components/settingItem/CompactSettingItems.kt` | 同上 |
| `app/.../ui/widget/components/coui/CouiSettingMenu.kt` | 删除 `CouiSettingMenu`，保留 `canUseCouiSettingMenu()`（注意 CRLF） |
| `app/.../ui/book/read/sheet/GlobalThemePage.kt` | 翻页动画两张卡传 `triggerShape = RoundedCornerShape(12.dp)` |
| `app/src/test/.../menu/AppChoiceCompactContractTest.kt` | 受控弹层 3 引擎用例（含 Mochi）+ EmbeddedMenu 48dp 锚点容器 |
| `app/src/test/.../menu/SettingItemAnchorContractTest.kt` | 锚点结构契约（含 COUI 锚点必须挂容器、包装器不得再用整行锚点） |

新增：

| 文件 | 作用 |
|---|---|
| `app/.../ui/widget/components/menu/TriggerShape.kt` | 共用的形状裁剪扩展 |
| `app/.../ui/widget/components/menuItem/MiuixGlassMenuHost.kt` | 玻璃弹层坐标根宿主 |
| `app/src/test/.../menu/MenuTriggerShapeContractTest.kt` | 2 用例：默认 CircleShape、四分支都裁、共用一份实现 |
| `app/src/test/.../menu/MiuixGlassMenuContractTest.kt` | 5 用例：判定唯一、host 唯一注册、Row/compact 接 host+锚点、anchorContent、受控回落 |

续作（2026-11-12）额外改动：

| 文件 | 作用 |
|---|---|
| `app/.../ui/book/read/sheet/TextProcessingSheet.kt` | 替换规则过滤的 compact 触发传 `RoundedCornerShape(10.dp)` |
| `app/.../ui/main/homepage/HomepageModuleManageSheet.kt` | 筛选按钮传 `IconButtonDefaults.extraSmallRoundShape` |
| `app/.../ui/book/readaloud/cloudtts/CloudTtsScreen.kt` | 5 处 `MediumTonalButton` 触发全部传同源形状 |
| `app/src/test/.../menu/MenuTriggerShapeContractTest.kt` | 新增 1 用例，锁定固定几何触发的形状同源（共 3 用例） |

第二轮续作（Indication 解耦，详见 §8）额外改动：

| 文件 | 作用 |
|---|---|
| `app/.../ui/widget/components/button/series/SeriesIconButton.kt` | 默认 `indication` 改吃 `LocalIndication.current` |
| `app/.../ui/widget/components/topbar/TopBarButton.kt` | 4 处写死 ripple 改吃 `LocalIndication.current` |
| `app/.../ui/widget/components/navigation/AppNavigationBar.kt` | 保留导航项专用无界 32dp ripple，加注释说明是例外 |
| `app/src/test/.../components/IndicationSourceContractTest.kt`（新增） | 2 用例：组件不得写死 ripple、导航栏例外必须保留 |

## 3. 最后一次验证（可直接复述）

```powershell
cd d:/Project/legado-with-MD3
.\gradlew.bat :app:compileAppCoreDebugKotlin --no-configuration-cache --console=plain
.\gradlew.bat :app:testAppCoreDebugUnitTest --tests "io.legado.app.ui.widget.components.menu.*" --tests "io.legado.app.ui.widget.components.menuItem.*" --tests "io.legado.app.ui.widget.components.IndicationSourceContractTest" --tests "io.legado.app.ui.widget.components.MochiOverlaysAndBarsTest" --tests "io.legado.app.ui.widget.components.CouiFloatingAndMenusTest" --tests "*TopBarActionButtonInteractionTest" --no-configuration-cache --console=plain
git diff --check
```

结果（2026-11-12 两轮续作后）：`compileAppCoreDebugKotlin` **BUILD SUCCESSFUL**（45s）；
**32 类 / 176 用例 / 0 失败**（新增 `IndicationSourceContractTest` 2 用例，并补跑
`TopBarActionButtonInteractionTest`，测试 6m47s）；`read_lints` 相关文件 0 诊断；
`git diff --check` 干净。
编译只有既有的 deprecation 警告（首次交接记录 `CloudTtsScreen.kt` 的 `Icons.Filled.PlaylistAdd`、
`SourceLoginSheets.kt` 的 `PasswordVisualTransformation`；续作编译另见
`TextProcessingSheet.kt:513` 的 `BasicTextField`），与本次改动无关。

> 提交前若需要更宽口径，再跑一次 `menu.* + menuItem.* + Mochi* + AppThemeEngineTest`
> （上一轮 191 用例 / 0 失败），或按 AGENTS.md 的主验证集；**平时不要跑全量**。

## 4. 关键坑位（下个会话别再踩）

- **COUI**：`CouiMenuAnchor` 未注册节点 → `Offset.Zero` → 弹层窗口左上角。受控模式没有 trigger 节点，
  必须显式把 `couiMenuAnchor` 挂到容器。`canUseCouiSettingMenu()` 依赖 `CouiShadowRenderer()` 的
  进程级静态缓存 + SDK 34+；若真机上 COUI 页面降级成 M3 面板，先查这里（可用真机日志）。
- **Miuix**：`ListPopupLayout` 用 `parentLayoutCoordinates` 定位，`IntRect.Zero` 时**直接不渲染**；
  所以受控菜单容器必须 `Modifier.matchParentSize()`，且父容器不能是 0 尺寸。
- **玻璃 gate**：`LocalAppGlassOverlay` 只在 `AppScaffold` 判定
  （Miuix + `useHyperOs4Components` + SDK 33+ + 原生采样可用）时才有值；
  **Robolectric 里一律 null**，玻璃分支真机外只能靠源码契约测试覆盖。
- **库 internal**：`GlassPopupAnchor.cornerRadius / floating / contentHidden` 都是 internal，
  设置只能走 `Modifier.glassPopupAnchor(anchor, cornerRadius, floating)`；
  `glassPopupAnchorRow` 只上报 bounds、不设圆角也不隐藏。
- **图标动画**：`glassPopupAnchor` 会瞬时隐藏原控件；不传 `anchorContent` 就是“图标直接消失再出现”。
  自绘 trigger **不要**挂 `glassPopupAnchorContent`（那是给自带 surface 的玻璃按钮“只复制图标”用的），
  弹层自绘面板时需要复制整个控件，`iconRect` 会回落到 `containerBounds`。
- **形状**：`CircleShape` == `RoundedCornerShape(50)`，对 32dp 圆按钮和 `fillMaxWidth` 胶囊都对；
  方形卡片触发必须显式传自己的 `triggerShape`。
- 仓库是 CRLF；用 `write_to_file` 新建/重写过的文件要留意 `git diff --check` 与换行符告警。

## 5. 已明确的未完成项（按优先级，就是「接下来做什么」）

1. **真机回归（最高优先，我这边无法验证）**
   - COUI（Android 14+ / HyperOS）：书源列表 ⋮、设置行下拉 —— 锚点是否在右侧取值区、面板是否原生、
     按压反馈是否圆角。用户上一轮说 COUI 按钮没圆角，本轮已修，待确认。
   - **Mochi：用户明确说“我没测”**。看 Row 包装裁剪是否生效、`MochiDropdownMenu(anchorBounds)` 位置是否正确。
   - Miuix + HyperOS 4 组件：书源列表 ⋮ 的玻璃 morph、图标副本是否跟着移动、起点 16dp 圆角是否贴合
     `SmallPlainButton`；紧凑卡片的 12dp 起点圆角是否与真实卡片一致（主题可覆盖卡片圆角）。
   - 阅读菜单「翻页动画」两张卡的 M3 水波纹是否已被裁进 12dp 圆角。
2. **受控模式取值文本仍是“瞬间隐藏 → 面板缩回后出现”**：要走原生淡出需 `glassPopupAnchorValue`，
   但它只发 `dropdownValueBounds`，且 `cornerRadius/floating` 是 internal；需要等库开口子或接受现状。
3. **`RoundDropdownMenu` 的自绘行页面**（阅读菜单、换源、字体、章节切换等几十处）：仍是
   `GlassDropdownPopup` 玻璃面板 + 项目自绘行、**没有 morph**。要统一得给调用方加锚点参数，属于另一刀，
   别顺手重构。
4. **`AppChoiceDropdown.MiuixIconChoice`**（顶栏图标单选）仍走库 `Window/OverlayIconDropdownMenu`，
   不是玻璃；顶栏 `AppActionMenu` 的玻璃路径由 `LocalMiuixGlassTopBar` 控制，两者口径不同。
5. **已完成** 其它 `compact = true` 触发内容的 `triggerShape`：
   - 7 处几何固定的触发：`TextProcessingSheet`（10dp，与 `CompactTextProcessTool` 同源）、
     `HomepageModuleManageSheet` + `CloudTtsScreen` 共 6 处 `MediumTonalButton`
     （`IconButtonDefaults.extraSmallRoundShape`，与 `SeriesButton` 默认 shape 同源），
     并加 1 条源码契约用例防漏传 / 漂移。
   - 剩余 8 处方卡触发 + `GlobalThemePage` 两张卡统一改吃 `resolveBaseCardShape(...)`
     （`card/GlassCard.kt`）：`BaseCard` 与菜单入口共用同一份「主题圆角覆盖 + Mochi
     连续曲率轮廓」解析，不再写死 dp。紧凑方卡 12dp 提成 `CompactCardCornerRadius`，
     `TinySettingItem` / `NormalCard` / 两张阅读菜单卡同源。方案与排除项见 §9。
   - 2 处纯图标触发（`FontSelectGrid`、`CharsetConfigSheet`）保持 `null` 是正确的，不要补。
   - **仍需真机确认**：`overrideBaseCardCornerRadius` 开启或 Mochi 引擎下，波纹裁剪与
     卡片实际轮廓是否贴合。Robolectric 里 `LocalAppUiConfiguration` 是默认值，
     源码契约只能锁住「取同一份解析」，覆盖不到实际取值。

## 6. 建议的接手顺序

1. 先 `git diff` 通读改动文件（尤其 `AppChoiceDropdown.kt` / `AppActionMenu.kt`），确认与本文描述一致。
2. 问用户要先做哪一项真机回归，拿到现象后再定位分支；**不要直接改 §5 的第 2~4 项**。
3. 若用户要求提交，先跑一次 §3 的两条命令 + `git diff --check`，按仓库规范写提交说明；
   **截至 2026-11-12 续作，仍未执行过任何 git 写操作**（未暂存、未提交）。

## 7. 续作记录（2026-11-12）

- 核对：工作区已切换为「设置项下拉受控弹层」这一轮；此前那轮三入口改造已提交，
  见 `docs/dev/native-menu-engines-handoff.md`，与本轮**不是同一改动集**，
  不要拿旧快照的 12 处迁移 / 分割线专题来套当前代码。
- 用户提到的 artifact 目录 `session-handoff.md` **未找到**，该目录只有 `menu-refactor-plan.md`；
  **以本文件为准**。
- 复跑：§3 两条命令均通过（编译 24s、受影响测试 2m14s，30 类 / 173 用例 0 失败）。
- 推进：§5.5 的 7 处固定几何触发补齐，详见 §2 续作表与 §5.5。真机回归（§5.1）、
  受控淡出（§5.2）、自绘行重构（§5.3）、`MiuixIconChoice`（§5.4）**均未动**。
- 新发现：`BaseCard` 主题动态圆角导致剩余 8 处不能硬编码，连已传的 `GlobalThemePage`
  12dp 也需真机复核；这是 §5.5 剩余工作的主要难点，**先做设计决策再动手**。
- 相邻问题：`HomepageModuleManageSheet` 的 `MediumTonalButton` trigger 没有透传
  `modifier = this.modifier`（`MediumTonalButton` KDoc 明确要求），COUI 锚点在该页可能仍会丢；
  与本轮形状裁剪是两件事。**已在 §10 修复**：静态证据充分，不必等真机。
- 第二轮续作见 §8：组件侧已去掉写死的 ripple，主题 provider 等 fork 的库级 `Indication`
  落地后再加（当前注入只能是 no-op 或丢反馈）。**截至本文件更新，仍未执行任何 git 写操作**。

## 8. Indication 解耦与 fork 改造方案（2026-11-12 第二轮续作）

### 8.1 本地已做：去掉写死的 ripple

| 文件 | 改动 |
|---|---|
| `ui/widget/components/button/series/SeriesIconButton.kt:85` | 默认参数 `indication: Indication? = ripple(bounded = true)` → `LocalIndication.current` |
| `ui/widget/components/topbar/TopBarButton.kt:360,407,680,709` | 4 处 `ripple(bounded = true)` → `LocalIndication.current`（`liquidGlassEnabled` 时仍为 `null`） |
| `ui/widget/components/navigation/AppNavigationBar.kt:146` | **刻意保留** `ripple(bounded = false, radius = 32.dp)`，导航项专用无界反馈，不是「忘了分发」，已加注释 |
| `app/src/test/.../components/IndicationSourceContractTest.kt`（新增） | 2 用例：组件不得再写死 `ripple(`、导航栏例外必须保留 |

解耦后，这些组件与三入口的 `Box(clickable)` 一样，全部读 `LocalIndication`——主题层注入什么就用什么。

### 8.2 为什么 `AppTheme` 的 provider 暂不加

COUI / Mochi 的库级 `Indication` 还不存在（现在只有 Modifier 扩展）。此时注入只能是两种结果：
回落 `ripple()`（等于 no-op），或 `null`（大面积丢按压反馈）。所以**先不加 no-op provider**，
把组件侧解耦做完即可。等 fork 落地，只需在 §8.5 的两处加 provider 就全链路生效。

### 8.3 库层 Indication 化可行性（给 fork 的结论）

- **COUI `couiButtonFeedback` → 可以改。** 调用形态是 `Modifier.couiButtonFeedback(interactionSource, enabled, list)` +
  `indication = null`（`SettingItem.kt:146,151`、`RoundDropdownMenuItem.kt:166,169`），
  说明是基于 `InteractionSource` 的绘制型反馈。本机**没有** `io.github.itoseo:coui-material3:0.0.1-alpha9`
  的 AAR / sources jar（`~/.gradle/caches`、`~/.m2`、仓库内均无），需你在 fork 里确认是否纯绘制；
  若含缩放/形变，按 Mochi 的方式拆。
- **Mochi `mochiPressDeform` → 不能整体改。** 已直接读到 fork 源码
  （`C:/Users/www13/.m2/repository/io/github/feice11/mochi-compose/0.2.1-menu4/mochi-compose-0.2.1-menu4-sources.jar`
  的 `ui/feice/mochi/effect/elastic/MochiPressDeform.kt`）：`PressDeformNode` 是
  `DelegatingNode + LayoutModifierNode + SuspendingPointerInputModifierNode`，在
  `MeasureScope.measure` 里用 `graphicsLayer` 做 `scaleX/scaleY/translationX/Y`，
  自己 `awaitMochiPress` 接管手势、处理滚动容器等待与弹簧回弹。`IndicationNodeFactory.create()`
  返回 `DelegatableNode`，标准用法是 `Modifier.Node(), DrawModifierNode`（见 vendored
  `MiuixIndication`），只在 `ContentDrawScope` 里 `drawContent()` 后画叠层；不改测量、
  不变换子内容、不消费指针事件。硬塞成 Indication 会与 `clickable` 的手势节点顺序冲突
  并改变测量，风险高。
- **建议 fork 走「拆分 + 并存」，不要二选一**：
  1. 新增 `MochiGlowIndication` / `CouiIndication`（绘制型高光/alpha 叠层），供主题层注入 `LocalIndication`；
  2. 保留 `mochiPressDeform` / `couiButtonFeedback` 给需要形变、跟手的组件显式叠加；
  3. 库组件内部 `indication` 默认参数统一改成 `LocalIndication.current`，别再写死。
- **可直接照抄的模板**：vendored `modules/miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/utils/MiuixIndication.kt`：
  `@Immutable class ... : IndicationNodeFactory`，`create(interactionSource): DelegatableNode` 返回
  `Modifier.Node(), DrawModifierNode`；`onAttach()` 里 `interactionSource.interactions.collect` 收集
  Press/Hover/Focus，`Animatable` 驱动 alpha，`ContentDrawScope.draw()` 里 `drawContent()` 后 `drawRect`。
  **必须实现 `equals` / `hashCode`**（模板 `:44-51`），否则 Compose 比较 indication 实例时会反复重建节点。
  Compose foundation 为 `1.13.0-beta01`（`gradle/libs.versions.toml:14`），该 API 可用。

### 8.4 fork 落地后本地要改的两处 provider

- Mochi：`AppTheme.kt:199-201`，`MaterialThemeWrapper` **内层**、包住 `ProvideMochiTheme`
  （或写在 `MochiTheme.kt:46-50` 的 content 内）。
- COUI：`AppTheme.kt:218-226`，`ProvideCouiTheme` 内层（或 `CouiTheme.kt:79-81`）。
- 顺序硬约束：必须在 `MaterialThemeWrapper`（`ThemeComponents.kt:333-338` 的 `MaterialExpressiveTheme`）
  **内层**，放外层会被 M3 默认值按就近原则覆盖；`AppTheme.kt:181-184` 不要动。
- **Miuix 分支不要动**：`MiuixTheme` 已自己注入 `MiuixIndication`（库 `MiuixTheme.kt:33-37,63-67`），
  且该分支不套 `MaterialThemeWrapper`。

### 8.5 验证与遗留风险

- 本轮只跑了受影响的定向集，见 §3（更新后的用例数）。
- fork 改造后需真机复核：COUI 设置行 / 顶栏 / 导航项、Mochi 按钮形变是否仍与高光叠加而不打架、
  Miuix 库组件是否被外层 provider 误覆盖、Popup 独立 composition 是否继承到 `LocalIndication`。
- `indication = null` 显式关闭的 23 处中，与库 Modifier 反馈成对的 6 处
  （`TopBarButton.kt:441`、`AppTabRowIconButton.kt:134`、`AppCouiTabRow.kt:119`、
  `RoundDropdownMenuItem.kt:169`、`FloatingBottomBar.kt:122,124`、`SettingItem.kt:151`）
  在 fork 提供 Indication 后要重新评估：是继续走 Modifier，还是改吃主题注入，避免双层反馈。

## 9. §5.5 收尾：形状单一真源（2026-11-12 第三轮续作）

**状态订正**：§3 / §7 里「仍未执行任何 git 写操作」的声明已过时——§1~§8 的改动
已于 2026-11-12 提交为 `2c4d0856b`，§9 提交为 `4dcd2b0d9`。后续提交状态以 `git log`
为准，本文件不再维护。

### 方案

`card/GlassCard.kt` 把 `BaseCard` 原来的「读 `overrideBaseCardCornerRadius` → 选
`RoundedCornerShape` / `MochiRoundedRectangle`」抽成公开的
`@Composable @ReadOnlyComposable resolveBaseCardShape(cornerRadius: Dp = MiuixCardDefaults.CornerRadius)`。
`BaseCard` 与 10 处 compact `triggerShape` 共用这一份结果；紧凑方卡的 12dp 提成
`CompactCardCornerRadius`，供 `TinySettingItem`、`NormalCard`、阅读菜单两张卡同源引用。

排除的两个方向：

- `AppMenuTriggerScope` 回写 shape：入口的 `Modifier` 在父节点组合时已求值，
  trigger 内容还没执行；改成 State 会引入额外重组并破坏 `@Stable` 契约。
- `CompositionLocal` 下发：方向反了，入口 `Box(clickable)` 是卡片的父节点，
  读不到子层提供的 Local。

### 改动范围

- 卡片层：`GlassCard.kt`（解析函数 + 常量 + `BaseCard` 复用）、`TinySettingItems.kt`（12dp 改常量）。
- 调用点：8 处方卡触发（RSS / 书源编辑、点击配置、知识 / 角色详情、漫画点击区域）
  传 `resolveBaseCardShape()`；`GlobalThemePage` 两张卡传
  `resolveBaseCardShape(CompactCardCornerRadius)`，删掉本地 `RoundedCornerShape(12.dp)`。
- 文档 / 契约：`TriggerShape.kt` 与 `AppChoiceDropdown.kt` 的 KDoc 指向真源；
  `MenuTriggerShapeContractTest` 新增 1 用例，锁定单一真源、8 处补齐、12dp 同源、
  两张卡不再硬编码、2 处纯图标保持 `null`。

### 未动 / 未验证

- 未动：`AppChoiceDropdown.kt:225,233` 受控玻璃锚点的硬编码 `12.dp`（属 §5.2，
  是玻璃 morph 起点半径，与 `triggerShape` 不是一回事）；§5.1 / §5.3 / §5.4 仍未动。
- 未验证：真机上「覆盖卡片圆角」开关（0–40dp）与 Mochi 引擎下的波纹贴合；
  Robolectric 里 `LocalAppUiConfiguration` 取默认快照，源码契约覆盖不到实际取值。

## 10. COUI trigger 锚点 modifier 透传（2026-11-12 第四轮续作）

**静态证据，不需要等真机**：

- `AppChoiceDropdown.kt:491-495` 的 `CouiChoice` 把 `Modifier.couiMenuAnchor(couiAnchor)`
  注入 `AppMenuTriggerScopeImpl.modifier`；非受控分支里 trigger 是唯一注册该锚点的节点。
- `MediumTonalButton.kt:13-18` 的 KDoc 明写：`onClick = null` 的纯展示调用方
  「必须把入口给的锚点 modifier 透传给本组件，否则菜单定位会丢」。
- `AppActionMenu.kt:286-291` 的 `CouiActions` 同样把锚点注入 scope.modifier，
  Row trigger 与 `AppChoiceDropdown` 一样依赖透传。
- `HomepageModuleManageSheet.kt` 的分组筛选按钮漏传；`CloudTtsScreen.kt:1018` 的区域菜单
  （`AppActionMenu` 的 Row trigger）只写 `Modifier.fillMaxWidth()`，也没透传。
  对照 `CloudTtsScreen.kt` 另 5 处与 `TextProcessingSheet.kt:300` 都传了 `this.modifier`。

**修复**：两处补透传 —— 筛选按钮补 `modifier = this.modifier`；区域菜单改成
`this.modifier.fillMaxWidth()`，保留布局同时挂上锚点。`SettingItemAnchorContractTest` 新增
`pureDisplayTriggersForwardScopeModifier`，锁定 COUI 两个分支仍注入锚点，且两处文件的所有
`MediumTonalButton(onClick = null)` 都透传 `this.modifier`。第二处缺陷是该契约用例跑出来的，
不在 §7 原始记录里。`CloudTtsChoiceMenuContractTest` 原先把透传数冻结为 5（等于默许
第 6 个 trigger 漏传），现改为与 `onClick = null` 数量联动，不再留例外。

**未验证**：真机上 COUI 引擎下首页分组筛选弹层的实际位置。契约只保证 modifier 到位，
`CouiMenuAnchor.getPositionInWindow()` 的运行时行为仍归 §5.1 真机回归。
