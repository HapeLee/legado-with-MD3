# UI fork 构建

Miuix 通过固定子模块与 `includeBuild` 接入。COUI、Mochi 同样固定子模块提交，但先生成 Maven 产物，应用再读取 AAR、POM 与 Gradle Module Metadata。Mochi 因而继续经过 `app/mochi-compat.gradle` 的 Backdrop 兼容转换。

## 本地与 CI

首次检出执行 `git submodule update --init modules/miuix modules/coui modules/mochi`。配置 JDK 21、Python 3.11+ 与 `ANDROID_HOME`，安装 Android SDK 36、37.0、37.1 平台及 Build Tools 36.0.0、37.0.0。运行 `python scripts/prepare-ui-forks.py` 后，按 README 或 AGENTS 中的命令编译应用。`--check` 只核对提交与版本，不运行 Gradle。

脚本仅发布 COUI 的核心、Material 3、图标三个模块和 Mochi 的 compose、ios 两个模块，输出到 `build/ui-forks/maven`。应用将这五个模块独占路由到该目录，不读取开发机的旧 Maven Local 包，也不会回落到上游发行版。清理根 `build/` 后需重新准备；仅修改应用代码时无需重复发布。

Verify 的 core/comic job、自动 Release 和手动 APK 构建共用 `.github/actions/prepare-ui-forks`。每个 job 在 JDK/Gradle 配置完成后准备 SDK 和库产物，依赖构建失败时立即停止，不继续打包应用。action 在 Release 签名凭据写入前运行，不需要 Packages、Central 或签名凭据。

## 更新 fork

1. 在对应 fork 完成修改与验证，提交并先推送到 `.gitmodules` 指向的公开仓库，确保全新克隆能取得该提交。
2. 更新 Legado 中 `modules/coui` 或 `modules/mochi` 的检出提交。
3. 将 `gradle/libs.versions.toml` 中对应版本设为 `<基础版本>-legado.<提交前 12 位>`，同时更新子模块指针。脚本会拒绝提交指针、实际 HEAD、版本后缀之间的不一致，以及子模块内未提交的跟踪文件改动。
4. 运行脚本并验证受影响的应用构建。COUI 新增/重命名 API 还需核对应用调用方；不把库构建通过视为宿主兼容验证。

固定提交与 fork 版本必须随 Legado 一起提交。只更新版本号、只上传 AAR 或让 CI 跟随 fork 的 `main` 均不能复现同一份依赖。当前这两个 fork 的 Maven group 沿用源码配置，产物仅在项目内消费；若以后公开发布，另行配置 fork 自有坐标和发行流程。
