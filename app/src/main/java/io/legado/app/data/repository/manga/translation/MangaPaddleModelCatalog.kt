package io.legado.app.data.repository.manga.translation

import io.legado.app.domain.model.manga.MangaLocalOcrEngine

/** Revisions and checksums are pinned; changing weights requires a new storage/cache revision. */
internal object MangaPaddleModelCatalog {
    private const val V5_DET = "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/e6f4fa85f00e168c862bc462aebca69eef9b3d3d"
    private const val V5_REC = "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/ed152b8b495f84de93cda5709d768548a9127622"
    private const val V6_DET = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/28fe5895c24fd108c19eb3e8479f4ab385fbfc62"
    private const val V6_SMALL = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/b8f84f0b80c529de40b4fbb3544b84fa7233a513"
    private const val V6_MEDIUM = "https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_onnx/resolve/50c7eacafc52fa7bcf4194e8cd08e46f8558504b"
    private const val V6_MANGA = "https://huggingface.co/fumetodev/PP-OCRv6_small_rec_manga_ONNX/resolve/1ef01f78c59f6f66389c9722fd2d0ab761680ea9"
    private val v6Det = MangaOcrModelFile("det.onnx", "$V6_DET/inference.onnx", 9_880_512, "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e")
    private val v6Keys = MangaOcrModelFile("keys.yml", "$V6_SMALL/inference.yml", 150_579, "ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1")

    private val v5Det = MangaOcrModelFile("det.onnx", "$V5_DET/inference.onnx", 4_826_518, "a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d")

    val files: Map<MangaLocalOcrEngine, List<MangaOcrModelFile>> = mapOf(
        MangaLocalOcrEngine.PADDLE_V5 to listOf(
            MangaOcrModelFile("det.onnx", "$V5_DET/inference.onnx", 4_826_518, "a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d"),
            MangaOcrModelFile("rec.onnx", "$V5_REC/inference.onnx", 16_534_782, "da72dc72ca4dc220df0dfde68c1dedc31c58d3e76a25871122e5056227d50092"),
            MangaOcrModelFile("keys.yml", "$V5_REC/inference.yml", 148_345, "5dfeb2777f6d0db8177d8128a8acfcf6e6276dc4ac73ea3bf0dc06d6a5e85d8e"),
        ),
        MangaLocalOcrEngine.PADDLE_V5_KOREAN to listOf(v5Det,
            MangaOcrModelFile("rec.onnx", "https://huggingface.co/PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx/resolve/5c6f574b8e2230adf4287b33e736d71b9fabd28e/inference.onnx", 13_418_787, "92f0b7785e64fc9090106a241cf4c1eb97472824558272751b88a2a4476d3a08"),
            MangaOcrModelFile("keys.yml", "https://huggingface.co/PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx/resolve/5c6f574b8e2230adf4287b33e736d71b9fabd28e/inference.yml", 96_039, "f757fa1c40e99edcf27e9cce879b93eb2a51fa46f5ef39095689b8c37dd75998")),
        MangaLocalOcrEngine.PADDLE_V5_ESLAV to listOf(v5Det,
            MangaOcrModelFile("rec.onnx", "https://huggingface.co/PaddlePaddle/eslav_PP-OCRv5_mobile_rec_onnx/resolve/9a32171fc5718746875e1a261818884517975013/inference.onnx", 7_887_627, "b3018ef2b09a0250b6e0c8e871c927098363e5fd4df890cc68e8358eb0aaf1bd"),
            MangaOcrModelFile("keys.yml", "https://huggingface.co/PaddlePaddle/eslav_PP-OCRv5_mobile_rec_onnx/resolve/9a32171fc5718746875e1a261818884517975013/inference.yml", 4_538, "025039bac23eb4a308efcefa4d58eab3af440767815c6ba6938468bf6353ee5a")),
        MangaLocalOcrEngine.PADDLE_V6_TINY to listOf(
            MangaOcrModelFile("det.onnx", "https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_det_onnx/resolve/2ba1506c0380b8f0b03dd142459aac66d4421f6c/inference.onnx", 1_780_590, "193bab7a04fca699a6c82e6abb5b81bdb28177f0abd4062552b04908dafb19f8"),
            MangaOcrModelFile("rec.onnx", "https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_rec_onnx/resolve/2612ab37152ae0a677521bae4e1e3d4fb4cf7c30/inference.onnx", 4_462_639, "9ef676d6ed3c88256a2d92c640c44f25b0c40947e111b14b8be8f594091563e6"),
            MangaOcrModelFile("keys.yml", "https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_rec_onnx/resolve/2612ab37152ae0a677521bae4e1e3d4fb4cf7c30/inference.yml", 55_571, "66170210bad538e83fff3c4a3867e547d6bf20b50d64b20347c4b913f3034ea1")),
        MangaLocalOcrEngine.PADDLE_V6_SMALL to listOf(v6Det,
            MangaOcrModelFile("rec.onnx", "$V6_SMALL/inference.onnx", 21_159_378, "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"), v6Keys),
        MangaLocalOcrEngine.PADDLE_V6_MEDIUM to listOf(v6Det,
            MangaOcrModelFile("rec.onnx", "$V6_MEDIUM/inference.onnx", 76_554_979, "9c09abf0957f7968c7586464b7397b84ad2387a0497a351af40e9acc71b673ba"),
            MangaOcrModelFile("keys.yml", "$V6_MEDIUM/inference.yml", 150_580, "991b700facf5b50a7de193468207d5f4255b538dde0d312ae3b7c7a9b6873129")),
        MangaLocalOcrEngine.PADDLE_V6_MANGA to listOf(v6Det,
            MangaOcrModelFile("rec.onnx", "$V6_MANGA/ppocr-rec-v6-small-manga.onnx", 21_143_614, "c5cc5038a98c3df3e2d37de5716f603e2b0bcd3536c74078fdd91876a48a25ef"), v6Keys),
    )

    fun directory(engine: MangaLocalOcrEngine): String = when (engine) {
        MangaLocalOcrEngine.ML_KIT -> error("ML Kit has no downloadable model set")
        MangaLocalOcrEngine.PADDLE_V5_KOREAN -> "pp-ocrv5-korean-v1"
        MangaLocalOcrEngine.PADDLE_V5_ESLAV -> "pp-ocrv5-eslav-v1"
        MangaLocalOcrEngine.PADDLE_V6_TINY -> "pp-ocrv6-tiny-v1"
        MangaLocalOcrEngine.PADDLE_V5 -> "pp-ocrv5-mobile-v1" // Keep existing installations.
        MangaLocalOcrEngine.PADDLE_V6_SMALL -> "pp-ocrv6-small-v1"
        MangaLocalOcrEngine.PADDLE_V6_MEDIUM -> "pp-ocrv6-medium-v1"
        MangaLocalOcrEngine.PADDLE_V6_MANGA -> "pp-ocrv6-manga-v1"
    }

    fun supports(engine: MangaLocalOcrEngine, language: String): Boolean = engine.downloadable && language in engine.sourceLanguages
}
