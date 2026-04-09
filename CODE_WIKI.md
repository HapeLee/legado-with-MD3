# Legado with MD3 - Code Wiki

## 项目概述

**Legado with MD3** 是基于开源项目 [阅读 (Legado)](https://github.com/gedoor/legado) 开发的 Material Design 3 风格重构版本。本项目在对 UI 进行重绘的基础上，加入了多项分支独有功能，并正在逐步从传统 View 迁移至 Jetpack Compose 框架，目标是提供更加现代、流畅且一致的阅读体验。

### 主要特性

- **全新主题**：全新 Material Design 3 设计界面，支持预测性返回手势与共享元素动画
- **阅读界面**：更加个性化的阅读界面与菜单配置
- **阅读记录**：提供详尽的阅读记录，支持时间轴与章节维度统计
- **体验增强**：更健全的漫画阅读、有声书与发现等界面体验
- **书架布局**：更多的书架布局选择，针对平板端进行了专门的界面优化
- **实用功能**：新增书籍备注、智能伴生分组（自动归类已读/未读），支持手柄上下翻页

## 项目架构

### 整体架构

Legado with MD3 采用典型的 Android 应用架构，主要分为以下几个核心模块：

1. **数据层**：负责数据的存储和获取，包括数据库操作、网络请求等
2. **业务逻辑层**：处理核心业务逻辑，如书籍阅读、内容解析等
3. **UI层**：负责界面展示，包括传统 View 系统和正在迁移的 Jetpack Compose 组件
4. **服务层**：提供后台服务，如下载、朗读等

### 目录结构

```
app/src/main/java/io/legado/app/
├── api/            # API 接口相关
├── base/           # 基础组件和工具类
├── constant/       # 常量定义
├── data/           # 数据层，包括数据库实体和 DAO
├── di/             # 依赖注入
├── exception/      # 异常处理
├── help/           # 工具类和辅助方法
├── lib/            # 第三方库和自定义组件
├── model/          # 业务逻辑模型
├── receiver/       # 广播接收器
├── service/        # 服务
├── ui/             # UI 界面
└── App.kt          # 应用入口
```

## 核心模块

### 1. 数据层

数据层负责数据的存储和获取，主要包括：

#### 数据库

- **AppDatabase**：应用数据库的核心类，定义了所有数据表和 DAO
- **DatabaseMigrations**：数据库迁移管理

#### 实体类

- **Book**：书籍实体，包含书籍的基本信息和阅读状态
- **BookChapter**：章节实体，存储书籍章节信息
- **BookSource**：书源实体，存储网络书源信息
- **RssSource**：RSS 源实体，存储 RSS 订阅信息

#### 仓库类

- **BookRepository**：书籍相关的数据操作
- **ExploreRepository**：发现页面的数据操作
- **ReadRecordRepository**：阅读记录的数据操作
- **RssRepository**：RSS 相关的数据操作

### 2. 业务逻辑层

业务逻辑层处理核心业务逻辑，主要包括：

#### 阅读核心

- **ReadBook**：阅读功能的核心类，管理阅读状态、章节加载、页面跳转等
- **BookHelp**：书籍相关的辅助方法，如内容获取、章节处理等
- **ContentProcessor**：内容处理器，负责处理书籍内容的解析和转换

#### 网络书源

- **WebBook**：网络书源的核心类，负责从网络获取书籍信息和内容
- **SourceHelp**：书源相关的辅助方法

#### 本地书籍

- **LocalBook**：本地书籍的核心类
- **TextFile**：文本文件处理
- **EpubFile**：EPUB 文件处理
- **MobiFile**：MOBI 文件处理

### 3. UI 层

UI 层负责界面展示，主要包括：

#### 主界面

- **MainActivity**：应用主界面
- **BookshelfFragment**：书架界面
- **ExploreFragment**：发现界面
- **RssFragment**：RSS 订阅界面
- **MyFragment**：我的界面

#### 阅读界面

- **ReadBookActivity**：阅读界面
- **ReadBookViewModel**：阅读界面的 ViewModel
- **ReadMenu**：阅读菜单

#### 其他界面

- **BookInfoActivity**：书籍详情界面
- **BookSourceActivity**：书源管理界面
- **SearchActivity**：搜索界面
- **ConfigActivity**：设置界面

### 4. 服务层

服务层提供后台服务，主要包括：

- **DownloadService**：下载服务
- **CacheBookService**：书籍缓存服务
- **AudioPlayService**：音频播放服务
- **WebService**：Web 服务

## 核心类与函数

### 1. App.kt

应用的入口类，负责初始化应用环境、配置主题、创建通知渠道等。

**主要功能**：
- 初始化 Koin 依赖注入框架
- 配置 Material Design 3 主题和动态取色
- 初始化各种服务和工具类
- 创建通知渠道

**关键方法**：
- `onCreate()`：应用启动时的初始化方法
- `createNotificationChannels()`：创建通知渠道
- `initRhino()`：初始化 Rhino 脚本引擎

### 2. Book.kt

书籍实体类，存储书籍的基本信息和阅读状态。

**主要属性**：
- `bookUrl`：书籍详情页 URL（本地书源存储完整文件路径）
- `name`：书籍名称
- `author`：作者名称
- `coverUrl`：封面 URL
- `intro`：简介内容
- `type`：书籍类型
- `durChapterIndex`：当前章节索引
- `durChapterPos`：当前阅读位置

**关键方法**：
- `getUnreadChapterNum()`：获取未读章节数
- `getDisplayCover()`：获取显示封面
- `getDisplayIntro()`：获取显示简介
- `save()`：保存书籍信息
- `delete()`：删除书籍

### 3. ReadBook.kt

阅读功能的核心类，管理阅读状态、章节加载、页面跳转等。

**主要属性**：
- `book`：当前阅读的书籍
- `durChapterIndex`：当前章节索引
- `durChapterPos`：当前阅读位置
- `curTextChapter`：当前章节内容
- `nextTextChapter`：下一章节内容
- `prevTextChapter`：上一章节内容

**关键方法**：
- `resetData()`：重置阅读数据
- `loadContent()`：加载章节内容
- `moveToNextPage()`：移动到下一页
- `moveToPrevPage()`：移动到上一页
- `moveToNextChapter()`：移动到下一章
- `moveToPrevChapter()`：移动到上一章
- `saveRead()`：保存阅读进度

### 4. BookHelp.kt

书籍相关的辅助方法，如内容获取、章节处理等。

**关键方法**：
- `getContent()`：获取章节内容
- `getDurChapter()`：获取当前章节
- `clearInvalidCache()`：清除无效缓存

### 5. WebBook.kt

网络书源的核心类，负责从网络获取书籍信息和内容。

**关键方法**：
- `getChapterList()`：获取章节列表
- `getBookInfo()`：获取书籍信息
- `getContent()`：获取章节内容

### 6. ContentProcessor.kt

内容处理器，负责处理书籍内容的解析和转换。

**关键方法**：
- `getContent()`：处理章节内容
- `getTitleReplaceRules()`：获取标题替换规则

## 依赖关系

### 核心依赖

| 依赖项 | 版本 | 用途 |
|-------|------|------|
| Kotlin | 1.9.0+ | 主要开发语言 |
| AndroidX | - | Android 支持库 |
| Jetpack Compose | - | 现代 UI 框架 |
| Room | - | 数据库 ORM |
| Koin | - | 依赖注入 |
| OkHttp | - | 网络请求 |
| Rhino | - | JavaScript 引擎 |
| ExoPlayer | - | 音频播放 |
| Coil/Glide | - | 图片加载 |

### 模块间依赖关系

- **UI层** 依赖 **业务逻辑层** 和 **数据层**
- **业务逻辑层** 依赖 **数据层**
- **数据层** 依赖 **数据库** 和 **网络请求**
- **服务层** 依赖 **业务逻辑层**

## 项目运行方式

### 环境要求

- Android Studio Arctic Fox 或更高版本
- Android SDK 33 或更高版本
- Kotlin 1.9.0 或更高版本
- Gradle 7.0 或更高版本

### 构建与运行

1. **克隆项目**：
   ```bash
   git clone https://github.com/HapeLee/legado-with-MD3.git
   ```

2. **打开项目**：
   在 Android Studio 中打开项目目录。

3. **同步依赖**：
   等待 Gradle 同步完成。

4. **构建项目**：
   点击 "Build > Make Project" 或使用快捷键 `Ctrl+F9`。

5. **运行项目**：
   点击 "Run > Run 'app'" 或使用快捷键 `Shift+F10`，选择目标设备运行。

### 调试模式

- **调试构建**：使用 `debug` 构建变体进行调试
- **日志输出**：应用日志会输出到 Logcat，标签为 "App"
- **调试工具**：可使用 Android Studio 的调试工具进行断点调试

## 关键功能实现

### 1. 阅读功能

阅读功能是应用的核心，主要通过 `ReadBook` 类实现。它管理阅读状态、章节加载、页面跳转等功能。

**实现流程**：
1. 初始化阅读数据，包括书籍信息、章节列表等
2. 加载当前章节和前后章节的内容
3. 处理页面跳转、章节切换等用户操作
4. 保存阅读进度和阅读记录

### 2. 书源系统

书源系统允许用户从网络获取书籍信息和内容，主要通过 `BookSource` 和 `WebBook` 类实现。

**实现流程**：
1. 解析书源规则（JSON 格式）
2. 根据规则从网络获取书籍列表、详情和内容
3. 处理内容解析、章节提取等
4. 缓存已获取的内容

### 3. 本地书籍

本地书籍功能支持读取本地存储的各种格式的书籍，主要通过 `LocalBook` 和相关文件处理类实现。

**支持格式**：
- TXT
- EPUB
- MOBI
- PDF

**实现流程**：
1. 扫描本地存储，发现书籍文件
2. 根据文件格式选择对应的解析器
3. 解析书籍内容，提取章节信息
4. 提供阅读功能

### 4. 阅读记录

阅读记录功能记录用户的阅读历史和统计数据，主要通过 `ReadRecordRepository` 实现。

**功能特点**：
- 记录阅读时长
- 统计阅读进度
- 提供时间轴视图
- 支持章节维度统计

### 5. 主题系统

主题系统采用 Material Design 3 风格，支持动态取色和自定义主题，主要通过 `ThemeStore` 和相关类实现。

**功能特点**：
- 支持浅色/深色模式
- 支持动态取色（基于封面或自定义图片）
- 支持自定义主题颜色
- 支持多种阅读背景

## 技术亮点

1. **Material Design 3**：采用最新的 Material Design 3 设计语言，提供现代、一致的用户体验

2. **Jetpack Compose 迁移**：逐步从传统 View 系统迁移至 Jetpack Compose，提高 UI 开发效率和质量

3. **多格式支持**：支持多种电子书格式，包括 TXT、EPUB、MOBI、PDF 等

4. **书源系统**：灵活的书源系统，支持自定义书源规则，可从网络获取书籍内容

5. **阅读体验优化**：多种翻页模式、字体设置、背景设置等，提供个性化的阅读体验

6. **性能优化**：章节预加载、内容缓存、图片加载优化等，提高应用性能

7. **扩展性**：模块化设计，易于扩展新功能和支持新格式

## 未来发展

1. **完成 Jetpack Compose 迁移**：将所有界面从传统 View 系统迁移至 Jetpack Compose

2. **增强漫画阅读体验**：进一步优化漫画阅读功能，支持更多漫画格式

3. **云同步功能**：增强云同步功能，支持更多云存储服务

4. **AI 辅助阅读**：集成 AI 技术，提供智能摘要、内容分析等功能

5. **多平台支持**：考虑支持其他平台，如 iOS、Web 等

## 总结

Legado with MD3 是一个功能强大、设计现代的阅读应用，它在保持原有功能的基础上，通过 Material Design 3 重构和 Jetpack Compose 迁移，提供了更加现代、流畅的阅读体验。项目采用模块化设计，代码结构清晰，易于扩展和维护。

该项目不仅是一个优秀的阅读工具，也是学习 Android 应用开发、Material Design 3 和 Jetpack Compose 的重要参考资料。