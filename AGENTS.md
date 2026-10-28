# AGENTS.md

本文件定义仓库级不变量。专项流程放在 `.agents/skills/`，长期方案放在 `docs/dev/`
；不要把临时任务、讨论过程或一次性结论继续堆进本文件。

## 资料优先级

执行任务前按以下顺序建立上下文：

1. 用户本次目标、范围与验收条件。
2. 本文件的仓库级约束。
3. 与任务匹配的 `.agents/skills/*/SKILL.md` 及其明确要求读取的 reference。
4. `docs/dev/` 中的专项设计或迁移计划。
5. 当前源码、测试、Gradle 配置和相邻实现；它们用于确认当前版本的真实 API 与行为。
6. 外部资料只作为设计依据；涉及会变化的 Android、Kotlin、Compose 或库 API 时，以官方当前文档和本仓库版本为准。

文档与源码不一致时，先指出差异。不要为了让实现符合过期文档而静默改代码。

## Skill 路由

- XML/View/Activity/Fragment/RecyclerView 到 Compose 的页面迁移、新 Compose 页面：使用
  `legado-compose-migration`。
- Compose Screen、Contract、ViewModel、导航、状态与兼容边界审查：使用 `legado-compose-review`。
- Gradle 模块拆分、`commonMain` 抽取、KMP/CMP、`expect/actual`、平台能力适配、迁移门禁或相关脚手架：使用
  `legado-kmp-migration`。
- 单个任务可组合 skill，但只读取与当前工作切片有关的 reference。

## 工作方式

- 开始前说明假设、成功条件和不在范围内的内容。存在多种会显著改变结果的解释时，不静默选择。
- 优先做最小、可回滚、可独立验证的垂直切片；不得借任务重构无关代码。
- 修 bug 时先建立可复现测试或其他可观察证据；重构前后保持同一行为验证通过。
- 保留工作区中用户已有修改。只移除本次改动产生的无用代码和资源。
- 不为了“架构完整”创建空模块、单方法包装、无调用方抽象或通用 `Utils/Common` 容器。

## 当前工程事实与目标方向

当前工程是 Android-first 的渐进迁移仓库，不是已经完成的 KMP 工程：

- Gradle 模块：`:app`、`:modules:book`、`:modules:rhino`、`:baselineprofile`；`modules/web` 是独立 Vue 3
  工程。
- `:app` 同时包含 Android UI、数据、领域、服务和大量遗留 View 代码；包目录只表达逻辑边界，尚无 Gradle
  编译隔离。
- 新 UI 以 Jetpack Compose、Material 3、Navigation 3、Koin、StateFlow/SharedFlow 为默认；阅读器渲染核心等成熟
  View 代码可作为 Android 专业渲染岛保留。
- `domain/` 是迁移目标边界，但当前仍存在 Android/JVM 类型渗透；不要把“目标纯净度”描述成已完成事实。
- 长期目标是渐进演进为 KMP/CMP。详细目标结构、阶段和门禁见 `docs/dev/kmp-cmp-modernization.md`。

### Feature-first 过渡结构

- 新 Compose-first Feature 和完成整页迁移的代码使用 `io.legado.app.feature.<name>`；目录、Gradle
  path、Kotlin package 分别采用 `feature/book-info`、`:feature:book-info`、`feature.bookinfo`。
- 在 `:app` 内先形成稳定 Feature 包，再提升为真实 `:feature:<name>` 模块；只有确有多宿主/多模块消费者时才拆
  `api/impl`，只有确有跨平台价值时才改为 KMP/CMP source sets。
- Feature 内共置 Contract、ViewModel、Route、Screen 和私有 components/dialog/sheet/model；文件的详细归属与迁移步骤见
  `docs/dev/feature-first-structure.md`，现有业务 owner 见 `docs/dev/feature-catalog.md`。
- App host 聚合导航和 DI；Feature 不直接依赖其他 Feature 实现。领域、数据、平台能力不能为了目录整齐塞入
  Feature UI 包。
- 旧 `ui/...` 是渐进迁移区，不做全量机械搬家；一个 Feature 的新 owner 建立后，禁止继续向旧包新增同职责文件。

### 目标依赖方向

```text
app host / composition root
        ↓
feature implementation → feature API
        ↓
domain / model / platform contracts
        ↓
data abstractions
        ↓
platform implementations (Android/JVM/iOS/...)
```

