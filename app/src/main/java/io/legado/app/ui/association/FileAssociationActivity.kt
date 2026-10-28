package io.legado.app.ui.association

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.os.postDelayed
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.AppLog
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.ui.main.MainIntent
import io.legado.app.exception.InvalidBooksDirException
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.utils.FileUtils
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.canRead
import io.legado.app.utils.checkWrite
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.readUri
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

class FileAssociationActivity : BaseComposeActivity(transparent = true) {

    private val otherSettingsGateway by inject<OtherSettingsGateway>()

    private val viewModel by viewModels<FileAssociationViewModel>()

    private val loadingFlow = MutableStateFlow(true)
    private val showSelectDirFlow = MutableStateFlow(false)

    /** 待导入的书籍文件，等待用户选择保存目录 */
    private var pendingImportUri: Uri? = null

    private val openTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            val bookUri = pendingImportUri
            if (bookUri == null) {
                finish()
                return@registerForActivityResult
            }
            if (treeUri == null ||
                RealPathUtil.getTreePath(treeUri)?.startsWith(appCtx.externalFiles.parent!!) == true
            ) {
                // 取消选择或选到了应用私有目录：提示说明后直接按原方式导入
                toastStorageHelp()
                importBook(null, bookUri)
                return@registerForActivityResult
            }
            if (treeUri.isContentScheme()) {
                treeUri.takePersistablePermissionSafely(this)
            }
            lifecycleScope.launch {
                otherSettingsGateway.update {
                    it.copy(defaultBookTreeUri = treeUri.toString())
                }
            }
            importBook(treeUri, bookUri)
        }

    private val handler by lazy {
        buildMainHandler()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.importBookLiveData.observe(this) { uri ->
            importBook(uri)
        }
        viewModel.onLineImportLive.observe(this) {
            startActivity<OnLineImportActivity> {
                data = it
            }
            finish()
        }
        viewModel.successLive.observe(this) {
            when (it.first) {
                "bookSource" -> {
                    startActivity(MainActivity.createBookSourceManageIntent(this, it.second))
                    finish()
                }
                "rssSource" -> showDialogFragment(ImportRssSourceDialog(it.second, true))
                "replaceRule" -> showDialogFragment(ImportReplaceRuleDialog(it.second, true))
                "httpTts" -> showDialogFragment(ImportHttpTtsDialog(it.second, true))
                "theme" -> showDialogFragment(ImportThemeDialog(it.second, true))
                "txtRule" -> showDialogFragment(ImportTxtTocRuleDialog(it.second, true))
                "dictRule" -> showDialogFragment(ImportDictRuleDialog(it.second, true))
                "bubblePack" -> importBubblePack(Uri.parse(it.second))
            }
        }
        viewModel.errorLive.observe(this) {
            loadingFlow.value = false
            toastOnUi(it)
            handler.postDelayed(2000) {
                finish()
            }
        }
        viewModel.openBookLiveData.observe(this) {
            loadingFlow.value = false
            startActivityForBook(it)
            finish()
        }
        viewModel.notSupportedLiveData.observe(this) { data ->
            loadingFlow.value = false
            alert(
                title = appCtx.getString(R.string.draw),
                message = appCtx.getString(R.string.file_not_supported, data.second)
            ) {
                yesButton {
                    importBook(data.first)
                }
                noButton {
                    finish()
                }
                onCancelled {
                    finish()
                }
            }
        }
        intent.data?.let { data ->
            if (data.isContentScheme() && data.canRead()) {
                viewModel.dispatchIntent(data)
            } else {
                PermissionsCompat.Builder()
                    .addPermissions(*Permissions.Group.STORAGE)
                    .rationale(R.string.tip_perm_request_storage)
                    .onGranted {
                        viewModel.dispatchIntent(data)
                    }.onDenied {
                        toastOnUi("请求存储权限失败。")
                        handler.postDelayed(2000) {
                            finish()
                        }
                    }.request()
            }
        } ?: finish()
    }

    @Composable
    override fun Content() {
        val loading by loadingFlow.collectAsStateWithLifecycle()
        val showSelectDir by showSelectDirFlow.collectAsStateWithLifecycle()
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AppCircularProgressIndicator()
            }
        }
        FilePickerSheet(
            show = showSelectDir,
            onDismissRequest = { showSelectDirFlow.value = false },
            title = stringResource(R.string.select_book_folder),
            onSelectSysDir = {
                showSelectDirFlow.value = false
                openTreeLauncher.launch(null)
            },
        )
    }

    /**
     * 气泡包：URI 读写权限只在本 activity 手里（跨 activity 传 Uri 会丢授权），
     * 所以这里就地装好，再跳到段评气泡页让用户看到结果。
     */
    private fun importBubblePack(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                withContext(IO) {
                    BubblePackageManager.importZipUri(uri)
                }
            }.onSuccess { entry ->
                toastOnUi("已导入段评气泡「${entry.config.name}」")
                startActivity(MainIntent.createBubbleManageIntent(this@FileAssociationActivity))
                finish()
            }.onFailure {
                val msg = "导入段评气泡失败\n${it.localizedMessage}"
                AppLog.put(msg, it)
                toastOnUi(msg)
                handler.postDelayed(2000) {
                    finish()
                }
            }
        }
    }

    private fun toastStorageHelp() {
        val storageHelp = String(assets.open("storageHelp.md").readBytes())
        toastOnUi(storageHelp)
    }

    private fun importBook(uri: Uri) {
        if (uri.isContentScheme()) {
            val treeUriStr = otherSettingsGateway.currentSettings.defaultBookTreeUri
            if (treeUriStr.isNullOrEmpty()) {
                pendingImportUri = uri
                showSelectDirFlow.value = true
            } else {
                importBook(Uri.parse(treeUriStr), uri)
            }
        } else {
            importBook(null, uri)
        }
    }

    private fun importBook(treeUri: Uri?, uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                withContext(IO) {
                    if (treeUri == null) {
                        viewModel.importBook(uri)
                    } else if (treeUri.isContentScheme()) {
                        val treeDoc =
                            DocumentFile.fromTreeUri(this@FileAssociationActivity, treeUri)
                        if (!treeDoc!!.checkWrite()) {
                            throw InvalidBooksDirException(
                                "请重新设置书籍保存位置\nPermission Denial"
                            )
                        }
                        readUri(uri) { fileDoc, inputStream ->
                            val name = fileDoc.name
                            var doc = treeDoc.findFile(name)
                            if (doc == null || fileDoc.lastModified > doc.lastModified()) {
                                if (doc == null) {
                                    doc = treeDoc.createFile(FileUtils.getMimeType(name), name)
                                        ?: throw InvalidBooksDirException(
                                            "请重新设置书籍保存位置\nPermission Denial"
                                        )
                                }
                                contentResolver.openOutputStream(doc.uri)!!.use { oStream ->
                                    inputStream.copyTo(oStream)
                                    oStream.flush()
                                }
                            }
                            viewModel.importBook(doc.uri)
                        }
                    } else {
                        val treeFile = File(treeUri.path ?: treeUri.toString())
                        if (!treeFile.checkWrite()) {
                            throw InvalidBooksDirException(
                                "请重新设置书籍保存位置\nPermission Denial"
                            )
                        }
                        readUri(uri) { fileDoc, inputStream ->
                            val name = fileDoc.name
                            val file = treeFile.getFile(name)
                            if (!file.exists() || fileDoc.lastModified > file.lastModified()) {
                                FileOutputStream(file).use { oStream ->
                                    inputStream.copyTo(oStream)
                                    oStream.flush()
                                }
                            }
                            viewModel.importBook(Uri.fromFile(file))
                        }
                    }
                }
            }.onFailure {
                when (it) {
                    is InvalidBooksDirException -> {
                        pendingImportUri = uri
                        showSelectDirFlow.value = true
                    }

                    else -> {
                        val msg = "导入书籍失败\n${it.localizedMessage}"
                        AppLog.put(msg, it)
                        toastOnUi(msg)
                        handler.postDelayed(2000) {
                            finish()
                        }
                    }
                }
            }
        }
    }

}
