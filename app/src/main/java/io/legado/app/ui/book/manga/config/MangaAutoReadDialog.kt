package io.legado.app.ui.book.manga.config

import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.databinding.DialogMangaAutoReadBinding
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.utils.viewbindingdelegate.viewBinding
import org.koin.android.ext.android.inject

class MangaAutoReadDialog : BaseBottomSheetDialogFragment(R.layout.dialog_manga_auto_read) {

    private val binding by viewBinding(DialogMangaAutoReadBinding::bind)
    private val mangaSettingsGateway: MangaSettingsGateway by inject()

    var initialAutoPageEnabled: Boolean = false

    var callback: Callback? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
    }

    private fun initView() {

        binding.chipAutoPage.run {
            isChecked = initialAutoPageEnabled
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onAutoPageToggle(isChecked)
                initAutoPageSpeed(isChecked)
            }
        }

        initAutoPageSpeed(initialAutoPageEnabled)
    }

    private fun initAutoPageSpeed(boolean: Boolean) {
        binding.scvAutoPageSpeed.run {
            isEnabled = boolean
            valueFormat = { "$it 秒" }
            progress = mangaSettingsGateway.currentSettings.autoPageSpeed

            onChanged = { newValue ->
                callback?.onAutoPageSpeedChanged(newValue)
            }
        }
    }

    interface Callback {
        fun onAutoPageToggle(enable: Boolean)
        fun onAutoPageSpeedChanged(speed: Int)
    }

}
