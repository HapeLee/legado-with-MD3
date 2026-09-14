# CMP 模块 convention 与 designsystem 首块切片（M1-2）

本文记录两件事：**怎么在本仓建一个真 CMP 模块**（可复用契约），以及**哪些依赖真的能进
`commonMain`**（实测结论）。后者是 M1-3「把 `tagrules` 转 CMP」的直接前置。

## 1. convention：`legado.kmp.compose`

在 `build-logic` 里，叠加在既有的 `legado.kmp.library` 之上：

```kotlin
// build.gradle.kts（模块侧）
plugins {
    id("legado.kmp.compose")
}
```

它做三件事：

1. apply `legado.kmp.library`（→ `kotlin.multiplatform` + `com.android.kotlin.multiplatform.library`，
   android + desktop 两个 target）；
2. apply `org.jetbrains.compose`（CMP 1.12.0）+ `org.jetbrains.kotlin.plugin.compose`；
3. 把 Compose runtime / foundation 加进 **`commonMain`**（版本取插件常量 `ComposeBuildConfig`，
   不手写；并在配置期断言版本目录里的 CMP 版本与插件常量一致）。

**前置约束（必须成对）**：应用该 convention 的模块，必须在根 `build.gradle.kts` 的
`CheckSharedPurityTask.kmpModuleTypes` 里登记为 `"cmp"`；否则 G2 门禁 `checkSharedPurity`
会因为 `commonMain` 里出现 `androidx.compose.*` 直接拦下。当前登记：

| 模块 | 类型 | 原因 |
| --- | --- | --- |
| `core/designsystem` | `cmp` | M1-2 起为真 CMP |
| `core/platform`、`core/data`、`smoke/{room,network}-kmp-probe` | `data` | commonMain 用 Room / Ktor |
| `core/model`、`feature/reader/core`、`smoke/{kmp,rhino-capability}-probe` | `pure` | 零 androidx |

### 为什么 Compose 在 `commonMain` 而不是 `composeMain`

本模块历史上用过 `composeMain` 中间源集（android/desktop 各自 `dependsOn` 它），那是
「commonMain 必须零 Compose」时代的产物。M0-1 之后门禁按模块类型分策，那个约束只对
pure/data 模块成立；CMP 模块直接让 Compose 进 `commonMain`：

- 加 iOS / wasm target 时**不需要再补一层源集**；
- 组件只有一份，不存在「android 侧一份、desktop 侧一份」的漂移；
- 代价只是上面那条登记要求。

## 2. 包名策略：搬文件不改 import

`core:designsystem` 与 `core:ui` 共用 `io.legado.app.ui.*` 命名空间。所以把一个组件从
`:core:ui` 搬到 `:core:designsystem` 时**保持包名不变**，调用方 import 一个字都不用改——
只需要该调用方模块本身依赖 `:core:designsystem`（`app`、`core:ui`、
`feature:{tagrules,replacrules,dict,txttocrules}` 都已依赖）。

这是本仓搬 UI 组件的默认手法，比改一圈 import 更安全（不会漏、不会误伤同名符号）。

### 搬走的组件还依赖 `:core:ui` 里的东西怎么办（M1-3h 的手法）

依赖方向是**单向**的（`:core:ui` → `:core:designsystem`），反向不可行。所以搬组件前要把它
**引用的每个符号**都核对一遍归属，遇到「两边都要用」的声明，处置只有两条：

| 情形 | 处置 |
|---|---|
| 声明本身 `android.*`-free，且原文件整体能搬 | 连文件一起搬（首选） |
| 原文件还带 `android.*`（搬不动），但只有声明这一小块是共享的 | **把声明单独下沉到共享层的同名包** |

第二条就是 `LocalUseMiuixWindowPopup` 的处理：它原声明在 `:core:ui` 的
`menuItem/RoundDropdownMenu.kt`（该文件经 `rememberOpaqueColorScheme` → `ThemeEngine` →
`Context` 仍带 `android.*`），而搬进 designsystem 的 `AppModalBottomSheet` 要 provide 它。
做法是只把 `val LocalUseMiuixWindowPopup = staticCompositionLocalOf { false }` 放进共享层的
**同名包** `io.legado.app.ui.widget.components.menuItem`——`:core:ui` 留在原地的那份仍在同包内
引用它，**连 import 都不用加**。

**跨模块同包可见性是既有事实，不是新机制**（本仓先例：`core:ui` 的 `ui/theme` 文件直接引用
designsystem 的 `LocalLegadoThemeColors` / `LocalAppUiConfiguration`，零 import；反之显式
`import io.legado.app.ui.theme.ThemeEngine.getColorScheme` 这种同包同模块的冗余 import 也合法）。
注意它**不会**造成重复声明——同包同名声明仍只能有一份，只是这份可以在另一个模块里。

另一个容易漏的维度：搬动会引入**新的显式依赖**。`AppModalBottomSheet` 用
`Modifier.animateContentSize`（`androidx.compose.animation` 的顶层扩展，属 animation 制品，
不是 foundation）。它虽然能被 foundation 传递带上，但按本仓「依赖只列实际用到的」要显式声明
（别名 `compose-multiplatform-animation`）。**搬完先查 import 里有没有当前源集未声明的制品。**

## 3. 制品可用性实测（M1-2 的核心情报）

**结论：`commonMain` 只能放「有 desktop/jvm 变体」的库**；判断某个坐标行不行，要看它
`.module` 元数据里的实际变体，**不能凭"group 里带 androidx"就判它是 android-only**。
逐项实测：

| 需要 | 进 `commonMain` 的坐标 | 实测结论 |
| --- | --- | --- |
| Compose runtime / foundation | `org.jetbrains.compose.*`，convention 已带（版本取插件常量） | ✅ android + desktop |
| Material3 | `org.jetbrains.compose.material3:material3` | ✅ **真 KMP**：`module` 里有 android / desktop / ios×3 / js / wasmJs 变体 |
| Material3 的 android 变体 | 由 CMP 自动解析，不用手写 | CMP 的 `material3-android` **内部再依赖 `androidx.compose.material3:material3`** ——「在 android 上最终用的就是 Google 那份 AndroidX Material3」是这么来的 |
| Material3（直接写 androidx 坐标） | `androidx.compose.material3:material3` | ⚠️ 该坐标自身只有 android + `jvmStubs`/`nativeStubs`（stub 是空的编译期占位，不是实现）。**别把它写进 commonMain**——CMP 的 material3 才是能用的多平台制品 |
| 图标 | `androidx.compose.material:material-icons-extended:1.7.8`（本仓既有版本） | ✅ 有 jvm 变体（Google 只发到 1.7.8 这一档，1.9+ 只有 android）；CMP 自己的 `material-icons-*` 冻在 1.7.3 且已 deprecate。**两条路都不再更新**，要新图标得迁 Material Symbols |
| Miuix | `top.yukonga.miuix.kmp:miuix-core` / `miuix-ui` / `miuix-icons`（**不带 `-android` 后缀**） | ✅ 全平台（android/desktop/js/wasmJs/ios_arm64/ios_simulator_arm64/macos_arm64） |
| Miuix（android 别名） | `top.yukonga.miuix.kmp:miuix-*-android` | ❌ android 专用，只给 `:core:ui` / `:app` 用 |
| 图片加载（Coil3） | `io.coil-kt.coil3:coil-compose`（`libs.coil.compose`） | ✅ `coil-compose-jvm`（M1-3p 实测）。`LocalPlatformContext` 是 coil3 自带的多平台 `LocalContext` 替代，Android 上就是同一个 `Context` |
| 拖拽排序 | `sh.calvin.reorderable:reorderable`（`libs.reorderable`） | ✅ `reorderable-jvm`（M1-3q 实测） |
| 其它 androidx（lifecycle / activity / navigation …） | 逐项验证 | 未验证前一律视为 android-only |

⚠️ **「看起来像平台库」不是判据**：coil3 与 reorderable 都曾在审计表里被并列写成「平台库」
（与 `kotlinx.collections.immutable` 一起），之后都被证实是普通 KMP 制品。判据只有一条——
**Maven 的 `.module` 里有没有非 Android 变体**，与「是不是图形/拖拽/系统能力库」无关。

**M1-3d 追加实测（2026-09-10）**——这四个是 `LegadoTheme.kt` 与组件层的依赖，此前被列为
「未知，可能挡住主题进 CMP」。查 Maven Central 的 `.module` 元数据后**全部有非 Android 变体**：

| 需要 | 坐标 | 实测结论 |
| --- | --- | --- |
| Haze（`HazeState` 等） | `dev.chrisbanes.haze:haze` | ✅ `haze-jvm`（另有 js / wasmJs / ios×3 / macos×2） |
| MaterialKolor（`PaletteStyle`） | `com.materialkolor:material-kolor` | ✅ `material-kolor-jvm` |
| Backdrop（`Backdrop`） | `io.github.kyant0:backdrop` | ✅ **变体名是 `desktop` 不是 `jvm`**（`backdrop-desktop`，另带 `org.jetbrains.kotlin.platform.type=jvm`） |
| Capsule | `io.github.kyant0:capsule` | 同一发布方，与 backdrop 同批（本切片未用到，未单独验证） |

