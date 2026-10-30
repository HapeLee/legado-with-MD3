package io.legado.app.data.repository.manga

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import li.joye.yakuyomi.engine.Translator
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

internal object Small100Protocol {
    const val VERSION = "small100-int8-d66e226-sp022-greedy-kv-v2-ort1311-reduced1"
    private val languages = ("af am ar ast az ba be bg bn br bs ca ceb cs cy da de el en es et fa ff fi fr fy ga gd gl gu ha he hi hr ht hu hy id ig ilo is it ja jv ka kk km kn ko lb lg ln lo lt lv mg mk ml mn mr ms my ne nl no ns oc or pa pl ps pt ro ru sd si sk sl so sq sr ss su sv sw ta th tl tn tr uk ur uz vi wo xh yi yo zh zu").split(' ')
    fun languageId(language: String): Long {
        val normalized = language.lowercase().replace('_', '-').substringBefore('-')
        val index = languages.indexOf(normalized)
        require(index >= 0) { "SMaLL-100 不支持目标语言：$language" }
        return 128004L + index
    }

    /** Split text, never token IDs: each chunk must get its own SentencePiece boundary marker. */
    fun split(text: String, countTokens: (String) -> Int): List<String> {
        if (countTokens(text) <= 1022) return listOf(text)
        val middle = text.offsetByCodePoints(0, text.codePointCount(0, text.length) / 2)
        require(middle > 0 && middle < text.length) { "文字无法拆分" }
        val punctuation = text.indices.filter { it > 0 && text[it - 1] in "。！？!?；;\n" }
            .minByOrNull { kotlin.math.abs(it - middle) }
        val boundary = punctuation ?: middle
        return split(text.substring(0, boundary), countTokens) + split(text.substring(boundary), countTokens)
    }
}

/** Owned by the serial page processor. ORT tensors and the tokenizer never escape a page call. */
internal class Small100MangaTranslator(directory: File, language: String) : Translator, Closeable {
    private val target = Small100Protocol.languageId(language)
    private val environment = OrtEnvironment.getEnvironment()
    private val vocabulary = JSONObject(File(directory, "vocab.json").readText()).let { json ->
        json.keys().asSequence().associateWith { json.getInt(it) }
    }
    private val piecesById = vocabulary.entries.associate { it.value to it.key }
    private val tokenizer = Small100SentencePiece(File(directory, "sentencepiece.bpe.model").path)
    private val encoder: OrtSession
    private val decoder: OrtSession

    init {
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(2)
            // Dynamic sequence/KV sizes otherwise retain unnecessarily large CPU memory arenas.
            options.setMemoryPatternOptimization(false)
            encoder = environment.createSession(File(directory, "encoder_model_quantized.onnx").path, options)
            try {
                decoder = environment.createSession(File(directory, "decoder_model_merged_quantized.onnx").path, options)
            } catch (error: Throwable) { encoder.close(); throw error }
        } catch (error: Throwable) { tokenizer.close(); throw error }
        finally { options.close() }
    }

    override suspend fun translate(queries: List<String>): List<String> {
        val translated = linkedMapOf<String, String>()
        for (query in queries.distinct()) {
            currentCoroutineContext().ensureActive()
            translated[query] = if (query.isBlank()) query else Small100Protocol.split(query) { tokenizer.encode(it).size }
                .map { decode(tokenizer.encode(it)) }
                .joinToString(if (target == 128102L || target == 128046L) "" else " ")
        }
        return queries.map { translated.getValue(it) }
    }

    private fun ids(values: LongArray): OnnxTensor = OnnxTensor.createTensor(environment,
        LongBuffer.wrap(values), longArrayOf(1, values.size.toLong()))

    private suspend fun decode(pieces: List<String>): String {
        val input = longArrayOf(target) + pieces.map { (vocabulary[it] ?: 3).toLong() }.toLongArray() + 2L
        ids(input).use { inputIds ->
            ids(LongArray(input.size) { 1L }).use { mask ->
                encoder.run(mapOf("input_ids" to inputIds, "attention_mask" to mask)).use { encoded ->
                    val hidden = encoded[0] as OnnxTensor
                    val emptyCaches = decoder.inputInfo.filterKeys { it.startsWith("past_key_values.") }.mapValues { (_, node) ->
                        val shape = (node.info as TensorInfo).shape
                        require(shape.size == 4) { "SMaLL-100 KV cache 形状不兼容" }
                        OnnxTensor.createTensor(environment, FloatBuffer.allocate(0), longArrayOf(1, 16, 0, 64))
                    }
                    var first: OrtSession.Result? = null
                    var latest: OrtSession.Result? = null
                    val output = mutableListOf<Int>()
                    var token = 2L
                    try {
                        repeat(255) {
                            currentCoroutineContext().ensureActive()
                            ids(longArrayOf(token)).use { decoderIds ->
                                OnnxTensor.createTensor(environment, booleanArrayOf(latest != null)).use { branch ->
                                    val inputs = mutableMapOf("input_ids" to decoderIds,
                                        "encoder_hidden_states" to hidden, "encoder_attention_mask" to mask,
                                        "use_cache_branch" to branch)
                                    emptyCaches.forEach { (name, empty) ->
                                        val present = name.replace("past_key_values.", "present.")
                                        val owner = if (name.contains(".encoder.")) first else latest
                                        inputs[name] = owner?.get(present)?.orElse(null) as? OnnxTensor ?: empty
                                    }
                                    val next = decoder.run(inputs)
                                    if (latest !== first) latest?.close()
                                    latest = next
                                    if (first == null) first = next
                                    val logits = next.get("logits").get() as OnnxTensor
                                    val scores = logits.floatBuffer
                                    val vocabSize = logits.info.shape.last().toInt()
                                    val start = scores.limit() - vocabSize
                                    var best = 0
                                    for (id in 1 until vocabSize) {
                                        if (scores.get(start + id) > scores.get(start + best)) best = id
                                    }
                                    token = best.toLong()
                                    if (token == 2L) {
                                        val text = tokenizer.decode(output.filter { it > 3 && it < 128004 }
                                            .map { piecesById[it] ?: "<unk>" }).trim()
                                        require(text.isNotEmpty()) { "SMaLL-100 返回空译文" }
                                        return text
                                    }
                                    output += best
                                }
                            }
                        }
                        error("SMaLL-100 译文超出 256 token 限制，请重试或使用 AI 翻译")
                    } finally {
                        if (latest !== first) latest?.close()
                        first?.close()
                        emptyCaches.values.forEach { it.close() }
                    }
                }
            }
        }
    }

    override fun close() {
        try { decoder.close() } finally { try { encoder.close() } finally { tokenizer.close() } }
    }
}
