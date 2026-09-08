# Legado KMP/CMP 迁移规划（基于 `shutiao/legado` 参照样本）

> 写作时点：2026-09-05。
> 本文的「当前事实」全部来自本仓库实测扫描与 `D:\Project\shutiao\legado` 的实测调研，未采信二手描述。
> 与 `docs/dev/kmp-cmp-modernization.md`（2026-08-20 提案）的关系：那份是**方向图与门禁定义**，本文是**可执行的迁移规划**。
> 两者冲突时，以本文的实测基线为准，并把差异回填到那份文档。
> 与 `docs/dev/track-f-reader-kmp-migration-plan.md` 的关系：Track F 是本文 **P5** 的深化，不改其结论。

---

## 0. 一句话结论

参照样本用约 5 周、40 个提交，把一个 legado 分支改造成 `shared`（1450 文件 / 24.1 万行）+ 四端宿主的形态，证明这条路**走得通**。
但它能走通，建立在三个我们**不具备也不应复制**的前提上：集中攻坚的窗口期、鸿蒙 CPF fork 工具链、以及放弃 DI 改用 64 个手写 `XxxProviders` 注册器。

**我们要取的是它的分层纪律与硬骨头解法，不是它的组织形态与工具链。**
具体：取「`commonMain` 零 Compose」「`expect/actual` 只用于平台原语、业务能力走接口」「先啃 Room→网络→解析/JS 引擎→UI 的顺序」；
不取「单一大 `shared` 模块」「无 DI 的 Service Locator」「鸿蒙目标与 fork 工具链」「QuickJS 替换 Rhino 作为前置」。

---

## 1. 两个仓库的实测对照

### 1.1 参照样本（`shutiao/legado`，已迁移完成）

| 项 | 数值 |
|---|---|
| `shared/src` | 1450 文件 / 241,091 行 |
| └ `commonMain` | 529 文件 / 69,166 行，**其中仅 3 个文件 import `androidx.compose`** |
| └ `sharedUiMain`（全部 Compose UI） | 328 文件 / 93,034 行 |
| `app/src/main`（Android 宿主） | 227 文件 / 34,835 行，仅 20 个文件 import `androidx.compose`，7 个 Activity |
| `desktop/src/main`（JVM 宿主） | 110 文件 / 25,613 行 |
| `expect` / `actual` | 193 组声明 / 958 个实现（53 个是 `actual typealias`） |
| 手写服务注册器 | 64 个 `object XxxProviders`，193 处 `register()` 调用；**零 Koin / Hilt / Dagger** |
| 源集 | 15 个，含 `sharedUiMain` / `skikoUiMain` / `nonOhosUiMain` / `iosAndOhosUiMain` / `nativeMain` / `roomEntitiesMain`（编译粒度切片）/ `ohosCompatMain`（兼容开关） |
| 测试 | **无 `commonTest`**，只有 `jvmAndAndroidTest` 27 文件 / 2,821 行；书源规则与排版黄金用例为主 |
| 契约测试 | 仅 `modules/quickjs` 有（`QuickJsEngineTestBase` + `RealWorldSourceScenarioTestBase`）；193 组 `expect/actual` **无契约测试** |
| 架构守卫 | **无**。没有依赖方向检查、没有 `commonMain` 纯度检查 |
| convention plugin | 6 个 + 1 个条件插件（`legado.kmp.ohos`） |
| CI | 7 个 workflow，四端各自出包，含 `:shared:linkReleaseFrameworkIosArm64` |

### 1.2 本仓库（`legado-with-MD3`，迁移起点）

`app/src/main` 共 **1555** 个 Kotlin 文件，按顶层包：

| 包 | 文件数 | 含 `android`/`androidx` import | 含 `java`/`javax` import |
|---|---:|---:|---:|
| `ui/` | 706 | — | — |
| `data/` | 203 | **128** | 17 |
| `domain/` | 170 | **11** | 11 |
| `help/` | 124 | **64** | 62 |
| `utils/` | 107 | **78** | 39 |
| `lib/` | 97 | **54** | 20 |
| `model/` | 55 | **26** | 19 |
| `feature/` | 21 | — | — |
| `base/` | 21 | — | — |
| `constant/` | 14 | **6** | 0 |
| `service/` `exception/` `api/` `web/` `receiver/` `di/` | 36 | — | — |

关键依赖使用面：

| 依赖 | 文件数 | 备注 |
|---|---:|---|
| `androidx.compose.*` | 474 | UI 已大面积 Compose 化——**这是好消息**，CMP 迁移的桥比想象中短 |
| Koin | 170 | 已深度使用，不能推翻 |
| Room（`androidx.room` 2.8.4） | 91 | 39 个 `*Dao.kt` + 66 个 entities |
| `java.io.File` / `InputStream` / `OutputStream` | 122 | 最主要的 JVM 泄漏面 |
| coil3 3.5.0 | 39 | 已是 KMP 就绪库 |
| Rhino | 27 | `modules/rhino` 31 个 kt |
| `android.webkit` | 26 | 纯平台 |
| Cronet | 24 | 纯平台 |
| Glide | 21 | 与 coil3 并存，需清理 |
| jsoup 1.16.2 | 13 | **被 AGENTS.md 锁定版本**；`JsoupXpath` 1 个文件 |
| 单元测试 | 172 文件 | 基础尚可 |

已落地的前置成果（Track F）：

- `build-logic/` 已有 `LegadoKmpLibraryConventionPlugin`（Kotlin 2.4.10 + AGP 9.2.1，Android library + `jvm("desktop")`）。
- `:smoke:kmp-probe`、`:feature:reader:core` 与 `:core:platform` 已是真实 KMP 模块；
  `:core:platform` 落 `Clock`（expect/actual）+ `DispatcherSet`（接口+DI）+ `FileSystem`（接口+JVM 实现）
  + `Digest`（接口+JCA 实现）+ `HttpClient`（接口+Ktor 实现）+ `ImageDecoder`（接口+BitmapFactory/ImageIO），25 项契约测试
  （desktop：含原子写 6 场景 + SHA-256 NIST 向量 + HttpClient MockEngine 4 场景 + ImageDecoder 3 场景）；
  另有 `:smoke:network-kmp-probe`（D3 Go）、`:smoke:rhino-capability-probe`（D4 Go）、
  `:smoke:room-kmp-probe`（D1 Go，Room 2.8.4 KMP 双 target KSP 生成 + BundledSQLiteDriver 真实查询）三个 PoC smoke。
  `:feature:reader:core` **54 个 `commonMain` + 62 个 `commonTest`**，
  覆盖 `model/layout/style/navigation/selection/gesture/readaloud/transition/accessibility/source/pageestimate`。
- `.github/workflows/verify.yml` 已把两个模块的 `compileCommonMainKotlinMetadata` / `compileAndroidMain` /
  `compileKotlinDesktop` / `desktopTest` / `testAndroidHostTest` 纳入主 CI。
- 已有 `verifyConfigArchitecture` 历史债务棘轮（只降不升）。

**判断：我们的起点比样本迁移前更好。** `domain/` 只有 11/170 文件沾 Android、`constant/` 6/14、coil3 已就位、
已有 172 个测试和一条能跑的 KMP CI。缺的是**编译期纯度门禁**和**平台能力契约层**——样本这两项恰恰是弱项，
我们可以在它停下的地方做得更严。

---

## 2. 从样本提炼的 8 条可迁移纪律

每条都给出样本中的证据位置，便于后续复查。

1. **`commonMain` 零 Compose。** 样本 529 个 `commonMain` 文件里只有 3 个碰 `androidx.compose`，Compose 全部关在
   `sharedUiMain`（328 文件）。这是 24 万行 `shared` 能收敛的前提。
   → 证据：`shared/src/commonMain/**` vs `shared/src/sharedUiMain/**`。
2. **`expect/actual` 只用于平台原语，业务能力一律「接口 + 注册器/DI」。** 样本 193 组 `expect` 集中在
   dispatcher、时间、编码、HTTP 类型、DB 构造、图片解码、文件路径这类**类型级/函数级**原语；
   业务能力走 `PlatformCapabilities`（约 90 个方法，全部带 `unsupported("…")` 默认实现）+ `PlatformServices`。
   → 证据：`shared/src/commonMain/kotlin/io/legado/app/ui/root/PlatformServices.kt`、`PlatformCapabilities.kt`。
3. **硬骨头顺序：Room → 网络 → HTML/JS 引擎 → UI。** 样本的 git 节奏是
   `07-28 骨架搭建 → 07-28 主题/Coil3 → 08-03 app 单页化与逻辑下沉 → 08-16 多平台抽象收拢 → 09-01 收尾`，
   逻辑下沉排在能力补齐之后。
   → 证据：`git log --oneline --grep=kmp`。
4. **数据库：entities/dao/database 全进 `commonMain`，driver 按端注入。** Room 的
   `@ConstructedBy(AppDatabaseConstructor::class)` + `expect object`，KSP 自动生成各端 actual；
   Android `AndroidSQLiteDriver` / JVM `BundledSQLiteDriver` / iOS·鸿蒙 `NativeSQLiteDriver`。
   另把 5 个高频实体切到 `roomEntitiesMain` 源集，缩小 KSP 重编面——**这个编译粒度技巧值得照抄**。
   → 证据：`shared/src/commonMain/kotlin/io/legado/app/data/AppDatabase.kt`、
   `shared/src/{androidMain,jvmMain,iosMain,ohosMain}/.../data/*DatabaseDriver.kt`。
5. **网络：自建 `KmpHttpClient` 面。** `commonMain` 定义 19 个 `expect` 类型；JVM/Android 用 `actual typealias`
   直接别名到 okhttp3（零包装开销），native 用 Ktor CIO，鸿蒙走 napi 桥。上层只面向这套类型。
   → 证据：`shared/src/commonMain/kotlin/io/legado/app/help/http/KmpHttpTypes.kt`。
6. **HTML 解析必须保留 `org.jsoup` 兼容层。** 样本换成了 ksoup 0.2.6，但为**书源 JS**单独写了 9 个 `org.jsoup.*`
   兼容文件，且 JVM/Android 的 actual 带 `@JvmStatic`——因为 JS 桥按静态方法反射调用，必须对齐原版 Java jsoup 的
   静态 `connect/parse` 签名。否则存量书源全挂。
   → 证据：`shared/src/commonMain/kotlin/org/jsoup/Jsoup.kt`（头注释写明了这条约束）。
7. **文件路径一律 String，不引入 JDK `File`。** `expect object FileUtilsCommon` 提供
   exist/readBytes/writeBytes/createFolderIfNotExist/getPath，iOS/鸿蒙委托 `kotlin.io.File`；
   目录走 `AppFilesDirs` 门面；文件选择/SAF 走普通接口 `FilePickerService`。
   → 证据：`shared/src/commonMain/kotlin/io/legado/app/help/FileUtilsCommon.kt`、`help/file/AppFilesDir.kt`。
8. **依赖注册顺序是真实 bug 源，必须显式文档化 + 启动自检。** 样本在
   `IosProviderRegistry.kt` / `app/App.kt` 里写满了「须在 X 之前注册，否则 fail-silent 拿到空值」的注释。
   → 证据：`shared/src/iosMain/kotlin/io/legado/app/help/config/IosProviderRegistry.kt`、
   `app/src/main/java/io/legado/app/App.kt`。

## 3. 明确不复制的 5 条

| 样本做法 | 我们不这么做 | 理由 |
|---|---|---|
| 单一大 `shared` 模块（1450 文件 / 24 万行） | 按 Feature 切成多个小 KMP 库（`:core:model` / `:core:platform` / `:core:data` / `:feature:<name>`） | 我们已有 feature-first 结构与 `:feature:reader:core` 的成功模板；大模块会让「一个 PR 一个边界变化」的纪律失效 |
| 零 DI，64 个手写 `XxxProviders` 注册器 | **保留 Koin 4.2.2** | 我们有 170 个文件在用 Koin；`koin-core` 本身支持 KMP。推翻 DI 的成本远大于收益，且样本的注册顺序 bug 正是 Service Locator 的代价 |
| 鸿蒙目标 + CPF fork 工具链（Kotlin/CMP/Room 全换 fork 版本，配套 `deriveOhosRoom*` 三个派生任务） | 不引入 | fork 版本回退、schema 键剥离、suspend 签名差异等成本集中在鸿蒙一端，与我们产品目标无关 |
| QuickJS 替换 Rhino（含 cinterop、Android JNI、KSP `@JsApi` 静态分派表） | 不作为前置；先做 `RuleEngine` capability，Rhino 留 JVM/Android actual | 换引擎是**独立的高风险立项**，需要书源脚本兼容测试授权，不能夹在架构迁移里 |
| 自研导航（`AppRoute`/`AppNavigator`/`RouteBackStack`/`ScreenModel`） | 保留 Navigation 3 在宿主；先只共享 route 语义与参数 | 我们已有 Navigation 3 1.1.7 与 474 个 Compose 文件依赖它；换导航是第二个主要风险维度，不能与 KMP 同批 |

