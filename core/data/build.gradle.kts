plugins {
    id("legado.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:platform"))
            implementation(project(":core:model"))
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // Gson 门面（`io.legado.app.utils.GSON` / `INITIAL_GSON`）与 `*Android.kt` 反序列化兼容层
        // 只存在于 Android/JVM 侧：`com.google.gson` 是 JVM 三方库，commonMain 的 `JsonCodec`
        // 才是跨平台契约。用 `api` 是为了让消费方（`:app`、`:core:viewmodel`、未来的
        // `:feature:tagrules`）能直接使用 `GSON.toJson/fromJsonObject/fromJsonArray`，
        // 无需各自再声明 Gson——与参考项目 `shared` 对 okhttp 的处理一致。
        androidMain.dependencies {
            api(libs.gson)
        }
        // 反射是不可变式守卫（如 ReadSettings 全字段落盘覆盖面）的唯一手段，而
        // kotlin-reflect 是 JVM-only：这类测试只能放在 JVM 目标下，不能进 commonTest。
        desktopTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlin("reflect"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    "kspAndroid"(libs.room.compiler)
    "kspDesktop"(libs.room.compiler)
}

room {
    schemaDirectory(file("schemas").absolutePath)
}
