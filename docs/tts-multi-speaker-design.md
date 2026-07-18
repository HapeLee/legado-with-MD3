# 多角色朗读设计方案

> 实现进度：章节进入朗读服务时，已经执行并缓存“确定性分段 → 本地人物识别 →
> 角色音色绑定”的 `speechPlan`。HTTP 与系统 TTS 服务均已采用带绝对章节位置的播放单元，
> 并启用逐片段音色路由，以保持页面高亮与上一段/下一段行为一致。
> 运行时 `ReadAloudPlaybackQueue` 已建立绝对章节坐标与片段内偏移模型，并已随章节计划载入服务；
> 当前已接管两类朗读服务的播放完成回调。

HTTP 朗读现已率先接管运行时片段队列和播放完成回调，并按片段选择角色绑定的 HTTP
声源。不可用、被禁用或类型不兼容的音色会依次使用绑定回退和当前全局 HTTP TTS；音频缓存键
包含实际声源 URL，避免相同文本在不同角色声源间误用缓存。

系统 TTS 已采用单片段协调模式：依据当前角色音色按需切换 Android TTS 引擎，相同引擎复用，
不同引擎先停止并销毁旧实例再初始化新实例。初始化使用代次编号丢弃迟到结果，朗读回调使用片段
ID 丢弃旧引擎的迟到事件，避免错误推进播放游标。

后续章节的文件式与流式 HTTP 音频预下载也使用相同的角色分析和路由结果。预下载章节若无法
生成计划，只回退该章节的原段落缓存，不改变当前播放会话。

跨类型混播由 HTTP/ExoPlayer 服务承担统一音频时间线：HTTP 音色直接下载，系统 TTS 音色通过
`synthesizeToFile` 生成临时音频后进入同一队列。检测到系统音色时流式模式自动转为文件队列。
每次新朗读会话都会根据当前书籍绑定重新选择服务，因此默认系统 TTS 加角色 HTTP TTS、默认
HTTP TTS 加角色系统 TTS 两个方向都能进入统一协调器。

云 TTS 已从对话 AI Profile 中独立出来，使用统一的 `CloudTtsEngine` 与云音色目录。当前支持
OpenAI Speech、Gemini TTS、MiMo、Azure Speech、阿里云百炼、Amazon Polly 与火山引擎。
引擎集中保存厂商、凭据、区域、模型和扩展参数；人物绑定只引用稳定的云音色 ID，因此修改
密钥、区域或模型不需要重建人物配音。

云 TTS Compose/MVI 管理页是唯一的在线模型 TTS 入口，可新增、编辑、删除引擎，获取厂商音色，
或手动填写 Voice ID。每个音色可配置输出格式、速度、音调、音量、语言、原生风格、角色风格
和 instruction。Azure 与 AWS 使用在线音色发现，其余厂商提供内置目录并保留手动输入能力。

云 TTS 编辑器支持真实连接测试与试听：两者都调用实际供应商生成短音频，试听文件只写入应用
缓存并由临时播放器播放。听书页复用现有朗读服务和目录导航，提供章节前后切换、暂停继续、
章内进度、目录入口，以及当前角色和引擎展示。

原 `ReadAloudPlayerSheet` 已由全屏 `ReadAloudPlayerScreen` 替代。听书页包含悬浮返回/设置按钮、
1:1 封面、两行当前朗读文本、章内可拖动进度、上下章与播放控制、目录/语速/定时入口。背景
提取为可复用 `CoverBlurBackdrop`，使用封面全屏裁切、24dp 模糊、主题取色遮罩与 surface 遮罩，
不使用渐变。设置 Sheet 可进入 TTS 引擎、多角色配音、云 TTS 引擎或返回阅读页原朗读设置。

听书主视觉改为双页 `HorizontalPager`：初始页为封面，向右滑显示纯章节文本页；文本页对当前
朗读片段做背景高亮。页面按钮统一优先使用 Medium 系列组件，文本使用 `AppText` 与
`LegadoTheme.typography` 层级。进入人物配音、云 TTS 或原设置前会先关闭设置 Sheet，避免子页
返回时 Sheet 重现、闪烁或干扰返回手势。

## 1. 目标与边界

目标是在不破坏现有单发言人朗读的前提下，支持：

- 旁白、人物对白、人物心理活动使用不同声音；
- 直接复用 `book_character_profiles` 中的角色 ID、姓名、别名、角色类型、性格和置信度；
- 系统 TTS 与多个 HTTP TTS 可以混合编排，并有稳定的降级策略；
- 章节可以先用规则结果立即播放，AI 只增量处理不确定台词，不阻塞普通朗读；
- 用户可以预览、修正台词归属并锁定人物声音；
- 后续可以增加情绪、音量归一化、场景配乐和音效，而不重写主链路。

