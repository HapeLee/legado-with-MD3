# 漫画翻译：本地预览切片

已接入离线／AI 文字翻译、按书自动翻译、长图分块和文字掩膜局部修复；新增图像模型管理与初版适配，真实权重及真机验收尚未完成。详见 [模型与排版方案的实施记录](manga-translation-models-and-layout-plan.md#11-初版实施记录2026-10-28)。以下此前切片说明属于原有基线，新增状态以末节为准。

## 当前可用路径

阅读菜单 → 全局 → 第一项「翻译当前页」，在同一设置面板内进入翻译子页。
长按漫画页面 → 翻译当前页，也进入同一子页，并绑定被长按的图片。
选择原文（日语、英语、韩语）与目标语言（中文、英语、日语、韩语）→ 下载模型／翻译。
子页返回按钮或系统返回先回到全局列表；退出翻译子页或关闭菜单会取消手动任务，自动任务继续执行。
译图在阅读器原位置显示，重新打开面板可切换原图、译图或清除译图缓存。
语言与自动／横排／竖排方向通过 MangaSettingsGateway 原子持久化。

图像获取沿用 Coil 和书源图片处理。OCR 与图片修复均在本机执行，不上传漫画图片。离线引擎不上传识别文字；AI 引擎仅向配置的服务发送 OCR 文字，并在翻译面板展示模型、服务地址及用量说明。
译图通过文字像素掩膜去字与局部插值修复，不再整块涂白。掩膜外原图像素保持不变，译文绘制在透明补丁上。
使用系统字体测量与有限字号候选搜索；区域无法在参考可读下限内容纳完整译文时保留该区域原文，不静默裁切，其他区域可以继续生成。
气泡按掩膜轮廓逐行／逐列计算可用空间；横竖候选比较、标点禁则和字素边界共同决定换行。竖排支持从右到左分列、标点方向、连续拉丁词旋转与短数字横排。
OCR、气泡、翻译、文字掩膜、背景修复及布局计划分别持久缓存。改变方向只重算布局与合成；含失败区域的结果可复用成功阶段后重试。中间缓存上限 64 MiB，与最终译图的 128 MiB 配额分开；清理译图缓存同时清理两者。

## 自动翻译与 AI 配置

- 「本书自动翻译」默认关闭，保存于 `Book.ReadConfig.mangaAutoTranslation`，经书籍观察链路恢复；换书不会沿用其他书的开关。
- 引擎与原文／目标语言使用全局漫画设置。默认离线；AI 复用当前默认模型，独立的 `translate_manga` 提示词可在 AI 提示词设置编辑，不复用小说翻译的正文输出格式。
- 可见区稳定 300ms 后串行处理当前原始图片（双页优先两张、长条按图片处理），随后预译同章下一张。不会主动预译整章或跨章图片。
- 手动指定优先；预译页进入可见区时复用正在执行的任务。离开窗口、关闭自动开关、后台暂停或退出阅读器会取消相应自动任务；返回菜单不影响自动任务。
- 当前会话记录成功、无文字、失败与主动取消，不因重复可见回调而重发。失败保留原图，手动重试；取消请求和配置更新使用代次保护，旧进度与结果不得覆盖当前状态。
- AI 输入为带稳定区域 ID 的文字列表，按上下文与输出预算串行分批（单批最多 32 区域）。超限区域直接失败，不截断。返回必须是严格 JSON 数组，每个 ID 恰好一次且译文非空；乱序按 ID 回填。
- 每个 AI 批次超时 90 秒，并通过请求级 `maxAttempts=1` 禁用适配器的自动重试。分别报告配置、鉴权、限流／额度、超时、格式、长度与网络错误，不自动切换引擎。
- 缓存基于原图内容、OCR／修复版本、语言、引擎以及服务路径、模型、提示词、有效生成参数；不包含 API 密钥。修改配置会取消旧任务并清除当前译图绑定。
- 清理缓存不会立即重新翻译当前窗口；原图选择在当前会话中保留。模型下载仍沿用 Google SDK 的 Wi-Fi 限制。

## 模型与运行限制

- OCR 使用随应用打包的日文识别器（也处理拉丁字符）和韩文识别器，按原文语言选择。
  固定依赖 `com.google.mlkit:text-recognition-japanese:16.0.1` 与
  `com.google.mlkit:text-recognition-korean:16.0.1`。
  这与原方案中的 CTD / PP-OCR 候选不同，目的是先验证最小阅读闭环。
  OCR 不再调用 Google Play 的模块检查和动态安装；离线翻译语言包仍独立下载。目标设备兼容性仍需真机验证。
- 离线翻译使用 `com.google.mlkit:translate:17.0.3`。首次使用下载所需语言包，下载限定 Wi-Fi；
  已下载后本地推理。日译中经过英语中转，漫画语境质量须实测。
- 模型下载由 Google SDK 管理。取消页面处理不能保证停止已交给 SDK 的模型下载；
  面板取消和超时可立即结束等待。原生识别/翻译调用结束后才释放相关图片与客户端。
- 单例 Repository 的 Mutex 限制重任务并行。ViewModel 的请求代次、书籍、页键和重试代次
  避免取消、换页、页面刷新后的旧结果覆盖。
- 原图先按需取得文件租约（在线）或 content/file source（本地），用项目现有 EXIF 区域解码器处理。
  OCR 和渲染不依赖 256px 获取预览。按 2 的幂采样至宽度不超过 1536px，输出保持采样后的完整高度。
- OCR 核心步长 1024px，两侧各保留 512px 上下文，单次识别最高 2048px。
  重叠区域按文字行几何去重，重复行连接两侧段落碎片，译文按合并后的区域生成一次。
  对于超过重叠上下文的超大字或异常断行，不保证 OCR 无损合并。
- 输出每次解码 512 行，遇到跨输出边界的文字使用相同全局补丁坐标。流式 PNG 编码器只保留一行
  RGBA 数据与有界压缩缓冲，写出单一 zlib 数据流及多个 IDAT 块，不创建完整长图 Bitmap。
  原图和 PNG 写出过程支持逐块/逐行取消，半成品不会成为结果缓存。
- 极端输入上限：采样后高度 1,000,000px、10,000 个 OCR 候选区域、单个修复区域高度 4096px。
  不支持区域解码的格式显式失败并保留原图。限制是资源保护，不能描述为无限长图支持。
- 结果存入应用 cacheDir 下独立目录。SHA-256 包含原文件内容、语言、采样率、模型/分块/修复版本；
  原始图片不修改。译图数据写出按剩余 128 MiB 配额检查，超限中止并删除临时文件。尺寸索引有少量额外开销；用户可清理缓存。
  此缓存受系统淘汰，不能替代用户的离线漫画文件。模型包不包含在译图缓存清理范围内。


## 精细掩膜与局部背景修复

- 在 OCR 元素四边形周围采样边界主色，在局部区域中提取与背景显著不同的连通分量。
- 只接纳与文字四边形相交且未连接到外侧保护带的分量，排除穿过该区域的气泡边框/连续线稿。
  小幅膨胀覆盖字形抗锯齿边缘，不把整个气泡或矩形当作去字掩膜。
- 从掩膜边界向内逐层按邻域距离加权插值，重建白底、黑底、彩色与平滑渐变背景。
  修复像素和新字形之外的补丁透明，因此多个补丁不会把未修改的原图重新盖住。
- 任一识别元素无法获得安全掩膜时，保留所在区域原文、记录待处理原因；不静默退回整块涂白，也不拖垮其他可用区域。
- 这是传统局部修复，不是 LaMa/扩散模型。复杂纹理和被文字遮住的线稿没有可靠还原保证；
  可能因为保护边界而拒绝处理，也可能丢失局部纹理。仍需真实漫画质量对照。
- 没有增加图像推理运行时、修复权重或原生库，保持当前安装包依赖范围。

## 后续原方案仍需完成

1. 目标手机上的日文竖排、注音、艺术字、彩漫 OCR 对照与内存/耗时测试。
2. 独立于 Google Play 的 CTD / PP-OCR 模型、校验和离线导入；选定并验证推理运行时。
3. 可选增强修复模型，复杂纹理、被遮挡线稿重建和跨块 OCR 质量优化。
4. 在已实现的按区域 ID 翻译、批次内上下文与严格返回校验基础上，继续扩展跨批次上下文和术语表。
5. 文字区域校正和整章任务；当前已支持同章下一张图片预译。

不预先加入 ONNX/Paddle/PyTorch 多套运行时，避免为未验证模型增加安装包负担。

## 依据

- [ML Kit OCR Android 接入](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [ML Kit 离线翻译](https://developers.google.com/ml-kit/language/translation/android)
- [manga-image-translator](https://github.com/zyddnys/manga-image-translator)
- [BallonsTranslator](https://github.com/dmMaze/BallonsTranslator)
- [manga-translator-ui](https://github.com/hgmzhn/manga-translator-ui)

本实现没有复制三个参考项目的源码、权重或服务接口。借鉴了分阶段处理、文字约束掩膜和原图对比交互；当前掩膜和局部修复算法独立实现。

## 此前离线切片的验证与提交清理（2026-10-28）

- 清理前完成 41 项定向测试：覆盖翻译状态、无文字、失败重试、取消后的旧结果丢弃、原图切换、
  韩语语言选择、设置映射、跨块合并、文字掩膜修复、PNG 逐行往返、20,001 行原生区域解码、
  掩膜外像素和气泡边框保护，以及区域解码生命周期与 EXIF 方向。
- 按提交约定，新增的测试文件、用例和模拟实现已清除。原有测试保留，仅适配新增构造参数和设置字段。
- 清理后重新运行 `:app:assembleAppDebug`、关联的 `verifyConfigArchitecture`，以及原有 28 项定向回归，均通过。
  回归选择 `MangaSettingsMappingTest`、`MangaReaderViewModelNavigationTest`、
  `MangaAndroidRegionDecoderTest`、`LocalMangaRegionRecoveryTest`；`git diff --check` 通过。
- 本机通过 `ANDROID_HOME` 指向 SDK；Windows 跨盘 KSP 增量问题通过命令参数
  `-Pksp.incremental=false` 规避，未修改仓库通用构建参数。
- 上述证据来自状态、合成样本、JVM/Robolectric 与原生图形测试；尚未验证真实模型下载、
  真实漫画识别与修复质量、设备手势、旋转、峰值内存、耗时或发热。
- Debug APK 不能用于推断 Release 体积增量；尚未做优化 Release 与原版对比。


## 自动翻译与 AI 切片验证（2026-10-28）

- 最终串行运行 `:app:assembleAppDebug verifyConfigArchitecture :app:testAppDebugUnitTest`，60 项定向测试全部通过，`git diff --check` 通过。
- 测试选择：`TranslateMangaTextUseCaseTest`、`MangaReaderViewModelNavigationTest`、`MangaTranslationSettingsPanelTest`、`MangaSettingsMappingTest`、`MangaBookSnapshotTest`、`DefaultMangaReaderSessionTest`、`ReadConfigJsonRoundTripTest`、`AiRetryUtilsTest`。
- 覆盖默认关闭、300ms 稳定窗口、双页优先、下一图预译提升、快速翻页取消、失败去重、后台恢复不自动重试、手动重试、无文字、清缓存抑制、引擎切换、书籍配置序列化及观察更新；同时验证 AI 分批、乱序回填、重复／缺失 ID、鉴权／限流／网络／超时、长度上限和缓存身份。
- 验证使用 JDK 21；通过被 Git 忽略的 `local.properties` 指向本机 SDK，命令带 `-Pksp.incremental=false --no-configuration-cache --no-daemon --no-watch-fs`。没有修改通用 Gradle 参数。
- 中途重叠验证曾造成 Windows JAR 文件占用及新旧编译结果混用；最终停止重叠后串行重跑全部上述检查，结果通过。
- 本次没有连接 Android 设备，也没有发送真实 AI 请求。真实 OCR／模型下载、漫画识别与翻译质量、设备手势与后台行为、长图峰值内存及耗时仍需真机验证；自动化结果不代表这些路径已实测。


## 本次提交前清理与复核（2026-10-28）

- 清理本轮新增 AI 翻译用例、自动调度用例与辅助模拟代码，仅保留原有测试的字段映射及构造参数适配；实现前已存在的界面测试改动保留在工作区，不进入本次提交。
- 清理后重新运行 `:app:assembleAppDebug verifyConfigArchitecture :app:testAppDebugUnitTest`，选取 `MangaReaderViewModelNavigationTest`、`MangaSettingsMappingTest`、`MangaBookSnapshotTest`、`DefaultMangaReaderSessionTest`，共 29 项原有回归全部通过；暂存差异检查通过。
- 构建参数与上节相同。真机、真实服务与性能验证限制仍适用。

## 图像模型与排版初版验证（2026-10-28）

- 接入 CTD 与 MangaLens 的模型管理、CPU 推理适配、同泡文本归并和保守内部矩形布局；
  AOT 提供专用导出脚本、本地导入契约与局部合成路径。完整范围及待办见方案第 11 节。
- 区域修复／布局失败保留原文，并在译文面板提供待处理原因；结果元数据随译图保存，缓存命中仍保留诊断。
  补丁生成一次后以临时 PNG 跨输出条带复用，最后释放；清缓存覆盖异常退出留下的补丁目录。
- 最终运行 `:app:assembleAppDebug verifyConfigArchitecture :app:testAppDebugUnitTest` 成功。
  使用 JDK 21，参数 `-Pksp.incremental=false --no-configuration-cache --no-daemon --no-watch-fs`。
  通过工作区外的临时 init 脚本，将 Test 类文件 include 限定为下面三类，避免 `forkEvery=1` 的全量扫描开销；
  没有修改通用 Gradle 参数。
- `MangaTranslationLayoutTest` 5 项、`MangaReaderViewModelNavigationTest` 14 项、
  `MangaTranslationSettingsPanelTest` 5 项，共 24 项通过，0 失败／错误。
  覆盖字号上下搜索、拒绝极小字、布局取消、椭圆气泡内部矩形、损坏导入保护旧模型与源文件，以及现有阅读器和面板回归。
  用户原有的两份界面测试改动保留，未被本轮覆盖。
- 检查 arm64 Debug APK：包含 ONNX Runtime 原生库，没有 `.onnx`、`.ckpt`、`.pt`、SafeTensors 权重。
  `git diff --check` 通过；AOT 导出脚本通过 Python 语法和 `--help` 入口检查。
- 模型权重及 Python CPU 推理环境下载出现连接超时／EOF，未完成真实制品推理、AOT 实际导出及数值对照。
  尚未验证目标真机、漫画视觉质量、模型成功安装／推理中删除、长图峰值内存、发热或 Release/R8。
  上述自动化结果不能证明这些路径已通过，也不能视为整个 A–E 方案完成。

## 轮廓横竖排与分阶段缓存验证（2026-10-28）

- 已实现按气泡掩膜逐行／逐列约束、动态规划换行、实际字形越界检查，以及自动／横排／竖排持久设置。自动模式在同字号下比较横竖候选代价；完整译文无法满足可读下限时保留原文。
- 竖排支持 Unicode 字形方向、标点变体、连续拉丁词塑形旋转、短数字横排、字素簇与行首／行尾禁则。Android 26–28 的 Unicode 17 回退表通过锁定 SHA-256 的生成器重新生成，并与提交源码逐字比对一致；Unicode 许可随应用保留。
- OCR、气泡、翻译、掩膜、修复和布局分别持久缓存；依赖键防止字体／轮廓／模型变化时误复用。校验失败或 JSON 损坏删除重算，原子写入、取消保护与 LRU 配额均有回归覆盖。中间结果上限 64 MiB，与最终译图 128 MiB 配额独立。
- 最终串行执行 `:app:assembleAppDebug verifyConfigArchitecture :app:testAppDebugUnitTest` 成功；JDK 21，参数与上节一致，通过工作区外临时 init 脚本仅选择本节七类测试，没有修改通用 Gradle 参数。
- 测试报告：`MangaContourLayoutTest` 16 项（Android 26／35）、`MangaStageCacheTest` 5 项、`MangaStageKeysTest` 3 项、`MangaTranslationLayoutTest` 5 项、`MangaSettingsMappingTest` 9 项、`MangaReaderViewModelNavigationTest` 14 项、`MangaTranslationSettingsPanelTest` 5 项，共 57 项，0 失败／错误。`git diff --check` 通过。
- 本次完成代码与合成样本／Robolectric 验证；真实模型制品、漫画视觉效果、字体缺字、目标设备内存／耗时／温升和 Release/R8 仍未验收。尚未提供区域编辑器或独立区域重试按钮。完整实施范围见方案第 12 节。

## 翻译准备阶段失败修复（2026-10-29）

- 最终译图缓存键直接序列化 `translationOverrides.toSortedMap()`，触发 `TreeMap` 在 `SortedMap` 范围内没有序列化器的异常。空映射也触发，因此未进入 OCR 就失败，与是否安装图像模型无关；通用错误文案会误导为模型／网络问题。
- 将最终译图键集中到 `MangaStageKeys.rendered`，显式使用 `Map<String, String>` 序列化器，保留区域键排序及原有缓存依赖。没有更换 OCR 或翻译引擎。
- 新增回归先复现上述异常，修复后缓存键 4 项、阶段缓存 5 项、阅读器状态与重试 14 项共 23 项通过；Debug APK 构建通过。使用 JDK 21、`--no-configuration-cache -Pksp.incremental=false`，`git diff --check` 通过。
- 没有连接目标手机，尚未进行真机模型调用和漫画译图效果验证。

## 识别阶段排障（2026-10-29）

- 用户反馈修复准备阶段后，仍在“正在本地识别文字”失败；没有连接设备或取得 SDK 错误，不能据此断言是翻译语言包问题。
- 日／韩 OCR 改为随 APK 打包的 ML Kit 16.0.1，去除 Google Play 模块可用性检查及动态安装。OCR 阶段缓存键区分 bundled 版本；离线翻译仍由 ML Kit 独立下载语言包。
- OCR 初始化／识别、CTD 与气泡检测分别反馈失败原因和可复制错误编号。编号仅包含组件、SDK 数值码／异常类型及应用源码位置，不展示异常原文、图片路径或识别文本。普通未知错误也保留失败阶段和异常类型；重开面板保留编号，重试清除旧编号。
- 22 项回归通过：识别错误与取消 3 项、缓存键 4 项、阅读器失败／重试／状态 15 项。`:app:assembleAppDebug` 及关联架构门禁通过；JDK 21，`--no-configuration-cache --no-daemon --no-watch-fs -Pksp.incremental=false`。常驻 Gradle daemon 曾错误缓存 SDK 目录状态，使用新进程后恢复，没有修改 SDK 配置。
- 未进行真机漫画识别、实际模型推理和翻译质量验证；本轮移除动态 OCR 下载依赖并完善诊断，设备上的具体故障需通过新版错误编号确认。

## OCR 小裁剪输入修复（2026-10-29）

- 设备返回 `OCR: MLKIT-3@MangaTranslationRepository.kt:178`，对应 `recognizer.process(InputImage.fromBitmap(...))`。官方定义 3 为 `INVALID_ARGUMENT`；核对当前 `vision-common:17.3.0` 的 `MobileVisionBase.processBase(InputImage)` 字节码，宽或高不足 32 时直接返回此码，错误文案为 `InputImage width and height should be at least 32!`。
- 原实现把 CTD 文字框直接裁剪交给 OCR，未满足 SDK 最小尺寸。新增统一输入准备：在原图块内扩大过小裁剪，图块本身不足 32 时右侧／下侧白色补边，不缩放原始像素；同时规范为软件 ARGB_8888 输入。返回实际裁剪原点，文字框和字形统一使用此原点回填全页坐标。
- OCR 输入位图独立持有，在 SDK 调用结束后释放，不回收原图块。OCR 阶段和最终译图缓存键均升级，避免复用旧输入规则的结果。
- 设备错误码符合该输入约束缺陷；尚未读取设备裁剪尺寸或完成真机重测，不能将合成测试等同于真实漫画翻译成功。
- 新增原生图形输入测试 4 项覆盖细框／页边缘、原像素与全页坐标、极小图片补边和普通裁剪；加识别错误 3 项、缓存键 4 项、阅读器 15 项，共 26 项通过。`:app:assembleAppDebug` 与关联架构门禁通过，`git diff --check` 通过；JDK 21 与构建参数同上节。

## 提交前清理与复核（2026-10-29）

- 按提交约定删除本轮新增的 7 个测试文件，移除原有测试文件中新增的 8 个测试用例及配套模拟／注入代码。保留仓库原有测试，仅为设置映射基线补充新增方向字段的必要适配；本轮没有新增测试专用依赖。清理测试报告及对应编译任务缓存文件，保留正式模型导出、Unicode 数据生成和运行时错误反馈。
- 清理后重新运行 `:app:assembleAppDebug verifyConfigArchitecture :app:testAppDebugUnitTest`，仅选择原有 `MangaSettingsMappingTest` 与 `MangaReaderViewModelNavigationTest`，共 16 项通过，0 失败／错误；`git diff --check` 通过。构建参数同上节。
- 本次提交涵盖图像模型管理、轮廓横竖排、分阶段缓存与后续缓存序列化／OCR 修复。尚未完成目标设备实际漫画译图和 Release/R8 验证。

## 原文残留与叠字修复（2026-10-29）

- 合成图先复现三项失败：CTD 局部漏检导致同一 OCR 框内的断开笔画未擦除、浅灰抗锯齿边缘残留、框内一部分笔画被拒绝后仍被判定修复成功。背景采样区域扩大到文字多边形外 6 像素，CTD 改为浅色笔画提示，不再否决 OCR 框内的颜色连通分量；颜色阈值和两像素膨胀共同覆盖浅灰字边。
- 连通分量接触外沿或面积过大且进入 OCR 多边形时，将整个区域标记为修复不完整，沿用已有 REPAIR 失败处理保留原图，不继续嵌字。外侧气泡／分镜线仍排除在擦除掩膜之外，没有采用矩形白块覆盖。
- AOT 输入掩膜由稀疏的源像素前向投影改为目标像素足迹采样，放大时填满覆盖范围，缩小时用最大池化保留细笔画。局部修复的加权颜色改为四舍五入，避免截断误差逐层把纯白背景染灰。
- 最终译图与修复阶段缓存键均升级；OCR、翻译、CTD 原始掩膜与布局缓存保持可复用。重新翻译会生成新修复结果。
- JDK 21 下运行 `:app:testAppDebugUnitTest :app:assembleAppDebug verifyConfigArchitecture` 成功，参数为 `--no-configuration-cache --no-daemon --no-watch-fs -Pksp.incremental=false`；工作区外 init 脚本限定新去字测试 5 项、新掩膜采样测试 3 项、原有设置映射与阅读器状态测试各 8 项，共 24 项通过。`git diff --check` 通过。
- 验证使用合成图与 Robolectric；真实漫画页、端侧模型输出质量、设备性能和 Release/R8 尚未验收。

## 掩膜与排版误拒绝修复（2026-10-29）

- 用户继续遇到 REPAIR／LAYOUT 保留原图提示。合成回归复现两个独立缺陷：相邻 OCR 框的连通笔画越过各自 6 像素外沿时被误判为画线；1536 像素宽页面要求至少 40 像素字号，即使原文字号仅 16 像素且文字框只有 42 像素高，仍拒绝缩小。
- 合并外沿相交的 OCR 多边形，共享背景采样与连通分量搜索范围。前景仍须与原始 OCR 多边形相交，至少一半像素位于 OCR 多边形内，并通过边缘连接与面积检查；合并后的大范围不作为矩形填白区域。真正连接画线且无法可靠分离的区域继续保留原图。
- 最小字号取页面可读参考与原文字号 65% 的较小值，保留 6 像素底线并应用用户字号比例。完整排版、字形轮廓与实际绘制像素检查保持有效，不通过截字或取消越界校验解决排版失败。
- 排版版本、最终译图与修复缓存键升级，重新翻译会重新生成结果，OCR 与译文缓存仍可复用。新增小文字框成功／极小文字框失败、跨框笔画、封闭气泡边框保护回归；这些合成样本不代表用户漫画页已经通过真机验收。
- 最终运行 `:app:testAppDebugUnitTest :app:assembleAppDebug verifyConfigArchitecture` 成功，JDK 21，构建参数同上节，通过工作区外 init 脚本限定去字 7 项、排版 2 项、掩膜缩放 3 项、设置映射与阅读器状态各 8 项，共 28 项测试，0 失败／错误；`git diff --check` 通过。ARM64 Debug APK 已生成，尚未验证目标设备上的实际漫画页或 Release/R8。

## 去字与排版修复提交前清理（2026-10-29）

- 按用户提交要求，在完成 28 项定向回归后删除本轮新增的 3 个测试文件（去字、掩膜缩放、字号适配共 12 项），清理临时 init 脚本、日志与对应测试构建产物；未新增测试专用依赖、调试开关或正式代码中的测试入口。仓库原有测试保留。
- 清理后重新运行 `:app:assembleAppDebug verifyConfigArchitecture :app:testAppDebugUnitTest`，使用两个 `--tests` 参数选择原有 `MangaSettingsMappingTest` 与 `MangaReaderViewModelNavigationTest`，共 16 项，0 失败／错误；构建参数同上节，`git diff --check` 通过。复核后清理测试构建输出，不修改正式源码。真机漫画效果与 Release/R8 仍未验收。
