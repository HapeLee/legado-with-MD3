# 下一会话接手文案（四主题库菜单统一入口）— 已归档

> ⚠️ **已归档（2026-11-13 第四十九轮）：本文生成于第三十七轮，正文的 8/4 计数、
> 「剩余 4 处业务调用点」与「下一轮三选一」全部已作废，不要复制下方「粘贴开始」
> 段落作为新会话指令。** 阶段 C 已完成：业务调用点 0 处（`RoundDropdownMenu(` 4 处
> = 定义 1 + 三入口内部 3）；当前状态与第三十一～四十九轮记录见
> `docs/dev/native-menu-engines-plan.md` 顶部与 §9。本文仅作历史追溯。
> 原用途：复制「粘贴开始」到「粘贴结束」之间的内容，作为新会话的第一条消息。
> 生成：2026-11-12，第三十七轮结束。

--- 粘贴开始 ---

⚠️ 已作废，见文件顶部归档说明。以下内容保留仅供历史追溯。

继续 `legado-with-MD3` 的「四主题库菜单统一入口」迁移（阶段 C 收尾）。先读
`AGENTS.md` 与 `docs/dev/native-menu-engines-plan.md`（§9 是第三十一～三十七轮实施记录，
顶部是当前计数口径）。**不要按 `docs/dev/native-menu-engines-handoff.md` 的旧正文行动**：
它自己标注「停在第二十二轮之前」，51 处等数字与结论均已过期；长期方案以 plan.md 为准。

## 1. 当前事实（2026-11-12，第三十七轮结束）

- 全仓 `RoundDropdownMenu(` 文本命中 **8 处** = 业务调用点 **4 处**
  + 三入口内部 3 处（`AppActionMenu.kt:680` 的 Material `LegacyActions`、
  `AppAnchoredPanel.kt:77/104`）+ 组件定义 1 处（`RoundDropdownMenu.kt:107`）。
  内部 3 处与定义 1 处**不算待迁**。
- 剩余 4 处业务调用点：
  1. `ui/book/info/edit/BookInfoEditScreen.kt:392` — `ExposedDropdownMenuBox` 书类型单选
  2. `ui/replace/edit/ReplaceEditScreen.kt:401` — `ExposedDropdownMenuBox` 分组**可编辑**组合框
  3. `ui/widget/components/theme/ThemeListContent.kt:315` — 点「网格列数」原地翻页换菜单
  4. `ui/widget/components/card/SelectionItemCard.kt:279` — `dropdownContent` 插槽（组件 API）
- 这 4 处**都不是套模板能收的**：1/2 换 `AppChoiceDropdown` 会改 trigger 外观，2 还会丢掉
  自由输入；3 是「不关菜单」形态；4 是公共组件契约，改动波及所有用到该卡片的列表。
  建议先选一类建行为基线 + 四引擎真机外观基线，而不是继续减计数。

### 两个已作废的旧结论（别再当阻塞项）

- `ReadBookMenuBarFloating` 那处巨型 overflow 菜单**从来没有调用方**
  （`private fun OverflowDropdownMenu`，`git log -S` 只有 `7e435c1ea` 一条），第三十三轮已整段
  删除。此前担心的「`reSegment` / `delRubyTag` / `delHTag` 点击不关菜单」冲突因此不存在。
- `AppActionMenu` **不需要**补逐项 `keepOpen`：四引擎都是「先 dismiss 再 onAction」，入口本身
  没有也不需要「动作后保持展开」的能力；第七轮 `ToggleActionMenuContractTest` 与第十轮
  「导入面板配置页」的结论继续成立。

## 2. 已稳定下来的迁移形态（第三十一～三十七轮）

每处都是「先建契约/行为基线跑绿 → 再迁移 → 同步下调棘轮」：

- 单个受控 `AppActionMenu`：`MainScreen` 书架 Rail 分组、`BookshelfScreen` 分组、
  `ReaderMoreActionsSheet` 5 处 + `MangaReaderSheets` 1 处、`ReadBookMenuBarTitle` 2 处、
  `RssReadRouteScreen` 2 处合并成 1 个（重定向策略降级为 `AppAction.children`）、
  `FontSelectSheet`、`RssSortScreen` 底部分组切换、`ExploreKindMultiTypeItem`。
- 典型改法：`embeddedExpanded = <页面状态>`、`trigger = {}`、
  `modifier = Modifier.matchParentSize()`（**调用方在 `BoxScope` 内传**，组件内部拿不到）、
  稳定 ID 前缀 + ID → 行为分派、`AppAction.selected` / `checked` 替掉手画 Check。
- 每个切片配一份 `app/src/test/java/io/legado/app/ui/widget/components/menu/*ContractTest.kt`；
  减少旧菜单时**必须同步下调**相邻测试里「仍剩 N 处」的棘轮，否则全包会红。
