# Android 调试场景

项目级 Android 调试入口。它负责构建、安装、恢复 fixture、执行领域动作，并将结果写入
`.debug-runs/<session-id>/summary.json`。

当前只提供首个端到端场景：

```bash
tools/android/debug.sh run reader/page-turn \
  --device emulator-5554 \
  --fixture long-chapter-v1 \
  --action-repeat 20
```

当只有一台 ready 状态的设备时可以省略 `--device`。`--action-repeat` 表示在同一次应用启动中
重复领域动作；它不等同于完整重置后多次运行场景。

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
`DebugScenarioActivity` 导入。场景只使用 `open_reader` 和 `turn_page` 两个领域动作；这里刻意
不提供坐标级通用 DSL。

`summary.json` 是主要机器输入。`failure.suspectedFrame` 仅在堆栈中成功解析到应用代码帧时出现，
并明确携带 `confidence`；`crash.txt` 始终是原始证据。

阅读器的 `ReadView` 会把当前页正文写入 accessibility `contentDescription`。运行器只比较这个
正文节点，而不是比较包含时间、焦点等噪声的整棵 UI 树。`session-logcat.txt` 只保存本次应用
进程及 session 标记日志，`logcat.txt` 保留完整原始日志。场景结束后，无论成功或失败，运行器
都会停止 debug 应用进程。

运行器不会在每次场景后停止 Gradle daemon，以免破坏增量构建。开发机需要回收构建进程时，
在本轮工作结束后执行：

```bash
./gradlew --stop
```

运行 Python 单元测试：

```bash
python3 -m unittest discover -s tools/android/tests -v
```
