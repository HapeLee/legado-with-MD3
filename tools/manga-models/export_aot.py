"""Export the selected manga AOT checkpoint, then verify CPU parity before import.

Run in an isolated Python environment with torch, numpy, onnx and onnxruntime.
No model or downloaded source is placed in Android assets.
"""
import argparse
import ast
import hashlib
import json
from pathlib import Path
import urllib.request

REVISION = "d6de6ab62a7be6afc0d07a3184149cdefbcd32f2"
CHECKPOINT_SHA = "878d541c68648969bc1b042a6e997f3a58e49b6c07c5636ad55130736977149f"
SOURCE = f"https://raw.githubusercontent.com/zyddnys/manga-image-translator/{REVISION}/manga_translator/inpainting/inpainting_aot.py"
WEIGHTS = "https://github.com/zyddnys/manga-image-translator/releases/download/beta-0.3/inpainting.ckpt"


def fetch(url, target):
    if not target.exists():
        temporary = target.with_suffix(target.suffix + ".part")
        try:
            with urllib.request.urlopen(url, timeout=60) as response, temporary.open("wb") as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="External artifact directory, outside the APK")
    args = parser.parse_args()
    directory = args.directory.resolve()
    directory.mkdir(parents=True, exist_ok=True)
    source = directory / "upstream_aot.py"
    checkpoint = directory / "inpainting.ckpt"
    fetch(SOURCE, source)
    fetch(WEIGHTS, checkpoint)
    assert hashlib.file_digest(checkpoint.open("rb"), "sha256").hexdigest() == CHECKPOINT_SHA, "Checkpoint SHA-256 mismatch"

    import numpy as np
    import onnx
    import onnxruntime as ort
    import torch
    import torch.nn as nn
    import torch.nn.functional as F

    # Load only the generator definitions, avoiding the upstream application's imports.
    names = {"relu_nf", "LambdaLayer", "ScaledWSConv2d", "ScaledWSTransposeConv2d",
             "GatedWSConvPadded", "GatedWSTransposeConvPadded", "my_layer_norm", "AOTBlock", "AOTGenerator"}
    tree = ast.parse(source.read_text(encoding="utf-8"))
    tree.body = [node for node in tree.body if isinstance(node, (ast.ClassDef, ast.FunctionDef)) and node.name in names]
    scope = {"torch": torch, "nn": nn, "F": F, "np": np, "List": list, "Optional": __import__("typing").Optional}
    exec(compile(tree, str(source), "exec"), scope)

    # The fixed deployment size uses the layer's configured output_padding. This avoids
    # upstream's private _output_padding call, whose signature varies between torch releases.
    def transpose(self, x, output_size=None):
        assert output_size is None
        return F.conv_transpose2d(x, self.get_weight(), self.bias, self.stride,
                                 self.padding, self.output_padding, self.groups, self.dilation)
    scope["ScaledWSTransposeConv2d"].forward = transpose
    model = scope["AOTGenerator"]().eval()
    state = torch.load(checkpoint, map_location="cpu", weights_only=True)
    model.load_state_dict(state.get("model", state), strict=True)
    torch.set_num_threads(2)
    torch.manual_seed(20261028)
    image = torch.rand(1, 3, 512, 512) * 2 - 1
    mask = torch.zeros(1, 1, 512, 512)
    mask[:, :, 180:330, 200:300] = 1
    masked = image * (1 - mask)
    output = directory / "manga-aot-512-v1.onnx"
    temporary = directory / "manga-aot-512-v1.tmp.onnx"
    try:
        with torch.inference_mode():
            torch.onnx.export(model, (masked, mask), temporary, input_names=["image", "mask"],
                              output_names=["repaired"], opset_version=17, dynamo=False)
        graph = onnx.load(temporary)
        onnx.helper.set_model_props(graph, {"legado.contract": "manga-aot-512-v1",
            "legado.checkpoint.sha256": CHECKPOINT_SHA, "legado.source.revision": REVISION})
        onnx.checker.check_model(graph)
        onnx.save(graph, temporary)
        session = ort.InferenceSession(str(temporary), providers=["CPUExecutionProvider"])
        errors = []
        with torch.inference_mode():
            for seed in range(3):
                torch.manual_seed(seed)
                sample = (torch.rand_like(image) * 2 - 1) * (1 - mask)
                expected = model(sample, mask).numpy()
                actual = session.run(None, {"image": sample.numpy(), "mask": mask.numpy()})[0]
                np.testing.assert_allclose(actual, expected, atol=2e-3, rtol=2e-3)
                errors.append(float(np.max(np.abs(actual - expected))))
        temporary.replace(output)
        report = {"revision": REVISION, "checkpoint_sha256": CHECKPOINT_SHA,
                  "onnx_sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "bytes": output.stat().st_size,
                  "opset": 17, "torch": torch.__version__, "onnx": onnx.__version__, "ort": ort.__version__,
                  "max_errors": errors, "device_quality_verified": False}
        (directory / "export-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report, indent=2))
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