---

## 4. 必须先用 PoC 决策的差异项

这 6 项不能靠"看起来应该可以"推进，每项都要先出编译级证据。

| # | 议题 | 我们的现状 | 样本的做法 | 待验证结论 |
|---|---|---|---|---|
| D1 | **Room KMP** | `androidx.room` **2.8.4** | `androidx.room3` **3.0.1** | ✅ **Go（2026-09-05 PoC）**：`:smoke:room-kmp-probe` 证明 Room 2.8.4 KMP 在 commonMain 的 entity/DAO/`@Database(@ConstructedBy)` + `expect object` 编译、KSP2 为 Android+Desktop 双 target 生成 `ProbeDatabase_Impl`/`ProbeDao_Impl`/`ProbeDatabaseConstructor` actual、`BundledSQLiteDriver` 在 desktop JVM 跑真实 SQLite 查询（3 项测试：insert+query/Flow/delete）。**结论：留在 2.8.4，不升 room3**。发现：Android target 的 `Room.databaseBuilder` 要求 Context，host test 无法构造 DB（需 Robolectric/instrumentation），但 KSP 生成已验证可用。P3 迁移前需把 389 个 blocking DAO 函数转 suspend + 2 个 SupportSQLite 文件改 driver API |
| D2 | **HTML 解析** | jsoup **1.16.2**（AGENTS.md 锁定，不得升级）+ `JsoupXpath` | ksoup 0.2.6 + 9 文件 `org.jsoup` 兼容层 | ✅ **Go（2026-09-06）**：双轨成立。`HtmlParser`/`HtmlDocument`/`HtmlElement` 三接口进 `:core:platform` commonMain，`JsoupHtmlParser` 在 androidMain/desktopMain 委托 jsoup 1.16.2；8 项契约测试双 target 通过；首个真实消费方 `HtmlFormatter` 迁移后既有 11 项测试行为零变化。**接缝不是全量替换**：书源 JS 边界的 `org.jsoup.Connection.Response` 属平台岛不迁移（见下方 D2 落地记录）。ksoup 只在出现 native target 需求时再引入 |
| D3 | **网络** | okhttp 5.4.0 + Cronet（24 文件） | `KmpHttpClient` 面：okhttp typealias / Ktor / napi | ✅ **Go（2026-09-05 PoC）**：`:smoke:network-kmp-probe` 证明 Ktor 3.5.1 client-core 在 commonMain 双 target 编译、MockEngine 测试过、OkHttp 引擎在双 target 可实例化。结论：HttpClient 契约用 **Ktor client-core**（commonMain）+ okhttp 引擎（androidMain/desktopMain actual），Cronet 留 Android actual |
| D4 | **JS 引擎** | Rhino（27 文件）+ `modules/rhino` | QuickJS + KSP 分派表 | ✅ **Go（2026-09-05 PoC）**：`:smoke:rhino-capability-probe` 证明 Rhino 1.8.1（纯 JVM）在 commonMain `RuleEngine` 契约 + android/desktop actual 下工作，双 target 各 3 测试过（算术/绑定/字符串）。结论：RuleEngine capability 用 Rhino（不换 QuickJS，含书源兼容测试单独立项不进关键路径） |
| D5 | **图片** | coil3 3.5.0（39 文件）+ Glide（21 文件） | coil3 + `BookImageLoaders` 注册器 | coil3 已 KMP 就绪。先清理 Glide 残留（21 文件），再抽 `BookImageLoader` |
| D6 | **Gradle 环境** | Windows 上 Gradle transforms cache 跨盘句柄冲突 | —— | **已知阻塞**：Gradle 命令需显式 `-Dgradle.user.home=D:/Android/.gradle`，否则 `:app` 的 dataBinding/KSP 等 transform 任务会撞「锁拒绝访问」与 KSP「different roots」。所有验证命令统一带此参数 |

---

## 5. 目标结构（演进式，不预先建整棵树）

```text
build-logic/convention/            # 已有 1 个，按模块类型扩展
core/model/                        # :core:model        可序列化值对象（KMP）
core/platform/                     # :core:platform     窄能力契约（KMP）
core/data/                         # :core:data         Repository 接口 + 组合逻辑（KMP）
core/designsystem/                 # :core:designsystem CMP tokens + 叶子组件（KMP + CMP）
feature/<name>/                    # 逐个从 :app 升格为 KMP 库（:feature:reader:core 已是模板）
platform/android/                  # Android actual：Room driver、okhttp/Cronet、Rhino、WebView、服务
platform/jvm/                      # Desktop actual：BundledSQLiteDriver、Ktor、Swing/AWT 文件选择
app/                               # Android 宿主，退化方向 = 平台实现 + 组装（样本终点：227 文件）
desktop/                           # 首个非 Android 证明宿主
```

**依赖不变量**（沿用 `kmp-cmp-modernization.md` §3，并新增一条）：

1. `commonMain` 不依赖 Android、JDK-only、Room DAO/entity、资源 ID、`File`、URI、服务类型。
2. `commonMain` 不依赖 `androidx.compose.*`（新增，对应样本纪律 1）。
3. Feature `api` 不依赖其他 Feature；`impl` 只依赖他人的 `api`。
4. 无 core → feature 反向依赖。
5. 平台能力不可用时显式建模为 `unsupported`，不写静默空实现。

---

## 6. 分阶段迁移

### P0 —— 冻结基线与建立门禁（2 周，大部分已完成）

**已完成**：`build-logic`、`:smoke:kmp-probe`、`:feature:reader:core`、CI 已跑 KMP 编译、`verifyConfigArchitecture` 棘轮、**`checkSharedPurity`（G2）、`checkModuleDependencies`（G1）、`kmp-cmp-modernization.md` 盘点回填（均为 2026-09-05）**。**P0 全部完成。**

**待办**：

1. ~~新增 **`checkSharedPurity`** 任务~~（**2026-09-05 完成**）：`commonMain` 源码树禁止出现
   `import android.*` / `import androidx.*`（`androidx.compose.runtime.Stable` 白名单）/ `import java.io.File` /
   `java.io.InputStream` / `java.io.OutputStream` / `kotlin.jvm.*`。已落地为 `build.gradle.kts` 的
   `CheckSharedPurityTask`，**day 1 起 blocking**（当前全仓 0 违规，无需 report 阶段）。
   *样本没有这道门禁，这是我们在它停下的地方补的第一课。*
2. ~~新增 **`checkModuleDependencies`**~~（**2026-09-05 完成**）：禁止 core→feature、core→host、
   feature api→feature、feature impl→feature impl 的 Gradle 依赖方向。已落地为 `build.gradle.kts` 的
   `CheckModuleDependenciesTask`，day 1 起 blocking（全仓 0 违规），已接入 `verify.yml`。
3. ~~把 `docs/dev/kmp-cmp-modernization.md` 的盘点数字按本文 §1.2 更新~~（**2026-09-05 完成**）：
   `kmp-cmp-modernization.md` §2 盘点与 §6 门禁表已回填 2026-09-05 实测数字与 G1/G2 落地状态。

**退出条件**：`checkSharedPurity` 与 `checkModuleDependencies` 均在 `verify.yml` 中 blocking；
`:app` 与 `:feature:reader:core` 全部通过；历史泄漏数值冻结进基线（当前 0）。**P0 全部完成。**

### P1 —— 平台能力契约层（3–4 周）

建 `:core:platform`，每个契约都是「接口 + Android actual + contract test」三件套，**不改底层实现**：

| 契约 | 现状泄漏点 | Android actual | 备注 |
|---|---|---|---|
| `Clock` / `DispatcherSet` | `System.currentTimeMillis()`、`Dispatchers.IO` 直用 | 委托现有实现 | ✅ **2026-09-05 完成（P1-1）**：见下 |
| `FileSystem`（String 路径） | **122 个文件**用 `java.io.File`（实测 97 文件 import `java.io.File`） | SAF/内部存储 | ✅ **2026-09-05 契约落地（P1-FS）**：契约+JVM 实现+双 target 契约测试，试点 FileUtils 原子写委托；全量迁移归 P2 |
| `HttpClient`（`HttpRequest`/`HttpResponse`） | `help/http`（14 文件）、okhttp+Cronet | okhttp 5.4.0 / Ktor client-core | ✅ **2026-09-05 契约落地（P1-Http）**：见下；help/http 全量迁移归 P2 |
| `RuleEngine` | Rhino（27 文件） | Rhino | ✅ **2026-09-05 PoC Go（D4）**：`:smoke:rhino-capability-probe` 证明 Rhino 在 capability 边界下双 target 工作；正式契约入 `:core:platform` 待真实消费方 |
| `DatabaseDriver` | Room（91 文件） | 现有 Room | ✅ D1 PoC Go：留在 2.8.4 不升 room3；KSP2 双 target 生成可用、`BundledSQLiteDriver` 在 JVM 可跑。P3 迁移前需 389 blocking→suspend + SupportSQLite→driver |
| `Crypto`（AES/Digest/HMAC） | `help/crypto`（4 文件，CryptoUtils 0 调用方）+ JCA 散落 11 文件 | JCA | ✅ **2026-09-05 Digest 子契约落地（P1-Digest）**：见下；AES/HMAC 待真实消费方再加 |
| `ImageDecoder` / `BookImageLoader` | coil3（44 文件，Glide 已清零） | coil3 | ✅ **D5 Glide 清除完成 + ImageDecoder 契约落地（2026-09-06）**：见下 |

**P1-1 落地记录（2026-09-05）**：`:core:platform` KMP 模块已建（`legado.kmp.library`）。

- **`Clock`**：`commonMain` 接口（`nowMillis()`/`nowNanos()`）+ `expect fun systemTimeMillis/Nanos`，
  androidMain 与 desktopMain 的 `actual` 委托 `System.currentTimeMillis()`/`System.nanoTime()`。
  —— 跑通 **expect/actual** 流水线；系统时间属"各 target 必须静态提供的原语"，符合 AGENTS.md expect/actual 使用边界。
- **`DispatcherSet`**：`commonMain` 接口 + `DefaultDispatcherSet` 委托 `kotlinx.coroutines.Dispatchers`
  （coroutines 1.11.0 在 commonMain 可用）。—— 跑通 **接口 + DI** 流水线；用 `get()` 延迟求值避免触碰未安装的 Main dispatcher。
- **契约测试**：`commonTest` 4 项（Clock epoch 区间/单调非降；DispatcherSet io/default 可跑协程），
  双 target 各 4 项通过。负向证据：双 target 编译+测试全绿。
- **真实消费方**：`:feature:reader:core` 的 `WholeBookPageCoordinator` 原 commonMain 直调
  `System.currentTimeMillis()`/`System.nanoTime()`/`Dispatchers.Default`（4 处 JVM 泄漏，
  `checkSharedPurity` 因未拦 `java.lang.*` 而漏过）→ 改为注入 `clock`/`dispatcherSet`（默认
  `SystemClock`/`DefaultDispatcherSet`，行为等价）。`:feature:reader:core` 与 `:app` 均加
  `implementation(project(":core:platform"))`；feature→core 方向经 G1 守卫放行。
- ✅ `:core:platform` 双 target + `:feature:reader:core` 300 测试不变 + `:app` 编译 + app 单测 + 双守卫全过。
- **未完成**：`PageEstimateMetrics` 的 1 处 `System.currentTimeMillis()` 仍直调（object 注入形态不同，留后续）；
  `HttpClient`/`RuleEngine` 等其余契约未开始。

**P1-FS 落地记录（2026-09-05）**：`FileSystem` 契约 + `JvmFileSystem` 实现 + 双 target 契约测试。

- **契约**（`commonMain`）：`readBytes`/`writeBytes`/`exists`/`delete`/`makeDirs`/`writeTextAtomic`/
  `copyFileAtomic`——全 String 路径 + `ByteArray`，不暴露 `java.io.File`/`Uri`/`Context`。
  路径穿越检查留调用方构造层（参照样本纪律 7，不在契约重复）。
