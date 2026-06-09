package io.legado.app.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.legado.app.R
import io.legado.app.base.BaseOverlayDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogClickActionConfigBinding
import io.legado.app.ui.config.readMangaConfig.ReadMangaConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.viewbindingdelegate.viewBinding

class MangaClickActionConfigDialog : BaseOverlayDialogFragment(R.layout.dialog_click_action_config) {
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
        tvTopLeft.text = actions[ReadMangaConfig.mangaClickActionTL]
        tvTopCenter.text = actions[ReadMangaConfig.mangaClickActionTC]
        tvTopRight.text = actions[ReadMangaConfig.mangaClickActionTR]
        tvMiddleLeft.text = actions[ReadMangaConfig.mangaClickActionML]
        tvMiddleCenter.text = actions[ReadMangaConfig.mangaClickActionMC]
        tvMiddleRight.text = actions[ReadMangaConfig.mangaClickActionMR]
        tvBottomLeft.text = actions[ReadMangaConfig.mangaClickActionBL]
        tvBottomCenter.text = actions[ReadMangaConfig.mangaClickActionBC]
        tvBottomRight.text = actions[ReadMangaConfig.mangaClickActionBR]
    }

    private fun initViewEvent() {
        binding.ivClose.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.tvTopLeft.setOnClickListener {
            selectAction { action ->
                setClickAction(PreferKey.mangaClickActionTL, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvTopCenter.setOnClickListener {
            selectAction { action ->
                setClickAction(PreferKey.mangaClickActionTC, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvTopRight.setOnClickListener {
            selectAction { action ->
                setClickAction(PreferKey.mangaClickActionTR, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvMiddleLeft.setOnClickListener {
            selectAction { action ->
                setClickAction(PreferKey.mangaClickActionML, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvMiddleCenter.setOnClickListener {
            selectAction { action ->
                setClickAction(PreferKey.mangaClickActionMC, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvMiddleRight.setOnClickListener {
            selectAction { action ->
                setClickAction(PreferKey.mangaClickActionMR, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvBottomLeft.setOnClickListener {
            selectAction { action ->
                setClickAction(PreferKey.mangaClickActionBL, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvBottomCenter.setOnClickListener {
            selectAction { action ->
                setClickAction(PreferKey.mangaClickActionBC, action)
                (it as? TextView)?.text = actions[action]
            }
        }

        binding.tvBottomRight.setOnClickListener {
            selectAction { action ->
                setClickAction(PreferKey.mangaClickActionBR, action)
                (it as? TextView)?.text = actions[action]
            }
        }
    }

    private fun setClickAction(key: String, action: Int) {
        when (key) {
            PreferKey.mangaClickActionTL -> ReadMangaConfig.mangaClickActionTL = action
            PreferKey.mangaClickActionTC -> ReadMangaConfig.mangaClickActionTC = action
            PreferKey.mangaClickActionTR -> ReadMangaConfig.mangaClickActionTR = action
            PreferKey.mangaClickActionML -> ReadMangaConfig.mangaClickActionML = action
            PreferKey.mangaClickActionMC -> ReadMangaConfig.mangaClickActionMC = action
            PreferKey.mangaClickActionMR -> ReadMangaConfig.mangaClickActionMR = action
            PreferKey.mangaClickActionBL -> ReadMangaConfig.mangaClickActionBL = action
            PreferKey.mangaClickActionBC -> ReadMangaConfig.mangaClickActionBC = action
            PreferKey.mangaClickActionBR -> ReadMangaConfig.mangaClickActionBR = action
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
        if (!hasMenuClickArea()) {
            ReadMangaConfig.detectMangaClickArea()
        }
        super.onDestroy()
    }

    private fun hasMenuClickArea(): Boolean {
        return ReadMangaConfig.mangaClickActionTL * ReadMangaConfig.mangaClickActionTC *
                ReadMangaConfig.mangaClickActionTR * ReadMangaConfig.mangaClickActionML *
                ReadMangaConfig.mangaClickActionMC * ReadMangaConfig.mangaClickActionMR *
                ReadMangaConfig.mangaClickActionBL * ReadMangaConfig.mangaClickActionBC *
                ReadMangaConfig.mangaClickActionBR == 0
    }
}
