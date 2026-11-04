# 漫画本地引擎拆分、轻量变体与扩展 APK 实施计划

状态：待实施。2026-10-31 基于源码 HEAD `b21025d37` 制定；本文不代表变体、模块、协议或 CI task 已存在。

术语：产品维度 2026-11-05 起为 `core` / `comic`（中文名「漫画增强版」），此前该产品标记为 `full`；
历史 Release 的资产名与 mapping 仍沿用旧标记，迁移口径见 §5.1。

用户目标：主安装包恢复到原来约 20 MB；保留漫画增强版，并允许 core 用户通过可选扩展获得漫画本地翻译能力。
实施顺序：行为基线与引擎边界 → `core/comic` 变体 → 独立引擎扩展 APK → 发布与回归收口。
本次交付仅新增计划文档，不修改构建、业务或安装行为。

## 1. 目标、口径与范围

### 1.1 成功条件

- 默认面向普通用户发布轻量主包；完整本地能力可通过漫画增强版或独立扩展获得。
- 主包体积目标按十进制 **20,000,000 字节（20 MB）** 跟踪，主比较对象是 R8／资源压缩开启、
  单 `arm64-v8a` 的最终 Release APK；同时记录 MiB，避免把 20 MiB 当作 20 MB。
- 该数值是待实测的目标，不是已经实现的承诺。第一轮只隔离漫画引擎；若仍超标，记录差额和包内容，
  再选择有证据的最小优化，不降低门槛或借机重构其他业务。
- `core` 的依赖图与 APK 中均不存在 ONNX Runtime、ML Kit OCR 原生库、OCR 模型和气泡模型。
- `comic` 的识别、检测、擦字、排版、缓存、取消与旧数据行为保持原有语义。
- 最终 `core + 扩展` 使用与 `comic` 相同的引擎实现；安装、缺失、升级、卸载和服务断开均有明确状态。
- 未安装扩展不影响普通阅读、漫画阅读和已有译图展示；需本地引擎的操作明确提示缺少能力，
  不伪装成功、不默默更改用户设置，也不自动改用付费云端服务。

20 MB 约束针对主 APK，不包含扩展、下载后的权重、应用数据和缓存；另行记录这些占用，不能将首装瘦身描述为总存储同幅度减少。
ARM32 包也记录结果；Universal 和模拟器包不作为 20 MB 的主验收对象。

### 1.2 不在本计划范围内

- 不替换阅读器、数据库、网络、DI、导航、AI 供应商或现有模型算法。
- 不同时迁移 KMP/CMP，不创建 `commonMain` 或承诺非 Android 平台可用。
- 不优先自编译裁剪 ORT，不同时移除 ML Kit 或以 Paddle 替代其识别结果。
- 不实现从网络下载 DEX／SO 并在主进程加载的插件框架。
- 不以 Google Play Dynamic Feature、资源包或只下载权重替代独立扩展 APK 的首期交付。
- 不为拆包整体搬迁漫画 UI，不改变已有书架、供应商凭据或翻译记录的存储归属。

## 2. 当前事实与体积证据

2026-10-31 检查的本地 Release 产物均生成于 2026-10-27，**不是对当前 HEAD 的重新构建**：

| 产物 | 文件字节数 | APK 大小 |
| --- | ---: | ---: |
| `app-app-arm64-v8a-release.apk` | 71,754,462 | 68.43 MiB |
| `app-app-armeabi-v7a-release.apk` | 58,727,740 | 56.01 MiB |
| `app-app-universal-release.apk` | 198,226,580 | 189.04 MiB |

以下为 ZIP entry 的 `CompressedLength` 汇总，表示在 APK 内的占用；不是安装后磁盘占用：

| 项目 | arm64 | ARM32 | Universal |
| --- | ---: | ---: | ---: |
| `lib/*/libonnxruntime*.so` | 27.42 MiB | 19.52 MiB | 113.12 MiB |
| ML Kit OCR assets 与 `libmlkit_google_ocr_pipeline.so` | 13.46 MiB | 9.37 MiB | 42.04 MiB |
| `detector-v4-s_int8.onnx` | 7.07 MiB | 7.07 MiB | 7.07 MiB |

arm64 静态扣除约为 `68.43 - 27.42 - 13.46 - 7.07 = 20.48 MiB`，约 21.5 MB，仍高于严格的 20 MB 目标。
这只是机会估算：尚未扣除可移走的 Java/Kotlin 实现，也未计入新适配代码和重新打包差异；必须以新 Release 实测收口。

当前源码和调用点：

