# Legado KMP/CMP 工程现代化路线图

> 状态：提案 / 渐进执行基线（2026-08-20）。本文区分“当前事实”“目标结构”和“计划门禁”；未创建的模块与
> Gradle task 都不是现有 API。

## 1. 目标与非目标

目标是在不牺牲 Android 行为、阅读性能和规则兼容性的前提下，把仓库演进为可验证的 Kotlin Multiplatform /
Compose Multiplatform 工程：

- 共享稳定的模型、领域规则、UseCase、Repository 组合逻辑和适合共享的 Compose UI。
- Android、Desktop、iOS 等宿主只组装平台实现与能力差异。
- 用 Gradle 依赖边界和 CI 门禁替代仅靠包名与评审维持的约定。
- 让 AI 与人工开发使用同一套 AGENTS、专项 Skill、架构文档、源码与测试证据。

非目标：不一次性重写 `:app`；不承诺首个非 Android 目标功能齐平；不把所有代码移入 `commonMain`；不以
Compose 替换成熟阅读器渲染为前置；不在同一批同时替换数据库、网络、导航、DI、UI 和模块系统；不照搬 Now in
Android 的模块数量。

## 2. 依据与当前基线

设计参考：

- [Now in Android](https://github.com/android/nowinandroid) 的应用组装、Feature `api/impl`、core 模块和
  convention plugin
  思路，以及其 [架构说明](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md)
  与 [模块化说明](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)。
- [AndroidProject-Compose 工程边界](https://compose.dusksnow.top/help/intro/modularization.html)、[架构职责](https://compose.dusksnow.top/help/intro/architecture.html)
  和 [AI 辅助开发](https://compose.dusksnow.top/help/intro/ai-coding.html) 的“规则 / Skill / 文档 /
  源码 / 测试”分层。
- KMP/CMP 的具体 API
  与支持矩阵必须在每个实施切片中重新核对 [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)、[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
  和 Android 官方文档，本文不冻结易过期的版本结论。

2026-08-20 盘点快照：

- 现有 Gradle 模块为 `:app`、`:modules:book`、`:modules:rhino`、`:baselineprofile`；`modules/web` 独立构建。
- `app/src/main/java/io/legado/app` 约有 803 个 UI Kotlin 文件，UI、领域、数据、平台服务仍集中在 `:app`。
- `domain/` 中仍存在 Android import、`File`、`InputStream`、`java.time`、JCA 等 JVM 类型，不满足直接搬入
  `commonMain` 的条件。
- 已有 147 个本地测试文件，以及 `verifyConfigArchitecture`、lint、unit test、debug assemble 的 CI 门禁。
- `:modules:book` 的解析职责相对独立，但源码以 Java/JDK API 为主且含少量 Android
  API，适合作为早期依赖审计与边界试点，不能直接视为 `commonMain`；`:modules:rhino` 依赖 Rhino、OkHttp 和
  JVM 生态，应先作为平台实现保留。
- `docs/dev/mad-modernization-plan.md` 已确立“行为优先、基线棘轮、阅读器渲染岛”的纪律，本路线不能推翻它。
- 当前单体内部的 Feature-first 目录、文件归属和模块晋级门槛见 `docs/dev/feature-first-structure.md`
  ，现有目录到 canonical owner 的初始映射见 `docs/dev/feature-catalog.md`；它们是本路线进入真实 Gradle
  模块前的过渡规范。

盘点数字会变化。实施前重新运行 inventory，不把数字写进自动门禁。

## 3. 目标工程结构

目标结构是方向图，不要求一次创建全部模块：

```text
build-logic/convention/             # 模块类型、编译、Compose、测试、lint 约定
app/android/                        # Android Application、DI、服务与兼容入口
app/desktop/                        # 首个低成本非 Android 证明宿主
app/ios/                            # capability 与产品范围成熟后再创建
core/model/                         # 可序列化领域值
core/domain/                        # Gateway、UseCase、纯规则
core/platform/                      # Clock、FileAccess、RuleEngine 等窄契约
core/data/                          # Repository 接口与可共享组合逻辑
core/designsystem/                  # CMP tokens、theme、稳定基础组件
core/ui/                            # 跨 Feature UI
feature/<domain>/                   # presentation、Feature UI、内部 DI
feature/<domain>/api/               # 仅在真实跨模块契约出现后拆分
feature/<domain>/impl/              # 与 api 成对且有真实消费者时存在
platform/android/{database,network,ruleengine,services,reader}/
platform/jvm/ruleengine/            # Rhino 可复用时的 JVM 实现
modules/{book,rhino,web}/            # 现有能力渐进演进
```

### 模块依赖不变量

1. App host 是 composition root，可依赖所有需要交付的 Feature `impl` 和平台实现。
2. Feature `api` 不依赖其他 Feature；Feature `impl` 只能依赖其他 Feature 的 `api`，不能依赖其 `impl`。
3. Core 不反向依赖 Feature。Feature graph 聚合放在 app host，不放入可复用 navigation/core 模块。
4. `commonMain` 不依赖 Android、JDK、Room DAO、文件 URI、资源 ID 或平台服务类型。
5. 平台实现依赖共享契约，公共契约不依赖具体平台实现。
6. `api` 只暴露真实稳定契约；默认使用 `implementation`，需要下游编译可见时才使用 Gradle `api`。
7. 不设无限增长的 `core:common` 或 `utils`。跨 Feature 复用必须有稳定职责名称。

### Source set 策略

- `commonMain`：平台无关实现；至少由一个非 Android target 编译，才算完成共享。
- `commonTest`：领域不变量、序列化、Repository 组合、状态归约和规则契约测试。
- `androidMain`：Context、Room/SQLite 驱动、服务、通知、URI、Cronet/OkHttp、Activity 兼容等。
- `desktopMain` / `iosMain`：只实现 capability matrix 承诺的能力；不支持项显式返回不可用状态。
- 优先普通 interface + DI；只有语言/系统原语且各 target 必须静态提供时才采用 `expect/actual`。
- Android 与 Desktop/JVM 共用但不能进入 common 的代码，先放有明确 owner 的 JVM library/source
  set；自定义中间 source set 必须同时验证 Gradle、IDE 分析和消费方 JVM target，不能用同一源码根多挂
  target 掩盖边界。
- 禁止用 `expect class URL/File/InputStream`、OkHttp API 镜像或 `Any`
  类型擦除维持旧公开签名；先改成领域值、source/sink、request/response 或 capability 契约。

## 4. 平台能力矩阵

每新增 target，先在 PR 中更新矩阵；不允许用空实现把红格伪装成绿格。

| 能力                       | Android     | Desktop/JVM 试点 | iOS 候选   | 初始策略                                 |
|--------------------------|-------------|----------------|----------|--------------------------------------|
| 领域模型 / 纯规则 / UDF reducer | 支持          | 支持             | 支持       | 第一批 commonMain                       |
| TXT/EPUB 解析              | 支持          | 待验证            | 待验证      | 先审计 `modules:book` 的 Android/JVM API |
| Room 数据                  | 支持          | 待 PoC          | 待 PoC    | 先抽 Gateway，保留 Android actual         |
| HTTP / Cronet            | 支持          | 部分             | 部分       | 共享请求语义，客户端留平台侧，之后再评估 Ktor            |
| Rhino JS 规则              | 支持          | JVM 可评估        | 不支持/替代引擎 | `RuleEngine` capability + 兼容测试       |
| 前台服务 / 通知 / 媒体会话         | 支持          | 不适用            | 平台实现     | 永不进入 commonMain                      |
| 阅读器渲染 / 手势 / 翻页动画        | Android 专业岛 | 不承诺            | 不承诺      | 共享 render model，不强制共享 renderer       |
| Compose 设计系统 / 低风险页面     | 支持          | 试点             | 后续       | 先 tokens 和叶子组件，再 Feature             |
| WebService / 远程书架        | 支持          | 可选             | 可选       | 协议模型可共享，server lifecycle 平台化         |

## 5. 分阶段迁移

### Phase 0：冻结基线与建立地图

- 记录模块图、包依赖、Android/JVM import、测试和关键 Android 行为路径。
- 新规则采用 report → freeze baseline → blocking，历史值只允许下降。
- 为书源/规则、导入导出、阅读记录、设置和阅读器关键路径补契约/行为基线。
- 明确首个非 Android 目标和首个共享 Feature；建议先用 Desktop 做证明，产品目标另行决策。

退出条件：现有 CI 全绿；每个候选切片有 owner、输入输出、平台依赖表和回滚点。

### Phase 1：构建逻辑与边界门禁

- 引入 `build-logic` convention plugins，先覆盖新模块，不一次改写 `:app` 全部构建脚本。
- 按 `feature-first-structure.md` 建立 Feature catalog 与 `:app` 内 canonical package；新代码先停止扩大旧
  `ui/...` 混杂结构。
- 只定义需要的模块类型：KMP/CMP library、Android app/library、JVM library、Feature API/impl。
- 在 smoke module 中评估当前 AGP/Kotlin 组合下的 KMP Android library plugin，通过后再固化到
  convention plugin。
- 生成/校验模块依赖图；禁止 core→feature、feature impl→feature impl 等逆向依赖。
- 保留 `verifyConfigArchitecture` 作为 Android 遗留棘轮；新增规则逐步拆成职责明确的 task 或静态分析。
- convention plugin 只放跨模块稳定默认值；native 打包、版本改写和单平台发布流程拆到独立插件或脚本。

退出条件：代表性模块可用 convention plugin 构建；依赖违规有可读错误；Android 产物不变。

### Phase 2：纯 Kotlin/KMP 核心

按风险从低到高抽取：值对象与序列化模型 → 纯函数/解析规则 → Gateway → UseCase → Repository 组合。

- 先消除公共 API 中的 `Context`、`File`、`InputStream`、Room entity、资源 ID 和 JVM-only 时间/加密类型。
- 每移动一组类型，先在原位置建立适配层，迁移调用方后再删除旧入口。
- `modules:book` 先做 dependency audit；能纯化的源码进入 common，平台文件访问留 adapter。
- 每个 `expect/actual` 声明在审查中写明为何普通接口 + DI 不足，并由门禁统计新增量。

退出条件：`commonTest` 通过，至少一个非 Android target 编译，同一 Android 行为测试仍通过，历史泄漏基线下降。

### Phase 3：平台契约与数据层

- 为 Clock、Dispatcher、FileAccess、Archive、Crypto、RuleEngine、NetworkEngine、Database 等定义窄能力契约。
- Android 实现先包住现有 Room、OkHttp/Cronet、Rhino、文件与系统服务，不同时更换底层库。
- 平台实现由 Koin/app composition root 显式注入。
- Repository 的同步、缓存选择、错误语义和取消语义可共享后再移到 common。
- 数据库跨平台方案用 PoC 和迁移/导入导出测试决策，不能仅凭“支持 KMP”替换生产数据库。

退出条件：Android adapter contract tests 通过；共享层没有平台类型；故障、取消、事务与序列化语义有测试。

### Phase 4：CMP 设计系统与首个 Feature

- 先共享颜色/尺寸/排版 token、无平台依赖的叶子组件和 Preview fixtures。
- 选择低风险、少系统依赖、非阅读主链的 Feature 做首个端到端切片。
- Route/导航 runtime 留在 host；共享 UiState、Intent/reducer、Screen/Content，在平台层桥接 Effect。
- 不在首个 Feature 同时替换 Navigation 3。先共享 route 语义/参数，至少两个宿主出现相同栈需求并有返回结果测试后，再决定是否共享导航
  runtime。
- Material 3 与 Miuix 双引擎只有在目标平台均有真实实现时才进入公共组件，否则以平台主题 adapter 隔离。

退出条件：Android 行为与视觉基线通过；选定 target 能编译并完成主路径；无平台调用泄入 shared UI。

### Phase 5：按业务域扩展

- 以垂直 Feature 为单位迁移，不按技术层进行长时间“大搬家”。
- 只有跨 Feature 契约稳定时拆 `api/impl`；应用宿主聚合所有 impl 和导航 graph。
- 每完成一批，删除已无调用方的兼容入口并下调 baseline。

### Phase 6：高风险平台岛决策

- Rhino：以现有脚本兼容测试决定 JVM 复用、替代引擎或非 JVM target 不支持。
- 阅读器：共享书籍/章节/render model 与业务状态；Android `ReadView` 可长期保留。新 renderer 必须先过真机性能与交互
  parity。
- Android 服务、媒体、通知、WebService：共享协议和领域状态，生命周期实现保持平台专属。

### Phase 7：新增正式 target

只有 capability matrix、产品范围、发布链、崩溃/性能观察和数据兼容方案明确后，才把 Desktop/iOS
从架构证明提升为正式产品 target。

## 6. 门禁分级

| 级别             | 适用范围                      | 必须通过                                                                        | 状态           |
|----------------|---------------------------|-----------------------------------------------------------------------------|--------------|
| G0 Android 基线  | 所有 PR                     | unit test、lint、`verifyConfigArchitecture`、debug assemble、`git diff --check` | 已存在          |
| G1 模块边界        | 新/改 Gradle 模块             | convention plugin、依赖图规则、无循环、无禁止方向                                           | 计划           |
| G2 Common 纯度   | `commonMain` 变化           | 禁止平台 API、`commonTest`、metadata + 非 Android target compile、公共 API 检查         | 随首个 KMP 模块启用 |
| G3 数据/平台适配     | Gateway/Repository/actual | contract tests、取消/线程/错误/事务语义、序列化和迁移兼容                                       | 按切片启用        |
| G4 CMP Feature | shared UI                 | Android UI/行为验证、目标平台 smoke、导航/effect parity、accessibility/insets            | 按 Feature 启用 |
| G5 高风险能力       | reader/rules/services     | 真机 parity、性能基线、脚本兼容、release/noR8 或专项验证                                      | 强制人工审批       |
| G6 发布          | 正式 target                 | target 测试/打包/签名、数据升级回退、观测与 capability 文档                                    | 产品化后启用       |

门禁规则：新门禁先 report 再冻结 baseline 最后 blocking；baseline 减少时必须下调，新增违规不得抬高；未创建的
task 只能写成计划名称；主分支 CI 跑对应级别完整矩阵；平台状态分别记录
compile、contract-test、smoke、package、release-ready，不能用一个“支持”覆盖所有等级；编译成功不替代行为、视觉、数据兼容和性能验证。

## 7. 代码生成与 convention plugins

优先顺序：

1. convention plugin 统一 target、compiler、Compose、lint、测试、显式 API 与 source-set 默认值。
2. 约定稳定后，Feature scaffold 生成最小 Contract、ViewModel/presenter、Screen/Content、测试与 build 文件。
3. 模块图从 Gradle 模型生成，不手工维护易漂移的图。
4. 库自身的编译期生成物留在 `build/generated`，不包装成第二套自研框架。

生成器门禁：至少两个手工样本已证明结构稳定；支持 `--dry-run` 且拒绝覆盖；已有 graph/DI
只结构化追加或输出人工补丁；不生成空层；有 fixture 和编译验证；生成结果继续接受普通审查。

建议 Phase 1/2 稳定后再实现 `tools/new-kmp-feature`，本文不提前固定其 CLI/API。

## 8. 重构审查协议

推荐顺序：characterization test → contract/adaptor → 调用方迁移 → 删除旧入口 → baseline 下调。

- 一个 PR 只改变一个主要维度；“移动模块 + 换库 + 改 UI + 改业务行为”必须拆分。
- 纯移动与逻辑修改尽量分开；每个新抽象必须有真实调用方。
- P0/P1：数据损坏、规则不兼容、阅读/服务回归、错误 actual、取消/线程/事务破坏。
- P2：依赖倒置、平台类型泄漏、Feature impl 耦合、状态双重所有权、baseline 放宽。
- P3：命名、模板、文档或模块图漂移。

PR 必备证据：前后依赖变化、行为不变量与测试、`expect/actual` 数量净变化、精确验证命令、capability matrix
的 compile/test/smoke/package 状态变化、未验证项、兼容入口的移除条件与回滚路径。

## 9. AI 上下文与工作流

```text
AGENTS.md                 全局不变量、资料优先级、最低验证
.agents/skills/           专项执行与审查流程
docs/dev/                 架构理由、路线图、能力矩阵
source + tests + Gradle   当前真实 API、行为和可执行证据
```

推荐流程：明确目标/验收 → AGENTS → 专项 Skill → 设计文档与相邻实现 → 依赖/行为基线 → 单一切片 →
风险匹配门禁 → diff 与未验证项报告。

AI 不得根据目标结构虚构当前模块、task 或 API；文档建议 KMP 不等于获得大规模移动或删除权限。

## 10. 首批建议 Backlog

1. 生成当前包/模块依赖和平台 import 报告，先作为 CI artifact。
2. 建 `build-logic`，仅给一个 KMP smoke module 使用，验证 convention plugin。
3. 从 `domain/model` 选择 3–5 个纯值对象建立 `shared:core:model` PoC 和 `commonTest`。
4. 为 `File`/`InputStream` 暴露点设计 source/sink capability，不改底层实现。
5. 审计 `modules:book` 的 Java/JDK/Android 依赖，输出 common-ready、需改写/adapter、必须 JVM 三类清单。
6. 选择低风险设置/信息 Feature 做 CMP 垂直切片，保留 Android 主导航和 DI。
7. 两个成功 Feature 后再决定是否实现脚手架。

完成以上项目后，再基于构建时间、模块图和真实跨平台收益决定下一批，不预先创建整棵目标目录树。
