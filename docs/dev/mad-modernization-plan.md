# Reader 核心现代化改造计划（MAD 收敛）

> 本文档是一份**执行计划**，不是一次架构评分。它把此前审阅收敛出的结论，转化为可分批落地、可验证、可回滚的工程步骤。
>
> 范围：阅读器核心（`ReadBook` 全局单例、`ReadBookViewModel`、`ReadBook.CallBack` 渲染协议）以及全应用范围内的 UI→DAO 直连问题。**不包含**外围已完成 MAD 化的 Compose 屏幕。

## 进度快照（2026-07-21 · 为新窗口交接）

> 本节是给新开窗口的接续点，描述**当前 HEAD 的真实状态**。F1/F2 已推进过半，Track A/B/C 尚未开始。下方数字均已核实。

### 已完成

- **F2 冻结机制**：双基线 + 双向棘轮（新增报红、减少了不下调也报红）+ 陈旧条目检测，已 `dependsOn` assemble/compile 在 CI 生效（`build.gradle.kts`）。
  - `legacyDaoInjectionBaseline` —— ViewModel 主线
  - `legacyUiDaoAccessBaseline` —— 非 ViewModel 的 UI 层文件（第二条线）
- **F1 部分**：`app/lint-baseline.xml` 已建并接入 `lint {}`；`verifyConfigArchitecture` 已在 CI 传递触发；单测 **272 全绿**。
- **F2 清零（已提交 13 批）**：关联导入 → RSS → 书籍导入 → 书源管理 → 换源+目录（`77d38b8`…`04161fa`）；随后清完 8 个可清 VM（Web/BookInfoEdit/ChangeCover/TagGroupRule/SourceLogin/AudioPlay/ReadManga/BookInfo）+ `ReadBookViewModel` 非阅读态子集。**主线基线从 ~247 → 130 → 36**（仅剩 `ReadBookViewModel` 的 book/chapter 阅读态访问，留给 Track A）。
- **数据层事务加固**：目录替换（`replaceChaptersAndUpdateBook`）、换序 move（`BookSourceDao.moveToTop/Bottom`）已改原子事务。

### 待续（新窗口从这里接）

**① F2 主线还剩 36，全部在 `ReadBookViewModel`**（`build.gradle.kts` 的 `legacyDaoInjectionBaseline`）：

| VM | 直连数 | 备注 |
|---|---:|---|
| `ReadBookViewModel` | 36 | 剩 24× `bookChapterDao` + 12× `bookDao`，**均为阅读态，不要手工硬清**，随 Track A 溶解 |

> `ReadBookViewModel` 的 book/chapter 访问会被 Track A（`ReaderSession` 接管阅读状态）**溶解**，因此已在本轮清掉与阅读状态无关的部分（httpTTS/bookSource/bookmark 共 16 处，走 `HttpTtsRepository`/`BookSourceRepository`/`BookmarkRepository`），其余 36 处留给 Track A，别在 6400+ 行里硬拆。
>
> 清 8 个可清 VM 时给若干仓库补了无聊方法：`BookRepository`（`getAll`/`getLastReadBook`/`getChapter`/`delete`）、`BookSourceRepository`（`has`/`getAllTextEnabledPart`）、`SearchRepository`（`getEnableHasCover`/`getSearchBook`）、`BookGroupRepository`（`getIdsSum`/`getGroupNames`）、`HighlightTagRuleRepository`（`getEnabled`，并在 Koin 注册）、`HttpTtsRepository`（`getAll`/`getAllSync`/`getNameSync`/`delete`）。宿主 `by viewModels()` → Koin `by viewModel()` 已同步改（Web/BookInfoEdit/ChangeCover/SourceLogin/AudioPlay）。

**② F2 第二条线（非 VM UI 层）**：`legacyUiDaoAccessBaseline` 已冻结 **26 文件 / 62 处**，**只冻不修**。等主线清完、仓库齐备、部分 Activity 随 Compose 迁移消失后再回头清（大头 `BookSourceActivity=10`、`RssJsExtensions=8`、`KeyboardAssistsConfig=7`）。

**③ F1 收尾**：仍缺一个**显式跑 `testAppDebugUnitTest` + `lintAppDebug` 的 CI job**——这是 F1 唯一没关上的门（当前单测失败/新增 lint 违规不会让 CI 红）。