| 位置 | 事实与拆分影响 |
| --- | --- |
| `app/build.gradle.kts` | 全局 `implementation` 引入 ORT Android 1.28.0，以及拉丁／中／日／韩四套 ML Kit 16.0.1；Release 已开启 R8 与资源压缩 |
| 同上 | 只有 `mode=app` flavor；ABI splits 默认开启，生成 ARM32、ARM64 和 Universal |
| 已有 Universal APK | 实际包含 ARM32、ARM64、x86、x86_64；不能认为 `splits.include` 已限制 Universal 的 ABI |
| `data/repository/manga/translation/MangaTranslationRepository.kt` | 同时拥有数据库、设置、缓存、AI 调用和本地引擎注入；构造函数默认参数直接引用检测器 |
| `MangaTranslationImages.kt` | `mangaAnalysisBands` 为长条图分段直接调用 ML Kit，形成云端路径也可能触发的隐式依赖 |
| `MangaBubbleDetector.kt` | 从 assets 复制约 11.1 MB 气泡模型，固定大小／SHA-256；串行 CPU 推理并复用会话 |
| `MangaLocalTextDetector.kt` | 直接使用四种 ML Kit recognizer |
| `MangaPaddleOcr.kt`、`MangaLocalInpainter.kt` | 使用 ORT；Paddle、MI-GAN 和 LaMa 权重已按需下载 |
| `di/appModule.kt` | 当前无条件注册 OCR／修复模型仓库、推理器及翻译实现 |
| `feature/manga/translation/MangaTranslationViewModel.kt` | 初始化就观察并刷新模型状态，不能仅隐藏入口而保留错误装配 |
| `domain/gateway/MangaTranslationGateway.kt` | 翻译编排与缓存的既有入口，保留在主应用，不直接改为整仓库 IPC |

上表源码路径均相对 `app/src/main/java/io/legado/app/`，另有注明的 Gradle 文件除外。
实施前重新审计全部调用、assets、NOTICE、ProGuard、测试和传递依赖，不能把本表视为封闭清单。

### 文档差异处理

[本地视觉模型](manga-local-vision-models.md)和[AI 翻译](manga-ai-translation.md)描述的“内置气泡模型／ML Kit”是当前事实。
[擦字引擎](manga-inpaint-engines.md)描述的权重按需下载也仍成立，但它不意味着 ORT 已移出主包。
本计划描述未来交付差异；对应阶段合并时按 `core/comic/扩展` 更新原文，不能提前把目标写成现状。

## 3. 产品形态和行为矩阵

| 能力 | `core` 未安装扩展 | `core + 扩展` | `comic` |
| --- | --- | --- | --- |
| 阅读、漫画阅读、原图展示 | 保留 | 保留 | 保留 |
| 已发布译图、设置、词典、缓存读取 | 保留 | 保留 | 保留 |
| ML Kit、Paddle、本地气泡检测、模型擦字 | 明确不可用 | 由扩展执行 | 内置执行 |
| 云端 OCR、AI 识图／翻译／图片编辑 | 仅开放已验证不依赖本地引擎的组合 | 按所选流程执行 | 保持现有行为 |
| 本地无模型处理、排版及缓存重排 | 按操作所需能力逐项判断 | 保留 | 保留 |
| 未下载所选权重 | 提示引擎／模型缺失 | 显式下载后使用 | 显式下载后使用 |

首个 `core` 切片不承诺所有云端模式都可用。要从调用图证明每种定位／合成组合的依赖，尤其是长图分段、
字号取样、几何锚点和擦字。无法独立执行的组合先明确禁用；纯云端替代流程作为单独行为变更验证，不随拆包悄悄加入。
已有缓存只在证据充分且不需重新推理时允许重用，不能把模型缺失处理成空 OCR 结果。

首次进入相关操作时显示用户可理解的“安装本地引擎”“更新引擎”“下载模型”等动作。
未就绪时暂停自动队列，避免逐页重复报错；恢复后由用户显式继续，不能自动重复付费请求。
安装完成后重新检查能力和协议，不要求重装主应用；扩展卸载后不删除主应用的译图和设置。

`core` 的设置界面不提供“端侧 OCR”选项：识别方式下拉按 `MangaLocalEngine.available` 过滤，
`comic` 遗留的本地选择在读取设置时回落 AI（写入时一并规范化），分组内仅保留一条“需要漫画增强版”说明。
这样用户不会停在一个必然抛 `MangaLocalEngineUnavailableException` 的分支上，也不需要为失败之后的报错
再解释一次产品差异。

模型擦字（MI-GAN／LaMa）同理：它们依赖 ONNX，core 读取设置时回落到内置擦字
（`MangaPageComposer` 的纯绘制实现，位于 `main`，core 可用），因此 SMART 排版保留可用，
处理分组只注明“模型擦字需要漫画增强版”。不要因为缺擦字模型就禁用 SMART——内置擦字不需要任何权重。

## 4. 职责与依赖边界

### 4.1 主应用保留

- 阅读器、翻译界面、设置、进度、自动队列、宿主导航和安装跳转。
- `MangaTranslationRepository` 的编排、数据库访问、词典、缓存身份和最终译图原子发布。
- AI／云端 OCR 请求、供应商凭据与主应用 Keystore；不得将密钥复制给扩展。
- 不依赖 ML 的几何、掩膜生成、字号测量、排版和结果合成；是否属于引擎逐文件审计，不整目录机械搬家。
- 领域能力状态和引擎适配入口；Koin 在 composition root 中选择实现。

