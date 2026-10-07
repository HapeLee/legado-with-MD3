package io.legado.app.help.readaloud.cast

import android.content.Context
import io.legado.app.R
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SystemTtsVoiceConfig
import io.legado.app.help.readaloud.playback.CloudTtsAudioSynthesizer
import io.legado.app.help.readaloud.playback.SystemTtsFileSynthesizer
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.GSON
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * 音色试听：按这条音色自己的参数合成一句话，返回可直接播放的文件。
 *
 * 合成链路与朗读服务一致（云 TTS 用 [CloudTtsAudioSynthesizer] 的音色重载，系统 TTS 用
 * [SystemTtsFileSynthesizer] 文件合成），只是输出到缓存的试听目录，不进朗读队列、
 * 不影响正在播放的内容。http 音色由朗读服务负责，这里不支持。
 */
object VoiceAudition {

    suspend fun synthesize(context: Context, voiceId: String, text: String): File? {
        if (voiceId.isBlank() || text.isBlank()) return null
        val koin = GlobalContext.get()
        val voice = koin.get<ReadAloudVoiceGateway>().getVoice(voiceId) ?: return null
        val output = File(File(context.cacheDir, "voice_audition"), "${voice.id.hashCode()}.audio")
        val success = runCatching {
            when (voice.engineType) {
                ReadAloudVoice.ENGINE_CLOUD ->
                    CloudTtsAudioSynthesizer(koin.get<CloudTtsEngineGateway>())
                        .synthesize(voice, text, output)

                ReadAloudVoice.ENGINE_SYSTEM -> {
                    val config = runCatching {
                        GSON.fromJson(voice.traitsJson, SystemTtsVoiceConfig::class.java)
                    }.getOrNull() ?: SystemTtsVoiceConfig()
                    SystemTtsFileSynthesizer(context).synthesize(
                        engine = voice.engineId,
                        voiceName = voice.speakerId,
                        text = text,
                        output = output,
                        speechRate = config.speechRate ?: globalSpeechRate(),
                        pitch = config.pitch ?: 1f,
                    )
                }

                else -> false
            }
        }.getOrDefault(false)
        return output.takeIf { success && it.length() > 0 }
    }

    /** 与 TTSReadAloudService.applyPreset 同源的全局语速，音色自身没配语速时用它。 */
    private fun globalSpeechRate(): Float = when {
        ReadConfig.ttsFollowSys -> 1f
        else -> (ReadConfig.ttsSpeechRate + 5) / 10f
    }

    /** 通用试听文本：用户在角色声音池里自定义过就用他的，否则用内置示例句。 */
    fun defaultPreviewText(context: Context): String =
        GlobalContext.get().get<ReadAloudSettingsGateway>().currentSettings.voicePreviewText
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.cloud_tts_voice_preview_text)
}
