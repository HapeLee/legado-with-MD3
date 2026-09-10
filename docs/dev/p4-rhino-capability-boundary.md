# P4 Rhino capability 边界调研

> **归档说明（2026-09-10）**：本文记录 `BaseSource` 下沉时的 Android/Desktop 局部决策，不再定义完整
> KMP/CMP 的最终 runtime。最终目标需要 iOS native runtime、真实书源 corpus、构造注入和旧 `help/Provider`
> 清退，统一见 [`kmp-cmp-migration-plan.md`](kmp-cmp-migration-plan.md) M4。

调研对象：`D:\Project\shutiao\legado`（下称「样本仓」）的 QuickJS + Rhino 双桥接方案，
以及本仓库 `BaseSource` 下沉所缺的能力边界。

本文只回答一个问题：**`BaseSource` 及其派生的 4 个实体（BookSource / RssSource /
BookSourcePart / HttpTTS）要下沉 commonMain，JS 能力边界该划在哪。**

---

## 0. 结论先行

样本仓的答案不是「抽象 JS 引擎」，而是 **把「JS 面」从实体上剥离**：

> 实体（BookSource 等）下沉后**不再继承 `JsExtensions`**；
> `evalJS` 注入 bindings 时用一个 **30 行包装器** 把 JS 面补回去，
> 包装器用 `BaseSource by source` 接口委托，属性读写转发回原实体。

本仓库只需取这一个手法，**不必**取样本仓的双引擎、KSP 分派表、iOS/鸿蒙 actual——
理由见 §4「哪些可以不学」。

---

## 1. 样本仓的 JS 架构（四层）

| 层 | 模块 / 位置 | 体量 | 职责 |
|---|---|---|---|
| 契约 | `modules/js-api` | **3 文件** | `@JsApi` 注解 + `JsValueConverter` 协议 + 注册表 |
| 引擎 | `modules/rhino`、`modules/quickjs` | 各 20~40 文件 | 两套**平行**实现（不是同一接口的两个实现） |
| 统一面 | `shared/commonMain/.../model/script/JsEngine.kt` | ~290 行 | `JsEngine` / `JsScope` / `JsBindings` / `JsObject` / `JsFn` / `JsCompiledScript` |
| 分派注入 | `.../model/script/JsEngines.kt` | ~160 行 | `JsEngineProvider` 注册 + 双检缓存 + 双判谓词 |

### 1.1 关键的「双判谓词」设计

`JsEngine` 把类型判定下放到引擎实例，抽象面因此**不见任何具体引擎类**：

```kotlin
fun isJsObject(obj: Any?): Boolean   // rhino NativeObject vs quickjs NativeObject
fun asJsObject(obj: Any?): JsObject?
fun isJsException(t: Throwable): Boolean
```

这解决的是「同名不同包的两种类型」问题。本仓库只有 Rhino，
**这三个谓词不会真正用到**，但设计思想值得记：判定逻辑属于实现，不属于抽象。

### 1.2 `JsBindings` 是纯 Map

```kotlin
class JsBindings : MutableMap<String, Any?> by LinkedHashMap()
```

刻意**不继承**任何引擎类型（rhino 的 `ScriptBindings` 继承 `NativeObject`、
quickjs 的继承 `LinkedHashMap`）。各引擎自己在 `getRuntimeScope` / `eval` 入口转换。

---

## 2. 破局手法：JsExtensions 三件套拆分

样本仓里**没有** `JsExtensions.kt` 这个文件。它被拆成：

| 件 | 位置 | 体量 | 内容 |
|---|---|---|---|
| `JsExtensionsCommon` | `shared/commonMain` | 1176 行 | 跨平台的 JS API 面 |
| `JsExtensionsPlatform` | commonMain(70) + jvmAndAndroidMain(72) | expect object | JDK 调用门面 |
| `JsExtProvider` | `shared/commonMain` | 36 行 | `wrap(source): Any` 工厂 |
| `BookSourceJsExt` / `HttpTTSJsExt` | `shared/jvmAndAndroidMain` | **30 行** | 包装器 |

`BaseSource` 的继承关系从 `BaseSource : JsExtensions` 变成：

```kotlin
interface BaseSource : JsExtensionsCommon { ... }
```

### 2.1 `JsExtensionsPlatform` —— JDK 门面（expect object）

把 commonMain 不能碰的 JDK API 全部收进一个 `internal expect object`：