**④ Track A / B / C**：均未开始。

### 未提交

数据层事务加固（3 个文件：`BookSourceDao.kt`、`BookRepository.kt`、`BookSourceRepository.kt`）尚在工作区，建议单独一提：`refactor: 目录替换与换源移序改为原子事务`。

## 0. 框架与判定口径

用两个维度描述现状，而不是一个小数分：

- **严格 Google MAD 符合度：约 5.5–6.0 / 10** —— UI→DAO 系统性直连、阅读器核心由全局可变单例驱动、ViewModel 持有 `Application`、`ViewModel→UI` 存在指令式 Effect 通道、CI 无测试/lint 质量门禁。
- **遗留现代化工程质量：约 7.5 / 10** —— 未做高风险全量重写、新旧架构渐进隔离、已有架构护栏（`verifyConfigArchitecture`）、领域抽象（Gateway/UseCase/Repository）真实存在、迁移方向正确。

这两个分数并不矛盾：一次高质量的遗留迁移，可以同时只是中等程度符合目标架构。**本计划的目标是把第一个分数往上推，而不破坏第二个分数所代表的迁移纪律。**

## 1. 已核实的现状（事实基线）

| 事实 | 证据 |
|---|---|
| `ReadBook` 是进程级全局单例 | `object ReadBook : CoroutineScope by MainScope(), KoinComponent`（`model/ReadBook.kt:73`） |
| 阅读器真实所有权在单例，VM 是回调适配器 | `ReadBookViewModel(...) : BaseViewModel(application), ReadBook.CallBack`（`ui/book/read/ReadBookViewModel.kt:203`） |
| `ReadBookViewModel` 体量失控 | 6484 行 |
| `ReadBook` 被外部广泛引用 | 44 个非 VM 文件引用；写入约 7 处；引用逃逸式写入近 0；其余为读取/身份比较 |
| `Book` 是可变实体 | `data class Book(... var durChapterIndex ... var durChapterPos ...)`（`data/entities/Book.kt:41`） |
| `CallBack` 是混合协议（渲染 + 业务状态） | 11 个方法，`interface CallBack : LayoutProgressListener`（`model/ReadBook.kt:1330`） |
| `upContentAwait` 是 `suspend`，模型反向等待视图 | `model/ReadBook.kt`（CallBack 定义内） |
| VM 是 `CallBack` 唯一实现者 | 全仓仅 `ReadBookViewModel` 声明 `: ReadBook.CallBack` |
| Effect 通道带订阅握手 | `_effects.subscriptionCount.first { it > 0 }`（`ReadBookViewModel.kt:210-223`） |
| UI→DAO 直连是系统性模式 | 计划撰写时约 33 个 VM 构造注入 DAO；**现已冻结并清零 8 个可清 VM + ReadBook 非阅读态子集，主线余 36**（全在 ReadBookViewModel，均阅读态，留给 Track A；见进度快照） |
| CI 门禁 | lint 基线与架构护栏已就位；**仍缺显式 CI test/lint job**（见进度快照 ③） |
| 已有架构护栏 + 基线机制 | `VerifyConfigArchitectureTask` + 三条基线（`legacyPreferenceCallBaseline` / `legacyDaoInjectionBaseline` / `legacyUiDaoAccessBaseline`），已 `dependsOn` assemble/compile |

## 2. 反目标（明确不做）

- **不做全量重写**：`ReadBook` 6000 行不横向切成几十个文件——那只是"分文件的 God Object"。
- **不做伪状态**：不把 `upPageAnim`/`cancelSelect` 之类指令式渲染命令塞进 `UiState`（`shouldAnimatePage: Boolean`）。那是把门铃伪装成恒温器。
- **不做架构宇航员**：DAO 边界仓库保持"刻意无聊"，禁止为凑 Clean Architecture 目录树而造假 UseCase、无谓 mapping、单方法包装类。
- **不用 Compose 迁移当作解耦前置**：所有权重构和渲染边界下沉都不依赖 Compose。
- **不优先造扫描器**：写入面是个位数，用编译器（`private set`）约束，而不是先写 Detekt/lint 规则。
- **不做万能 Repository**：DAO 去注入按功能逐批，不预先设计几十个大仓库。

## 3. 三轨模型 + 两条横切地基

