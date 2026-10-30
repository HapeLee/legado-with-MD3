# 视频类型书源支持 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Legado 增加第五种书源类型「视频」，章节内容解析出单个视频地址并用内置 ExoPlayer 全屏播放。

**Architecture:** 复用音频链路的三段式：`BookSourceType.video` → `BookContent` 返回 URL（已跳过 HTML 格式化）→ `MediaItem` 交给 ExoPlayer。新增独立的播放器 Compose 页面和「视频」书架分组，不触碰现有音频/图片/文本逻辑。

**Tech Stack:** Kotlin, Jetpack Compose, Navigation 3, Koin, Room, Media3 ExoPlayer 1.11.0, Media3 UI 1.11.0

**Spec:** `docs/superpowers/specs/2026-10-24-video-book-source-design.md`

## Global Constraints

- 所有新 UI 遵循项目 MVI 约定：`XxxContract.kt` / `XxxViewModel.kt` / `XxxScreen.kt`
- `UiState` 使用 `@Stable data class`，列表字段用 `ImmutableList`
- ViewModel 直接继承 `ViewModel()`，`_uiState` 为 `MutableStateFlow`，`_effects` 为 `MutableSharedFlow(extraBufferCapacity = 16)`
- 不修改现有音频、图片、文本的任何行为
- `media3` 版本固定 `1.11.0`，与 `libs.versions.toml:61` 一致
- 单测运行命令（本机 JDK 21）：
  ```powershell
  $env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
  .\gradlew.bat :app:testAppDebugUnitTest --tests "<测试类全名>" --console=plain
  ```

---

### Task 1: 书源与书籍类型模型

**Files:**
- Modify: `app/src/main/java/io/legado/app/constant/BookSourceType.kt`
- Modify: `app/src/main/java/io/legado/app/constant/BookType.kt`
- Modify: `app/src/main/java/io/legado/app/help/source/BookSourceExtensions.kt:127-134`
- Modify: `app/src/main/res/values/arrays.xml:8-13`
- Modify: `app/src/main/res/values/strings.xml`（`audio` 附近，第 775 行）
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Test: `app/src/test/java/io/legado/app/help/source/BookSourceGetBookTypeTest.kt`

**Interfaces:**
- Produces: `BookSourceType.video = 4`；`BookSource.getBookType()` 对 video 返回 `BookType.video`；`R.string.video`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/io/legado/app/help/source/BookSourceGetBookTypeTest.kt`：

```kotlin
package io.legado.app.help.source

import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.BookSource
import org.junit.Assert.assertEquals
import org.junit.Test

class BookSourceGetBookTypeTest {

    @Test
    fun videoSourceMapsToVideoBookType() {
        val source = BookSource(
            bookSourceUrl = "https://example.com",
            bookSourceType = BookSourceType.video,
        )
        assertEquals(BookType.video, source.getBookType())
    }

