#include "reverb_processor.h"
#include <cstring>
#include <cmath>

ReverbProcessor::ReverbProcessor()
    : m_roomSize(0.7f)        // Sony: 70% (large café space)
    , m_decayTime(2.1f)       // Sony: 2.1 seconds
    , m_preDelay(42.0f)       // Sony: 42ms
    , m_wetLevel(0.45f)       // Sony: 45% wet
    , m_dryLevel(0.55f)       // 55% dry for balance
    , m_highDamping(0.8f)     // Sony: -8dB at 5kHz
    , m_lowDamping(0.4f)      // Sony: -4dB at 150Hz
    , m_lateReverbGain(0.15f) {
    
    setupSonyCafeReflections();
    clearBuffers();
}

ReverbProcessor::~ReverbProcessor() {
}

void ReverbProcessor::process(const float* input, float* output, int frames) {
    // Mono processing
    for (int i = 0; i < frames; i++) {
        float drySignal = input[i] * m_dryLevel;
        float wetSignal = 0.0f;
        
        // Process Sony café early reflections
        for (int r = 0; r < NUM_REFLECTIONS; r++) {
            wetSignal += processSonyReflection(input[i], m_reflections[r]);
        }
        
        // Sony late reverb processing
        wetSignal += processSonyLateReverb(input[i], 0);
        
        output[i] = drySignal + wetSignal * m_wetLevel;
    }
}

void ReverbProcessor::process(const float* leftIn, const float* rightIn,
                             float* leftOut, float* rightOut, int frames) {
    if (!m_initialized) {
        // Pass through if not initialized
        for (int i = 0; i < frames; i++) {
            leftOut[i] = leftIn[i];
            rightOut[i] = rightIn[i];
        }
        return;
    }
    
    for (int i = 0; i < frames; i++) {
        // Sony Café Mode - Reverb Engine Implementation
        
        // Dry signals
        float leftDry = leftIn[i] * m_dryLevel;
        float rightDry = rightIn[i] * m_dryLevel;
        
        // Early reflections processing
        float leftWet = 0.0f;
        float rightWet = 0.0f;
        
        // Process early reflections with Sony café acoustics
        for (int r = 0; r < NUM_REFLECTIONS; r++) {
            leftWet += processSonyReflection(leftIn[i], m_reflections[r]);
            // Slightly different timing for right channel (stereo spread)
            rightWet += processSonyReflection(rightIn[i], m_reflections[r], true);
        }
        
        // Late reverb processing (separate for L/R)
        leftWet += processSonyLateReverb(leftIn[i], 0);
        rightWet += processSonyLateReverb(rightIn[i], 1);
        
        // Apply Sony-specific damping
        applySonyDamping(leftWet, rightWet);
        
        // Apply echo/delay effects for spatial positioning
        applySonyEchoEffects(leftWet, rightWet, leftWet, rightWet);
        
        // Final mix with makeup gain compensation
        float makeupGain = 1.0f + (m_wetLevel * 0.2f); // Slight boost for perceived loudness
        
        leftOut[i] = (leftDry + leftWet * m_wetLevel) * makeupGain;
        rightOut[i] = (rightDry + rightWet * m_wetLevel) * makeupGain;
    }
}

float ReverbProcessor::processSonyReflection(float input, Reflection& reflection, bool rightChannel) {
    // Get delayed sample with Sony café timing
    int delayOffset = rightChannel ? 2 : 0; // Slight stereo offset
    int readIndex = (reflection.delayIndex - reflection.delaySamples - delayOffset + MAX_REFLECTION_DELAY) % MAX_REFLECTION_DELAY;
    float delayedSample = reflection.delayBuffer[readIndex];
    
    // Apply Sony café-specific gain and filtering
    float output = delayedSample * reflection.gain;
    
    // Sony's frequency-dependent damping
    float dampingFactor = reflection.dampingCoeff;
    if (rightChannel) {
        dampingFactor *= 0.95f; // Slight asymmetry for realism
    }
    
    // Apply low-pass filtering for natural sound absorption
    output = output * dampingFactor + input * (1.0f - dampingFactor) * 0.1f;
    
    // Store input in delay buffer
    reflection.delayBuffer[reflection.delayIndex] = input;
    reflection.delayIndex = (reflection.delayIndex + 1) % MAX_REFLECTION_DELAY;
    
    return output;
}

float ReverbProcessor::processSonyLateReverb(float input, int channel) {
    // Sony late reverb with exponential decay
    int lateIndex = m_lateReverbIndex[channel];
    float lateSignal = m_lateReverbBuffer[channel][lateIndex];
    
    // Apply Sony decay characteristics
    float decayFactor = powf(0.001f, 1.0f / (m_decayTime * m_sampleRate)); // -60dB decay
    lateSignal *= decayFactor;
    
    // Add input with pre-delay
    int preDelayIndex = (lateIndex - m_preDelaySamples + LATE_REVERB_SIZE) % LATE_REVERB_SIZE;
    float preDelayedInput = m_lateReverbBuffer[channel][preDelayIndex];
    
    // Update late reverb buffer
    m_lateReverbBuffer[channel][lateIndex] = input * 0.2f + lateSignal * 0.95f;
    m_lateReverbIndex[channel] = (lateIndex + 1) % LATE_REVERB_SIZE;
    
    return lateSignal * m_lateReverbGain;
}

