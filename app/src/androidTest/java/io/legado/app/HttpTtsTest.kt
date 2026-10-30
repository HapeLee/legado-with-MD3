package io.legado.app

import io.legado.app.model.analyzeRule.AnalyzeUrl
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HttpTtsTest {

    @Test
    fun test() {
        val url = """
            http://tsn.baidu.com/text2audio,{
                "method": "POST",
                "body": "tex={{java.encodeURI(java.encodeURI(speakText))}}&spd={{(speakSpeed + 5) / 10 + 4}}&per=4114&cuid=baidu_speech_demo&idx=1&cod=2&lan=zh&ctp=1&pdt=220&vol=5&aue=6&pit=5&_res_tag_=audio"
            }
        """.trimIndent()
        val analyzeUrl =
            // Keep this endpoint smoke test independent of the user's playback settings.
            AnalyzeUrl(url, speakText = "魔神", speakSpeed = 10)
        runBlocking {
            val response = analyzeUrl.getResponseAwait()
            response.headers
        }
    }

}
