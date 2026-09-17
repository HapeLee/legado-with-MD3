# ui/about 转 CMP 的切片审计（M5-1）

> 与 `feature-slicing-audit-tagrules.md` 同规格：先审计依赖闭包，再决定切法。
> 本文件记录 **M5 批次 2（低风险管理页）** 第一站 `ui/about` 的实测结论与分片计划。

## 1. 「边界小」的判断不成立

`feature-catalog.md` 给 `ui/about` 的备注是「边界小，适合首个 package relocation 样板候选」。
**该判断写在 M1 时代，实测不成立**：`ui/about` 本体 7 文件 / 1442 行、表面只有 4 处
`android.*` import（`Build` / `Application` / `Uri` / `Intent`），但**依赖闭包牵出的缺口资产
是它本体的 1.5 倍以上**，且夹着一个 miuix blur 平台岛。

结论：`about` 不能单片落地，按「一次只改一个风险维度」拆成 M5-1a ~ M5-1d。

## 2. 依赖闭包实测

### 2.1 Screen 侧缺口（about 渲染要用、但不在共享层的组件）

| 资产 | 现位置 | 规模 | 真实调用方 | 平台依赖 | 归处 |
|---|---|---|---|---|---|
| `TextCard` | `:core:ui/src/main` | 90 | **39** | 0 | designsystem/commonMain（**M5-1a 已搬**） |
| `MarkdownBlock` | `:app` ui/widget/components/text | 894 | 6 | `android.content.Intent` x2、Splitties `clipboardManager` x1、`R.drawable.ic_copy` | 需先契约化（M5-1b） |
| `CrashLogSheet` | `:app` ui/widget/components/log` | 67 | **仅 about** | `FileDoc`、`R.string` | **`:feature:about` 私有组件**（单调用方不进 core） |
| `FileDoc` | `:app` utils | - | **23** | 深 SAF/DocumentFile 依赖 | **不迁**；共享侧改不透明引用（同 tagrules 的 `Uri`->String 判据） |

已在共享层的（无需处理）：`AppScaffold` / `SplicedColumnGroup` / `SettingItemWithDivider` /
`AppAlertDialog` / `AppCircularProgressIndicator` / `SettingItem` / `AppText` /
`GlassMediumFlexibleTopAppBar` / `GlassTopAppBarDefaults` / `TopBarNavigationButton` /
`PrimaryButton` / `MediumTonalButton` / `AppModalBottomSheet` / `AnimatedTextLine` / `AppIcon`。

### 2.2 ViewModel 侧要新建的平台能力契约

`AboutViewModel`（264 行）里这些是纯平台能力，共享层拿不到：

- **更新检查**：`AppUpdate.gitHubUpdate`（`:app` 的 `help/update`，全局 object 门面）
  ⇒ 契约化 `UpdateInfo`（`tagName`/`updateLog`/`downloadUrl`/`fileName` 四字段已是纯数据）。
- **诊断**：崩溃日志列表 / 读取 / 清理、保存日志到备份目录、`System.gc()` +
  `CrashHandler.doHeapDump`、`Runtime.exec("logcat -d")`、`ZipUtils` 打包。
- **资源文本**：`context.assets.open("privacyPolicy.md")` 读内建 md。
- **环境信息**：`BuildConfig.VERSION_NAME`、`Build.SUPPORTED_ABIS`、`appInfo.versionName`。

按 AGENTS.md：平台能力缺失必须**显式建模**（desktop 实现抛 `UnsupportedOperationException`），
不得静默空实现。先例见 M2-1 的 `DesktopImportJsonEditor`。

## 3. 三条探测结论（都是「先 PoC 再动手」换来的）

### 3.1 `miuix-blur` 无 desktop 变体 ⇒ `MiuixAboutScreen` 是 platform island

`MiuixAboutScreen`（519 行）直接调 `top.yukonga.miuix.kmp.blur.*`
（`textureBlur` / `layerBackdrop` / `BlurColors` / `BlendColorEntry`）。探测结果：

- `top.yukonga.miuix.kmp.utils.*`（`overScrollVertical` / `scrollEndHaptic`）→ desktop **可解析**；
- `top.yukonga.miuix.kmp.shader.isRenderEffectSupported` → desktop **可解析**；
- `top.yukonga.miuix.kmp.blur.*` → **Unresolved**。版本目录里只有 `miuix-blur-android`，
  没有不带后缀的 KMP 坐标。

⇒ **`MiuixAboutScreen` 与 `dict` 查询面板同判据：platform island，留 `:app`**。
设计上由 androidMain 的 Route 分流——miuix 引擎渲染 `:app` 的特化屏，否则渲染共享的
Material 屏，**两者复用同一个 ViewModel / Contract**（M5 风险表：「必要时平台 Screen 复用同一
ViewModel/domain」）。

连带受益：`MiuixUtils`（`LocalAppState`/`BlurredBar`/`pageScrollModifiers`，160 行）与
`BgEffect*`（907 行）**都不必搬**——它们的真实调用方只有 miuix 屏。

### 3.2 `LocalAppState` 全仓零 provide 点

全仓没有 `LocalAppState provides ...`，所有读取方恒得 `compositionLocalOf { AppState() }` 的
默认值。所以即便将来要搬它，**保留默认态即行为完全等价**，无兼容风险。

### 3.3 designsystem 的 commonMain 组件无法独立做 Compose UI 测试

`LegadoTheme.typography` / `colorScheme` 是 CompositionLocal 支撑的
（`staticCompositionLocalOf { error("No Typography provided") }`），而 provide 点在
`:core:ui`（Android 模块）的 `ThemeComponents.kt` / `ThemeColorSchemeOverride.kt`——
**designsystem 自己的 commonMain 里没有 provide 点**。

实测：在 `core:designsystem/src/desktopTest` 里 `runComposeUiTest { TextCard(...) }`
⇒ 3 个用例全部 `IllegalStateException: No Typography provided`。
构造 `LegadoTypography`（24 个无默认值字段）/`LegadoColorScheme`（更多）来提供主题，
等于为测试复制整套主题定义——属 AGENTS.md 禁止的「为测试造重复抽象」。

⇒ **结论：designsystem 的 UI 组件在 desktop 上不可独立渲染测试**（`smoke:compose-desktop-probe`
那个探针之所以能过，是因为它渲染的是不依赖 `LegadoTheme` 的自制组件）。
这条对后续所有 UI 组件搬迁都适用：**要么把主题 provide 点也下沉（独立大工程），
要么承认共享层组件的验证止于「编译 + 门禁」，渲染证据仍由 Android 侧承担**。

## 4. 分片计划

| 片 | 内容 | 状态 |
|---|---|---|
| **M5-1a** | `TextCard` 上提 designsystem/commonMain（39 引用方，零平台依赖） | **已完成** |
| M5-1b | `MarkdownBlock` 契约化并进共享层：`Intent(ACTION_VIEW)` → `onOpenUrl` 回调；Splitties 剪贴板 → `Clipboard` 契约（**注意**：现有 `Clipboard.setText` 自带「复制完成」提示，而原代码是静默 `setPrimaryClip`，其 KDoc 明确要求「只复制不提示应另立能力」⇒ 需新增静默方法）；`ic_copy` → composeResources | 待做 |
| M5-1c | 建 `:feature:about`：Contract / VM / Material Screen / Sheets 进 commonMain，`CrashLogSheet` 私有化（`FileDoc` → 不透明引用），三个平台能力契约 + desktop 显式 unsupported 实现，文案与图标进 composeResources，Route 留 androidMain | 待做 |
| M5-1d | 消费方迁移：DI 绑定、nav3 route、`CrashReportActivity`（Activity ABI 留 `:app`）、删除旧包 | 待做 |

## 5. M5-1a 实录

**改动**：`git mv core/ui/src/main/.../card/TextCard.kt → core/designsystem/src/commonMain/.../card/TextCard.kt`
（包名不变 ⇒ 39 个调用方 import 零改动；`:app` 与 `:core:ui` 均已依赖 `:core:designsystem`）。

**为什么它能直接搬**：文件内零 `android.*` / 零 `R.` / 零 Android-only Compose API；
依赖闭包全在 designsystem（`NormalCard` 在 `card/AppCardSurface.kt`、`AppIcon`、
`AnimatedTextLine`、`LegadoTheme`）。`LegadoTheme.typography.labelSmallEmphasized` 是
material3 Expressive API，desktop 的 CMP material3 1.9.0 实测有。

**验证**：`:core:designsystem:compileKotlinDesktop` / `:core:ui:compileDebugKotlin` /
`:app:compileAppDebugKotlin` 全绿；四门禁 `--rerun` 全绿且**无需下调 G4 基线**；
干净重建后全量验证集通过，用例计数不变。

**为什么没加测试**：见 §3.3——共享层无法独立渲染 `LegadoTheme` 系组件。
本片是纯搬迁、零逻辑改动，原 `:core:ui` 下也无测试，故主集/全量计数均不变。
渲染证据仍由 Android 侧（`:app:assembleAppDebug` + 真机）承担。