- **实现**（androidMain + desktopMain，均为 JVM）：`JvmFileSystem` 精确移植 `FileUtils` 的
  readBytes/writeBytes/replaceAtomic 逻辑（行为等价）。两 target 同实现。
- **契约测试**：commonTest 抽象基类 `FileSystemContractTest`（10 场景：读写往返、missing→null、
  exists/delete、makeDirs 嵌套、原子写新文件/覆盖/不留临时/失败保旧、原子复制覆盖/失败保旧，
  移植自 `FileUtilsAtomicWriteTest`）；desktopTest + androidHostTest 各一个子类提供 `JvmFileSystem`
  与临时目录。`:core:platform` 双 target 各 14 测试（Clock 2 + DispatcherSet 2 + FileSystem 10）。
- **真实消费方**（带测试安全网）：`:app` 的 `FileUtils.writeTextAtomic`/`copyFileAtomic` 委托
  `JvmFileSystem`，删除 FileUtils 内的 `replaceAtomic` 私有实现。`FileUtilsAtomicWriteTest`（6 项）
  作为安全网验证委托后原子写语义不变——全过。这是「接口 + 实现 + contract test」三件套
  在有真实生产消费方与既有测试下的落地范例。
- ✅ `:core:platform` 双 target 14 测试 + `:app` 编译 + `FileUtilsAtomicWriteTest` 6 项 + 双守卫全过。
- **范围边界**：P1 只迁原子写路径（2 方法）。`FileUtils` 其余 ~25 方法与全仓 97 文件的
  `java.io.File` 直用归 P2 分批迁移，不在本切片。`readBytes`/`writeBytes` 等基础契约已有
  测试覆盖，P2 调用方迁移可直接复用。

**P1-Digest 落地记录（2026-09-05）**：`Digest` 子契约 + `JcaDigest` 实现 + 双 target 契约测试。

- **契机**：核查发现 `help/crypto/CryptoUtils` 在 :app **0 调用方**（疑似死码/书源运行时用），
  JCA 散落 11 文件无单一中心；但 `domain/model/readaloud/SpeechIdentity`（纯逻辑 object，P2 KMP 候选）
  用 `MessageDigest.getInstance("SHA-256")` 生成 voiceId/analysisId/segmentId——这是阻碍该 object
  进入 commonMain 的唯一 JVM 依赖。故只立 **Digest.sha256** 子契约（不为 AES/HMAC 提前扩接口，
  AGENTS.md「无调用方抽象」）。
- **契约**（commonMain）：`Digest` 接口，仅 `sha256(data: ByteArray): ByteArray`。
- **实现**（androidMain + desktopMain，均 JVM，同实现 `JcaDigest`）：委托 `MessageDigest.getInstance("SHA-256")`。
  样本 shutiao/legado 用 mbedTLS cinterop；本仓库 native 需求未定，先 JCA。
- **契约测试**：commonTest 抽象基类 `DigestContractTest`（4 场景：空串/abc 的 NIST 已知向量、
  32 字节确定性、不同输入区分），desktopTest + androidHostTest 各一子类提供 `JcaDigest`。
- **真实消费方**：`SpeechIdentity` 的 `sha256` 委托 `digest: Digest = JcaDigest` 字段（object 单例默认注入，
  同 FileUtils/fileSystem 模式），移除 `import java.security.MessageDigest` 与
  `import java.nio.charset.StandardCharsets`（后者换 `Charsets.UTF_8`，kotlin common）。
  `SyncReadAloudVoicesUseCaseTest`（3 项）间接覆盖 SpeechIdentity，作安全网——全过。
- ✅ `:core:platform` 双 target 18 测试 + `:app` 编译 + `SyncReadAloudVoicesUseCaseTest` 3 项 + 双守卫全过。
- **价值**：SpeechIdentity 的唯一 JVM 依赖已契约化。后续 P2 将其纯算法迁入 `:core:model` 的
  `SpeechIdentityCalculator`，并把绑定 `JcaDigest` 的兼容入口保留在 :app；AES/HMAC 等待真实消费方出现再加。

**P1-Http 落地记录（2026-09-05）**：`HttpClient` 契约 + `KtorHttpClient` 实现 + MockEngine 契约测试。

- **契约**（commonMain）：`HttpRequest`（method/url/headers/`ByteArray?` body）/`HttpResponse`（statusCode/
  headers/`ByteArray?` body）/`HttpClient.send(suspend)`。不暴露 okhttp/Cronet/Ktor 类型（AGENTS.md 共享契约纪律）。
  含 `ByteArray` 字段，故 data class 手写 `equals`/`hashCode`（用 `contentEquals`）。
- **实现**（commonMain `KtorHttpClient(engine: HttpClientEngine)`，D3 已 Go）：用 ktor-client-core 映射
  HttpRequest↔Ktor↔HttpResponse。引擎由调用方注入（Android/Desktop 注入 okhttp 引擎，测试注入 `MockEngine`）。
  POST body 设 `ContentType.OctetStream` + `setBody(ByteArray)`。
- **契约测试**：`HttpClientContractTest`（4 场景：GET 映射状态/头/体、非 2xx 保留、POST body 不抛异常、
  空响应体），用 `MockEngine`（ktor-client-mock，无真实网络）验证 KtorHttpClient 映射正确，双 target 各过。
- **范围边界**：契约 + 真实实现 + 测试已立，但**无生产消费方**——`help/http`（14 文件）是 okhttp 层本身
  （无测试、深 okhttp 耦合，`StrResponse` 持 `okhttp3.Response`），全量迁移归 P2。这与 Clock/FileSystem/Digest
  不同（那三者有生产试点）；HttpClient 的"真实消费方"是 P2 的 help/http 迁移。D3 Go + 计划"先用接口窄化"
  是此刻立契约的依据。`:core:platform` 现 **22 测试/双 target**（Clock 2 + DispatcherSet 2 + FileSystem 10 + Digest 4 + HttpClient 4）。

**D4 PoC 记录（2026-09-05）**：`:smoke:rhino-capability-probe`，`RuleEngine` capability + Rhino actual + 契约测试。

- **结论：D4 = Go**。Rhino 1.8.1（纯 JVM）在 commonMain `RuleEngine` 契约 + android/desktop actual 下工作。
  双 target 各 3 测试过（算术 `1+2`、绑定 `a*2`、字符串拼接）。
- **契约**（smoke commonMain）：`RuleEngine.eval(script, bindings): Any?`——PoC 阶段最小返回类型；
  正式 P1 RuleEngine 契约按真实消费方收敛领域返回类型（布尔判定/字符串提取/数值）。
- **实现**（androidMain + desktopMain，均 JVM，同 `RhinoRuleEngine`）：委托 `org.mozilla.javascript.Context`。
  不换 QuickJS（AGENTS.md：Rhino 书源规则高行为风险，换引擎含兼容测试单独立项不进关键路径）。
- **未做**：正式 `RuleEngine` 契约入 `:core:platform` + 真实消费方（27 文件的 Rhino 书源规则）迁移归后续切片。

**D2 落地记录（2026-09-06）**：`HtmlParser` 契约 + `JsoupHtmlParser` actual + 双 target 契约测试 + `HtmlFormatter` 真实消费方。

- **结论：D2 = Go，接缝按「解析侧抽象 / 网络响应侧平台岛」切分**（不是全量替换 jsoup）。
- **契约**（`:core:platform` commonMain）：`HtmlParser.parseBodyFragment`、`HtmlDocument.body()` /
  `setPrettyPrint`、`HtmlElement.select` / `remove` / `html`。只覆盖当时唯一真实消费方 `HtmlFormatter`
  用到的 5 个 jsoup 调用，**不为 `AnalyzeByJSoup` / `JsoupExtensions.textArray` 的宽遍历面提前扩接口**
  （AGENTS.md「无调用方抽象」）。`setPrettyPrint` 是行为开关而非样式偏好：`prettyPrint(true)` 会给
  `html()` 插入换行缩进，直接改变清洗后的段落切分，故必须进契约。
- **实现**（androidMain + desktopMain，均 JVM，同 `JsoupHtmlParser`）：委托 jsoup **1.16.2**
  （AGENTS.md 锁定版本）。`checkSharedPurity` 已把 `org.jsoup.*`、`org.seimicrawler.*`（JsoupXpath）、
  `com.jayway.*`（JsonPath）、`org.mozilla.javascript.*`（Rhino）、`okhttp3.*` 列为 commonMain 禁止
  import——**这是本次补的缺口**：此前这些纯 JVM 库可以悄悄进 commonMain 而不被任何门禁发现
  （实测当时 0 违规，故 day 1 起 blocking）。
- **契约测试**：`HtmlParserContractTest` 8 场景（空输入仍有 body、`html()` 不含自身标签、
  script/style/noscript **连同内容**移除、prettyPrint 开关的行为差异、select 空结果、逗号并集选择器、
  只摘除被选中的元素），desktopTest + androidHostTest 各 8 项通过。
- **真实消费方**：`HtmlFormatter.formatDisplayText` 的 3 处 jsoup 直调改为走 `HtmlParser`
  （`private val parser: HtmlParser = JsoupHtmlParser`，同 `FileUtils.fileSystem` / `SpeechIdentity.digest`
  的 object 注入形态）。安全网是既有 `HtmlFormatterTest` 11 项，全过、行为零变化。
- **三条发现（影响后续所有 HTML 相关工作）**：
  1. `help/JsExtensions.get/head/post` 把 `org.jsoup.Connection.Response` **原样返回给书源 JS**，JS 桥靠
     反射调 `body()/code()/header()`。**jsoup 类型在 JS 边界必须保留原样——这一侧是平台岛，不抽象、
     不替换。** 参照样本被迫写 9 个 `org.jsoup.*` 兼容文件正是同一约束，我们只是把岛的边界画清楚了。
  2. jsoup 全仓使用点 **13 文件**，不止 `model/analyzeRule`：`help/JsExtensions`、`help/http/CookieManager`、
     `lib/webdav/WebDav`、`model/localBook/{EpubFile,MobiFile}`、`ui/rss/read/RssReadRouteScreen`、
     `ui/widget/components/text/HtmlContent`、`utils/{EncodingDetect,HtmlFormatter,JsoupExtensions}`。
  3. `utils/JsoupExtensions.kt` 的 `textArray` 用到 `org.jsoup.internal.StringUtil`（**internal API**）+
     `NodeTraversor` / `NodeVisitor` / `CDataNode` / `tag().preserveWhitespace()`。这是本契约**未覆盖**的
     宽遍历面；下一刀要扩接口时，代价主要集中在这里。
- **未做**：`AnalyzeByJSoup`(524) / `AnalyzeByXPath`(155) / `AnalyzeByJSonPath`(172) / `AnalyzeRule`(985) /
  `AnalyzeUrl`(999) 均未迁移；JsoupXpath 2.5.5、JsonPath、Rhino、okhttp 均未立契约（契约已禁止其进入
  commonMain，但实现侧尚无对应窄接口）。

**D1 PoC = Go（2026-09-05）**：`:smoke:room-kmp-probe` 证明 Room 2.8.4 KMP 可用。留在 2.8.4 不升 room3。
KSP2 双 target 生成 `ProbeDatabase_Impl`/`ProbeDao_Impl`/`ProbeDatabaseConstructor` actual，
`BundledSQLiteDriver` 在 desktop JVM 跑真实 SQLite 查询（3 项测试）。
发现：Android target `Room.databaseBuilder` 要求 Context，host test 无法构造 DB（需 Robolectric/instrumentation），
但 KSP 生成已验证可用。**之前"Windows JVM 原生 SQLite 风险"判断修正**：`BundledSQLiteDriver` 从源码编译 SQLite，
不依赖系统 SQLite，风险被高估。`checkSharedPurity` 白名单加 `androidx.room`/`androidx.sqlite`（KMP 兼容库）。

**退出条件**：每个契约都有 `commonTest` 契约测试；Android 行为测试无回归；`:core:platform` 在 desktop target 编译通过。

### P2 —— 纯逻辑下沉（4–6 周，可与 P1 后半并行）

