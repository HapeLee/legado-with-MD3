# Track F0 —— 阅读界面 KMP/CMP 冻结基线

> **状态：已完成（2026-09-05，revision `9ce52559c`，工作区干净）。**
> 本文是 `track-f-reader-kmp-migration-plan.md` §5-F0 的实际产出，只新增脚本与文档，**未改动产品代码**。

---

## 1. 依赖清单

### 1.1 生成方式

```powershell
python3 tools/report_reader_kmp_dependencies.py
python3 tools/report_reader_kmp_dependencies.py -o build/reports/reader-kmp-deps.md
```

脚本 `tools/report_reader_kmp_dependencies.py` 是可重跑的报告工具：扫描
`app/src/main/java/io/legado/app/ui/book/read` 与 `app/src/main/java/io/legado/app/feature/reader`，
按 import 做机械分类，并把 `android.*` 引用归入计划 §3 的契约候选分组。

分类口径：

| 分类 | 判定 |
|---|---|
| `common-ready` | 无 `android.*`/`androidx.*` import；**或**仅有 `androidx.compose.runtime.Stable` |
| `androidx-only` | 有 `androidx.*` 但无 `android.*` —— 需逐个确认该库在 CMP 下可用 |
| `platform-island` | 有 `android.*` |

脚本**只报证据不做判定**：`common-ready` 仍需非 Android 编译才算成立，`platform-island`
也可能经窄契约进入共享层。`build/` 被 `.gitignore` 忽略，报告不入版本控制，由 CI 生成。

### 1.2 快照（2026-09-05，157 个文件）

| 分类 | 文件数 | 占比 |
|---|---|---|
| `common-ready` | 70 | 45% |
| `androidx-only` | 44 | 28% |
| `platform-island` | 43 | 27% |
| **合计** | **157** | 100% |

按目录：

| 目录 | common-ready | androidx-only | platform-island |
|---|---|---|---|
| `feature/reader/core/*`（10 个子包） | **44** | 0 | 0 |
| `feature/reader/legacy` | 3 | 0 | 2 |
| `feature/reader/platform` | 0 | 0 | 6 |
| `feature/reader`（根，含 `ReaderCanvasSurface`） | 0 | 0 | 2 |
| `ui/book/read`（根） | 12 | 13 | 19 |
| `ui/book/read/config` | 0 | 0 | 7 |
| `ui/book/read/page/entities` | 2 | 0 | 0 |
| `ui/book/read/page/provider` | 1 | 0 | 0 |
| `ui/book/read/pageestimate` | 7 | 0 | 1 |
| `ui/book/read/sheet` | 1 | 31 | 6 |

**关键读数**：`feature/reader/core` 的 **44 个文件全部 `common-ready`**，与计划 §3-A 一致，
是 F2 的搬迁主体。`ui/book/read/sheet` 的 31 个 `androidx-only` 说明那批 Sheet 已无
`android.*` 依赖，但仍是 F6 才评估的对象（缺少第二宿主之前搬它不产生收益）。

### 1.3 契约候选分组

所有 `android.*` import 均已归入已知分组，**无未匹配项**：

| 契约 | 命中文件数 | 说明 |
|---|---|---|
| 宿主 Effect / FileAccess | 24 | `Context`/`Intent`/`Uri`/`Activity`/`OpenableColumns`，走 Effect 回调不进共享层 |
| `platform-island` | 20 | 自定义 Span、`nativeCanvas`、`Paint`/`Path`/`Typeface`、`View`/`Sensor`/`Build` 等 |
| `ReaderImageDecoder` | 8 | `Bitmap`/`BitmapFactory`/`Drawable` |
| `ReaderTextMeasurer / ReaderRichTextParser` | 4 | `android.text` 整形与 HTML |
| TTS capability | 2 | `android.speech.tts` |

`android.*` 直方图前六：`android.text.style` 23、`android.content.Context` 18、
`android.graphics.Paint` 11、`android.net.Uri` 9、`android.graphics.Canvas` 7、
`android.graphics.drawable` 5。