⚠️ **不要用本地 Gradle 缓存反推「有没有 desktop 变体」**：Android-only 构建的缓存里只会出现
`-android` 制品（例：`dev.chrisbanes.haze` 目录下当时只有 `haze-android`），看起来就像没有 jvm 变体。
判据必须是**上游 `.module` 元数据**（`https://repo1.maven.org/maven2/<group-path>/<module>/<ver>/<module>-<ver>.module`
的 `variants[].available-at.module`）。反过来，`miuix-*-desktop` 能在缓存里看到，只是因为本仓已在
desktop 目标解析过它。

### ⚠️ 库有 desktop 变体 ≠ 你用的那个 API 也在

第 3 节判的是**制品**粒度；实际编译按**源集**粒度失败。同一个包会按源集切分。判据同样要落到源码：
`ui-android-<ver>-sources.jar` 解出的源集是 `androidMain / commonMain / jvmAndAndroidMain`，
`ui-desktop-<ver>-sources.jar` 是 `commonMain / desktopMain / jvmAndAndroidMain / skikoMain`——
`LocalConfiguration` 只在 `androidMain` 那侧存在。

| API | 实测 | 结论 |
| --- | --- | --- |
| `androidx.compose.ui.platform.LocalDensity` | 在 `ui` 的 **`commonMain`**（`CompositionLocals.kt:120`） | 共享层可用 |
| `androidx.compose.ui.platform.LocalConfiguration` | 只在 `ui` 的 **`androidMain`**（`AndroidCompositionLocals.android.kt:36`） | 共享层**不可用** |
| `androidx.compose.material.ExperimentalMaterialApi` | 在 **material2**（`androidx.compose.material`）制品里。`:core:ui` 依赖了它、designsystem 没有 | 组件搬进来时**连带删掉该 `@OptIn`**，不要为它顺手加一条 material2 依赖 |
| CMP material3 的 expressive 系列（`ExperimentalMaterial3ExpressiveApi` / `MaterialExpressiveTheme` / `MotionScheme` / `rememberBottomSheetState` / `BottomSheetDefaults.modalWindowInsets`） | **这是版本问题，不是平台问题。** 符号都在 CMP material3 的 `commonMain`，android/desktop/ios/js/wasm/macos 变体齐全；但 **1.9.0**（CMP 1.12.0 插件常量所指的版本）把它们标成了 `internal`，跨模块编译报 `Cannot access '…': it is internal in file`；`rememberBottomSheetState` / `modalWindowInsets` 在 1.9.0 则压根不存在（`Unresolved reference`） | **升级 `composeMultiplatformMaterial3` 即可，不需要「留 Android 侧」**——放开点与做法见下面「第三种阻塞」 |

### 第三种阻塞：API 在 `commonMain`，但被上游标成 `internal`（版本问题）

前两种阻塞都看「符号在哪个源集 / 哪个制品」，第三种看**可见性**：符号就在 `commonMain`，
所有目标都有制品，但**上游当前版本**把它声明成 `internal`，于是跨模块不可见。
**表现是 `Cannot access 'X': it is internal in file.`，和「源集里没有」完全不同**——认错这一条
就会得出「这是 Android 独占 API」的错误结论。

CMP material3 的 expressive 系列就是这种。上游源码实测的放开时间线：

| 版本 | `MaterialExpressiveTheme` / `MotionScheme` / `@ExperimentalMaterial3ExpressiveApi` | `BottomSheetDefaults.modalWindowInsets` | `rememberBottomSheetState` |
| --- | --- | --- | --- |
| 1.9.0（CMP 1.12.0 插件常量所指） | `internal` | 不存在 | 不存在 |
| 1.10.0-alpha05 | **public** | 不存在 | 不存在 |
| 1.11.0-alpha07 | public | **存在** | 不存在 |
| 1.12.0-alpha03 | public | 存在 | **存在** |
| 1.13.0-alpha01 | public | 存在 | 存在 |

因此本仓取 **1.12.0-alpha03**（满足全部需求的最低版本），由 convention 的 `MATERIAL3_PIN`
显式登记；`androidx.compose.material3` 侧不受影响（`material3-android:1.12.0-alpha03` 只委托到
`1.5.0-alpha22`，低于本仓锁定的 `1.5.0-alpha23`，Gradle 取高版本）。

⚠️ **`javap` 不能当判据**。Kotlin 的 `internal` 顶层声明在 JVM 字节码里**仍是 `public`**
（可见性记在 `@Metadata`，不在 access flag；也不会 mangle 方法名）。实测 1.9.0 与
1.12.0-alpha03 的 `javap` 输出**一模一样**都是 `public static final void MaterialExpressiveTheme(...)`，
但前者跨模块编译必失败。**只有两个真判据：上游源码的可见性修饰符 + 实际编译非 Android 目标。**

⚠️ **按类名 grep jar/源集不可靠**：`LocalConfiguration` 的宿主文件叫
`AndroidCompositionLocals.android.kt`，按文件名找会漏；反过来在 `jar` 里按类名找也会因为顶层属性
被编进 `*Kt` 类而失效。**要按符号名 grep 整个源集目录**（先 `unzip` 出 sources jar，再 `grep -rn`）。

**M1-3e 实例**：`AppDensity.kt` 搬进 `commonMain` 时，它唯一一处 Android-only API 就是
`LocalConfiguration.current.fontScale`（读「系统字体缩放」）。两者**同源可互换**，依据是 Compose
自己的实现：`ProvideCommonCompositionLocals` 提供 `LocalDensity provides owner.density`，Android 的
owner（`AndroidComposeView`）用 `density = Density(context)`；而 `LocalConfiguration` 被提供为
`owner.configuration = Configuration(context.resources.configuration)`。**同一个
`context.resources` ⇒ `LocalDensity.current.fontScale` 恒等于该 configuration 的 `fontScale`**
（`AndroidComposeView.android.kt` 亦以 `Density(density = displayMetrics.density, fontScale =
configuration.fontScale)` 从同一份 resources 构造）。改读 `LocalDensity.current.fontScale` 后
Android 行为不变、desktop 也能编译。**同名 API 的取舍优先找「同一事实的另一个公共入口」，而不是
上 `expect/actual`。**

### foundation 侧的 Android-only 成员（M1-3k 补测）

Android-only 的不只有 `androidx.compose.ui.*`。**`androidx.compose.foundation` 也有一族**，
同样绕得过 `android.*` / `R` 两条 import 规则：

| 符号 | desktop | 备注 |
| --- | --- | --- |
| `Modifier.systemGestureExclusion()` | ❌ | 底层是 `View.setSystemGestureExclusionRects`。**M1-3v 已解**：下沉为 `:core:designsystem` 的 `expect fun Modifier.systemGestureExclusionCompat()`（androidMain 转发原生 / desktopMain 恒等），`lazylist/*` 两个文件随之进 `commonMain` |
| `Modifier.excludeFromSystemGesture()` / `preferKeepClear()` | ❌ | 同族 |
| `WindowInsets.statusBarsIgnoringVisibility` 及同族 `*IgnoringVisibility` | ❌ | |
| `WindowInsets.isImeVisible` / `isTappableElementVisible` / `areNavigationBarsVisible` | ❌ | |
| `WindowInsets.imeAnimationSource` / `imeAnimationTarget` | ❌ | |
| `WindowInsets.ime` / `.tappableElement` / `.captionBar` / `.waterfall` / `.safeDrawing` | ✅ | **可用**，别误杀 |
| `Modifier.{ime,navigationBars,statusBars,systemBars}Padding()` | ✅ | `WindowInsetsPadding_skikoKt` 里有 |

### ⚠️ 实测方法论：**不能比类名，只能比成员**

补这张表时踩了一次大坑。看起来最自然的做法是「解包 desktop jar 与 android aar，类路径做差集」，
但差集里那些 `*_androidKt` 大多是 **`expect/actual` 配对**：

- `WindowInsetsPadding_androidKt` ↔ `WindowInsetsPadding_skikoKt`
- `WindowInsets_androidKt` ↔ `WindowInsets_notMobileKt`
- `Clickable_androidKt` ↔ `ClickableKt` …

成员**一模一样**，只是文件名不同。只看类名会一次性误杀 12 个成员（`navigationBarsPadding` /
`imePadding` / `statusBarsPadding` / `WindowInsets.ime` / `tappableElement` / `captionBar` /
`waterfall` / `safeDrawing` …），把一堆可搬的组件判成不可搬。

**正确的三步**（对会变化的库版本要重跑）：

1. 类路径差集，得到「android 有、desktop 无」的 `*_androidKt` 名单；
2. 对这些类 `javap` 列出**公开成员**；
3. **逐个回 desktop jar 里找该符号证伪**——`grep -rl --binary-files=text -w <name>` 命中即「其实可用」。
   最后活下来的才是 Android 独有。

两个附带的坑：① 短名（`ime`）在 class 里到处命中，必须配合 `\.name`（成员访问）或 `name(` 的模式，
否则会撞上同名**局部变量**（`feature/replacerules/ReplaceEditScreen.kt` 里就有
`val isImeVisible = …`）；② `javap` 对 `internal` 不可信（见上），但对「类里有没有这个方法」
是可信的——这一步只问存在性，不问可见性。

这套判据已固化进 `.agents/skills/legado-kmp-migration/scripts/portability-triage.py` 的
`ANDROID_ONLY_COMPOSE`，含正反两面的清单。

### 怎么声明 CMP 依赖：三种写法的实测

