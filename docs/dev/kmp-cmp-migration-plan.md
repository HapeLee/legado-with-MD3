# Legado 完整 KMP/CMP 迁移计划

> 状态：执行计划（2026-09-10）。目标架构见 [`kmp-cmp-modernization.md`](kmp-cmp-modernization.md)。
> 本文替代此前按日期累加的 P0–P7 流水账；历史实现细节由 Git 保留。本文只维护当前基线、未完成差距、
> 有序里程碑、门禁和下一批可执行切片。

## 0. 结论与路线选择

`D:\Project\shutiao\legado` 证明 Legado 可以运行在 KMP/CMP 多宿主上，但它的巨型 `shared`、旧包名搬运、
服务定位器和复杂 source-set 网不是本项目的目标范式。本项目选择：

1. 以业务域和 Feature 为 Gradle 边界，不建单体 `shared`。
2. 纯逻辑用 KMP，界面/资源/共享 ViewModel 用 CMP，平台实现留 target/platform 模块。
3. 继续使用 Koin，但只做 composition root 和构造注入；清退全局 `XxxProvider.current`。
4. 不保留内部 `help/utils/base` 兼容 façade；规范 API 建立后迁移调用方并删除旧实现。
5. Android、Desktop、iOS 均进入正式完成定义；Web 继续使用现有 Vue + Ktor WebService 形态。
6. Room、Ktor、CMP resources、KMP ViewModel、Navigation 3 采用官方支持路径；版本替换与模块移动分开提交。
7. Rhino/jsoup 兼容、书源 JS ABI、阅读/TTS/服务是专门工作流，不因“架构更干净”改变行为。

迁移按可发布垂直切片推进：每个里程碑结束时 Android 仍可发布，且至少一个非 Android 宿主获得可观察能力。

## 1. 2026-09-10 当前事实

### 1.1 已存在的工程边界

根 `settings.gradle` 当前包含：

- Android host：`:app`；另有 `:baselineprofile`。
- KMP：`:core:platform`、`:core:model`、`:core:data`、`:core:designsystem`、`:feature:reader:core`。
- Android library：`:core:ui`、`:core:viewmodel`、`:feature:tagrules`、`:feature:replacerules`、
  `:feature:dict`、`:feature:txttocrules`。
- 现有能力模块：`:modules:book`、`:modules:rhino`。
- PoC：`:smoke:kmp-probe`、`:smoke:network-kmp-probe`、`:smoke:rhino-capability-probe`、
  `:smoke:room-kmp-probe`。

KMP convention 已使用官方 `com.android.kotlin.multiplatform.library`，当前统一声明 Android +
`jvm("desktop")`；尚未声明 iOS targets。CI 已执行 Android 基线、四个 PoC、reader core 和三个 KMP core
的 metadata/Android/Desktop 编译及 host/desktop test。

### 1.2 实测规模

以下数字只作为 2026-09-10 快照，不写入固定阈值：

| 范围 | Kotlin 文件 | Kotlin 行数 | 判断 |
|---|---:|---:|---|
| `app/src/main` | 1044 | 213,780 | 仍是主要单体，包含 UI、平台、数据和业务实现 |
| `core:platform` | 58 | 2,415 | KMP，但混合多种无关 capability，并有全局 Provider |
| `core:model` | 94 | 4,760 | KMP，但仍保留 14 个 `io.legado.app.utils` 文件 |
| `core:data` | 278 | 17,585 | KMP，已含 Room 主体，但过粗且混有旧 package/platform façade |
| `core:designsystem` | 3 | 101 | 当前不是 CMP，仅有纯状态叶子；名称与职责不符 |
| `core:ui` | 166 | 19,370 | Android library，尚非共享 CMP UI |
| `core:viewmodel` | 10 | 900 | Android library，保留 `BaseViewModel/BaseRuleViewModel` 继承结构 |
| `feature:reader:core` | 116 | 8,808 | 纯 KMP reader core，尚不是完整 reader Feature |
| 四个独立 Feature | 20 | 4,286 | 都是 Android library，不是 CMP |

`:app` 物理目录仍有 `help/` 110 个 Kotlin 文件、`utils/` 98 个；全仓生产/测试代码还出现 11 个
`object XxxProvider(s)` 定义。`appCtx/appDb/GSON/LocalConfig/ReadBookConfig` 等静态入口仍有广泛调用。
这些不是要“搬到 shared”的资产，而是必须按职责拆解的迁移债务。

