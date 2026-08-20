# Feature-first 工程结构与文件归属规范

> 状态：Android 单体整理与 KMP/CMP 模块化之间的过渡规范。本文定义新文件放置位置和旧目录迁移方式，不授权一次性移动现有源码。

## 1. 为什么需要过渡层

当前 `app/src/main/java/io/legado/app/ui` 约有 803 个 Kotlin 文件，其中 `book`、`widget`、`config`
体量很大；目录同时按页面、业务域、控件类型和历史技术分组。Compose Contract/Screen/ViewModel
已有基本形态，但没有统一的 Feature 所有权，因此仅靠 Compose 编码规范不能解决：

- 同一 Feature 的 route、状态、UI、dialog、兼容 Activity 分散；
- `widget`、`utils`、`help` 容易继续成为共享倾倒区；
- 当前包结构和未来 `:feature:<name>` 模块结构不同，模块化时还要再次大搬迁；
- 文件移动、业务重构和 KMP 抽取容易被混入同一个 PR。

采用三级演进，不直接创建几十个空 Gradle 模块：

```text
Stage A  :app 内 feature-first 包
Stage B  独立 Android :feature:<name> 模块
Stage C  有共享价值时再把该模块改为 KMP/CMP source sets
```

## 2. 统一命名

以“书籍详情”为例：

| 类型                  | 形式                               |
|---------------------|----------------------------------|
| 物理目录                | `feature/book-info/`             |
| Gradle project path | `:feature:book-info`             |
| Kotlin 包            | `io.legado.app.feature.bookinfo` |
| 类型前缀                | `BookInfo`                       |
| Android resource 前缀 | `feature_book_info_`             |

- 目录和 Gradle path 使用 kebab-case；Kotlin 包使用全小写且不含连字符。
- 不使用含义重叠的 `home` / `homepage`、`manage` / `management` 新命名；新增前先查 Feature catalog。
- Feature 名表达用户能力，不用 `screen`、`compose`、`new`、`v2` 或技术实现命名。

## 3. Stage A：`:app` 内的标准 Feature 包

所有新 Compose-first Feature，以及正在完整迁移且已有清晰 owner 的页面，目标位置为：

```text
app/src/main/java/io/legado/app/feature/<feature>/
├── FeatureContract.kt        # UiState、Intent、Effect、Dialog/Sheet 状态
├── FeatureViewModel.kt       # 状态所有者与 intent 分发
├── FeatureRoute.kt           # Navigation/host 接线；需要时才创建
├── FeatureScreen.kt          # 无状态业务 Screen
├── components/               # 只被该 Feature 使用的组件
├── dialog/                   # 复杂且独立的 Feature dialog
├── sheet/                    # 复杂且独立的 Feature sheet
├── model/                    # 仅 presentation 使用的稳定 UI model
└── legacy/                   # 临时兼容桥；必须记录删除条件
```

目录按需创建。四个核心文件也不是强制空模板：简单 Feature 可以合并小型 Contract 或组件，但不能把业务状态塞回
Composable。

### 文件归属

| 文件/职责                                     | 应放位置                                                   | 不应放位置                 |
|-------------------------------------------|--------------------------------------------------------|-----------------------|
| Screen、Contract、ViewModel、Feature 私有组件    | `feature/<name>`                                       | `ui/widget`、`utils`   |
| Navigation key/参数的稳定公开契约                  | Feature 根或未来 `api`                                     | 公共 widget、data        |
| 全局 nav graph 聚合、Koin 聚合                   | app host                                               | Feature Screen        |
| 两个以上 Feature 使用的纯 UI 组件                   | `core/ui` 或 `core/designsystem` 候选                     | 为“以后可能复用”提前移动         |
| 领域模型、Gateway、UseCase                      | 当前 `domain/<business>`，未来 `core:model` / `core:domain` | Compose package       |
| Repository 实现、DAO mapping                 | 当前 `data/<business>`，未来 `core:data` / platform data    | ViewModel、Composable  |
| Activity Result、Context、Service、通知、文件 URI | Android host/platform adapter                          | `commonMain`、纯 Screen |
| 阅读渲染、Rhino、Android service                | platform island                                        | 普通共享 Feature          |

### Route 与 Screen 的边界

- `FeatureRoute` 可取得 ViewModel、生命周期状态、导航回调和 Android launcher。
- `FeatureScreen` 只接收 `state`、`onIntent` 和必要的 UI 回调。
- Feature 不直接修改全局 back stack；公开“去哪”和“返回什么”，由 app host 接线。
- 兼容 Activity 只解析旧 Intent/extras/result，并转交 Feature；不成为第二状态所有者。

## 4. Stage B：提升为真实 Gradle Feature 模块

只有满足以下条件才从 `:app` 提升：

