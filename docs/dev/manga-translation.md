# 漫画翻译：本地预览切片

已接入离线／AI 文字翻译、按书自动翻译、长图分块和文字掩膜局部修复；独立 OCR 与增强修复模型仍待后续实施。

## 当前可用路径

阅读菜单 → 全局 → 第一项「翻译当前页」，在同一设置面板内进入翻译子页。
长按漫画页面 → 翻译当前页，也进入同一子页，并绑定被长按的图片。
选择原文（日语、英语、韩语）与目标语言（中文、英语、日语、韩语）→ 下载模型／翻译。
子页返回按钮或系统返回先回到全局列表；退出翻译子页或关闭菜单会取消手动任务，自动任务继续执行。
译图在阅读器原位置显示，重新打开面板可切换原图、译图或清除译图缓存。
语言选择通过 MangaSettingsGateway 原子持久化。

图像获取沿用 Coil 和书源图片处理。OCR 与图片修复均在本机执行，不上传漫画图片。离线引擎不上传识别文字；AI 引擎仅向配置的服务发送 OCR 文字，并在翻译面板展示模型、服务地址及用量说明。
译图通过文字像素掩膜去字与局部插值修复，不再整块涂白。掩膜外原图像素保持不变，译文绘制在透明补丁上。
使用系统字体测量和缩小字号；区域无法容纳完整译文时失败，不静默裁切。

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

- OCR 暂用 Google Play 服务动态下载的日文识别器（也处理拉丁字符）和韩文识别器，按原文语言选择。
  固定依赖 `play-services-mlkit-text-recognition-japanese:16.0.1` 与
  `play-services-mlkit-text-recognition-korean:16.0.1`。
  这与原方案中的 CTD / PP-OCR 候选不同，目的是先验证最小阅读闭环。
  没有可用 Google Play 服务时显式提示不可用，不声称支持所有国产系统。
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
- 任一识别元素无法获得安全掩膜时，明确报告失败并保留原图，不静默退回整块涂白。
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
