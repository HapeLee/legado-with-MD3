# 密集竖排漫画校准

测试样本：用户提供的「黑白漫画对白气泡风格合集.png」，1536×1024；
SHA-256 `f80ec1a72e04850c30b07ff5a898c3af6ffa62f76d51c4b20361125f569ade9f`。
原图不加入 APK 或测试资源。运行输出位于本机 `build/reports/manga-translation/complex/`。

## 方法与基线

PLK110 真机、应用内现有 OAI Chat Completions 配置，关闭思考。仅在设备内读取密钥；
测试导出仅包含公共样图、模型 ID、响应、定位和译图。使用独立测试书籍和模型缓存，
不改变真实书籍设置，不把临时权重标记为用户已安装模型。

肉眼检查正文共有 53 个物理文字区，包括独立标点及连体气泡中的各个片段。
RT-DETR-v2 产生 55 个编号锚点，其中 a33（建筑）、a36（藤蔓装饰）是假候选。
这不是逐字标注语料，不能用区域数代替 OCR 字符准确率。

| 基线 | 实际请求数 | 区域数 | 整段耗时 | 观察 |
| --- | ---: | ---: | ---: | --- |
| Flash，AI 排版，本地擦字及覆盖重排 | 1 | 53 | 19.36s | 正确忽略两个假候选；竖排和连体气泡分区可用，擦字会损伤部分轮廓 |
| Flash，本地排版，本地擦字及覆盖重排 | 1 | 49 | 25.39s | 旧整页估坐标路径明显偏移、漏擦，部分长句过小 |
| V4 Pro，AI 排版，本地擦字及覆盖重排 | 1 | 55 | 40.46s | 格式有效但多处原文幻觉，错误接收假候选，不能视作识别通过 |
| Flash 原计划回放，MI-GAN 及覆盖重排 | 0 | 53 | 34.02s | 模型修复改善轮廓附近背景，但有淡字影；覆盖模式不运行模型 |
| Flash 原计划回放，LaMa 及覆盖重排 | 0 | 53 | 180.88s | 同样存在淡字影；此设备上整页成本明显高于 MI-GAN |

以上耗时包含整条管线及第二次 PATCH 重排，含 AI 请求的行与纯回放行不可直接相减
作为单个推理耗时。所有回放使用同一份 Flash 原始响应，固定文本、锚点和排版，
避免模型重新翻译造成对照失真。两种图片编辑／生成模式未测：当前没有配置相应图片模型。

Pro 的反例：原图 a6 为「……うん／わかった」，返回「あんたは」；a3 为
「ちょっと／待ってください」，返回「俺が」。JSON／坐标验证只能检查结构，无法识别这种幻觉。

## 本轮修复依据

1. 过紧的擦字分析裁剪把长曲线切成短小片段，片段大小像文字，造成气泡边线误擦。
   扩大的是连通成分观察上下文，不是矩形擦除范围；裁剪边缘的不完整成分不能作为可靠字形。
2. RGB 图像用双线性缩放，旧蒙版仅近邻放大，笔画的灰色边缘落在模型认为无需修复的区域。
   推理蒙版覆盖双线性采样的全部贡献像素，最终仍只合回原始擦除蒙版内的像素。

两个像素回归测试均在旧实现失败，覆盖上述问题；最终验证与优化后对照见后续记录。

## 优化后的回放

同一份 Flash 原始响应，在设备上完成 SMART 和随后 PATCH 重排：LOCAL 7.83s、MI-GAN
36.38s、LaMa 189.88s，均为 53 区、零外部 AI 请求。检查整图及放大后的胶囊、连体、
黑底、粉色装饰气泡：先前的明显轮廓缺口已消除，仍有少量浅灰字影和纹理修复痕迹。
这组样图没有表明 LaMa 的视觉收益能抵消其推理成本。

完整页擦字蒙版从 100,847 像素变为 96,685 像素，移除 4,603 个旧选点、增加 441 个
上下文中确认的选点；不把蒙版变小等同于质量得分。另以原始像素逐一比较：LOCAL 和
MI-GAN 的最终 SMART 图在「擦字蒙版及目标排版区域」以外均为零像素变化。
`border-comparison.png` 展示原图、旧本地处理、新本地处理、MI-GAN 的同一裁剪。

