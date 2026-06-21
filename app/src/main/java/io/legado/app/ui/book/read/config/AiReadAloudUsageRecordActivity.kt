package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiReadAloudUsageRecord
import io.legado.app.databinding.ActivityThemeManageBinding
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.applyUiBodyTypefaceDeep
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.compose.ComposeActionListDialog
import io.legado.app.ui.widget.compose.ComposeConfirmDialog
import io.legado.app.utils.dpToPx
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiReadAloudUsageRecordActivity : BaseActivity<ActivityThemeManageBinding>() {

    override val binding by viewBinding(ActivityThemeManageBinding::inflate)

    private val adapter = UsageAdapter()
    private val selectedIds = linkedSetOf<Long>()
    private var records: List<AiReadAloudUsageRecord> = emptyList()
    private var typeFilter: String = ""
    private var currentBookOnly = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        load()
    }

    private fun initView() = binding.run {
        titleBar.title = "消耗记录"
        tabBar.visibility = View.GONE
        btnAdd.text = "全选"
        btnAdd.background = UiCorner.actionSelector(
            ContextCompat.getColor(this@AiReadAloudUsageRecordActivity, R.color.background_card),
            ContextCompat.getColor(this@AiReadAloudUsageRecordActivity, R.color.background_menu),
            UiCorner.actionRadius(this@AiReadAloudUsageRecordActivity)
        )
        btnAdd.setOnClickListener { toggleSelectAll() }
        tvSummary.setTextColor(secondaryTextColor)
        recyclerView.layoutManager = LinearLayoutManager(this@AiReadAloudUsageRecordActivity)
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        root.applyUiBodyTypefaceDeep(uiTypeface())
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_FILTER_TYPE, 0, "类型筛选").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_FILTER_BOOK, 1, "当前书/全部").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_DELETE_SELECTED, 2, "删除选中").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_CLEAR_ALL, 3, "清空全部").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_FILTER_TYPE -> showTypeFilter()
            MENU_FILTER_BOOK -> {
                currentBookOnly = !currentBookOnly
                selectedIds.clear()
                load()
            }
            MENU_DELETE_SELECTED -> deleteSelected()
            MENU_CLEAR_ALL -> confirmClearAll()
        }
        return true
    }

    private fun load() {
        lifecycleScope.launch {
            val bookUrl = if (currentBookOnly) ReadBook.book?.bookUrl.orEmpty() else ""
            val loaded = withContext(Dispatchers.IO) {
                appDb.aiReadAloudUsageRecordDao.list(typeFilter, bookUrl)
            }
            records = loaded
            selectedIds.retainAll(records.mapTo(hashSetOf()) { it.id })
            adapter.submit(records)
            updateSummary()
        }
    }

    private fun updateSummary() {
        val totalInput = records.sumOf { it.inputTokens }
        val totalCached = records.sumOf { it.cachedInputTokens }
        val totalOutput = records.sumOf { it.outputTokens }
        val total = records.sumOf { it.totalTokens.takeIf { value -> value > 0 } ?: (it.inputTokens + it.outputTokens) }
        val typeText = typeLabel(typeFilter).takeIf { typeFilter.isNotBlank() } ?: "全部类型"
        val bookText = if (currentBookOnly) "当前书" else "全部书籍"
        binding.tvSummary.text = "$typeText · $bookText · ${records.size} 条 · 输入 $totalInput · 缓存 $totalCached · 输出 $totalOutput · 总 $total · 已选 ${selectedIds.size}"
    }

    private fun showTypeFilter() {
        val types = listOf(
            "" to "全部",
            AiReadAloudUsageRecord.TYPE_ROLE to "多角色",
            AiReadAloudUsageRecord.TYPE_BGM to "配乐",
            AiReadAloudUsageRecord.TYPE_SFX to "音效",
            AiReadAloudUsageRecord.TYPE_AUDIO to "音频分析"
        )
        showDialogFragment(
            ComposeActionListDialog.create(
                title = "类型筛选",
                labels = types.map { if (it.first == typeFilter) "${it.second} ✓" else it.second },
                negativeText = getString(R.string.cancel),
                onSelected = { index ->
                    types.getOrNull(index)?.let { selected ->
                        typeFilter = selected.first
                        selectedIds.clear()
                        load()
                    }
                }
            )
        )
    }

    private fun toggleSelectAll() {
        if (records.isEmpty()) return
        if (selectedIds.size == records.size) {
            selectedIds.clear()
        } else {
            selectedIds.clear()
            records.mapTo(selectedIds) { it.id }
        }
        adapter.submit(records)
        updateSummary()
    }

    private fun deleteSelected() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) {
            toastOnUi("请先长按选择记录")
            return
        }
        showDialogFragment(
            ComposeConfirmDialog.create(
                title = "删除记录",
                message = "确定删除选中的 ${ids.size} 条消耗记录？",
                positiveText = getString(R.string.delete),
                negativeText = getString(R.string.cancel),
                dangerPositive = true,
                onPositive = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        appDb.aiReadAloudUsageRecordDao.deleteByIds(ids)
                        selectedIds.clear()
                        launch(Dispatchers.Main) { load() }
                    }
                }
            )
        )
    }

    private fun confirmClearAll() {
        showDialogFragment(
            ComposeConfirmDialog.create(
                title = "清空记录",
                message = "确定清空所有朗读 AI 消耗记录？",
                positiveText = getString(R.string.clear),
                negativeText = getString(R.string.cancel),
                dangerPositive = true,
                onPositive = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        appDb.aiReadAloudUsageRecordDao.clear()
                        selectedIds.clear()
                        launch(Dispatchers.Main) { load() }
                    }
                }
            )
        )
    }

    private fun showRecordActions(record: AiReadAloudUsageRecord) {
        showDialogFragment(
            ComposeActionListDialog.create(
                title = typeLabel(record.type),
                labels = listOf("删除"),
                dangerIndices = setOf(0),
                negativeText = getString(R.string.cancel),
                onSelected = { index ->
                    if (index == 0) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            appDb.aiReadAloudUsageRecordDao.delete(record.id)
                            selectedIds.remove(record.id)
                            launch(Dispatchers.Main) { load() }
                        }
                    }
                }
            )
        )
    }

    private inner class UsageAdapter : RecyclerView.Adapter<UsageHolder>() {
        private var items: List<AiReadAloudUsageRecord> = emptyList()

        fun submit(value: List<AiReadAloudUsageRecord>) {
            items = value
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsageHolder {
            val root = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dpToPx(), 12.dpToPx(), 14.dpToPx(), 12.dpToPx())
                background = UiCorner.actionSelector(
                    ContextCompat.getColor(parent.context, R.color.background_card),
                    ContextCompat.getColor(parent.context, R.color.background_menu),
                    UiCorner.panelRadius(parent.context)
                )
            }
            val title = TextView(parent.context).apply {
                textSize = 15f
                setTextColor(primaryTextColor)
                typeface = uiTypeface()
            }
            val sub = TextView(parent.context).apply {
                textSize = 12f
                setTextColor(secondaryTextColor)
                gravity = Gravity.START
            }
            root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(sub, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 4.dpToPx()
            })
            root.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dpToPx()
            }
            return UsageHolder(root, title, sub)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: UsageHolder, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class UsageHolder(
        itemView: View,
        private val title: TextView,
        private val sub: TextView
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(record: AiReadAloudUsageRecord) {
            val selected = record.id in selectedIds
            title.text = buildString {
                if (selected) append("✓ ")
                append(typeLabel(record.type))
                append(" · ")
                append(statusLabel(record.status))
                if (record.batchName.isNotBlank()) append(" · ").append(record.batchName)
            }
            sub.text = buildList {
                add(record.chapterTitle.ifBlank { record.bookName.ifBlank { "未知章节" } })
                add(formatTime(record.createdAt))
                if (record.modelId.isNotBlank()) add(record.modelId)
                add("输入 ${record.inputTokens}")
                if (record.cachedInputTokens > 0) add("缓存 ${record.cachedInputTokens}")
                add("输出 ${record.outputTokens}")
                add("总 ${record.totalTokens.takeIf { it > 0 } ?: (record.inputTokens + record.outputTokens)}")
                if (record.error.isNotBlank()) add(record.error)
            }.joinToString(" · ")
            itemView.setOnClickListener {
                if (selectedIds.isNotEmpty()) {
                    toggle(record.id)
                } else {
                    showRecordActions(record)
                }
            }
            itemView.setOnLongClickListener {
                toggle(record.id)
                true
            }
        }

        private fun toggle(id: Long) {
            if (!selectedIds.add(id)) selectedIds.remove(id)
            adapter.submit(records)
            updateSummary()
        }
    }

    companion object {
        private const val MENU_FILTER_TYPE = 1
        private const val MENU_FILTER_BOOK = 2
        private const val MENU_DELETE_SELECTED = 3
        private const val MENU_CLEAR_ALL = 4

        private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        private fun typeLabel(type: String): String = when (type) {
            AiReadAloudUsageRecord.TYPE_ROLE -> "多角色"
            AiReadAloudUsageRecord.TYPE_BGM -> "配乐"
            AiReadAloudUsageRecord.TYPE_SFX -> "音效"
            AiReadAloudUsageRecord.TYPE_AUDIO -> "音频分析"
            else -> "全部"
        }

        private fun statusLabel(status: String): String = when (status) {
            AiReadAloudUsageRecord.STATUS_SUCCESS -> "成功"
            AiReadAloudUsageRecord.STATUS_FAILED -> "失败"
            AiReadAloudUsageRecord.STATUS_CACHE -> "缓存"
            else -> status.ifBlank { "未知" }
        }

        private fun formatTime(time: Long): String {
            return if (time > 0) timeFormat.format(Date(time)) else "-"
        }
    }
}
