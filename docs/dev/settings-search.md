# 设置项搜索

搜索从设置首页右上角进入，覆盖阅读、外观、高级、漫画、朗读、封面、气泡、下载缓存、备份、
AI、翻译、隐私、实验室，以及 AI 摘要、提示词、自定义主题、主题包，共 17 个分类入口。
目录包含 234 个固定设置项／入口，加上分类共 251 条结果。结果展示标题、分组路径和静态说明；
点击后进入原页面并定位，背景柔和高亮两次后消失，返回保留关键词与列表位置。

索引不包含用户创建的主题包、气泡包、AI 服务商和模型名称，不读取用户填写的实际值。
需要先选择具体记录的编辑表单、弹窗内部字段、动态提示词条目和未实现的占位动作不作为独立结果。

## 职责

- `feature/settings/reading/ReadSetting` 是条目 ID、标题资源、分组、静态说明和同义词的 owner。
  ID 与偏好存储 key 分离；原页面使用同一标题资源，避免搜索文案与页面各自维护。
- `SettingsSearchCatalog` 维护其他分类的固定条目、分组、稳定 ID 和条件项的前置入口，复用原页面
  的标题和静态说明资源。分类与条目都参与匹配，外观和高级另有主题、通用等同义词。
- `feature/settings/search` 持有搜索 Contract、ViewModel、Route 和 Screen。查询经
  `SavedStateHandle` 恢复，结果以不可变集合暴露。Route 按当前语言解析资源，语言改变时替换目录。
- 匹配为本地内存匹配；空查询不列出全部条目。所有空白分隔的关键词都须命中，标题优先于同义词、
  说明和路径；同分保留目录顺序，不使用网络、DAO 或偏好读取。
- `ui/config/readConfig` 仍持有现有页面、ViewModel 和设置行为。本次只将原单个 Lazy item
  拆为三个带稳定 key 的分组。其他页面使用 `SearchableSettingsList`／`SearchableSetting` 添加定位，
  原控件参数、回调和业务行为保留。各页共用 `SettingSearchTarget` 的滚动和两次背景高亮提示。
  未搬迁页面或改变持久化、Gateway、Intent、弹窗业务。
- `MainNavGraph` 组装搜索与目标页，复用来源感知的 `pushRoute`；双栏选中其他结果时替换右侧详情。
  阅读结果沿用 `MainRouteSettingsReadTarget(settingId)`，其他结果走 `MainRouteSettingsTarget(settingId)`。
  普通入口和搜索入口共用宿主的 `SettingsDestinationContent` 组装，包含同一个隐私验证 Gate，不能
  通过搜索绕过验证。原 object 路由、漫画翻译快捷定位和旧 Intent 入口保留。

## 定位与条件项

定位先滚动到条目所属分组，再请求组内条目进入可视区；预留 top bar 遮挡区域。完成后记录本次定位
已处理，避免重建页面后再次把用户拉回初始位置。定位容器不接管控件点击，也不改变开关语义，
Material 和 Miuix 共用此容器。其他页面先按目录分组让目标进入组合，再用真实注册的锚点定位。
目标因开关或引擎隐藏时沿目录前置关系查找可见入口，显示具体提示，不自动改变选项。当前配置
无可到达入口时回到页面顶部并明确提示不可用。气泡创建入口按列表末尾定位，不依赖用户包数量。

“亮度条位置”仅在“竖排”亮度条模式下显示。搜索目录保留该项并标注条件；不可见时定位到亮度条模式，
显示操作提示，不自动写设置。条件文案复用现有模式名称资源。

## 扩展约定

新增可搜索条目时同时定义稳定 ID、复用展示资源和真实定位目标。为新 section 接入目录与宿主映射即可；
业务动作留在原页面，不用搜索元数据生成设置业务或修改设置值。新增条件项必须定义可到达的前置入口，
不能制造搜索命中后无处可去的结果。

