# Android 调试场景

项目级 Android 调试入口。它负责构建、安装、恢复 fixture、执行领域动作，并将结果写入
`.debug-runs/<session-id>/summary.json`。

当前提供 16 个端到端场景，覆盖阅读翻页、护眼模式、RSS 阅读日夜刷新、
书架返回、书源编辑预见性返回，以及主题资源的保存、应用、删除和导出。
先用 `list` 查看运行器实际注册的能力：

```bash
tools/android/debug.sh list
```

## 快速开始

阅读页连续翻页：

```bash
tools/android/debug.sh run reader/page-turn \
  --device emulator-5554 \
  --fixture long-chapter-v1 \
  --action-repeat 20
```

从书架打开固定书籍，再返回同一书架：

```bash
tools/android/debug.sh run bookshelf/open-reader-back --device emulator-5554
```

它通过书籍的 accessibility 标签定位 fixture，不依赖固定坐标；阅读页 ready 使用 `ReadView`
的 `READER_PAGE` logcat 信号（见下），返回后再次确认同一书籍节点可见。

书源编辑预见性返回必须从真实上一级进入，不能把编辑 Activity 单独作为任务栈根启动：

```bash
tools/android/debug.sh run source-edit/predictive-back
```

该场景安装固定书源，先启动书源管理页，通过同一行的编辑按钮进入编辑页，再执行边缘返回手势，
最后确认书源管理 Activity 恢复且 fixture 书源仍可见。

## 内置场景

| 场景 | 验证目标 |
| --- | --- |
| `reader/page-turn` | 阅读页 ready、Activity 可见且翻页后正文变化 |
| `rss/read-day-night` | RSS WebView 停留期间切换日夜并保留前后截图与录屏 |
| `bookshelf/open-reader-back` | 从书架进入阅读页并返回同一书架 |
| `bookshelf/miuix-hidden-statusbar` | Miuix + 隐藏状态栏条件下的阅读页返回 |
| `source-edit/predictive-back` | 从书源管理进入编辑页后执行预见性返回 |
| `theme/save-current-baseline` | 无外部资源时保存当前主题 |
| `theme/export-current-baseline` | 在系统文件选择器中确认保存并校验导出 ZIP |
| `theme/select-day-background` | 经系统照片/文件选择器选图并验证图片复制到应用目录 |
| `theme/save-background-after-restart` | 直接注入有效图片，重启后保存且资源进入主题包 |
| `theme/export-background-after-restart` | 直接注入有效图片，重启后导出且校验资源哈希 |
| `theme/save-missing-file` | 注入缺失文件路径，验证保存失败反馈且不崩溃 |
| `theme/export-missing-file` | 注入缺失文件路径，验证导出失败反馈且不崩溃 |
| `theme/save-missing-content-uri` | 在点击保存前让受控 URI 失效，证明 `openInputStream` 调用链 |
| `theme/export-missing-content-uri` | 在确认导出前让受控 URI 失效，证明 `openInputStream` 调用链 |
| `theme/export-current-after-deleting-applied-theme` | 保存并应用主题、删除主题包后，当前主题仍可完整导出 |

## 长章节段评夹具

`long-chapter-v1` 在正文第一段末尾注入 `paragraph-review-v1`：一个带书源 click 脚本的内联
`ImageColumn`。点击后沿正式正文图片点击链调用 `java.showBrowser`，在 WebView 中展示三条固定假段评。
章节 `reviewImg` 保持为空，因此入口不会挂到标题，也不会在每段尾部追加占位字符。夹具会固定关闭
旧的字符段评开关，并把图片点击方式设为执行 click 脚本，避免受 debug 包历史设置影响。

## 主题资源故障注入

主题场景支持顶层字段 `themeAsset`：

| 值 | 注入状态 |
| --- | --- |
| `valid-file` | 将固定 PNG 推到 Debug 应用目录，并把绝对路径写入白天背景设置 |
| `missing-file` | 写入确定不存在的文件路径 |
| `missing-content-uri` | 写入 Debug 专用 ContentProvider URI；初始可读，在保存/导出前切换为抛出 `FileNotFoundException` |

`missing-content-uri` 使用仅存在于 `src/debug` 的 `DebugThemeAssetProvider`。运行器在实际点击保存按钮，
或在 DocumentsUI 中点击最终“保存”按钮之前调用 `armFailure`，日志必须依次出现
`INJECTED_THEME_ASSET_FAILURE_ARMED` 和 `INJECTED_THEME_ASSET_OPEN`。这样能证明异常由目标业务操作
触发，不会把页面预览图片产生的读取误判成保存/导出调用。

主题导出场景不会在打开 DocumentsUI 后等待人工操作：运行器会填写唯一文件名、点击系统“保存”，
等待文件大小稳定后拉回 `export.zip`，再校验 ZIP、`manifest.json`、资源条目和 fixture 哈希。失败产生的
占位 ZIP 不会被标记为有效 artifact。

