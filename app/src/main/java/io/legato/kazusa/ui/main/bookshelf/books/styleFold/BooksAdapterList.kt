package io.legato.kazusa.ui.main.bookshelf.books.styleFold

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.legato.kazusa.R
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookGroup
import io.legato.kazusa.databinding.ItemBookshelfListBinding
import io.legato.kazusa.databinding.ItemBookshelfListGroupBinding
import io.legato.kazusa.help.book.isLocal
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.toTimeAgo
import io.legato.kazusa.utils.visible
import splitties.views.onLongClick

@Suppress("UNUSED_PARAMETER")
class BooksAdapterList(context: Context, callBack: CallBack) :
    BaseBooksAdapter<RecyclerView.ViewHolder>(context, callBack) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            1 -> GroupViewHolder(ItemBookshelfListGroupBinding.inflate(inflater, parent, false))
            else -> BookViewHolder(ItemBookshelfListBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        when (holder) {
            is BookViewHolder -> (getItem(position) as? Book)?.let {
                holder.registerListener(it)
                holder.onBind(it, payloads)
            }

            is GroupViewHolder -> (getItem(position) as? BookGroup)?.let {
                holder.registerListener(it)
                holder.onBind(it, payloads)
            }
        }
    }

    private fun getBooksForGroup(group: BookGroup): List<Book> {
        return if (group.groupId == -1L) {
            allBooks.take(4)
        } else {
            allBooks.filter { (it.group and group.groupId) != 0L }.take(4)
        }
    }

    inner class BookViewHolder(val binding: ItemBookshelfListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: Book) = binding.run {
            tvName.text = item.name
            tvAuthor.text = item.author
            tvRead.text = item.durChapterTitle
            tvLast.text = item.latestChapterTitle
            ivCover.load(item.getDisplayCover(), item.name, item.author, false, item.origin)
            cdUnread.visible()
//            ivAuthor.visible()
//            ivLast.visible()
//            ivRead.visible()
            upRefresh(this, item)
            upLastUpdateTime(binding, item)
        }

        fun onBind(item: Book, payloads: MutableList<Any>) = binding.run {
            if (payloads.isEmpty()) {
                onBind(item)
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
                                item.origin
                            )

                            "refresh" -> upRefresh(this, item)
                            "lastUpdateTime" -> upLastUpdateTime(binding, item)
                        }
                    }
                }
            }
        }

        private fun upLastUpdateTime(binding: ItemBookshelfListBinding, item: Book) {
            if (AppConfig.showLastUpdateTime && !item.isLocal) {
                val time = item.latestChapterTime.toTimeAgo()
                if (binding.tvLastUpdateTime.text != time) {
                    binding.tvLastUpdateTime.text = time
                    binding.tvLastUpdateTime.visible()
                }
            } else {
                binding.tvLastUpdateTime.gone()
            }
        }

        fun registerListener(item: Any) {
            binding.cvContent.setOnClickListener {
                callBack.onItemClick(item, binding.cdCover)
            }
            binding.cvContent.onLongClick {
                callBack.onItemLongClick(item, binding.cdCover)
            }
        }

        private fun upRefresh(binding: ItemBookshelfListBinding, item: Book) {
            if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
                binding.tvUnread.isVisible = false
                binding.rlLoading.isVisible = true
            } else {
                binding.rlLoading.isVisible = false
                if (AppConfig.showUnread) {
                    val unreadCount = item.getUnreadChapterNum()
                    if (unreadCount > 0) {
                        binding.cdUnread.visible()
                        binding.tvUnread.text = unreadCount.toString()
                    } else {
                        binding.cdUnread.gone()
                    }
                } else {
                    binding.cdUnread.gone()
                }
            }
        }

    }

    inner class GroupViewHolder(val binding: ItemBookshelfListGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: BookGroup) = binding.run {
            tvName.text = item.groupName
            loadGroupCover(item)
            cdUnread.gone()
        }


        fun onBind(item: BookGroup, payloads: MutableList<Any>) = binding.run {
            if (payloads.isEmpty()) {
                onBind(item)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "groupName" -> tvName.text = item.groupName
                            "cover" -> loadGroupCover(item)
                        }
                    }
                }
            }
        }

        fun registerListener(item: Any) {
            binding.cvContent.setOnClickListener {
                callBack.onItemClick(item, binding.ivCover)
            }
            binding.cvContent.onLongClick {
                callBack.onItemLongClick(item, binding.ivCover)
            }
        }

        private fun loadGroupCover(item: BookGroup) = binding.run {

            binding.ivCover.setImageDrawable(
                ContextCompat.getDrawable(binding.root.context, R.drawable.bg_surface_variant)
            )
            binding.ivCover1.setImageDrawable(null)
            binding.ivCover2.setImageDrawable(null)
            binding.ivCover3.setImageDrawable(null)
            binding.ivCover4.setImageDrawable(null)
            if (!item.cover.isNullOrEmpty()) {
                ivCover.load(item.cover)
            } else {

                val books = getBooksForGroup(item)

                if (books.size > 0) binding.ivCover1.load(books[0].getDisplayCover())
                if (books.size > 1) binding.ivCover2.load(books[1].getDisplayCover())
                if (books.size > 2) binding.ivCover3.load(books[2].getDisplayCover())
                if (books.size > 3) binding.ivCover4.load(books[3].getDisplayCover())
            }
        }

    }

}