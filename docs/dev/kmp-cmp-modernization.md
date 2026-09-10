# Legado KMP/CMP 目标架构

> 状态：目标架构决策（2026-09-10）。本文回答“完成后是什么样”；当前差距、顺序和退出门禁见
> [`kmp-cmp-migration-plan.md`](kmp-cmp-migration-plan.md)。源码与本文冲突时，源码代表现状，本文代表目标，
> 不得把尚未实现的模块或任务写成当前事实。

## 1. “完整、规范”的定义

本项目的完整 KMP/CMP 不是把 Android 代码原样塞进一个 `shared` 模块，也不是让每个平台勉强编译。完成态必须同时满足：

- Android、Desktop/JVM、iOS 是明确支持的产品宿主；`modules/web` 继续作为连接应用内 Ktor WebService 的 Vue 客户端，
  除非另有产品立项，不把 Wasm 当作本轮完成条件。
- 领域模型、业务规则、数据访问编排、状态管理和适合共享的界面位于真实 KMP/CMP 模块，并由三个目标编译。
- 宿主只负责入口、生命周期、平台权限、系统集成、平台实现绑定和打包发布。
- Gradle 模块表达高内聚业务边界；不存在巨型 `shared`、巨型 `core:data`、`core:platform` 或新的 `help/utils/common` 倾倒区。
- 依赖通过构造函数和 Koin module 注入；生产代码不存在全局可变 `XxxProvider(s)`、`install/current` 注册器、
  `appCtx`、`appDb` 或 service locator。
- `io.legado.app.help.*`、`io.legado.app.utils.*` 不再是生产代码 owner；可规范化的实现按职责迁移并改名，
  无真实职责或调用方的实现删除。
- Android 数据、规则脚本、导入导出、阅读、TTS 和服务行为有兼容证据；每个平台的能力状态按
  compile / contract-test / smoke / package / release-ready 分级，而不是用空实现伪装“支持”。

“共享比例”不是指标。清晰边界、可替换实现、跨端一致语义和可发布产物才是指标。

## 2. 依据与对照结论

### 2.1 官方范式

本方案以当前官方文档为设计基线：

- Kotlin 推荐优先使用默认 source-set hierarchy；只有真实的平台子集需要共享代码时才建立中间 source set。
  手写 `dependsOn` 会关闭默认层级模板，必须有明确理由：
  [Hierarchical project structure](https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html)。
- Kotlin 官方建议优先使用普通接口和 DI；简单场景不要用 `expect class`，`expect/actual` 只承担无法通过普通依赖表达的
  平台原语：[Expected and actual declarations](https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html)。
- Android KMP library 使用 `com.android.kotlin.multiplatform.library`。Android Application 没有对应的 KMP
  application 插件，应保持为独立宿主模块：
  [Android-KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin)。
