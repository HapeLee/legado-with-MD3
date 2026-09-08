plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

// `:feature:tagrules` 是仓库里**第一个**由 `:app` 提升出来的真实 Feature 模块（Stage B 首例）。
//
// 它承载「标签分组规则」与「高亮标签规则」两个屏幕的 Contract / EditSheet / ViewModel：
//   - 数据与仓储来自 `:core:data`；
//   - 列表/导入/上传编排来自 `:core:viewmodel` 的 `BaseRuleViewModel`；
//   - UI 组件与主题来自 `:core:ui`，纯状态契约来自 `:core:designsystem`；
//   - 剪贴板/轻提示走 `:core:platform` 的 provider。
//
// 包名保持 `io.legado.app.feature.tagrules.*`，`:app` 侧 import 零改动。
//
// 资源策略（沿用 `:core:ui` 的做法）：本模块 `res/values*/strings.xml` 只放**默认值**，
// app 侧同名资源按 Android 资源合并优先级覆盖；因此 `io.legado.app.R` 换成
// `io.legado.app.feature.tagrules.R` 后，用户可见文案不变。
//
// 尚未迁入：`highlight/HighlightTagRuleScreen.kt`——它依赖 app 侧的
// `ui.widget.components.importComponents.{BatchImportDialog, SourceInputDialog}`
// （Gson JSON 树），待那条依赖解除后随下一个切片移入。
//
// 依赖只列实际用到的；新增文件需同步补依赖。

android {
    compileSdk = 37
    namespace = "io.legado.app.feature.tagrules"

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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
