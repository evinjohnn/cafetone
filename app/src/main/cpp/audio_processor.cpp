#include "audio_processor.h"
#include <cmath>
#include <algorithm>

AudioProcessor::AudioProcessor() 
    : m_sampleRate(48000)
    , m_initialized(false) {
}

AudioProcessor::~AudioProcessor() {
}

void AudioProcessor::setSampleRate(int sampleRate) {
    m_sampleRate = sampleRate;
    m_initialized = true;
}

void AudioProcessor::reset() {
    // Base implementation - derived classes should override
}

float AudioProcessor::clamp(float value, float min, float max) {
    return std::clamp(value, min, max);
}

float AudioProcessor::linearToDb(float linear) {
    if (linear <= 0.0f) return -96.0f;
    return 20.0f * log10f(linear);
}

float AudioProcessor::dbToLinear(float db) {
    return powf(10.0f, db / 20.0f);
}

float AudioProcessor::frequencyToRadians(float frequency) {
    return 2.0f * M_PI * frequency / m_sampleRate;
} 