> **进度（2026-09-06）**：`:core:model` 已建立并完成十一个子切片。第一批迁 `constant/` 的 8 个
> 零平台依赖对象（AppPattern/EventBus/IntentAction/NotificationId/PreferKey/ReadTipType/Status/Theme，
> 新增 `AppPatternTest` 14 条 characterization test，并清理 PreferKey 重复键 textSelectAble）；
> 第二批迁 `domain/model/readaloud/` 的 10 个纯值对象 + 4 个测试；第三批迁 `domain/model/` 零依赖
> 纯值对象 30 个（含 `settings/` 14 个、`manga/` 1 个）；第四批迁 `BookSearchScope`，以
> `kotlinx-serialization-json` common 依赖替代对 `app.utils.splitNotBlank` 的反向依赖，并在 commonTest
> 固化 JSON/旧逗号格式与异常输入共 5 条行为测试；第五批迁 `SpeechIdentityCalculator`，依赖
> `:core:platform` 的 `Digest` 契约，在 :app 保留绑定 `JcaDigest` 的 `SpeechIdentity` 兼容入口，commonTest
> 固化 ID 输入分隔符与角色排序语义；第六批迁 `Utf8BomUtils`，以 common 的 UTF-8 编解码与 `copyOfRange`
> 替代 JVM 字节 API，并固化 BOM 检测、文本/字节剥离和 marker-only 兼容语义各 3 条测试；第七批迁
> `AlphanumComparator`，固化文件名数字段、前导零和空名称排序语义；第八批迁 `ByteArray.indexOf`，
> 固化匹配、重叠模式及 `start`/`stop` 边界语义；第九批迁 `formatReadDuration`，固化零/负时长、
> 分钟秒与完整天时分秒显示语义；第十批迁 `MapExtensions`，固化大小写键查询及有界缓存不扩容语义。
> 第十一批迁 `String.splitNotBlank` 两个 overload，固化 vararg、Regex、trim/blank 过滤与 limit 语义；
> 第十二批建立 `:core:data` 并迁 `model/analyzeRule` 的 `AnalyzeByRegex`、`RuleAnalyzer` 和
> `RuleDataInterface`，用 commonTest 固化正则 capture/递归、选择器内分隔符及 10,000 字符变量外置语义。
> `CloudTtsVoiceConfigTest` 因依赖 `utils.GSON`、`AiModelRegistry`/`ContentChunker`/
> `PartialTranslationAssembler` 因同包隐式依赖 `AiCapability`/`TextChunk`（与 `@Keep` 序列化数据类同文件）
> 暂留 :app。双 target 编译 + 测试 + `checkSharedPurity` + `checkModuleDependencies` +
> `:app:compileAppDebugKotlin` + `testAppDebugUnitTest` 全绿。CI 已加 `:core:model` 五任务。
>
> **第十三层（2026-09-06）** 迁 `constant/` 的 `BookType`/`BookSourceType`/`SourceType`/`PageAnim`：
> 四者的外部依赖**只有** `androidx.annotation.IntDef`。处理方式是**只摘掉 `@IntDef(...)`，保留内部的
> `annotation class Type/Anim`**——那 9 个调用点（`@BookType.Type` 等）引用的是后者，因此调用点**一行未改**。
> 代价与依据：`@IntDef` 是 `@Retention(SOURCE)` 的编译期辅助注解，运行时与 R8 零影响；且 `BookType` 的
> `@IntDef` 本身就漏了 `video`，取值校验并不完整。新增 `BookTypeTest`（4 条：位掩码唯一性、
> 组合掩码并集、set/add/remove 位运算、tag 字面量）与 `TypeConstantsTest`（4 条：三对象取值逐一锁死 +
> 4 个标记注解类仍存在）。迁移前用归一化 diff 验证除 `@IntDef` 外与原文零差异。
> `constant/` 现只剩 `AppConst`/`AppLog`（`BuildConfig`/Context/Log，属平台能力，不下沉）。
>
> **第十四层（2026-09-06）** 迁 `domain/model/` 的 `@Keep` 组：`AiMessageParts`/`AiModels`/
> `BookContentProcessModels`/`TranslationModels`（共 33 处 `@Keep`），以及零依赖的 `AiModelRegistry`/
> `ContentChunker`/`PartialTranslationAssembler`。**`@Keep` 的处理是本次关键设计**：该注解的作用是让 R8
> 不裁剪 Gson 反射用的数据类，不能无脑删。项目的 `proguard-rules.pro` 里已有同类的显式
> `-keep class ...{*;}` 约定（`data.entities`、`model.translation` 以及 `DictPair`/`TextChunk`/`ModuleDef`
> 等），因此把逐类枚举**收敛为整包规则 `-keep class io.legado.app.domain.model.**{*;}`**。
> 之所以用整包而非逐类：漏保任何一个曾被 `@Keep` 保护的嵌套类，都会导致 release 包 Gson 反序列化静默失败，
> 而 debug 与单测都测不出来——整包规则在物理上不可能漏保。这些类虽移入 `:core:model`，**包名未变**，
> 按 FQCN 匹配的规则照旧生效。`BookContentProcessEngine`（依赖 Room 实体 `BookContentProcess` + `GSON`）
> 仍留 :app。
>
> **R8 语义验证（非「构建通过」而已）**：`assembleAppRelease`（7m14s）后取
> `app/build/outputs/mapping/appRelease/mapping.txt`，用脚本把 HEAD 中那 33 处 `@Keep` 各自保护的声明
> 逐个取出并匹配，结果 **33/33 均以原名保留**（`X -> X:` 表示未混淆、未移除），`domain.model` 包共 228 条
> 条目。即本次下沉**没有削弱任何一处的 R8 保护**。
> 复查方法可复用：mapping 行 `原名 -> 原名:` 即「保留且未重命名」，删改 `@Keep` 或 proguard 规则后可直接重跑。

按「泄漏度从低到高」而不是「目录从浅到深」排序：

1. `constant/`（14 文件，仅 6 个沾 Android）→ `:core:model`　✅ 已迁 12 个（前 8 个零依赖 + 第十三层 4 个
   `@IntDef`）；**仅剩 `AppConst`/`AppLog`**（`BuildConfig`/Context/Log/Settings，属平台能力，不下沉）
2. `domain/model/`（58 文件，`domain/` 整体仅 11/170 沾 Android）→ `:core:model`　✅ readaloud 10 + 零依赖 30
   + 第十四层 7 个已迁。**实测（2026-09-06 第十四层后）根目录只剩 4 个**：
   `BookContentProcessEngine`（依赖 Room 实体 + `GSON`）、`CoverAlbum`（`java.io.InputStream`）、
   `TranslationDictionaryPolicy`（`java.util.Locale`）、`HomepageModels`（另带
   `androidx.compose.runtime.Immutable`，而 `:core:model` 无 compose 依赖——`@Immutable` 是
   Compose 稳定性提示，移除只影响重组次数不影响正确性，但需单独决策）。子目录已全部清空
3. `utils/` 中的纯函数（字符串/编码/集合/URL，从 107 文件中挑）→ `:core:model`
  　**实测（2026-09-06）剩余候选均不值得迁**：`Throttle`→`Debounce` 用 `android.os.SystemClock`；
  　`toTimeAgo` 只有 1 个 Compose UI 调用方；`BookChapterExtensions` 依赖 Room 实体。
4. `model/analyzeRule/`（规则解析，样本 `commonTest` 里占比最高的测试域）→ `:core:data`
  　`RuleData`/`CustomUrl` 卡 `utils.GSON`（137 文件在用，已加入 `checkSharedPurity` 黑名单）；
  　其余 5 文件全撞 JVM-only 库（jsoup/JsoupXpath/JsonPath/Rhino）。
5. `help/config`、`help/storage`（13+8 文件）
6. **`java.io.File` 全量迁移（R3）—— 实测修正**：85 文件 import `java.io.File`，但严格筛选（阻塞依赖
   **只有** `java.io.File` 且正文无 `appCtx`/`appDb`/`GSON`/`Context`/`Uri`）后**只有 1 个**真正能
   转化为 commonMain 下沉（`TranslationCacheGateway`，已改）。其余 84 个全部另有 Android 绑定（`Context`/
   `Uri`/Room/`GSON`/`appCtx`/`splitties`），改 `File`→`FileSystem` 只是 Android 侧内部改写，不产生
   commonMain 边界变化。**结论：R3 的「97 文件 File 迁移」应从 P2 下沉目标中拆出——大部分归 Android
   侧内部重构，不是 KMP 迁移。** P2 的 `File` 相关工作收缩为：清理 domain 层网关契约中的 `File`/`Uri`
   泄漏（已做 `TranslationCacheGateway`，1 处）。
7. **`TranslationCacheGateway` 契约去 `File`（2026-09-06）**：领域网关 `getCacheFile(...): File` 改为
   `getCachePath(...): String`，`File` 只留在平台实现 `TranslationCacheRepositoryImpl` 内部。3 个外部
   调用方（`TranslationManager`/`ExportBookService`/impl 内部）改走 `FileSystem` 契约的
   `exists`/`readBytes`。这是 AGENTS.md「共享领域契约不得暴露 File/Uri/Context」的第一处执行。

**纪律**：每移动一组，先在原位置留适配层，迁移调用方后再删旧入口；一个 PR 只完成一个可说明的边界变化。

**退出条件**：`commonTest` 全绿；`compileCommonMainKotlinMetadata` + `compileKotlinDesktop` 通过；
`verifyConfigArchitecture` 基线与 `checkSharedPurity` 基线**双降**；Android 单测与 lint 无回归。

### P3 —— 数据层（3–4 周，D1 已 Go）

**D1 PoC 结论（2026-09-05）**：留在 `androidx.room` 2.8.4，不升 room3。KSP2 双 target 生成可用，
`BundledSQLiteDriver` 在 desktop JVM 跑真实 SQLite 查询。`checkSharedPurity` 白名单已加 `androidx.room`/`androidx.sqlite`。

**P3 迁移前置工作**（在正式下沉前必须完成）：
- ✅ **blocking DAO 函数 → suspend**（2026-09-07 完成）：Room 2.8.4 KMP 在非 Android target 上不支持
  blocking 函数。**实测精确计数 218 个真正 blocking**（非本文件早期写的 389——那个含非注解函数），
  已分 6 批转完，剩 0。38 个 DAO 文件全 suspend/Flow，仅 `BookGroupDao.isInRules` 保留非 suspend（纯函数无 DAO 查询）。
- ✅ **2 个 SupportSQLite 文件 → driver API**（2026-09-07 完成）：`AppDatabase.kt` + `DatabaseMigrations.kt`
  的 `SupportSQLiteDatabase` 全改 `SQLiteConnection`。
- ✅ **`@Database` 加 `@ConstructedBy` + `expect object`**（2026-09-07 完成）：`AppDatabase` 主体
  （103 entity 的 `@Database` 注解 + 39 DAO + views + autoMigrations + companion 常量 + `@TypeConverters`）
  已整体下沉 `:core:data` commonMain，`@ConstructedBy(AppDatabaseConstructor::class)` + `expect object`。
  KSP 为 Android + Desktop 双 target 生成 actual 与 `AppDatabase_Impl`。app 侧仅留 `appDb` 单例 +
  `dbCallback`（`setLocale(Locale.CHINESE)` / 预置分组 SQL 依赖 Android 栈 + `DefaultData`）。

> **下沉进度（2026-09-07 晚更新）**：**P3 主体已完成**——`56 entity + 39 DAO + AppDatabase` 全部下沉
> `:core:data` commonMain。app 侧 `data/entities/` 仅剩 9 个 `*Android.kt` 扩展 + `rule/` deserializer
> （均依赖 Gson JVM 库，正确地留 app），**无任何 `@Entity` 残留**。schema identityHash 与 app 侧一致
> （`a6f43940...`），104 个 schema JSON 已复制到 `core/data/schemas/`。门禁全绿。
>
> **实体下沉收官的关键手法**（沉淀到 MEMORY.md）：
> - `BaseSource` 的 JS 桥用「接口 + `JsExtProvider` 注入」剥离（P4-d），实体不再继承完整 JS 面；
>   五契约（KeyValueStore/CookieStore/SymmetricCrypto/Logger/SourceRuntime）+ JsonCodec 扩展破 `BaseSource`
>   对 JsExtensions/CacheManager/Gson 的依赖。
> - `BookSourceConverters`（依赖 Gson）改用 `JsonCodec` 重写下沉，ReviewRule 恒 null/"null" 语义保留；
>   6 个 rule deserializer 的「原始字符串回退」语义弱化（低概率边界，与样本仓取舍一致）。
> - `BookShelfItem`（UI DTO）纯数据下沉（去 `@Stable`/`toUiItem`，UI 扩展留 app），**不引入 compose/immutable
>   依赖**，BookDao 得以完整下沉。
>
> **剩余 P3 收尾（非阻塞）**：repository 层（92 文件，依赖 appDb + UI 类型 + Android 栈）是否继续下沉；
> `entities/*Android.kt` 扩展与 `rule/` deserializer 的 Gson 语义是否需要进一步契约化。

