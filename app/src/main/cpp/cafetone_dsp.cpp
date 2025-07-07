#include "audio_effect.h"
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <memory>
#include "audio_processor.h"
#include "binaural_processor.h"
#include "haas_processor.h"
#include "eq_processor.h"
#include "reverb_processor.h"

#define LOG_TAG "CafeToneEffect"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// --- Forward Declarations ---
int32_t CafeMode_Command(effect_interface_t** self, uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData);
int32_t CafeMode_Process(effect_interface_t** self, audio_buffer_t* in, audio_buffer_t* out);

// --- Global Variables ---
const struct effect_interface_s gCafeModeInterface = { CafeMode_Process, CafeMode_Command };

const effect_descriptor_t gCafeModeDescriptor = {
        { 0x12345678, 0x1234, 0x5678, 0x1234, { 0x56, 0x78, 0x90, 0xab, 0xcd, 0xef } }, // type
        { 0x87654321, 0x4321, 0x8765, 0x4321, { 0xfe, 0xdc, 0xba, 0x09, 0x87, 0x65 } }, // uuid
        EFFECT_CONTROL_API_VERSION, EFFECT_FLAG_TYPE_INSERT, 0, 1, "Café Mode DSP", "CaféTone Audio"
};

enum { PARAM_INTENSITY, PARAM_SPATIAL_WIDTH, PARAM_DISTANCE };

// --- Effect Context ---
struct CafeModeContext {
    effect_interface_t mItfe;
    std::unique_ptr<BinauralProcessor> binauralProcessor;
    std::unique_ptr<HaasProcessor> haasProcessor;
    std::unique_ptr<EQProcessor> eqProcessor;
    std::unique_ptr<ReverbProcessor> reverbProcessor;
    float intensity = 0.7f, spatialWidth = 0.6f, distance = 0.8f;
    bool enabled = false;
    static const int MAX_BUFFER_SIZE = 4096;
    float inputBuffer[2][MAX_BUFFER_SIZE], tempBuffer[2][MAX_BUFFER_SIZE], outputBuffer[2][MAX_BUFFER_SIZE];
};

// --- C-Style Interface Implementation ---
extern "C" {
int32_t EffectCreate(const effect_uuid_t* uuid, int32_t sessionId, int32_t ioId, effect_interface_t** pItfe) {
    if (!pItfe || !uuid || memcmp(uuid, &gCafeModeDescriptor.uuid, sizeof(effect_uuid_t)) != 0) return -EINVAL;
    auto* ctx = new(std::nothrow) CafeModeContext;
    if (!ctx) return -ENOMEM;
    ctx->mItfe = gCafeModeInterface;
    try {
        ctx->binauralProcessor = std::make_unique<BinauralProcessor>();
        ctx->haasProcessor = std::make_unique<HaasProcessor>();
        ctx->eqProcessor = std::make_unique<EQProcessor>();
        ctx->reverbProcessor = std::make_unique<ReverbProcessor>();
    } catch (const std::bad_alloc& e) { delete ctx; return -ENOMEM; }
    *pItfe = &ctx->mItfe;
    return 0;
}

int32_t EffectRelease(effect_interface_t** itfe) {
    if (!itfe || !*itfe) return -EINVAL;
    delete reinterpret_cast<CafeModeContext*>(*itfe);
    return 0;
}

int32_t EffectGetDescriptor(const effect_uuid_t* uuid, effect_descriptor_t* pDescriptor) {
    if (!pDescriptor || !uuid || memcmp(uuid, &gCafeModeDescriptor.uuid, sizeof(effect_uuid_t)) != 0) return -EINVAL;
    memcpy(pDescriptor, &gCafeModeDescriptor, sizeof(effect_descriptor_t));
    return 0;
}

// Main library entry point
audio_effect_library_t AUDIO_EFFECT_LIBRARY_INFO_SYM = {
        .tag = AUDIO_EFFECT_LIBRARY_TAG, .version = EFFECT_CONTROL_API_VERSION, .name = "CaféTone Library",
        .implementor = "CaféTone Audio", .create_effect = EffectCreate, .release_effect = EffectRelease,
        .get_descriptor = EffectGetDescriptor
};
}