首期不建议同时实现 BGM、音效、自动头像生成和复杂情绪演绎。先把“文本片段 -> 角色 -> 声音 -> 有序音频”做稳定，再逐层增加表现力。

## 2. 现状结论

### 2.1 当前分支

当前 `codex/book-knowledge-nav3` 已有 `BookCharacterProfile`、事件、关系、知识条目和大纲。角色档案适合作为全应用唯一的角色身份源：

- `id` 是稳定字符串，可供台词片段和声音绑定引用；
- `name` 与 `aliasesJson` 可用于显式说话人匹配；
- `role`、`tagsJson`、`personality` 可作为自动选音提示；
- `source`、`confidence`、`status` 可区分用户确认与 AI 候选。

当前朗读仍以 `ReadAloud` 在 `TTSReadAloudService` / `HttpReadAloudService` 二选一为核心，`BaseReadAloudService` 只保存字符串段落。它没有“片段角色”和“每片段声音路由”的概念。

### 2.2 archive-v3-3.26.07062303 中值得复用的设计

- `ReadAloudCue` 保留章节字符位置、页面位置和稳定播放顺序；
- `ReadAloudRolePreprocessor` 先用引号、冒号和心理活动提示词切分候选对白；
- `ReadAloudSpeechPlanner` 补齐未覆盖区间并把角色片段转换为播放项；
- `SpeechRoute` 抽象系统 TTS / HTTP TTS、发言人、音色和情绪；
- HTTP TTS 通过 `currentToneID`、`currentSpeakerName`、`currentEmotionTag` 等脚本变量支持原生发言人目录；
- 音频缓存键包含引擎、声音、语速和文本，且支持按引擎限制合成并发；
- 下一章预分析、合成失败降级、响度学习等方向是合理的。

### 2.3 archive 实现不应直接移植的部分

- `AiReadAloudRoleService` 约 3000 行，混合了分段、AI、数据库、缓存、人物创建、头像生成、声音分配和 UI 状态，难测试也难替换；
- 开启多角色后，首次播放会等待 AI，AI 不可用时可能直接阻断朗读；
- 它另建 `book_characters`，会与当前 `book_character_profiles` 形成两个角色真相源；
- 声音路由以 JSON 塞进角色行，声音被删除或 TTS 更新后缺少可靠引用关系；
- 章节片段以大块 `segmentsJson` 保存，无法高效查询、局部修正和精确失效；
- 角色识别缓存混入 `voiceHash`，换声音会触发本不需要的语义缓存处理；
- 角色分析输入会受“按页朗读”切分影响。同一章节切换显示/朗读设置后，语义片段不应变化；
- 多引擎调度、系统 TTS 转文件和所有降级逻辑集中在 `HttpReadAloudService`，职责失衡；
- Activity/DialogFragment 与直接 `appDb` 访问不符合当前 Compose + MVI + Gateway 约定。

## 3. 推荐架构

主链路分成五层，层与层之间只传不可变模型：

```text
CanonicalChapter（与分页无关）
  -> SpeechSegmenter（纯规则切分）
  -> SpeakerResolver（本地匹配 + 可选 AI 增量解析）
  -> SpeechPlanBuilder（关联人物与声音绑定）
  -> SpeechPlaybackCoordinator（合成、缓存、排序、播放、降级）
```

关键原则：角色识别结果与声音渲染配置分开缓存。修改人物姓名可能需要重新解析，修改人物声音只需要重新合成受影响的音频。

### 3.1 领域模型

建议放在 `domain/model/readaloud/`：

```kotlin
data class CanonicalSpeechCue(
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val chapterPosition: Int,
)

enum class SpeechRoleType { Narrator, Character, Thought, Unknown }

data class ResolvedSpeechSegment(
    val cue: CanonicalSpeechCue,
    val roleType: SpeechRoleType,
    val characterId: String?,
    val characterName: String,
    val emotion: String?,
    val confidence: Float,
    val source: ResolutionSource,
)

data class VoiceRoute(
    val voiceId: String,
    val engineType: EngineType,
    val engineId: String?,
    val speakerId: String?,
    val speakerName: String,
    val emotionTag: String?,
)

data class SpeechPlanItem(
    val segment: ResolvedSpeechSegment,
    val route: VoiceRoute,
    val fallbackRoutes: ImmutableList<VoiceRoute>,
)
```

不要让领域模型依赖 `TextChapter`、Room Entity、`appDb` 或 Service。

### 3.2 数据库模型

保留现有 `book_character_profiles`，不要引入第二套人物表。建议新增四张表：