改造被拆成三条**相互解耦、可并行**的轨道，外加两条横切地基。关键洞察：三条轨道的进度互不阻塞，**除了 Track C 依赖 Track A/B 的产出**。

```
横切地基：
  F1  CI 质量门禁（测试/lint 基线/架构检查）
  F2  UI→DAO 去注入（全应用，非阅读器专属）

Track A  所有权与业务状态   —— 现在就能做，最高优先级
Track B  遗留渲染边界下沉   —— 现在就能做，与 A 并行
Track C  声明式渲染 / Compose —— 长期，依赖 A、B 的产出
```

## 4. 分阶段执行

### F1 —— 建立可信的 CI 底线（最先做，成本最低）

没有它，后续拆 DAO、拆单例、拆 VM 都没有安全网。

> **当前状态**：单测 272 全绿；`app/lint-baseline.xml` 已建并接入 `lint {}`；`verifyConfigArchitecture` 已在 CI 传递生效。**唯一未做 = 下面的第 3 步（显式 CI test/lint job）**。

**任务**
1. ~~反复运行现有单元测试，识别确定性失败与 flaky 套件~~ ✅ 当前 272 tests / 0 failures。
2. ~~生成 lint 基线并提交 `app/lint-baseline.xml`~~ ✅ 已建并接入。
3. **（待做）** CI 增加一个 `verify` job（与现有 `assembleAppDebug` 并列或前置）：
   ```bash
   ./gradlew testAppDebugUnitTest
   ./gradlew lintAppDebug          # 仅新增违规会 fail（基线已冻结历史债）
   ./gradlew verifyConfigArchitecture
   ./gradlew assembleAppDebug
   ```
   > 注：`verifyConfigArchitecture` 已通过 `dependsOn` 在 assemble/compile 前运行，此处显式列出是为了让门禁意图清晰、失败信息更早。
4. 无论成败都上传 test/lint 报告。

**基线纪律（防止"CI 变绿垃圾压缩机"）**
- PR 可以**删除**基线条目（修复债务）。
- PR **不得整体重生成**基线。
- 基线增长必须显式 review。

**验收**：任一新 PR 若新增 lint 违规、破坏单测或触碰架构规则，CI 红。历史债务不阻塞。

---

### F2 —— 冻结并逐步清除 UI→DAO 直连（全应用）

系统性问题。按功能逐批，不预造大仓库。

**冻结已落地** ✅：`VerifyConfigArchitectureTask` 里实际统计 `daoImport + appDbDaoAccess` 两种形式，双向棘轮 + 陈旧检测，覆盖两条线：
- `legacyDaoInjectionBaseline` —— `ui/**` 里**含 `ViewModel`** 的文件（主线，当前 **36**，全在 `ReadBookViewModel`）
- `legacyUiDaoAccessBaseline` —— `ui/**` 里**非 `ViewModel`** 的文件（第二线，当前 **26 文件 / 62 处**，只冻不修）

> 第二条线是后加的：清 VM 时发现护栏只看 `*ViewModel*`，而 `BookSourceActivity` 这类兄弟文件里还有直连 DAO。故把范围扩到整个 `ui/**`、给非 VM 文件建了第二基线，防止"清了半个 UI 层还以为清干净了"。

**逐批清零**：每批一个功能，DAO → 薄 Repository → VM。仓库刻意无聊：
```kotlin
class ReplaceRuleRepository(private val dao: ReplaceRuleDao) {
    fun observeAll() = dao.flowAll()
    suspend fun save(rule: ReplaceRule) = dao.insert(rule)
    suspend fun delete(rule: ReplaceRule) = dao.delete(rule)
}
```
它的价值是**边界**，不是业务：VM 不再认识 Room、测试可替身、实体不再向上泄漏、依赖方向可强制。清一批，就从基线里删一批条目。

