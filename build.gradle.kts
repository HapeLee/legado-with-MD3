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
        // `appDb.xxxDao` 不是唯一形态：`XxxRepository(appDb)` 是 UI 层自己 new 出数据层，
        // 一样绕开注入。2026-09-08 收 tag-rules 时全仓只剩 1 处（GroupViewModel），现已清零。
        val appDbRepositoryConstruction = Regex(
            """[A-Za-z0-9_]*Repository\s*\(\s*(?:\bappDb|io\.legado\.app\.data\.appDb)\s*\)"""
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

            // feature-first 迁移后 ViewModel 会搬出 `ui/` 进入 `feature/`，
            // UI 层判定必须同时覆盖两个根，否则一搬家就脱离棘轮。
            val isUiLayer = relativePath.startsWith("io/legado/app/ui/") ||
                relativePath.startsWith("io/legado/app/feature/")

            if (isUiLayer && file.name.contains("ViewModel")) {
                val daoDependencies = daoImport.findAll(text).count() +
                    appDbDaoAccess.findAll(text).count() +
                    appDbRepositoryConstruction.findAll(text).count()
                val allowedDaoDependencies = daoInjectionBaseline[relativePath] ?: 0
                if (daoDependencies > allowedDaoDependencies) {
                    violations += "$displayPath: ViewModel 新增了 ${daoDependencies - allowedDaoDependencies} 个 DAO 直连"
                } else if (daoDependencies < allowedDaoDependencies) {
                    violations += "$displayPath: 已减少 DAO 直连，请将基线从 $allowedDaoDependencies 下调到 $daoDependencies"
                }
            }

            if (isUiLayer && !file.name.contains("ViewModel")) {
                val daoDependencies = daoImport.findAll(text).count() +
                    appDbDaoAccess.findAll(text).count() +
                    appDbRepositoryConstruction.findAll(text).count()
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

// G2 守卫：commonMain 共享层禁止出现 platform / JVM-only import。
// M0-1：把「全 commonMain 一刀切禁 platform / JVM-only import」改成按模块类型（pure/data/cmp）分策。
// 模块类型由注册处传入的 `kmpModuleTypes`（模块相对目录 → pure|data|cmp）决定；未登记的 KMP 模块
// 一律按最严的 pure 处理，迫使其显式登记。政策模型见 docs/dev/kmp-cmp-modernization.md §3.2。
@DisableCachingByDefault(because = "共享层纯度验证任务没有输出文件")
abstract class CheckSharedPurityTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sharedSourceFiles: ConfigurableFileCollection

    // 仅用于违规消息的相对路径展示，不参与 up-to-date 判定（@Internal）。
    // 用 @Internal 注入避免在 @TaskAction 里访问 Task.project（configuration cache 禁止）。
    @get:Internal
    abstract val rootDir: DirectoryProperty

    // 模块相对目录（如 "core/data"）→ 模块类型（pure|data|cmp）。作为任务输入参与 up-to-date 判定。
    @get:Input
    abstract val kmpModuleTypes: MapProperty<String, String>

    @TaskAction
    fun check() {
        val root = rootDir.get().asFile
        val typeMap = kmpModuleTypes.get()

        // 所有类型一律禁止的 import：绑定平台 / JVM-only / 书源运行时实现库。
        // 这些库没有 KMP 产物，进 commonMain 会在加 native target 时最晚阶段炸掉；
        // 必须走「commonMain 窄接口 + 各 target actual 委托」的双轨（见 modernization §5.3）。
        //   - org.jsoup.*      jsoup 1.16.2（AGENTS.md 锁定版本）→ HtmlParser 契约
        //   - org.seimicrawler.*  JsoupXpath 2.5.5 → 同属 HTML 解析，待契约
        //   - com.jayway.jsonpath.* JsonPath → 待契约
        //   - org.mozilla.javascript.* Rhino 1.8.1 → RuleEngine 契约（M4）
        //   - okhttp3.*        okhttp 5.4.0 → HttpClient 契约走 Ktor client-core
        //   - com.google.gson.*  Gson 2.x → 共享 JSON 走 kotlinx-serialization；Gson 只在平台源集
        val alwaysForbidden = listOf(
            Regex("""^import android\."""),                       // 平台 SDK（android.*）commonMain 一律禁
            Regex("""^import java\.io\.(File|InputStream|OutputStream)\b"""), // JVM 文件句柄，共享层走 ByteArray/source-sink
            Regex("""^import kotlin\.jvm\."""),                  // 绑定 JVM target 的注解
            Regex("""^import org\.jsoup\."""),
            Regex("""^import org\.seimicrawler\."""),
            Regex("""^import com\.jayway\."""),
            Regex("""^import org\.mozilla\.javascript\."""),
            Regex("""^import okhttp3\."""),
            Regex("""^import com\.google\.gson\."""),
        )

        // androidx 允许前缀按类型：CMP 模块允许 Compose/Lifecycle/ViewModel/Nav3 的公共 API，
        // data 模块允许 Room 数据实现；pure 全禁（不出现 androidx）。
        // 新增前缀前必须验证该库有官方 KMP 支持（有 commonMain 元数据）且在 commonMain 编译通过。
        val androidxAllow = mapOf(
            "pure" to emptyList<String>(),
            "data" to listOf("androidx.room.", "androidx.sqlite."),
            "cmp" to listOf(
                "androidx.compose.",   // CMP 提供的 runtime/foundation/ui/material/animation 等
                "androidx.lifecycle.", // KMP lifecycle (lifecycle-viewmodel 等)
                "androidx.navigation3.", // Nav3 多平台
            ),
        )

        val violations = mutableListOf<String>()
        fun moduleTypeOf(relPath: String): String {
            val idx = relPath.indexOf("/src/commonMain")
            val moduleDir = if (idx > 0) relPath.substring(0, idx) else relPath
            return typeMap[moduleDir] ?: "pure"
        }

        sharedSourceFiles.files.forEach { file ->
            if (!file.isFile) return@forEach
            val rel = file.relativeTo(root).invariantSeparatorsPath
            val type = moduleTypeOf(rel)
            val allowedAndroidx = androidxAllow[type] ?: emptyList()

            file.readText().lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("import ")) return@forEach
                val imported = trimmed.removePrefix("import ").trim()
                // 去掉 as 别名 / 通配星号尾巴，只留首个标识符段用于前缀判断
                val fq = imported.substringBefore(' ').substringBefore('*')
                alwaysForbidden.forEach { re ->
                    if (re.containsMatchIn(trimmed)) {
                        violations += "$rel [$type] 禁止的 import: $trimmed"
                    }
                }
                if (fq.startsWith("androidx.")) {
                    val ok = allowedAndroidx.any { fq.startsWith(it) }
                    if (!ok) {
                        violations += "$rel [$type] androidx 越界: $trimmed（$type 允许的 androidx 前缀: " +
                            "${allowedAndroidx.joinToString(" | ") { it }})"
                    }
                } else if (fq.startsWith("org.jetbrains.compose.")) {
                    // CMP resources（org.jetbrains.compose.resources.Res 等）只有 CMP 模块能进 commonMain
                    if (type != "cmp") {
                        violations += "$rel [$type] CMP resources 越界: $trimmed（仅 cmp 模块可用）"
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            violations.joinToString(
                prefix = "共享层纯度护栏失败（commonMain 纯度按模块类型校验）:\n",
                separator = "\n",
            ) + "\n参考: docs/dev/kmp-cmp-modernization.md §3.2；docs/dev/kmp-cmp-migration-plan.md M0-1"
        }
    }
}

// G1 守卫：禁止非法的 Gradle 模块依赖方向。
// 规则见 .agents/skills/legado-kmp-migration/references/slice-checklist.md "Module graph"。
// 当前全仓 0 违规，day 1 起 blocking。
@DisableCachingByDefault(because = "模块依赖方向验证任务没有输出文件")
abstract class CheckModuleDependenciesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleBuildFiles: ConfigurableFileCollection

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @TaskAction
    fun check() {
        val root = rootDir.get().asFile
        val projectDep = Regex("""project\("(:[^"]+)"\)""")
        val violations = mutableListOf<String>()

        fun isCore(path: String) = path == ":core" || path.startsWith(":core:")
        fun isFeature(path: String) = path == ":feature" || path.startsWith(":feature:")

        fun reason(declarer: String, dep: String): String? {
            if (declarer == dep) return null
            // core 不得依赖 feature 或宿主：core 是最底层共享层。
            if (isCore(declarer)) {
                if (isFeature(dep)) return "core 不得依赖 feature（方向必须 feature → core）"
                if (dep == ":app") return "core 不得依赖宿主 :app"
            }
            // feature API 不得依赖任何 feature（api 只被 impl/宿主消费，不横向依赖）。
            if (declarer.startsWith(":feature:") && declarer.endsWith(":api") && isFeature(dep)) {
                return "feature API 不得依赖其他 feature"
            }
            // feature impl 不得依赖另一个 feature impl（只能依赖 feature API）。
            if (declarer.startsWith(":feature:") && declarer.endsWith(":impl") &&
                dep.startsWith(":feature:") && dep.endsWith(":impl")
            ) {
                return "feature impl 不得依赖另一个 feature impl（只能依赖 feature API）"
            }
            return null
        }

        moduleBuildFiles.files.forEach { file ->
            if (!file.isFile || file.name != "build.gradle.kts") return@forEach
            val text = file.readText()
            // 声明模块路径由文件相对根的目录推导：app → :app，feature/reader/core → :feature:reader:core。
            val declarer = ":" + file.parentFile.relativeTo(root).invariantSeparatorsPath.replace('/', ':')
            projectDep.findAll(text).forEach { match ->
                val dep = match.groupValues[1]
                reason(declarer, dep)?.let { msg ->
                    violations += "$declarer -> $dep（$msg）@ ${file.relativeTo(root).invariantSeparatorsPath}"
                }
            }
        }
        check(violations.isEmpty()) {
            violations.joinToString(
                prefix = "模块依赖方向护栏失败（G1 模块边界）:\n",
                separator = "\n",
            ) + "\n参考: .agents/skills/legado-kmp-migration/references/slice-checklist.md 'Module graph'；" +
                "AGENTS.md 目标依赖方向图"
        }
    }
}

// M0-2 守卫：legacy 架构债棘轮。
// 把 2026-09-10 的真实计数冻结进 gradle/architecture/legacy-baseline.txt（目录级聚合），
// 之后「目录内新增 = 失败」「减少 = 要求下调基线」「未登记区域出现 = 失败」，
// 从而让新代码 day-one 就被拦住，而不是等 M2/M5 再清算。
// 全局门面一律以 import 锚定，避免把 `private val appDb: AppDatabase` 这类构造参数误判为全局单例。
// 规则、热点和分级结论见 docs/dev/legacy-architecture-report.md。
@DisableCachingByDefault(because = "legacy 架构基线验证任务没有输出文件")
abstract class CheckLegacyArchitectureTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: RegularFileProperty

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @TaskAction
    fun check() {
        val root = rootDir.get().asFile
        // <模块>/src/<源集>/kotlin|java/<包内相对路径>
        val sourceSetPattern = Regex("""^(.+)/src/([^/]+)/(?:kotlin|java)/(.+)$""")
        val descriptions = mapOf(
            "appCtx" to "全局 Context 直连（splitties appCtx）",
            "appDb" to "全局数据库门面（io.legado.app.data.appDb）",
            "gson" to "全局 GSON 门面（io.legado.app.utils.GSON）",
            "legacyHelp" to "help 静态门面（import io.legado.app.help.**）",
            "legacyBase" to "base 静态门面（import io.legado.app.base.**）",
            "legacyNaming" to "*Help/*Utils 顶层门面",
            "coreProvider" to "core Provider 静态委托",
        )
        val importRules = mapOf(
            "appCtx" to Regex("""^import splitties\.init\.appCtx$""", RegexOption.MULTILINE),
            "appDb" to Regex("""^import io\.legado\.app\.data\.appDb$""", RegexOption.MULTILINE),
            "gson" to Regex("""^import io\.legado\.app\.utils\.GSON$""", RegexOption.MULTILINE),
            "legacyHelp" to Regex("""^import io\.legado\.app\.help\.[A-Za-z0-9_.]+$""", RegexOption.MULTILINE),
            "legacyBase" to Regex("""^import io\.legado\.app\.base\.[A-Za-z0-9_.]+$""", RegexOption.MULTILINE),
            // 只认以 Help/Utils 结尾的类型名；`io.legado.app.utils.GSON` 这类包内成员不算命名门面，
            // 它由 gson 规则单独盯。
            "legacyNaming" to Regex(
                """^import io\.legado\.app\.[A-Za-z0-9_.]*\.[A-Za-z0-9_]*(?:Help|Utils)$""",
                RegexOption.MULTILINE,
            ),
        )
        val providerUse = Regex(
            """\b(Clipboard|CookieStore|ImportJsonEditor|KeyValueStore|Logger|SourceRuntime""" +
                """|SymmetricCrypto|Toaster|BigDataStore)Provider\b"""
        )
        val providerDeclaration = Regex("""\bobject\s+([A-Za-z0-9_]*Provider)\b""")
        // legacy 大本营：这些目录本身就是待下沉/待删除的债主体，其内部浮动只警告不失败，
        // 否则「在债堆里做清理」会不断撞墙，反而掩盖真正的新增方向债。
        val reportOnlyAreas = listOf(
            "app/main/io/legado/app/help",
            "app/main/io/legado/app/base",
            "app/main/io/legado/app/model",
            "app/main/io/legado/app/service",
            "app/main/io/legado/app/api",
            "app/main/io/legado/app/utils",
            "app/main/io/legado/app/lib",
            "app/main/io/legado/app/receiver",
        )

        val actual = sortedMapOf<String, Int>()
        sourceFiles.files.forEach { file ->
            if (!file.isFile || !file.name.endsWith(".kt")) return@forEach
            val relativePath = file.relativeTo(root).invariantSeparatorsPath
            val match = sourceSetPattern.find(relativePath) ?: return@forEach
            val module = match.groupValues[1]
            val sourceSet = match.groupValues[2]
            // 测试源集不冻结：契约测试引用 Provider 是正当用法。
            if (sourceSet.contains("test", ignoreCase = true)) return@forEach
            val packageDir = match.groupValues[3].substringBeforeLast('/')
            val area = if (packageDir.isEmpty()) "$module/$sourceSet" else "$module/$sourceSet/$packageDir"
            val text = file.readText()
            importRules.forEach { (category, regex) ->
                val count = regex.findAll(text).count()
                if (count > 0) actual["$category|$area"] = (actual["$category|$area"] ?: 0) + count
            }
            // Provider 委托在定义文件内部（同包）无需 import，改用类型名匹配：
            // 逐行统计并跳过注释行（KDoc 里的 [ClipboardProvider.install] 不是调用），
            // 再排除该文件的 object 声明自身。
            val declared = providerDeclaration.findAll(text).map { it.groupValues[1] }.toSet()
            var providerCount = 0
            text.lineSequence().forEach { line ->
                val stripped = line.trimStart()
                if (stripped.startsWith("//") || stripped.startsWith("*") ||
                    stripped.startsWith("/*")
                ) {
                    return@forEach
                }
                providerCount += providerUse.findAll(line)
                    .count { it.groupValues[1] + "Provider" !in declared }
            }
            if (providerCount > 0) {
                actual["coreProvider|$area"] = (actual["coreProvider|$area"] ?: 0) + providerCount
            }
        }

        val baseline = linkedMapOf<String, Int>()
        baselineFile.get().asFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val parts = trimmed.split("|")
            check(parts.size == 3 && parts[2].toIntOrNull() != null) {
                "legacy 基线条目格式应为 <category>|<area>|<count>：$trimmed"
            }
            baseline["${parts[0]}|${parts[1]}"] = parts[2].toInt()
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        baseline.forEach { (key, allowed) ->
            val category = key.substringBefore('|')
            val area = key.substringAfter('|')
            val description = descriptions[category] ?: category
            val found = actual[key] ?: 0
            if (found > allowed) {
                val message = "$area：${description}新增 ${found - allowed} 处（基线 $allowed → 当前 $found）"
                if (reportOnlyAreas.any { area == it || area.startsWith("$it/") }) {
                    warnings += message
                } else {
                    errors += message
                }
            } else if (found < allowed) {
                errors += "$area：${description}已减少到 $found，请将基线从 $allowed 下调（棘轮只降不升）"
            }
        }
        (actual.keys - baseline.keys).forEach { key ->
            val category = key.substringBefore('|')
            val area = key.substringAfter('|')
            val description = descriptions[category] ?: category
            errors += "$area：${description}首次出现 ${actual[key]} 处；新区域必须为零，" +
                "或经评审后在基线中显式登记"
        }

        warnings.forEach { logger.warn("[legacy 债] $it") }
        check(errors.isEmpty()) {
            errors.joinToString(prefix = "legacy 架构基线护栏失败:\n", separator = "\n") +
                "\n参考: docs/dev/legacy-architecture-report.md；基线: gradle/architecture/legacy-baseline.txt"
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
    alias(libs.plugins.jetbrains.compose) apply false
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
            // 平台实现层的偏好读写是正当落点，与 data/repository、help/config 同类，
            // 不属 UI 层债务：替换规则的排序偏好由 :app 侧 impl 持有，契约侧只见 String。
            "io/legado/app/domain/gateway/AndroidReplaceRuleSettingsGateway.kt" to 2,
            // 已清零：排序模式改走 ReplaceRuleSettingsGateway。
            // 保留 0 值条目让棘轮继续盯着——VM 里再出现偏好直连会立即报红。
            "io/legado/app/feature/replacerules/ReplaceRuleViewModel.kt" to 0,
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
            "io/legado/app/ui/book/read/ReadBookController.kt" to 3,
            // F2 step4：ExactChapterPageCountStore 的 Room 实现已移至 data/reader/pageestimate/，
            // DAO 访问随之离开 ui 层，基线条目删除。
            "io/legado/app/ui/book/search/SearchScope.kt" to 4,
            "io/legado/app/ui/config/bookshelfConfig/BookshelfManageScreenConfig.kt" to 1,
            "io/legado/app/ui/main/MainNavGraph.kt" to 2,
            "io/legado/app/ui/rss/article/RssArticlesCompose.kt" to 1,
            "io/legado/app/ui/rss/read/RssJsExtensions.kt" to 8,
            "io/legado/app/ui/widget/dialog/BottomWebViewDialog.kt" to 1,
        )
    )
}