关闭 AI 排版的管线也使用原图＋编号裁剪，正常一次请求返回原文、译文、源文字方向、
误检测拒绝列表和对话顺序；不要求 AI 返回坐标、字号或排版。客户端保留候选锚点和
原图字号测量，本地按实际译文换行；不再对这些锚点运行旧的整页气泡位置恢复。
无候选时保留原来的视觉定位能力；独立 OCR 与逐区识别仍走原有分阶段路径。

初次真机请求把两个假候选同时放入 regions 和 ignoredAnchorIds，触发一次格式纠错后得到
53 区；两次请求共 25.24s。随后检查发现通用 parse 会清除客户端字号测量，已改为在
清理不可信字段之后，按锚点 ID 补回本地原图测量，并检查发布快照保留这些值。

手动入口仍为 `MangaAiCalibrationInstrumentedTest`：`mangaAiCalibration=pipeline` 调用
配置模型；`replay` 只回放缓存响应。显式设置 `mangaAiCalibrationTypesetting=true/false`
及 `mangaAiCalibrationInpaint=LOCAL/MI_GAN/LAMA`。原始响应回放需
`mangaAiCalibrationReplayRaw=true`；支持 Markdown JSON 围栏和不含 typesetting 的纯翻译响应。
离线模型放在该测试自己的 `cache/manga-ai-calibration/models/<revision>/model.onnx`，
沿用生产校验与推理，不改生产模型安装目录；测试结束删除临时权重。

补回测量后的本地排版回放：LOCAL 7.43s、MI-GAN 38.12s、LaMa 209.17s，均完成 53 区与随后 PATCH
重排，无外部 AI 请求。逐区检查 53 个源框与最初检测锚点完全相同、字号等于对应源方向的
原图测量，typesetting 均为空；本地兼容模式仅使用本地排版。三者蒙版与 AI 排版
模式一致，为 96,685 像素；六组最终 SMART 图的区域外像素均无变化。`local-layout-comparison.png` 展示旧估坐标、
补锚点但丢字号的中间问题、最终本地排版及 MI-GAN 对照。

## 代码与验证

- `MangaSourceInkCleanup`／`MangaPageComposer`：曲线裁剪保护、有限观察上下文、使用可靠原文字号。
- `MangaLocalInpainter`：双线性 RGB 与推理蒙版一致，最终合成仍保留原蒙版外所有像素。
- `MangaAiAnchors`／`MangaTranslationRepository`：本地排版也绑定编号锚点；解析后补回客户端字号，
  与模型排版字段隔离；升级渲染、重排和背景缓存标识，保留旧发布译图。
- `MangaAiCalibrationInstrumentedTest`：实际生产推理接入可选测试模型目录，导出纯擦字蒙版，
  支持有／无 AI 排版的原始响应回放。

JDK 21：`:app:testAppDebugUnitTest :app:assembleAppDebug :app:assembleAppDebugAndroidTest
verifyConfigArchitecture --continue --offline --no-configuration-cache`，使用本机已有临时 Maven
初始化脚本及 `mangaTestClasses=**/manga/translation/**,**/domain/model/manga/**`。
最终 284 项测试、34 类全部通过，无失败／错误／跳过；APK 编译、打包、架构检查通过，
最终 APK 已安装 PLK110。XML 和摘要留存于 `unit-results.zip`、`unit-summary.json`。
测试结束已删除设备上的测试图片、响应、两个临时模型和空测试目录；保留主机报告，
未更改生产模型安装目录、真实书籍设置及用户提供的原图。

已执行 `git diff --check`，并对本轮未跟踪的新文件单独做 `git diff --no-index --check`。
尚未验证：其他语言／真实章节的同轮全量对照、端侧 OCR 模型逐一对照、云端图片编辑服务。
浅色笔画残影、复杂纹理的修复痕迹、AI 原文幻觉与标点竖排美观仍是质量限制；
结构校验通过不能代表这些视觉／语义问题已经消失。
