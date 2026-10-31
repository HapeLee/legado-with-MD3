# 漫画视觉定位校准与供应商实测

2026-10-28。用户提供英文气泡示例图后，用设备中已配置的 DeepSeek Flash 实测定位，
避免把通过 JSON／数值校验误当成图像位置准确。生产协议见 [AI 定位与排版](manga-ai-typesetting.md)。

## 样本与方法

- 原图 `manga_bubbles_english.png`，531×1024，SHA-256
  `108551e208bedd4894cda8a16abeb57e5d0281810425969661b027ed767f7957`。
  用户图不加入仓库资产；本机来源为 `D:/Downloads/manga_bubbles_english.png`。
- 人工近似标注 33 个正文区域，包含相连的两个物理气泡和仅有省略号的旁白框。
  只比较正文；不将标题、底部分类标签、独立拟声词及爱心装饰纳入正文参考集。
- 使用 PLK110 中的 `deepseek-flash` 配置，`api.deepseek.com`，OAI Chat Completions。
  图片保持原尺寸 PNG；英语到简体中文；思考关闭，实际发送 `thinking.type=disabled`。
  输出上限 16384 tokens，两次完整实验均未收到 reasoning 内容；未记录计费 token 总量。
- 当前生产提示词、归一化坐标合同完整调用一次；像素坐标实验完整调用一次。
  后者仅在手动测试中替换坐标／字号单位要求，程序按已知宽高转换后沿用同一计划校验。
  不自动重试修正几何，以观察每次首次完整响应。
- 人工框只作为近似参考，不是精确字形分割真值。中心误差以原图像素计，
  正式比较使用两次共同匹配的 31 个正文区域，不混用两次不同的检出集合。
  包围矩形不能证明曲线气泡四角安全；图像中的越界仍需视觉审查。

## 观察结果

| 合同 | 完整响应耗时 | 匹配独立正文区域 | 共同 31 区域中心平均偏差 | 横向平均偏差 | 纵向平均偏差 |
| --- | --- | --- | --- | --- | --- |
| 初始 0–1000（旧） | 11.843 s | 31 / 33 | 121.78 px | −120.69 px | −9.62 px |
| 实验：原图像素 | 9.989 s | 32 / 33 | 45.66 px | −7.61 px | −43.56 px |

负值分别表示向左／向上。两次计划均通过结构及数值校验；两次都把 `I...` 与 `I still like you.`
两个相连气泡合并为一个区域。当前合同另外漏掉省略号旁白框。输出总区域数分别为 39 和 32，
前者额外包括拟声和底部标签；这些额外输出不作为正文检出率的分母。

旧合同响应的 `No way...` 横坐标为 400–500。按约定转换成原图像素后落在约 212–266，
实际文字在约 403–490：模型返回值看起来更接近像素位置，而没有可靠执行横轴归一化要求。
这是从响应与人工标注得到的解释，不能据此给所有供应商静默乘一个统一校准系数。

像素合同显著改善横向偏差，仍存在按行变化的向上偏移、原文字形覆盖不足、相连气泡合并。
因此不能将该像素实验直接当成已验证的生产替代方案，也不能仅通过提示词保证正确位置。
这些偏差在模型返回的原始框中已经存在，并非只能由最终绘制观察到。

这是单张示例图、每种合同一次完整响应的探索性结果，不是供应商准确率或稳定性排名。
未覆盖日文小字／注音、真实单行本背景、不同分辨率、竖排正文、旋转文字及重复调用方差。
前两次后台尝试未取得完整响应：一次主动终止，另一次被设备 `Cached(nirvana)` 后台管理杀死；
不纳入几何与耗时统计，是否产生部分计费无法由当前流式 Gateway 确认。

## 编号候选：已接入的优化

应用已有 RT-DETR 只检测位置，不负责 OCR 识字。客户端给每个位置生成 a-ID 和放大裁剪，
AI 同时看原图与编号表，只绑定原文、译文、方向和行列，不再为这些 ID 估算坐标。
同一物理气泡的文本检测可以合并，不同气泡即使相连也独立。字号基准取原图字形测量，
AI 可选择相对倍率。检测遗漏文字仍可用显式原图像素 free 区域补充；检测失败保留兼容路径。

首轮 32 个正文候选全部正确绑定，平均中心偏差 2.11 px，最大约 9.56 px（该框包含爱心装饰）。
与旧合同共同匹配的 31 区域平均偏差约 2.16 px。这个误差来自本地框被正确绑定后的结果，
不是 DeepSeek 的自由坐标估计精度。相连的 `I...`／`I still like you.` 已分开，原来漏掉的纯省略号
框仍未检出，也未凭空制造译文。图像仍保留该处原样。

该次本地准备 1.093 s、DeepSeek 完整流式请求 6.643 s、输出 5503 字符，思考关闭。
随后正式管线增加了气泡外标题／拟声／标签候选：一次 DeepSeek 逻辑请求完成，生成 SMART 图片，
再直接沿用已存计划生成 PATCH 图片，没有增加第二次 AI 请求。含解码、检测、识图、诊断和两次合成，
总计 10.596 s；共 39 个输出区域，正文仍匹配 32 / 33，正文平均中心偏差仍为 2.11 px。

成图复核还暴露了搜索外扩过大时的底色与边框误擦：深色爆炸框污染底色取样、彩色标题外白纸
被当成标题底色。修复后按字形尺度外扩／过滤长边框，紧邻原文字框采样；SMART 可借用附近渐变底色。
用同一批已存文字离线重放完整管线复核 SMART／PATCH，外部请求数为 0，约 3.296 s。
本地擦字仍可能留下渐变／纹理修复痕迹，不能等同于图像生成式修复。

