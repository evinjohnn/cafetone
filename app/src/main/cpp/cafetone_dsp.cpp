#include "audio_effect.h"
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <memory>
#include <chrono>
#include <jni.h>

#include "audio_processor.h"
#include "binaural_processor.h"
#include "haas_processor.h"
#include "eq_processor.h"
#include "reverb_processor.h"
#include "dynamic_processor.h"

#define LOG_TAG "CafeToneEffect"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// --- Forward Declarations ---
int32_t CafeMode_Command(effect_interface_t** self, uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData);
int32_t CafeMode_Process(effect_interface_t** self, audio_buffer_t* in, audio_buffer_t* out);

// --- Global Variables ---
const struct effect_interface_s gCafeModeInterface = { CafeMode_Process, CafeMode_Command };

const effect_descriptor_t gCafeModeDescriptor = {
        { 0x12345678, 0x1234, 0x5678, 0x1234, { 0x56, 0x78, 0x90, 0xab, 0xcd, 0xef } }, // type
        { 0x87654321, 0x4321, 0x8765, 0x4321, { 0xfe, 0xdc, 0xba, 0x09, 0x87, 0x65 } }, // uuid
        EFFECT_CONTROL_API_VERSION, EFFECT_FLAG_TYPE_INSERT, 0, 1, "Sony Café Mode DSP", "CaféTone Audio"
};

enum { PARAM_INTENSITY, PARAM_SPATIAL_WIDTH, PARAM_DISTANCE };

// --- Enhanced Effect Context ---
struct CafeModeContext {
    effect_interface_t mItfe;
    std::unique_ptr<EQProcessor> eqProcessor;
    std::unique_ptr<HaasProcessor> haasProcessor;
    std::unique_ptr<BinauralProcessor> binauralProcessor;
    std::unique_ptr<ReverbProcessor> reverbProcessor;
    std::unique_ptr<DynamicProcessor> dynamicProcessor;
    float intensity = 0.7f;
    float spatialWidth = 0.6f;
    float distance = 0.8f;
    bool enabled = false;
    static const int MAX_BUFFER_SIZE = 4096;
    float inputBuffer[2][MAX_BUFFER_SIZE]{};
    float eqBuffer[2][MAX_BUFFER_SIZE]{};
    float haasBuffer[2][MAX_BUFFER_SIZE]{};
    float binauralBuffer[2][MAX_BUFFER_SIZE]{};
    float reverbBuffer[2][MAX_BUFFER_SIZE]{};
    float outputBuffer[2][MAX_BUFFER_SIZE]{};
    int sampleRate = 48000;
};