```kotlin
internal expect object JsExtensionsPlatform {
    fun urlEncode(str: String, charset: String): String
    fun strToBytes(str: String, charset: String): ByteArray
    fun bytesToStr(bytes: ByteArray, charset: String): String
    fun formatTimeUtc(time: Long, format: String, shiftHours: Int): String
    fun base64DecodeStr(str: String?, charset: String): String?
    fun chineseT2S(text: String): String
    fun chineseS2T(text: String): String
    fun sha256Hex(bytes: ByteArray): String
    fun isMainThread(): Boolean
    fun unsafeSslContext(): Any?      // ← 见下
}
```

**`unsafeSslContext(): Any?` 是本仓库 jsoup 平台岛的直接答案。**

本仓库 `AGENTS.md` / 项目记忆明确：书源 JS 边界的 `org.jsoup.Connection.Response`
原样返回给书源，桥靠反射调 `body()/code()/header()`，**jsoup 类型不抽象、不替换**，
否则存量书源静默全挂。

样本仓的解法不是去抽象 jsoup，而是把 jsoup 需要的 JVM 专属 SSL 上下文
**以 `Any?` 透传**：jvmAndAndroid actual 返回真实 `SSLContext`，native 端返回 `null`。
jsoup 类型一个都没动，commonMain 也不依赖 JVM 类型。

### 2.2 `JsExtProvider` —— `wrap` 返回 `Any` 是刻意的

```kotlin
object JsExtProviders {
    fun register(factory: JsExtFactory)
    fun get(): JsExtFactory = factory ?: error("JsExtFactory 未注册")
}

interface JsExtFactory {
    fun wrap(source: BaseSource): Any     // ← 不是 JsExtensions
}
```

注释写得很直白：

> 返回 [Any] 而非 JsExtensions，避免 shared jvmAndAndroidMain 引用 app-only 接口。
> 兜底：已是 JsExtensions 的对象（如 AnalyzeUrl/AnalyzeRule）直接返回原对象。

**这是类型擦除换依赖倒置**——JS 面接口留在 app/JVM 侧，shared 侧只见 `Any`。

### 2.3 包装器只有 30 行

```kotlin
@JsApi
class BookSourceJsExt(val source: BookSource) : BaseSource by source, JsExtensionsJvm {
    override fun getSource(): BaseSource? = source
    override fun log(msg: Any?): Any? = super<JsExtensionsJvm>.log(msg)
}
```

`BaseSource by source` 是**接口委托**，保证 `var` 属性（header / concurrentRate 等）
的 getter/setter 转发到原 `BookSource` 实例——JS 里 `source.setHeader(...)` 仍然改的是
数据库实体本身，不是包装器副本。

### 2.4 `evalJS` 只多一行

```kotlin
val jsBinding = JsExtProviders.get().wrap(this)
val bindings = buildScriptBindings { bindings ->
    bindings["java"] = jsBinding
    bindings["source"] = jsBinding
    ...
}
```

---

## 3. 本仓库现状

### 3.1 已有基础（比预想的好）

- **`modules/rhino` 已移植**：namespace `com.script`，`com.script.ScriptBindings` /
  `buildScriptBindings` / `com.script.rhino.RhinoScriptEngine` 已就位。
  `BaseSource` 用的已经是 `com.script.*` 封装层，**不是裸 `org.mozilla.javascript`**。
- **D4 PoC 已完成**：`smoke/rhino-capability-probe` 有 commonMain 契约
  （`RuleEngine.eval(script, bindings)`）+ androidMain/desktopMain 双 actual + 契约测试。
  已证明 **Rhino 是纯 JVM 库，两个 target 实现相同**。

### 3.2 缺什么

| 缺项 | 说明 |
|---|---|
| `JsEngine` 统一抽象面 | 业务代码直连 `com.script.rhino.RhinoScriptEngine`，无 `JsScope`/`JsBindings` 抽象 |
| `JsExtensions` 三件套拆分 | 现为 **1220 行单文件、144 个方法/注解** |

### 3.3 `JsExtensions` 的真实障碍面

`app/.../help/JsExtensions.kt` 依赖：

- Android：`android.webkit.JavascriptInterface`、`WebSettings`、`splitties.init.appCtx`
- UI 层：`OnLineImportActivity`、`OpenUrlConfirmActivity`（**Activity 直接依赖**）
- JVM 三方：okhttp、okio、jsoup、koin
- app 单例：`BackstageWebView`、`ReadBookConfig`、`ThemeConfigStore`、
  `CookieStore`、`CacheManager`、`SourceVerificationHelp`