当前生产实现和兼容分支见 [识字排版合同](manga-ai-typesetting.md)。分析身份升级为 v2-anchors；
渲染版本 12、重排版本 11，旧发布译图保留。247 项漫画／领域单测通过（31 类，零失败／错误／跳过）；
执行 `:app:testAppDebugUnitTest :app:assembleAppDebug :app:assembleAppDebugAndroidTest verifyConfigArchitecture`。
PLK110 上 5 项既有原生回归通过，手动校准入口默认 skip，正式管线及离线重放各通过一次；
默认回归不调用付费 AI。Debug 主包／测试包已更新。文本差异检查通过；未重跑全量 Lint／Release/R8。
这仍是单张英文示例图的结果，未验收真实日文极小字、注音、复杂底纹或不同供应商的稳定率。

## 可重复的手动入口

`MangaAiCalibrationInstrumentedTest` 默认在解析服务前 skip，不会在普通 CI／真机测试中调用付费 AI。
只有显式模式参数才运行；inventory 只导出非敏感模型信息，prepare 只准备候选和裁剪，
render／replay 只重放已有计划；run／pipeline 才调用付费 AI。
run 使用应用中的 Gateway 和已有配置，密钥始终留在应用，不把异常 HTTP 正文、请求 URL、
凭证或思考文本导出到日志／报告。存在多个候选模型时必须传入 profile ID，不自行猜选。

构建并安装 Debug 主包／测试包后，先将图片放入
`io.legato.kazusa.debug` 的 `cache/manga-ai-calibration/source.png`。
可用 `adb push` 到 `/data/local/tmp/codex-manga-calibration.png`，再由 `run-as` 复制进缓存，
校验传输前后 SHA-256。避免通过终端文本 stdin 传 PNG 二进制，部分 Windows ADB 链路会破坏数据。

```powershell
# 仅盘点配置，不发 AI 请求
adb -s SERIAL shell am instrument -w -r `
  -e mangaAiCalibration inventory `
  -e class io.legado.app.feature.manga.translation.MangaAiCalibrationInstrumentedTest `
  io.legato.kazusa.debug.test/androidx.test.runner.AndroidJUnitRunner

# 付费手动调用一次，默认要求唯一 deepseek-flash；可显式传 profile ID
adb -s SERIAL shell am instrument -w -r `
  -e mangaAiCalibration run `
  -e mangaAiCalibrationModel PROFILE_ID `
  -e class io.legado.app.feature.manga.translation.MangaAiCalibrationInstrumentedTest `
  io.legato.kazusa.debug.test/androidx.test.runner.AndroidJUnitRunner
```

实验像素合同增加 `-e mangaAiCalibrationCoordinates pixels`；可用
`-e mangaAiCalibrationReasoning off` 显式选择测试强度，否则沿用最近漫画设置／模型默认值。
编号实验使用 `-e mangaAiCalibrationAnchors true`。pipeline 直接走正式 Gateway，使用独立临时书身份，
强制思考关闭；完成后删除该临时书目录，不修改用户原来的漫画／模型设置。
replay 从 `cache/manga-ai-calibration/replay-plan.json` 读取已存计划，经过相同正式管线生成两种本地合成图，
明确不调用外部 AI；仅适用于本校准单页帧。render 直接绘制 cache 中的 plan.json。
手动 run 会短暂打开 MainActivity，避免后台冻结；不修改永久电源策略。
请求仍受原有网络／流式超时约束，大图预算为 4 Mi pixels／文件 8 MiB。

导出目录包含原始响应、标准化响应、可用计划、公开请求参数、计时和校验结果。
每次 run 清理该目录上次输出，比较实验前应先导出。`adb exec-out run-as ... cat ...`
在部分环境下会以退出码 0 输出找不到文件的错误文本，导出工具仍须解析 JSON 后才保存。

本次完整响应、人工参考、逐区域指标及图片在本地忽略目录
`build/reports/manga-translation/calibration/{baseline,pixels}/`；局部对照图为
`build/reports/manga-translation/calibration/comparison-summary.png`。
编号实验与正式管线结果另存 anchors／pipeline／replay 子目录；replay/rendered-smart.png
为擦字调整后的本地擦字译图，rendered-patch.png 为覆盖译图。
手动入口编译及 `verifyConfigArchitecture` 已通过；生产翻译逻辑没有因此切换协议。
PLK110 默认参数运行时，该手动用例的 assumption 状态为 skip，另外 5 项现有原生漫画用例通过；
这次默认验证没有发起 AI 请求。导出报告后已清理设备上的校准临时图和响应文件。

## 后续门槛

英日韩补充样本、混合横竖排问题及墨迹方向提示的实测见
[英日韩混排校准](manga-cjk-calibration.md)。后续该记录的 v3-direction 合同覆盖本文历史 v2
身份说明；原有英文实验仍按其当时版本保留，不能与新样本混算准确率。

将这张图与真实日文漫画页组成固定回归集。分别记录文字检出／拆分、源字覆盖、气泡安全区、
字号和重叠指标，不能只统计合法坐标或 JSON 成功率。
后续优先验证显式像素合同，以及由候选气泡／图像字形证据约束模型框的位置；
AI 保留识字、翻译、语境和行列排版职责。没有图像证据的固定平移／缩放补偿不足以解决这组结果。
