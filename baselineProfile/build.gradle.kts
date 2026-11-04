plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.legado.baselineprofile"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "DEBUGGABLE"
}

    targetProjectPath = ":app"

    // 必须与 :app 的 flavor 维度对齐，否则 baseline profile 任务找不到目标变体。
    flavorDimensions += listOf("mode", "engine")
    productFlavors {
        create("app") { dimension = "mode" }
        create("core") { dimension = "engine" }
        create("comic") { dimension = "engine" }
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    // managedDevices += "pixel6Api34"
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
}

androidComponents {
    beforeVariants { v ->
        // baseline profile 只针对 core 采集；comic 的本地引擎体积与启动路径另算。
        val comic = v.productFlavors.any { it.second == "comic" }
        if (v.buildType == "noR8" || v.buildType == "debug" || comic) {
            v.enable = false
        }
    }
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
    }
}