**逐批操作手册（已跑通 5 批，新窗口照此做）**：
1. VM 里的 `appDb.xxxDao` / `import data.dao.*` → 注入 `XxxRepository`（优先复用已有：`BookRepository` / `BookSourceRepository` / `SearchRepository` / `BookmarkRepository` 等）。
2. 仓库方法保持无聊：`suspend fun … = withContext(Dispatchers.IO) { dao.… }`；VM 原本非 suspend 暴露的同步读就**保持同步**，别硬加 `withContext` 改签名。
3. VM 有了非空构造参数后，宿主 Activity/Dialog 的 `by viewModels()` / `by activityViewModels()` **必须**改成 Koin `by viewModel()` / `by activityViewModel()`——否则运行时崩，**compile 不报**。
4. 仓库若 `singleOf(::Xxx)` 注册，新增构造参数自动解析；否则要在 `appModule` 补 `get()`。
5. 从 `legacyDaoInjectionBaseline` 删该 VM 条目（清到 0 删行；部分清就下调数字——护栏强制"减少了必须下调"）。
6. 验证三连：`verifyConfigArchitecture` + `:app:compileAppDebugKotlin` + `testAppDebugUnitTest`，全绿。

**已踩过的坑**：
- **忠实第一，零行为漂移**：这是护栏驱动的机械清零。别顺手把 `isEmpty()` 改 `isBlank()`、别改条件、别重排逻辑（换源那批出现过一次，已改回）。
- **compound 操作要原子**：多 DAO 的"删+插+改""读序号+改"要包 `runInTransaction`（Kotlin 直调），或做成 DAO 的 `@Transaction` 方法。**验证事务真生成**：查 `app/build/generated/ksp/appDebug/java/.../Xxx_Impl.java` 里是否 `performBlocking(…, inTransaction=true, …)`。
- **别把 DAO 挪进非 VM 文件规避护栏**：它们现在被第二基线盯着。
- **compile/test 证明不了 DI 与事务**：新增构造参数能否被 Koin 解析、`@Transaction` 是否真生成事务，要分别查 `appModule` 注册方式和生成的 `*_Impl.java`。

**验收**：两条基线均单调递减；`ui/**` 任意文件（VM 或非 VM）新增 DAO 直连，CI 红。

---

### Track A —— 所有权与业务状态（现在做，最高优先级）

目标：**阅读状态有唯一写入入口，Snapshot 可信**。不受 View/Compose 进度影响。

**A1. 编译器冻结写入**。把 `ReadBook` 的会话字段改为 `private set`：
```kotlin
var book: Book? = null
    private set
var durChapterIndex = 0
    private set
var durChapterPos = 0
    private set
```
> 用 `private set` 而非 `internal set`——单体 `:app` 内 `internal` 几乎不设防。

**A2. 为约 7 个外部写入点提供语义化命令**：
```kotlin
fun replaceCurrentBook(book: Book)
fun moveToChapter(index: Int, position: Int)
fun updateReadingPosition(position: Int)
fun clearCurrentSession()
```
把外部散落的字段赋值改为调用这些命令。编译器强制，重构工具可跟踪，无法用换行/别名绕过。

**A3. 停止向外递出可变实体**。绝大多数外部读取只是身份比较，用意图化 API 替代裸实体：
```kotlin
fun isCurrentBook(bookUrl: String): Boolean = book?.bookUrl == bookUrl
```
这同时堵住"引用逃逸写入"（`ReadBook.book?.durChapterIndex = x`）未来复发的可能——当前近 0，但接口不再暴露就永久免疫。

**A4. 权威不可变快照**。仅在写入受控后引入：
```kotlin
data class LegacyReaderSnapshot(
    val bookId: String?,
    val chapterIndex: Int,
    val chapterPosition: Int,
    val chapterCount: Int,
    val isLoading: Boolean,
    val isReadingAloud: Boolean,
    // 注意：不直接暴露可变 Book / TextChapter / List / Map
)

private val _snapshot = MutableStateFlow(buildSnapshot())
val snapshot: StateFlow<LegacyReaderSnapshot> = _snapshot.asStateFlow()

private fun publishSnapshot() { _snapshot.value = buildSnapshot() }
```
每个受控 mutator 在**一次原子状态转移完成后**（而非每次单字段赋值后）调用 `publishSnapshot()`：
```kotlin
fun replaceCurrentBook(book: Book) {
    this.book = book
    durChapterIndex = book.durChapterIndex
    durChapterPos   = book.durChapterPos
    clearTextChapter()
    publishSnapshot()   // 只发一次，不暴露中间态
}
```

> **性能红线**：快照区分高频与低频。`chapterPosition`/viewport 随滚动高频变化，`bookId`/`chapterIndex`/结构信息低频变化。**不要**在每个滚动 tick 深拷贝 `TextChapter` 进快照。高频 viewport 状态与低频结构状态应分开，避免把 60fps 热路径挂到 equality-based 快照上。

