# Track C3 阅读器 parity / 性能基线

> 状态：**已完成（2026-07-21）**。首轮失败与修正历史保留在 §5–§7；字体/字号、
> 明确章节边界、首个非空正文帧和 GPU overdraw 已在 §8 补齐。

## 1. 固定条件

每轮 Legacy 与 Compose overlay 必须使用同一台真机、同一构建、同一本本地纯文本书、
同一章节/页位置，并记录：

| 项目 | 值 |
|---|---|
| Git revision | `e3a848d5c` |
| APK variant | `appDebug` |
| 设备 / Android / 刷新率 | Samsung SM-S9310 / Android 16（API 36）/ 采集时 30Hz，自适应最高 120Hz |
| 分辨率 / 字体缩放 / 显示缩放 | 1080×2340 / 1.0 / 480dpi |
| 阅读字体 / 字号 / 行距 / 边距 | 默认字体 24sp；字体场景另测系统衬线字体并增大 3 级 |
| 测试书与章节 | 《斗破苍穹》第 8 章末页 / 第 9 章首页 |

截图前关闭阅读菜单、自动翻页、朗读和系统通知浮层。页眉中的时间、电量若会变化，比较时裁掉
页眉/页脚，只比较 `ChapterProvider.visibleRect` 对应的正文区域。

## 2. parity 场景

采集由脚本自动切换「实验室 → Compose 渲染」、启动同一本书、正反各翻固定页数、抓取成对
截图和 `gfxinfo`，结束时还原原来的实验室开关：

```bash
python3 tools/capture_reader_c3_baseline.py \
  --crop '<正文左>,<正文上>,<正文右>,<正文下>'
```

默认产物写入 `build/reader-c3-baseline/<timestamp>/`：`plain-page-*`、`forward-turn-*`、
`rapid-turn-*`、两份 `framestats` 和带 PNG 差异指标的 `result.json`。脚本在前进操作后停留并
截图；若起点与前进页的差异像素不足 1%，立即失败，避免“前进/后退未发生却因终点相同而误报
通过”。`--crop` 应裁掉会变化的状态栏、页眉和页脚；
不传时比较全屏。多台设备时传 `--serial`，翻页次数/坐标可用 `--turns`、`--next-x-ratio`、
`--prev-x-ratio` 调整；默认执行五轮往返，冒烟时可传 `--rounds 1`。默认使用“最后阅读的书”；
需要指定书籍时再传 `--book-url`。

脚本默认目标为当前工程的 `io.legato.kazusa.debug`；采集前须安装本次构建的 `appDebug`。

字体/字号场景只需先在应用中设置一次目标样式，再运行同一条脚本；章节边界场景将测试书定位到
边界附近后运行。两者都不再需要逐页手动截图。

| 场景 | 操作 | 必须核对 | Legacy | Compose |
|---|---|---|---|---|
| `plain-page` | 普通正文静止页 | 字形、baseline、换行、首尾行 | 通过 | 通过 |
| `font-size` | 更换字体并调整字号 | 重排后无旧页闪回，坐标一致 | 通过 | 通过 |
| `chapter-boundary` | 末页进入下一章，再返回 | 章节标题、页索引、正文不串章 | 通过 | 通过 |
| `rapid-turn` | 连续前进 20 页，再后退 20 页，重复 5 次 | 无空白页、旧页闪回、章节错位 | 通过 | 通过 |

首轮基线不预设像素差阈值；保留两张原图与正文裁剪图，并记录可解释差异。若普通文本的字形、
baseline 或换行不同，C3 不通过；页眉/页脚、图片、下划线、选择和动画不属于本阶段 parity。

## 3. 性能与 overdraw

Legacy 与 Compose 各执行一次同一 `rapid-turn` 操作；采集器会在每次操作前清空
`gfxinfo` 并保存 `framestats` 。

