package io.legado.app.ui.config

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemThemeConfigBinding
import io.legado.app.help.config.OldThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
//import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ThemeListDialog : BaseBottomSheetDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { Adapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        //setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        //binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.theme_list)
        initView()
        initMenu()
        initData()
    }

    private fun initView() = binding.run {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun initMenu() = binding.run {
        toolBar.setOnMenuItemClickListener(this@ThemeListDialog)
        toolBar.inflateMenu(R.menu.theme_list)
        toolBar.menu.applyTint(requireContext())
    }

    fun initData() {
        adapter.setItems(OldThemeConfig.configList)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_import -> {
                requireContext().getClipText()?.let {
                    if (OldThemeConfig.addConfig(it)) {
                        initData()
                    } else {
                        toastOnUi("格式不对,添加失败")
                    }
                }
            }
        }
        return true
    }

    fun delete(index: Int) {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                OldThemeConfig.delConfig(index)
                initData()
            }
            noButton()
        }
    }

    fun share(index: Int) {
        val json = GSON.toJson(OldThemeConfig.configList[index])
        requireContext().share(json, "主题分享")
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<OldThemeConfig.Config, ItemThemeConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemThemeConfigBinding {
            return ItemThemeConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemThemeConfigBinding,
            item: OldThemeConfig.Config,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                tvName.text = item.themeName
                if (item.primaryColor.toColorInt() == context.primaryColor)
                    cdRoot.setCardBackgroundColor(context.themeColor(com.google.android.material.R.attr.colorSecondaryContainer))
                else
                    cdRoot.setCardBackgroundColor(context.themeColor(com.google.android.material.R.attr.colorSurfaceContainer))
            }
        }


        override fun registerListener(holder: ItemViewHolder, binding: ItemThemeConfigBinding) {
            binding.apply {
                root.setOnClickListener {
                    OldThemeConfig.applyConfig(
                        context,
                        OldThemeConfig.configList[holder.layoutPosition]
                    )
                }
                ivShare.setOnClickListener {
                    share(holder.layoutPosition)
                }
                ivDelete.setOnClickListener {
                    delete(holder.layoutPosition)
                }
            }
        }

    }
}