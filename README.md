# Legado with MD3

<p align="left">
  <a href="https://github.com/HapeLee/legado-with-MD3">简体中文</a> ｜ 
  <a href="https://github.com/HapeLee/legado-with-MD3/blob/main/English.md">English</a>
</p>

## 📖 介绍

**Legado with MD3** 是基于开源项目 [阅读 (Legado)](https://github.com/gedoor/legado) 开发的 Material
Design 3 风格重构版本。

本项目在对 UI 进行重绘的基础上，加入了多项分支独有功能，并正在逐步从传统 View 迁移至 Jetpack Compose
框架，目标是提供更加现代、流畅且一致的阅读体验。

> [!CAUTION]
> **注意事项：**
> 由于使用 Monet 引擎重构了主题系统，官方版本的主题在此版本中不再可用。
>
> **Android 12 以下设备：**
> 暂时无法使用自定义主题与动态取色功能。此限制将在 Jetpack Compose
> 迁移完成后得到解决（由于开发者精力有限，迁移过程将持续较长时间）。

---

## ✨ 分支特性

相比于官方版本，本项目具有以下独有特性：

* **全新主题：** 全新 Material Design 3 设计界面，支持 **预测性返回手势** 与 **共享元素动画**。
* **阅读界面：** 更加个性化的阅读界面与菜单配置。
* **阅读记录：** 提供详尽的阅读记录，支持 **时间轴** 与 **章节维度** 统计。
* **体验增强：** 更健全的 **漫画阅读** 、 **有声书** 与 **发现** 等界面体验。
* **书架布局：** 更多的书架布局选择，针对 **平板端** 进行了专门的界面优化。
* **实用功能：** 新增书籍备注、智能伴生分组（自动归类已读/未读），支持**手柄**上下**翻页**。

---

## 🛠️ 核心功能

1. **多格式支持：** 支持本地 TXT、EPUB 阅读，智能扫描本地文件。
2. **高度自定义：** 切换字体、背景、行距、段距、加粗、简繁转换等。
3. **净化替换：** 强力去除广告，替换正文内容。
4. **翻页模式：** 覆盖、仿真、滑动、滚动等多种模式随心切换。
5. **完全开源：** 无广告，持续迭代优化。

---

## ❤️ 致谢

感谢以下优秀开源项目提供的灵感与技术支持：

* [gedoor/legado](https://github.com/gedoor/legado) (这个项目最吊的老爹)
* [Luoyacheng/legado](https://github.com/Luoyacheng/legado) (提供了更多的扩展功能)
* [komikku-app/komikku](https://github.com/komikku-app/komikku) (提供了界面灵感与一些 Compose
  的优秀控件)
* [FoedusProgramme/Gramophone](https://github.com/FoedusProgramme/Gramophone) (提供了界面灵感与 View
  系统的封面取色方法)
* [jordond/MaterialKolor](https://github.com/jordond/MaterialKolor) (基于 Jetpack Compose
  优秀的取色实现)
* [Calvin-LL/Reorderable](https://github.com/Calvin-LL/Reorderable) (基于 Jetpack Compose
  优秀的拖动排序实现)
* 以及更多开源项目...

## ⚠️ 免责声明

项目内容不代表原作者立场，与原项目作者不存在任何隶属或授权关系。