### 3.4 改造面

- 直接引用 `com.script` / `org.mozilla.javascript` 的文件：**59 个**
- `@JavascriptInterface` 标注点：**119 处**（核查结论见 §3.5）

---

## 3.5 `@JavascriptInterface` 必要性核查

### 判据

`@JavascriptInterface` 只对 **`WebView.addJavascriptInterface`** 生效（API 17+ 起，
未标注的 public 方法不再暴露给页面 JS）。Rhino 桥是纯反射，**完全不看这个注解**。

所以判据只有一个：**该类型是否真的被注入到 WebView。**

### 注入点（全仓共 4 类对象）

| 注入对象 | 注入点 |
|---|---|
| `WebCacheManager` | `BackstageWebView:110`、`BookInfoScreen:1495`、`RssReadWebController:540`、`BottomWebViewDialog:524` |
| `source as BaseSource` | `BackstageWebView:113`、`BookInfoScreen:1497`、`RssReadWebController:539`、`BottomWebViewDialog:523` |
| `WebJsExtensions` | `BackstageWebView:114`、`BookInfoScreen:1498`、`RssReadWebController:538`、`BottomWebViewDialog:521` |
| 匿名对象 / `JSInterface` | `RssReadWebController:430/435`、`VisibleWebView:35`、`BottomWebViewDialog:515` |

### 继承链（决定注解的必要范围）

```
JsEncodeUtils (30)  ── interface
    └─ JsExtensions (44)  ── interface
           ├─ BaseSource (9)  ── interface  ← BookSource / RssSource / HttpTTS / BookSourcePart
           └─ RssJsExtensions (6)  ── open class
                  └─ WebJsExtensions (15)  ── class
```

独立被注入类型：`WebCacheManager`(9)、`JSInterface`(2)、匿名对象(3+1)。

### 结论

**119 处全部必要** —— 每一处都落在「被注入 WebView 的对象」的类型继承链或类本身上。
**不是历史遗留。** 但必要性**只来自 WebView 路径**，Rhino 路径零需求。

### 样本仓的处理：彻底移除 WebView 注入

样本仓全仓**没有** `addJavascriptInterface`，`nameSource` / `nameJava` / `nameCache`
常量也无残留；`createWebView()` 只设 `javaScriptEnabled = true`，JS 执行走
`evaluateJavascript`（页面内 JS，不注入宿主对象）。

**本仓库不应照搬** —— 书源 `webView` 规则依赖 `source.getVariable()` /
`java.xxx()` / `cache.xxx()` 在页面内调用，移除即丢书源能力。

### 对 P4 范围的影响（关键）

注解必须留在 **app 侧**，这与「JS 面外置 + 包装器」方案天然吻合：

| 类型 | 处数 | P4 是否需要动 | 理由 |
|---|---|---|---|
| `BaseSource` | 9 | **需要** | 实体要下沉，Android 注解不能进 commonMain |
| `JsExtensions` | 44 | 否 | 留在 app 侧 |
| `JsEncodeUtils` | 30 | 否 | 同上 |
| `WebJsExtensions` | 15 | 否 | 同上 |
| `RssJsExtensions` | 6 | 否 | 同上（本就是 UI 侧类） |
| `WebCacheManager` | 9 | 否 | 独立 object，不依赖实体 |
| `JSInterface` / 匿名对象 | 6 | 否 | 同上 |

**P4 真正需要处理的注解只有 `BaseSource` 的 9 处**，其余 110 处原样不动。

这 9 个方法是：`login` / `getLoginHeader` / `getLoginInfo` / `putLoginInfo` /
`removeLoginInfo` / `putVariable` / `getVariable` / `put` / `get`。

**解法**：4 个 WebView 注入点从注入实体改为注入**带注解的包装器**
（`JsExtProviders.get().wrap(source)`），注解随之留在包装器上。
这与 Rhino 侧用同一个 `wrap()`，即 §2.4 的写法，无额外成本。

---

## 4. 哪些可以不学（重要）

样本仓要服务 iOS + 鸿蒙，本仓库只有 **android + desktop 两个 JVM target**。
以下都是它为了 K/N 无反射 / native 平台才付出的成本，本仓库**不需要**：