### 4.2 本地引擎承担

- 气泡检测、ML Kit／Paddle OCR、MI-GAN／LaMa 修复及其 ORT 会话、线程与内存资源。
- 引擎权重的清单、下载、校验、取消、删除、租约和 NOTICE。
- 返回可验证的识别／几何数据或修复图像，不直接写主应用数据库、发布译图或调用付费服务。
- `comic` 在本进程使用引擎，扩展在独立应用进程使用同一实现，避免维护两套算法。

### 4.3 分阶段形成真实 Gradle 边界

阶段 1 在 `:app` 内形成能力契约和调用适配；阶段 2 先用 `src/core`／`src/comic` 做编译隔离。
需要复用到第二宿主时才建立下列模块；名称为计划，尚未创建：

| 计划模块 | 类型／真实消费者 | 内容与禁止依赖 |
| --- | --- | --- |
| `:modules:manga-engine` | Android library；`app` 的 comic 与扩展宿主 | 本地引擎、必要的小型输入输出值及模型管理；不依赖 `:app`、Room、Feature UI 或供应商仓库 |
| `:modules:manga-engine-ipc` | Android library；主应用客户端与扩展服务 | AIDL、传输 DTO、协议版本；不传递依赖 ORT／ML Kit／运行时实现 |
| `:extensions:manga-engine` | Android application；独立安装产物 | Service、权限、签名策略、引擎装配及最小模型管理入口；不依赖 `:app` |

最终依赖：`app(comic) → manga-engine`；`app(core) → manga-engine-ipc`；
`extension → manga-engine + manga-engine-ipc`。主应用领域契约通过适配器映射运行时结果或 IPC DTO。
模块不可反向依赖 `:app` 以复用其 domain 类型；只移动真实需要的值，避免抽出整个 app domain。
模型仓库不得再依赖主应用的资源 ID；错误以稳定代码／值返回，由主应用或扩展 UI 映射文案。
主应用的 IPC 客户端在现有平台／数据适配层实现，不为单一调用方再建一个空模块。

不额外建立无消费者的 `api/impl` 或“common”容器。运行时和 IPC 的 Android 类型属于平台侧，
不宣称它们是跨平台契约；新增领域状态不暴露 `Bitmap`、`File`、`Uri`、PFD、Room 或资源 ID。

## 5. 变体、签名和数据兼容

- 保留 `mode=app`，新增 `engine=core/comic` 维度，按固定维度顺序形成组合；具体任务名以 AGP 实际输出为准。
- ORT／ML Kit 改为 comic 专用依赖；模型和引擎专用 NOTICE 放入 comic／运行时对应 source set。
  共用合法声明仍保留，不能通过简单排除 `.so` 造成 Java 入口存在却加载失败。
- 移走全部默认参数、辅助方法和测试对具体引擎的引用；main 源集不能静态依赖 comic 实现。
  引擎测试进入 comic／运行时测试源集，公共编排和无引擎状态测试仍覆盖 core。
- `core/comic` Release 使用现有 `io.legato.kazusa` 和同一签名；它们是可替换的产品配置，不要求并装。
  不引入 flavor 专用数据库名或清空设置；验证同签名、同或更高 versionCode 的覆盖安装与应用商店更新。
- 从 comic 切到 core 后保留已下载模型和缓存，禁止启动时清理；切回后可重新校验使用。
- 扩展使用独立 applicationId（计划 `io.legato.kazusa.mangaengine`），namespace 继续使用 `io.legado.app` 前缀。
  release 与 debug 的客户端／服务包配对和签名策略分开，release 不信任 debug 扩展。
- comic 默认使用内置引擎，即使同时安装扩展也不隐式切换或重复执行任务。
- 主包和扩展均发布 ARM64，按实际需求保留 ARM32；明确 ABI filters 与模拟器配置，检查 Universal 内容，
  不依赖 splits 自动剔除其他架构。不得为了体积误删实际支持的运行架构。

### 5.1 发布形态（2026-10-31 定案，2026-11-05 产品标记由 `full` 改为 `comic`）

- **双产品命名**：`core` 是基础包，`comic` 是带本地漫画引擎的漫画增强版。`comic` 在 2026-11-05 前叫 `full`：
  旧 Release 的资产名与 mapping 仍是 `-full-`／`mapping-full.txt`，应用内更新不会跨产品匹配，
  停在旧 `full` 版的用户需要手动下载一次 `comic` 包，之后恢复正常更新。历史日志还原仍回落读取 `mapping-full.txt`。
- **长期双包发布**：`core` 与 `comic` 同版本、同签名、同 versionCode，各自 3 个 ABI 产物，挂在同一个
  GitHub Release。体积报告里的路线 B（换推理栈）不实施，检测模型外置（路线 A）暂缓。
- 附件命名固定为 `legado-<version>-<core|comic>-<arm64-v8a|armeabi-v7a|universal>.apk`。
  应用内更新按命名中的产品标记过滤（`AppProduct`），只挑本产品那一份；任何一侧缺包按"暂无更新"处理，
  绝不跨产品升级（core→comic 会突然变大，comic→core 会丢本地引擎）。
