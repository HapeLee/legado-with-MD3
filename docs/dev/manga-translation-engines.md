# 漫画翻译管线与引擎选择

2026-10-31。目标是把漫画翻译的“识别 / 翻译 / 合成”从一组平铺设置里显式拆出来，
并为后续接入非 AI 的文字翻译引擎（Google 等）留出唯一落点。本轮只做可读性与 seam，
没有新增第二个翻译实现。

## 问题

现有设置不能直接回答“这一页会走哪条链”：

- `manga_translation_locator`（文案“OCR 引擎”）实际是**识别来源**，值里还包含 AI 识图。
- `MangaComposition` 同时表达合成载体（本地排版 / 图片模型）和是否需要文字翻译
  （`AI_PAGE` 完全没有文字阶段）。
- `ocrModelId` 为空（文案“跟随翻译模型”）的真实含义是**识别、翻译、排版合并为一次请求**，
  代价是不能单独更换翻译引擎，这一点 UI 与文案都没有表达。
- 真正的分支在 `analyzeAiTypesetting()` / `analyzeOcr()` 和
  `singleModel = ocr == null || 同指纹` 这个隐式条件里，用户与诊断面板都看不到。
- 因此“翻译模型”这一项既不能说明当前译文来自哪里，也无法容纳 Google 这类引擎。

## 管线模型

`MangaTranslationPipeline.of(settings)` 是唯一推导点，三段固定为：

| 阶段 | 取值 | 实际执行 |
| --- | --- | --- |
| 识别 | `AiVision(modelId)` / `LocalOcr(engine)` / `CloudOcr(provider)` / `Unused` | `recognizeAi`、Paddle、ML Kit、云端 OCR |
| 翻译 | `FusedWithRecognition` / `AiText(modelId)` / `Unused` | 合并请求，或 `TEXT_TRANSLATION_PROMPT` 纯文字 |
| 合成 | `LocalLayout(inpaint)` / `LocalOverlay` / `AiImage(mode, modelId)` | `MangaPageComposer`、图片编辑模型 |

推导规则：

- `composition == AI_PAGE`：识别与翻译都是 `Unused`，只保留图片合成。
- `locator == AI && ocrModelId 为空`：翻译为 `FusedWithRecognition`。
- `PATCH` 是 `LocalOverlay`，选择的擦字模型不参与；`SMART` 才携带 `inpaintEngine`。
- 识图模型与翻译模型被配置成同一份模型配置时，仓库仍会在请求期合并为一次；
  管线视图报告的是**配置意图**，不把该优化伪装成三段请求。

管线推导仍只发生在 `MangaTranslationPipeline.of()`，但不再渲染成面板顶部的「当前管线：…」
摘要（2026-11-02 移除）：同一信息由各分组当前选中的值直接表达，摘要只是把同样的值再拼一遍。
`MangaTranslationPipelineText` 随之删除，需要"当前配置会走哪条链"时读推导本身，不再维护第二套文案。

## ID 对齐

翻译阶段是“N 个已识别文字块 → N 条译文”，任何引擎都必须保持 ID 一一对应：

- **契约**：输入为 `id + original`，输出必须覆盖全部 ID，缺、重、多一律判失败
  （`MangaTranslationPlan.translateDetected`）。识别结果拥有原文、顺序与几何，
  翻译不得改变它们。
- **AI**：一次请求返回 JSON 数组，客户端校验后按 ID 回填，已有纠错重试预算。
- **Google（后续）**：gtx 端点无法把多段文本映射回 ID，只能逐条请求：
  文本去重（同页重复的“……”只请求一次）、并发上限 3–4、按输入顺序回填。
  失败即失败，不回落 AI、不静默画回原文。真正批量且有序的 Cloud Translation v2
  另立为可选引擎，不混入免费端点。禁用“用哨兵拼接多段再切分”的做法，
  Google 会改写换行与标点，必然错位。
- **缓存**：条级缓存键需包含 `引擎身份 | 源语言 | 目标语言 | 术语 | 上下文 | 原文`。
  AI 的上下文包含上页对话与 dialogue flows，必须参与键；Google 拿不到上下文，
  键更粗、命中率更高。页面级 `analysisKey` 需追加翻译引擎身份，换引擎不得复用旧译图。

## 设置与文案

- 分组与阶段一一对应：识别（方式 + 模型 + 原文语言）、翻译（引擎 + 目标语言 + 模型）、
  合成（本地 / AI 图片）。
- “跟随翻译模型”改为“与 AI 识别合并为一次请求”，说明只能用 AI 翻译；
  选择 Google 时该项禁用，而不是让用户配出跑不通的组合。
