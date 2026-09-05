plugins {
    id("legado.kmp.library")
}

// D3 PoC：验证 Ktor client（3.5.1）在 commonMain + desktop(JVM) target 可用，
// 为 P1 的 HttpClient 契约选型产出 Go/No-Go 证据。smoke 不走 convention 的 room/ksp。

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.client.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        desktopMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}