- R8 mapping 按产品分别上传为 `mapping-core.txt`／`mapping-comic.txt`，崩溃还原时不能拿错。
- Telegram 单文件上限 50 MB：core 三个产物正常推送附件，comic 三个均超限，只发 GitHub Release 链接。
- 体积基线（arm64-v8a Release）：core 20.61 MiB、comic 68.90 MiB。变化需要记录条目与原因。
- 扩展包路线（下文的 P3、§6）**暂不启动**；`core` 的用户只得到"需要漫画增强版"的明确提示，不提供安装扩展的入口。

## 6. 扩展协议与运行生命周期

传输方式采用显式绑定的 Android Service／AIDL。只传小型元数据、操作 ID 与受控图像／掩膜句柄，
不把 Kotlin 对象图、Flow、数据库实体或整页 Bitmap 放进 Binder transaction。

| 合同项 | 首版必须定义的语义 |
| --- | --- |
| 握手 | 协议 major／minor、实现版本、模型 revision、支持的操作／语言／ABI、资源预算；不兼容时禁止提交 |
| 能力 | 区分缺少扩展、不可信签名、版本不兼容、引擎不支持、缺模型、下载中、就绪、连接中断；缺模型不是空结果 |
| 请求 | 唯一任务 ID、操作类型、引擎 revision、图像尺寸／方向／像素格式、坐标系与映射、预算和源内容标识 |
| 传输 | 大图及大批量结果通过平台适配器管理的 PFD／文件传输；掩膜定义极性、尺寸与格式；限制解码尺寸、结果数量及元数据大小 |
| 文件所有权 | 输入只读、输出为任务专用目标；明确双方 close、复制、租约、超时与失败清理；不把私有绝对路径当跨应用接口 |
| 结果校验 | 核对任务 ID、引擎身份、尺寸、坐标范围、有限数值和输出完整性；成功且仍属当前任务才交给主应用发布 |
| 异步与取消 | Binder 方法快速返回，在受限工作队列推理；回调给进度和唯一终态；幂等取消，不允许取消后迟到结果发布 |
| 原生资源 | 不强行中断不可取消的 ORT 前向；完成前保留图像与模型租约，完成后观察取消并丢弃结果；保持串行／并发限制 |
| 错误 | 稳定错误代码区分输入、模型损坏、资源不足、超时、服务死亡和内部错误；不以空列表吞掉执行失败 |
| 断开／升级 | 处理 Binder death、扩展更新／卸载、主应用退出；任务失败且原译图保留，恢复后重新握手，不自动重放付费流程 |
| 可信调用 | 显式组件、最小 package visibility、signature 权限与双方签名／调用 UID 检查；在接收图像前完成校验，支持既定签名轮换策略 |
| 版本管理 | 协议 major 不兼容明确要求更新；minor 以能力协商增加可选字段；缓存身份包括真正影响输出的引擎／模型／算法 revision |

服务只在需要时绑定，空闲释放会话并解绑。首版不承诺应用退到后台或进程被杀后仍可持续跑完整章；
记录和保留原有前台任务语义。若后续需要长期后台运行，另行评估 foreground service 类型、通知和系统限制。
日志不输出原图内容、供应商密钥或用户原文；主应用与扩展使用不同 UID，不使用 sharedUserId。

## 7. 模型存储、迁移和缓存

- `comic` 保持内置气泡模型及其开箱离线能力，已由 `MangaVisionModelRepository` 承接：assets 存在时首次
  复制到私有目录，缺失则显式下载，两条路径统一走字节数 + SHA-256 + 原子替换；`core` 没有该资源，
  直接返回能力缺失。扩展首版也可内置气泡与 ML Kit；扩展大小单列，不压到主包里（当前暂不启动）。
- 擦字引擎分两类（`MangaInpaintEngine.usesModel`）：`BUILTIN`＝内置，`MI_GAN`／`LAMA`＝ONNX 模型。
  「是否需要下载」与「是否使用模型」在这三档上是同一件事，`downloadable` 由 `usesModel` 派生。
- `comic` 的 SMART 依赖 ONNX／ML Kit 是**正常且预期**的，但依赖属于不同步骤，不能合并表述：

  | SMART 步骤 | 漫画增强版默认路径 | 运行时依赖 | core |
  | --- | --- | --- | --- |
  | 长图分段 | `localEngine.locate`（仅超长条；整页直接单段） | ML Kit | 沟槽检测（`locate` 返回 `null`） |
  | 识别 | `LOCAL`＝ML Kit／Paddle，`AI`＝远程，`CLOUD`＝远程 | ML Kit 或 ONNX（Paddle） | 无本地识别 |
  | 气泡几何 | `DEVICE` 调端侧检测；`AI`／`OUTLINE`／`OFF` 不调 | ONNX 气泡模型 | `DEVICE` 保留选择但不产几何（键加 `-off`） |
  | 擦字 | `BUILTIN`＝`MangaPageComposer` 纯像素绘制；`MI_GAN`／`LAMA`＝`MangaLocalInpainter` | **`BUILTIN` 不加载 ORT**；模型档位用 `ai.onnxruntime`（512×512） | 只有 `BUILTIN` |

  因此 `comic` 选 `BUILTIN` 时，`requireInpaint` 预检与 `erase` 都不会触到 ORT（`MangaTranslationRepository`
  只在 `SMART && inpaintEngine.downloadable` 时预检），但同一次 SMART 仍可能因为气泡检测、Paddle OCR
  或长图分段而加载 ONNX／ML Kit。两条陈述不冲突，文档不得只保留其中一条。
