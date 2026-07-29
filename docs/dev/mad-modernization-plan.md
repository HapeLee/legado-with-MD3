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
- **F2 清零（已提交 13 批）**：关联导入 → RSS → 书籍导入 → 书源管理 → 换源+目录（`77d38b8`…`04161fa`）；随后清完 8 个可清 VM（Web/BookInfoEdit/ChangeCover/TagGroupRule/SourceLogin/AudioPlay/ReadManga/BookInfo）+ `ReadBookViewModel` 非阅读态子集。**主线基线从 ~247 → 130 → 36 → 0**（`ReadBookViewModel` 的 book/chapter 阅读态访问已随 R2.1 溶解进 `BookRepository`）。
- **数据层事务加固**：目录替换（`replaceChaptersAndUpdateBook`）、换序 move（`BookSourceDao.moveToTop/Bottom`）已改原子事务。

### 待续（新窗口从这里接）

**① F2 主线已清零**（`build.gradle.kts` 的 `legacyDaoInjectionBaseline`）：

| VM | 直连数 | 备注 |
|---|---:|---|
| `ReadBookViewModel` | 0 | 原 24× `bookChapterDao` + 12× `bookDao` 已于 R2.1 全部改走 `BookRepository`；棘轮保留 0 值条目继续盯这个文件 |

> R2.1 已把余下 33 处改走 `BookRepository`。计划书原文写的是「随 `ReaderSession` 溶解」，实际没这么做：`ReaderSession` 的契约明写「调用方只拿到只读快照，拿不到可变领域对象（Book/TextChapter）」，往里加 `getChapter(): BookChapter?` 正好破掉这条不变式，而 `BookRepository` 早已具备所需的全部方法。
>
> 清 8 个可清 VM 时给若干仓库补了无聊方法：`BookRepository`（`getAll`/`getLastReadBook`/`getChapter`/`delete`）、`BookSourceRepository`（`has`/`getAllTextEnabledPart`）、`SearchRepository`（`getEnableHasCover`/`getSearchBook`）、`BookGroupRepository`（`getIdsSum`/`getGroupNames`）、`HighlightTagRuleRepository`（`getEnabled`，并在 Koin 注册）、`HttpTtsRepository`（`getAll`/`getAllSync`/`getNameSync`/`delete`）。宿主 `by viewModels()` → Koin `by viewModel()` 已同步改（Web/BookInfoEdit/ChangeCover/SourceLogin/AudioPlay）。

**② F2 第二条线（非 VM UI 层）**：`legacyUiDaoAccessBaseline` 已冻结 **26 文件 / 62 处**，**只冻不修**。等主线清完、仓库齐备、部分 Activity 随 Compose 迁移消失后再回头清（大头 `BookSourceActivity=10`、`RssJsExtensions=8`、`KeyboardAssistsConfig=7`）。

**③ F1 收尾**：仍缺一个**显式跑 `testAppDebugUnitTest` + `lintAppDebug` 的 CI job**——这是 F1 唯一没关上的门（当前单测失败/新增 lint 违规不会让 CI 红）。

**④ Track A**：**A1–A5 已全部落地**。A1–A3：`ReadBook.book/durChapterIndex/durChapterPos` 已 `private set`，外部 6 处写入改走 `replaceCurrentBook`/`clearCurrentBook`/`updateReadingPosition` + `isCurrentBook` 谓词，编译器证明外部写入点为 0。A4：`LegacyReaderSnapshot` + `ReadBook.snapshot: StateFlow`，`publishSnapshot()` 落在 14 个内部 mutator + 3 个命令的原子末尾。A5：`ReaderSession` 接口 + `LegacyReaderSession` 桥接（`model/ReaderSession.kt`，Koin 注册），`ReadBookViewModel` 已注入并 `collectReaderSession()` 反应式消费。

**⑤ Track B**：**B1/B2 已落地**（渲染子集下沉），端游剩余项见下。**Track C**：**C0–C5 已落地后按 2026-07-21 决策暂停**（Compose overlay 冻结为 flag 下的可选渲染器，不推进 C6–C9），详见下方 ⑥ 与 `docs/dev/track-c-compose-reader-plan.md` 顶部状态。

