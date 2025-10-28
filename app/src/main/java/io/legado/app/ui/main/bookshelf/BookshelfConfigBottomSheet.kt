package io.legado.app.ui.main.bookshelf

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogBookshelfConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.bookshelfLayoutGrid
import io.legado.app.utils.postEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

class BookshelfConfigBottomSheet : BaseBottomSheetDialogFragment(R.layout.dialog_bookshelf_config) {

    private val binding by viewBinding(DialogBookshelfConfigBinding::bind)
    private val activityViewModel by activityViewModels<MainViewModel>()

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {

        val orientation = requireContext().resources.configuration.orientation

        val bookshelfLayout = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            AppConfig.bookshelfLayoutModeLandscape
        } else {
            AppConfig.bookshelfLayoutModePortrait
        }

        val isList = bookshelfLayout == 0
        val isGrid = bookshelfLayout == 1
        val isGridCompact = bookshelfLayout == 2
        val isGridCover = bookshelfLayout == 3
        val isListCompact = bookshelfLayout == 4

        val columnCount = requireContext().bookshelfLayoutGrid.takeIf { it > 0 } ?: 1

        val bookshelfSort = AppConfig.bookshelfSort

        binding.apply {

            resources.getStringArray(R.array.group_style).forEachIndexed { index, label ->
                val chip = Chip(context).apply {
                    text = label
                    isCheckable = true
                    isClickable = true
                    id = View.generateViewId()
                }
                chipGroupStyle.addView(chip)
                if (index == AppConfig.bookGroupStyle) {
                    chipGroupStyle.check(chip.id)
                }
            }

            resources.getStringArray(R.array.bookshelf_px_array).forEachIndexed { index, label ->
                val chip = Chip(context).apply {
                    text = label
                    isCheckable = true
                    isClickable = true
                    id = index
                }
                chipGroupSort.addView(chip)
                if (index == AppConfig.bookshelfSort) {
                    chipGroupSort.check(chip.id)
                }
            }

            swShowUnread.isChecked = AppConfig.showUnread
            swShowLastUpdateTime.isChecked = AppConfig.showLastUpdateTime
            swShowWaitUpBooks.isChecked = AppConfig.showWaitUpCount
            swShowBookshelfFastScroller.isChecked = AppConfig.showBookshelfFastScroller

            chipList.isChecked = isList
            chipListCompact.isChecked = isListCompact
            chipGrid.isChecked = isGrid
            chipGridCompact.isChecked = isGridCompact
            chipGridCover.isChecked = isGridCover
            sliderGridCount.progress = columnCount

            binding.tvValue.text = if (AppConfig.bookshelfRefreshingLimit <= 0) "无限制"
                                    else "${AppConfig.bookshelfRefreshingLimit} 本"

            binding.layoutRefreshLimit.setOnClickListener {
                NumberPickerDialog(requireContext())
                    .setTitle("书架更新数量限制")
                    .setMinValue(0) // 0 表示无限制
                    .setMaxValue(500) // 可根据需求设定最大值
                    .setValue(AppConfig.bookshelfRefreshingLimit)
                    .show { selected ->
                        AppConfig.bookshelfRefreshingLimit = selected
                        tvValue.text = if (selected <= 0) "无限制" else "$selected 本"
                    }
            }

            when (AppConfig.bookshelfSortOrder) {
                0 -> binding.chipGroupOrder.check(binding.chipAsc.id)
                1 -> binding.chipGroupOrder.check(binding.chipDesc.id)
            }

            btnOk.setOnClickListener {
                var notifyMain = false
                var recreate = false

                if (AppConfig.bookGroupStyle != chipGroupStyle.checkedChipId) {
                    AppConfig.bookGroupStyle = chipGroupStyle.indexOfChild(
                        chipGroupStyle.findViewById(chipGroupStyle.checkedChipId)
                    )
                    notifyMain = true
                }

                if (AppConfig.showUnread != swShowUnread.isChecked) {
                    AppConfig.showUnread = swShowUnread.isChecked
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }
                if (AppConfig.showLastUpdateTime != swShowLastUpdateTime.isChecked) {
                    AppConfig.showLastUpdateTime = swShowLastUpdateTime.isChecked
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }
                if (AppConfig.showWaitUpCount != swShowWaitUpBooks.isChecked) {
                    AppConfig.showWaitUpCount = swShowWaitUpBooks.isChecked
                    activityViewModel.postUpBooksLiveData(true)
                }
                if (AppConfig.showBookshelfFastScroller != swShowBookshelfFastScroller.isChecked) {
                    AppConfig.showBookshelfFastScroller = swShowBookshelfFastScroller.isChecked
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }

                if (bookshelfSort != chipGroupSort.checkedChipId) {
                    AppConfig.bookshelfSort = chipGroupSort.checkedChipId
                    (requireParentFragment() as? BaseBookshelfFragment)?.upSort()
                }

                val selectedColumn = sliderGridCount.progress
                val newLayout = when {
                    chipList.isChecked -> 0
                    chipGrid.isChecked -> 1
                    chipGridCompact.isChecked -> 2
                    chipGridCover.isChecked -> 3
                    chipListCompact.isChecked -> 4
                    else -> 0
                }

                val oldColumn = requireContext().bookshelfLayoutGrid
                if (bookshelfLayout != newLayout || oldColumn != selectedColumn) {
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        AppConfig.bookshelfLayoutModeLandscape = newLayout
                        AppConfig.bookshelfLayoutGridLandscape = selectedColumn
                    } else {
                        AppConfig.bookshelfLayoutModePortrait = newLayout
                        AppConfig.bookshelfLayoutGridPortrait = selectedColumn
                    }

                    if (newLayout == 0 || newLayout == 4) {
                        activityViewModel.booksGridRecycledViewPool.clear()
                    } else {
                        activityViewModel.booksListRecycledViewPool.clear()
                    }
                    recreate = true
                }

                val newOrder = when (binding.chipGroupOrder.checkedChipId) {
                    binding.chipAsc.id -> 0
                    binding.chipDesc.id -> 1
                    else -> 1
                }
                if (AppConfig.bookshelfSortOrder != newOrder) {
                    AppConfig.bookshelfSortOrder = newOrder
                    (requireParentFragment() as? BaseBookshelfFragment)?.upSort()
                }

                if (recreate) {
                    dismiss()
                    postEvent(EventBus.RECREATE, "")
                } else if (notifyMain) {
                    dismiss()
                    postEvent(EventBus.NOTIFY_MAIN, false)
                }
                dismiss()
            }

            btnCancel.setOnClickListener {
                dismiss()
            }
        }
    }
}