记录 `Janky frames` 以及 50th / 90th / 95th / 99th percentile。用 Android Studio System Trace
从“打开阅读页”点击到第一帧完整正文可见，记录首帧耗时；同一 trace 中记录连续翻页的
FrameTimeline。打开开发者选项的「调试 GPU 过度绘制」，分别保存静止正文页截图并注明覆盖颜色。

| 指标 | Legacy | Compose overlay | 备注 |
|---|---:|---:|---|
| 阅读页首个非空正文帧 P50 / P90 | 1512 / 1522ms | 1561 / 1581ms | 冷启动各 5 次，renderer draw marker |
| 50th / 90th / 95th / 99th frame | 6 / 14 / 16 / 77ms | 27 / 40 / 46 / 48ms | `gfxinfo framestats`，C4 补采 |
| Janky frames | 25 / 630（3.97%） | 23 / 45（51.11%） | 相同两轮、每轮 5 页往返 |
| 正文区 overdraw | 背景约 2×，文字约 3× | 背景约 3×，文字达 4×+ | GPU overdraw 截图 |

C3 的性能数据是 C7 停止双重绘制后的对照物，不作为 C3 的优化目标；但若 Compose overlay
出现稳定空白帧或明显阻塞，必须先修复，不能把异常值冻结为“基线”。

## 4. C3 放行条件

- 四个 parity 场景均留有 Legacy / Compose 成对证据。
- `rapid-turn` 五轮均无空白页、旧页闪回或章节错位。
- 首帧、帧分位数、jank 与 overdraw 均已记录，设备与构建信息完整。
- 关闭 feature flag 后仍走纯 Legacy renderer，行为无变化。

以上真机证据未补齐前，C3 保持“实施中”，不得开始 C4 之外的复杂渲染功能。

## 5. 首轮实机结果（2026-07-21）

| 项目 | 结果 |
|---|---|
| 设备 | Samsung SM-S9310，1080×2340，`io.legato.kazusa.debug` |
| 测试书 | 最后阅读书《斗破苍穹》，第 8 章第 5/9 页 |
| 操作 | Legacy / Compose 各 20 页前进 + 20 页后退 |
| 产物 | `build/reader-c3-baseline/20260721-galaxy-s25-run2/` |
| 往返稳定性 | 通过：两模式均回到同一正文页，未见空白页、旧页闪回或章节错位 |
| 全屏像素差 | MAE 25.77；差异像素 22.72%（全屏比较，未裁掉页眉/页脚） |
| 静态视觉 parity | **未通过**：Compose 缺旧栈页眉/页脚；正文颜色/高亮与旧 Canvas 不一致 |
| Legacy 帧统计 | 50/90/95/99 = 5/8/21/65ms；jank 4.13% |
| Compose 帧统计 | 50/90/95/99 = 30/42/44/53ms；jank 50.00% |

帧统计仅作为 overlay 首轮基线，不能直接判定动画性能优劣：Legacy 路径执行原有翻页动画，
Compose overlay 当前为无动画直接换页（C8 才补动画）。下一步先处理静态绘制 parity，再复测。

## 6. 第二轮修正进展（2026-07-21）

- 修复采集器 route 切换：每次以 `am start -S` 冷启动目标 route，避免复用 `MainActivity`
  后仍停留在实验室页却继续截图、点击。
- 所有 ADB 子进程增加 15 秒超时，单次 `input tap` 异常不再无限挂起。
- 增加前进页截图与硬断言。1 页导航冒烟中，Legacy / Compose 起点到前进页的差异像素分别为
  19.29% / 19.70%，确认两条路径都实际完成前进，再返回起点。
- Compose 普通文字列已补齐旧绘制语义：按列文字色、搜索结果强调色、自定义字体、标题字号，
  以及 Android 15+ letter spacing 半字偏移；正文内容坐标增加旧 `ContentTextView` 在
  `PageView` 内的偏移量，避免 Compose 画面上移覆盖页眉。