- 引擎能力说明常驻：术语表、跨气泡上下文、是否需要 AI 密钥。

## 切片

| 片 | 内容 | 状态 |
| --- | --- | --- |
| S1 | `MangaTranslationPipeline` 推导 + 设置面板摘要 | 完成 |
| S2 | 抽出纯文字翻译阶段入口 `translateText()`，两条路径等价迁移 | 完成 |
| S3 | Google 引擎 + 条级缓存，先接端侧／云端 OCR 链 | 完成 |
| S4 | 拆分识图路径接入、跟随全局翻译设置、缓存键与文案重排 | 完成 |
| S5 | Cloud Translation v2 批量引擎（可选） | 未开始 |

## S3 落地（2026-10-31）

- `MangaTranslationSettings.textEngine`（`AI` / `GOOGLE`，默认 `AI`，旧 JSON 直接解码为 AI）。
- 可用范围：`composition != AI_PAGE`。整页重绘没有纯文字阶段，保存时由 `validateSettings`
  拒绝，而不是运行期静默降级。AI 视觉识别不受限制：合并态（`ocrModelId` 为空）在非 AI 引擎下
  由翻译模型兼任识别，"识别 + 外部翻译 + 排版"分两段执行（见 S4）。
- 设置为 Google 时不再解析或要求 AI 翻译模型，也不显示翻译模型与思考强度；端侧／云端 OCR +
  Google 组合因此不需要任何 AI 密钥。图片合成（`AI_REGION`／`AI_REPAIR`）仍按所选图片模型计费。
- 请求：`MangaGoogleTranslator` 去重同页相同文字、并发上限 3（`DEFAULT_CONCURRENCY`），
  逐条调用 `GoogleTranslateClient`；`sl=auto`，目标语言沿用漫画自己的语言列表（如 `zh-CN`）。
- 对齐：每条译文按原文回填到所有相同文字的区域，随后整页 `MangaTranslationPlan.validate()`；
  任意请求失败或返回空文本即失败该段，不回落 AI、不绘制原文。
- 缓存：条级文本缓存位于 `manga-translation/<book>/texts/`，键为 `google-gtx-v1|目标语言|原文`
  （落盘前取 SHA-256）。页面级分析身份在非 AI 引擎下追加该版本号，AI 缓存不受影响；
  `clear()` 改为递归删除，避免新增目录残留。
- 章节翻译的 Google 调用改为共用 `GoogleTranslateClient`，不再保留第二份 URL 与解析逻辑。

## 不做

- 不给 `MangaLocator` / `MangaComposition` 增加引擎取值，避免继续混淆阶段与载体。
- 不为单一实现提前创建接口；第二个翻译引擎落地时再把 `translateText()` 一并升级为
  可注入的翻译策略。
- 不引入端侧离线翻译模型，除非另有产品结论。
- Google 失败不自动回退 AI，默认引擎保持 AI。

## S4 落地（2026-10-31）

- AI 识图 + Google：指定识图模型（`ocrModelId` 非空）时，识别与排版走该视觉模型，文字翻译交给
  Google；未指定（"与 AI 识别合并"）时，识别与排版由**翻译模型**兼任，行为与原来一致，只是文字
  阶段改由 Google 完成。识别模型与翻译模型是同一配置时仍只发一次识别请求（`models()` 解析出的
  `ocr` 与 `analysis` 指纹相同即合并），因此"翻译引擎跟随 AI 识图模型"的语义没有被拆散。
- 合并态要求翻译模型具备视觉能力：AI 引擎下本来就由 `models()` 强制，非 AI 引擎下沿用同一检查，
  失败时在发请求前报 `manga_ocr_vision_required`，不会静默降级。
- 可配置性随阶段走，不随引擎走：合并态下"翻译模型"就是识别模型，因此它和"思考强度"在非 AI 引擎下
  仍可见可改（`TEXT_MODEL` / `REASONING_LEVEL` 只在"既不翻译也不识别"时才置灰）；"AI 识图模型"下拉
  也始终提供"识别跟随翻译模型"，避免空值在列表里错位成第一个模型。
- `MangaTranslateEngine.FOLLOW_SETTINGS`：运行时读取 `TranslationSettingsGateway` 的 `provider`
  （`google` → Google，其余 → AI），因此全局设置变化后下一页即生效；分析身份按**解析后**的引擎
  计算，切换全局引擎不会复用旧译图。合并态跟随全局引擎同样成立：解析为 Google 时由翻译模型兼任
  识别，解析为 AI 时仍是一次请求。
