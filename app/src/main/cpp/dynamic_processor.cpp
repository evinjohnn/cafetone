#include "dynamic_processor.h"
#include <cmath>
#include <algorithm>

DynamicProcessor::DynamicProcessor()
        : m_distanceCompression(0.8f)
        , m_makeupGain(1.0f)
        , m_softLimitingEnabled(true)
        , m_limiterThreshold(0.9f)
        , m_limiterRatio(10.0f) {

    setupSonyCompressorBands();
    clearStates();
}

DynamicProcessor::~DynamicProcessor() {
}

void DynamicProcessor::process(const float* input, float* output, int frames) {
    if (!m_initialized) {
        for (int i = 0; i < frames; i++) {
            output[i] = input[i];
        }
        return;
    }

    for (int i = 0; i < frames; i++) {
        float sample = input[i];
        // GUARANTEED FIX: Pass the channel index (0 for mono/left)
        processMultiBandCompressor(sample, sample, 0);
        sample *= m_makeupGain;
        output[i] = sample;
    }
}

void DynamicProcessor::process(const float* leftIn, const float* rightIn,
        float* leftOut, float* rightOut, int frames) {
    if (!m_initialized) {
        for (int i = 0; i < frames; i++) {
            leftOut[i] = leftIn[i];
            rightOut[i] = rightIn[i];
        }
        return;
    }

    for (int i = 0; i < frames; i++) {
        float leftSample = leftIn[i];
        float rightSample = rightIn[i];

        // GUARANTEED FIX: Pass the correct channel index for left (0) and right (1)
        processMultiBandCompressor(leftSample, leftSample, 0);
        processMultiBandCompressor(rightSample, rightSample, 1);

        applyDistanceCompression(leftSample, 0);
        applyDistanceCompression(rightSample, 1);

        if (m_softLimitingEnabled) {
            applySoftLimiter(leftSample, rightSample);
        }

        leftSample *= m_makeupGain;
        rightSample *= m_makeupGain;

        leftOut[i] = leftSample;
        rightOut[i] = rightSample;
    }
}

// GUARANTEED FIX: Added the 'channel' parameter back to match the header declaration.
void DynamicProcessor::processMultiBandCompressor(float input, float& output, int channel) {
    float lowBand = input;
    float midBand = input;
    float highBand = input;

    lowBand = processCompressorBand(lowBand, m_bands[0]);
    midBand = processCompressorBand(midBand, m_bands[1]);
    highBand = processCompressorBand(highBand, m_bands[2]);

    output = (lowBand + midBand + highBand) * 0.33f;
}

float DynamicProcessor::processCompressorBand(float input, CompressorBand& band) {
    float gain = calculateCompressorGain(
            input,
            band.threshold,
            band.ratio,
            band.envelope,
            band.attack,
            band.release
    );
    return input * gain * band.gain;
}

void DynamicProcessor::applyDistanceCompression(float& sample, int band) {
    float compressionAmount = m_distanceCompression;
    if (band > 1) {
        compressionAmount *= 1.3f;
    }

    float threshold = 0.3f;
    if (abs(sample) > threshold) {
        float excess = abs(sample) - threshold;
        float compressedExcess = excess * (1.0f - compressionAmount * 0.5f);
        sample = (sample > 0 ? 1.0f : -1.0f) * (threshold + compressedExcess);
    }
}

void DynamicProcessor::applySoftLimiter(float& leftSample, float& rightSample) {
    float peakLevel = std::max(abs(leftSample), abs(rightSample));

    if (peakLevel > m_limiterThreshold) {
        float limitGain = m_limiterThreshold / peakLevel;
        float targetGain = limitGain;
        float attack = 0.001f;
        float release = 0.01f;

        if (targetGain < m_limiterEnvelope[0]) {
            m_limiterEnvelope[0] += (targetGain - m_limiterEnvelope[0]) * attack;
        } else {
            m_limiterEnvelope[0] += (targetGain - m_limiterEnvelope[0]) * release;
        }

        leftSample *= m_limiterEnvelope[0];
        rightSample *= m_limiterEnvelope[0];
    }
}