## 用参数复现 bug 前置状态

有些 bug 依赖特定设置（主题引擎、阅读器开关等）。场景可以声明 `preferences`，由
`DebugScenarioActivity` 在拉起界面前写入 `AppConfigStore`，从而**用参数复现，而不是手动去 UI
里开开关**。只接受字符串/布尔值。配合 `record: true` 会在动作阶段用 `adb screenrecord` 录一段，
拉回 `.debug-runs/<session>/transition.mp4`，用于判断翻页/返回这类瞬时动画类 bug（静态截图抓不到）。

例如“miuix 主题 + 阅读器隐藏状态栏时，返回书架有下移动画”：

```json
{
  "record": true,
  "preferences": { "composeEngine": "miuix", "hideStatusBar": true },
  "actions": [ { "type": "open_fixture_book" }, { "type": "press_back" } ]
}
```

```bash
tools/android/debug.sh run bookshelf/miuix-hidden-statusbar
```

## Debug 状态探针与专用夹具

`DebugStateProvider` 仅打进 debug APK，由 runner 通过 `content call` 读取机器可断言状态：

- `toggleReaderDayNight`：通过生产设置网关复现阅读页快捷日夜按钮的主题切换。

这些准备动作均在 debug 包中完成，不进入 release 包。

修改 `DebugScenarioActivity`（如新增可注入的设置）后第一次要全量构建，之后同一 APK 反复调用
`--skip-build` 即可秒级复跑。

当只有一台 ready 状态的设备时可以省略 `--device`。`--action-repeat` 表示在同一次应用启动中
重复领域动作；它不等同于完整重置后多次运行场景。

fixture 是在 `am start` 时由 `DebugScenarioActivity` 现装的，所以只要 debug APK 没变，换场景/
换 fixture 都不需要重新构建安装。迭代时加 `--skip-build` 复用设备上已装的 APK，跳过 Gradle
构建与安装（此时 `summary.build.status` 为 `skipped`），把一轮从分钟级降到秒级：

```bash
tools/android/debug.sh run reader/page-turn --skip-build
```

退出码：

| 退出码 | 含义 |
| --- | --- |
| 0 | 通过 |
| 1 | 行为断言失败 |
| 2 | 应用崩溃或 ANR |
| 3 | 构建或安装失败 |
| 4 | 环境或设备失败 |
| 5 | 场景定义错误 |

fixture 位于 `app/src/debug/assets/debug-fixtures/`，由只存在于 debug 包中的
`DebugScenarioActivity` 导入。当前 entry 为 `reader`、`bookshelf`、`source_manage` 和
`theme_config`。动作保持领域语义，例如 `turn_page`、`open_fixture_source`、
`save_current_theme`、`apply_saved_theme`、`delete_saved_theme`，这里刻意不提供坐标级通用 DSL。

entry、action、assertion 通过 `runner.py` 里的 `ENTRIES` / `ACTIONS` / `ASSERTIONS` 注册表扩展：
新增一个 bug 场景 = 注册一个 handler（`@register_action` / `@register_entry`）再写一个场景 JSON，
不需要改 `execute()` 或 `validate_scenario()`。handler 只把原始观测写进 `Context`，assertion 是
对这些观测的纯读取。用 `list` 命令可以随时查到已注册的能力。

`summary.json` 是主要机器输入。`failure.suspectedFrame` 仅在堆栈中成功解析到应用代码帧时出现，
并明确携带 `confidence`；`crash.txt` 始终是原始证据。启用 `record` 时还会生成
`transition.mp4`；主题导出成功时会保留经过校验的 `export.zip`。

阅读页的观测走 logcat 而非 uiautomator：`ReadView.upContent` 在 debug 包（`BuildConfig.DEBUG`）
下每次渲染都打一条 `READER_PAGE <当前页正文>`，运行器 grep `logcat -s LegadoDebug:I` 取最后一条
判就绪与翻页变化。这样跨设备可靠——某些设备（如 One UI）的 uiautomator 根本不暴露 `ReadView`
节点。书架侧仍用 uiautomator 定位（Compose 节点可正常 dump）。翻页后在场景限定时间内轮询该信号，
不依赖固定动画等待。`session-logcat.txt` 只保存本次应用
进程及 session 标记日志，`logcat.txt` 保留完整原始日志。场景结束后，无论成功或失败，运行器
都会停止 debug 应用进程。运行器还会在场景前唤醒并解锁设备、临时保持 USB 供电时亮屏，
结束后恢复原始亮屏与 Keyguard 状态。

运行器不会在每次场景后停止 Gradle daemon，以免破坏增量构建。开发机需要回收构建进程时，
在本轮工作结束后执行：

```bash
./gradlew --stop
```

运行 Python 单元测试：

```bash
python3 -m unittest discover -s tools/android/tests -v
```
