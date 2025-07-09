#include "reverb_processor.h"
#include <cstring>
#include <cmath>

ReverbProcessor::ReverbProcessor()
        : m_roomSize(0.7f)
        , m_decayTime(2.1f)
        , m_preDelay(42.0f)
        , m_wetLevel(0.45f)
        , m_dryLevel(0.55f)
        , m_highDamping(0.8f)
        , m_lowDamping(0.4f)
        , m_lateReverbGain(0.15f) {

    setupSonyCafeReflections();
    clearBuffers();
}

ReverbProcessor::~ReverbProcessor() {
}

void ReverbProcessor::process(const float* input, float* output, int frames) {
    for (int i = 0; i < frames; i++) {
        float drySignal = input[i] * m_dryLevel;
        float wetSignal = 0.0f;

        for (auto & reflection : m_reflections) {
            wetSignal += processSonyReflection(input[i], reflection);
        }

        wetSignal += processSonyLateReverb(input[i], 0);
        output[i] = drySignal + wetSignal * m_wetLevel;
    }
}

void ReverbProcessor::process(const float* leftIn, const float* rightIn,
        float* leftOut, float* rightOut, int frames) {
    if (!m_initialized) {
        for (int i = 0; i < frames; i++) {
            leftOut[i] = leftIn[i];
            rightOut[i] = rightIn[i];
        }
        return;
    }

    for (int i = 0; i < frames; i++) {
        float leftDry = leftIn[i] * m_dryLevel;
        float rightDry = rightIn[i] * m_dryLevel;
        float leftWet = 0.0f;
        float rightWet = 0.0f;

        for (auto & reflection : m_reflections) {
            leftWet += processSonyReflection(leftIn[i], reflection, false);
            rightWet += processSonyReflection(rightIn[i], reflection, true);
        }

        leftWet += processSonyLateReverb(leftIn[i], 0);
        rightWet += processSonyLateReverb(rightIn[i], 1);

        applySonyDamping(leftWet, rightWet);
        applySonyEchoEffects(leftWet, rightWet, leftWet, rightWet);

        float makeupGain = 1.0f + (m_wetLevel * 0.2f);
        leftOut[i] = (leftDry + leftWet * m_wetLevel) * makeupGain;
        rightOut[i] = (rightDry + rightWet * m_wetLevel) * makeupGain;
    }
}

float ReverbProcessor::processSonyReflection(float input, Reflection& reflection, bool rightChannel) {
    int delayOffset = rightChannel ? 2 : 0;
    int readIndex = (reflection.delayIndex - reflection.delaySamples - delayOffset + MAX_REFLECTION_DELAY) % MAX_REFLECTION_DELAY;
    float delayedSample = reflection.delayBuffer[readIndex];

    float output = delayedSample * reflection.gain;
    float dampingFactor = reflection.dampingCoeff * (rightChannel ? 0.95f : 1.0f);
    output = output * dampingFactor + input * (1.0f - dampingFactor) * 0.1f;

    reflection.delayBuffer[reflection.delayIndex] = input;
    reflection.delayIndex = (reflection.delayIndex + 1) % MAX_REFLECTION_DELAY;

    return output;
}

float ReverbProcessor::processSonyLateReverb(float input, int channel) {
    int lateIndex = m_lateReverbIndex[channel];
    float lateSignal = m_lateReverbBuffer[channel][lateIndex];

    float decayFactor = powf(0.001f, 1.0f / (m_decayTime * m_sampleRate));
    lateSignal *= decayFactor;

    int preDelayIndex = (lateIndex - m_preDelaySamples + LATE_REVERB_SIZE) % LATE_REVERB_SIZE;
    // GUARANTEED FIX: Use the preDelayedInput variable to fix the warning.
    float preDelayedInput = m_lateReverbBuffer[channel][preDelayIndex];

    m_lateReverbBuffer[channel][lateIndex] = input * 0.2f + preDelayedInput * 0.1f + lateSignal * 0.95f;
    m_lateReverbIndex[channel] = (lateIndex + 1) % LATE_REVERB_SIZE;

    return lateSignal * m_lateReverbGain;
}

void ReverbProcessor::applySonyDamping(float& leftWet, float& rightWet) {
    leftWet *= (1.0f - m_highDamping * 0.6f);
    rightWet *= (1.0f - m_highDamping * 0.6f);
    leftWet *= (1.0f - m_lowDamping * 0.37f);
    rightWet *= (1.0f - m_lowDamping * 0.37f);
}