**A5. `ReaderSession` 面向所有者的 API**（写入全部受控后引入）：
```kotlin
interface ReaderSession {
    val state: StateFlow<ReaderSessionState>
    suspend fun open(bookId: String)
    suspend fun moveToChapter(index: Int, position: Int = 0)
    suspend fun nextChapter()
    suspend fun previousChapter()
    suspend fun updateViewport(position: Int)
}
```
首个实现仍可 `LegacyReaderSession → ReadBook`，但必须保证：所有 mutation 经过它；state 投影真实遗留状态、不维护竞争副本；调用方拿不到可变领域对象。

**验收**：`ReadBook` 会话字段全部 `private set`；外部写入点为 0（全部走命令）；`snapshot`/`state` 在任何路径的状态变更后都能正确发射；VM 可从 `ReaderSession.state` 取业务状态。

---

### Track B —— 遗留渲染边界下沉（与 A 并行，**不等 Compose**）

关键结论：把指令式渲染协议**从 ViewModel 移到 UI 层的渲染控制器**，不需要 Compose。Compose 只用于让渲染**声明式**（Track C）。

**B1. 先拆分 `CallBack` 接口，而不是整体搬迁**。`CallBack` 是两套协议穿了一件外套：

| 子集 | 方法 | 归属 |
|---|---|---|
| 指令式渲染 | `upContent`、`upContentAwait`、`pageChanged`、`contentLoadFinish`、`upPageAnim`、`cancelSelect`、`LayoutProgressListener.*` | Track B 渲染控制器 |
| 业务/UI 状态 | `upMenuView`、`loadChapterList(book)`、`notifyBookChanged`、`sureNewProgress(BookProgress)` | Track A 会话 / VM `UiState` |

> **陷阱**：若让渲染控制器实现整个 `CallBack`，就把 `sureNewProgress`/`notifyBookChanged` 这些真业务事件塞进了渲染层——正是当前"业务状态骑在渲染总线上"的镜像翻版。**Track B 第一步是把接口切成 `ReaderRenderCallback`（渲染子集）与状态子集**，后者进会话/VM。

**B2. 渲染子集下沉到 UI 层控制器**：
```
ReadBook ──legacy render callback──▶ LegacyReaderRenderController
                                         ├─ ContentTextView.invalidate()
                                         ├─ PageProvider.update()
                                         ├─ startPageAnimation()
                                         └─ clearSelection()
```
`LegacyReaderRenderController` 是普通 UI 层对象，生命周期绑定 Activity/View。它**合法地**调用指令式 View API——因为它就是 UI 实现层，知道 View 是否 attached/laid out。

**成果**：删掉一整层无价值中转——
```
旧：ReadBook → VM → SharedFlow Effect → View → ContentTextView
新：ReadBook → UI 渲染适配器 → ContentTextView
```
`subscriptionCount` 握手随之从 VM 消失；渲染时序由真正拥有 View 生命周期的对象管理；VM 不再是渲染总线。

**B3. `upContentAwait` 是 B/C 边界的绊线**。它是 `suspend`——`ReadBook` 挂起自己的协程等待视图完成排版，是一次 model→UI→model 往返。Track B 能把这个 await **搬到**渲染控制器后面，但**删不掉**它。当你试图消除这个 `suspend` await（而不是搬迁它）时，就已跨出"重新归位协议"、进入"重写渲染器"（Track C）。

**Track B 中期目标不是"完全声明式"，而是**：
> 指令式渲染只能存在于 UI 层，不能穿过 ViewModel 或业务层。

**验收**：VM 不再实现 `ReadBook.CallBack`；`_effects` 中的渲染类 Effect（`ToggleReadAloud`/`ToggleAutoPage` 等指令）全部由渲染控制器承接；`subscriptionCount.first { it > 0 }` 握手删除。剩余 `_effects` 仅保留真正的一次性 UI 消息/导航（严格 MAD 下仍扣分，但已是可辩护的最小面）。

---

### Track C —— 声明式渲染 / Compose 迁移（长期，依赖 A、B）

```
Legacy Canvas View
 → 抽取稳定 RenderModel（viewport 尺寸、分页结果流、动画进度、手势、朗读高亮、预加载反馈）
 → Compose Canvas 或新渲染组件
 → 用状态 + 本地动画状态替代大部分渲染命令（含反转 upContentAwait 的往返）
 → 删除 LegacyReaderRenderController
```

