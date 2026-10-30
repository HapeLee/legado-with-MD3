#include <jni.h>
#include <memory>
#include <string>
#include <vector>
#include "sentencepiece_processor.h"

using Processor = sentencepiece::SentencePieceProcessor;
static void fail(JNIEnv* env, const std::string& message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
}
static std::string bytes(JNIEnv* env, jbyteArray value) {
    std::string text(env->GetArrayLength(value), '\0');
    env->GetByteArrayRegion(value, 0, text.size(), reinterpret_cast<jbyte*>(text.data()));
    return text;
}
static jbyteArray array(JNIEnv* env, const std::string& text) {
    auto value = env->NewByteArray(text.size());
    if (value) env->SetByteArrayRegion(value, 0, text.size(), reinterpret_cast<const jbyte*>(text.data()));
    return value;
}
extern "C" JNIEXPORT jlong JNICALL
Java_io_legado_app_data_repository_manga_Small100SentencePiece_create(JNIEnv* env, jobject, jstring path) {
    try {
        auto processor = std::make_unique<Processor>();
        const char* raw = env->GetStringUTFChars(path, nullptr);
        auto status = processor->Load(raw);
        env->ReleaseStringUTFChars(path, raw);
        if (!status.ok()) { fail(env, status.ToString()); return 0; }
        return reinterpret_cast<jlong>(processor.release());
    } catch (const std::exception& e) { fail(env, e.what()); return 0; }
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_legado_app_data_repository_manga_Small100SentencePiece_encodeNative(JNIEnv* env, jobject, jlong handle, jbyteArray text) {
    try {
        std::vector<std::string> pieces;
        auto status = reinterpret_cast<Processor*>(handle)->Encode(bytes(env, text), &pieces);
        if (!status.ok()) { fail(env, status.ToString()); return nullptr; }
        auto result = env->NewObjectArray(pieces.size(), env->FindClass("[B"), nullptr);
        for (size_t i = 0; result && i < pieces.size(); ++i) {
            auto value = array(env, pieces[i]);
            env->SetObjectArrayElement(result, i, value);
            env->DeleteLocalRef(value);
        }
        return result;
    } catch (const std::exception& e) { fail(env, e.what()); return nullptr; }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_legado_app_data_repository_manga_Small100SentencePiece_decodeNative(JNIEnv* env, jobject, jlong handle, jobjectArray input) {
    try {
        std::vector<std::string> pieces;
        for (jsize i = 0; i < env->GetArrayLength(input); ++i) {
            auto value = static_cast<jbyteArray>(env->GetObjectArrayElement(input, i));
            pieces.push_back(bytes(env, value));
            env->DeleteLocalRef(value);
        }
        std::string text;
        auto status = reinterpret_cast<Processor*>(handle)->Decode(pieces, &text);
        if (!status.ok()) { fail(env, status.ToString()); return nullptr; }
        return array(env, text);
    } catch (const std::exception& e) { fail(env, e.what()); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_io_legado_app_data_repository_manga_Small100SentencePiece_destroy(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<Processor*>(handle);
}
