# Optional comic text-removal models

Weights are not bundled in the APK. They are downloaded only after an explicit user action,
verified by fixed byte count and SHA-256, and stored in private no-backup storage.

## MI-GAN

- Authors: Picsart AI Research; Andranik Sargsyan et al.
- Project: https://github.com/Picsart-AI-Research/MI-GAN
- Weights license: MIT, copyright 2024 Picsart AI Research; see MI-GAN-LICENSE.txt.
- ONNX pipeline: https://huggingface.co/andraniksargsyan/migan
- Revision: 406830d0fa60666da0071c342ad2fbc8f30c5c64
- File: migan_pipeline_v2.onnx (28,079,181 bytes).
- SHA-256: 6f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b

## LaMa

- Authors: Roman Suvorov et al.; original project https://github.com/advimman/lama
- License: Apache-2.0; see LaMa-LICENSE.txt.
- Export: https://huggingface.co/Carve/LaMa-ONNX, based on https://github.com/Carve-Photos/lama
- Model card declares Apache-2.0 and identifies this as an ONNX port of big-lama.
- Revision: c3c0c9e468934d62e79c329e35d82dd09ff8c444
- File: lama_fp32.onnx (208,044,816 bytes), opset 17, fixed 512x512.
- SHA-256: 1faef5301d78db7dda502fe59966957ec4b79dd64e16f03ed96913c7a4eb68d6

This is the general LaMa model, not an AnimeLaMa or manga-specific fine-tune.
The application adapts image/mask tensors and composites only selected source pixels.
