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
| `MarkdownSheet` | `:app` ui/about (`AboutSheets.kt`) | 28 | **6**（about 自身 + book/source/debug + book/source/edit + `SourceLoginSheets` + `MainActivity` + rss/source/debug + rss/source/edit） | 0（M5-1b 之后） | **`:core:designsystem`**（跨 book/rss/login/main 的通用组件，不放 Feature） |
| `EmptyMessage` | `:core:ui/src/main` | 106 | **~40** | `@StringRes` + `androidx.compose.ui.res.stringResource`（仅 Int 重载） | **`:core:designsystem/commonMain`**（String 重载）+ `androidMain`（Int 重载） |
| `UpdateSheet` | `:app` ui/about (`AboutSheets.kt`) | 112 | **仅 about** | `BuildConfig.VERSION_NAME`、`Build.SUPPORTED_ABIS` | `:feature:about` commonMain（平台值改**参数注入**） |
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

## 3.4 审计漏掉的两件共享组件（深入后补上）

首轮审计只算了「about 渲染要用什么」，没算「哪些同包文件其实不属于 about」。深入后发现两件：

- **`MarkdownSheet` 有 6 个包外调用方**（`BookSourceDebugScreen` / `BookSourceEditScreen` / `SourceLoginSheets` / `MainActivity` / `RssSourceDebugScreen` / `RssSourceEditScreen`）——它与 `UpdateSheet` 同住 `AboutSheets.kt`，但**本身不是 about 特有的**。若随 `AboutSheets.kt` 一并搬进 `:feature:about`，会让 book/rss/login 的屏幕反向依赖 about Feature（`AGENTS.md`：Feature 之间不直接依赖实现）。零平台依赖（M5-1b 之后）⇒ 归 designsystem。
- **`EmptyMessage` 位于 `:core:ui/src/main`**（~40 调用方），`CrashLogSheet` 的空态用它。它只有一处平台依赖：`@StringRes` + `androidx.compose.ui.res.stringResource` 的 **Int 重载**（`String` 重载零平台依赖）。⇒ commonMain 放 String 重载，Int 重载跨到 designsystem 的 **androidMain**。若不上提，就得在 about 里重写空态——那是**可见 UI 行为变化**，不取。

这两件合并为 **M5-1c-pre**（同 M1-3x-pre 形态：先把 Feature 闭包缺的共享组件提上去，再搬 Feature）。包名不变 ⇒ 调用方 import 零改动。

## 4. 分片计划

| 片 | 内容 | 状态 |
|---|---|---|
| **M5-1a** | `TextCard` 上提 designsystem/commonMain（39 引用方，零平台依赖） | **已完成** |
| **M5-1b** | `MarkdownBlock` 上提 designsystem/commonMain（6 引用方）；三处平台依赖全部用**既有**回调/组件就地消除，**未新增任何平台契约**；`markdown-jvm` → `markdown`（真 KMP 坐标） | **已完成** |
| **M5-1c-pre** | `MarkdownSheet`（`git mv`，6 包外调用方）+ `EmptyMessage`（~40 调用方；`@StringRes` 的 Int 重载跨到 androidMain）上提 designsystem | **已完成** |
| **M5-1c** | 建 `:feature:about` 并完成接线：`Contract / VM / Material Screen / Sheets` 进 commonMain，`CrashLogSheet` 私有化（`FileDoc` → 不透明引用），三个平台能力契约 + desktop 显式 unsupported 实现，文案与图标进 composeResources；**分流与 Route 留 `:app`** | **已完成**：1c-1+1c-2 建共享层（不接线），**1c-3 接线 + 删旧包**（`appModule` 三绑定 / `MainNavGraph` 分流 + Effect 收集 / `CrashReportActivity` 改 import / 删 6 个旧文件）。实录见 §8 |
| ~~M5-1d~~ | 原计划的「消费方迁移」 | **已并入 1c-3**（实际内容就是那四项，拆开只会让中间态既没接线又没删旧包） |

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

## 7. M5-1c-pre 实录

**改动**：