### 1.3 已验证资产

- `checkSharedPurity`、`checkModuleDependencies`、`verifyConfigArchitecture` 已 blocking。
- `core:model` 已有一批纯值/纯函数和 commonTest。
- `core:data` 已将 Room entities/DAO/AppDatabase 放入 commonMain，并有 Desktop 建库、schema、迁移、事务和并发测试。
- `core:platform` 已对 Clock、dispatcher、文件、摘要、HTTP、图片、HTML、规则引擎等做过 Android/Desktop PoC 或契约测试。
- `feature:reader:core` 已覆盖分页、布局、选择、手势、朗读和状态模型等共享逻辑。
- Android Feature-first 和 Navigation 3 已开始收拢独立 Activity。

这些证据保留；现有模块名、Provider、旧 package 或临时抽象不因此自动成为目标 API。

### 1.4 与 `shutiao/legado` 的关键差异

| 议题 | 参照仓库 | 本项目决策 |
|---|---|---|
| 共享边界 | 一个 1450 文件的 `shared` | 多个高内聚 domain/data/feature KMP/CMP 模块 |
| UI | 328 文件集中在 `sharedUiMain` | Compose 直接进入各 CMP Feature 的 `commonMain` |
| DI | 大量手写 `XxxProviders` | Koin module + 构造注入，宿主组装 |
| 旧命名 | 大量 `help/utils/*Shared` 原样保留 | 迁移时改成职责名；无内部兼容 façade |
| source sets | 17 个定制 source set | 默认 hierarchy；只为真实 target 子集增加中间层 |
| iOS 规则引擎 | QuickJS/cinterop | 作为兼容工作流引入，先过真实书源 corpus |
| 鸿蒙 | fork Kotlin/CMP/Room + 派生源码 | 不在本轮范围 |
| 测试/守卫 | 共享测试和架构守卫偏少 | common contract + 平台 parity + 依赖/旧架构棘轮 |

## 2. 当前结构的主要问题

### 2.1 “移入 KMP”不等于完成规范化

当前已有几种过渡做法需要继续收口：

- `core:model` 仍使用 `io.legado.app.utils` 包名，说明物理移动完成但 owner/语言未完成。
- `core:data` 同时承载 Room、Repository、规则分析、Android Gson façade 和旧 `help`，职责过宽。
- `core:platform` 包含 UI toast/clipboard、source runtime、crypto、file、HTTP、parser 等不相关能力；
  `SourceRuntime` 甚至包含 `Any` 与 `androidId()`，不是合格的共享契约。
- `ClipboardProvider`、`ToasterProvider`、`KeyValueStoreProvider`、`BigDataStoreProvider` 等把 DI 退化成
  运行时 service locator，形成注册顺序与测试污染风险。
- Room entity 含 I/O 行为时只能借全局 Provider 取得依赖，反向证明 entity 与领域行为没有分开。
- Android Feature 为了复用 `BaseRuleViewModel`、Android `R`、Android lifecycle 和 `core:ui`，尚不能成为 CMP。

### 2.2 旧计划中需要废止的假设

- “`commonMain` 一律零 Compose”只适用于 pure KMP；CMP Feature/design system 的 Compose 必须位于
  `commonMain`。门禁需按模块类型区分。
- “为了调用方 import 零改动保留旧包名”不再是目标。内部调用方应随切片迁移到规范 API。
- “Android actual/desktop actual 共用同一 JVM 实现即完成多平台”只能算 PoC；完整目标还需 iOS compile/test/package。
- “Feature 提升为 Android module 就完成模块化”只是 Stage B；最终 Feature 需要 CMP source sets、共享资源和宿主 smoke。
- “接口 + 全局 Provider”不是 DI。接口必须由构造函数接收，Koin 只在图的边界解析。

## 3. 执行不变量

每个切片都遵守：