---

## 2. 测试覆盖核对

### 2.1 结论：`feature/reader/core` 44 个文件全部有覆盖

按"主源文件名 ↔ `{stem}Test.kt`"严格匹配后有 4 个"孤儿"，逐个 grep 核实后确认
**全部被其他测试覆盖**：

| 文件 | 覆盖它的测试 |
|---|---|
| `core/gesture/ReaderPageViewportLayout.kt` | `ReaderSelectionViewportLayoutTest` |
| `core/layout/ReaderTextShaper.kt` | `ReaderChapterBlockMeasurerTest`、`ReaderImageOptionsTest`、`ReaderIndentTest`、`ReaderSingleImageTest`、`ReaderAndroidPaintFactoryTest`、`ReaderSubtitleMetricsTest` |
| `core/model/ReaderTipValue.kt` | `ReaderTipValueFormatterTest` |
| `core/source/ReaderChapterSource.kt` | `ReaderChapterBlockMeasurerTest`、`ReaderImageOptionsTest`、`ReaderIndentTest`、`ReaderSingleImageTest`、`ReaderChapterSourceParserTest`、`ReaderTitleSegmentationTest`、`AndroidReaderHtmlSourceResolverTest`、`ReaderSubtitleMetricsTest` |

**判定**：F2 可以"直接搬"，不需要从零补 characterization test。缺口在**口径**而非**存在性**
—— 见 §3。

### 2.2 F0 的最大发现：文本测量契约已经存在

核对 `ReaderTextShaper.kt` 时确认：**F3 要建的契约不需要新建**。

```kotlin
// core/layout/ReaderTextShaper.kt —— 无 Android import
fun interface ReaderTextShaper {
    fun shape(text: String): GlyphClusters
    val fontBounds: ReaderFontBounds? get() = null
    val fontLineMetrics: ReaderFontLineMetrics? get() = null
}
```

- 唯一实现 `platform/AndroidReaderTextShaper(paint: TextPaint)`；
  消费方 `core/layout/ReaderChapterBlockMeasurer`、`legacy/LegacyReaderChapterPaginator`。
- 配套 `ReaderTextShaperFactory`（`ReaderChapterBlockMeasurer.kt:13`）、
  `GlyphClusters`、`clusterGlyphs` 均在 `core/layout`，后者有 `ReaderLayoutMathTest`。
- `FloatArray` 是 `kotlin.FloatArray`，在 `commonMain` 合法，不算平台泄漏。

**这比参照样本 `shutiao/legado` 的 `TextMeasurer` 更适合跨平台**：返回字形簇而非逐字
`FloatArray`，把 emoji / 组合字符 / 连字切分留给平台实现，共享层只见 `List<String>` + `List<Float>`。

**并且它绕开了一整类口径问题**：`AndroidReaderTextShaper` 内部
`TextPaint(paint).apply { letterSpacing = 0f }` —— 字间距从 Paint 剥离、由分页层插入。
`shutiao/legado` 在 `SkiaTextMeasurer` 里花大量注释处理的 letterSpacing 跨平台补偿
（含 API 35+ `getTextWidths` 砍首尾半格），**本项目不需要做**。

据此已改写计划 §5-F3：从"定义契约"改为"审计完备性 + 补口径测试 + 收敛注入点"。

---

## 3. F5 parity 对照项与采集条件

**F0 不采集。** 本节只固定"将来采集什么、怎么采"，F5 启动时执行。

### 3.1 对照项清单

从 `feature/reader/core/model` 的渲染契约与 `ReaderCanvasSurface` 的绘制路径反推：

