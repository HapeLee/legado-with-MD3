package io.legato.kazusa.ui.main.explore

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.card.MaterialCardView
import io.legato.kazusa.R
import io.legato.kazusa.base.adapter.ItemViewHolder
import io.legato.kazusa.base.adapter.RecyclerAdapter
import io.legato.kazusa.data.entities.BookSourcePart
import io.legato.kazusa.data.entities.rule.ExploreKind
import io.legato.kazusa.databinding.ItemFilletTextBinding
import io.legato.kazusa.databinding.ItemFindBookBinding
import io.legato.kazusa.help.coroutine.Coroutine
import io.legato.kazusa.help.source.clearExploreKindsCache
import io.legato.kazusa.help.source.exploreKinds
import io.legato.kazusa.ui.login.SourceLoginActivity
import io.legato.kazusa.ui.widget.dialog.TextDialog
import io.legato.kazusa.utils.activity
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.removeLastElement
import io.legato.kazusa.utils.showDialogFragment
import io.legato.kazusa.utils.startActivity
import io.legato.kazusa.utils.visible
import kotlinx.coroutines.CoroutineScope
import splitties.views.onLongClick

class ExploreAdapter(
    context: Context,
    private val callBack: CallBack
) : RecyclerAdapter<BookSourcePart, ItemFindBookBinding>(context) {

    private val recycler = arrayListOf<View>()
    private var expandedId: String? = null
    private var scrollToId: String? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position)?.bookSourceUrl.hashCode().toLong()
    }

    override fun getViewBinding(parent: ViewGroup): ItemFindBookBinding {
        return ItemFindBookBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemFindBookBinding,
        item: BookSourcePart,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (payloads.isEmpty()) {
                tvName.text = item.bookSourceName
            }

            val isExpanded = (item.bookSourceUrl == expandedId)

            if (isExpanded) {
                ivStatus.icon = ContextCompat.getDrawable(context, R.drawable.ic_arrow_right)
                ivStatus.rotation = 90f
                rotateLoading.visible()
                if (scrollToId == item.bookSourceUrl) {
                    val pos = holder.bindingAdapterPosition
                    if (pos >= 0) callBack.scrollTo(pos)
                    scrollToId = null
                }

                Coroutine.async(callBack.scope) {
                    item.exploreKinds()
                }.onSuccess { kindList ->
                    upKindList(flexbox, item.bookSourceUrl, kindList)
                }.onFinally {
                    rotateLoading.gone()
                }
            } else {
                ivStatus.icon = ContextCompat.getDrawable(context, R.drawable.ic_arrow_right)
                ivStatus.rotation = 0f
                recyclerFlexbox(flexbox)
                flexbox.gone()
            }
        }
    }

    private fun upKindList(flexbox: FlexboxLayout, sourceUrl: String, kinds: List<ExploreKind>) {
        if (kinds.isNotEmpty()) kotlin.runCatching {
            recyclerFlexbox(flexbox)
            flexbox.visible()
            kinds.forEach { kind ->
                val cardView = getFlexboxChild(flexbox)
                val textView = cardView.findViewById<TextView>(R.id.text_view)
                flexbox.addView(cardView)

                textView.text = kind.title
                kind.style().apply(cardView)

                if (kind.url.isNullOrBlank()) {
                    cardView.setOnClickListener(null)
                } else {
                    cardView.setOnClickListener {
                        if (kind.title.startsWith("ERROR:")) {
                            it.activity?.showDialogFragment(TextDialog("ERROR", kind.url))
                        } else {
                            callBack.openExplore(sourceUrl, kind.title, kind.url)
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    private fun getFlexboxChild(flexbox: FlexboxLayout): MaterialCardView {
        return if (recycler.isEmpty()) {
            ItemFilletTextBinding.inflate(inflater, flexbox, false).root
        } else {
            recycler.removeLastElement() as MaterialCardView
        }
    }

    @Synchronized
    private fun recyclerFlexbox(flexbox: FlexboxLayout) {
        recycler.addAll(flexbox.children)
        flexbox.removeAllViews()
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemFindBookBinding) {
        binding.apply {
            llTitle.setOnClickListener {
                val item = getItem(holder.bindingAdapterPosition) ?: return@setOnClickListener
                val oldId = expandedId
                expandedId = if (expandedId == item.bookSourceUrl) null else item.bookSourceUrl
                notifyItemChangedById(oldId)
                notifyItemChangedById(expandedId)

                if (expandedId != null) {
                    scrollToId = expandedId
                    callBack.scrollTo(holder.bindingAdapterPosition)
                }
            }
            llTitle.onLongClick {
                showMenu(ivStatus, holder.bindingAdapterPosition)
            }
        }
    }

    fun compressExplore(): Boolean {
        return if (expandedId == null) {
            false
        } else {
            val oldId = expandedId
            expandedId = null
            notifyItemChangedById(oldId)
            true
        }
    }
    private fun notifyItemChangedById(id: String?) {
        id ?: return
        for (pos in 0 until itemCount) {
            val item = getItem(pos) ?: continue
            if (item.bookSourceUrl == id) {
                notifyItemChanged(pos)
                break
            }
        }
    }
    private fun showMenu(view: View, position: Int) {
        val source = getItem(position) ?: return
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.explore_item)
        popupMenu.menu.findItem(R.id.menu_login).isVisible = source.hasLoginUrl
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_edit -> callBack.editSource(source.bookSourceUrl)
                R.id.menu_top -> callBack.toTop(source)
                R.id.menu_search -> callBack.searchBook(source)
                R.id.menu_login -> context.startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", source.bookSourceUrl)
                }
                R.id.menu_refresh -> Coroutine.async(callBack.scope) {
                    source.clearExploreKindsCache()
                }.onSuccess {
                    notifyItemChanged(position)
                }
                R.id.menu_del -> callBack.deleteSource(source)
            }
            true
        }
        popupMenu.show()
    }

    interface CallBack {
        val scope: CoroutineScope
        fun scrollTo(pos: Int)
        fun openExplore(sourceUrl: String, title: String, exploreUrl: String?)
        fun editSource(sourceUrl: String)
        fun toTop(source: BookSourcePart)
        fun deleteSource(source: BookSourcePart)
        fun searchBook(bookSource: BookSourcePart)
    }
}
