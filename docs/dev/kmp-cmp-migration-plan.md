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
- **389 个 blocking DAO 函数 → suspend**：Room 2.8.4 KMP 在非 Android target 上不支持 blocking 函数。
  已有 180 suspend + 169 Flow（兼容），需转 389 blocking。这是工作量最大的一步。
- **2 个 SupportSQLite 文件 → driver API**：`AppDatabase.kt` + `DatabaseMigrations.kt`（60 条 migration）。
  用 `room-sqlite-wrapper`（2.8.0+ 兼容制品）渐进迁移，或直接改 `SQLiteConnection`。
- **`@Database` 加 `@ConstructedBy` + `expect object`**：当前用 `Room.databaseBuilder(context, ...)`，
  需改为 KMP 构造模式。

- Room entities（66）/ DAO（39）下沉到 `:core:data` 的 `commonMain`。
- 高频实体（如 `BookChapter`、`Cache`、`Cookie`）切到独立源集 `roomEntitiesMain`，**照抄样本的 KSP 重编面优化**。
- Android 端注入 `AndroidSQLiteDriver`，Desktop 注入 `BundledSQLiteDriver`。
- 必须有：schema 迁移兼容测试、导入导出往返测试、并发/事务语义测试。

**退出条件**：两端 driver 的 contract test 通过；现有 Android 数据库迁移与备份恢复测试无回归。

### P4 —— CMP 设计系统与首个 Feature（4–6 周）

- `:core:designsystem`：颜色/尺寸/排版 token + 无平台依赖的叶子组件。
- 首个 Feature 选**低风险、少系统依赖、非阅读主链**的页面（如「关于」「书源调试」「替换规则」一档，
  避开读正文、WebView 26 文件、Cronet、前台服务）。
- 只共享 `UiState` / `Intent` / `Screen` / `Content`；**导航 runtime 与 Effect 留在宿主**。
- 桌面宿主 `desktop/` 起最小可运行壳。

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