- 设置项是否生效只有一个判定入口：`MangaSettingsRelevance.of(settings, capabilities)`。
  它给出 `ignored`（当前组合不会读这个值）与 `degraded`（产物没有相应能力，读取时会被规范掉），
  界面用它决定显示与说明，测试用它锁定 `SMART`／`PATCH`／`AI_*`／融合请求／三种 locator 的差异。
  产物能力由 `MangaProductCapabilities` 表达，`BuildConfig` 只在 `LocalMangaCapabilities` 出现一次。
  新增跨选项影响或产物差异时，先改这张表，再改界面；不要在两处各写一遍条件。
  界面呈现策略：分组标题下先给出该阶段「当前实际执行什么」（`mangaPipelineStageText`）；受影响的控件
  **不隐藏**，留在原位置灰，原因写在控件下方（`MangaSettingReasonNote`）；只有互斥分支（识别方式各自的
  模型与凭据、擦字档位里被产物裁掉的模型）仍按分支渲染，因为它们是同一个选择的实现细节。
  思考强度只有一个入口：`MangaTranslationOptions` 基础设置组内的下拉。书内面板在本漫单独设置时渲染它；
  继承全局设置时由「漫画翻译设置」页承担，同一字段不允许出现两个并列入口。
- 设置持久化（`settings.json`、每本书的 `book.json`）必须能容忍改名与裁剪：
  枚举值改名时在新增值上标 `@JsonNames("旧名")`；`MangaTranslationRepository.readStored` 在解析失败时
  把原文件复制成 `.corrupt` 并回落默认值，下一次写入自愈。只改一个字段就让整个设置面板空白是不可接受的
  回归——`MangaInpaintEngine.BUILTIN` 的 `LOCAL` 别名即为此而加。
- 气泡几何来源是显式设置 `MangaBubbleSource`，影响擦除遮罩与排版约束，不影响识别与翻译本身：
  - `DEVICE`（默认）：调端侧检测，只采信端侧框，识别结果给的容器框一律忽略；
  - `AI`：不调端侧检测，在布局 prompt 末尾追加 `MANGA_AI_BALLOON_PROMPT`，要求模型返回 `aiGeometry.containerBox`；
    客户端复核「必须包住 textBox、面积不超过 textBox 的 20 倍、必须有 containerId」，不合格直接丢弃；
  - `OUTLINE`：「本地轮廓推断」。复用 `recoverMangaBubbles` 的洪泛结果写成 `bubbleShape`，不看模型容器框、
    不调端侧模型，`core` 与 `comic` 行为一致；背景延伸到裁剪边缘（没有闭合容器）时放弃，
    结果与既有矩形行为完全相同；
  - `OFF`：不做气泡增强，端侧检测一次也不调用；
  - `AUTO`：已停用，仅为读旧存档保留。它当年的实际行为是「端侧检测 + 有机会就用识别框」，与 `DEVICE` 等价
    ——`AUTO` 从不追加 `MANGA_AI_BALLOON_PROMPT`，有本地锚点时 `resolveMangaAiAnchors` 还会剥掉
    `aiGeometry`。因此它退出可选列表，读取时迁移为 `DEVICE`。

  产物差异只体现在选项集上：`comic` 提供「端侧检测／AI 识别框／画面轮廓／关闭」四项，`core` 只提供
  「AI 识别框／画面轮廓／关闭」三项。`core` 读不到 `DEVICE`，`usable()` 把它归一化为 `OFF`
  （两者在 core 上的请求与输出完全相同，因此不做别的改写）——代价是从 `comic` 带过来的选择会变成「关闭」，
  切回 `comic` 需要重新选端侧。
  `AI`／`OUTLINE`／`OFF` 的请求与产物能力无关；`DEVICE` 在分析缓存键里沿用停用 `AUTO` 的 token，
  改名不该让 comic 用户重付一次分析。气泡来源控件下方不再附说明行，选项名本身承担语义。
  来源参与分析缓存键（请求内容会变），因此切换后需要重新分析，不会误用旧结果。
  几何诊断图除 AI 容器框（黄色）外还画出最终 `bubbleShape` 的轮廓（青色），
  用于区分「模型没给框」和「给了但被客户端复核丢弃」。
- Paddle／MI-GAN／LaMa 继续显式下载，不因选择、绑定、安装或翻译动作偷偷下载。
  运行时统一维持固定来源版本、大小、SHA-256、临时文件校验后原子替换、取消清理和使用租约。