1. 先列出精确源文件、调用方、行为基线、目标 module/source set、平台依赖与回滚点。
2. 依赖分类为 `common-ready`、`contract-needed`、`platform-island`、`unknown`；unknown 先 PoC。
3. 同一切片只改变一个主要风险维度。移动模块、换库、改数据格式、改 UI 行为分别提交。
4. 新 API 先有真实消费方和测试。调用方迁完立即删除旧入口，不保留内部 `Help/Utils` façade。
5. 不新建空模块；本计划中的路径只有首个真实文件进入时才加入 settings/CI。
6. 不抬高任何历史 baseline。新增规则 report → freeze → blocking；新代码可 day-one blocking。
7. Android G0 始终保留；共享代码还必须由 Desktop 和 iOS 的真实 target 验证。
8. jsoup 固定 1.16.2、Hutool 固定 5.8.22；任何引擎/解析替换必须有书源兼容证据。

切片 PR 必须回答：

- 什么行为不变，哪条测试证明？
- 依赖净变化和 legacy baseline 减少多少？
- 是否新增 module/interface；其真实调用方是谁？
- 三端的错误、取消、线程、序列化和能力语义是否一致？
- 精确运行了哪些 task；哪些真机/外部服务/平台仍未验证？

## 4. 里程碑

里程碑按依赖顺序编号，不按日历估时。M0–M2 是后续大规模 Feature 迁移的硬前置；M3–M6 可在边界稳定后按
不同业务域并行，但单个 PR 仍保持垂直、可回滚。

### M0：让门禁表达正确的目标架构

目的：先停止复制过渡债务。

工作：

1. 把 `checkSharedPurity` 改成按模块类型配置：
   - pure KMP 禁 Compose/Android/JDK-only/实现库；
   - data KMP 只允许白名单 Room/Ktor 等数据实现；
   - CMP 允许 Compose resources/Lifecycle/ViewModel/Nav3，仍禁 Android `R` 和平台 API。
2. 新增 `checkLegacyArchitecture`，扫描生产源码：
   - 禁止新增 `io.legado.app.help` / `utils` / `base` package；
   - 禁止新增 `XxxHelp/XxxUtils`；
   - 禁止新增带可变 delegate 的 `XxxProvider(s)`、`install/current/get()` 服务定位器；
   - 统计 `appCtx/appDb/GSON` 直连和 Feature→DAO/data source 直连，冻结为只降 baseline。
3. 扩展 `checkModuleDependencies`：区分 domain/data/runtime/platform/feature/host，禁止具体实现越界。
4. 从 Gradle 模型生成 module/source-set/target 报告；文档不再手抄易漂移模块数。
5. 将根 `settings.gradle` 转为 Kotlin DSL 是独立机械切片；行为和仓库解析结果必须一致。

退出：三类模块各有一个 fixture/代表模块；新增违规可读地失败；现有 Android/KMP CI 全绿。回滚是移除新 blocking
规则并保留 report，不能通过抬 baseline 回滚。

### M1：建立一个不含旧架构的三端垂直样板

目的：用真实 Feature 定型 build logic、CMP resources、KMP ViewModel、Koin 和三端 host 接线。

首选样板：`:feature:tagrules`，因为边界小、已有 Contract/ViewModel/Screen/Repository 调用且能直接暴露当前
`BaseRuleViewModel`、Android 资源和 Provider 问题。若依赖审计发现书源运行时耦合扩大，则降级选更小的
`:feature:txttocrules` 管理子域。

按独立切片执行：

1. 建立已证明的 convention plugins：`legado.kmp.pure`、`legado.kmp.data`、`legado.cmp.feature`、
   `legado.cmp.library`；先只服务样板，不批量改全仓。
2. 把 `core:designsystem` 转为真实 CMP：theme/token/首批有两个调用方的基础组件进入 `commonMain`，资源进入
   `commonMain/composeResources`。不再保留“名称是 designsystem、实际只有三个纯状态文件”的形态。
3. 将样板 Feature 转为 KMP/CMP：UiState/Intent/Effect、AndroidX KMP ViewModel、Screen 和资源进 `commonMain`；
   文件 picker/clipboard/toast 以 effect 或构造注入 port 处理。
4. 删除样板对 `BaseRuleViewModel`、`ClipboardProvider`、`ToasterProvider` 和 Android `R` 的依赖；共享导入导出流程
   抽为无 UI `RuleTransferUseCase`，不再抽基类。
