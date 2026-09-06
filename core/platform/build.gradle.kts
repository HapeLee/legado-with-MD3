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
        }
        desktopMain.dependencies {
            implementation(libs.jsoup)
        }
    }
}
