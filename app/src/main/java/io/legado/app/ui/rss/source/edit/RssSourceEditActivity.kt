package io.legado.app.ui.rss.source.edit

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.data.appDb
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.source.debug.RssSourceDebugActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

class RssSourceEditActivity : BaseComposeActivity(), VariableDialog.Callback {
 @Composable override fun Content(){val vm=koinViewModel<RssSourceEditViewModel>();val state=vm.uiState.collectAsStateWithLifecycle().value
  LaunchedEffect(Unit){vm.onIntent(RssSourceEditIntent.Load(intent.getStringExtra("sourceUrl")));vm.effects.collectLatest{e->when(e){
   is RssSourceEditEffect.Finish->{if(e.url.isNotEmpty())setResult(RESULT_OK,Intent().putExtra("origin",e.url));finish()}
   is RssSourceEditEffect.Debug->startActivity<RssSourceDebugActivity>{putExtra("key",e.url)}
   is RssSourceEditEffect.Login->startActivity<SourceLoginActivity>{putExtra("type","rssSource");putExtra("key",e.url)}
   is RssSourceEditEffect.Copy->sendToClip(e.text);is RssSourceEditEffect.Share->share(e.text);RssSourceEditEffect.ReadClipboard->{val t=getClipText();if(t.isNullOrBlank())toastOnUi("剪贴板为空")else vm.onIntent(RssSourceEditIntent.Import(t))}
      is RssSourceEditEffect.Variable -> openVariable(e.url); is RssSourceEditEffect.Message -> toastOnUi(
          e.text
      )
  }}}
  RssSourceEditScreen(state,vm::onIntent){vm.onIntent(RssSourceEditIntent.Back)}
 }
 private fun openVariable(url:String)=lifecycleScope.launch{val s=withContext(Dispatchers.IO){appDb.rssSourceDao.getByKey(url)}?:return@launch;showDialogFragment(VariableDialog(getString(R.string.set_source_variable),s.getKey(),withContext(Dispatchers.IO){s.getVariable()},s.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")))}
 override fun setVariable(key:String,variable:String?){lifecycleScope.launch(Dispatchers.IO){appDb.rssSourceDao.getByKey(key)?.setVariable(variable)}}
}
