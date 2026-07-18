package io.legado.app.domain.model.readaloud

data class SpeechEngineRoute(
    val engineType: String,
    val engineId: String,
    val speakerId: String = "",
)

data class RoutedSpeechVoice(
    val voice: ReadAloudVoice?,
    val usedFallback: Boolean,
)

/** Selects a voice that a concrete playback backend can execute. */
object SpeechVoiceRouter {

    fun route(
        cue: ReadAloudPlaybackCue,
        supportedEngineTypes: Set<String>,
        defaultRoute: SpeechEngineRoute,
    ): RoutedSpeechVoice {
        val candidates = buildList {
            add(cue.voice)
            addAll(cue.fallbackVoices)
        }.filterNotNull()
            .filter { it.enabled && it.available && it.engineType in supportedEngineTypes }
            .distinctBy(ReadAloudVoice::id)

        val selected = candidates.firstOrNull()
        if (selected != null) {
            return RoutedSpeechVoice(
                voice = selected,
                usedFallback = selected.id != cue.voice?.id,
            )
        }
        return RoutedSpeechVoice(
            voice = ReadAloudVoice(
                id = "runtime-default:${defaultRoute.engineType}:${defaultRoute.engineId}:${defaultRoute.speakerId}",
                engineType = defaultRoute.engineType,
                engineId = defaultRoute.engineId,
                speakerId = defaultRoute.speakerId,
                displayName = "",
                managedBy = ReadAloudVoice.MANAGED_BY_USER,
            ),
            usedFallback = true,
        )
    }
}
