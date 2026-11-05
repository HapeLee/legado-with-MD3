# 视频类型书源支持 — 设计文档

日期：2026-10-24
分支：`feature/video-book-source`

## 目标

为 Legado 增加第五种书源类型：**视频**。用户在书源里选择「Video」后，搜索、详情、书架、播放走与音频一致的链路，章节内容解析出单个视频地址，使用内置 ExoPlayer 全屏播放。

第一版范围限定为「最小可用播放器」：播放/暂停、进度拖动、上下章、横屏全屏、进度写回。

明确不做：画中画、后台播放、字幕、倍速、手势亮度/音量、视频缓存离线。

## 现状

| 位置 | 现状 |
|---|---|
| `constant/BookSourceType.kt` | 4 类：`default(0)` 文本、`audio(1)`、`image(2)`、`file(3)` |
| `constant/BookType.kt` | 已定义 `video = 0b100`，但全项目无赋值，`isVideo` 仅被 `BookContent` 引用一次 |
| `model/webBook/BookContent.kt:235` | 已有 `!book.isAudio && !book.isVideo` 分支，视频 URL 跳过 HTML 格式化 |
| `help/source/BookSourceExtensions.kt` | `getBookType()` 映射 4 类源到 `BookType` |
| `service/AudioPlayService.kt` | 音频用 `ExoPlayerHelper.createHttpExoPlayer()` |
| `help/exoplayer/ExoPlayerHelper.kt` | 已封装 OkHttp 数据源、请求头（`SPLIT_TAG` 编码）、缓存、证书 |
| 依赖 | 只有 `media3-exoplayer` 与 `media3-datasource-okhttp`，无 UI 渲染模块 |

## 设计

### 1. 数据模型

| 文件 | 改动 |
|---|---|
| `constant/BookSourceType.kt` | 新增 `const val video = 4`，加入 `@IntDef` |
| `constant/BookType.kt` | `allBookType` 加入 `video` |
| `help/source/BookSourceExtensions.kt` | `BookSourceType.video -> BookType.video` |
| `res/values/arrays.xml` | `book_type` 数组增加 `Video` |
| `res/values/strings.xml` | 新增 `video` 字符串 |
| `data/entities/BookGroup.kt` | 新增 `IdVideo = -9L`，`getManageName` 映射 `R.string.video` |

`bookSourceType` 为 Int 列，新增枚举值无需 Room 迁移。

### 2. 解析

不改 `BookContent` 的核心逻辑。视频章节正文即单个视频地址（mp4 / m3u8），沿用现有 `AnalyzeRule` 解析，`isVideo` 分支已跳过 HTML 格式化。

请求头：与音频一致，通过 `AnalyzeUrl.getMediaItem()`（内部 `setCookie()` + `headerMap` → `ExoPlayerHelper.createMediaItem`）构造 `MediaItem`，不另起一套请求头逻辑。

### 3. 播放器

新增依赖：

```toml
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
```

新增文件（遵循项目 MVI 约定）：

```
ui/book/video/
├── VideoPlayerContract.kt   // VideoPlayerUiState / Intent / Effect
├── VideoPlayerViewModel.kt  // ExoPlayer 持有、章节切换、进度写回
└── VideoPlayerScreen.kt     // AndroidView(PlayerView) + 控制层
```

- 复用 `ExoPlayerHelper.createHttpExoPlayer()`
- 播放状态：`isPlaying`、`position`、`duration`、`bufferedPosition`、`loading`、`error`
- 章节切换：上一章 / 下一章，切换前保存当前进度
- 进度写回：`Book.durChapterIndex`、`Book.durChapterPos`
- 全屏：横屏时隐藏系统栏；退出时恢复
- 错误：URL 为空、解析失败、播放失败均通过 `Effect.ShowToast` 提示

### 4. 导航

| 文件 | 改动 |
|---|---|
| `ui/main/MainNavKey.kt` | 新增 `MainRouteVideoPlay(bookUrl: String, sharedCoverKey: String?)` |
| `ui/main/MainNavGraph.kt` | 注册 entry；`onOpenBookshelfBook` 增加 `book.isVideo` 分支（在 `isAudio` 之前判断） |
| 书籍详情页 | 「开始阅读」按 `isVideo` 分流到视频播放器 |

书架点击优先级：`isVideo` → `isAudio` → `isImage` → 文本阅读器。

### 5. 书架

| 文件 | 改动 |
|---|---|
| `data/dao/BookDao.kt` | 分组计数查询增加 video 分支 |
| 书架侧边栏 | 增加「视频」分组入口 |
| `ui/main/bookshelf/BookItem.kt` | 类型标签增加「视频」 |

### 6. 错误处理

| 场景 | 行为 |
|---|---|
| 章节内容为空 | Toast「未获取到视频链接」，停留在播放器 |
| ExoPlayer 播放失败 | Toast + 重试按钮 |
| 播放中切换章节 | 先保存进度再释放旧 MediaItem |
| 退出播放器 | 保存进度，释放 ExoPlayer |

### 7. 测试

**单元测试**
- `BookSource.getBookType()` 对 4 种源类型的映射
- `BookGroup.getManageName(IdVideo)` 返回「视频」

**手动验证**
1. 新建书源，类型选 Video
2. 搜索 → 加入书架 → 书架出现「视频」分组
3. 打开详情 → 开始播放
4. 播放/暂停/拖动/切章
5. 退出重进，进度正确恢复

## 风险

1. `media3-ui` 是新增依赖。若体积或兼容性有问题，降级方案是用 `SurfaceView` 手写渲染层，控制逻辑不变。
2. 部分视频源需要 `Referer` / `User-Agent`，需要确认 headers 的获取时机与音频一致。
3. `m3u8` 直播流与点播流的 seek 行为不同，第一版按点播处理，直播流只保证能播。
