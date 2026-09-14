plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

// `:core:ui` 是 **Android 专用** 的 Compose UI 模块，承载：
//   1. `io.legado.app.ui.theme`——主题引擎、配色方案、自适应间距/密度；
//   2. `io.legado.app.ui.widget.components` 中 **不依赖 app 单例** 的闭包子集（通用 UI 组件：
//      Scaffold/Text/TextField/Button/Card/SettingItem/CheckBox/Divider/Swipe/Pager/TabRow/
//      ProgressIndicator/TopBar 等）；
//   3. `io.legado.app.ui.animation` 与 `io.legado.app.ui.util` 中同样无 app 单例依赖的
//      纯 Compose 交互/手势工具（`InteractiveHighlight`、`inspectDragGestures`）。
//
// 为什么不放 `:core:designsystem`：**这段理由已于 2026-09-10 复核并修正**。旧说法是
// 「designsystem 是 KMP 模块，commonMain 受 `checkSharedPurity` 约束（零 Compose、零
// `android.*`）」——那是 `composeMain` 中间源集时代的规则，M0-1 之后 `checkSharedPurity`
// 已按模块类型分策：**只有 `pure`/`data` 模块的 commonMain 必须零 Compose；登记为 `cmp`
// 的模块（designsystem 已登记）允许 Compose 直接进 commonMain**。
//
// 实测（2026-09-10）：本模块的 `ui/theme` 与 `ui/widget/components` 大多数文件是
// `android.*`-free 的——搬出 7 个 theme 文件后，`ui/theme` 递归剩 29 个文件，其中只有 4 个
// 带 `android.*`（`ImageSeedColorExtractor` / `ThemeComponents` / `ThemeEngine` /
// `ThemeSeedColorProvider`），常见组件（`AppAlertDialog` / `AppText` / `AppIcon` /
// `LazyList` / `OptionSheet` / `RoundDropdownMenuItem` / `SmallPlainButton`）也是 0 个。
// 所以**「整套主题与组件只能留 Android 侧」不成立**，迁移按文件实测逐个搬。
//
// 四个此前被视为未知的第三方依赖已核对 Maven Central 的 `.module` 元数据，全部有
// desktop/jvm 变体：haze→`haze-jvm`、material-kolor→`material-kolor-jvm`、
// kyant0:backdrop→`backdrop-desktop`、miuix→`miuix-*-desktop`（后者本仓已在 desktop 解析过）。
//
// 已搬出的 theme 文件（包名保持不变 `io.legado.app.ui.theme`，消费方 import 零改动，
// 只要求消费方已声明 `:core:designsystem` 依赖——`app` / `core:ui` / `feature:tagrules` /
// `feature:replacerules` 均已声明）：
//   - M1-3d：`LegadoTheme.kt`（自包含、0 个 `android.*`）——所有组件的 keystone 依赖；
//   - M1-3e：`AppThemeMode` / `ThemeColorSpec` / `ThemeResolver` / `AppContentColor` /
//     `LocalAppUiConfiguration` / `AppDensity`——原子组件（`AppModalBottomSheet` 等）的
//     直接依赖闭包。其中 `AppDensity` 唯一一处 Android-only API（`LocalConfiguration`）
//     已换成同源的 `LocalDensity.current.fontScale`，依据见该文件 KDoc。
//   - M1-3f：`text/AppText.kt` / `icon/AppIcon.kt` / `icon/AppIcons.kt` 整体搬走；
//     `card/GlassCard.kt` 里的 `NormalCard` 与底层卡片表面拆到共享层
//     （`card/AppCardSurface.kt` + 纯函数 `card/CardDecoration.kt`），本文件只剩 `GlassCard`。
//   - M1-3p：`GlassCard.kt` 本身也搬走了——它此前唯一搬不动的原因同样是闭包里的九宫格解码，
//     那段已下沉为 `:core:platform` 的 `NinePatchLoader` 窄契约；同批搬走同族的
//     `checkBox/AppCheckbox.kt`、`checkBox/CheckboxItem.kt`（后者依赖 `GlassCard`），
//     以及 `widget/components/AppContainerBackground.kt`。本模块的 `checkBox/` 只剩
//     无消费方的 `CheckboxGroupContainer.kt`（本模块自用，未搬）。
//   - M1-3h：`modalBottomSheet/AppModalBottomSheet.kt` + `modalBottomSheet/OptionSheet.kt`
//     整体搬走；`LocalUseMiuixWindowPopup` 的**声明**也跟着下沉到 designsystem 的同名包
//     `widget/components/menuItem/`（本模块的 `RoundDropdownMenu.kt` 仍在同包内引用它，
//     import 零改动）。
//   - M1-3i：**原子组件整族搬走 25 个**——`button/`（`AppButton` / `AppIconButton` /
//     `ConfirmDismissButtonsRow` / `ToggleChip`）、`button/series/`（13 个
//     `Medium*` / `Small*` / `Animated*`）、`divider/`（3 个）、`progressIndicator/`（3 个）、
//     `title/SmallTitle.kt`、`SectionTitle.kt`。判据是机械复核过的两条：① 文件内零
//     `android.*` / 零 `R.` / 零 Android-only Compose API（`LocalContext` /
//     `LocalConfiguration` / `androidx.compose.ui.res.*` / `AndroidView` 等）；② 引用闭包
//     全部落在 designsystem（本族唯一的跨模块边是 `ToggleChip → card.NormalCard`，已在
//     `AppCardSurface.kt`）。这批是 Feature Screen 的原子积木，搬完让 Screen 侧只剩平台契约类阻塞。
//
// M1-3f 顺手打掉的两个**不可跨端**事实（不是 `android.*`，所以上面那套"数 android 依赖"
// 的判据看不见，只有编译 desktop 才会暴露）：
//   1. `androidx.compose.material.ExperimentalMaterialApi` 属 **material2** 制品——搬文件时
//      连带删掉 `@OptIn`，不要为它给 designsystem 加 material2 依赖；
//   2. `modalBottomSheet/AppModalBottomSheet.kt` 用的 CMP material3 **expressive 系列**
//      （`MaterialExpressiveTheme` / `MotionScheme` / `ExperimentalMaterial3ExpressiveApi` /
//      `rememberBottomSheetState` / `BottomSheetDefaults.modalWindowInsets`）曾在 CMP material3
//      **1.9.0 里被标成 `internal`**，跨模块编译报 "it is internal in file"（M1-3f 卡的就是它）。
//      ⚠️ 那不是 Android-only —— 五个 API 一直都在 commonMain，纯粹是**版本问题**：M1-3g 已把
//      pin 抬到 1.12.0-alpha03（见 libs.versions.toml 与 convention 的 `MATERIAL3_PIN`），阻塞
//      已解除，两个文件已于 **M1-3h 搬走**（搬前复核过一遍传递闭包，desktop 编译通过）。
//      注意 `OptionSheet` 自己是 0 个 `android.*` 的，判断"能不能搬"必须看**传递闭包**，
//      而传递闭包里还要多一个维度：**依赖库的同一个 API 是否也在非 Android 源集里**。
//
// 仍然只能留 Android 侧的：带 `android.*` 的少数文件（`ImageSeedColorExtractor` 的
// `Bitmap`、`ThemeEngine` 的 `Context`/`Build`、`ThemeComponents`、`ThemeSeedColorProvider`）、
// `AppTheme`/`OpaqueColorScheme`（经 `ThemeEngine` 传递依赖 `Context`）、
// `AppBackground`（coil3 取色 + Android 主题）、以及所有 `res/values*/strings.xml` 文案。
// （`android.webkit.MimeTypeMap` 与 `BitmapFactory`/`NinePatch` 两条已分别由 M1-3n 的
// `MimeTypeResolver`、M1-3p 的 `NinePatchLoader` 契约解除，相应文件已搬进共享层。）
//
// 存在的意义：此前 `ui/theme` 与 `ui/widget/components` 都留在 `:app`，任何 Feature 提升为
// Gradle 模块都会形成 `:app → :feature:x → :app` 的循环依赖。本模块是拆开这个环的通道。
//
// 组件面的切片规则（可机械复核）：文件中不得出现 `io.legado.app` 的 app 单例、`utils.GSON`、
// `ui.main.MainDestination`；不得 import app 层包（`ui.theme` / `ui.widget.components` /
// `ui.animation` / `ui.util` / `domain.model` 除外），且其组件内依赖闭包同样满足该规则。
//
// 资源策略：库侧 `res/values*/strings.xml` 只放**默认值**，app 侧同名资源按 Android 资源
// 合并优先级覆盖；因此迁移文件只把 `io.legado.app.R` 换成 `io.legado.app.core.ui.R`，
// app 侧代码与资源均不改动。仍有 `utils.GSON` 等 app 单例依赖的组件留在 `:app`。
//
// 依赖只列实际用到的；新增文件需同步补依赖，不要顺手塞通用 UI 库。

