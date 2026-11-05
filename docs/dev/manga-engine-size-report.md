# 漫画本地引擎体积报告

2026-10-31。范围：arm64-v8a Release（不含 universal），目标是 full 单包 ≤ 50 MB，
达成后不再需要 `core/full` 分包，也不进入扩展包路线。

> 产品标记 2026-11-05 由 `full` 改为 `comic`（中文名「漫画增强版」）。本文是当时的实测记录，
> 正文沿用 `full` 原称；Gradle 任务名按现名给出。

> **定案（2026-10-31，同日）**：产品决定同时发布两个包，路线 B 不再实施。
> `core`（20.61 MiB）作为轻量版，`full`（68.90 MiB）作为内置本地引擎的完整版；
> 体积门槛从"单包 ≤ 50 MB"改为"两包各自不劣化"。本报告此后只作为两者的体积基线与
> 未来评估依据，不再驱动分包决策。下文 §5、§6 的路线与建议保留原始判断以示来源。

## 1. 结论

1. 现状 full arm64 Release = **72,248,654 B（72.25 MB）**，其中 **71%** 是本地推理栈与权重。
2. **ML Kit 不是问题**：14 MiB（so + 模型）是同类应用的固定成本，参照项目同样支付。
3. 真正的体积大头是 **ONNX Runtime 27.31 MiB**，其次是内置的检测模型 11.1 MB。
4. 换掉 ORT 后即可单独满足 50 MB 目标：测算 **≈ 45~49 MB**；再把检测模型外置可到 **≈ 39~42 MB**。
5. 因此**分包（core/full）不是达成目标所必需的**，但它目前是唯一能立刻交付的路径，建议在替换推理栈落地前保留。
6. 定案后 B 不实施、A 暂缓：A 的作用只是把 full 压向 50 MB，而 full 的卖点正是开箱离线，
   外置会让首次本地排版依赖联网，反而削弱两包的差异。

## 2. 外部参照

| | Yakuyomi | overlay-translator |
|---|---|---|
| 推理栈 | NCNN（唯一栈，纯 CPU） | ONNX Runtime + ML Kit + llama.cpp |
| 检测/OCR/去字 | DBNet + 48px CTC + AOT-GAN，全部 NCNN | PaddleOCR(ONNX) / manga-ocr(ONNX) / ML Kit |
| 模型 | 250 MB 全部运行时下载 | 205 MB 日漫 OCR 等全部下载/导入 |
| 体积相关表述 | 「移除 ONNX Runtime 单省约 70 MB」 | 「本地模型不包含在 APK 中」 |
| 分发 | arm64 + universal | **只发 arm64** |

两条可执行经验：

- **权重一律不内置**。两家 APK 内都没有模型权重。
- **推理栈越少越省**。Yakuyomi 把 5 个模型统一到 NCNN，只维护一个 runtime。

补充事实：本项目的 `MangaBubbleDetector` 头部已注明「RT-DETR contract and buffering adapted from
ciddwd/overlay-translator (64a7aa2)」，即检测器协议已经借鉴过该项目；这次借鉴的是体积策略。

## 3. 实测数据

### 3.1 我们的产物（arm64-v8a Release，压缩后口径）

| 条目 | 体积 |
|---|---:|
| `lib/arm64-v8a/libonnxruntime.so` | **27.31 MiB**（stored，不压缩） |
| `lib/arm64-v8a/libmlkit_google_ocr_pipeline.so` | 10.55 MiB（stored） |
| `assets/manga-vision/detector-v4-s_int8.onnx` | 11.1 MB → 7.07 MiB |
| `assets/mlkit-google-ocr-models/`（4 语言） | 4.20 MiB |
| `lib/arm64-v8a/libarchive-jni.so` 等其余 native | 1.99 MiB |
| dex | 11.84 MiB |
| `resources.arsc` + `tc/s2t.txt` | 4.47 MiB |
| assets（web/vue、bg 等） | 1.71 MiB |
| **合计** | **72.25 MB** |

对照：core 变体 21.61 MB，两者 `resources.arsc` 只差 4 KB，说明拆分本身没有额外开销。

### 3.2 候选推理栈（外部实测）

| 项 | 数据 | 来源 |
|---|---|---|
| ncnn `arm64-v8a/lib/libncnn.a` | **7.56 MiB**（静态库，未链接、未 strip） | 下载 `ncnn-20261020-android.zip`（26.38 MiB，含 5 ABI 静态库与头文件）实测 |
| ncnn 链接后 JNI so 预估 | 2~5 MiB | 需自建实测（`--gc-sections` + strip 后通常显著小于静态库） |
| Paddle-Lite `armv8 clang c++_shared` 预编译包 | 4.54 MiB（tar.gz，含 arm64 so + 头文件） | GitHub Release `v2.14-rc` 实测 |
| ORT `--minimal_build` 后 so | 社区经验 1~3 MB；官方不给固定倍数 | 官方文档仅给方法，不给数字 |

