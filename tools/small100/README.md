# SMaLL-100 专用 ONNX Runtime

本目录维护漫画本地文字翻译使用的 CPU 运行库。检测、OCR、去字继续由 Yakuyomi/NCNN 处理，
不参与这里的裁剪。原来的四个 SMaLL-100 模型文件、下载地址与摘要保持不变。

运行库基于 ONNX Runtime **1.31.1**，源码固定为
`6af1a932e2286d5c58166085d24c68783033edee`。使用 `MinSizeRel`、LTO、静态 C++ 运行库，
只构建 Android API 26 / arm64-v8a CPU 推理。
裁剪未使用的算子内核、传统机器学习算子、内置生成算子、遥测组件和 RTTI；应用自己的贪心解码不依赖内置生成算子。
保留普通 ONNX 文件加载、异常处理、动态形状、量化、控制流和图优化。

`required-operators.config` 合并两份模型的原始计算图和 BASIC、EXTENDED、ALL 三种 CPU 优化结果，
递归检查 merged decoder 的两个 `If` 分支。包含 29 种原始节点类型和 3 种实际生成的融合算子。
上游裁剪工具还会保留图布局转换要求的基础算子，因此不能把这个清单误认为二进制中全部内核的数量。
更换模型、ORT 或图优化设置时必须重新生成和验证清单，并更新 `Small100Protocol.VERSION`，重新验证本地译图缓存。

`app/libs/onnxruntime-small100-1.31.1.aar` 使用官方同版本 AAR 的 Java 类和 R8 规则，
运行库及 JNI 接口均从同一版本源码构建。JNI 不定义 `USE_NNAPI`，避免引用 CPU 包未导出的 NNAPI 符号。
移除其他 ABI 的原生文件，并补入上游许可。
漫画翻译原本就明确限制为 arm64；32 位安装包继续提供阅读功能。
同目录 JSON 记录构建来源和摘要，`.aar.sha256` 由 `:app:verifySmall100Runtime` 在构建前检查。

2026-10-30 构建产物：AAR 为 4,210,419 字节，arm64 运行库为 9,908,168 字节，
JNI 为 82,448 字节。相较官方 arm64 运行库的 34,208,672 字节，运行库本体减少约 71%。
静态检查通过 179 个 JNI 方法导出、JNI 所需运行库符号、16 KiB ELF 对齐及动态依赖检查；
除配套 `libonnxruntime.so` 外，两份库只依赖 Android 系统库。
这些检查不代表 Android 实际模型推理已通过。

## 重新构建

需要 JDK 21、Python 3.12、Git、CMake 3.31.6、Ninja 1.11.1、Android SDK 和 NDK 28.2.13676358。
首次 CMake 配置会下载并校验源码指定的依赖。Windows 建议使用短路径，例如 `D:/ort-small100-1311`。
无需 Visual Studio：Android 使用 NDK Clang 及 Ninja，JDK 生成匹配 JNI 头，官方 AAR 提供相同版本的 Java 类。

1. 在独立 Python 环境运行 `python -m pip install -r tools/small100/requirements-build.txt`。
2. 克隆 `https://github.com/microsoft/onnxruntime.git`，检出上述提交，执行
   `git submodule update --init cmake/external/onnx`。
3. 准备清单中的两份 ONNX 模型；脚本先校验固定大小和 SHA-256。
4. 下载 Maven Central 的 `com.microsoft.onnxruntime:onnxruntime-android:1.31.1` AAR。
   脚本要求其 SHA-256 为 `d4d50660300ed2c4f38ffdfd5ab5bfa3a22f995f282dda24f42ea3ea55467b49`。
5. 在项目根目录运行，替换示例路径：

```powershell
python tools/small100/build_runtime.py `
  --source D:/ort-small100-1311/src `
  --build-dir D:/ort-small100-1311/reduced `
  --models D:/models/small100 `
  --android-sdk C:/Android/Sdk `
  --android-ndk C:/Android/Sdk/ndk/28.2.13676358 `
  --cmake C:/tools/cmake/bin/cmake.exe `
  --ninja C:/tools/ninja/ninja.exe `
  --base-aar D:/downloads/onnxruntime-android-1.31.1.aar `
  --jobs 4
```

脚本依次生成清单、与仓库清单比较、编译、打包并生成摘要。`--phase prepare|build|package` 可单独运行某一步。
生成的新 AAR 和摘要必须一起保存。然后执行 `verifyConfigArchitecture`、Debug 与 Release 构建，
检查最终 APK 的体积、签名、16 KiB 对齐和 native 依赖。
比较体积时使用全新打包产物，避免增量 APK 保留旧 native 文件产生的空洞。

构建与静态检查不能替代 arm64 推理验收。应使用原始模型验证编码器、merged decoder 首步及 KV cache，
比较完整版与裁剪版的输出，再验证 Android JNI、离线翻译、取消与资源释放。

## Release JNI 回归检查

应用的 `proguard-rules.pro` 必须保留 `ai.onnxruntime.**` 的类名和成员。
1.31.1 AAR 自带规则只保护遥测类；JNI 会用字符串查找 `TensorInfo` 等类及构造函数，
单独保留 native 方法不够。丢失类时 Android 在 `GetMethodID` 中触发 SIGABRT，应用异常处理器收不到日志。

Release 验收需核对最终 R8 映射中的 ORT 类名与 JNI 构造函数。2026-10-30 已在 vivo V2196A / Android 15 验证 JNI 张量计算通路；完整页面翻译仍需另行验收。
