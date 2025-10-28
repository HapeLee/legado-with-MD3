package io.legado.app.ui.book.read.config

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.databinding.DialogToolButtonConfigBinding
import io.legado.app.databinding.ItemToolButtonBinding
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ToolButtonConfigDialog : BaseBottomSheetDialogFragment(R.layout.dialog_tool_button_config) {

    private val binding by viewBinding(DialogToolButtonConfigBinding::bind)
    private val prefs by lazy {
        requireContext().getSharedPreferences(
            "tool_button_config",
            Context.MODE_PRIVATE
        )
    }

    private val callBack: CallBack? get() = activity as? CallBack

    private lateinit var adapter: ToolButtonAdapter

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        val configList = loadButtonConfig().toMutableList()

        adapter = ToolButtonAdapter(configList)
        recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val touchHelper = ItemTouchHelper(TouchHelperCallback(adapter))
        touchHelper.attachToRecyclerView(recyclerView)

        btnSave.setOnClickListener {
            saveButtonConfig(adapter.items)
            callBack?.refresh()
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as ReadBookActivity).bottomDialog--
    }

    data class ConfigEntry(val id: String, var enabled: Boolean)

    inner class ToolButtonAdapter(val items: MutableList<ConfigEntry>) :
        RecyclerView.Adapter<ToolButtonAdapter.VH>() {

        inner class VH(val binding: ItemToolButtonBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemToolButtonBinding.inflate(layoutInflater, parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]

            val (iconRes, name) = getButtonInfo(item.id)
            holder.binding.tvName.text = name
            holder.binding.ivIcon.setIconResource(iconRes)
            holder.binding.ivIcon.isEnabled = item.enabled
            holder.binding.btnDisable.icon = ContextCompat.getDrawable(
                holder.itemView.context,
                if (item.enabled) R.drawable.ic_visibility_on else R.drawable.ic_visibility_off
            )

            holder.binding.btnDisable.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                item.enabled = !item.enabled

                holder.binding.btnDisable.icon = ContextCompat.getDrawable(
                    holder.itemView.context,
                    if (item.enabled) R.drawable.ic_visibility_on else R.drawable.ic_visibility_off
                )

                if (!item.enabled) {
                    val removed = items.removeAt(pos)
                    items.add(removed)
                    notifyItemMoved(pos, items.size - 1)
                } else {
                    notifyItemChanged(pos)
                }
                holder.binding.ivIcon.isEnabled = item.enabled
            }
        }


        override fun getItemCount() = items.size

        fun swap(from: Int, to: Int) {
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return
            val fromItem = items[from]
            val toItem = items[to]
            if (!fromItem.enabled || !toItem.enabled) return

            items.add(to, items.removeAt(from))
            notifyItemMoved(from, to)
        }
    }

    private fun getAllButtonIds(): List<String> {
        return listOf("search", "auto_page", "catalog", "read_aloud", "setting", "addBookmark", "theme", "prev_chapter", "next_chapter")
    }

    private fun getButtonInfo(id: String): Pair<Int, String> {
        return when (id) {
            "search" -> R.drawable.ic_search to getString(R.string.search_content)
            "auto_page" -> R.drawable.ic_auto_page to getString(R.string.auto_next_page)
            "catalog" -> R.drawable.ic_toc to getString(R.string.chapter_list)
            "read_aloud" -> R.drawable.ic_read_aloud to getString(R.string.read_aloud)
            "setting" -> R.drawable.ic_settings to getString(R.string.setting)
            "addBookmark" -> R.drawable.ic_bookmark to getString(R.string.bookmark)
            "theme" -> R.drawable.ic_daytime to getString(R.string.day_night_switch)
            "prev_chapter" -> R.drawable.ic_previous to getString(R.string.previous_chapter)
            "next_chapter" -> R.drawable.ic_next to getString(R.string.next_chapter)
            else -> R.drawable.ic_help to id
        }
    }

    private fun loadButtonConfig(): List<ConfigEntry> {
        val str = prefs.getString("tool_buttons", null)

        return if (str.isNullOrBlank()) {
            getAllButtonIds().mapIndexed { index, id ->
                ConfigEntry(id, index < 5)
            }
        } else {
            val saved = str.split(";").mapNotNull {
                val parts = it.split(",")
                if (parts.size == 2) ConfigEntry(parts[0], parts[1].toBoolean()) else null
            }.toMutableList()

            val allIds = getAllButtonIds()
            for (id in allIds) {
                if (saved.none { it.id == id }) {
                    saved.add(ConfigEntry(id, true))
                }
            }

            saved
        }
    }

    private fun saveButtonConfig(list: List<ConfigEntry>) {
        val str = list.joinToString(";") { "${it.id},${it.enabled}" }
        prefs.edit { putString("tool_buttons", str) }
    }

    class TouchHelperCallback(
        private val adapter: ToolButtonAdapter
    ) : ItemTouchHelper.Callback() {

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val pos = viewHolder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return 0

            val item = adapter.items[pos]
            return if (!item.enabled) {
                0
            } else {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                makeMovementFlags(dragFlags, 0)
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPos = viewHolder.bindingAdapterPosition
            val toPos = target.bindingAdapterPosition
            if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false

            adapter.swap(fromPos, toPos)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        override fun isLongPressDragEnabled() = true
    }

    interface CallBack {
        fun refresh()
    }

}
