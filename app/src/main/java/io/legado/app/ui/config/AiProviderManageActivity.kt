package io.legado.app.ui.config

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityAiProviderManageBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.ai.AiProviderConfig
import io.legado.app.ui.widget.compose.AppManagementCard
import io.legado.app.ui.widget.compose.AppManagementMenuAction
import io.legado.app.ui.widget.compose.AppManagementMoreActionButton
import io.legado.app.ui.widget.compose.AppManagementPalette
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.rememberAppManagementPalette
import io.legado.app.ui.widget.compose.showComposeConfirmDialog
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AiProviderManageActivity : BaseActivity<ActivityAiProviderManageBinding>() {

    override val binding by viewBinding(ActivityAiProviderManageBinding::inflate)
    private var providersState by mutableStateOf<List<AiProviderConfig>>(emptyList())
    private var modelCountsState by mutableStateOf<Map<String, Int>>(emptyMap())
    private var currentProviderIdState by mutableStateOf<String?>(null)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeRoot.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeRoot.setContent {
            AiProviderManageScreen(
                providers = providersState,
                modelCounts = modelCountsState,
                currentProviderId = currentProviderIdState,
                onBack = { finish() },
                onAdd = { openEdit(null) },
                onOpenProvider = { openEdit(it) },
                providerActions = ::providerActions
            )
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        providersState = AppConfig.aiProviderList
        currentProviderIdState = AppConfig.aiCurrentProviderId
        modelCountsState = AppConfig.aiProviderList.associate { provider ->
            provider.id to AppConfig.aiModelConfigList.count { it.providerId == provider.id }
        }
    }

    private fun openEdit(provider: AiProviderConfig?) {
        startActivity(Intent(this, AiProviderEditActivity::class.java).apply {
            provider?.id?.let { putExtra(AiProviderEditActivity.EXTRA_PROVIDER_ID, it) }
        })
    }

    private fun providerActions(provider: AiProviderConfig): List<AppManagementMenuAction> {
        return listOf(
            AppManagementMenuAction(getString(R.string.edit)) {
                openEdit(provider)
            },
            AppManagementMenuAction(
                text = getString(R.string.delete),
                danger = true,
                onClick = { confirmRemoveProvider(provider) }
            )
        )
    }

    private fun providerName(provider: AiProviderConfig): String {
        return provider.name.ifBlank {
            provider.baseUrl.ifBlank { getString(R.string.ai_provider) }
        }
    }

    private fun confirmRemoveProvider(provider: AiProviderConfig) {
        val relatedModelCount = AppConfig.aiModelConfigList.count { it.providerId == provider.id }
        showComposeConfirmDialog(
            title = providerName(provider),
            message = getString(
                if (relatedModelCount > 0) R.string.ai_remove_provider_confirm_with_models
                else R.string.ai_remove_provider_confirm,
                relatedModelCount
            ),
            positiveText = getString(R.string.delete),
            negativeText = getString(R.string.cancel),
            dangerPositive = true,
            onPositive = {
                AppConfig.aiProviderList = AppConfig.aiProviderList.filterNot { it.id == provider.id }
                notifyAiConfigChanged()
                reload()
                toastOnUi(R.string.ai_provider_removed)
            }
        )
    }

    private fun notifyAiConfigChanged() {
        postEvent(EventBus.AI_CONFIG_CHANGED, true)
    }
}

@Composable
private fun AiProviderManageScreen(
    providers: List<AiProviderConfig>,
    modelCounts: Map<String, Int>,
    currentProviderId: String?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenProvider: (AiProviderConfig) -> Unit,
    providerActions: (AiProviderConfig) -> List<AppManagementMenuAction>
) {
    val palette = rememberAppManagementPalette()
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = palette.settings.bodyFontFamily)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = palette.settings.page,
            contentColor = palette.settings.primaryText
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                AiProviderTopBar(onBack = onBack)
                Text(
                    text = stringResource(R.string.ai_provider_manage_summary),
                    color = palette.settings.secondaryText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 6.dp, end = 16.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (providers.isEmpty()) {
                        item {
                            AiProviderEmptyCard()
                        }
                    } else {
                        items(providers, key = { it.id }) { provider ->
                            AiProviderCard(
                                provider = provider,
                                modelCount = modelCounts[provider.id] ?: 0,
                                current = provider.id == currentProviderId,
                                onClick = { onOpenProvider(provider) },
                                moreActions = providerActions(provider)
                            )
                        }
                    }
                }
                LegadoMiuixActionButton(
                    text = stringResource(R.string.ai_add_provider),
                    palette = palette.miuix,
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    primary = true,
                    cornerRadius = palette.miuix.actionRadius,
                    minHeight = 46.dp
                )
            }
        }
    }
}

@Composable
private fun AiProviderTopBar(onBack: () -> Unit) {
    val palette = rememberAppManagementPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clickable(onClick = onBack),
            shape = RoundedCornerShape(palette.miuix.actionRadius ?: 12.dp),
            color = Color.Transparent,
            contentColor = palette.settings.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = palette.settings.primaryText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.ai_provider_manage_title),
            color = palette.settings.primaryText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = palette.settings.titleFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(42.dp))
    }
}

@Composable
private fun AiProviderCard(
    provider: AiProviderConfig,
    modelCount: Int,
    current: Boolean,
    onClick: () -> Unit,
    moreActions: List<AppManagementMenuAction>
) {
    val palette = rememberAppManagementPalette()
    AppManagementCard(
        palette = palette,
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick,
        insidePadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        color = palette.settings.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (current) {
                        AiProviderCurrentBadge(palette)
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = provider.baseUrl.ifBlank { "OpenAI compatible" },
                    color = palette.settings.secondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.ai_manage_models_summary, modelCount),
                    color = palette.settings.secondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(if (current) R.string.ai_current_provider else R.string.ai_provider),
                    color = if (current) palette.settings.accent else palette.settings.secondaryText,
                    fontSize = 12.sp,
                    fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            AppManagementMoreActionButton(
                actionsProvider = { moreActions },
                palette = palette,
                contentDescription = stringResource(R.string.more)
            )
        }
    }
}

@Composable
private fun AiProviderCurrentBadge(palette: AppManagementPalette) {
    Surface(
        shape = RoundedCornerShape(palette.miuix.actionRadius ?: 12.dp),
        color = palette.settings.accent.copy(alpha = 0.14f),
        contentColor = palette.settings.accent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = stringResource(R.string.ai_current_provider),
            color = palette.settings.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AiProviderEmptyCard() {
    val palette = rememberAppManagementPalette()
    AppManagementCard(
        palette = palette,
        modifier = Modifier.fillMaxWidth(),
        insidePadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp)
    ) {
        Text(
            text = stringResource(R.string.ai_current_provider_summary_empty),
            color = palette.settings.primaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.ai_add_provider_summary),
            color = palette.settings.secondaryText,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}
