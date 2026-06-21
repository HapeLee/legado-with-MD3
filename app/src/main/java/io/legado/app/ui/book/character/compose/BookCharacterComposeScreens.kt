package io.legado.app.ui.book.character.compose

import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.widget.compose.releaseComposeImage
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterRelation
import io.legado.app.help.character.BookCharacterProfileMeta
import io.legado.app.help.readaloud.speech.SpeechEmotion
import io.legado.app.help.readaloud.speech.SpeechRoute
import io.legado.app.help.readaloud.speech.SpeechVoiceEngineGroup
import io.legado.app.help.readaloud.speech.SpeechVoiceOption
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.composeActionRadius
import io.legado.app.lib.theme.composePanelRadius
import io.legado.app.ui.book.read.config.SpeechVoiceRoutePickerDialog
import io.legado.app.ui.book.read.config.speechRouteSummary
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.LegadoMiuixCard
import io.legado.app.ui.widget.compose.LegadoMiuixPalette
import io.legado.app.ui.widget.compose.LegadoMiuixSlider
import io.legado.app.utils.ColorUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
data class CharacterColors(
    val accent: Color,
    val page: Color,
    val card: Color,
    val cardAlt: Color,
    val text: Color,
    val subText: Color,
    val stroke: Color,
    val danger: Color
)

@Immutable
data class CharacterStyle(
    val colors: CharacterColors,
    val radius: androidx.compose.ui.unit.Dp = 22.dp,
    val smallRadius: androidx.compose.ui.unit.Dp = 14.dp
)

@Stable
@Composable
fun rememberCharacterStyle(): CharacterStyle {
    val context = LocalContext.current
    val night = AppConfig.isNightTheme
    val accent = context.accentColor
    val page = ContextCompat.getColor(context, if (night) R.color.md_grey_900 else R.color.white)
    val card = if (night) 0xff202329.toInt() else 0xfff7f8fb.toInt()
    val cardAlt = ColorUtils.blendColors(card, accent, if (night) 0.18f else 0.08f)
    val text = if (night) 0xfff2f3f5.toInt() else 0xff202124.toInt()
    val subText = if (night) 0xffaeb4bc.toInt() else 0xff6b7178.toInt()
    val stroke = if (night) 0x26ffffff else 0x17000000
    return CharacterStyle(
        colors = CharacterColors(
            accent = Color(accent),
            page = Color(page),
            card = Color(card),
            cardAlt = Color(cardAlt),
            text = Color(text),
            subText = Color(subText),
            stroke = Color(stroke),
            danger = Color(0xffe34f4f.toInt())
        ),
        radius = context.composePanelRadius(),
        smallRadius = context.composeActionRadius()
    )
}

private fun CharacterStyle.toCharacterMiuixPalette(): LegadoMiuixPalette {
    return LegadoMiuixPalette(
        accent = colors.accent,
        surface = colors.page,
        surfaceVariant = colors.cardAlt,
        primaryText = colors.text,
        secondaryText = colors.subText,
        danger = colors.danger
    )
}

data class CharacterEditDraft(
    val name: String = "",
    val avatar: String = "",
    val gender: String = BookCharacter.GENDER_UNKNOWN,
    val age: String = "",
    val roleLevel: Int = BookCharacter.ROLE_NORMAL,
    val identity: String = "",
    val skills: String = "",
    val attributes: String = "",
    val appearance: String = "",
    val personality: String = "",
    val biography: String = "",
    val speechRouteJson: String = ""
)

data class CharacterSpeechEngineUi(
    val group: SpeechVoiceEngineGroup
) {
    val key: String get() = group.key
    val name: String get() = group.title
    val subtitle: String get() = group.subtitle
    val engineType: String get() = group.engineType
    val engineValue: String get() = group.engineValue
    val options: List<SpeechVoiceOption> get() = group.options
    val emotions: List<SpeechEmotion> get() = group.emotions
}

data class RelationEditDraft(
    val id: Long = 0L,
    val fromCharacterId: Long = 0L,
    val toCharacterId: Long = 0L,
    val relationName: String = "",
    val relationType: String = "",
    val description: String = "",
    val strength: Int = 50,
    val sortOrder: Int = 0
)

fun BookCharacter.toDraft(): CharacterEditDraft = CharacterEditDraft(
    name = name,
    avatar = avatar,
    gender = gender,
    age = BookCharacterProfileMeta.ageOf(this),
    roleLevel = roleLevel,
    identity = identity,
    skills = skills,
    attributes = BookCharacterProfileMeta.attributesWithoutAge(attributes),
    appearance = appearance,
    personality = personality,
    biography = biography,
    speechRouteJson = speechRouteJson
)

fun BookCharacter.summaryText(): String = listOf(
    identity,
    skills,
    BookCharacterProfileMeta.attributesWithoutAge(attributes)
)
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .joinToString(" · ")
    .ifBlank { "点击查看角色卡片" }

@Composable
fun CharacterScaffold(
    title: String,
    subtitle: String = "",
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val style = rememberCharacterStyle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(style.colors.page)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("返回", color = style.colors.text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = style.colors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = style.colors.subText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
        }
        content()
    }
}

