# Android 调试场景

项目级 Android 调试入口。它负责构建、安装、恢复 fixture、执行领域动作，并将结果写入
`.debug-runs/<session-id>/summary.json`。

当前提供三个端到端场景（`reader/page-turn`、`bookshelf/open-reader-back`、
`bookshelf/miuix-hidden-statusbar`）：

```bash
tools/android/debug.sh run reader/page-turn \
  --device emulator-5554 \
  --fixture long-chapter-v1 \
  --action-repeat 20
```

第二个场景验证从书架打开固定书籍，再返回同一书架：

```bash
tools/android/debug.sh run bookshelf/open-reader-back --device emulator-5554
```

它通过书籍的 accessibility 标签定位 fixture，不依赖固定坐标；阅读页 ready 使用 `ReadView`
的 `READER_PAGE` logcat 信号（见下），返回后再次确认同一书籍节点可见。

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

查看当前支持的 entry、action、assertion（含参数）：

```bash
tools/android/debug.sh list
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
`DebugScenarioActivity` 导入。当前的领域动作是 `reader` entry 的 `turn_page`，以及 `bookshelf`
entry 的 `open_fixture_book` / `press_back`；这里刻意不提供坐标级通用 DSL。

entry、action、assertion 通过 `runner.py` 里的 `ENTRIES` / `ACTIONS` / `ASSERTIONS` 注册表扩展：
新增一个 bug 场景 = 注册一个 handler（`@register_action` / `@register_entry`）再写一个场景 JSON，
不需要改 `execute()` 或 `validate_scenario()`。handler 只把原始观测写进 `Context`，assertion 是
对这些观测的纯读取。用 `list` 命令可以随时查到已注册的能力。

`summary.json` 是主要机器输入。`failure.suspectedFrame` 仅在堆栈中成功解析到应用代码帧时出现，
并明确携带 `confidence`；`crash.txt` 始终是原始证据。

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
