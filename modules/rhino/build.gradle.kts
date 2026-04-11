plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = 37
    namespace = "com.script"
    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    defaultConfig {
        minSdk = 26

        consumerProguardFiles += file("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        checkDependencies = true
        targetSdk = 37
    }
    testOptions {
        targetSdk = 37
    }
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:deprecation")
    }
}

dependencies {
    api(libs.mozilla.rhino)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.androidx.collection)
}
