# 漫画图像模型制品

权重不进入 Git、Android assets 或 APK；在工作区外准备制品。

## CTD 与 MangaLens

应用目录中的 `MangaImageModelRepository` 固定分发地址与 SHA-256。下载和本地导入走同一安装流程。
CTD 使用上游 beta-0.3 的 `comictextdetector.pt.onnx`；气泡模型使用社区的 1024 ONNX 导出。
导入任意同名文件不会绕过校验。两者尚未在本次开发环境成功取得完整权重，不能把张量适配代码视为真实模型验证。

## 漫画版 AOT 导出

在独立 Python 环境安装 `torch`（CPU）、`numpy`、`onnx`、`onnxruntime`，然后运行：

```powershell
python tools/manga-models/export_aot.py D:/ModelArtifacts/legado-aot
```

脚本锁定 manga-image-translator revision 和 `inpainting.ckpt` SHA-256，只加载生成器定义，
使用 `weights_only=True` 读取权重。固定 512 输入、FP32、opset 17；掩膜 1 表示擦除，
图像 RGB 归一化到 [-1,1]，掩膜内输入置零。脚本适配上游转置卷积对 PyTorch 私有 API 的依赖，
保留配置的 output_padding；导出后用三个固定随机种子对照原框架数值，失败不发布 ONNX。

产物为 `manga-aot-512-v1.onnx` 和 `export-report.json`。导出报告记录工具版本、内容哈希与数值误差；
这不是漫画视觉质量、Android 兼容性或性能报告。成功导出后通过应用文件选择器导入 ONNX。

AOT 没有已发布、经本项目验证的 ONNX 下载地址，因此界面只提供导入，不下载训练权重充当部署模型。
应用验证专用契约元数据、checkpoint 标识、输入输出、实际文件哈希与 CPU 试运行；
元数据可由文件作者修改，不能单独证明权重来源。自行导入时应保留脚本产生的报告并核对内容哈希。
应用以实际导入文件的 SHA-256 区分缓存，不把所有 AOT 导出视为同一制品。

## 来源与验证限制

- CTD：https://github.com/dmMaze/comic-text-detector
- 管线及 AOT：https://github.com/zyddnys/manga-image-translator
- 气泡原权重：https://huggingface.co/huyvux3005/manga109-segmentation-bubble
- 气泡 ONNX：https://huggingface.co/mednasserallah/manga109-segmentation-bubble-onnx

部署前分别核对代码、模型、数据集及转换工具的适用条款；不把第三方仓库的许可证标签当作全部权利证明。
本轮受下载连接超时／EOF 阻塞，未完成 Python 推理环境、AOT 实际导出、数值对照或三个模型的真实推理。
脚本仅完成 Python 语法与命令行入口检查。没有发布 AOT 权重，也没有进行 Android 真机验收。
