package io.legado.app.ui.video.config

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.model.VideoPlay
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.dpToPx
import io.legado.app.utils.windowSize

class SettingsDialog(
    @Suppress("unused") private val context: Context,
    private val callBack: CallBack? = null
) : DialogFragment() {

    private val widthFraction: Float = 0.9f
    private val maxWidthDp: Int = 520

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    VideoSettingsContent()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val attr = window.attributes
            attr.gravity = Gravity.CENTER
            window.attributes = attr
            val dm = requireActivity().windowManager.windowSize
            val target = (dm.widthPixels * widthFraction).toInt()
            val maxWidth = maxWidthDp.dpToPx()
            window.setLayout(minOf(target, maxWidth), WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    @Composable
    private fun VideoSettingsContent() {
        var autoPlay by rememberSaveable { mutableStateOf(VideoPlay.autoPlay) }
        var startFull by rememberSaveable { mutableStateOf(VideoPlay.startFull) }
        var fullBottomProgress by rememberSaveable { mutableStateOf(VideoPlay.fullBottomProgressBar) }
        var longPressSpeed by rememberSaveable { mutableIntStateOf(VideoPlay.longPressSpeed) }

        NormalCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppText(
                    text = stringResource(R.string.config_settings),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                )
                TinySwitchSettingItem(
                    title = stringResource(R.string.auto_play),
                    checked = autoPlay,
                    onCheckedChange = { checked ->
                        autoPlay = checked
                        VideoPlay.autoPlay = checked
                    }
                )
                if (autoPlay) {
                    TinySwitchSettingItem(
                        title = stringResource(R.string.start_full),
                        checked = startFull,
                        onCheckedChange = { checked ->
                            startFull = checked
                            VideoPlay.startFull = checked
                        }
                    )
                }
                TinySwitchSettingItem(
                    title = stringResource(R.string.full_bottom_progress),
                    checked = fullBottomProgress,
                    onCheckedChange = { checked ->
                        fullBottomProgress = checked
                        VideoPlay.fullBottomProgressBar = checked
                    }
                )
                TinyClickableSettingItem(
                    title = stringResource(R.string.press_speed),
                    description = longPressSpeed.toPressSpeedStr(),
                    onClick = { showPressSpeedDialog { value -> longPressSpeed = value } }
                )
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showPressSpeedDialog(onChanged: (Int) -> Unit) {
        NumberPickerDialog(requireContext())
            .setTitle(getString(R.string.press_speed))
            .setMaxValue(60)
            .setMinValue(5)
            .setValue(VideoPlay.longPressSpeed)
            .setCustomButton(R.string.btn_default_s) {
                VideoPlay.longPressSpeed = 30
                onChanged(30)
            }
            .show {
                VideoPlay.longPressSpeed = it
                onChanged(it)
            }
    }

    private fun Int.toPressSpeedStr(): String {
        return "${this / 10.0f}X"
    }

    interface CallBack
}
