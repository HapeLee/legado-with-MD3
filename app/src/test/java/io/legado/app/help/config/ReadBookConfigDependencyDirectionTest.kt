package io.legado.app.help.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R4.3 —— `ReadBookConfig` 与 `ReadStyleGateway` 之间的依赖必须单向。
 *
 * 原来是个环：`ReadBookConfig`（排版数据的全局存储）持有 `readStyleGateway` 并在
 * `clearMissingTextFont()` 里反过来命令它改样式、落盘；而 gateway 的实现
 * `ReadBookStyleConfigRepository` 又大量读 `ReadBookConfig`。存储反向命令拥有它的领域层，
 * 谁是权威就说不清了，也让两边都没法单独测。
 *
 * 现在方向是单向的：消费方（`ChapterProvider`）直接找 gateway，`ReadBookConfig` 只向下
 * 依赖 `ReadStyleConfigStore`（状态本体，R4.5 之前是 `ReadStyleRepository`）。
 *
 * 注意 `readSettingsGateway` 不在此列——那是**只读**上游设置，不构成环。
 *
 * R4.4 又把写面整个搬进了 gateway 实现，`ReadBookConfig` 降为只读投影。这条不变式有两半，
 * 分别由两处把守：**调用点**那半归 build.gradle.kts 的 `verifyConfigArchitecture`
 * （「ReadBookConfig 写入必须经过 ReadStyleGateway」，编译期就拦）；**声明点**那半归本文件
 * ——文件内部写的是 `config.x = value`，绕不过那条正则，只能在这里断言。
 */
class ReadBookConfigDependencyDirectionTest {

    @Test
    fun `ReadBookConfig 不反向依赖 ReadStyleGateway`() {
        val source = stripComments(
            mainSourceFile("io/legado/app/help/config/ReadBookConfig.kt").readText()
        )
        val violations = buildList {
            if (Regex("""\bReadStyleGateway\b""").containsMatchIn(source)) {
                add("引用了 ReadStyleGateway")
            }
            if (Regex("""\bReadStyleMutation\b""").containsMatchIn(source)) {
                add("引用了 ReadStyleMutation（在向领域层下达样式变更）")
            }
            if (Regex("""\battachGateway\b""").containsMatchIn(source)) {
                add("又出现了 attachGateway 这类事后回填的反向引用")
            }
        }
        assertTrue(
            "ReadBookConfig 又反向依赖排版 gateway 了：${violations.joinToString()}。\n" +
                "排版存储不该命令拥有它的领域层——需要改样式的调用方请自己拿 " +
                "ReadStyleGateway（ChapterProvider.getTypeface 是现成例子）。",
            violations.isEmpty(),
        )
    }

    @Test
    fun `ReadBookConfig 是只读投影，没有公开可写属性`() {
        val source = stripComments(readBookConfigSource())
        val writable = Regex("""^    var (\w+)""", RegexOption.MULTILINE)
            .findAll(source)
            .map { it.groupValues[1] }
            .filterNot { it in WRITABLE_ALLOWLIST }
            .toList()

        assertTrue(
            "ReadBookConfig 又长出了公开可写属性：${writable.joinToString()}。\n" +
                "排版值的写面只属于 ReadStyleGateway 的实现——加一个 setter，" +
                "就等于给全应用重新开了一条绕过 gateway 改配置、且不会触发 save/publishState " +
                "的旁路，而且它是静默的：改了不落盘、订阅方收不到通知。\n" +
                "新增可写排版项请加 ReadStyleMutation 的 key，在 " +
                "ReadBookStyleConfigRepository 的 dispatch 里写 Config。",
            writable.isEmpty(),
        )
    }