- 计数沿革：24/20 → 23/19（第三十一轮曾漏记 `MainScreen`，已在 §9 更正）→ 17/13 → 15/11
  → 13/9 → 11/7 → 9/5 → **8/4**（文本命中 / 业务调用点）。

## 3. 三个实现坑（别重踩）

1. `buildList {}` / `map {}` 的 lambda **不是 `@Composable`**，`stringResource` 必须在外面先
   求值赋给 val；否则编译器报 `Expecting ')'` 之类语法错误，看起来像括号不配对。
2. 受控弹层四引擎都是先 `onDismissRequest()` 再 `onAction(id)`，组件随即可能被移出组合。
   选中回调里的 suspend 副作用**不要**挂在组件自己的 `rememberCoroutineScope()` 上（会被取消），
   要用调用方传入的 scope —— 第三十一轮在 `MainScreen` 已修过一次。
3. 已知外观差异（未真机复核）：`FontSelectSheet` 旧实现的 `ProvideAppDensity` 包裹用不上了；
   Material 下 `children` 平铺成同级项；四引擎图标 / 选中态 / 分段线形态各不相同。

## 4. 工作区与验证口径

- 所有改动**均未提交**，工作区有大量用户既有修改；只改本次切片，不要顺手重构或清理。
- 用户偏好**只跑定向编译 / 测试，不跑全量**；JDK 21；所有文本改动至少 `git diff --check`。
- 最小验证集（本会话每片都跑过、全绿）：

```powershell
.\gradlew.bat :app:compileAppCoreDebugKotlin :app:compileAppComicDebugKotlin --no-configuration-cache --console=plain
.\gradlew.bat :app:testAppCoreDebugUnitTest --tests "io.legado.app.ui.widget.components.menu.*" --no-configuration-cache --console=plain
git --no-pager diff --check
```

  最近一次：双变体编译 `BUILD SUCCESSFUL`，`menu` 包定向全绿，`diff --check` 干净，改动文件
  `read_lints` 0 诊断。`lintAppCoreDebug`、真机四引擎外观、TalkBack 一直**未复核**。
  编译日志习惯：`> build\c.log 2>&1` 后用 `Select-String -Pattern "^e: "` 提错误。

## 5. 下一轮三选一（建议先声明选择）

- **A. 两个 `ExposedDropdownMenuBox`**：先补四引擎真机外观基线，再决定
  `AppChoiceDropdown(compact = true)` 的 trigger 形态；`ReplaceEditScreen.GroupSelector`
  的自由输入能力必须保留，不能只换成单选。
- **B. `ThemeListContent`**：先建「原地翻页」行为基线，再决定改成 `AppAnchoredPanel`
  自定义 content，还是把列数选择拆成独立面板（第十轮「配置页」同构解法）。
- **C. `SelectionItemCard`**：先盘点所有传 `dropdownContent` 的调用点，评估改公共 API 的影响面。

无论选哪条：先写基线测试并跑绿 → 再动组件 → 完成后同步 plan.md §9 与顶部计数。
交付说明要包含：修改职责、关键取舍、实际运行的验证、未验证风险。

--- 粘贴结束 ---

## 附：本会话改动文件（供核对，均在工作区未提交）

- 主源码：`ui/main/MainScreen.kt`、`ui/main/bookshelf/BookshelfScreen.kt`、
  `ui/book/read/sheet/ReaderMoreActionsSheet.kt`、`ui/book/manga/MangaReaderSheets.kt`、
  `ui/book/read/ReadBookMenuBarTitle.kt`、`ReadBookMenuBarTool.kt`、
  `ReadBookMenuBarFloating.kt`、`ui/book/read/sheet/ReaderBookSheet.kt`、
  `ui/rss/read/RssReadRouteScreen.kt`、`ui/rss/article/RssSortScreen.kt`、
  `ui/widget/components/FontSelectSheet.kt`、
  `ui/widget/components/explore/ExploreKindMultiTypeItem.kt`。
- 测试（均在 `app/src/test/java/io/legado/app/ui/widget/components/menu/`）：新增
  `MainScreenRailGroupMenuContractTest`、`BookshelfGroupMenuContractTest`、
  `ReaderActionMenuContractTest`、`ReadMenuDropdownContractTest`、`RssReadMenuContractTest`、
  `FontTypefaceMenuContractTest`、`ExploreKindSelectorMenuContractTest`；下调棘轮
  `LongPressMenuContractTest`、`CompactChoiceMenuContractTest`、`RssSortMenuContractTest`、
  `MenuHeadingDividerContractTest`。
- 文档：`docs/dev/native-menu-engines-plan.md`（§9 追加第三十一～三十七轮、顶部计数）。