// --- Effect-Specific Implementations ---
int32_t CafeMode_Process(effect_interface_t** self, audio_buffer_t* in, audio_buffer_t* out) {
    auto* ctx = reinterpret_cast<CafeModeContext*>(*self);
    if (!ctx || !in || !out || !in->s16 || !out->s16 || in->frameCount == 0) return -EINVAL;
    if (!ctx->enabled) { if (in->raw != out->raw) memcpy(out->raw, in->raw, in->frameCount * sizeof(int16_t) * 2); return 0; }

    int frames = std::min((size_t)CafeModeContext::MAX_BUFFER_SIZE, in->frameCount);
    for (int i = 0; i < frames; i++) {
        ctx->inputBuffer[0][i] = in->s16[i * 2] / 32768.0f;
        ctx->inputBuffer[1][i] = in->s16[i * 2 + 1] / 32768.0f;
    }

    ctx->eqProcessor->process(ctx->inputBuffer[0], ctx->tempBuffer[0], frames);
    ctx->eqProcessor->process(ctx->inputBuffer[1], ctx->tempBuffer[1], frames);
    ctx->haasProcessor->process(ctx->tempBuffer[0], ctx->tempBuffer[1], ctx->tempBuffer[0], ctx->tempBuffer[1], frames);
    ctx->binauralProcessor->process(ctx->tempBuffer[0], ctx->tempBuffer[1], ctx->tempBuffer[0], ctx->tempBuffer[1], frames);
    ctx->reverbProcessor->process(ctx->tempBuffer[0], ctx->tempBuffer[1], ctx->outputBuffer[0], ctx->outputBuffer[1], frames);

    for (int i = 0; i < frames; i++) {
        float left = ctx->inputBuffer[0][i] * (1.0f - ctx->intensity) + ctx->outputBuffer[0][i] * ctx->intensity;
        float right = ctx->inputBuffer[1][i] * (1.0f - ctx->intensity) + ctx->outputBuffer[1][i] * ctx->intensity;
        out->s16[i * 2] = std::clamp(left, -1.0f, 1.0f) * 32767.0f;
        out->s16[i * 2 + 1] = std::clamp(right, -1.0f, 1.0f) * 32767.0f;
    }
    return 0;
}

int32_t CafeMode_Command(effect_interface_t** self, uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData) {
    auto* ctx = reinterpret_cast<CafeModeContext*>(*self);
    if (!ctx) return -EINVAL;

    switch (cmdCode) {
        case EFFECT_CMD_ENABLE: ctx->enabled = true; return 0;
        case EFFECT_CMD_DISABLE: ctx->enabled = false; return 0;
        case EFFECT_CMD_SET_PARAM: {
            if (!pCmdData || cmdSize < 8 || !replySize || *replySize < 4) return -EINVAL;
            int32_t paramId = *(int32_t*)pCmdData;
            float value = *(float*)((char*)pCmdData + sizeof(int32_t));
            *(int32_t*)pReplyData = 0;
            switch (paramId) {
                case PARAM_INTENSITY: ctx->intensity = std::clamp(value, 0.0f, 1.0f); break;
                case PARAM_SPATIAL_WIDTH:
                    ctx->spatialWidth = std::clamp(value, 0.0f, 1.0f);
                    ctx->haasProcessor->setDelayAmount(ctx->spatialWidth * 15.0f);
                    break;
                case PARAM_DISTANCE:
                    ctx->distance = std::clamp(value, 0.0f, 1.0f);
                    ctx->binauralProcessor->setDistance(ctx->distance);
                    ctx->eqProcessor->setHighPassFilter(50.0f + ctx->distance * 150.0f);
                    ctx->eqProcessor->setLowPassFilter(12000.0f - ctx->distance * 8000.0f);
                    break;
                default: *(int32_t*)pReplyData = -EINVAL;
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
        default: return -EINVAL;
    }
}