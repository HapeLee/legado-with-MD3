package io.legado.app.ui.book.explore

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemSearchBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.model.BookShelfState
import io.legado.app.ui.widget.text.AccentBgTextView
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.visible


class ExploreShowAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<SearchBook, ItemSearchBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemSearchBinding {
        return ItemSearchBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchBinding,
        item: SearchBook,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            bind(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bindChange(binding, item, bundle)
            }
        }

    }

    private fun bind(binding: ItemSearchBinding, item: SearchBook) {
        binding.run {
            tvName.text = item.name
            tvAuthor.text = context.getString(R.string.author_show, item.author)
            when (callBack.getBookShelfState(item.name, item.author, item.bookUrl)) {
                BookShelfState.IN_SHELF -> {
                    ivInBookshelf.isVisible = true
                    tvBookshelf.text = context.getString(R.string.remove_from_bookshelf)
                }
                BookShelfState.SAME_NAME_AUTHOR -> {
                    ivInBookshelf.isVisible = true
                    tvBookshelf.text = context.getString(R.string.same_name_book)
                }
                BookShelfState.NOT_IN_SHELF -> {
                    ivInBookshelf.isVisible = false
                }
            }
            if (item.latestChapterTitle.isNullOrEmpty()) {
                tvLasted.gone()
            } else {
                tvLasted.text = context.getString(R.string.lasted_show, item.latestChapterTitle)
                tvLasted.visible()
            }
            tvIntroduce.text = item.trimIntro(context)
            val kinds = item.getKindList()
            if (kinds.isEmpty()) {
                kindContainer.gone()
            } else {
                kindContainer.visible()
                kindContainer.removeAllViews()
                kinds.forEach { kind ->
                    val kindTextView = AccentBgTextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = 6.dpToPx()
                        }
                        textSize = 11f
                        text = kind
                        setLines(1)
                        setSingleLine(true)
                    }
                    kindContainer.addView(kindTextView)
                }
            }
            ivCover.load(
                item.coverUrl,
                item.name,
                item.author,
                AppConfig.loadCoverOnlyWifi,
                item.origin
            )
        }
    }

    private fun bindChange(binding: ItemSearchBinding, item: SearchBook, bundle: Bundle) {
        binding.run {
            bundle.keySet().forEach {
                when (it) {
                    "isInBookshelf" -> {
                        when (callBack.getBookShelfState(item.name, item.author, item.bookUrl)) {
                            BookShelfState.IN_SHELF -> {
                                ivInBookshelf.isVisible = true
                                tvBookshelf.text = context.getString(R.string.remove_from_bookshelf)
                            }
                            BookShelfState.SAME_NAME_AUTHOR -> {
                                ivInBookshelf.isVisible = true
                                tvBookshelf.text = context.getString(R.string.same_name_book)
                            }
                            BookShelfState.NOT_IN_SHELF -> {
                                ivInBookshelf.isVisible = false
                            }
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchBinding) {
        binding.llContent.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.showBookInfo(it.toBook())
            }
        }
    }

    interface CallBack {
        /**
         * 是否已经加入书架
         */
        fun getBookShelfState(name: String, author: String, url: String?): BookShelfState

        fun showBookInfo(book: Book)
    }
}