@Composable
fun CharacterManageScreen(
    bookName: String,
    characters: List<BookCharacter>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenCard: (BookCharacter) -> Unit,
    onEdit: (BookCharacter) -> Unit,
    onDelete: (BookCharacter) -> Unit,
    onOpenRelations: () -> Unit
) {
    val style = rememberCharacterStyle()
    val roleSections = remember(characters) { characterRoleSections(characters) }
    var selectedRoleLevel by remember(characters) {
        mutableStateOf(
            roleSections.firstOrNull { it.items.isNotEmpty() }?.roleLevel
                ?: BookCharacter.ROLE_MAIN
        )
    }
    val selectedSection = roleSections.firstOrNull { it.roleLevel == selectedRoleLevel }
        ?: roleSections.first()
    CharacterScaffold(
        title = "角色资料",
        subtitle = bookName,
        onBack = onBack,
        actions = {
            TextButton(onClick = onOpenRelations) { Text("关系网", color = style.colors.accent) }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 10.dp, 18.dp, 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    CharacterSummaryHeader(
                        count = characters.size,
                        onAdd = onAdd
                    )
                }
                if (characters.isEmpty()) {
                    item { EmptyCharacterCard("还没有角色，先添加主角或重要角色。") }
                } else if (selectedSection.items.isEmpty()) {
                    item(key = "section_${selectedSection.roleLevel}") {
                        CharacterRoleSectionHeader(selectedSection.title, 0)
                    }
                    item { EmptyCharacterCard("这个分类还没有角色。") }
                } else {
                    item(key = "section_${selectedSection.roleLevel}") {
                        CharacterRoleSectionHeader(selectedSection.title, selectedSection.items.size)
                    }
                    items(selectedSection.items, key = { it.id }) { character ->
                        CharacterListCard(
                            character = character,
                            onClick = { onOpenCard(character) },
                            onEdit = { onEdit(character) },
                            onDelete = { onDelete(character) }
                        )
                    }
                }
            }
            CharacterRoleBottomTabs(
                sections = roleSections,
                selectedRoleLevel = selectedRoleLevel,
                onSelect = { selectedRoleLevel = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private data class CharacterRoleSection(
    val roleLevel: Int,
    val title: String,
    val items: List<BookCharacter>
)

private fun characterRoleSections(characters: List<BookCharacter>): List<CharacterRoleSection> {
    return listOf(
        CharacterRoleSection(
            BookCharacter.ROLE_MAIN,
            "主角",
            characters.filter { it.roleLevel == BookCharacter.ROLE_MAIN }
        ),
        CharacterRoleSection(
            BookCharacter.ROLE_IMPORTANT,
            "重要角色",
            characters.filter { it.roleLevel == BookCharacter.ROLE_IMPORTANT }
        ),
        CharacterRoleSection(
            BookCharacter.ROLE_NORMAL,
            "普通角色",
            characters.filter { it.roleLevel != BookCharacter.ROLE_MAIN && it.roleLevel != BookCharacter.ROLE_IMPORTANT }
        )
    )
}

@Composable
private fun CharacterRoleBottomTabs(
    sections: List<CharacterRoleSection>,
    selectedRoleLevel: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = rememberCharacterStyle()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(12.dp, RoundedCornerShape(style.radius), clip = false),
        color = style.colors.card,
        shape = RoundedCornerShape(style.radius)
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sections.forEach { section ->
                val selected = section.roleLevel == selectedRoleLevel
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(style.smallRadius))
                        .clickable { onSelect(section.roleLevel) },
                    color = if (selected) style.colors.accent.copy(alpha = 0.16f) else Color.Transparent,
                    shape = RoundedCornerShape(style.smallRadius)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = section.title,
                            color = if (selected) style.colors.accent else style.colors.text,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${section.items.size}",
                            color = style.colors.subText,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterRoleSectionHeader(title: String, count: Int) {
    val style = rememberCharacterStyle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = style.colors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text("$count 个", color = style.colors.subText, fontSize = 12.sp)
    }
}

@Composable
private fun CharacterSummaryHeader(count: Int, onAdd: () -> Unit) {
    val style = rememberCharacterStyle()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(style.radius), clip = false),
        color = style.colors.cardAlt,
        shape = RoundedCornerShape(style.radius)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("本书角色库", color = style.colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (count == 0) "角色卡会只绑定当前书籍" else "已记录 $count 个角色",
                    color = style.colors.subText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = style.colors.accent)
            ) {
                Text("添加")
            }
        }
    }
}

@Composable
fun CharacterListCard(
    character: BookCharacter,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val style = rememberCharacterStyle()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.radius))
            .clickable(onClick = onClick),
        color = style.colors.card,
        shape = RoundedCornerShape(style.radius),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CharacterAvatar(character.avatar, character.displayName(), 64)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = character.displayName(),
                        color = style.colors.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    RolePill(character.roleLabel(), compact = true)
                }
                Text(
                    text = character.summaryText(),
                    color = style.colors.subText,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp)
                )
                val age = BookCharacterProfileMeta.ageOf(character)
                val meta = listOf(character.genderLabel(), age, character.roleLabel())
                    .filter { it.isNotBlank() && it != "未知" }
                    .joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = style.colors.subText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
            var expanded by remember { mutableStateOf(false) }
            TextButton(onClick = { expanded = true }) {
                Text("更多", color = style.colors.subText)
            }
            if (expanded) {
                CharacterMoreDialog(
                    character = character,
                    onDismiss = { expanded = false },
                    onEdit = {
                        expanded = false
                        onEdit()
                    },
                    onDelete = {
                        expanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun CharacterMoreDialog(
    character: BookCharacter,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val style = rememberCharacterStyle()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(style.radius),
        containerColor = style.colors.card,
        title = { Text(character.displayName(), color = style.colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoreActionRow("编辑角色", "修改头像、身份、技能和生平", onEdit)
                MoreActionRow("删除角色", "同时删除与此角色相关的关系", onDelete, danger = true)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = style.colors.accent) } }
    )
}

@Composable
private fun MoreActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val style = rememberCharacterStyle()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (danger) style.colors.danger.copy(alpha = 0.09f) else style.colors.page,
        shape = RoundedCornerShape(style.smallRadius)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = if (danger) style.colors.danger else style.colors.text, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = style.colors.subText, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
fun CharacterCardScreen(
    character: BookCharacter?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    speechEngines: List<CharacterSpeechEngineUi> = emptyList(),
    onSpeechRouteChange: (String) -> Unit = {}
) {
    val style = rememberCharacterStyle()
    CharacterScaffold(
        title = "角色卡片",
        subtitle = character?.displayName().orEmpty(),
        onBack = onBack,
        actions = {
            TextButton(onClick = onEdit, enabled = character != null) {
                Text("编辑", color = if (character == null) style.colors.subText else style.colors.accent)
            }
        }
    ) {
        if (character == null) {
            EmptyCharacterCard("角色不存在")
            return@CharacterScaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 10.dp, 18.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { CharacterHeroCard(character, speechEngines, onSpeechRouteChange) }
            item { CharacterSection("身份", character.identity) }
            item { CharacterSection("技能", character.skills, minHeight = 112.dp) }
            item { CharacterSection("属性", character.attributes, minHeight = 112.dp) }
            item { CharacterSection("形象描述", character.appearance) }
            item { CharacterSection("性格描述", character.personality) }
            item { CharacterSection("角色生平", character.biography) }
        }
    }
}

@Composable
private fun CharacterHeroCard(
    character: BookCharacter,
    speechEngines: List<CharacterSpeechEngineUi>,
    onSpeechRouteChange: (String) -> Unit
) {
    val style = rememberCharacterStyle()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.radius))
            .background(
                Brush.linearGradient(
                    listOf(
                        style.colors.cardAlt,
                        style.colors.card,
                        style.colors.page
                    )
                )
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = style.colors.page.copy(alpha = 0.72f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, style.colors.stroke)
                ) {
                    CharacterAvatar(character.avatar, character.displayName(), 96)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = character.displayName(),
                        color = style.colors.text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RolePill(character.roleLabel(), compact = false)
                        InfoPill(character.genderLabel(), maxWidth = 72.dp)
                        BookCharacterProfileMeta.ageOf(character).takeIf { it.isNotBlank() }?.let {
                            InfoPill(it, maxWidth = 88.dp)
                        }
                        character.identity.takeIf { it.isNotBlank() }?.let {
                            InfoPill(it, maxWidth = 150.dp)
                        }
                    }
                }
            }
            val highlights = listOf(
                "技能" to character.skills,
                "属性" to BookCharacterProfileMeta.attributesWithoutAge(character.attributes)
            ).filter { it.second.isNotBlank() }
            if (highlights.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    highlights.forEach { (label, value) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            color = style.colors.page.copy(alpha = 0.58f),
                            shape = RoundedCornerShape(style.smallRadius),
                            border = androidx.compose.foundation.BorderStroke(1.dp, style.colors.stroke)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(label, color = style.colors.subText, fontSize = 12.sp)
                                Text(
                                    value,
                                    color = style.colors.text,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
            CharacterSpeechRouteQuickSwitch(
                speechRouteJson = character.speechRouteJson,
                speechEngines = speechEngines,
                onChange = onSpeechRouteChange
            )
        }
    }
}

