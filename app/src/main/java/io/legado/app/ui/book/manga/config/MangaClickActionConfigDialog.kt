package io.legado.app.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseOverlayDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogClickActionConfigBinding
import io.legado.app.domain.gateway.MangaClickArea
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.MangaSettingsUpdate
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MangaClickActionConfigDialog : BaseOverlayDialogFragment(R.layout.dialog_click_action_config) {
    private val binding by viewBinding(DialogClickActionConfigBinding::bind)
    private val mangaSettingsGateway: MangaSettingsGateway by inject()

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
        tvTopLeft.text = actions[mangaSettingsGateway.currentSettings.clickActionTL]
        tvTopCenter.text = actions[mangaSettingsGateway.currentSettings.clickActionTC]
        tvTopRight.text = actions[mangaSettingsGateway.currentSettings.clickActionTR]
        tvMiddleLeft.text = actions[mangaSettingsGateway.currentSettings.clickActionML]
        tvMiddleCenter.text = actions[mangaSettingsGateway.currentSettings.clickActionMC]
        tvMiddleRight.text = actions[mangaSettingsGateway.currentSettings.clickActionMR]
        tvBottomLeft.text = actions[mangaSettingsGateway.currentSettings.clickActionBL]
        tvBottomCenter.text = actions[mangaSettingsGateway.currentSettings.clickActionBC]
        tvBottomRight.text = actions[mangaSettingsGateway.currentSettings.clickActionBR]
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
        val area = when (key) {
            PreferKey.mangaClickActionTL -> MangaClickArea.TL
            PreferKey.mangaClickActionTC -> MangaClickArea.TC
            PreferKey.mangaClickActionTR -> MangaClickArea.TR
            PreferKey.mangaClickActionML -> MangaClickArea.ML
            PreferKey.mangaClickActionMC -> MangaClickArea.MC
            PreferKey.mangaClickActionMR -> MangaClickArea.MR
            PreferKey.mangaClickActionBL -> MangaClickArea.BL
            PreferKey.mangaClickActionBC -> MangaClickArea.BC
            PreferKey.mangaClickActionBR -> MangaClickArea.BR
            else -> return
        }
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mangaSettingsGateway.update(MangaSettingsUpdate.ClickAction(area, action))
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
            lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                mangaSettingsGateway.update(
                    MangaSettingsUpdate.ClickAction(MangaClickArea.MC, 0)
                )
            }
            context?.toastOnUi("当前没有配置菜单区域,自动恢复中间区域为菜单.")
        }
        super.onDestroy()
    }

    private fun hasMenuClickArea(): Boolean {
        return mangaSettingsGateway.currentSettings.clickActionTL * mangaSettingsGateway.currentSettings.clickActionTC *
                mangaSettingsGateway.currentSettings.clickActionTR * mangaSettingsGateway.currentSettings.clickActionML *
                mangaSettingsGateway.currentSettings.clickActionMC * mangaSettingsGateway.currentSettings.clickActionMR *
                mangaSettingsGateway.currentSettings.clickActionBL * mangaSettingsGateway.currentSettings.clickActionBC *
                mangaSettingsGateway.currentSettings.clickActionBR == 0
    }
}