// --- C-Style Interface Implementation ---
extern "C" {
CafeModeContext* g_context = nullptr;

// GUARANTEED FIX: Added [[maybe_unused]] to silence compiler warnings for unused parameters.
int32_t EffectCreate(const effect_uuid_t* uuid, [[maybe_unused]] int32_t sessionId, [[maybe_unused]] int32_t ioId, effect_interface_t** pItfe) {
    LOGI("EffectCreate called for Sony Café Mode DSP");

    if (!pItfe || !uuid || memcmp(uuid, &gCafeModeDescriptor.uuid, sizeof(effect_uuid_t)) != 0) {
        LOGE("EffectCreate: Invalid parameters");
        return -EINVAL;
    }

    auto* ctx = new(std::nothrow) CafeModeContext;
    if (!ctx) {
        LOGE("EffectCreate: Memory allocation failed");
        return -ENOMEM;
    }

    ctx->mItfe = gCafeModeInterface;

    try {
        ctx->eqProcessor = std::make_unique<EQProcessor>();
        ctx->haasProcessor = std::make_unique<HaasProcessor>();
        ctx->binauralProcessor = std::make_unique<BinauralProcessor>();
        ctx->reverbProcessor = std::make_unique<ReverbProcessor>();
        ctx->dynamicProcessor = std::make_unique<DynamicProcessor>();
        ctx->eqProcessor->setSampleRate(ctx->sampleRate);
        ctx->haasProcessor->setSampleRate(ctx->sampleRate);
        ctx->binauralProcessor->setSampleRate(ctx->sampleRate);
        ctx->reverbProcessor->setSampleRate(ctx->sampleRate);
        ctx->dynamicProcessor->setSampleRate(ctx->sampleRate);
        LOGI("Sony Café Mode DSP chain initialized successfully");
    } catch (const std::bad_alloc& e) {
        LOGE("EffectCreate: DSP processor allocation failed");
        delete ctx;
        return -ENOMEM;
    }

    *pItfe = &ctx->mItfe;
    return 0;
}

int32_t EffectRelease(effect_interface_t** itfe) {
    LOGI("EffectRelease called");
    if (!itfe || !*itfe) return -EINVAL;
    delete reinterpret_cast<CafeModeContext*>(*itfe);
    *itfe = nullptr;
    return 0;
}

int32_t EffectGetDescriptor(const effect_uuid_t* uuid, effect_descriptor_t* pDescriptor) {
    if (!pDescriptor || !uuid || memcmp(uuid, &gCafeModeDescriptor.uuid, sizeof(effect_uuid_t)) != 0) {
        return -EINVAL;
    }
    memcpy(pDescriptor, &gCafeModeDescriptor, sizeof(effect_descriptor_t));
    return 0;
}

audio_effect_library_t AUDIO_EFFECT_LIBRARY_INFO_SYM = {
        .tag = AUDIO_EFFECT_LIBRARY_TAG,
        .version = EFFECT_CONTROL_API_VERSION,
        .name = "Sony Café Mode DSP Library",
        .implementor = "CaféTone Audio",
        .create_effect = EffectCreate,
        .release_effect = EffectRelease,
        .get_descriptor = EffectGetDescriptor
};

// GUARANTEED FIX: Added [[maybe_unused]] to JNI parameters to silence compiler warnings.
JNIEXPORT jint JNICALL
Java_com_cafetone_audio_dsp_CafeModeDSP_nativeInit([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz) {
    if (g_context == nullptr) {
        g_context = new(std::nothrow) CafeModeContext();
        if (g_context) {
            g_context->eqProcessor = std::make_unique<EQProcessor>();
            g_context->haasProcessor = std::make_unique<HaasProcessor>();
            g_context->binauralProcessor = std::make_unique<BinauralProcessor>();
            g_context->reverbProcessor = std::make_unique<ReverbProcessor>();
            g_context->dynamicProcessor = std::make_unique<DynamicProcessor>();
        }
    }
    return g_context != nullptr ? 0 : -1;
}

JNIEXPORT void JNICALL
Java_com_cafetone_audio_dsp_CafeModeDSP_nativeRelease([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz) {
delete g_context;
g_context = nullptr;
}

JNIEXPORT void JNICALL
Java_com_cafetone_audio_dsp_CafeModeDSP_nativeSetParameter([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jint param_id, jfloat value) {
if (g_context == nullptr) return;
switch (param_id) {
case PARAM_INTENSITY: g_context->intensity = value; break;
case PARAM_SPATIAL_WIDTH: g_context->spatialWidth = value; break;
case PARAM_DISTANCE: g_context->distance = value; break;
}
}

JNIEXPORT jfloat JNICALL
        Java_com_cafetone_audio_dsp_CafeModeDSP_nativeGetParameter([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jint param_id) {
if (g_context == nullptr) return 0.0f;
switch (param_id) {
case PARAM_INTENSITY: return g_context->intensity;
case PARAM_SPATIAL_WIDTH: return g_context->spatialWidth;
case PARAM_DISTANCE: return g_context->distance;
default: return 0.0f;
}
}

JNIEXPORT void JNICALL
Java_com_cafetone_audio_dsp_CafeModeDSP_nativeSetEnabled([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jboolean enabled) {
if (g_context != nullptr) {
g_context->enabled = enabled;
}
}

} // extern "C"

int32_t CafeMode_Process(effect_interface_t** self, audio_buffer_t* in, audio_buffer_t* out) {
    auto* ctx = reinterpret_cast<CafeModeContext*>(*self);
    if (!ctx || !in || !out || !in->s16 || !out->s16 || in->frameCount == 0) {
        return -EINVAL;
    }

    auto start_time = std::chrono::high_resolution_clock::now();

    if (!ctx->enabled) {
        if (in->raw != out->raw) {
            memcpy(out->raw, in->raw, in->frameCount * sizeof(int16_t) * 2);
        }
        return 0;
    }

    int frames = std::min((size_t)CafeModeContext::MAX_BUFFER_SIZE, in->frameCount);

    for (int i = 0; i < frames; i++) {
        ctx->inputBuffer[0][i] = in->s16[i * 2] / 32768.0f;
        ctx->inputBuffer[1][i] = in->s16[i * 2 + 1] / 32768.0f;
    }

    ctx->eqProcessor->process(ctx->inputBuffer[0], ctx->eqBuffer[0], frames);
    ctx->eqProcessor->process(ctx->inputBuffer[1], ctx->eqBuffer[1], frames);
    ctx->haasProcessor->process(ctx->eqBuffer[0], ctx->eqBuffer[1], ctx->haasBuffer[0], ctx->haasBuffer[1], frames);
    ctx->binauralProcessor->process(ctx->haasBuffer[0], ctx->haasBuffer[1], ctx->binauralBuffer[0], ctx->binauralBuffer[1], frames);
    ctx->reverbProcessor->process(ctx->binauralBuffer[0], ctx->binauralBuffer[1], ctx->reverbBuffer[0], ctx->reverbBuffer[1], frames);
    ctx->dynamicProcessor->process(ctx->reverbBuffer[0], ctx->reverbBuffer[1], ctx->outputBuffer[0], ctx->outputBuffer[1], frames);

    for (int i = 0; i < frames; i++) {
        float dryLeft = ctx->inputBuffer[0][i];
        float dryRight = ctx->inputBuffer[1][i];
        float wetLeft = ctx->outputBuffer[0][i];
        float wetRight = ctx->outputBuffer[1][i];
        float finalLeft = dryLeft * (1.0f - ctx->intensity) + wetLeft * ctx->intensity;
        float finalRight = dryRight * (1.0f - ctx->intensity) + wetRight * ctx->intensity;
        out->s16[i * 2] = (int16_t)(std::clamp(finalLeft, -1.0f, 1.0f) * 32767.0f);
        out->s16[i * 2 + 1] = (int16_t)(std::clamp(finalRight, -1.0f, 1.0f) * 32767.0f);
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end_time - start_time);

    if (duration.count() > 10000) {
        LOGE("Real-time constraint violated: %lld μs (target: <10,000 μs)", (long long)duration.count());
    }

    return 0;
}

int32_t CafeMode_Command(effect_interface_t** self, uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData) {
    auto* ctx = reinterpret_cast<CafeModeContext*>(*self);
    if (!ctx) return -EINVAL;

    switch (cmdCode) {
        case EFFECT_CMD_ENABLE:
            LOGI("Sony Café Mode DSP enabled");
            ctx->enabled = true;
            return 0;

        case EFFECT_CMD_DISABLE:
            LOGI("Sony Café Mode DSP disabled");
            ctx->enabled = false;
            return 0;

        case EFFECT_CMD_SET_PARAM: {
            if (!pCmdData || cmdSize < 8 || !replySize || *replySize < 4) return -EINVAL;
            int32_t paramId = *(int32_t*)pCmdData;
            float value = *(float*)((char*)pCmdData + sizeof(int32_t));
            *(int32_t*)pReplyData = 0;
            switch (paramId) {
                case PARAM_INTENSITY:
                    ctx->intensity = std::clamp(value, 0.0f, 1.0f);
                    LOGV("Sony Café Mode intensity set to: %.2f", ctx->intensity);
                    break;
                case PARAM_SPATIAL_WIDTH:
                    ctx->spatialWidth = std::clamp(value, 0.0f, 1.0f);
                    ctx->haasProcessor->setDelayAmount(ctx->spatialWidth * 20.0f);
                    ctx->binauralProcessor->setSpatialWidth(1.0f + ctx->spatialWidth * 0.7f);
                    LOGV("Sony Café Mode spatial width set to: %.2f", ctx->spatialWidth);
                    break;
                case PARAM_DISTANCE:
                    ctx->distance = std::clamp(value, 0.0f, 1.0f);
                    ctx->binauralProcessor->setDistance(ctx->distance);
                    ctx->eqProcessor->setHighPassFilter(40.0f + ctx->distance * 160.0f);
                    ctx->eqProcessor->setLowPassFilter(12000.0f - ctx->distance * 4000.0f);
                    ctx->dynamicProcessor->setDistanceCompression(ctx->distance);
                    LOGV("Sony Café Mode distance set to: %.2f", ctx->distance);
                    break;
                default:
                    *(int32_t*)pReplyData = -EINVAL;
                    LOGE("Unknown parameter ID: %d", paramId);
            }
            return 0;
        }

        case EFFECT_CMD_GET_PARAM: {
            if (!pCmdData || cmdSize < 4 || !pReplyData || !replySize || *replySize < 8) return -EINVAL;
            int32_t paramId = *(int32_t*)pCmdData;
            float* valuePtr = (float*)((char*)pReplyData + sizeof(int32_t));
            *(int32_t*)pReplyData = 0;
            switch (paramId) {
                case PARAM_INTENSITY: *valuePtr = ctx->intensity; break;
                case PARAM_SPATIAL_WIDTH: *valuePtr = ctx->spatialWidth; break;
                case PARAM_DISTANCE: *valuePtr = ctx->distance; break;
                default: *(int32_t*)pReplyData = -EINVAL;
            }
            return 0;
        }

        default:
            LOGV("Unknown command: %d", cmdCode);
            return -EINVAL;
    }
}