不为这一步增加 Gradle 模块、数据库或设置生成 DSL。保留旧 XML，不把其当成当前 Compose 页面目录。

## 架构复核与后续演进

目前保留“呈现元数据／纯匹配 → 宿主导航与身份验证 → 原页面／ViewModel／Gateway”的职责边界。
搜索目录描述可发现性与位置，不承担设置读写或业务动作。普通入口、搜索入口和漫画翻译快捷入口
共用宿主的 `SettingsDestinationContent`，不同输入通过参数表达，避免重复维护 DI 和导航回调。

后续按独立切片优化，优先级如下：

1. 以稳定的分组 key 与当前可见分组映射替代 `groupIndex` 手写下标。页面重排、条件分支和动态列表
   不应要求维护另一套位置数字；先在外观或高级页验证，再逐页接入。
2. 按分类共置条目 ID、标题、说明和条件入口，`SettingsSearchCatalog` 只聚合目录。页面和搜索复用
   同一份描述，减少字符串 ID 和资源引用的双处维护。不要同时让描述承担业务回调。
3. 阅读设置逐步接入同一定位契约，保留旧路由反序列化兼容；目录资源仍属 Android 呈现层，
   不为目录整齐提前拆 Gradle 模块、引入设置生成 DSL 或迁入 `commonMain`。

这些属于后续结构演进，本次只落实宿主组装去重和目录／锚点一致性门禁。

## 验证

`ReadConfigSearchTest` 在整理前建立开关 Intent、配置入口和条件项行为基线；整理后继续运行相同行为，
并覆盖阅读目录所有条目的真实控件、跨分组定位、条件项引导、重建恢复、两种 UI 引擎和宽窗口。
`ExtendedSettingsSearchTest` 覆盖中英文的全部分类与目录、前置关系完整性、外观字体和高级语言的
搜索到达／返回／重建，以及隐藏分隔线设置和实验室开关的引导；搜索到清理入口时不执行清理。
`SettingsSearchCatalogIntegrityTest` 双向检查固定目录与页面锚点，阻止无目标的搜索项和缺少目录的页面锚点。
`SettingsSearchViewModelTest` 覆盖排序、多关键词、中英文、空查询、语言目录替换和查询恢复；
`SettingsSearchScreenTest` 覆盖搜索到定位再返回、清空、空结果和界面状态恢复；
`SettingsSearchNavigationTest` 覆盖返回栈、双栏详情替换和新旧路由序列化。

执行命令与实际结果以本次交付说明为准。Robolectric 不能替代真实 IME、TalkBack 和预测返回手势实测。

定向复验命令（PowerShell，JDK 21；本地验证采用串行编译，避免双变体同时编译占满 Kotlin 堆）：

```powershell
$settingsSearchFilters = @(
    '--tests', 'io.legado.app.feature.settings.reading.*',
    '--tests', 'io.legado.app.feature.settings.search.*',
    '--tests', 'io.legado.app.ui.main.SettingsSearchNavigationTest',
    '--tests', 'io.legado.app.ui.main.MainNavRouteRegistryTest',
    '--tests', 'io.legado.app.feature.settings.manga.MangaConfigScreenTest'
)
.\gradlew.bat :app:testAppCoreDebugUnitTest @settingsSearchFilters :app:testAppComicDebugUnitTest @settingsSearchFilters verifyConfigArchitecture --max-workers=1 '-Pkotlin.daemon.jvmargs=-Xmx4g' --no-configuration-cache
.\gradlew.bat :app:assembleAppCoreDebug :app:assembleAppComicDebug --max-workers=1 '-Pkotlin.daemon.jvmargs=-Xmx4g' --no-configuration-cache
.\gradlew.bat :app:lintAppCoreDebug :app:lintAppComicDebug --max-workers=1 '-Pkotlin.daemon.jvmargs=-Xmx4g' --no-configuration-cache
git diff --check
```
