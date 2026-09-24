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
| `BigDataStoreProvider` ✅ M2-2 | 实现**下沉共享层**（`RuleDataFileStore`），经平台原语 `expect object RuleDataStorage` 拿文件 IO | 路径布局与迁移前逐字节一致（硬编码 MD5 向量钉住）；Provider 删除，`entities` 区域计数 45 → 36 |
| `ImportJsonEditorProvider` ✅ M2-1 | 公共组件的能力走**参数注入**（调用方传入） | 漏传即编译错误；desktop 侧显式 unsupported |
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
     基线 `gradle/architecture/legacy-baseline.txt`（目录级，M1-3b 后 323 条）；
     门禁 `checkLegacyArchitecture`（挂 `assemble`/`compile`）；重新冻结脚本
     `tools/generate-legacy-baseline.py`。Feature→DAO 一类沿用 `verifyConfigArchitecture` 的
     DAO 基线，未重复造轮子。
   - 实测补充：`app/main/io/legado/app/di` 的 `legacyHelp` 基线（5）意味着 **`di` 不能再 import
     `io.legado.app.help.**`**。新增平台能力适配要放到 legacy 区之外的包（见 M1-3a 的
     `io.legado.app.platform`），否则会被这条棘轮拦下。
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
   - 按「一次只改一个风险维度」拆成 a/b 两片。
   - **M1-3a 已完成（2026-09-10）**：去静态平台入口。两个规则 VM 改为构造注入
     `Clipboard` / `Toaster`，`GSON` 门面换 `JsonCodec` 契约；`Clipboard`/`Toaster` 的
     Android 适配搬到 `io.legado.app.platform.AndroidPlatformCapabilities`（避开 `di` 的
     help import 棘轮），`appModule` 增加对应的 `single<>` 绑定。tagrules `main` 源集的
     `gson` 与 `coreProvider` 计数归零，基线同步删 4 条。
     验证：四门禁 + 14 用例（含变异验证）+ `:core:platform:desktopTest` +
     `:app:compileAppDebugKotlin` 全绿。细节见
     [feature-slicing-audit-tagrules.md](./feature-slicing-audit-tagrules.md) §9。
   - **M1-3b 已完成（2026-09-10）**：`Application` / `R` / `BaseRuleViewModel` 退出两个 VM。
     共享编排抽成无 UI 的 `RuleTransferUseCase` + `RuleEntitySpec`（M1 明确「不再抽基类」；
     `BaseRuleViewModel` 留给其余 5 个子类），两个 VM 变普通 `androidx.lifecycle.ViewModel`。
     为了不撞 `legacyBase` 棘轮（只降不升、新区域必须为零），规则导入导出的共享层
     （`RuleTransferPlatform` / `BuiltInRulesImporter` / `RuleTransferUseCase` / `RuleTransferEvent`）
     整体搬到 `io.legado.app.core.rules`——`tagrules` 的 `legacyBase` 因此从 2/3 归零。
     app 侧的两个 Android 实现仍留在 `app/.../base/rules/`（report-only 区，动目录会把
     `help.*` 带进新区域），只加一行指向新包的 import。
     验证：四门禁 + 45 用例（core:viewmodel 19 / tagrules 14 / designsystem 12）+ 变异验证 +
     `:app:compileAppDebugKotlin` 全绿；基线 326 → **323** 条。细节见
     [feature-slicing-audit-tagrules.md](./feature-slicing-audit-tagrules.md) §10。
   - **M1-3c 已完成（2026-09-10）**：文件选择能力契约化。新增 `:core:platform` 的
     `DocumentPicker`（`openDocument` / `createDocument`，结果用**不透明引用字符串**而非 `Uri`）+
     `:core:ui` 的 `rememberDocumentPicker()`（SAF 实现）。launcher 必须在 Composition 里注册，
     所以由 Route 持有并只向 Screen 暴露回调；Screen 里的 `rememberLauncherForActivityResult` ×2、
     `LocalContext`、`contentResolver.openInputStream` 全部删除（读取交给
     `RuleTransferPlatform.readImportSource`，`Uri.readText` 与之语义等价）。
     验证：四门禁 + 46 用例（core:viewmodel 19 / tagrules 15 / designsystem 12）+ 变异验证 +
     `:app:compileAppDebugKotlin` 全绿；基线维持 **323**。
     细节见 [feature-slicing-audit-tagrules.md](./feature-slicing-audit-tagrules.md) §11。
   - **③ 仍未完成（下一个切片）**：Screen/VM/Contract 进 `commonMain` 还差——
     `FilePickerSheet`（`:core:ui`，内含 `android.webkit.MimeTypeMap`）、
     以及 Screen/Sheet 的 `R.string.*`（26 键）与 `LocalClipboard` 换 CMP 资源/契约。
     ~~`AppModalBottomSheet` 的 CMP material3 expressive API~~ **已由 M1-3g 解除，M1-3h 已搬进
     designsystem**。组件原子件（按钮/分割线/进度条）已由 **M1-3i** 整族搬走，
     故 Screen 侧剩下的阻塞已收敛为**平台契约类**（文件选择、剪贴板、字符串资源）而非组件。
     之后模块才能登记为 `cmp`。Android route 是回滚点。

