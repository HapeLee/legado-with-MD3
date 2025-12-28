package io.legado.app.ui.main.my

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.ui.theme.AppTheme
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.FragmentMyConfigBinding
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.SwitchPreference
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.service.WebService
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.readRecord.ReadRecordActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.explore.ExploreFragment
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.utils.LogUtils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MyFragment : Fragment(), MainFragmentInterface {

    companion object {
        private const val ARG_POSITION = "position"

        fun newInstance(position: Int): MyFragment {
            return MyFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_POSITION, position)
                }
            }
        }
    }

    override val position: Int by lazy {
        arguments?.getInt(ARG_POSITION) ?: 0
    }

    private val viewModel: MyViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return ComposeView(requireContext()).apply {

            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )

            setContent {
                AppTheme{
                    MyScreen(
                        viewModel = viewModel,
                        onNavigate = { event ->
                            handleEvent(event)
                        }
                    )
                }
            }
        }
    }

    private fun handleEvent(event: PrefClickEvent) {
        when (event) {

            is PrefClickEvent.StartActivity -> {
                startActivity(
                    Intent(requireContext(), event.destination).apply {
                        event.configTag?.let {
                            putExtra("configTag", it)
                        }
                    }
                )
            }

            is PrefClickEvent.OpenUrl ->
                requireContext().openUrl(event.url)

            is PrefClickEvent.CopyUrl ->
                requireContext().sendToClip(event.url)

            is PrefClickEvent.ShowMd ->
                showHelp(event.title)

            PrefClickEvent.ExitApp ->
                requireActivity().finish()

            else -> Unit
        }
    }
}
