# HTTP TTS 目录、合成与备份契约

本实现对应 `docs/http-tts-compatibility-audit-2026-10-17.md` 的八项问题。

| 审计项 | 修复落点 | 回归证据 |
| --- | --- | --- |
| 1. 跨窗口读取与删除竞争 | DAO 查询事务、SQL 计数、统一存储事务 | 一万行目录并发读取/删除、取消与失败回滚 |
| 2. 管理页 HTTP 试听 | HTTP 分支调用共享合成器 | 本地 HTTP 请求变量、脚本与登录检查 |
| 3. 备份及导出遗漏目录 | HTTP 音色快照、目录导出器、原子恢复 | 空库恢复、用户状态及外部目录往返 |
| 4. 孤儿音色与错误源兜底 | 原子删除、历史清理、候选及路由检查 | 排除行清理、迟到写入、缺失源完整回退 |
| 5. 共用试听缺少 HTTP | `VoiceAudition` 使用同一合成器 | 共用试听真实本地请求及取消清理 |
| 6. 情绪标签及名称 | 目录映射、默认音色回查、双变量 | 中英文别名、默认值、脚本请求参数 |
| 7. 多源并发上限 | 进程内按实际源共享配额 | 不同源独立、同源等待、流关闭及取消释放 |
| 8. 批量全选遗漏目录 | 共用候选判定及集合关系 | 混合候选、同数量不同集合、取消全选 |

## 存储与删除

- 目录的权威副本仍是 `read_aloud_voices`，不新增数据库列或第二份可编辑目录。
- 音色列表的 Room 查询使用事务，跨 CursorWindow 读取期间保持一致视图；排除数量使用 SQL COUNT。
- `HttpTtsRepository` 统一提交源和目录的导入、删除、恢复。同步 UseCase 使用相同存储事务，
  不再使用无法约束其他写入方的独立 Mutex。
- 删除源会删除其全部 HTTP 音色，包括用户音色、默认音色和被排除的目录行。
- 管理页进入时按源表修复历史孤儿，包括排除行。候选查询也排除没有源的 HTTP 音色。
  播放队列中的旧音色还会在路由时验证源，选择完整备用音色；合成阶段发现源消失则报错，
  不会保留旧 toneID 向另一源发请求。删除前生成的旧同步草稿在写入时再次检查源，避免迟到写入重建孤儿。

## 合成与试听

- `HttpTtsAudioSynthesizer` 由管理页试听、共用试听、实时合成、后台合成和流式读取复用。
  所有入口继续经由 `AnalyzeUrl` 执行源脚本、请求头、Cookie 和登录检查。
- `currentToneID`、`currentSpeakerName`、`speakSpeed` 使用实际源及音色；默认音色使用空 toneID，
  不发送编辑器占位 ID。
- 情绪先精确匹配目录，再匹配有限的中英文同义标签；不支持的情绪使用目录中的默认/中性项，
  没有默认项时传空值，由源决定默认行为。未声明目录的旧源仍接收原 tag。
- `currentEmotionName` 与 `currentEmotionTag` 同时取自匹配的目录项。默认/手工音色没有自己的情绪目录时，
  从该引擎的目录行回查，包括已被排除的行；排除音色不等于移除引擎声明的情绪能力。
- 全局预合成任务上限与源级并发配额分别约束任务数及实际请求。进程内同一个源共用配额，
  持续到响应流消费完毕或关闭，覆盖试听、实时、后台、流式和按章下载。
- 取消、打开失败及读取失败必须关闭响应并释放配额；流式响应的生命周期属于服务，
  不绑定到创建响应时短暂的 `runBlocking`。取消通过底层 `Call.cancel()` 中止网络，
  响应体留给读取线程清理，避免并发关闭 Okio 缓冲流。
- 有音色或情绪的 HTTP 缓存键包含实际源、音色、修订、情绪映射版本及源合成参数。
  缓存键也包含实际使用的情绪目录。旧默认音色在默认合成语速且没有情绪目录时继续使用原缓存键，
  以兼容既有离线音频。

## 传输与恢复

- 标准备份在原 `httpTTS` 备份选项下同时保存 `httpTTS.json` 与 `httpTtsVoices.json`。
  后者保存 HTTP 音色完整快照，包括情绪、标签、enabled、available、excluded、revision 与时间戳。
  即使没有音色也写出空数组，以区分旧备份与明确的空快照。
- 恢复新备份时，在同一事务中恢复源并替换这些源的音色快照，其他源保持原样。
  缺少快照的旧备份继续尝试解析其原 JSON 中的目录；缺少目录时保留本机已有目录。
  旧备份从未保存的音色无法凭空恢复。
- 面向外部格式的文件/URL 导出从目录表重建字符串字段 `speakersJson` / `emotionsJson`，
  包括被本机排除的目录音色。外部格式传递源目录，不携带本机排除等用户状态。
- Gson 使用的发言人和情绪目录模型有 `@Keep`；标准快照实体沿用现有实体保留规则。

## 批量操作

单选、全选、批量删除/排除、加入声音池使用同一可移除候选判定。全选依据 ID 集合关系，
页面刷新时剔除失效选择，避免数量相同但对象不同导致误判。

## 回归验证

针对性测试位于 `HttpTtsCompatibilityRepositoryTest`、`HttpTtsAudioSynthesizerTest`、
`HttpTtsEmotionMapperTest`、`HttpTtsSynthesisLimiterTest`、`HttpTtsBatchSelectionTest`、
`InputStreamDataSourceTest` 以及已有目录同步和路由测试中。

2026-10-17 使用 JDK 21 运行 `:app:testAppDebugUnitTest`，以 `--tests` 逐个指定上述测试及
`HttpTTSJsonImportTest`、`SpeechVoiceRouterTest`、`BuildSpeechPlanUseCaseTest`、
`SyncHttpTtsCatalogUseCaseTest`、`SyncReadAloudVoicesUseCaseTest`、`HttpTtsAudioCacheKeyTest`、
`HttpTtsCatalogParserTest`：13 个测试类、61 项测试全部通过，无失败或跳过。
数据库测试使用隔离的内存 Room 数据库，合成契约测试使用本地 HTTP 服务，没有修改用户原数据库。

以下任务已通过（离线依赖解析、单 worker；最终构建耗时 11 分 12 秒）：

```powershell
.\gradlew.bat :app:compileAppDebugKotlin verifyConfigArchitecture lintAppDebug assembleAppDebug --continue --offline --max-workers=1
git diff --check
```

构建进程临时使用 4 GiB 堆；没有修改仓库 JVM 配置。Lint 报告为 181 个警告、26 个提示，
既有 `lint-baseline.xml` 未调整；29 个历史错误由既有基线过滤。Debug APK 已重新生成。
`verifyConfigArchitecture` 的 CloudTtsViewModel DAO 直连基线从 13 下调为 5，与本次移除的访问一致。

本地 HTTP 测试验证请求契约，不代表第三方付费服务、真机播放性能或限流压测已通过。
本次没有运行 Release/R8 构建；目录 DTO 的字段保留由 `@Keep` 明确约束。