    @Test
    fun audioSourceStillMapsToAudioBookType() {
        val source = BookSource(
            bookSourceUrl = "https://example.com",
            bookSourceType = BookSourceType.audio,
        )
        assertEquals(BookType.audio, source.getBookType())
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.source.BookSourceGetBookTypeTest" --console=plain
```
Expected: 编译失败，`Unresolved reference: video`

- [ ] **Step 3: 修改类型定义**

`constant/BookSourceType.kt`：

```kotlin
const val default = 0           // 0 文本
const val audio = 1             // 1 音频
const val image = 2             // 2 图片
const val file = 3              // 3 只提供下载服务的网站
const val video = 4             // 4 视频

@IntDef(default, audio, image, file, video)
annotation class Type
```

`constant/BookType.kt`：

```kotlin
const val allBookType = text or image or audio or video or webFile
```

并把 `video` 加入 `@IntDef`：

```kotlin
@IntDef(text, updateError, audio, image, video, webFile, local, archive, notShelf)
annotation class Type
```

`help/source/BookSourceExtensions.kt` 的 `getBookType()`：

```kotlin
fun BookSource.getBookType(): Int {
    return when (bookSourceType) {
        BookSourceType.file -> BookType.text or BookType.webFile
        BookSourceType.image -> BookType.image
        BookSourceType.audio -> BookType.audio
        BookSourceType.video -> BookType.video
        else -> BookType.text
    }
}
```

`res/values/arrays.xml`：

```xml
<string-array name="book_type">
    <item>Text</item>
    <item>Audio</item>
    <item>Image</item>
    <item>File</item>
    <item>Video</item>
</string-array>
```

`res/values/strings.xml`：

```xml
<string name="video">Video</string>
```

`res/values-zh-rCN/strings.xml`：

```xml
<string name="video">视频</string>
```

- [ ] **Step 4: 运行测试确认通过**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.source.BookSourceGetBookTypeTest" --console=plain
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/io/legado/app/constant/BookSourceType.kt \
        app/src/main/java/io/legado/app/constant/BookType.kt \
        app/src/main/java/io/legado/app/help/source/BookSourceExtensions.kt \
        app/src/main/res/values/arrays.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rCN/strings.xml \
        app/src/test/java/io/legado/app/help/source/BookSourceGetBookTypeTest.kt
git commit -m "feat(source): 新增视频类型书源"
```

---

### Task 2: 书架「视频」分组

**Files:**
- Modify: `app/src/main/java/io/legado/app/data/entities/BookGroup.kt:31-66`
- Modify: `app/src/main/java/io/legado/app/data/AppDatabase.kt:380-386`（音频分组插入之后）
- Modify: `app/src/main/java/io/legado/app/data/dao/BookDao.kt`（`flowByGroup`、`flowBookShelfByGroup`、`flowSystemGroupCounts`、新增两个查询）
- Test: `app/src/test/java/io/legado/app/data/dao/VideoBookQueryTest.kt`

**Interfaces:**
- Consumes: `BookType.video`（Task 1）
- Produces: `BookGroup.IdVideo = -9L`；`BookDao.flowVideo()`、`BookDao.flowBookShelfVideo()`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/io/legado/app/data/dao/VideoBookQueryTest.kt`（模仿 `BookShelfIntroQueryTest` 的 in-memory Room 写法）：

```kotlin
package io.legado.app.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoBookQueryTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun flowVideoReturnsOnlyVideoBooks() = runBlocking {
        db.bookDao.insert(Book(bookUrl = "v1", name = "视频书", type = BookType.video))
        db.bookDao.insert(Book(bookUrl = "t1", name = "文本书", type = BookType.text))

        val result = db.bookDao.flowVideo().first()

        assertEquals(listOf("v1"), result.map { it.bookUrl })
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.dao.VideoBookQueryTest" --console=plain
```
Expected: 编译失败，`Unresolved reference: flowVideo`

- [ ] **Step 3: 加分组常量**

`BookGroup.kt` companion object 中，`IdManga` 之后加：

```kotlin
const val IdVideo = -9L
```

`getManageName` 的 `when` 中加：

```kotlin
IdVideo -> GroupNameInfo(groupName, context.getString(R.string.video))
```

- [ ] **Step 4: 注册内置分组**

`AppDatabase.kt` 的 `onOpen` 中，`insertBookGroupMusicSql` 之后加：

```kotlin
@Language("sql")
val insertBookGroupVideoSql = """
    insert into book_groups(groupId, groupName, 'order', show)
    select ${BookGroup.IdVideo}, '视频', -24, 0
    where not exists (select * from book_groups where groupId = ${BookGroup.IdVideo})
""".trimIndent()
db.execSQL(insertBookGroupVideoSql)
```

- [ ] **Step 5: 加 DAO 查询**

`BookDao.kt` 中，`flowManga()` 之后加：

```kotlin
@Query("SELECT * FROM books WHERE type & ${BookType.video} > 0")
fun flowVideo(): Flow<List<Book>>
```

`flowBookShelfManga()` 之后加（复制整段 SQL，只把 `BookType.image` 换成 `BookType.video`）：

```kotlin
@Query(
    """
    SELECT
        bookUrl,
        name,
        author,
        origin,
        originName,
        coverUrl,
        customCoverUrl,
        durChapterTitle,
        durChapterTime,
        durChapterPos,
        latestChapterTitle,
        latestChapterTime,
        lastCheckCount,
        totalChapterNum,
        durChapterIndex,
        type,
        `group`,
        `order`,
        canUpdate,
        ifnull(customIntro, ifnull(listIntro, intro)) as intro,
        kind,
        customTag,
        wordCount
    FROM books
    WHERE type & ${BookType.video} > 0
    AND $PUBLIC_BOOK_FILTER
    """
)
fun flowBookShelfVideo(): Flow<List<BookShelfItem>>
```

`flowByGroup` 与 `flowBookShelfByGroup` 的 `when` 中加分支：

```kotlin
BookGroup.IdVideo -> flowVideo()
```
```kotlin
BookGroup.IdVideo -> flowBookShelfVideo()
```

`flowSystemGroupCounts` 的 SQL 中，音频行之后加：

```sql
UNION ALL SELECT ${BookGroup.IdVideo}, COUNT(*) FROM books WHERE type & ${BookType.video} > 0 AND $PUBLIC_BOOK_FILTER
```

同时把 `IdNetNone` 的条件从：

```sql
WHERE type & ${BookType.audio} = 0 AND type & ${BookType.local} = 0
```

改为：

```sql
WHERE type & ${BookType.audio} = 0 AND type & ${BookType.video} = 0 AND type & ${BookType.local} = 0
```

避免视频书混进「网络未分组」。

- [ ] **Step 6: 运行测试确认通过**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.dao.VideoBookQueryTest" --console=plain
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/io/legado/app/data/entities/BookGroup.kt \
        app/src/main/java/io/legado/app/data/AppDatabase.kt \
        app/src/main/java/io/legado/app/data/dao/BookDao.kt \
        app/src/test/java/io/legado/app/data/dao/VideoBookQueryTest.kt
git commit -m "feat(bookshelf): 新增视频分组"
```

---

### Task 3: 播放器 Contract 与 ViewModel

**Files:**
- Create: `app/src/main/java/io/legado/app/ui/book/video/VideoPlayerContract.kt`
- Create: `app/src/main/java/io/legado/app/ui/book/video/VideoPlayerViewModel.kt`
- Modify: `gradle/libs.versions.toml`（media3-ui 依赖）
- Modify: `app/build.gradle.kts:221-222`
- Modify: `app/src/main/java/io/legado/app/di/appModule.kt`（注册 ViewModel）
- Test: `app/src/test/java/io/legado/app/ui/book/video/VideoPlayerProgressTest.kt`

**Interfaces:**
- Consumes: `BookRepository.getBook()`、`BookRepository.getChapter()`、`BookRepository.getChapterCount()`、`BookSourceRepository.getBookSource()`
- Produces:
  - `VideoPlayerUiState`、`VideoPlayerIntent`、`VideoPlayerEffect`
  - `VideoPlayerViewModel.onIntent(intent)`、`uiState`、`effects`、`exoPlayer`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/io/legado/app/ui/book/video/VideoPlayerProgressTest.kt`：

```kotlin
package io.legado.app.ui.book.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerProgressTest {

    @Test
    fun clampsSeekPositionToDuration() {
        assertEquals(9_000L, clampSeekPosition(12_000L, 9_000L))
    }

    @Test
    fun keepsValidSeekPosition() {
        assertEquals(3_000L, clampSeekPosition(3_000L, 9_000L))
    }

    @Test
    fun handlesUnknownDuration() {
        assertEquals(3_000L, clampSeekPosition(3_000L, 0L))
    }

    @Test
    fun previousChapterAtIndexZeroReturnsSameIndex() {
        assertEquals(0, previousChapterIndex(0))
    }

    @Test
    fun nextChapterStopsAtLastIndex() {
        assertEquals(9, nextChapterIndex(9, 10))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.book.video.VideoPlayerProgressTest" --console=plain
```
Expected: 编译失败，`Unresolved reference: clampSeekPosition`

- [ ] **Step 3: 加 media3-ui 依赖**

`gradle/libs.versions.toml` 的 `[libraries]` 中，`media3-exoplayer` 之后加：

```toml
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
```

`app/build.gradle.kts` 的 media3 依赖块中加：

```kotlin
implementation(libs.media3.ui)
```

- [ ] **Step 4: 写 Contract**

`VideoPlayerContract.kt`：

```kotlin
package io.legado.app.ui.book.video

import androidx.compose.runtime.Stable

@Stable
data class VideoPlayerUiState(
    val bookName: String = "",
    val bookAuthor: String = "",
    val chapterTitle: String = "",
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val position: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
)

sealed interface VideoPlayerIntent {
    data object PlayPause : VideoPlayerIntent
    data class SeekTo(val positionMs: Long) : VideoPlayerIntent
    data object PreviousChapter : VideoPlayerIntent
    data object NextChapter : VideoPlayerIntent
    data object Retry : VideoPlayerIntent
}

sealed interface VideoPlayerEffect {
    data class ShowToast(val message: String) : VideoPlayerEffect
}
```

- [ ] **Step 5: 写 ViewModel**

`VideoPlayerViewModel.kt`（纯函数放在同文件顶层，供测试直接调用）：

```kotlin
package io.legado.app.ui.book.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.help.exoplayer.ExoPlayerHelper
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.getMediaItem
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

internal fun clampSeekPosition(positionMs: Long, durationMs: Long): Long {
    return if (durationMs > 0) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)
}

internal fun previousChapterIndex(index: Int): Int = (index - 1).coerceAtLeast(0)

internal fun nextChapterIndex(index: Int, chapterCount: Int): Int {
    return if (chapterCount <= 0) index else (index + 1).coerceAtMost(chapterCount - 1)
}

class VideoPlayerViewModel(
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<VideoPlayerEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayerHelper.createHttpExoPlayer(appCtx).apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _uiState.update {
                        it.copy(
                            isLoading = playbackState == Player.STATE_BUFFERING,
                            duration = duration.coerceAtLeast(0L),
                            bufferedPosition = bufferedPosition.coerceAtLeast(0L),
                        )
                    }
                }
            })
        }
    }

    private var book: Book? = null
    private var bookSourceUrl: String? = null
    private var chapterIndex: Int = 0

    fun init(bookUrl: String) {
        viewModelScope.launch {
            val loaded = bookRepository.getBook(bookUrl) ?: run {
                _effects.tryEmit(VideoPlayerEffect.ShowToast("书籍不存在"))
                return@launch
            }
            book = loaded
            bookSourceUrl = loaded.origin
            chapterIndex = loaded.durChapterIndex
            _uiState.update {
                it.copy(
                    bookName = loaded.name,
                    bookAuthor = loaded.author,
                    chapterCount = bookRepository.getChapterCount(bookUrl),
                )
            }
            loadChapter(chapterIndex, loaded.durChapterPos)
        }
    }

    fun onIntent(intent: VideoPlayerIntent) {
        when (intent) {
            is VideoPlayerIntent.PlayPause -> {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            }
            is VideoPlayerIntent.SeekTo -> {
                exoPlayer.seekTo(clampSeekPosition(intent.positionMs, exoPlayer.duration))
            }
            is VideoPlayerIntent.PreviousChapter -> {
                val target = previousChapterIndex(chapterIndex)
                if (target != chapterIndex) loadChapter(target, 0L)
            }
            is VideoPlayerIntent.NextChapter -> {
                val target = nextChapterIndex(chapterIndex, _uiState.value.chapterCount)
                if (target != chapterIndex) loadChapter(target, 0L)
            }
            is VideoPlayerIntent.Retry -> loadChapter(chapterIndex, 0L)
        }
    }

    private fun loadChapter(index: Int, positionMs: Long) {
        val currentBook = book ?: return
        val sourceUrl = bookSourceUrl ?: return
        viewModelScope.launch {
            saveProgress()
            _uiState.update { it.copy(isLoading = true, error = null) }
            val chapter = bookRepository.getChapter(currentBook.bookUrl, index) ?: run {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(VideoPlayerEffect.ShowToast("章节不存在"))
                return@launch
            }
            _uiState.update { it.copy(chapterTitle = chapter.title, chapterIndex = index) }
            val source = bookSourceRepository.getBookSource(sourceUrl) ?: run {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(VideoPlayerEffect.ShowToast("书源不存在"))
                return@launch
            }
            try {
                val content = WebBook.getContentAwait(source, currentBook, chapter)
                if (content.isBlank()) {
                    _uiState.update { it.copy(isLoading = false) }
                    _effects.tryEmit(VideoPlayerEffect.ShowToast("未获取到视频链接"))
                } else {
                    chapterIndex = index
                    val mediaItem: MediaItem = AnalyzeUrl(
                        content,
                        source = source,
                        ruleData = currentBook,
                        chapter = chapter,
                        coroutineContext = kotlin.coroutines.EmptyCoroutineContext,
                    ).getMediaItem()
                    exoPlayer.setMediaItem(mediaItem, positionMs)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
                _effects.tryEmit(VideoPlayerEffect.ShowToast(e.message ?: "加载失败"))
            }
        }
    }

    private fun saveProgress() {
        val currentBook = book ?: return
        if (chapterIndex < 0) return
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        viewModelScope.launch {
            bookRepository.update(
                currentBook.copy(
                    durChapterIndex = chapterIndex,
                    durChapterPos = position,
                )
            )
        }
    }

    override fun onCleared() {
        saveProgress()
        exoPlayer.release()
        super.onCleared()
    }
}
```

> 签名依据：`WebBook.getContentAwait`（`model/webBook/WebBook.kt:393`）是 suspend 函数；`AnalyzeUrl(...).getMediaItem()` 的构造方式与 `service/AudioPlayService.kt:259-266` 完全一致。

- [ ] **Step 6: 注册 ViewModel**

`di/appModule.kt` 中，其他 `viewModelOf(::...)` 附近加：

```kotlin
viewModelOf(::VideoPlayerViewModel)
```

并补 import：

```kotlin
import io.legado.app.ui.book.video.VideoPlayerViewModel
```

- [ ] **Step 7: 运行测试确认通过**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.book.video.VideoPlayerProgressTest" --console=plain
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/io/legado/app/ui/book/video/ \
        app/src/test/java/io/legado/app/ui/book/video/ \
        app/src/main/java/io/legado/app/di/appModule.kt \
        gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat(video): 视频播放器 ViewModel 与契约"
```

