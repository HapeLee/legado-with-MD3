# 外部 HTTP TTS 兼容问题排查与交接

记录日期：2026-10-17（Asia/Hong_Kong）。

## 项目与排查范围

- 正确项目：`D:\Project\legado-with-MD3`。
- 仓库：<https://github.com/HapeLee/legado-with-MD3>。
- 排查时 HEAD：`6974b5a852a5d29618e144e5974696b6bd25d7f1`。
- 重点提交：`d41dc7eeaf2c6d29ee9b17bf31c6094b832bec66`，`feat(tts): 兼容外部 httpTTS 源的发言人目录与并发声明`。
- 用户报告的发行版本：`3.26.16-beta.48`，versionCode `18645`。
- 外部格式参考项目：`D:\Project\Legado-Arc\legado`。
- 原聊天的工作目录是 `D:\Project\One`，那是另一个 Android 项目，不应在其中修复本问题。

本次已完成诊断和代码审查，尚未修改业务代码、数据库结构或应用原数据库。本文件用于回到正确项目后继续处理，不表示下述修复已经实现。

## 已有证据及其边界

1. 用户最初提供 Xiaomi / Redmi、Android 16 的混淆异常，读取 `CursorWindow` 时报告 `row 730, col 0`。
2. 用户随后在 OnePlus PLK110、Android 17、debug 包中复现，提供了未混淆堆栈。debug 包名为 `io.legato.kazusa.debug`。
3. 在已连接的 OnePlus 上，读取 debug 应用数据库文件到临时目录，使用副本完成完整性检查和隔离实验；没有修改原数据库。
4. 对照重点提交、当前源码及 Legado-Arc 的外部格式，检查了导入、目录同步、删除、试听、正式朗读、导出和备份。
5. 从副本中的一个已导入源提取情绪转换函数，单独执行其纯函数部分验证标签兼容性，没有调用外部合成接口。

发行版 mapping 的 release 资产已确认存在，但未下载和完成 retrace。debug 堆栈及隔离实验已直接定位此次复现；不能把发行版堆栈称为已精确还原。

## 1. [P1] 音色全表读取与并发删除导致 CursorWindow 崩溃

### 调用链

```text
CloudTtsViewModel.deleteHttpTts
  → SyncHttpTtsCatalogUseCase.clear
    → SyncReadAloudVoicesUseCase.invoke
      → ReadAloudVoiceRepository.getVoices
        → ReadAloudVoiceDao_Impl.getVoices
          → CursorWindow.getString
```

debug 堆栈中的关键位置：

```text
ReadAloudVoiceDao_Impl.java:254
ReadAloudVoiceRepository.kt:22
SyncReadAloudVoicesUseCase.kt:34
CloudTtsViewModel.kt:981
```

### 原因

删除 HTTP 源后，目录清理会调用 `getVoices()` 扫描整张音色表。同时，页面的引擎监听触发 `pruneAndPublish()`，通过 `voiceGateway.deleteVoice()` 删除已失去引擎的默认音色。

列表查询没有事务保护。查询结果较大、需要重新填充 CursorWindow 时，并发删除使后续查询结果变短，但 Cursor 仍持有之前统计的行数，最终访问窗口中不存在的行。`SyncReadAloudVoicesUseCase` 内部的 Mutex 不能约束页面直接发起的删除。

`d41dc7e` 导入大量目录音色并新增删除后的目录清理，使这条竞争路径暴露出来。不能将本异常解释成单行音色过大或设备特有的问题。

### 验证

副本中共有 9,971 条音色，其中外部目录音色 9,946 条；`PRAGMA quick_check` 为 `ok`，最大单行文本合计约 2 KB。

在同一台手机上，以独立 Java/Dex 探针通过 `app_process` 操作隔离数据库副本：

| 实验 | 结果 |
| --- | --- |
| 静态全表读取 | 全部成功 |
| 读取期间，另一线程删除一条 `configured_tts` 音色 | 精确复现 `Couldn't read row 604, col 0 from CursorWindow` |
| 用事务保护读取，另一线程尝试同样的删除 | 全部读取成功，删除随后完成 |

