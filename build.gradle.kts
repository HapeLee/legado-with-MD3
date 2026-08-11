@DisableCachingByDefault(because = "架构验证任务没有输出文件")
abstract class VerifyConfigArchitectureTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoot: DirectoryProperty

    @get:Input
    abstract val legacyPreferenceCallBaseline: MapProperty<String, Int>

    @get:Input
    abstract val legacyDaoInjectionBaseline: MapProperty<String, Int>

    @get:Input
    abstract val legacyUiDaoAccessBaseline: MapProperty<String, Int>

    @TaskAction
    fun verify() {
        val sourceRootDir = sourceRoot.get().asFile
        val kotlinFiles = sourceRootDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val preferenceBaseline = legacyPreferenceCallBaseline.get()
        val daoInjectionBaseline = legacyDaoInjectionBaseline.get()
        val uiDaoAccessBaseline = legacyUiDaoAccessBaseline.get()
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
        // 上面两条都按 `ReadBookConfig.` 前缀找，成员 import 之后的裸写一个都看不见：
        // `import io.legado.app.help.config.ReadBookConfig.durConfig`（含 as 别名）之后
        // `durConfig = ...` 就是绕过 gateway 的写——不落盘也不 publishState。
        // 不必管通配 import：Kotlin 不允许从 object 按需导入。
        val readBookConfigMemberImport = Regex(
            """^import io\.legado\.app\.help\.config\.ReadBookConfig\.durConfig\b""",
            RegexOption.MULTILINE,
        )
        // 同样的裸写还能从 `with(ReadBookConfig) { durConfig = ... }`／`ReadBookConfig.apply { }`
        // 这类作用域函数里冒出来。与其枚举作用域函数（还会误伤 ChapterProvider 里只读的
        // `with(ReadBookConfig)`），不如直接盯裸赋值本身：带 `.` 前缀的限定写法归上面那条管。
        val readBookConfigBareWrite = Regex("""(?<![.\w])durConfig\s*=(?!=)""")
        // 文件读写层 ReadStyleRepository 同理是 Koin 单例：谁 inject 谁就能直接 save()
        // 覆盖 readConfig.json，磁盘与 ReadStyleConfigStore 的内存状态就此分叉。
        val styleRepositoryOwners = setOf(
            "io/legado/app/data/repository/ReadStyleRepository.kt",
            "io/legado/app/data/repository/ReadStyleConfigStore.kt",
            "io/legado/app/data/repository/ReadBookStyleConfigRepository.kt",
            "io/legado/app/di/appModule.kt",
        )
        // R4.7：Config 的值字段已是 val，字段写入由编译器拦；剩下的唯一写入口是
        // ReadStyleConfigStore 的列表操作。它是 Koin 单例，谁 inject 谁就能绕过 gateway
        // 改配置且不触发 save/publishState——所以限定只有下面这几个文件能提到这个类型。
        val configStoreOwners = setOf(
            "io/legado/app/data/repository/ReadStyleConfigStore.kt",
            "io/legado/app/data/repository/ReadBookStyleConfigRepository.kt",
            "io/legado/app/help/config/ReadBookConfig.kt",
            "io/legado/app/di/appModule.kt",
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
                    readBookConfigMutationCall.containsMatchIn(text) ||
                    readBookConfigMemberImport.containsMatchIn(text) ||
                    readBookConfigBareWrite.containsMatchIn(text))
            ) {
                violations += "$displayPath: ReadBookConfig 写入必须经过 ReadStyleGateway"
            }
            if (relativePath !in configStoreOwners && "ReadStyleConfigStore" in text) {
                violations += "$displayPath: 排版配置的写入口只对 ReadStyleGateway 的实现开放，" +
                    "不要注入 ReadStyleConfigStore"
            }
            if (relativePath !in styleRepositoryOwners && "ReadStyleRepository" in text) {
                violations += "$displayPath: readConfig.json 的读写只对 ReadStyleConfigStore 与 " +
                    "ReadStyleGateway 的实现开放，不要注入 ReadStyleRepository"
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

            if (relativePath.startsWith("io/legado/app/ui/") &&
                !file.name.contains("ViewModel")
            ) {
                val daoDependencies = daoImport.findAll(text).count() +
                    appDbDaoAccess.findAll(text).count()
                val allowedDaoDependencies = uiDaoAccessBaseline[relativePath] ?: 0
                if (daoDependencies > allowedDaoDependencies) {
                    violations += "$displayPath: UI 层新增了 ${daoDependencies - allowedDaoDependencies} 个 DAO 直连"
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
        (uiDaoAccessBaseline.keys - sourcePaths).forEach { relativePath ->
            violations += "app/src/main/java/$relativePath: 文件已移除，请删除 UI DAO 直连基线"
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
    description = "禁止配置架构回退、UI 层(ViewModel 及其它)新增 DAO 直连和新增旧偏好调用"
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
            // R2.1 已清零：ReadBookViewModel 的书籍/目录读写全部经 BookRepository。
            // 保留 0 值条目让棘轮继续盯着这个文件——新增一处直连就报红。
            "io/legado/app/ui/book/read/ReadBookViewModel.kt" to 0,
            // 护栏缺席期间（MAD-3 未合并窗口）main 新增的直连，随合并冻结，清理归 Track A/F2
            "io/legado/app/ui/book/readaloud/cloudtts/CloudTtsViewModel.kt" to 13,
        )
    )
    // 非 ViewModel 的 UI 层文件直连 DAO 的历史债，只冻结不修复；
    // 清理时逐条下调/删除。护栏会自动要求"减少了就下调基线"，防止回退。
    legacyUiDaoAccessBaseline.set(
        mapOf(
            "io/legado/app/ui/association/AddToBookshelfDialog.kt" to 5,
            "io/legado/app/ui/association/ImportReplaceRuleDialog.kt" to 1,
            "io/legado/app/ui/association/ImportRssSourceDialog.kt" to 1,
            "io/legado/app/ui/book/audio/AudioPlayActivity.kt" to 1,
            "io/legado/app/ui/book/bookmark/BookmarkDialog.kt" to 2,
            "io/legado/app/ui/book/changesource/ChangeBookSourceDialog.kt" to 1,
            "io/legado/app/ui/book/group/GroupManageDialog.kt" to 2,
            "io/legado/app/ui/book/group/GroupSelectDialog.kt" to 1,
            "io/legado/app/ui/book/read/ReadBookController.kt" to 3,
            "io/legado/app/ui/book/read/page/provider/TextChapterLayout.kt" to 1,
            // 护栏缺席期间 main 新增（整书页码估算），随合并冻结
            "io/legado/app/ui/book/read/pageestimate/ExactChapterPageCountStore.kt" to 3,
            "io/legado/app/ui/book/search/SearchScope.kt" to 4,
            "io/legado/app/ui/config/bookshelfConfig/BookshelfManageScreenConfig.kt" to 1,
            "io/legado/app/ui/main/MainNavGraph.kt" to 2,
            "io/legado/app/ui/rss/article/RssArticlesCompose.kt" to 1,
            "io/legado/app/ui/rss/read/RssJsExtensions.kt" to 8,
            "io/legado/app/ui/widget/dialog/BottomWebViewDialog.kt" to 1,
            "io/legado/app/ui/widget/keyboard/KeyboardAssistsConfig.kt" to 7,
            "io/legado/app/ui/widget/keyboard/KeyboardToolPop.kt" to 1,
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
