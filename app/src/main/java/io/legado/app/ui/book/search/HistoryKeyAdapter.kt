package io.legado.app.ui.book.search

import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.databinding.ItemSearchHistoryBinding
import io.legado.app.ui.widget.anima.explosion_field.ExplosionField
import splitties.views.onLongClick

class HistoryKeyAdapter(activity: SearchActivity, val callBack: CallBack) :
    RecyclerAdapter<SearchKeyword, ItemSearchHistoryBinding>(activity) {

    private val explosionField = ExplosionField.attach2Window(activity)

    init {
        setHasStableIds(true)
    }


    override fun getItemId(position: Int): Long {
        return getItem(position)!!.lastUseTime
    }

    override fun getViewBinding(parent: ViewGroup): ItemSearchHistoryBinding {
        return ItemSearchHistoryBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchHistoryBinding,
        item: SearchKeyword,
        payloads: MutableList<Any>
    ) {
        binding.run {
            textView.text = item.word
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchHistoryBinding) {

        holder.itemView.apply {
            setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    callBack.searchHistory(it.word)
                }
            }
            onLongClick {
                explosionField.explode(this, true)
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    callBack.deleteHistory(it)
                }
            }
        }
        binding.btnDelete.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let {
                callBack.deleteHistory(it)
            }
        }
    }

    interface CallBack {
        fun searchHistory(key: String)
        fun deleteHistory(searchKeyword: SearchKeyword)
    }
}