CMP 1.12 的 `compose.*` / `compose.dependencies.*` 已经不是老文档里的样子了。在
`:core:designsystem` 里逐条改、跑 `./gradlew :core:designsystem:help` 看解析结果：

| 写法 | 结果 |
| --- | --- |
| `implementation(compose.material3)` | ✅ **能解析**（这里的 `compose` 是插件挂在 `kotlin` 扩展上的 `ComposePlugin.Dependencies`），但 **已 `@Deprecated("Specify dependency directly")`** |
| `implementation(compose.dependencies.material3)` | ❌ `Unresolved reference` —— 该 `compose` 上没有 `dependencies` 这一层。顶层 `ComposeExtension.dependencies` 只在把 `org.jetbrains.compose` 直接写进本模块 `plugins {}` 时才会有访问器；**经 convention 间接应用不会生成** |
| `implementation("org.jetbrains.compose.material3:material3:…")` | ✅ 官方推荐写法（正是上面那条 deprecation 的 `ReplaceWith` 内容） |

结论：**用直写坐标**。

### 版本从哪来：不手写，取插件常量

直写坐标的代价是版本得自己填，而 **CMP 的 material3 版本号与插件版本号不是一回事**：
插件 1.12.0 对应 `ComposeBuildConfig.composeMaterial3Version = "1.9.0"`，语义是
「material3 的最新 stable」——1.10/1.11/1.12/1.13 目前全是 alpha。本仓踩过这个坑：
凭「插件是 1.12.0」把 material3 写成 `1.12.0-alpha03`。现在的做法：

- 值写在 `gradle/libs.versions.toml`（`composeMultiplatformMaterial3`），模块侧可见、可审；
- **期望值登记在 convention 的 `MATERIAL3_PIN` 常量里**（不是插件常量）。`assertComposeVersionsInSync`
  在配置期断言「版本目录 == `MATERIAL3_PIN`」，并单独断言 `>= 插件常量`（只允许向上覆盖，
  不允许降级）——漂了直接构建失败，而不是运行时行为悄悄变；
- convention 自带的 runtime / foundation 的版本直接取 `ComposeBuildConfig.composeVersion`
  （插件常量），不经过版本目录。

另外两个坑：

- **`Switch` 不在 `miuix-core`**：`top.yukonga.miuix.kmp.basic.Switch` 在 **`miuix-ui`** 里。
  只加 `miuix-core` 会在 `compileKotlinDesktop` 报 `Unresolved reference 'basic'`。
- 查制品可用性时 **Google Maven 必须一起查**（`dl.google.com/dl/android/maven2`）：androidx 的
  多平台变体不出现在 Maven Central 上，只查 Central 会把存在的东西判成不存在。

### CMP 坐标能不能替掉 androidx 坐标？——android 侧是「转发」，不是「替代」

**不能简单替，两条坐标各有职责**（2026-09-10 实测，`./gradlew :app:dependencies`）：

| 做法 | android 侧解析到的 material3 | compose 栈 |
| --- | --- | --- |
| 现状：android-only 模块写 `androidx.compose.material3:material3` | 1.5.0-alpha23 | 1.12.0 线统一 ✅ |
| 删掉它、CMP 坐标停在 `1.12.0-alpha03` | **1.5.0-alpha22**（降级） | `core:ui` 编译失败 ❌ |
| 删掉它、CMP 升到 `1.13.0-alpha01` | 1.5.0-alpha27 | **整栈 1.13.0-alpha02**，BOM 与各 pin 全失效 ⚠️ |

原因：CMP 的 `material3-android` 是**转发空壳**，只对 `androidx.compose.material3:material3`
下一条 **`requires`（软约束，不是 `strictly`）**；于是「android 上最终是哪一版」由 **CMP 的版本**
决定，而 CMP 制品是**整条线同版本发布**的——升 CMP material3 必然同时要求
`org.jetbrains.compose.foundation` / `runtime` / `ui` 同版本，其 android 变体再转发给 androidx
⇒ **整条 compose 栈跟着 CMP 的 alpha 节奏走**。而 `composeBom` / 各 pin 属 androidx 体系，两者不通用。

⚠️ **不要把「抬栈」归因给 androidx material3 自己**：`material3-android-1.5.0-alpha27` 只
requires compose **`1.12.0-beta01`**（所以它其实可以独立小步升）；抬栈的是 CMP 那条**元数据依赖链**。
同理 androidx 最新 `1.5.0-alpha28` 已把 `foundation` 要求提到 `1.13.0-alpha01`，也会自己撕裂栈
⇒ 要留在 1.12.0 线就止步 **alpha27**。

**结论**：`androidx material3` 是「实际跑的那份」，CMP 坐标是「commonMain 能不能看见 expressive
API 的入口」——不同坐标系，数字永久不可能相等，现状自洽（android 侧只解析出一个 material3）。
真正的「单一版本来源」需要**整栈 CMP 化**（foundation / ui / runtime 也换 CMP 坐标、BOM 退场），
那是三端终局而非过渡切片。

排查手法：KMP 制品的**根 `*.module` 常只有 `metadataApiElements`**，真 android 变体在
**`<artifact>-android`** 子模块里，要单独拉它的 `.module` 才看得到对 androidx 的约束。

## 4. 首块真实切片

按「只迁有真实消费方的东西」的规则，M1-2 只搬了两块，消费方都是
`:feature:tagrules`（以及 `:core:ui` 内部）：

| 内容 | 位置 | 说明 |
| --- | --- | --- |
| `ComposeEngine` / `parseComposeEngine` / `LocalComposeEngine` | `core/designsystem/…/ui/theme/ComposeEngine.kt` | 引擎语义进共享层 |
| `adaptive*Padding`（8 个函数） | `core/designsystem/…/ui/theme/AdaptivePadding.kt` | 自 `:core:ui` 搬入，包名不变 |
| `AdaptiveSwitch` / `TinySwitch` / `IconSwitch` | `core/designsystem/…/ui/widget/components/IconSwitch.kt` | 自 `:core:ui` 搬入；首个真双引擎组件 |

配套改动（`:core:ui` 侧）：

- 删除同名的 `AdaptivePadding.kt` 与 `IconSwitch.kt`；
- `ThemeResolver.isMiuixEngine(String)` 改为委托 `parseComposeEngine`（"miuix" 字面量不再
  散落在 Android 侧）；
- 在提供 `LocalLegadoThemeColors` 的三处（`AppTheme.kt` ×2、`ThemeColorSchemeOverride.kt` ×1）
  同步 `LocalComposeEngine provides parseComposeEngine(...)`。

**新增提供点时必须同时提供 `LocalComposeEngine`**，否则间距与开关形态会静默退回
Material3 一路（不报错，只是长得不一样）。

### 引擎条件式刻度，不是常量 token

间距在本仓是 Miuix 一套 / Material3 一套（如内容横向 12dp vs 16dp）。所以搬进来的第一件事
是把「当前是哪个引擎」这个**判定**共享化，而不是把数字抽成不带引擎语义的常量——后者会把
真实的条件语义抹平成假抽象。刻度集中在 `AdaptiveSpacing`（`internal object`）一处，供
`AdaptiveSpacingTest` 逐项冻结。

## 5. 验证

- `:core:designsystem:compileKotlinDesktop` / `compileCommonMainKotlinMetadata`：过；
- `:core:designsystem:desktopTest`：12 个用例全绿（新增 7 个：引擎解析 2 + 刻度表 5）；
- 四门禁 + `:feature:tagrules:testDebugUnitTest` + `:core:viewmodel:testDebugUnitTest`
  + `:app:compileAppDebugKotlin`：全绿；
- 变异测试：把 `AdaptiveSpacing.horizontal` 的 Miuix 值 12dp 改成 14dp →
  `AdaptiveSpacingTest.horizontalContentPadding` 失败（确认用例不会空转）。
- 版本断言：把 `libs.versions.toml` 的 `composeMultiplatformMaterial3` 临时改成别的值 →
  配置期直接失败并指出改哪里；改回后全绿。
- 行为等价核对：搬迁前后逐分支比对 dp 取值（`adaptive*Padding` 的 10 个函数的条件分支
  全部落到 `AdaptiveSpacing` 的 8 个成员，取值一一对应）。

**离线提醒**：CMP 相关 `*-metadata` 制品首次解析必须联网（`--offline` 会报
"No cached version available for offline mode"）；拉过一次后即可离线。

## 6. 对 M1-3 的含义

`tagrules` 的 UI 闭包约 20 个组件，按本片结论分两类：

- **可以直接搬**：只依赖 Compose foundation/ui + CMP material3 + 图标 + Miuix KMP 模块的组件；
- **仍需先处理**：依赖 `android.*`（`Context` / `Uri` / `Bitmap` / `LruCache`）、
  `LocalClipboard` / `LocalContext`、`rememberLauncherForActivityResult`、
  `FilePickerSheet`、`GSON` 门面，以及 Miuix 的 **android 专用别名模块**
  （`miuix-blur-android` / `miuix-preference-android` 等）的组件。

也就是说：M1-3 的顺序应是「先把平台能力抽成契约（SAF 选文件、剪贴板、JSON），
再按组件逐个搬」，而不是先搬 UI 再补契约。

## 7. CMP 多平台资源（`org.jetbrains.compose.resources`，M1-3j 定案并打通）