5. Feature 导出 Koin module；Android host 加 Android capability module。业务类不实现 `KoinComponent`。
6. 建最小 `desktopApp` 和 iOS framework/Xcode host，显示并操作同一 Feature；宿主不是空壳，必须覆盖列表加载、编辑、
   导入/导出取消和状态恢复中的一条真实主路径。
7. Nav3 back-stack entry 安装 saveable-state/ViewModel decorators；三个宿主验证 ViewModel 销毁和恢复。

退出：样板达到 Android/Desktop/iOS compile + contract-test；Android/Desktop smoke；iOS simulator smoke；三端 package 至少一次。
旧 Android Feature 文件和内部 façade 删除。回滚点是切回旧 Android route，不回滚已经验证的 domain/usecase。

### M2：清退全局 Provider、继承基类与旧 package

目的：避免后续把 Android 单体的依赖获取方式复制进 KMP。

按调用闭包分批，不做全仓一次性重命名：

| 当前债务 | 目标处理 | 完成证据 |
|---|---|---|
| `ClipboardProvider` / `ToasterProvider` | UI Effect 或 Feature 构造注入 port | 所有 Feature 测试直接传 fake |
| `KeyValueStoreProvider` | 领域设置 Repository 构造注入 | 无全局 delegate；原子更新 contract |
| `CookieStoreProvider` | `data:source` 私有 cookie store | network/source tests 不依赖全局状态 |
| `SymmetricCryptoProvider` | 使用方所属 domain port + platform implementation | 已知向量和错误 contract |
| `SourceRuntimeProvider` | 拆成 typed ports；移除 `Any`、`androidId` | 每个 port 有单一职责/消费方 |
| `BigDataStoreProvider` | `SourceVariableRepository` 注入 UseCase | Room entity 变纯数据，不执行 I/O |
| `ImportJsonEditorProvider` | 导入流程的 Feature effect/usecase | 取消、错误、保存行为测试 |
| `BaseViewModel/BaseRuleViewModel` | 每 Feature ViewModel + 共享无 UI usecase | 删除 `core:viewmodel` |
| `utils.*` in `core:model` | 迁到所属 model/domain 包并改职责名 | `io.legado.app.utils` commonMain = 0 |
| `help.*` in `core:data/viewmodel` | 迁到 data/domain/runtime owner | KMP/CMP 模块旧 package = 0 |

`:app/help` 和 `:app/utils` 使用同一规则分类：

- 纯函数：迁到使用它的 domain/core owner，使用描述行为的函数名。
- Repository/data source：迁到对应 `data:<domain>`，实现保持 `internal`。
- Android UI/system extension：迁到 `platform:android` 或 Android host 的明确 package。
- 跨对象业务流程：改为 UseCase/Coordinator 并构造注入。
- 无调用方、仅为历史 API 形状存在：删除。
- 经 corpus 证明属于书源 JS ABI：只留 `compat:source-js-api` 薄桥，实际实现不得留在 `help`。

每批退出时删除旧文件/类型，legacy baseline 同步下降。M2 完成时所有共享模块零旧 package、零 service locator；
Android host 的旧 package 允许继续按后续 Feature 批次下降，但禁止新增。

### M3：把巨型 `core:data` 拆成规范的数据/领域边界

目的：让 Feature 只见 Repository/UseCase，不见 Room 和平台 façade。

建议顺序：规则管理样板 → settings → library/bookshelf → source/RSS → sync/backup → readaloud/AI。

每个业务域执行同一模板：

1. 从现有实体/DAO/Repository 调用链生成依赖清单和 public API 清单。
2. 在 `domain:<name>` 定义领域模型、Repository port、错误和 UseCase；不复制 Room entity。
3. 在 `data:<name>` 放 Repository 实现、mapper 和 data source；它可依赖 `data:database`、Ktor、文件或平台 port。
4. `data:database` 保持 Room entity/DAO/database/schema 的唯一 owner；DAO 和 entity 默认 `internal`，Feature 禁止依赖。
5. 从 `core:data` 移走该域后删除原文件，不留 typealias/façade；稳定序列化字段用显式 serializer/migration 维持。
6. 当 `core:data` 没有剩余单一职责时删除该模块。

专项决策：