android {
    compileSdk = 37
    namespace = "io.legado.app.core.ui"

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkDependencies = true
        targetSdk = 37
    }
}

dependencies {
    implementation(project(":core:model"))
    // `importComponents/ImportComponents.kt` 的 JSON 字段编辑走 `:core:platform` 的
    // `ImportJsonEditor` 契约（实现 `GsonImportJsonEditor` 在 `:core:data`），
    // 因此 UI 模块本身不依赖 Gson，也不依赖数据层。
    implementation(project(":core:platform"))
    // 本模块目前**不再拥有** `AppScaffold`（M1-3s）、`list/ListScaffold` + `list/ListUiState`
    // （M1-3s）、`rules/RuleListScaffold`（M1-3s）、`topbar/*`（M1-3r）与
    // `lazylist/*`（M1-3v）——它们都已搬进 designsystem 的同名包，消费方 import 零改动。
    // 保留本依赖是因为 theme 闭包与大量原子组件（`AppText` / `AppIcon` / `card/*` /
    // `button/*` 等）仍由 designsystem 提供，且 `AppBackground` 这类 Android-only 组件
    // 仍在本模块内消费它们。
    implementation(project(":core:designsystem"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material)
    implementation(libs.compose.materialIcons)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.reorderable)
    // M1-3c：`ui.platform.AndroidDocumentPicker` 要用 SAF 注册 Activity Result launcher
    // （`rememberLauncherForActivityResult` + `ActivityResultContracts`），供规则类 Screen 的
    // Route 取用，让 Screen 不再直接依赖 `androidx.activity` / `Context.contentResolver`。
    implementation(libs.activity.compose)

    implementation(libs.core.ktx)
    implementation(libs.material.kolor)
    implementation(libs.haze.core)
    implementation(libs.haze.materials)
    implementation(libs.miuix.core)
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.blur.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.preference.android)
    implementation(libs.backdrop)
    // `FloatingBottomBar` 用 `com.kyant.capsule.ContinuousCapsule` 做连续胶囊裁剪。
    implementation(libs.capsule)
    implementation(libs.coil.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.compose)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