void ReverbProcessor::applySonyDamping(float& leftWet, float& rightWet) {
    // Sony Café Mode damping characteristics
    
    // High-frequency damping: -8dB at 5kHz
    float highFreqDamping = m_highDamping;
    leftWet *= (1.0f - highFreqDamping * 0.6f);   // -8dB ≈ 0.4 remaining
    rightWet *= (1.0f - highFreqDamping * 0.6f);
    
    // Low-frequency damping: -4dB at 150Hz
    float lowFreqDamping = m_lowDamping;
    leftWet *= (1.0f - lowFreqDamping * 0.37f);   // -4dB ≈ 0.63 remaining
    rightWet *= (1.0f - lowFreqDamping * 0.37f);
}

void ReverbProcessor::applySonyEchoEffects(float leftIn, float rightIn, float& leftOut, float& rightOut) {
    // Sony echo/delay effects for spatial positioning
    
    // Multiple delay taps for spatial positioning
    int echoDelay1 = (int)(120.0f * m_sampleRate / 1000.0f); // 120ms
    int echoDelay2 = (int)(180.0f * m_sampleRate / 1000.0f); // 180ms
    int echoDelay3 = (int)(240.0f * m_sampleRate / 1000.0f); // 240ms
    
    float echo1 = 0.0f, echo2 = 0.0f, echo3 = 0.0f;
    
    // Get echo samples (simplified - would use proper delay lines in full implementation)
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
    
    // Apply echo effects
    leftOut = leftIn + echo1 + echo2 * 0.8f + echo3 * 0.6f;
    rightOut = rightIn + echo1 * 0.8f + echo2 + echo3 * 0.7f;
}

void ReverbProcessor::setSampleRate(int sampleRate) {
    AudioProcessor::setSampleRate(sampleRate);
    updateSonyReflectionDelays();
    
    // Update pre-delay samples
    m_preDelaySamples = (int)(m_preDelay * m_sampleRate / 1000.0f);
    m_preDelaySamples = clamp(m_preDelaySamples, 0, LATE_REVERB_SIZE - 1);
}

void ReverbProcessor::reset() {
    clearBuffers();
}

void ReverbProcessor::setParameter(int param, float value) {
    switch (param) {
        case 0: // Room size
            setRoomSize(value);
            break;
        case 1: // Decay time
            setDecayTime(value);
            break;
        case 2: // Wet level
            setWetLevel(value);
            break;
        case 3: // Dry level
            setDryLevel(value);
            break;
        case 4: // Pre-delay
            setPreDelay(value);
            break;
    }
}

float ReverbProcessor::getParameter(int param) const {
    switch (param) {
        case 0:
            return m_roomSize;
        case 1:
            return m_decayTime;
        case 2:
            return m_wetLevel;
        case 3:
            return m_dryLevel;
        case 4:
            return m_preDelay;
        default:
            return 0.0f;
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
    // Sony Café Mode - Early reflection pattern
    // Simulates large café space with specific acoustic characteristics
    
    // Primary café reflections (walls, ceiling, floor)
    m_reflections[0] = {150, 0.65f, 0.75f, 0.8f, {0}, 0};  // Front wall
    m_reflections[1] = {220, 0.58f, 0.70f, 0.75f, {0}, 0}; // Left wall
    m_reflections[2] = {280, 0.52f, 0.65f, 0.72f, {0}, 0}; // Right wall
    m_reflections[3] = {340, 0.45f, 0.60f, 0.68f, {0}, 0}; // Ceiling
    
    // Secondary café reflections (furniture, back wall)
    m_reflections[4] = {420, 0.38f, 0.55f, 0.65f, {0}, 0}; // Back wall
    m_reflections[5] = {490, 0.32f, 0.48f, 0.60f, {0}, 0}; // Table reflections
    m_reflections[6] = {560, 0.25f, 0.40f, 0.55f, {0}, 0}; // Chair/furniture
    m_reflections[7] = {630, 0.18f, 0.32f, 0.50f, {0}, 0}; // Floor reflection
    
    // Tertiary reflections (ambient café atmosphere)
    m_reflections[8] = {720, 0.12f, 0.25f, 0.45f, {0}, 0};  // Corner reflections
    m_reflections[9] = {810, 0.08f, 0.18f, 0.40f, {0}, 0};  // Distant surfaces
    m_reflections[10] = {900, 0.05f, 0.12f, 0.35f, {0}, 0}; // Multiple bounces
    m_reflections[11] = {990, 0.03f, 0.08f, 0.30f, {0}, 0}; // Ambient reverb tail
    
    // Clear all delay buffers
    for (int i = 0; i < NUM_REFLECTIONS; i++) {
        memset(m_reflections[i].delayBuffer, 0, sizeof(m_reflections[i].delayBuffer));
        m_reflections[i].delayIndex = 0;
    }
}

void ReverbProcessor::updateSonyReflectionDelays() {
    // Scale reflection delays based on Sony café room size
    float roomScale = 0.3f + m_roomSize * 1.4f; // 30% to 170% scaling
    
    for (int i = 0; i < NUM_REFLECTIONS; i++) {
        int baseDelay = m_reflections[i].delaySamples;
        m_reflections[i].delaySamples = (int)(baseDelay * roomScale);
        m_reflections[i].delaySamples = clamp(m_reflections[i].delaySamples, 1, MAX_REFLECTION_DELAY - 1);
    }
}

void ReverbProcessor::clearBuffers() {
    // Clear late reverb buffers
    memset(m_lateReverbBuffer, 0, sizeof(m_lateReverbBuffer));
    m_lateReverbIndex[0] = 0;
    m_lateReverbIndex[1] = 0;
    
    // Clear reflection buffers
    for (int i = 0; i < NUM_REFLECTIONS; i++) {
        memset(m_reflections[i].delayBuffer, 0, sizeof(m_reflections[i].delayBuffer));
        m_reflections[i].delayIndex = 0;
    }
}