# OxygenX 0.0.2-local.9

Unofficial / 非官方，Apache-2.0，API experimental。

这是来自 D:/Project/OxygenX 的本地构建快照。manifest.json 保留源提交、dirty 标记与 Maven zip 的 SHA-256。压缩包校验和标识含有未提交改动的实际制品。

Gradle 校验压缩包并解压到 .gradle/oxygenx，避免 clean 删除准备的依赖；不会读取开发机的 Maven Local。升级时同时更新压缩包、manifest.json 与 gradle/libs.versions.toml，并重新运行双产品构建与 Oxygen 组件回归。

快照由 `tools/release.py` 的 `zip_repository` 校验并打包 `build/maven-repo`，来源为 `./gradlew publishAllPublicationsToLocalRepoRepository "-PoxygenxVersion=0.0.2-local.9"`。当前图标快照统一 Google Material Symbols Outlined / opsz=24 / wght=300 / FILL=0 / GRAD=0。`oxygenx-icons-material` 包含 128 个符号并重导出 `oxygenx-icons` 的 14 个核心矢量，`OxygenIcons` 兼容名称保留；AppIcons 的 Oxygen 分支全部使用这一集合。来源锁与生成器位于源仓库 design/icons/material-symbols.json、tools/material_symbols.py。

菜单采用经典 M3 样式并保留 Oxygen 配色：独立小圆角、轻投影、紧凑标签与图标槽、长菜单滚动和 RTL 定位；原 API 签名保留。说明与验收见 ../../docs/dev/oxygenx-integration.md。