- 设置分组与阶段同构，顺序改为 识别 → 翻译 → 合成：原文语言移入识别组，`OCR 引擎` 改为
  `识别方式`，`AI 识图定位` 改为 `AI 视觉识别`，`图片处理` 改为 `合成方式`，
  `跟随翻译模型` 改为 `与 AI 识别合并`（它表示与识别合并为一次请求，不是跟随全局设置）。
- 识别方式切回 AI 时不再把引擎归位为 AI，也不再改写「AI 识图模型」：合并只是"识别沿用翻译模型"，
  换引擎不改设置，由 `models()` 在运行时按引擎解析识别阶段。配置不再被设置项之间的切换污染。

## 复盘后修正

- **DI 缺绑定（会崩）**：`singleOf(::TranslateChapterUseCase)` 由 Koin 逐个 `get()` 解析构造参数，
  不支持 Kotlin 默认值。新加的 `GoogleTranslateClient` 已注册为 `single`，否则打开阅读器解析
  该用例时直接 `NoDefinitionFoundException`。凡是给 `singleOf` 注入类加“带默认值的构造参数”，
  都必须同时注册该类型。
- **整页重绘与文字引擎冲突**：切到 `AI_PAGE` 时同步把引擎归位 `AI`。否则保留 Google 的文字
  引擎会留下 `validateSettings` 拒绝的配置，表现为每页都报“需要独立的识别阶段”。
- **进度语义**：Google 分支不再用“区域数”改写阶段进度，避免进度在 band 计数与气泡数之间跳变。
- **core 降级会变成付费请求**：`usable()` 把本地识别回落成云端 AI 时，识别从免费端侧变成上传图片，
  若保留 `automatic` 开关，打开新页面就会静默计费。降级时一并把自动翻译置为暂停。识别方式、擦字
  引擎、气泡来源这三项被改写时，`MangaSettingsRelevance` 直接用 `storedSettings`（未降级的存储值）
  与生效值比较，得出"该端侧选择已不可用"。

## 设置面板提示（2026-11-02）

选项下方的逐条提示（引擎差异、被改写的原因、自动翻译被暂停）全部撤掉，改成"分组一句话 + Info 详情"：

- `ui/widget/components/card/InfoCard.kt` 只有一个实现：`InfoCard` 画浅底小块，圆角、内边距、
  文字样式与按钮都可替换。`InfoNote` 只是它的预设——更小圆角与内边距、次要文字色、按钮固定为
  Info，点开用 `MarkdownDialog` 展示 Markdown 详情——贴在各分组内部顶部；主题设置的重构提示直接用
  `InfoCard`。详情改用 Markdown 是因为档位与代价需要列表和强调，靠 `\n` 拼出来的纯文本没法分层。
- `MarkdownDialog`（`ui/widget/components/alert`）是新增的对话框载体：设置面板本身就是
  `AppModalBottomSheet`，用 sheet 展示详情会叠两层可下滑面板，返回手势也要按两次才关得掉；
  对话框只有一层，且沿用 `AppAlertDialog` 的双引擎外观。
- `MarkdownSheet` 仍供整页文档（关于页更新日志、书源说明）使用，并从 `ui/about` 移到
  `ui/widget/components/modalBottomSheet`：共享组件不该反向依赖某个页面包。
- 分组详情 = 静态说明（`manga_translation_{recognition,basic,processing}_detail`）+ 当前组合下的
  失效项。`mangaGroupDetail` 用 `MangaSettingsRelevance.inactiveOf/degradedOf` 按原因汇总，
  控件仍原位置灰，原因只在用户主动展开详情时出现，不再逐条占行。
- 自动翻译被降级暂停不再单独提示，改写进「识别」分组详情：没有端侧运行时，保存的端侧选择会回落，
  自动翻译同时暂停，以免它自行开始计费的云端识别。
- 「识别、翻译与合成的关系」从基础组的可点条目改为高级设置上方的 `InfoCard`：它横跨三个阶段，
  不归属任何一组，留在组内会被当成一个设置项。
- 面板顶部的「当前管线」摘要与关系弹窗首行摘要一并移除，`MangaTranslationPipelineText.kt` 删除：
  链路已由各分组的当前值表达，摘要只是把同一批值再拼一次，且在整页重绘等状态下会与置灰项重复。
- 文案取舍：分组说明只写"这一组决定什么"，代价与档位差异（计费、密钥、是否需要独立识别阶段）
  放进详情，避免同一句话在面板里出现两遍。

## 面板密度与术语表（2026-11-02）

单书面板挂在底部 sheet 里，行高应当比独立设置页更小：