1. `read_aloud_voices`
   - 全局声音目录；字段包含 `id`、`engineType`、`engineId`、`speakerId`、`displayName`、`traitsJson`、`emotionCatalogJson`、`enabled`、`revision`。
   - `id` 使用稳定业务键的哈希，而不是自增 ID；业务键由引擎类型、引擎 ID、发言人 ID 组成。

2. `book_voice_bindings`
   - 一本书内“人物或旁白”到声音的显式绑定；字段包含 `bookUrl`、`subjectType`、`subjectId`、`voiceId`、`locked`、`source`、`confidence`、`updatedAt`。
   - `subjectType` 首期只需 `character`、`narrator`、`unknown_male`、`unknown_female`、`unknown`。
   - 人物绑定的 `subjectId` 直接引用 `BookCharacterProfile.id`。

3. `chapter_speech_analysis`
   - 记录章节内容哈希、解析器版本、人物库版本、状态、错误和时间；不包含声音版本。

4. `chapter_speech_segments`
   - 每个语义片段一行；包含章节、段落与字符区间、`characterId`、角色类型、情绪、来源、置信度、是否用户锁定。
   - 唯一键建议为 `analysisId + paragraphIndex + start + end`。

不要把发言人字段直接加到 `BookCharacterProfile`。人物知识和朗读偏好生命周期不同；同一人物还可能按书、语言或场景选择不同声音。

### 3.3 人物库的使用方式

本地解析优先级：

1. 用户锁定的章节片段；
2. 明确的“姓名/别名 + 说/问/答/喊”等归属句式；
3. 当前对话窗口中最近的已确认说话人；
4. 角色姓名与 `aliasesJson` 的精确匹配；
5. AI 只处理仍为 `Unknown` 的对白单元；
6. 仍无法确定时使用 `unknown_male` / `unknown_female` / `unknown`，最终降级为旁白。

别名应在加载人物列表时解析成归一化索引，禁止在 SQL 中用 `aliasesJson LIKE` 判断台词归属。归一化至少处理空白、书名号、引号、常见称谓后缀，但不能做模糊子串匹配，以免把短人名误命中普通词。

`role` 可用于推断性别与重要程度，`tagsJson` / `personality` 可用于声音特征排序，但只能作为选音提示，不能作为角色身份判断。现有四个 role 常量无法覆盖未知性别、非人角色和群体角色，因此自动选音必须允许“不确定”。

### 3.4 规则与 AI 协作

规则分段器必须是纯 Kotlin、确定性、可单测的组件。它在规范化章节段落上工作，不依赖分页。输出必须覆盖全文且区间不重叠。

AI 请求只发送：

- 未确定对白的稳定 `segmentId`；
- 前后少量上下文；
- 当前书中 active 人物的 ID、姓名、别名和必要标签；
- 严格结构化输出 schema。

AI 返回后执行本地验证：segmentId 必须存在、区间不可改变、characterId 必须存在；未知人物只能作为候选 `BookCharacterProfile(status = DRAFT, source = AI)`，默认不自动变成 active。用户确认或达到明确策略阈值后再保存。

播放不等待 AI：

- 当前段落已有规则/缓存结果时立即建立计划；
- 未解析对白先用未知角色或旁白声音播放；
- AI 在后台完成后，只更新尚未进入合成队列的片段；
- 下一章可在当前章播放期间预解析。

这样离线、无 AI 配置、AI 超时和 token 不足都不会破坏基础朗读。

### 3.5 声音目录与自动分配

HTTP TTS 的发言人目录不要只存在于临时 JSON。导入或编辑 TTS 时由 `VoiceCatalogRepository` 同步到 `read_aloud_voices`。为兼容现有规则脚本，合成请求向 `AnalyzeUrl` 增加以下只读变量：

- `currentSpeakerId`
- `currentSpeakerName`
- `currentEmotionTag`
- `currentVoiceRouteJson`

自动分配先过滤硬条件，再做稳定排序：

- 硬条件：声音启用、引擎可用、语言匹配；
- 软条件：性别、年龄感、角色重要度、性格标签；
- 稳定选择：用 `bookUrl + characterId` 在同分候选中稳定取值，避免每次朗读换声音；
- 用户绑定一旦 `locked = true`，任何自动过程都不能覆盖。

旁白必须有独立绑定。人物声音合成失败时的固定降级顺序为：人物备用声音 -> 书籍未知角色声音 -> 书籍旁白 -> 全局默认声音。不要随机换到另一个人物声音。

### 3.6 合成与播放

新增统一 `SpeechPlaybackCoordinator`，Service 只负责 Android 生命周期、MediaSession、AudioFocus、通知和命令转发。

```kotlin
interface SpeechSynthesizer {
    fun supports(route: VoiceRoute): Boolean
    suspend fun synthesize(request: SynthesisRequest): SynthesisResult
}
```

