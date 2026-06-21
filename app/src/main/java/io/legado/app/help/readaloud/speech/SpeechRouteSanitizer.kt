package io.legado.app.help.readaloud.speech

import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook

object SpeechRouteSanitizer {

    data class CleanupResult(
        val characterCount: Int = 0,
        val bookCount: Int = 0,
        val speakerGroupItemCount: Int = 0,
        val globalCleared: Boolean = false
    ) {
        val changed: Boolean
            get() = characterCount > 0 || bookCount > 0 || speakerGroupItemCount > 0 || globalCleared
    }

    fun validOrNull(
        route: SpeechRoute?,
        httpTtsList: List<HttpTTS> = appDb.httpTTSDao.all
    ): SpeechRoute? {
        if (route == null || !route.isConfigured) return route
        return if (isValid(route, httpTtsList)) route else null
    }

    fun validOrDefault(
        route: SpeechRoute?,
        httpTtsList: List<HttpTTS> = appDb.httpTTSDao.all
    ): SpeechRoute {
        return validOrNull(route, httpTtsList) ?: SpeechRoute()
    }

    fun cleanDeletedHttpTts(httpTTS: HttpTTS): CleanupResult {
        val deletedId = httpTTS.id.toString()
        var characterCount = 0
        var bookCount = 0

        appDb.bookCharacterDao.allCharacters()
            .filter { routeReferencesHttpTts(it.speechRouteJson, deletedId) }
            .forEach { character ->
                SpeechVoiceGroupRepository.markInvalidRoute(
                    SpeechRoute.fromJson(character.speechRouteJson),
                    reason = "HTTP TTS 已删除"
                )
                appDb.bookCharacterDao.updateCharacter(
                    character.copy(
                        speechRouteJson = "",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                characterCount++
            }

        appDb.bookDao.all
            .filter { routeReferencesHttpTts(it.getTtsEngine(), deletedId) }
            .forEach { book ->
                SpeechVoiceGroupRepository.markInvalidRoute(
                    SpeechRoute.fromTtsEngineValue(book.getTtsEngine()),
                    reason = "HTTP TTS 已删除"
                )
                book.setTtsEngine(null)
                appDb.bookDao.update(book)
                bookCount++
            }

        val globalCleared = routeReferencesHttpTts(AppConfig.ttsEngine, deletedId)
        if (globalCleared) {
            SpeechVoiceGroupRepository.markInvalidRoute(
                SpeechRoute.fromTtsEngineValue(AppConfig.ttsEngine),
                reason = "HTTP TTS 已删除"
            )
            AppConfig.ttsEngine = null
        }
        ReadBook.book?.takeIf { routeReferencesHttpTts(it.getTtsEngine(), deletedId) }?.setTtsEngine(null)
        val groupsById = appDb.readAloudSpeakerGroupDao.groups().associateBy { it.id }
        val speakerItems = appDb.readAloudSpeakerGroupDao.items()
            .filter {
                it.engineType == SpeechRoute.ENGINE_HTTP &&
                    it.engineValue == deletedId &&
                    groupsById[it.groupId]?.let(SpeechVoiceGroupRepository::isInvalidGroup) != true
            }
        SpeechVoiceGroupRepository.markInvalidItems(speakerItems, reason = "HTTP TTS 已删除")

        return CleanupResult(
            characterCount = characterCount,
            bookCount = bookCount,
            speakerGroupItemCount = speakerItems.size,
            globalCleared = globalCleared
        )
    }

    fun cleanInvalidRoutes(): CleanupResult {
        val httpTtsList = appDb.httpTTSDao.all
        var characterCount = 0
        var bookCount = 0

        appDb.bookCharacterDao.allCharacters()
            .filter { it.speechRouteJson.isNotBlank() }
            .filter { validOrNull(SpeechRoute.fromJson(it.speechRouteJson), httpTtsList) == null }
            .forEach { character ->
                SpeechVoiceGroupRepository.markInvalidRoute(
                    SpeechRoute.fromJson(character.speechRouteJson),
                    reason = "发言人已失效"
                )
                appDb.bookCharacterDao.updateCharacter(
                    character.copy(
                        speechRouteJson = "",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                characterCount++
            }

        appDb.bookDao.all
            .filter { it.getTtsEngine().isNullOrBlank().not() }
            .filter { validOrNull(SpeechRoute.fromTtsEngineValue(it.getTtsEngine()), httpTtsList) == null }
            .forEach { book ->
                SpeechVoiceGroupRepository.markInvalidRoute(
                    SpeechRoute.fromTtsEngineValue(book.getTtsEngine()),
                    reason = "发言人已失效"
                )
                book.setTtsEngine(null)
                appDb.bookDao.update(book)
                bookCount++
            }

        val globalCleared = AppConfig.ttsEngine?.let {
            validOrNull(SpeechRoute.fromTtsEngineValue(it), httpTtsList) == null
        } ?: false
        if (globalCleared) {
            SpeechVoiceGroupRepository.markInvalidRoute(
                SpeechRoute.fromTtsEngineValue(AppConfig.ttsEngine),
                reason = "发言人已失效"
            )
            AppConfig.ttsEngine = null
        }
        ReadBook.book?.takeIf {
            validOrNull(SpeechRoute.fromTtsEngineValue(it.getTtsEngine()), httpTtsList) == null
        }?.setTtsEngine(null)
        val groupsById = appDb.readAloudSpeakerGroupDao.groups().associateBy { it.id }
        val invalidSpeakerItems = appDb.readAloudSpeakerGroupDao.items()
            .filter { item ->
                groupsById[item.groupId]?.let(SpeechVoiceGroupRepository::isInvalidGroup) != true &&
                    !SpeechVoiceGroupRepository.isValidItem(item, httpTtsList)
            }
        SpeechVoiceGroupRepository.markInvalidItems(invalidSpeakerItems, reason = "发言人已失效")

        return CleanupResult(
            characterCount = characterCount,
            bookCount = bookCount,
            speakerGroupItemCount = invalidSpeakerItems.size,
            globalCleared = globalCleared
        )
    }

    private fun routeReferencesHttpTts(value: String?, httpTtsId: String): Boolean {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return false
        val route = SpeechRoute.fromTtsEngineValue(raw)
        return route.engineType == SpeechRoute.ENGINE_HTTP && route.engineValue == httpTtsId
    }

    private fun isValid(route: SpeechRoute, httpTtsList: List<HttpTTS>): Boolean {
        if (!route.isConfigured) return true
        if (route.engineType != SpeechRoute.ENGINE_HTTP) return true
        val id = route.engineValue.toLongOrNull() ?: return false
        val httpTTS = httpTtsList.firstOrNull { it.id == id } ?: return false
        if (route.toneID.isBlank()) return true
        val speakers = SpeechVoiceCatalogParser
            .parseSpeakerGroups(httpTTS.speakersJson)
            .flatMap { it.items }
        return speakers.any { it.toneID == route.toneID }
    }
}
