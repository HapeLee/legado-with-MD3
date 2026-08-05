# Copilot Instructions for AI Coding Agents

## 项目架构概览
- 本项目为 Android 阅读器（Material Design 3 风格重构版），包含主 app（Kotlin/Java）、配套 web 端（Vue3）、多模块扩展。
- 主要目录：
  - `app/`：Android 主程序，核心逻辑、服务、UI、数据层。
  - `modules/web/`：Web 端书架与源编辑，需与 app 后端联动。
  - `modules/book/`、`modules/rhino/`：功能扩展模块。

## 关键组件与数据流
- 书籍、书源、订阅源等核心数据结构定义于 `app/src/main/java/io/legato/kazusa/data/`。
- 解析规则、导入/导出逻辑见 `model/`、`model/localBook/`。
- 服务（如音频播放、下载、朗读、Web 服务）在 `service/`，通过广播或接口与 UI、数据层交互。
- UI 组件分布于 `ui/`，按功能细分（如书架、阅读、搜索、订阅等）。
- Web 端通过 REST API 与 app 通信，需配置 `.env.development` 的 `VITE_API` 指向 app 的 web 服务 IP。

## 构建与开发流程
- **Android 构建**：
  - 使用 Gradle，入口为 `build.gradle`、`app/build.gradle`。
  - 常用命令：`./gradlew assembleRelease`、`./gradlew test`。
  - ProGuard 混淆规则见 `proguard-rules.pro`、`cronet-proguard-rules.pro`。
- **Web 端开发**：
  - 进入 `modules/web/`，使用 `pnpm dev` 启动开发，`pnpm build` 打包。
  - 调试需保证手机与电脑同网段，手机端开启 web 服务。

## 项目约定与特殊模式
- 书源、订阅源规则高度自定义，相关解析逻辑集中于 `model/analyzeRule`、`model/rss`。
- 加密/解密相关辅助函数见 `help/crypto/`，RhinoJs 调用 Java 方法有特殊重载（见 README）。
- 主题、权限、网络存储等通用功能在 `lib/` 下有独立实现。
- 书籍文件支持 TXT、EPUB、PDF、UMD，解析入口为 `model/localBook/LocalBook.kt`。

## 外部依赖与集成
- 主要依赖：JsoupXpath、json-path、rhino-android、okhttp、glide、ktor、bga-qrcode-zxing、colorpicker、commons-text、markwon、hanlp、epublib-core 等。
- 依赖声明见 `build.gradle`、`app/build.gradle`，部分第三方库源码在 `lib/`。

## 典型开发场景示例
- 新增书源解析：扩展 `model/analyzeRule`，同步更新 UI 相关界面。
- 增加服务：在 `service/` 新建服务类，注册广播或接口，UI 层调用。
- Web 端联动：确保 API 路由与 app web 服务一致，调试时修改 `.env.development`。

## 参考文档与社区
- 官方帮助文档：https://www.yuque.com/legado/wiki
- 书源规则教程：https://mgz0227.github.io/The-tutorial-of-Legado/
- 社区交流：Telegram、Discord、语雀社区

---
如需补充或有疑问，请反馈具体场景或模块。