提供 `SystemTtsSynthesizer` 和 `HttpTtsSynthesizer`。协调器按计划顺序播放，但允许不同引擎并行预合成；每个引擎使用独立 `Semaphore`，并发数受引擎配置限制。

音频缓存键至少包含：

```text
chapterContentHash + segmentRange + normalizedTextHash +
voiceId + voiceRevision + emotionTag + speechRate + synthesizerVersion
```

预取窗口建议从 3 个片段开始，按实际合成耗时动态扩展，而不是一次性合成整章。播放位置始终保存章节绝对字符位置，页面高亮只是该位置到当前布局的投影。

### 3.7 Compose / MVI UI

不把 archive 的 DialogFragment 搬过来。建议新增独立 Navigation 3 目的地：

- `ReadAloudVoiceManageScreen`：声音目录与试听；
- `BookVoiceCastingScreen(bookUrl)`：旁白和人物声音绑定；
- `ChapterSpeechPreviewScreen(bookUrl, chapterIndex)`：查看、修改和锁定台词归属。

每个功能按 `Contract + ViewModel + Screen` 拆分，UiState 使用 `@Stable` 与 `ImmutableList`。Reader 的现有 `ReadBookContract` 只增加打开入口、模式开关和轻量播放状态，不应继续塞入所有配音管理状态。

朗读面板首期显示当前说话人、声音名和“解析中/规则/AI/用户确认”来源即可。人物卡的配音入口跳转到 `BookVoiceCastingScreen`，共享同一 `BookCharacterProfile.id`。

## 4. 建议实施顺序

### Phase 1：可用的手动多发言人

- 新表、DAO、Gateway、Repository 与迁移；
- 规范化 cue 与规则分段器；
- 声音目录、旁白/人物手动绑定和试听；
- `SpeechPlanBuilder` 与统一合成协调器；
- HTTP TTS 脚本变量与稳定缓存键；
- AI 完全关闭时仍可用。

验收：手工给两个人物和旁白绑定三个声音，含同段旁白+对白的章节可连续播放、跳段、切章并恢复正确位置。

### Phase 2：自动角色识别

- 基于姓名/别名/说话动词的本地 resolver；
- 只处理 Unknown 片段的 AI gateway；
- 章节预览、人工修正、锁定与局部重算；
- 下一章后台预解析。

验收：AI 离线或超时时朗读不中断；用户修正后再次播放不被 AI 覆盖；切换“按页朗读”不导致重新识别。

### Phase 3：表现力与性能

- 情绪目录与按片段情绪；
- 响度归一化；
- 自适应预取和磁盘缓存治理；
- 可选 BGM / 音效轨道，使用独立 mixer，不进入角色识别服务。

## 5. 测试清单

- 分段：中英文引号、跨段引号、嵌套引号、冒号对白、引用/书名号、心理活动、空段和图片标记；
- 区间：所有输出在原文范围内、无重叠、无丢字，合并后等于规范化输入；
- 人物：本名、别名、同姓短名、未知性别、角色删除/禁用、用户锁定；
- 缓存：改声音不重跑角色识别，改人物别名只使相关章节解析过期，改语速只使音频缓存过期；
- 播放：系统/HTTP 混合、单引擎失败、超时、暂停恢复、跳段、切章、进程重建、来电与音频焦点；
- MVI：所有用户操作经 Intent，导航/Toast 经 Effect，Screen 不访问数据库或 Service；
- 回归：关闭多角色时行为与现有单发言人朗读一致。

## 6. 第一批建议文件

```text
domain/model/readaloud/*
domain/gateway/ReadAloudVoiceGateway.kt
domain/gateway/ChapterSpeechGateway.kt
domain/usecase/BuildSpeechPlanUseCase.kt
domain/usecase/ResolveChapterSpeakersUseCase.kt
data/entities/ReadAloudVoice.kt
data/entities/BookVoiceBinding.kt
data/entities/ChapterSpeechAnalysis.kt
data/entities/ChapterSpeechSegment.kt
data/dao/ReadAloudVoiceDao.kt
data/dao/ChapterSpeechDao.kt
data/repository/ReadAloudVoiceRepository.kt
data/repository/ChapterSpeechRepository.kt
help/readaloud/segment/*
help/readaloud/synthesis/*
model/readaloud/SpeechPlaybackCoordinator.kt
ui/book/readaloud/voice/*
ui/book/readaloud/casting/*
ui/book/readaloud/preview/*
```

建议先提交 Phase 1 的数据库与纯 Kotlin 分段测试，不要从 archive 一次性复制大批文件。archive 更适合作为行为参考和测试样本来源。
