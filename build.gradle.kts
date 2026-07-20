import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "架构验证任务没有输出文件")
abstract class VerifyConfigArchitectureTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoot: DirectoryProperty

    @get:Input
    abstract val legacyPreferenceCallBaseline: MapProperty<String, Int>

    @get:Input
    abstract val legacyDaoInjectionBaseline: MapProperty<String, Int>

    @TaskAction
    fun verify() {
        val sourceRootDir = sourceRoot.get().asFile
        val kotlinFiles = sourceRootDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val preferenceBaseline = legacyPreferenceCallBaseline.get()
        val daoInjectionBaseline = legacyDaoInjectionBaseline.get()
        val violations = mutableListOf<String>()
        val forbiddenConfigImport = Regex(
            """^import io\.legado\.app\.(?:help\.config\.AppConfig|ui\.config\..*Config)$""",
            RegexOption.MULTILINE,
        )
        val preferenceCall = Regex("""\b(?:getPref|putPref)[A-Za-z0-9_]*\s*\(""")
        val daoImport = Regex(
            """^import io\.legado\.app\.data\.dao\.[A-Za-z0-9_*]+$""",
            RegexOption.MULTILINE,
        )
        val appDbDaoAccess = Regex(
            """(?:\bappDb|io\.legado\.app\.data\.appDb)\.[A-Za-z0-9_]*Dao\b"""
        )
        val readBookConfigWrite = Regex(
            """\bReadBookConfig\.[a-z_][A-Za-z0-9_]*(?:\.[a-z_][A-Za-z0-9_]*)?\s*="""
        )
        val readBookConfigMutationCall = Regex(
            """\bReadBookConfig\.durConfig\.set[A-Za-z0-9_]*\s*\("""
        )
        val settingsUpdateDeclaration = Regex(
            """\b(?:class|interface|object|typealias)\s+[A-Za-z0-9_]*SettingsUpdate\b"""
        )
        val updateAllDeclaration = Regex("""\bfun\s+(?:<[^>\n]+>\s*)?updateAll\s*\(""")
        val injectedConfigFiles = setOf(
            "io/legado/app/help/config/AppConfig.kt",
            "io/legado/app/help/config/ReadBookConfig.kt",
            "io/legado/app/help/config/ThemePackageManager.kt",
        )

        kotlinFiles.forEach { file ->
            val text = file.readText()
            val relativePath = file.relativeTo(sourceRootDir).invariantSeparatorsPath
            val displayPath = "app/src/main/java/$relativePath"

            if ("prefDelegate" in text || "prefStateDelegate" in text ||
                "Snapshot.withMutableSnapshot" in text
            ) {
                violations += "$displayPath: 禁止 Snapshot 配置桥"
            }
            if ((relativePath.startsWith("io/legado/app/data/") ||
                    relativePath.startsWith("io/legado/app/domain/")) &&
                forbiddenConfigImport.containsMatchIn(text)
            ) {
                violations += "$displayPath: data/domain 禁止导入全局 Config"
            }
            if (("@Composable" in text || "import androidx.compose" in text) &&
                forbiddenConfigImport.containsMatchIn(text)
            ) {
                violations += "$displayPath: Composable 禁止读取兼容 Config"
            }
            if (file.name.endsWith("Config.kt") &&
                ("mutableStateOf(" in text || "Snapshot.withMutableSnapshot" in text ||
                    "import androidx.compose.runtime.State" in text ||
                    "import androidx.compose.runtime.MutableState" in text)
            ) {
                violations += "$displayPath: 配置门面禁止持有 Compose State"
            }
            if (relativePath !=
                "io/legado/app/data/repository/ReadBookStyleConfigRepository.kt" &&
                (readBookConfigWrite.containsMatchIn(text) ||
                    readBookConfigMutationCall.containsMatchIn(text))
            ) {
                violations += "$displayPath: ReadBookConfig 写入必须经过 ReadStyleGateway"
            }
            if (relativePath in injectedConfigFiles && "GlobalContext" in text) {
                violations += "$displayPath: 配置所有者必须显式注入依赖，禁止 GlobalContext"
            }
            if (settingsUpdateDeclaration.containsMatchIn(text)) {
                violations += "$displayPath: 设置网关禁止重新引入 *SettingsUpdate 分发类型"
            }
            if (relativePath.startsWith("io/legado/app/domain/gateway/") &&
                file.name.endsWith("SettingsGateway.kt") &&
                updateAllDeclaration.containsMatchIn(text)
            ) {
                violations += "$displayPath: 设置网关批量修改必须使用单次 update { copy(...) }"
            }

            val preferenceCalls = preferenceCall.findAll(text).count()
            val allowedCalls = preferenceBaseline[relativePath] ?: 0
            if (preferenceCalls > allowedCalls) {
                violations += "$displayPath: 新增了 ${preferenceCalls - allowedCalls} 个旧偏好调用"
            }

            if (relativePath.startsWith("io/legado/app/ui/") &&
                file.name.contains("ViewModel")
            ) {
                val daoDependencies = daoImport.findAll(text).count() +
                    appDbDaoAccess.findAll(text).count()
                val allowedDaoDependencies = daoInjectionBaseline[relativePath] ?: 0
                if (daoDependencies > allowedDaoDependencies) {
                    violations += "$displayPath: ViewModel 新增了 ${daoDependencies - allowedDaoDependencies} 个 DAO 直连"
                } else if (daoDependencies < allowedDaoDependencies) {
                    violations += "$displayPath: 已减少 DAO 直连，请将基线从 $allowedDaoDependencies 下调到 $daoDependencies"
                }
            }
        }

        val sourcePaths = kotlinFiles.mapTo(hashSetOf()) {
            it.relativeTo(sourceRootDir).invariantSeparatorsPath
        }
        (daoInjectionBaseline.keys - sourcePaths).forEach { relativePath ->
            violations += "app/src/main/java/$relativePath: 文件已移除，请删除 DAO 直连基线"
        }

        check(violations.isEmpty()) {
            violations.joinToString(prefix = "配置架构护栏失败:\n", separator = "\n")
        }
    }
}

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