- Room 2.8.4 → 当前官方 Room 3 是独立版本迁移：先在 PoC 上复跑 KSP/driver/schema，再迁生产 DB；
  必须通过 schema identity、103→当前版本、备份恢复、事务、并发、Android 真机升级。失败则继续使用已验证的 2.8.4，
  不阻塞模块规范化。
- 新网络 data source 使用 Ktor；engine、TLS、proxy、cookie 和 credential 在宿主注入。删除通用 `help/http` façade，
  Cronet 只作为 Android engine/特殊 source adapter。
- 新序列化使用 kotlinx.serialization。Android 历史 JSON 读取可在 migration adapter 使用 Gson，写出格式由 golden test 固定；
  不把 `GSON` 或 Gson extensions 暴露给下游。
- 文件访问先统一领域 document/source/sink，再选择可用的 KMP I/O 实现；SAF/security-scoped bookmark 是宿主 handle，
  不能退化成假 `String path`。

退出：每个 domain 的 Feature 只依赖 domain API；三端 data contract 通过；`core:data` 最终删除。

### M4：书源/RSS 规则运行时产品化

目的：把最危险的 JVM/Android 绑定收进明确 runtime，而不是散在 `help` 和 domain entity 中。

切片顺序：

1. 建立脱敏、可提交的真实书源/RSS/TTS corpus，覆盖 JS bindings、网络、cookie、重定向、编码、HTML、XPath、
   JSONPath、正则、并发、取消和异常。
2. 把 `BaseSource` 等 Room entity 的 JS/I/O 方法提取为 `domain:source` UseCase；entity 只保留持久化数据。
3. 将当前 `HtmlParser`、`RuleEngine` 等 PoC 按真实调用方重塑到 `runtime:source-api`；删除 `Any` 返回和过宽接口。
4. `runtime:source-jvm` 委托现有 Rhino、jsoup 1.16.2、JsoupXpath、JsonPath、Hutool runtime；Android/Desktop
   共用实现必须保持取消、安全名单和反射 ABI 一致，不能让 Desktop 使用“裸 Rhino”后声称正式支持。
5. `runtime:source-native` 选择 QuickJS/解析实现；先跑同一 corpus，再接 iOS。引擎替换只在该 runtime 内发生。
6. 识别 `Packages.*`、静态方法、jsoup response 等外部 ABI；确需保留的旧 FQCN 放进 `compat:source-js-api` 薄桥并测试。
7. 网络、cookie、大变量、crypto 通过构造注入进入 runtime session；无全局注册顺序。

退出：Android/Desktop/iOS 同一 corpus 达到约定 parity；超时/取消/内存限制和恶意脚本测试通过；
`help/JsExtensions`、`help/source`、`RuleBigDataHelp` 等旧实现删除或仅剩有证据的 ABI bridge。

### M5：按 Feature 迁移 CMP UI

目的：将 500+ 个 Android UI 文件按用户能力收敛，而不是横向搬整个 `ui/widget`。

批次：

1. 已独立的规则 Feature：tag rules、replace rules、dict rules、TXT TOC rules。
2. 低风险管理页：about、settings 子流程、source/rule 管理、备份/同步配置。
3. 核心浏览：bookshelf、search、book info、TOC、RSS。
4. 高交互：source debug/login、WebView 流程、readaloud、AI flows。
5. reader 完整 Feature（与 M6 协作）。

每个 Feature 的完成形态：

```text
feature/<name>/src/commonMain/kotlin/
  <Name>Contract.kt
  <Name>ViewModel.kt
  <Name>Screen.kt
  components/                  # Feature 私有
feature/<name>/src/commonMain/composeResources/
feature/<name>/src/commonTest/
feature/<name>/src/androidMain/    # 仅 Android adapter/interop
feature/<name>/src/desktopMain/
feature/<name>/src/iosMain/
```

- `Route`/route key 可共享，但 root entry graph 在 `app-shared`，external intent/deep link 在 host。
- ViewModel 直接继承 KMP AndroidX `ViewModel`，私有 MutableStateFlow/SharedFlow，单一 `onIntent`；不继承项目基类。
- Screen 不解析 Koin、不接 DAO、不调 platform API；Route/entry 从 Koin 取得 ViewModel，Screen 接 state/callback。
- 资源使用 CMP `Res`，不复制 app `R` 并依赖 Android merge override。
- 共用组件只有出现第二真实调用方且语义一致时进入 `core:ui/designsystem`。
- Android Activity 只有外部 Intent/result/ContentProvider 等 ABI 才保留为 host adapter；内部导航全部进共享 Nav3 stack。

