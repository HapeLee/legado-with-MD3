buildscript {
    extra.apply {
        set("compile_sdk_version", 36)
        set("build_tool_version", "34.0.0")
    }
}

plugins {
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.download) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

val legacyPreferenceCallBaseline = mapOf(
    "app/src/main/java/io/legado/app/App.kt" to 3,
    "app/src/main/java/io/legado/app/base/BaseActivity.kt" to 2,
    "app/src/main/java/io/legado/app/base/BaseService.kt" to 1,
    "app/src/main/java/io/legado/app/data/repository/CoverAlbumRepository.kt" to 4,
    "app/src/main/java/io/legado/app/data/repository/HighlightRuleRepository.kt" to 9,
    "app/src/main/java/io/legado/app/data/repository/HomeDashboardRepository.kt" to 3,
    "app/src/main/java/io/legado/app/data/repository/ReadRecordRepository.kt" to 1,
    "app/src/main/java/io/legado/app/data/repository/SettingsRepository.kt" to 7,
    "app/src/main/java/io/legado/app/help/config/LocalConfig.kt" to 3,
    "app/src/main/java/io/legado/app/help/config/ThemeConfigStore.kt" to 8,
    "app/src/main/java/io/legado/app/help/storage/Restore.kt" to 2,
    "app/src/main/java/io/legado/app/receiver/MediaButtonReceiver.kt" to 2,
    "app/src/main/java/io/legado/app/service/WebService.kt" to 2,
    "app/src/main/java/io/legado/app/ui/association/ImportReplaceRuleDialog.kt" to 1,
    "app/src/main/java/io/legado/app/ui/book/explore/ExploreShowViewModel.kt" to 2,
    "app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt" to 2,
    "app/src/main/java/io/legado/app/ui/book/readRecord/ReadRecordViewModel.kt" to 1,
    "app/src/main/java/io/legado/app/ui/book/search/SearchViewModel.kt" to 3,
    "app/src/main/java/io/legado/app/ui/config/CheckSourceConfig.kt" to 1,
    "app/src/main/java/io/legado/app/ui/config/otherConfig/OtherConfigViewModel.kt" to 1,
    "app/src/main/java/io/legado/app/ui/replace/ReplaceRuleViewModel.kt" to 2,
    "app/src/main/java/io/legado/app/utils/ContextExtensions.kt" to 12,
    "app/src/main/java/io/legado/app/web/socket/BookSearchWebSocket.kt" to 2,
)

val verifyConfigArchitecture by tasks.registering {
    group = "verification"
    description = "禁止配置 Snapshot 桥、跨层全局 Config 直读和新增旧偏好调用"

    doLast {
        val sourceRoot = rootProject.file("app/src/main/java")
        val kotlinFiles = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .toList()
        val violations = mutableListOf<String>()
        val forbiddenConfigImport = Regex(
            """^import io\.legado\.app\.(?:help\.config\.AppConfig|ui\.config\..*Config)$""",
            RegexOption.MULTILINE,
        )
        val preferenceCall = Regex("""\b(?:getPref|putPref)[A-Za-z0-9_]*\s*\(""")

        kotlinFiles.forEach { file ->
            val text = file.readText()
            val relativePath = file.relativeTo(rootProject.projectDir).invariantSeparatorsPath

            if ("prefDelegate" in text || "prefStateDelegate" in text ||
                "Snapshot.withMutableSnapshot" in text
            ) {
                violations += "$relativePath: 禁止 Snapshot 配置桥"
            }
            if (("/data/" in relativePath || "/domain/" in relativePath) &&
                forbiddenConfigImport.containsMatchIn(text)
            ) {
                violations += "$relativePath: data/domain 禁止导入全局 Config"
            }
            if (("@Composable" in text || "import androidx.compose" in text) &&
                forbiddenConfigImport.containsMatchIn(text)
            ) {
                violations += "$relativePath: Composable 禁止读取兼容 Config"
            }
            if (file.name.endsWith("Config.kt") &&
                ("mutableStateOf(" in text || "Snapshot.withMutableSnapshot" in text ||
                    "import androidx.compose.runtime.State" in text ||
                    "import androidx.compose.runtime.MutableState" in text)
            ) {
                violations += "$relativePath: 配置门面禁止持有 Compose State"
            }

            val preferenceCalls = preferenceCall.findAll(text).count()
            val allowedCalls = legacyPreferenceCallBaseline[relativePath] ?: 0
            if (preferenceCalls > allowedCalls) {
                violations += "$relativePath: 新增了 ${preferenceCalls - allowedCalls} 个旧偏好调用"
            }
        }

        check(violations.isEmpty()) {
            violations.joinToString(prefix = "配置架构护栏失败:\n", separator = "\n")
        }
    }
}

subprojects {
    tasks.configureEach {
        if (name.startsWith("assemble") || name.startsWith("compile")) {
            dependsOn(rootProject.tasks.named(verifyConfigArchitecture.name))
        }
    }
}
