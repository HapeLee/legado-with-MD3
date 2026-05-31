package io.legado.app.model.rss

import androidx.annotation.Keep
import io.legado.app.R
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setRuleData
import io.legado.app.model.analyzeRule.RuleData
import io.legado.app.utils.NetworkUtils
import splitties.init.appCtx
import java.util.Locale
import kotlin.coroutines.coroutineContext

@Keep
object RssParserByRule {

    @Throws(Exception::class)
    suspend fun parseXML(
        sortName: String,
        sortUrl: String,
        redirectUrl: String,
        body: String?,
        rssSource: RssSource,
        ruleData: RuleData
    ): Pair<MutableList<RssArticle>, String?> {
        val sourceUrl = rssSource.sourceUrl
        var nextUrl: String? = null
        if (body.isNullOrBlank()) {
            throw NoStackTraceException(
                appCtx.getString(R.string.error_get_web_content, rssSource.sourceUrl)
            )
        }
        Debug.log(sourceUrl, "≡获取成功:$sourceUrl")
        Debug.log(sourceUrl, body, state = 10)
        var ruleArticles = rssSource.ruleArticles
        if (ruleArticles.isNullOrBlank()) {
            Debug.log(sourceUrl, "⇒List rule is empty, using default rule to parse")
            return RssParserDefault.parseXML(sortName, body, sourceUrl)
        } else {
            val articleList = mutableListOf<RssArticle>()
            val analyzeRule = AnalyzeRule(ruleData, rssSource)
            analyzeRule.setCoroutineContext(coroutineContext)
            analyzeRule.setContent(body).setBaseUrl(sortUrl)
            analyzeRule.setRedirectUrl(redirectUrl)
            var reverse = false
            if (ruleArticles.startsWith("-")) {
                reverse = true
                ruleArticles = ruleArticles.substring(1)
            }
            Debug.log(sourceUrl, "┌Getting list")
            val collections = analyzeRule.getElements(ruleArticles)
            Debug.log(sourceUrl, "└List size:${collections.size}")
            if (!rssSource.ruleNextPage.isNullOrEmpty()) {
                Debug.log(sourceUrl, "┌Getting next page link")
                if (rssSource.ruleNextPage!!.uppercase(Locale.getDefault()) == "PAGE") {
                    nextUrl = sortUrl
                } else {
                    nextUrl = analyzeRule.getString(rssSource.ruleNextPage)
                    if (nextUrl.isNotEmpty()) {
                        nextUrl = NetworkUtils.getAbsoluteURL(sortUrl, nextUrl)
                    }
                }
                Debug.log(sourceUrl, "└$nextUrl")
            }
            val ruleTitle = analyzeRule.splitSourceRule(rssSource.ruleTitle)
            val rulePubDate = analyzeRule.splitSourceRule(rssSource.rulePubDate)
            val ruleDescription = analyzeRule.splitSourceRule(rssSource.ruleDescription)
            val ruleImage = analyzeRule.splitSourceRule(rssSource.ruleImage)
            val ruleLink = analyzeRule.splitSourceRule(rssSource.ruleLink)
            val variable = ruleData.getVariable()
            for ((index, item) in collections.withIndex()) {
                getItem(
                    sourceUrl, item, analyzeRule, variable, index == 0,
                    ruleTitle, rulePubDate, ruleDescription, ruleImage, ruleLink
                )?.let {
                    it.sort = sortName
                    it.origin = sourceUrl
                    articleList.add(it)
                }
            }
            if (reverse) {
                articleList.reverse()
            }
            return Pair(articleList, nextUrl)
        }
    }

    private fun getItem(
        sourceUrl: String,
        item: Any,
        analyzeRule: AnalyzeRule,
        variable: String?,
        log: Boolean,
        ruleTitle: List<AnalyzeRule.SourceRule>,
        rulePubDate: List<AnalyzeRule.SourceRule>,
        ruleDescription: List<AnalyzeRule.SourceRule>,
        ruleImage: List<AnalyzeRule.SourceRule>,
        ruleLink: List<AnalyzeRule.SourceRule>
    ): RssArticle? {
        val rssArticle = RssArticle(variable = variable)
        analyzeRule.setRuleData(rssArticle)
        analyzeRule.setContent(item)
        Debug.log(sourceUrl, "┌Getting title", log)
        rssArticle.title = analyzeRule.getString(ruleTitle)
        Debug.log(sourceUrl, "└${rssArticle.title}", log)
        Debug.log(sourceUrl, "┌Getting time", log)
        rssArticle.pubDate = analyzeRule.getString(rulePubDate)
        Debug.log(sourceUrl, "└${rssArticle.pubDate}", log)
        Debug.log(sourceUrl, "┌Getting description", log)
        if (ruleDescription.isEmpty()) {
            rssArticle.description = null
            Debug.log(sourceUrl, "└Description rule is empty, will parse content page", log)
        } else {
            rssArticle.description = analyzeRule.getString(ruleDescription)
            Debug.log(sourceUrl, "└${rssArticle.description}", log)
        }
        Debug.log(sourceUrl, "┌Getting image URL", log)
        rssArticle.image = analyzeRule.getString(ruleImage, isUrl = true)
        Debug.log(sourceUrl, "└${rssArticle.image}", log)
        Debug.log(sourceUrl, "┌Getting article link", log)
        rssArticle.link = NetworkUtils.getAbsoluteURL(sourceUrl, analyzeRule.getString(ruleLink))
        Debug.log(sourceUrl, "└${rssArticle.link}", log)
        if (rssArticle.title.isBlank()) {
            return null
        }
        return rssArticle
    }
}