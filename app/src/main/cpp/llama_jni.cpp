#include <jni.h>
#include <android/log.h>

#include "llama.h"

#include <algorithm>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

constexpr const char * kLogTag = "GrasslingLocalAI";
std::once_flag g_backendOnce;

struct EngineState {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    std::mutex mutex;

    ~EngineState() {
        if (context != nullptr) {
            llama_free(context);
        }
        if (model != nullptr) {
            llama_model_free(model);
        }
    }
};

void throwJava(JNIEnv * env, const char * message) {
    if (env->ExceptionCheck()) {
        return;
    }
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message);
        env->DeleteLocalRef(exceptionClass);
    }
}

std::string toUtf8(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return {};
    }

    const jchar * chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) {
        throw std::runtime_error("Could not read the input text.");
    }

    const jsize length = env->GetStringLength(value);
    std::string result;
    result.reserve(static_cast<size_t>(length) * 3);

    for (jsize i = 0; i < length; ++i) {
        uint32_t codePoint = chars[i];
        if (codePoint >= 0xD800 && codePoint <= 0xDBFF && i + 1 < length) {
            const uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                codePoint = 0x10000 + ((codePoint - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            }
        }

        if (codePoint <= 0x7F) {
            result.push_back(static_cast<char>(codePoint));
        } else if (codePoint <= 0x7FF) {
            result.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else if (codePoint <= 0xFFFF) {
            result.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else {
            result.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        }
    }

    env->ReleaseStringChars(value, chars);
    return result;
}

jstring fromUtf8(JNIEnv * env, const std::string & value) {
    std::vector<jchar> chars;
    chars.reserve(value.size());

    for (size_t i = 0; i < value.size();) {
        const auto first = static_cast<uint8_t>(value[i]);
        uint32_t codePoint = 0xFFFD;
        size_t count = 1;

        if (first <= 0x7F) {
            codePoint = first;
        } else if ((first & 0xE0) == 0xC0 && i + 1 < value.size()) {
            codePoint = ((first & 0x1F) << 6) | (static_cast<uint8_t>(value[i + 1]) & 0x3F);
            count = 2;
        } else if ((first & 0xF0) == 0xE0 && i + 2 < value.size()) {
            codePoint = ((first & 0x0F) << 12)
                    | ((static_cast<uint8_t>(value[i + 1]) & 0x3F) << 6)
                    | (static_cast<uint8_t>(value[i + 2]) & 0x3F);
            count = 3;
        } else if ((first & 0xF8) == 0xF0 && i + 3 < value.size()) {
            codePoint = ((first & 0x07) << 18)
                    | ((static_cast<uint8_t>(value[i + 1]) & 0x3F) << 12)
                    | ((static_cast<uint8_t>(value[i + 2]) & 0x3F) << 6)
                    | (static_cast<uint8_t>(value[i + 3]) & 0x3F);
            count = 4;
        }
        i += count;

        if (codePoint <= 0xFFFF) {
            chars.push_back(static_cast<jchar>(codePoint));
        } else {
            codePoint -= 0x10000;
            chars.push_back(static_cast<jchar>(0xD800 + (codePoint >> 10)));
            chars.push_back(static_cast<jchar>(0xDC00 + (codePoint & 0x3FF)));
        }
    }

    return env->NewString(chars.data(), static_cast<jsize>(chars.size()));
}

bool setBatchTokens(
        llama_batch_ext * batch,
        const llama_token * tokens,
        int32_t tokenCount,
        llama_pos firstPosition) {
    llama_batch_ext_clear(batch);

    for (int32_t i = 0; i < tokenCount; ++i) {
        const int32_t index = llama_batch_ext_add_token(batch, 0, tokens[i]);
        if (index < 0) {
            return false;
        }

        const llama_pos position = firstPosition + i;
        if (!llama_batch_ext_set_pos(batch, index, &position)
                || !llama_batch_ext_set_output_logits(batch, index, i == tokenCount - 1)) {
            return false;
        }
    }
    return true;
}

jlong nativeLoadModel(JNIEnv * env, jobject, jstring path, jint contextSize, jint threadCount) {
    try {
        const std::string modelPath = toUtf8(env, path);
        if (modelPath.empty()) {
            throw std::runtime_error("The model path is empty.");
        }

        std::call_once(g_backendOnce, [] {
            llama_log_set([](ggml_log_level level, const char * text, void *) {
                if (level >= GGML_LOG_LEVEL_ERROR && text != nullptr) {
                    __android_log_write(ANDROID_LOG_ERROR, kLogTag, text);
                }
            }, nullptr);
            llama_backend_init();
        });

        auto state = std::make_unique<EngineState>();
        llama_model_params modelParams = llama_model_default_params();
        modelParams.n_gpu_layers = 0;
        state->model = llama_model_load_from_file(modelPath.c_str(), modelParams);
        if (state->model == nullptr) {
            throw std::runtime_error("llama.cpp could not load the GGUF model.");
        }

        llama_context_params contextParams = llama_context_default_params();
        contextParams.n_ctx = static_cast<uint32_t>(contextSize);
        contextParams.n_batch = static_cast<uint32_t>(contextSize);
        contextParams.n_threads = std::max(1, static_cast<int>(threadCount));
        contextParams.n_threads_batch = std::max(1, static_cast<int>(threadCount));
        state->context = llama_init_from_model(state->model, contextParams);
        if (state->context == nullptr) {
            throw std::runtime_error("llama.cpp could not allocate the inference context.");
        }

        return reinterpret_cast<jlong>(state.release());
    } catch (const std::exception & error) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Model load failed: %s", error.what());
        throwJava(env, error.what());
        return 0;
    }
}

jstring nativeGenerate(
        JNIEnv * env,
        jobject,
        jlong handle,
        jstring promptValue,
        jint maxTokens,
        jfloat temperature,
        jfloat topP,
        jint topK,
        jfloat presencePenalty) {
    auto * state = reinterpret_cast<EngineState *>(handle);
    if (state == nullptr) {
        throwJava(env, "The local model has not been loaded.");
        return nullptr;
    }

    try {
        const std::string prompt = toUtf8(env, promptValue);
        std::lock_guard<std::mutex> lock(state->mutex);

        llama_memory_clear(llama_get_memory(state->context), true);
        const llama_vocab * vocab = llama_model_get_vocab(state->model);
        const int32_t requiredTokens = llama_tokenize(
                vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
        if (requiredTokens >= 0) {
            throw std::runtime_error("Could not calculate the prompt token count.");
        }

        std::vector<llama_token> promptTokens(static_cast<size_t>(-requiredTokens));
        const int32_t promptTokenCount = llama_tokenize(
                vocab,
                prompt.c_str(),
                static_cast<int32_t>(prompt.size()),
                promptTokens.data(),
                static_cast<int32_t>(promptTokens.size()),
                true,
                true);
        if (promptTokenCount < 0) {
            throw std::runtime_error("llama.cpp could not tokenize the prompt.");
        }
        if (promptTokenCount + maxTokens > static_cast<int32_t>(llama_n_ctx(state->context))) {
            throw std::runtime_error("The conversation is too long for the configured model context.");
        }

        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
                llama_sampler_chain_init(llama_sampler_chain_default_params()),
                &llama_sampler_free);
        if (sampler == nullptr) {
            throw std::runtime_error("llama.cpp could not initialize the text sampler.");
        }
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_penalties(
                llama_vocab_n_tokens(vocab), 64, 1.0f, 0.0f, presencePenalty));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)> batch(
                llama_batch_ext_init(state->context),
                &llama_batch_ext_free);
        if (batch == nullptr) {
            throw std::runtime_error("llama.cpp could not allocate a decode batch.");
        }

        std::string response;
        const llama_token * inputTokens = promptTokens.data();
        int32_t inputCount = promptTokenCount;
        llama_pos position = 0;

        for (int32_t generated = 0; generated < maxTokens; ++generated) {
            if (!setBatchTokens(batch.get(), inputTokens, inputCount, position)) {
                throw std::runtime_error("llama.cpp could not prepare the prompt batch.");
            }
            if (llama_process(state->context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
                throw std::runtime_error("llama.cpp failed while evaluating the prompt.");
            }

            position += inputCount;
            const llama_token nextToken = llama_sampler_sample(sampler.get(), state->context, -1);
            if (llama_vocab_is_eog(vocab, nextToken)) {
                break;
            }

            std::vector<char> piece(256);
            int32_t pieceSize = llama_token_to_piece(vocab, nextToken, piece.data(), piece.size(), 0, true);
            if (pieceSize < 0) {
                piece.resize(static_cast<size_t>(-pieceSize));
                pieceSize = llama_token_to_piece(vocab, nextToken, piece.data(), piece.size(), 0, true);
            }
            if (pieceSize < 0) {
                throw std::runtime_error("llama.cpp could not decode a generated token.");
            }
            response.append(piece.data(), static_cast<size_t>(pieceSize));
            inputTokens = &nextToken;
            inputCount = 1;
        }

        return fromUtf8(env, response);
    } catch (const std::exception & error) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Generation failed: %s", error.what());
        throwJava(env, error.what());
        return nullptr;
    }
}

void nativeReleaseModel(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<EngineState *>(handle);
}

JNINativeMethod kNativeMethods[] = {
        {"nativeLoadModel", "(Ljava/lang/String;II)J", reinterpret_cast<void *>(nativeLoadModel)},
        {"nativeGenerate", "(JLjava/lang/String;IFFIF)Ljava/lang/String;", reinterpret_cast<void *>(nativeGenerate)},
        {"nativeReleaseModel", "(J)V", reinterpret_cast<void *>(nativeReleaseModel)},
};

} // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * vm, void *) {
    JNIEnv * env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        return JNI_ERR;
    }

    jclass bridgeClass = env->FindClass("com/grassling/app/local_ai/NativeLlamaBridge");
    if (bridgeClass == nullptr) {
        return JNI_ERR;
    }
    const jint result = env->RegisterNatives(
            bridgeClass,
            kNativeMethods,
            sizeof(kNativeMethods) / sizeof(kNativeMethods[0]));
    env->DeleteLocalRef(bridgeClass);
    return result == JNI_OK ? JNI_VERSION_1_6 : JNI_ERR;
}