- **B1（接口切分）**：`ReadBook.CallBack` 切成 `ReaderRenderCallback`（渲染子集：`upContent`/`upContentAwait`/`pageChanged`/`contentLoadFinish`/`upPageAnim`/`cancelSelect` + `LayoutProgressListener`）与状态子集 `CallBack`（`upMenuView`/`loadChapterList`/`notifyBookChanged`/`sureNewProgress`）。
- **B2（渲染下沉）**：新增 `ReadBook.renderCallBack: ReaderRenderCallback` 独立槽位；30+ 处渲染调用点由 `callBack?.` 改走 `renderCallBack?.`。`ReadBookController` 实现 `ReaderRenderCallback`，`onRefsReady` 注册 / `clearTts` 注销，渲染方法复用既有 `handleEffect` 分支（零渲染逻辑漂移）。**线程关键点**：`ReadBook` 在 `Coroutine.async`(默认 `Dispatchers.IO`) 里回调渲染，旧路径靠 VM 的 `SharedFlow`→生命周期收集器切主线程；B2 用 `handler.post`/`withContext(Main)` 保留同样的异步-切主线程语义。业务状态刷新改由 `collectReaderSession()` 收集快照驱动——为内容加载路径（`contentLoadFinish`/`contentLoadFinishAwait` 布局完成、`loadOrUpContent`、`upMsg`）补 `publishSnapshot()`，补齐旧 VM 渲染回调顺带做的 `syncFromReadBook`（尤其 `seekMax` 依赖 `curTextChapter.pages.size`）。`isInitFinish` 作为纯业务标志留在 VM，由 `controller.contentLoadFinish` 幂等置位。
- **Track B 端游剩余（未做）**：① VM **仍实现** `ReadBook.CallBack`（状态子集），完整 B 验收「VM 不再实现 CallBack」需把状态子集也迁到 `ReaderSession`；② `emitEffectWhenSubscribed` 的 `subscriptionCount` 握手仍在（现仅服务于 `UpdateReadViewConfig` 这类非渲染 init 时序，一处），完整移除属端游；③ `PageChanged`/`ContentLoadFinish`/`LayoutPageCompleted` 三个 Effect 现在只由 controller 的渲染方法**直接**触发 `handleEffect`（不再经 `_effects` 总线），可在后续把这三个 handler 内联进渲染方法体、删掉对应 Effect 类。
- **B2 验证边界（真机）**：翻页/换章时章节号与进度、seek bar 上限（page 模式依赖 `pages.size`，B2 改为布局完成后单次 `publishSnapshot` 刷新，非逐页）、首帧渲染（`registerRender` 已在有内容时立即同步一次）、朗读中翻页、`upContentAwait` 时序（B2 下 `withContext(Main)` 会在 await 内**同步**跑完视图更新，比旧实现「只 await emit」更靠后完成，需确认换章无闪烁/错序）。

**⑥ 方向调整（2026-07-21）：Compose 阅读器暂停于 C5，新增 Track D（`ReadView` 自身业务解耦）为近期主线。**

依据：C3 真机帧基线显示 Compose 渲染劣于旧 View（jank 50% vs 4.13%；50/90/95/99 = 30/42/44/53ms vs 5/8/21/65ms），且仿真卷曲/选择/滚动/自动翻页等成熟能力全量重写为「高回归风险、低用户收益」的改造。故：

