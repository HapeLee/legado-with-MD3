package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.net.toUri
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.databinding.ItemTextBinding
import io.legado.app.databinding.PopupActionMenuBinding
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible

@SuppressLint("RestrictedApi")
class TextActionMenu(
    private val context: Context,
    private val callBack: CallBack,
    private val expandTextMenu: () -> Boolean
) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val binding = PopupActionMenuBinding.inflate(LayoutInflater.from(context))
    private val adapter = Adapter(context).apply {
        setHasStableIds(true)
    }

    private var menuItems: List<MenuItemImpl> = emptyList()
    private val visibleMenuItems = arrayListOf<MenuItemImpl>()
    private val moreMenuItems = arrayListOf<MenuItemImpl>()
    private val myMenu = MenuBuilder(context)

    init {
        @SuppressLint("InflateParams")
        contentView = binding.root

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false
        animationStyle = R.style.TextActionMenuAnimation

        SupportMenuInflater(context).inflate(R.menu.content_select_action, myMenu)
        binding.recyclerView.adapter = adapter
        binding.recyclerViewMore.adapter = adapter
        setOnDismissListener {
            if (!expandTextMenu()) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }
        binding.ivMenuMore.setOnClickListener {
            if (binding.recyclerView.isVisible) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_arrow_back)
                adapter.setItems(moreMenuItems)
                binding.recyclerView.gone()
                binding.recyclerViewMore.visible()
            } else {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }
        upMenu()
    }

    fun upMenu() {
        val otherMenu = MenuBuilder(context)
        onInitializeMenu(otherMenu)
        menuItems = myMenu.visibleItems + otherMenu.visibleItems
        visibleMenuItems.clear()
        moreMenuItems.clear()
        if (menuItems.size > 6) {
            visibleMenuItems.addAll(menuItems.subList(0, 6))
            moreMenuItems.addAll(menuItems.subList(6, menuItems.size))
        } else {
            visibleMenuItems.addAll(menuItems)
        }

        if (expandTextMenu()) {
            adapter.setItems(menuItems)
            binding.ivMenuMore.gone()
        } else {
            adapter.setItems(visibleMenuItems)
            binding.ivMenuMore.visible()
        }
    }

    fun show(
        view: View,
        windowHeight: Int,
        startX: Int,
        startTopY: Int,
        startBottomY: Int,
        endX: Int,
        endBottomY: Int
    ) {
        if (expandTextMenu()) {
            when {
                startTopY > 500 -> {
                    showAtLocation(
                        view, Gravity.BOTTOM or Gravity.START, startX, windowHeight - startTopY
                    )
                }

                endBottomY - startBottomY > 500 -> {
                    showAtLocation(view, Gravity.TOP or Gravity.START, startX, startBottomY)
                }

                else -> {
                    showAtLocation(view, Gravity.TOP or Gravity.START, endX, endBottomY)
                }
            }
        } else {
            contentView.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED,
            )
            val popupHeight = contentView.measuredHeight
            when {
                startBottomY > 500 -> {
                    showAtLocation(
                        view, Gravity.TOP or Gravity.START, startX, startTopY - popupHeight
                    )
                }

                endBottomY - startBottomY > 500 -> {
                    showAtLocation(
                        view, Gravity.TOP or Gravity.START, startX, startBottomY
                    )
                }

                else -> {
                    showAtLocation(
                        view, Gravity.TOP or Gravity.START, endX, endBottomY
                    )
                }
            }
        }
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<MenuItemImpl, ItemTextBinding>(context) {

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getViewBinding(parent: ViewGroup): ItemTextBinding {
            return ItemTextBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextBinding,
            item: MenuItemImpl,
            payloads: MutableList<Any>
        ) {
            with(binding) {
                textView.text = item.title
                val icon = item.icon
                if (icon != null && ReadConfig.showSelectMenuIcon) {
                    val size = 18.dpToPx()
                    icon.setBounds(0, 0, size, size)
                    textView.setCompoundDrawables(icon, null, null, null)
                    textView.compoundDrawablePadding = 6.dpToPx()
                } else {
                    textView.setCompoundDrawables(null, null, null, null)
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    if (!callBack.onMenuItemSelected(it.itemId)) {
                        onMenuItemSelected(it)
                    }
                }
                callBack.onMenuActionFinally()
            }
            holder.itemView.setOnLongClickListener {
                if (ReadConfig.contentSelectSpeakMod == 0) {
                    ReadConfig.contentSelectSpeakMod = 1
                    context.toastOnUi("切换为从选择的地方开始一直朗读")
                } else {
                    ReadConfig.contentSelectSpeakMod = 0
                    context.toastOnUi("切换为朗读选择内容")
                }
                true
            }
        }
    }

    private fun onMenuItemSelected(item: MenuItemImpl) {
        when (item.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_browser -> {
                kotlin.runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        Intent(Intent.ACTION_VIEW).apply {
                            data = callBack.selectedText.toUri()
                        }
                    } else {
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, callBack.selectedText)
                        }
                    }
                    context.startActivity(intent)
                }.onFailure {
                    it.printOnDebug()
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }

            else -> item.intent?.let {
                kotlin.runCatching {
                    it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                    context.startActivity(it)
                }.onFailure { e ->
                    AppLog.put("执行文本菜单操作出错\n$e", e, true)
                }
            }
        }
    }

    private fun createProcessTextIntent(): Intent {
        return Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
    }

    private fun getSupportedActivities(): List<ResolveInfo> {
        return context.packageManager
            .queryIntentActivities(createProcessTextIntent(), 0)
    }

    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent {
        return createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
    }

    /**
     * Start with a menu Item order value that is high enough
     * so that your "PROCESS_TEXT" menu items appear after the
     * standard selection menu items like Cut, Copy, Paste.
     */
    private fun onInitializeMenu(menu: Menu) {
        kotlin.runCatching {
            var menuItemOrder = 100
            val pm = context.packageManager
            val filterSet = ReadConfig.textSelectMenuFilter.split(",").filter { it.isNotEmpty() }.toSet()
            for (resolveInfo in getSupportedActivities()) {
                val componentName = "${resolveInfo.activityInfo.packageName}/${resolveInfo.activityInfo.name}"
                if (filterSet.contains(componentName)) {
                    continue
                }
                val menuItem = menu.add(
                    Menu.NONE, Menu.NONE,
                    menuItemOrder++, resolveInfo.loadLabel(pm)
                )
                menuItem.intent = createProcessTextIntentForResolveInfo(resolveInfo)
                if (ReadConfig.showSelectMenuIcon) {
                    menuItem.icon = kotlin.runCatching {
                        resolveInfo.loadIcon(pm)
                    }.getOrNull()
                }
            }
        }.onFailure {
            context.toastOnUi("获取文字操作菜单出错:${it.localizedMessage}")
        }
    }

    interface CallBack {
        val selectedText: String

        fun onMenuItemSelected(itemId: Int): Boolean

        fun onMenuActionFinally()
    }
}
