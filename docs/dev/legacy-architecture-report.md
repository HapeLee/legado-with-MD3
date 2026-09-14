# legacy 架构债报告（M0-2）

本文是 [kmp-cmp-migration-plan.md](./kmp-cmp-migration-plan.md) Backlog「M0-2：legacy architecture
report」的产物：把当前 legacy 耦合的**真实分布**扫出来、冻结成基线，并让新代码 day-one 就被拦截。

配套产物：

| 产物 | 作用 |
| --- | --- |
| `gradle/architecture/legacy-baseline.txt` | 机器可读基线（目录级聚合；M0-2 冻结时 330 条，M1-3a 后 326 条，M1-3b 后 323 条） |
| 根 `build.gradle.kts` 的 `CheckLegacyArchitectureTask` / `checkLegacyArchitecture` | 门禁任务（挂 `assemble`/`compile`，与 `verifyConfigArchitecture` 同级） |
| `tools/generate-legacy-baseline.py` | 重新冻结脚本（**会抹平棘轮**，需评审后使用） |

## 1. 扫描口径

- **范围**：生产源集，即 `app/src/main/java` 与各 Gradle 模块的 `src/<非 test 源集>/kotlin|java`；
  共 1699 个 `.kt` 文件。测试源集不冻结——契约测试引用 Provider 是正当用法。
- **聚合粒度**：`<模块或 app>/<源集>/<包目录>`，例如 `app/main/io/legado/app/ui/book/read`、
  `core/data/commonMain/io/legado/app/data/entities`。目录级而非文件级，是为了避免每次搬文件都要重写
  基线（lint 基线漂移的教训）；代价见 §5 的局限。
- **全局门面一律以 import 锚定**：`appDb`/`appCtx`/`GSON` 都是极常见的标识符，
  `private val appDb: AppDatabase`（构造参数）和 KDoc 里的 `appDb` 都会被裸词正则误判，
  所以只认 `import io.legado.app.data.appDb` 这类显式引用。
- **core Provider 逐行统计并跳过注释行**：KDoc 中的 `[ClipboardProvider.install(...)]`
  不是调用；同时排除定义文件自身的 `object XxxProvider` 声明。

## 2. 规则表

| category | 检测 | 为什么是债 |
| --- | --- | --- |
| `appCtx` | `import splitties.init.appCtx` | 全局 `Context`；共享层一旦拿到它，Android 依赖就无法摘除 |
| `appDb` | `import io.legado.app.data.appDb` | 全局数据库门面，绕过注入与 Gateway |
| `gson` | `import io.legado.app.utils.GSON` | 全局 GSON 门面，把序列化实现钉死在平台侧 |
| `legacyHelp` | `import io.legado.app.help.**` | `help` 是遗留静态门面集合，方向上是「谁都能调谁」 |
| `legacyBase` | `import io.legado.app.base.**` | 遗留 Activity/Service/Dialog 基类，与 Android 生命周期绑定 |
| `legacyNaming` | import 到以 `Help`/`Utils` 结尾的顶层类型 | 同上，按命名捕获 `help` 包之外的同类门面 |
| `coreProvider` | 9 个 core Provider 静态委托 | 静态 service locator，Koin 之外的第二套 DI；M2 要逐个删除 |

## 3. 冻结结果（2026-09-10）

| category | 总处数 | 涉及区域 | Top 3 热点 |
| --- | --- | --- | --- |
| `legacyHelp` | 698 | 85 | `data/repository` 46、`ui/book/read` 45、`model` 41 |
| `legacyNaming` | 242 | 59 | `help` 23、`domain/usecase` 15、`model/localBook` 15 |
| `appCtx` | 127 | 54 | `utils` 11、`help` 10、`ui/association` 9 |
| `gson` | 107 | 54 | `domain/usecase` 9、`ui/association` 8、`data/repository` 7 |
| `legacyBase` | 104 | 38 | `ui/association` 34、`service` 7、`di` 4 |
| `coreProvider` | 84 | 11 | `core:data/entities` 45、`app/help` 8、`feature/tagrules/group` 7 |
| `appDb` | 56 | 29 | `help` 5、`api/controller` 4、`model` 4 |

（热点列省略 `app/main/io/legado/app` 前缀。）

## 4. 分级结论

### 4.1 直接阻塞 KMP/CMP 的债（优先清）

- **`core:data` 共享层 Provider 委托 47 → 35 处（M2-2 / M2-3，2026-09-14）**：`commonMain/.../data/entities`
  **45 → 36 → 33** + `commonMain/.../data` 2 处。共享实体里出现静态 service locator，
  是 M2「删除 9 个 core Provider」最先要解的一批。
  - **已清退：`BigDataStoreProvider`（9 处，M2-2）。** 实现下沉共享层
    （`core/data/commonMain` 的 `RuleDataFileStore`，承载全部 `book`/`rss` 路径与标记文件语义），
    只依赖一个平台原语 `core:platform` 的 `expect object RuleDataStorage`
    （根目录 + 文件 IO + MD5，**不认识任何业务名词**）。三个 entity 改为直接引用共享实现，
    `Provider` object 删除。计划里写的「entity 变纯数据 + 上游注入 UseCase」在本仓
    **没有装配点**（entity 由 Room 构造、方法名是书源 JS 兼容面、求值入口全是 `object` 单例），
    详见 `feature-slicing-audit-tagrules.md` §33 与 `cmp-module-convention.md` §15。
  - **已清退：`SymmetricCryptoProvider`（3 处，M2-3）。** 改为 `core:platform` 的
    `expect object SymmetricCrypto`（JCA 在 android/desktop 两个 JVM target 上语义恒等、
    无第三实现）。原判「实现依赖 `:app` 工具」经核实**不成立**：那些工具只用到 stdlib 的
    `kotlin.io.encoding.Base64` 与 JVM 自带的 `MessageDigest`。兼容面（既有用户已落库的
    `userInfo_<sourceKey>` 密文能否读回）由 `openssl` 与 Node 两个**独立实现交叉确认**的硬编码
    向量钉住。详见 `feature-slicing-audit-tagrules.md` §34。
  - **剩余 33 处**全是 `BaseSource` 的 `KeyValueStoreProvider` 15 / `SourceRuntimeProvider` 9 /
    `LoggerProvider` 5 / `CookieStoreProvider` 4。⚠️ **M2-3 已实测否掉「上游装配可行」这个猜想**：
    卡点是**可达性**而非写法——这些实现要 `AppDatabase`（`caches`/`cookies` 表）或 okhttp
    `CookieManager` 这类 **host 实例**，而 `expect/actual` 的 actual 够不着 `:app`；
    `Logger` 则因实现是同名同义的 `constant.AppLog`（300+ 调用方、日志界面读 `AppLog.logs`）
    而不能换成 `android.util.Log`。详见 §34「下一步」。
