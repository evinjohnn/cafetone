#include "haas_processor.h"
#include <cstring>

HaasProcessor::HaasProcessor()
    : m_delayAmount(5.0f)
    , m_width(0.6f)
    , m_balance(0.0f)
    , m_delaySamples(0)
    , m_delayCoeff(0.5f) {
    
    clearDelayBuffer();
}

HaasProcessor::~HaasProcessor() {
}

void HaasProcessor::process(const float* input, float* output, int frames) {
    // Mono processing - pass through
    memcpy(output, input, frames * sizeof(float));
}

void HaasProcessor::process(const float* leftIn, const float* rightIn,
                           float* leftOut, float* rightOut, int frames) {
    if (!m_initialized) return;
    
    for (int i = 0; i < frames; i++) {
        // Get delayed samples
        int delayReadIndex = (m_delayIndex[0] - m_delaySamples + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;
        float delayedLeft = m_delayBuffer[0][delayReadIndex];
        float delayedRight = m_delayBuffer[1][delayReadIndex];
        
        // Apply Haas effect (interaural time difference)
        float leftSignal = leftIn[i];
        float rightSignal = rightIn[i];
        
        // Add delayed signal to opposite channel
        leftOut[i] = leftSignal + delayedRight * m_delayCoeff * m_width;
        rightOut[i] = rightSignal + delayedLeft * m_delayCoeff * m_width;
        
        // Apply balance adjustment
        if (m_balance > 0.0f) {
            leftOut[i] *= (1.0f - m_balance * 0.5f);
            rightOut[i] *= (1.0f + m_balance * 0.5f);
        } else {
            leftOut[i] *= (1.0f + m_balance * 0.5f);
            rightOut[i] *= (1.0f - m_balance * 0.5f);
        }
        
        // Store in delay buffer
        m_delayBuffer[0][m_delayIndex[0]] = leftSignal;
        m_delayBuffer[1][m_delayIndex[1]] = rightSignal;
        
        // Update delay indices
        m_delayIndex[0] = (m_delayIndex[0] + 1) % MAX_DELAY_SAMPLES;
        m_delayIndex[1] = (m_delayIndex[1] + 1) % MAX_DELAY_SAMPLES;
    }
}

void HaasProcessor::setSampleRate(int sampleRate) {
    AudioProcessor::setSampleRate(sampleRate);
    updateDelaySamples();
}

void HaasProcessor::reset() {
    clearDelayBuffer();
}

void HaasProcessor::setParameter(int param, float value) {
    switch (param) {
        case 0: // Delay amount
            setDelayAmount(value);
            break;
        case 1: // Width
            setWidth(value);
            break;
        case 2: // Balance
            setBalance(value);
            break;
    }
}

float HaasProcessor::getParameter(int param) const {
    switch (param) {
        case 0:
            return m_delayAmount;
        case 1:
            return m_width;
        case 2:
            return m_balance;
        default:
            return 0.0f;
    }
}

void HaasProcessor::setDelayAmount(float delayMs) {
    m_delayAmount = clamp(delayMs, 0.0f, 15.0f);
    updateDelaySamples();
}

void HaasProcessor::setWidth(float width) {
    m_width = clamp(width, 0.0f, 1.0f);
}

void HaasProcessor::setBalance(float balance) {
    m_balance = clamp(balance, -1.0f, 1.0f);
}

void HaasProcessor::updateDelaySamples() {
    m_delaySamples = (int)(m_delayAmount * m_sampleRate / 1000.0f);
    m_delaySamples = clamp(m_delaySamples, 0, MAX_DELAY_SAMPLES - 1);
}

void HaasProcessor::clearDelayBuffer() {
    memset(m_delayBuffer, 0, sizeof(m_delayBuffer));
    m_delayIndex[0] = 0;
    m_delayIndex[1] = 0;
} 