- comic 使用主应用已有模型路径；扩展使用其私有路径，由宿主注入存储根，不能借模块化更换 comic 的目录身份。
- 不依赖跨 UID 访问主应用私有目录。已有模型由用户选择复用时，通过受控文件流交给扩展，
  在扩展侧重新校验后落盘；复制成功前不删除原件，提示短期双份占用，不自动重下大型模型。
- 扩展卸载会失去其私有模型；主应用中的译图、原文、词典和可复用背景缓存仍保留。
- 引擎拆分本身不全量失效缓存；只有输出语义发生变化才更新对应算法标识。
  本地和远程适配返回等价证据，不因包名或 IPC 传输方式改变现有坐标、阅读顺序与掩膜语义。

## 8. 实施切片、验收和回滚

各阶段分别提交可回滚的垂直切片；不能以“文件已移动／能编译”代替行为验收。

### P0：冻结可重复的基线

1. 同一 checkout、JDK 21、依赖、签名配置下重新生成 ARM64 Release，记录 commit、文件大小、SHA-256 和 ZIP 分项。
2. 审计 ORT／ML Kit 的直接及传递依赖、静态入口、assets、NOTICE、R8 规则及全部测试引用。
3. 为 comic 建立固定输入的 OCR／检测／修复证据，并固定翻译编排、长图、取消、失败保留旧译图与缓存重排行为。
   复用现有测试，缺少的边界才补测试；不把历史测试结果写成本次执行结果。
4. 产出定位／合成组合的能力清单，确认哪些云端操作无需本地引擎；为每种缺失状态定义 UI 与自动队列行为。

验收：可重跑的行为与体积基线齐全，调用清单包含 `MangaTranslationImages.kt` 辅助分段。
回滚：无业务变化，只保留基线测试和报告。

### P1：隔离本地引擎，保持现有单一产品行为

1. 在 app 内提取有实际调用方的能力状态与本地推理适配入口，涵盖检测、OCR、修复及模型管理。
2. 将 `MangaTranslationRepository` 构造函数默认实现和长图辅助检测改为显式装配，清除隐藏加载路径。
3. Koin 仍装配现有完整实现，模型状态刷新通过适配入口；领域缺失状态由真实的不可用实现表达，拒绝成功空操作。
4. 在请求前检查整条执行链所需能力与模型；真实推理错误不得被误认为“没有文字”。

验收：原 comic 行为基线通过，调用方不再依赖具体运行库类；不改识别算法、云端协议或数据格式。
回滚：恢复原装配；无持久化迁移。

**状态（2026-10-31）：已完成。** `MangaTranslationRepository` 的五个引擎参数（含两个捕获 `Application`
的默认实现）合并为必需 `internal` 构造参数 `MangaLocalEngine`；`mangaAnalysisBands` 的 ML Kit 直连改为
显式 `locate` 注入；新增 `MangaLocalEngineUnavailableException`、`UnavailableMangaLocalEngine` 与
不可用模型网关。`data.repository.manga.*` 单测通过（comic 285 项）。

### P2：交付 `core/comic` 两种变体

1. 创建 flavor 和 source-set 隔离，把引擎实现、依赖、模型、专用资源／规则及测试移到 comic 对应源集。
2. core 装配能力不可用实现和主应用既有云端能力；UI 明确尚未支持安装扩展，先提供切换漫画增强版的真实路径。
   不能显示一个尚无可安装产物的“扩展已支持”入口。
3. 运行 core 的编译／打包、依赖图与 ZIP 禁入检查；确保无 JNI 初始化错误，修复 DI 和生成绑定引用。
4. 同步调整 CI、Release 上传路径、文件命名、手动用户构建、baseline profile 和测试矩阵。
5. 重测 ARM64 Release 字节数；若未达 20 MB，给出残余项及差额，只在实证后追加小范围瘦身切片。

验收：core 不带引擎负载且能阅读；comic 行为保持；两种产品覆盖安装保留数据；两种 Release/R8 产物可安装。
首个 core 可以先交付，但不宣称独立扩展已完成。20 MB 未达则体积目标仍待完成。
回滚：恢复 comic 为默认发布，保留用户数据，不要求卸载主应用。

**状态（2026-10-31）：变体与隔离已完成，体积目标未达标。**

- 变体：`appCoreDebug/Release`、`appComicDebug/Release`；源集 `src/core`、`src/comic`、`src/testComic`、
  `src/androidTestComic`。`proguard-manga-engine.pro` 只挂 `comic` flavor，ORT 的 `keep` 规则已从公共规则移出。
- core 的依赖图与 APK 条目中都没有 `mlkit`／`onnxruntime`／`manga-vision`；本地引擎绑定换为
  `UnavailableMangaLocalEngine`，模型网关换为不可用实现，`refresh()` 静默以免普通阅读报错。
- UI：core 隐藏 OCR／擦字模型下载入口，并在本地引擎选项处给出“需要漫画增强版”说明；不提供任何无产物的
  “安装扩展”入口。