- **Track C 暂停、不删旧栈**：C0–C5 产物（`ReaderRenderer` 契约、`ReaderRenderModel`、`ComposeReaderSurface`、不可变 `ReaderPageSnapshot`）**保留在 lab flag 后，冻结为可选可插拔渲染器**（默认关），作为「将来真要换渲染器」的逃生舱与 parity 实验台。**C6–C9 不再是当前主线**，待后续专门的性能调查后再重估「Compose 默认 + 删 View」还是「View 永久渲染岛」。
- **新增 Track D：把 `ReadView` 自身的出站业务耦合解掉**。核实（`ui/book/read/page/ReadView.kt`）：ReadView 仍直呼 `ReadBook.moveToNextChapter/moveToPrevChapter/moveToNextPage/moveToNextChapterAwait/syncProgress`、`ReadAloud.pause/resume`（509–750 行），并经 `callBack.*` 直接驱动书签/内容编辑/替换规则/目录/搜索/进度确认（504、513–519 行）。这是 A/B/C 都未触及的一面——A 收敛 `ReadBook` 所有权、B 把渲染回调移出 VM、C 让 Compose 面消费只读快照，**都没动 `ReadView` 的出站调用**。Track D 用 `ReaderEvent` 出站接口把这些业务调用路由回 `ReaderSession`/命令，让 View 阅读器本身符合 UDF——**不依赖 Compose**。设计见下方 Track D 节。
- **C5 工作区改动保留**（`ComposeReaderSurface.kt`/`ReaderRenderModel.kt`/`ReaderRenderStateStoreTest.kt`/`ReaderPageSnapshot.kt`），作为冻结状态；可单独提交 `feat(Track C): C5 不可变快照（冻结为可选渲染器）`。

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
| UI→DAO 直连是系统性模式 | 计划撰写时约 33 个 VM 构造注入 DAO；**现已冻结并清零 8 个可清 VM + `ReadBookViewModel` 全部访问，主线余 0**（见进度快照） |
| CI 门禁 | lint 基线与架构护栏已就位；**仍缺显式 CI test/lint job**（见进度快照 ③） |
| 已有架构护栏 + 基线机制 | `VerifyConfigArchitectureTask` + 三条基线（`legacyPreferenceCallBaseline` / `legacyDaoInjectionBaseline` / `legacyUiDaoAccessBaseline`），已 `dependsOn` assemble/compile |

## 2. 反目标（明确不做）

- **不做全量重写**：`ReadBook` 6000 行不横向切成几十个文件——那只是"分文件的 God Object"。
- **不做伪状态**：不把 `upPageAnim`/`cancelSelect` 之类指令式渲染命令塞进 `UiState`（`shouldAnimatePage: Boolean`）。那是把门铃伪装成恒温器。
- **不做架构宇航员**：DAO 边界仓库保持"刻意无聊"，禁止为凑 Clean Architecture 目录树而造假 UseCase、无谓 mapping、单方法包装类。
- **不用 Compose 迁移当作解耦前置**：所有权重构和渲染边界下沉都不依赖 Compose。
- **不优先造扫描器**：写入面是个位数，用编译器（`private set`）约束，而不是先写 Detekt/lint 规则。
- **不做万能 Repository**：DAO 去注入按功能逐批，不预先设计几十个大仓库。

## 3. 轨道模型 + 两条横切地基

改造被拆成若干**相互解耦、可并行**的轨道，外加两条横切地基。

```
横切地基：
  F1  CI 质量门禁（测试/lint 基线/架构检查）
  F2  UI→DAO 去注入（全应用，非阅读器专属）

Track A  所有权与业务状态       —— 已落地（A1–A5）
Track B  遗留渲染边界下沉       —— 已落地（B1/B2），端游剩余
Track C  声明式渲染 / Compose   —— C0–C5 已落地，2026-07-21 起暂停；2026-07-25 已删除
Track D  ReadView 自身业务解耦   —— 近期主线，不依赖 Compose，依赖 A（ReaderSession）
Track E  阅读设置的 SSOT 与 UDF —— 与 D 正交、可并行，无前置依赖
```