val checkSharedPurity = tasks.register<CheckSharedPurityTask>(
    "checkSharedPurity"
) {
    group = "verification"
    description = "按模块类型(pure/data/cmp)校验 commonMain 纯度：禁 platform / JVM-only / 越界 androidx（G2 纯度守卫）"
    rootDir.set(layout.projectDirectory)
    // M0-1：模块相对目录 → 模块类型。未登记模块按最严 pure 处理（见 task 内 policy）。
    //   pure = 零 Compose/Android/实现库（core:model 等领域模块）
    //   data = 允许 Room/sqlite 数据实现（core:data 及依赖 Room/ktor 的模块）
    //   cmp  = 允许 Compose/Lifecycle/Nav3 的 KMP 公共 API（尚无真实模块，M1 起由 CMP Feature 登记）
    // 类型语义与长期目标见 docs/dev/kmp-cmp-modernization.md §3.2。
    kmpModuleTypes.set(
        mapOf(
            "core/platform" to "data",        // commonMain 直接 import io.ktor
            "core/data" to "data",            // commonMain import androidx.room/sqlite
            "smoke/room-kmp-probe" to "data", // commonMain import androidx.room
            "smoke/network-kmp-probe" to "data", // commonMain 用 io.ktor
            "core/model" to "pure",
            // M1-2：转真 CMP（convention `legado.kmp.compose`），Compose 进 commonMain。
            // 应用该 convention 的模块必须在这里登记 "cmp"，两者成对出现，否则 G2 拦。
            "core/designsystem" to "cmp",
            "feature/reader/core" to "pure",
            "smoke/kmp-probe" to "pure",
            "smoke/rhino-capability-probe" to "pure",
        )
    )
    // 自动发现全仓 KMP 模块的 commonMain 源码：新增 KMP 模块无需改此配置即可被覆盖。
    // 排除 build/ 等生成物与无关重目录，避免把产物或 web 前端算入共享层。
    sharedSourceFiles.setFrom(
        layout.projectDirectory.asFileTree.matching {
            include("**/src/commonMain/**/*.kt")
            exclude(
                "**/build/**",
                "**/.gradle/**",
                "**/.workbuddy/**",
                "**/.idea/**",
                "**/.git/**",
                "**/node_modules/**",
                "**/modules/web/**",
            )
        }
    )
}