退出：旧 `ui/<domain>` owner 清空；Android/Desktop/iOS 主路径 smoke；视觉、语义、状态恢复和 back 行为达标；
Feature 的旧 Android library 配置删除。

### M6：阅读器、TTS 与系统能力

目的：完成用户主链，而不是永久把高风险能力标为平台岛。

- 以 `feature:reader:core` 现有分页/布局/选择/手势/朗读模型为起点，建立完整 `feature:reader` CMP。
- 共享 render model、状态机、排版规则、菜单和适合共享的 renderer；字体测量、图片解码、平台文本/selection、
  window/gesture 差异通过窄 adapter/slot 注入。
- Android 当前 Compose reader 行为是回归基准；Desktop/iOS 分别建立大书、图片书、漫画、字体、选择、翻页和恢复 smoke。
- TTS 共享队列、角色、请求编排和状态；系统 TTS、audio session、media control、notification、background execution
  为平台实现。云 TTS credential 不进入 common resources/log。
- Android Service/Broadcast/ContentProvider/WebService 都留 Android host/platform，实现只接收领域 command/state；
  Desktop/iOS 使用各自生命周期实现，不伪造 Android service。

退出：三端阅读主路径和承诺的 TTS 能力达到 release-ready；性能/内存/电量基线有证据；Android 外部入口保持兼容。

### M7：宿主、发布和旧单体退场

目的：把“能跑的共享库”变成真正的多平台产品工程。

1. `app-shared` 只组装根 CMP UI、共享 Nav3 graph 和 common modules；不吸收 data/runtime 具体实现。
2. `androidApp` 从现有 `:app` 演进：保留 applicationId `io.legato.kazusa`、Android service/receiver/provider、
   权限、Firebase、R8、baseline profile 和发布配置。
3. `desktopApp` 完成 installer、升级、文件关联、日志、崩溃和数据目录策略。
4. `iosApp` 完成 framework 集成、签名、document picker、keychain、background/audio、崩溃和发布配置。
5. 每个宿主有 Koin graph creation test；没有 provider install 顺序。
6. 三端数据迁移、备份恢复和同账户同步互操作通过。
7. PoC 结论已进入生产 contract 后删除 `smoke:*`；只保留仍验证工具链未知项的短期 PoC，并写删除条件。
8. `:app` 中最后的共享业务移走后改名/收敛为 `:androidApp`；删除空旧模块和过渡资源。

退出即完整验收：三端 clean build/test/package；Android release/noR8；Desktop installer smoke；iOS simulator/device smoke；
legacy gate 归零；目标能力矩阵达到 release-ready。

## 5. 工作流 Backlog

下面按“解除后续阻塞的价值”排序。一次只领取一个可独立验证的切片。

### 立即执行

1. **M0-1：模块类型 purity policy**
   - 文件：根 `build.gradle.kts` 的 `CheckSharedPurityTask`、build-logic、对应测试 fixture。
   - 结果：pure/data/CMP 三种 policy；不再用“全 commonMain 禁 Compose”阻塞 CMP。
   - 已完成（2026-09-10）：policy 入 `CheckSharedPurityTask.kmpModuleTypes`，CMP 正反断言已验证。
2. **M0-2：legacy architecture report**
   - 扫描 `help/utils/base`、`XxxHelp/Utils`、Provider delegate、`appCtx/appDb/GSON`、Feature→DAO。
   - 先生成报告并冻结当前 baseline；新代码 blocking。
   - 已完成（2026-09-10）：报告 [legacy-architecture-report.md](./legacy-architecture-report.md)；
     基线 `gradle/architecture/legacy-baseline.txt`（330 条，目录级）；
     门禁 `checkLegacyArchitecture`（挂 `assemble`/`compile`）；重新冻结脚本
     `tools/generate-legacy-baseline.py`。Feature→DAO 一类沿用 `verifyConfigArchitecture` 的
     DAO 基线，未重复造轮子。
