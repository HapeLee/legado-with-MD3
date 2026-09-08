package io.legado.app.base.rules

import android.content.Context
import androidx.core.net.toUri
import io.legado.app.constant.AppConst
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isUri
import io.legado.app.utils.readText

/**
 * [RuleTransferPlatform] 的 Android 实现。
 *
 * 两个方法的实现体是从 `BaseRuleViewModel.resolveSource` / `exportToUri` **原样搬过来**的，
 * 只把 `context` 由构造函数注入。迁移到独立模块时，本文件留在 `:app`，接口随基类下沉。
 */
class AndroidRuleTransferPlatform(private val context: Context) : RuleTransferPlatform {

    override suspend fun readImportSource(text: String): String {
        return when {
            text.isAbsUrl() -> {
                okHttpClient.newCallResponseBody {
                    if (text.endsWith("#requestWithoutUA")) {
                        url(text.substringBeforeLast("#requestWithoutUA"))
                        header(AppConst.UA_NAME, "null")
                    } else {
                        url(text)
                    }
                }.decompressed().text("utf-8")
            }

            text.isUri() -> text.toUri().readText(context)
            else -> text
        }
    }

    override suspend fun writeExport(targetUri: String, content: String) {
        context.contentResolver.openOutputStream(targetUri.toUri())?.use { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                writer.write(content)
                writer.flush()
            }
        }
    }
}
