package io.legato.kazusa.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.get
import com.github.liuyueyi.quick.transfer.constants.TransType
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.base.adapter.ItemViewHolder
import io.legato.kazusa.base.adapter.RecyclerAdapter
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.databinding.DialogReadBookStyleBinding
import io.legato.kazusa.databinding.ItemReadStyleBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.help.config.ReadBookConfig
import io.legato.kazusa.help.config.ThemeConfig
import io.legato.kazusa.lib.dialogs.alert
import io.legato.kazusa.model.ReadBook
import io.legato.kazusa.ui.book.read.ReadBookActivity
import io.legato.kazusa.utils.ChineseUtils
import io.legato.kazusa.utils.dpToPx
import io.legato.kazusa.utils.postEvent
import io.legato.kazusa.utils.showDialogFragment
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding

class ReadStyleDialog : BaseBottomSheetDialogFragment(R.layout.dialog_read_book_style),
    FontSelectDialog.CallBack {

    private val binding by viewBinding(DialogReadBookStyleBinding::bind)
    private val callBack get() = activity as? ReadBookActivity
    private lateinit var styleAdapter: StyleAdapter

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        initView()
        initData()
        initViewEvent()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun initView() = binding.run {
        if (AppConfig.isNightTheme) {
            tvDayNight.setIconResource(R.drawable.ic_daytime)
        } else {
            tvDayNight.setIconResource(R.drawable.ic_brightness)
        }
        dsbTextSize.valueFormat = {
            (it + 5).toString()
        }

        styleAdapter = StyleAdapter()
        rvStyle.adapter = styleAdapter
        styleAdapter.addFooterView {
            ItemReadStyleBinding.inflate(layoutInflater, it, false).apply {
                tvStyle.text = ""
                cdStyle.cardElevation = 2f.dpToPx()
                cdStyle.radius = 32f.dpToPx()
                cdStyle.strokeWidth = 0
                ivStyle.setImageResource(R.drawable.ic_add)
                root.setOnClickListener {
                    ReadBookConfig.configList.add(ReadBookConfig.Config())
                    showBgTextConfig(ReadBookConfig.configList.lastIndex)
                }
            }
        }


    }

    private fun initData() {
        upView()
        styleAdapter.setItems(ReadBookConfig.configList)
    }

    private fun initViewEvent() = binding.run {
        btnChineseConverter.setOnClickListener {
            alert(titleResource = R.string.chinese_converter) {
                items(resources.getStringArray(R.array.chinese_mode).toList()) { _, i ->
                    AppConfig.chineseConverterType = i

                    // 不需要更新文字，也不需要切换图标
                    ChineseUtils.unLoad(*TransType.entries.toTypedArray())
                    postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                }
            }
        }

        tvTextFont.setOnClickListener {
            showDialogFragment<FontSelectDialog>()
        }
        tvPadding.setOnClickListener {
            dismissAllowingStateLoss()
            callBack?.showPaddingConfig()
        }
        tvTip.setOnClickListener {
            TipConfigDialog().show(childFragmentManager, "tipConfigDialog")
        }
        tvMore.setOnClickListener {
            showDialogFragment<MoreConfigDialog>()
        }
        tvDayNight.setOnClickListener {
            AppConfig.isNightTheme = !AppConfig.isNightTheme
            ThemeConfig.applyDayNight(requireContext())
        }
//        rgPageAnim.setOnCheckedChangeListener { _, checkedId ->
//            ReadBook.book?.setPageAnim(-1)
//            ReadBookConfig.pageAnim = binding.rgPageAnim.getIndexById(checkedId)
//            callBack?.upPageAnim()
//            ReadBook.loadContent(false)
//        }
        binding.rgPageAnim.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            ReadBook.book?.setPageAnim(-1)
            ReadBookConfig.pageAnim = when (checkedId) {
                R.id.rb_anim0 -> 0  // 覆盖动画
                R.id.rb_anim1 -> 1  // 滑动动画
                R.id.rb_simulation_anim -> 2  // 仿真翻页
                R.id.rb_scroll_anim -> 3  // 滚动动画
                R.id.rb_no_anim -> 4  // 无动画
                else -> 0
            }
            callBack?.upPageAnim()
            ReadBook.loadContent(false)
        }

        dsbTextSize.onChanged = {
            ReadBookConfig.textSize = it + 5
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }

    private fun changeBgTextConfig(index: Int) {
        val oldIndex = ReadBookConfig.styleSelect
        if (index != oldIndex) {
            ReadBookConfig.styleSelect = index
            upView()
            styleAdapter.notifyItemChanged(oldIndex)
            styleAdapter.notifyItemChanged(index)
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            if (AppConfig.readBarStyleFollowPage) {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
        }
    }

    private fun showBgTextConfig(index: Int): Boolean {
        dismissAllowingStateLoss()
        changeBgTextConfig(index)
        callBack?.showBgTextConfig()
        return true
    }

    private fun upView() = binding.run {
        ReadBook.pageAnim().let {
            if (it >= 0 && it < rgPageAnim.childCount) {
                rgPageAnim.check(rgPageAnim[it].id)
            }
        }
        ReadBookConfig.let {
            dsbTextSize.progress = it.textSize - 5
        }
    }

    override val curFontPath: String
        get() = ReadBookConfig.textFont

    override fun selectFont(path: String) {
        if (path != ReadBookConfig.textFont || path.isEmpty()) {
            ReadBookConfig.textFont = path
            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        }
    }

    inner class StyleAdapter :
        RecyclerAdapter<ReadBookConfig.Config, ItemReadStyleBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): ItemReadStyleBinding {
            return ItemReadStyleBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemReadStyleBinding,
            item: ReadBookConfig.Config,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                tvStyle.text = item.name.ifBlank { "文字" }
                tvStyle.setTextColor(item.curTextColor())
                ivStyle.setImageDrawable(item.curBgDrawable(100, 150))
                if (ReadBookConfig.styleSelect == holder.layoutPosition) {
                    llStyle.gravity = Gravity.TOP
                    cdStyle.cardElevation = 1f.dpToPx()
                    cdStyle.radius = 16f.dpToPx()
                    cdStyle.strokeWidth = 1.dpToPx()
                    //cdStyle.strokeColor = item.curTextColor()
                    //tvStyle.setTextBold(true)
                } else {
                    cdStyle.cardElevation = 2f.dpToPx()
                    cdStyle.radius = 32f.dpToPx()
                    cdStyle.strokeWidth = 0
                    //cdStyle.strokeColor = item.curTextColor()
                    //tvStyle.setTextBold(false)
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemReadStyleBinding) {
            binding.apply {
                // ivStyle 现在是 MaterialCardView
                cdStyle.setOnClickListener {
                    changeBgTextConfig(holder.layoutPosition)
                }

                cdStyle.setOnLongClickListener {
                    showBgTextConfig(holder.layoutPosition)
                    true // 表示事件已消费
                }
            }
        }

    }
}