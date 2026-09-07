plugins {
    id("legado.kmp.library")
    // Compose Multiplatform：让本模块在 desktop 也能编译 Compose UI 代码。
    // 这是全仓首个接入 CMP 的模块，故在本模块显式 apply，而非抬进共享 convention
    // （共享 convention 只在有第二个 CMP 模块且形态稳定后再收口，见 AGENTS.md 代码生成纪律）。
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // commonMain 零 Compose：只放纯值 token（数值 / ARGB 色值 / 字号），
                // 不依赖 androidx.compose.*（G2 纯度守卫硬约束）。
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        // 专用 UI 源集：android + desktop 共享的 Compose 代码（Color/Dp/TextStyle 映射）。
        // 命名对照样本仓 sharedUiMain，但本仓库按「窄 UI 源集」而非整仓大 shared 命名。
        // 该源集不叫 commonMain，因此 checkSharedPurity 不扫描它——Compose 依赖被关在这里。
        val composeMain = create("composeMain") {
            dependsOn(commonMain.get())
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
            }
        }
        androidMain.get().dependsOn(composeMain)
        getByName("desktopMain").dependsOn(composeMain)
    }
}