- 实测 arm64 Release：core **21,605,953 B（21.61 MB）**，comic **72,248,654 B（72.25 MB）**，拆分前单一
  变体为 71,754,462 B。
- **差额 +1,605,953 B**。残余项全部是应用既有负载：dex 11.84 MiB、`resources.arsc` + `tc/s2t.txt` 4.47 MiB、
  `libarchive-jni` 1.99 MiB、assets 1.71 MiB、res 0.39 MiB。未为达标删除用户可见功能，20 MB 目标仍待完成。
- 任务名变更：`compileAppCoreDebugKotlin`、`testAppCoreDebugUnitTest`、`lintAppCoreDebug`、
  `assembleAppCoreRelease` 等；`AppDebug`／`AppRelease` 系列名称不再存在，CI 与 `AGENTS.md` 已同步。
- 体积归因：逐条目对比显示 core 与 comic 的 `resources.arsc` 几乎相同（3,696.3 / 3,700.7 KB），
  comic 相对拆分前多出的 494 KB 来自 10/31 引入的漫画翻译 UI 资源与 dex 重排，不是 flavor 开销。

**复盘修复（同日）**

1. **能力缺失与运行时故障必须分开。** 原 P1 把全部原生异常合并成一种异常，导致 core 的云端 OCR
   在 `geometry()` → `detectContainers` 链路上直接失败（该调用点没有兜底，且气泡分组只是可选排版
   增强）。新增 `MangaLocalEngineFailureException` 表示"运行时存在但失败"，只有
   `MangaLocalEngineUnavailableException` 才降级为空容器；故障仍快速失败以保持 comic 基线。
   回归测试：`cloud OCR still composes when the local engine is absent`。
2. core 不再渲染本地引擎下拉与擦字引擎下拉，只保留说明文案，避免用户选中必然失败的项。
3. `detectContainers` 恢复对普通 `Exception` 的兜底（P1 曾收窄为只兜 `LinkageError`）。
4. 已知行为：band 缓存键不含"是否具备本地检测能力"。comic→core 覆盖安装后命中的是带检测的旧分段，
   反向则可能命中无检测分段（质量降级但合法）；改键会导致全量重算与重新付费，暂不调整。

### P3：运行时模块与扩展的首条真实调用（暂不启动，见 §5.1）

1. 创建实际被 comic 与扩展消费的 `:modules:manga-engine`，迁入有界运行时闭包，移除对 app 类型／资源的反向依赖。
2. 在同一可运行切片创建 IPC 合同与扩展宿主，从握手、可信绑定、一个真实 OCR 操作到取消／结果校验走通。
3. core 新增远程适配，替换 P2 的不可用装配；comic 继续走本地适配，对同一输入比较两条路径结果。
4. 先证明单页、受限图像的通信与生命周期，再开放其他本地操作；只广告已实现的 capabilities。

验收：真机可安装扩展后完成首个本地操作，缺失／不兼容／不可信服务在执行前失败；新增模块均有消费者及 CI 编译。
回滚：core 回到明确不可用状态，comic 不依赖扩展；主应用数据库和缓存不受影响。

### P4：完成能力、模型管理和更新生命周期

1. 接入气泡检测、全部现有 OCR 引擎和两种模型修复；逐操作比较本地／IPC 的结构化结果和修复像素范围。
2. 将现有模型 Gateway 接到扩展状态和命令，完成进度、取消、删除、租约和旧模型显式复用。
3. 验证长条图／大图、内存上限、模型切换、快速换书、自动队列、重排、迟到回调和主／服务进程死亡。
4. 完成安装／更新／卸载感知、兼容提示和宿主安装跳转；首版使用系统安装器，不尝试静默安装。
5. 对新旧主应用／扩展版本做双向兼容测试；大版本不兼容给出可恢复提示。

验收：功能矩阵闭环，取消／失败不覆盖译图；UI/VM 不新增 DAO、偏好或网络直连。
回滚：按 capability 禁用有问题的操作，提供 comic；不删旧译图，不偷偷切云端。

### P5：发布和体积收口

1. 交付 `core` 与 `comic` 的 ARM64／ARM32／Universal 六个产物（`auto-release.yml` 的 matrix 已双发）。
2. 附件名带产品标记；应用内更新按 `AppProduct` 过滤；Telegram 对超 50 MB 的 comic 产物只发链接。
3. 记录同环境两包的大小、模型下载后总占用、冷启动／推理耗时与峰值内存（见体积报告 §3.1）。
4. 更新现有漫画开发文档及用户说明，描述 `core` 真实可用的能力，不提前宣称纯云端全部可用。

验收：两包都用实物结算；同签名覆盖安装、core↔comic 互切后已下载模型与缓存保留；无关业务无回退。
若体积劣化，列出条目与原因，不以估算或放宽门槛结项。不实施路线 B，不启动扩展包路线。

## 9. 验证命令、CI 与门禁

### 9.1 当前真实任务（已含 flavor）

