package io.legado.app.ui.main.bookshelf.books.styleDefalut

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.isVisible
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ItemBookshelfGridBinding
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.gone
import io.legado.app.utils.visible

class BooksAdapterGrid(context: Context, private val callBack: CallBack) :
    BaseBooksAdapter<ItemBookshelfGridBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookshelfGridBinding {
        return ItemBookshelfGridBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookshelfGridBinding,
        item: Book,
        payloads: MutableList<Any>
    ) = binding.run {
        if (payloads.isEmpty()) {
            tvName.text = item.name
            ivCover.load(item.getDisplayCover(), item.name, item.author, false, item.origin)
            upRefresh(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bundle.keySet().forEach {
                    when (it) {
                        "name" -> tvName.text = item.name
                        "cover" -> ivCover.load(item.getDisplayCover(), item.name, item.author, false, item.origin)
                        "refresh" -> upRefresh(binding, item)
                    }
                }
            }
        }
    }

    private fun upRefresh(binding: ItemBookshelfGridBinding, item: Book) {
        if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
            binding.cdUnread.gone()
            binding.rlLoading.visible()
        } else {
            binding.rlLoading.gone()
            if (AppConfig.showUnread) {
                val unreadCount = item.getUnreadChapterNum()
                if (unreadCount > 0) {
                    binding.cdUnread.visible()
                    binding.tvUnread.text = unreadCount.toString()
                    if (AppConfig.showUnreadNew)
                        binding.newChapter.isVisible = item.lastCheckCount > 0
                } else {
                    binding.cdUnread.gone()
                    binding.newChapter.gone()
                }
            } else {
                binding.cdUnread.gone()
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookshelfGridBinding) {

        binding.cvContent.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.open(it, binding.cdCover)
            }
        }

        binding.cvContent.setOnLongClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.openBookInfo(it, binding.cdCover)
            }
            true
        }
    }
}