| 样本仓的东西 | 本仓库是否需要 | 原因 |
|---|---|---|
| QuickJS 引擎 | **否** | 换引擎高行为风险，AGENTS.md 已定不换 |
| 双引擎 `JsEngineType` 切换 | 否 | 只有 Rhino |
| KSP `JsApiDispatcher` 静态分派表 | 否 | JVM 有反射，无需编译期分派表 |
| `@JsApi` + `quickjs-processor` | 否 | 同上 |
| iOS / 鸿蒙 actual（`sha256Hex` 走 mbedTLS 等） | 否 | 只有 JVM target |
| `unsafeSslContext()` 的 native null 分支 | 否 | 只有 JVM，恒返回真实 SSLContext |

**结论：本仓库的 P4 只需要「JsExtensions 三件套拆分 + 包装器」这一刀**，
外加一个极小的 `JsEngine` 门面（可以比样本仓小一个数量级）。

---

## 5. 建议落地路径

按依赖顺序切，每步都保证编译 + 门禁绿：

**P4-a：`JsEngine` 最小门面**（`core:platform`）— ✅ 已完成
- 契约在 `core/platform/src/commonMain/.../JsEngine.kt`：`expect object JsEngine`
  + `JsBindings`（纯 Map）+ `JsScope`（`native: Any?` 透传原生 scope）
- 三个能力：`eval(js, bindings)` / `eval(js, scope)` / `getRuntimeScope(bindings, parent)`
- actual：androidMain 委托 `com.script.rhino.RhinoScriptEngine`；
  desktopMain 委托裸 `org.mozilla.javascript`（com.script 是 android library，desktop 无法依赖）
- 契约测试 9 个，android（hostTest）与 desktop 两端各 9/9 通过
- **暂未接入调用方**（`BaseSource.evalJS` / `SharedJsScope` 仍直连 Rhino），见下方待办

**P4-b：`JsExtensionsPlatform` expect object**
- 照抄样本仓 9 个方法的门面，把 `JsExtensions` 里的 JDK 调用收进去
- android/desktop actual 都委托原 JDK 实现（两target 代码相同）

**P4-c：`JsExtensions` 拆 `Common` / `Platform`**
- `JsExtensionsCommon`：纯规则 + 加解密 + 字符串处理部分
- 平台部分（WebView / Activity / Context）留在 app 侧

**P4-d：`JsExtProvider` + 包装器**
- `wrap(source): Any` 工厂，app 侧 `App.onCreate` 注册
- `BookSourceJsExt` / `RssSourceJsExt` / `HttpTTSJsExt`（各约 30 行）

**P4-e：实体下沉**
- `BaseSource` / `BookSource` / `RssSource` / `BookSourcePart` / `HttpTTS` + 3 DAO

风险点：119 处 `@JavascriptInterface` 的语义在 Android 上只是「暴露给 WebView」，
对 Rhino 桥**无实际作用**（Rhino 靠反射）。拆分时应确认哪些真正需要保留。

---

### 3.6 P4-a 遗留待办

契约已立且两端验证通过，但**尚未接入生产调用方**，接入时有两处需要扩展：

1. **`coroutineContext`**：`SharedJsScope` 用的是
   `RhinoScriptEngine.eval(js, scope, coroutineContext)`——协程取消由
   `com.script` 的 `RhinoContext.observeInstructionCount` 实现，用于打断书源死循环。
   当前契约没有该参数。desktop 裸 Rhino 无此能力，接入时需决定：
   把协程取消做成平台能力（desktop no-op），还是契约不承载、由 app 侧持有。
2. **`preventExtensions()` / scope 缓存**：`SharedJsScope` 持有
   `WeakReference<Scriptable>` 并调用 `scope.preventExtensions()`（阻止隐式全局变量）。
   这是 Rhino 独有语义，需走 `JsScope.native` escape（契约已为此预留）。

## 6. 待确认

- `SharedJsScope` / `getShareScope()` 现驻 app 侧，`BaseSource.evalJS` 依赖它 ——
  需在 P4-a 一并规划下沉或抽象。
- `HttpTTS` 额外卡 `com.jayway.jsonpath`（纯 JVM 库），需单独的 JsonPath 契约或门面。
- ~~119 处 `@JavascriptInterface` 的必要性~~ —— 已核查完毕，见 §3.5：
  全部必要（仅服务 WebView 路径），但 P4 只需处理 `BaseSource` 的 9 处，
  办法是 4 个注入点改注入带注解的包装器。
- 4 个 WebView 注入点改造前，需确认 `source as BaseSource` 的强制转换在改为
  注入包装器后，`WebJsExtensions` 内部持有的 `sourceRef` 语义是否仍成立
  （包装器 `by source` 委托，属性读写转发回原实体，理论上一致，需实测验证）。
