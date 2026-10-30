# 漫画本地视觉模型与参考项目评估

状态：2026-10-27，已进入 Android 实施。RT-DETR、固定区域字号与形状排版已经接入；
运行与验证结果以主文档[漫画 AI 翻译](manga-ai-translation.md)为准。
已采用 overlay 的固定区域字号策略，并完成内置模型、可信掩膜缓存、局部合成与免在线 AI 重排。
目标是修正原文字号不稳、气泡误合并、译文越界和擦字残留，保留当前成功译图持久化与免 AI 重排。
不在本切片引入离线 LLM、替换阅读器或迁移 KMP 模块。

## 已核对的依据

阅读了用户的[网页端分享对话](https://chatgpt.com/share/6ae03f68-2058-83ee-9bb9-90ebcac46e50)，
并检查三个本地 checkout 的源码，而非只依据 README 推荐：

| 项目 | 本地 HEAD | 主要职责 |
|---|---|---|
| `D:/Project/Yakuyomi` | `4123ee34f9b59c6472fa10f150c926c2ba262af4` | Mihon 阅读器、后台队列、模型管理、缓存和重绘入口 |
| `D:/Project/yakuyomi-engine` | `f990b451bdb4b42d1946868a18c15937a3053b09` | NCNN DBNet、CTC OCR、AOT-GAN、分组和 Canvas 排版 |
| `D:/Project/overlay-translator` | `64a7aa2c9605c88dd35bdeea42ab5c0f1c18ce5e` | ONNX 漫画气泡检测、OCR 文字归属、局部轮廓提取和形状排版 |

HEAD 只标记此次核对的提交，不能替代各 checkout 中尚未提交改动的内容。
本地路径仅供追溯，未来构建不得依赖这些绝对路径。

## 结论

优先引入漫画专用的本地检测证据与形状约束，不能继续只依靠 AI 粗框和像素阈值估算。
当前采用 `overlay-translator` 使用的 RT-DETR-v2 INT8 小模型，配合已有 ML Kit 文字行证据；
形状感知排版、原文字号测量与译文翻译分别负责自己的结果。
Yakuyomi 的 DBNet／CTC 是增强路线候选，AOT-GAN 是复杂背景修复候选。

检测模型不会直接给出排印字号，也不会自动保持两颗相连气泡之间的对白连贯。
“框、轮廓、笔画、文字、阅读顺序、字号”仍须分开保存和验证。

## 最值得复用的实现

### overlay-translator：检测框之后的处理

关键源码在 `app/src/main/java/com/gameocr/app/`：

- `ocr/BubbleDetectionModelProvider.kt`：内置模型复制到私有路径，固定字节数和 SHA-256 验证。
- `ocr/MangaBubbleDetectionDebugEngine.kt`：虽然名字含 Debug，实际有 ORT 会话复用、输入缓冲、互斥推理和分阶段耗时。
- `ocr/MangaBubbleDetectionPostprocessor.kt`：区分 `bubble`、`text_bubble`、`text_free`，去除近重复检测；
  相交不等于重复，不能把所有交叠框合成一个。
- `ocr/DetectorGuidedBubbleMaskExtractor.kt`：先把 OCR 成员分配给检测框，再在受限 ROI 中用边缘／颜色恢复内部；
  失败时有受约束椭圆回退，不是另一个神经网络输出的精确分割。
- `ocr/MangaBubbleTextAssignmentRefiner.kt`、`BubbleMaskAssociator.kt`：文字成员与容器实例的归属。
- `ocr/ShapeAwareTextLayout.kt`：每行／列取得整条高度／宽度上都有效的掩膜区间，检查完整矩形内部；
  圆弧、凹口和内部空洞不能只检查四角或两端。
- `ocr/ShapeAwareBubblePatch.kt`、`LocalBubbleBackgroundRepairer.kt`：局部透明贴片、确认过的修复像素与掩膜约束。
- `overlay/TranslationGlyphSizing.kt`：从行厚度中位数估计正文大小，竖排再用总列长度／字数限制注音和边距造成的高估。

值得带入的测试包括椭圆、内部空洞、完整行矩形包含、横／竖排、左右列顺序、标点禁则、成员归属和修复范围。
掩膜可靠性仍要验证：检测可能漏框，连通背景可能串联邻泡，椭圆只是一种近似，不应无条件升级为可信边界。

**字号策略采用其固定区域模式。** `translationGlyphSizeRange(..., constrainToBounds=true)` 的上限允许为原文估算值 1.6 倍，
下限为其 0.35 倍。用户确认优先采用这套策略，字号滑块改变搜索范围，不再承诺 1.0 倍与原文字形严格等大。
`ShapeTranslationLayoutPolicy` 也会把可用掩膜裁到文字包络附近，不能把它当作充分利用整个气泡的通用方案。

### yakuyomi-engine：精确文字证据、分组和分析素材

- `inference-core/.../Detector.kt`：DBNet 返回文字行四边形和逐像素笔画掩膜；
  四边形可用于去倾斜测量，笔画掩膜适合约束擦字，二者不是气泡内部掩膜。
- `inference-core/.../Grouping.kt`：相近文字行连边后，使用 MST 离群长边递归拆分，缓解邻近对白过合并；
  保留行顺序、方向、区域角度和自然坐标尺寸。这是几何分组，不是可靠的气泡实例分割或跨气泡语义分组。
- `engine/.../Pipeline.kt`：`PageAnalysis(mask, regions)` 保留原文／译文和掩膜；去字可与网络翻译重叠执行。
- `inference-core/.../NcnnBackend.kt`：原生资源生命周期、统一线程池、推理锁与模型复用需要一起考虑。

**排版器不直接替换。** `Renderer.kt` 明确放弃连通气泡 flood-fill，使用扩展文字框；
从最大可用字号向下搜索再乘 `fontScale`，默认最大 60px、最小 9px、缩放 0.85，
并非原文实际字号作为固定目标。它也没有本项目需要的完整容器轮廓约束。

**源码与文档有差异。** `TranslationEngine.kt` 注释仍提 ONNX 和单实例非并发安全；
当前后端与 README 已改为全 NCNN，并描述暖机后的多页并发。
接入时应以锁、暖机、释放与调用方的实际契约测试确认，不能直接据 README 开放并发。

`Yakuyomi` 阅读器负责模型与任务生命周期，不能把它视为独立检测 SDK。
保留本仓库 AI Gateway、设置、思考面板、分析缓存和流式长图合成；不引入 Mihon 阅读器或另一套翻译设置。

## 模型候选

大小为权重文件的十进制 MB，不含推理运行时、字典、Bitmap、Tensor 或会话工作内存。

| 能力 | 模型与文件 | 权重大小 | 上游标注 | 建议 |
|---|---|---:|---|---|
| 气泡与文字区域检测 | RT-DETR-v2 `detector-v4-s_int8.onnx` | 11.1 MB | Apache-2.0 | 首个模型验证切片 |
| 文字行与笔画掩膜 | Yakuyomi DBNet `dbnet_detect.ncnn.param` + `.bin` | 153.0 MB | GPL-3.0 | 漫画文字定位／擦字增强候选 |
| 轻量日文识别 | `ppocr-rec-v6-small-manga.onnx` 与匹配字典 | 约 21 MB | Apache-2.0 | 在固定样本上对比 ML Kit，需另配行检测器 |
| 漫画行识别 | Yakuyomi `ocr_48px_ctc` 两份 param 与共用 bin | 83.1 MB | GPL-3.0 | NCNN 增强组合候选 |
| 复杂背景修复 | Yakuyomi `mit_aot_fixed512.ncnn.param` + `.bin` | 11.4 MB | GPL-3.0 | 按需启用，纯色气泡无需运行 |

RT-DETR 模型卡定义三类输出；`overlay-translator` 的输入为 640×640 RGB NCHW 和原图尺寸，
结果为 labels、boxes、scores，**没有实例轮廓掩膜或字号**。
该 checkout 的模型实际为 11,120,765 字节，提供方固定 SHA-256 为
`5FE9E4F576E49D4E7E8B0E029D6D3CDC252ABD4694113E1CAE120E62C931EA79`；
后续采用时仍须实际校验，不根据文件名信任任意权重。

PP-OCRv6 manga 模型卡说明它只识别日文、输入是行裁切 `[N,3,48,W]`，不会自动剔除注音。
它不是检测器，也不能替代韩文 OCR。其模型卡数据与 overlay README 中通用 PP-OCRv6 small 的测速不是同一个实验。

Yakuyomi `models.json` 的三类翻译权重合计约 247.5 MB；
`manga_seg_s` 与 `cartoonseg` 是夜读人物分割，不是漫画文字字形或气泡模型，本方案不需要它们。
不应下载整个 manifest 来实现气泡翻译。

## 字号与空间应怎样解耦

建议保存原文证据，而非只保存换算后的目标字体 em：

1. 每条文字行的四边形／精确矩形、方向、主文与注音角色、识别置信度，以及原图坐标的字形高度／宽度估计。
2. 气泡实例 ID 与内部掩膜；笔画掩膜另存，不能拿整个气泡内部去擦字。
3. 正文字号估计值与估计方法、置信度。按行厚度、去倾斜笔画、正文字符间距交叉校验，
   排除注音、孤立标点和边框；低置信度时可参考同页相近正文，不能把标题和对白机械统一。
4. 阅读顺序和对白组独立于气泡实例：两颗相连气泡仍是两颗，翻译可以整组发送，并按实例编号返回片段。

按 overlay 策略从正文行厚度／列长估算原文基准，结合实际目标字体的字形度量，
乘用户缩放一次，得到约 0.35–1.6 倍的固定区域搜索范围；选择能完整放下的最大候选字号。
短译文允许比原文更大，长译文可以缩小，边界仍由可信掩膜和完整行列矩形约束。
渲染日志记录源基准、倍率、搜索范围、最终字号和是否使用掩膜；仅看到最终字号无法判断测量与容纳取舍。
不让未知正文大小的回退框静默变成整段高度，亦不宣称模型能恢复原字体文件或排印点数。

横排每行、竖排每列都检查安全掩膜中完整矩形及描边边距；绘制裁剪作为最后保护，不能靠裁剪隐藏漏字。
掩膜失败或正文识别缺失时明确降低置信度／保留当前结果，不静默启用新的付费视觉请求。

## 接入顺序与回滚

当前切片：RT-DETR + 当前 ML Kit 文字行，持久化容器 ID 和压缩掩膜，接入完整掩膜行／列排版。
先验证“两个容器不合并、原文与气泡分开、几何映射一致”。模型的新增价值仍需固定漫画样本比较，不能只看合成白底框。
初次评估时 DBNet／CTC、PP-OCRv6 manga、AOT-GAN 均为候选；当前 DBNet／CTC 与 V6 的接入状态见后文，AOT-GAN 仍未接入。

首个 ONNX 模型先采用与权重直接匹配的 ORT Android 路线评估，单任务 CPU、延迟加载、会话复用和后台推理；
当前 Legado 未接入 ORT／NCNN，11.1 MB 不等于安装包增量。不得只因 Yakuyomi 的另一组模型更快，
便宣称 NCNN 对该 RT-DETR 更快或可直接加载 ONNX；转换和算子兼容需要独立验证。
NCNN 组合若成为实际需求，再决定有真实消费者的 Android 模块边界，不提前创建空 KMP／平台模块。

运行时、模型文件、Bitmap 和 native handle 留在 Android 实现侧；领域数据表达坐标、掩膜资产引用与版本，
不新增 `Context`、ORT／NCNN 对象或 `File` 泄漏到共享契约。宿主／数据层组装，UI 不加载模型。
运行时不可用、模型缺失和设备不支持需可观察，不能用空检测结果伪装“页面没有文字”。

检测、识别、翻译、去字与排版分别缓存，模型／几何版本参与对应身份。
升级检测模型不主动撤下成功译图，改变字号或本地合成方式不增加 AI 请求。
新增本地模型不应通过更新分析身份，迫使所有已付费页面重新翻译；已有原文译文与新实例的匹配失败时须显式处理。
保留现有检测／发布路径作为回滚点，不更改阅读器切换图片的契约或长图输出内存预算。

## 验收与未验证事项

- 代表样本：相连双圆泡、竖胶囊横排、长短译文、带注音竖排、黑底白字、斜字、韩漫长条与画面叠字。
- 检测：容器召回、重复／误合并、正文归属、主文／注音分离，不能只统计框数。
- 字号：标注原文单字尺寸，对比估计误差与最终缩字原因；同锚点重复重排确定一致，用户倍率只换算一次。
- 排版：所有行列矩形和描边在所属安全掩膜内；组内片段连贯且不重复；无截断和重叠。
- 性能：同设备、同样本，分别测冷启动／暖机 p50、p95、进程 PSS／native 峰值、长图内存和 UI 帧延迟。
- 兼容：ABI、native 库打包与 16 KB 页大小、Release/R8、取消后不发布、模型校验与损坏恢复。

参考源码已经移植必要算法和合同测试，内置模型固定哈希校验，ORT Android 固定 1.28.0。
没有运行外部项目完整测试集，也没有对三套视觉路线做同机效果／性能比较。
Yakuyomi README 的约 0.79 秒检测／1.25 秒 OCR 是作者特定设备／样本报告；
其报告的旧版峰值约 1.9–2.1 GB，不能当作本应用的耗时或内存承诺。
分享对话中的轻量内存预算是规划假设，不能覆盖完整 NCNN 流水线的项目报告。

## 可选 OCR 与独立翻译模型

漫画翻译面板可分别设置 OCR 路线和文字翻译模型：

- AI：保留「跟随翻译模型」的旧合并请求；显式选择支持图片输入的 AI 模型时，
  该模型只识别原文／紧框，再由翻译模型处理纯文字。沿用 AI 设置中的供应商、协议与思考配置。
- ML Kit：保留内置的中、日、韩、英文识别器，不要求 Google Play 动态模块下载。
- PP-OCRv5 mobile：新增端侧 DBNet 检测与 CTC 识别，支持中、日、英文。
  检测器、识别器、字典共 21,509,645 字节（约 20.5 MiB），按需下载，不增加 APK 中的模型体积。

PP-OCRv5 使用 PaddlePaddle 官方 ONNX 文件，检测器固定提交
`e6f4fa85f00e168c862bc462aebca69eef9b3d3d`，识别器／字典固定提交
`ed152b8b495f84de93cda5709d768548a9127622`；两仓库均声明 Apache-2.0。
各文件的 SHA-256 与大小在 `MangaPaddleModelCatalog.files` 中固定。
下载到应用不参与备份的私有目录，文件校验后原子替换，只有整套就绪才允许运行。
下载可以取消、失败重试、删除；重试复用已校验的完整文件。删除与 native 识别串行，
不会在推理过程中移除模型文件。下载由阅读器 ViewModel 生命周期管理，未接入后台系统下载服务。

OCR 引擎／独立 OCR 模型参与分析缓存身份，旧默认配置保持原有身份；已发布译图不会因切换设置被撤下。
「仅重新排版」仍使用保存的文字／锚点，不重新 OCR。要将新引擎应用到已译页，应显式重新翻译。
本次复用参考项目的 DB 几何与张量／CTC 合同，没有照搬其完整多尺度、方向分类和文字恢复流水线。
2026-10-28 的多模型切片补充 PP-OCRv6 Small、Medium 和日文漫画版，详见下节。
Yakuyomi NCNN OCR 与 AOT 擦字仍未接入。

原始引擎接入以 66 项定向单元／Compose 测试验证；2026-10-28 四边形与裁切切片补跑 103 项定向测试，全部通过。
当前已保存 ML Kit／Paddle 文字行四边形，改用直接到 48px 输入的 bicubic 采样，补边保留 detector unclip 恢复范围并避让近邻。
源字号使用四边形行厚度；无归属 Paddle 散行增加图分组／MST 拆分，已确认物理气泡保持分别处理。
该四边形切片的 Debug 主包、测试包和架构门禁通过；当时没有增加 NCNN 或新的模型下载项。
PLK110 / Android 17 原生测试共三项通过，覆盖完整 Paddle 流水线、ML Kit 四边形和长图 PNG 解码。
Paddle 在 800×320 合成英文／日文／12° 斜字上正确识别，检测加识别为 424／300／243ms；运行时是 APK 的 ORT Android 1.28.0 CPU。
这些时间包含校验和会话创建，权重已在设备测试夹具，不含 AI 翻译／擦字／排版；不代表真实漫画准确率或 p95。
此前 Python ORT 1.31.1 桌面联测仍只作为原始模型格式验证记录，不能替代上述 Android 证据。
真机用例可显式传 `mangaDownloadPaddle=true` 下载约 20.5 MiB，也可由 `mangaPaddleFixtures` 指定独立的本地测试夹具。
两种方式均使用临时安装目录并按生产目录中的大小／SHA-256 校验；结束后删除测试模型，不改用户已下载模型和电源策略。
本轮未重跑全量 Lint 或 Release/R8，未验收真实漫画、低内存设备与复杂装饰字体。

## 多模型下载与选择（2026-10-28）

| 模型 | 整套字节数 | 面板开放语言 | 说明 |
| --- | ---: | --- | --- |
| PP-OCRv5 mobile | 21,509,645 | 中、日、英 | 保留原安装目录与缓存身份 |
| PP-OCRv6 Small | 31,190,469 | 中、日、英 | 官方 Small 检测器＋Small 识别器 |
| PP-OCRv6 Medium | 86,586,071 | 中、日、英 | 同一 Small 检测器＋较大识别器 |
| PP-OCRv6 Small 日文漫画版 | 31,174,705 | 日 | fumetodev 微调识别器＋官方 Small 检测器／字典 |
| PP-OCRv5 mobile 韩文 | 18,341,344 | 韩、英 | 专用识别器／字典，V5 检测器 |
| PP-OCRv5 mobile 东斯拉夫语 | 12,718,683 | 俄、乌克兰、白俄罗斯、保加利亚、英 | 专用识别器／字典，V5 检测器 |
| PP-OCRv6 Tiny | 6,298,800 | 中、英 | Tiny 检测器＋识别器；字典不含日文 |

每套独立保存、校验、删除，模型文件不打入 APK。V5 原目录无需迁移；旧设置的默认 ML Kit 不变。
下载前如另一已装套件存在 SHA-256／大小一致的有效文件，复制后再次校验，避免重复网络下载；
文件仍各自占用磁盘空间，删除一套不会破坏另一套。所有下载／删除／推理保留串行文件锁，下载一次一套。
模型选择按书持久化；关闭面板、切书不会把下载目标改成当前模型，取消操作带原模型 ID。
选择未下载模型时提示下载，不自动联网、不静默回退到 V5。
下载仍由阅读器 ViewModel 管理，离开其生命周期可取消；尚未接入 WorkManager 或断点续传。

实际检查权重 I/O 与配置：V6 检测输出单通道 DB 概率，采用其 YAML 的阈值 0.2／框均值 0.45／unclip 1.4；
三种识别器均为 `[N,3,48,W] → [N,T,18710]`，官方 Small 与 Medium 字典的 18,708 项内容相同。
通用版 YAML 要求 BGR，漫画模型卡要求 RGB，按所选模型分别处理，CTC 仍使用 blank＋字典＋space。
上述 Small／Medium 通用字典不包含常用韩文音节，不将模型卡的“50 种语言”直接映射为面板韩文支持。
韩文改用后来补充的 V5 专用套件，详情与云端识别来源见 [云端 OCR 与补充模型](manga-cloud-ocr.md)。
本轮没有引入新的推理库、OCR 方向分类器或振假名过滤；漫画版仍可能把注音当作独立文字。
真实漫画效果与不同设备上的内存、速度需进一步用代表样本比较，不能用上游模型卡成绩替代本应用验收。

多模型切片验证：68 项定向单元／Compose 测试全部通过（6 个类，无失败、错误或跳过），
覆盖多套安装／重建、校验文件复用、独立删除、缺失模型拒绝、实际引擎路由、设置保存、
旧译图保留、漫画版语言拒绝，以及紧凑／展开窗口切换后下载与取消目标正确。
实际执行 `:app:testAppDebugUnitTest`（定向集）、`:app:assembleAppDebug`、`:app:assembleAppDebugAndroidTest`、
`verifyConfigArchitecture` 与 `git diff --check`，通过。下载取消完成前的重入保护另补跑 ViewModel 定向集。

PLK110／Android 17／APK 内 ORT Android 1.28.0 CPU 上，三个新模型各一项原生测试通过，共 10 个输入：
Small 与 Medium 各验证英文、日文、12° 英文斜字、中文；漫画版验证日文与 12° 日文斜字。
均校验生产清单中的真实权重 SHA-256，执行检测＋识别，要求文本完全相同且保留四边形。
设备通过独立测试夹具加载权重，未验证手机网络直接下载；桌面已下载并核对固定来源文件。
测试后已清理专属夹具与测试安装目录，没有修改已下载的用户模型或设备电源豁免。
本轮未重跑全量 Lint／Release R8，未测真实漫画准确率、低内存设备或持续运行发热。

## OCR 检测参数与预处理（2026-10-28）

漫画翻译面板中选择本地 Paddle OCR 后，可选择「速度优先」或「精度优先」，
分别把检测输入长边上限设为 960px／1920px；不自动切换模型、不额外切图。
这次参考屏译／overlay-translator 的输入分辨率档位与独立阈值设计，
尚未移植其精度档位中的漫画多切片、多次推理策略，不能据此承诺与参考项目相同的效果。
旧设置仍为速度优先；所有图像预处理默认关闭。

「OCR 高级设置」中的自定义检测参数直接传入 DB 后处理：

| 参数 | 范围 | 实际作用 |
| --- | --- | --- |
| 二值化阈值（检测概率图） | 0.05–0.95 | 概率图生成连通候选区域，调低可能增加文字候选与噪声 |
| 连通区域得分阈值 | 0.05–1.00 | 过滤区域平均概率较低的候选框 |
| 外扩比例 | 0.5–3.0 | 扩展识别裁切；源锚点、擦除区域与译文框继续使用检测支持区域 |

未启用自定义时跟随模型默认值：V5 三套为 0.3／0.6／1.5，
V6 Tiny 为 0.2／0.4／1.4，其余 V6 三套为 0.2／0.45／1.4。
检测分辨率档位与上述三个阈值相互独立；关闭自定义后切换模型会跟随新模型默认值。

本地 ML Kit、Paddle 和云端 OCR 均可显式开启以下识别副本预处理：
2× 上采样、颜色反转、Otsu 自动二值化，顺序为缩放→反色→Otsu。
检测概率图的二值化与图片像素的 Otsu 二值化是两个独立步骤。
预处理副本限制为 4,194,304 像素、长边 4096px；大图可能缩小，小图最多放大 2 倍，
引擎本身还可再次缩放。只反色／Otsu 同样受此预算约束。
采用逐行像素缓冲，预处理可响应取消；不可取消的 native 消费者结束后才释放副本。
Otsu 可能丢失细笔画、描边或灰阶细节，不自动开启。
AI 图片识别及整图生成不使用这些预处理项，云端服务的专有检测参数仍由其协议配置决定。

设置通过现有 Gateway 按书保存，解析旧 JSON 不需要迁移；恢复默认一次提交所有相关字段。
OCR 输出按识别副本尺寸归一化到 0–1000，沿用原图坐标框架；合成、底色测量和擦除仍读取原图。
只有实际参与识别的选项进入分析缓存身份，ML Kit／云端不会因残留的 Paddle 阈值改变缓存。
参数默认值本身保持原缓存身份；源几何升级另有版本，切换参数不撤下已发布译图；已有页面需「重新翻译」重新识别，
「仅重新排版」继续复用保存的原文、译文和锚点。

验证：91 项定向单元／Compose 测试通过（无失败、错误或跳过），覆盖默认与模型阈值、
参数校验／持久化／缓存、真实概率图候选过滤与外扩、预处理像素／尺寸／取消释放、
ML Kit／云端输入路由、紧凑／展开界面中的档位与滑块设置。
实际执行 `:app:testAppDebugUnitTest`（定向集）、`:app:assembleAppDebug`、
`:app:assembleAppDebugAndroidTest`、`verifyConfigArchitecture`，均通过。

PLK110／Android 17／ORT Android 1.28.0 CPU 原生测试通过，使用校验过生产哈希的 V6 Small 夹具：
默认模式识别中、日、英和 12° 英文斜字；另用 1800×900 输入验证精度优先、2× 上采样、
Otsu、自定义阈值和外扩的组合，要求文本完全相同、归一化左右锚点偏差不超过 20（1000 标度），
且保留四边形。将区域得分阈值调至 1.0 时候选全部被过滤，证明参数进入真实推理后处理；
原图全像素哈希不变。已安装当前 Debug 包，清理专属测试夹具与安装目录，未改设备电源豁免。
这不是代表性漫画准确率或速度对比；没有验收低内存设备、付费云端预处理效果、全量 Lint 或 Release/R8。

## 原始来源

- [PP-OCRv5 mobile 检测器](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx)
- [PP-OCRv5 mobile 识别器](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx)
- [PP-OCRv6 Small 检测器](https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx)
- [PP-OCRv6 Small 识别器](https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx)
- [PP-OCRv6 Medium 识别器](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_onnx)

- [RT-DETR 模型卡](https://huggingface.co/ogkalu/comic-text-and-bubble-detector) 与[实际权重列表](https://huggingface.co/ogkalu/comic-text-and-bubble-detector/tree/main)。
- [PP-OCRv6 small manga 模型卡](https://huggingface.co/fumetodev/PP-OCRv6_small_rec_manga_ONNX)。
- [overlay-translator](https://github.com/ciddwd/overlay-translator)；代码 LICENSE 为 Apache-2.0，模型出处另见 NOTICE。
- [yakuyomi-engine](https://github.com/joyeli/yakuyomi-engine)，角色、大小、哈希、来源和作者标注的许可证见本地 `models.json` 与 `docs/MODELS_zh.md`。
- [Yakuyomi](https://github.com/joyeli/Yakuyomi)，阅读器与引擎关系见 `.gitmodules` 和 `LICENSE-YAKUYOMI.md`。

现有[容器／对白分组方案](manga-container-flow-layout.md)仍为待实现设计，本评估为其增加可选的模型来源与接入顺序。