- ✅ Room entities（56）/ DAO（39）/ AppDatabase 下沉到 `:core:data` 的 `commonMain`（2026-09-07 完成）。
- ⬜ 高频实体（如 `BookChapter`、`Cache`、`Cookie`）切到独立源集 `roomEntitiesMain`，**照抄样本的 KSP 重编面优化**。
- ✅ **两端 driver 端到端注入验证**（2026-09-08）：新增 `core/data/src/desktopTest/…/AppDatabaseDesktopTest.kt`，
  用 `BundledSQLiteDriver` 在 **desktop 上真实建起 104 版 `AppDatabase`（103 entity）**——证明 KSP 生成的
  desktop actual 可用、全表可建，且 `room_master_table` identityHash 与下沉前导出 schema 一致
  （`a6f43940451deb4c04a525583e1bea59`，即下沉未改动 schema）。Android 侧 `appDb` 仍注入 `AndroidSQLiteDriver`。
- ✅ **schema 迁移兼容 / 并发·事务语义测试**（2026-09-08，同一测试类，6 项）：
  - `migratesFromExportedVersion103Schema`：按 `core/data/schemas/…/103.json` 手工建库 + 钉 `user_version`，
    再由 Room 跑 autoMigration 103→104（`httpTTS.speed`），校验 `user_version=104`、老数据保留、新列建出。
    证明 `schemas/` 仍是迁移链路的真实依据，且迁移在 **desktop 可执行**。
  - `deepWaterEntitiesRoundTrip` / `searchBookCascadesWithBookSource`：深水区实体（Book / BookSource 含
    rule 簇 TypeConverter / HttpTTS / RssSource / SearchBook 外键级联）往返读写。
  - `transactionRollsBackOnFailure` / `concurrentInsertsAreAllPersisted`：事务回滚与并发写语义。
  - ⬜ 仍未做：**导入导出（备份恢复）往返测试**。

**退出条件**：两端 driver 的 contract test 通过；现有 Android 数据库迁移与备份恢复测试无回归。

### P4 —— CMP 设计系统与首个 Feature（4–6 周）

- `:core:designsystem`：颜色/尺寸/排版 token + 无平台依赖的叶子组件。
- 首个 Feature 选**低风险、少系统依赖、非阅读主链**的页面（如「关于」「书源调试」「替换规则」一档，
  避开读正文、WebView 26 文件、Cronet、前台服务）。
- 只共享 `UiState` / `Intent` / `Screen` / `Content`；**导航 runtime 与 Effect 留在宿主**。
- 桌面宿主 `desktop/` 起最小可运行壳。

