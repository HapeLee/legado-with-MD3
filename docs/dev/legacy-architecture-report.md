# legacy 架构债报告（M0-2）

本文是 [kmp-cmp-migration-plan.md](./kmp-cmp-migration-plan.md) Backlog「M0-2：legacy architecture
report」的产物：把当前 legacy 耦合的**真实分布**扫出来、冻结成基线，并让新代码 day-one 就被拦截。

配套产物：

| 产物 | 作用 |
| --- | --- |
| `gradle/architecture/legacy-baseline.txt` | 机器可读基线（330 条，目录级聚合） |
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

- **`core:data` 共享层 47 处 Provider 委托**：`commonMain/.../data/entities` 45 处
  （`BaseSource` 用 `KeyValueStoreProvider`/`SourceRuntimeProvider`/`CookieStoreProvider` 直连
  KeyValue 与 source runtime）+ `commonMain/.../data` 2 处。共享实体里出现静态 service locator，
  是 M2「删除 9 个 core Provider」最先要解的一批。
- **`feature/*` 的 Provider/base/GSON 债**（正是 M1-3 的目标）：
  `tagrules/group` 7 + `tagrules/highlight` 3、`replacerules/edit` 5、`txttocrules` 5、
  `dict/rule` 3 处 Provider；`tagrules` 另有 `legacyBase` 5、`gson` 2。
  这些 Feature 已停在 Stage B，转成 CMP 前必须先把 `Provider.current` 换成注入。
- **`core:ui` 2+2 处 Provider**：`ui/widget/components/importComponents` 走 `ImportJsonEditorProvider`，
  与 `:core:ui` 不依赖 `:core:data`/Gson 的约束相关。

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