> **方向修订（2026-07-21）**：原路线把 Track C（Compose 替换阅读器）当作阅读渲染的终点。基于 C3 真机性能基线（Compose 帧耗时/jank 明显劣于旧 View）与「仿真卷曲/选择/滚动/自动翻页全量重写=高回归风险、低用户收益」的判断，**改为**：保留成熟的 `ReadView` 作为渲染核心，把它从「业务+状态+渲染全包」重构成**只做绘制/手势/动画的专业渲染岛**；Compose 用于阅读器外围 UI（工具栏/菜单/设置/弹窗）与「可选可插拔渲染器」实验。**MAD ≠ Compose**：分层、UDF、状态所有权、`ViewModel+StateFlow`、生命周期感知、可测试的数据/业务层，均可在 View 上成立（Android 官方至 2026 仍维护专门的 View 架构指南）。Track C 是否最终成为默认渲染器，留待专门的性能调查后重估，不作为当前目标。

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
- `legacyDaoInjectionBaseline` —— `ui/**` 里**含 `ViewModel`** 的文件（主线，当前 **0**；`ReadBookViewModel` 保留 0 值条目防回退，另有 `CloudTtsViewModel` 13 处为护栏缺席期冻结的历史债）
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

> **进度**：A1–A5 已完成并提交（见本节末「A4/A5 落地说明」）。

目标：**阅读状态有唯一写入入口，Snapshot 可信**。不受 View/Compose 进度影响。

**A1. 编译器冻结写入** ✅。把 `ReadBook` 的会话字段改为 `private set`：
```kotlin
var book: Book? = null
    private set
var durChapterIndex = 0
    private set
var durChapterPos = 0
    private set
```
> 用 `private set` 而非 `internal set`——单体 `:app` 内 `internal` 几乎不设防。

**A2. 为外部写入点提供语义化命令** ✅。核实后外部写入面只有 **6 处**（不是估的 7）：BookInfoViewModel×2、BookInfoEditViewModel×1、`Book.delete`×1（置空 book）、ReadBookController×2（章内位置）。**实际落地的命令集**（忠实最小实现，命令体只做原代码做的事，不做计划示例里那种「顺手重置 index/pos」的行为漂移）：
```kotlin
fun replaceCurrentBook(book: Book)   // 仅替换引用，不重置章节/进度
fun clearCurrentBook()               // 仅置空 book
fun updateReadingPosition(position: Int)
```
> 计划原稿的 `moveToChapter` / `clearCurrentSession` **没有实现**——现有外部写入点没有这两种语义；章节跳转本就走 `ReadBook.openChapter`/`setProgress` 等内部方法，不是外部裸赋值。避免造未使用的命令。

**A3. 意图化谓词替代裸实体身份比较** ✅（部分）。已落地：
```kotlin
fun isCurrentBook(bookUrl: String): Boolean = book?.bookUrl == bookUrl
fun isCurrentBook(other: Book): Boolean = book?.isSameNameAuthor(other) == true
```
6 处写入点的身份判定已改走谓词。
> A3 的完整形态（「绝不向外递出可变 `Book`」）**未做**——`ReadBook.book` 仍在 ~44 个文件被读取，是 Track B/C 与 VM 拆分的范畴，不在本轮。已核实当前「引用逃逸写入」（`ReadBook.book?.durChapterIndex = x`）为 **0**（grep 无命中），`private set` 也堵住了其经由 `ReadBook.*` 字段复发的路径。

**A4. 权威不可变快照** ✅。仅在写入受控后引入：
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

**A5. `ReaderSession` 面向所有者的 API** ✅（写入全部受控后引入）：
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

**验收**：`ReadBook` 会话字段全部 `private set` ✅；外部写入点为 0（全部走命令）✅（编译器保证）；`snapshot`/`state` 在任何路径的状态变更后都能正确发射 ✅（见下「发布覆盖」）；VM 可从 `ReaderSession.state` 取业务状态 ✅（`ReadBookViewModel.collectReaderSession()`）。

#### A4/A5 落地说明（本轮结论）

**实际实现与计划示例的差异（忠实优先）**：