3. **M1-1：`tagrules` 依赖审计**
   - 精确列出 7 个 Feature 文件、`BaseRuleViewModel`、Repository、资源、clipboard/toast/file picker 的调用闭包。
   - 先补 reducer/import/export characterization tests，不改生产行为。
   - 已完成（2026-09-10）：审计 [feature-slicing-audit-tagrules.md](./feature-slicing-audit-tagrules.md)；
     12 个 characterization 用例（真实 GSON + 内存 Room）锁定导入分类/导出/落库/排序/`hasChanged`
     语义，并用两处变异（去掉 enabled 比较、默认勾选改全 true）验证过它们会红。
     结论：阻塞 CMP 的只有 5 类边界依赖（Application、Provider、`R`、SAF、GSON 门面），
     数据与列表编排无需先动。
4. **M1-2：CMP convention + designsystem 最小真实切片**
   - 只迁 `tagrules` 真实使用的 theme/组件/资源；拒绝预建空 token 和未来组件。
   - 已完成（2026-09-10）：新增 convention `legado.kmp.compose`（build-logic），Compose 依赖
     进 `commonMain`（不再用 `composeMain` 中间源集）；`:core:designsystem` 应用后登记为 `cmp`。
     首块真实切片 = 引擎语义 `ComposeEngine` + `LocalComposeEngine`（`ComposeEngine.kt`）、
     `adaptive*Padding`、`AdaptiveSwitch`/`IconSwitch`（后两块自 `:core:ui` 搬入，
     **包名不变故调用方 import 零改动**）。间距刻度收敛为 `AdaptiveSpacing` 一处，由 7 个
     新用例锁住；`parseComposeEngine` 承接了 `ThemeResolver.isMiuixEngine` 的原语义。
     契约与制品可用性实测见 [cmp-module-convention.md](./cmp-module-convention.md)。
   - **最大的未知项已退役**：双引擎（CMP material3 + Miuix KMP 模块）在 `commonMain` 里
     **android 与 desktop 都编译通过**。material3 走 CMP 坐标（它的 android 变体内部再委托给
     `androidx.compose.material3:material3`，平台解析由 CMP 负责，不用手写分支）；版本取插件常量
     `composeMaterial3Version`（= **1.9.0**，不是插件号 1.12.0——别挑 alpha），并由 convention
     在配置期断言版本目录与之一致。Miuix 必须用不带 `-android` 后缀的模块
     （`Switch` 在 `miuix-ui`，不在 `miuix-core`）。
   - 对 M1-3 的含义：UI 组件本身大多可搬，真正的阻塞仍是**平台能力**
     （`android.*`、`LocalClipboard`/`LocalContext`、SAF 选文件、`GSON` 门面）与
     Miuix 的 **android 专用别名模块**（`miuix-blur-android` / `miuix-preference-android`）。
     顺序应是先抽契约、再搬 UI（见上文档 §6）。
5. **M1-3：消除 tagrules Provider/base 依赖并转 CMP**
   - 迁完删除旧 API；Android route 保持可回滚。
6. **M1-4：最小 Desktop/iOS host 主路径**
   - 展示同一 Feature，验证 Koin graph、ViewModel lifecycle、resources、Nav3 和一条数据路径。

### 随后执行

7. 按 M2 表逐一删除 9 个已知 core Provider；先处理会阻塞现有四个 Feature 的 clipboard/toast/import。
8. 将 `core:model` 的 `utils` 文件按领域改包改名；同一 PR 迁完调用方，不加 typealias façade。
9. 把 `core:viewmodel` 的共享流程下沉为无 UI UseCase，迁四个 Feature 后删除模块。
10. 以 rules/settings 为第一个 `core:data` 拆分样板；Feature 不再见 DAO/entity。
11. 建真实书源 corpus，再决定 native JS/parser，不先搬 `JsExtensions`。
12. 从 Feature catalog 逐域推进 M5；reader、TTS、service 使用 M6 专项门禁。

## 6. 验证矩阵

### 6.1 当前真实任务

文档改动和所有切片至少运行：

```powershell
git diff --check
```

当前 Android G0：

```powershell
.\gradlew.bat testAppDebugUnitTest lintAppDebug verifyConfigArchitecture `
  checkSharedPurity checkModuleDependencies checkLegacyArchitecture assembleAppDebug `
  --continue --no-configuration-cache
```

