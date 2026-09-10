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
| 其它 androidx（lifecycle / activity / navigation …） | 逐项验证 | 未验证前一律视为 android-only |

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
- convention `legado.kmp.compose` 在**配置期断言**它与插件常量相等
  （`assertComposeVersionsInSync`）——漂了直接构建失败，而不是运行时行为悄悄变；
- convention 自带的 runtime / foundation 的版本直接取 `ComposeBuildConfig.composeVersion`，
  不经过版本目录。

另外两个坑：

- **`Switch` 不在 `miuix-core`**：`top.yukonga.miuix.kmp.basic.Switch` 在 **`miuix-ui`** 里。
  只加 `miuix-core` 会在 `compileKotlinDesktop` 报 `Unresolved reference 'basic'`。
- 查制品可用性时 **Google Maven 必须一起查**（`dl.google.com/dl/android/maven2`）：androidx 的
  多平台变体不出现在 Maven Central 上，只查 Central 会把存在的东西判成不存在。

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