@Composable
private fun CharacterSpeechRouteQuickSwitch(
    speechRouteJson: String,
    speechEngines: List<CharacterSpeechEngineUi>,
    onChange: (String) -> Unit
) {
    val style = rememberCharacterStyle()
    val route = remember(speechRouteJson) { SpeechRoute.fromJson(speechRouteJson) }
    val groups = remember(speechEngines) { speechEngines.map { it.group } }
    var pickerVisible by remember { mutableStateOf(false) }
    Surface(
        color = style.colors.page.copy(alpha = 0.58f),
        shape = RoundedCornerShape(style.smallRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, style.colors.stroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("角色配音", color = style.colors.subText, fontSize = 12.sp)
                Text(
                    text = speechRouteSummary(route, groups, defaultText = "使用默认朗读引擎"),
                    color = style.colors.text,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            SmallAction("切换", { pickerVisible = true })
        }
    }
    if (pickerVisible) {
        SpeechVoiceRoutePickerDialog(
            title = "角色配音",
            groups = groups,
            currentRoute = route,
            onDismiss = { pickerVisible = false },
            onRouteSelected = { selectedRoute ->
                pickerVisible = false
                onChange(selectedRoute.toJson())
            }
        )
    }
}

@Composable
private fun InfoPill(text: String, maxWidth: androidx.compose.ui.unit.Dp) {
    val style = rememberCharacterStyle()
    Surface(
        modifier = Modifier.widthIn(max = maxWidth),
        color = style.colors.page.copy(alpha = 0.60f),
        shape = RoundedCornerShape(style.smallRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, style.colors.stroke)
    ) {
        Text(
            text = text,
            color = style.colors.subText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CharacterSection(
    label: String,
    value: String,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    val style = rememberCharacterStyle()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        color = style.colors.card,
        shape = RoundedCornerShape(style.radius)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, color = style.colors.subText, fontSize = 13.sp)
            Text(
                text = value.ifBlank { "未填写" },
                color = style.colors.text,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun CharacterEditScreen(
    title: String,
    draft: CharacterEditDraft,
    speechEngines: List<CharacterSpeechEngineUi>,
    onDraftChange: (CharacterEditDraft) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onPickLocalAvatar: () -> Unit,
    onPickOnlineAvatar: () -> Unit,
    onPickGalleryAvatar: () -> Unit,
    onRegenerateAvatar: () -> Unit,
    onClearAvatar: () -> Unit
) {
    val style = rememberCharacterStyle()
    CharacterScaffold(
        title = title,
        onBack = onBack,
        actions = {
            TextButton(onClick = onSave) { Text("保存", color = style.colors.accent) }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 10.dp, 18.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(color = style.colors.card, shape = RoundedCornerShape(style.radius)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CharacterAvatar(draft.avatar, draft.name, 82)
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text("角色头像", color = style.colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SmallAction("本地", onPickLocalAvatar)
                                SmallAction("在线", onPickOnlineAvatar)
                                SmallAction("图库", onPickGalleryAvatar)
                                SmallAction("生成", onRegenerateAvatar)
                                SmallAction("清除", onClearAvatar, danger = true)
                            }
                        }
                    }
                }
            }
            item { CharacterTextField("角色名称", draft.name, { onDraftChange(draft.copy(name = it)) }, singleLine = true) }
            item { RoleSelector(draft.roleLevel) { onDraftChange(draft.copy(roleLevel = it)) } }
            item { GenderSelector(draft.gender) { onDraftChange(draft.copy(gender = it)) } }
            item { CharacterTextField("角色年纪", draft.age, { onDraftChange(draft.copy(age = it)) }, singleLine = true) }
            item { CharacterTextField("角色身份", draft.identity, { onDraftChange(draft.copy(identity = it)) }, singleLine = true) }
            item { CharacterTextField("角色技能", draft.skills, { onDraftChange(draft.copy(skills = it)) }) }
            item { CharacterTextField("角色属性", draft.attributes, { onDraftChange(draft.copy(attributes = it)) }) }
            item { CharacterTextField("角色形象描述", draft.appearance, { onDraftChange(draft.copy(appearance = it)) }) }
            item { CharacterTextField("角色性格描述", draft.personality, { onDraftChange(draft.copy(personality = it)) }) }
            item { CharacterTextField("角色生平", draft.biography, { onDraftChange(draft.copy(biography = it)) }, minLines = 5) }
            item {
                CharacterSpeechRouteEditor(
                    speechRouteJson = draft.speechRouteJson,
                    speechEngines = speechEngines,
                    onChange = { onDraftChange(draft.copy(speechRouteJson = it)) }
                )
            }
        }
    }
}

@Composable
private fun CharacterSpeechRouteEditor(
    speechRouteJson: String,
    speechEngines: List<CharacterSpeechEngineUi>,
    onChange: (String) -> Unit
) {
    val style = rememberCharacterStyle()
    val route = remember(speechRouteJson) { SpeechRoute.fromJson(speechRouteJson) }
    val groups = remember(speechEngines) { speechEngines.map { it.group } }
    var pickerVisible by remember { mutableStateOf(false) }
    Surface(color = style.colors.card, shape = RoundedCornerShape(style.radius)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("角色配音", color = style.colors.subText, fontSize = 13.sp)
            Text(
                text = speechRouteSummary(route, groups, defaultText = "使用书籍或全局默认朗读引擎"),
                color = style.colors.text,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (groups.isEmpty()) {
                Text(
                    text = "暂无可用朗读引擎。",
                    color = style.colors.subText,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallAction("选择发言人", { pickerVisible = true })
                SmallAction("使用默认", { onChange("") })
            }
        }
    }
    if (pickerVisible) {
        SpeechVoiceRoutePickerDialog(
            title = "角色配音",
            groups = groups,
            currentRoute = route,
            onDismiss = { pickerVisible = false },
            onRouteSelected = { selectedRoute ->
                pickerVisible = false
                onChange(selectedRoute.toJson())
            }
        )
    }
}

@Composable
private fun GenderSelector(gender: String, onGenderChange: (String) -> Unit) {
    val style = rememberCharacterStyle()
    val current = BookCharacter.normalizeGender(gender)
    Surface(color = style.colors.card, shape = RoundedCornerShape(style.radius)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("角色性别", color = style.colors.subText, fontSize = 13.sp)
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    BookCharacter.GENDER_UNKNOWN to "未知",
                    BookCharacter.GENDER_MALE to "男",
                    BookCharacter.GENDER_FEMALE to "女"
                ).forEach { (value, label) ->
                    val selected = current == value
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(style.smallRadius))
                            .clickable { onGenderChange(value) },
                        color = if (selected) style.colors.accent.copy(alpha = 0.16f) else style.colors.page,
                        shape = RoundedCornerShape(style.smallRadius),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) style.colors.accent else style.colors.stroke)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) style.colors.accent else style.colors.text,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleSelector(role: Int, onRoleChange: (Int) -> Unit) {
    val style = rememberCharacterStyle()
    Surface(color = style.colors.card, shape = RoundedCornerShape(style.radius)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("角色类型", color = style.colors.subText, fontSize = 13.sp)
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    BookCharacter.ROLE_NORMAL to "普通角色",
                    BookCharacter.ROLE_IMPORTANT to "重要角色",
                    BookCharacter.ROLE_MAIN to "主角"
                ).forEach { (value, label) ->
                    val selected = role == value
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(style.smallRadius))
                            .clickable { onRoleChange(value) },
                        color = if (selected) style.colors.accent.copy(alpha = 0.16f) else style.colors.page,
                        shape = RoundedCornerShape(style.smallRadius),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) style.colors.accent else style.colors.stroke)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) style.colors.accent else style.colors.text,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = false,
    minLines: Int = 2
) {
    val style = rememberCharacterStyle()
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(style.smallRadius)
    )
}