- Android 官方模块化原则是高内聚、低耦合、最小公开 API、默认 `implementation`，数据模块对外暴露 Repository，
  数据源保持内部可见：
  [Common modularization patterns](https://developer.android.com/topic/modularization/patterns)。
- Compose Multiplatform 的 Compose、资源、Lifecycle、ViewModel、Navigation 3 均可用于共享代码；
  因此 CMP 模块的 `commonMain` 可以且应该承载共享 UI，不能套用纯逻辑模块的“禁止 Compose”规则：
  [Resources](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html)、
  [Multiplatform ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)、
  [Navigation 3](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)。
- Room 已提供官方 KMP 配置；数据库升级与模块迁移仍须分开验证：
  [Room for KMP](https://developer.android.com/kotlin/multiplatform/room)。
- Koin 提供 KMP Compose/ViewModel/Navigation 3 集成，可继续作为本项目 DI 容器：
  [Koin for Compose](https://insert-koin.io/docs/reference/koin-compose/compose/)。

### 2.2 `D:\Project\shutiao\legado` 给出的证据

2026-09-10 实测参照仓库：`shared/src` 1450 个 Kotlin 文件；其中 `commonMain` 529、`sharedUiMain` 328；
Android 宿主 227 个文件，Desktop 宿主 110 个文件。它证明了 Room、规则引擎、共享 UI、Desktop/iOS 宿主可以穿过
Legado 的真实业务复杂度，但不代表其组织方式是推荐终点。

采纳的经验：

- 先建立可运行宿主和平台能力，再按垂直 Feature 迁移。
- Room schema、规则脚本、解析、文件、网络和阅读器必须有兼容测试，不能仅凭编译通过判断完成。
- Android/JVM 共用实现与 Native 实现可以不同，但公共契约的输入、输出、错误和取消语义必须一致。
- 书源 JS 暴露的类名、静态方法和对象形状属于外部 ABI，改名或换引擎前必须先有真实脚本语料回归。

明确不采纳：

- 单一巨型 `shared` 模块及复杂的手工 source-set 网。
- 大量 `XxxProviders.register()/get()` 服务定位器及依赖注册顺序。
- 把旧 `help`、`utils`、Android 命名和静态单例原封不动复制到 `commonMain`。
- 超宽 `PlatformCapabilities`、`Any` 类型擦除、默认 `null`/no-op 的“不支持”实现。
- 为迁移而公开 OkHttp、Gson、jsoup、Room DAO 等实现类型。
- 鸿蒙 fork 工具链和生成物文本改写；它不在本项目本轮产品目标内。

## 3. 目标工程形态

目录是方向图，只在出现真实代码、调用方和验证任务时创建模块：

```text
build-logic/                         # 模块类型、target、编译、测试、lint、资源约定

androidApp/                          # Android Application、外部 Intent、Service、通知、权限、发布
desktopApp/                          # Compose Desktop 入口、窗口/文件选择、打包
iosApp/                              # Xcode 壳、签名、平台集成
app-shared/                          # 三端共同的根 UI、Nav3 back stack、共享 DI 聚合；不放业务实现

core/model/                          # 少量跨业务稳定值、ID、错误与序列化协议
core/time/                           # Clock 等基础原语；有真实复用时才独立
core/io/                             # source/sink/path 等领域无关 I/O 语义
core/designsystem/                   # CMP theme、tokens、基础组件、共享资源
core/ui/                             # 跨 Feature 的组合 UI；不依赖具体 Feature

domain/library/                      # 书籍、章节、书架、阅读记录的业务规则与 ports
domain/source/                       # 书源/RSS 规则模型、解析编排与 source-runtime ports
domain/reader/                       # 阅读状态、分页/排版模型、会话用例
domain/settings/                     # 类型化设置模型与更新语义
domain/sync/                         # 备份、WebDAV、阅读进度协议与用例
domain/readaloud/                    # TTS 计划、角色、队列、provider ports

data/database/                       # Room entities/DAO/database/schema；实现细节，不给 Feature 直接依赖
data/library/                        # Library Repository 实现、mapping、local/remote data source
data/source/                         # Source Repository、cookie/cache/big-data 实现
data/settings/                       # DataStore/文件设置实现与迁移器
data/sync/                           # 备份、WebDAV、导入导出实现

runtime/source-api/                  # JS/HTML/JSONPath/XPath 执行契约与兼容语义
runtime/source-jvm/                  # Android/Desktop：Rhino + 锁定的 jsoup 1.16.2 等
runtime/source-native/               # iOS：经真实书源语料批准的 QuickJS/解析实现

feature/bookshelf/                   # 以下 Feature 均为 KMP/CMP，按用户能力命名
feature/search/
feature/book-info/
feature/reader/
feature/source/
feature/rss/
feature/settings/
feature/rules/
feature/readaloud/

platform/android/                    # Android-only 系统能力实现
platform/desktop/                    # Desktop/JVM 系统能力实现
platform/ios/                        # iOS 系统能力实现
compat/source-js-api/                # 仅保留经证据确认的外部书源 JS ABI 薄桥
```

不要求严格照此数量拆分。若两个候选模块总是一起变化、彼此了解内部细节且没有独立消费者，应先合并；若一个模块同时
包含互不相关的数据库、网络、UI 和系统服务，再按真实变更边界拆分。

### 3.1 依赖方向

```text
androidApp / desktopApp / iosApp
              │
              ├── app-shared ──> feature:* ──> domain:*
              │                       │            │
              │                       └────────> data ports
              │
              └── platform:* / runtime:* / data:* ──> domain contracts
```

硬规则：

1. 宿主是唯一 composition root；具体实现只能在宿主组装层被选择。
2. Feature 不依赖另一 Feature 的实现。跨 Feature 导航交换稳定 route key、ID 和结果，不传实体、ViewModel 或 DAO。
3. `domain:*` 不依赖 Feature、Compose、Room、Ktor、OkHttp、Gson、jsoup、Koin 或平台类型。
4. `data:*` 实现 domain repository port；DAO、HTTP response、文件句柄和序列化 DTO 不越过模块公共 API。
5. 平台模块依赖契约，契约不依赖平台实现。禁止 `core -> app`、`domain -> data implementation`、
   `feature -> platform implementation`。
6. Feature 默认一个模块。只有多宿主或多实现确实需要独立稳定契约时才拆 `api/impl`。
7. 公共 API 默认 `internal`/`private` 和 Gradle `implementation`；`api` 必须说明哪个下游需要编译可见。

### 3.2 模块类型而不是统一模板

| 模块类型 | `commonMain` 允许内容 | 目标/平台代码 | 典型例子 |
|---|---|---|---|
| pure KMP | Kotlin、coroutines、serialization、领域允许的 KMP 库；禁止 Compose/Room/platform | 少量原语适配 | `domain:*`, `core:model` |
| data KMP | Repository 实现、Room/Ktor 等被批准的 KMP 数据库与网络库 | driver、credential、平台 data source | `data:*` |
| CMP | Compose、CMP resources、Lifecycle/ViewModel/Nav3 的公共 API | launcher、权限、原生视图互操作 | `feature:*`, `core:designsystem` |
| platform | 单个平台 SDK 与具体实现 | 无 `commonMain` 要求 | `platform:*`, `runtime:source-jvm` |
| host | 入口、composition root、打包发布 | 对应平台 application | `androidApp`, `desktopApp`, `iosApp` |

`checkSharedPurity` 必须按模块类型工作。纯 KMP 的 `commonMain` 禁 Compose；CMP 的 `commonMain` 允许 Compose，
但仍禁止 `android.*`、`java.io.File`、Android `R` 和平台实现库。

## 4. 代码与包归属

### 4.1 禁止以形态命名职责

目标态不新增以下生产 package 或类型命名：

- package：`help`、`utils`、`common`、`base`、`manager`、`provider`。
- 无明确领域含义的类型：`XxxHelp`、`XxxUtils`、`BaseXxx`、`CommonXxx`、`XxxManager`。
- 全局状态入口：`XxxProvider.current`、`XxxProviders.get()`、`object` 中的可变 delegate、顶层 `appCtx/appDb`。

命名按职责：纯变换用明确动词或领域名；持久化用 `Repository`/`DataSource`；外部能力用靠近调用方的 port；
跨对象流程用 `UseCase`/`Coordinator`；平台实现以 `Android`/`Desktop`/`Ios` 前缀或 source set 区分。

示例：

| 旧 owner/命名 | 目标 owner/命名 |
|---|---|
| `help.config.*` | `domain:settings` 的模型/port + `data:settings` 实现 + 宿主迁移器 |
| `help.http.*` | `data:*` 私有 Ktor data source；Cronet/WebView 留 Android adapter |
| `help.storage.*` | `domain:sync` 用例 + `data:sync` 实现 + `core:io` source/sink |
| `help.book.*` | `domain:library` / `domain:reader` 的用例或 `data:library` data source |
| `help.source.*` | `domain:source` / `runtime:source-*` |
| `utils.StringUtils` | 使用点附近的命名函数或领域 formatter/parser |
| `utils.FileUtils` | `core:io` 接口与平台文件系统实现；UI picker 仍在 platform |
| `BaseRuleViewModel` | 每个 Feature 的共享 ViewModel + 可复用的无 UI `RuleTransferUseCase` |
| `SourceRuntime(Any, androidId, ...)` | 按调用方拆为 typed ports；不允许 `Any` 或 Android 概念进入契约 |
| `BigDataStoreProvider` | `SourceVariableRepository` 构造注入；entity 不执行 I/O |

### 4.2 兼容边界

“不保留旧实现”不等于破坏外部协议。先用证据区分：

- 内部源码兼容：不保留。调用方迁完即删除旧类型与旧 package，不为 import 零改动保留 façade。
- 数据兼容：用 serializer/migration 显式维护字段、schema 和默认值，不靠旧类所在 package 偶然维持。
- 书源 JS/反射 ABI：若真实规则会调用旧 FQCN 或 Java 静态签名，在 `compat:source-js-api` 保留无状态薄桥；
  业务实现仍委托规范模块。每个桥必须有脚本语料测试、owner 和删除/永久支持决策。
- Android 外部 Intent/ContentProvider ABI：留在 `androidApp` adapter，内部立即转成领域 route/command。

迁移桥最多跨两个里程碑；没有 ABI 证据的桥不得进入 `compat`。

## 5. 数据、平台与运行时策略

### 5.1 数据层

- Domain 对外只见 Repository port 和领域模型。Feature、ViewModel 不依赖 DAO、Room entity、Ktor client 或文件路径实现。
- Room entities/DAO/database 可以放 KMP source set，但它们属于 `data:database`，不是 domain model。
- 当前 Room 2.8.4 已有双 target 证据；官方当前文档以 Room 3 为主。版本升级必须是独立切片，通过 schema identity、
  103→当前版本迁移、备份恢复、事务、并发和 Android 真机测试后才切换，不能借模块移动顺便升级。
- 网络默认 Ktor client，由各宿主注入 engine 和 TLS/proxy/cookie 策略。不要在 domain 层复制一套通用
  `HttpRequest/HttpResponse`；只有书源脚本协议确实需要时，才在 `runtime:source-api` 定义稳定 DTO。
- 新共享序列化使用 kotlinx.serialization。Gson 只允许存在于 Android/旧数据兼容适配器，不提供名为 `GSON` 的
  common façade。
- 文件契约表达 `Source`/`Sink`、`ByteArray`、领域 document handle 或相对路径，不向公共 API 泄漏
  `File`、`Uri`、`InputStream`、bookmark/security-scoped URL。

### 5.2 平台能力

能力契约靠近使用它的领域，而不是集中塞入 `core:platform`。剪贴板、文件选择、通知、媒体会话等 UI/系统动作通过
Feature effect 或小接口注入；不可用返回显式 `Capability.Unavailable(reason)` 或领域错误。

`expect/actual` 仅用于每个目标必须静态提供的原语，例如平台名称或无法注入的极小工厂。Clock、dispatcher、文件、
crypto、network、rule engine 均优先普通接口 + Koin。每个 `expect` 需要记录“为何构造注入不足”。

### 5.3 书源规则运行时

- `runtime:source-api` 固化脚本可观察行为：绑定对象、异常、编码、cookie、重定向、HTML/JSONPath/XPath 选择和取消。
- Android/Desktop 先继续使用 Rhino、jsoup 1.16.2、现有 JsoupXpath/JsonPath；jsoup 与 Hutool 版本约束继续有效。
- iOS 的 JS/HTML 实现由兼容语料决定。QuickJS/其他 parser 只有通过相同 contract/golden tests 后才成为正式实现。
- `Any` 返回、Java 反射对象和 `org.jsoup.Connection.Response` 等只存在于运行时 adapter/ABI bridge，
  不进入 domain 或普通 Feature。

## 6. CMP UI、状态、资源与导航

- Feature 的 `UiState`、`Intent`、`Effect`、ViewModel 和 Screen 默认在 `commonMain`；ViewModel 使用 KMP AndroidX
  `ViewModel`，依赖通过构造函数传入。Desktop 提供 `kotlinx-coroutines-swing` 以满足 Main dispatcher。
- 不再创建 `core:viewmodel` 的继承式 `BaseViewModel/BaseRuleViewModel`。跨 Feature 的重复流程提取为无 UI 的
  UseCase/Interactor；各 Feature 保持自己的状态与事件语义。
- Compose 资源使用 Feature 自己的 `commonMain/composeResources` 与生成的 `Res`。不复制 Android `R.string`
  作为默认值，也不依赖 app 资源覆盖顺序。
- `core:designsystem` 是真实 CMP 模块，承载 theme、tokens 和稳定基础组件；`core:ui` 只收至少两个 Feature
  的真实复用组件。Android-only Miuix/Blur 等通过平台 slot/adapter 使用，不污染公共组件 API。
- Navigation 3 route key 可以共享；根 back stack 和三端一致的 entry graph 放 `app-shared`。外部 Intent、deep link
  验证、文件 picker 和平台返回结果由宿主适配。导航只传 ID/稳定参数，目标页从数据层单一真相源读取对象。
- Nav3 必须安装 saveable-state 与 ViewModel store entry decorators，保证 ViewModel 按 back-stack entry 释放。
- Insets、预测式返回、键鼠、无障碍、窗口尺寸和 iOS 手势分别有目标端 smoke；共享 UI 不等于忽略平台 UX。

## 7. DI 与组装

每个 `domain/data/feature/platform` 模块可导出一个职责明确的 Koin module。宿主按顺序组合：

```text
common foundation modules
    + domain/data modules
    + feature modules
    + target platform modules
    = host application graph
```

- 业务对象只声明构造函数依赖，不主动调用 `getKoin()`，不实现 `KoinComponent`。
- Koin DSL 只出现在 DI 文件或宿主边界；领域 API 不暴露 Koin 类型。
- 平台接口到实现使用显式绑定；ViewModel、UseCase、Repository 可用构造函数 DSL。
- 每个宿主有 graph creation test，启动时验证全部必需定义；不依赖 `install()` 调用顺序。
- 测试直接传 fake，不安装全局 delegate。

## 8. Target 与发布矩阵

| 能力 | Android | Desktop | iOS | 完成要求 |
|---|---|---|---|---|
| model/domain/reducer | 正式 | 正式 | 正式 | 三端编译 + commonTest |
| CMP resources/design system | 正式 | 正式 | 正式 | 视觉、字体、语言、无障碍 smoke |
| Room 数据 | 正式 | 正式 | 正式 | schema/migration/transaction/backup contract |
| Ktor 网络 | 正式 | 正式 | 正式 | TLS/proxy/cookie/取消/错误 contract |
| 书源 JS/解析 | Rhino/jsoup | Rhino/jsoup | Native engine | 真实书源 golden corpus parity |
| 文件导入导出 | SAF | native picker | document picker | 往返、权限恢复、取消测试 |
| 阅读器 | 正式 | 正式 | 正式 | 分页/选择/图片/性能与交互基线 |
| TTS/媒体/通知 | Android 实现 | Desktop 实现 | iOS 实现 | capability 明示 + 平台测试 |
| WebService | 正式 | 可选宿主 | 可选宿主 | 协议兼容，生命周期平台化 |

“完整”要求三个宿主至少达到 package；承诺为正式的能力必须达到 release-ready。某能力明确不属于某平台时，产品矩阵可标
“不适用”，但不能让 no-op 实现返回成功。

## 9. 架构门禁与完成条件

门禁应从源码事实生成，不手工维护易漂移数字：

- G0：Android 现有 unit/lint/architecture/assemble/release 回归。
- G1：Gradle 依赖方向、无循环、最小 public API、禁止 Feature impl 互依赖。
- G2-pure：纯 KMP `commonMain` 禁止 Compose、Android、JDK-only 和具体实现库。
- G2-cmp：CMP `commonMain` 允许 Compose/CMP resources/Lifecycle/Nav3，仍禁止 Android `R`、平台 API 和实现库。
- G3-data：schema、migration、序列化、备份恢复、事务、并发、取消、错误语义。
- G4-runtime：书源脚本 golden corpus、HTML/JSONPath/XPath、网络、cookie、JS ABI parity。
- G5-ui：三端 screenshot/semantics/smoke、navigation state restore、键鼠/手势/insets/accessibility。
- G6-release：Android AAB/APK、Desktop installer、iOS framework/app 的签名、升级、崩溃和性能观测。
- G7-legacy：禁止新增 `help/utils/base`、`XxxHelp/Utils`、全局 Provider/service locator；baseline 只降不升，
  最终归零（有证据的外部 ABI bridge 单列，不计作内部豁免）。

最终验收：

1. 三个宿主能从干净环境构建、测试、打包，正式能力与矩阵一致。
2. `androidApp` 不再承载可共享业务逻辑；旧 `:app` 单体与迁移 PoC 模块删除。
3. 生产源码无 `io.legado.app.help.*` / `utils.*` owner，无全局 Provider、`appCtx/appDb/GSON` 入口。
4. 所有共享 Feature 使用 CMP resources、共享 ViewModel/UDF 和构造注入。
5. 数据库升级/恢复、真实书源脚本、阅读/TTS/服务关键路径有 Android 回归和目标端证据。
6. 文档、模块图、CI task 与源码一致；不存在“计划中的 task”被描述为已存在。
