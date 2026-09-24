package io.legado.app.ui.widget.components.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.BookshelfConflict
import io.legado.app.domain.model.ConflictBookSummary
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.ui.book.changesource.ChangeSourceMigrationOptionsSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppRadioButton
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText

/**
 * [ChangeSourceMigrationOptions] 不是 Parcelable，默认 Saver 只认能塞进 Bundle 的类型，
 * 直接用 rememberSaveable 会在组合时抛 IllegalArgumentException。这里按字段逐个存取。
 *
 * 注意：给 [ChangeSourceMigrationOptions] 增加字段时，必须同步在这里补一个存取位，
 * 否则恢复出来的选项会静默丢失新字段的勾选状态。
 */
private val ChangeSourceMigrationOptionsSaver = listSaver<ChangeSourceMigrationOptions, Boolean>(
    save = {
        listOf(
            it.migrateChapters,
            it.migrateReadingProgress,
            it.migrateGroup,
            it.migrateCover,
            it.migrateCategory,
            it.migrateRemark,
            it.migrateReadConfig,
            it.deleteDownloadedChapters,
        )
    },
    restore = {
        ChangeSourceMigrationOptions(
            migrateChapters = it[0],
            migrateReadingProgress = it[1],
            migrateGroup = it[2],
            migrateCover = it[3],
            migrateCategory = it[4],
            migrateRemark = it[5],
            migrateReadConfig = it[6],
            deleteDownloadedChapters = it[7],
        )
    },
)

/**
 * 加入书架时发现疑似重复的处理 Sheet。
 *
 * 换源、批量换源、搜索/发现/首页加入书架共用这个组件：
 * - 卡片点击 → 打开书架已有作品的详情页；
 * - 多个候选时通过单选决定「对哪一本」执行操作，默认选中最近阅读过的那本；
 * - 底部「共存 / 迁移」复用换源的携带数据选项。
 */
@Composable
fun BookshelfConflictSheet(
    conflict: BookshelfConflict?,
    isResolving: Boolean = false,
    onDismissRequest: () -> Unit,
    onOpenExistingBook: (ConflictBookSummary) -> Unit,
    onCoexist: (existingBookUrl: String, options: ChangeSourceMigrationOptions) -> Unit,
    onMigrate: (existingBookUrl: String, options: ChangeSourceMigrationOptions) -> Unit,
) {
    val stateKey = conflict?.incoming?.bookUrl
    var options by rememberSaveable(stateKey, stateSaver = ChangeSourceMigrationOptionsSaver) {
        mutableStateOf(ChangeSourceMigrationOptions())
    }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var selectedBookUrl by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }

    AppModalBottomSheet(
        show = conflict != null,
        onDismissRequest = {
            // 关闭冲突 Sheet 时一并收起选项子 Sheet，否则会留下一个没有归属的「要携带的数据」面板。
            showOptions = false
            onDismissRequest()
        },
        title = stringResource(R.string.bookshelf_conflict_title),
        endAction = {
            MediumTonalButton(
                onClick = { showOptions = true },
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.setting)
            )
        },
    ) {
        val candidates = conflict?.candidates.orEmpty()
        if (conflict != null && candidates.isNotEmpty()) {
            val target = candidates.firstOrNull { it.bookUrl == selectedBookUrl }
                ?: candidates.first()
            AppText(
                text = stringResource(R.string.bookshelf_conflict_existing_title),
                style = LegadoTheme.typography.titleSmallEmphasized,
            )
            AppText(
                text = stringResource(R.string.bookshelf_conflict_open_hint),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(candidates, key = { it.bookUrl }) { candidate ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (candidates.size > 1) {
                            AppRadioButton(
                                selected = candidate.bookUrl == target.bookUrl,
                                onClick = { selectedBookUrl = candidate.bookUrl },
                            )
                        }
                        ConflictBookCard(
                            summary = candidate,
                            onClick = { onOpenExistingBook(candidate) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 这条说明解释的是下面两个按钮的行为，因此贴在按钮上方，而不是放进「要携带的数据」面板里。
            AppText(
                text = stringResource(R.string.bookshelf_conflict_actions_hint),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isResolving) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AppCircularProgressIndicator()
                }
            } else {
                ConfirmDismissButtonsRow(
                    onDismiss = { onCoexist(target.bookUrl, options) },
                    onConfirm = { onMigrate(target.bookUrl, options) },
                    dismissText = stringResource(R.string.bookshelf_conflict_coexist),
                    confirmText = stringResource(R.string.bookshelf_conflict_migrate),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    ChangeSourceMigrationOptionsSheet(
        // 没有冲突对象时不允许单独出现，避免选项面板脱离它所属的书籍。
        show = showOptions && conflict != null,
        title = stringResource(R.string.bookshelf_conflict_options_title),
        initialOptions = options,
        showDeleteDownloaded = false,
        onDismissRequest = { showOptions = false },
        onConfirm = {
            options = it
            showOptions = false
        }
    )
}

/** 「书架里已有的同作品」卡片：冲突 Sheet 与详情页的「书架操作」Sheet 共用。 */
@Composable
internal fun ConflictBookCard(
    summary: ConflictBookSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectionItemCard(
        title = summary.name,
        modifier = modifier,
        containerColor = LegadoTheme.colorScheme.onSheetContent,
        leadingContent = {
            CoilBookCover(
                name = summary.name,
                author = summary.author,
                path = summary.displayCover,
                sourceOrigin = summary.origin,
                bookUrl = summary.bookUrl,
                preferCache = true,
                modifier = Modifier
                    .width(52.dp)
                    .aspectRatio(5f / 7f),
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (summary.author.isNotBlank()) {
                    AppText(
                        text = summary.author,
                        style = LegadoTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppText(
                    text = summary.sourceName,
                    style = LegadoTheme.typography.labelMediumEmphasized,
                    color = LegadoTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = stringResource(R.string.all_chapter_num, summary.totalChapterNum),
                    style = LegadoTheme.typography.labelSmallEmphasized,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onToggleSelection = onClick,
    )
}
