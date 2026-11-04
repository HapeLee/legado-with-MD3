package io.legado.app.di

import io.legado.app.data.repository.manga.engine.LocalMangaEngine
import io.legado.app.data.repository.manga.engine.MangaLocalEngine
import io.legado.app.data.repository.manga.translation.MangaInpaintModelRepository
import io.legado.app.data.repository.manga.translation.MangaLocalInpainter
import io.legado.app.data.repository.manga.translation.MangaOcrModelRepository
import io.legado.app.data.repository.manga.translation.MangaPaddleOcr
import io.legado.app.data.repository.manga.translation.MangaVisionModelRepository
import io.legado.app.domain.gateway.MangaInpaintModelGateway
import io.legado.app.domain.gateway.MangaOcrModelGateway
import io.legado.app.domain.gateway.MangaVisionModelGateway
import org.koin.dsl.module

/** 完整产品：内置运行时与权重管理。装配形态与拆分前保持一致。 */
val mangaEngineModule = module {
    single { MangaOcrModelRepository(get()) }
    single<MangaOcrModelGateway> { get<MangaOcrModelRepository>() }
    single { MangaInpaintModelRepository(get()) }
    single<MangaInpaintModelGateway> { get<MangaInpaintModelRepository>() }
    single { MangaLocalInpainter(get(), get()) }
    single { MangaPaddleOcr(get()) }
    single { MangaVisionModelRepository(get()) }
    single<MangaVisionModelGateway> { get<MangaVisionModelRepository>() }
    single<MangaLocalEngine> { LocalMangaEngine(get(), get(), get(), get()) }
}
