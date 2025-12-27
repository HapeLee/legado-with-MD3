package io.legado.app.help.coil

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.network.HttpException
import coil.request.Options

import io.legado.app.data.entities.BaseSource
import io.legado.app.help.http.await
import io.legado.app.model.ReadManga
import io.legado.app.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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

        val request = Request.Builder()
            .url(url)
            .headers(options.headers)
            .build()

        val response = callFactory.newCall(request).execute()
        val body = response.body

        if (!response.isSuccessful) {
            body.close()
            throw HttpException(response)
        }

        if (ImageUtils.skipDecode(source, !isManga)) {
            return SourceResult(
                source = ImageSource(source = body.source(), context = options.context),
                mimeType = null,
                dataSource = DataSource.NETWORK
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val bytes = body.bytes()
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
                    dataSource = DataSource.NETWORK
                )
            } finally {
                body.close()
            }
        }
    }

    class Factory(
        private val okHttpClient: OkHttpClient,
        private val okHttpClientManga: OkHttpClient
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "http" && data.scheme != "https") return null

            val isManga = options.parameters.value("manga") as? Boolean == true
            val client = if (isManga) okHttpClientManga else okHttpClient

            return CoverFetcher(data.toString(), options, client)
        }
    }
}