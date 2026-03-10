package io.legado.app.help.coil

import android.net.Uri
import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.network.HttpException
import coil.request.Options
import io.legado.app.data.entities.BaseSource
import io.legado.app.model.ReadManga
import io.legado.app.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException

class CoverFetcher(
    private val url: String,
    private val options: Options,
    private val callFactory: Call.Factory
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val source = options.tags.tag<BaseSource>()
        val isManga = options.parameters.value("manga") as? Boolean == true

        val bytes = if (url.startsWith("data:", true)) {
            val base64Data = url.substringAfter("base64,", "")
            if (base64Data.isEmpty()) {
                throw IOException("Invalid data URI")
            }
            Base64.decode(base64Data, Base64.DEFAULT)
        } else {
            val request = Request.Builder()
                .url(url)
                .headers(options.headers)
                .build()

            val response = withContext(Dispatchers.IO) {
                callFactory.newCall(request).execute()
            }
            val body = response.body

            if (!response.isSuccessful) {
                body.close()
                throw HttpException(response)
            }

            val b = body.use { body ->
                body.bytes()
            }
            b
        }

        if (ImageUtils.skipDecode(source, !isManga)) {
            return SourceResult(
                source = ImageSource(source = Buffer().write(bytes), context = options.context),
                mimeType = null,
                dataSource = if (url.startsWith(
                        "data:",
                        true
                    )
                ) DataSource.MEMORY else DataSource.NETWORK
            )
        }

        return withContext(Dispatchers.IO) {
            val decodedBytes = if (isManga) {
                ImageUtils.decode(url, bytes, false, source, ReadManga.book)
            } else {
                ImageUtils.decode(url, bytes, true, source)
            } ?: throw IOException("图片解密失败")

            SourceResult(
                source = ImageSource(
                    source = Buffer().write(decodedBytes),
                    context = options.context
                ),
                mimeType = null,
                dataSource = if (url.startsWith(
                        "data:",
                        true
                    )
                ) DataSource.MEMORY else DataSource.NETWORK
            )
        }
    }

    class Factory(
        private val okHttpClient: OkHttpClient,
        private val okHttpClientManga: OkHttpClient
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            if (scheme != "http" && scheme != "https" && scheme != "data") return null

            val isManga = options.parameters.value("manga") as? Boolean == true
            val client = if (isManga) okHttpClientManga else okHttpClient

            return CoverFetcher(data.toString(), options, client)
        }
    }
}