float DynamicProcessor::calculateCompressorGain(float input, float threshold, float ratio,
        float& envelope, float attack, float release) {
    float inputLevel = abs(input);

    if (inputLevel > envelope) {
        envelope += (inputLevel - envelope) * attack;
    } else {
        envelope += (inputLevel - envelope) * release;
    }

    if (envelope > threshold) {
        float excess = envelope - threshold;
        float compressedExcess = excess / ratio;
        float targetLevel = threshold + compressedExcess;
        return targetLevel / (envelope + 1e-10f);
    }

    return 1.0f;
}

void DynamicProcessor::setSampleRate(int sampleRate) {
    AudioProcessor::setSampleRate(sampleRate);
    updateCrossoverFilters();
}

void DynamicProcessor::reset() {
    clearStates();
}

void DynamicProcessor::setParameter(int param, float value) {
    switch (param) {
        case 0: setDistanceCompression(value); break;
        case 1: setMakeupGain(value); break;
        case 2: setSoftLimiting(value > 0.5f); break;
    }
}

float DynamicProcessor::getParameter(int param) const {
    switch (param) {
        case 0: return m_distanceCompression;
        case 1: return m_makeupGain;
        case 2: return m_softLimitingEnabled ? 1.0f : 0.0f;
        default: return 0.0f;
    }
}

void DynamicProcessor::setDistanceCompression(float amount) {
    m_distanceCompression = clamp(amount, 0.0f, 1.0f);
}

void DynamicProcessor::setMakeupGain(float gain) {
    m_makeupGain = clamp(gain, 0.1f, 2.0f);
}

void DynamicProcessor::setSoftLimiting(bool enabled) {
    m_softLimitingEnabled = enabled;
}

void DynamicProcessor::setupSonyCompressorBands() {
    m_bands[0] = { 0.5f, 3.0f, 0.01f, 0.1f, 1.0f, 0.0f, 0.0f };
    m_bands[1] = { 0.4f, 4.0f, 0.005f, 0.05f, 1.1f, 0.0f, 0.0f };
    m_bands[2] = { 0.3f, 6.0f, 0.002f, 0.02f, 0.9f, 0.0f, 0.0f };
}

void DynamicProcessor::updateCrossoverFilters() {
    m_lowMidCrossover.frequency = 300.0f;
    m_midHighCrossover.frequency = 3000.0f;

    float omega1 = 2.0f * M_PI * 300.0f / m_sampleRate;
    float omega2 = 2.0f * M_PI * 3000.0f / m_sampleRate;

    m_lowMidCrossover.coeff[0] = omega1 / (omega1 + 1.0f);
    m_lowMidCrossover.coeff[1] = 1.0f - m_lowMidCrossover.coeff[0];

    m_midHighCrossover.coeff[0] = omega2 / (omega2 + 1.0f);
    m_midHighCrossover.coeff[1] = 1.0f - m_midHighCrossover.coeff[0];
}

void DynamicProcessor::clearStates() {
    for (auto & band : m_bands) {
        band.envelope = 0.0f;
        band.previousSample = 0.0f;
    }
    m_limiterEnvelope[0] = 1.0f;
    m_limiterEnvelope[1] = 1.0f;
    m_lowMidCrossover.state[0] = 0.0f;
    m_lowMidCrossover.state[1] = 0.0f;
    m_midHighCrossover.state[0] = 0.0f;
    m_midHighCrossover.state[1] = 0.0f;
}

float DynamicProcessor::processCrossoverFilter(float input, CrossoverFilter& filter) {
    float output = filter.coeff[0] * input + filter.coeff[1] * filter.state[0];
    filter.state[0] = output;
    return output;
}