1. `MarkdownSheet` 从 `app/.../ui/about/AboutSheets.kt` 拆出，新建
   `core/designsystem/src/commonMain/.../modalBottomSheet/MarkdownSheet.kt`
   （与 `AppModalBottomSheet` / `OptionSheet` 同包）。
2. `git mv core/ui/src/main/.../components/EmptyMessage.kt
   → core/designsystem/src/commonMain/.../components/EmptyMessage.kt`，并把
   `@StringRes` 的 Int 重载拆到
   `core/designsystem/src/androidMain/.../components/EmptyMessage.android.kt`。

### 7.1 为什么这两件是同一片而不是两片

它们的共同点是「**被 about 的闭包牵出来、但本身不属于 about**」：

- `MarkdownSheet` —— `AboutSheets.kt` 里和它同居的 `UpdateSheet` 确实只有 about 一个调用方，
  但 `MarkdownSheet` 自己有 **6 个包外调用方**；若随文件一并进 `:feature:about`，
  book / rss / login 的屏幕就要反向依赖 about Feature。
- `EmptyMessage` —— `CrashLogSheet`（M5-1c 要进 `:feature:about`）的空态用它，
  而它自身有 ~40 个调用方。不上提就只能在 about 里重写一份空态——
  那是**可见 UI 变化**（少了表情动画与 `AnimatedTextLine`），不取。

合并为一片的理由是「一个可说明的边界变化」：
**把 about 闭包缺的共享 UI 资产一次性收口到 designsystem**（同 M1-3x-pre 形态）。

### 7.2 `EmptyMessage`：一个声明重载靠 `@StringRes` 强行分家

它只有一处平台依赖，而且只在**一个重载**里：

```kotlin
@Composable
fun EmptyMessage(@StringRes messageResId: Int, ...) {
    val message = stringResource(id = messageResId)   // androidx.compose.ui.res，Android-only
    ...
}
```

CMP 的替代品 `org.jetbrains.compose.resources.stringResource` 接的是
`StringResource` 而不是 `Int`，所以这条「传资源 id」的便利写法**无法跨平台**。
⇒ 按源集分家：commonMain 只放 `message: String` 重载，
`androidMain` 放 Int 重载（同名文件 `EmptyMessage.android.kt`）。
`AGENTS.md` 的「不得静默空实现伪造跨平台支持」在这里的落地就是：
**宁可分源集，不在 commonMain 放一个假实现。**

androidMain 重载**只保留现存调用方用到的两个参数**（`messageResId` + `modifier`）——
`:app` 侧 5 处调用全部只传这两个。其余可选参数共享层的 String 重载已提供，
重复一套 7 元素的 `faces` 默认值只会让两处定义漂移。

### 7.3 包名：`MarkdownSheet` 改名，`EmptyMessage` 不改

- `EmptyMessage` 保包名 `io.legado.app.ui.widget.components` ⇒ **40 个调用方 import 零改动**。
- `MarkdownSheet` **改包名**为 `...ui.widget.components.modalBottomSheet`（与 `AppModalBottomSheet` 同包）。
  这与 M5-1a/1b「保包名换目录」不同是有意的：一个叫 `about` 的包出现在
  `:core:designsystem` 里语义就是错的。代价是 7 处 import 改动（含 `AboutScreen.kt`——
  它原本与 `MarkdownSheet` 同包，**没有** import，这次是新增一行）。

### 7.4 验证

- `:core:designsystem:compileKotlinDesktop` + `:app:compileAppDebugKotlin` +
  `:core:ui:compileDebugKotlin` 全绿（第一次 `:app` 编译 54s）
- 四门禁 `--rerun-tasks` 全绿，**无需下调 G4 基线**
- `clean` + 全量验证集 BUILD SUCCESSFUL（463 tasks / 6m31s），
  用例计数 **712 / 1152 零偏离**
