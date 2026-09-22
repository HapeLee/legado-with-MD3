#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""统计撰定后各测试任务的用例数，用于和棘轮基线比对。

为什么要有它：每片 KMP/CMP 搬迁都要求「只搬文件 ⇒ 用例数与基线逐字一致」。Gradle 的
BUILD SUCCESSFUL 只能证明没失败，**证明不了没少跑**——「悄悄少跑 19 个用例但全绿」
是完全可以穿过 `BUILD SUCCESSFUL` 的事故。这里直接从 surefire XML 取
`tests/failures/errors/skipped`，不解析 Gradle 输出。

用法（干净重建并跑完全量验证集后）：

    python tools/count-test-results.py

当前基线：主验证集 **714**、全量 **1154**（详见 .workbuddy/memory/topics/gates-and-verification.md）。
"""
import pathlib
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent

# 显示名 -> (结果 XML 目录, 是否计入「主验证集」)
RESULT_DIRS = {
    # M4-5b：634 → 633（**-1**）。原 `CryptoCompatibilityTest` 里那条
    # `nameUuidFromBytes 与 java UUID v3 一致` 的被测对象（`:app/utils/UuidExtensions.kt`）随本片
    # 下沉到 `:core:platform` ⇒ 该用例失去被测对象而删除（同 M2-2 删 `BigDataStoreProvider` 的
    # 两例、M2-4 删 `LoggerContractTest` 两例的处理）。护栏本身**没丢**，而是被更强的版本接替：
    # `NameUuidContractTest` 在 androidHostTest 与 desktopTest **两个** target 上各 6 例，其中
    # `matches java UUID nameUUIDFromBytes` 就是原用例的搬家版（因为 `java.util.UUID` 是 JVM API，
    # 只能写在 target 子类里）。本模块在主验证集内 ⇒ **主集基线随之下调 1**。
    "app            (testAppDebugUnitTest)": ("app/build/test-results/testAppDebugUnitTest", True),
    # designsystem / tagrules 的 `commonTest` 在两个目标上各跑一遍；统一取
    # `testAndroidHostTest`（KMP android 目标的主机测任务名）。⚠️ 模块转 KMP 后
    # 老 Android library 的 `testDebugUnitTest` 不再产出，别改回去。
    "designsystem   (testAndroidHostTest)": ("core/designsystem/build/test-results/testAndroidHostTest", True),
    "viewmodel      (testAndroidHostTest)": ("core/viewmodel/build/test-results/testAndroidHostTest", True),
    "tagrules       (testAndroidHostTest)": ("feature/tagrules/build/test-results/testAndroidHostTest", True),
    # M1-3x：`ReplaceRuleStateTest`（原住 `:app/src/test`）随模块转 KMP 搬进本模块的
    # `androidHostTest`。app 的 635 相应降到 633、本模块 +2 ⇒ **主验证集合计仍是 708**，
    # 只是分布变了（这是有意的归属调整，不是用例增减）。
    "replacerules   (testAndroidHostTest)": ("feature/replacerules/build/test-results/testAndroidHostTest", True),
    # M5-1c：`:feature:about`（M5 批次 2 第一站）首次纳入统计。**进主集**，与其它 Feature 同口径。
    # 2 例 = 搬来的 `更新渠道通过唯一UiState入口下发`（原住 `:app/src/test/.../AboutViewModelTest.kt`，
    # 被测对象下沉 ⇒ 用例跟着搬，属归属调整）+ 新增的
    # `未设置备份目录时保存日志只提示不落盘`（`saveLog` 里那条判定以前写在 `:app` 的 VM 里、
    # 顺手用 `context`，下沉后成了共享层自己的分支逻辑，必须钉住「提示了但**没有**调用
    # `diagnostics.saveLogs()`」——否则提示与动作会同时发生）。
    "about          (testAndroidHostTest)": ("feature/about/build/test-results/testAndroidHostTest", True),
    # M5-2a：`:feature:settings`（M5 批次 2 第二站，第一批只装实验室页）首次纳入统计。进主集。
    # 3 例**全部新增**——迁移前 `LabConfigViewModel` 一个测试都没有。三条分别钉住：
    # 「设置经唯一 uiState 入口下发」、「导出诊断发 Effect 且**不写设置**」（这条分支写错会
    # 退化成点了导出顺带写一次设置）、以及「诊断计数初值来自共享计数器」。
    "settings       (testAndroidHostTest)": ("feature/settings/build/test-results/testAndroidHostTest", True),
    # M2-2：`core:data` 的 commonTest 与 desktopTest 加 4 —— 删掉 2 个已失去被测对象的
    # `BigDataStoreProvider` 用例（未安装/安装后读回），新增 6 例 `RuleDataFileStoreDesktopTest`
    # （真文件系统 + **硬编码 MD5 向量**钉住「路径布局与迁移前逐字节一致」，那是既有用户数据
    # 能否读回的关键）。之所以要在桌面端真落盘验证：新实现是共享层 object，commonTest 里
    # 没有可用的 `RuleDataStorage` actual，只有 `desktopTest` 才跑得到真 IO。
    "core:data      (desktopTest)": ("core/data/build/test-results/desktopTest", False),
    # M2-3：`core:platform` 的 commonTest 挂在 `SymmetricCrypto` 原语上重写 —— 原 2 例测的是
    # `SymmetricCryptoProvider` 的 install/uninstall（被测对象已删除），换成 7 例对原语本身的
    # 契约测试（硬编码 AES 密文向量，由 openssl 与 node 两个独立实现交叉确认）。该文件同时跑在
    # `testAndroidHostTest` 与 `desktopTest` 上，但**只统计 desktopTest**（口径与其它模块一致）。
    # M4-5a：+4 = `DigestContractTest` 的 md5 契约用例（空输入 / `abc` 两个 RFC 1321 向量、
    # `hello` 的独立实现向量 + 16 字节、以及两个不同输入的**各自固定向量**——最后一条不是
    # 「互相不等就算过」，而是各自比对 Python hashlib 算出的常量）⇒ **88 → 92**。
    # 变异验证另外证明**两个 target 各自有效**：把 androidMain 的 md5 换成 SHA-256 前 16 字节
    # 并只跑 `testAndroidHostTest`，同样 4 例变红（否则「只测 desktop」会让 android 侧的
    # 错误实现在任何用例上都看不出来）。
    # M4-5b：92 → **98**（+6 = `NameUuidContractTest` 5 例 + target 子类的
    # `matches java UUID nameUUIDFromBytes` 1 例）。这是把 `:app/utils/nameUuidFromBytes`
    # （UUID v3 名称空间哈希）下沉到本模块的护栏：5 条基类用例钉住「与独立实现算出的 UUID 向量
    # 一致」「空输入的 version/variant 位改写」「版本位恒为 3、变体位 ∈ {8,9,a,b}」「同输入确定性
    # 且不同输入可区分」「对分隔符敏感」，1 条 target 用例与 `java.util.UUID.nameUUIDFromBytes`
    # 交叉确认。变异 4 轮全红：删 version 行 / 删 variant 行 / `md5[6]→md5[7]` 索引错 /
    # 摘要把 `md5` 换成 `sha256` 前 16 字节（第 4 轮只跑 `testAndroidHostTest`，同时证明
    # android 侧子类有效）。
    "core:platform  (desktopTest)": ("core/platform/build/test-results/desktopTest", False),
    # M1-4：desktop host 的主路径证据。**计入主验证集**——它是本仓第一个越过「能编译」的
    # desktop 断言，少跑就没人发现。基线与 app 侧一样靠它兜底。
    # M1-4b 加 2 例（两目的地导航 + entry 级 VM 释放）；M2-1 加 1 例（desktop 的
    # ImportJsonEditor 显式不支持，防它被改成静默降级）。
    "host:desktop    (desktopTest)": ("host/desktop/build/test-results/desktopTest", True),
    # M1-4 工具链探针（CMP UI 测试能否在 desktop 跑）。只进全量：证明过一次即可，
    # 不必每次主验证都跑。
    "compose-probe   (desktopTest)": ("smoke/compose-desktop-probe/build/test-results/desktopTest", False),
    # M3-1：`:data:rules`（替换规则域的实现与映射器）自带的等价性基线。口径与其它 data
    # 层模块一致（`core:data` / `core:platform` 都只取 `desktopTest`，见上），故**不进主集**。
    # 用例是 `ReplaceRuleMapperTest`：实体 ↔ 领域模型逐字段往返、默认值集合、只按 id 判等的
    # 语义、`isValid()` / `getValidTimeoutMillisecond()` 与实体行为一致。
    # M3-2 起同一模块还装高亮标签规则域的 `HighlightTagRuleMapperTest`（6 例）、M3-3 再加
    # 字典规则域的 `DictRuleMapperTest`（6 例）、M3-4 再加 TXT 目录规则域的
    # `TxtTocRuleMapperTest`（7 例）、M3-5 再加规则订阅域的 `RuleSubMapperTest`（8 例）、
    # M3-6 再加标签分组规则域的 `TagGroupRuleMapperTest`（7 例），故本目录计数为
    # **六片之和**（7 + 6 + 6 + 7 + 8 + 7 = 41）。
    "data:rules      (desktopTest)": ("data/rules/build/test-results/desktopTest", False),
    # M4-1：`:data:ai`（AI 提示词预设域的实现与映射器）自带的等价性基线。口径与其它 data
    # 层模块一致（`core:data` / `core:platform` / `data:rules` 都只取 `desktopTest`），
    # 故**不进主集**。用例是 `AiPromptPresetMapperTest`（7 例）：实体 ↔ 领域模型逐字段
    # 往返、默认值集合（`enabled` / `builtIn` / `sortNumber`）、`createdAt` / `updatedAt`
    # 默认取当前毫秒、判等按**全字段**而非主键（与 M3-6 相反、与 M3-5 同侧）、集合映射保序。
    # M4-2：同目录再加 `AiMemoryMapperTest`（7 例）与 `AiMemoryRepositoryImplTest`（4 例）
    # ⇒ **11 例**。后者是 M4-2 起的新形态：`AiMemoryRepositoryImpl` 不像 M4-1 的纯委派，
    # 它有两条真实逻辑（`upsert` 写前覆盖 `updatedAt`、`getForPrompt` 的「全局+会话」拼接
    # 与空白会话 id 短路），光靠 mapper 用例护不住 ⇒ 用手写的 DAO 假实现钉住。
    # M4-3：同目录再加 `AiArtifactMapperTest`（9 例）与 `AiArtifactRepositoryImplTest`（7 例）
    # ⇒ **27 例**。前者比 M4-1/M4-2 多两例，因为本域有两样别处没有的东西：**四个 `STATUS_*`
    # 常量**（DAO 用实体的常量做 SQL 插值，`:app` 已改用领域模型的常量 ⇒ 取值必须一致）与
    # **三个可空字段**（`chapterIndex` / `output` / `errorMessage`，映射不得归一化）。
    # 后者是本片**必须**补的：`observeBookArtifacts` 是本域唯一的 `Flow` 端口方法，
    # `AiArtifactMapperTest` 只测 `toDomain` / `toEntity` / `toDomainList`、**不驱动那条流**
    # ⇒ 把流内映射换成 `as List<AiArtifact>` 能编译通过且九条 mapper 用例全绿，而真机每次
    # 发射都会 `ClassCastException`。故按 M4-2 立的判据补 Impl 测试，用假 DAO 驱动
    # **多次发射**的流，钉住「每次发射都映射」+ `queryArtifacts` 三个可空筛选参数的透传。
    # M4-5c：同目录再加 AI profile 域（`AiProviderProfile` / `AiModelProfile` / `AiTaskPreset`
    # 三个实体 + `AiProfileGateway` 的 `AiProfileRepositoryImpl`）：三个 Mapper 测试
    # （9 + 10 + 9 = 28）＋ 一个 Impl 行为测试 **46 例** ⇒ **69 → 143**。
    # 46 例是 M4-2 以来最大的一个 Impl 测试，理由是 13 个端口方法里 **10 个带真实逻辑**，
    # 且全是 mapper 测试碰不到的：`saveProvider` 的 apiKey 回落与 7 个可选字段的沿用、
    # `saveModel` 的 `stableModelId`（UUID v3 字节语义）与能力合并保序、`importProviderModels`
    # 的「>0 才覆盖」两级回落、`setDefaultModel` 一次写三个内建预设（三条取值来源各不相同）、
    # `deleteProvider` 的两条 DAO 调用**顺序**、`toConfig` 的**合并方向**与三条路径回落、
    # 以及坏 JSON 的容错兜底。手写假 DAO 把 `@Insert` 写进内存表（回读才有意义）并用
    # `callLog` 记录调用序列（两个独立列表看不出 `deleteProvider` 的先后）。
    # ⚠️ 本片还踩到一个**只有干净重建才暴露**的坑：4 条表达式体 `@Test 方法`（
    # `fun x() = runBlocking { ... assertFailsWith ... }`）因末表达式返回异常对象而**不是
    # `void`**，JUnit 4 直接把整个测试类判 `InvalidTestClassError`（只跑出 1 个
    # `initializationError`、**0 个真实用例执行**）。增量构建下这个任务常是 UP-TO-DATE，
    # 于是「绿」是假的 —— 必须 `clean` 或 `--rerun`。修法是让末语句落回 Unit。
    "data:ai         (desktopTest)": ("data/ai/build/test-results/desktopTest", False),
    # M4-6：用户划线/高亮笔记域（`book_marks`）。新模块对 `domain/marking` + `data/marking`
    # 的用例全在 `data:marking`（domain 只放模型与端口，没有可测逻辑）。
    # 15 例 = `BookMarkingMapperTest` 9 + `BookMarkingRepositoryImplTest` 6。
    # 后者**必须存在**：端口有 `Flow` 方法 `flowByBook`，映射在流内每次发射都跑，
    # 而 mapper 测试只调 `toDomain`/`toEntity`/`toDomainList`，**从不驱动那条流** ——
    # 把 `.map { it.toDomainList() }` 换成 unchecked cast 能编译、九个 mapper 用例全绿，
    # 真机每次发射才 `ClassCastException`（M4-3 立的判据）。不进主集（同 `:data:ai`）。
    "data:marking    (desktopTest)": ("data/marking/build/test-results/desktopTest", False),
    # M4-7：首页模块域（`homepage_modules` / `homepage_custom_sets`）。
    # 20 例 = `HomepageModuleMapperTest` 7 + `HomepageCustomSetMapperTest` 6 +
    # `HomepageModulesRepositoryImplTest` 7。Impl 测试同样是**必须**的：本域端口有**四个**
    # `Flow` 方法，映射在流内每次发射都跑，mapper 测试一个都碰不到；另外它还是唯一能钉住
    # `createCustomSet` 的 id 形状（`cs_<millis>`）与 `deleteCustomSet` 顺序（先摘模块再删集合）
    # 的地方。不进主集（同 `:data:rules` / `:data:ai` / `:data:marking`）。
    "data:homepage   (desktopTest)": ("data/homepage/build/test-results/desktopTest", False),
    # M4-8：正文处理域。两个模块都有用例 —— **首次**出现「domain 模块自带测试」。
    #   `domain/contentprocess` 6 例 = 从 `:app/src/test/.../domain/model/` **搬来**的
    #   `BookContentProcessEngineTest`（原 5 例，随引擎一起下沉）＋ 新增 1 例
    #   （`disabledOrDraftProcessIsFilteredOut`：引擎第一个 filter 原先无用例）。
    #   搬迁时把 `GSON` 换成 `JsonCodec`、`MD5Utils` 换成常量（引擎从不读该字段）、
    #   JUnit4 换成 `kotlin.test` —— 断言与输入逐字未动。
    #   `data/contentprocess` 14 例 = mapper 8（含 15 个 companion 常量的双向逐值比对）
    #   + impl 6（`nextOrder` 的 `maxOrder()+1`、`delete` 走软删 `markDeleted` 等）。
    "domain/contentprocess (desktopTest)": ("domain/contentprocess/build/test-results/desktopTest", False),
    "data/contentprocess (desktopTest)": ("data/contentprocess/build/test-results/desktopTest", False),
    # M4-4：`:core:model` **首次纳入统计**（此前各片从未跟踪本模块，既有 59 例）。
    # 现在才加的理由：本片在这条路径上修掉了一个**静默的生产故障** —— `AiMessageParts.kt`
    # 由 `86c7428d24` 从 `:app`（该模块 apply 了 serialization 插件）移进本模块时漏了给
    # 目标模块 apply 插件 ⇒ 六个子类的 `$$serializer` 一个都没生成，`AiMessagePartJson`
    # 的多态编解码在运行期抛 `SerializationException`（`decode` 还 `runCatching` 吞成
    # `emptyList()` ⇒ 聊天记录静默丢内容），而这条路径**零测试覆盖** ⇒ 一直没暴露。
    # 新增 `AiMessagePartJsonTest` 8 例（往返 / 判别字段 / legacy 迁移 / null 省略 /
    # 未知字段 / 空白 / 坏 JSON）钉住它；不跟踪本模块，这个护栏被删掉也没人知道。
    "core:model     (desktopTest)": ("core/model/build/test-results/desktopTest", False),
}

# M3-6：主验证集 712 → 713（**本片独有**）。合并「标签分组规则匹配语义」的两份实现时，给
# `:app` 侧的单本书路径补了一条护栏用例——那段逻辑迁前住在
# `io.legado.app.help.book.applyTagGroupRulesForBook`，全仓**零测试**。新用例
# （`BookGroupMutationRepositoryTest.单本书路径只改内存分组位而不写库`）同时钉住「只处理这一本书」
# 与「不写库（persist = false）」，住 `:app` 主集，故基线必须上调 1。
# 前面五片（M3-1～M3-5）都只动 `:data:rules`，主集始终是 712。
# M4-5（M4-5b）：713 → **712**（**下调 1**）。`:app` 的 `CryptoCompatibilityTest` 里
# `nameUuidFromBytes 与 java UUID v3 一致` 的被测对象随本片下沉到 `:core:platform`
# （见 RESULT_DIRS 里 app 条目的说明），`:app` 在主集内 ⇒ 主集基线必须同步下调。
# ⚠️ 这是**有意减少**（被测对象搬走），不是用例丢失；替代护栏在 `:core:platform` 的两个
# target 上各 6 例。前一次下调是 M2-4（契约删除）与 M2-2（Provider 删除）。
# M5-1c：712 → **714**（净 **+2**）。三项相加：`:app` **-1**（`AboutViewModelTest`
# 随被测对象 `AboutViewModel` 下沉到 `:feature:about` ⇒ 用例跟着搬）+ 新模块 **+2**
# （搬来 1 例 + 新增 1 例，见 RESULT_DIRS 里 about 条目的说明）+ `:host:desktop` **+1**
# （`DesktopAboutCapabilitiesTest`：钉住三个新平台契约在 desktop 上**显式抛
# UnsupportedOperationException** 而不是降级返回空列表/`null`/`false`）。
# 前两片（M5-1a / M5-1b / M5-1c-pre）都是纯搬迁，主集与全量逐字不变；本片是 M5 里第一次
# 动基线，因为**第一次出现共享层自己的分支逻辑**（`saveLog` / `createHeapDump` 的
# 目录与开关判定）和**第一组需要显式 unsupported 的 desktop 契约**。
# M4-8：714 → **709**（**下调 5**）。`:app` 的 `BookContentProcessEngineTest`（5 例）随被测
# 对象 `BookContentProcessEngine` 一起下沉到 `:domain:contentprocess` 的 commonTest，
# 并在那里扩到 6 例。`:app` 在主集内 ⇒ 主集基线同步下调。这是**有意减少**（被测对象搬走），
# 不是用例丢失；先例：M4-5b、M5-1c 各下调 1。
# M2-8：709 → **710**（**+1**）。`:app` 新增 `AppModuleGraphTest`（Koin graph creation test），
# 补上 M5-1c-3 变异实测出的缺口「删掉一条 `single<>` 绑定后编译仍然绿」。
# M5-2a：710 → **713**（**+3**）。`:feature:settings` 首次入表，3 例全是新增
# （迁移前 `LabConfigViewModel` 零测试）。`:app` 本片**没减**——它的 labConfig 目录
# （4 文件，含 1 个 0 行空文件）本来就没有测试。
# M5-2d：713 → **716**（**+3**）。translation 子页迁入，新增 `TranslationConfigViewModelTest`
# 3 例（迁移前同样零测试）：初值来自 gateway / `SetProvider` 经唯一入口下发 /
# 另两个 Intent 各映射到自己的字段（防 `when` 分支复制粘贴串行）。`:app` 本片也没减。
# M5-3b：716 → **719**（**+3**）。customTheme 子页迁入，新增 `CustomThemeViewModelTest`
# 3 例（迁移前零测试）：DaySeed 选色要「写设置 + 发旧引擎通知」两件事都做 / DeepColor
# 映射到对应 slot 且不发那条通知 / 写失败时把提示发出去而不是静默吞掉。`:app` 没减。
# M5-4b：719 → **723**（**+4**）。ai/summary 子页迁入，新增 `AiSummaryConfigViewModelTest`
# 4 例（迁移前零测试），钉的是本片改的那条 fallback 语义：「保存失败时**有**异常文案就用它、
# **没有**才回落到资源里的保存失败」+ 重置提示走资源枚举 + 加载失败走运行期文本 +
# 保存成功要「提示 + 返回」都发。`:app` 没减。
# M5-4e：723 → **727**（**+4**）。**补回** ai/prompt 的用例 —— M5-4c 迁移时欠下的：
# 当时 VM 内部直接调 `getString`，M5-4d 探针量出这会让 VM 在 androidHostTest 下无法构造，
# 于是 M5-4e 把「VM 需要的资源字符串」抽成可注入的 `AiPromptStringSource`，VM 恢复可测。
# 4 例钉：已存提示词优先于默认值 / 保存成功走 Toast 而非 Effect（本页与 ai/summary 的差异）/
# 失败时「有异常文案用它、没有才回落资源」/ 重置单个用默认提示词并提示成功。
# M5-5a：727 → **731**（**+4**）。ai 主入口页迁入，新增 `AiConfigViewModelTest` 4 例
# （迁移前零测试）：模型按 provider 归组且孤儿模型被过滤 / 「当前模型」优先取默认翻译预设
# 指向的模型 / 没有预设时退到第一个模型 / 设为默认的两条**硬编码英文**提示逐字。
# ⚠️ 写这 4 例时被纠正了一个既有语义：`modelCount` 是**原始**模型数（含孤儿），
# 与过滤后的 `models.size` 本来就不等 —— 已把差异钉进用例注释。
# M5-5b：731 → **735**（**+4**）。AiModelEdit 页迁入，新增 `AiModelEditViewModelTest`
# 4 例（迁移前零测试）：`defaultParamsJson` 经 `JsonCodec` 反序列化进状态（**本片的实质改动**：
# `GSON.fromJson` → `JsonCodec.fromJsonObject`）/ 非法 JSON 回落默认参数而不崩 /
# `initialized` 之后流刷新**不覆盖**用户正在编辑的字段 / 保存成功发提示+返回并回写 modelProfileId。
BASELINE_MAIN = 735
# M2-3：877 → 882（`core:platform` 的 SymmetricCryptoContractTest 2 → 7 例）。主验证集不变。
# M2-4：882 → 891（净 +9 = -2 +11）。`Logger` / `LoggerProvider` 契约删除 ⇒ 随契约走的
# `LoggerContractTest` 2 例失去被测对象（同 M2-2 删 `BigDataStoreProvider` 用例的处理）；
# 同时给下沉共享层的新实现补契约测试：`ConcurrentRateRegistryTest` 5 例（core:data）、
# `AppLogStoreContractTest` 6 例（core:platform desktopTest）。**主验证集仍 712**
# （两个模块都在非主集一侧）。新增用例已做变异验证：改 trim 上界 / 改 `add` 顺序 /
# 交换 accessLimit 与 interval 都会让它们变红。
# M3-1：891 → 898（`:data:rules` 新增 `ReplaceRuleMapperTest` 7 例）。主验证集不变（仍 712）。
# 新模块的用例是**新增**而非搬迁，故基线必须上调；已做变异验证：把映射器里的
# `timeoutMillisecond` 去掉、把 `excludeScope` 写成 `scope`、把 `order` 映射删掉，
# 三种变异都会让 `ReplaceRuleMapperTest` 变红。
# M3-2：898 → 904（`:data:rules` 新增 `HighlightTagRuleMapperTest` 6 例）。主验证集**仍是 712**
# ——本片只搬实现与类型、不改 `:app` / Feature 的用例句，故 app 633 与 tagrules 15 逐字不变。
# 同样做了变异验证：把 `enabled` 写死为 `true`、把 `order` 错映射成 `id`、把 `title` 与
# `pattern` 对调，三种变异都会让 `HighlightTagRuleMapperTest` 变红。
# M3-3：904 → 910（`:data:rules` 新增 `DictRuleMapperTest` 6 例）。主验证集**仍是 712**
# ——本片只搬实现与类型、不改 `:app` / Feature 的用例句（`:app` 的 `DictViewModel` 那处改动
# 是「同步阻塞换 suspend」，用例数不变；`feature:dict` 模块级测试本来就是零）。
# 同样做了变异验证：把 `enabled` 写死为 `true`、把 `sortNumber` 错映射、把集合映射的
# `urlRule` 与 `showRule` 对调，三种变异都会让 `DictRuleMapperTest` 变红。
# M3-4：910 → 917（`:data:rules` 新增 `TxtTocRuleMapperTest` 7 例 —— 比前几片多一条
# 「`example` 的 `null` 原样穿过映射」）。主验证集**仍是 712**——本片只搬实现与类型，
# `:app` 侧 `TxtTocRuleDeserializerTest`（6 例，测实体级键名提升）逐字未改，Feature 侧
# 模块级测试本来就是零。
# 同样做了变异验证：把 `example` 的 `null` 归一成 `""`、把 `serialNumber` 的默认值改成 0、
# 把集合映射的 `chapterRule` 与 `volumeRule` 对调，三种变异都会让 `TxtTocRuleMapperTest` 变红。
# M3-5：917 → 925（`:data:rules` 新增 `RuleSubMapperTest` 8 例 —— 比前几片多两条：一条钉住
# 「判等是**全字段**而非主键」（`RuleSub` 是六个规则实体里唯一没重写 `equals` 的，其余五片恰好
# 相反），一条钉住领域侧 `RuleSubType` 与实体侧常量的镜像关系与数值。主验证集**仍是 712**
# ——本片只搬实现与类型，`:app` 的 RSS 订阅页（`RuleSubViewModel` / `RuleSubScreen`）用例数
# 本来就是零。
# M3-6：925 → 933（净 +8 = `:app` +1、`:data:rules` +7）。后者是 `TagGroupRuleMapperTest` 7 例
# ——与 M3-1～M3-4 同形（逐字段断言，因为实体与领域模型**都只按 `id` 判等**，整对象
# `assertEquals` 漏映射时照样通过），没有 M3-5 那种「判等是全字段」的反向用例，故回到 7 例。
# 前者见 `BASELINE_MAIN` 上方。
# M4-1：933 → 940（净 +7 = `:data:ai` 新增 `AiPromptPresetMapperTest` 7 例）。本片是第一个
# 非 rules 域：映射用例的写法回到「全字段判等」一侧（同 M3-5 `RuleSubMapperTest`），
# 用例数同样是 7。主验证集**不变**（713）——新用例不进主集，`:app` 侧只换了 import。
# M4-2：940 → 951（净 +11 = `:data:ai` 新增 `AiMemoryMapperTest` 7 例 +
# `AiMemoryRepositoryImplTest` 4 例）。主验证集**不变**（713）——新用例不进主集，`:app` 侧只换了 import 与 DI 绑定。
# M4-3：951 → 967（净 +16 = `:data:ai` 的 `AiArtifactMapperTest` 9 例 +
# `AiArtifactRepositoryImplTest` 7 例）。主验证集**不变**（713）——新用例不进主集，`:app` 侧
# 只换了 import 与 DI 绑定（并把 `AiToolRepository` 的 DAO 直连换成 Gateway，不涉及被计模式）。
# Impl 测试是本片**必须**补的：`observeBookArtifacts` 是唯一的 `Flow` 端口方法，mapper 测试
# 不驱动那条流 ⇒ 流内映射的变异能穿过后者的全部用例。
# M4-4：967 → 1069（净 +102）。两块：① `:data:ai` +35 = AI 会话域的
# `AiChatConversationMapperTest` 8 例 + `AiChatMessageMapperTest` 9 例 +
# `AiChatRepositoryImplTest` 18 例；② `:core:model` 首次纳入的 59 例既有 + 本片新增
# `AiMessagePartJsonTest` 8 例 = 67。主验证集**不变**（713）——两块都不进主集。
# M4-5a：1069 → 1073（净 +4 = `:core:platform` 的 `DigestContractTest` 新增 md5 用例）。
# `Digest` 契约的原文写明「只暴露 sha256：当前唯一真实消费方只需它；AES/HMAC 等在有真实消费方
# 时再加，不为对称提前扩接口」——本片补 md5 正是因为出现了**真实消费方**：AI profile 域下沉需要
# `nameUuidFromBytes`（UUID v3 名称空间哈希的字节级复刻），而它用 MD5。
# 主验证集**不变**（713）：`:core:platform` 不在主集一侧。
# M4-5：1073 → **1078**（净 **+5** = `:core:platform` +4（M4-5a 的 md5 契约）
# + 再 +6（M4-5b 的 NameUuid 契约）= +10，减去 `:app` 的 -1（被测对象搬走）… 分两步看更清楚：
# M4-5a 结束时 1073（= 1069 + 4）；M4-5b 再 +6 -1 ⇒ **1078**。
# 两片合为一次提交的理由：M4-5b 的 `nameUuidFromBytes` 正是 M4-5a 的 `Digest.md5` 的
# **首个真实消费方**，没有 A 则 B 无法存在；拆开提交会让「先提交的 A 里 md5 零消费方」，
# 反而违反「无调用方抽象」的纪律。
# M4-5c：1078 → **1152**（净 **+74** = `:data:ai` 的 69 → 143，见上方该模块条目的说明）。
# 本片是 M4 AI 域下沉的**末片**，也是最大的一片（12 个新文件 + 19 个消费方 + 2 个旧件删除）。
# 主验证集**不变**（712）——新用例全在 `:data:ai`（不在主集一侧），`:app` 侧只换 import、
# 删两个零调用方的 override（用例数不变）。10 轮变异全红后回绿，脚本
# `legado-verify/m4-5c-mutate.py`。
# M5-1c：1152 → **1154**（净 **+2**，同 `BASELINE_MAIN` 上方）；主集与全量同为 +2
# ——三个新用例全在计入口径内（about 进主集、host:desktop 进主集、`:app` 减 1）。
# M4-6：1154 → **1169**（净 **+15** = `:data:marking` 的 mapper 9 + impl 6）。
# 主验证集**不变**（714）：新模块在非主集一侧，与 `:data:rules` / `:data:ai` 同口径。
# M4-7：1169 → **1189**（净 **+20** = `:data:homepage` 的 mapper 7+6 + impl 7）。
# 主验证集仍不变（714）。
# M4-8：1189 → **1204**（净 **+15** = 1189 − 5（`:app` 的引擎测试搬走）
# + 6（`:domain:contentprocess`）+ 14（`:data:contentprocess`））。
# 主验证集同步下调到 709。
# M2-8：1204 → **1205**（**+1** = `:app` 的 `AppModuleGraphTest`）。主集同步 +1 到 710。
# M5-2a：1205 → **1208**（**+3** = `:feature:settings` 的 `LabConfigViewModelTest`）。主集同步到 713。
# M5-2d：1208 → **1211**（**+3** = `TranslationConfigViewModelTest`）。主集同步到 716。
# M5-3b：1211 → **1214**（**+3** = `CustomThemeViewModelTest`）。主集同步到 719。
# M5-4b：1214 → **1218**（**+4** = `AiSummaryConfigViewModelTest`）。主集同步到 723。
# M5-4e：1218 → **1222**（**+4** = `AiPromptConfigViewModelTest`）。主集同步到 727。
# M5-5a：1222 → **1226**（**+4** = `AiConfigViewModelTest`）。主集同步到 731。
# M5-5b：1226 → **1230**（**+4** = `AiModelEditViewModelTest`）。主集同步到 735。
BASELINE_ALL = 1230


def tally(d: pathlib.Path):
    tests = failures = errors = skipped = 0
    files = sorted(d.glob("*.xml")) if d.is_dir() else []
    if not files:
        return 0, 0, 0, 0, 0
    for f in files:
        root = ET.parse(f).getroot()
        tests += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
    return tests, failures, errors, skipped, len(files)


def main() -> int:
    main_total = all_total = 0
    bad = []
    missing = []
    for label, (rel, in_main) in RESULT_DIRS.items():
        t, f, e, s, n = tally(ROOT / rel)
        if n == 0:
            missing.append(label)
            print(f"{label:38s} *** 没有结果 XML，是不是没跑？")
            continue
        if in_main:
            main_total += t
        all_total += t
        flag = ""
        if f or e:
            bad.append(label)
            flag = f"  <<< fail={f} err={e}"
        print(f"{label:38s} tests={t:5d}  fail={f}  err={e}  skip={s}  files={n}{flag}")

    print("-" * 78)
    m_ok = "OK" if main_total == BASELINE_MAIN else f"偏离 {main_total - BASELINE_MAIN:+d}"
    a_ok = "OK" if all_total == BASELINE_ALL else f"偏离 {all_total - BASELINE_ALL:+d}"
    print(f"主验证集合计 = {main_total:5d}  （基线 {BASELINE_MAIN}） {m_ok}")
    print(f"全量合计     = {all_total:5d}  （基线 {BASELINE_ALL}） {a_ok}")

    ok = True
    if missing:
        print(f"\n!!! {len(missing)} 个结果目录为空 —— 计数不可信")
        ok = False
    if bad:
        print(f"!!! 存在失败用例: {', '.join(bad)}")
        ok = False
    if main_total != BASELINE_MAIN or all_total != BASELINE_ALL:
        print("!!! 与基线不一致：若是有意变更请写进提交文案并做变异验证，否则查回退")
        ok = False
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