共享组件的文案**不能**再用 Android `R.string.*`（`androidx.compose.ui.res.stringResource`
在 desktop 源集里不存在）。项目选定的方案是 CMP 自带的资源体系。

### 7.1 依赖与目录

- 依赖：`org.jetbrains.compose.components:components-resources`，**版本 ref 必须是
  `composeMultiplatform`**（与 CMP 插件严格同线，convention 已在配置期断言）。
- 资源目录：`src/commonMain/composeResources/values{,-zh-rCN,-zh-rHK,-zh-rTW}/strings.xml`。
- 代码里用生成的 `Res.string.xxx`（`internal`，故**必须逐条顶层 import**）：
  `import <pkg>.Res` + `import <pkg>.res_<name>`。

### 7.2 包名：由 convention 从模块 path 推导

`:core:designsystem` → `io.legado.app.core.designsystem.res`。规则：`io.legado.app` +
path 的 `:`→`.`、`-` 去掉 + `.res`。

**必须显式设**：convention 没给项目设 `group`，而 CMP 的默认包名是
`{group}.{module}.generated.resources`，在这里会退化成不可读的名字。
只设 `packageOfResClass`、不动 `generateResClass`（默认 `auto`）⇒ 没有 `composeResources`
目录也没依赖 resources 库的模块**不会被生成 `Res`**，对现有 CMP 模块零影响。

⚠️ **包名只看模块 path，与源集里的 Kotlin 子包无关**（M1-3z 踩到）：`:feature:dict` 的源文件
住在 `commonMain/.../feature/dict/**rule**/`，但资源在 `commonMain/composeResources/` 根下
⇒ 包名仍是 `io.legado.app.feature.dict.res`，**不含 `rule`**。按 Kotlin 包名去推
`io.legado.app.feature.dict.rule.res` 会报 `Unresolved reference 'Res'`。
Kotlin 包目录与 CMP 资源包名是两套东西，别互相推。

### 7.3 两个必须记住的 DSL 事实

1. **`resources` 不是 `ComposeExtension` 的属性**。它是 `ComposeExtension` 作为
   `ExtensionAware` 注册的**子扩展**（类型 `ResourcesExtension`）
   ⇒ convention 里只能
   `extensions.configure<ComposeExtension> { extensions.configure<ResourcesExtension> { … } }`；
   模块侧写 `compose.resources { … }`。**写成属性访问编译期就 Unresolved reference**
   （`compose.resources.packageOfResClass = …` 报错；`compose.dependencies.*`
   也有同一族的坑，见 §3）。
2. **Android 侧要显式开资源处理**：AGP 的 `androidLibrary` target 默认**不启用**资源处理，
   而 `composeResources/` 要经 assets 打进 Android 产物（
   `copyAndroidMainComposeResourcesToAndroidAssets` 任务，产物是 `*.cvr`）
   ⇒ CMP convention 里必须
   `targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach { androidResources.enable = true }`。
   否则 Android 运行期读不到资源（`Res.string.x` 抛资源缺失）。
   放在 `legado.kmp.compose` 而不是 `legado.kmp.library`：只有 CMP 模块会产出/消费
   `composeResources`，pure 模块不该被带上资源处理。

### 7.4 ⚠️ 语义变化：不参与 Android 资源合并

`:core:ui` 此前的策略是「库侧 `res/values*/strings.xml` 只放默认值、app 侧同名 `strings.xml`
覆盖」。`composeResources` **打进 assets 由 `Res` 读取，不参与 Android 资源合并**
⇒ **app 侧同名资源覆盖不到它**。所以：

- 共享层必须**自带全部支持的语言目录**，不能只放默认值；
- 每个条目搬入前要与 app 侧同名资源**逐字节核对**（本片已核对，无用户可见变化）；
- **按需补条目**——搬哪个文件补哪几条，不要一次性把所有 R 键都搬过去。

### 7.5 验证要点

- 生成物位置（可自检）：
  - 访问器 `build/generated/compose/resourceGenerator/kotlin/commonMainResourceAccessors/<pkg>/String0.commonMain.kt`
  - `Res` 对象 `…/commonResClass/<pkg>/Res.kt`
  - Android assets `build/generated/assets/copyAndroidMainComposeResourcesToAndroidAssets/composeResources/<pkg>/values*/strings.commonMain.cvr`
- 必跑：`:core:designsystem:compileKotlinDesktop` + `compileAndroidMain`（**desktop 编译是唯一
  能暴露「同包兄弟引用」的判据**），再 `:app:assembleAppDebug` 确认 assets 真进了产物。
- 离线提醒同 §5。

### 7.6 一次踩到的工具坑

批量改写 `R.string.x` → `Res.string.x` 的脚本必须**先删旧 import 行、再统一插入新 import**，
否则 import 顺序错位。

⚠️ **行尾不是问题，别在这里花时间（2026-09-11 实测纠正）**。本仓 `core.autocrlf=true`，
**仓库 blob 一律存 LF**、工作区的 CRLF 是检出时 smudge 出来的。实测：同一文件转成
LF / CRLF / MIXED 三种行尾后，`git hash-object --path=<f> --stdin` 算出的 blob **完全相同**，
`git add` 后也没有内容 diff —— LF 文件只会留下一条 `warning: LF will be replaced by CRLF`
和一个 stat 级别的「假 M」（`git update-index --refresh <f>` 后即消失）。所以：

- **不需要为「保持 CRLF」做任何收尾工作**，`Edit` / `Write` 产出 LF 也无害；
- 旧版本本节写的「行尾基准是 CRLF，否则 diff 会炸」是**误诊**：当时的证据
  `git show HEAD:<f> | grep -c $'\r$'` 会被 smudge 过滤器干扰、永远输出 CRLF，
  根本不能用来判 blob 行尾（正确判据是 `git cat-file blob HEAD:<f>`）。

真正会炸的只有 **import 顺序错位**。

### 7.7 ⚠️ 同名不同包：`stringResource` 有两个

`org.jetbrains.compose.resources.stringResource` 与
`androidx.compose.ui.res.stringResource` **简单名逐字相同**，前者可用后者不可用。
任何「按符号名扫全文」的静态检查都会在这里误判（`portability-triage.py` 就把 M1-3j 已搬进
designsystem 的 `ReorderAccessibility.kt` 误报成 Android-only）。判据只能是
**import 溯源**：这个简单名是从哪个包导入的。

CMP 1.12.0 的 `components-resources` 实测提供：`stringResource` / `vectorResource` /
`painterResource` / `imageResource` / `pluralStringResource` / `stringArrayResource`；
**没有** `dimensionResource`（无 dimen 支持）⇒ 后者仍是 android-only。

## 8. 平台能力和 `commonMain` 的交界：窄契约（M1-3l）

当一段 `commonMain` 逻辑只差**一两个平台能力**就能搬时，做法是**抽契约 + 宿主注入**，
而不是 `expect/actual`、也不是把整段留在 Android 侧。本仓已有 4 个同型先例：
`BigDataStore`、`SourceRuntime`、`JsExtProvider`、`ThemeSeedColors`。

以配色引擎（M1-3l）为例：`ThemeEngine` 里只有两处 `android.*`
——动态取色（`dynamicLight/DarkColorScheme(context)` + `Build.VERSION.SDK_INT`）与
`Context.primaryColor`。契约拆成两个单方法 `fun interface`：

```kotlin
fun interface ThemeSeedColorProvider { fun primaryColor(): Int }
fun interface DynamicColorSchemeProvider { fun colorScheme(darkTheme: Boolean): ColorScheme? }
```

### 8.1 先证明「哪条分支不可达」，再决定契约面

**「把 `Context` 换成契约」不等于「行为会变」。** 改造前要逐个调用点查上下文到底有没有
真的参与计算——M1-3l 查完发现 `ThemeSeedColors.primaryColor(context)` 在所有调用点都
**不可达**（三个调用点传的都是非空 `Int`；`OpaqueColorScheme` 恒走 `forceOpaque = true`
把 `Transparent` 归一到 `WH`，落不进 `Custom` 分支）。这类结论直接决定契约要多宽、
以及语义变化要不要写进提交文案。

### 8.2 失败语义要按「缺失是否合理」区分，别一刀切

| 契约 | 未注入时 | 理由 |
| --- | --- | --- |
| `ThemeSeedColorProvider` | `requireNotNull` **抛异常** | 值来自 app 偏好存储，缺失是**配置错误** |
| `DynamicColorSchemeProvider` | 返回 `null`，回落预定义配色 | 没有系统调色板在 desktop/iOS 上是**正常状态**，不是错误 |

要求所有非 Android 宿主都注入一个「永不命中」的实现才肯编译，是仪式而不是约束。

### 8.3 ⚠️ G4 门禁：新区域里的全局 `appCtx` 是 blocking

契约的 Android 实现常要 `Context`。**别在新目录里直接 `import splitties.init.appCtx`**
——`checkLegacyArchitecture` 按 `^import splitties\.init\.appCtx$` 统计，
`app/main/io/legado/app/ui/theme` 这类**新区域**首次出现即失败：

```
app/main/io/legado/app/ui/theme：全局 Context 直连（splitties appCtx）首次出现 1 处；
新区域必须为零，或经评审后在基线中显式登记
```

**正确响应是把 `Context` 显式传参**（`installAndroidThemePlatform(this)`，启动时调用点只有
`Application.onCreate`）。往基线里登记是下策——门禁的意图正是「新代码不许再新增隐藏的
全局耦合」，而显式传参本来也是更干净的 DI。