- 应用宿主负责组装 Feature 实现、DI、平台生命周期和导航运行时。
- Feature 之间通过稳定契约或导航 API 协作，不直接依赖其他 Feature 的实现。
- `commonMain` 只容纳经过依赖审计、能被至少一个非 Android 目标编译验证的代码；移动目录不等于完成跨平台迁移。
- Android `Context`、Room 实体/DAO、Service、Broadcast、Activity Result、Cronet、文件
  URI、通知、媒体会话、成熟阅读器渲染和现有 Rhino 集成默认留在平台侧，通过窄接口进入共享层。
- 平台能力不可用时必须显式建模为 capability/unsupported，不得用静默空实现伪造跨平台支持。

## Compose 屏幕约束

- 新屏幕使用 Compose；不要新增 XML/View Activity/Fragment。
- Android Compose 新屏幕采用 UDF/MVI：`@Stable XxxUiState`、`XxxIntent`、`XxxEffect`；ViewModel 直接继承
  `ViewModel`，私有 `MutableStateFlow` / `MutableSharedFlow(extraBufferCapacity = 16)`，对外只暴露只读
  Flow，并由单一 `onIntent` 分发用户动作。共享 Feature 的状态宿主按 KMP skill 单独决策。
- Screen/Content 保持无业务逻辑、无 DAO/网络/存储直连；ViewModel 通过 Gateway/Repository/UseCase
  访问业务能力。
- 所有 UiState 与 UI item 数据类标注 `@Stable`。Compose 渲染边界中的集合使用
  `kotlinx.collections.immutable`；内部计算和数据层不机械替换集合类型。
- 导航、权限、文件选择和其他宿主动作通过回调或 Effect 处理。新目的地优先由 `MainActivity` 的 Navigation
  3 图组装。
- 使用项目的 `AppScaffold`、主题、top bar、dialog/sheet、列表和设置组件。正确处理 edge-to-edge 与
  predictive back。
- Activity 仅在遗留 `Intent`/result 兼容确有需要时作为薄宿主保留。

具体文件形态和审查清单由 Compose migration/review skill 维护，本文件不复制完整模板。

## 数据与设置边界

- UI（包括 ViewModel）不得新增 DAO、`appDb.*Dao`、网络客户端或旧偏好 API 直连；历史债务由
  `verifyConfigArchitecture` 的基线棘轮冻结并逐步下调。
- 普通设置 Gateway 通过 `update { current -> current.copy(...) }` 修改状态；关联字段在一次
  `copy(...)` 中原子提交。
- 不引入 `*SettingsUpdate` 分发类型或 `updateAll`。
- `ReadStyleMutation`、`ThemePackageSettingsGateway.applyAndAwait`、`ThemeStateTransaction`、
  `AppUiConfigurationGateway` 等专用 API 保持现有形态。
- Koin 中 Gateway 接口到 Repository 实现保持显式绑定；不要用构造函数绑定掩盖接口归属。普通
  Repository/UseCase/ViewModel 可继续使用项目既有 `singleOf` / `viewModelOf` 约定。
- 新的共享领域契约不得暴露 `Context`、`File`、`Uri`、Room 类型、Android resource id 或 JVM-only
  stream；用领域值、`ByteArray`/抽象 source-sink、序列化模型或平台接口表达。

## KMP/CMP 迁移纪律

- 先写行为/契约测试和依赖清单，再移动代码；一个 PR 只完成一个可说明的边界变化。
- 优先抽取稳定模型、纯函数、规则解析契约和 UseCase；网络、数据库、JS 引擎、服务、阅读器渲染最后通过平台适配器处理。
- `expect/actual` 只用于真正的平台原语。可用普通接口 + DI 表达的能力，不使用 `expect/actual`。
- 平台实现由 Koin 和应用 composition root 组装。
- 不因 KMP 目标同时替换数据库、网络、DI、导航和 UI 框架。每次只改变一个主要风险维度。
- Feature `api/impl` 只有形成真实 Gradle 边界并存在跨 Feature/宿主调用时才创建，不能只建同名文件夹。
- 首个非 Android 目标是架构证明，不承诺功能齐平；目标顺序由 capability matrix 和产品需求决定。