- **快照字段**：只承载 `ReadBook` **自己拥有、并在受控 mutator 中重新发布**的廉价标量——`bookUrl`/`bookName`/`chapterIndex`/`chapterPos`/`chapterCount`/`simulatedChapterCount`/`isLocalBook`。**刻意排除计划示例里的 `isLoading` / `isReadingAloud`**：`isReadingAloud` 真实来源是 `BaseReadAloudService.isRun`（独立服务，ReadBook 变更不触发其重发），`isLoading` 只有 `msg`/`loadingChapters` 间接信号；塞进 ReadBook 发布的快照会得到静默陈旧字段。要做需让来源方参与发布，另立字段。
- **命令体忠实**：`replaceCurrentBook` 只替换引用、不像计划示例那样顺手 `clearTextChapter`/重置 index/pos（那是行为漂移）。
- **发布覆盖**：核实后内部写入落在 **14 个 mutator**（`resetData`/`upData`/`setProgress`/`moveToNextPage`/`moveToPrevPage`/`moveToNextChapter`(+`Await`)/`moveToPrevChapter`/`skipToPage`/`setPageIndex`/`openChapter`/`syncReadAloudPage`/`upToc`/`onChapterListUpdated`）+ 3 个语义命令，每处在**原子转移末尾** `publishSnapshot()`（`grep` 已逐一核对覆盖）。过发无害（StateFlow 对相等值去重），漏发才会陈旧。
- **消费方式（关键设计决策）**：`ReadBookViewModel` 用**反应式收集** `readerSession.state.collect { syncFromReadBook }` 消费，而**非**让 `syncFromReadBook` 同步读 `snapshot.value`。原因：`upMenuView()`/`pageChanged()` 会在 mutator **方法体内、`publishSnapshot()` 之前**同步调用 `syncFromReadBook`；若那里读 `snapshot.value` 会拿到发布前的旧值（一帧滞后）。反应式收集在 mutator 返回后异步触发，此时 ReadBook 字段已是最终态，`syncFromReadBook` 直读 ReadBook 即新值；该刷新与遗留 CallBack 路径叠加、幂等，无回归、无滞后。

**性能**：快照全是标量、`buildSnapshot()` 为 O(1)，发布频率是用户级（翻页/换章/位置 settle），非 60fps 滚动 tick，未把热路径挂上 equality 快照。`updateReadingPosition` 目前仅 `ReadBookController` 朗读暂停/恢复 2 处调用，非逐帧。

**仍未做（Track A 之外）**：A3 完整形态「绝不外递可变 `Book`」——`ReadBook.book` 仍在 ~44 文件被读，属 Track B/C 与 VM 拆分。快照的「高频 viewport / 低频结构」二流拆分本轮未做（当前单流即够，因无 60fps 发布源）；若将来位置更新变高频再拆。

> **验证边界**：编译 + 272 单测全绿，但都覆盖不到「阅读器实时状态在每条路径正确发射」。需在真机/模拟器验证的路径：翻页/换章时章节号与进度、保存书籍信息时正在读该书、删除正在读的书、朗读中翻页。

---

### Track B —— 遗留渲染边界下沉（与 A 并行，**不等 Compose**）

> **进度**：B1（接口切分）+ B2（渲染子集下沉到 `ReadBookController`）已落地并提交，端游剩余项见进度快照 ⑤。

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

> **B2 已达成中期目标**（渲染子集不再穿过 VM）；上面这条**完整验收**尚未全绿——VM 仍实现 `CallBack` 的**状态子集**、握手仍在。三项端游剩余见进度快照 ⑤，属把状态子集也迁入 `ReaderSession` 的后续工作。

---

### Track C —— 声明式渲染 / Compose 迁移（**2026-07-21 暂停，2026-07-25 已删除**）