---

### Task 4: 播放器界面

**Files:**
- Create: `app/src/main/java/io/legado/app/ui/book/video/VideoPlayerScreen.kt`

**Interfaces:**
- Consumes: `VideoPlayerViewModel`、`VideoPlayerUiState`、`VideoPlayerIntent`、`VideoPlayerEffect`

- [ ] **Step 1: 写 Screen**

`VideoPlayerScreen.kt`：

```kotlin
package io.legado.app.ui.book.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun VideoPlayerScreen(
    bookUrl: String,
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val effects: Flow<VideoPlayerEffect> = viewModel.effects

    LaunchedEffect(bookUrl) {
        viewModel.init(bookUrl)
    }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is VideoPlayerEffect.ShowToast -> toastOnUi(effect.message)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = true
                        player = viewModel.exoPlayer
                    }
                },
                onRelease = {
                    it.player = null
                },
            )
            if (state.chapterTitle.isNotBlank()) {
                Text(
                    text = "${state.bookName} · ${state.chapterTitle}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            if (state.error != null) {
                Text(
                    text = state.error ?: "",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
```

> `PlayerView` 自带播放/暂停、进度条和倍速菜单，第一版不额外写控制层。上下章按钮后续需要时再加（`VideoPlayerIntent` 已备好）。

