package io.legado.app.feature.bubble

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.config.PackageResource
import java.io.File
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class BubblePackageManageUiState(
    val packages: ImmutableList<BubblePackageItem> = persistentListOf(),
    val activeDirName: String = BubblePackageManager.BUILTIN_DIR_NAME,
    val loading: Boolean = true,
    val draft: BubblePackageDraft? = null,
    val pendingDelete: BubblePackageItem? = null,
)

@Stable
data class BubblePackageItem(
    val dirName: String,
    val name: String,
    val builtin: Boolean,
    val sizeScale: Float,
    val resourceCount: Int,
)

/** 编辑中的气泡包；[dirName] 为空表示新建（含「复制内置包」的情形）。 */
@Stable
data class BubblePackageDraft(
    val dirName: String?,
    val name: String,
    val svgTemplate: String,
    val sizeScale: Float,
    val dayNormalColor: String,
    val dayEmphasisColor: String,
    val nightNormalColor: String,
    val nightEmphasisColor: String,
    val resources: List<PackageResource> = emptyList(),
    val resourceRoot: File? = null,
) {
    val isNew: Boolean get() = dirName == null
}

enum class BubbleColorSlot { DAY_NORMAL, DAY_EMPHASIS, NIGHT_NORMAL, NIGHT_EMPHASIS }

sealed interface BubblePackageManageIntent {
    data object Refresh : BubblePackageManageIntent
    data class Apply(val dirName: String) : BubblePackageManageIntent
    data class Edit(val dirName: String) : BubblePackageManageIntent
    data object Create : BubblePackageManageIntent
    data class Duplicate(val dirName: String) : BubblePackageManageIntent
    data class RequestDelete(val dirName: String) : BubblePackageManageIntent
    data object DismissDelete : BubblePackageManageIntent
    data object ConfirmDelete : BubblePackageManageIntent
    data class Export(val dirName: String) : BubblePackageManageIntent
    data object RequestImport : BubblePackageManageIntent
    data class DraftNameChanged(val value: String) : BubblePackageManageIntent
    data class DraftTemplateChanged(val value: String) : BubblePackageManageIntent
    data class DraftScaleChanged(val value: Float) : BubblePackageManageIntent
    data class DraftColorChanged(val slot: BubbleColorSlot, val value: String) : BubblePackageManageIntent
    data object SaveDraft : BubblePackageManageIntent
    data object DismissDraft : BubblePackageManageIntent
}

sealed interface BubblePackageManageEffect {
    data class ShareFile(val file: File) : BubblePackageManageEffect
    data class Message(@StringRes val textRes: Int) : BubblePackageManageEffect
    data object PickImportFile : BubblePackageManageEffect
}