val verifyConfigArchitecture = tasks.register<VerifyConfigArchitectureTask>(
    "verifyConfigArchitecture"
) {
    group = "verification"
    description = "禁止配置架构回退、ViewModel 新增 DAO 直连和新增旧偏好调用"
    sourceRoot.set(layout.projectDirectory.dir("app/src/main/java"))
    legacyPreferenceCallBaseline.set(
        mapOf(
            "io/legado/app/App.kt" to 3,
            "io/legado/app/base/BaseActivity.kt" to 2,
            "io/legado/app/base/BaseService.kt" to 1,
            "io/legado/app/data/repository/CoverAlbumRepository.kt" to 4,
            "io/legado/app/data/repository/HighlightRuleRepository.kt" to 9,
            "io/legado/app/data/repository/HomeDashboardRepository.kt" to 3,
            "io/legado/app/data/repository/ReadRecordRepository.kt" to 1,
            "io/legado/app/data/repository/SettingsRepository.kt" to 7,
            "io/legado/app/help/config/LocalConfig.kt" to 3,
            "io/legado/app/help/config/ThemeConfigStore.kt" to 8,
            "io/legado/app/help/storage/Restore.kt" to 2,
            "io/legado/app/receiver/MediaButtonReceiver.kt" to 2,
            "io/legado/app/service/WebService.kt" to 2,
            "io/legado/app/ui/association/ImportReplaceRuleDialog.kt" to 1,
            "io/legado/app/ui/book/explore/ExploreShowViewModel.kt" to 2,
            "io/legado/app/ui/book/read/ReadBookViewModel.kt" to 2,
            "io/legado/app/ui/book/readRecord/ReadRecordViewModel.kt" to 1,
            "io/legado/app/ui/book/search/SearchViewModel.kt" to 3,
            "io/legado/app/ui/config/CheckSourceConfig.kt" to 1,
            "io/legado/app/ui/config/otherConfig/OtherConfigViewModel.kt" to 1,
            "io/legado/app/ui/replace/ReplaceRuleViewModel.kt" to 2,
            "io/legado/app/utils/ContextExtensions.kt" to 12,
            "io/legado/app/web/socket/BookSearchWebSocket.kt" to 2,
        )
    )
    legacyDaoInjectionBaseline.set(
        mapOf(
            "io/legado/app/ui/book/audio/AudioPlayViewModel.kt" to 11,
            "io/legado/app/ui/book/changecover/ChangeCoverViewModel.kt" to 3,
            "io/legado/app/ui/book/changesource/ChangeBookSourceViewModel.kt" to 20,
            "io/legado/app/ui/book/changesource/ChangeChapterSourceViewModel.kt" to 5,
            "io/legado/app/ui/book/import/local/ImportBookViewModel.kt" to 4,
            "io/legado/app/ui/book/import/remote/RemoteBookViewModel.kt" to 2,
            "io/legado/app/ui/book/info/BookInfoViewModel.kt" to 34,
            "io/legado/app/ui/book/info/edit/BookInfoEditViewModel.kt" to 2,
            "io/legado/app/ui/book/manga/ReadMangaViewModel.kt" to 17,
            "io/legado/app/ui/book/read/ReadBookViewModel.kt" to 52,
            "io/legado/app/ui/book/source/edit/BookSourceEditViewModel.kt" to 3,
            "io/legado/app/ui/book/source/manage/BookSourceViewModel.kt" to 29,
            "io/legado/app/ui/book/toc/TocViewModel.kt" to 13,
            "io/legado/app/ui/book/toc/rule/preview/TxtTocRulePreviewViewModel.kt" to 2,
            "io/legado/app/ui/browser/WebViewModel.kt" to 1,
            "io/legado/app/ui/login/SourceLoginViewModel.kt" to 6,
            "io/legado/app/ui/tagGroupRule/TagGroupRuleViewModel.kt" to 4,
        )
    )
}

subprojects {
    tasks.configureEach {
        if (name.startsWith("assemble") || name.startsWith("compile")) {
            dependsOn(verifyConfigArchitecture)
        }
    }
}
