# 听书多角色功能修复交接文档

> 基于 `codex/tts-multi-speaker` 分支，在原有 TTS 多角色朗读功能的基础上做了以下修改。

---

## 一、朗读胶囊可拖动

**文件**：`app/src/main/java/io/legado/app/ui/book/read/ReadAloudCapsule.kt`

**改动**：
- 添加 `pointerInput` + `detectDragGestures`，用户可拖动胶囊到屏幕任意位置
- 位置通过 SharedPreferences 持久化（`read_aloud_capsule`），下次进入保持上次位置
- 使用 `skipNextSave` 标志避免初始加载时触发不必要的保存
- `Modifier.offset { IntOffset(offsetX, offsetY) }` 叠加在 `Alignment.BottomCenter` 之上

**注意**：胶囊默认仍在底部居中，拖动后偏移量相对于默认位置。

---

## 二、新版朗读控制界面状态同步修复

**文件**：`app/src/main/java/io/legado/app/ui/book/readaloud/player/ReadAloudPlayerViewModel.kt`

**根因**：
1. `isPaused` 直接读取 `BaseReadAloudService.pause` 静态变量，但只在 `combine` 的 lambda 中执行——该 lambda 仅在 `playbackInfo` 发射时重新计算。服务未运行时 `playbackInfo` 不发射，导致 `isPaused` 永远停在初始值 `true`。
2. `TogglePause` 调用 `ReadAloud.resume()`，但 `resume()` 在 `isRun = false` 时静默返回，用户无法从播放器启动朗读。

**修复**：
1. 新增 `aloudState` flow，通过 `callbackFlow` 监听 `EventBus.ALOUD_STATE` 事件（`PLAY`/`PAUSE`/`STOP`），订阅时发射当前状态
2. 将 `aloudState` 加入 `combine`，`isPaused` 根据事件值动态计算
3. `TogglePause` 逻辑：`!isRun` 时调用 `ReadBook.readAloud()` 启动服务，`isRun && pause` 时 resume，否则 pause
4. `snapshot()` 也修正为根据 `isRun` 正确反映初始状态

---

## 三、多角色朗读开关

**功能**：在朗读配置面板新增"多角色朗读"开关，关闭后完全走原来的 legacy 单引擎朗读路径。

### 配置层

| 文件 | 改动 |
|---|---|
| `PreferKey.kt` | 新增 `const val useMultiSpeaker = "useMultiSpeaker"` |
| `ReadTtsConfig.kt` | 新增 `var useMultiSpeaker by prefDelegate(PreferKey.useMultiSpeaker, true)` |
| `ReadConfig.kt` | 新增 `var useMultiSpeaker` 透传属性 |
| `ReadAloudSettingsRepository.kt` | `ReadAloudPreferences` 新增字段，新增 `setUseMultiSpeaker()` 方法，新增 DataStore Key |

### 业务逻辑层

**核心拦截点** — `BaseReadAloudService.buildSpeechPlan()`（第 323 行）：

```kotlin
if (bookUrl.isEmpty() || !ReadConfig.useMultiSpeaker) return emptyList()
```

关闭时直接返回空列表 → `playbackQueue` 为空 → 服务走 legacy `contentList` 路径，行为与多角色功能引入前完全一致。

**服务选择** — `ReadAloud.findCoordinatorHttpSeed()`：

```kotlin
if (!ReadConfig.useMultiSpeaker) return null
```

关闭时不查询绑定的 HTTP/云端音色，避免被错误路由到 HTTP TTS 服务。

**UseCase 参数透传**：
- `PrepareChapterSpeechPlanUseCase` 新增 `useMultiSpeaker` 参数
- `BuildSpeechPlanUseCase` 新增 `useMultiSpeaker` 参数（关闭时所有 segment 强制用默认引擎，但实际不会到达——因为上层已拦截返回空）

### UI 层

| 文件 | 改动 |
|---|---|
| `ReadBookContract.kt` | `ReadAloudPlayerUiState` 新增 `useMultiSpeaker`，`ReadBookIntent` 新增 `SetUseMultiSpeaker` |
| `ReadBookViewModel.kt` | 处理 `SetUseMultiSpeaker` 意图，`collectReadAloudPreferences()` 收集偏好 |
| `ReadAloudConfigSheet.kt` | 在"语音分析模式"下拉框下方添加 `TinySwitchSettingItem` |

### 字符串资源

| Locale | Key | Value |
|---|---|---|
| EN | `use_multi_speaker` | Multi-speaker reading |
| EN | `use_multi_speaker_summary` | Use different voices for narration, dialogue and other roles |
| CN | `use_multi_speaker` | 多角色朗读 |
| CN | `use_multi_speaker_summary` | 为旁白、对话等不同角色使用不同音色 |
| TW/HK | `use_multi_speaker` | 多角色朗讀 |
| TW/HK | `use_multi_speaker_summary` | 為旁白、對話等不同角色使用不同音色 |

### 关闭时的完整行为链

```
用户关闭开关
  → ReadConfig.useMultiSpeaker = false
  → BaseReadAloudService.buildSpeechPlan() 返回 emptyList()
  → playbackQueue = ReadAloudPlaybackQueue.Empty
  → 所有播放逻辑走 legacy contentList 路径
  → ReadAloud.findCoordinatorHttpSeed() 返回 null
  → getReadAloudClass() 根据 ttsEngine 配置选择 TTSReadAloudService 或 HttpReadAloudService
  → 行为与多角色功能引入前完全一致
```

### 缓存无需清理

音频文件命名方案在 legacy 路径下不变（`sourceKey = httpTts?.url`），旧缓存文件仍可复用。已有的 `removeCacheFile()` 机制（按时间过期清理）足够处理残留文件。

---

## 编译验证

所有改动均通过 `.\gradlew.bat :app:compileAppDebugKotlin` 编译验证，仅有预存的 deprecation warning。