探针和设备上的隔离数据库已清理。本地临时副本用于诊断，不应提交到仓库。异常中的行号不要直接当作某个固定坏音色的定位依据。

### 建议修复

- 对会跨窗口读取的音色列表查询提供一致性保护，重点检查 `getVoices()`、`observeVoices()`、`getEnabledVoices()` 的 `@Transaction` 行为。
- 引擎删除与其目录清理在数据层统一执行，避免页面和同步 UseCase 各自逐行删除。
- 页面统计排除音色数量改用 SQL `COUNT(*)`，避免为一个计数反复读取全部音色。
- 查询事务解决读取竞争；删除操作的原子性和失败恢复仍须单独处理。

相关文件：

- [ReadAloudVoiceDao.kt](../app/src/main/java/io/legado/app/data/dao/ReadAloudVoiceDao.kt)
- [SyncReadAloudVoicesUseCase.kt](../app/src/main/java/io/legado/app/domain/usecase/SyncReadAloudVoicesUseCase.kt)
- [SyncHttpTtsCatalogUseCase.kt](../app/src/main/java/io/legado/app/domain/usecase/SyncHttpTtsCatalogUseCase.kt)
- [CloudTtsViewModel.kt](../app/src/main/java/io/legado/app/ui/book/readaloud/cloudtts/CloudTtsViewModel.kt)

## 2. [P1] 管理页试听将 HTTP 音色错误地送入云 TTS 分支

用户反馈导入的音色无法使用，提示“云 TTS 引擎不存在”。已经找到该提示的确定触发路径。

`CloudTtsViewModel.previewVoice()` 将音色转换成编辑器模型，再调用 `previewWith()`。后者只区分系统 TTS 和其余情况：

```kotlin
if (editor.engineType == ReadAloudVoice.ENGINE_SYSTEM) {
    // 系统合成
} else {
    val engine = engines.firstOrNull { it.id == editor.engineId }
        ?: error(application.getString(R.string.cloud_tts_engine_missing))
    // 云合成
}
```

外部目录音色的类型是 `ENGINE_HTTP`，其 ID 对应 `httpTTS` 源，而 `engines` 是云引擎列表。因此查询失败，请求尚未发给外部源。

建议显式区分系统、云、HTTP 三类。HTTP 试听应复用 HTTP 源的脚本执行、请求头、Cookie、登录检查、语速及音色变量处理，不要另写一个仅拼 URL 的简化网络请求。

位置：`CloudTtsViewModel.kt`，排查时约 571–606 行。

正式朗读的 `HttpReadAloudService.synthesizeSingleCue()` 已有独立 HTTP 分支。这条提示能够证明管理页试听缺陷，不能单凭它认定正式朗读的所有路径都不可用。

## 3. [P1] 备份及 HTTP 源导出遗漏目录，换机后无法还原音色

`speakersJson` / `emotionsJson` 只在导入时解析，目录权威副本在 `read_aloud_voices`，`HttpTTS` 实体不保存这两个字段。

但是：

- `Backup.kt` 约 188 行，只将 `appDb.httpTTSDao.all` 写入 `httpTTS.json`。
- 备份文件清单和写入逻辑没有包含音色表。
- `CloudTtsViewModel.exportHttpTtsFile()`、`exportHttpTtsUrl()` 同样只序列化 `HttpTTS` 列表。
- `Restore.restoreHttpTtsCatalogs()` 依赖原 JSON 中的目录字段，无法从本应用生成的上述文件恢复缺失内容。

因此，在新安装或新设备上恢复这些文件会丢失目录音色和情绪数据；排除记录也没有随标准备份保存。旧设备原表仍存在时，恢复流程可能保留旧行，从而掩盖问题。

建议明确持久化与传输契约：标准备份应保存音色及其用户状态；面向外部格式的源导出应从目录表重建兼容字段。不能只在恢复入口再调用一次解析器。

相关文件：