> **状态（2026-07-25）**：C0–C5 的 Compose 正文渲染实现**已从代码库删除**——`ComposeReaderSurface`、`ComposeReaderRenderCache`、`ReaderPageSnapshot`、`ReaderRenderModel`/`ReaderRenderStateStore`、`ReaderRenderer` 契约、lab flag `LabSettings.composeRenderer` 及其字符串与 DataStore 键，连同两个对应单测一并移除。冻结状态维护成本（每次翻页都在 `publishStructure` 里做一遍无消费方的快照投影）大于其 parity 实验台价值。
>
> **保留下来的 C 期产物**：`ReaderViewport`/`ReaderLayoutCoordinator`（C4 的 viewport 解耦接缝，`ContentTextView` 与 `ReadBookController` 在用）、`ReaderFirstFrame.kt`（debug 首帧探针，`tools/` 采集脚本依赖）。
>
> **原暂停依据（2026-07-21）**：C3 真机帧基线 Compose 明显劣于旧 View（jank 50% vs 4.13%）；仿真卷曲/选择/滚动/自动翻页全量重写 = 高回归风险、低用户收益。若将来重启，从下面的历史设计重新实现，不必复活已删代码。

> **执行计划见 `docs/dev/track-c-compose-reader-plan.md`**（已按两轮外部审阅重排：C0 立 `ReaderRenderer` 契约〔随 C3 补〕 → C1 抽只读 `ReaderRenderModel`〔已落地〕 → C2 flag 下 Compose 静态页 overlay〔已落地〕 → C3 稳住 overlay + parity/性能基线 → **C4 抽 `ReaderViewport`（含 `mode` 预留）让 Compose 驱动分页、旧 `ReadView` 可关、并开始拆 `upContentAwait`〔核心里程碑〕** → C5 不可变 `ReaderPageSnapshot` → C6 无动画阅读闭环〔图片/双页/点击/选择/滚动〕 → C7 停止双绘 + 删 `upContentAwait` → C8 翻页动画、仿真卷曲垫底 → C9 翻默认、删旧栈；排版引擎 `ChapterProvider` 全程不动）。**关键调整：把 viewport 解耦与不可变快照提到动画/复杂交互之前，避免在 overlay 上堆功能形成永久双栈。**

```
Legacy Canvas View
 → 抽取稳定 RenderModel（viewport 尺寸、分页结果流、动画进度、手势、朗读高亮、预加载反馈）
 → Compose Canvas 或新渲染组件
 → 用状态 + 本地动画状态替代大部分渲染命令（含反转 upContentAwait 的往返）
 → 删除 LegacyReaderRenderController
```

> Compose 不会自动蒸发分页排版复杂度，只是让"状态↔绘制"的关系更好表达。它是最自然、但非唯一的声明式实现。

---

### Track D —— `ReadView` 自身业务解耦（近期主线，不依赖 Compose）

> **由来（2026-07-21）**：Track A 收敛了 `ReadBook` 所有权、Track B 把渲染回调移出 VM、Track C 让 Compose 面消费只读快照——但三者都没动 **`ReadView` 自己**的出站耦合。既然决定「保留 View 作为渲染核心」，就必须把 View 阅读器本身拉回 UDF：**`ReadView` 只做绘制/手势/动画，不认识 `ReadBook`/`ReadAloud`/Activity 导航。**

**已核实的耦合**（`ui/book/read/page/ReadView.kt`）：
- **直呼业务单例**：`ReadBook.moveToNextChapter/moveToPrevChapter/moveToNextPage/moveToNextChapterAwait/syncProgress`、`ReadAloud.pause/resume`（509、510、518、524、526、749、750 行）。
- **经 `callBack` 直驱业务/导航**：`addBookmark`、`openContentEdit`、`changeReplaceRuleState`、`openChapterList`、`openSearchActivity`、`sureNewProgress`、`showActionMenu`（504、513–519 行）。
- **直接读 `ReadBook` 取页数据**：`ReadBook.textChapter(0/1/-1)`（823–833 行，经 `pageFactory`）。

**三类状态纪律（呼应反目标「不做伪状态」）**——Track D 必须守住这条线，避免为 UDF 把每个触点塞进 `StateFlow`：