- 安装修正构建后的普通页 1 页冒烟，全屏 MAE 为 0.023、差异像素为 0.113%（首轮分别为
  25.77 / 22.72%），当前测试页的静态画面已基本一致。

以上仅说明采集链路和单页代码修正成立；完整五轮结果见下一节。

## 7. 五轮压力复采（2026-07-21）

提交 `e3a848d5c` 对应的 `appDebug` 在 Samsung SM-S9310 上完成 Legacy / Compose 各五轮，
每轮前进 20 页再后退 20 页：

| 指标 | Legacy | Compose overlay |
|---|---:|---:|
| 起点 → 前进 20 页差异像素 | 19.351% | 19.351% |
| 五轮后起点 → 终点差异像素 | 0.0212% | 0.0127% |
| 总渲染帧 | 6351 | 423 |
| Janky frames | 244（3.84%） | 210（49.65%） |
| 50 / 90 / 95 / 99 分位 | 5 / 7 / 11 / 57ms | 26 / 36 / 40 / 48ms |

Legacy / Compose 起点全屏差异为 0.0212%，五轮终点差异为 0.0127%。两条路径均确实前进，
且五轮后回到各自起点；未发现空白页、旧页闪回或章节错位。证据目录：
`build/reader-c3-baseline/20260721-c3-five-rounds/`。

Compose 帧数显著更少且 jank 比例高，是“无动画直接换页 + overlay 双绘”与 Legacy 翻页动画的
语义差异，继续只作为 C7 对照基线，不在 C3 内优化。尚未完成的放行项缩小为：字体/字号场景、
明确章节边界场景、首帧 System Trace 和 GPU overdraw 截图。

采集器随后补上两项硬性约束：每种 renderer 五轮后的终点必须与自己的起点差异不超过 1%，
并把总帧数、jank、P50/P90/P95/P99 自动解析进 `result.json`。本轮既有截图回算结果分别为
0.0212% / 0.0127%，满足新增约束。采集开始前还会读取当前用户的 `deviceLocked` 状态；真机
锁定时直接失败，不创建空结果目录，也不在锁屏上继续点击。

## 8. C3 放行项补采（2026-07-21）

提交 `7e7418ac8` 后的 C4 构建在同一台 Samsung SM-S9310 上补齐 C3 剩余证据：

- **字体/字号 parity**：正文改为系统衬线字体，字号在原配置上增大 3 级；Legacy / Compose
  同一静态页正文裁剪差异为 0 像素，翻页再返回后的差异同为 0，字形、baseline 与换行一致。
- **明确章节边界 parity**：从《斗破苍穹》第 8 章 9/9 前进到第 9 章 1/12，再返回；两 renderer
  的起点、下一章标题/首页和返回页视觉一致，正文裁剪差异为 0，未出现串章或旧页闪回。
- **首个非空正文帧**：新增 renderer 边界的一次性 `ReaderFirstFrame` draw marker，并用
  `tools/capture_reader_c3_first_frame.py` 各冷启动 5 次。Legacy 为
  1497.37–1522.40ms（P50 1512.33ms，P90 1522.40ms）；Compose overlay 为
  1534.04–1580.73ms（P50 1561.42ms，P90 1580.73ms）。Compose P50 慢约 49ms，
  未观察到空白首帧；实验室设置的初值改由 `LabSettingsGateway.currentSettings` 提供后，
  Compose 冷启动不再先绘制一帧 Legacy。此数据作为 C7 单栈后的对照，不在 C3 优化。
- **GPU overdraw**：`debug.hwui.overdraw=show` 下，Legacy 正文背景主要为绿色（约 2×），
  文字区域粉红/红色（约 3×）；Compose overlay 正文整块升为粉红/深红（约 3×），文字区域
  达红色（4×+）。结果确认当前开销来自预期的 legacy + Compose 双绘，C7 停止双重绘制后复测。

证据目录：`build/reader-c3-supplement/`。至此 §4 的四项放行条件全部有真机证据，C3 完成。
