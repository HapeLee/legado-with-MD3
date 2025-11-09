package io.legado.app.ui.book.searchContent

import android.content.Context
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemSearchListBinding
import io.legado.app.utils.themeColor


class SearchContentAdapter(context: Context, val callback: Callback) :
    RecyclerAdapter<SearchResult, ItemSearchListBinding>(context) {

    private val textColorInt = context.themeColor(com.google.android.material.R.attr.colorOnSurface)
    private val accentColorInt = context.themeColor(androidx.appcompat.R.attr.colorPrimary)
    private val bgColorInt = context.themeColor(com.google.android.material.R.attr.colorSecondaryContainer)

    override fun getViewBinding(parent: ViewGroup): ItemSearchListBinding {
        return ItemSearchListBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchListBinding,
        item: SearchResult,
        payloads: MutableList<Any>
    ) {
        binding.run {
            val isDur = callback.durChapterIndex() == item.chapterIndex
            if (payloads.isEmpty()) {
                tvTitle.text = item.getTitleSpannable(textColorInt)
                tvContent.text = item.getContentSpannable(textColorInt, accentColorInt, bgColorInt)

                cdRoot.setCardBackgroundColor(
                    if (isDur) context.themeColor(com.google.android.material.R.attr.colorSurfaceContainer)
                    else context.themeColor(com.google.android.material.R.attr.colorSurface)
                )
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchListBinding) {
        binding.cdRoot.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                if (it.query.isNotBlank()) callback.openSearchResult(it, holder.layoutPosition)
            }
        }
    }

    interface Callback {
        fun openSearchResult(searchResult: SearchResult, index: Int)
        fun durChapterIndex(): Int
    }
}