### 8.4 包名不变 ⇒ 消费方零改动

契约与共享实现都沿用 `io.legado.app.ui.theme` 包名，因此 `:core:ui` 的 `AppTheme.kt`
（同包、Android 侧）**不需要新增 import**，只需删掉 `getColorScheme(context = …)` 那个实参。
`git mv` 的方式与 M1-3h 起各切片一致。

### 8.5 契约住在哪个模块，由**契约类型**决定（M1-3o 的新先例）

§8 前几节的契约都住在 `:core:platform`——那是**纯 KMP、零 Compose** 的模块，所以装得下
`Clipboard` / `Toaster` / `MimeTypeResolver` / `ThemeSeedColorProvider` 这些不碰 Compose 的类型。

M1-3o 遇到一个装不进去的：`plainTextClipEntry` 的契约方法要返回
`androidx.compose.ui.platform.ClipEntry`，而 `ClipEntry` 是 CMP 的 `expect class`
（common 侧**没有构造器**；Android 是 `ClipEntry(clipData: ClipData)`、desktop 是
`ClipEntry(nativeClipEntry: Any)`）。它进不了 `:core:platform` ⇒ **契约只能住在 Compose 模块**
（本片放 `:core:designsystem`，与 helper 同包 `io.legado.app.ui.util`）。

结论两条：

1. **不要按惯例机械地往 `:core:platform` 塞契约**——先看契约签名里有没有 Compose 类型。
   有 ⇒ 落在 Compose 模块；这是结构上必要的选择，不是随手放。
2. **契约模块的归属变化不影响注入点**：Android 实现仍在
   `io.legado.app.platform.AndroidPlatformCapabilities`，仍在 `PlatformServices.install()`
   统一注入；`di` 侧不受影响（app 同时依赖两个模块）。

⚠️ 顺带一条排除法：这里本可以用「旧 API `ClipboardManager.setText(AnnotatedString)`」绕过
`ClipEntry`（它在 common 也有），但 CMP 1.12.0 里 `ClipboardManager` 与 `LocalClipboardManager`
都已是 `@Deprecated("Use Clipboard instead, which supports suspend functions.")`。

### 8.6 镜像案例：把平台类型挡在签名之外，契约就能回到 `:core:platform`（M1-3p）

§8.5 容易被读成「返回平台/Compose 类型 ⇒ 只好放 designsystem」。M1-3p 是它的镜像：
`widget/components/AppContainerBackground.kt` 要读 `.9.png` 九宫格，Android 侧交出去的是
`NinePatchDrawable`。若契约签名写成 `fun load(path: String): NinePatchDrawable?`，那就不仅是
「住 designsystem」的问题——`android.*` 连 designsystem 的 `commonMain` 都进不去（G2 拦），
只能退化成 `expect/actual` 双轨。实际签的是：

```kotlin
// :core:platform/commonMain
fun interface NinePatchLoader { fun load(path: String): Any? }   // 就是图像加载器 data 的形参类型
```

**不透明 payload ⇒ 没有任何平台类型漏进签名 ⇒ 契约照惯例住 `:core:platform`**；Android 实现
（`BitmapFactory` + `NinePatch.isNinePatchChunk` + `NinePatchDrawable`）留在 `app` 的
`AndroidPlatformCapabilities`，注入点仍是 `PlatformServices.install()`。

所以决策顺序应该反过来：**先问「契约签名能不能不出现平台类型」，再问「契约该住哪个模块」**。

| 切片 | 契约签名里有平台/Compose 类型吗 | 契约落点 |
| --- | --- | --- |
| M1-3o | 有（`ClipEntry` 是 CMP `expect class`） | `:core:designsystem` |
| M1-3p | 没有（`Any?`，即加载器 `data` 的形参类型） | `:core:platform` |

两者的注入点、Android 实现位置、失败语义判据**完全一致**——只有「契约声明放哪」这一个自由度在变。

配套两条：

- **失败语义仍看「缺失是否合理」**：九宫格缺失是正常状态（非 `.9.png` / 解析失败 / 平台无此概念
  三种 `null` 收敛成**同一条**回落路径：按原路径交给 Coil）⇒ 未注入一律 `null`，不抛异常，
  同 `MimeTypeResolver`。判断依据依旧是 `topics/gates-and-verification.md` 里那条判据，
  而不是「契约住哪个模块」。
- **顺手删掉注入依赖之前，先读宿主的接线**：M1-3p 删掉了
  `imageLoader = koinInject<ImageLoader>()`，依据是 `App` 实现 `coil3.SingletonImageLoader.Factory`
  且 `newImageLoader(context) = get()`——正是那个 Koin 单例，所以省略参数后走
  `SingletonImageLoader` 拿到的是**同一个实例**（crossfade / 各 Decoder / `CoverInterceptor`
  一字不差），而 `:core:designsystem` 因此**不必引入 Koin**。省下一个依赖的前提是能证明等价，
  不是「看起来应该一样」。

### 7.8 配方的第二次使用：零摩擦，但仍要盯三件事

M1-3m 用同一套配方把 `SelectionBottomBar.kt` 搬进 `commonMain`（补 `select_all` /
`invert_selection` / `more_menu`），**零新增依赖**——闭包全在 designsystem（`theme/*` 与
`RoundDropdownMenu*` 都是 M1-3l 搬的）。复用时要盯的三件事：

1. **值逐字节核对，且要核两侧**：`:core:ui` 与 `app` 的同名条目**都要比**（本仓两者多数同值，
   但「用户可见的那份」可能只有一侧有）。比过才能写「无用户可见变化」。
2. **`composeResources` 的 XML 本来就是 LF，但那也无关紧要**——见 §7.6 的纠正：
   行尾不影响 blob，不必为了「保持 LF」做任何事。
3. **加字符串时别插错位置**：新条目要插在**已有的** `</resources>` **之前**。
   用脚本追加时很容易写成「原 `</resources>` 保留 + 末尾再补一个」，产出双闭合标签
   （XML 直接解析失败，M1-3n 就踩了一次）。写完用 `xml.dom.minidom.parse()` 校验一下。

### 7.9 ⚠️ Kotlin 块注释**可嵌套**：KDoc 里写通配 MIME 会炸

M1-3n 实录：在 `typesOfExtensions` 的 KDoc 里写了 MIME 通配字面量（形如「星号斜杠星号」和
「text 斜杠星号」），`compileKotlinDesktop` 报一串 `Expecting a top level declaration`。
根因是 **Kotlin 的 `/* */` 嵌套**：

- `*/` 会**提前闭合** KDoc（后面的正文变成顶层垃圾）；
- `/*` 会**再开一层**注释（于是真正的 `*/` 只关掉里层，KDoc 一直开着）。

两个序列都在 MIME 字面量里 (`*/*`、`text/*`)，于是同一段注释同时踩中两种。
**判据是注释上下文，不是全文**——同一个字面量出现在**字符串字面量**里完全合法
（`types.add("*/*")` 一直没问题）。改注释后要用「注释感知」的扫描器验一遍
（朴素全文扫描会把字符串里的命中一起报出来，产生噪音）。

## 9. Feature 模块整体转 CMP 的依赖与验证坑（M1-3w）

`feature:tagrules` 是仓库里首个**整体**转 CMP 的 Feature（此前转 CMP 的都是 `:core:*`）。
源码侧的搬运是机械的（实录见 `feature-slicing-audit-tagrules.md` §26），真正花时间的是
**依赖声明**与**验证纪律**——下面 5 条都不在「读 import 猜可移植性」的可见范围内。

### 9.1 KMP 源集依赖块里没有 `platform()`

```kotlin
kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(platform(libs.koin.bom))          // ❌ Unresolved reference 'platform'
            implementation(project.dependencies.platform(libs.koin.bom))  // ✅
        }
    }
}
```

`sourceSets.*.dependencies {}` 的接收者是 `KotlinDependencyHandler`，它**不**继承 Gradle 的
`DependencyHandler`，所以 `platform` 那个扩展函数在这个作用域里不存在。顶层
`dependencies {}` 块里照旧可以写 `platform(...)`。

### 9.2 无版本 alias 在 KMP 模块里会断链

版本目录里有一批**没有版本号**的 alias——它们的版本一直由 BOM 提供（`koin-compose` /
`koin-core` 之于 `koin-bom`，`androidx-lifecycle-runtime-compose` 之于…）。在 android-only 模块里
这不成问题，因为 app 侧引了 `platform(libs.koin.bom)` 之后**同一配置**能看到版本约束；

但新 KMP 模块的 `androidMain` 是**另一个配置**（`:xxx:androidCompileClasspath`），BOM 不在里面：

```
Could not resolve io.insert-koin:koin-androidx-compose.
  > No cached version of io.insert-koin:koin-androidx-compose: available for offline mode.
```

⇒ 用了无版本 alias 就**必须**在同一个源集里引对应 BOM。第 9.1 条的写法就是为它准备的。

### 9.3 传递依赖可能解析到本地没有的版本 ⇒ 按「既有理由」显式对齐

引入 `lifecycle-runtime-compose` 后，android 侧配置里冒出
`androidx.navigationevent:navigationevent-compose:1.0.2`（由 `lifecycle-runtime-compose-android`
传递），而 app 侧早就为「预测式返回崩溃」把它抬到 `1.2.0-alpha04`——**app 的约束不跨模块传递**，
所以新模块要自己声明一次：