| 类别 | 归属 | 例 |
|---|---|---|
| 业务状态 | `ReaderSession`/VM | 书籍、章节、进度、配置、加载/错误、朗读态 |
| 渲染状态 | 分页引擎产出 → View | 前/当前/后页、文本布局、图片布局、页面样式 |
| 瞬时控件状态 | **`ReadView` 自持，绝不上浮** | 触摸坐标、`Path`/`Matrix`、`Scroller`、翻页动画进度、卷曲 `Bitmap`、阴影、选择手柄拖动、每帧 `invalidate` |

> 尤其卷曲手势、动画进度、每帧 `invalidate` 是每秒几十上百次变化、且只属于 `ReadView` 的状态——**不进 `UiState`**。把它们塞进 `StateFlow` 是「把门铃伪装成恒温器」的镜像错误。

**D1（出站解耦，先做、低风险、不依赖 Compose）**：定义 `ReaderEvent` 出站接口，`ReadView` 把「业务意图」以事件发出，由外层（Activity/VM）翻译成 `ReaderSession`/命令调用：
```kotlin
sealed interface ReaderEvent {
    data object RequestPreviousPage : ReaderEvent
    data object RequestNextPage : ReaderEvent
    data object OpenMenu : ReaderEvent
    data class SyncProgress(/* … */) : ReaderEvent
    data class ToggleReadAloud(/* … */) : ReaderEvent
    data object AddBookmark : ReaderEvent
    data object OpenContentEdit : ReaderEvent
    data object OpenChapterList : ReaderEvent
    data object OpenSearch : ReaderEvent
    // …翻页完成 / 选择变化 / 链接点击按需补
}
fun interface ReaderEventListener { fun onEvent(event: ReaderEvent) }
```
`ReadView` 的 `ReadBook.*`/`ReadAloud.*` 直调与 `callBack` 的业务项改为 `eventListener?.onEvent(...)`；外层 `readView.eventListener = ReaderEventListener { viewModel.onIntent(it.toReaderIntent()) }`。验收：`grep` 证明 `ReadView.kt` 不再为 mutation 引用 `ReadBook`/`ReadAloud`，业务调用全部经事件出站。**保留** `ReadView` 对 `pageFactory` 的**只读**页数据拉取（属 D2，D1 不强改签名）。

> **忠实边界**：D1 只搬「谁下达业务命令」，不改翻页/排版/动画行为。`callBack` 里真正属于「瞬时 UI 副作用」的项（`screenOffTimerStart`/`upSystemUiVisibility`/`showTextActionMenu`）可留在 View↔宿主的直接协作里，不必强行事件化——它们不是业务状态。

**D2（入站解耦，较大、可后置）**：把 `ReadView` 对 `ReadBook.textChapter(...)` 的直接页数据拉取，改为消费一个由会话/渲染模型派生的**只读输入**（报告里的 `ReaderRenderState`——与 Track C1/C5 的 `ReaderRenderModel`/`ReaderPageSnapshot` 同源，只是这次喂给旧 `ReadView` 而非 Compose 面）。这一步更大（涉及 `pageFactory` 取页时序），**可在 D1 稳定后再评估**，不与 D1 捆绑。

**最终形态（可选，视需要）**：把「渲染岛」收敛到 `ReaderRenderer` 契约背后（Track C0 已立），当前实现 `ViewReaderRenderer`（包 `ReadView`）为**规范实现**；`ComposeReaderRenderer`（C0–C5 的冻结产物）为可选。业务层不因渲染器切换而重做。

**验收**：`ReadView` 不再直接写 `ReadBook`/调 `ReadAloud`/驱动 Activity 业务导航（D1，编译器/grep 可证）；瞬时控件状态全部留在 View、未上浮到 `UiState`；翻页/仿真卷曲/选择/滚动/自动翻页行为零漂移（真机 parity）。

---

### Track E —— 阅读设置的单一数据源与 UDF（与 D 正交，可立即开始）

> **执行计划见 `docs/dev/track-e-reader-settings-udf-plan.md`**。

Track D 管「`ReadView` 怎么把业务意图**发出去**」，Track E 管「设置怎么**流进来**」。两者不互相阻塞。

