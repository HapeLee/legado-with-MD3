package io.legado.app.data.repository.ai

import io.legado.app.help.http.okHttpClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * AI generation can legitimately spend several minutes before the next token arrives.
 * Keep the normal connect timeout from the shared client, but do not time out reads or
 * the whole call. A blocking body read ignores coroutine cancellation, so ending a stream
 * is done by closing the response — see `readSseData` in AiSseUtils.kt.
 */
internal val aiOkHttpClient: OkHttpClient by lazy {
    okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
}