```kotlin
androidMain.dependencies {
    // 版本对齐（不是直接使用）：见 libs.versions.toml 的 navigationevent 注
    implementation(libs.androidx.navigationevent.compose)
}
```

判据是「本机/目标环境能否解析到」，不是「有没有 import 到它的类型」。**注释里要写清是
版本对齐**，否则下一个人会以为这个模块真的在用 navigationevent。

### 9.4 lifecycle：用 `androidx.lifecycle` 的 KMP 坐标，它有 `-desktop` 变体

两套坐标都能在 CMP 项目里见到，选错会掉进不同的坑：

| 坐标 | desktop 产物 | 坑 |
| --- | --- | --- |
| `androidx.lifecycle:lifecycle-viewmodel`（**选它**） | `lifecycle-viewmodel-desktop`（独立 artifact id） | 无；与 Android 侧同版本号 |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` | 有 | 2.9.6 的元数据 `requires androidx.lifecycle:…:2.9.4`，目标环境若没缓存这个版本就要联网 |

⚠️ **「`androidx.lifecycle` 只有 android 产物」是错的**：它按 KMP 惯例把 desktop 变体发成
**独立 artifact id**（`lifecycle-viewmodel-desktop` 而不是 `.module` 里的 variant），
光看 `lifecycle-viewmodel/2.11.0/` 目录里只有一个 `.aar` 会得出相反结论。
**判据只能是「解析 desktop 配置」，不是看目录。**

### 9.5 验证纪律：`clean` 要单独跑

`./gradlew clean :some:task` **在配置期失败时，`clean` 根本不会执行**（Gradle 先配置全图再跑任务）。
后果不是「构建失败」而是**残留旧产物**：

- 模块从 Android library 转 KMP 后，`build/test-results/testDebugUnitTest/`（旧任务名）会留着；
- 计数脚本按目录读 XML ⇒ **读到旧结果，照样报「与基线一致」**——它证明的是旧结果还在。

⇒ 规矩：要干净重建就**单独跑 `./gradlew clean`**，在输出里确认 `:xxx:clean` 执行过，再跑验证集。
（KMP 转轨后模块的测试任务名是 `testAndroidHostTest`，`testDebugUnitTest` 不再产出。）

## 10. Feature 转 CMP 的两个**前置**判据（M1-3x / M1-3x-pre）

§9 是「一个 Feature 的内部怎么拆源集」。但在动手之前还有两道**筛查**，它们的失败方式都是
「代码看着干净、门禁/编译才报」——`feature:tagrules` 恰好两条都躲过了，`feature:replacerules`
两条全撞上，所以补在这里。

### 10.1 前置一：Feature 用到的 `io.legado.app.ui.**` 资产必须已在 `:core:designsystem/commonMain`

`:core:ui` 是 **Android-only** 模块（`alias(libs.plugins.android.library)`），`cmp` Feature 的
`commonMain` 看不到它。组件是**一片一片**搬进 designsystem 的（M1-3i…v），所以「这个 Feature 依赖面
干净吗」不能靠印象，要逐个符号查**声明处住哪个模块**：

```text
对 Feature 的每个 import（含同包兄弟！）→ 在 core/*/src 下找真正声明该符号的文件
  → 只要有声明落在 core/ui/ ⇒ 该符号是 commonMain 的硬阻塞
```

判据是**声明文件的模块**，不是「包名看起来像 foundational」——`io.legado.app.ui.theme.*` 同时存在于
`:core:designsystem`（已搬）与 `:core:ui`（旧副本的来源），光看包名必然判断错。同理，「查不到」
不等于「干净」：同包兄弟调用、扩展函数接收者、`when` 位置都不会出现在 import 行里。

- `tagrules` 恰好只用已搬走的 17 个组件 ⇒ 前置为空；
- `replacerules` 撞上 `AppTabRow` / `GroupManageBottomSheet` / `contentProcess/ContentProcessUiState`，
  `txttocrules` + `dict` 撞上 `rules/RuleEditSheet` ⇒ 合成一个 **M1-3x-pre** 切片先上提（4 文件，
  零新增依赖，其中 2 个文件要把 `R.string.*` 换成 `Res.string.*`，见 §7）。

上提切片自身的纪律：包名不变（消费方 import 零改动）、`:core:ui` 侧因搬走而变死的同名资源
一并删除、文案四语言逐字取自原 Android res（搬前脚本比对 `designsystem == core:ui == app`）。

### 10.2 前置二：新源集 / 新包目录在 G4 棘轮上**起步就是零**

`checkLegacyArchitecture` 的真实数据在 **`gradle/architecture/legacy-baseline.txt`**（不是在
`build.gradle.kts` 里），键是 `<category>|<module>/<sourceSet>/<packageDir>`，计数**精确匹配**：

| category | 命中口径 |
| --- | --- |
| `legacyHelp` / `legacyBase` | `import io.legado.app.help.**` / `base.**` |
| `gson` / `appCtx` / `appDb` | `import io.legado.app.utils.GSON` / `splitties.init.appCtx` / `io.legado.app.data.appDb` |
| `legacyNaming` | 以 `Help`/`Utils` 结尾的 `io.legado.app.**` import |
| `coreProvider` | 行内出现 `Clipboard|Toaster|Logger|…Provider`（跳过注释行、排除声明文件自身） |

转 CMP 后源集从 `main` 变成 `commonMain`/`androidMain`，于是三件事同时发生：

1. **旧条目必须删**：`<module>/main/...` 已不存在，门禁会报「已减少到 0/文件已移除，请下调」。
2. **新区域必须为零**：任何 legacy 门面 import 落在新源集里 ⇒ 「首次出现 N 处」直接拦。
3. **不能靠放宽基线过关**（AGENTS.md + §8.3 记录的棘轮纪律）。解法是**把依赖搬到干净包名**：
   M1-3b 把 `RuleTransferUseCase` 放进 `io.legado.app.core.rules`；M1-3x 把 `ReplaceAnalyzer`
   从 `io.legado.app.help` 搬到 `io.legado.app.data.rules`（连带 2 个调用方 import 更新、
   对应区域 `legacyHelp` 基线**下调** 1 处）。顺带把 `XxxProvider.current` 换成注入契约——
   在 Android Route 里就是 `koinInject<Toaster>()`（`:app` 侧 `single<Toaster>` 与 Provider 路径
   共用同一组工厂，语义不分叉），这本来就是 M2 要删 Provider 的方向。

⚠️ 还有一条容易漏：门禁**只扫 `app/src/main/java` 前缀的区域**是误解——`app/main/...` 只是命名，
迁移到 `feature/<name>/src/{commonMain,androidMain}` 的代码**照样被扫**（口径是「模块/源集/包目录」的
生产源集，`*test*` 源集跳过）。所以「搬出 app 目录就不受门禁管」是错的，反过来才对：
搬出去之后是**新区域**，要求更严。

---

## 11. 要不要为「规则导入」建平台契约？（M1-3x / 3y / 3z 三片收敛）

四个规则页转 CMP 时，三个各自不同地回答了同一个问题。判据不是「有没有旧格式」。

### 11.1 一句话判据

> **看「实体的 JSON 兼容逻辑是否落在共享层看不见的地方」。**
> 如果是 —— 建平台契约；如果兼容逻辑就在实体自身（`@SerializedName` 等）或不需要兼容 —— 直接换 `JsonCodec`。

「共享层看不见的地方」在本仓库有两个具体形态：

1. **JVM-only 三方库**：旧格式解析靠 `com.jayway.jsonpath`（`ReplaceAnalyzer`）。
2. **app 侧 `GSON` 门面注册的 deserializer**：`JsonCodec` 是按 `INITIAL_GSON` 配置重建的
   **独立实例**，不含任何业务侧 `registerTypeAdapter`。

### 11.2 三片对照

| 切片 | 实体 | 兼容逻辑在哪 | 结论 |
| --- | --- | --- | --- |
| M1-3x `replacerules` | `ReplaceRule` | 实体自身无兼容；**旧 jsonpath 格式**要 JVM 库 | 建 `ReplaceRuleImportCompat`，**只兜旧格式**（标准 JSON 共享层可解） |
| M1-3y `txttocrules` | `TxtTocRule` | 旧键名 `rule`→`chapterRule` 已从 `@SerializedName(alternate=)` 搬成门面 deserializer | 建 `TxtTocRuleImportCompat`，**标准 JSON 也走平台** |
| M1-3z `dict` | `DictRule` | 无自定义 deserializer、无 `alternate` | **不建契约**，直接 `JsonCodec` |

⚠️ M1-3y 是最容易漏的一类：只看实体源码（`@SerializedName` 已经没了）会以为可以直接换，
但兼容逻辑**搬走了而不是删掉了** —— 它现在住在门面上，共享层看不见。
⇒ 动手前必须查 **`GSON` 门面的 `registerTypeAdapter` 清单**，不能只看实体定义。

### 11.3 契约实现放哪（G4 技巧）

- 需要 `GSON` 的解析实现：放 `core/data/src/androidMain/.../utils/GsonExtensions.kt`
  —— 与 `GSON` **同包**，引用无需 import。G4 的 `gson` 规则**按文件计 import**，
  同文件新增函数**不涨计数**（放进 `domain/gateway` 或 `data/entities` 会因「新区域首次出现」直接失败）。
- 契约实现（转接层）：住 `app/domain/gateway`，只转接、不 import `GSON`。

### 11.4 失败语义

`RuleEntitySpec.parse` 的失败语义是**抛异常**（`RuleTransferUseCase` 会捕获并转成 UI 错误）。
注意 `JsonCodec.fromJsonObject(json, KClass)` 返回**可空 `T?`**，不是 GSON 门面的 `Result<T>`
⇒ 写 `?: throw Exception(...)`，照抄 `.getOrThrow()` 会报类型推断失败。

---

## 12. Desktop host 与 CMP UI 测试（M1-4）

### 12.1 让 desktop 证据越过「能编译」：headless UI 测试

`compileKotlinDesktop` 全绿**证明不了能渲染**。最小可行配方（见 `smoke/compose-desktop-probe`）：

```kotlin
desktopTest.dependencies {
    implementation(kotlin("test"))
    implementation(libs.compose.multiplatform.ui.test.junit4)  // org.jetbrains.compose.ui:ui-test-junit4
    implementation(compose.desktop.currentOs)                   // Skiko 渲染运行时，缺它测不出画面
}
```

⚠️ **CMP 1.12.0 的两个陷阱**：

1. `compose.runtime` / `compose.foundation` / `compose.material3` 这些 accessor 已 deprecated
   （要求直写坐标），且 **`compose.uiTestJUnit4` 已不存在** ⇒ 在版本目录显式加
   `org.jetbrains.compose.ui:ui-test-junit4`（ref `composeMultiplatform`，必须与插件同线）。
   `compose.desktop.currentOs` 这个 accessor **未废弃**，仍可用。
2. `runComposeUiTest` 分 v1 / v2。用 **v2**（`androidx.compose.ui.test.v2.runComposeUiTest`）：
   v1 已废弃且**默认立即执行协程**，对「VM 状态流经 Flow 到界面」会掩盖「首帧还没收到数据」
   这类时序问题；v2 默认 `StandardTestDispatcher`，更贴近生产。

### 12.2 主题是渲染的隐藏前置条件（本片最重要的发现）

`:core:designsystem` 的组件（`RoundDropdownMenuItem`、`AppModalBottomSheet`、`FilePickerSheet`…）
读 `LegadoTheme.colorScheme`，背后是 `LocalLegadoColorScheme` / `LocalLegadoTypography`，
**默认值是 `error("No ColorScheme provided")`**。Android 侧由 `:core:ui` 的 `AppTheme`
提供——而 `:core:ui` 是 **Android-only** ⇒ desktop 上一渲染就抛 `IllegalStateException`。

⇒ 「转 CMP 编译通过」与「能在 desktop 上渲染」之间隔着这一层。解法（已实施）：把两个
**纯映射**函数上提到 `core/designsystem/commonMain`（包名 `io.legado.app.ui.theme` 不变
⇒ `:core:ui` 侧零改动）：

- `ColorScheme.toLegadoColorScheme()`（两个重载，只碰 `ColorScheme` / `Color`）；
- 整个 `Typography.kt`（import 全是跨平台的：material3 / `ui.text.font` / miuix `TextStyles`）。

desktop 侧用 `DesktopTheme` 搭最小主题：`MaterialTheme` 打底 + 两个映射补 Local。
⚠️ 它不是 `AppTheme` 的等价物（无自定义字体、无 Miuix 引擎切换、无动态取色）。

### 12.3 desktop host 的组装要点

- **Room**：`Room.databaseBuilder<AppDatabase>(name = path).setDriver(BundledSQLiteDriver())
  .setQueryCoroutineContext(Dispatchers.IO)`（照抄 `smoke/room-kmp-probe`）。
- **Koin**：版本目录里 `koin-core` / `koin-compose-viewmodel` **无版本号** ⇒ 必须
  `implementation(project.dependencies.platform(libs.koin.bom))`。
  ⚠️ 是 `implementation(平台)`，**不是**裸调 `project.dependencies.platform(...)`——后者只是
  创建了 BOM 对象却没加进任何配置，不生效（报错长成 "Could not find …:koin-core:."，
  版本号为空，看起来像依赖不存在）。
- **依赖不传递**：`RuleTransferPlatform` 在 `:core:viewmodel`，而 Feature 对它是
  `implementation` ⇒ host 必须自己显式声明，别指望从 Feature 传过来。
- **UI 测试要等真实时间**：Room 查询跑在真实 IO 线程上，用 `waitUntil { … }` 轮询，
  而不是只 advance 测试时钟（那驱动不了数据库回调）。

### 12.4 变异验证是 UI 测试的入场券

把插入的数据与断言改成不一致 ⇒ 测试**必须失败**。UI 测试的 `assertIsDisplayed` 在节点树
异常时可能假通过，不做的变异验证，「全绿」没有意义。

## 13. Nav3 在非 Android 平台上的真实边界（M1-4b 实测）

M1-4 之后剩下的第一个问题是「Nav3 能不能跨平台」。答案不是「能」或「不能」，而是**分层**的。
下面每一条都是解 Gradle Module Metadata / `javap` 解 jar 实测的结果，不是推测。

### 13.1 制品矩阵

| 制品 | desktop (jvm) | iOS native | 结论 |
|---|---|---|---|
| `androidx.navigation3:navigation3-runtime` | ✅ 真 jar（`nav3-runtime-desktop-1.2.0-beta01.jar`） | ✅ | **可共享** |
| `androidx.navigation3:navigation3-ui` | ⚠️ 只有 `navigation3-ui-jvmstubs` | ❌ | 见 13.2 |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | ❌ 只有 `-android` 变体 | ❌ | 见 13.3（可自建） |
| `androidx.lifecycle:lifecycle-viewmodel`（KMP） | ✅ `ViewModelStore` 是 public | ✅ | 可共享 |
| `androidx.navigationevent` / `androidx.savedstate` | ✅ | ✅ | 可共享 |

⚠️ 判据要用**实际制品**：Gradle 缓存的 `.module` 文件里出现 `desktopApiElements-published`
不等于桌面实现存在（`navigation3-ui` 也曾有 desktop 变体记录，实际是 jvmStubs）。
拿不到就 `curl` 直接下 jar 再 `javap`——比"猜 API 后编译试错"快得多。

### 13.2 `jvmStubs` 不是「能跑的桌面实现」

`navigation3-ui-jvmstubs-1.2.0-beta01.jar` 里 `NavDisplay` **只有三个 metadata 工厂**：
`transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec`。
**没有 `NavDisplay(backStack, entryProvider, …)` 这个 `@Composable` 本体**。

⇒ 桌面端**连编译 `NavDisplay(…)` 都过不去**。jvmStubs 的用途是让「写了 NavDisplay metadata
的共享代码」在非 Android 平台能编译，不是让 NavDisplay 可用。
**Nav3 的 UI 层在非 Android 平台不存在，共享导航图必须自带渲染层。**

### 13.3 entry 级 ViewModel 作用域：缺的是 decorator，不是能力

- `ViewModel.clear()` 是 **internal**（`javap` 显示 `clear$lifecycle_viewmodel`），外部无法直接调；
- 但 KMP 的 `ViewModelStore` 是 public，`clear()` 也是 public。

⇒ host 侧维护 `destination -> ViewModelStore` 映射、entry 离栈时 `store.clear()`，
即可复刻 `rememberViewModelStoreNavEntryDecorator()` 的语义。实测证据：
`DesktopNavigationHostTest.clearsEntryViewModelWhenEntryLeavesBackStack` 断言 pop 后
`viewModelScope.isActive == false`（不是"看起来清理了"）。

### 13.4 `rememberNavBackStack` 在 desktop 上不是必需的

`NavBackStack()` / `NavBackStack(vararg T)` 构造是 **public**（`javap` 实测）⇒ desktop 直接
持有实例即可，不必走 `rememberNavBackStack`（它需要 `SavedStateConfiguration` 与
`@Serializable` 的 nav key）。**返回栈恢复**（进程重建）才需要补 serialization，
那是把 nav key 上提为三端共享契约时的独立切片。

### 13.5 本仓的落地形态（M1-4b）

- `DesktopRoute`（`sealed interface … : NavKey`）与 `DesktopNavHost` 住 `host/desktop` 的
  **`commonMain`**：`:host:desktop` 是 KMP 模块（android + desktop 双 target）⇒ 这两个文件
  被两个 target 编译，"导航契约不依赖 Android 类型"因此有编译期证据。
- `DesktopNavHost` = `backStack.last()` → `entryProvider(key)` → `NavEntry.Content()`
  + `rememberSaveableStateHolder()`（每 destination 一份 saveable 状态）
  + 自维护的 `ViewModelStore`（见 13.3）。
- 仍未接的两项（`NavDisplay` 的剩余职责）：**入场/退场动画与 predictive back**、
  **同栈结果回传（picker）**。桌面端要用它们只能自建，成本按需评估——不要在 M1/M2 顺手做。

### 13.6 第二目的地为什么不跨 Feature

跨 Feature 导航（例如 dict → txttocrules）在 desktop 上的真实成本不是"多写一个 entry"，
而是**第二个 Feature 的整套平台注入面**：`txttocrules` 需要 `TxtTocRuleImportCompat` 的
desktop 实现（= 在 `:core:data` 之外**再写一套 JSON 解析**，旧键名 `rule→chapterRule` 的
语义无法证明与 GSON 门面一致），`replacerules` 的 `ReplaceRuleImportCompat` 依赖
`core/data/src/androidMain` 的 `ReplaceAnalyzer`（jsonpath，JVM-only）。
⇒ 本片的第二个目的地是 **host 入口页**（宿主的真实职责：聚合入口），把"跨 Feature 导航"
留到契约实现齐备之后再做。**导航验证不需要第二个 Feature，需要一个真实的两屏。**

## 14. 共享层怎么拿平台能力：参数注入 vs 注入点（M2-1）

M2-1 把 `ImportJsonEditorProvider`（`:core:platform` 的全局注入点，**唯一使用方是 designsystem
的 `BatchImportDialog`**）改成参数注入，并删掉 Provider。这条形态对"共享层组件需要平台能力"
的所有场合通用，选型看**这个能力有没有可回落的默认实现**：

| 场合 | 形态 | 例 |
|---|---|---|
| 公共 Composable 需要能力才能渲染，且**没有**合理回落 | **参数**（无默认值） | `BatchImportDialog(importJsonEditor = …)`（M2-1）、各 Feature Screen 的 `onPick*` 回调 |
| 能力缺失时有明确的**展示性回落** | 注入点 + `current` 可为 null，调用方显式写回落分支 | `NinePatchLoaderProvider`（null ⇒ 按原路径加载）、`MiuixPreferenceRendererProvider`（null ⇒ 落 Material3 分支） |
| 有明确默认值的**引擎语义** | CompositionLocal + 默认值 | `LocalComposeEngine` |

`ImportJsonEditor` 必须走第一条：它三个方法都有「看似合理」的降级返回值（`fieldsOf` 返回 null
的语义是「该对象不可编辑」），所以「这个平台还没做」不能拿降级值表达——那会把平台缺口伪装成
内容属性。抛异常 + 参数注入（漏传即编译错误）是唯一不撒谎的表达。

三条配套纪律：

1. **绑定在各 host 的 composition root**：`:app` 的 `appModule` 绑 Android 实现，
   `host:desktop` 的 `desktopHostModule` 绑 desktop 实现。共享层不认识 service locator，
   也不需要为了拿实现而依赖数据层。
2. **平台缺口用测试钉住**：`host:desktop` 的 `DesktopImportJsonEditorTest` 断言三个方法都抛
   `UnsupportedOperationException`——谁把它改成"返回 null 让它别崩"，谁就得先改这条断言。
3. **删掉注入路径，不留死代码**：`:app` 的 `PlatformServices.install()` 里那行
   `ImportJsonEditorProvider.install(...)` 与字段一起删除；"留着不用"会让下一次读代码的人
   以为还有第二条路径。

## 15. 消费方拿不到 DI 时：平台原语 `expect object`，而不是注入点（M2-2）

§14 的前提是"消费方是共享层组件，能力可以顺着参数传下去"。但有一类消费方**根本进不了 DI 图**：

`BigDataStore` 的调用方是 Room entity 的实例方法（`BaseBook` / `BaseRssArticle` / `BookChapter`
的 `putBigVariable` / `getBigVariable`）。而

1. entity 由 Room 构造，不经过 Koin；
2. 方法名是**书源 JS 兼容面**——`AnalyzeUrl` 里 `bindings["book"] = ruleData as? Book`，
   脚本直接调 `book.putVariable(...)`，签名与存在性都不能动；
3. 规则求值入口（`WebBook` / `BookList` / `BookContent` / `Rss` / `RssParserByRule`）全是
   `object` 单例，也没有构造注入的位置。

⇒ **构造注入与参数注入两条路都被物理封死**（这也是"上游装配到数据层"方案在本仓不可行的原因：
闭包里有 66 处 `WebBook.*` 调用）。

此时唯一干净的形态是：**把实现下沉共享层，实现只依赖一个平台原语 `expect object`**。

| 层 | 内容 | 例（M2-2） |
|---|---|---|
| 共享层 | **全部业务语义**（路径规则、分区布局、标记文件） | `core/data/commonMain` 的 `RuleDataFileStore : BigDataStore` |
| 平台原语 | **只有原始能力**（根目录 + 文件 IO + MD5） | `core/platform` 的 `expect object RuleDataStorage` |

判据与纪律：

1. **原语必须真的是"原语"**：`RuleDataStorage` 只装 `rootDir` / `md5` / `readText` / `writeText` /
   `exists` / `delete` / `list`，不认识 `book` / `rss` / `bookUrl.txt`——**目录布局是共享层知识**。
   把布局漏进 actual 就等于把业务语义复制到每个平台，每个平台都可能漂移。
2. **平台实现可以是两份相同的 JVM actual**：`androidMain` 与 `desktopMain` 各一份 `actual object`，
   内容一致（先例：`JvmFileSystem`、`JcaDigest`）。这在本仓**不违反去重纪律**——`actual` 的定义
   域就是各自的 source set，共享不了，唯一的选择是"两份相同"或"多一个中间层只为省重复"。
   ⇒ `diff` 两份应完全相同，收尾时顺手验一次。
3. **配置项由 host 的 composition root 设置，且"起就绪"**：Android 在 `App.onCreate` 设
   `RuleDataStorage.rootDir = …`，desktop 放在 `desktopHostModule(...)` 的构造体里
   （**起 graph 即就绪**，避免"还有第二处需要记得调用"）。未设置时 getter 显式
   `error(...)`——**不许静默回落成空目录**，那会把配置缺失伪装成"用户没有大变量"。
4. **原语只提供机制，清理这类需要 `appDb` 的逻辑留在 host**：`RuleDataCleaner`
   （`:app/help`）用 `RuleDataFileStore.listBookOwners()` / `listRssOwners()` 拿条目，
   再问 `appDb.bookDao.has(id)`。共享层只提供 `list*` / `delete*Entry` 这类**不带策略**的原语。
5. **兼容面逐字节钉住**：`md5` 必须与迁移前的 `CryptoUtils.toHexString` 一致（小写 hex、UTF-8、
   无盐），目录/标记文件名照抄。这是**既有用户数据能否读回**的问题，测试要用**已知向量**
   （`md5("abc") = 900150983cd24fb0d6963f7d28e17f72`）硬编码期望路径，不能拿被测函数自己算期望值。
   ⚠️ **Windows 文件系统大小写不敏感**，路径用例在 Windows 上守不住"hex 大小写"——必须另有一条
   直接断言 hex 串的用例（见 `topics/cmp-pitfalls.md`）。

## 16. 判定「能不能做成原语」：把「实现依赖 `:app`」查到底（M2-3）

§15 解决了「**消费方**拿不到 DI 时该怎么办」（平台原语）。M2-3 补上另一半：**什么时候这招用不了**。

判据只有一条，但它常被一句没核实的断言挡掉：

> `expect object` 的 `actual` 只能住在**声明它的那个模块**的 source set 里。
> 所以「能不能原语化」≡「这个实现能不能由平台模块自己写出来」。

`SymmetricCrypto` 是正例：当年判成「接口 + 注入」的理由是「实现依赖 `:app` 的
`isHex` / `hexToByteArray` / `toBase64`」。核一遍 `CryptoUtils` 就会发现那几个工具只用到
`kotlin.io.encoding.Base64`（**stdlib，Kotlin 2.2 起稳定**）与 `MessageDigest`（JVM 自带），
本模块自足。**「实现依赖 `:app`」这句话要查到底，不能停在 import 的表象。**

反例是 M2-3 侦察后剩下的 33 条，共两类，都不是「写法不对」而是「可达性」：

| 类型 | 例子 | 为什么原语化不行 |
|---|---|---|
| 需要 host 的**实例** | `AppDatabase`（`caches` / `cookies` 表）、okhttp `CookieManager` | 这是**服务**，不是「一个路径」那种**配置**；actual 够不着 `:app` 的实例 |
| 实现**同名同义**已在 `:app` 且有大面积消费方 | `constant.AppLog`（300+ 调用方、日志界面读 `AppLog.logs`） | 换成 `android.util.Log` 会**静默丢掉站内日志**——比留一个 Provider 更糟 |

两条纪律：

1. **分清「配置」与「服务」**：`RuleDataStorage` 能做成原语，只因为它要的是 host 给**一个路径**
   （`rootDir`，composition root 一设就完事，见 §15 第 3 条）。而 `AppDatabase` / `CookieManager`
   要的是**活的服务对象**，host 给不了也不该给——给了就等于在共享层多一个 service locator
   持有者，与「共享模块零 service locator」直接冲突。遇到这类债，**先解决基础设施（谁拥有库实例），
   再谈清 Provider**；不要为了清一条基线而在共享层新开一个持有者。
2. **顺手消掉模块内重复，但注意依赖方向**：本片把 `RuleDataStorage` 两份 actual 里手写的 hex
   循环换成新的 `internal` `CryptoCodecs.encodeHexLower()`。**为什么不能复用
   `io.legado.app.utils.isHex()`**：`:core:model` 已经依赖 `:core:platform`，反向复用构成依赖环
   （`checkModuleDependencies` 会拦）⇒ 低层模块的编解码工具只能在本模块内部留一份 `internal` 的。
   两份 identical 的 `actual` 本身**不算**重复（见 §15 第 2 条），但 `actual` 内部再抄一遍同一段
   编码就算。