val checkModuleDependencies = tasks.register<CheckModuleDependenciesTask>(
    "checkModuleDependencies"
) {
    group = "verification"
    description = "禁止非法 Gradle 模块依赖方向：core→feature/host、feature-api→feature、feature-impl→feature-impl（G1 边界守卫）"
    rootDir.set(layout.projectDirectory)
    // 扫描全仓各模块的 build.gradle.kts；build-logic 是独立 included build，排除以免误判其内部 project()。
    moduleBuildFiles.setFrom(
        layout.projectDirectory.asFileTree.matching {
            include("**/build.gradle.kts")
            exclude(
                "**/build/**",
                "**/.gradle/**",
                "build-logic/**",
            )
        }
    )
}


val checkLegacyArchitecture = tasks.register<CheckLegacyArchitectureTask>(
    "checkLegacyArchitecture"
) {
    group = "verification"
    description = "legacy 架构债棘轮：全局门面(appCtx/appDb/GSON)、help/base 门面、core Provider 委托（M0-2 冻结，新代码 blocking）"
    rootDir.set(layout.projectDirectory)
    baselineFile.set(
        layout.projectDirectory.file("gradle/architecture/legacy-baseline.txt")
    )
    // 只扫生产源码：app 主源集 + 各模块 src/<非 test 源集>/kotlin|java。
    sourceFiles.setFrom(
        layout.projectDirectory.asFileTree.matching {
            include("app/src/main/java/**/*.kt")
            include("*/src/*/kotlin/**/*.kt")
            include("*/*/src/*/kotlin/**/*.kt")
            include("*/*/*/src/*/kotlin/**/*.kt")
            exclude(
                "**/build/**",
                "**/.gradle/**",
                "**/.workbuddy/**",
                "**/.idea/**",
                "**/.git/**",
                "**/node_modules/**",
                "**/modules/web/**",
                "build-logic/**",
            )
        }
    )
}

subprojects {
    tasks.configureEach {
        if (name.startsWith("assemble") || name.startsWith("compile")) {
            dependsOn(verifyConfigArchitecture)
            dependsOn(checkLegacyArchitecture)
        }
    }
}