- Feature owner 和用户入口清晰；
- 已至少完成一个可运行的 Screen/流程，而不是空架构；
- 对 `app`、其他 Feature、data/platform 的依赖已经列清；
- 迁移后能删除或下调至少一个依赖/架构基线；
- 有独立编译或测试任务，并存在可回滚点。

目标形态：

```text
feature/<feature>/
├── build.gradle.kts
└── src/main/kotlin/io/legado/app/feature/<package>/...
```

Gradle module 默认使用 `:feature:<name>`。Feature 之间不依赖 `impl`；跨 Feature 跳转由
route/navigation contract 协作。只有契约确实被多个模块或宿主消费时才拆：

```text
:feature:<name>:api
:feature:<name>:impl
```

不要给每个页面默认生成 `api/impl`。

## 5. Stage C：按价值升级为 KMP/CMP

不是所有 Android Feature 都需要共享。升级后目录形态为：

```text
feature/<feature>/src/
├── commonMain/kotlin/...     # Contract、纯 reducer/presenter、可共享 Screen
├── commonTest/kotlin/...     # 状态归约和业务契约测试
├── androidMain/kotlin/...    # Android ViewModel/route/effect adapter
├── desktopMain/kotlin/...    # 目标确实需要时创建
└── iosMain/kotlin/...        # 目标确实需要时创建
```

- `Contract` 和纯状态归约通常最先成为 common 候选。
- `Screen` 只有不依赖 Android resource/API、navigation runtime 和平台 launcher 时才进入 `commonMain`。
- ViewModel/状态宿主是否共享必须通过实际依赖与 lifecycle API 验证，不因文件名机械移动。
- source set 不为未来假设预建；新增 target 同时新增真实 compile/test gate。

## 6. Core、platform 与 app host

长期物理结构统一为：

```text
app/android/                    # Android Application、导航图、Koin 聚合、兼容入口
app/desktop/                    # 需要时创建
core/model/                     # 领域值与序列化模型
core/domain/                    # Gateway、UseCase、纯业务规则
core/data/                      # Repository 契约与可共享组合
core/designsystem/              # token、theme、基础视觉组件
core/ui/                        # 跨 Feature 组合组件
feature/<name>/                 # 用户能力
platform/android/<capability>/  # DB、网络、服务、reader、rule engine 等实现
platform/jvm/<capability>/
```

`app` 只负责组装，不承载可复用业务实现。`core` 不依赖 Feature；Feature 不依赖其他 Feature 的实现；platform
实现依赖 core contract，反向依赖禁止。

## 7. 资源和测试

- Stage A 资源仍在 `app/src/main/res`，新资源统一使用 `feature_<feature>_` 前缀，便于模块提升时精确移动。
- Feature 私有 drawable/font/raw 记录 owner；没有第二调用方不移入公共资源。
- Stage B 后资源随 Feature module 存放；公共主题/token 才进入 designsystem。
- 测试路径镜像生产包：`app/src/test/.../feature/<name>` 或模块的 `src/commonTest`、
  `src/androidUnitTest`。
- Preview fixture、fake 和 test data 放在 Feature 的 preview/test source set；不放入生产 `utils`。

## 8. 旧结构迁移规则

1. 维护 `docs/dev/feature-catalog.md`，记录 canonical name、现包、入口、owner、状态和目标模块；先解决命名，不先移动文件。
2. 选择一个中小型、已 Compose 化的 Feature 做样板；不要先移动 `book/read`、`config` 或整个 `widget`。
3. 第一批 PR 只做 package/file relocation 与 import 修复，行为保持不变。
4. 第二批再收拢 Contract/Screen/ViewModel 和直接 DAO/host 依赖。
5. 边界稳定后才提升 Gradle module；KMP 化是之后独立切片。
6. 旧 Activity/Fragment/XML 未迁移时可以留在 `ui/...`；通过明确 route/adapter 调用新
   Feature，不为目录整齐强搬遗留实现。
7. Feature 完成迁移后，禁止继续向其旧 `ui/...` 包新增文件，并删除空目录和无调用方兼容桥。

每次迁移必须报告：移动文件、包名/API 变化、依赖边变化、旧路径剩余调用方、资源 owner、测试和回滚方式。

## 9. 首批建议

- 先建立 catalog 和静态检查的 report 模式。
- 从 `about`、`highlightTagRule`、`replace/edit` 等边界较小且已有 Compose Contract/Screen/ViewModel
  的候选中选一个样板，实际选择前做依赖审计。
- `book/read` 保持平台岛；`widget/components` 先做 owner/调用方盘点，不整体改名为 core。
- `config` 先作为一个 Settings 业务域，内部按 section 分包；不要立即为每个设置页创建独立 Gradle
  module。