void ReverbProcessor::applySonyEchoEffects(float leftIn, float rightIn, float& leftOut, float& rightOut) {
    int echoDelay1 = (int)(120.0f * m_sampleRate / 1000.0f);
    int echoDelay2 = (int)(180.0f * m_sampleRate / 1000.0f);
    int echoDelay3 = (int)(240.0f * m_sampleRate / 1000.0f);

    float echo1 = 0.0f, echo2 = 0.0f, echo3 = 0.0f;

    if (echoDelay1 < LATE_REVERB_SIZE) {
        int echo1Index = (m_lateReverbIndex[0] - echoDelay1 + LATE_REVERB_SIZE) % LATE_REVERB_SIZE;
        echo1 = m_lateReverbBuffer[0][echo1Index] * 0.3f;
    }
    if (echoDelay2 < LATE_REVERB_SIZE) {
        int echo2Index = (m_lateReverbIndex[0] - echoDelay2 + LATE_REVERB_SIZE) % LATE_REVERB_SIZE;
        echo2 = m_lateReverbBuffer[0][echo2Index] * 0.2f;
    }
    if (echoDelay3 < LATE_REVERB_SIZE) {
        int echo3Index = (m_lateReverbIndex[0] - echoDelay3 + LATE_REVERB_SIZE) % LATE_REVERB_SIZE;
        echo3 = m_lateReverbBuffer[0][echo3Index] * 0.1f;
    }

    leftOut = leftIn + echo1 + echo2 * 0.8f + echo3 * 0.6f;
    rightOut = rightIn + echo1 * 0.8f + echo2 + echo3 * 0.7f;
}

void ReverbProcessor::setSampleRate(int sampleRate) {
    AudioProcessor::setSampleRate(sampleRate);
    updateSonyReflectionDelays();
    m_preDelaySamples = (int)(m_preDelay * m_sampleRate / 1000.0f);
    m_preDelaySamples = clamp(m_preDelaySamples, 0, LATE_REVERB_SIZE - 1);
}

void ReverbProcessor::reset() {
    clearBuffers();
}

void ReverbProcessor::setParameter(int param, float value) {
    switch (param) {
        case 0: setRoomSize(value); break;
        case 1: setDecayTime(value); break;
        case 2: setWetLevel(value); break;
        case 3: setDryLevel(value); break;
        case 4: setPreDelay(value); break;
    }
}

float ReverbProcessor::getParameter(int param) const {
    switch (param) {
        case 0: return m_roomSize;
        case 1: return m_decayTime;
        case 2: return m_wetLevel;
        case 3: return m_dryLevel;
        case 4: return m_preDelay;
        default: return 0.0f;
    }
}

void ReverbProcessor::setRoomSize(float size) {
    m_roomSize = clamp(size, 0.0f, 1.0f);
    updateSonyReflectionDelays();
}

void ReverbProcessor::setDecayTime(float decay) {
    m_decayTime = clamp(decay, 0.1f, 10.0f);
}

void ReverbProcessor::setWetLevel(float wet) {
    m_wetLevel = clamp(wet, 0.0f, 1.0f);
}

void ReverbProcessor::setDryLevel(float dry) {
    m_dryLevel = clamp(dry, 0.0f, 1.0f);
}

void ReverbProcessor::setPreDelay(float preDelay) {
    m_preDelay = clamp(preDelay, 0.0f, 100.0f);
    if (m_sampleRate > 0) {
        m_preDelaySamples = (int)(m_preDelay * m_sampleRate / 1000.0f);
        m_preDelaySamples = clamp(m_preDelaySamples, 0, LATE_REVERB_SIZE - 1);
    }
}

void ReverbProcessor::setupSonyCafeReflections() {
    m_reflections[0] = {150, 0.65f, 0.75f, 0.8f, {0}, 0};
    m_reflections[1] = {220, 0.58f, 0.70f, 0.75f, {0}, 0};
    m_reflections[2] = {280, 0.52f, 0.65f, 0.72f, {0}, 0};
    m_reflections[3] = {340, 0.45f, 0.60f, 0.68f, {0}, 0};
    m_reflections[4] = {420, 0.38f, 0.55f, 0.65f, {0}, 0};
    m_reflections[5] = {490, 0.32f, 0.48f, 0.60f, {0}, 0};
    m_reflections[6] = {560, 0.25f, 0.40f, 0.55f, {0}, 0};
    m_reflections[7] = {630, 0.18f, 0.32f, 0.50f, {0}, 0};
    m_reflections[8] = {720, 0.12f, 0.25f, 0.45f, {0}, 0};
    m_reflections[9] = {810, 0.08f, 0.18f, 0.40f, {0}, 0};
    m_reflections[10] = {900, 0.05f, 0.12f, 0.35f, {0}, 0};
    m_reflections[11] = {990, 0.03f, 0.08f, 0.30f, {0}, 0};

    for (auto & reflection : m_reflections) {
        memset(reflection.delayBuffer, 0, sizeof(reflection.delayBuffer));
        reflection.delayIndex = 0;
    }
}

void ReverbProcessor::updateSonyReflectionDelays() {
    float roomScale = 0.3f + m_roomSize * 1.4f;
    for (auto & m_reflection : m_reflections) {
        int baseDelay = m_reflection.delaySamples;
        m_reflection.delaySamples = (int)(baseDelay * roomScale);
        m_reflection.delaySamples = clamp(m_reflection.delaySamples, 1, MAX_REFLECTION_DELAY - 1);
    }
}

void ReverbProcessor::clearBuffers() {
    memset(m_lateReverbBuffer, 0, sizeof(m_lateReverbBuffer));
    m_lateReverbIndex[0] = 0;
    m_lateReverbIndex[1] = 0;
    for (auto & reflection : m_reflections) {
        memset(reflection.delayBuffer, 0, sizeof(reflection.delayBuffer));
        reflection.delayIndex = 0;
    }
}