> Compose 不会自动蒸发分页排版复杂度，只是让"状态↔绘制"的关系更好表达。它是最自然、但非唯一的声明式实现。

---

### ViewModel 拆分（不整体等待 Session）

拆分按依赖归类，**与渲染无关的部分现在就能并行拆**：

**可立即抽离**（进 UseCase / Coordinator / Repository / 普通 scoped controller）：
AI 清理与改写、翻译、TTS 引擎查询与配置、换源、上传/同步进度、阅读设置、备份与缓存策略。

**依赖 Track A（ReaderSession）后再拆**：
当前书籍、当前章节与位置、前后章缓存、进度保存、章节切换、会话生命周期。

**依赖 Track B（渲染边界）后再拆**：
`upContentAwait`、页面排版完成、页面动画、选择取消、局部重绘、page recorder 回收——进渲染控制器/分页引擎，**不进** ReaderSession 或 VM。

> 抽出的类多数应是普通 Kotlin state holder / scoped service，不必都是 Android `ViewModel`。

**顺序纪律**：先移除 `CallBack` 归属，再激进拆 VM。否则抽出的 `ReaderXxxController` 全都还是围绕同一个全局单例的回调适配器——那是"分布式单体"，文件更多，所有权问题照旧。

## 5. 目标架构

```
        ┌──────────────────────────┐
        │      ReaderSession       │  权威业务状态 + 业务命令（Track A）
        └────────────┬─────────────┘
                     │ StateFlow
        ┌────────────▼─────────────┐
        │    ReadBookViewModel     │  屏幕业务状态、用户动作协调（瘦身后）
        └────────────┬─────────────┘
                     │ UiState
        ┌────────────▼─────────────┐
        │  Activity / Fragment UI  │
        └────────────┬─────────────┘
                     │
  ┌──────────────────▼──────────────────┐
  │     LegacyReaderRenderController     │  View 生命周期、分页、重绘、动画、选择（Track B）
  └──────────────────┬──────────────────┘
                     │ imperative calls
  ┌──────────────────▼──────────────────┐
  │ ContentTextView / PageProvider …    │  → 最终由 Track C 替换为声明式渲染
  └─────────────────────────────────────┘
```
迁移期，旧 `ReadBook.CallBack`（渲染子集）由 `LegacyReaderRenderController` 实现；最终从 `ReadBook` 抽走渲染职责。

## 6. 强制机制（编译器 > 扫描器）

| 约束 | 机制 | 强度 |
|---|---|---|
| `ReadBook` 会话字段不可外部写 | Kotlin `private set` | 编译期，不可绕过 |
| ViewModel 不新增 DAO 直连 | `VerifyConfigArchitectureTask` + `legacyDaoInjectionBaseline` | CI，双向棘轮 ✅ |
| 非 VM UI 层不新增 DAO 直连 | 同上 + `legacyUiDaoAccessBaseline` | CI，双向棘轮 ✅ |
| 不新增旧偏好直读 | 现有 `legacyPreferenceCallBaseline` | CI（已存在） |
| `ReadBook` 读取收敛 | lint/Detekt（读取难以编码约束，且低风险，用扫描器足够） | CI 提示 |
| 单测/新增 lint 违规 | `testAppDebugUnitTest` / `lintAppDebug` + 基线 | **待接入 CI job（F1 ③）** |

原则：能路由的**写入**用可见性（语言级）约束；难封装的**读取**用扫描器（工具级）约束。

## 7. 推荐执行顺序（一句话）

**先用编译器冻结写入 → 集中约 7 个 mutator → 建立权威 ReaderSession（Track A）；同时把渲染 Effect 从 VM 下沉到 UI 渲染控制器（Track B）→ 并行拆分 VM 中与渲染无关的业务 → 最后渐进迁移 Compose/新渲染器（Track C）。** 全程由 F1 的 CI 门禁与 F2 的 DAO 去注入护栏兜底。

严格 MAD 下 Effect 仍扣分；但准确的表述是——**问题从来不是"指令式 View 无解"，而是"指令式渲染协议被错误地穿过了 ViewModel"**。本计划的每一步都在把那条线拉回 UI 层，而不需要等待 Compose。
