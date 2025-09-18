package io.legato.kazusa.ui.book.toc

import android.content.Context
import android.view.ViewGroup
import io.legato.kazusa.base.adapter.ItemViewHolder
import io.legato.kazusa.base.adapter.RecyclerAdapter
import io.legato.kazusa.data.entities.Bookmark
import io.legato.kazusa.databinding.ItemBookmarkBinding
import io.legato.kazusa.utils.gone
import splitties.views.onClick
import splitties.views.onLongClick

class BookmarkAdapter(context: Context, val callback: Callback) :
    RecyclerAdapter<Bookmark, ItemBookmarkBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookmarkBinding {
        return ItemBookmarkBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookmarkBinding,
        item: Bookmark,
        payloads: MutableList<Any>
    ) {
        binding.tvChapterName.text = item.chapterName
        binding.tvBookText.gone(item.bookText.isEmpty())
        binding.tvBookText.text = item.bookText
        binding.tvContent.gone(item.content.isEmpty())
        binding.tvContent.text = item.content
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookmarkBinding) {
        binding.root.onLongClick {
            getItem(holder.layoutPosition)?.let { bookmark ->
                callback.onLongClick(bookmark, holder.layoutPosition)
            }
        }
        binding.root.onClick {
            getItem(holder.layoutPosition)?.let { bookmark ->
                callback.onClick(bookmark)
            }
        }
        binding.btnLocate.onClick {
            getItem(holder.layoutPosition)?.let { bookmark ->
                callback.onLocate(bookmark)
            }
        }
    }

    interface Callback {
        fun onClick(bookmark: Bookmark)
        fun onLongClick(bookmark: Bookmark, pos: Int)
        fun onLocate(bookmark: Bookmark)
    }

}