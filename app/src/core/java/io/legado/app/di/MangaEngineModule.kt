package io.legado.app.di

import android.content.Context
import io.legado.app.R
import io.legado.app.data.repository.manga.engine.MangaLocalEngine
import io.legado.app.data.repository.manga.engine.UnavailableMangaInpaintModelGateway
import io.legado.app.data.repository.manga.engine.UnavailableMangaLocalEngine
import io.legado.app.data.repository.manga.engine.UnavailableMangaOcrModelGateway
import io.legado.app.data.repository.manga.engine.UnavailableMangaVisionModelGateway
import io.legado.app.domain.gateway.MangaInpaintModelGateway
import io.legado.app.domain.gateway.MangaOcrModelGateway
import io.legado.app.domain.gateway.MangaVisionModelGateway
import org.koin.dsl.module

/**
 * core 基础产品：本地能力以真实"不可用"实现装配，缺能力时明确失败而不是伪造成空结果。
 * 云端 OCR、AI 识图/翻译、阅读与已发布译图展示都不依赖这里的绑定。
 */
val mangaEngineModule = module {
    single<MangaLocalEngine> {
        UnavailableMangaLocalEngine(get<Context>().getString(R.string.manga_local_engine_requires_comic))
    }
    single<MangaOcrModelGateway> {
        UnavailableMangaOcrModelGateway(get<Context>().getString(R.string.manga_local_engine_requires_comic))
    }
    single<MangaInpaintModelGateway> {
        UnavailableMangaInpaintModelGateway(get<Context>().getString(R.string.manga_local_engine_requires_comic))
    }
    single<MangaVisionModelGateway> {
        UnavailableMangaVisionModelGateway(get<Context>().getString(R.string.manga_local_engine_requires_comic))
    }
}