```powershell
.\gradlew.bat :app:compileAppCoreDebugKotlin :app:compileAppComicDebugKotlin
.\gradlew.bat testAppCoreDebugUnitTest testAppComicDebugUnitTest lintAppCoreDebug lintAppComicDebug verifyConfigArchitecture assembleAppCoreDebug assembleAppComicDebug --continue --no-configuration-cache
.\gradlew.bat assembleAppCoreRelease assembleAppComicRelease
git diff --check
```

P1 跑受影响的编排与模型测试，并运行编译／架构门禁；P0 和改变打包的切片必须有 Release/R8 产物。
文档变更只跑空白与引用检查，不需要为此重跑 Android 构建。

### 9.2 flavor 已落地，后续模块才需要扩展

`compileAppCoreDebugKotlin`、`testAppCoreDebugUnitTest`、`lintAppCoreDebug`、`assembleAppCoreDebug`、
`assembleAppCoreRelease` 及 comic 对应任务**均已创建并运行**；旧 `AppDebug` 名称不再存在，CI 必须显式选择产品。
新增模块（运行时模块、扩展宿主）后，用 `tasks --all` 记录真实任务名再入 CI。

已同步的配置：

- `.github/workflows/verify.yml`：core/comic 单测、lint、架构、debug build 与各自产物路径。
- `.github/workflows/auto-release.yml`：matrix 双发；artifact 名带产品；APK 改名为
  `legado-<version>-<product>-<abi>.apk`；mapping 分别上传为 `mapping-<product>.txt`。
- `.github/workflows/build-apk-for-user.yml`：`product` 输入选择 `appCore`／`appComic`。
- `.github/workflows/retrace-log.yml`：`product` 输入，按产品下载对应 mapping。
- `baselineProfile/build.gradle.kts`：维度已对齐，profile 只生成 core 变体。
- `app` 的测试源集及 ProGuard 配置：comic 引擎测试不污染 core；ORT keep 规则只挂在 comic flavor。

### 9.3 必须能证明的检查

| 门禁 | 证据 |
| --- | --- |
| core 无大依赖 | 对 core Release runtimeClasspath 查传递依赖；ZIP 检查 ORT、ML Kit OCR 模型／原生库和气泡权重均不存在 |
| 体积 | 最终签名／对齐后 ARM64 Release 的实际字节数；同时存储环境、commit、哈希、ZIP 分组与差额 |
| 同行为 | `MangaTranslationRepositoryTest`、`MangaTranslationImagesTest`、模型仓库、几何／排版及 ViewModel／Sheet 既有回归集 |
| 实机推理 | 复用 `MangaVisionInstrumentedTest`、`MangaOcrInstrumentedTest`、`MangaInpaintInstrumentedTest` 等夹具，在 comic 与扩展路径执行 |
| IPC 合同 | 成功／空结果／错误、非法输入、取消、重复终态、旧任务迟到、超大负载、服务死亡、协议与签名不匹配 |
| 发布运行 | R8 下反序列化／AIDL／JNI 可用；core 干净安装无扩展、安装后使用、更新／卸载、comic/core 覆盖安装 |
| 存量数据 | 设置／词典／译图／原文／修复缓存保留；模型从 comic 到扩展复制后再校验，不破坏原文件 |
| 架构 | `verifyConfigArchitecture` 等既有门禁通过，不提高 baseline；若减少历史违规，同步下调对应基线 |
| 性能 | 同样本记录 comic 与扩展的冷启动、传输、推理、峰值内存和长图耗时；P0 固定设备与可接受预算后再判断回退 |

不能以模拟 Binder 或 JVM 单测通过替代跨 UID 真机验证；不能以 debug 包通过替代 Release/R8 安装和运行。
真实云端付费服务、手机直连模型下载源、不同厂商后台限制、ARM32 与旧 Android 版本单独列出实际验证范围。

## 10. 设计依据与后续决策

仓库依据：[KMP/CMP 现代化](kmp-cmp-modernization.md)、[Feature-first 结构](feature-first-structure.md)、
[漫画 AI 翻译](manga-ai-translation.md)、[云端 OCR](manga-cloud-ocr.md)、[擦字引擎](manga-inpaint-engines.md)。

官方依据（2026-10-31 已核对，实施时以本仓库 SDK／库版本继续验证）：

- [Android AIDL](https://developer.android.com/develop/background-work/services/aidl)：跨应用 IPC、线程和小型传输合同。
- [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery)：可按需交付代码与资源，面向 Play App Bundle 分发；不作为普通 APK 首期方案。
- [ML Kit Android OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)：内置与 Play services 下载版的交付差异。

开始 P0 无需再选择架构路线。实施中需要用证据收口的事项是：同 HEAD 的实际基线、每条云端流程的依赖、
首版 IPC 格式／性能预算、既有签名轮换与 debug 配对、以及不足 20 MB 的残余字节来源。
这些事项不妨碍先做行为基线和可回滚的引擎隔离；不得据此虚构已验证能力或已存在的构建任务。