@Composable
fun CharacterRelationScreen(
    characters: List<BookCharacter>,
    relations: List<BookCharacterRelation>,
    selectedCenterId: Long,
    selectedRelation: BookCharacterRelation?,
    editingRelation: RelationEditDraft?,
    onBack: () -> Unit,
    onAddRelation: () -> Unit,
    onEditRelation: (BookCharacterRelation) -> Unit,
    onDeleteRelation: (BookCharacterRelation) -> Unit,
    onSaveRelation: (RelationEditDraft) -> Unit,
    onDismissEdit: () -> Unit,
    onSelectCenter: (Long) -> Unit,
    onOpenCard: (BookCharacter) -> Unit,
    onSelectRelation: (BookCharacterRelation?) -> Unit
) {
    val style = rememberCharacterStyle()
    val relationCharacterIds = remember(relations) {
        relations.flatMap { listOf(it.fromCharacterId, it.toCharacterId) }.toSet()
    }
    val graphCharacters = remember(characters, relationCharacterIds) {
        characters.filter { it.id in relationCharacterIds }
    }
    val graphCenterId = remember(graphCharacters, selectedCenterId) {
        selectedCenterId.takeIf { id -> graphCharacters.any { it.id == id } }
            ?: graphCharacters.firstOrNull { it.roleLevel == BookCharacter.ROLE_MAIN }?.id
            ?: graphCharacters.firstOrNull { it.roleLevel == BookCharacter.ROLE_IMPORTANT }?.id
            ?: graphCharacters.firstOrNull()?.id
            ?: 0L
    }
    LaunchedEffect(graphCenterId, selectedCenterId) {
        if (graphCenterId > 0L && graphCenterId != selectedCenterId) {
            onSelectCenter(graphCenterId)
        }
    }
    val centerRelations = remember(relations, graphCenterId) {
        directRelationsForCenter(relations, graphCenterId)
    }
    CharacterScaffold(
        title = "角色关系网",
        subtitle = "主角视角 · ${graphCharacters.size} 个角色 · ${relations.size} 条关系",
        onBack = onBack,
        actions = {
            TextButton(onClick = onAddRelation) { Text("添加关系", color = style.colors.accent) }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            if (graphCharacters.isEmpty()) {
                EmptyCharacterCard("暂无关系，点击右上角添加角色关系。")
                Spacer(modifier = Modifier.weight(1f))
            } else {
                CharacterCenterSelector(graphCharacters, graphCenterId, onSelectCenter)
                CharacterGraph(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 14.dp, end = 14.dp),
                    characters = graphCharacters,
                    relations = centerRelations,
                    selectedCenterId = graphCenterId,
                    onCharacterClick = onOpenCard,
                    onRelationClick = onSelectRelation
                )
            }
            RelationListPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                relations = centerRelations,
                characters = characters,
                onEdit = onEditRelation,
                onDelete = onDeleteRelation
            )
        }
    }
    selectedRelation?.let {
        RelationDetailDialog(
            relation = it,
            characters = characters,
            onDismiss = { onSelectRelation(null) },
            onEdit = {
                onSelectRelation(null)
                onEditRelation(it)
            }
        )
    }
    editingRelation?.let {
        RelationEditSheet(
            draft = it,
            characters = characters,
            onChange = onSaveRelation,
            onDismiss = onDismissEdit
        )
    }
}

