package io.legado.app.ui.main.bookshelf.books.styleDefalut

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ItemBookshelfGridCompactBinding
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.gone

class BooksAdapterGridCover(context: Context, private val callBack: CallBack) :
    BaseBooksAdapter<ItemBookshelfGridCompactBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookshelfGridCompactBinding {
        return ItemBookshelfGridCompactBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookshelfGridCompactBinding,
        item: Book,
        payloads: MutableList<Any>
    ) = binding.run {
        if (payloads.isEmpty()) {
            tvName.gone()
            ivCover.load(item.getDisplayCover(), item.name, item.author, false, item.origin)
            upRefresh(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bundle.keySet().forEach {
                    when (it) {
                        "name" -> tvName.gone()
                        "cover" -> ivCover.load(
                            item.getDisplayCover(),
                            item.name,
                            item.author,
                            false,
                            item.origin
                        )

                        "refresh" -> upRefresh(binding, item)
                    }
                }
            }
        }
    }

    private fun upRefresh(binding: ItemBookshelfGridCompactBinding, item: Book) {
        if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
            binding.cdUnread.visibility = View.GONE
            binding.rlLoading.visibility = View.VISIBLE
        } else {
            binding.rlLoading.visibility = View.GONE
            if (AppConfig.showUnread) {
                val unreadCount = item.getUnreadChapterNum()
                if (unreadCount > 0) {
                    binding.cdUnread.visibility = View.VISIBLE
                    binding.tvUnread.text = unreadCount.toString()
                    if (AppConfig.showUnreadNew)
                        binding.newChapter.isVisible = item.lastCheckCount > 0
                } else {
                    binding.cdUnread.visibility = View.GONE
                }
            } else {
                binding.cdUnread.visibility = View.GONE
            }
        }
    }

    override fun registerListener(
        holder: ItemViewHolder,
        binding: ItemBookshelfGridCompactBinding
    ) {

        binding.cvContent.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.open(it, binding.cvContent)
            }
        }

        binding.cvContent.setOnLongClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.openBookInfo(it, binding.cvContent)
            }
            true
        }
    }
}