- **消费方解析变异**（逐个做，因为 Gradle fast-fail 会卡在
  `:core:designsystem` 而不进 `:app`）：
  - 移走 `modalBottomSheet/MarkdownSheet.kt` ⇒ 7 个消费文件各 2 处
    `Unresolved reference 'MarkdownSheet'`（`AboutScreen` / `BookSourceDebugScreen` /
    `BookSourceEditScreen` / `SourceLoginSheets` / `MainActivity` / `RssSourceDebugScreen` /
    `RssSourceEditScreen`）。
  - 移走 `EmptyMessage.kt` + `EmptyMessage.android.kt` ⇒ **25 个消费文件 / 61 处**
    `Unresolved reference 'EmptyMessage'`。
  - 只移走 `EmptyMessage.kt`（保留 androidMain）⇒ `:core:designsystem`
    自身报 `EmptyMessage.android.kt:27 No parameter with name 'message' found`
    ——这正是「两个源集的重载真的是一对」的证据。
  共三轮，每轮均还原回绿。

## 8. M5-1c-3 实录：接线 + 删旧包

M5-1c-1/1c-2 只建了共享层、`:app` 一个原文件没动。本片把它接上并删掉旧实现，
是这一批里**唯一会改变 `:app` 运行路径**的一片。

### 8.1 接线形态（三处）

| 位置 | 内容 |
|---|---|
| `appModule` | 三个 `single<>` 绑定：`AppUpdateChecker` → `AndroidAppUpdateChecker()`、`AboutDiagnostics` → `AndroidAboutDiagnostics(androidContext(), get())`、`BundledTextReader` → `AndroidBundledTextReader(androidContext())`。`viewModelOf(::AboutViewModel)` 不变，由 Koin 按类型解析 5 个构造参数 |
| `MainNavGraph` 的 `entry<MainRouteAbout>` | 收集 Effect（4 个分支）+ **分流**（miuix / material）+ `AboutOverlays` |
| `CrashReportActivity` | 只改一行 import，指向 feature 的 `CrashReportScreen` |

**分流为什么留在宿主**：`MiuixAboutScreen` 直连 `miuix-blur`（只有 `-android` 制品），
共享层引用它会形成 `:app → :feature:about → :app` 的环。两支**复用同一 ViewModel/Contract**
——这是 §3.1 判定的 platform island 的既定处置，不是本片新引入的取舍。

### 8.2 行为等价清单（本片没有自动化测试，故逐条列出对照）

| 行为 | 迁移前 | 迁移后 | 判据 |
|---|---|---|---|
| 引擎分流 | `AboutScreen` 内 `if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine))` | 同一条件搬到 entry 内 | 表达式逐字搬运 |
| 版本号 | `AboutScreen` 的默认参 `versionName = appInfo.versionName`，传给两个分支与 `UpdateSheet` | 显式传给 `MaterialAboutScreen` 与 `AboutOverlays`，两处同一表达式 | 同一表达式，无第二来源 |
| ABI | `UpdateSheet` 内 `Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"` | `AboutOverlays(abi = …)`，回落留在宿主 | 逐字 |
| 更新检查失败提示 | `"${context.getString(R.string.check_update)}\n${e.localizedMessage}"` | `buildString { append(msg.localizedText()); detail?.let { append('\n').append(it) } }` | 同一「文案 + `\n` + 异常」格式 |
| 其余 7 条提示 | `context.getString(R.string.x)` | `AboutMessage.localizedText()` | 8 条文案的**值**由 `verify-compose-resources.py` 140/140 与 `:app` 逐字比对钉住 |
| 运行期文本提示 | `toastOnUi(it.localizedMessage ?: "")` | `AboutEffect.ShowText` → 同 | 逐字 |
| 打开链接 / 启动下载 | `context.openUrl` / `Download.start` | 同 | 未改 |
| 崩溃报告页 | `:app` 的 `CrashReportScreen` | feature 的同名 Screen | M5-1c-2 已逐字搬迁（仅 `R` → `Res`） |

### 8.3 G4 基线随之下调（三条）

删掉的旧文件里带走了 `AboutViewModel` 的 `help.*` / `base.*` / `utils.*Utils` import：

- `legacyHelp|app/main/io/legado/app/ui/about` **5 → 1**（剩 `CrashReportActivity` 的 `help.CrashHandler`）
- `legacyBase|app/main/io/legado/app/ui/about` **2 → 1**（剩 `CrashReportActivity` 的 `base.BaseComposeActivity`）
- `legacyNaming|app/main/io/legado/app/ui/about` **2 → 0**（`utils.FileUtils` / `utils.compress.ZipUtils`
  已随实现搬进 `io/legado/app/platform`，那边的计数在 1c-1 已登记 ⇒ **条目删除**）