@Composable
private fun CharacterCenterSelector(
    characters: List<BookCharacter>,
    selectedCenterId: Long,
    onSelect: (Long) -> Unit
) {
    val style = rememberCharacterStyle()
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        characters.forEach { character ->
            val selected = character.id == selectedCenterId
            Surface(
                color = if (selected) style.colors.accent.copy(alpha = 0.14f) else style.colors.card,
                shape = RoundedCornerShape(style.smallRadius),
                modifier = Modifier.clickable { onSelect(character.id) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CharacterAvatar(character.avatar, character.displayName(), 32)
                    Text(
                        text = character.displayName(),
                        color = if (selected) style.colors.accent else style.colors.text,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterGraph(
    modifier: Modifier,
    characters: List<BookCharacter>,
    relations: List<BookCharacterRelation>,
    selectedCenterId: Long,
    onCharacterClick: (BookCharacter) -> Unit,
    onRelationClick: (BookCharacterRelation) -> Unit
) {
    val style = rememberCharacterStyle()
    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val visibleCharacters = remember(characters, relations, selectedCenterId) {
        buildVisibleCharacters(characters, relations, selectedCenterId)
    }
    val visibleIds = visibleCharacters.map { it.id }.toSet()
    val visibleRelations = remember(relations, visibleIds, selectedCenterId) {
        relations.filter {
            (it.fromCharacterId == selectedCenterId && it.toCharacterId in visibleIds) ||
                (it.toCharacterId == selectedCenterId && it.fromCharacterId in visibleIds)
        }
    }
    val visibleKey = remember(visibleCharacters) {
        visibleCharacters.joinToString("|") { it.id.toString() }
    }
    Surface(
        modifier = modifier.shadow(8.dp, RoundedCornerShape(style.radius), clip = false),
        color = style.colors.card,
        shape = RoundedCornerShape(style.radius)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val layout = remember(visibleCharacters, visibleRelations, selectedCenterId, widthPx, density) {
                buildGraphLayout(visibleCharacters, visibleRelations, selectedCenterId, widthPx, density.density)
            }
            val fitScale = remember(layout.canvasWidth, layout.canvasHeight, widthPx, heightPx) {
                graphFitScale(layout, widthPx, heightPx)
            }
            val minScale = remember(fitScale) {
                graphMinScale(fitScale)
            }
            LaunchedEffect(visibleKey, selectedCenterId, layout.canvasWidth, layout.canvasHeight) {
                scale = fitScale
                pan = Offset.Zero
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(visibleCharacters, selectedCenterId, layout.canvasWidth, layout.canvasHeight, minScale) {
                        detectTransformGestures { centroid, p, zoom, _ ->
                            val oldScale = scale.coerceAtLeast(0.01f)
                            val nextScale = (scale * zoom).coerceIn(minScale, 2.4f)
                            val viewportCenter = Offset(size.width / 2f, size.height / 2f)
                            val zoomPan = (centroid - viewportCenter - pan) * (1f - nextScale / oldScale)
                            scale = nextScale
                            pan = constrainGraphPan(
                                pan = pan + p + zoomPan,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                layout = layout,
                                scale = nextScale
                            )
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val graphCenter = layout.nodes[selectedCenterId]?.center
                        ?.let { transformGraphPoint(it, layout, scale, pan, size.width, size.height) }
                        ?: Offset(size.width * 0.5f, size.height * 0.5f)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                style.colors.accent.copy(alpha = 0.18f),
                                style.colors.accent.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = graphCenter,
                            radius = size.minDimension * 0.72f
                        )
                    )
                    layout.edges.sortedBy { it.relation.strength }.forEach { edge ->
                        val start = transformGraphPoint(edge.start, layout, scale, pan, size.width, size.height)
                        val end = transformGraphPoint(edge.end, layout, scale, pan, size.width, size.height)
                        val strength = edge.relation.strength.coerceIn(0, 100)
                        val strokeWidth = (1.4f + strength / 58f).dp.toPx()
                        val edgeColor = style.colors.accent.copy(alpha = 0.18f + strength / 380f)
                        drawLine(
                            color = edgeColor,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawGraphArrowHead(
                            start = start,
                            end = end,
                            color = edgeColor,
                            strokeWidth = strokeWidth
                        )
                    }
                }
                layout.edges
                    .filter { it.showLabel }
                    .forEach { edge ->
                        val p = transformGraphPoint(edge.labelPoint, layout, scale, pan, widthPx, heightPx)
                        GraphRelationLabel(
                            relation = edge.relation,
                            onClick = { onRelationClick(edge.relation) },
                            modifier = Modifier.offset {
                                IntOffset(
                                    (p.x - 48.dp.toPx()).roundToInt(),
                                    (p.y - 14.dp.toPx()).roundToInt()
                                )
                            }
                        )
                    }
                visibleCharacters.forEach { character ->
                    val node = layout.nodes[character.id] ?: return@forEach
                    val p = transformGraphPoint(node.center, layout, scale, pan, widthPx, heightPx)
                    val avatarSize = when (character.id) {
                        selectedCenterId -> 82
                        else -> if (character.roleLevel == BookCharacter.ROLE_IMPORTANT) 64 else 56
                    }
                    val nodeWidth = 112.dp
                    val nodeWidthPx = with(density) { nodeWidth.toPx() }
                    val avatarSizePx = with(density) { avatarSize.dp.toPx() }
                    CharacterGraphNode(
                        character = character,
                        isCenter = character.id == selectedCenterId,
                        avatarSize = avatarSize,
                        onClick = { onCharacterClick(character) },
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (p.x - nodeWidthPx / 2f).roundToInt(),
                                    (p.y - avatarSizePx / 2f).roundToInt()
                                )
                            }
                            .width(nodeWidth)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGraphArrowHead(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    val angle = atan2(dy, dx)
    val arrowLength = max(10.dp.toPx(), strokeWidth * 4.2f)
    val spread = 0.55f
    val left = Offset(
        x = end.x - cos(angle - spread) * arrowLength,
        y = end.y - sin(angle - spread) * arrowLength
    )
    val right = Offset(
        x = end.x - cos(angle + spread) * arrowLength,
        y = end.y - sin(angle + spread) * arrowLength
    )
    drawLine(
        color = color,
        start = end,
        end = left,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = end,
        end = right,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

@Composable
private fun GraphRelationLabel(
    relation: BookCharacterRelation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = rememberCharacterStyle()
    Text(
        text = relation.displayName(),
        color = style.colors.accent.copy(alpha = 0.92f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            shadow = Shadow(
                color = style.colors.page.copy(alpha = 0.82f),
                offset = Offset(0f, 1.2f),
                blurRadius = 4f
            )
        ),
        modifier = modifier
            .widthIn(max = 96.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(onClick = onClick)
    )
}

private fun buildVisibleCharacters(
    characters: List<BookCharacter>,
    relations: List<BookCharacterRelation>,
    centerId: Long
): List<BookCharacter> {
    if (characters.isEmpty()) return emptyList()
    val center = characters.firstOrNull { it.id == centerId } ?: characters.first()
    val byId = characters.associateBy { it.id }
    val directIds = relations
        .filter { it.fromCharacterId == center.id || it.toCharacterId == center.id }
        .sortedWith(compareByDescending<BookCharacterRelation> { it.strength }.thenBy { it.sortOrder }.thenBy { it.id })
        .mapNotNull {
            when (center.id) {
                it.fromCharacterId -> it.toCharacterId
                it.toCharacterId -> it.fromCharacterId
                else -> null
            }
        }
        .distinct()
        .take(8)
    return (listOf(center.id) + directIds)
        .mapNotNull { byId[it] }
        .ifEmpty { listOf(center) }
}

private fun directRelationsForCenter(
    relations: List<BookCharacterRelation>,
    centerId: Long
): List<BookCharacterRelation> {
    return relations
        .filter { it.fromCharacterId == centerId || it.toCharacterId == centerId }
        .sortedWith(compareByDescending<BookCharacterRelation> { it.strength }.thenBy { it.sortOrder }.thenBy { it.id })
}

@Composable
private fun CharacterGraphNode(
    character: BookCharacter,
    isCenter: Boolean,
    avatarSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = rememberCharacterStyle()
    Column(
        modifier = modifier.pointerInput(character.id) {
            detectTapGestures(onTap = { onClick() })
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.shadow(if (isCenter) 14.dp else 8.dp, CircleShape, clip = false),
            shape = CircleShape,
            color = if (isCenter) style.colors.accent.copy(alpha = 0.18f) else style.colors.page.copy(alpha = 0.96f),
            border = androidx.compose.foundation.BorderStroke(
                if (isCenter) 2.dp else 1.dp,
                if (isCenter) style.colors.accent else style.colors.stroke
            )
        ) {
            Box(modifier = Modifier.padding(if (isCenter) 4.dp else 3.dp)) {
                CharacterAvatar(character.avatar, character.displayName(), avatarSize)
            }
        }
        Text(
            text = character.displayName(),
            color = if (isCenter) style.colors.accent else style.colors.text,
            fontSize = if (isCenter) 12.sp else 11.sp,
            fontWeight = if (isCenter) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                shadow = Shadow(
                    color = style.colors.page.copy(alpha = 0.92f),
                    offset = Offset(0f, 1.2f),
                    blurRadius = 5f
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 4.dp, end = 4.dp)
        )
    }
}

private data class GraphLayout(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val center: Offset,
    val nodes: Map<Long, GraphNodeLayout>,
    val edges: List<GraphEdgeLayout>
)

private data class GraphNodeLayout(
    val character: BookCharacter,
    val center: Offset
)

private data class GraphEdgeLayout(
    val relation: BookCharacterRelation,
    val start: Offset,
    val end: Offset,
    val labelPoint: Offset,
    val showLabel: Boolean
)

private fun buildGraphLayout(
    characters: List<BookCharacter>,
    relations: List<BookCharacterRelation>,
    centerId: Long,
    width: Float,
    density: Float
): GraphLayout {
    val minCanvasWidth = 420f * density
    val baseWidth = max(width, minCanvasWidth)
    val canvasScale = when {
        characters.size <= 5 -> 1.08f
        characters.size <= 9 -> 1.28f
        else -> 1.55f
    }
    val canvasWidth = baseWidth * canvasScale
    val canvasHeight = baseWidth * when {
        characters.size <= 5 -> 1.10f
        characters.size <= 9 -> 1.32f
        else -> 1.58f
    }
    if (characters.isEmpty()) {
        val emptyCenter = Offset(canvasWidth / 2f, canvasHeight / 2f)
        return GraphLayout(canvasWidth, canvasHeight, emptyCenter, emptyMap(), emptyList())
    }
    val center = characters.firstOrNull { it.id == centerId } ?: characters.first()
    val others = characters.filter { it.id != center.id }
    val result = linkedMapOf<Long, GraphNodeLayout>()
    val centerPoint = Offset(
        x = canvasWidth * 0.5f,
        y = canvasHeight * 0.48f
    )
    result[center.id] = GraphNodeLayout(center, centerPoint)
    val relationStrength = relations.flatMap {
        listOf(it.fromCharacterId to it.strength, it.toCharacterId to it.strength)
    }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.maxOrNull() ?: 0 }
    val orderedOthers = others.sortedWith(
        compareByDescending<BookCharacter> { relationStrength[it.id] ?: 0 }
            .thenByDescending { it.roleLevel }
            .thenBy { it.sortOrder }
            .thenBy { it.id }
    )
    val slots = graphSlotRatios(orderedOthers.size)
    orderedOthers.forEachIndexed { index, character ->
        val slot = slots.getOrElse(index) { Offset(0.5f, 0.5f) }
        result[character.id] = GraphNodeLayout(
            character = character,
            center = Offset(
                x = canvasWidth * slot.x,
                y = canvasHeight * slot.y
            )
        )
    }
    val edges = buildGraphEdges(relations, result, center.id, density)
    return GraphLayout(canvasWidth, canvasHeight, centerPoint, result, edges)
}

private fun buildGraphEdges(
    relations: List<BookCharacterRelation>,
    nodes: Map<Long, GraphNodeLayout>,
    centerId: Long,
    density: Float
): List<GraphEdgeLayout> {
    val acceptedLabels = mutableListOf<Offset>()
    val minLabelDistance = 86f * density
    return relations
        .filter {
            it.fromCharacterId in nodes &&
                it.toCharacterId in nodes &&
                (it.fromCharacterId == centerId || it.toCharacterId == centerId)
        }
        .sortedWith(compareByDescending<BookCharacterRelation> { it.strength }.thenBy { it.sortOrder }.thenBy { it.id })
        .map { relation ->
            val startNode = nodes.getValue(relation.fromCharacterId)
            val endNode = nodes.getValue(relation.toCharacterId)
            val (start, end) = trimGraphEdge(
                start = startNode.center,
                end = endNode.center,
                startRadius = graphEdgeRadius(startNode.character, centerId, density),
                endRadius = graphEdgeRadius(endNode.character, centerId, density)
            )
            val labelPoint = Offset(
                x = (start.x + end.x) / 2f,
                y = (start.y + end.y) / 2f
            )
            val labelAllowed = acceptedLabels.none { distance(it, labelPoint) < minLabelDistance }
            if (labelAllowed) acceptedLabels += labelPoint
            GraphEdgeLayout(
                relation = relation,
                start = start,
                end = end,
                labelPoint = labelPoint,
                showLabel = labelAllowed
            )
        }
}

private fun graphEdgeRadius(character: BookCharacter, centerId: Long, density: Float): Float {
    val radiusDp = when {
        character.id == centerId -> 52f
        character.roleLevel == BookCharacter.ROLE_IMPORTANT -> 42f
        else -> 38f
    }
    return radiusDp * density
}

private fun trimGraphEdge(
    start: Offset,
    end: Offset,
    startRadius: Float,
    endRadius: Float
): Pair<Offset, Offset> {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    if (length <= startRadius + endRadius + 1f) return start to end
    val ux = dx / length
    val uy = dy / length
    return Offset(start.x + ux * startRadius, start.y + uy * startRadius) to
        Offset(end.x - ux * endRadius, end.y - uy * endRadius)
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun graphFitScale(layout: GraphLayout, width: Float, height: Float): Float {
    if (width <= 0f || height <= 0f || layout.canvasWidth <= 0f || layout.canvasHeight <= 0f) {
        return 1f
    }
    val horizontal = width * 0.88f / layout.canvasWidth
    val vertical = height * 0.84f / layout.canvasHeight
    return minOf(horizontal, vertical).coerceIn(0.42f, 1f)
}

private fun graphMinScale(fitScale: Float): Float {
    return (fitScale * 0.72f).coerceIn(0.32f, fitScale)
}

private fun graphSlotRatios(count: Int): List<Offset> {
    return when (count) {
        0 -> emptyList()
        1 -> listOf(Offset(0.82f, 0.50f))
        2 -> listOf(Offset(0.18f, 0.50f), Offset(0.82f, 0.50f))
        3 -> listOf(Offset(0.50f, 0.10f), Offset(0.16f, 0.68f), Offset(0.84f, 0.68f))
        4 -> listOf(Offset(0.50f, 0.08f), Offset(0.86f, 0.50f), Offset(0.50f, 0.92f), Offset(0.14f, 0.50f))
        5 -> listOf(
            Offset(0.50f, 0.07f),
            Offset(0.86f, 0.32f),
            Offset(0.74f, 0.88f),
            Offset(0.26f, 0.88f),
            Offset(0.14f, 0.32f)
        )
        6 -> listOf(
            Offset(0.30f, 0.10f),
            Offset(0.70f, 0.10f),
            Offset(0.90f, 0.48f),
            Offset(0.70f, 0.90f),
            Offset(0.30f, 0.90f),
            Offset(0.10f, 0.48f)
        )
        7 -> listOf(
            Offset(0.50f, 0.06f),
            Offset(0.82f, 0.18f),
            Offset(0.92f, 0.50f),
            Offset(0.72f, 0.88f),
            Offset(0.28f, 0.88f),
            Offset(0.08f, 0.50f),
            Offset(0.18f, 0.18f)
        )
        else -> listOf(
            Offset(0.50f, 0.06f),
            Offset(0.82f, 0.18f),
            Offset(0.94f, 0.44f),
            Offset(0.80f, 0.74f),
            Offset(0.50f, 0.94f),
            Offset(0.20f, 0.74f),
            Offset(0.06f, 0.44f),
            Offset(0.18f, 0.18f)
        )
    }
}

private fun transformGraphPoint(
    point: Offset,
    layout: GraphLayout,
    scale: Float,
    pan: Offset,
    width: Float,
    height: Float
): Offset {
    val viewportCenter = Offset(width / 2f, height / 2f)
    return viewportCenter + (point - layout.center) * scale + pan
}

private fun constrainGraphPan(
    pan: Offset,
    width: Float,
    height: Float,
    layout: GraphLayout,
    scale: Float
): Offset {
    val margin = (minOf(width, height) * 0.18f).coerceIn(72f, 180f)
    val maxX = ((layout.canvasWidth * scale - width) / 2f + margin).coerceAtLeast(margin)
    val maxY = ((layout.canvasHeight * scale - height) / 2f + margin).coerceAtLeast(margin)
    return Offset(
        pan.x.coerceIn(-maxX, maxX),
        pan.y.coerceIn(-maxY, maxY)
    )
}

@Composable
private fun RelationListPanel(
    modifier: Modifier = Modifier,
    relations: List<BookCharacterRelation>,
    characters: List<BookCharacter>,
    onEdit: (BookCharacterRelation) -> Unit,
    onDelete: (BookCharacterRelation) -> Unit
) {
    val style = rememberCharacterStyle()
    var expanded by remember { mutableStateOf(false) }
    val panelHeight = if (expanded) 280.dp else 118.dp
    Surface(
        color = style.colors.page.copy(alpha = 0.96f),
        shape = RoundedCornerShape(style.radius),
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("关系列表", color = style.colors.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${relations.size} 条", color = style.colors.subText, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
                Text(if (expanded) "收起" else "展开", color = style.colors.accent, fontSize = 13.sp)
            }
            if (!expanded) {
                val first = relations.firstOrNull()
                val summary = first?.let { relation ->
                    val from = characters.firstOrNull { it.id == relation.fromCharacterId }?.displayName().orEmpty()
                    val to = characters.firstOrNull { it.id == relation.toCharacterId }?.displayName().orEmpty()
                    "$from → $to · ${relation.displayName()}"
                } ?: "暂无关系，点击右上角添加"
                Text(
                    text = summary,
                    color = style.colors.subText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(relations.take(12), key = { it.id }) { relation ->
                        val from = characters.firstOrNull { it.id == relation.fromCharacterId }?.displayName().orEmpty()
                        val to = characters.firstOrNull { it.id == relation.toCharacterId }?.displayName().orEmpty()
                        Surface(color = style.colors.card, shape = RoundedCornerShape(style.smallRadius)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$from → $to", color = style.colors.text, fontSize = 14.sp, maxLines = 1)
                                    Text(relation.displayName(), color = style.colors.subText, fontSize = 12.sp, maxLines = 1)
                                }
                                TextButton(onClick = { onEdit(relation) }) { Text("编辑", color = style.colors.accent) }
                                TextButton(onClick = { onDelete(relation) }) { Text("删除", color = style.colors.danger) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationDetailDialog(
    relation: BookCharacterRelation,
    characters: List<BookCharacter>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val style = rememberCharacterStyle()
    val from = characters.firstOrNull { it.id == relation.fromCharacterId }?.displayName().orEmpty()
    val to = characters.firstOrNull { it.id == relation.toCharacterId }?.displayName().orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(style.radius),
        containerColor = style.colors.card,
        title = { Text(relation.displayName()) },
        text = {
            Column {
                Text("$from → $to")
                Text("属性：${relation.relationType.ifBlank { "未填写" }}")
                Text("强度：${relation.strength}")
                if (relation.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(relation.description)
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("编辑") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun RelationEditSheet(
    draft: RelationEditDraft,
    characters: List<BookCharacter>,
    onChange: (RelationEditDraft) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember(draft) { mutableStateOf(draft) }
    var selectingTarget by remember { mutableStateOf<RelationSelectTarget?>(null) }
    val style = rememberCharacterStyle()
    val palette = style.toCharacterMiuixPalette()
    CharacterBackHandler {
        if (selectingTarget != null) {
            selectingTarget = null
        } else {
            onDismiss()
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (AppConfig.isNightTheme) 0.34f else 0.22f))
                .clickable(onClick = onDismiss)
        )
        LegadoMiuixCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .heightIn(max = 620.dp),
            color = style.colors.page,
            contentColor = style.colors.text,
            cornerRadius = style.radius,
            insidePadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        if (draft.id > 0) "编辑关系" else "添加关系",
                        color = style.colors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    CharacterSelectField("角色 A", characters.firstOrNull { it.id == editing.fromCharacterId }) {
                        selectingTarget = RelationSelectTarget.FROM
                    }
                }
                item {
                    CharacterSelectField("角色 B", characters.firstOrNull { it.id == editing.toCharacterId }) {
                        selectingTarget = RelationSelectTarget.TO
                    }
                }
                item { CharacterTextField("关系名称", editing.relationName, { editing = editing.copy(relationName = it) }, singleLine = true) }
                item { CharacterTextField("关系属性", editing.relationType, { editing = editing.copy(relationType = it) }, singleLine = true) }
                item {
                    Column {
                        Text("关系强度 ${editing.strength}", color = style.colors.subText, fontSize = 13.sp)
                        LegadoMiuixSlider(
                            value = editing.strength.toFloat(),
                            onValueChange = { editing = editing.copy(strength = it.roundToInt().coerceIn(0, 100)) },
                            palette = palette,
                            valueRange = 0f..100f
                        )
                    }
                }
                item { CharacterTextField("关系说明", editing.description, { editing = editing.copy(description = it) }, minLines = 3) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LegadoMiuixActionButton(
                            text = "取消",
                            palette = palette,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            cornerRadius = style.smallRadius
                        )
                        LegadoMiuixActionButton(
                            text = "保存",
                            palette = palette,
                            onClick = { onChange(editing) },
                            modifier = Modifier.weight(1f),
                            primary = true,
                            cornerRadius = style.smallRadius
                        )
                    }
                }
            }
        }
    }
    selectingTarget?.let { target ->
        CharacterSelectDialog(
            title = if (target == RelationSelectTarget.FROM) "选择角色 A" else "选择角色 B",
            characters = characters,
            selectedId = if (target == RelationSelectTarget.FROM) editing.fromCharacterId else editing.toCharacterId,
            onDismiss = { selectingTarget = null },
            onSelect = { id ->
                editing = if (target == RelationSelectTarget.FROM) {
                    editing.copy(fromCharacterId = id)
                } else {
                    editing.copy(toCharacterId = id)
                }
                selectingTarget = null
            }
        )
    }
}

@Composable
private fun CharacterBackHandler(onBack: () -> Unit) {
    val activity = LocalContext.current as? AppCompatActivity ?: return
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(activity) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
        activity.onBackPressedDispatcher.addCallback(callback)
        onDispose {
            callback.remove()
        }
    }
}

@Composable
private fun CharacterSelectField(
    label: String,
    selected: BookCharacter?,
    onClick: () -> Unit
) {
    val style = rememberCharacterStyle()
    Column {
        Text(label, color = style.colors.subText, fontSize = 13.sp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clickable(onClick = onClick),
            color = style.colors.card,
            shape = RoundedCornerShape(style.smallRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, style.colors.stroke)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected != null) {
                    CharacterAvatar(selected.avatar, selected.displayName(), 38)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (selected != null) 10.dp else 0.dp)
                ) {
                    Text(
                        text = selected?.displayName() ?: "请选择角色",
                        color = style.colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    selected?.identity?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = style.colors.subText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("更换", color = style.colors.accent, fontSize = 13.sp)
            }
        }
    }
}

private enum class RelationSelectTarget {
    FROM, TO
}

@Composable
private fun CharacterSelectDialog(
    title: String,
    characters: List<BookCharacter>,
    selectedId: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val style = rememberCharacterStyle()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(style.radius),
        containerColor = style.colors.card,
        title = { Text(title, color = style.colors.text) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(characters, key = { it.id }) { character ->
                    val selected = character.id == selectedId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(character.id) },
                        color = if (selected) style.colors.accent.copy(alpha = 0.14f) else style.colors.page,
                        shape = RoundedCornerShape(style.smallRadius),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) style.colors.accent else style.colors.stroke
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CharacterAvatar(character.avatar, character.displayName(), 44)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    character.displayName(),
                                    color = style.colors.text,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    character.identity.ifBlank { character.roleLabel() },
                                    color = style.colors.subText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (selected) {
                                Text("已选", color = style.colors.accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = style.colors.accent) } }
    )
}

@Composable
private fun RolePill(text: String, compact: Boolean, modifier: Modifier = Modifier) {
    val style = rememberCharacterStyle()
    Surface(
        modifier = modifier,
        color = style.colors.accent.copy(alpha = 0.13f),
        shape = RoundedCornerShape(style.smallRadius)
    ) {
        Text(
            text = text,
            color = style.colors.accent,
            fontSize = if (compact) 11.sp else 13.sp,
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit, danger: Boolean = false) {
    val style = rememberCharacterStyle()
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (danger) style.colors.danger.copy(alpha = 0.10f) else style.colors.accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(style.smallRadius)
    ) {
        Text(
            text = text,
            color = if (danger) style.colors.danger else style.colors.accent,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun EmptyCharacterCard(text: String) {
    val style = rememberCharacterStyle()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        color = style.colors.card,
        shape = RoundedCornerShape(style.radius)
    ) {
        Text(
            text = text,
            color = style.colors.subText,
            fontSize = 14.sp,
            modifier = Modifier.padding(22.dp)
        )
    }
}

@Composable
fun CharacterAvatar(
    path: String,
    contentDescription: String,
    sizeDp: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(rememberCharacterStyle().colors.card),
        factory = {
            ImageView(it).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                this.contentDescription = contentDescription
            }
        },
        update = { imageView ->
            val target = path.trim()
            if (imageView.tag != target) {
                imageView.tag = target
                ImageLoader.load(context, target.ifBlank { null })
                    .placeholder(R.drawable.ic_bottom_person)
                    .error(R.drawable.ic_bottom_person)
                    .into(imageView)
            }
        },
        onRelease = { it.releaseComposeImage() }
    )
}