> **P4 进展（2026-09-08）**：`:core:designsystem` 模块已建立，并**首次接入 Compose Multiplatform 工具链**
> （全仓此前零 CMP：现有 KMP 模块 `feature/reader/core`、`:core:platform`、`:core:data` 全是纯 Kotlin）。
>
> **关键架构决策**（呼应 §5 不变量 2「commonMain 不依赖 androidx.compose.*」与样本纪律 1「commonMain 零
> Compose」）：
> - `commonMain` **零 Compose**，只放纯值 token（间距/圆角用 `Float`、色值用 ARGB `Long`）——
>   `checkSharedPurity` 白名单无需为此放宽。
> - Compose 映射层（`Color`/`Dp`）放在**专用 `composeMain` 源集**（android + desktop 共享），
>   该源集不是 `commonMain`，故 `checkSharedPurity` 不扫描它。命名对照样本仓 `sharedUiMain`，
>   但按「窄 UI 源集」而非整仓大 `shared` 落地。
> - CMP 插件 `org.jetbrains.compose` 1.12.0（对齐 Kotlin 2.4.10），**在模块内显式 apply**，
>   暂不抬进 `legado.kmp.library` 共享 convention（等第二个 CMP 模块形态稳定后再收口，遵守 AGENTS.md 代码生成纪律）。
>
> **验证**：`compileCommonMainKotlinMetadata` / `compileAndroidMain` / `compileKotlinDesktop` 三目标通过；
> `desktopTest` 3 项 token 契约测试全绿；`checkSharedPurity`/`checkModuleDependencies`/`verifyConfigArchitecture`
> /`:app:compileAppDebugKotlin` 全绿。**首个 Feature 选择留待下一片**（候选 `about`/`highlightTagRule` 均已做
> 依赖审计：`about` 的 Screen/Contract 深度耦合 `AppScaffold`/`SettingItem`/`FileDoc`/`AppUpdate`，
> `highlightTagRule` Contract 已泄漏 `android.net.Uri`，均需在迁移前先解耦 Contract 层）。
>
> **P4 组件侧下沉（2026-09-08）**：`ui/theme` 已落 `:core:ui` 之后的第一刀组件迁移——把
> `ui/widget/components` 中 **零 `io.legado.app.R`、零 app 单例** 的最大闭包子集
> **80 文件 / 8412 行** 下沉进 `:core:ui`（保留原包名 → `:app` 的 import 零改动），
> `:app` 侧同类文件由 146 降到 66。
>
> - **切片规则（可机械复核）**：文件不含 `io.legado.app.R`、不 import app 层包（`ui.theme`/
>   `ui.widget.components`/`domain.model` 除外）、无 `ui.main.*`，且其组件内依赖闭包同样满足。
>   **这纠正了先前判断**——43 处 `R.*` 只落在 43 个文件上，并不挡住整包约 60% 的组件。
> - **未下沉的 66 个文件只有三类阻碍**：① 43 处 `R.*`（几乎全是 `stringResource`/`painterResource`；
>   Android library 的 R 类只含本模块资源，`io.legado.app.R` 必然失效）；② `utils.GSON`
>   （`importComponents/ImportComponents.kt`）；③ `ui.main.MainDestination`（`icon/AppIcons.kt`）。
> - **9 个 `internal` 符号因跨模块不可见改 public**：`AnimatedActionButtonCore`、`SeriesButton` +
>   `SeriesIconButtonStyle` + 4 个尺寸常量、`bgEffectDraw`、`BgEffectPainter`、`BgEffectConfig.get`、
>   `sliderAccessibility`——纯可见性变化，行为零改动。**object 成员形态的 indented `internal`
>   用正则扫不到，只有编译器能兜住。**
> - **同包隐式依赖（无 import）是本次唯一的真实阻碍**：据此排除 `AppSearchBar.kt`（→`SearchBar`）、
>   `image/cover/*`（→`CoilBookCover`）、`topbar/Glass*TopAppBar.kt`（→`TopBarButton.kt` 的 internal helper）
>   ——core 不得反向依赖 app，这类文件只能留或整包一起后移。
> - **`:core:ui` 依赖补齐**：`androidx.compose.material`、`compose.materialIcons`、
>   `miuix-{blur,icons,preference}-android`。
> - **验证**：`:core:ui:compileDebugKotlin` + `:core:ui:testDebugUnitTest`（8 项，含随生产代码迁走的
>   `ReaderMenuVisualStateTest` 6 项）、`:app:compileAppDebugKotlin`、`:app:testAppDebugUnitTest`
>   （**630 项全绿** = 原 636 − 迁走的 6 项）、`assembleAppDebug`、`checkSharedPurity` /
>   `checkModuleDependencies` / `verifyConfigArchitecture` 全绿、`git diff --check` 干净。
> - **`lintAppDebug` 仍红，但与本切片无关**：5 个 app 自有 error（`BookInfoScreen.kt` 2×
>   `LocalContextGetResourceValueCall` + 1× `JavascriptInterface`，`BackstageWebView.kt` /
>   `BottomWebViewDialog.kt` 各 1× `JavascriptInterface`），HEAD 基线里本就没有条目。迁移的 20 条
>   基线条目已重定位为 `../core/ui/src/main/kotlin/...`（app lint 带 `checkDependencies=true`，
>   会连带扫依赖源码），error 数由 12 回到 5，即**未新增任何 lint 问题**。
> - **Stage B 仍未破冰**：三个 feature 自身深度依赖 `:app`（`BaseRuleViewModel`、`data.repository.*`、
>   `data.entities.*`、`utils.GSON`、`io.legado.app.R`）。组件下沉只是必要条件之一；下一片应先解
>   feature 自身的 `:app` 依赖，或先做那 43 处 `R.*` 的资源归属决策。
>
> **P4 组件侧下沉（第二片，2026-09-08）**：再下沉 **20 文件 / 3111 行** 进 `:core:ui`，并**建立
> `:core:ui` 的字符串资源归属**。`:app` 侧组件文件由 66 降到 46，`:core:ui` 组件面达 100 文件。
>
> - **关键杠杆是 `AppIcons`**：它是 9 个组件依赖的枢纽，唯一的 app 依赖是导航枚举
>   `ui.main.MainDestination`。把 `mainDestination()` 映射搬到宿主侧新文件
>   `app/ui/main/MainDestinationIcons.kt`（改 4 处调用点），`AppIcons` 即与导航语义解耦、可下沉。
> - **资源归属规则**：`:core:ui` 自带 `res/values{,-zh-rCN,-zh-rHK,-zh-rTW}/strings.xml`（本片 55 条）
>   作为**默认值**；app 侧同名资源按 Android 资源合并优先级覆盖，**app 的代码与资源零改动**。
>   迁移文件只把 `import io.legado.app.R` 换成 `import io.legado.app.core.ui.R`（本片 17 处）。
> - **新增两个纯 UI 依赖**：`kotlinx-collections-immutable`、`reorderable`（都是 app 已在用的 Compose 库）。
> - **主动退回 4 个文件**：`bookmark/{BookmarkItem,BookmarkEditSheet}.kt`、
>   `explore/{ExploreKindItem,ExploreKindLayout}.kt` 需要 `:core:data` 的 Room 实体
>   （`Bookmark`/`ExploreKind`）——**不让 UI 模块依赖数据层**，故留在 `:app`。这条边界应写进
>   `:core:ui` 的依赖纪律：只依赖 `:core:model` + UI 库，不依赖 `:core:data`。
> - **验证**：`:core:ui:compileDebugKotlin` + `testDebugUnitTest`（8 项）、`:app:compileAppDebugKotlin`、
>   `testAppDebugUnitTest`（**630 项全绿**）、`assembleAppDebug`（三份 APK）、
>   `checkSharedPurity` / `checkModuleDependencies` / `verifyConfigArchitecture` 全绿、
>   `git diff --check` 干净；`:app:lintAppDebug` 与上一片同（5 个既有 app 自有 error，本片未新增，
>   core/ui 相关 error 为 0）。
> - **tagrules 的组件依赖已基本解除**：`AppIcons`、`SelectionBottomBar`/`ActionItem`、
>   `AppFloatingActionButton`、`SearchBar`、`ReorderableSelectionItem`、`FilePickerSheet` 均已进
>   `:core:ui`；只剩 `importComponents/ImportComponents.kt`（GSON + 16 字符串）与
>   `rules/RuleListScaffold.kt`（依赖 topbar 链 `TopBarButton` → `TopBarLiquidGlass` →
>   `ui.animation.InteractiveHighlight`）。
>
> **P4 组件侧下沉（第三片，2026-09-08）**：下沉 **topbar 链 9 文件 / 1316 行** 进 `:core:ui`
> ——`ui/widget/components/topbar` 全部 7 文件 + `ui/animation/InteractiveHighlight.kt` +
> `ui/util/DragGestureInspector.kt`。`:app` 侧组件文件由 46 降到 39。顶栏是 50+ 屏的公共依赖，
> 本片是目前单点收益最高的一刀。
>
> - **链条拆法**：`TopBarButton` → `TopBarLiquidGlass` → `ui.animation.InteractiveHighlight` →
>   `ui.util.inspectDragGestures`。后两个是纯 Compose 手势/着色器工具（`android.graphics.RuntimeShader`
>   已有 `@SuppressLint("NewApi")`），一并下沉即整条链解开；`:app` 侧剩下的消费方
>   （`FloatingBottomBar`、`ReaderMenuGlass`、`DampedDragAnimation`）因包名保留而**零改动**。
> - **新增模块依赖 `:core:ui → :core:designsystem`**：`DynamicTopAppBar` 读
>   `ui.widget.components.list.ListUiState`（KMP commonMain 的纯状态契约）。方向合法
>   （Android UI → 纯契约），`checkModuleDependencies` 无新增违规。
> - **资源**：补 `back` / `search` / `cancel_select` / `list_loading_title` /
>   `list_selected_count` 共 5 条 × 4 语言（沿用第二片的「库侧默认值 + app 覆盖」规则），
>   迁移文件只换 R 导入（2 处）。
> - **可见性**：`BookInfoScreen` 从 `:app` 调用 `TopBarActionsRow` / `miuixTopBarSlotPadding` /
>   `miuixTopBarActionsEndPadding` 三个原 `internal` 符号，随跨模块改为公开；其余
>   （`LocalTopBarMergeState` / `topBarLiquidGlass` / `topBarLiquidGlassEnabled` /
>   `topBarActionSpacing`）经全仓扫描确认只在 `:core:ui` 内使用，保持 `internal`。
> - **验证**：`:core:ui:compileDebugKotlin` + `:core:ui:testDebugUnitTest`（8 项）、
>   `:app:compileAppDebugKotlin`、`testAppDebugUnitTest`（**630 项全绿**）、`assembleAppDebug`、
>   `checkSharedPurity` / `checkModuleDependencies` / `verifyConfigArchitecture` 全绿、
>   `git diff --check` 干净。
> - **下一片的组件面只剩三类硬依赖**：`:core:data` 的 Room 实体（`bookmark/*`、`explore/*`）、
>   `utils.GSON`（`importComponents/*`、`Json*Editor`、`text/HtmlContent`）、以及非组件的
>   feature 自身 `:app` 依赖（`base.BaseRuleViewModel`、`data.repository.UploadRepository`、
>   `utils.*` 扩展）。`RuleListScaffold` 的 topbar 阻塞已随本片解除。
>
> **P4 组件侧下沉（第四片，2026-09-08）**：下沉 **10 文件 / 2295 行** 进 `:core:ui`
> ——`rules/{RuleEditSheet,RuleListScaffold}.kt`、`list/ListScaffold.kt`、
> `settingItem/{CompactSettingItems,TinySettingItems}.kt`、`ReorderableConfigList.kt`、
> `AppPullToRefresh.kt`、`reader/ReaderMenuGlass.kt`、`FloatingBottomBar.kt`、
> `ui/animation/DampedDragAnimation.kt`。`:app` 侧组件文件由 39 降到 **30**，
> 剩余文件全部卡在具体的 app 单例/实体依赖上，不再有「只差资源」的文件。
>
> - **判定规则收紧为「无 app 单例依赖」**：本片文件只差 `io.legado.app.R` 字符串
>   （18 条 × 4 语言，沿用「库侧默认值 + app 覆盖」规则）或根本无 app 依赖；无一引用
>   `utils.*` / `data.*` / `base.*`。`RuleListScaffold` + `RuleEditSheet` 是 tagrules 的
>   最后两个组件阻塞，随本片解除。
> - **新增第三方依赖 `io.github.kyant0:capsule`**：`FloatingBottomBar` 用
>   `com.kyant.capsule.ContinuousCapsule` 做连续胶囊裁剪。app 侧本来就有这个依赖，只是
>   `:core:ui` 未声明——**「文件无 app 单例依赖」不等于「库依赖已齐」**，仍要靠编译兜底。
> - **`DampedDragAnimation` 一并下沉**是解开 `FloatingBottomBar` 的前置：它只依赖
>   `inspectDragGestures`（上一片已进 `:core:ui`）。`image/cover/{BookshelfCover,CoverBlurBackdrop}.kt`
>   因引用留守的 `CoilBookCover.kt` 而**主动留下**（core 不能反向依赖 app）。
> - **lint 基线**：9 条 `settingItem/TinySettingItems.kt` 条目重定位为
>   `../core/ui/src/main/kotlin/...`。
> - **验证**：`:core:ui:compileDebugKotlin` + `:core:ui:testDebugUnitTest`（8 项）、
>   `:app:compileAppDebugKotlin`、`testAppDebugUnitTest`（**630 项全绿**）、`assembleAppDebug`、
>   `checkSharedPurity` / `checkModuleDependencies` / `verifyConfigArchitecture` 全绿、
>   `git diff --check` 干净。
> - **lint 用独立 worktree 做了同环境对照**（同一 commit 前后各跑一次全量 `:app:lintAppDebug`）：
>   error **5 → 5（逐条相同，全部 app 自有）**；warning **89 → 90**。逐条 diff 后，唯一的净新增是
>   `reader/ReaderMenuGlass.kt:25 AnnotateVersionCheck`——该文件代码逐字节未变，是**移入 library
>   模块后 lint 才给出「建议加 `@ChecksSdkIntAtLeast`」**；另 2 条 `ReorderableConfigList` 警告只是
>   路径从 `app/...` 变成 `core/ui/...`（净 0）。顺带发现：同一 commit 的增量 lint 报 86 条、
>   全量报 89 条，**warning 计数在本仓不可当作回归信号**（error 可以）。
> - **Stage B 的组件侧只剩 `importComponents/ImportComponents.kt`（`utils.GSON`）**；
>   真正的闸门已转到 ViewModel 侧：`base.BaseRuleViewModel`（`Application`/`BaseViewModel`/
>   okhttp 上传）、`data.repository.UploadRepository`、`utils.{GSON,getClipText,sendToClip,toastOnUi}`、
>   `help.book.applyTagGroupRules`。
>
> **P4 规则基类去平台直连（第五片，2026-09-08）**：`BaseRuleViewModel` 原先直接依赖
> `okHttpClient` / `AppConst` / `ContentResolver` / `Uri` / `utils.{isAbsUrl,isUri,readText}`。
> 本片把「取导入文本」与「写导出文本」收敛为契约 `RuleTransferPlatform`
> （`io.legado.app.base.rules`），Android 实现 `AndroidRuleTransferPlatform` 留在 `:app`，
> 由 Koin 注入。**这是把规则基类搬进独立模块的前置**，本身不改任何用户可见行为。
>
> - **实现体逐行照搬**：`readImportSource` 与迁移前 `resolveSource` 归一化后**零差异**；
>   导出仅把 `openOutputStream(uri)` 换成 `openOutputStream(targetUri.toUri())`——调用方传
>   `uri.toString()`，SAF `content://` 的字符串往返无损。
> - **先立 8 项行为基线**（`app/src/test/.../base/BaseRuleViewModelTransferTest.kt`，Robolectric +
>   假契约）：导入分类 New/Update/Existing 与默认勾选、trim 后交给平台、解析失败进 `Error`、
>   空选择不写文件、写入内容与目标、写失败上报原因、空选择不上传、上传成功事件的
>   url/actionLabel/fileName。断言用**状态/事件等待**（`first { }` + `withTimeout`）而非虚拟时钟：
>   基类内部硬编码 `Dispatchers.IO`/`Main`，推进调度器不可靠。**局限**：该测试锁的是编排契约，
>   平台实现体的等价性靠「逐行照搬 + 人工比对」证明，不是差分测试。
> - **7 个子类构造函数各加一个 `transferPlatform` 参数**（`TocViewModel` 用命名参数传），
>   Koin 新增一条 `single<RuleTransferPlatform>`；无任何直接 `new` 调用点（全部走 `viewModelOf`）。
> - **验证**：`testAppDebugUnitTest` **638 项全绿**（新增 8 项）、`:core:ui:testDebugUnitTest` 8 项、
>   `assembleAppDebug`、三门禁全绿、`git diff --check` 干净。
> - **下一步（第六片）**：基类已无 okhttp/ContentResolver 直连，剩下 `BaseViewModel(application)`
>   （只需 `AndroidViewModel`）+ `UploadRepository` 接口位置 + 子类的 `context.{getClipText,sendToClip,
>   toastOnUi}`；处理完即可把 `BaseRuleViewModel`/`BaseRuleEvent` 移入 Android library 模块。
>
> **P4 共享 ViewModel 基类层下沉（第六片，2026-09-08）**：新建 `:core:viewmodel`（Android library），
> 下沉 **8 文件 / 1026 行**——`io.legado.app.base.{BaseViewModel,BaseRuleViewModel}` +
> `io.legado.app.base.rules.RuleTransferPlatform` + `io.legado.app.help.coroutine.{Coroutine,
> ActivelyCancelException,CompositeCoroutine,CoroutineContainer}`，随 `BaseRuleViewModel` 的 8 项
> 基线测试一并迁入。`UploadRepository` **接口**另下沉 `:core:data`（实现 `DirectLinkUploadRepository`
> 留 `:app`）。
>
> - **本片最重要的发现是「洋葱有三层」**：`BaseRuleViewModel` 对 `:app` 的依赖并不止 `BaseViewModel`——
>   `BaseRuleViewModel` → `BaseViewModel`（`context = getApplication<App>()`）→
>   `help.coroutine.Coroutine` → `Throwable.printOnDebug()`（读 `io.legado.app.BuildConfig`，全仓 130 处）。
>   只搬规则基类必然编译失败（第五片末尾的估计偏乐观）。故本片一次下沉整个「VM 基类层」。
> - **两处解耦（行为等价）**：① `BaseViewModel.context` 改 `getApplication<Application>()`
>   （对外仍是 `Context`，全仓无 `context as App`）；② `Coroutine` 的 `printOnDebug()` 改读本模块
>   `io.legado.app.core.viewmodel.DebugFlags.enabled`，由 `:app` 在 `App.onCreate` **首行**按
>   `BuildConfig.DEBUG` 注册（未注册 = release 行为，不打印栈）。
> - **零 import 改动**：所有包名原样保留，`:app` 只加一条 `implementation(project(":core:viewmodel"))`；
>   `BaseViewModel`（35 文件）与 `help.coroutine`（46 文件）的调用点一行未动。
> - **验证**：`:core:viewmodel:compileDebugKotlin` + `:core:viewmodel:testDebugUnitTest`（8 项）、
>   `testAppDebugUnitTest`、`:core:ui:testDebugUnitTest`、`assembleAppDebug`、三门禁、
>   `git diff --check`；`:core:viewmodel:compileDebugKotlin` / `:core:viewmodel:testDebugUnitTest`
>   已加入 `verify.yml`。
> - **顺带修掉一个潜伏缺陷**：`app/src/test/.../dialog/TimePickerDialogTest.kt` 测的是
>   `TimePickerDialog.kt` 的 `internal` 函数，而该文件第二片已移入 `:core:ui`——跨模块后测试本应编不过。
>   它之所以连续四片「绿」，是因为 Kotlin **增量编译**只重编改动的源文件，未改动的测试类沿用了移动前
>   的旧 class（`internal` 只在编译期检查，运行期照跑）。本片给 `:app` 新增 project 依赖使测试编译任务
>   失效，才暴露出来。修法：测试随生产代码移到 `:core:ui/src/test`。
>   **教训**：跨模块可见性变更必须靠**干净重建**验证，增量 `testAppDebugUnitTest` 不能证明没有这类残留。
> - **仍未解除**（下一片）：子类仍用 `context.{getClipText,sendToClip,toastOnUi}`、
>   `utils.GSON`、`help.book.applyTagGroupRules`、`io.legado.app.R`。`BaseRuleViewModel` 本身已可离开
>   `:app`。
>
> **P4 规则 VM 去平台交互直连（第七片，2026-09-08）**：`:core:platform` 新增两个契约
> `Clipboard`（`getText` / `setText`）与 `Toaster`（`toast` / `longToast`），各带
> `XxxProvider`（`install` / `uninstall` / `current`，未安装抛 `IllegalStateException`）；
> `:app` 的 `PlatformServices` 用既有 `Context.getClipText()` / `sendToClip()` /
> `toastOnUi()` 适配并注册。4 个规则 VM（tagrules ×2、txttoc、dict）改用契约，
> 不再直接调 `context` 扩展。
>
> - **保留既有副作用**：`Clipboard.setText` 委托 `sendToClip`，因此**仍会弹一次「复制完成」提示**。
>   这是迁移前行为，按「行为等价优先」原样保留，并在契约注释里写明；要「只复制不提示」须另立能力。
> - **两个接口而非一个**：`Toaster.toast` / `longToast` 对应 app 侧既有的 `toastOnUi` /
>   `longToastOnUi` 一对扩展，不合并成带 `duration` 的方法——否则会把 Android `Toast.LENGTH_*`
>   常量语义带进共享契约。
> - **为什么走 provider 而不是 Koin 构造注入**：`:core:platform` 既有 5 个能力契约
>   （`KeyValueStore` / `CookieStore` / `SymmetricCrypto` / `Logger` / `SourceRuntime`）统一是
>   「`XxxProvider` + `PlatformServices.install()`」模式，剪贴板/轻提示属同一类平台能力；
>   沿用同一模式可让 VM 不必再改构造函数（与第六片 Koin 注入的 `RuleTransferPlatform` 分工：
>   后者是「规则导入导出的业务能力」，前者是「平台交互能力」）。
> - **验证**：`:core:platform:testAndroidHostTest`（新增 5 项 provider 契约测试）+
>   `:core:platform:desktopTest`（双目标）、`testAppDebugUnitTest`、`:core:ui` / `:core:viewmodel`
>   测试、`assembleAppDebug`、三门禁、`git diff --check`。
>
> **Stage B（tagrules 提升为 Gradle 模块）剩余清单**（逐项都可独立成片）：
>
> | # | 剩余阻碍 | 涉及 | 备注 |
> |---|---|---|---|
> | ~~1~~ | ~~`utils.GSON` / `fromJsonArray` / `fromJsonObject` / `isJsonArray` / `isJsonObject`~~ | 6 个规则 VM + `importComponents/*`、`Json*Editor`、`text/HtmlContent` | ✅ 第九片已解决（门面下沉 `:core:data/androidMain`） |
> | ~~2~~ | ~~`help.book.applyTagGroupRules(books, rules)`~~ | TagGroupRuleViewModel | ✅ 第八片已解决 |
> | 3 | `importComponents/ImportComponents.kt`（Gson JSON 树） | tagrules Screen | `JsonCodec` 无 JSON 树 API；或给 `:core:ui` 加 Gson，或抽 JSON 树契约 |
> | 4 | `io.legado.app.R` 字符串 | tagrules 全部 Screen/EditSheet/VM | 沿用「库侧默认值 + app 覆盖」策略，为 `:feature:tagrules` 建自己的 `strings.xml` |
>
> **P4 消除 `applyTagGroupRules` 重复实现（第八片，2026-09-08）**：`:core:data` 的
> `TagGroupRuleApplier` 本来就是 app 侧 `help.book.applyTagGroupRules(books, rules, groupDao, bookDao)`
> 的镜像（原注释明写「keep the two in sync」）。本片把「全量重算」的唯一入口收敛到
> `BookGroupMutationGateway.applyTagGroupRulesToAllBooks()`（实现在 `BookGroupMutationRepository`，
> 事务内调 `TagGroupRuleApplier`），`TagGroupRuleViewModel` 改走该 gateway，并删除 app 侧已成
> 死代码的两个 `applyTagGroupRules` 重载（74 行）与随之无用的 import。
>
> - **语义等价逐项核对**：`Book.getDisplayTagList()` = `(getCustomTagList() + getSourceTagList()).distinct()`，
>   与 `TagGroupRuleApplier.displayTagList()` 一致（两者都是 `customTag`/`kind` 经
>   `splitNotBlank(",", "\n")` 后 `distinct`）；`rules.isEmpty()` 早退、非法正则 `mapNotNull` 跳过、
>   `book.group or newGroupMask` 只加不清、只写有变化的书——全部一致。
> - **一处刻意差异（已写进 KDoc）**：原 VM 先取 `books`/`rules` 再进事务，新路径在事务内直接读全量。
>   少一次事务外读，且避免读到陈旧快照；`BookGroupMutationRepositoryTest` 的既有用例覆盖同一语义。
> - **顺带清掉**：`TagGroupRuleViewModel` 的 `BookRepository` 构造参数（只服务于那次冗余读取）
>   与 `BookGroupMutationGateway` 早已在 Koin 中绑定，无需新增注册。
> - **验证**：`BookGroupMutationRepositoryTest` **8 项**（新增 2 项直测 gateway：只添加匹配分组、
>   缺失分组先建再套用）、`testAppDebugUnitTest` 631 项、`:core:data:testAndroidHostTest` 72 项 +
>   `:core:data:desktopTest` 81 项、`:core:ui` / `:core:viewmodel` / `:core:platform` 全绿、
>   `assembleAppDebug`、三门禁、`git diff --check`。
> - **Stage B 剩余**：只剩 #1（`utils.GSON` 语义）、#3（`ImportComponents` 的 JSON 树）、
>   #4（`R` 字符串策略）三项。
>
> **P4 GSON 门面下沉（第九片，2026-09-08）**：把 `io.legado.app.utils.GSON` / `INITIAL_GSON`
> 及其 `*Android.kt` 反序列化兼容层（`data/entities/TxtTocRuleAndroid.kt`、
> `data/entities/rule/RuleAndroid.kt`，共 **333 行**）移入 **`:core:data/src/androidMain`**；
> `String.isJsonObject()` / `isJsonArray()` 移入 `:core:model` commonMain（原在 `:app` 的
> `utils/StringExtensions.kt`）。包名全部保留，`:app` 侧零 import 改动。
>
> - **为什么是 androidMain 而不是 commonMain**：`com.google.gson` 是 JVM 三方库；
>   `:core:data` 的 commonMain 受 `checkSharedPurity` 约束，且已用跨平台契约 `JsonCodec`。
>   Gson 门面只服务尚未迁移的 Android 代码，故落 androidMain；用 `api(libs.gson)` 暴露给消费方
>   （`:app`、`:core:viewmodel`、未来的 `:feature:tagrules`），免得每个模块各自声明 Gson。
> - **兼容层必须一起搬**：`GSON` 注册的 7 个 `JsonDeserializer` 就在这两个 `*Android.kt` 里
>   （实体下沉时把 `@SerializedName(alternate = ["rule"])` 等语义搬到了这里）。只搬 `GSON`
>   会形成 `:core:data → :app` 环——**先查清被搬文件的依赖闭包，再决定搬什么**。
> - **行为由既有测试守住**：`TxtTocRuleDeserializerTest`（6 项：旧备份 `rule` 键名提升、
>   `chapterRule` 优先、序列化只写 `chapterRule`、旧格式数组往返）留在 `:app`，直接覆盖搬走的 `GSON`。
> - **验证**：`testAppDebugUnitTest` 631 项（含上述 6 项）、`:core:data:testAndroidHostTest` 72 项 +
>   `:core:data:desktopTest` 81 项、`:core:model:testAndroidHostTest` 59 项、`:core:ui` 9 项、
>   `:core:viewmodel` 8 项、`:core:platform` 73 项，全绿；`assembleAppDebug`、三门禁、
>   `git diff --check`。
> - **效果**：`HighlightTagRuleViewModel`、`DictRuleViewModel` 的 app 层 import **归零**；
>   `TagGroupRuleViewModel` 只剩 `io.legado.app.R`；`TxtTocRuleViewModel` 剩 `R` + `help.DefaultData`。
> - **Stage B 剩余**：只剩 #3（`ImportComponents` 的 JSON 树）与 #4（`R` 字符串策略）。
>
> **P4 Stage B 首例：`:feature:tagrules` 模块成立（第十片，2026-09-08）**：新建
> `:feature:tagrules`（Android library + Compose），迁入 **6 文件 / 918 行**——
> `group/{TagGroupRuleContract,TagGroupRuleEditSheet,TagGroupRuleViewModel}.kt` 与
> `highlight/{HighlightTagRuleContract,HighlightTagRuleEditSheet,HighlightTagRuleViewModel}.kt`。
> 包名保持 `io.legado.app.feature.tagrules.*`，**`:app` 侧 import 零改动**。
>
> - **资源策略落地（本片即 #4）**：模块自带 `res/values{,-zh-rCN,-zh-rHK,-zh-rTW}/strings.xml`
>   （15 条，从 app 原样抄录）作默认值；3 处 `import io.legado.app.R` 改为
>   `import io.legado.app.feature.tagrules.R`。app 侧同名资源按资源合并优先级覆盖 → 文案不变。
> - **`:app` 只加一条 project 依赖**；`ui/main/bookshelf/GroupManageSheet.kt` 与 `di/appModule.kt`
>   的引用（`TagGroupRuleEditSheet` / `TagGroupRuleViewModel` / `TagGroupRuleIntent`）因包名保留而零改动。
> - **仍未迁入**：`highlight/HighlightTagRuleScreen.kt`（376 行）——它依赖 app 侧的
>   `ui.widget.components.importComponents.{BatchImportDialog, SourceInputDialog}`（Gson JSON 树，
>   即剩余清单 #3）。这条待办写在该模块 `build.gradle.kts` 的头注释里。
> - **新依赖补齐的教训**：首次编译报 `Unresolved reference 'isJsonArray'`——第九片把两个谓词放进
>   `:core:model`，而 `:core:data` 对 `:core:model` 是 `implementation`，**不传递**。新建模块时按
>   「实际用到的符号」逐条列依赖，别假设能透传。
> - **验证（干净重建：删 `app/build` 与 `feature/tagrules/build`）**：`:feature:tagrules:compileDebugKotlin`、
>   `testAppDebugUnitTest` 631 项、`:core:data` 72/81 项、`:core:model` 59 项、`:core:ui` 9 项、
>   `:core:viewmodel` 8 项、`:core:platform` 73 项，全绿；`assembleAppDebug`；三门禁；
>   `git diff --check`；`:feature:tagrules:compileDebugKotlin` 已加入 `verify.yml`。
> - **Stage B 剩余**：只剩 #3（`ImportComponents` 的 JSON 树），之后 `HighlightTagRuleScreen.kt`
>   即可随最后一个切片迁入，tagrules 提升完成。
> - **注意**：`:feature:tagrules` 目前**没有测试**（tagrules 原本也没有），CI 只编译它。

