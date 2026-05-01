package io.legado.app.ui.book.cache

import android.content.Context
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.button.MaterialButton
import io.legado.app.R
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ItemDownloadBinding
import io.legado.app.help.book.isLocal
import io.legado.app.utils.gone
import io.legado.app.utils.visible

class CacheAdapter(context: Context, private val callBack: CallBack) :
    DiffRecyclerAdapter<Book, ItemDownloadBinding>(context) {

    override val diffItemCallback: DiffUtil.ItemCallback<Book>
        get() = object : DiffUtil.ItemCallback<Book>() {
            override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
                return oldItem.bookUrl == newItem.bookUrl
            }

            override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
                return oldItem.name == newItem.name
                        && oldItem.author == newItem.author
            }

        }

    override fun getViewBinding(parent: ViewGroup): ItemDownloadBinding {
        return ItemDownloadBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemDownloadBinding,
        item: Book,
        payloads: MutableList<Any>
    ) {
        binding.run {
            progressDownload.gone()
            progressExport.gone()
            tvMsg.gone()
            tvDownload.text = ""
            ivDownload.icon = null

            if (payloads.isEmpty()) {
                tvName.text = item.name
                tvAuthor.text = context.getString(R.string.author_show, item.getRealAuthor())

                if (item.isLocal) {
                    tvDownload.setText(R.string.local_book)
                } else {
                    val cs = callBack.cacheChapters[item.bookUrl]
                    tvDownload.text = if (cs == null) {
                        context.getString(R.string.loading)
                    } else {
                        context.getString(R.string.download_count, cs.size, item.totalChapterNum)
                    }
                }
            } else {
                if (item.isLocal) {
                    tvDownload.setText(R.string.local_book)
                } else {
                    val cacheSize = callBack.cacheChapters[item.bookUrl]?.size ?: 0
                    tvDownload.text = context.getString(R.string.download_count, cacheSize, item.totalChapterNum)
                    if (callBack.isBookDownloading(item.bookUrl)) {
                        val progress = if (item.totalChapterNum > 0) cacheSize * 100 / item.totalChapterNum else 0
                        progressDownload.progress = progress
                        progressDownload.visible()
                    }
                }
            }
            upDownloadIv(ivDownload, item)
            upExportInfo(tvMsg, progressExport, item)
        }
    }


    override fun registerListener(holder: ItemViewHolder, binding: ItemDownloadBinding) {
        binding.run {
            ivDownload.setOnClickListener {
                getItem(holder.layoutPosition)?.let { book ->
                    callBack.toggleBookDownload(book)
                }
            }
            tvExport.setOnClickListener {
                callBack.export(holder.layoutPosition)
            }
            ivDelete.setOnClickListener {
                getItem(holder.layoutPosition)?.let { book ->
                    callBack.deleteDownload(book)
                }
            }

        }
    }

    private fun upDownloadIv(button: MaterialButton, book: Book) {
        if (book.isLocal) {
            button.gone()
        } else {
            button.visible()
            val iconRes = if (callBack.isBookDownloading(book.bookUrl)) {
                R.drawable.ic_stop_black_24dp
            } else {
                R.drawable.ic_play
            }
            button.icon = ContextCompat.getDrawable(context, iconRes)
        }
    }

    private fun upExportInfo(msgView: TextView, progressView: ProgressBar, book: Book) {
        msgView.gone()
        progressView.gone()

        val msg = callBack.exportMsg(book.bookUrl)
        if (msg != null) {
            msgView.text = msg
            msgView.visible()
            return
        }
        val progress = callBack.exportProgress(book.bookUrl)
        if (progress != null) {
            progressView.max = book.totalChapterNum
            progressView.progress = progress
            progressView.visible()
        }
    }

    interface CallBack {
        val cacheChapters: Map<String, Set<String>>
        fun isBookDownloading(bookUrl: String): Boolean
        fun toggleBookDownload(book: Book)
        fun export(position: Int)
        fun exportProgress(bookUrl: String): Int?
        fun exportMsg(bookUrl: String): String?
        fun deleteDownload(book: Book)
    }

}
