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
| **M5-1b** | `MarkdownBlock` 上提 designsystem/commonMain（6 引用方）；三处平台依赖全部用**既有**回调/组件就地消除，**未新增任何平台契约**；`markdown-jvm` → `markdown`（真 KMP 坐标） | **已完成** |
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

## 6. M5-1b 实录

**改动**：`git mv app/src/main/java/io/legado/app/ui/widget/components/text/MarkdownBlock.kt
→ core/designsystem/src/commonMain/kotlin/io/legado/app/ui/widget/components/text/MarkdownBlock.kt`
（894 → 907 行；包名不变 ⇒ 6 个调用方 import 零改动）。

### 6.1 三处平台依赖的处置（全部复用既有能力，零新增契约）

审计 §2.1 原计划新增「静默剪贴板」契约。**实测不需要**——侦察发现三处都已有现成出口：

| 原代码 | 处置 | 依据 |
|---|---|---|
| `context.startActivity(Intent(ACTION_VIEW, linkDest.toUri()))` | 删掉 `else` 分支，改 `onClickLink?.invoke(linkDest)` | `onClickLink: ((String) -> Unit)?` **回调本就存在**，只是原来只用于内链，外链走 Intent 兜底 |
| 图片兜底 `painterResource` / 加载失败跳转 | 改为 `LocalMarkdownImageHandlers.current.onClick(imageUrl)` | 该 CompositionLocal 在 designsystem `ui/util`，已有 `MarkdownImageHandlers` 数据类 |
| `clipboardManager.setText(...)` + `R.drawable.ic_copy` | `Icons.Default.ContentCopy` + `LocalClipboard.setClipEntry(plainTextClipEntry("code", code))` | `plainTextClipEntry`（`PlainTextClipEntryFactory`）**纯写入不弹提示**，与 `ClipData.newPlainText` + `setPrimaryClip` 语义等价 |

**关键判断**：共享层的静默剪贴板能力**已经存在**，且**刻意**没有与 `:core:platform` 的
`Clipboard.setText`（会弹「复制完成」）合并——后者 KDoc 也写明「只复制不提示应另立能力」，
而 `plainTextClipEntry` 就是那个「另立的能力」。所以本片**不动任何契约**，
只把调用点从 Android API 换成共享 API。这条对后续 UI 搬迁是通用经验：
**先 grep 共享层有没有等价出口，再考虑新增契约。**

### 6.2 `markdown-jvm` → `markdown`（真 KMP 坐标）

`gradle/libs.versions.toml` 里原坐标是 `org.jetbrains:markdown-jvm`。以 `curl` 取
Maven Central 的 `markdown-0.7.3.module` 后用 Python 解析变体清单，实测：

```
metadataApiElements (common)  +  jvm  +  js/wasm-js/wasm-wasi
+ iOS(iosArm64/iosSimulatorArm64/iosX64) + macOS + linux + mingw
```

⇒ `org.jetbrains:markdown` 是**真 KMP 制品**，去掉 `-jvm` 后缀即可进 commonMain。
版本目录已就地改正并附注释说明核对结果。

### 6.3 一个编译期坑：`setClipEntry` 是 suspend

复制按钮写在 `clickable { ... }` 里（非挂起上下文），首次编译报
`Suspend function 'setClipEntry' can only be called from a coroutine`。
修法：`val clipboardScope = rememberCoroutineScope()` + `clipboardScope.launch { ... }`。
既有调用点（dict / replacerules / tagrules 的 Screen）都在 `LaunchedEffect` 内，所以此前没暴露。

### 6.4 验证

- `:core:designsystem:compileKotlinDesktop` BUILD SUCCESSFUL（20s）
- `:app:compileAppDebugKotlin` + `:core:ui:compileDebugKotlin` BUILD SUCCESSFUL（6 个调用方零改动）
- 四门禁 `--rerun` 全绿，**无需下调 G4 基线**（`MarkdownBlock` 不含被计数的 GSON/coreProvider 模式）
- `clean` + 全量验证集（458 tasks / 7m54s）BUILD SUCCESSFUL，用例计数 **712 / 1152 零偏离**
- **消费方解析变异**：移走 `core/designsystem/.../text/MarkdownBlock.kt` ⇒
  `:app:compileAppDebugKotlin` 立即报 13 处 `Unresolved reference 'MarkdownBlock'`
  （`OnboardingScreen.kt` 3 处 / `AboutSheets.kt` 3 处 / `AiGeneratedMessageContent.kt` 2 处 /
  `AiThinkingCard.kt` 3 处 / `BookInfoScreen.kt` 2 处），还原后回绿。
  证明「文件真的从共享层被消费」，而非仍有副本在原位。

**为什么没加测试**：同 §3.3——`MarkdownBlock` 渲染依赖 `LegadoTheme`，desktop 无法独立构造主题。
纯搬迁零逻辑改动，故计数不变。
