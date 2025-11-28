package io.legado.app.ui.book.search

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.R
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemSearchBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.model.BookShelfState
import io.legado.app.ui.widget.text.AccentBgTextView
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.visible


class SearchAdapter(context: Context, val callBack: CallBack) :
    DiffRecyclerAdapter<SearchBook, ItemSearchBinding>(context) {

    override val keepScrollPosition = true

    override val diffItemCallback: DiffUtil.ItemCallback<SearchBook>
        get() = object : DiffUtil.ItemCallback<SearchBook>() {

            override fun areItemsTheSame(oldItem: SearchBook, newItem: SearchBook): Boolean {
                return when {
                    oldItem.name != newItem.name -> false
                    oldItem.author != newItem.author -> false
                    else -> true
                }
            }

            override fun areContentsTheSame(oldItem: SearchBook, newItem: SearchBook): Boolean {
                return false
            }

            override fun getChangePayload(oldItem: SearchBook, newItem: SearchBook): Any {
                val payload = Bundle()
                payload.putInt("origins", newItem.origins.size)
                if (oldItem.coverUrl != newItem.coverUrl)
                    payload.putString("cover", newItem.coverUrl)
                if (oldItem.kind != newItem.kind)
                    payload.putString("kind", newItem.kind)
                if (oldItem.latestChapterTitle != newItem.latestChapterTitle)
                    payload.putString("last", newItem.latestChapterTitle)
                if (oldItem.intro != newItem.intro)
                    payload.putString("intro", newItem.intro)
                return payload
            }

        }

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

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchBinding) {
        binding.llContent.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.showBookInfo(it.name, it.author, it.bookUrl)
            }
        }
    }

    private fun bind(binding: ItemSearchBinding, searchBook: SearchBook) {
        binding.run {
            tvName.text = searchBook.name
            tvAuthor.text = context.getString(R.string.author_show, searchBook.author)
            when (callBack.getBookShelfState(searchBook.name, searchBook.author, searchBook.bookUrl)) {
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
            bvOriginCount.text = searchBook.origins.size.toString()
            upLasted(binding, searchBook.latestChapterTitle)
            tvIntroduce.text = searchBook.trimIntro(context)
            upKind(binding, searchBook.getKindList())
            ivCover.load(
                searchBook.coverUrl,
                searchBook.name,
                searchBook.author,
                AppConfig.loadCoverOnlyWifi,
                searchBook.origin
            )
        }
    }

    private fun bindChange(binding: ItemSearchBinding, searchBook: SearchBook, bundle: Bundle) {
        binding.run {
            bundle.keySet().forEach {
                when (it) {
                    "origins" -> bvOriginCount.text = searchBook.origins.size.toString()
                    "last" -> upLasted(binding, searchBook.latestChapterTitle)
                    "intro" -> tvIntroduce.text = searchBook.trimIntro(context)
                    "kind" -> upKind(binding, searchBook.getKindList())
                    "isInBookshelf" -> {
                        when (callBack.getBookShelfState(searchBook.name, searchBook.author, searchBook.bookUrl)) {
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
                    "cover" -> ivCover.load(
                        searchBook.coverUrl,
                        searchBook.name,
                        searchBook.author,
                        false,
                        searchBook.origin
                    )
                }
            }
        }
    }

    private fun upLasted(binding: ItemSearchBinding, latestChapterTitle: String?) {
        binding.run {
            if (latestChapterTitle.isNullOrEmpty()) {
                tvLasted.gone()
            } else {
                tvLasted.text =
                    context.getString(R.string.lasted_show, latestChapterTitle)
                tvLasted.visible()
            }
        }
    }

    private fun upKind(binding: ItemSearchBinding, kinds: List<String>) = binding.run {
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
                        marginEnd = 4.dpToPx()
                    }
                    textSize = 11f
                    text = kind
                    setLines(1)
                    setSingleLine(true)
                }
                kindContainer.addView(kindTextView)
            }
        }
    }

    interface CallBack {

        /**
         * 是否已经加入书架
         */
        fun getBookShelfState(name: String, author: String, url: String?): BookShelfState

        /**
         * 显示书籍详情
         */
        fun showBookInfo(name: String, author: String, bookUrl: String)
    }
}