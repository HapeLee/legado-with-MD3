package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogShadowSetBinding
import io.legado.app.utils.postEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 字体选择对话框
 */
class ShadowSetDialog : BaseBottomSheetDialogFragment(R.layout.dialog_shadow_set) {

    companion object {
        const val S_COLOR = 123
    }
    private val fontRegex = Regex("(?i).*\\.[ot]tf")
    private val binding by viewBinding(DialogShadowSetBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        upView()
        initViewEvent()
    }

    private fun initView() = binding.run {
        binding.dsbShadowRadius.valueFormat = { "$it px" }
        binding.dsbShadowDx.valueFormat = { "$it px" }
        binding.dsbShadowDy.valueFormat = { "$it px" }
    }

    private fun initViewEvent() = binding.run {
        binding.dsbShadowRadius.onChanged = {
            ReadBookConfig.shadowRadius = it.toFloat()
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        binding.dsbShadowDx.onChanged = {
            ReadBookConfig.shadowDx = it.toFloat()
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        binding.dsbShadowDy.onChanged = {
            ReadBookConfig.shadowDy = it.toFloat()
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }

    private fun upView() = binding.run {
        ReadBookConfig.let {
            binding.dsbShadowRadius.progress = it.shadowRadius.toInt()
            binding.dsbShadowDx.progress = it.shadowDx.toInt()
            binding.dsbShadowDy.progress = it.shadowDy.toInt()
        }
    }

    private val callBack: CallBack?
        get() = (parentFragment as? CallBack) ?: (activity as? CallBack)

    interface CallBack {
        fun selectFont(path: String)
        val curFontPath: String
    }
}