## 代码生成

- 生成器用于编码已稳定的约定，不用于发明架构。至少有两个手工迁移且通过审查的同类样本后，才能固化模板。
- 脚手架必须可预览或 dry-run、拒绝覆盖已有文件、输出最小文件集，并在已有 Feature 上采用追加/AST
  感知修改，禁止覆盖 route graph 或 DI 文件。
- 生成的可提交源码仍接受普通代码审查；真正的编译期生成物放入 `build/generated`，不得手工编辑或提交。
- 模板和生成器变更必须有 fixture/snapshot 或编译验证，并与 convention plugin 的模块类型保持一致。

## 重构审查门禁

每个迁移 PR 至少回答：

- 行为基线是什么，哪些测试或手工路径证明没有回退？
- 依赖边界净变化是什么，是否减少了 Android/JVM 泄漏或历史基线？
- 新模块/接口是否有真实调用方，能否用更小改动完成？
- Android 实现与共享契约的错误、线程、取消、序列化语义是否一致？
- 做了哪些验证，哪些真机、性能、外部服务或其他平台行为尚未验证？
- CI 是否执行了受影响的共享测试、目标编译或 adapter contract test？

减少历史违规时必须同步下调对应 baseline；门禁不接受“先放宽基线再迁移”。详细分级见 KMP/CMP 现代化计划。

## 构建与验证

开发与 CI 使用 JDK 21。按风险选择最小充分验证：

```powershell
# Kotlin/Compose 快速编译
.\gradlew.bat :app:compileAppDebugKotlin

# 当前主验证集
.\gradlew.bat testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache

# 资源、Manifest、生成绑定或打包变化
.\gradlew.bat :app:assembleAppDebug

# Release/R8 相关
.\gradlew.bat assembleAppRelease
.\gradlew.bat assembleAppNoR8

# Web 前端
pnpm --dir modules/web build
```

KMP 任务名在模块实际创建后才存在；不要假装运行尚未定义的 `commonTest`、目标编译或 API
检查任务。新增模块时把其真实任务加入 CI，并在交付中列出准确命令。

所有文本改动至少运行 `git diff --check`。构建通过不替代架构边界、行为和真机性能复核。

## 重要项目约束

- 不将 jsoup 升级到 1.16.2 以上；新版行为会影响 `AnalyzeByJSoup.kt` 与 JsoupXpath。
- 应用代码仅在两处依赖 Hutool：①作为书源 JS 的运行时加密库保留在 classpath（书源直接调用
  `Packages.cn.hutool.*`）；②`help/crypto/CryptoUtils.kt` 的 base64 **解码**统一走
  `cn.hutool.core.codec.Base64.decode`，以兼容缺 `=` 填充等宽松输入（Kotlin
  `kotlin.io.encoding.Base64` 严格填充会导致部分书源取章名/正文回归）。版本固定 5.8.22 勿升级。
  应用内部加密主体仍用现有 JCA（`javax.crypto`/`java.security`）与 `help/crypto` 路径，KMP 抽取时
  再通过能力契约替换 JVM API。
- 代码 namespace 为 `io.legado.app`，Android `applicationId` 为 `io.legato.kazusa`，不要混用。
- 当前 minSdk 26、target/compile SDK 37；Release 启用 R8 与资源压缩，`noR8` 变体用于排障。
- Rhino 书源/RSS/TTS 规则、Android 服务、Web 服务和阅读器渲染属于高行为风险平台能力，迁移前必须建立兼容测试或
  capability 边界。
- `modules/web` 必须连接应用内 Ktor WebService；开发环境通过 `VITE_API` 指向设备服务地址。

## 交付说明

最终说明应包含：修改的职责范围、关键设计取舍、实际运行的验证与结果、未验证风险。不要把“已生成代码”或“已移动文件”当作完成标准。

## 漫画翻译开发约定