- [Backup.kt](../app/src/main/java/io/legado/app/help/storage/Backup.kt)
- [Restore.kt](../app/src/main/java/io/legado/app/help/storage/Restore.kt)
- [HttpTTS.kt](../app/src/main/java/io/legado/app/data/entities/HttpTTS.kt)

## 4. [P1] 删除中断留下可用的孤儿音色，合成时可能用错源

`deleteHttpTts()` 先删除源行，再调用目录清理，两个步骤不具备整体原子性。如果后者异常或任务取消，目录会残留。

`pruneAndPublish()` 对 HTTP 孤儿的判断只允许 `MANAGED_BY_CONFIGURED_TTS`，漏掉新引入的 `MANAGED_BY_HTTP_SOURCE_CATALOG`，重进页面也不能自动清掉这些目录音色。

实际副本中已经有 **10 条外部目录音色**：对应 HTTP 源不存在，但仍是 `available = true`。这证明孤儿状态确实存在；仅凭副本不能断言每条残留的历史成因。

更进一步，`HttpReadAloudService` 多处使用：

```kotlin
val itemHttpTts = routedVoice.engineId.toLongOrNull()
    ?.let(appDb.httpTTSDao::get) ?: httpTts
```

缺失源时会使用默认源，却保留原音色 ID，可能把 A 引擎的音色参数发给 B 引擎。应验证实际引擎存在，再决定完整的备用音色路由，避免只替换源。

建议同时处理原子删除、已有孤儿的修复、候选有效性和合成兜底。清理时须考虑已排除的行，不能只依赖 UI 当前可见列表。

位置：`CloudTtsViewModel.kt` 约 147、976 行；`HttpReadAloudService.kt` 约 1080 行及其他源查找分支。

## 5. [P2] 共用试听组件也没有 HTTP 合成能力

[VoiceAudition.kt](../app/src/main/java/io/legado/app/help/readaloud/cast/VoiceAudition.kt) 的 `when (voice.engineType)` 只支持云和系统，HTTP 走 `else -> false`。

该实现由 `VoiceAuditionButton` 等共用组件调用，影响角色编辑、正文配音弹层、变声器和规则页等入口。即使管理页的“云 TTS 引擎不存在”修好了，这些位置仍然无法试听外部音色。

建议将 HTTP 音色合成能力放在可复用的数据/平台能力层，让管理页与共用试听入口共享同一套合成语义，并保留取消和错误信息。

## 6. [P2] 情绪目录没有参与实际标签映射，且缺少兼容变量

`SyncHttpTtsCatalogUseCase` 将 `draft.emotions` 存入每条音色的 `emotionCatalogJson`，但 HTTP 合成没有读取它做映射。

播放队列保留内部 `segment.emotion`，HTTP 请求直接将该值传给 `currentEmotionTag`。内部枚举包含 `cheerful`、`fearful`、`whispering` 等，外部源可能声明 `happy`、“开心”、“害怕”等不同标签。

对已导入讯飞源的 `selectedEmotion()` 纯函数做了独立执行：

```text
cheerful -> neutral
happy    -> happy
sad      -> sad
calm     -> neutral
```

这证明至少该源的开心情绪无法由内部 `cheerful` 正确表达。其他源是否拒绝参数、忽略参数或自行映射，要按源验证，不能一概认定都会报错。

另外，Legado-Arc 的 `AnalyzeUrl` 和合成调用同时提供 `currentEmotionName` 与 `currentEmotionTag`；本次兼容只新增了 tag，没有提供 name。依赖名称的外部脚本不完整兼容。

建议建立显式的内部情绪到源目录的映射，并为无法匹配的情绪定义安全的默认行为；同时补全名称变量。避免简单地把全部中文或英文标签强行视为同一种格式。

相关文件：

- [HttpTtsCatalogDraft.kt](../app/src/main/java/io/legado/app/domain/model/readaloud/HttpTtsCatalogDraft.kt)
- [SpeechEmotion.kt](../app/src/main/java/io/legado/app/domain/model/readaloud/SpeechEmotion.kt)
- [AnalyzeUrl.kt](../app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt)
- [HttpReadAloudService.kt](../app/src/main/java/io/legado/app/service/HttpReadAloudService.kt)