- [ ] **Step 2: 编译验证**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat :app:compileAppDebugKotlin --console=plain
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/io/legado/app/ui/book/video/VideoPlayerScreen.kt
git commit -m "feat(video): 视频播放界面"
```

---

### Task 5: 导航与入口

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt:168-176` 附近
- Modify: `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`（`onOpenBookshelfBook`，约 545-565；新增 entry）
- Modify: `app/src/main/java/io/legado/app/ui/book/info/BookInfoRouteScreen.kt:140-165`
- Modify: `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt:1355-1370`（传给 BookInfoRouteScreen 的回调）

**Interfaces:**
- Consumes: `VideoPlayerScreen`（Task 4）
- Produces: `MainRouteVideoPlay(bookUrl, sharedCoverKey)`

- [ ] **Step 1: 加路由**

`MainNavKey.kt` 中 `MainRouteAudioPlay` 之后加：

```kotlin
@Serializable
data class MainRouteVideoPlay(
    val bookUrl: String? = null,
    val inBookshelf: Boolean = true,
    val sharedCoverKey: String? = null,
) : MainRoute
```

- [ ] **Step 2: 注册 entry**

`MainNavGraph.kt` 中 `entry<MainRouteReadManga>` 附近加：

```kotlin
entry<MainRouteVideoPlay> { route ->
    VideoPlayerScreen(
        bookUrl = route.bookUrl ?: return@entry,
        onBack = onNavigateBack,
    )
}
```

