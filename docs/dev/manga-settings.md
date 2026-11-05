# 漫画全局设置

入口为“设置 → 漫画设置”，与阅读界面设置并列。Navigation 3 key 为 `MainRouteSettingsManga`，
普通设置页参与宽窗口双栏，并可通过 `settings/manga` 恢复入口。页面 owner 为
`feature/settings/manga`；现有漫画阅读器保留快捷设置，不移动成熟渲染、手势或会话代码。
`MainRouteSettingsManga.openTranslation` 可定位到同页的“漫画翻译”分组，等待参数就绪后只定位一次；
滚动位置和已定位状态随页面重建恢复，不另建翻译设置目的地。

## 配置范围

- 漫画浏览开关从“其他设置”移至此页，仍使用已有 `showMangaUi` 偏好键，不另存一份。
- 阅读布局：默认阅读方式、默认条漫侧边留白，以及全局缩放、宽页、双页和章节首尾提示。
- 阅读操作：捏合缩放、点击翻页、图片长按菜单、音量键／反向音量键和自动阅读速度。
- 图片显示：翻页动画、图片淡入、自动／固定背景、灰度、墨水屏及阈值。
- 预加载与缓存：当前章原图预加载、自动离线缓存及前后章节数；提供下载与缓存设置入口。
- 漫画翻译：直接编辑自动翻译总开关、全局翻译选项，并提供 AI 供应商／模型入口。

设置项与“阅读界面”统一使用 `SwitchSettingItem`、`DropdownListSettingItem`、`SliderSettingItem`、
`ClickableSettingItem` 和 `SplicedColumnGroup`，不用 Tiny 系列。滑块沿用正式设置组件的松手提交、
输入数值和恢复默认值交互。
翻译总开关与供应商入口同组；翻译设置、识别与定位、擦字与排版、高级设置分别使用并列的
`SplicedColumnGroup`，不放进外层大卡片，也不用普通文字代替分组标题。下载状态等自定义内容
通过 `SettingItemWithDivider` 参与所属组的分隔。阅读器中的本书配置、术语表和整章操作也分别归组。
页面及翻译控件的说明、状态、按钮文字和输入框标签统一使用 `AppText`，字体样式直接引用
`LegadoTheme.typography` 令牌；输入值同样通过 textStyle 引用令牌，不单独指定字号、字重或字体。
按钮和输入装饰的文字颜色保留组件提供的内容色，以适配启用、禁用及聚焦状态。

“漫画翻译”分组中的选项由漫画翻译 Feature 提供，宿主通过内容槽组装到漫画设置页面；语言、文字／识图／图片编辑模型、
思考强度、定位、OCR 参数、擦字与合成、字号等可以全局配置。OCR／擦字模型下载及云端连接
复用已有全局服务。每本漫画默认继承，可在阅读器中关闭“使用全局翻译设置”单独配置。
详见[翻译设置与持久化](manga-translation-settings.md)。

阅读方式和条漫留白有按书覆盖，新入口只修改缺省值，不覆盖书籍 ReadConfig。
章节预缓存数量继续通过 `ReadSettingsGateway` 修改，与正文共用；不使用已不参与当前会话的
`MangaSettings.chapterPrefetchCount` 作为第二来源。全局漫画参数使用 `MangaSettingsGateway.update`，
全局翻译配置使用 `MangaTranslationGateway.updateGlobal`。本书覆盖、本书开关、术语和单页操作
归阅读器；详细页脚、菜单配色、点按区域等仍可在阅读器快捷面板调整。

灰度和墨水屏互斥，切换时在同一次 `copy` 中提交；滑块只在松手／步进完成后写入，拖动值留在 UI。
页面收集各 Gateway 的 Flow，阅读器和设置页共享同一配置来源。分组没有 bookUrl，也不会读取
书籍锚点、创建空书配置或发起翻译请求。错误保存在 UiState 中，取消不当作写入失败。

## 验证范围

使用 JDK 21，执行 `:app:testAppDebugUnitTest :app:assembleAppDebug :app:compileAppDebugAndroidTestKotlin verifyConfigArchitecture`。
定向测试覆盖漫画设置页、内联翻译分组及 ViewModel、设置入口、Navigation 3 注册／恢复／双栏返回。
页面验证紧凑、展开、横屏窗口、重建、异步加载后定位翻译分组、条件选项与上下游入口；
配置验证失败／取消、全局开关联动、共享章节数与无书籍／翻译调用。
全局参数和译图缓存实现继续由翻译 Repository 契约测试覆盖；本次分组合并未修改数据层。
布局与导航合并的日志和 XML 归档位于忽略目录 `build/reports/manga-translation/settings-ux/inline-translation-group*`。
卡片分组调整通过 31 项／3 类界面回归、Kotlin 编译、Debug 打包、架构门禁和差异检查；
覆盖手机／宽屏／横屏，以及自定义分隔线、边框和圆角。日志及 XML 归档为同目录下的
`spliced-setting-groups*`。检查范围内没有直接 Text 调用或手写字号／字重。

本轮不操作真机 UI、不调用线上 AI；真实设备的系统返回手势、软键盘与视觉效果尚未验证。
