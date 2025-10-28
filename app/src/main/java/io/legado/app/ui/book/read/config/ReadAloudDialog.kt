package io.legado.app.ui.book.read.config

//import io.legado.app.lib.theme.bottomBackground
//import io.legado.app.lib.theme.getPrimaryTextColor
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogReadAloudBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible


class ReadAloudDialog : BaseBottomSheetDialogFragment(R.layout.dialog_read_aloud) {
    private val callBack: CallBack? get() = activity as? CallBack
    private val binding by viewBinding(DialogReadAloudBinding::bind)

    override fun onStart() {
        super.onStart()
//        dialog?.window?.run {
//            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
//            setBackgroundDrawableResource(R.color.background)
//            decorView.setPadding(0, 0, 0, 0)
//            val attr = attributes
//            attr.dimAmount = 0.0f
//            attr.gravity = Gravity.BOTTOM
//            attributes = attr
//            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
//        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as ReadBookActivity).bottomDialog--
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val bottomDialog = (activity as ReadBookActivity).bottomDialog++
        if (bottomDialog > 0) {
            dismiss()
            return
        }
        //val bg = requireContext().bottomBackground
        //val isLight = ColorUtils.isColorLight(bg)
        //val textColor = requireContext().getPrimaryTextColor(isLight)
        binding.run {
//            rootView.setBackgroundColor(bg)
//            tvPre.setTextColor(textColor)
//            tvNext.setTextColor(textColor)
//            ivPlayPrev.setColorFilter(textColor)
//            ivPlayPause.setColorFilter(textColor)
//            ivPlayNext.setColorFilter(textColor)
//            ivStop.setColorFilter(textColor)
//            ivTimer.setColorFilter(textColor)
//            tvTimer.setTextColor(textColor)
//            ivTtsSpeechReduce.setColorFilter(textColor)
//            tvTtsSpeed.setTextColor(textColor)
//            tvTtsSpeedValue.setTextColor(textColor)
//            ivTtsSpeechAdd.setColorFilter(textColor)
//            ivCatalog.setColorFilter(textColor)
//            tvCatalog.setTextColor(textColor)
//            ivMainMenu.setColorFilter(textColor)
//            tvMainMenu.setTextColor(textColor)
//            ivToBackstage.setColorFilter(textColor)
//            tvToBackstage.setTextColor(textColor)
//            ivSetting.setColorFilter(textColor)
//            tvSetting.setTextColor(textColor)
//            cbTtsFollowSys.setTextColor(textColor)
        }
        initData()
        initEvent()
    }

    private fun initData() = binding.run {
        upPlayState()
        upTimerText(BaseReadAloudService.timeMinute)
        cbTtsFollowSys.isChecked = requireContext().getPrefBoolean("ttsFollowSys", true)
        upTtsSpeechRateEnabled(!cbTtsFollowSys.isChecked)
        upSeekTimer()
    }

    private fun initEvent() = binding.run {
        ivMainMenu.setOnClickListener {
            callBack?.showMenuBar()
            dismissAllowingStateLoss()
        }
        ivSetting.setOnClickListener {
            ReadAloudConfigDialog().show(childFragmentManager, "readAloudConfigDialog")
        }
        tvPre.setOnClickListener { ReadBook.moveToPrevChapter(upContent = true, toLast = false) }
        tvNext.setOnClickListener { ReadBook.moveToNextChapter(true) }
        ivStop.setOnClickListener {
            ReadAloud.stop(requireContext())
            dismissAllowingStateLoss()
        }
        ivPlayPause.setOnClickListener { callBack?.onClickReadAloud() }
        ivPlayPrev.setOnClickListener { ReadAloud.prevParagraph(requireContext()) }
        ivPlayNext.setOnClickListener { ReadAloud.nextParagraph(requireContext()) }
        ivCatalog.setOnClickListener { callBack?.openChapterList() }
        ivToBackstage.setOnClickListener { callBack?.finish() }
        cbTtsFollowSys.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.ttsFlowSys = isChecked
            upTtsSpeechRateEnabled(!isChecked)
            upTtsSpeechRate()
        }

        ivTimer.setOnClickListener {
            AppConfig.ttsTimer = seekTimer.value.toInt()
            toastOnUi("保存设定时间成功！")
        }