## 7. [P2] 多源混播没有按实际引擎执行并发上限

`synthesisConcurrency(httpTts)` 只读取默认源的 `synthesisThreadCount`。章节任务共享按该值创建的 Semaphore，而每条 cue 可以路由到其他源。

例如，全局并发 8，默认 A 上限 8，角色使用的 B 上限 1：若一批 cue 都路由到 B，现有代码仍可能同时向 B 发出多个请求。

此外，正在播放的合成与后台预合成没有共享一个源级配额，单独限制后台并发也不等于限制源的总并发。

建议保留全局任务上限，同时按实际 HTTP 引擎标识共享源级限流。需要覆盖实时合成、预合成、流式和按章下载；不能只修改某一处 Semaphore 的数字。

位置：`HttpReadAloudService.kt` 约 156、532、992、1278 行。此项通过控制流确认，尚未进行真实服务限流压测。

## 8. [P2] 批量全选漏掉外部目录音色

`isRemovable()` 已允许用户音色和外部目录音色，但 `ToggleSelectAllVoices` 仍只筛选 `MANAGED_BY_USER`。

表现是单条可以选外部音色，“全选”却选不到它们，无法按预期批量加入声音池或排除。

建议单选、全选、批量删除和添加到声音池共用一致的候选判定，并根据实际集合关系判断当前是否全选，避免只比较数量。

位置：`CloudTtsViewModel.kt` 约 272–281、1184 行。

## 建议的修复顺序与验收

| 顺序 | 工作 | 最小验收 |
| --- | --- | --- |
| 1 | 查询一致性、原子删除、孤儿修复 | 上万音色下删除引擎、并发读取、取消任务不崩溃；不产生或继续选中孤儿 |
| 2 | 两套试听入口接入 HTTP 能力 | 管理页和共用试听均使用正确源、正确 toneID；取消不显示误导错误 |
| 3 | 备份、导出、恢复契约 | 空库恢复后音色、情绪和用户状态符合约定；源导出再导入保留目录 |
| 4 | 情绪和外部变量兼容 | `cheerful` 等内部值能转换为源接受的 tag；脚本能读取名称与标签 |
| 5 | 源级并发限制 | 默认 A 与角色 B 上限不同，实时和后台同时运行也不超过 B 的配额 |
| 6 | 批量选择 | 混合用户音色和目录音色时，全选、取消全选、加入池、排除均一致 |

建议补充覆盖：

- 同源重复导入不翻倍；同步 B 不删除 A；排除记录在重新导入时保留。
- 导入或删除中断后重新进入页面，目录和引擎状态仍一致。
- 默认音色、角色绑定、流式与文件合成、后台预下载、离线下载均走正确源。
- 同一句换音色或情绪时不会命中其他声音的缓存。
- debug 与开启 R8 的 release 均验证序列化兼容，特别是新目录模型的字段名稳定性。此点尚未证明存在独立的 R8 缺陷。

后续实现遵循仓库 `AGENTS.md` 和相应实现 skill；新增数据操作通过 Gateway/Repository/UseCase 承载，不继续向 ViewModel 增加 DAO/网络直连。

按最终改动选择验证命令，通常包含针对性单元/数据库测试、`:app:compileAppDebugKotlin` 和 `git diff --check`。本次审查未改业务代码，没有运行完整构建；不要将隔离探针通过当作整个应用修复已经验收。

## 在正确项目中继续时可使用的任务描述

> 请先阅读 `AGENTS.md` 和 `docs/http-tts-compatibility-audit-2026-10-17.md`，核对当前 HEAD 与文档中的代码差异。目标是继续处理外部 HTTP TTS 兼容问题，参考提交 `d41dc7eeaf2c6d29ee9b17bf31c6094b832bec66`，外部格式参考 `D:\Project\Legado-Arc\legado`。文档记录了诊断证据，修复尚未实现；按照本次实际授权的范围继续，不要假定任何问题已经修好。保留工作区已有改动，不修改用户原数据库来绕过问题。
