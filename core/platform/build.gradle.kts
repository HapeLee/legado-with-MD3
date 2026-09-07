plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
        // jsoup 1.16.2 是纯 JVM 库（AGENTS.md 锁定版本，不得升级），只能出现在平台源集。
        // commonMain 只依赖 HtmlParser/HtmlDocument/HtmlElement 三个窄接口，
        // commonTest 的契约测试则由各 target 子类注入这里的 JsoupHtmlParser。
        androidMain.dependencies {
            implementation(libs.jsoup)
            implementation(libs.gson)
            // P4-a: android 侧委托 com.script 封装层（含 RhinoContext 协程取消 / ClassShutter /
            // WrapFactory），与 app 当前行为一致。该模块是 android library，只能挂 androidMain。
            implementation(project(":modules:rhino"))
        }
        desktopMain.dependencies {
            implementation(libs.jsoup)
            implementation(libs.gson)
            // P4-a: com.script 封装层是 android library，desktop 无法依赖，
            // 故 desktop actual 直接委托裸 Rhino（纯 JVM 库，两 target 同一份实现能力）。
            // 已知缺口：协程取消 / 安全名单等 com.script 增强在 desktop 侧不生效，
            // desktop 仅作为「管线可编译 + 契约行为一致」的验证 target，非生产目标。
            implementation(libs.mozilla.rhino)
        }
    }
}