注意：Paddle-Lite 只吃 Paddle 原生模型，而**我们的权重全部是 ONNX**（HuggingFace `PaddlePaddle/*_onnx`，
`MangaPaddleModelCatalog` 与内置 detector 均为 `.onnx`）。因此 Paddle-Lite 需要 ONNX→Paddle 转换（工具链不成熟），
**实际可行路线是 NCNN（onnx2ncnn）或自建 ORT minimal build**。

## 4. 目标测算

| 方案 | 变化 | 预计 full |
|---|---|---:|
| 现状 | — | 72.25 MB |
| A：检测模型外置 | −7.07 | 65.2 MB |
| B1：ORT minimal build（不改代码） | −27.31 + 3~6 | 44~48 MB |
| B2：NCNN 重写推理层 | −27.31 + 2~5 | 43~47 MB |
| B + A | 再 −7.07 | **36~41 MB** |

结论：**B 单独就足以满足 ≤ 50 MB**；A 是可叠加的额外收益。

## 5. 路线与取舍

### A. 检测模型外置（无 NDK 依赖，可立即交付）

- 状态：**暂缓**（2026-10-31 定案走分包；full 保留内置权重以维持开箱离线）。
  代码侧已具备"内置优先、缺失回落下载"的能力，将来要减 7.07 MB 只需托管权重、填 `URL`、删资源。
- 收益 −7.07 MB；代价是首次本地排版/AI 锚点前需要联网下载，并要处理离线降级与重试。
- 现有 `MangaOcrModelRepository` 已有 `.part` + 字节数 + SHA-256 校验，可直接复用下载与校验逻辑。

### B1. 自建 ORT minimal build（推荐先试）

- 优点：**不改 Kotlin 侧任何推理代码**，`ai.onnxruntime` Java API 与现有 `MangaPaddleOcr` /
  `MangaBubbleDetector` / `MangaLocalInpainter` 全部保持；精度理论上逐算子一致（只是裁掉未用算子）。
- 步骤：`create_reduced_build_config.py` 生成算子清单 → 模型转 `.ort` 格式 →
  `--minimal_build --include_ops_by_config` 构建 → 用产物 so 替换 AAR 内 so，Java 绑定继续用官方 jar。
- 风险：
  - minimal build **只接受 `.ort` 格式模型**，需要把 8 个 ONNX 权重全部转换并重新托管（URL + sha256 全变）。
  - PP-OCR 识别模型是**动态宽度**，minimal build 对动态 shape 支持受限，可能需要 `--minimal_build=extended`（体积更大）。
  - 算子清单必须覆盖融合后算子，否则运行时报 `Could not find an implementation`。

### B2. NCNN 重写（体积最优，工作量最大）

- Yakuyomi 的实际做法：`onnx2ncnn` + `ncnnoptimize` 转模型，写 C++ JNI 封装，Kotlin 侧只保留前后处理。
- 我们需要新写 JNI 层并替换 3 个推理点（detector / OCR det+rec / inpaint），相当于重写 `MangaPaddleOcr` 的推理部分。
- 动态 shape 支持好，so 更小；但没有真机就完全无法验证数值一致性。

### 关键环境约束（当前阻塞）

| 依赖 | 状态 |
|---|---|
| Android NDK | **缺失** |
| CMake | **缺失** |
| Python + onnx 工具链 | 未确认（B1 必需） |
| 真机 / 模拟器验证 | 当前不做真机 |

B1/B2 都需要先安装 NDK 与 CMake（GB 级下载），B1 还需要 Python 侧 ONNX 工具链与 ORT 源码构建。
本报告不假设这些环境已就绪。

## 6. 对分包决策的建议（已被文首定案取代）

用户条件是「full ≤ 50 MB 就不再分包」。按测算，50 MB **只能通过 B 达成**，A 单做仍差 15 MB。

原本的建议顺序：

1. **保留 `core/full` 变体**，作为 B 落地前的过渡交付形态（core 21.61 MB 已经可用）。
2. 先做 A（−7 MB，无环境依赖）。
3. 环境就绪后做 B1；若 B1 因动态 shape 或算子覆盖失败，再评估 B2。
4. B 验证通过后，把 `full` 作为唯一发布形态，`core` 降级为可选/停止发布，**不进入扩展包路线**。

**实际决定**：第 1 步成为终态——长期双包发布，A 暂缓，B 不实施。
两个产品的发布口径（命名、更新选包、体积基线）见
[漫画本地引擎迁移计划](manga-engine-extension-plan.md) 的「发布形态」一节。

## 7. 验证计划

| 项 | 方法 |
|---|---|
| 体积 | `assembleAppComicRelease` 后按条目统计（本报告 §3.1 的口径） |
| 数值一致性 | 用同一批页面在旧/新栈上跑 OCR，逐行比对（参照 Yakuyomi「9 页 242 行 241 行一致」的做法） |
| 检测一致性 | 对比气泡框数量与 IoU，设定可接受阈值 |
| 降级 | 离线、下载失败、模型校验失败三条路径都必须显式可见 |
| 性能 | 单页检测/OCR 耗时，骁龙 8 系参考值：检测 ≈0.8 s、OCR ≈1.25 s（Yakuyomi 口径） |
