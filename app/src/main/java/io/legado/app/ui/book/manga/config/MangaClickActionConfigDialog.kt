package io.legado.app.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogClickActionConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.viewbindingdelegate.viewBinding

class MangaClickActionConfigDialog : BaseDialogFragment(R.layout.dialog_click_action_config) {
    private val binding by viewBinding(DialogClickActionConfigBinding::bind)

    private val actions by lazy {
        linkedMapOf(
            Pair(-1, getString(R.string.non_action)),
            Pair(0, getString(R.string.menu)),
            Pair(1, getString(R.string.next_page)),
            Pair(2, getString(R.string.prev_page)),
            Pair(3, getString(R.string.next_chapter)),
            Pair(4, getString(R.string.previous_chapter)),
        )
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.setBackgroundColor(getCompatColor(R.color.translucent))
        initData()
        initViewEvent()
    }

    private fun initData() = binding.run {
        tvTopLeft.text = actions[AppConfig.mangaClickActionTL]
        tvTopCenter.text = actions[AppConfig.mangaClickActionTC]
        tvTopRight.text = actions[AppConfig.mangaClickActionTR]
        tvMiddleLeft.text = actions[AppConfig.mangaClickActionML]
        tvMiddleCenter.text = actions[AppConfig.mangaClickActionMC]
        tvMiddleRight.text = actions[AppConfig.mangaClickActionMR]
        tvBottomLeft.text = actions[AppConfig.mangaClickActionBL]
        tvBottomCenter.text = actions[AppConfig.mangaClickActionBC]
        tvBottomRight.text = actions[AppConfig.mangaClickActionBR]
    }

    private fun initViewEvent() {
        binding.ivClose.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.tvTopLeft.setOnClickListener {
            selectAction { action ->
                putPrefInt(PreferKey.mangaClickActionTL, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvTopCenter.setOnClickListener {
            selectAction { action ->
                putPrefInt(PreferKey.mangaClickActionTC, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvTopRight.setOnClickListener {
            selectAction { action ->
                putPrefInt(PreferKey.mangaClickActionTR, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvMiddleLeft.setOnClickListener {
            selectAction { action ->
                putPrefInt(PreferKey.mangaClickActionML, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvMiddleCenter.setOnClickListener {
            selectAction { action ->
                putPrefInt(PreferKey.mangaClickActionMC, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvMiddleRight.setOnClickListener {
            selectAction { action ->
                putPrefInt(PreferKey.mangaClickActionMR, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvBottomLeft.setOnClickListener {
            selectAction { action ->
                putPrefInt(PreferKey.mangaClickActionBL, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvBottomCenter.setOnClickListener {
            selectAction { action ->
                putPrefInt(PreferKey.mangaClickActionBC, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvBottomRight.setOnClickListener {
            selectAction { action ->
                putPrefInt(PreferKey.mangaClickActionBR, action)
                (it as? TextView)?.text = actions[action]
            }
        }
    }

    private fun selectAction(success: (action: Int) -> Unit) {
        context?.selector(
            getString(R.string.select_action),
            actions.values.toList()
        ) { _, index ->
            success.invoke(actions.keys.toList()[index])
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppConfig.detectMangaClickArea()
    }
}