补 import：

```kotlin
import io.legado.app.ui.book.video.VideoPlayerScreen
```

- [ ] **Step 3: 书架点击分流**

`onOpenBookshelfBook` 中，在 `if (book.isAudio)` 之前插入：

```kotlin
if (book.isVideo) {
    onNavigateToRoute(
        MainRouteVideoPlay(
            bookUrl = book.bookUrl,
            sharedCoverKey = sharedCoverKey,
        )
    )
} else if (book.isAudio) {
```

补 import：

```kotlin
import io.legado.app.help.book.isVideo
```

- [ ] **Step 4: 详情页分流**

`BookInfoRouteScreen` 的 `OpenReader` 分支：

```kotlin
is BookInfoEffect.OpenReader -> {
    when {
        effect.book.isVideo -> onOpenVideoPlayer(
            effect.book.bookUrl,
            effect.inBookshelf,
        )

        effect.book.isAudio -> onOpenAudioPlay(
            effect.book.bookUrl,
            effect.inBookshelf
        )
        // ...其余不变
    }
}
```

函数签名中加参数：

```kotlin
onOpenVideoPlayer: (bookUrl: String, inBookshelf: Boolean) -> Unit = { _, _ -> },
```

`MainNavGraph.kt` 的 `BookInfoRouteScreen(...)` 调用处加：