        // 设置初始值
        seekTtsSpeechRate.value = AppConfig.ttsSpeechRate.toFloat()
        seekTimer.value = if (BaseReadAloudService.timeMinute > 0)
            BaseReadAloudService.timeMinute.toFloat()
        else AppConfig.ttsTimer.toFloat()

        // 减速按钮逻辑
        ivTtsSpeechReduce.setOnClickListener {
            val newValue = (seekTtsSpeechRate.value - 1).coerceAtLeast(seekTtsSpeechRate.valueFrom)
            seekTtsSpeechRate.value = newValue
            AppConfig.ttsSpeechRate = newValue.toInt()
            upTtsSpeechRateText(newValue.toInt())
            upTtsSpeechRate()
        }

        // 加速按钮逻辑
        ivTtsSpeechAdd.setOnClickListener {
            val newValue = (seekTtsSpeechRate.value + 1).coerceAtMost(seekTtsSpeechRate.valueTo)
            seekTtsSpeechRate.value = newValue
            AppConfig.ttsSpeechRate = newValue.toInt()
            upTtsSpeechRateText(newValue.toInt())
            upTtsSpeechRate()
        }

        btnTimer.setOnClickListener {
            val times = intArrayOf(0, 5, 10, 15, 30, 60, 90, 180)
            val timeKeys = times.map { "$it 分钟" }
            context?.selector("设定时间", timeKeys) { _, index ->
                ReadAloud.setTimer(requireContext(), times[index])
                upTimerText(times[index])
            }
        }

        //设置保存的默认值
        seekTtsSpeechRate.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                upTtsSpeechRateText(value.toInt())
            }
        }

        seekTtsSpeechRate.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                AppConfig.ttsSpeechRate = slider.value.toInt()
                upTtsSpeechRate()
            }
        })

        seekTimer.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                upTimerText(value.toInt())
            }
        }

        seekTimer.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                ReadAloud.setTimer(requireContext(), slider.value.toInt())
            }
        })

    }

    private fun upTtsSpeechRateEnabled(enabled: Boolean) {
        binding.run {
            upTtsSpeechRateText(AppConfig.ttsSpeechRate)
            tvTtsSpeedValue.visible(enabled)
            seekTtsSpeechRate.isEnabled = enabled
            ivTtsSpeechReduce.isEnabled = enabled
            ivTtsSpeechAdd.isEnabled = enabled
        }
    }

    private fun upPlayState() {
        if (!BaseReadAloudService.pause) {
            binding.ivPlayPause.icon =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_pause)
            binding.ivPlayPause.contentDescription = getString(R.string.pause)
        } else {
            binding.ivPlayPause.icon =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_play)
            binding.ivPlayPause.contentDescription = getString(R.string.audio_play)
        }

        // val bg = requireContext().bottomBackground
        // val isLight = ColorUtils.isColorLight(bg)
        // val textColor = requireContext().getPrimaryTextColor(isLight)
        // binding.ivPlayPause.iconTint = ColorStateList.valueOf(textColor)
    }

    private fun upSeekTimer() {
        binding.seekTimer.post {
            binding.seekTimer.value = if (BaseReadAloudService.timeMinute > 0) {
                BaseReadAloudService.timeMinute.toFloat()
            } else {
                AppConfig.ttsTimer.toFloat()
            }
        }
    }

    private fun upTimerText(timeMinute: Int) {
        if (timeMinute < 0) {
            binding.btnTimer.text = requireContext().getString(R.string.timer_m, 0)
        } else {
            binding.btnTimer.text = requireContext().getString(R.string.timer_m, timeMinute)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun upTtsSpeechRateText(value: Int) {
        binding.tvTtsSpeedValue.text = value.toString()
    }

    private fun upTtsSpeechRate() {
        ReadAloud.upTtsSpeechRate(requireContext())
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(requireContext())
            ReadAloud.resume(requireContext())
        }
    }

    override fun observeLiveBus() {
        observeEvent<Int>(EventBus.ALOUD_STATE) { upPlayState() }
        observeEvent<Int>(EventBus.READ_ALOUD_DS) {
            val value = it.coerceIn(binding.seekTimer.valueFrom.toInt(), binding.seekTimer.valueTo.toInt())
            binding.seekTimer.value = value.toFloat()
        }
    }

    interface CallBack {
        fun showMenuBar()
        fun openChapterList()
        fun onClickReadAloud()
        fun finish()
    }
}