6. **M1-3d / M1-3e / M1-3f / M1-3g / M1-3h / M1-3i：主题层、组件原子件与 material3 pin（2026-09-10 完成，为 ③ 清路）**
   - **起因**：把 ③ 拆解时发现 `LegadoTheme` 是所有组件的必经依赖（`OptionSheet` /
     `AppModalBottomSheet` / `VerticalFastScroller` / `AppText` / `AppIcon` 全都指向它），
     不先搬它，`FilePickerSheet` 与任何组件都动不了。
   - **先做制品可用性验证（checklist 要求 unknown 必须先 PoC）**：`LegadoTheme.kt` 依赖的四个
     第三方库此前都是「未知」。查 Maven Central 的 `.module` 元数据后**全部有非 Android 变体**：
     haze→`haze-jvm`、material-kolor→`material-kolor-jvm`、kyant0:backdrop→`backdrop-desktop`、
     miuix→`miuix-*-desktop`。**踩坑：不能拿本地 Gradle 缓存反推**——Android-only 构建的缓存里
     只会出现 `-android` 制品，看着就像没有 jvm 变体；判据必须是上游元数据。
   - **实测各文件 Android 依赖密度**（推翻旧结论）：`ui/theme` 23 个文件里只有 4 个带 `android.*`
     （`ImageSeedColorExtractor` / `ThemeComponents` / `ThemeEngine` / `ThemeSeedColorProvider`）；
     常见组件（`AppAlertDialog` / `AppText` / `AppIcon` / `LazyList` / `OptionSheet` /
     `RoundDropdownMenuItem` / `SmallPlainButton`）都是 0 个 `android.*`。
     `:core:ui/build.gradle.kts` 里「这套主题与组件大量使用 Context/Bitmap/Uri，只能落在 Android 侧」
     的理由属于 `composeMain` 时代（该约束现在只对 `pure`/`data` 模块成立），已就地修正。
   - **切片**：`theme/LegadoTheme.kt`（自包含、0 个 `android.*`、184 行）整体移入
     `:core:designsystem/commonMain`，**hash 逐字节一致**。包名 `io.legado.app.ui.theme` 不变 →
     消费方 import 零改动；已核对 `app` / `core:ui` / `feature:tagrules` / `feature:replacerules`
     四个使用方都已显式声明 `:core:designsystem`（否则会因 `implementation` 不暴露而断）。
     designsystem 侧补 `haze-core` / `material-kolor` / `backdrop` 三个坐标
     （`ColorSchemeMode` 来自已依赖的 `miuix-ui`，不在 `miuix-core`——与 `basic.Switch` 同一个坑）。
   - **验证**：`:core:designsystem:compileKotlinDesktop` 编译通过（**真实非 Android 目标证据**，
     并确认 `haze-jvm` / `material-kolor-jvm` / `backdrop-desktop` 三个制品被实际解析下来）；
     四门禁全绿（legacy 基线维持 323）；**681 用例 0 失败**
     （app 635 / core:viewmodel 19 / tagrules 15 / designsystem desktop 12）；
     `:app:compileAppDebugKotlin` 通过。
   - **M1-3d 回滚点**：把 `LegadoTheme.kt` 移回 `:core:ui` 并撤掉三个坐标即可，无调用方改动。
   - **M1-3e 已完成（2026-09-10）**：原子组件的直接依赖闭包再搬 6 个文件进 `commonMain`——
     `AppThemeMode` / `ThemeColorSpec` / `ThemeResolver` / `AppContentColor` /
     `LocalAppUiConfiguration` / `AppDensity`，其中 5 个 **hash 逐字节一致**（纯搬动）。
     唯一行为相关改动是 `AppDensity`：Android-only 的 `LocalConfiguration.current.fontScale`
     换成**同源**的 `LocalDensity.current.fontScale`（两者都由同一个 `context.resources` 派生，
     论证与源码依据见 [cmp-module-convention.md](./cmp-module-convention.md) §3）。
     另新增 `AppFontScaleTest`（3 用例）钉住 0.8~1.6 区间与越界回落，避免后续再动它时静默改字号。
     验证：`:core:designsystem:compileKotlinDesktop` + 四门禁 + **684 用例 0 失败**
     （app 635 / core:viewmodel 19 / tagrules 15 / designsystem 15）。
   - **M1-3f 已完成（2026-09-10）**：组件原子件第一块。`AppText` / `AppIcon` / `AppIcons`
     整体搬入（hash 逐字节一致，`miuix-icons` 按**不带 `-android` 后缀**的 KMP 坐标补进
     designsystem——`MiuixIcons` 与 `icon.extended.*` 只在它里面，不在 `miuix-ui`/`miuix-core`）；
     `NormalCard` 从 `GlassCard.kt` 拆出，与它的底层表面一起进共享层（新 `card/AppCardSurface.kt`）。
     - **关键取舍 1（卡片基元的 seam）**：`GlassCard` 的「条目背景层」依赖
       `BitmapFactory`/`NinePatch`/Coil（Android）。没有把它抽象成布尔开关，而是建模为
       `AppCardSurface(itemBackground: Modifier?)` 这个**可空 modifier 槽**：`null` = 不要背景层
       （`NormalCard`），非 `null` = 在内容下叠一层 `matchParentSize` 并应用它（`GlassCard` 传
       `Modifier.appContainerBackground(Item)`）。共享层因此不认识平台类型，`:core:ui` 的
       `GlassCard` 也不必复制一份卡片表面；分支与搬动前逐字等价。
     - **关键取舍 2（唯一定义）**：圆角/描边的主题覆盖决议抽成纯函数 `resolveCardDecoration`
       （入参 `ThemeSettings` + `isDark`，不读 CompositionLocal），`NormalCard` 与 `GlassCard`
       共用它，避免两份会漂移的覆盖语义；纯函数也让 `CardDecorationTest`（5 用例）不用 Compose
       runtime 就能钉住语义，并做了**变异验证**（翻转 `isDark` 分支 → 2 用例精确变红）。
     - **两个真阻塞**：
       ① `androidx.compose.material.ExperimentalMaterialApi` 属 **material2** 制品，designsystem
       没有它 → 搬文件时**连带删掉该 `@OptIn`**，不许为它顺手加 material2 依赖；
       ② `AppModalBottomSheet` 用的 CMP material3 **expressive 系列**（`ExperimentalMaterial3ExpressiveApi`
       / `MaterialExpressiveTheme` / `MotionScheme` / `rememberBottomSheetState` /
       `BottomSheetDefaults.modalWindowInsets`）当时编不过 → 它**未搬**，与 `OptionSheet` 一起留在
       `:core:ui`，`LocalUseMiuixWindowPopup` 也据此原地不动（避免"借任务重构无关代码"）。
       ⚠️ **当时的归因写错了**（写成"只有 android 变体有"）——实为**版本问题**：符号都在
       `commonMain`，只是 1.9.0 把它们标成了 `internal`。已由 **M1-3g** 解除，两个文件不再阻塞。
     - 验证：`:core:designsystem:compileKotlinDesktop` + `desktopTest`（designsystem 15 → **20** 用例）
       + `:app:compileAppDebugKotlin` + 四门禁全绿；legacy 基线维持 **323**。
       回滚点：把 4 个文件移回 `:core:ui` 即可，无调用方 import 或签名改动。
   - **M1-3g 已完成（2026-09-10）：CMP material3 升到 1.12.0-alpha03，解除 expressive 阻塞。**
     - **纠错**：M1-3f 把 expressive 编不过归因为「只有 android 变体有」是**错的**。实证（解上游
       `-sources.jar`）：符号就在 CMP material3 的 `commonMain`，android / desktop / ios / js /
       wasm / macos 变体齐全；**1.9.0 把它们声明为 `internal`**，故跨模块报
       `Cannot access '…': it is internal in file`（`rememberBottomSheetState` / `modalWindowInsets`
       在 1.9.0 则压根不存在）。**这是版本问题，不是平台问题。**
     - **实测放开时间线**：`MaterialExpressiveTheme` / `MotionScheme` /
       `@ExperimentalMaterial3ExpressiveApi` 自 **1.10.0-alpha05** 转 public，`modalWindowInsets`
       自 **1.11.0-alpha07**，`rememberBottomSheetState` 自 **1.12.0-alpha03** → 取
       **1.12.0-alpha03**（满足全部 expressive 需求的最低版本）。
     - **做法**：`composeMultiplatformMaterial3` 改 1.12.0-alpha03；convention 的
       `assertComposeVersionsInSync` 把 material3 的期望值从「插件常量」换成**显式登记的
       `MATERIAL3_PIN`**，并新增「pin 不得低于插件常量」的断言——**防漂移能力不丢**，只是登记处
       从插件常量换成带理由的常量。`composeMultiplatform` 那条仍严格等于插件常量。
     - **连带风险已实测**：`material3-android:1.12.0-alpha03` 只委托
       `androidx.compose.material3:1.5.0-alpha22`，低于本仓锁定的 **1.5.0-alpha23**，Gradle 取高版本
       → **Android 侧不降级**；skiko 统一到 `0.150.1`。
     - **解锁面**：`AppModalBottomSheet` / `OptionSheet`；`ThemeComponents.kt` 的
       `MaterialThemeWrapper`（用的是同一组 expressive 符号）——即「`AppTheme` 全栈进 `commonMain`」
       的前置；以及 `AppAlertDialog` 等一批此前**被三分法欠判**的 `:core:ui` 组件（脚本看不见可见性）。
     - 验证：desktop + Android 双目标编译、`desktopTest`、`testAppDebugUnitTest`（0 失败）、四门禁、
       `assembleAppDebug` 全绿；**变异验证**——把 toml 改回 1.9.0 而不同步 pin → 配置期精确报
       `CMP 版本漂移` 并失败。
     - 回滚点：toml 改回 1.9.0 + convention 恢复即可，**无源码改动**。
   - **M1-3h 已完成（2026-09-10）：弹层 `AppModalBottomSheet` + `OptionSheet` 进 `commonMain`。**
     - **搬法**：`git mv` 两个文件到 `:core:designsystem` 的**同一包名**
       `io.legado.app.ui.widget.components.modalBottomSheet` ⇒ 85 个带显式 import 的消费文件
       （`:app` 77 / `:core:ui` 6 / `:feature:tagrules` 2）**import 语句零改动**，三个模块本来
       就已依赖 `:core:designsystem`。
     - **连带依赖——`LocalUseMiuixWindowPopup` 要有个共享的家**：它原本声明在 `:core:ui` 的
       `menuItem/RoundDropdownMenu.kt` 里，而 `AppModalBottomSheet`（Miuix 分支）要 provide 它。
       `:core:ui` → `:core:designsystem` 是单向的，反向不可行，故把**声明**下沉到 designsystem 的
       **同名包** `widget/components/menuItem/`。`RoundDropdownMenu.kt` 留在 `:core:ui`（它经
       `rememberOpaqueColorScheme` → `ThemeEngine` → `Context` 仍带 `android.*`），同包引用无需
       import。**跨模块同包可见性在本仓已有先例**（`core:ui` 的 `ui/theme` 直接引用本模块的
       `LocalLegadoThemeColors` / `LocalAppUiConfiguration`），不是新机制。
     - **补一个显式依赖**：`AppModalBottomSheet` 用 `Modifier.animateContentSize`——它在
       `org.jetbrains.compose.animation:animation`（不是 foundation）。foundation 会传递带上，但按
       「依赖只列实际用到的」显式加别名 `compose-multiplatform-animation`（源集与 runtime/foundation
       同在 1.12.0 线）。
     - **搬前复核的三个传递闭包维度**（M1-3f 的教训固化）：① 文件自身 0 个 `android.*`；
       ② 其引用闭包（`LegadoTheme` / `LocalLegadoThemeColors` / `ProvideAppDensity` /
       `ProvideAppContentColor` / `ThemeResolver` / `NormalCard` / `AppIcon` / `AppText`）都已在
       designsystem；③ **依赖库里用到的 API 在非 Android 源集里存在**——`WindowBottomSheet` 位于
       miuix 的 `commonMain`（查 sources jar 实为 `commonMain/top/yukonga/miuix/kmp/window/`，
       desktop 变体存在），expressive 系列由 M1-3g 的 pin 保证。
     - 验证：desktop 编译、`testAppDebugUnitTest`、四门禁、`:app:compileAppDebugKotlin`；跨模块搬文件
       按规程**删 `app/build` + `core/*/build` 干净重建**（否则增量编译会假绿）。
   - **M1-3i 已完成（2026-09-10）：原子组件整族搬进 `commonMain`（25 个文件）。**
     - **范围**：`button/`（`AppButton` / `AppIconButton` / `ConfirmDismissButtonsRow` / `ToggleChip`）、
       `button/series/`（`AnimatedActionButtonCore` / `AnimatedIcon` / `Medium*` ×5 /
       `Small*` ×5 / `SeriesIconButton`，共 13）、`divider/`（`PillDivider` /
       `PillHeaderDivider` / `SettingItemDivider`）、`progressIndicator/`（`AppCircular*` /
       `AppContainedLoadingIndicator` / `AppLinear*`）、`title/SmallTitle.kt`、`SectionTitle.kt`。
       全部 `git mv` 到 `:core:designsystem` 的**同一包名**（`io.legado.app.ui.widget.components.**`）
       ⇒ 本模块内文件与 `:app` / `:feature:*` 的 import **零改动**。
     - **候选筛选用两条机械判据**（脚本扫 `:core:ui` 全部 117 个 Compose 文件）：
       ① **文件内**零 `android.*`、零 `R.`、零 Android-only Compose API——注意后一类**不是**
       `android.*`，正则要显式列出（`LocalContext` / `LocalConfiguration` / `LocalInspectionMode` /
       `androidx.compose.ui.res.*` / `stringResource` / `painterResource` / `AndroidView` /
       `viewinterop` / `LocalClipboard*` / `LocalUriHandler`）；② **引用闭包**全部落在 designsystem。
       76/117 个文件过第 ① 条，本族过第 ② 条（唯一跨模块边是 `ToggleChip → card.NormalCard`，
       已在 `AppCardSurface.kt`）。
     - **同类被排除的**（提醒别误判）：`checkBox/CheckboxItem.kt` 自身干净，但它依赖 `GlassCard`，
       而 `GlassCard` 的 `Modifier.appContainerBackground` 走 `BitmapFactory`/`NinePatch` 读九宫格
       ——**判据必须看闭包，不能只看文件**；`EmptyMessage` / `AppPullToRefresh` / `GroupManageBottomSheet`
       等 40 个文件则卡在 `R.string.*`。
     - **第三方依赖零新增**：本族只用到 Miuix（`basic`/`theme`，已依赖）与
       `androidx.compose.material.icons`（`materialIcons` 已声明，且 `Check` 属 icons-core）。
       `:core:ui` 侧反而核查了一遍是否有依赖变成死代码——`miuix.icons.android` /
       `compose.materialIcons` / `reorderable` / `capsule` 都仍有本模块内的使用方，无需删。
     - 验证：`:core:designsystem:compileKotlinDesktop` 通过 + 四门禁 + 全量单测 + `assembleAppDebug`
       （干净重建）。新增显式依赖时按「依赖只列实际用到的」判断，本切片**无需改任何 build.gradle.kts 依赖**。
   - **M1-3j 已完成（2026-09-11）：CMP 多平台资源打通，`R.string.*` 不再是搬件阻塞。**
     - **决策**：共享组件的文案走 CMP 的 `org.jetbrains.compose.resources`
       （`commonMain/composeResources/values*/` + 生成的 `Res.string.*`），而不是「宿主传文案」
       或薄 `StringProvider` 契约。依赖 `org.jetbrains.compose.components:components-resources`，
       ref `composeMultiplatform`（与插件严格同线）。
     - **convention 两处改动**：① 从模块 path 推导 `packageOfResClass`
       （`:core:designsystem` → `io.legado.app.core.designsystem.res`，必须显式设——项目没有
       `group`，CMP 默认包名会退化）；② CMP 模块的 `androidLibrary` target 开
       `androidResources.enable = true`（AGP 默认不启用，不开则 Android 运行期读不到 assets 里的
       `.cvr`）。`resources` 是 `ComposeExtension` 的**子扩展**而非属性 ⇒ 只能
       `extensions.configure<ResourcesExtension>` / 模块侧 `compose.resources { }`。
     - **⚠️ 语义变化**：`composeResources` 打进 **assets**，**不参与 Android 资源合并**
       ⇒ app 侧同名 `strings.xml` **覆盖不到**它，共享层必须自带全部支持语言目录
       （`values` + `values-zh-rCN` + `values-zh-rHK` + `values-zh-rTW`）。
       值与 `:core:ui` 同名资源逐字节核对，**无用户可见变化**。
     - **实际搬入 3 个**：`AppFloatingActionButton.kt` / `ReorderAccessibility.kt` /
       `reader/ReaderMenuActionSquare.kt`。**另 4 个已退回 `:core:ui`**
       （`SearchBar` / `topbar/TopBarButton` / `ReorderableConfigList` / `player/PlayerTocPage`）：
       除 `R` 外还引用留在 `:core:ui` 的**同包兄弟**（`AppDenseTextField` /
       `GlassTopAppBarDefaults`）或平台库（coil3 / `sh.calvin.reorderable` /
       `kotlinx.collections.immutable`）⇒ **不是纯资源阻塞**。
       教训：**静态 import 扫描看不见「不带 import 的同包引用」，只有 desktop 编译能判**。
     - 机制、convention 改法、生成物位置、脚本坑（必须保 CRLF + 先删旧 import 再插新 import）
       见 `docs/dev/cmp-module-convention.md` §7；切片实录见
       `feature-slicing-audit-tagrules.md` §13。
     - 验证：`:core:designsystem:compileKotlinDesktop` + `compileAndroidMain`、四门禁、全量单测
       （基线 689）、`:app:assembleAppDebug`（干净重建）。
   - **M1-3k 已完成（2026-09-11）：§12 剩余「可直接机械搬」收敛，台账清零。**
     搬入 `alert/AppAlertDialog.kt`、`AppTextField.kt`、`DraggableSelectionHandler.kt`、
     `menuItem/RoundDropdownMenuItem.kt`（零新增依赖）；退回 `lazylist/LazyList.kt`
     （同包兄弟 `VerticalFastScroller.kt` 用了 Android-only 的 `Modifier.systemGestureExclusion()`。
     **该阻塞已由 M1-3v 解除**：那个 API 下沉为 `expect/actual` 原语后，`lazylist/*`
     两个文件已搬进 `commonMain`）。
     `portability-triage.py` 补两处结构性盲区：**同包兄弟边**（同包顶层声明不需 import，
     原闭包扫描看不见）与**种子结论**（闭包内任一成员 HARD ⇒ 种子搬不动）。实测记录于
     `cmp-module-convention.md` §3「foundation 侧的 Android-only 成员」。
   - **M1-3l 已完成（2026-09-11）：配色平台契约，theme 引擎进 `commonMain`（17 文件 + 1 契约）。**
     - **动机**：`SelectionItemCard` / `SelectionBottomBar` / `RuleListScaffold` 原判「只因 `R`
       卡住」是**欠判**——闭包都经 `theme/OpaqueColorScheme.kt` → `ThemeEngine` →
       `android.content.Context`/`android.os.Build`。该主题链同时卡住 `menuItem/RoundDropdownMenu.kt`，
       做一片可解锁多个。
     - **搬入**（`git mv`，包名不变）：`theme/{BaseColorScheme,CustomColorScheme,ThemeEngine,
       OpaqueColorScheme}.kt` + `theme/colorScheme/*.kt` ×12 → `:core:designsystem/commonMain`。
       **零新增依赖**（material3 / material-kolor / `:core:model` 已在）。
     - **契约**：两处 `android.*` 抽成单方法 `fun interface` —— `ThemeSeedColorProvider.primaryColor()`
       （去掉了原先的 `context` 形参）与 `DynamicColorSchemeProvider.colorScheme(darkTheme)`；
       Android 实现落在 **app**（回退主色要 `lib.theme.ThemeStore`，`:core:ui` 依赖不到），
       由 `App.onCreate` 的 `installAndroidThemePlatform(this)` 注入。
     - **⚠️ 语义风险 ≈ 0，且是实测出来的**：改造前逐个调用点查「上下文是否真参与计算」——
       `ThemeSeedColors.primaryColor(context)` 在**所有**调用点都不可达（三个调用点传非空 `Int`；
       `OpaqueColorScheme` 恒 `forceOpaque = true` ⇒ `Transparent` 归一到 `WH`）。两个差异都只是
       「Activity context → Application context」，前者对系统调色板 / `ThemeStore` 默认值均无影响。
     - **失败语义按「缺失是否合理」区分**：回退主色未注入 ⇒ `requireNotNull` 抛异常（配置错误）；
       动态取色未注入 ⇒ 返回 `null` 回落预定义配色（desktop/iOS 本就没有系统调色板，是正常状态）。
     - **踩到的坑**：G4 `checkLegacyArchitecture` 把**新区域**（`app/main/…/ui/theme`）首次出现的
       `import splitties.init.appCtx` 判为 blocking ⇒ 改为 `Context` 显式传参，**不要**往基线登记。
       另修 `portability-triage.py` 的**同名不同包**误判（CMP 的 `stringResource` vs androidx 的），
       改为 import 溯源。
     - **解锁**：`menuItem/RoundDropdownMenu.kt` 已搬入；`SelectionBottomBar` 只剩 `R`；
       `SelectionItemCard` / `RuleListScaffold` 另有真实阻塞（九宫格、`RuntimeShader`）。
     - 手法与门禁细节见 `cmp-module-convention.md` §8；切片实录见
       `feature-slicing-audit-tagrules.md` §15。
   - **M1-3m 已完成（2026-09-11）：`SelectionBottomBar.kt` 进 `commonMain`（CMP 资源配方首次复用）。**
     `git mv` 进 `:core:designsystem/commonMain`（包名不变 ⇒ 消费方 import 零改动），
     `R.string.*` → `Res.string.*`；`composeResources/values*/strings.xml` 各补 3 条
     （`select_all` / `invert_selection` / `more_menu`，值与 `:core:ui`、`app` 两侧逐字节核对
     ⇒ 无用户可见变化）。**零新增依赖**。顺带实证 `WindowInsets.navigationBars` /
     `.tappableElement` / `.ime` / `.union` / `windowInsetsPadding` 在 desktop 均有实现
     （M1-3k 的 foundation 正反清单）。
     - **验证**：干净重建，四门禁 + `compileAppDebugKotlin` + 全量单测（基线 689 逐字一致）
       + `assembleAppDebug` 全绿。
     - ~~新工具坑：`Edit` 会把文件转成 LF、必须转回 CRLF~~ **已于 M1-3n 实测推翻**：
       `core.autocrlf=true` 下 blob 一律存 LF，工作区行尾（LF / CRLF / MIXED）既不影响 blob
       也不产生内容 diff。见 `cmp-module-convention.md` §7.6。
   - **M1-3n 已完成（2026-09-11）：`FilePickerSheet.kt` 进 `commonMain`，MIME 推断下沉为窄契约。**
     - **两个阻塞分开做**：6 条 `R.string` 走 CMP 资源（4 语言，值与 `:core:ui`、`app` 两侧
       逐字节核对 ⇒ 无用户可见变化）；`android.webkit.MimeTypeMap` 下沉为 `:core:platform`
       的 `fun interface MimeTypeResolver` + `MimeTypeResolverProvider`（install / uninstall /
       isInstalled / current）。Android 实现在 app 的
       `io.legado.app.platform.AndroidPlatformCapabilities.mimeTypeResolver()`，由
       `PlatformServices.install()` 注入——与 `clipboard` / `toaster` 同一组工厂、
       **不需要 `Context`**（`MimeTypeMap` 是进程级单例）。
     - **契约面是先量清输入域再定的**：22 个调用点的 `allowExtensions` 只有 `null` /
       `arrayOf("json")` / `arrayOf("json", "txt")` 三种，真正走平台查表的只有 `"json"`
       （`*` / `txt` / `xml` 都是特判）。这一步排除了「把扩展名表抄进共享层」那个看起来
       更省事的选项——它在今天的调用点上等价，但明天多传一个扩展名就会静默退化成
       `application/octet-stream`（Android 原本认得）。
     - **失败语义**沿用 M1-3l 的判据：未注入 ⇒ 「一律 `null`」⇒ 调用方回落
       `application/octet-stream`，与 Android「未知扩展名」同一条路径。
       desktop 没有 MIME 表是**正常状态**，不抛异常。
     - **新增模块依赖 1 条**：`:core:designsystem` → `:core:platform`（方向无环，G1/G2 通过）。
     - **首例 characterization 测试带来的基线变动**：`typesOfExtensions` 提为 `internal`，
       新增 `FilePickerSheetMimeTest`（10 用例，desktopTest）并做**变异验证**（改回落值 ⇒
       2 个用例变红）。**主验证集 689 → 699**（designsystem 20 → 30），不是回归。
     - **踩到的坑**：① Kotlin 块注释**可嵌套**——KDoc 里写 MIME 通配字面量会同时引入
       `*/`（提前闭合）与 `/*`（再开一层），编译报一片 `Expecting a top level declaration`；
       同名序列在**字符串字面量**里合法（见 `cmp-module-convention.md` §7.9）。
       ② 向 `strings.xml` 追加条目要插在**已有 `</resources>` 之前**，否则双闭合标签。
     - 实录见 `feature-slicing-audit-tagrules.md` §17。
   - **M1-3o 已完成（2026-09-11）：`plainTextClipEntry` 进 `commonMain`，`ClipEntry` 构造下沉为窄契约。**
     - `git mv` `core/ui/.../ui/util/PlainTextClipEntry.kt` → `:core:designsystem/commonMain`
       （**包名 `io.legado.app.ui.util` 不变** ⇒ 9 个文件、14 处调用点**一行没动**）。
     - **为什么不能按原判「换 `ClipboardProvider`」**：那个 `setText` 会 `longToastOnUi` 弹一次
       「复制完成」，而这 14 处全是 Snackbar 的「复制链接」动作 ⇒ 换过去是**行为变化**。
     - **为什么必须抽契约**：`ClipEntry` 是 CMP 的 `expect class`，common 侧**无构造器**
       （Android 要 `ClipData`、desktop 要 AWT）。`LocalClipboard` / `setClipEntry` 本身在
       common 就有，卡住的只有构造这一步。旧 API `ClipboardManager` 已弃用，不采用。
     - 契约 `PlainTextClipEntryFactory` + `PlainTextClipEntryProvider`（install / uninstall /
       isInstalled / current）**住在 `:core:designsystem`**——`:core:platform` 零 Compose
       装不下。**首个「契约住在 Compose 模块」的先例**，见 `cmp-module-convention.md` §8.5。
     - 失败语义 = **抛异常**（判据同 `ClipboardProvider`：用户主动动作，没接上就该炸，
       不能静默 no-op）。Android 实现在 `AndroidPlatformCapabilities.plainTextClipEntryFactory()`，
       由 `PlatformServices.install()` 注入，**不需要 `Context`**。
     - 新增 `PlainTextClipEntryTest`（3 用例，desktopTest）：未注入 ⇒ 抛异常、install/uninstall
       同步、参数透传（哨兵异常技巧——共享层造不出 `ClipEntry`，成功路径无法断言）。
       **主验证集 699 → 702**（designsystem 30 → 33）；变异验证：调换实参 ⇒ 精准 1/3 变红。
     - 实录见 `feature-slicing-audit-tagrules.md` §18。
   - **M1-3p 已完成（2026-09-11）：九宫格下沉为窄契约，`GlassCard` / `AppCheckbox` / `CheckboxItem` 进 `commonMain`。**
     - `git mv` 4 个文件进 `:core:designsystem/commonMain`（**包名一律不变** ⇒ `app` / `core:ui` /
       `feature:*` 的 import 一行没动；`GlassCard` 被 59 个文件引用）：
       `widget/components/AppContainerBackground.kt`（**改写**）、`card/GlassCard.kt`、
       `checkBox/AppCheckbox.kt`、`checkBox/CheckboxItem.kt`。
     - **契约面**：`:core:platform` 新增 `NinePatchLoader { fun load(path: String): Any? }`
       + `NinePatchLoaderProvider`。返回类型是**不透明 payload**（就是图像加载器 `data` 形参的
       类型），因此**可以**住零 Compose 的 `:core:platform`——与 M1-3o 的 `ClipEntry`（CMP
       `expect class`，只能住 designsystem）恰好构成一组对照：**契约住哪个模块由契约的返回类型
       决定**。Android 实现是 `AndroidPlatformCapabilities.ninePatchLoader()`（**逐字搬运**原私有
       `loadNinePatch`，含 `.9.png` 预筛与 `runCatching`），由 `PlatformServices.install()` 注入。
     - **失败语义 = 一律 `null`**（判据同 `MimeTypeResolver`）：「这个平台没有九宫格」是正常状态。
       三种 `null`（非 `.9.png` / 解析失败 / 无平台能力）收敛成**同一条回落路径**——按原路径交给
       Coil。唯一行为差异：desktop 上背景图按普通位图缩放，不按九宫格拉伸。
     - **没有把「整层绘制」下沉**（契约返回 `Modifier` 或 `Painter` 两条路都量过）：前者要
       `@Composable` 契约方法、且会把跨平台的 Coil 管线整段推进 `app`；后者要光栅化九宫格并丢掉
       Coil 的 crossfade/缓存，是**行为变化**。只切真正平台相关的那一步。
     - **搬动时换掉的两处平台绑定**：`LocalContext` → coil3 的 `LocalPlatformContext`（Android 上
       同一个 `Context`）；`LocalConfiguration.screenWidthDp/HeightDp` → CMP 的
       `LocalWindowInfo.containerSize`（只作 Coil 解码目标尺寸；补了「尺寸为 0 时不设显式请求」
       的守卫）。另**删掉 `koinInject<ImageLoader>()`**——`App` 的 `newImageLoader(context) = get()`
       就是这个 Koin 单例，走 `SingletonImageLoader` 拿到的是同一实例 ⇒ **designsystem 不必引入
       Koin**。`designsystem` 新增 1 个依赖：`libs.coil.compose`（KMP，有 `coil-compose-jvm`）。
     - **踩坑**：`git mv` 不会替你建目标父目录（搬进 designsystem 里尚不存在的 `checkBox/` 时报
       `renaming … failed: No such file or directory`，先 `mkdir -p`）；`GlassCard` 上历史遗留的
       material2 `@OptIn(ExperimentalMaterialApi::class)` 是死注解，按 M1-3f 的处理删掉。
     - **新增 `NinePatchLoaderContractTest`（3 用例，desktopTest）**：未注入 ⇒ 一律 `null`、
       install/uninstall 同步、路径原样透传。主验证集 **702 不变**（本片没动可测语义），
       `:core:platform:desktopTest` 76 → 79 ⇒ 全量 **862**。变异验证：兜底改成返回空串 ⇒
       2/3 精确变红。
     - 实录见 `feature-slicing-audit-tagrules.md` §19。
   - **M1-3q 已完成（2026-09-11）：`SelectionItemCard` 进 `commonMain`，CMP 资源配方第三次复用。**
     - `git mv` `widget/components/card/SelectionItemCard.kt` 进 `:core:designsystem/commonMain`
       （包名不变 ⇒ **26 个引用文件的 import 一行没动**；它承载 `SelectionItemCard` /
       `SelectionItemCardContent` / `ReorderableSelectionItem` 三个组件）。
     - **拆开后的两条阻塞其实都不算阻塞**：`R.string.edit` 是纯资源问题（走 M1-3j 配方，
       按 `:core:ui` 的原值补进 4 个语言文件）；`sh.calvin.reorderable` 实测**是 KMP 制品**
       （`reorderable-3.1.0.module` 含 `reorderable-jvm` 变体）。⚠️ 之前 §12 把它与 coil3 一并
       当作「平台库」是连带误判——判据只有一条：**`.module` 里有没有非 Android 变体**。
     - `:core:ui` 的 `edit` 资源与 `libs.reorderable` 依赖**都保留**（`ReorderableConfigList`、
       `GroupManageBottomSheet`、`InputSettingItem`、`SliderSettingItem` 仍在用）。
     - **踩坑**：`ListItem(modifier = …, supportingContent = …, colors = …) { AppText(title) }`
       的括号外 lambda 在 **Android 目标能编译**、在 **desktop 目标**报
       `No value passed for parameter 'headlineContent'`（`headlineContent` 在签名里是第一个
       参数）。改成显式命名参数 `headlineContent = { … }`，两边都成立且语义不变。
     - 实录见 `feature-slicing-audit-tagrules.md` §20。
   - **M1-3r 已完成（2026-09-12）：topbar 全家桶进 `commonMain`，两条平台契约。**
     - `git mv` 11 个文件（topbar ×6、`theme/GlassDefaults` + `HazeStyle` + `hazeStyle/HazeLegado`、
       `SearchBar`、`text/AnimatedText`）进 `:core:designsystem/commonMain`，包名不变 ⇒
       消费方 import 零改动。补齐 7 条文案到 4 个语言文件（与 `app` 原值逐个比对一致）。
     - **两条新契约都住 `:core:designsystem`**（签名里分别是 `WindowInsets` 与 `Modifier`）：
       - `StatusBarInsets`：Android 注入 `statusBarsIgnoringVisibility`（**保住「顶栏高度恒定」**
         这一真实行为），其它平台回落 `statusBars`（desktop 恒为零）；
       - `LiquidGlassEffects`：断开 `Glass*TopAppBar ──同包──> TopBarActionsRow ─>
         `topBarLiquidGlass ─> InteractiveHighlight ─> RuntimeShader(AGSL)` 这条链。
         未注入 ⇒ 关闭液态玻璃（这本来就是原实现在 API < 33 时的合法状态）。
     - ⚠️ **§21 的「已证伪」只对 `RuleListScaffold` 自身成立**：搬 topbar 全家桶**确实会**
       碰到 `RuntimeShader`。旧记录没判错平台能力，错在归因；§21 里「四件套不引用
       `TopBarButton`」那一行也已回改（同包调用不需要 import）。详见 §22。
     - **零新增依赖**：`HazeStyle.kt` 的 `@OptIn(ExperimentalHazeMaterialsApi::class)` 是空
       opt-in（materials 的真实用户 `ReaderMenuEffects` 仍在 `:core:ui`），删掉即不需要
       `libs.haze.materials`。
     - 新增 `TopBarPlatformContractsTest`（4 例）⇒ **主验证集 702 → 706、全量 862 → 866**
       （有意变更，已做变异验证：回落改成每次新建 ⇒ 4 例全红）。
       实录见 `feature-slicing-audit-tagrules.md` **§22**。
   - **M1-3s 已完成（2026-09-12）：`AppScaffold` 进 `commonMain`（`ListScaffold` /
     `RuleListScaffold` 随行）。**
     - ⚠️ **台账漏了 `AppScaffold`**：`ListScaffold` 明文 import 它，而 designsystem 不能反向
       依赖 `:core:ui` ⇒ 必须先搬。它是本片消费方最多的文件（**58 文件 / 114 处引用**），
       **包名不变 ⇒ 调用点一行没动**。另纠正两处过期记录：`list/ListUiState.kt` 早已在
       commonMain；`:core:ui` 的 `rules/` 只剩不引用 `RuleListScaffold` 的 `RuleEditSheet.kt`。
     - 闭包 55 文件里除三个种子自身外全 `ok` ⇒ **零新契约、零新模块依赖**。唯一不可跨端的
       `koinInject<ImageLoader>()` 直接删掉（沿用 M1-3p 的 `SingletonImageLoader` 配方：
       `App.newImageLoader()` 就是 `get()`，给到同一个实例），不给 designsystem 加 Koin。
     - **维度 3 实测**：`MiuixScaffold` / `FabPosition`（miuix-ui-desktop）、`layerBackdrop` /
       `rememberLayerBackdrop` / `rememberCombinedBackdrop`（backdrop-desktop 的 `backdrops/*`）
       在 desktop 制品里均存在；判据仍是 `compileKotlinDesktop`。
     - 5 条文案（`add` / `delete` / `sure_del` / `ok` / `cancel`）× 4 语言；`.cvr` 解码比对
       **100/100** 与 `app` 逐字节一致。⚠️ 坑：`build/` 下多份 `.cvr`，旧的 `assets/` 那份是
       陈的（只有 20 条），只有 `resourceGenerator/preparedResources/` 是本次生成的。
     - **验证**：干净重建后四门禁 + desktop 编译 + `assembleAppDebug` 全绿；
       主验证集 706 不变、全量 866 不变（本片没动共享层可测语义）。
       实录见 `feature-slicing-audit-tagrules.md` §23。
   - **M1-3t 已完成（2026-09-12）：导入/设置组件闭包进 `commonMain`，`MiuixPreferenceRenderer`
     新契约。**
     - 起点是把 `feature:tagrules` 的 7 个文件搬进 commonMain；逐个符号定位后发现真正的阻塞是
       它们消费的 **`:core:ui` 组件**（Android library，`commonMain` 不能反向依赖）⇒ 拆成 4 组前置，
       本回合啃第 1 组：`ImportComponents.kt` 及其传递闭包共 **5 文件 / 858 行**
       （`SplicedColumnGroup` / `card/SettingCard` / `settingItem/SettingItem` /
       `settingItem/SwitchSettingItem`），包名不变 ⇒ 消费方 import 零改动。
     - **新契约 `MiuixPreferenceRenderer`**（住 `:core:designsystem`，Android 实现留 `:core:ui`，
       在 `PlatformServices.install()` 注入）：`miuix-preference` 是**整个 miuix 里唯一没有
       desktop 变体的制品**（只有 `miuix-preference-android`；`miuix-ui-desktop` 里连
       `preference/` 包都没有）⇒ 必然走 seam。失败语义取 **`current` 返回 `null`**，由调用方显式
       落 Material3：判据是 `LocalComposeEngine` 默认 Material3、Miuix 引擎只由 Android 侧提供
       ⇒ desktop 上该分支不可达（同 M1-3r `LiquidGlassEffects` 的判据）。
     - **零新增依赖**：`SettingCard.kt` 的 `ExperimentalMaterialApi`（material2）是空 opt-in，删掉
       即可。`LocalConfiguration`（Android-only）→ `LocalWindowInfo.containerSize` + `LocalDensity`
       （3 处），首帧可能为 0 的差异已写进 KDoc。
     - 12 条文案 × 4 语言（另复用已有的 `ok` / `cancel`，原 `android.R.string`）⇒ 每语言 **37 条**；
       产物级核对 148/148 与源 XML 一致、新增 48/48 与 `app` 逐字节一致。
     - G4：唯一的 `ImportJsonEditorProvider.current` 使用点随文件换区域 ⇒ 旧 key 归零删除、新 key
       同计数补登并写明是路径迁移（Backlog 第 8 项清完时应归零删除）。
     - 新增 `MiuixPreferenceRendererContractTest`（2 例）⇒ **主验证集 706 → 708、全量 866 → 868**
       （有意变更，已做变异验证：未注入返回空实现 / 每次新建实例 ⇒ 2 例全红）。
       实录见 `feature-slicing-audit-tagrules.md` **§24**。
   - **M1-3v 已完成（2026-09-13）：`lazylist/*` 进 `commonMain`，`systemGestureExclusion` 下沉为平台原语。**
     - `git mv` `widget/components/lazylist/{LazyList,VerticalFastScroller}.kt` →
       `:core:designsystem/commonMain`（包名不变 ⇒ 5 个消费方 `app` +
       `feature/{dict,tagrules,replacerules,txttocrules}` 的 import 零改动）。
     - 它唯一的 Android-only 点是 `Modifier.systemGestureExclusion()`（全仓仅此文件的 2 个调用点）。
       实测解 CMP `foundation-desktop-1.12.0.jar` 确认整个制品无 `*Exclu*` 类 ⇒ 新增
       `expect fun Modifier.systemGestureExclusionCompat()`（androidMain 转发原生 / desktopMain 恒等）。
       刻意**不用** Provider 注入：没有第三个实现、没有失败语义，恒等返回就是正确语义；
       零状态的 `Modifier` 工厂也无法用 DI 表达。`:core:designsystem` 因此**首次出现
       `androidMain` / `desktopMain` 源集**；**零新增依赖**。
     - 验证：`clean` 后四门禁 + 两个 desktop 编译器 + `:app:compileAppDebugKotlin` + 6 个测试任务
       + `:app:assembleAppDebug` 全绿；用例 **708 / 868 与基线逐字不变**（纯搬迁切片）。
       实录见 `feature-slicing-audit-tagrules.md` **§25**。
   - **M1-3w 已完成（2026-09-13）：`feature:tagrules` 转 KMP/CMP —— 首个 CMP Feature 模块。**
     - 7 个源文件拆源集：`commonMain` 6 个（Contract ×2 / VM ×2 / EditSheet ×2 / 纯 Screen），
       `androidMain` = `HighlightTagRuleRouteScreen`（从 `HighlightTagRuleScreen.kt` 拆出——它要
       `koinViewModel()` 与 `rememberDocumentPicker()`，两者都 Android-only）；
       `androidHostTest` = characterization 测试。**`:app` 侧 import 零改动。**
     - 31 条文案 × 4 语言从 `src/main/res/values*/` 搬进 `src/commonMain/composeResources/values*/`，
       `R.string.x` → `Res.string.x`（每个名字单独 import）；根登记 `"feature/tagrules" to "cmp"`。
     - **文案等价性用解 APK 证明**（不靠读源码）：`assets/composeResources/io.legado.app.feature.tagrules.res/`
       4 个语言目录齐全，解码 `.cvr` 与 `app/src/main/res` 同名条目逐条比对 **124/124 一致**。
     - 依赖侧四个 KMP 坑（都不在「读 import」的可见范围内，详见 §26）：
       ① KMP 源集依赖块里没有 `platform()` ⇒ `project.dependencies.platform(...)`；
       ② 无版本 alias（koin 系列靠 BOM）在 KMP 模块里会断链 ⇒ 显式引 BOM；
       ③ `lifecycle-runtime-compose-android` 传递的 `navigationevent-compose` 解析到本机没缓存的
       1.0.2 ⇒ 按 app 侧既有理由抬到 1.2.0-alpha04（版本对齐）；
       ④ lifecycle 要取 **`androidx.lifecycle` 的 KMP 坐标**（有 `-desktop` 变体，与 Android 侧同号
       2.11.0），而**不是** `org.jetbrains.androidx.lifecycle`（其 2.9.6 元数据要求 androidx 2.9.4）。
     - 验证：**单独 `clean`** 后四门禁 + `:feature:tagrules:compileKotlinDesktop` +
       `:core:designsystem|viewmodel:compileKotlinDesktop` + `:app:compileAppDebugKotlin` +
       6 个测试任务 + `:app:assembleAppDebug` 全绿；用例 **708 / 868 与基线逐字不变**（纯搬迁）。
     实录见 `feature-slicing-audit-tagrules.md` **§26**。
   - **M1-3x-pre 已完成（2026-09-13）：4 件 UI 资产从 `:core:ui` 上提到 `:core:designsystem/commonMain`。**
     - 动因是本批 Feature（`replacerules` / `txttocrules` / `dict`）的共同前置：它们的 `commonMain`
       里用到的 `AppTabRow` / `GroupManageBottomSheet` / `rules/RuleEditSheet` /
       `contentProcess/ContentProcessUiState` 仍声明在 **Android-only** 的 `:core:ui`。
       `tagrules` 恰好没用到它们，故 §26 未暴露这条判据——现已补进 skill 与
       `cmp-module-convention.md` §10.1（判据是**声明文件的模块**，不是包名像不像）。
     - `git mv` 4 文件进 `:core:designsystem/src/commonMain`（包名不变 ⇒ 消费方 import 零改动；
       两个文件的 `R.string.*` → `Res.string.*`，新增 10 条文案 × 4 语言；`:core:ui` 侧 8 条
       变死资源删除）。
     - `:core:designsystem` **首次声明依赖** `kotlinx.collections.immutable`
       （`ContentProcessUiState` 需要；此前该模块零依赖）。
     - 验证：`clean` 后四门禁 + 两个 desktop 编译器 + `:app:compileAppDebugKotlin` 全绿；
       用例 **708 / 868 逐字不变**；文案经解 APK 比对 `designsystem 188/188` 一致。
   - **M1-3x 已完成（2026-09-13）：`feature:replacerules` 转 KMP/CMP（本批最脏的一个）。**
     - 7 个源文件拆源集：`commonMain` 8 个（Contract ×2 / VM ×2 / Screen ×2 / Route-contract /
       `ReplaceRuleImportCompat`），`androidMain` = 两个新拆的 Route（要 `koinViewModel()` 与
       `rememberDocumentPicker()`）；`ReplaceRuleStateTest` 从 `:app/src/test` **搬进**
       `androidHostTest`。**`:app` 侧屏幕 import 零改动。**
     - 52 条文案 × 4 语言搬进 `composeResources`，`R.string.x` → `Res.string.x`；
       根登记 `"feature/replacerules" to "cmp"`。
     - **四处平台直连的收口**：① 三个 VM 都不再继承吃 `Application` 的 `BaseRuleViewModel`，
       导入/导出/上传下沉 `RuleTransferUseCase`；② `exportToUri(Uri)` → 契约本来就是 `String`；
       ③ `GSON` → `JsonCodec`（配置严格对齐、`ReplaceRule` 无自定义 deserializer ⇒ 字节等价）；
       ④ 旧格式导入依赖的 `ReplaceAnalyzer`（jsonpath）抽成平台契约 `ReplaceRuleImportCompat`，
       Android 实现直接委托，**行为逐字不变**（仅两处畸形输入属有意宽松化，已写进 KDoc）。
     - G4 棘轮三条战线：销 5 条旧 `main` 条目 + 下调 2 处 `legacyHelp`；新区域 `coreProvider`
       清零（`ToasterProvider.current` → `koinInject<Toaster>()`）；`ReplaceAnalyzer` 搬到
       `io.legado.app.data.rules`、`AndroidReplaceRuleImportCompat` 搬到 `io.legado.app.domain.gateway`
       （**搬到干净包名**，不放宽基线）。
     - 两个新坑（§27）：**`combine` 的类型化重载最多 5 个流**（6 个退化到 `Array<Any>` 重载，
       类型推导崩塌并级联报一堆假错）⇒ 两级嵌套；**`when` 的 `data class` 分支必须带 `is`**
       （漏掉会被当成伴生对象引用，报「不穷尽」）。
     - 验证：**单独 `clean`** 后四门禁 + 4 个 desktop 编译器 + `:app:compileAppDebugKotlin` +
       `:app:assembleAppDebug` + **7 个测试任务**全绿；用例 **708 / 868 与基线逐字不变**
       （其中 `ReplaceRuleStateTest` 2 例从 app 搬到本模块 ⇒ app **635 → 633**、replacerules
       **0 → 2**，合计不变，`count-test-results.py` 已加映射）。
     - 文案等价性：**解 APK** 比对 `assets/composeResources/io.legado.app.feature.replacerules.res/**`
       四语言齐全、与 `app/src/main/res` 同名条目 **208/208（52×4）逐字一致**。本片把该核对固化成
       `tools/verify-compose-resources.py`（先用 tagrules 自证复现 124/124）。
     实录见 `feature-slicing-audit-tagrules.md` **§27**。
   - **M1-3y 已完成（2026-09-13）：`feature:txttocrules` 转 KMP/CMP。**
     - 3 个源文件：`commonMain` 装 Contract（未改）/ 纯 Screen / 重写的 VM + 新增平台契约
       `TxtTocRuleImportCompat`；`androidMain` 只装新拆的 `TxtRuleRouteScreen`；
       26 条文案 × 4 语言进 `composeResources`；根登记 `"feature/txttocrules" to "cmp"`。
       **本模块无模块级测试**（`TxtTocRuleDeserializerTest` 属 `:core:data`）。
     - **与 replacerules 的关键差异——契约面由「实体的 JSON 兼容逻辑在哪」决定**：
       `TxtTocRule.chapterRule` 的旧键名兼容（`rule` → `chapterRule`）已从
       `@SerializedName(alternate=…)` 改成了 `GSON` 门面上的
       `txtTocRuleJsonDeserializer`，而共享层 `JsonCodec` **不含**它 ⇒ **标准 JSON 也必须走平台**
       （不像 replacerules 只有旧 jsonpath 格式才回落）。解析实现放
       `core/data/androidMain/.../utils/GsonExtensions.kt`（与 `GSON` **同包**、引用无需 import
       ⇒ 不增 G4 `gson` 计数），契约实现 `AndroidTxtTocRuleImportCompat` 住 `domain/gateway` 只做转接。
     - VM 内三处 `context.getString` **不能**改成字面量（原文本地化，会回退）⇒ 用 CMP
       `getString(Res.string.x)`（suspend，故在协程内发 effect）；Screen 内联 toast 改
       `onShowToast` 回调（Route 侧 `koinInject<Toaster>()`，仍是 toast）；
       `BaseRuleEvent` → `RuleTransferEvent`（避免 `legacyBase` 新区域欠账）。
     - G4：删 3 条 `feature/txttocrules/main/...`（gson / legacyBase / coreProvider），
       新区域零欠账，**未放宽基线**。
     - 文案等价性：`98/98`（26+26+23+23）与 `:app` 同名条目逐字一致。⚠️ `zh-rHK` / `zh-rTW`
       合法地比 `values` 少 3 条（源模块 res 与 `:app` res 都缺 ⇒ 两侧都按 qualifier 回落默认）。
       据此修正了 `tools/verify-compose-resources.py` 的第 ② 项判据：**「与 `:app` 对应语言一致」
       而非「各语言彼此一致」**；四模块回归（98/124/208/188）全绿。
     - 验证：**单独 `clean`** 后四门禁 + 5 个 desktop 编译器 + `:app:compileAppDebugKotlin` +
       `:app:assembleAppDebug` + 7 个测试任务全绿；用例 **708 / 868 与基线逐字不变**。
     实录见 `feature-slicing-audit-tagrules.md` **§28**。
   - **M1-3z 已完成（2026-09-13）：`feature:dict` 转 KMP/CMP —— 本批最干净的一个，M1-3 收官。**
     - 勘察结论比预估好：**「查询面板是 platform island」不在模块里**——`feature/dict` 只装
       `rule/` 子域，查询弹窗（`DictActivity`/`DictSheet`/`DictViewModel`）本来就在 `:app`。
     - 3 个源文件：`commonMain` 装 Contract（未改）/ 纯 Screen / 重写的 VM；`androidMain` 只装
       新拆的 `DictRuleRouteScreen`；19 条文案 × 4 语言进 `composeResources`；
       根登记 `"feature/dict" to "cmp"`。本模块无模块级测试。
     - **唯一需要判断的问题：要不要平台契约？—— 不要**。判据被三片收敛成一句话：
       **看「实体的 JSON 兼容逻辑是否落在共享层看不见的地方」**（门面 deserializer /
       JVM-only 库），而不是看「有没有旧格式」。`GSON` 门面只注册 7 个自定义 deserializer
       （`Explore`/`Search`/`BookInfo`/`Toc`/`Content`/`Review`/`TxtTocRule`），**不含
       `DictRule`**；实体也无 `@SerializedName(alternate=)`；`JsonCodec` 的 GSON 配置与
       `INITIAL_GSON` 逐字一致 ⇒ 对 `DictRule` 完全等价，直接换。
     - VM 不再继承 `BaseRuleViewModel`，导入/导出/上传下沉 `RuleTransferUseCase`；
       `uiState` **仍用 5 流 `combine`**（与父类逐字一致，`groupFilter` 一路本 VM 从不写）。
       剪贴板：VM 的 `ClipboardProvider.current` → 注入 `Clipboard`（`coreProvider` 3 条清零）。
       ⚠️ Screen 里的 `LocalClipboard.current` 是 `androidx.compose.ui.platform` 的 CMP 共享 API，
       不是 Provider，保留不动；判据是**有没有 `Provider` 后缀**。
       `BaseRuleEvent` → `RuleTransferEvent`。
     - G4：删 3 条 `feature/dict/main/...`（gson 1 / legacyBase 2 / coreProvider 3），
       新区域零欠账，**未放宽基线**。
     - ⚠️ **新坑：CMP 资源包名 = 模块 Gradle path + `.res`，不含源集子包**。本模块源集目录是
       `.../feature/dict/rule/` 但资源包名是 `io.legado.app.feature.dict.res`（资源在
       `commonMain/composeResources/` 根下）。按 Kotlin 包名推 ⇒ `Res` Unresolved。
     - 验证：**单独 `clean`** 后四门禁 + **6 个** desktop 编译器 + `:app:compileAppDebugKotlin` +
       `:app:assembleAppDebug` + 7 个测试任务全绿；用例 **708 / 868 与基线逐字不变**；
       文案 `76/76`（19×4）一致。
     实录见 `feature-slicing-audit-tagrules.md` **§29**。
     至此 **M1-3「把干净 UI 页切成 Feature 模块」全部收官**：`tagrules` / `replacerules` /
     `txttocrules` / `dict` 四个 Feature 均已 CMP 化。
   - **③ 的剩余阻塞（M1-3v 后重新逐个符号核算）**：`tagrules` 本体 7 个文件的依赖面**已清空**——
     - ✅ `:core:viewmodel` 的 `core/rules`（M1-3u 完成）；
     - ✅ `FastScrollLazyColumn` / `lazylist/*`（M1-3v 完成）；
     - ❌ ~~`AndroidDocumentPicker`~~ **不是前置**：`rememberDocumentPicker()` 只在
       `HighlightTagRuleRouteScreen` 里调用，而 Route 本来就该留 `androidMain`（它还要用
       Android-only 的 `org.koin.androidx.compose.koinViewModel`）。纯 Screen 只收
       `onPickImportSource` / `onPickExportTarget` 两个回调。
     - 其余依赖（designsystem 组件 17 个、`:core:data` 实体与仓储、`:core:platform` 三契约、
       `:core:model` 的 `isJson*`、`sh.calvin.reorderable`）**全部已在 `commonMain`**。
     - ⇒ 这 4 组阻塞**已全部解除**，实施结果见上条 **M1-3w**：7 个文件拆源集、31 条文案 × 4 语言
       搬进 `composeResources`、登记 `"feature/tagrules" to "cmp"` 全部落地；文案等价性由解 APK 的
       `.cvr` 比对证明（124/124 逐字一致）。
7. **M1-4：最小 Desktop/iOS host 主路径**
   - 展示同一 Feature，验证 Koin graph、ViewModel lifecycle、resources、Nav3 和一条数据路径。
   - **已完成（2026-09-13）：最小 Desktop host（`feature:dict` + 真实 Room + headless UI 测试）。**
     - 新建 `smoke/compose-desktop-probe`（工具链探针）与 `host:desktop`（最小 host）。
       **desktop 证据第一次越过「能编译」**：`desktopTest` 里建真库、起 Koin、渲染
       `DictRuleScreen`，断言数据从 Room 流到界面那一行。
     - ⚠️ **本片最重要的产出是一个此前被掩盖的阻塞**：`:core:designsystem` 的组件读
       `LegadoTheme.colorScheme` ⇒ `LocalLegadoColorScheme`，其默认值是
       `error("No ColorScheme provided")`；而提供它的 `ThemeComponents` 在 **Android-only 的
       `:core:ui`**。四个 Feature 转 CMP 时 `compileKotlinDesktop` 全绿，但**一渲染就抛异常**。
       解法是把两个纯映射函数（`ColorScheme.toLegadoColorScheme`、整个 `Typography.kt`）
       上提到 `core/designsystem/commonMain`（包名不变 ⇒ `:core:ui` 零改动），desktop 侧用
       `DesktopTheme`（MaterialTheme + 两个映射）自己搭语义色。
       ⚠️ `DesktopTheme` 是**最小可用**主题，不是 `AppTheme` 等价物（无自定义字体 / Miuix
       引擎切换 / 动态取色）；`AppTheme` 整体搬进共享层仍是独立切片。
     - 两个 CMP 1.12.0 陷阱：`compose.runtime|foundation|material3` accessor 已废弃且
       `compose.uiTestJUnit4` 已移除（须显式加 `org.jetbrains.compose.ui:ui-test-junit4`）；
       `runComposeUiTest` 要用 **v2**（v1 废弃且默认立即执行协程，会掩盖首帧时序问题）。
     - 平台能力缺失一律显式建模：`UploadRepository` / `RuleTransferPlatform` 的 URL 分支
       抛 `UnsupportedOperationException`；`Clipboard` 是进程内的（KDoc 写明不是系统剪贴板）。
       只有 `writeExport` 按契约静默吞异常——那是迁移前的既有语义。
     - 变异验证：插入的规则名与断言不一致 ⇒ 测试必须失败（实测失败），证明不是假绿。
     - 用例基线 **708 → 709 / 868 → 870**（新增 host 1 例 + 探针 1 例，属有意变更）。
     实录见 `feature-slicing-audit-tagrules.md` **§30**。
   - **M1-4b（2026-09-14）：Nav3 在 desktop 上的边界已实测并落地。**
     `host:desktop` 现在有真正的两目的地导航图（host 入口页 → `DictRuleScreen`），
     渲染层自建：`nav/DesktopNavHost` = `NavBackStack.last()` + `NavEntry.Content()` +
     `rememberSaveableStateHolder()` + 自维护的 `ViewModelStore`（entry 离栈即 `clear()`）。
     desktop UI 测试 2 例（导航切换 + entry 级 VM 释放），并做了变异验证（改动栈顶取值与
     清理前提 ⇒ 两例均失败）。
     实测结论（`javap` 解制品，不是推测）：**`navigation3-runtime` 可共享（desktop/jvm 与 iOS
     native 都是真变体）；`navigation3-ui` 在桌面端只有 `jvmStubs`，`NavDisplay` 连编译都过不去；
     `lifecycle-viewmodel-navigation3` 只有 `-android` 变体，但 entry 级 VM 作用域可用 KMP 的
     `ViewModelStore` 自行补齐；nav key + back stack 逻辑可住 `commonMain` 并被双 target 编译。**
     实录见 `feature-slicing-audit-tagrules.md` §31，制品矩阵见 `cmp-module-convention.md` §13。
   - **未做**：iOS host（本机 Windows 无法编译 Kotlin/Native iOS ⇒ 产生不了验证证据，硬写只会
     得到"看起来做了"的假象）；`AppTheme` 整体搬共享层（`DesktopTheme` 仍是最小主题）；
     `NavDisplay` 的入场/退场动画、predictive back、scene strategy 与同栈结果回传（picker）
     在 desktop 上未复刻；跨 Feature 导航未接（第二个 Feature 的 desktop 平台契约尚未实现，
     见 `cmp-module-convention.md` §13.6）。

### 随后执行

8. 按 M2 表逐一删除 9 个已知 core Provider；先处理会阻塞现有四个 Feature 的 clipboard/toast/import。
   - **M2-1 已完成（2026-09-14）：`ImportJsonEditorProvider` 退役 → `BatchImportDialog` 参数注入。**
     clipboard/toast 在 M1-3a 已改成构造注入并被四个 Feature 消费，**import 是剩下唯一还住在
     CMP 共享层（`:core:designsystem/commonMain`）里的静态委托**——通用 UI 组件库直接读全局
     单例，且这是公共 API 的隐藏前置条件（不注册就 `error()`，签名上看不出来）。
     形态：给 `BatchImportDialog` 加无默认值的 `importJsonEditor: ImportJsonEditor` 参数
     （8 个调用点显式传：4 个 CMP Feature 的 Screen/commonMain + Route/androidMain、4 个 app 屏），
     删掉 `:core:platform` 的 Provider object；`:app` 的 `appModule` 绑 `GsonImportJsonEditor`、
     `PlatformServices` 的 install 路径整体退役；`host:desktop` 绑显式抛
     `UnsupportedOperationException` 的 `DesktopImportJsonEditor`（desktop 复刻 Gson 版需先建立
     等价证据，属独立切片）。
     G4：`coreProvider|…importComponents` 归零（条目删除）、`app/…/help` 8→7，总条目 **328→327**；
     用例 **711→712 / 872→873**（新增 `DesktopImportJsonEditorTest` 锁住「desktop 显式不支持」）。
     变异验证：删掉 desktop 的绑定 ⇒ 2 例以 `NoDefinitionFoundException` 失败。
     实录见 `feature-slicing-audit-tagrules.md` §32。
   - **M2-2 已完成（2026-09-14）：`BigDataStoreProvider` 退役 → 实现下沉共享层 + 平台原语。**
     侦察否掉了两条常规路：调用方是 **Room 构造**的 entity 实例方法（进不了 DI），且方法名是
     **书源 JS 兼容面**（`bindings["book"] = ruleData` 后脚本直接调 `book.putVariable`，签名不可动）；
     规则求值入口（`WebBook`/`BookList`/`BookContent`/`Rss`）又全是 `object` 单例，
     所以"上游装配到数据层"（`SourceVariableRepository`）在本仓**没有装配点**。
     形态：`BigDataStore` 实现下沉到 `core/data/commonMain` 的 `RuleDataFileStore`（携带全部路径
     语义），只依赖 `core/platform` 的新原语 `expect object RuleDataStorage`（根目录 + 文件 IO + MD5）；
     `Provider` object 删除，三个 entity 直接引用共享实现；根目录由 host 的 composition root 设置
     （Android `App.onCreate`、desktop `desktopHostModule` 构造体），未设置显式抛异常；
     需要 `appDb` 的清理逻辑留在 `:app` 的新 `RuleDataCleaner`。
     G4：`coreProvider|…entities` 45→36，`coreProvider|app/main/io/legado/app` 2→0（条目删除）；
     另有 `appCtx`/`legacyHelp`/`legacyNaming` 四条因删掉 `RuleBigDataHelp` 门面连带下调
     ⇒ `coreProvider` 条目 **5→4**、基线数据行 311→310。
     用例 **712→712 / 873→877**（删 2 个失去被测对象的 Provider 用例、新增 6 例桌面真文件系统用例）。
     变异验证：hex 改大写 ⇒ 1 例失败；`book`→`books` ⇒ 3 例失败，均随后还原。
     实录见 `feature-slicing-audit-tagrules.md` §33；形态见 `cmp-module-convention.md` §15。
   - **M2-3 已完成（2026-09-14）：`SymmetricCryptoProvider` 退役 → 平台原语 + `CryptoCodecs`。**
     这是 `entities` 那 36 条里**唯一真正可退休**的一条，其余四条被两类硬约束卡住（见下一条）。
     `SymmetricCrypto` 从「interface + Provider」改成 `:core:platform` 的
     `expect object`：androidMain/desktopMain 各一份内容相同的 JCA actual。判据是 AGENTS.md
     「无第三实现且语义恒等」——原判成注入的理由是「实现依赖 `:app` 工具」，移植时发现那些工具
     只用到 `kotlin.io.encoding.Base64`（stdlib）与 `MessageDigest`（JVM 自带），本模块自足。
     兼容面（既有 `userInfo_<sourceKey>` 密文能否读回）由**硬编码密文向量**钉住，向量由
     `openssl enc -aes-128-ecb` 与 Node `createCipheriv` 两个独立实现交叉确认，不是自证。
     刻意不移植三条无调用方的分支（`setIv`/随机密钥/DES-DESede 密钥截断），已在契约 KDoc 列明。
     G4：`coreProvider|…entities` 36→33、`coreProvider|app/…/help` 7→6，并因删掉
     `import io.legado.app.help.crypto.SymmetricCryptoAndroid` 连带 `legacyHelp|…/help` 35→34；
     **零新增区域**，基线数据行 310 不变。
     用例 **712→712 / 877→882**（`core:platform` 的 `SymmetricCryptoContractTest` 2→7，
     主验证集不变——`core:platform` 只进全量）。
     变异验证：断掉 hex 解密分支 ⇒ 精确 1 例失败；`encodeBase64` 换 URL-safe ⇒ 2 例失败。
     实录见 `feature-slicing-audit-tagrules.md` §34。
   - 剩余 3 条 `coreProvider` 里最大的债是 `core/data/.../entities`（33，`BaseSource` 的
     `KeyValueStore` 15 / `SourceRuntime` 9 / `Logger` 5 / `CookieStore` 4）；另一条
     `core/ui/.../widget/components`（2，`LoadMoreFooter` 的 `ClipboardProvider.current`）
     等它随页面迁 designsystem 时一起做。
     ⚠️ **这 33 条不能照搬 M2-2/M2-3 的原语方案**，侦察已确认两类硬约束（详见 §34「下一步」）：
     ① 实现真的需要 host 实例（`AppDatabase` / okhttp `CookieManager`），而
     `:core:platform` 的 actual 够不着 `:app` ⇒ 要么先让 `:core:data` 自己拥有库实例
     （会连带动 `appDb` 全局，是里程碑级前置），要么接受一个新的共享层持有者（与「零 service
     locator」冲突，必须显式决策）；② `Logger` 的实现是同名同义的 `:app` `constant.AppLog`，
     它有 300+ 调用方且日志界面读 `AppLog.logs`，换成 `android.util.Log` 会**静默丢掉站内日志**。
9. 将 `core:model` 的 `utils` 文件按领域改包改名；同一 PR 迁完调用方，不加 typealias façade。
10. 把 `core:viewmodel` 的共享流程下沉为无 UI UseCase，迁四个 Feature 后删除模块。
11. 以 rules/settings 为第一个 `core:data` 拆分样板；Feature 不再见 DAO/entity。
   - **M3-6 已完成（2026-09-15）：标签分组规则域下沉 `domain/rules` + `data/rules` —— `rules` 域拆完。**
     本片是 M3 的最后一片，也是唯一一片**要先合并语义双份**才能动的：`TagGroupRuleApplier`
     （全量重算，事务内）与 `:app` 的 `applyTagGroupRulesForBook`（单本书，`Book.save()` 调用）
     各持一份匹配实现，只靠注释约定同步。合并方向是把单本书路径变成
     `TagGroupRuleApplier.applyToBook` 的薄委托，共享同一个私有实现，只多一个 `persist = false`
     ——镜像在 `:app` 侧，而共享实现必须留在 `:core:data`（它同时是 `Book` / `BookGroup` /
     `BookGroupMutationRepository` 的 owner，搬出去会构成环）。该路径迁前**零测试**，故补了一条
     `:app` 用例同时钉住「只处理这一本书」与「**不写库**」（因此 `BASELINE_MAIN` 712 → 713，
     属有意变更，已显式声明）。
     形态：`domain/rules` 加 `TagGroupRule`（`var id = systemTimeMillis()`、**id-only 判等**——
     与 M3-5 的 `RuleSub` 恰好相反，见领域模型 KDoc）+ 端口；`data/rules` 加 Mapper + Impl
     （收 `TagGroupRuleDao`，**保留**迁移前的 `withContext(Dispatchers.IO)`）+ `TagGroupRuleMapperTest`
     7 例；旧 `:core:data` 仓储删除，不留门面/typealias；消费方 `appModule` / `GroupViewModel` /
     `GroupEditSheet` / `GroupManageSheet` 与 `feature:tagrules`（Contract / EditSheet / VM /
     特征化测试）全部改见领域模型与端口。`Restore.kt` / `Backup.kt` 仍按**实体**读写
     `tagGroupRule.json`——备份格式的 owner 是实体，本片**不改文件格式**（同 M3-4）。
     用例 **712 → 713 / 925 → 933**；四门禁 `--rerun` 全绿，G4 **零变更**（`:app` 的 UI 层与
     `feature/*` 都不落在受监测的计数项上，无新增区域、无下降项）。
     模板、选片表与验证配方见 `.agents/skills/legado-kmp-migration/references/m3-domain-slice.md`。
   - M3-1～M3-5（替换规则 / 高亮标签 / 字典 / TXT 目录 / 规则订阅）只改了代码与
     `tools/count-test-results.py` 的台账注释，提交见同上 reference 的 Measured slices 表
     （`fb64d2be95` / `9d21369475` / `df00c585d8` / `8a0b7cd1c9` / `0555a0acc0`）。
     下一步 M4/M5 的 rules 侧已无剩余域；`core:data` 仍有 settings / library / source 等域待拆。
   - **M4-1 已完成（2026-09-15）：AI 提示词预设域下沉 `domain/ai` + `data/ai` —— 第一个非 rules 域。**
     `rules` 域拆完后，按同一套模板继续拆 `core:data` 的其它域。选片依据（真实 Feature 消费 >
     测试护栏 > 无平台契约、面最小）：`AiPromptPreset` 实体 9 字段、仓储 33 行，实体在 `:app` 侧
     **只有 1 个文件**引用（`ReadAiDelegate`），且**不进备份/恢复、不经 `JsonCodec` 反序列化**
     ⇒ 领域模型可逐字照抄实体——字段**全 `val`**、判等为 data class **全字段**（与 M3-5 `RuleSub`
     同侧，与 M3-6 `TagGroupRule` 的 `var` + id-only 判等相反）。
     ⚠️ 本片**新建了模块对**（`domain/ai` pure + `data/ai` data）：`domain/rules` + `data/rules`
     是 rules 域专用，不能往里塞别的域。两处登记必须成对出现——`settings.gradle` 的
     `include ':domain:ai'` 与根 `build.gradle.kts` 的 `kmpModuleTypes`（未登记一律按 pure 处理）。
     G4 对新区域零容忍 ⇒ 新模块源码必须零 `appCtx` / `appDb` / `GSON` / `coreProvider`（实测零变更）。
     端口形态与 rules 域不同：本域**沿用既有契约名** `AiPromptPresetGateway`（本仓 `domain/gateway`
     下有 60+ 个同形态契约，改名是无收益的 rename churn，还会连带改注入点变量名），只把收发类型
     换成领域模型；零调用方的 `savePreset(preset)` 随片删除（底层 DAO 的 `upsert` 留在 DAO 上，
     归 `data:database` 债务）。实现 `AiPromptPresetRepositoryImpl` 收 `AiPromptPresetDao`，
     **保留**迁移前的 `withContext(Dispatchers.IO)`。旧仓储与旧 Gateway 一并删除、不留门面。
     用例 **713 不变 / 933 → 940**（新增 `AiPromptPresetMapperTest` 7 例，不进主集）；四门禁
     `--rerun` 全绿，G4 零变更。
   - **M4-2 已完成（2026-09-15）：AI 长期记忆域下沉 —— 复用 `domain/ai` + `data:ai`，不再新建模块对。**
     选片依据同 M4-1，本片的价值在三点「与上一片不同」：
     ① **复用模块对**：AI 域第二片直接放进已有 `domain/ai` + `data:ai`，不再动 `settings.gradle`
     与 `kmpModuleTypes`（M4-1 已建好）。判据是「域」而不是「实体」——AI 域的其余 Gateway
     （`AiArtifact` / `AiChat` / `AiProfile` …）后续都走这条路。
     ② **端口只留 3 / 8 个方法**：`observeByConversation` / `observeGlobal` / `getByConversation`
     / `getGlobal` / `deleteAllForConversation` 全仓零调用方，随片不进端口。前两个是 `Flow` 形态
     ⇒ 本域下沉后**不再有任何 Flow 端口方法**；`getByConversation` / `getGlobal` 的 DAO 方法被
     `getForPrompt` 内部使用，只删端口不动 DAO。
     ③ **实现有真实逻辑，光 mapper 测试不够**：`upsert` 写前用当前时间**覆盖** `updatedAt`
     （迁移前 `System.currentTimeMillis()`），`getForPrompt` 是「全局 + 本会话」拼接且有空白
     会话 id 的短路。因此 M4-2 起新增**第二种测试形态**：`AiMemoryRepositoryImplTest` 用手写的
     DAO 假实现（纯 Kotlin 实现 Room 的 `@Dao` interface，不碰 Room 运行时）钉住这两条。
     `System.currentTimeMillis()` 换成 `:core:platform` 的 `systemTimeMillis()`（android/desktop
     的 `actual` 都是它，语义等价）—— 换的原因是 commonMain 拿不到 `java.lang.System`；
     为此 `data:ai` 新增 `implementation(project(":core:platform"))`。
     ⚠️ 本域**复合主键**（`conversationId` + `key`，无 `id` 字段），且**空 `conversationId` 是
     语义值**（表示全局记忆，DAO 查询写 `WHERE conversationId = ''`）⇒ 映射必须原样搬运、
     不得归一化。判等仍是**全字段**（data class 默认），陷阱从「补 id-only `equals`」变成
     「补按复合主键的 `equals`」——那会吞掉 `upsert` 刷新 `updatedAt` 的差异。
     用例 **713 不变 / 940 → 951**（`AiMemoryMapperTest` 7 例 + `AiMemoryRepositoryImplTest`
     4 例，均不进主集）；四门禁 `--rerun` 全绿，G4 零变更；变异 5 轮全红后回绿。
   - **M4-3 已完成（2026-09-15）：AI 产物域下沉 —— 复用模块对，但踩到三个「本片独有」。**
     选片 `AiArtifact`（实体 13 字段、仓储 53 行纯 DAO 委派、`:app` 侧 7 文件引用、四个端口
     方法全有调用方、不进备份/恢复、零既有护栏 ⇒ 自带 mapper 测试）。三点与前两片不同：
     ① **`Flow` 端口方法首次出现**：`observeBookArtifacts(...): Flow<List<AiArtifact>>`。
     M4-2 把本域仅有的两个 `Flow` 方法删掉后，`domain/ai` 的端口全是 `suspend`，模块无需
     coroutines 依赖；本片起必须加 `implementation(libs.kotlinx.coroutines.core)`
     （与 `domain/rules` 同因）。实现侧因此必须 `dao.observeX().map { it.toDomainList() }`
     ——映射发生在**流内、每次发射都做**，不能透传 DAO 的实体 `Flow`。
     ② **首次「扩端口」而非搬端口**：`:app` 的 `AiToolRepository` 原本直连
     `aiArtifactDao.queryArtifacts`。本片把该 DAO 直连收窄成走新端口方法 `queryArtifacts`
     ⇒ `AiToolRepository` 从「1 个 DAO + 3 个 Gateway」变成「0 个 DAO + 4 个 Gateway」。
     不选「让 `:app` 自己 `toEntity()`」是为了不把映射细节漏出数据层。构造注入的 DAO
     **不落 G4 的 `appDb|...` 模式**（只有 `appDb.` 锚定的访问才算）⇒ 扩端口对门禁中性。
     ③ ⚠️ **`:core:data` 自己也是消费方**（M3/M4 前几片从未遇到）：`:core:data` 的
     `domain/usecase/AiTaskManager.kt` import 旧 Gateway 与实体，删旧件后在 `:core:data`
     编译失败（不是 `:app`）。修法＝ `:core:data` 新增 `implementation(project(":domain:ai"))`
     （方向合规：G1 只禁 core→feature/宿主；AGENTS.md 目标依赖图 domain 在 data abstractions
     之上）。**`AiTaskManager` 不搬**——它是应用级用例编排器而非仓储，不属数据域切片；
     其 `System.currentTimeMillis()` 也**不换** `systemTimeMillis()`（该模块已能编译，
     换是行为噪音，一片只改一个边界；M4-2 的换是因为文件本身要迁进共享层 commonMain）。
     领域模型逐字照抄实体（全 `val`、全字段判等，与 M4-1/M4-2 同侧），并**首次复刻
     `companion object` 的四个 `STATUS_*` 常量**（DAO 用实体的常量做 SQL 插值，`:app` 已改用
     领域模型的常量 ⇒ 两侧并存且取值必须一致，mapper 测试有一条逐值比对钉它）。
     实现体本身仍是纯委派（无 `copy(`、无分支），但**本片必须补 Impl 测试**——
     `observeBookArtifacts` 是本域唯一的 `Flow` 端口方法，而 mapper 测试只测
     `toDomain` / `toEntity` / `toDomainList`、**不驱动那条流**：把流内映射换成
     `as List<AiArtifact>` 能编译通过（仅 unchecked cast 告警）且九条 mapper 用例全绿，
     真机每次发射才 `ClassCastException`。故 `AiArtifactRepositoryImplTest`（**7 例**）
     用假 DAO 驱动**多次发射**的流，钉住「每次发射都映射」+ `queryArtifacts` 三个可空筛选
     参数的透传 + 未命中缓存透传 `null`；`AiArtifactMapperTest` **9 例**。
     合计 `data:ai` **27 → 34 例**。
     旧 `AiArtifactGateway` / `AiArtifactRepository` 一并删除、不留门面。用例 **713 不变 /
     951 → 967**；四门禁 `--rerun` 全绿，G4 零变更；变异 6 轮全红后回绿。
     模板与新增要点见 `.agents/skills/legado-kmp-migration/references/m3-domain-slice.md`。
   - **M4-4 已完成（2026-09-16）：AI 会话域下沉 —— 复用模块对，本片的价值是「顺手挖出并修掉一个
     静默的生产故障」。** 选片 `AiChat`（两个实体 `AiChatConversation` 6 字段 /
     `AiChatMessage` 9 字段；端口 13 方法但只有 **11 个有调用方**；**实现逻辑最重的一片**，
     有 5 处真实分支）。四点与前几片不同：
     ① ⚠️ **`AiMessageParts.kt` 所在的 `core:model` 缺 serialization 编译器插件**（本片发现并修复）。
     `AiMessagePartJson` 的多态编解码走 `@Serializable sealed interface AiMessagePart` + 六个子类，
     而 `86c7428d24` 把该文件从 `:app`（该模块 apply 了 `org.jetbrains.kotlin.plugin.serialization`）
     移进 `core:model` 时**漏了给目标模块 apply 插件** ⇒ 六个子类的 `$$serializer` 一个都没生成
     （实测 `core:model/build/classes/.../desktop` 下零个 `*$$serializer.class`）。后果是双重的：
     `encode` 直接抛 `SerializationException: Serializer for subclass 'X' is not found in the
     polymorphic scope of 'AiMessagePart'`；而 `decode` 把异常 `runCatching` 吞成 `emptyList()`
     ⇒ **聊天记录会静默丢光内容，不崩不报错**。编译期完全无感（插件缺失只影响代码生成），
     该路径**零测试覆盖**，故潜伏至今。修法＝ `core:model/build.gradle.kts` 加
     `alias(libs.plugins.kotlin.serialization)`；同时补 `AiMessagePartJsonTest` **8 例**钉住它，
     并把 `:core:model`（此前各片从未跟踪）纳入 `count-test-results.py`。这是本片**修 bug 与迁
     移同片**的唯一处，且是**迁移前置**：不修它，新 `AiChatRepositoryImpl` 的两处 `encode`
     照样是坏的。
     ② **集合映射一实体一文件**（`AiChatConversationMapper` / `AiChatMessageMapper`），不合并：
     两条 `List<*>.toDomainList()` 放进同一文件会因泛型擦除编译成同一个 JVM facade 类里的
     同名同形方法 ⇒ platform declaration clash。`data:ai` 因此从「一个 Mapper 一个 `Impl` 文件」
     变为「2 个 Mapper + 1 个 `Impl`」；`Impl` 里不再有集合重载（M4-3 是放在 `Impl` 里的）。
     ③ **端口删两个方法**：`observeMessages` / `getBranches` 零外部调用方 ⇒ 不进端口。
     但 `getBranches` 的 **DAO 方法要留**（`saveRegeneratedMessage` 与 `selectBranch` 都在实现内部
     调它），故是「只删端口、不删 DAO」；这与 M4-3「只删端口、不删 DAO」同律、与 M4-2「删端口
     且 DAO 方法本来就零调用方」不同。
     ④ **保留迁移前的 `withContext(Dispatchers.IO)`**（除两个 `Flow` 方法外每方法都包），与
     M4-1/M4-2 同侧、与 M4-3 相反（那边迁前就是裸调 DAO）。本片是**最需要照抄调度行为的一片**：
     分支逻辑里 DAO 调用成串（读兄弟 → 逐条改写 → 写新消息 → 推会话时间戳）。
     实现体有 5 处真实逻辑 ⇒ 按 M4-2 判据**必须**配 `AiChatRepositoryImplTest`（**18 例**，
     假 DAO 记录调用序列，钉住 `branchIndex = countBranches(...)` 的取值、`saveRegeneratedMessage`
     的「不删旧分支只逐条取消选中」、`selectBranch` 的两处提前返回、`getBranchCounts` 的
     `List<BranchCount>` → `Map` 聚合、`deleteConversation` 的**先删消息再删会话**顺序）；
     `AiChatConversationMapperTest` **8 例** + `AiChatMessageMapperTest` **9 例**（后者含
     `partsJson` 原样搬运的往返约束）。
     合计 `data:ai` **34 → 69 例**（+35）。旧 `AiChatGateway` / `AiChatRepository` 一并删除、
     不留门面；消费方只有 `:app` 三处（`appModule` / `AiChatGenerationUseCase` / `AiChatViewModel`），
     **本轮没有 `:core:data` 内部消费方**（与 M4-3 不同）。用例 **713 不变 / 967 → 1069**；
     四门禁 `--rerun` 全绿，G4 零变更；变异 8 轮全红后回绿。
     ⑤ ⚠️ **变异验证在第 5 轮抓出测试自身的盲区**（这是本片第二条值得复用的通则）：
     「`partsJson` 被顺手 `trim()`」那一轮**首轮仍绿**——用例思路是对的（用**非法 JSON** 挡
     「顺手 decode/encode」），但字面量首尾没有空白 ⇒ `trim()` 隐形。把 fixture 改成
     `"  not-a-json-at-all { unbalanced  "` 后**生产代码一行未动**、同一轮立刻变红。
     **通则：某一轮幸存时要改的是「测试输入」而不是「删掉这一轮」**——问「我的断言能容忍的
     最小改动是什么」，把输入正好放在那条边界上（逐字搬运的 `String` ⇒ 首尾空白 + 不可规范化；
     `List` ⇒ 多元素且顺序非平凡；可空字段 ⇒ 放 `null`）。配套信号：若能想出的变异全落在 mapper
     字段上，说明本片有风险的逻辑没被测到。
     ⚠️ 流程教训：**变异脚本绝不能与其它 Gradle 构建并发**——两者共用 `data/ai/build/`，
     实测导致全量验证集报出 7 个「假失败」（源码其实是好的，全是变异泄漏），并把结果 XML 冲掉。
     脚本已加锁 + `atexit`/信号还原，见 `legado-verify/m4-4-mutate.py`。
   - **M4-5a 已完成（2026-09-16）：`Digest` 契约扩展 `md5()` —— AI profile 域下沉的平台前置。**
     本片不改任何业务代码，只补能力；它的存在理由是下一片（M4-5b）**搬不动**：AI profile 域的
     `AiProfileRepository.stableModelId` 调 `:app/utils/nameUuidFromBytes`，而后者用
     `java.security.MessageDigest.getInstance("MD5")`（JVM-only）⇒ 不先把 MD5 变成共享能力，
     那条 `model_<uuid>` 的 ID 生成就进不了 `commonMain`。
     ① ⚠️ **扩展平台契约的判据是「出现了真实消费方」，不是「对称/完整性」**：`Digest` 的原始
     注释写得很明确——「只暴露 [sha256]：当前唯一真实消费方只需它；AES/HMAC 等在有真实消费方时
     再加，**不为对称提前扩接口**」。本片补 `md5` 正是因为它**终于**有了真实消费方；反过来若有人
     为了「对称」提前加 `md5`，那条注释就是反例。这一条与 AGENTS.md 的「无调用方抽象」同源。
     ② ⚠️ **MD5 在本仓是「持久化兼容」而非实现细节**（已在 `Digest.md5` 的 KDoc 里写明）：
     `nameUuidFromBytes` 是 UUID v3 名称空间哈希的字节级复刻，输出会**落库**（
     `ai_model_profiles.id = "model_<hex>"`）并**参与查询**。换算法/换实现会让既有用户的模型档案
     ID 全部漂移，表现为「升级后模型列表空了」——所以哪怕 MD5 在密码学上不安全也不能动它。
     `DigestContractTest` 的 4 条用例（空输入 / `abc` 两个 RFC 1321 向量、`hello` 的独立实现向量、
     两个不同输入的**各自固定向量**）就是这个承诺的护栏。
     ③ ⚠️ **契约测试的向量必须是独立实现算出的固定值**：本片第一版我写成
     `assertEquals("a3b1...".length, composite.length)` 这类「只比长度」的假断言，以及
     `assertFalse(a == b)` 这种「互相不等就算过」的形式——两者都**无法区分实现**（换个摘要算法
     照样通过）。最终四条用例全部比对 Python `hashlib` 独立算出的常量，脚本里还有一步
     「向量自证」先验证常量本身。
     ④ ⚠️ **契约测试是抽象基类 + 每 target 子类 ⇒ 「量一边」不等于「两边都对」**：本片的变异
     验证因此特意做了三轮，第 3 轮**只改 `androidMain` 的 `md5` 并只跑
     `:core:platform:testAndroidHostTest`**，同样 4 例变红——这才排除掉「android 侧子类没接上 /
     没跑」的可能。**只做 desktop 侧变异是不充分的**（`DigestContractTest` 同时跑
     `testAndroidHostTest` 与 `desktopTest`，但计数口径只取 desktopTest）。
     改动：`Digest.md5()`（`commonMain` 契约）+ `JcaDigest` 两份 `override`（android/desktop）+
     `DigestContractTest` **+4 例** + `SpeechIdentityCalculatorTest.RecordingDigest` 补实现
     （它实现 `Digest` 接口，加抽象方法后必须同步）。用例 **主集 713 不变 / 全量 1069 → 1073**
     （`:core:platform` 88 → 92，该模块不在主集一侧）；四门禁全绿、G4 零变更；变异 3 轮全红后回绿。
     脚本：`legado-verify/m4-5a-*.py`、`legado-verify/m4-5-baseline.py`。
   - **M4-5b 已完成（2026-09-16）：`nameUuidFromBytes` 下沉 `:core:platform`。与 M4-5a 合并为
     一次提交**（理由见末尾「为什么合并」）。
     把 `:app/utils/UuidExtensions.kt`（UUID v3 名称空间哈希）搬成 `:core:platform` 的
     `NameUuid.kt`。它是 M4-5c（AI profile 域下沉）的**第二个**平台前置：AI profile 的
     `stableModelId` 用它生成 `model_<hex>`。
     ① ⚠️ **它不做成 `expect/actual`，而是「共享纯函数 + 参数注入」**——这是本片最值得复用的一条
     判断：函数体只有「MD5 + 两个位运算 + `Uuid.fromByteArray`」，**不含任何平台 API**；
     真正平台相关的是它的**依赖**（MD5）。所以签名为
     `fun nameUuidFromBytes(bytes: ByteArray, digest: Digest): Uuid`：算法留共享层，摘要交
     `Digest` 契约由调用方注入 `JcaDigest`。按 AGENTS.md「`expect/actual` 只用于平台原语」，
     这里用普通顶层函数更贴切。**判据：先看函数体有没有平台 API，再看它的依赖**——只看
     「它原来住 `:app`」会误判成 expect/actual。
     ② 于是 M4-5a 的 `Digest.md5` 有了**首个真实消费方**（这也是两片合并提交的理由：拆开提交
     会让先提交的 A 里 `md5` 零消费方，反而违反「无调用方抽象」）。
     ③ ⚠️ **下沉「被测对象」时要显式处理它的既有护栏**：`:app` 的 `CryptoCompatibilityTest`
     有一条 `nameUuidFromBytes 与 java UUID v3 一致`（对照 `java.util.UUID.nameUUIDFromBytes`）。
     被测对象搬走后这条用例失去对象 ⇒ 按 M2-2 / M2-4 先例删除，**同时**在新位置补更强的版本：
     `NameUuidContractTest` 在 androidHostTest 与 desktopTest **两个** target 上各 6 例，
     其中 `matches java UUID nameUUIDFromBytes` 就是原用例的搬家（`java.util.UUID` 是 JVM API，
     只能写在 target 子类、不能进 commonTest）。护栏没丢、覆盖面还扩到两个 target。
     ④ ⚠️ **第一版写错的断言值得记下来**：变体位写成 `assertEquals('8', uuid[19])`。实际
     `(b[8] and 0x3F) or 0x80` 的结果落在 `0x80..0xBF` ⇒ hex nibble 是 `{8,9,a,b}` 之一，
     **不是固定值**（本仓向量实际是 `'b'`）。那条断言会把**正确**实现判错。修法是
     `assertTrue(uuid[19] in "89ab")`。教训：写「格式/位域」类断言前先用独立实现验一遍**边界内
     有几种取值**，别把「我算出来的那一个」当成契约。
     改动：新增 `NameUuid.kt`（commonMain）+ `NameUuidContractTest`（commonTest 抽象基类）+
     两个 target 子类；删 `:app/utils/UuidExtensions.kt`；`:app` 的 `AiProfileRepository`
     调用点补 `JcaDigest` 实参（该文件本体到 M4-5c 才搬走）；`CryptoCompatibilityTest` 删
     失效用例与 import。用例 **主集 713 → 712（-1）/ 全量 1073 → 1078（+6 -1）**——主集下调是因为
     删掉的那条 `:app` 用例在被计模块内；四门禁全绿、G4 零变更；变异 **4 轮全红**（删 version 行 /
     删 variant 行 / `md5[6]→md5[7]` / 摘要换成 `sha256` 前 16 字节，末轮只跑
     `testAndroidHostTest` 顺带证明 android 侧子类有效）。
     脚本：`legado-verify/m4-5b-*.py`。
     **为什么 M4-5a 与 M4-5b 合并为一次提交**：B 的 `nameUuidFromBytes` 是 A 的 `Digest.md5` 的
     首个真实消费方，没有 A 则 B 不存在、没有 B 则 A 违反「无调用方抽象」；且两片的
     `count-test-results.py` 基线变更落在同一处。分两次提交只会让第一次的 `md5` 暂时零调用方。
   - **M4-5c 已完成（2026-09-16）：AI profile 域下沉 `domain/ai` + `data/ai` —— M4 AI 域下沉的
     末片，也是最大的一片。** 前两片（M4-5a/5b）正是为它清障：`stableModelId` 要 MD5 与
     UUID v3 字节语义，两者都不在 `commonMain` 可达范围。
     搬走的是**三个实体**（`AiProviderProfile` 17 字段 / `AiModelProfile` 12 字段 /
     `AiTaskPreset` 12 字段）**共用一个端口** `AiProfileGateway`（既有契约，13 方法）＋
     实现 `AiProfileRepositoryImpl`（`AiProfileRepository` 439 行整份搬迁）＋三个 Mapper；
     19 个消费方迁移，2 个旧件删除（`:core:data` 的 gateway、`:app` 的 repository）。
     ① ⚠️ **端口是「既有契约搬家」，名字不改**（本仓 `domain/gateway` 有 60+ 个 `XxxGateway`；
     改名会连带改全部注入点变量名）。与 M3 的 rules 端口叫 `XxxRepository` 的区别在于：
     那边「它本来就叫那个名字」，与本片同律——**判据是「新造端口 vs 既有契约搬家」**。
     ② ⚠️ **随片删掉两个零调用方的方法**（15 → 13）：`getProviderApiKey` / `saveDefaultChatProfile`
     全仓只有「声明 + 实现 + 测试假实现」三处、**零真实调用**（后者写在 M4-1 之前，此后被
     `AiConfigViewModel` 的新路径取代）。**背后的 DAO 方法一个都没删**（留给 `data:database`）。
     ③ ⚠️ **`GSON` → `:core:platform` 的 `JsonCodec`，判据是「模块可达性 + 配置逐行相同」**：
     `GSON` 门面住 `core/data/src/androidMain`，而 `data/ai` **只有 commonMain** ⇒ 编译期拿不到。
     `JsonCodec` 的 Gson 配置与 `INITIAL_GSON` 逐行相同（`MapDeserializerDoubleAsIntFix` +
     `Int`/`String` deserializer + `ToNumberPolicy.LONG_OR_DOUBLE` + `disableHtmlEscaping` +
     `setPrettyPrinting`），唯一差异是 `GSON` 多注册的 7 个 **rule 类型** deserializer——
     本域序列化的是 `AiGenerationParams` / `AiTaskRuntimeOptions` / `Map`，都不是 rule 类型
     ⇒ 字节输出与解析行为一致。替换点只有三处：`toJson` / `fromJsonObject` / `decodeAnyMap`。
     **先比对配置再替换，不要凭「名字不一样」就认定语义不同**。
     ④ ⚠️ **`stableModelId` 是持久化兼容边界，MD5 由构造注入**：它复刻
     `java.util.UUID.nameUUIDFromBytes`，输出落库成 `ai_model_profiles.id = "model_<hex>"` 并
     **参与查询**；`toString().replace("-", "")` 的大小写与去横线方式逐字保留（改一个字符就是
     「升级后模型列表空了」）。平台能力的分派按 AGENTS.md：**没有可回落默认 ⇒ 参数注入**
     （`AiProfileRepositoryImpl(dao, digest: Digest)`，漏传即编译错误），算法本体留在
     `:core:platform` 的 `nameUuidFromBytes`。**没有做成 `expect/actual`**（它不是平台原语）、
     **也没做成 CompositionLocal**（没有任何展示性回落）。
     ⑤ ⚠️ **`withContext(Dispatchers.IO)` 逐条照抄迁移前行为**：迁移前**除三个 `Flow` 方法外每个
     方法都包了 IO**，本片照抄（与 M4-1/M4-2/M4-4 同侧，与 M4-3「迁前就是裸调 DAO」相反）。
     拒绝「向上一片对齐」——那会改变实际调度行为而测试仍全绿。
     ⑥ ⚠️ **三个 `observeXxx()` 必须在流内映射**（`map` 挂在 DAO 的 `Flow` 上，不是先 `first()`），
     否则后续每次发射都不会再映射；mapper 测试碰不到这条路径 ⇒ 由 Impl 测试用假 DAO
     发射**多次**来钉住（M4-3 立的判据，本片是第二次应用）。
     ⑦ ⚠️ **JVM facade 泛型擦除 ⇒ 一实体一 Mapper 文件**：两条 `List<XEntity>.toDomainList()`
     放进同一个文件会编译成同一个 facade 类里的同名同形方法，构成 platform declaration clash。
     自检：`grep -rn 'fun List<.*>\.\(toDomain\|toEntity\)'`。
     ⑧ ⚠️ **G4 基线要随「删掉一个带 GSON 的文件」下调**：`:app/data/repository` 的 `gson` 门面计数
     7 → **6**（`AiProfileRepository.kt` 内有 `import io.legado.app.utils.GSON`），棘轮只降不升
     ⇒ 同一片里改 `gradle/architecture/legacy-baseline.txt`。同类先例：M3-4 的 `ui/association` 8 → 7。
     改动：`domain/ai` 新增三个领域模型 + 端口；`data/ai` 新增三个 Mapper + Impl；删除
     `:core:data/.../gateway/AiProfileGateway.kt` 与 `:app/.../data/repository/AiProfileRepository.kt`；
     `appModule` 绑定改 `single<AiProfileGateway> { AiProfileRepositoryImpl(get(), JcaDigest) }`。
     用例 **主集 712 不变 / 全量 1078 → 1152（+74 = `:data:ai` 69 → 143：三个 Mapper 测试 9+10+9
     ＝ 28，Impl 行为测试 46）**；四门禁全绿（含 G4 基线下调）；变异 **10 轮全红后回绿**
     （三实体映射字段 / `id` 不得重算 / `isDefault` 不得写死 / 摘要分隔符 / apiKey 回落 /
     `deleteProvider` 顺序 / `mergeWithFallback` 方向 / 流内映射）。脚本 `legado-verify/m4-5c-*.py`。
     ⑨ ⚠️ ⚠️ **本片最值得记的流程教训：增量构建下的「绿」可以是假的。** 第一遍定向验证报
     `:data:ai:desktopTest` 绿，但那次该任务是 **UP-TO-DATE**（没真跑）。干净重建后它**红**：
     4 条**表达式体** `@Test 方法`（`fun x() = runBlocking { ...; assertFailsWith<...>{...} }`）
     的返回类型取自 lambda 末表达式，`assertFailsWith` 返回异常对象、`assertNotNull` 返回 `T`
     ⇒ **不是 `void`**，JUnit 4 把整个测试类判 `InvalidTestClassError`（只报 1 个
     `initializationError`，**0 个真实用例执行**）。同一遍还揪出第二条：`setDefaultModel` 的
     返回配置 `id` 是**预设**的 id（`default_translate_chapter`）而不是模型 id——已用
     `git show HEAD:app/.../AiProfileRepository.kt` 逐行核对确认**是测试写错、不是搬错**。
     两条都说明同一件事：**「绿」必须来自 `clean` 或 `--rerun-tasks` 的真实执行**；
     且 Impl 测试断言要拿 `git show HEAD:<原文件>` 对齐语义，别凭直觉。
12. 建真实书源 corpus，再决定 native JS/parser，不先搬 `JsExtensions`。
13. 从 Feature catalog 逐域推进 M5；reader、TTS、service 使用 M6 专项门禁。

13.1. **M5-1：`ui/about` 转 CMP（进行中）—— 审计先于动手，先修 catalog 的旧判断。**
     完整审计见 [feature-slicing-audit-about.md](./feature-slicing-audit-about.md)。
     - 警告 **catalog 说它「边界小，适合首个样板候选」是错的**（该判断写在 M1 时代）。
       本体 7 文件 / 1442 行、表面只有 4 处 `android.*`，但依赖闭包牵出的缺口是它本体的
       1.5 倍以上：`TextCard`（39 引用方）/ `MarkdownBlock`（894 行，含 `Intent` 与
       Splitties 剪贴板）/ `CrashLogSheet`（`FileDoc`）/ `FileDoc`（**23 个引用方、深 SAF
       依赖，不迁**），外加 VM 的更新检查、诊断（崩溃日志/堆转储/`logcat`）、assets 读 md
       三组平台能力。**先审计依赖闭包再决定切法，别按行数挑「小页面」。**
     - 警告 **`miuix-blur` 只有 `miuix-blur-android`，无 desktop 变体**（编译探测：
       `top.yukonga.miuix.kmp.blur.*` Unresolved，而 `kmp.utils.*` 与 `kmp.shader.*` 可解析）
       ⇒ `MiuixAboutScreen`（519 行，直接调 `textureBlur`/`layerBackdrop`）**与 dict 查询面板
       同判据：platform island，留 `:app`**。由 androidMain 的 Route 按
       `ThemeResolver.isMiuixEngine` 分流，两支**复用同一 ViewModel/Contract**
       （M5 风险表：「必要时平台 Screen 复用同一 ViewModel/domain」）。
       连带 `MiuixUtils`（160 行）与 `BgEffect*`（907 行）**都不必搬**——它们只有 miuix 屏用。
     - 警告 **designsystem 的 commonMain 组件在 desktop 上无法独立做 Compose UI 测试**：
       `LegadoTheme.typography/colorScheme` 的 provide 点在 `:core:ui`（Android），
       designsystem 自己没有 provide 点 ⇒ `runComposeUiTest` 一律
       `IllegalStateException: No Typography provided`。构造 `LegadoTypography`（24 个无默认值
       字段）来提供主题等于为测试复制整套主题定义，属禁止的「为测试造重复抽象」。
       **共享层 UI 组件的验证因此止于「编译 + 门禁」，渲染证据由 Android 侧承担**——
       `smoke:compose-desktop-probe` 的探针能过是因为它渲染的是不依赖 `LegadoTheme` 的自制组件。
     - **M5-1a 已完成（2026-09-16）**：`TextCard` 从 `:core:ui/src/main` 上提
       `designsystem/commonMain`（`git mv`，包名不变 ⇒ 39 个调用方 import 零改动）。
       判据：文件内零 `android.*`/零 `R.`；闭包全在 designsystem；
       `labelSmallEmphasized`（material3 Expressive）在 desktop 的 CMP material3 1.9.0 实测有。
       验证：`:core:designsystem:compileKotlinDesktop` + `:core:ui:compileDebugKotlin` +
       `:app:compileAppDebugKotlin` + 四门禁全绿（**无需下调 G4 基线**）+ 干净重建全量集通过，
       用例计数不变（纯搬迁、零逻辑改动，原本也无测试）。
     - **M5-1b 已完成（2026-09-17）**：`MarkdownBlock`（894 行）从 `:app` 的
       `ui/widget/components/text` 上提 `designsystem/commonMain`（`git mv`，包名不变 ⇒
       6 个调用方 import 零改动）。**三处平台依赖全部用既有出口就地消除，零新增契约**：
       `Intent(ACTION_VIEW)` 兜底删掉走已存在的 `onClickLink` 回调；图片兜底走已存在的
       `LocalMarkdownImageHandlers`；剪贴板走 `LocalClipboard.setClipEntry(plainTextClipEntry(…))`
       ——共享层的**静默**写入能力**早已存在**（`PlainTextClipEntryFactory`），且刻意未与
       `:core:platform` 的 `Clipboard.setText`（会弹「复制完成」）合并。**通用经验：先 grep
       共享层有没有等价出口，再考虑新增契约。** 另：版本目录 `markdown-jvm` → `markdown`
       （经 Maven Central `.module` 变体清单核对，是真 KMP 制品）；`setClipEntry` 是 suspend，
       `clickable` 内需 `rememberCoroutineScope()` + `launch`。验证：desktop 编译 + `:app`/
       `:core:ui` 编译 + 四门禁全绿（无需下调基线）+ 干净重建全量集 712/1152 零偏离 +
       消费方解析变异（移走文件 ⇒ 5 个消费方 13 处 `Unresolved reference`，还原回绿）。
     - **M5-1c-pre 已完成（2026-09-17）**：about 闭包缺的两件共享 UI 资产收口到
       designsystem（同 M1-3x-pre 形态）——`MarkdownSheet`（`AboutSheets.kt` 拆出，**6 个包外调用方**：
       book/rss 的 debug+edit 屏、`SourceLoginSheets`、`MainActivity`；若随 about 文件一并进 Feature
       会让它们反向依赖 about）与 `EmptyMessage`（`:core:ui/src/main`，~40 调用方）。
       后者只有一处平台依赖且只在一个重载里：`@StringRes messageResId: Int` +
       `androidx.compose.ui.res.stringResource`（CMP 的同名 API 接 `StringResource` 不接 `Int`）
       ⇒ **按源集分家**：commonMain 放 String 重载、`androidMain/….android.kt` 放 Int 重载；
       宁可分源集也不在 commonMain 放假实现（AGENTS.md）。`EmptyMessage` 保包名
       （零 import 改动），`MarkdownSheet` **改包名**到 `...components.modalBottomSheet`
       （一个叫 `about` 的包不该出现在 designsystem；7 处 import 改动）。
       验证：三个模块编译 + 四门禁全绿（无需下调基线）+ clean 全量集 712/1152 零偏离 +
       消费方解析变异三轮（MarkdownSheet 7 文件 / EmptyMessage 25 文件 61 处 /
       只移走 commonMain 那份 ⇒ androidMain 重载断链）。
     - **M5-1c 已完成（2026-09-21）**，分两次提交：1c-1+1c-2 建共享层（三契约 + VM + 屏幕 +
       composeResources，不接线）；**1c-3 接线并删旧包** —— `appModule` 绑三个契约实现、
       `MainNavGraph` 的 `entry<MainRouteAbout>` 收 4 个 Effect 分支 + 按 `isMiuixEngine` 分流
       （`MiuixAboutScreen` 是 platform island，共享层引用它会成环）、`CrashReportActivity` 改
       import feature 的 `CrashReportScreen`，删 `:app` 的
       `ui/about/{AboutScreen,AboutSheets,AboutViewModel,AboutContract,CrashReportScreen}.kt`
       与 `ui/widget/components/log/CrashLogSheet.kt`（留 `MiuixAboutScreen` + `CrashReportActivity`）。
       **G4 随之下调**：`legacyHelp|ui/about` 5→1、`legacyBase|ui/about` 2→1、
       `legacyNaming|ui/about` 2→0（条目删除）。
       验证：单独 `clean` 后四门禁 + `:feature:about` 的 desktop 编译与 `testAndroidHostTest`
       （2 例）+ `:app:compileAppDebugKotlin` + `assembleAppDebug` 全绿；全量计数 **714/1154
       零偏离**；资源回归 about 140/140、tagrules 124/124。
       ⚠️ **变异验证实测出一个真实缺口**：删掉 `single<BundledTextReader>` 后
       `:app:compileAppDebugKotlin` **仍然绿** —— Koin 的 `viewModelOf` 是运行期解析，
       而 `:app` 目前没有宿主 graph creation test（`grep checkModules` 只命中 `App.kt` 的
       `startKoin`）⇒ **漏绑只在运行期暴露**。补该测试是独立切片（本片不引入 Koin test 依赖）。
       行为等价清单与未验证项见 `feature-slicing-audit-about.md` §8。
   - **M4-6 已完成（2026-09-21）：用户划线/高亮笔记域（`BookMarking`，`book_marks`）下沉
     `domain/marking` + `data/marking`。** M4-5c 关闭 AI 域后的第一片，选片判据是**依赖闭包**
     而非引用数：看起来更该做的 `Bookmark`（书签）闭包有 **42 个文件**（含 `Restore`/`Backup`/
     `BookmarkExporter`/`feature:reader:core`），是好几片而不是一片；`BookMarking` 则
     **已有 `BookMarkingGateway`**（端口是搬家不是新造）、**已有 `SaveMarkingUseCaseTest`
     护栏**、**无 `companion object` 常量**（避开 M4-3 `AiArtifact` 的 SQL 插值陷阱）、
     **不进备份/恢复**（无兼容面）。
     形态：`domain/marking` 装领域模型（12 字段逐字照抄，全 `val`、全字段判等）+ 端口；
     `data/marking` 装 `BookMarkingMapper` + `BookMarkingRepositoryImpl`（照抄迁移前每个方法的
     `withContext(Dispatchers.IO)`，`flowByBook` 的映射留在**流内**）。
     两处与模板同律的判断：① `setEnabled` **零调用方** ⇒ 随片从端口删除（DAO 方法保留，
     测试假实现的 override 同步去掉）；② `ContentProcessor` 此前直连
     `appDb.bookMarkingDao.getForChapterSync(...)`（渲染热路径）⇒ 端口**新增**
     `getForChapter(bookUrl, chapterIndex)` 把它收进来，顺带消掉一条 `appDb.` 计数。
     旧 `BookMarkingGateway`（在 `core:data` 的 `domain/gateway`）+ `BookMarkingRepository`
     一并删除，不留门面；13 个消费文件改 import，`appModule` 绑
     `single<BookMarkingGateway> { BookMarkingRepositoryImpl(get()) }`。
     新增用例 **15 例**（mapper 9 + impl 6）。impl 测试**必须存在**：端口有 `Flow` 方法，
     把流内映射换成 unchecked cast 能编译且九个 mapper 用例全绿，只有驱动多次发射才抓得住。
     验证：`clean` 后四门禁 + `:app:compileAppDebugKotlin` + `testAppDebugUnitTest` +
     `assembleAppDebug` + 13 个模块测试任务全绿；计数 **主集 714 不变 / 全量 1154 → 1169
     （+15）**，0 失败；`lintAppDebug` 仍是既有的 **5 errors**（warnings 79 → 78）。
     变异 **4 轮全红后回绿**（流内 cast / 漏映射 `styleJson` / `getForChapter` 参数不透传 /
     `chapterIndex` 归一化）——第 1 轮正是上面那条「mapper 测试抓不到」的判据。
   - **M4-7 已完成（2026-09-21）：首页模块域（`HomepageModule` / `HomepageCustomSet`）下沉
     `domain/homepage` + `data/homepage`。** 本片的价值在于它**推翻了两个"看起来该做"的候选**：
     ① `RssArticle` 实现 `BaseRssArticle`（`putVariable` / `putBigVariable`，**书源 JS ABI**）
     且被 `Rss` / `RssParser*` 等 object 单例消费 ⇒ 属 AGENTS.md 划的高行为风险区，没有 corpus
     之前不碰；② `BookContentProcess` 闭包 **22 个文件**（含 `feature:replacerules` 与
     `:core:designsystem` 的 `ContentProcessUiState`），远超单片。**先量闭包再选片**这条又救了一次。
     形态：`domain/homepage` 只装**端口**（`HomepageModulesGateway`，既有契约搬家、名字不改）
     —— 本域的**领域模型** `ModuleItem` / `CustomSetItem` 早在下沉前就住 `:core:model`，
     本片**不搬它们**（共享层是它们的正确归属，搬走只会让 2 个消费方多改 import 而无边界收益）。
     `data/homepage` 装两个 Mapper（**一实体一文件**，避免 JVM facade 擦除冲突）+
     `HomepageModulesRepositoryImpl`（**裸调 DAO、不包 `withContext(IO)`**——迁移前就是如此，
     与 M4-3 同侧、与 M4-1/2/4/6 相反）；
     `createCustomSet` 的 `System.currentTimeMillis()` → `systemTimeMillis()`（文件本身要进
     commonMain，M4-2 的判据）。端口 **20 → 18 个方法**：`setSortOrder` /
     `setCustomSetSortOrder` 零调用方 ⇒ 随片删除（DAO 方法保留）。
     新增用例 **20 例**（mapper 7 + 6、impl 7）。验证：`clean` 后四门禁 + `:app` 编译/单测/
     `assembleAppDebug` + 16 个模块测试任务全绿；计数 **主集 714 不变 / 全量 1169 → 1189
     （+20）**，0 失败；`lintAppDebug` 仍 **5 errors / 78 warnings**（均不变）。
     变异 **4 轮全红后回绿**，其中**第 4 轮首跑幸存**，暴露的是**测试结构**缺陷而非用例缺失：
     `deleteCustomSet` 的「先摘模块、再删集合」是跨两个 DAO 的顺序，而断言分别检查两个 fake
     **各自的**调用列表 ⇒ 对调两条语句后两边列表都仍只有一条，全绿。改为两个 fake 共享一个
     `CallOrder` 序列后同一轮立刻变红（通则已写进 skill reference）。
   - **M4-8 已完成（2026-09-21）：正文处理域（`BookContentProcess`）下沉，纯函数引擎一并搬。**
     `domain/contentprocess` + `data/contentprocess`。三处与前几片不同：
     ① **闭包要按"去注释后的代码"量**：naive grep `\bBookContentProcess\b` 命中 22 个文件，
     去注释与字符串后真实闭包是 **16** —— 6 个假阳性全是 KDoc 里"提到"该类型（含
     `:core:designsystem` 的 `ContentProcessUiState`，它自己在注释里写明**不含**实体字段）。
     这是「注释干扰扫描」的又一例（同变异锚点、portability-triage）。
     ② **纯函数引擎属于 domain，随之搬迁**：`BookContentProcessEngine`（192 行、零 Room/Android）
     此前只因历史原因躺在 `:core:data` 的 `io.legado.app.domain.model`，本片收进
     `domain/contentprocess`；它的测试从 `:app/src/test` 搬进该模块 `commonTest`，并把
     JUnit4/`GSON`/`MD5Utils` 换成 `kotlin.test`/`JsonCodec`/常量（引擎从不读
     `normalizedTextHash`），断言与输入逐字未动并新增 1 例。**这是首个 domain 模块自带测试的
     切片 ⇒ 主验证集首次因本方向下修**（`BASELINE_MAIN` 714 → **709**）。
     ③ **15 个 companion 常量是"看不见的第二副本"**：实体那份被 DAO 的 `@Query` 字符串插值成
     SQL 字面量，模型这份被 `:app` 的 `when` 与引擎使用 ⇒ 漂移无编译错误。按 M4-3 配方复刻
     全部 15 个，并让 mapper 测试**双向**逐值比对（对实体常量 **和** 对字面量）。
     端口 6 → 5 个方法（`flowForChapter` 零调用方，随片删；DAO 方法保留），副作用是本模块
     不再需要 coroutines 依赖。另把 `ContentProcessor` 里另一处 `appDb.bookContentProcessDao`
     直连也改走端口（端口本就有 `getForChapter`）。
     验证：`clean` 后四门禁 + `:app` 编译/单测/`assembleAppDebug` + 18 个模块测试任务全绿；
     计数 **主集 714 → 709 / 全量 1189 → 1204**，0 失败；`lintAppDebug` 仍 **5 errors /
     78 warnings**。变异 **5 轮全红后回绿**（`nextOrder` 漏 `+1` / `delete` 改走 `setEnabled` /
     漏映射 `kind` / 模型 `STATUS_DELETED` 改值 / 引擎去掉 `STATUS_ACTIVE` 过滤）。
   - **M2-5 已完成（2026-09-22，首片）：`core:model` 的 `io.legado.app.utils` 收口，8 个文件中的
     5 个已迁走。** M2 表里的「`utils.*` in `core:model` → 迁到所属 model/domain 包并改职责名」
     分几片做，本片选**调用面最小**的 5 个：`Utf8BomUtils` →
     `io.legado.app.domain.model.text.Utf8Bom`（**去 `Utils` 后缀**）、`StringHexExtensions`
     （`isHex`）与 `ByteArrayExtensions`（KMP 的 `ByteArray.indexOf`）→ 同 `text` 包、
     `AlphanumComparator` → `text` 包、`ReadRecordTimeFormatter`（`formatReadDuration`）→
     `domain.model.readrecord` 包；4 个测试文件随之搬包。剩余 3 个
     （`JsonStringExtensions` / `MapExtensions` / `StringSplitExtensions`，调用面 32+29+26）
     留给后续片 —— 特意**不**混进本片，免得一次改 60+ 文件而没法逐条复核。
     ⚠️ 本片又踩了一次「批量改 import 必须**先删旧再插新**」：`core:ui` 的两个文件插了新
     import 却留下旧的 `import io.legado.app.utils.isHex`，编译器报的是
     `Unresolved reference 'isHex'` 而**不是** 'utils'，极易误判成"新包没生效"。
     另：`:app` 的 `io.legado.app.utils`（同名包、不同模块）里的 `EncodingDetect.kt` 此前靠
     **同包**用 `ByteArray.indexOf`、根本没有 import ⇒ 这类调用方要"新增一个原本不存在的
     import"。G4 随之下调三条 `legacyNaming`（`Utf8BomUtils` 这个标识符在三个目录各少出现
     一次，`toc/rule/preview` 归零删除条目）。
     验证：`clean` 后四门禁 + `:app` 编译/单测/`assembleAppDebug` + 18 个模块测试任务全绿；
     计数 **709 / 1204 零偏离**（纯重命名，用例数不变）；`lintAppDebug` 仍 **5 errors /
     78 warnings**。本片无语义变更，**不做常规变异**，改用**消费方解析变异**：把
     `object Utf8Bom` 临时改名 ⇒ 3 个 `:app` 文件 7 处 `Unresolved reference 'Utf8Bom'`
     （证明调用方真的解析到新位置、没有遗留旧副本），随后还原回绿。
   - **M2-6 已完成（2026-09-22，第二片）：再迁 2 个，`core:model` 的 `io.legado.app.utils`
     只剩 1 个文件。** `StringSplitExtensions`（`splitNotBlank`，26 处引用）→
     `domain.model.text`；`MapExtensions`（`HashMap<String,*>.has` / `.get(key, ignoreCase)` /
     `MutableMap.getOrPutLimit`）→ 新建 `domain.model.collections.MapLookup.kt`
     （文件名从"形态命名"改成"职责命名"，扩展函数名不动 —— 它们就是 API）。
     消费方 **24 个文件**（9 个在 `:core:data`、1 个 `:data:rules`、14 个 `:app`）。
     ⚠️ 两个新情况：① `BaseSource.kt` 的 `has("User-Agent", true)` 是**隐式 receiver**调用
     （前面没有点），`\.has\(` 这类正则抓不到 ⇒ 靠编译器逐个报出来再补；② 反过来
     `has` / `get` 这种**通用名**不适合批量推断 import（会与 `Map.get`、任意 `.has(` 混），
     本片只对 `splitNotBlank` / `getOrPutLimit` 做批量，其余交给编译器。
     验证：`clean` 后四门禁 + `:app` 编译/单测/`assembleAppDebug` + 18 个模块测试任务全绿；
     计数 **709 / 1204 零偏离**；`lintAppDebug` 仍 **5 errors / 78 warnings**。
     同样无语义变更，**不做常规变异**：改用消费方解析变异（改名 `splitNotBlank` ⇒
     `:core:model` 内 `BookSearchScope.kt` 断链；Gradle 在拥有者模块就 fast-fail，下游不再编译，
     这是 M5-1c-pre 记过的现象）+ **静态检查**（`splitNotBlank` / `has` / `get` /
     `getOrPutLimit` 的旧 import 全仓计数均为 **0**）。
   - **M2-7 已完成（2026-09-22，收官）：`core:model` 的 `io.legado.app.utils` 归零 —— M2 表
     「`utils.*` in `core:model` → 迁到所属 model/domain 包并改职责名」这一行完成。**
     最后一个文件 `JsonStringExtensions`（`isJsonObject` / `isJsonArray`）→
     `domain.model.json`（新建包，比塞进 `text` 更准：这两个函数是 JSON 形状判定）。
     消费方 **32 个文件**（含 4 个 CMP Feature 的 VM 与 `:core:data` 的 androidMain）。
     ⚠️ **批量推断 import 会误伤**：脚本按"含 `isJsonObject`"给 `:core:platform` 的两个
     `JsonCodec.android/desktop.kt` 也插了 import，但 `:core:platform` **不依赖 `:core:model`**
     ⇒ 编译失败。那里只是同名方法/注释，并不需要 import。教训：全仓批量脚本必须
     **先确认目标模块已依赖符号所在模块**，否则"看起来一致的插入"会插到够不着的地方；
     宁可少批量、让编译器报。
     验证：`clean` 后四门禁全绿（**无需下调基线**）+ `:app` 编译/单测/`assembleAppDebug`
     + 18 个模块测试任务全绿；计数 **709 / 1204 零偏离**；`lintAppDebug` 仍 **5 errors /
     78 warnings**。**归零用静态检查确证**：`core/model/src` 下 `package io.legado.app.utils`
     的文件数 = **0**；全仓 `import io.legado.app.utils.(isJsonObject|isJsonArray)` = **0**。
     同样无语义变更，不做常规变异。
   - **M2-8 已完成（2026-09-22）：给 `:app` 补上 Koin graph creation test，关闭 M5-1c-3 实测出的
     缺口。** 新增 `app/src/test/java/io/legado/app/di/AppModuleGraphTest.kt`：逐条解析
     `appModule` 的 **83 条 `single<接口>` 绑定**（清单由 `single<` 派生），断言
     「**异常链上没有 `NoDefinitionFoundException`**」。
     三处形态判断（都是实测逼出来的，已写进 skill reference）：
     ① **不用 `checkModules()`** —— `io.insert-koin:koin-test:4.2.2` 在本环境解析不到
     （Koin 4 的 KMP 模块），改用零依赖的「逐条 `koin.get<T>()`」；
     ② **判据是「缺定义」而非「实例化成功」** —— 补完 `AppConfigStore.init` / `injectAsAppCtx`
     后仍有 1 条红（`ReadStyleGateway` 炸在 `ReadBookConfig` 的 `lateinit`，那个字段由
     `AppConfig.initialize` 在生产启动链的**更后面**赋值）。把环境性失败当失败，测试就会长期
     红在一处与绑定正确性无关的地方；而真正要防的漏绑（含**传递依赖**缺失）恰好都以
     `NoDefinitionFoundException` 出现，判据依然精准。环境性失败**打印但不断言**；
     ③ **宿主用 `Application::class` 而非项目的 `App`** —— 用 `App` 时 Robolectric 会跑
     `App.onCreate`，它死在 `LocalConfig` 的静态初始化（`App.kt:121`），根本到不了 Koin；
     改用干净宿主后需自己补 `AppConfigStore.init(app)` + `app.injectAsAppCtx()`（缺前者
     61/83 条红、缺后者同样，两条根因）。另需 `@Config(application = …, sdk = [35])`
     （Robolectric 不吃项目的 `targetSdk = 37`）。
     **对照变异**：把 M5-1c-3 删过的同一条 `single<BundledTextReader>` 再删一次 ⇒ 本测试
     **变红**并报 `NoDefinitionFoundException: No definition found for type
     'io.legado.app.feature.about.BundledTextReader'`，而当时 `:app:compileAppDebugKotlin`
     保持绿 —— 缺口闭环。已还原回绿。
     验证：`clean` 后四门禁 + `:app` 编译/单测/`assembleAppDebug` + 18 个模块测试任务全绿；
     计数 **主集 709 → 710 / 全量 1204 → 1205**（各 +1，即本测试）；`lintAppDebug` 仍
     **5 errors / 78 warnings**。

   - **M5-2a 已完成（2026-09-22）：`ui/config/labConfig` 迁进 `:feature:settings` ——
     M5 批次 2（低风险管理页）的第二站，也是 `ui/config/*` 这个 Feature 域的第一片。**
     按审计文档 [feature-slicing-audit-config.md](./feature-slicing-audit-config.md)
     的分级，labConfig 属 B 级「0 硬阻塞（仅系统分享 Intent）」；本片选它而不是 A 级的
     translation，判据是**依赖闭包**：它的两个依赖（`LabSettingsGateway` /
     `LocalPageEstimateMetrics`）**已经在共享层** ⇒ **零新增契约**，适合先把
     「域级模块 + 按页分片」这个形态跑通一次。**下一片应是 translation**。
     - **模块形态**：域级 `:feature:settings`，按子页分片填充（`lab/` 第一片），
       而不是一次搬完 78 文件的 `ui/config/*`——各子域依赖闭包差异太大。
     - **意外工作（M5-2a-pre）**：页面用的 `ClickableSettingItem` 还在 `:core:ui`
       （Android-only），按 M1-3x-pre 先例 `git mv` 到 `:core:designsystem/commonMain`
       （包名不变 ⇒ 36 个调用方 import 零改动）。它的 Miuix 分支撞上和 `SwitchSettingItem`
       当初**同一条**约束（`miuix-preference` 无 desktop 变体）⇒ 沿用 M1-3t 的窄契约，
       给 `MiuixPreferenceRenderer` 加 `arrowPreference(...)`，实现留 `:core:ui`
       （注入点 `PlatformServices.install()` 本就在，无需改 `:app`）。
     - ⚠️ **契约扩展被既有契约测试抓住**：`MiuixPreferenceRendererContractTest` 的匿名探针
       编译失败（缺新方法）。这是它该有的反应——扩展契约时所有实现方（含探针）必须跟上。
     - **新增 3 条用例**（迁移前这个 VM 零测试）：唯一 uiState 入口 / 导出诊断发 Effect
       且**不写设置**（这条分支写错会退化成「点了导出顺带写一次设置」）/ 诊断计数初值。
     - **死资源**：`:app` 侧 10 条 lab 文案 ×4 语言删除；保留 `lab_setting`
       （`ConfigNavScreen` 用）与 `lab_page_estimate_diagnostics_share_title`
       （`MainNavGraph` 用）。
     - 验证：`clean` 后四门禁全绿（无需下调基线）+ 新模块 desktop 编译与
       `testAndroidHostTest`（3 例）+ `:app` 编译/单测/打包；计数
       **710 → 713 / 1205 → 1208**（各 +3）；资源 4/4 逐字一致（回归 about 64/64）；
       `lintAppDebug` 仍 **5 errors / 78 warnings**。
       未验证：实验室页的渲染与交互（开关联动显隐、Miuix 下 `ArrowPreference` 外观、
       导出→系统分享整条路径）需真机冒烟。
   - **M5-2b / M5-2c 已完成（2026-09-22）：translation 页的三个前置资产上提。**
     `ui/config/translation` 用了三个还在 `:core:ui`（Android-only）的组件，故先按
     M1-3x-pre 先例逐个上提（均 `git mv`、**包名不变** ⇒ 调用方 import 零改动）：
     `AppSlider.kt`（62 行，含 `sliderAccessibility`，零阻碍）、
     `DropdownListSettingItem`（87 行，Miuix 分支撞 `miuix-preference` ⇒ 契约加
     `overlaySpinnerPreference`）、`SliderSettingItem`（268 行，**要带资源**——它用 `:core:ui`
     自己的 `R`，故 4 条文案 ×4 语言搬进 designsystem 的 composeResources，值逐字照搬；
     `:core:ui` 副本不删，`InputSettingItem.kt` 仍在用其中两条）。
     契约面刻意**不出现 miuix 类型**（`DropdownItem` → `List<String>`、
     `startAction` → `ImageVector?`），否则共享层签名会绑死在 Android-only 制品上。
     ⚠️ **契约被扩展两次，两次都被 `MiuixPreferenceRendererContractTest` 的探针抓住**
     ——这是它该有的反应：扩展契约时所有实现方（含测试探针）必须显式跟上。
     验证：四门禁全绿 + designsystem 的 desktop/`androidHostTest` 编译与测试 +
     `:core:ui` / `:app` 编译 + 全模块测试；计数 **713 / 1208 零偏离**（资产移动，无用例增减）；
     文案 4 条 ×4 语言与 `:core:ui` 逐字一致（脚本核对）。
     未验证：三个组件在 **Miuix 引擎下**的实际外观（下拉弹层、滑块无障碍语义）无自动化
     覆盖——成功路径在 commonTest 里既跑不到也没有意义。需真机在 Miuix 主题下人工核对。
   - **M5-2d 已完成（2026-09-23）：translation 子页本体迁进 `:feature:settings/translation/`。**
     审计的 A 级判定（零 app 私有依赖）实测成立：VM 只依赖 `TranslationSettingsGateway`，
     Screen 只多一个 `TranslationConstants` ⇒ **零新增契约**。它排在三片资产上提之后才做，
     纯粹是因为那三个 UI 组件当时还在 `:core:ui`。
     ⚠️ **与 labConfig 的形态差异**：本页**没有**平台动作——`TranslationConfigEffect` 是
     **空的** sealed interface（迁移前就如此），所以宿主那侧没有 Effect 要收，`MainNavGraph`
     的 entry 只剩「取 VM、收 state、接导航回调」。**Route 保留纯粹是为了
     `koinViewModel()` 不进共享层**，不是为了隔离平台动作。
     新增 3 条用例（迁移前零测试）：初值来自 gateway / `SetProvider` 经唯一入口下发 /
     另两个 Intent 各映射到自己的字段（防 `when` 分支复制粘贴串行——穷举由编译期兜住，
     但"处理错字段"不会）。
     死资源：9 条里 7 条删除，保留 `translation_config`（`ConfigNavScreen` 用）与
     `ai_config`（AI 配置页在用）。
     验证：四门禁全绿 + 新模块 desktop 编译与 `testAndroidHostTest`（**6 例** = lab 3 +
     translation 3）+ `:app` 编译/单测/打包 + 全模块测试；计数 **713 → 716 / 1208 → 1211**；
     资源 **40/40 逐字一致**（删副本前跑）；lint **errors 仍 5**（warnings 78→95，增量
     全是联网查询的 `GradleDependency`，与改动无关）。
     未验证：页面渲染与交互（下拉选择、滑块的默认值/范围/步进、
     `provider == PROVIDER_APP_AI` 时才出现的跳转条目）需真机冒烟。
   - **M5-3a-pre / M5-3b 已完成（2026-09-23）：customTheme 子页。**
     M5-3a-pre 上提 `ColorPickerSheet`（182 行 + 4 条文案；依赖链浅、Miuix 的 `ColorPicker`
     在 `miuix-ui` 有 desktop 变体 ⇒ **不撞** `miuix-preference`）。
     M5-3b 迁页面本体：Contract/VM/Screen 进 `:feature:settings/customtheme/`，
     `RouteScreen` 的两个 Effect 留宿主（`ThemeStore.editTheme(…).primaryColor(…).apply()` 与
     Toast）——**这是三页里唯一「Route 留下是因为真有平台动作」的**（translation 只为了
     `koinViewModel()`，labConfig 是 `ACTION_SEND`）。
     ⚠️ **两处不在编译期暴露**：① CMP 的 `stringArrayResource` 返回 `List<String>`
     （Android 是 `Array<String>`）⇒ 6 个 array 的调用点要 `.toTypedArray()`，且覆盖情况
     与 `:app` 一致（三个 `*_value` 只在默认语言、靠 fallback）；
     ② `Integer.toHexString` 在 `commonMain` 不存在，且它与 `Int.toString(16)` 对**负数**
     不等价（颜色值可能为负）⇒ 必须 `.toUInt().toString(16)`，否则颜色显示错而编译不报。
     **本批第一片没有死资源**：15 条 string + 6 个 array 在 `:app` 侧仍被 themeManage 等共用。
     验证：四门禁全绿 + 新模块 `testAndroidHostTest`（**9 例**）+ `:app` 编译/单测/打包；
     计数 **716 → 719 / 1211 → 1214**；资源 **72/72 逐字一致**；lint errors 未增加。
     未验证：两套 UI 切换、下拉取值、`ThemeStore` 那条路径是否真的跟上。需真机冒烟。
   - **M5-4a-pre / M5-4b 已完成（2026-09-23）：ai/summary 子页 —— ai 域的第一片。**
     M5-4a-pre 上提 `InputSettingItem`（133 行 + 3 条文案；本次**删掉** `:core:ui` 的
     `edit`/`text_default`/`confirm` 副本，因两个 settingItem 都迁走后它们零引用）。
     M5-4b 迁页面：本批第一个「**VM 自己带着平台依赖**」的页——迁移前 VM 直接
     `appCtx.getString(R.string.x)`（3 处）⇒ 照 about 先例改成「发枚举 + UI 侧查表」。
     ⚠️ **Effect 必须分成 `ShowMessage`（资源枚举）与 `ShowRawMessage`（运行期文本）两个**：
     `save()` 失败原文是 `error.message ?: getString(ai_config_save_failed)`，
     「有异常文案就用它、没有才回落资源」，合成一个参数会丢这个 fallback。
     另一个点：**本页提示不走宿主**（Screen 自己收 Effect 显示 Snackbar），
     宿主只剩「取 VM、收 state」——与 translation 同形；加上 labConfig（宿主解释
     `ACTION_SEND`）与 customTheme（宿主调 `ThemeStore` + Toast），
     **同一 Feature 域里 Route 留下的理由已出现三种**。
     **G4 随之下调**：`appCtx|app/main/io/legado/app/ui/config/ai/summary` **1 → 0**
     （条目删除）——删掉那处 `appCtx` 后**门禁主动拦下并要求下调**，棘轮生效。
     新增 `:feature:settings` 对 `:domain:ai` 的依赖（`AiProfileGateway` 是 M4 建好的共享端口）。
     验证：四门禁全绿 + 新模块 `testAndroidHostTest`（**13 例**）+ `:app` 编译/单测/打包；
     计数 **719 → 723 / 1214 → 1218**；资源 **120/120 逐字一致**。
     未验证：Snackbar 实际弹出、滑块/输入交互、编辑提示词弹层。需真机冒烟。
   - **M5-4c 已完成（2026-09-23）：ai/prompt 子页（本域最重的一页）。**
     两处结构性改动：① `AiPromptTaskItem` 不再存 Android 资源 id（`nameResId: Int`）
     ——**资源句柄泄漏进 UI 状态**，改成 `AiPromptTask` 枚举 + UI 侧查表；这条**编译期不报**。
     ② 保存成功的提示是 **Toast**（`appCtx.toastOnUi`）而非页面里的 Snackbar ⇒ 改由 VM 注入的
     `Toaster`（`:core:platform` 既有契约）直发，其余提示照旧走 Effect。
     ⚠️ **本片最有价值的发现**：同一个 XML 片段，**aapt2 会展开 `\"` / `\'`，CMP 的资源生成器
     不展开** ⇒ 照搬源 XML 会把多余的反斜杠发给用户，**而 commonMain 照样编译通过**。
     `verify-compose-resources.py` 报 7 条不一致，修正 13 处后 **226/226**。
     ⇒ 搬这类文案必须在 CMP 侧写裸字符；**以那个脚本为准，不是以编译器为准**（已写进 skill）。
     **G4 随之下调**：`appCtx|app/main/io/legado/app/ui/config/ai/prompt` **1 → 0**（条目删除）。
     死资源 33 条删除。验证：四门禁全绿 + `:app` 编译/单测/打包 + 全模块测试；
     计数 **723 / 1218 零偏离**（**本片未新增用例**——VM 会调 `getString(Res.string.*)`，
     构造即触碰 CMP 资源运行时，其在 `androidHostTest` 下的可行性当时未验证）。
   - **M5-4d 已完成（2026-09-23）：探针量出「CMP 资源在 `androidHostTest` 下不可读」。**
     做法同 M1-4 的桌面探针：在 `:feature:settings` 的 `androidHostTest` 里直接
     `getString(Res.string.confirm)`，抛 `MissingResourceException: ... Android context is not
     initialized.`（即使 `@Config(application = Application::class)`）。探针验证完即删除，
     结论写进 skill。
     **后果是一条架构判据**：① 只用于展示的文案 ⇒ VM 发枚举、UI 侧查表（about / ai/summary
     的做法），VM 因此**可构造、可测**；② VM 真要把字符串当数据用（ai/prompt 的默认提示词
     写回 gateway）⇒ 也应**注入而非在 VM 里 `getString`**。ai/prompt 目前是后者，
     **属已知待改项**，改完即可补回测试。
     本片无代码产出（探针已删），只有 skill 与文档更新；计数不变。
   - **M5-4e 已完成（2026-09-23）：把 M5-4d 那条判据落地，补回 ai/prompt 的用例。**
     新增 `AiPromptStringSource`（可注入的资源字符串来源）：默认提示词与「保存成功」的
     Toast 文案都改为从它取，生产实现 `composeResourcePromptStrings()` 读 composeResources
     并由 `:app` 绑定 —— **测试实现给假值 ⇒ VM 重新可构造**。
     **补回 4 条用例**（M5-4c 欠下）：init 的取值优先级（已存优先于默认，写反会让用户
     自定义提示词每次被重置）/ 保存成功走 Toast 而非 Effect（本页与 ai/summary 的差异）/
     失败 fallback（有异常文案用它、没有才回落资源）/ 重置单个用默认值并提示成功。
     验证：四门禁全绿 + `:feature:settings` 的 `testAndroidHostTest`（**17 例**）+
     `:app` 编译/单测/打包 + 全模块测试；计数 **723 → 727 / 1218 → 1222**；资源 226/226 未变。
   - **M5-5a 已完成（2026-09-23）：ai 主入口页迁进 `:feature:settings/ai/`。**
     本批**最干净**的 VM（只依赖 `AiProfileGateway`，不碰 `R`/`appCtx`/`GSON`）⇒ 可测性无碍。
     两条提示是**硬编码英文**（迁移前就如此，原样保留）⇒ `AiConfigEffect.ShowMessage` 带裸
     `String` 而非枚举，与本域另两页不同。
     新增 4 例：模型按 provider 归组且孤儿模型被过滤 / 「当前模型」优先取默认翻译预设指向的
     模型（写反会让主页面与翻译页不一致）/ 没有预设时退到第一个模型 / 两条硬编码提示逐字。
     ⚠️ 写用例时被纠正了一个**既有语义**：`modelCount` 是原始模型数（**含**孤儿），
     与过滤后的 `models.size` 本来就不等 —— 已把差异钉进注释。
     死资源 11 条删除。验证：四门禁全绿 + `:feature:settings`（**21 例**）+ `:app` 编译/单测/
     打包 + 全模块测试；计数 **727 → 731 / 1222 → 1226**；资源 **164/164**。
   - **M5-5b 已完成（2026-09-23）：AiModelEdit 页。**
     唯一的非机械改动是 ai 域两处 `GSON` 之一 → `JsonCodec.fromJsonObject`（`:core:platform`
     的 expect object）。⚠️ 顺带修掉空安全差异：`GSON.fromJson` 是平台类型，返回 null 时
     `getOrDefault` 兜不住 ⇒ 后面会 NPE；改成 `getOrNull() ?: …`（对正常 JSON 等价，
     把潜在 NPE 变成明确回落）。
     另一个跨文件的坑：`formatTokenLimit` 原是 `AiModelEditScreen.kt` 里的 `internal`，
     **同包的 `AiProviderEditScreen` 也在用** ⇒ 迁走后 `:app` 编译不过，已在 `:app` 留一份
     **临时副本** `ai/TokenLimitFormat.kt`（ai 域收官时删除）。
     **G4 随之下调**：`gson|…/ui/config/ai` **2 → 1**。
     新增 4 例：JSON 经 `JsonCodec` 反序列化进状态 / 非法 JSON 回落不崩 /
     `initialized` 后流刷新**不覆盖**用户正在编辑的字段 / 保存成功发提示+返回并回写 id。
     死资源 3 条。验证：四门禁全绿 + `:feature:settings`（**25 例**）+ `:app` 编译/单测/打包 +
     全模块测试；计数 **731 → 735 / 1226 → 1230**；资源 **176/176**。
   - **M5-5c 已完成（2026-09-23）：AiProviderEdit 页 —— ai 域收官。**
     `ui/config/ai` 整个目录清空（四片：M5-5a 主入口 / M5-5b modelEdit / M5-5c providerEdit /
     M5-4b-4c summary+prompt）。本页是本域平台依赖最集中的一页（同时用 `R`/`appCtx`/`GSON`）：
     ① `GSON.fromJson` → `JsonCodec.fromJsonObject`（ai 域最后一处 GSON，顺带修空安全差异）；
     ② 三处 `appCtx.getString` → 注入的 `AiProviderStringSource`（沿用 M5-4e 的模式——
     M5-4d 探针已量出 VM 里不能直接 `getString`，否则不可测）。
     其余提示保持硬编码英文（迁移前就如此）。
     **G4 同时下调两条**：`appCtx|…/ui/config/ai` 与 `gson|…/ui/config/ai` 各 **1 → 0**
     （条目删除）——本批第一次一片内同时归零两个维度。
     顺带清掉跨片遗留：M5-5b 为未迁的 `AiProviderEditScreen` 在 `:app` 留的
     `ai/TokenLimitFormat.kt` **临时副本已删**（共享层同名 `internal fun` 与它同包可见）。
     新增 5 例：init 填充且 initialized 后不覆盖用户编辑 / 测试连接「0 个模型」用注入文案 /
     「N 个模型」把 count 传进格式参数 / 失败时拼「兜底文案: 详情」（含无详情分支）/
     `defaultParamsJson` 经 `JsonCodec` 反序列化。
     ⚠️ 前三条钉的是注入改动——假文案源给带标记的假值，所以「真的走了注入路径」被断言验证。
     死资源 24 条删除（保留 `hide_password`/`show_password`/`ok`/`delete`）。
     验证：四门禁全绿（G4 按下调）+ `:feature:settings`（**30 例**）+ `:app` 编译/单测/打包 +
     全模块测试；计数 **735 → 740 / 1230 → 1235**；资源 **276/276**。
     **下一步**：`otherConfig` / `backupConfig`（需先抽 `WebService` / `ImportOldData` 胶水）
     或 `themeConfig`（撞 `ui.main.*`，成本更高）。
   - **M5-6a 已完成（2026-09-23）：删除 `ui/config` 下 3 个零引用的 `@Deprecated` 兼容壳。**
     `ImportBookConfig`(14) / `ReadMangaConfig`(39) / `BookshelfConfig`(56)，共 109 行——
     都是「设置下沉到 gateway 时留下的过渡壳」，调用方早已全部改走 gateway（脚本扫
     `.kt`/`.java`/`.xml`/`.kts` 确认为全仓零引用）。**删而不迁**：依赖虽已全在共享层，
     但把死代码换个地方放没有价值。
     ⚠️ git 跟踪的生成物 `app/src/appNoR8/generated/baselineProfiles/*-prof.txt` 里有
     `BookshelfConfig` 的旧条目——**不手改**（编译器会忽略不存在的类，设备重跑即消失）。
   - **M5-6b 已完成（2026-09-23）：`ConfigNavScreen` → `:feature:settings/nav/`。**
     `:feature:settings` 里**第一个不是子页面的成员**：设置域首页（9 项导航列表），
     原住 `ui/config` 根包。迁入标志模块从「子页面集合」变成「域」。
     - **零硬阻塞**：9 个导航动作全是 `() -> Unit` ⇒ 宿主**无需改 entry**（本就已写成回调形态），
       只换一行 import。无 VM / 无 Effect / 无 `R.string` 以外资源。
     - 10 条文案：7 新增、3 已存在（`ai_config`/`translation_config`/`lab_setting` 此前因
       **这一页还在 `:app`** 而两边都有）。**无死资源**——7 条在 `:app` 侧仍被其他未迁子页引用。
     - `ConfigTag`（12 行常量）**留在 `:app`**：只被 `MainIntent` 用于 deep-link 路由 tag 映射。
     - **本片不加测试**：无状态、无 VM 的纯组合函数；`:feature:settings` **没有 Compose UI
       测试基建**（本模块 8 个测试文件全是 VM 测试），要写就得先引 `compose.ui:ui-test` ——
       与 M2-8 拒绝 `koin-test` 同判据：**不在接线片里顺带引入测试依赖**。
       宁可写明「未加及原因」，不写只把源码再断言一遍的假测试。
     - 验证：四门禁全绿 + `:feature:settings`（30 例）+ `:app` 编译/单测/打包 + 全模块测试；
       计数 **740 / 1235 零偏离**；资源 **208/208**；lint 仍 **5 errors / 95 warnings**。
       未验证：首页渲染与 9 条导航的实际跳转，需真机冒烟。
     **下一步**：`otherConfig` / `backupConfig`（各需先抽 1–2 个平台契约）或 `themeConfig`
     （撞 `ui.main.*` 的 10 个 `Launcher*` 图标资源）。
   - **M5-7 已完成（2026-09-23）：`downloadCacheConfig` → `:feature:settings/downloadcache/`。
     剩余子域里最小的一块（4 文件 420 行），也是本批**第一次需要新抽平台契约**。**
     新契约 `DownloadCachePlatform`（5 成员）收掉迁移前 VM+Screen 的 5 处平台直连：
     引擎并发上限 / OkHttp 缓存大小 / OkHttp 缓存清理 / 缓存目录清理 / 图片内存缓存重分配。
     - **两条边界判断**：① `maxDownloadConcurrency` 走契约而非在共享层写 `const val 8`
       —— 否则引擎与共享层各有一份上限，日后引擎放宽会静默漂移（表现为「滑块拉不到底」）；
       ② `clearCacheDirectories` 与 `:core:data` 的 `ClearBookCacheUseCase` **不合并**
       —— 条目清理与目录清理是两步，合并会让 use case 失去独立性。
       `HttpCacheKind` 只保留两值枚举，不带 Android `HttpCacheType` 的 `dirName` / `maxSize`。
     - **一处结构变更**：UI state 新增 `maxDownloadConcurrency`（迁移前 Screen 自己读
       `CacheBook`，共 3 处）。该字段**刻意不给默认值** —— 给默认值 = 共享层写死第二真源。
     - **G4：两根棘轮同时下调 + 两个「净变化为零」的上调**。
       `ui/config/downloadCacheConfig` 的 `appCtx`(1)/`legacyHelp`(3)/`legacyNaming`(1) 全部归零；
       `io.legado.app.platform` 的 `legacyHelp` 2 → **5**、`legacyNaming` 2 → **3**，
       但净变化为零（5 = 2+3，3 = 2+1）。`platform` 是「共享契约的 Android 适配」指定住所，
       **不在 `reportOnlyAreas`** ⇒ 上调必须显式登记并注释「换住所、非新增债」（先例 M5-1c）。
     - ⚠️ **踩到门禁盲点**：`legacyHelp` 规则行尾锚定为 `$`，故
       `import X as Y` 形式**不被计入**。最初为绕开成员遮蔽写的别名导致只 +2 而非 +3
       （本该搬 3 处）——**凭空少记一笔**。已改成「顶层私有函数 + 朴素 import」让计数如实；
       盲点本身记进 `legacy-architecture-report.md` §7，附修复方向（属独立切片）。
     - 新增 7 例（迁移前零测试），重心是**契约交互**：并发上限进 state 并夹取 /
       字节→MB 换算 / 改图片缓存「写设置 + 重分配」两件事都发生 / 清封面缓存调平台并归零 /
       清书缓存「先清条目再清目录」且不碰 OkHttp / 收缩数据库只走 use case / 设置流刷新 state。
     - 验证：四门禁全绿 + `:feature:settings`（**37 例**）+ `:app` 编译/单测/打包 +
       全模块测试；计数 **740 → 747 / 1235 → 1242**；资源 **308/308**；死资源 12 条；
       lint 仍 **5 errors / 95 warnings**。
       未验证：页面渲染、四个确认对话框、四条真实平台路径（清 OkHttp / 清缓存目录 /
       图片缓存重分配 / 收缩数据库），需真机冒烟。
     **下一步**：`backupConfig`（1144 行；抽 `Permissions` / `ImportOldData`）或
     `otherConfig`（1157 行；抽 `WebService` 状态 / `Permissions` / 最后 1 处 `GSON`）。
   - **M5-8a 已完成（2026-09-23）：`otherConfig` 的*逻辑层*（Contract + VM）→
     `:feature:settings/otherconfig/`。** 本片是**按层分片**：勘察发现 VM(374)/Screen(253)
     只依赖 `R`，平台耦合集中在 `RouteScreen`(146) 与 `DirectLinkUploadBottomSheet`(215)；
     而 Screen 需要 54 条文案 + 4 个 `string-array`（其中 `default_app_variant` 的条目是
     `@string/*` **间接引用**，要展开成字面量——共享层的数组约定是纯字面量）⇒ 拆成
     「逻辑迁移」（本片）与「页面+资源迁移」（下一片）两个风险维度。
     - VM 的八个依赖里**七个本来就是共享契约**（先前切片的成果）⇒ 本片只处理两处耦合：
       ① `OtherConfigMessage.resId: Int?`（`@StringRes`）→ `OtherConfigMessageRes` 枚举 +
       `OtherConfigMessageText.kt` 查表（`AboutMessage.localizedText()` 的模式）；
       ② `AppLog.put(msg, throwable)` → `AppLogStore.put(...)`（迁移前是**两参**形式，
       不含轻提示与 Logcat 直投 ⇒ 行为等价）。
     - ⚠️ **门禁拦下一个「顺手改好」的念头**：把 RouteScreen 的 `Toast.makeText` 换成共享
       `Toaster` 很自然，但 `ToasterProvider` 是 service locator，会让
       `ui/config/otherConfig` 成为 `coreProvider` 的**新区域**（门禁实测报
       「首次出现 2 处；新区域必须为零」）⇒ **改回 `Toast.makeText`**。棘轮的意图正是
       「新代码别引入新的静态委托」——这次是门禁赢了直觉。
     - ⚠️ **一处我该先发现的重复**：`:app` 里**早已有**一份 `OtherConfigViewModelTest`（5 例），
       到完整构建才暴露（编译不过）。已**合并**并删除旧文件 —— 原用例有 3 条我漏掉的覆盖
       （语言变更同时刷新 uiState、两条消息排队与逐条确认、直接链接**成功**路径）。
       ⚠️ 计数因此是**净 +4**（9 新 − 5 旧），不是 +9。教训写进 checklist：
       **迁 VM 前先 grep 类名找它已有的测试**。
     - ⚠️ 还抓到一条**我自己写错的断言**：「必填缺失」用例断言「弹层保持打开」却没先打开弹层
       ⇒ 假通过。已改成先 `ShowOverlay` 再确认。这类恒真断言比没有测试更糟。
     - 验证：四门禁全绿（**G4 无需基线变动**）+ `:feature:settings`（**46 例**）+
       `:app` 编译/单测/打包 + 全模块测试；计数 **747 → 751 / 1242 → 1246**；
       资源 **272/272**；死资源 3 条；lint 仍 **5 errors / 95 warnings**。
     **下一片**：迁 `OtherConfigScreen`（54 条文案 + 4 个数组，重点是展开 `@string/*`）；
     `RouteScreen` 与 `DirectLinkUploadBottomSheet` 是宿主壳，按现状留 `:app`。
   - **M5-8b 已完成（2026-09-23）：`OtherConfigScreen` → `:feature:settings/otherconfig/`
     —— otherConfig 页面收官。** 51 条文案 ×4 语言 + 4 个 `string-array`。承接上片的判断：
     `OtherConfigRouteScreen`(146) 与 `DirectLinkUploadBottomSheet`(215) 留 `:app`。
     - **数组迁移的四个坑**（都不是搬运）：
       ① CMP 的 `stringArrayResource` 返回 `List<String>`（androidx 返回 `Array<String>`）⇒
       每个调用点要多一次 `.toTypedArray()`；
       ② 显示数组逐语言本地化、`*_value` 只放默认 `values/`（本模块既有约定）——
       真正的不变量是「各语言显示长度 == **默认**值数组长度」，不相等会让下拉框
       **静默选错值**（编译器/lint 都不报），已脚本逐语言核对 4/4 OK；
       ③ `default_app_variant` 的条目是 `@string/*` **间接引用**，Android 逐项逐语言解析而
       CMP 表达不了 ⇒ 展开成字面量，从而**固化当前回落行为**：`all_version` 只在
       `values/`+`values-zh-rCN/` 定义 ⇒ zh-rHK/zh-rTW **真的显示英文 `All Version`**。
       逐字保留 + 写进 XML 注释（改它属产品文案决策）；
       ④ 同名可既是 string 又是 array（`language`），实测**一条不加别名的 import 能同时引入两者**。
     - ⚠️ `verify-compose-resources.py` **只比对 `strings.xml`**，数组不在覆盖内 ⇒ 单独脚本核。
     - 保真取舍：迁移后的 Screen **保留原文两处缩进瑕疵**，让 `git mv` 的 diff 只反映语义改动。
     - 验证：四门禁全绿（**G4 无需基线变动**）+ `:app` 编译/单测/打包 + 全模块测试；
       计数 **751 / 1246 零偏离**（本片无测试增减 —— `OtherConfigScreen` 是无 VM 的纯组合函数，
       与 M5-6b 的 `ConfigNavScreen` 同一判据）；资源 **464/464**；死资源 2 条；
       lint 仍 **5 errors / 95 warnings**。
     **下一步**：`backupConfig`（1144 行，抽 `Permissions` / `ImportOldData`）、
     `themeManage`（1138 行，需 `SavedTheme` / `ThemePackageManager`）、或 `coverConfig`
     （1534 行，但**需先下沉整条相册存储链** + 重新设计 `CoverAlbumImageInput` 的
     `java.io.InputStream`，是更大的一档）。
   - **M5-9a 已完成（2026-09-23）：`backupConfig` 的逻辑层（Contract + VM）→
     `:feature:settings/backup/`。** 沿用 otherConfig 的按层分片。
     勘察发现它**已经有干净的宿主壳**（`BackupConfigScreen.kt` 里 `BackupConfigRouteScreen`
     与页面本体职责已分开）⇒ 形态比 `themeManage` / `coverConfig` 干净。
     - 两个新契约/枚举：① `BackupConfigText`（11 值）+ 一张映射表派生**两个适配器**
       （`@Composable localized()` 给对话框标题、`suspend localizedText(argument)` 给宿主收 Effect）；
       ② `BackupIgnoreStore` + `BackupIgnoreKind` 四值枚举，替掉 `help.storage.BackupConfig`
       的四组「忽略集」（语义逐条核过：恢复/备份 × 配置项/数据库表）。
       另有两处内联：`Uri.parse(uri).toString()` → `uri`（恒等）；
       `String?.isContentScheme()` → 内联 `startsWith("content://")` + 注明出处。
     - ⚠️ **4 个 `saveXxx` 刻意不合并** —— 它们不对称（两个写两组并关弹层、两个只写一组且不关）。
       合并会静默改掉弹层关闭时机，已写成测试断言。
     - ⚠️ **坑一：按行删 `strings.xml` 会破坏 XML**。`restore_fail_with_error` 的值含**字面换行**，
       按行删留下孤立 `%1$s</string>`；Kotlin 编译照样过，要到 `mergeAppDebugResources` 才报。
       ⇒ 改为按元素删（DOTALL 正则）+ **删完必须 `ET.parse` 校验**（前几片都做，本片漏做）。
     - ⚠️ **坑二：弃用壳会藏住棘轮该看的耦合**。同包那个零引用 `object BackupConfig` 逼得 VM 只能写
       **全限定名**调真身，而门禁规则是 import 锚定的 ⇒ 那些耦合**一处都没被计入**。换成契约后
       `legacyHelp|…/platform` **5 → 6** —— **净债务没变，是账本变准了**。顺带删掉那个弃用壳（零引用）。
     - 新增 10 例（迁移前零测试），重心是 kind 配对 + 那处不对称；
       ⚠️ **只钉同步可观测的行为**（IO 尾巴的结果分支留给真机）。
     - 验证：四门禁全绿 + `:feature:settings`（**56 例**）+ `:app` 编译/单测/打包 + 全模块测试；
       计数 **751 → 761 / 1246 → 1256**；资源 **472/472**；死资源 7 条；
       lint **5 errors / 95 warnings**（filtered 245→244、23→21）。
     **下一步**：`backupConfig` 页面本体（**要先把它那个文件拆成宿主壳 + 页面**）、
     `themeManage`（其 `SavedTheme` 携带 `ThemePackageManifest` ⇒ 与 `coverConfig` 撞同一条存储链）、
     或 `readConfig` / `themeConfig`（后者最重）。
   - **M5-9b-pre 已完成（2026-09-23）：`CardTabRow` 从 `:core:ui` 上提到
     `:core:designsystem/commonMain`。** 纯搬运、**包名不变** ⇒ 10 处 `:app` 消费方
     import 零改动。这正是 `AppTabRow.kt` 的 KDoc 当初预告的那一步（「`CardTabRow.kt`
     刻意没跟着搬，只有 `:app` 消费方，**等真出现时按同一配方再搬**」）——M5-9b 让前提出现。
   - **M5-9b 已完成（2026-09-23）：`backupConfig` 页面本体 + 两个选项 sheet →
     `:feature:settings/backup/`。**
     - **勘察发现**：`:app` 的 `BackupConfigScreen.kt` 里**宿主壳（80–150 行）与 5 个 UI
       composable 边界干净** —— 18 处平台用法全在壳内 ⇒ 按职责拆开，壳留 `:app`
       （改名 `BackupConfigRouteScreen.kt` 与函数同名），本体搬走。
     - **`ConfirmDialog` 的可见性刻意没动**（迁移前 `public` 但只被同文件用）：
       可见性属 API 表面，不该夹在搬迁里收紧。
     - ⚠️ **漏查跨文件消费方**：`BackupOptionSheet` / `RestoreOptionSheet` 还被 `:app` 的
       `HomeScreen` 使用 —— 事先只查了自身声明与文案，编译那步才暴露。教训并入 checklist
       （与 M5-8a「VM 测试漏查源模块」同类）。
     - **数组**：`backup_sync_mode` 与 M5-8b 的 `default_app_variant` 同形态（`@string/*`
       间接引用 ⇒ 展开成字面量、4 语言都写）；`_value` 只放默认 `values/`。
     - **死资源 12 条；另 29 条看着能删其实不能** —— 最意外的是遗留的
       `res/xml/pref_config_backup.xml`（Android `PreferenceScreen`）仍引用
       `auto_check_new_backup_s` / `sub_dir` / `web_dav_url` 等；另有 `OnboardingScreen` /
       `ClickActionConfigSheet`。⇒ **删文案前必须全仓（含 `res/xml/*.xml`）核引用**。
     - ⚠️ **顺手修掉 M5-9a 留下的一例竞态测试**：本片跑全量时
       `恢复网络备份与测试连接都先进对应对话框` 挂了（`expected:<Loading(Loading)> but was:<null>`），
       而它在 M5-9a 当时是**通过**的。根因：`testWebDav` / `loadNetworkBackups` 先**同步**设
       Loading、再 `launch(IO)`，IO 尾巴 `withContext(Main)` 会清掉 `activeDialog`；
       那两处断言前面各插了一次 `idle()` ⇒ 放行尾巴 ⇒ 看运气。
       **修法：断言放在任何 `idle()` 之前**（只钉同步那一半），并把该例拆成两例；
       用 `--rerun-tasks` **连跑 3 次**确认稳定。教训写进 checklist ——
       这类竞态最恶劣之处是**首次绿**恰好把它藏住了。
     - 验证：四门禁全绿（**G4 无需基线变动**）+ designsystem/`:core:ui`/`:app` 编译 +
       全模块测试；`:feature:settings` **57 例 0 失败**；计数 **761 → 762 / 1256 → 1257**；
       资源 **634/634**；死资源 12 条；lint **5 errors / 94 warnings**（filtered 245→244）。
       `ui/config/backupConfig` 至此**只剩 1 个宿主壳文件**。
     **下一步**：`readConfig`（7 文件 1149 行，但被 `ui/book/*` 的两个 Sheet 挡着）、
     `themeManage`（与 `coverConfig` 撞同一条存储链）、或 `themeConfig`（最重）。
   - **M5-10a-pre 已完成（2026-09-23）：`TimePickerDialog` 从 `:core:ui` 上提到
     `:core:designsystem/commonMain`。** 与 M5-9b-pre 搬 `CardTabRow` 同一配方/同一触发条件
     （共享层出现第一个消费者），包名不变 ⇒ 两处消费方 import 零改动。
     ⚠️ **但它不是零改动搬运**：三处 JVM/Android 专用写法必须改写 ——
     `String.format(Locale.ROOT, …)` → `padStart` 拼接（`Locale.ROOT` 的用意就是恒输出 ASCII，
     否则阿拉伯语地区出 `٢٢:٠٧`）；`Character.digit(c, 10)` → `Char.digitToIntOrNull(10)`
     （原实现**刻意**接受非拉丁数字）；`R.string.ok/cancel` → designsystem 的 `Res`。
     **两条改写有既存单测兜底**：`:core:ui` 原有一份 `TimePickerDialogTest`（用
     `Locale.setDefault("ar")` 钉「恒输出 ASCII」+ 断言 `parseTimeNumber("٢٢") == 22`），
     随实现迁到本模块 `commonTest`（`java.util` 进不去，去掉 `setDefault` 包装）。
     实测 `digitToIntOrNull(10)` 在 JVM 上委托 `Character.digit` ⇒ **非拉丁数字语义保住**
     （3 例全绿）。⚠️ 其它平台的数字表可能只认 ASCII，已记为未验证。
     **计数 +3 不是新增覆盖**：那份测试原在 `:core:ui/src/test`（JVM 源集，
     **不在计数器模块清单里**、从未被计入）。CI 的 `verify.yml` 一直在跑
     `:core:ui:testDebugUnitTest` ⇒ 这是**可见性**增加。762 → **765**。
   - **M5-10a 已完成（2026-09-23）：`EyeProtectionConfigSheet` → `:feature:settings/readconfig/`。**
     仅资源访问改写（11 条，无数组），结构逐字保留。落位判断：它原是「阅读菜单与阅读设置共用」的一份，
     字段全落在 `ThemeSettings` 上 ⇒ 放 `readconfig/`（与将迁来的 `ReadConfigScreen` 同包），
     阅读器侧改 import；⚠️ 临时归属，将来有 `:feature:reader` 应重划。
     死资源 2 条；其余 9 条在 `:app` 侧仍被 `ThemeConfigScreen` / `GlobalThemePage` /
     `MoreConfigSheet` 引用。
     ⚠️ **一处工具口径存疑**：`verify-compose-resources.py` 报 622/622，而按「同名条目」
     应有 631 对（新增 9 条在两个源文件里都有，中间产物实测也含新 key）。未查出原因 ⇒
     改用**直接按 key 逐字 diff**（11 × 4 语言）作为本片断言，结论一致 ✅。工具分母待独立排查。
     **战略结论**：勘察后看清 `readConfig` 不是普通切片，而是**四个移植问题的集合** ——
     `ClickActionConfigSheet`（`androidx.activity` 的 `BackHandler`（共享层零先例）
     + 自 `koinInject` 仓储 vs `:feature:settings` 刻意无 koin 依赖）、
     `PageKeySheet`（`android.view.KeyEvent.nativeKeyEvent`）、
     `CanvasRecorderFactory`（`android.os.Build` + 具体 Impl）、
     `ApplyReadSettingUseCase`（`EventBus`/`ReadConfigUpdateBus` 待核）。
     前两片的性质是「**把阅读器栈跨端化**」而非「迁设置页」。
     加上 `themeConfig`(3388 行/10 个 `Launcher*` 图标) 与 `themeManage`/`coverConfig`
     （撞 `ThemePackageManager`(1254 行深依赖 `Context`/`Uri`/`AppCompatDelegate`) 与
     `BookCover`(`Bitmap`/`Drawable`) 那条链）——
     **`ui/config` 剩余项已进入「每片都要先跨端化或抽重契约」的区间**。
     建议下一轮先与使用者确认优先级，而不是默认按文件数挑最小的继续啃。
   - **M5-11a 已完成（2026-09-24）：`readConfig` 逻辑层 → `:feature:settings/readconfig/`。**
     按「先逻辑层、后页面」的节奏：Contract(118) + VM(225) + ApplyReadSettingUseCase(62)。
     VM 与契约**逐字**（依赖本就在共享层）。两件真要处理的事：
     - **`EyeProtectionUiState` 上提**：读者与设置页共用 ⇒ 必须双方可见。放进设置页契约，
       阅读器改 import（与 M5-9b 的 `HomeScreen` 改 import 特征模块同一处境）。⚠️ 临时归属。
     - ⚠️ **刻意没有**把 `ConfigUpdateAction` 搬进共享层：它是**阅读器**的类型
       （被 10 个 `:app` 文件使用，阅读器收集并分发），设置页只是投递方 ⇒ 搬过来会让共享层
       反向依赖阅读器概念。改为窄契约 `ReadConfigApplyPlatform`（7 方法，与迁移前调用一一对应），
       Android 实现留 `:app/platform/`。等价改写两处：`upPageAnim()` 无参 = `false`；
       `OptimizeRenderChanged` = `upPageAnim(true)` **然后** `loadContent(false)`（顺序保留）。
     - ⚠️ **一处 blanket 替换误伤**（当场回退）：批量改 import 时误伤了 import `ReadConfig`
       （**弃用门面，留在 `:app`**）的两个服务文件与 `MainNavGraph`（`ReadConfigRouteScreen`
       也是宿主壳）⇒ 批量改 import 前要先分清哪些符号迁走了、哪些留下。
       另有同包无 import 的文件（`EyeProtectionTest`、三个 readConfig 文件）覆盖不到，靠编译暴露。
     - 新增 **9 例**（迁移前零测试）：断言**调了平台的哪个方法**（假实现只记方法名），
       含渲染优化的**顺序**、以及「不在映射表里的改动（含护眼）什么都不调」。
     - 验证：四门禁全绿（**G4 无需变动** —— 新适配器不含 `*Utils`/`*Help`/`help.*`）
       + `:feature:settings`（**66 例**）+ `:app` 编译/单测/打包 + 全模块测试；
       计数 **765 → 774 / 1260 → 1269**；lint 仍 **5 errors / 94 warnings**。
       ⚠️ 未验证：`AndroidReadConfigApplyPlatform` 七个方法**无测试执行**（需真机正在运行的
       阅读器），方法体是否仍为原来那些动作靠人工逐条对照 + KDoc 注释。
     **下一步**：`readConfig` 页面本体 —— 前置是 `ClickActionConfigSheet`（`BackHandler` +
     `koinInject` 两个策略决定）、`PageKeySheet`（`android.view.KeyEvent`）、
     `CanvasRecorderFactory`（`android.os.Build`）。
   - **M5-11b 已完成（2026-09-24）：确立两条共享层跨端策略 + `PageKeySheet` 迁进
     `:feature:settings/readconfig/`。** 那两条策略会在阅读器栈反复用到，故先定策略再落地。
     - **按键 ✅**：`event.type == KeyEventType.KeyDown` + `event.key.nativeKeyCode`
       替代 `android.view.KeyEvent` 的 `nativeKeyEvent.action/keyCode`。
       用 `nativeKeyCode` 而非 CMP 的 `Key.*` 语义常量，是因为该设置存的是**数字 keyCode
       逗号分隔串**（如 `"21,22"`），换成语义 Key 等于改存储格式与匹配逻辑。
       ⚠️ `key` 是**扩展属性**，须 `import androidx.compose.ui.input.key.key`（否则
       `Unresolved reference 'key'`）。已 desktop 编译验证。
     - **返回键 ⚠️**：CMP 常见的 `androidx.compose.ui.backhandler.BackHandler` **实测不可用**
       —— ① designsystem `commonMain` 探针报 `Unresolved reference 'backhandler'`；
       ② 遍历 Gradle 缓存所有 compose jar 找 `backhandler` → **0 命中**（不是版本差异，
       是产物里没有）。**策略：共享 composable 不自带 BackHandler，只暴露 `onDismissRequest`，
       由宿主按共享 state 接线**（`BackHandler(enabled = state.activeSheet == …)`），
       与「宿主执行平台动作」判据一致，也不必为共享层引入新依赖。探针确认完即删。
     - `PageKeySheet`(121) 迁移：5 条文案、**无死资源**（五条在 `:app` 侧仍被引用，
       其中一个引用方又是遗留的 `res/xml/pref_config_read.xml`）。
       ⚠️ 顺带发现 `:app` 另有一个 `ui/book/read/sheet/PageKeyConfigSheet.kt` 做着几乎一样的事
       （与护眼 sheet 同样是「阅读菜单与设置页各一份」），迁阅读器栈时要一并处理。
     - 验证：四门禁全绿（G4 无需变动）+ 两端编译 + 全模块测试；计数 **774/1269 零偏离**
       （纯 UI 迁移，依 checklist「无 VM 的纯 composable 不加测试」）；lint **5/94**。
       ⚠️ 未验证：`nativeKeyCode` 在**真机**上是否等于 Android 的 keyCode —— 本片只做到
       desktop 编译通过；若不等，翻页键配置会静默失效（本片最需要冒烟的一点）。
     **下一步**：`ClickActionConfigSheet`（BackHandler 已由宿主接线解决；
     剩 `koinInject` → 改参数注入），之后 `CanvasRecorderFactory`（窄契约），再迁页面本体。
   - **M5-11c 已完成（2026-09-24）：`ClickActionConfigSheet`(230) → `:feature:settings/readconfig/`。**
     两处**结构性改动**（不是逐字搬）：
     - **去掉 `BackHandler`，宿主接线** —— ⚠️ **两个宿主都要补**：这个 sheet 有**两个入口**，
       `ReadBookScreen`（阅读菜单）里原本**没有** `BackHandler`（全靠 sheet 内部那个）⇒
       只补设置页那侧就会让阅读器入口**丢掉返回键关闭**，且无任何编译错误提示。
     - **去掉 `koinInject()`，改显式参数**（`preferences` + `onSetClickAction`）：
       `:feature:settings` 刻意无 koin 依赖，且既有约定是显式注入（参 `AiProviderStringSource`）。
       `ReadBookScreen` 本来就有 `preferences` 参数 ⇒ 只需补仓储与作用域。
       语义等价：原来 `scope.launch { setClickAction(); selectingPrefKey = null }`，
       现在同步调回调再置空，写入由调用方调度（`setClickAction` 是 suspend）。
     - 小坑两处：`ReadPreferences` 是 `ReadSettings` 的 **typealias**（报
       `State<ReadSettings> has no method 'getValue'` 时缺的是 `getValue` import）；
       ⚠️ **旧 import 没清掉** —— 本片只批量改过 `ui.config.readConfig.*`，而这个 sheet 原住
       `ui.book.read.sheet` ⇒ 旧 import 行仍在 + 新补一条 = 重复 import，编译才暴露
       （与 M5-11a 那条同类：批量改 import 要覆盖**所有**被迁走的包）。
     - 死资源 5 条；其余 10 条仍被 `:app` 引用。验证：四门禁全绿（G4 无需变动）
       + 两端编译 + 全模块测试；计数 **774/1269 零偏离**；lint **5/94**。
       ⚠️ 未验证：阅读器入口的返回键关闭、以及选动作后是否真的写入设置（都需真机冒烟）。
     **下一步**：`CanvasRecorderFactory`（31 行 → 窄契约，页面最后一个前置），再迁页面本体。
   - **M5-11d 已完成（2026-09-24）：`readConfig` 页面本体 → `:feature:settings/readconfig/`
     —— 本域收官。** 480 行、55 条文案、**18 个数组（9 对）**，结构逐字保留。
     - ⚠️ `CanvasRecorderFactory.isSupport` **没有**抽窄契约：与 M5-11a 的
       `ReadConfigApplyPlatform` 不同 —— 那是**行为**（需宿主执行）⇒ 契约；
       这只是**事实**（设备支不支持某渲染优化）⇒ 宿主读一次传参即可。抽接口会得到一个
       **没有调用方**的抽象，且换不来可测性（本域可测性来自那张映射表，不来自这个开关）。
     - **死资源 0**：55 条文案 + 18 个数组在 `:app` 侧全部仍被引用（遗留
       `res/xml/pref_config_read.xml`、阅读器、`ReadConfig.kt` 弃用门面）⇒ 一条没删。
       （与 M5-9b 的 41 条删 12 条对照：删不删取决于 `:app` 侧还有谁在用。）
     - `ui/config/readConfig` 剩 2 个文件：`ReadConfigRouteScreen`（宿主壳）+
       `ReadConfig.kt`（弃用门面，被未迁移的 TTS 服务使用，非死代码）。
     - 验证：四门禁全绿（G4 无需变动）+ 两端编译 + 全模块测试；计数 **774/1269 零偏离**
       （纯 UI 迁移，不加测试）；资源 884/884；lint **5/94**。
       ⚠️ 未验证：页面渲染与三个 sheet 交互，以及 `canvasRecorderSupported` 为真时
       「优化渲染」那一项是否还显示（改为宿主传入，值没变，但只有真机能确认）。
     **下一步**：`ui/config` 只剩 `themeConfig`(11/3388) / `coverConfig`(9) / `themeManage`(4)
     + 几个宿主壳，**三者都是重活**（10 个 `Launcher*` 图标；`ThemePackageManager`(1254 行)
     与 `BookCover`(`Bitmap`/`Drawable`) 那条存储链）⇒ 建议先确认优先级。
     验证：四门禁全绿（G4 无需变动）+ designsystem（**42 例**，含迁入 3 例）/`:core:ui`/
     `:feature:settings`/`:app` 编译 + 全模块测试；计数 **762 → 765 / 1257 → 1260**；
     资源 11×4 逐字一致 + designsystem 216/216；死资源 2 条；lint **5 errors / 94 warnings**。

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

### 7.1 与 upstream `main` 的关系（2026-09-23 决定）

本分支与 `origin/main` 已显著分叉（main 有 68 个本分支没有的提交，本分支有 114 个 main 没有的）。
**决定：不合、不覆盖，以本分支为准。**两条分别记下理由，免得每次同步都重判一次：

| 上游内容 | 规模 | 处置 |
|---|---|---|
| `main` 的功能提交（听书、翻页速度、书源兼容、详情页 Web 渲染…） | 68 提交 / 120 文件 / **+10139 −2004** | **不合进本分支**。与 KMP/CMP 迁移无关，且会把上游未适配的旧架构代码带进来；分歧 68/114 意味着合并冲突面很大。等本里程碑收敛后再单独评估 |
| `.agents/skills/legado-kmp-migration/**` 的重写版 | `SKILL.md` 287→**109** 行、`slice-checklist.md` 501→**65** 行、`renderer-host-strategy.md` +71（新增），`m3-domain-slice.md`/`check-kotlin-comments.py`/`portability-triage.py` **删除**，净 **−3061** | **保留本分支版本**。本地那 501 行里钉的是逐片实测出来的坑（Koin 漏绑不会让编译失败、`verify-compose-resources` 的双向判据、`miuix-preference` 无 desktop 变体、Robolectric 必须 `sdk=[35]`、契约扩展会撞测试探针…），整体覆盖会一次性丢掉。上游新增的 `renderer-host-strategy.md`（多渲染宿主视角：WinUI 3 / SwiftUI / IPC）**暂不引入**——本仓库当前没有对应宿主，不为架构完整留空配置 |
| `.agents/skills/legado-compose-{migration,review}`、`.claude/skills/**` | 上游新增/调整 | 同上，暂不引入 |

⚠️ 将来若要对齐上游 skill，**不能整体覆盖**：需逐条比对、把本地实测条目回填进新结构，
否则本节列出的那些"踩过才知道"的约束会静默消失（它们多数不会在编译期暴露）。

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

## 进度补记（M5-12a → M5-15a）

⚠️ 这一段是**补记**：M5-12a 之后我连续若干片只更新了 `feature-slicing-audit-config.md`（详细记录），
漏了本文件的逐片条目 ⇒ 这里按片补回一行摘要，**详情以审计文档为准**。

| 片 | 内容 | 关键判断 / 发现 |
|---|---|---|
| M5-12a | `coverConfig` 逻辑层（Contract + VM + `CoverAlbumContract`）→ `:feature:settings/coverconfig/` | ⚠️ **`io.legado.app.domain.**` 不是"共享层"信号**：`CoverAlbumUseCase`/`Gateway`/`CoverAlbumImageInput`（后者用 `java.io.InputStream`）都定义在 `:app` ⇒ 改收窄契约 `CoverRulePlatform` + `CoverAlbumProvider`，不搬整条链 |
| M5-12b | `CoverConfigViewModelTest`（10 例） | 兑现 M5-12a 留下的 finding（迁 VM 未补覆盖） |
| M5-13a/b/c | `CoverRuleConfigSheet` / `CoverAlbumSelectSheet` / 封面设置页本体 | M5-13b 引入本模块**首个 coil 依赖**（designsystem 内是 `implementation`、不透出；用真实需求直接加，而非为单一消费者造共享组件）；M5-13c 抓到 `Integer.toHexString`（`java.lang.Integer`，JVM 专用 → `toString(16)`） |
| M5-14a/b | 图库逻辑层（扩 `CoverAlbumProvider` + 10 例）/ 图库管理页本体 | 「URI 串 → 图片输入」整段留宿主（`CoverAlbumImageInput` 带 `java.io.InputStream`，出不了 `:app`）；**`coverConfig` 子域收官** |
| M5-15a | 勘察 `themeManage` / `themeConfig`（只量不改） | ⚠️ **勘察工具三个盲点**（只索引类 / 不区分源集 / `:app` 未进索引且"查不到"被静默跳过）—— 三次都给出过"无 `:app` 私有依赖"的错误结论 |

### 一处重要的验证教训（贯穿上述多片）

M5-12b 发现：`lintAppDebug` **一直在失败**，而我过去十几片只 grep 了它的汇总数字
（"5 errors / 94 warnings"）从未检查任务退出状态 ⇒ 稳定的数字掩盖了稳定的失败。
那 5 个 error 都在未迁移的遗留 `:app` 文件里（`BookInfoScreen` ×3、`BackstageWebView`、
`BottomWebViewDialog`），**非迁移引入**；处理方式（修 / 进 `lint-baseline.xml` / 暂不处理）
待定。自那以后每片都同时看 `BUILD SUCCESSFUL|FAILED`。已并入 checklist。

### 下一步（M5-15b）

上提 `CompactSettingItems`(311 行，4 个组件，无任何 `android./java./R.` 导入) + 其依赖
`ValueStepper`(73 行，⚠️ **不是零改动**：带 `androidx.compose.ui.res.stringResource` 与
`:core:ui` 自己的 `R`)，从 `:core:ui` 到 `:core:designsystem/commonMain`。
理由：这是 `themeManage` 与 `themeConfig` **两块共用的前置**，且被 10 个 `:app` 文件使用、
同包名 ⇒ 零 import 改动。

### M5-15b 已完成（2026-09-24）：⚠️ 计划**落空一半**，缩到只上提 `ValueStepper`

**`CompactSettingItems` 不是"可上提"** —— 它直接调 `top.yukonga.miuix.kmp.preference.*`
的三个组件（`WindowDropdownPreference` / `SwitchPreference` / `ArrowPreference`），
而 `miuix-preference` 是**整个 miuix 里唯一没有 desktop 变体的制品**（只有
`miuix-preference-android`）⇒ 进不了 `commonMain`。

**这个原因早就写在 designsystem 的 `MiuixPreferenceRenderer.kt` KDoc 里**，而且为它**早就建好了
既定方案**：窄契约 `MiuixPreferenceRenderer`（`switchPreference` / `arrowPreference` /
`dropdownPreference`）+ 宿主注入点 `MiuixPreferenceRendererProvider`，Android 实现留在
`:core:ui`；`ClickableSettingItem`(M5-2a-pre) / `SwitchSettingItem` / `DropdownListSettingItem`
都是这么过桥的。

⇒ 它的正确形态是**改写走契约**（需决定是否扩契约参数面），不是搬运 ⇒ 已 `git mv` 退回原位
（`git diff` 为空，**字节一致**）。

**本片实际交付**：`ValueStepper` `:core:ui` → `:core:designsystem/commonMain`（包名不变 ⇒
3 处消费者 import 零改动），改写两处资源访问，`a11y_decrease` / `a11y_increase` 两条文案
逐字搬入 designsystem 4 语言目录并从 `:core:ui` 删除，另删 `:app` 侧已成死文案的 8 行。

⚠️ 查过一个真实风险：Android 资源合并时 `:app` 同名条目会**覆盖** `:core:ui` 的 ⇒
`ValueStepper` 原来显示的其实是 `:app` 的值；逐语言比对 4 目录 × 2 条**全部一致** ⇒
未改变用户可见文案 ✅。

⚠️ 归因失败：`lintAppDebug` 仍 FAILED（5 个 error 与 M5-12b 记录**逐行一致** ⇒ 本片未新增），
但 warnings 94 → 95 **归因不了**。更该记的是过程问题 —— M5-10a 之后若干片引用的「lint 一致(94)」
是**没重测的旧值**。

验证：四门禁全绿 + designsystem/`:core:ui`/`:feature:settings`/`:app` 编译与全模块测试
（BUILD SUCCESSFUL）；计数 **794 / 1289 零偏离**；designsystem 资源 **224/224**
（56×4，正好 +2 key）；`:app` 4 个 res 文件 XML 通过。

**下一步**：把 `CompactSettingItems` 的三处 miuix 直接调用改写成走 `MiuixPreferenceRenderer`
契约（先读契约与三个既有范文），之后才是 `themeManage` / `themeConfig`。

### M5-15c 已完成（2026-09-24）：`CompactSettingItems` 改写到契约上并上提 —— M5-15b 的坑填上了

先读契约 + Android 实现 + 三个既有范文再动手，结果比预想干净：**三个站点里两个与既有契约方法
1:1 对应**（`switchPreference` / `arrowPreference`，参数逐项一致），只有
`CompactDropdownSettingItem` 需要新增一个方法。

**为什么第三个要新增而不复用 `overlaySpinnerPreference`**：`WindowDropdownPreference`
（`Compact*` 用的）与 `OverlaySpinnerPreference`（`ListSettingItem` 用的）是**两个不同的 miuix
组件**（前者 `items` 收 `List<String>`，后者要 `DropdownItem` 包装）。既有先例的判据是
「迁移后行为与迁移前**逐字等价**」⇒ 复用等于顺手换渲染，不可以 ⇒ 契约加
`windowDropdownPreference`，KDoc 写明它**看起来一样但是另一个方法**。

**逐字等价性核过**（不是"看起来对"）：`modifier = Modifier` 与
`insideMargin = BasicComponentDefaults.InsideMargin` 两个常量由实现侧补（与契约 KDoc 里既有说明
同一判据）；`startAction` 就是那个 `Icon(imageVector, null)`；`items` 直接传
`displayEntries.toList()`。

**契约扩展的连带项**：`commonTest` 的探针必须实现完整 —— 那个文件自己的注释写着「这个编译错误是
**契约测试该有的反应**」⇒ 照例补 override、不加用例（本源集验宿主语义）。

验证：四门禁全绿 + designsystem desktop/android 编译与 testAndroidHostTest + `:core:ui` /
`:feature:settings` / `:app` 编译、单测、打包 → **BUILD SUCCESSFUL**；全模块测试通过；
计数 **794 / 1289 零偏离**；`lintAppDebug` 重测 **5 errors / 95 warnings**（与 M5-15b 完全相同
⇒ 本片零影响，且说明 M5-15b 记的那个 94→95 漂移发生在更早的片里）；`:core:ui` 的
`miuix-preference-android` **没有被扩散**（新方法只加在 `:core:ui` 的实现类里，共享层签名无 miuix 类型）。

⚠️ 至此 `ui/config` 剩余两块在 `Compact*` 这一项上**已解阻**。

**下一步**：`themeManage`（4/1138；阻塞 = `SavedTheme` / `ThemePackageManager` 两个
`:app/help.config` 类型 + VM 的 `Uri`/`StringRes`）—— 建议先逻辑层（VM 走窄契约），再页面；
`themeConfig`(11/3388) 排在后面。

### M5-16a 已完成（2026-09-24）：`themeManage` 逻辑层 → `:feature:settings/thememanage/`

**勘察结论：`SavedTheme` 不能上提 —— 卡在「序列化注解」上。** `ThemeExportData`（`:core:model`）
是干净模型 ✓，但 `SavedTheme` 携带 `packageManifest: ThemePackageManifest?`，后者靠 **GSON 反射**
读写 ⇒ 带 `@Keep`（`androidx.annotation`）+ `@SerializedName`（`com.google.gson.annotations`），
两者都进不了 `commonMain`。这是 M5-12a 的 `CoverAlbumImageInput` 之后**第二类**「看着是纯模型、
其实被平台库绑住」的阻塞，但判据是**注解**而不是 `import java.`。

⚠️ 与 `:core:model` 既有约定（`PageAnim`：`IntDef` 可移除，因为 SOURCE 保留、运行时与 R8 无影响）
**不同**：`@Keep` 不是 SOURCE 保留，它**就是**为了让 R8 别动这些字段 ⇒ 移除它等于悄悄改 release
行为。不能照 `IntDef` 的先例办。

⇒ 沿用 M5-12a 的形态：**投影 + 窄契约**。
- 窄契约 `ThemeManagePlatform`（9 方法），实现 `AndroidThemeManagePlatform` 住 `:app`
- UI 状态改持投影 `SavedThemeSummary(name, data)`（`packageRootPath`/`packageManifest` 不进契约）
- `@StringRes Int` → 语义枚举 `ThemeManageText`（7 条）；**7 条文案刻意没搬进 composeResources**
  —— 这条 effect 由宿主消费（要 `context.toastOnUi`），文案属宿主那次 toast 的渲染
- 契约**以 `name` 为键**：宿主侧本就是「一个主题一个目录、目录名即主题名」⇒ 实现侧自己解析回
  真实 `SavedTheme`（代价是多一次目录扫描，换契约面里不出现平台路径/清单类型）

命名/实现细节：`deleteSavedTheme` 找不到对象**也照删**（迁移前那个方法只用 `name` 与
`packageRootPath`）；`apply` 相反，没清单就无法应用 ⇒ 明确失败。

**G4 基线两处下调**（棘轮只降不升）：`platform` 7 → **9**、`ui/config/themeManage` 5 → **1**
（净债务为零；剩的 1 条是宿主壳用的 `ThemePackageManager.FILE_EXTENSION`）。

验证：四门禁全绿 + `:feature:settings` desktop 编译 + `:app` 编译/单测/打包 → **BUILD SUCCESSFUL**；
全模块测试通过；计数 **794 / 1289 零偏离**（本片不加测试，依 M5-12a→M5-12b 先例）；
`lintAppDebug` 重测 5 errors / **102 warnings**，且逐条归因：`UseKtx +3` 是旧 VM 同 3 处 `Uri.parse`
移出 lint 基线（filtered 正好 −3，净零），`GradleDependency +4` 是版本提示、与本片无关。

未验证：页面与 4 个 SAF launcher 的交互、toast 在 4 语言下的显示。需真机冒烟。

**下一步**：M5-16b ＝ `ThemeManageViewModelTest`（锁互斥、错误→枚举映射、迁移计数、
`saveTheme` 改名时的删除分支）→ 然后 `themeManage` 页面 + 表单 → 再 `themeConfig`。

### M5-16b 已完成（2026-09-24）：`ThemeManageViewModelTest`（12 例，迁移前零覆盖）

按 M5-12b 的模式补上 M5-16a 留下的 finding。锁住：初始加载、保存成功刷新、保存失败→`SaveFailed`
+detail、**改名保存删旧名**、**名字没变不删自己**（`takeIf { it.name != intent.name }` 写反就会
「改个名多出一条」或「同一次保存把自己删掉」）、应用/删除/导出成功/导出失败/导入成功/导入失败
的枚举映射、旧版迁移两个计数 + `hasLegacyThemes` 取 `failedCount > 0`、**互斥丢弃**、
弹层意图的状态守卫。

#### ⚠️ 互斥那条第一次写错了，而错误本身很有价值

初版连发两次 `ImportPackage` 并断言第二次被丢弃 ⇒ **挂了**（两条都在）。两层原因叠加：
1. `viewModelScope` 在 **`Dispatchers.Main.immediate`** 上 ⇒ `launch` 会**内联执行到第一个
   挂起点**（测试跑在主线程，没有「排队」一说）；
2. 契约 fake **全同步** ⇒ 第一次当场跑完、`finally` 已解锁 ⇒ 第二次照跑。

⇒ 「前一个仍在进行」是这条语义的前提，而前提得由测试自己制造：改用 `CompletableDeferred`
闸门卡住 fake 的 `importPackage`。改后 `--rerun-tasks` **连跑 3 次全绿**。顺带把语义写清了：
**丢弃只发生在「前一个仍在飞行中」时**，跑完之后再点就是一次新操作。

验证：四门禁全绿；`:feature:settings` **98 例 0 失败**（57 → 98 为 M5-10a/11a/12b/14a/16b 累计）；
计数 **794 → 806 / 1289 → 1301** 零偏离；全模块测试 + `:app` 编译/单测/打包 → BUILD SUCCESSFUL。
本片只加测试源码（不在 `:app`）⇒ `lintAppDebug` 无需重测。

**下一步**：`themeManage` 页面 + 表单（`:app` 侧 3 个文件），再 `themeConfig`。

### M5-17a 已完成（2026-09-24）：`EditThemeSheet` → `:feature:settings/thememanage/`

勘察：两个待迁文件**都只有 `io.legado.app.R` 一处平台依赖** ⇒ 不需要新契约 ✓。
资源面 43 条字符串 + 7 组「标签/值」数组对。

**做法：脚本化等价改写**（403 行、40+ 处资源引用，手抄会把「逐字保留」降级成靠记忆重打）。
只改四类：包名、两个资源函数 import→CMP 版、`R.*`→`Res.*`（+58 行逐 key import）、
`Integer.toHexString(x).uppercase()`→`x.toString(16).uppercase()`（JVM 专用写法）。

#### ⚠️ 资源脚本连踩三个坑（都会静默出错）

1. **按文件名猜资源文件**：`:app` 的值数组住在 **`values/array_values.xml`**（不是 `arrays.xml`）
   ⇒ 3 个值数组漏复制、3 个「不一致」是假阳性（源侧读到 None）；**同一个坑在删除脚本又踩一次**。
   ⇒ 一律遍历该语言目录下所有 `*.xml`。
2. **正则把 `-array` 吃掉**：为容忍 `translatable="false"` 放宽成 `<string[^>]*name="k"` ⇒ 同时匹配
   `<string-array name="k"` ⇒ 把**数组体插成字符串值**，坏在 `default_home_page` / `tabletInterface` /
   `theme_mode` 这三个**同名不同类**的 key 上。修法 `<string(?![-\w])`。同一族的还有：跳过检查写成
   `<array name="k"` 永不匹配已存的 `<string-array name="k"` ⇒ 每个 key 被重插（values +82，已回退）；
   以及重复 key 导致迁移后 Kotlin 里 **import 重复**（3 行，需去重）。
3. **CMP 的 `stringArrayResource` 返回 `List<String>`**，而 Android 版返回 `Array<String>`，而
   designsystem 的 `Compact*SettingItem` 收 `Array<String>` ⇒ 14 处调用点补 `.toTypedArray()`。
   既有先例（`customtheme/CustomThemeScreen.kt`）里就留着这条发现的中文注释，我没先搜它。

#### 死资源与回落

`:app` 侧 43 条里只有 22 条变死（其余仍被 `ThemeManageScreen` / `themeConfig` 用着），连同 4 个数组
共删 91 项（按**元素**跨行删除，行删会留半截标签）。逐 key 回落：`:app` 的 zh 目录只定义部分条目，
CMP **不参与 Android 资源合并** ⇒ 每个语言目录都要自带一份（值取回落结果）；7 个值数组只落默认目录。

验证：四门禁全绿（G4 无需变动）+ `:feature:settings` desktop 编译 + `:app` 编译/单测/打包 +
全模块测试 → **BUILD SUCCESSFUL**；计数 **806 / 1301 零偏离**；资源**逐字一致**（脚本核验）+
无重复 key；死资源 26 key 已无定义；`lintAppDebug` 重测与本片之前**完全一致**（零 delta）。

未验证：表单在真机/desktop 的渲染与交互（7 个下拉、开关/滑杆、颜色选择器 14 个色槽）。

**下一步**：`ThemeManageScreen`（334 行，25 条字符串，无数组）→ 再 `themeConfig`(11/3388)。

### M5-18（M5-17b）已完成（2026-09-24）：`ThemeManageScreen` → `:feature:settings/thememanage/`

同一套脚本化迁移，但**把 M5-17a 踩过的三个坑直接写进了脚本**（遍历所有 `*.xml`、标签正则否定前瞻、
逐 key 回落、生成 import 去重），并加了一步**阶段 0 探查**（先扫 `Integer.` / `System.` /
`String.format(` / `Locale` / `Character.` / `java.` / `android.` / `@SuppressLint` / `LocalContext`
与 `R.<其它类型>`）。该文件无任何 JVM/Android 专用写法、无数组 ⇒ 只需三类改写。

**这一次三个坑一个都没踩** —— 因为它们是写进脚本的，不是记在脑子里的。

死资源：25 条里 17 条在 `:app` 变死（另 8 条仍被别的页面用），删 68 项。

验证：四门禁全绿（G4 无需变动）+ 全模块测试与 `:app` 编译/单测/打包 → **BUILD SUCCESSFUL**；
计数 **806 / 1301 零偏离**；资源 25 × 4 语言逐字一致 + 无重复 key；`lintAppDebug` 重测与上一片
**完全一致**（零 delta）。

未验证：页面渲染与交互（搜索框、卡片操作按钮、三个确认弹层、空态、旧版迁移入口）。

⚠️ **`themeManage` 子域收官**（只剩宿主壳 `ThemeManageRouteScreen` 按判据留 `:app`）。
`ui/config` 实测剩 **21 个文件**（`themeConfig` 11 / `coverConfig` 3 / `readConfig` 2 /
`otherConfig` 2 / `backupConfig` 1 / `themeManage` 1 / `bookshelfConfig` 1）。

**下一步**：`themeConfig`(11/3388) —— 最大的一档，建议先照 M5-15a 的方式**勘察量清再切**。

### M5-19a-pre 已完成（2026-09-24）：勘察 `themeConfig` + 把勘察工具落进仓库

**先把工具变成仓库的一部分**：M5-15a 那次用三个一次性 tmp 脚本、踩了三个盲点；M5-17b 的教训是
「教训留在散文里只对下一个作者有用，留在工具里立刻有用」⇒ 这次落成
**`tools/audit-slice-deps.py`**（docstring 里写明用法与四类踩坑），后续每片勘察都用它。
工具在这一片又修了一个假阳性：**按简单名索引会把「同名不同包」判错**（`AppModalBottomSheet`
在 `:app` 与 designsystem 各有一个 ⇒ 6 个文件被误报）⇒ 改成**按 FQN 索引**，只有 FQN 查不到时
才回退简单名并标注。

**`themeConfig` 的真实阻塞**（FQN 精确解析）：11 个文件里 **2 个完全干净**
（`BackgroundImageManageSheet` 199 / `TopBottomBarSettingsSheet` 177）⇒ 它不是一块铁板，
可按阻塞性质切成四档：

| 档 | 片 | 内容 | 需要的策略决定 |
|---|---|---|---|
| 1 | **M5-19a** | 两个 ✅ 无 的 sheet（376 行） | 无（常规片） |
| 2 | M5-19b | `MainNavigationSettingsSheet` + `NavIconManageSheet`（421 行） | `MainDestination` + `mainDestinationIcon` ⇒ 投影 + 契约（同 M5-16a 形状） |
| 3 | M5-19c | `LabelColorManageSheet` + `LauncherIconPickerSheet`（360 行） | `TagColorGenerator` + 8 个 `Launcher*` 图标 + `getCompatDrawable` + `ComponentName`/`ImageView` ⇒ 图标表可能改由宿主提供 |
| 4 | M5-19d+ | `ThemeConfigContract`(109) + VM(612) + Screen(1326) + `ThemeConfig.kt`(61) | VM 那 7 个 `:app` 符号混着**文件/字体/主题包存储**一族（`FileDoc`/`FileUtils`/`MD5Utils`/`externalFiles`/`inputStream`/`openInputStream`）⇒ 大概率要 `ThemeConfigPlatform`；Screen 里还挂着字体选择（`FontFolderState`/`FontSelectSheet`） |

**下一步**：M5-19a（两个零阻塞 sheet）。

### M5-19a 已完成（2026-09-24）：⚠️ 只迁成 1 个；另一个撞上**同包前置**

计划里「两个 ✅ 无」的那对，实际只有一个能独立迁走：
- **`BackgroundImageManageSheet`(199) 迁成** → `:feature:settings/themeconfig/`（4 条字符串）。
- **`TopBottomBarSettingsSheet`(177) 退回 `:app`** —— 它无 import 地用了**同包的
  `ThemeConfigIntent`**，而那个契约还没迁（属第 4 档）。

#### ⚠️ 同包陷阱有两个方向，按 import 解析的勘察两边都看不见

| 方向 | 例子 | 表现 |
|---|---|---|
| **(a) 往里用** | `TopBottomBarSettingsSheet` 用同包 `ThemeConfigIntent`（声明在 `ThemeConfigContract.kt`） | 勘察判「✅ 无」，一 `git mv` 就 `Unresolved reference`（M5-15b 同一类） |
| **(b) 往外被用** | 迁移**后**才暴露：`ThemeConfigScreen` 无 import 地用 `BackgroundImageExtraOption`，而该符号是**被迁走的文件声明的** | 迁走后**兄弟文件**编译失败 |

#### 工具升级：同包扫描 + **目标模块**参数

`tools/audit-slice-deps.py` 现在多一节「同包陷阱」扫描（两方向都查），并接受**第二个参数 = 目标模块键**
（`python tools/audit-slice-deps.py <dir> feature:settings`）—— 因为同包引用只在**跨模块**时才致命，
判据是「迁到目标模块**之后**还成不成立」。实测它报出了 `TopBottomBarSettingsSheet` → `ThemeConfigIntent`
这一条（**正是绊倒我的那条**），整个 `themeConfig` 报 24 处，等于把剩余各档的**前置图**列了出来。
另修一处假阳性：文件**自己声明**的符号不算。

#### 顺带修正了档位顺序

契约（`ThemeConfigContract`）其实是**多数 sheet 的前置** ⇒ 原「由易到难」的档位表要按**依赖**重排：
先迁契约（连同它声明的 `ThemeConfigIntent`/`UiState`/`Effect`/`Dialog`/`ThemeConfigSheet`），再回头做
第 2/3 档的四个 sheet。

退回时连带撤掉了 `TopBottomBarSettingsSheet` 的 21 条字符串与 1 组数组（不留"预置"）。
验证：四门禁全绿 + 全模块测试与 `:app` 编译/单测/打包 → **BUILD SUCCESSFUL**；计数 **806 / 1301 零偏离**；
资源 4 × 4 语言逐字一致；`:app` 这 4 条**零死资源**；lint 重测**零 delta**。

**下一步**：`ThemeConfigContract`(109 行，唯一阻塞 `FileDoc`)。

### M5-19b 已完成（2026-09-24）：`ThemeConfigContract` → `:feature:settings/themeconfig/`

纯类型文件、无 `R.*` ⇒ **不涉及资源搬运**。两个平台耦合点的替代**都有现成先例**：

| 迁移前 | 迁移后 | 依据 |
|---|---|---|
| `SelectAppFont(file: FileDoc)` | `SelectAppFont(name: String, uri: String)` | `FileDoc` 是 `utils` 里的平台类型（`Uri`/`DocumentFile`/`appCtx`）。判据写在 `AndroidAboutCapabilities` 的 KDoc：共享层不能出现 `Uri`/SAF 类型 ⇒ 边界上退化成「**id + 展示名**」（id = `uri.toString()`），宿主 `FileDoc.fromUri(Uri.parse(id), false)` 还原 |
| `ShowToast(stringRes: Int)` | `ShowToast(text: ThemeConfigToast)` + 枚举（2 条） | 共享层拿不到 `R`（M5-16a `ThemeManageText` 同一形态），宿主映射回自己的文案 |

`FileDoc` 的还原放在 `:app` 的 VM；产出端（`ThemeConfigScreen`）改成
`SelectAppFont(name = it.name, uri = it.uri.toString())`。

**顺手把 M5-19a 的 (b) 方向自动化**：按「本文件声明 ∩ 兄弟文件无 import 使用」自动补 import ——
4 个文件、19 个符号（含 M5-19a 退回的那个 `TopBottomBarSettingsSheet`，它的前置现在到位了）。

⚠️ lint 的 `+1` 是**我自己的新代码**：归因到本片新写的 `Uri.parse(intent.uri)`（`UseKtx`）⇒
按提示改写成 `intent.uri.toUri()`，重测**回到 102** ⇒ 零 delta。
（与 M5-17a 那个决定互为镜像：那次 3 处 `UseKtx` 是**搬过来的既有债**，刻意不改；
这次是**我新写的**，就按规则写。）

验证：四门禁全绿（G4 无需变动）+ `:feature:settings`/`:app` 编译、单测、打包 + 全模块测试 →
**BUILD SUCCESSFUL**；计数 **806 / 1301 零偏离**；lint 重测 **5 errors / 102 warnings**（与迁移前一致）。

**下一步**：第 2/3 档的四个 sheet（前置已到位）—— 先量清各自的同包符号族再切。

### M5-19c-0 已完成（2026-09-24）：⚠️ 修一个**静默的 UI bug**（`@string/` 间接引用）

**发现**：勘察导航组时去看 feature 侧 `default_home_page` 数组的实际内容（M5-17a 搬过它），
发现条目是字面量 **`@string/home`** 而不是「首页 / Home」。

**为什么是 bug**：CMP 的 composeResources 是 assets，**不参与 Android 资源解析** ⇒
`:app` 里的 `@string/home` 会逐项按语言解析，照抄到共享层则**原样显示 `"@string/home"`**。

**为什么没被发现（两个"看不见"叠加）**：① 编译通过（它只是字符串字面量）；
② 我的逐字比对口径错了 —— 比的是「feature 值 vs `:app` **原始**值」，而后者就是那个 token
⇒ 报 ✅。**该比的是解析后的可见值。**

**波及**：全仓扫描 **32 项 / 3 个数组** —— `progress_bar_behavior_title`、`screen_direction_title`
（readConfig 那几片）与 `default_home_page`（**M5-17a，我自己的片**）。

**修法**：按「数组所在语言 → 回落 `:app` 默认」解析成本地化字面量（= Android 的行为）。
抽查：`default_home_page` 现为 `Home/Bookshelf/Discovery/RSS Feeds/Me`、
`首页/书架/发现/订阅/我的`、`主頁/書架/發現/訂閱/我的` ✓。

**新工具**：`tools/check-resource-indirection.py`（扫全仓 composeResources、发现即非零退出、
`--fix` 展开）⇒ 修完全仓 **0 项** ✅。

验证：四门禁全绿 + 全模块测试与 `:app` 编译/单测/打包 → BUILD SUCCESSFUL；计数 **806 / 1301
零偏离**；4 个语言目录 XML 全部可解析；lint **5 errors / 102 warnings**（与本片之前一致）。

未验证：**渲染结果本身**（这正是该 bug 唯一能暴露的地方）—— 需在真机/desktop 打开三个下拉确认。

**下一步**：回到导航组（`MainDestination` 上提 + 两个 sheet）。

### M5-19c-pre 已完成（2026-09-24）：导航组的三个上提

勘察结论：这一组**不是"造契约"，而是三个上提**，每个都符合既有配方（**包名不变 ⇒ 消费方 import
零改动**），触发条件都是「共享层出现消费者」：

| 上提 | 改动 |
|---|---|
| `MainDestination` → `:core:designsystem`（包名 `io.legado.app.ui.main` 不变） | ⚠️ 一处改写：`@StringRes labelId: Int` → 语义枚举 `MainNavLabel`（共享层拿不到 `R`/`StringRes`） |
| `MainDestinationIcons` → 同上 | **逐字**（纯 Compose + designsystem + `miuix-icons`，designsystem 早已依赖） |
| `MutableList<T>.move`（原 `:app/utils/CollectionExtensions.kt`）→ 同上，包名保持 `io.legado.app.utils` | **逐字**（纯泛型）。放 designsystem 的理由：8 处消费方全在 UI 侧 |

`labelId` 的 9 处消费点：`MainScreen` 7 处（同包 ⇒ 无需 import）、`MainNavigationSettingsSheet` 3 处
（需 import `toRes`）；宿主映射写在 `:app/ui/main/MainNavLabelText.kt`（与 M5-16a/19b 同一判据：
共享层承载语义、宿主承载文案）。

⚠️ 顺手更新了一处**被本条推翻前提**的 KDoc：`MainDestinationIcons.kt` 原写「映射属于导航语义、
因此留在 `:app`」—— 那个前提正是被同片的 `MainDestination` 上提消掉的。（同类还有 M5-9b-pre 的
`AppTabRow`：「等真出现时按同一配方再搬」。⇒ 已进 checklist：**这类 KDoc 是待办清单**。）

验证：四门禁全绿（G4 无需变动）+ designsystem/`:app` 编译、单测、打包 + 相关测试 →
**BUILD SUCCESSFUL**；计数 **806 / 1301 零偏离**；lint **5 errors / 102 warnings**（一致）；
资源间接引用检查 **0 项**。

**下一步**：迁两个 sheet（前置已全部到位）。

### M5-19c 已完成（2026-09-24）：两个导航 sheet → `:feature:settings/themeconfig/`

前置（三个上提）到位后一并迁（195 + 226 行）。三类值得记的东西：

**① 新的改写类别 —— 共享层自己需要文案时用 `Res`。** 迁移前是
`stringResource(it.label.toRes())`，而 `toRes()` 是宿主的映射 ⇒ 改在共享侧再写一份
`mainNavLabelRes(label): StringResource`（`Res.string.home/…`，5 个 key 一并搬进 feature）。
与 M5-16a/19b 同一判据的另一面：共享层自己确实要渲染文案时，就用 `Res`。

**② `sh.calvin.reorderable` 依赖**：`MainNavigationSettingsSheet` 用它做拖拽重排，而
`:feature:settings` 没有。查过归属：`:app` / `:core:ui` / designsystem / 四个规则 Feature
**每个用它的模块各自声明**（都注明是 KMP 制品、有 `reorderable-jvm` 变体）⇒ 给 feature 加一行
是既定做法，已照该模块风格补注释。

**③ 文件内的 `@param:StringRes val labelRes: Int`**：`NavIconManageSheet` 的**私有**数据类把标签
声明成 `Int`，而机械改写只跟着 `R.string.` 走 ⇒ 编译才暴露（`StringResource` vs `Int`）。
⇒ 已改成 `StringResource` 并清掉无用 import。**教训**：共享化一个文件时要 grep 它内部的
`@StringRes`/`Int` 资源字段（M5-16a 在契约里遇到过，这次在私有数据类里）。

死资源：16 条里 7 条在 `:app` 变死，删 22 项；其余 9 条仍在用（含 5 个导航标签，宿主
`MainNavLabelText.toRes()` 在用它们）。

验证：四门禁全绿（G4 无需变动）+ `:feature:settings`/`:app` 编译、单测、打包 + 相关测试 →
**BUILD SUCCESSFUL**；计数 **806 / 1301 零偏离**；资源 16 × 4 语言逐字一致 + 无残留；
`tools/check-resource-indirection.py` 0 项；lint 5 errors / 102 warnings（live 一致，
filtered 239→238 属"账本变准"）。

未验证：拖拽重排（本模块首次用 `reorderable`）、显隐开关、默认主页下拉、导航图标选择与恢复默认、
两套引擎下的渲染。

**下一步**：`themeConfig` 剩 7 个文件 —— `LabelColorManageSheet` + `LauncherIconPickerSheet`
（图标/颜色档）与核心三件套（VM 612 / Screen 1326 / `ThemeConfig.kt` 61）。

### M5-19d 已完成（2026-09-24）：`LabelColorManageSheet` + 两个上提 + 一个纯算法替代

勘察把图标/颜色档分成两种难度：`LabelColorManageSheet`(161) 可做；
`LauncherIconPickerSheet`(199) **重度 Android**（自持 `LauncherIconItem(… ComponentName)` /
`LauncherIcons.list`，引用 8 个 `MainActivity` 子类 + `R.mipmap` + `ImageView`）⇒ 需要「启动图标表」
契约，是独立一片。**本片只做前者**。

三个前置动作：`TagColorPair` 上提（纯 data class，包名不变 ⇒ 5 处 import 零改动；同文件那个
`@Deprecated` 且带 `AppCompatDelegate` 的 `ThemeConfig` 留在 `:app`）；`TagColorGenerator` 上提
（唯一消费者就是本片的 sheet）；新增 `colorToHsl` 纯算式（替代 Android-only 的
`ColorUtils.colorToHSL`，含 `max == r` 那支的 `g < b` 回绕）。

⚠️ **`TagColorGenerator` 改了包名**（`io.legado.app.help.config` → `io.legado.app.utils`）：
留在旧包会让共享层**首次出现 `help.*` 导入**，而 G4 把那个当「`:app` 私有耦合」⇒ 纯假阳性。
**与其把假条目登记进基线，不如换个如实的住所**（它只有 1 个消费者，同片一并改 import ⇒ 零额外
改动）。同片把 `:app` 侧 `themeConfig` 的 `legacyHelp` 基线按棘轮 3 → 2。

✅ **纯算法替代 ≠ 搬运 ⇒ 必须有用例**：新增 `commonTest/ColorHslTest`（4 例：三原色 / 无彩色 /
`max==r && g<b` 回绕 / 明度两支饱和度）。搬文件靠编译 + 空 diff 就够，换算法不够。

⚠️ 顺带修掉工具自己的一个 bug：`tools/audit-slice-deps.py` 的「目标模块」判据里，索引存三段
`模块:子模块:源集` 而传入的是两段 ⇒ `target_mod in locs` **永远为假** ⇒ 一个假阳性。
已修（`loc_module_key` 截断后比），修后同包陷阱 0 处。

验证：四门禁全绿（含下调后的基线）+ designsystem desktop/android 编译与
`:feature:settings`/`:app` 编译、单测、打包 + 全模块测试 → **BUILD SUCCESSFUL**；
计数 **806 → 810 / 1301 → 1305**（+4 = `ColorHslTest`，`commonTest` 只计一次）零偏离；
资源 6 × 4 语言逐字一致 + 间接引用检查 0 项；lint 重测 **5 errors / 102 warnings**（零 delta）。

未验证：sheet 的真机交互（AI 生成配色 8 档、增删标签色、取色后算背景色）；
`colorToHsl` 与 `ColorUtils` 的等价性只由 4 个已知值用例担保（非全色域穷举）。

**`themeConfig` 剩 6 个文件**：`LauncherIconPickerSheet`（需图标表契约）+ VM 612 / Screen 1326 /
`ThemeConfig.kt` + 宿主壳。

### M5-19e 已完成（2026-09-24）：`LauncherIconPickerSheet` → 共享层（宿主注入图标表与渲染）

勘察查出两件事，后面所有判断都靠它们：原文件尾部的 `LauncherIcons` / `LauncherIconItem`
**零外部消费者**；其 `label` / `component` **全仓从未被读过**（换图标走
`LauncherIconHelp.changeIcon(String)`，按 value 字符串比 `className`，与 `ComponentName` 无关）。

契约照同模块先例 `BackgroundImageExtraOption`（宿主摊平数据作为参数，不用 CompositionLocal）：
`icons: List<LauncherIconOption>`，每项是 `value` + 一个 `@Composable (Modifier) -> Unit` 渲染槽。
宿主实现在 `:app` 新文件 `platform/LauncherIconOptions.kt`，渲染与迁移前**逐字等价**。

⚠️ **两次"别登记假条目，直接消掉债"**：① 带 `appCtx` 的表原样搬进 `platform/` 会让该目录首次出现
「全局 Context 直连」（G4 对新区域要求为零），而那 9 个 `ComponentName(appCtx, …)` 只服务于
**从未被读过**的 `component` ⇒ **删死字段**即真正消债；② 渲染取 drawable 改回
`LocalContext.current`（原 sheet 本就如此）—— composable 内作用域化的 Context 才是正确写法。
⇒ 基线本片**只有下调**（`themeConfig` 的 `appCtx` 2 → 1），**没有新增任何条目** ✅。

⚠️ 同包陷阱的 (a) 方向这次落在**调用点**：`ThemeConfigScreen` 需要两行新 import（被迁的 sheet +
宿主工厂）。工具另报出 `rememberLauncherIconOptions` 是 `:app` 私有符号 —— 即**本片新加的宿主胶水
会成为 `ThemeConfigScreen` 迁移时的前置**，已记账。

验证：四门禁全绿 + `:feature:settings` desktop 编译/`testAndroidHostTest` + `:app` 编译、单测、
打包 + 全模块测试 → **BUILD SUCCESSFUL**；计数 **810 / 1305 零偏离**；资源 `change_icon` × 4
语言逐字一致；lint **5 errors / 102 warnings**（零 delta）。

未验证：真机图标网格渲染与点击切换（`AndroidView(ImageView)`、`FIT_CENTER`、选中态边框）；
本片**未动** `LauncherIconHelp` 与 manifest。

**`themeConfig` 剩 5 个文件**（2026-09-24 实测）：`ThemeConfigViewModel.kt`(612) ·
`ThemeConfigScreen.kt`(1326) · `ThemeConfigRouteScreen.kt` · `ThemeConfig.kt`(61) ·
**`TopBottomBarSettingsSheet.kt`(178)**。

⚠️ 改正一处记账错误：M5-19d/e 的"剩 17 → 16 → 15"是**累减估算**（实测 **16**），且"余下"清单漏了
`TopBottomBarSettingsSheet.kt`（M5-19c 迁的是 `MainNavigationSettingsSheet` + `NavIconManageSheet`，
`git show --stat` 核过）。⇒ 文件数与清单每片实测，不能拿上一片的值往下减。

下一片建议先做 VM —— 它带着 `LauncherIconHelp` / `ThemeConfigStore` / `MD5Utils` / `postEvent` /
`toastOnUi` / `rememberLauncherIconOptions`（本片新加的宿主胶水）等一排 `:app` 私有符号。
