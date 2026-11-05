package io.legado.app.data.repository.manga.translation

import io.legado.app.domain.model.manga.MangaCloudOcrConfig
import com.google.gson.JsonParser
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class MangaCloudOcrSignatureTest {
    private fun parsed(text: String) = JsonParser.parseString(text).asJsonObject
    private fun payload(request: Request) = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
    @Test fun `Tencent requests have TC3 scope correct payload language and UTC date`() {
        val config = MangaCloudOcrConfig(apiKey = "fake-id", secretKey = "fake-secret", region = "ap-shanghai")
        val request = tencentOcrRequest("aGVsbG8=", "ja", config, 1551113065)
        assertEquals("GeneralAccurateOCR", request.header("X-TC-Action"))
        assertEquals("ap-shanghai", request.header("X-TC-Region"))
        assertTrue(request.header("Authorization")!!.contains("/2019-02-25/ocr/tc3_request"))
        // Independently generated Python hashlib/hmac vector for this exact UTF-8 body.
        assertTrue(request.header("Authorization")!!.endsWith("Signature=9704630c82a0d12dfeebee8c96e186b853861027b43c8fccd88d32a0b69d2d23"))
        assertEquals("jap", parsed(payload(request))["LanguageType"].asString)
        assertEquals("aGVsbG8=", parsed(payload(request))["ImageBase64"].asString)
        assertFalse(request.header("Authorization")!!.contains("fake-secret"))
        assertNotEquals(request.header("Authorization"), tencentOcrRequest("different", "ja", config, 1551113065).header("Authorization"))
    }

}
