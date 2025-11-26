package io.legado.app.ui.main.bookshelf.books.styleDefalut

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ItemBookshelfListBinding
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.gone
import io.legado.app.utils.toTimeAgo
import io.legado.app.utils.visible
import splitties.views.onLongClick

class BooksAdapterList(
    context: Context,
    private val fragment: Fragment,
    private val callBack: CallBack,
    private val lifecycle: Lifecycle
) : BaseBooksAdapter<ItemBookshelfListBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookshelfListBinding {
        return ItemBookshelfListBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookshelfListBinding,
        item: Book,
        payloads: MutableList<Any>
    ) = binding.run {
        if (payloads.isEmpty()) {
            tvName.text = item.name
            tvAuthor.text = item.author
            tvRead.text = item.durChapterTitle
            tvLast.text = item.latestChapterTitle
            ivCover.load(item.getDisplayCover(), item.name, item.author, false, item.origin)
            upRefresh(binding, item)
            upLastUpdateTime(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bundle.keySet().forEach {
                    when (it) {
                        "name" -> tvName.text = item.name
                        "author" -> tvAuthor.text = item.author
                        "dur" -> tvRead.text = item.durChapterTitle
                        "last" -> tvLast.text = item.latestChapterTitle
                        "cover" -> ivCover.load(
                            item.getDisplayCover(),
                            item.name,
                            item.author,
                            false,
                            item.origin,
                            fragment,
                            lifecycle
                        )

                        "refresh" -> upRefresh(binding, item)
                        "lastUpdateTime" -> upLastUpdateTime(binding, item)
                    }
                }
            }
        }
    }

    private fun upRefresh(binding: ItemBookshelfListBinding, item: Book) {
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
                }
            } else {
                binding.cdUnread.gone()
            }
        }
    }

    private fun upLastUpdateTime(binding: ItemBookshelfListBinding, item: Book) {
        if (AppConfig.showLastUpdateTime && !item.isLocal) {
            val time = item.latestChapterTime.toTimeAgo()
            if (binding.tvLastUpdateTime.text != time) {
                binding.tvLastUpdateTime.text = time
                binding.tvLastUpdateTime.isVisible = true
            }
        } else {
            binding.tvLastUpdateTime.isVisible = false
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookshelfListBinding) {
        holder.itemView.apply {
            binding.cvContent.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.open(it, binding.cdCover)
                }
            }

            binding.cvContent.onLongClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.openBookInfo(it, binding.cdCover)
                }
            }
        }
    }
}