- `MangaOptionItems.kt` 提供一层适配：`MangaOptionGroup` / `MangaOptionDropdown` / `MangaOptionSwitch` /
  `MangaOptionSlider` / `MangaOptionExpansion` 按 `LocalMangaOptionsTiny` 选择 Tiny 系列或完整设置项。
  密度由 `MangaTranslationOptions(tiny = ...)` 决定一次，各分组与子面板（云端 OCR、预处理、模型下载）
  通过 CompositionLocal 继承，不用逐层传参。全局设置页保持完整密度。
- Tiny 密度不套 `SplicedColumnGroup`：Tiny 条目本身就是卡片，再包一层会变成卡片套卡片。
- `TinyDropdownSettingItem` 补了 `enabled` / `unavailableValues` / `onUnavailableSelected`，与
  `DropdownListSettingItem` 对齐，sheet 里的"仅漫画增强版"档位仍然压灰、点了给 Toast。
- 「漫画翻译设置」入口只在 `useGlobalSettings` 为真时出现：本漫单独设置时它改不动眼前这些选项。
- 术语表 sheet：去掉自带的 16dp 边距（`AppModalBottomSheet` 已有内边距），条目改成 `TinyClickableSettingItem`；
  「添加词条」移到 `endAction`，是仅图标的 `MediumTonalButton`（`AppIcons.Add`）；取消保存按钮，
  增删改后立即 `EditGlossary` + `SaveGlossary` 落库，`manga_translation_save_glossary` 字符串随之删除；
  输入框换成 `AppTextField`。
- 诊断面板空态改用 `EmptyMessage`，与其它空列表保持一致。
- 卡片底色统一取 `LegadoTheme.colorScheme.onSheetContent`（sheet 内容的既定 token，见
  `AiAutoGroupSheet`）：Tiny 行通过适配层的 `color` 传，说明卡与关系卡通过 `InfoCard`/`InfoNote` 的
  `containerColor` 传。完整密度（全局设置页）仍用 `surfaceContainerLow`，只在 `tiny` 分支切换。
- tiny 密度下说明卡与关系卡跟着 Tiny 行的 12dp 圆角、12dp 内边距、底部 4dp 间距走（`InfoNote` 因此
  暴露了 `cornerRadius` / `contentPadding`）；完整密度仍是 2dp 窄块 + 16/8 内边距。
- 样式只走主题：这个目录里没有 `fontSize` / `.sp` / 字面色，文字一律 `LegadoTheme.typography.*`，
  颜色一律 `LegadoTheme.colorScheme.*`。唯一的例外是 `MangaTranslationStatus`（页面上叠的进度文字，
  `Color.White` + 阴影），它要压在任何漫画图上都读得清。

## 验证边界

本轮只移动代码与新增推导，不改变请求内容、缓存身份或渲染结果。

- `:app:compileAppDebugKotlin` 通过。
- `MangaTranslationPipelineTest` 11 组配置、`MangaGoogleTranslatorTest` 4 项（去重、并发上限、
  缓存命中、失败与空文本）通过；`MangaTranslationRepositoryTest` 新增 4 项（无 AI 模型可译且同文本只请求一次、
  失败不回落 AI 且不发布、AI 识图拆分后走 Google、跟随全局引擎），通过。
  `domain.model.manga.*`、`data.repository.manga.translation.*`、`feature.manga.*`、
  `feature.settings.manga.*` 共 378 项，全部通过。

## 顺带修掉的既有断点

此前 `MangaConfigScreenTest` 缺少 `onNavigateToBubble` 实参，单元测试源码集根本编译不过，
所以 `MangaTranslationSheetTest` 里 5 项失败一直没被看到。恢复编译后暴露并修复：

- `MangaTranslationDiagnosticsSheet` 的返回按钮改成图标按钮时漏了 `contentDescription`，
  debug 构建打开诊断面板会直接抛异常（`SeriesIconButton` 强制要求）。已补，测试改为按描述点击。
- 几何诊断预览已搬进诊断面板，两个测试仍在主面板找按钮；改为先点「诊断」设置项再断言预览与图例。
- 术语表测试在 `w400dp-h800dp` 下永不 idle：裸 `MaterialTheme` + `AlertDialog` + `OutlinedTextField`
  即可复现，与应用代码无关。该用例单独降到 `w320dp-h470dp` 并在注释里写明原因。
- 英文分组标题与「合成方式」原本都叫 `Composition`，导致按文本查找命中两个节点；
  合成方式改为 `Composition method`（中文本就是「合成」/「合成方式」，无歧义）。

真实 Google 端点、限流、逐条延迟与术语缺失的实际质量尚未验证；S3 之前不应宣称漫画翻译
可切换到非 AI 引擎。