- 目标是在 Legado with MD3 的漫画阅读流程中提供图片文字翻译能力。当前仅建立协作约定；未经用户后续要求，不开始修改功能代码。
- 参考项目：[manga-image-translator](https://github.com/zyddnys/manga-image-translator)，说明见
  [中文 README](https://github.com/zyddnys/manga-image-translator/blob/main/README_CN.md)。参考其文字检测、OCR、翻译、去字修复与嵌字流程；不把单纯的文本翻译视为完整的漫画图片翻译。
- 补充参考：[BallonsTranslator](https://github.com/dmMaze/BallonsTranslator)。重点参考文字区域与掩膜处理、译文排版、人工校正，以及带上下文和术语表的翻译流程。
- 补充参考：[manga-translator-ui](https://github.com/hgmzhn/manga-translator-ui)。重点参考多翻译引擎配置、气泡内排版、原图对比、局部文本编辑与批量任务交互。
- 三个参考项目用于研究算法、数据流与交互；手机端仍沿用 Legado with MD3 的原生界面和组件，模型及运行时须独立评估 Android 兼容性、体积、内存与耗时，不直接照搬桌面 UI 或运行环境。
- 实施前先检查现有漫画阅读、图片加载、缓存和设置入口，再确定最小接入方案；不要为了翻译功能重写整个阅读器。
- Android 客户端接入翻译服务与端侧模型推理是不同方案，须先说明依赖、部署方式和设备开销；未验证前不承诺完整端侧运行，也不直接把 Python/PyTorch 工程搬入 Android。
- 若接入上游服务，按选定版本的源码与 API 文档确认请求、配置、任务状态和结果格式，记录所参考的版本或 commit；不要猜测接口或依赖公开演示站作为默认服务。
- 翻译任务应支持取消、失败重试和状态反馈；离开页面或复用图片项时，避免旧任务结果覆盖当前页面。网络、OCR、修复和图片处理不得阻塞主线程。
- 原图与翻译结果分开缓存，允许切回原图；缓存标识须区分图片内容、目标语言和影响结果的配置，避免覆盖原始漫画文件或串用旧结果。
- 服务地址、目标语言及必要的翻译选项沿用现有设置架构和 MD3 组件。图片上传须由用户主动启用并明确说明发送目标；密钥、鉴权信息和漫画图片不得写入源码、提交记录或调试日志。
- 验证至少覆盖成功翻译、无文字图片、服务不可用、取消任务、快速翻页及原图切换；如实区分编译验证、模拟验证和真实服务/设备验证。

## 用户要求提交时的流程

用户说“提交”“帮我提交”等明确要求后，按以下顺序执行；仅编写代码或文档不自动触发提交。

1. **检查改动。** 查看 `git status`、当前分支与差异，确认本次提交范围，保留用户已有的无关修改。
2. **先验证，再清理。** 完成与本次改动匹配的验证并记录结果，然后清理本次开发产生的所有测试文件和测试代码，包括临时测试脚本、测试用例、fixture、mock、测试专用依赖或入口，以及调试日志、调试开关、占位实现和临时输出；这些内容不得进入提交。清理配套引用，避免遗留失效的构建配置。仓库原有且与本次任务无关的测试及正式功能代码不擅自删除。
3. **清理后复核。** 运行 `git diff --check`，检查最终差异中无测试与调试残留、生成物、密钥或无关改动；若清理影响源码或构建配置，重新执行必要的编译/构建验证。不能把清理前的通过结果当作清理后已验证。
4. **提交到分支。** 用户指定分支时遵循指定；否则使用对应的现有功能分支，若当前在 `main`、`master`、发布分支或 detached HEAD，则先创建用途明确的功能分支，例如 `feat/manga-translation`。不直接向主分支提交，不自动合并主分支。
5. **填写中文改动说明。** commit message 使用 Conventional Commits 格式，类型保留英文，如 `feat`、`fix`、`docs`、`refactor`、`perf`、`build`、`chore`；摘要和正文使用中文，代码标识符、路径和专有名词可保留原文。例如：`feat(manga): 添加漫画图片翻译入口`、`fix(manga): 修复翻页后翻译结果错位`、`docs: 补充漫画翻译与提交约定`。正文按需要说明具体改动、原因和实际验证结果。
6. **核对暂存并提交。** 只暂存本次任务相关文件，使用 `git diff --cached` 复核后执行提交；不使用无差别暂存或清理命令，不擅自覆盖历史提交。提交后报告分支名、commit hash、中文改动摘要及验证结果。

“提交”默认指本地 Git commit；用户要求推送时再 push 到对应远端分支。若同时创建 PR，PR 标题沿用英文类型前缀与中文摘要，描述使用中文。