| # | 对照项 | 依据类型 |
|---|---|---|
| 1 | 正文字形与位置（字体、字号、行距、字间距） | `ReaderTextStyle`、`ReaderTextShaper` |
| 2 | 段落缩进与对齐 | `ReaderParagraphFactory`、`ReaderTextAlignment` |
| 3 | 标题排版 | `isTitle`、`ReaderTextStyle` |
| 4 | 页眉 / 页脚（14 种取值） | `ReaderTipValueType`、`ReaderTipRowLayout` |
| 5 | 图片布局（单图 / 长图滚动） | `ReaderImageDrawLayout`、`ReaderImageCachePolicy` |
| 6 | 文字背景图与九宫格 | `ReaderTextBackgroundRun`、`ReaderNineSliceLayout` |
| 7 | 下划线（含强调、波浪、SVG、虚线、双线） | `ReaderUnderlineRun`、`ReaderEmphasisUnderlineRun`、`config/*Span.kt` |
| 8 | 选中高亮与选择手柄 | `ReaderSelection`、`ReaderSelectionBounds` |
| 9 | 书签徽章 | `ReaderBookmarkBadge` |
| 10 | 翻页动画（含仿真卷曲阴影） | `PageCurlGeometry`、`ReaderPageTransition` |
| 11 | 滚动模式 | `ReaderScrollState`、`ScrollPageDrawCache` |
| 12 | 双页 / 横屏 | `ReaderColumnMode`、`ReaderDoublePage` |
| 13 | 背景与颜色变换 | `ReaderPageColorTransform`、`ReaderBackgroundAlpha` |
| 14 | 首帧耗时 | `ReaderFirstFrame` |

### 3.2 固定采集条件

沿用 `track-c3-reader-parity-baseline.md` §1 的表格式与既有脚本方法：

| 项目 | 值 |
|---|---|
| Git revision | F5 启动时记录（F0 时点为 `9ce52559c`） |
| APK variant | `appDebug` |
| 设备 / Android / 刷新率 | Samsung SM-S9310 / Android 16（API 36）/ 采集时 30Hz，自适应最高 120Hz |
| 分辨率 / 字体缩放 / 显示缩放 | 1080×2340 / 1.0 / 480dpi |
| 阅读字体 / 字号 / 行距 / 边距 | 默认字体 24sp；字体场景另测系统衬线字体并增大 3 级 |
| 测试书与章节 | 《斗破苍穹》第 8 章末页 / 第 9 章首页 |

截图前关闭阅读菜单、自动翻页、朗读和系统通知浮层。页眉中的时间、电量会变化，比较时裁掉
页眉/页脚，只比较正文区域。

### 3.3 与 Track C3 采集方法的**实质差异**（F5 前必须处理）

`tools/capture_reader_c3_baseline.py` 是通过**切换 lab flag** 在同一个构建里对比两种渲染器。
该 flag 已于 2026-07-25 随 Track C 产物删除，**脚本当前无法按原方式工作**。

F5 的对比对象变为：

- 基线侧 = 当前栈（Compose `DrawScope` + `nativeCanvas.drawText` 逃逸）
- 候选侧 = 新栈（CMP `DrawScope.drawText(TextLayoutResult)`）

两者**不在同一个构建内可切换**，因此需要：

1. 改造脚本为**对比两个 APK**（或加回一个临时 flag），并固定两次采集的设备状态；
2. 或先做最小 PoC 验证技术可行性，再决定是否值得改造采集链路。

这条记录为 F5 的前置项，不阻塞 F1–F4。

---

## 4. F0 退出条件核对

| 条件 | 状态 |
|---|---|
| 依赖清单可重跑 | ✅ `tools/report_reader_kmp_dependencies.py`，无未匹配 import |
| `core` 纯函数测试覆盖无缺口 | ✅ 44/44 有覆盖（4 个为间接覆盖，已逐项核实） |
| parity 对照项清单落盘 | ✅ 本文 §3.1，14 项 |
| 采集条件固定 | ✅ 本文 §3.2，另记录 §3.3 的方法差异 |

**未做的**：不重新采集 parity 与性能数据（计划明确推迟到 F5）；不修改任何历史文档。

**下一步**：F1 —— 建 `build-logic` convention plugin 与 KMP smoke 模块。
注意 F1 属 `kmp-cmp-modernization.md` 的 Phase 1，若已由其他轨道完成则跳过。