`ui/widget/components/log` 的 `legacyNaming|1` 未变（删的是 `CrashLogSheet`，
`AppLogSheet` / `LogDetailSheet` 仍在）。

### 8.4 验证

- 单独 `clean` 后：四门禁（`checkSharedPurity` / `checkModuleDependencies` /
  `checkLegacyArchitecture` / `verifyConfigArchitecture`）全绿
- `:feature:about:compileKotlinDesktop` + `:feature:about:testAndroidHostTest` **2 例 0 失败**
- `:app:compileAppDebugKotlin` + `:app:assembleAppDebug` 全绿
- 全量计数 **714 / 1154 零偏离**（app 632 / designsystem 39 / viewmodel 19 / tagrules 15 /
  replacerules 2 / about 2 / host:desktop 5 = 714；本片无用例增删）
- 资源回归：about **140/140**、tagrules **124/124**
- `lintAppDebug` 仍是迁移前就有的 **5 个 error**（`BookInfoScreen` ×3 / `BackstageWebView` /
  `BottomWebViewDialog`），全部与 about 无关，数量未变

### 8.5 变异验证：DI 绑定缺一条，编译照样绿

删掉 `single<BundledTextReader> { … }` 后跑 `:app:compileAppDebugKotlin` ⇒ **BUILD SUCCESSFUL**。
即 Koin 的 `viewModelOf(::AboutViewModel)` 是**运行期**按类型解析，漏绑不会在编译期暴露；
而 `grep checkModules` 在 `:app` 只命中 `App.kt` 的 `startKoin` ⇒ 本仓**尚无宿主 graph creation
test**（AGENTS.md 目标态里有，当前未建立）。

已还原并复跑 `:app:assembleAppDebug` 回绿。**后续项**：给 `:app` 补一个启动期 Koin graph
creation test（属独立切片，本片不引入 Koin test 依赖——一次只改一个风险维度）。

### 8.6 死资源清理，以及 `verify-compose-resources.py` 的判据演进

迁走后 `:app` 侧有 **19 条字符串 × 4 语言**与 **2 个 drawable**（`ic_github.xml` /
`ic_import.xml`）变成零引用（判定方式：扫 `app/src/main` 全部 `.kt`/`.xml` 的
`R.string.x` 与 `@string/x`、`R.drawable.x` 与 `@drawable/x`，与已删文件的引用集合做差集）。
已按「只移除本次改动产生的无用资源」删除；`ic_launcher_foreground`（5 处）与
`ic_web_outline`（1 处）**仍在用**，保留。

⚠️ 这一删把 `tools/verify-compose-resources.py` 的第 ② 项判据打破了：它要求
「composeResources 的 key 集合 = `:app` 同语言的 key 集合」，删副本后直接红。
但**这个判据本身是过渡期的**——页面迁进 Feature 后 `:app` 那份副本**必然**要删，
否则每次清理都会让脚本永久报噪音。故把反向差集拆成两类：

| 方向 | 含义 | 处置 |
|---|---|---|
| `:app` 有、composeResources 无 | 模块**漏搬** | **硬错误**（保持原判据） |
| composeResources 有、`:app` 无 | `:app` 副本**已随迁移下线** | 只记录条数与键名，不判失败；且不进 ③ 的分母（没有可比对象） |

代价与补偿：这些条目此后不再被逐字校验。补偿是**在删副本之前先跑一次**——本片删前
about 是 **140/140**，删后 `64/64` + 「已下线 19 条 × 4 语言」。
回归：tagrules **124/124**、dict **76/76**（两者均无已下线条目）。

### 8.7 未验证（需真机冒烟）

about 页的**运行期**行为没有自动化覆盖：更新检查成功/失败、崩溃日志列举/读取/清空、
保存日志、堆转储、markdown 弹层、下载启动，以及 Miuix 分支的渲染。
本片能证明的是「编译 + 门禁 + 打包 + 文案逐字一致 + 计数零偏离」，
不能替代打开一次关于页并逐个点一遍。