**核心问题（2026-07-25 核实）**：排版底座 `ReadBookConfig.Config`（`readConfig.json`）是**可变全局、无 flow**，
而 `ReadStyleGateway.state` 只暴露 items/selectedIndex/shareLayout，不含任何排版字段。于是 UDF 靠 VM
在每次写入后**手工重建** `styleConfig`/`sheetConfig` 快照来模拟——漏一处就是「弹层显示旧值」
（2026-07-24 已修一例，但下一个新站点仍会复发）。另有 157 个 `ConfigUpdate` 成员各自**手写**
渲染副作用集（已查出 2 例错配），以及 4 个 sheet 文件直读可变全局并 seed 本地镜像状态。

**阶段**：E0 防回归不变式测试 → E1 按通道把 `ConfigUpdate` 拆成两族（漏填 actions 编译不过）→
**E2 给排版底座补全量不可变快照 flow（核心，单独就能消灭整个 bug 类别）** → E3 sheet 去全局直读 →
E4 收敛 EventBus 整数码 → E5 排版引擎去全局读（**不单独做，并入 Track D2**）。

**写入面不动**：排版写入已收敛到 `ReadStyleMutation` 类型化键，全库仅 `ReadBookStyleConfigRepository`
一处直写 `ReadBookConfig`。Track E 只补「写完之后怎么让所有人看到新值」。

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

> **2026-07-21 修订**：底部「→ 最终由 Track C 替换为声明式渲染」**改为长期可选**。当前目标是 `ReadView`/`ContentTextView` 作为**稳定的规范渲染岛**保留，Track D 补一条 `ReadView → ReaderEvent → VM/ReaderSession` 的出站回边（替代 `ReadView` 现在直呼 `ReadBook`/`ReadAloud`/`callBack` 业务项）。Compose 渲染器（`ComposeReaderRenderer`）冻结在 `ReaderRenderer` 契约后，作为可选实现，非默认终点。

## 6. 强制机制（编译器 > 扫描器）

| 约束 | 机制 | 强度 |
|---|---|---|
| `ReadBook` 会话字段不可外部写 | Kotlin `private set`（`book`/`durChapterIndex`/`durChapterPos`） | 编译期，不可绕过 ✅ |
| ViewModel 不新增 DAO 直连 | `VerifyConfigArchitectureTask` + `legacyDaoInjectionBaseline` | CI，双向棘轮 ✅ |
| 非 VM UI 层不新增 DAO 直连 | 同上 + `legacyUiDaoAccessBaseline` | CI，双向棘轮 ✅ |
| 不新增旧偏好直读 | 现有 `legacyPreferenceCallBaseline` | CI（已存在） |
| `ReadBook` 读取收敛 | lint/Detekt（读取难以编码约束，且低风险，用扫描器足够） | CI 提示 |
| 单测/新增 lint 违规 | `testAppDebugUnitTest` / `lintAppDebug` + 基线 | **待接入 CI job（F1 ③）** |

原则：能路由的**写入**用可见性（语言级）约束；难封装的**读取**用扫描器（工具级）约束。

## 7. 推荐执行顺序（一句话）

**先用编译器冻结写入 → 集中约 7 个 mutator → 建立权威 ReaderSession（Track A，已落地）；同时把渲染 Effect 从 VM 下沉到 UI 渲染控制器（Track B，已落地）→ 并行拆分 VM 中与渲染无关的业务 → 把 `ReadView` 自身的业务耦合解掉、让 View 阅读器符合 UDF（Track D，近期主线，不依赖 Compose），并行推进阅读设置的 SSOT 收敛（Track E，无前置依赖）。Compose 阅读器（Track C）已于 2026-07-25 删除，若重启需重新实现。** 全程由 F1 的 CI 门禁与 F2 的 DAO 去注入护栏兜底。

严格 MAD 下 Effect 仍扣分；但准确的表述是——**问题从来不是"指令式 View 无解"，而是"指令式渲染协议被错误地穿过了 ViewModel"**。本计划的每一步都在把那条线拉回 UI 层，而不需要等待 Compose。
