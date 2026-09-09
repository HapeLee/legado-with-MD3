# Feature catalog（初始提案）

> 本表用于统一 owner 与 canonical name，不表示模块已经存在。每个条目在首次迁移前仍需做调用方和依赖审计。

| 当前主要位置                                   | Canonical owner             | 目标类型                          | 备注                                                                           |
|------------------------------------------|-----------------------------|-------------------------------|------------------------------------------------------------------------------|
| `ui/main/MainActivity*`、全局 route graph   | `app/android`               | app host                      | 保留导航、DI、launcher 与全局 shell 聚合，不建 `feature-main`                              |
| `ui/main/bookshelf`                      | `feature/bookshelf`         | Feature                       | 书架列表、分组入口和选择状态；书籍数据仍经 core data/domain                                       |
| `ui/main/home`、`ui/main/homepage`        | `feature/home`              | Feature                       | 先确认两者职责后合并 canonical name，禁止继续新增第三套首页包                                       |
| `ui/main/explore`、`ui/book/explore`      | `feature/explore`           | Feature                       | 区分入口容器与探索结果子流程，避免两个独立 impl 互相依赖                                              |
| `ui/book/info`                           | `feature/book-info`         | Feature                       | 适合作为中型样板候选；兼容 Activity 可暂留 legacy bridge                                     |
| `ui/book/search`、`ui/book/searchContent` | `feature/book-search`       | Feature                       | 全局搜书与正文搜索可先作为同域子流程，稳定后再评估拆分                                                  |
| `ui/book/toc`                            | `feature/table-of-contents` | Feature                       | TXT 目录规则作为子流程，不直接下沉为公共 widget                                                |
| `ui/book/bookmark`                       | `feature/bookmarks`         | Feature                       | 与阅读入口通过 route/result contract 协作                                             |
| `ui/book/group`、`ui/book/manage`         | `feature/book-management`   | Feature                       | 先收拢管理动作，再决定书籍分组是否形成独立契约                                                      |
| `ui/book/import`、`ui/file` 中导入流程         | `feature/book-import`       | Feature + platform adapter    | 文件选择/URI 留 Android adapter，导入状态与规则归 Feature/domain                           |
| `ui/book/cache`                          | `feature/book-cache`        | Feature                       | 缓存任务执行由 domain/service 能力提供                                                  |
| `ui/book/changecover`                    | `feature/change-cover`      | Feature                       | 图片选择与裁剪由 host/platform effect 处理                                             |
| `ui/book/changesource`、`ui/book/source`  | `feature/book-source`       | Feature                       | source edit/manage/debug 可作为同域子包，避免交叉 impl 依赖                                |
| `ui/book/readRecord`                     | `feature/reading-history`   | Feature                       | 新包统一全小写；不要延续 camelCase package                                               |
| `ui/book/readaloud`                      | `feature/read-aloud`        | Feature + platform adapter    | 控制 UI 可 Feature 化，TTS/service/media 生命周期留 platform                           |
| `ui/book/audio`                          | `feature/audio-playback`    | Feature + platform adapter    | 播放服务、媒体会话和通知留 Android platform                                               |
| `ui/book/read`                           | `platform/android/reader`   | platform island               | 当前不作为普通 Feature 搬迁；共享状态/模型另行立项                                               |
| `ui/book/manga`                          | `feature/manga`             | Feature + renderer capability | 图片渲染、缓存和手势需专项边界审计                                                            |
| `ui/config/*`                            | `feature/settings`          | Feature domain                | 先保留 settings 下的 `appearance`、`reading`、`backup`、`advanced` 等 section，不为每页建模块 |
| `ui/theme`                               | `core/designsystem`         | core candidate                | theme engine、token 与基础主题；Feature 专属样式不进入 core                                |
| `ui/widget/components`                   | owner audit                 | core/ui candidate             | 有至少两个 Feature 调用且职责稳定的组件才进入 `core/ui` 或 `core/designsystem`                  |
| `ui/widget` 其余 View-era 控件               | legacy/platform owner       | migration zone                | Recycler/View/Dialog 控件按实际调用方迁走，禁止整目录改名为 core                                |
| `ui/rss/*`、`ui/main/rss`                 | `feature/rss`               | Feature domain                | 初期一个业务域，内部 `feed`、`article`、`source`、`subscription` 子包                       |
| `feature/replacerules/{,edit}`（原 `ui/replace`） | `feature/replace-rules` | Feature domain | **Stage B 完成（2026-09-09）**：`:feature:replacerules`（Android library + Compose），7 文件（ReplaceRuleScreen/Contract/VM + edit/…）+ `ReplaceRuleRepository`（下沉 `core:data/commonMain`）+ 52 R.string×4 语言。`io.legado.app.feature.replacerules[.edit]`。`:app` 仅剩 DI（`appModule`）、host 导航（`MainNavKey`/`MainNavGraph`）、调用点。原 catalog 记的「reader/toc/my 直接 import `ReplaceEditRoute`」已收口为 host 导航契约；`MainRouteReplaceRule` 是 route，非跨 Feature import。验证：模块 `compileDebugKotlin`（Feature 模块不跑 lint，对齐 tagrules） |
| `ui/dict`、`ui/dict/rule` | `feature/dict` | Feature + platform adapter | **拆两域 + ①已模块化（2026-09-09）**：① `rule` 子域 = 词典规则管理，**`:feature:dict` 已建**：`DictRuleScreen`（含 `DictRuleRouteScreen`）/`DictRuleContract`/`DictRuleViewModel` 迁入 `io.legado.app.feature.dict.rule`（3 文件 + 19 R.string×4 语言），结构逐文件同构 tagrules 的 HighlightTagRule 页；`DictRuleActivity` 因依赖 app 的 `BaseComposeActivity` **留 `:app` 作薄宿主**（仅 import 模块 `DictRuleRouteScreen`），`MyScreen` 入口与 Manifest 不变。② `dict` 主包 = 词典查询弹窗面板（DictActivity/DictSheet/DictViewModel），其 `DictRule.search` 走 app 独有 `DictRuleAndroid.kt`（依赖 `model.analyzeRule.AnalyzeRule`/`AnalyzeUrl`）→ **platform island，不迁**，待 search 抽平台注入契约。`ExportSelection.uri` 已去 `android.net.Uri`（→String）。验证：`:feature:dict:compileDebugKotlin` + `:app:compileAppDebugKotlin` 通过（模块不跑 lint，对齐 tagrules）。剩余：`ImportDictRuleViewModel`（`ui/association`）随依赖评估是否入模块 |
| `feature/tagrules/{highlight,group}`（原 `ui/highlightTagRule`、`ui/tagGroupRule`） | `feature/tag-rules` | Feature domain | **Stage B 完成（2026-09-08）**：`:feature:tagrules`（Android library + Compose），7/7 文件迁入（含 `highlight/HighlightTagRuleScreen`），`ImportComponents`（Gson JSON 树）入 `:core:ui`。Contract 已去平台化（Uri→String），规则 VM 注入 Repository，删除零引用类型 `TagGroupRuleEffect`/两个 `*RenderState`；`android.net.Uri.parse`/`context.toastOnUi` 已随 `BaseRuleViewModel` 去平台交互契约化（`:core:platform` 的 `RuleTransferPlatform`/`ToasterProvider`）清零。`:app` 仅剩 DI、host 导航、调用点 |
| `ui/ai/chat`                             | `feature/ai-chat`           | Feature                       | AI provider/config 归 settings 或 data/platform，不放 Screen                      |
| `ui/login`                               | `feature/source-login`      | Feature + host effect         | WebView/cookie/platform auth 通过 adapter                                      |
| `ui/about`                               | `feature/about`             | Feature                       | 边界小，适合首个 package relocation 样板候选                                             |
| `ui/welcome`                             | `feature/onboarding`        | Feature                       | 首次启动状态经 settings/domain contract                                             |
| `ui/qrcode`                              | `feature/qr-scan`           | Feature + Android capability  | Camera/permission/result 留 host/platform                                     |
| `ui/association`                         | `feature/file-association`  | Feature + Android capability  | 外部 Intent/URI 是兼容入口，不进入共享 contract                                           |
| `ui/browser`                             | `platform/android/browser`  | platform capability           | 若出现独立用户流程再由 Feature 包装，不把 WebView 放 core UI                                  |

## Catalog 使用规则

- 一项只能有一个 canonical owner；子流程通过子包表达，不复制完整 Feature 栈。
- 发现当前包横跨多个 owner 时，先画依赖和入口，不直接移动。
- owner 变更必须同步本表、route/DI 聚合位置和迁移 PR 的回滚说明。
- 新 Feature 名先登记再落文件，避免同义目录继续增长。
- Feature 提升为 Gradle module 或 KMP module 后，在备注中记录真实 module path、source sets 与验证
  task。
