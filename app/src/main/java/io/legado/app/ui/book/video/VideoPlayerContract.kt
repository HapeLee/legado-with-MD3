package io.legado.app.ui.book.video

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** 播放页底部弹出的面板。 */
enum class VideoPlayerSheet {
    EPISODES,
    SPEED,
    SETTINGS,
}

@Stable
data class VideoPlayerUiState(
    val bookName: String = "",
    val chapterTitle: String = "",
    /** 卷名；为空表示不分卷，此时选集只列集。 */
    val volumes: ImmutableList<String> = persistentListOf(),
    val volumeIndex: Int = 0,
    val episodes: ImmutableList<String> = persistentListOf(),
    val episodeIndex: Int = 0,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isFullscreen: Boolean = false,
    val error: String? = null,
    val speed: Float = 1f,
    val showDanmaku: Boolean = true,
    val autoPlay: Boolean = true,
    val startFull: Boolean = false,
    /** 长按倍速，单位 0.1 倍。 */
    val longPressSpeed: Int = 30,
    val floatingEnabled: Boolean = false,
    val sheet: VideoPlayerSheet? = null,
) {
    val hasEpisodeList: Boolean get() = episodes.size > 1 || volumes.size > 1
}

sealed interface VideoPlayerIntent {

    data object TogglePlay : VideoPlayerIntent

    /** 生命周期暂停：退到后台时保存进度，不写「用户已暂停」状态。 */
    data object Pause : VideoPlayerIntent

    data class SeekTo(val positionMs: Long) : VideoPlayerIntent

    data object PreviousEpisode : VideoPlayerIntent

    data object NextEpisode : VideoPlayerIntent

    data class SelectVolume(val index: Int) : VideoPlayerIntent

    data class SelectEpisode(val index: Int) : VideoPlayerIntent

    data class SetSpeed(val speed: Float) : VideoPlayerIntent

    data object ToggleFullscreen : VideoPlayerIntent

    data object Retry : VideoPlayerIntent

    data class OpenSheet(val sheet: VideoPlayerSheet) : VideoPlayerIntent

    data object CloseSheet : VideoPlayerIntent

    data class SetAutoPlay(val enabled: Boolean) : VideoPlayerIntent

    data class SetStartFull(val enabled: Boolean) : VideoPlayerIntent

    data class SetLongPressSpeed(val value: Int) : VideoPlayerIntent

    data object ToggleDanmaku : VideoPlayerIntent

    /** 手动切换悬浮小窗。 */
    data object ToggleFloating : VideoPlayerIntent

    data class SetFloatingEnabled(val enabled: Boolean) : VideoPlayerIntent

    /** 复制当前集的真实地址；走书源回调，与阅读/音频一致。 */
    data object CopyPlayUrl : VideoPlayerIntent

    /** 交给系统播放器播放当前地址。 */
    data object OpenInExternalPlayer : VideoPlayerIntent

    /** 长按加速期间临时提速，松手恢复常驻倍速。 */
    data class LongPressSpeed(val active: Boolean) : VideoPlayerIntent
}

sealed interface VideoPlayerEffect {

    data class ShowToast(val message: String) : VideoPlayerEffect

    /**
     * 复制当前集地址。书源可以接管这次复制（书源回调返回 true 时不走剪贴板），
     * 需要 Activity，所以交给宿主执行。
     */
    data class CopyPlayUrl(val url: String) : VideoPlayerEffect

    data class OpenExternalPlayer(val url: String, val title: String?) : VideoPlayerEffect
}
