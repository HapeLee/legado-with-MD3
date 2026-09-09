plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

// `:feature:txttocrules` —— 第四个由 `:app` 提升出来的真实 Feature 模块。
//
// 承载「TXT 目录规则管理」一个屏幕（TxtRuleScreen/TxtTocRuleContract/TxtTocRuleViewModel）：
//   - 数据/仓储来自 `:core:data`（TxtTocRule 实体、DAO、TxtTocRuleRepository，均已在
//     core:data commonMain）；
//   - 列表/导入/上传/排序编排来自 `:core:viewmodel` 的 `BaseRuleViewModel` 一族
//     （`exportToUri`、`RuleTransferPlatform`、`BuiltInRulesImporter` 在其内）；
//   - UI 组件与主题来自 `:core:ui`，纯状态契约来自 `:core:designsystem`；
//   - 剪贴板/轻提示走 `:core:platform` 的 `ClipboardProvider` / `ToasterProvider`。
//
// **不在本模块**：
//   - `TxtTocRuleActivity` 是薄宿主（依赖 `:app` 的 `BaseComposeActivity`），留 `:app`，
//     只 import 本模块的 `TxtRuleRouteScreen`；
//   - `rule/preview` 子域（TxtTocRulePreview*）依赖 app 的 `model.localBook.LocalBook`
//     与 `utils.Utf8BomUtils`（本地书解析），属 platform island，留 `:app`；
//   - `toc` 主域（TocActivity/Screen/ViewModel，书籍目录页）属阅读主链，留 `:app`。
//
// 包名 `io.legado.app.feature.txttocrules`。
//
// 资源策略（沿用 tagrules/replacerules/dict）：本模块 `res/values*/strings.xml` 只放**默认值**
//（从 app res 原样抄录），app 侧同名资源按 Android 资源合并优先级覆盖；因此 Screen/VM 由
// `io.legado.app.R` 换成模块 R 后，用户可见文案不变。
//
// 依赖只列实际用到的；新增文件需同步补依赖。

android {
    compileSdk = 37
    namespace = "io.legado.app.feature.txttocrules"

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
    implementation(project(":core:platform"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:viewmodel"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.materialIcons)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.compose)
    implementation(libs.reorderable)
    // Glass 顶栏组件（core:ui 内是 implementation，不透出）的 public API
    // `GlassTopAppBarDefaults.defaultScrollBehavior()` 返回类型含 dev.chrisbanes.haze.HazeState，
    // 本模块直接用它时需自行声明 haze 才能解析该类型。
    implementation(libs.haze.core)
    implementation(libs.haze.materials)
}
