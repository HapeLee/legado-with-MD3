package io.legado.app.model.analyzeRule

import android.app.Application
import com.script.rhino.RhinoScriptEngine
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 回归锁定：书源校验时，`@js:` 书源列表规则产生的 JS 对象条目（Rhino NativeObject）
 * 在 AnalyzeRule.getString/getStringList 的“单规则快捷路径”上必须按 mode 分发。
 *
 * 修复前该快捷路径只做 `result[sourceRule.rule]`（把 `$.name` / `@js:xxx` 当字面量键名），
 * `$.name`（Mode.Json）与 `@js:xxx`（Mode.Js）在 JS 对象条目上返回空字符串、`$.authors`
 * 返回 null。BookList.getSearchItem 会丢弃书名为空的条目 → 搜索/发现结果为空 →
 * `BookSourceCheckRepository.checkSource` 判定“搜索失效/发现失效” → 书源被判为失效。
 *
 * 修复后两个快捷路径与通用路径一致（上游 legado 7dc8d809c / 6eea19643 同款）：
 *
 *   result = when {
 *       sourceRule.mode == Mode.Js    -> evalJS(sourceRule.rule, result)
 *       sourceRule.mode == Mode.Json  -> getAnalyzeByJSonPath(result).getString(sourceRule.rule)
 *       sourceRule.getParamSize() > 1 -> sourceRule.rule
 *       else -> result[sourceRule.rule]
 *   }
 *
 * 用例跑在 Robolectric 上：见 [setUp] 关于 `appCtx` 的说明。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AnalyzeRuleFastPathReproTest {

    /**
     * 用 `Application::class` 而不是清单里的 `io.legado.app.App`：否则 Robolectric 会跑
     * `App.onCreate`，其中 `LocalConfig` 的静态初始化读 `appCtx`，而 App Startup 的 provider
     * 在 Robolectric 下不会执行 → `IllegalStateException: appCtx has not been initialized!`，
     * 测试根本到不了断言。
     */
    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    /** 模拟 `ruleBookList: @js:xxx` 返回的 JS 对象数组中的单个条目（NativeObject） */
    private fun jsObjectItem(): Any {
        return RhinoScriptEngine.eval(
            "({name:'测试书名', author:'作者', authors:['甲','乙']})"
        )!!
    }

    @Test
    fun `Mode Json rule on JS object item returns the field value`() {
        val analyzeRule = AnalyzeRule(RuleData(), null)
        analyzeRule.setContent(jsObjectItem())
        // 上游返回 "测试书名"；本仓库的快捷路径把 "$.name" 当字面量键名，返回 ""
        assertEquals("测试书名", analyzeRule.getString("$.name"))
    }

    @Test
    fun `Mode Js rule on JS object item is evaluated`() {
        val analyzeRule = AnalyzeRule(RuleData(), null)
        analyzeRule.setContent(jsObjectItem())
        // 上游返回 "测试书名"；本仓库的快捷路径不执行 JS，返回 ""
        assertEquals("测试书名", analyzeRule.getString("@js:result.name"))
    }

    @Test
    fun `Mode Json getStringList on JS object item returns list`() {
        val analyzeRule = AnalyzeRule(RuleData(), null)
        analyzeRule.setContent(jsObjectItem())
        // 上游返回 ["甲","乙"]；本仓库的快捷路径返回 null
        assertEquals(listOf("甲", "乙"), analyzeRule.getStringList("$.authors"))
    }
}
