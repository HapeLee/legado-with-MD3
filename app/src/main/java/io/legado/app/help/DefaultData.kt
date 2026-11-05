package io.legado.app.help

import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.BookCover
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug
import splitties.init.appCtx

object DefaultData {

    fun upVersion() {
        // 只读判断，无副作用：是否有待迁移项不依赖 MainActivity 写入
        // LocalConfig.versionCode 的时机，因此不受 Activity 启动顺序影响。
        if (!hasPendingMigration) {
            return
        }
        Coroutine.async {
            // 每项独立执行：单项失败不影响其余项；
            // 只有导入成功才提交标记，失败的项下次冷启动会重试。
            migrate(LocalConfig.needUpHttpTTS, ::importDefaultHttpTTS, LocalConfig::markHttpTTSUpToDate)
            migrate(
                LocalConfig.needUpTxtTocRule,
                ::importDefaultTocRules,
                LocalConfig::markTxtTocRuleUpToDate
            )
            migrate(
                LocalConfig.needUpRssSources,
                ::importDefaultRssSources,
                LocalConfig::markRssSourcesUpToDate
            )
            migrate(LocalConfig.needUpDictRule, ::importDefaultDictRules, LocalConfig::markDictRuleUpToDate)
        }.onError {
            it.printOnDebug()
        }
    }

    private val hasPendingMigration: Boolean
        get() = LocalConfig.needUpHttpTTS || LocalConfig.needUpTxtTocRule ||
                LocalConfig.needUpRssSources || LocalConfig.needUpDictRule

    private fun migrate(needed: Boolean, import: () -> Unit, mark: () -> Unit) {
        if (!needed) return
        runCatching(import)
            .onSuccess { mark() }
            .onFailure { it.printOnDebug() }
    }

    val httpTTS: List<HttpTTS> by lazy {
        val json =
            String(
                appCtx.assets.open("defaultData/httpTTS.json")
                    .readBytes()
            )
        HttpTTS.fromJsonArray(json).getOrElse {
            emptyList()
        }
    }

    val readConfigs: List<ReadBookConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/${ReadBookConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ReadBookConfig.Config>(json).getOrNull()
            ?: emptyList()
    }

    val txtTocRules: List<TxtTocRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/txtTocRule.json")
                .readBytes()
        )
        GSON.fromJsonArray<TxtTocRule>(json).getOrNull() ?: emptyList()
    }

    val themeConfigs: List<ThemeConfigStore.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/${ThemeConfigStore.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ThemeConfigStore.Config>(json).getOrNull() ?: emptyList()
    }

    val rssSources: List<RssSource> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/rssSources.json")
                .readBytes()
        )
        GSON.fromJsonArray<RssSource>(json).getOrDefault(emptyList())
    }

    val coverRule: BookCover.CoverRule by lazy {
        val json = String(
            appCtx.assets.open("defaultData/coverRule.json")
                .readBytes()
        )
        GSON.fromJsonObject<BookCover.CoverRule>(json).getOrThrow()
    }

    val dictRules: List<DictRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/dictRules.json")
                .readBytes()
        )
        GSON.fromJsonArray<DictRule>(json).getOrThrow()
    }

    val keyboardAssists: List<KeyboardAssist> by lazy {
        val json = String(
            appCtx.assets.open("defaultData/keyboardAssists.json")
                .readBytes()
        )
        GSON.fromJsonArray<KeyboardAssist>(json).getOrThrow()
    }

    fun importDefaultHttpTTS() {
        appDb.httpTTSDao.deleteDefault()
        appDb.httpTTSDao.insert(*httpTTS.toTypedArray())
    }

    fun importDefaultTocRules() {
        appDb.txtTocRuleDao.deleteDefault()
        appDb.txtTocRuleDao.insert(*txtTocRules.toTypedArray())
    }

    fun importDefaultRssSources() {
        appDb.rssSourceDao.deleteDefault()
        appDb.rssSourceDao.insert(*rssSources.toTypedArray())
    }

    /**
     * 历史上内置过、但源站已不可解析的词典规则（规则名 -> 旧 urlRule）。
     * 只在 urlRule 与旧内置值完全一致时删除，避免误删用户自建的同名规则。
     */
    private val obsoleteDictRules = mapOf(
        "百度汉语" to "https://dict.baidu.com/s?wd={{key}}"
    )

    fun importDefaultDictRules() {
        // 清理已失效的内置词典规则（源站改为前端渲染，规则无法解析出内容）
        obsoleteDictRules.forEach { (name, urlRule) ->
            val existing = appDb.dictRuleDao.getByName(name) ?: return@forEach
            if (existing.urlRule == urlRule) appDb.dictRuleDao.deleteByName(name)
        }
        appDb.dictRuleDao.insert(*dictRules.toTypedArray())
    }

}