**退出条件**：Android 视觉与行为基线通过；Desktop 能编译并完成该 Feature 主路径；`checkSharedPurity` 无新增违规。

### P5 —— 阅读器（接续 Track F，独立节奏）

沿用 `track-f-reader-kmp-migration-plan.md`：共享业务状态/排版模型/配置，渲染器本体留 Android。
本阶段与 P4 无依赖，可按人力并行。

### P6 —— Desktop 宿主产品化（视 P4 结果决定）

只有 capability 矩阵、发布链、崩溃/性能观测、数据兼容方案明确后，才把 Desktop 从「架构证明」提升为「正式 target」。

### P7 —— iOS（暂不排期）

样本在 iOS 上投入了 cinterop（quickjs/mbedtls/nskeyvalueobserving）、Ktor CIO、`NativeSQLiteDriver` 和一套
napi 之外的桥。在没有产品需求之前，**不启动**。

---

## 7. 门禁分级

| 级别 | 适用范围 | 必须通过 | 状态 |
|---|---|---|---|
| **G0** Android 基线 | 所有 PR | `testAppDebugUnitTest`、`lintAppDebug`、`verifyConfigArchitecture`、`assembleAppDebug`、`git diff --check` | ✅ 已存在（`verify.yml`） |
| **G1** 模块边界 | 新/改 Gradle 模块 | convention plugin、`checkModuleDependencies`、无循环 | ✅ `checkModuleDependencies` 已实现并接入 `verify.yml`（2026-09-05）；当前全仓 0 违规，day 1 起 blocking |
| **G2** Common 纯度 | `commonMain` 变化 | `checkSharedPurity`、`commonTest`、metadata + desktop 编译 | ✅ `checkSharedPurity` 已实现并接入 `verify.yml`（2026-09-05）；当前全仓 0 违规，day 1 起 blocking |
| **G3** 契约/适配 | 平台契约变更 | 各 target 的 contract test、线程/取消/错误/事务语义 | 计划（P1） |
| **G4** CMP Feature | shared UI | Android 视觉与行为基线、目标平台主路径 smoke、insets/返回手势 | 计划（P4） |
| **G5** 高风险能力 | 阅读器 / 书源规则 / 服务 | 真机 parity、性能基线、脚本兼容测试 | 强制人工审批 |

新门禁一律 **report → 冻结基线 → blocking**；baseline 下降时必须同步下调，禁止为让迁移通过而抬高基线。

**平台状态必须分级记录**：compile / contract-test / smoke / package / release-ready 五个等级分开写，
不允许用一个「支持」覆盖全部。

---

## 8. 风险登记册