- **`feature/*` 的 Provider/base/GSON 债**（正是 M1-3 的目标）：
  已清零：`tagrules/group` 7 + `tagrules/highlight` 3（M1-3w）、`replacerules/edit` 5（M1-3x）、
  `txttocrules` 5（M1-3y）、`tagrules` 的 `legacyBase` 5 与 `gson` 2、
  `dict/rule` 6（`gson` 1 + `legacyBase` 2 + `coreProvider` 3，M1-3z）。
  **截至 M1-3z 本项已全部清零** —— `tagrules`/`replacerules`/`txttocrules`/`dict` 四个 Feature
  均已 CMP 化，`feature/*` 下不再有 `main` 源集条目。
  ⚠️ `feature/*` 之外仍有两个规则 VM 停在 Android（`TocViewModel` / `RssSourceViewModel`，
  仍继承 `BaseRuleViewModel`）⇒ `BaseRuleViewModel` / `BaseRuleEvent` 需保留到 M2 退役。
  ⚠️ 注意转 CMP 会把 `main` 源集换成 `commonMain`/`androidMain` ⇒ 基线里的 `<module>/main/...`
  条目要**删除或下调**，且新源集/新包目录在棘轮上**起步为零**（不允许放宽基线，只能把依赖
  搬到干净包名）。详见 `cmp-module-convention.md` §10。
- **`core:ui` 的 Provider 已清退一半（M2-1，2026-09-14）**：`ui/widget/components/importComponents`
  曾是 `ImportJsonEditorProvider.current`——当初为了避免 `:core:ui` 依赖 `:core:data`/Gson，
  把 Gson 树模型收敛成契约 + 一个全局注入点；M2-1 进一步改成 `BatchImportDialog` 的**参数注入**
  并删除 Provider（通用组件库不再认识 service locator），计数归零。
  剩 `ui/widget/components` 的 2 处是 `LoadMoreFooter` 的 `ClipboardProvider.current`，
  等它随页面迁进 `:core:designsystem` 时一并处理（单独搬一个组件只为清零 2 计数不划算）。

### 4.2 blocking 区域（新代码 day-one 拦截）

除 §4.3 之外的一切区域：`app/ui/**`、`app/feature/**`、`app/data/**`、`app/domain/**`、
`core/*`、`feature/*`、`modules/*`。这些区域目录内新增任意一类即构建失败。

### 4.3 report-only 区域（legacy 大本营，只告警）

`app/main/io/legado/app/{help,base,model,service,api,utils,lib,receiver}`。

它们本身就是待下沉/待删除的债主体：在债堆内部做清理而让计数上下浮动，不该阻塞交付；
真正要拦的是**债往外扩散**。因此这些目录越界只打 `[legacy 债]` warning，不失败。

## 5. 门禁行为

| 情况 | 结果 |
| --- | --- |
| 目录内计数 > 基线 | blocking 区域失败；report-only 区域 warning |
| 目录内计数 < 基线 | **一律失败**，提示「请将基线从 X 下调」（棘轮只降不升） |
| 基线里没有的新区域出现违规 | 一律失败（新代码必须为零，或经评审显式登记） |
| 基线文件行格式错误 | 失败并指出错误行 |

命令：

```powershell
.\gradlew.bat checkLegacyArchitecture
```

它已与 `verifyConfigArchitecture` 一样挂在各子项目的 `assemble*`/`compile*` 上，
也可直接跑上面的独立任务。

## 6. 维护约定

- **越界时**：优先改走契约/Gateway/注入；确属历史文件搬家的，手工把受影响的基线条目迁移到新区域
  （计数随之搬），不要整体重跑生成脚本。
- **减少时**：按提示下调对应条目到当前值；条目归零就删掉该行。
- **批量清理后**：可 `python tools/generate-legacy-baseline.py` 重新冻结，但这会把所有区域重置为当前值，
  等于一次性抹平棘轮收益——只在「一批债已清掉、旧基线满屏报红」时经评审使用。

## 7. 已知局限

- 目录级聚合允许「同目录内删一处、加一处」互相抵消；这是为了减少基线维护噪音主动接受的代价
  （`verifyConfigArchitecture` 的 DAO 基线是文件级，两者互补）。
- 只认 import 形态的**直接**引用：同包内不 import 的使用、`*Help.xxx()` 的全限定写法不在统计内。
- `legacyHelp` 与 `legacyNaming` 有重叠（`help` 包下的 `XxxHelp` 会同时命中两条），
  分开统计是为了分别跟踪「包级债」和「命名级债」的下降趋势。
- 本报告只冻结数量，不判断每处是否合理；真正的清理顺序由 M1/M2 的切片决定。
