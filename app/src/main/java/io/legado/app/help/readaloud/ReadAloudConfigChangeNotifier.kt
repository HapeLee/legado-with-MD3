package io.legado.app.help.readaloud

import android.os.Bundle
import io.legado.app.constant.EventBus
import io.legado.app.utils.postEvent

object ReadAloudConfigChangeNotifier {

    fun notify(scope: String) {
        postEvent(
            EventBus.READ_ALOUD_CONFIG_CHANGED,
            Bundle().apply {
                putString(EventBus.READ_ALOUD_CONFIG_SCOPE, scope)
            }
        )
    }

    fun notifyEngine() {
        notify(EventBus.READ_ALOUD_CONFIG_SCOPE_ENGINE)
    }

    fun notifySpeech() {
        notify(EventBus.READ_ALOUD_CONFIG_SCOPE_SPEECH)
    }

    fun notifyAudio() {
        notify(EventBus.READ_ALOUD_CONFIG_SCOPE_AUDIO)
    }
}