| # | 风险 | 等级 | 影响 | 对策 |
|---|---|---|---|---|
| R1 | **jsoup 1.16.2 被锁定**，而 common 侧需要 HTML 解析 | 中（已降级） | 若强行换 ksoup，存量书源与 JsoupXpath 行为可能漂移 | ✅ **双轨已验证可行（D2 Go，2026-09-06）**：JVM/Android actual 继续用 jsoup 1.16.2；common 侧只依赖 `HtmlParser` 窄接口。**残留风险集中在 JS 边界**：`help/JsExtensions` 把 `org.jsoup.Connection.Response` 原样返回给书源 JS，该处 jsoup 类型必须保留（平台岛）。任何人不得因"统一走窄接口"而改动它，否则存量书源静默全挂 |
| ~~R2~~ | ~~**Room 2.8.4 的 KMP 产物完整性未验证**~~ | ~~高~~ | ✅ **已解除（D1 Go）**：PoC 证明 2.8.4 KMP 可用，不升 room3。P3 剩余风险是 389 blocking DAO + 2 SupportSQLite 文件的工作量，不是产物可用性问题 |
| R3 | **122 个文件用 `java.io.File`** | 高 | 最大的 JVM 泄漏面，且分散在 `help`/`utils`/`data` | P1 先立 `FileSystem` 契约，P2 分批改调用方；不搞一次性大替换 |
| R4 | **Rhino 无 K/N 实现** | 中 | 决定 iOS/native 目标能否承载书源规则 | 先做 `RuleEngine` capability；换 QuickJS 单独立项（样本有完整可抄的实现路径，需要时可取） |
| R5 | **Glide 与 coil3 并存**（21 vs 39 文件） | 中 | 双图片栈会让 `BookImageLoader` 契约无法收敛 | P1 顺带清理 Glide |
| R6 | **474 个 Compose 文件**的迁移面 | 中 | CMP 化工作量远大于逻辑层 | P4 只切首个 Feature；按 Feature 垂直迁移，不做横向大搬家 |
| R7 | **依赖注册/注入顺序**（样本的真实 bug 源） | 中 | fail-silent 的空值缺陷，难复现 | 保留 Koin 显式绑定（`AGENTS.md` 已要求 Gateway→Repository 显式绑定）；启动阶段加自检断言 |
| R8 | **Gradle 跨盘 transforms 缓存** | 中 | Windows 上 `:app` 的 transform 任务反复失败 | 所有命令统一 `-Dgradle.user.home=D:/Android/.gradle`（见 D6） |
| R9 | **测试密度不足**（172 文件 / 1555 源文件） | 中 | 下沉时缺 characterization test 兜底 | 每组下沉前先补 characterization test；样本 193 组 `expect/actual` 无契约测试的教训不重演 |

---

## 9. 首批 Backlog（可立即开工）

1. ~~**P0-1** 实现 `checkSharedPurity` 任务~~ ✅ **2026-09-05 完成**：直接 blocking（全仓 0 违规，无需 report 阶段），已接入 `verify.yml`。
2. ~~**P0-2** 实现 `checkModuleDependencies` 任务~~ ✅ **2026-09-05 完成**：4 条规则（core→feature/host、feature-api→feature、feature-impl→feature-impl），day 1 起 blocking，已接入 `verify.yml`。
3. ~~**P0-3** 回填 `kmp-cmp-modernization.md` 盘点~~ ✅ **2026-09-05 完成**：§2 盘点与 §6 门禁表已更新为实测数字与 G1/G2 落地状态。
4. ~~**D1-PoC** 建 `:smoke:room-kmp-probe`~~ ✅ **2026-09-05 完成 = Go**：Room 2.8.4 KMP 在 commonMain entity/DAO/`@Database(@ConstructedBy)` + `expect object` 编译、KSP2 双 target 生成 actual、`BundledSQLiteDriver` 在 desktop JVM 跑真实查询（3 项测试）。留在 2.8.4 不升 room3。**附：`checkSharedPurity` 白名单加 `androidx.room`/`androidx.sqlite`**（KMP 兼容库，有 commonMain 元数据）。
5. ~~**P1-1** 建 `:core:platform`，先落 `Clock` + `DispatcherSet`~~ ✅ **2026-09-05 完成**：Clock 用 expect/actual、DispatcherSet 用接口+DI，4 项契约测试双 target 通过；`WholeBookPageCoordinator` 接为真实消费方（消除 4 处 JVM 泄漏）。
6. **P1-2** 建 `:core:model`，从 `constant/`（14）与 `domain/model/`（58）中挑选零 Android 依赖的值对象下沉。
7. ~~**D2** 立 HTML 解析双轨~~ ✅ **2026-09-06 完成 = Go**：`HtmlParser`/`HtmlDocument`/`HtmlElement` 进
   `:core:platform` commonMain，`JsoupHtmlParser` 双 target 委托 jsoup 1.16.2，8 项契约测试双 target 通过，
   首个真实消费方 `HtmlFormatter` 迁移后既有 11 项测试行为零变化。`checkSharedPurity` 补齐
   `org.jsoup.*`/`org.seimicrawler.*`/`com.jayway.*`/`org.mozilla.javascript.*`/`okhttp3.*` 黑名单。
   **书源 JS 边界的 `org.jsoup.Connection.Response` 判定为平台岛，不迁移。**
8. **D2 第二刀**：扩 `HtmlElement` 到能承载 `JsoupExtensions.textArray` 的宽遍历面
   （`NodeTraversor`/`NodeVisitor`/`org.jsoup.internal.StringUtil`/`CDataNode`/`tag().preserveWhitespace()`），
   或判定 `textArray` 属平台岛。做之前先补 `textArray` 的 characterization test。

**统一验证命令**（注意 D6 的 Gradle 参数）：

```powershell
# G0 Android 基线 + 现有 KMP 模块
.\gradlew.bat testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-daemon --no-configuration-cache -Dgradle.user.home=D:/Android/.gradle

# KMP target 编译与测试（新增模块按此模式加入 verify.yml）
.\gradlew.bat :feature:reader:core:compileCommonMainKotlinMetadata :feature:reader:core:compileKotlinDesktop :feature:reader:core:desktopTest --no-daemon -Dgradle.user.home=D:/Android/.gradle

# 文本改动
git diff --check
```

新增模块时，把**真实存在的** task 加入 `verify.yml`，不要写尚未定义的任务名。

---

## 10. 关键证据文件（供复查）

**参照样本**：

- `D:\Project\shutiao\legado\settings.gradle.kts`、`shared\build.gradle.kts`（第 550–751 行源集层级）
- `...\shared\src\commonMain\kotlin\io\legado\app\ui\root\PlatformServices.kt`、`PlatformCapabilities.kt`
- `...\shared\src\commonMain\kotlin\io\legado\app\data\AppDatabase.kt`
- `...\shared\src\commonMain\kotlin\io\legado\app\help\http\KmpHttpTypes.kt`
- `...\shared\src\commonMain\kotlin\org\jsoup\Jsoup.kt`（jsoup 兼容层约束）
- `...\shared\src\commonMain\kotlin\io\legado\app\model\script\JsEngine.kt`（QuickJS/Rhino 取舍）
- `...\shared\src\iosMain\kotlin\io\legado\app\help\config\IosProviderRegistry.kt`（注册顺序约束原文）
- `...\app\src\main\java\io\legado\app\App.kt`、`...\desktop\src\main\kotlin\io\legado\desktop\Main.kt`（注册中枢）
- `...\ohosApp\INTEROP.md`（四层桥接架构；仅作视野参考，不采纳鸿蒙目标）

**本仓库**：

- `AGENTS.md`（仓库级不变量，含 jsoup / Hutool / namespace 约束）
- `.agents/skills/legado-kmp-migration/SKILL.md`（切片工作流与不可协商边界）
- `docs/dev/kmp-cmp-modernization.md`（方向图与门禁定义，数字待更新）
- `docs/dev/track-f-reader-kmp-migration-plan.md`（P5 深化）
- `docs/dev/feature-first-structure.md`、`feature-catalog.md`（Feature 晋级路径）
- `.github/workflows/verify.yml`（当前 CI 门禁）

---

## 11. 参考项目（`D:\Project\shutiao\legado`）怎么做这些事

本仓库前六片反复遇到的「怎么把 app 耦合的类搬进共享层」，参考项目给出的答案与我们有系统性差异。
下表是实测对照（源码位置随行给出），**结论不是「谁对」**，而是「两种策略各自的代价」。

| 我们遇到的阻碍 | 参考项目的做法 | 出处 |
|---|---|---|
| `utils.GSON` / `fromJsonArray` / `fromJsonObject` | **把 `GSON` 搬进 commonMain，底层换成 kotlinx.serialization**（`val GSON: Json get() = KS_JSON`），名字与扩展函数签名保持原样 → 调用方零改动 | `shared/src/commonMain/.../utils/GsonExtensions.kt:29` |
| `okHttpClient` / `AppConst` | `interface OkHttpClientProvider` + `object OkHttpClientProviders`（`register` / `get()`，未注册直接 `error(...)`）；okhttp 在 shared 里是 **api** 依赖 | `shared/src/commonMain/.../help/http/OkHttpClientProvider.kt`；`shared/build.gradle.kts` |
| Room DAO（`appDb.xxxDao`） | `interface AppDbAccessor` + `object AppDbProviders`，commonMain 里写 `AppDbProviders.get().txtTocRuleDao` | `shared/src/commonMain/.../data/AppDbAccessor.kt:104` |
| `BaseViewModel(application)` | **明确不继承**：「不采用 `expect abstract class` 让 app 端子类继承：BaseViewModel 是 AndroidViewModel，commonMain 不可用，Kotlin 单继承会冲突」→ 改**组合委托**：app 端 VM 持有 `XxxViewModelShared(scope = viewModelScope, ...)` | `shared/src/commonMain/.../book/source/manage/BookSourceViewModelShared.kt:41-60` |
| `BaseRuleViewModel` 那种「列表状态 + 导入导出」基类 | **根本不存在**。每个列表页有自己的 `XxxScreenModel`（`sharedUiMain`）持有 `XxxUiState` + `dispatch(event)`；DAO 写入用一个小 `XxxViewModelShared` | `shared/src/sharedUiMain/.../toc/rule/TxtTocRuleScreenModel.kt`（169 行）+ `.../TxtTocRuleViewModelShared.kt`（115 行） |
| 剪贴板 | `PlatformCapabilities.copyToClipboard(text)` / `getClipboardText()`（一个 100+ 成员的宽接口，**每个成员都有默认实现** `unsupported(...)` / `null`），或按屏传 lambda `clipTextProvider = { getClipText() }` | `shared/src/commonMain/.../ui/root/PlatformCapabilities.kt:113-115`；`app/.../HttpTtsEditViewModel.kt:20-27` |
| Toast | `interface Toaster` + `object Toasters` 注册表，各端 `Toaster.android.kt` / `Toaster.ios.kt` | `shared/src/commonMain/.../help/toast/Toaster.kt:39` |
| `Uri` / `ContentResolver` / `Bundle` | 留在 app 端；commonMain 先判 `isUri` 再把文本转发过去（lambda） | `ImportTxtTocRuleViewModelShared.kt:35-37` |
| 直链上传 | `DirectLinkUploadShared` 进 commonMain + `DirectLinkUploadStoreProvider` / `DefaultsProvider` 接口，app 的 `object DirectLinkUpload` 实现，`App.onCreate` 里 `registerAndroidDirectLinkUploadProviders()` | `app/src/main/java/io/legado/app/help/DirectLinkUpload.kt:33-45` |
| 协程调度 | 共享层用顶层常量 `IoDispatcher`，不是注入 | `shared/src/commonMain/.../help/coroutine/` |
| `R.string` | commonMain 一律不用，改抛领域异常 / 固定文案（`NoStackTraceException("格式不对")`） | `ImportTxtTocRuleViewModelShared.kt:36-37` |
| 调试开关（`BuildConfig.DEBUG`） | 未见于共享层（他们的共享层不含这类 app 常量） | — |

**两种策略的取舍**：

- 参考项目**把实现搬进共享层**（kotlinx-serialization 换 Gson、okhttp 以 `api` 暴露、注册表拿 DAO），
  换来的是「shared 里可以直接写业务」，代价是共享层 API 形状被 okhttp/Gson 语义绑住，
  且 `PlatformCapabilities` 长成 100+ 成员的宽接口。
- 本仓库**保留实现留在平台侧，只抽窄契约**（`JsonCodec` expect/actual、`HttpClient`/`FileSystem`
  契约、Koin 显式绑定），代价是每搬一个类都要先剥一层依赖（本片就是为此而做），
  好处是共享层不泄漏 `okhttp3.*` / `Gson` 类型，`checkSharedPurity` 可机械守住。

**本仓库已采纳参考项目的一点**：`UploadRepository` 这类「接口下沉、实现留 app、宿主启动注册/绑定」
正是参考项目 `*Providers` 模式在 Koin 下的等价写法；`RuleTransferPlatform` 的默认/未实现语义
也按参考项目「显式建模不支持，而不是静默空实现」的纪律写。
