# 段评气泡

段评气泡把书源里的「段评数字」渲染成本地 SVG 模板，替代书源自带的段评图标（图片或内嵌 SVG）。
实现以 [Legado-Arc](https://github.com/Legado-Arc) 的目录契约与设置语义为基准。

[[toc]]

## 书源怎么写

原生（不联网，最省事）：

```html
<img src="dp:12">
<img src='dp:12,{"status":"emphasis","displayColor":"#FF0000"}'>
```

`dp:` 后是显示的数字，`,` 后是可选图片选项。这类写法**不需要任何开关**即渲染为气泡。

scheme 形式：

```html
<img src="bubble://paragraph?num=12&status=emphasis&displayColor=%23FF0000">
```

支持 `num` / `displayText` / `status`（`emphasis` 用强调色）/ `displayColor`。

书源把段评入口画成一张图、靠开关接管：

```html
<img src="https://host/review.png,{"style":"TEXT","type":"cmt","click":"java.showBrowser(url, html)"}">
```

识别条件（`ParagraphBubbleSource.isForcedCandidate`）：`style=TEXT`、`type ∈ {qd,fqpl,fanqie,cmt,comment,review,paragraph,…}`、
`click`/`pclick` 里含 `showCmt(` / `showComment(` / `paragraph`。`click` 里的 `java.showBrowser(url, html)`
会打开阅读页的原生段评弹层，复用 `source/cache/java` JS 桥，点赞回复等交互书源无需改动。

`paragraphRule:` / `rule:` 类段落规则动作目前不支持，点击会提示。

## 气泡包目录契约

```
bubblePackages/<dirName>/
├── bubble.json
├── assets/     # 字体引用，asset://名称
└── images/     # 图片引用，asset://名称
```

`bubble.json` 字段：

| 字段 | 说明 |
|---|---|
| `name` | 显示名（≤200 字） |
| `dirName` | 目录名，单段路径、不以 `.` 开头 |
| `svgTemplate` | SVG 模板，`${num}` 替换数字、`${color}` 替换配色 |
| `sizeScale` | 0.5–1.5，同时作用于排版槽宽与位图 |
| `dayNormalColor` / `dayEmphasisColor` / `nightNormalColor` / `nightEmphasisColor` | 四色，书源 `displayColor` 可覆盖 |
| `updatedAt` | 更新时间，参与缓存键 |
| `formatVersion` | 1；引用包内资源时为 2 |
| `resources` | `[{alias, path, type}]`，`type` 为 image/font |

`builtin_default`（内置）不落盘，由 `BubblePackageManager.builtinConfig()` 提供。

## 设置

| 键 | 语义 |
|---|---|
| `paragraphBubblePackage` | 当前生效的气泡包目录名；空或 `builtin_default` 表示内置 |
| `forceSoftwareParagraphBubble` | 是否接管书源图片形式的段评入口 |

读取统一走 `BubbleSettings`（Koin 未就绪时退化为内置 + 不强制），写走 `ReadSettingsGateway`。

## 渲染链路

1. `LegacyReaderImageOptionsResolver` 命中 `ParagraphBubbleSource.parse` 时把图片转成
   `INLINE + REVIEW 占位`，并带上 `inlineWidthScale`
2. `ReaderChapterBlockMeasurer` 用「占位字 advance × inlineWidthScale」量槽宽
3. `ImageProvider.renderBubble` 按最终槽宽栅格化（`SvgUtils` + `PackageSvgResourceResolver`），
   走位图 LRU，键含包目录、`updatedAt`、尺寸、昼夜与最终颜色
4. 模板 `<text>` 的字体默认取正文字体（`ReadBookConfig.textFont/textBold/textItalic/systemTypefaces`），
   包内 `asset://` 字体优先

尺寸口径对齐旧 `TextChapterLayout` 的 `inlineWidth`：**槽宽 = 占位字 advance × sizeScale**，
位图按槽宽栅格化，因此渲染分辨率与排版宽度始终同口径（`sizeScale` 才会真正撑开行高）。

## 备份与恢复

- 备份：`bubblePackages/` 整棵树随备份 zip 走（目录式，与 Legado-Arc 一致）
- 恢复：同分区暂存后由 `BubblePackageManager.replacePackageTree` 整树事务替换——
  旧树 → backup、staging → root、装前装后逐包验清单，失败回滚旧树
- 旧版备份里的 `bubblePackages.zip` 仍按逐包原子安装（合并式）恢复，保证跨版本可用

## 决策记录

**为什么不用排版级 `ReaderElement.Review`？**

Legado-Arc 的 `TextChapterLayout` 里排版级 `ReviewColumn` 分支是整段注释的，实际走 `ImageColumn` +
占位字 + `inlineWidth`。本仓库的 Canvas 阅读器里 `ReaderElement.Review` 至今没有生产者
（`drawReview` 是死路径）。引入排版级实现等于凭空维护第二套渲染管线，且要先补齐生产者、测量与分页。
因此保留行内图路线，用 `inlineWidthScale` 解决「缩放不影响排版」的实际问题。

**为什么不用三态 `reviewBubbleStyle`（source/package/custom）？**

那会与 Legado-Arc 的设置模型（`paragraphBubblePackage` + `forceSoftwareParagraphBubble`）分叉：
从 ARC 迁移过来的用户选择、书源对 `dp:` 的既有约定都会失配，且磁盘上的气泡包虽通用，语义却变了。
若要加「自定义单文件 SVG」，应作为 `paragraphBubblePackage` 之外的一个补充态，而不是替换现有开关。

**与 PR #2508 的关系**

#2508 走的是排版级路线 + 三态设置 + 第二套管理页（`feature/bubble`）+ 服务端段评计数，与本文档描述的
实现有 8 个同路径文件冲突。已借用其事务/备份口径、`BubbleSvgPolicy` 的安全阈值与测试思路；
服务端段评计数（`chapter_review` / `para_review` 有界请求）尚未落地，属于独立切片。

## 已知缺口

- 云同步（上传/下载/远端过期合并）未做：仓库没有对应云存储基建
- 服务端段评计数未做：只有图标没有数字的书源仍显示原图
- 管理页没有 ARC 的全屏 SVG 编辑器、文件分享、云端容器选择
- EPUB/纯文本直读没有等价的气泡预生成链路（架构与 ARC 的 HTML 阅读器不同）

## 测试

| 测试 | 覆盖 |
|---|---|
| `BubbleDirectoryTransactionTest` | 替换、首次安装、校验失败回滚、无旧目录清理、move 失败回滚、双失败保留备份、兄弟包 |
| `BubblePackageTreeRestoreTest` | 整树替换、坏包整体拒绝、空备份拒绝 |
| `BubblePackageArchiveTest` | 包识别 |
| `ParagraphBubbleSourceTest` / `ParagraphBubbleForcedSourceTest` | `dp:` / scheme / 内嵌 SVG 数字、强制开关分支 |
| `ReaderChapterBlockMeasurerTest` | `inlineWidthScale` 影响段评占位槽宽 |
