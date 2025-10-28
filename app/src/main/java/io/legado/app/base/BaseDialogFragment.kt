package io.legado.app.base

import android.app.Dialog
import android.content.DialogInterface
import android.content.DialogInterface.OnDismissListener
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext


abstract class BaseDialogFragment(
    @LayoutRes layoutID: Int,
    private val adaptationSoftKeyboard: Boolean = false
) : DialogFragment(layoutID) {

    private var onDismissListener: OnDismissListener? = null

    private lateinit var dialogView: View

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dialogView = onCreateView(layoutInflater, null, savedInstanceState)!!
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setView(dialogView)
            .create()
    }

    override fun getView(): View? = dialogView

    fun setOnDismissListener(onDismissListener: OnDismissListener?) {
        this.onDismissListener = onDismissListener
    }

    override fun onStart() {
        super.onStart()
//        if (adaptationSoftKeyboard) {
//            dialog?.window?.setBackgroundDrawableResource(R.color.transparent)
//        } else if (AppConfig.isEInkMode) {
//            dialog?.window?.let {
//                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
//                val attr = it.attributes
//                attr.dimAmount = 0.0f
//                attr.windowAnimations = 0
//                it.attributes = attr
//                it.decorView.setBackgroundKeepPadding(R.color.transparent)
//            }
//            // 修改gravity的时机一般在子类的onStart方法中, 因此需要在onStart之后执行.
//            lifecycle.addObserver(LifecycleEventObserver { _, event ->
//                if (event == Lifecycle.Event.ON_START) {
//                    when (dialog?.window?.attributes?.gravity) {
//                        Gravity.TOP -> view?.setBackgroundResource(R.drawable.bg_eink_border_bottom)
//                        Gravity.BOTTOM -> view?.setBackgroundResource(R.drawable.bg_eink_border_top)
//                        else -> {
//                            val padding = 2.dpToPx();
//                            view?.setPadding(padding, padding, padding, padding)
//                            view?.setBackgroundResource(R.drawable.bg_eink_border_dialog)
//                        }
//                    }
//                }
//            })
//        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (adaptationSoftKeyboard) {
            view.findViewById<View>(R.id.vw_bg)?.setOnClickListener(null)
            view.setOnClickListener { dismiss() }
        } else if (!AppConfig.isEInkMode) {
            //view.setBackgroundColor(ThemeStore.backgroundColor())
        }
        onFragmentCreated(view, savedInstanceState)
        observeLiveBus()
    }

    abstract fun onFragmentCreated(view: View, savedInstanceState: Bundle?)

    override fun show(manager: FragmentManager, tag: String?) {
        kotlin.runCatching {
            //在每个add事务前增加一个remove事务，防止连续的add
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        }.onFailure {
            AppLog.put("显示对话框失败 tag:$tag", it)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.onDismiss(dialog)
    }

    fun <T> execute(
        scope: CoroutineScope = lifecycleScope,
        context: CoroutineContext = Dispatchers.IO,
        block: suspend CoroutineScope.() -> T
    ) = Coroutine.async(scope, context) { block() }

    open fun observeLiveBus() {
    }
}