当前 KMP 模块使用已存在的 task，例如：

```powershell
.\gradlew.bat `
  :core:model:compileCommonMainKotlinMetadata :core:model:compileKotlinDesktop :core:model:desktopTest `
  :core:data:compileCommonMainKotlinMetadata :core:data:compileKotlinDesktop :core:data:desktopTest `
  :feature:reader:core:compileCommonMainKotlinMetadata `
  :feature:reader:core:compileKotlinDesktop :feature:reader:core:desktopTest `
  :core:designsystem:compileCommonMainKotlinMetadata `
  :core:designsystem:compileKotlinDesktop :core:designsystem:desktopTest `
  --no-configuration-cache
```

注：`:core:designsystem` 是 CMP 模块，其 CMP/AndroidX-compose 的 `*-metadata` 制品首次
解析必须联网（`--offline` 会报 "No cached version available"）；拉过一次后即可离线。

Windows 若复现跨盘 Gradle cache 问题，再使用仓库已验证的 `-Dgradle.user.home=D:/Android/.gradle`；
不要把开发机绝对路径写入 CI 或 convention。

### 6.2 计划中的任务

以下只在对应模块创建后进入 CI，不得提前声称已运行：

- 每个 pure/data/CMP 模块：metadata、Android、Desktop、iOS simulator ARM64 compile + commonTest。
- data/runtime：各目标 contract tests；Room schema/migration；真实 source corpus。
- Feature：Android/Desktop/iOS UI smoke、semantics、state restore、navigation tests。
- Host：Android assemble/release/noR8、Desktop package、iOS link/package。
- 发布分支：三端升级/恢复/同步互操作与性能基线。

## 7. 风险与决策门

| 风险 | 触发点 | 决策门/回滚 |
|---|---|---|
| 旧 package 改名破坏书源 JS | FQCN/静态方法进入 corpus | 仅该 ABI 进 `compat:source-js-api`；实现仍使用新 API |
| Room 版本与 schema 漂移 | Room 3 PoC 或生产切换 | schema/migration/backup 任一失败即留 2.8.4，模块拆分继续 |
| Native 规则引擎不兼容 Rhino | iOS runtime corpus | iOS 不提升 release-ready；Android/JVM 不换引擎 |
| CMP UI 牺牲平台体验 | Feature/reader smoke | 使用 target slot/adapter；必要时平台 Screen 复用同一 ViewModel/domain |
| Koin 图只在运行时失败 | 新 host/module | 每宿主 graph creation test；禁止 Provider fallback |
| 模块过细导致 Gradle/开发负担 | 两模块总是一起变更 | 合并为同一业务域；不为目录图保留空 API/impl |
| `core:data/platform/ui` 再次长成倾倒区 | 新文件无明确 owner | gate 阻止新增；按业务域迁出后删除过渡模块 |
| Android 行为在多端重构中回退 | 任何垂直切片 | Android route/adapter 是回滚点；G0/G5 不通过不合并 |

## 8. 迁移完成清单

- [ ] Android、Desktop、iOS 三个宿主均能 clean build/test/package。
- [ ] 所有正式 Feature 是 CMP 或有书面、经产品批准的平台专属理由。
- [ ] `domain:*` 无 Compose/Room/Ktor/Koin/platform 类型。
- [ ] Feature 无 DAO/network/file/settings 直连。
- [ ] Room entity/DAO 不作为 domain API，不含需全局依赖的 I/O/业务行为。
- [ ] 生产源码无内部 `io.legado.app.help.*`、`utils.*`、`base.*` owner。
- [ ] 无 `XxxProvider.current`、可变全局 delegate、`appCtx/appDb/GSON` 入口。
- [ ] `core:data`、`core:platform`、`core:viewmodel` 等过渡聚合模块已拆解并删除。
- [ ] CMP resources、KMP ViewModel、Nav3 state/lifecycle 在三端有验证。
- [ ] Room migration/backup、网络/cookie、书源 corpus、reader/TTS/服务门禁通过。
- [ ] 所有 capability 显式说明 available/unavailable；无成功 no-op。
- [ ] PoC 模块已删除或仍有明确未知项与删除条件。
- [ ] CI、模块图、Feature catalog、target matrix 与源码一致。