```kotlin
onOpenVideoPlayer = { bookUrl, inBookshelf ->
    onNavigateToRoute(
        MainRouteVideoPlay(bookUrl = bookUrl, inBookshelf = inBookshelf)
    )
},
```

- [ ] **Step 5: 编译验证**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat :app:compileAppDebugKotlin --console=plain
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/io/legado/app/ui/main/MainNavKey.kt \
        app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt \
        app/src/main/java/io/legado/app/ui/book/info/BookInfoRouteScreen.kt
git commit -m "feat(video): 视频播放路由与入口"
```

---

### Task 6: 书架与详情页展示

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/main/bookshelf/BookShelfItem.kt:42-46`
- Modify: `app/src/main/java/io/legado/app/ui/main/bookshelf/BookItem.kt:755-765`

**Interfaces:**
- Consumes: `BookType.video`（Task 1）

- [ ] **Step 1: 加类型判断**

`BookShelfItem.kt` 中 `isImage` 之后加：

```kotlin
val isVideo: Boolean get() = (type and BookType.video) > 0
```

- [ ] **Step 2: 加类型标签**

`BookItem.kt` 的 `bookTypeLabel` `when` 中，`book.isAudio` 之前加：

```kotlin
book.isVideo -> stringResource(R.string.video)
```

- [ ] **Step 3: 编译验证**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat :app:compileAppDebugKotlin --console=plain
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/io/legado/app/ui/main/bookshelf/BookShelfItem.kt \
        app/src/main/java/io/legado/app/ui/main/bookshelf/BookItem.kt
git commit -m "feat(bookshelf): 视频类型标签"
```

---

### Task 7: 全量验证与手动测试

**Files:** 无新增

- [ ] **Step 1: 跑新增的全部单测**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.source.BookSourceGetBookTypeTest" --tests "io.legado.app.data.dao.VideoBookQueryTest" --tests "io.legado.app.ui.book.video.VideoPlayerProgressTest" --console=plain
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 全量编译**

```powershell
$env:JAVA_HOME="D:\Scoop\apps\corretto21-jdk\current"
.\gradlew.bat assembleAppDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`，生成 APK

- [ ] **Step 3: 手动验证**

1. 安装 Debug APK
2. 新建书源 → 类型下拉出现 `Video` → 选择并保存
3. 搜索一本书 → 加入书架
4. 书架侧边栏出现「视频」分组，书籍带「视频」标签
5. 点击书籍 → 打开播放器，视频开始播放
6. 暂停/播放、拖动进度条
7. 返回后重新进入，进度恢复

- [ ] **Step 4: 提交（如有手动验证中的修复）**

```bash
git add -A
git commit -m "fix(video): 手动验证问题修复"
```

---

## 已知风险

1. `WebBook.getContent` / `AnalyzeUrl` 的确切签名在 Task 3 实现时以现有音频代码为准，计划中的调用可能需要按实际签名微调。
2. `PlayerView` 的默认控制层包含播放/进度/倍速，但不含上下章；第一版接受。
3. 直播 m3u8 的 seek 行为与点播不同，第一版只保证能播。