    @Test
    fun `ReadBookConfig 不再持有排版状态本体`() {
        val source = stripComments(readBookConfigSource())
        val violations = buildList {
            Regex("""^    (?:private )?(?:val|var) (\w+)\s*:\s*(?:Array|Mutable)?List<Config>""", MULTILINE)
                .findAll(source)
                .forEach { add("${it.groupValues[1]}（配置列表）") }
            Regex("""^    (?:private )?lateinit var (\w+)\s*:\s*Config\b""", MULTILINE)
                .findAll(source)
                .forEach { add("${it.groupValues[1]}（共享排版那一份）") }
            Regex("""^    (?:private )?(?:val|var) (\w+)\s*=\s*(?:arrayListOf|mutableListOf|mutableMapOf|hashMapOf)""", MULTILINE)
                .findAll(source)
                .forEach { add("${it.groupValues[1]}（可变集合）") }
        }

        assertTrue(
            "ReadBookConfig 又把排版状态本体收回去了：${violations.joinToString()}。\n" +
                "R4.5 已经把 configList / shareConfig 连同那把锁一起搬进 ReadStyleConfigStore——" +
                "状态和保护它的锁必须待在一起，否则下一个人只会照着现成的字段继续往这个全局 " +
                "object 上堆。ReadBookConfig 只做两件事：按设置解析出「选哪一份」，以及把 " +
                "Config 的字段投影成只读属性。\n" +
                "需要新的排版状态，请加在 ReadStyleConfigStore 上。",
            violations.isEmpty(),
        )
    }

    @Test
    fun `ReadBookConfig 只从 store 读，不调它的写方法`() {
        val source = stripComments(readBookConfigSource())
        val called = STORE_WRITES.filter { Regex("""\bconfigStore\.$it\s*\(""").containsMatchIn(source) }
            .filterNot { it in INITIALIZE_ONLY && initializeBody(source).contains("$it(") }

        assertTrue(
            "ReadBookConfig 调了 ReadStyleConfigStore 的写方法：${called.joinToString()}。\n" +
                "它持有 store 只是为了把当前选中那份投影成只读属性——一旦从这里改状态，" +
                "改动既不落盘也不发 publishState，弹层和渲染都收不到通知，" +
                "而且 build.gradle.kts 那条「ReadBookConfig 写入必须经过 ReadStyleGateway」" +
                "的正则完全看不见（它只认 `ReadBookConfig.x = `）。\n" +
                "写请走 ReadStyleGateway 的实现（ReadBookStyleConfigRepository）。\n" +
                "例外：durConfig 的整份替换 setter 用 replaceConfigAt，initialize 用两个 init*。",
            called.isEmpty(),
        )
    }

    private companion object {
        val MULTILINE = RegexOption.MULTILINE

        /** [ReadStyleConfigStore] 上会改状态的方法。 */
        val STORE_WRITES = listOf(
            "updateEffective",
            "updateStyleAt",
            "addConfig",
            "deleteConfigAt",
            "importOrReplaceConfig",
            "initConfigs",
            "initShareConfig",
        )

        /** 只允许出现在 `initialize()` 里的两个——首帧之前得先把配置读进来。 */
        val INITIALIZE_ONLY = setOf("initConfigs", "initShareConfig")

        fun initializeBody(source: String): String {
            val start = source.indexOf("internal fun initialize(")
            if (start < 0) return ""
            val end = source.indexOf("\n    }", start)
            return if (end < 0) source.substring(start) else source.substring(start, end)
        }

        /** `durConfig` 是整份配置的替换入口（应用预设 / 导入），只有 gateway 实现用得到。 */
        val WRITABLE_ALLOWLIST = setOf("durConfig")

        fun readBookConfigSource(): String =
            mainSourceFile("io/legado/app/help/config/ReadBookConfig.kt").readText()

        fun stripComments(text: String): String = text
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

        fun mainSourceFile(relativePath: String): File {
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (prefix in listOf("src/main/java", "app/src/main/java")) {
                    val candidate = File(directory, "$prefix/$relativePath")
                    if (candidate.isFile) return candidate
                }
                directory = directory.parentFile
            }
            error("从 ${File("").absolutePath} 向上找不到 $relativePath")
        }
    }
}
