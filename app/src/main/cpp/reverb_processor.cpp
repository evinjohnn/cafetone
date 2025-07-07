#include "reverb_processor.h"
#include <cstring>
#include <cmath>

ReverbProcessor::ReverbProcessor()
    : m_roomSize(0.3f)
    , m_damping(0.5f)
    , m_wetLevel(0.15f)
    , m_dryLevel(0.85f)
    , m_lateReverbGain(0.1f) {
    
    setupReflections();
    clearBuffers();
}

ReverbProcessor::~ReverbProcessor() {
}

void ReverbProcessor::process(const float* input, float* output, int frames) {
    // Mono processing - simplified
    for (int i = 0; i < frames; i++) {
        float drySignal = input[i] * m_dryLevel;
        float wetSignal = 0.0f;
        
        // Process early reflections
        for (int r = 0; r < NUM_REFLECTIONS; r++) {
            wetSignal += processReflection(input[i], m_reflections[r]);
        }
        
        // Add late reverb
        int lateIndex = m_lateReverbIndex[0];
        wetSignal += m_lateReverbBuffer[0][lateIndex] * m_lateReverbGain;
        
        // Update late reverb buffer
        m_lateReverbBuffer[0][lateIndex] = wetSignal * 0.95f;
        m_lateReverbIndex[0] = (lateIndex + 1) % LATE_REVERB_SIZE;
        
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
        // Process left channel
        float leftDry = leftIn[i] * m_dryLevel;
        float leftWet = 0.0f;
        
        // Early reflections for left channel
        for (int r = 0; r < NUM_REFLECTIONS; r++) {
            leftWet += processReflection(leftIn[i], m_reflections[r]);
        }
        
        // Late reverb for left channel
        int lateLeftIndex = m_lateReverbIndex[0];
        leftWet += m_lateReverbBuffer[0][lateLeftIndex] * m_lateReverbGain;
        m_lateReverbBuffer[0][lateLeftIndex] = leftWet * 0.95f;
        m_lateReverbIndex[0] = (lateLeftIndex + 1) % LATE_REVERB_SIZE;
        
        // Process right channel
        float rightDry = rightIn[i] * m_dryLevel;
        float rightWet = 0.0f;
        
        // Early reflections for right channel (slightly different timing)
        for (int r = 0; r < NUM_REFLECTIONS; r++) {
            rightWet += processReflection(rightIn[i], m_reflections[r]);
        }
        
        // Late reverb for right channel
        int lateRightIndex = m_lateReverbIndex[1];
        rightWet += m_lateReverbBuffer[1][lateRightIndex] * m_lateReverbGain;
        m_lateReverbBuffer[1][lateRightIndex] = rightWet * 0.95f;
        m_lateReverbIndex[1] = (lateRightIndex + 1) % LATE_REVERB_SIZE;
        
        // Apply damping to high frequencies
        float dampingCoeff = 1.0f - m_damping * 0.3f;
        leftWet *= dampingCoeff;
        rightWet *= dampingCoeff;
        
        leftOut[i] = leftDry + leftWet * m_wetLevel;
        rightOut[i] = rightDry + rightWet * m_wetLevel;
    }
}

void ReverbProcessor::setSampleRate(int sampleRate) {
    AudioProcessor::setSampleRate(sampleRate);
    updateReflectionDelays();
}

void ReverbProcessor::reset() {
    clearBuffers();
}

void ReverbProcessor::setParameter(int param, float value) {
    switch (param) {
        case 0: // Room size
            setRoomSize(value);
            break;
        case 1: // Damping
            setDamping(value);
            break;
        case 2: // Wet level
            setWetLevel(value);
            break;
        case 3: // Dry level
            setDryLevel(value);
            break;
    }
}

float ReverbProcessor::getParameter(int param) const {
    switch (param) {
        case 0:
            return m_roomSize;
        case 1:
            return m_damping;
        case 2:
            return m_wetLevel;
        case 3:
            return m_dryLevel;
        default:
            return 0.0f;
    }
}

void ReverbProcessor::setRoomSize(float size) {
    m_roomSize = clamp(size, 0.0f, 1.0f);
    updateReflectionDelays();
}

void ReverbProcessor::setDamping(float damping) {
    m_damping = clamp(damping, 0.0f, 1.0f);
}

void ReverbProcessor::setWetLevel(float wet) {
    m_wetLevel = clamp(wet, 0.0f, 1.0f);
}

void ReverbProcessor::setDryLevel(float dry) {
    m_dryLevel = clamp(dry, 0.0f, 1.0f);
}

void ReverbProcessor::setupReflections() {
    // Setup early reflection pattern for café acoustics
    // These delays simulate reflections from walls, ceiling, and furniture
    
    // Primary reflections (first 4)
    m_reflections[0] = {120, 0.6f, 0.8f, {0}, 0};  // Front wall
    m_reflections[1] = {180, 0.5f, 0.7f, {0}, 0};  // Side wall
    m_reflections[2] = {240, 0.4f, 0.6f, {0}, 0};  // Back wall
    m_reflections[3] = {300, 0.3f, 0.5f, {0}, 0};  // Ceiling
    
    // Secondary reflections (next 4)
    m_reflections[4] = {360, 0.2f, 0.4f, {0}, 0};  // Corner reflection
    m_reflections[5] = {420, 0.15f, 0.3f, {0}, 0}; // Furniture reflection
    m_reflections[6] = {480, 0.1f, 0.2f, {0}, 0};  // Multiple bounce
    m_reflections[7] = {540, 0.05f, 0.1f, {0}, 0}; // Distant reflection
    
    // Clear all delay buffers
    for (int i = 0; i < NUM_REFLECTIONS; i++) {
        memset(m_reflections[i].delayBuffer, 0, sizeof(m_reflections[i].delayBuffer));
        m_reflections[i].delayIndex = 0;
    }
}

void ReverbProcessor::updateReflectionDelays() {
    // Scale reflection delays based on room size
    for (int i = 0; i < NUM_REFLECTIONS; i++) {
        int baseDelay = m_reflections[i].delaySamples;
        m_reflections[i].delaySamples = (int)(baseDelay * (0.5f + m_roomSize * 1.5f));
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

float ReverbProcessor::processReflection(float input, Reflection& reflection) {
    // Get delayed sample
    int readIndex = (reflection.delayIndex - reflection.delaySamples + MAX_REFLECTION_DELAY) % MAX_REFLECTION_DELAY;
    float delayedSample = reflection.delayBuffer[readIndex];
    
    // Apply gain and filtering
    float output = delayedSample * reflection.gain;
    
    // Simple low-pass filter for damping
    output = output * reflection.filterCoeff + input * (1.0f - reflection.filterCoeff);
    
    // Store input in delay buffer
    reflection.delayBuffer[reflection.delayIndex] = input;
    reflection.delayIndex = (reflection.delayIndex + 1) % MAX_REFLECTION_DELAY;
    
    return output;
} 