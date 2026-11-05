# 仅 full 产品（内置本地推理）需要，由 `engine=full` flavor 挂载。
# ONNX Runtime JNI constructs Java tensor/metadata classes by their original names.
-keep class ai.onnxruntime.** { *; }
