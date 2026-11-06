# 漫画原图导出

漫画导出复用书架管理的导出入口、`ExportBookService` 队列和进度消息，支持 `core` 与 `comic`。
漫画格式单独保存为 `mangaExportFormat`，默认 CBZ；小说继续使用原有 TXT/EPUB 设置。
混合选择时按书籍类型分流，漫画不会进入自定义 EPUB 分包或文本翻译导出流程。

## 当前范围

- 整本导出已缓存原图，也支持本地图片目录和现有本地加载器支持的 CBZ/ZIP 来源。
- 文件夹结构为 `书名/0001_章节名/0001.png`；CBZ 中使用相同相对路径。
- 章节按目录顺序，图片按正文中出现的顺序；重复 URL 保留每一次出现，不按缓存哈希排序。
- 保留编码后的原始字节，不转码、不截屏、不重排长条图片。根据文件头选择 JPG/PNG/WebP/GIF/AVIF/BMP 后缀。
- 预检全部章节和页面，缺失正文、空的非卷标题章节、缺失/无效/不支持的图片均阻止导出。
  预检不联网；失败消息包含章节名以及适用时的页码。
- 文件名清理路径分隔符、控制字符等，同名自动编号。目标采用新建方式，不复用已有文件。
- 文件夹与 CBZ 按页流式复制；在线缓存原图流持有租约，关闭后释放。
- 支持普通目录和 SAF 文档树。失败或主动取消会尝试删除本次新建的目标，保留此前导出的内容。

当前不提供选择章节、跳过缺页、边下载边导出、译图导出、ComicInfo.xml、WebDAV 上传或进程死亡后的续传。
进程被强制终止时无法保证清理未完成的目标；SAF provider 拒绝删除时也可能留下部分输出。
这些情况不能把未完成文件视为成功导出。

## 职责

- `feature/bookexport/BookExportOptions`：Compose 可用的稳定选项，负责文本与漫画格式选择。
  现有管理页继续持有入口、Intent 分发和系统文件选择；本次没有整体迁移管理页。
- `BookExportSettingsGateway`：通过原有原子 `update { copy(...) }` 持久化独立漫画格式。
- `MangaExportGateway` / `MangaExportRepository`：读取章节、组装页面、预检与管理输出生命周期。
  契约仅传递书籍标识、目标位置字符串、格式和进度，不暴露 Android/Room/stream 类型。
- `MangaExportWriter`：文件夹与 CBZ 共用页序、路径和字节复制规则。
- `BookHelp.openCachedImage`：只获取已有原图及其租约，不下载、不改变缓存用途索引。
- `LocalMangaLoader`：保持每个图片子目录的章节边界，包括所有章节均只有一页的情况。

## 验证与待验收

测试覆盖两个格式的相同页序与原始字节、重复 URL、真实编码后缀、同名保留、危险路径字符、
缺页预检、写入失败关闭流、取消清理、缓存租约和单页章节回读。
CBZ 内容通过 JDK ZIP 读取校验；本地目录通过 `LocalMangaLoader` 回读。

2026-11-05 使用 JDK 21 执行以下验证，通过两个产品的 Debug 打包、架构门禁及 46 项定向测试
（0 失败、0 错误、0 跳过；包含 `BookHelpImageFileTest` 的 SDK 26/35 两组）：

```powershell
.\gradlew.bat :app:assembleAppCoreDebug :app:assembleAppComicDebug :app:testAppCoreDebugUnitTest `
  --tests '*MangaExport*Test' --tests '*LocalMangaLoaderTest' `
  --tests '*BookshelfManageScreenExportConfigTest' --tests '*Phase2SettingsMappingTest' `
  --tests '*BookHelpImageFileTest' --continue --console=plain --no-configuration-cache
git diff --check
```

单页章节回归先复现了旧加载器将两个目录合成一个章节的行为，修正分组后同一测试通过。
本次未执行全量单元测试、lint 或 Release/R8 构建。

需另行真机验收：系统目录选择与授权撤销、不同 SAF provider 的创建/删除与空间不足、
导出途中退出阅读器或清理缓存、第三方 CBZ 阅读器、原生 libarchive 回读、
超长图片/超大书籍的耗时与内存，以及 TalkBack 操作新增格式选项。
