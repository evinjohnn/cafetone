#include "eq_processor.h"
#include <cmath>

EQProcessor::EQProcessor()
    : m_highPassFreq(80.0f)
    , m_lowPassFreq(8000.0f)
    , m_cafeEQEnabled(true)
    , m_distanceEQ(0.8f) {
    
    // Initialize filter state
    m_hpState[0] = m_hpState[1] = 0.0f;
    m_lpState[0] = m_lpState[1] = 0.0f;
    
    // Setup café EQ bands (simulating distant speaker response)
    setupCafeEQ();
}

EQProcessor::~EQProcessor() {
}

void EQProcessor::process(const float* input, float* output, int frames) {
    if (!m_initialized) {
        // Pass through if not initialized
        for (int i = 0; i < frames; i++) {
            output[i] = input[i];
        }
        return;
    }
    
    for (int i = 0; i < frames; i++) {
        float sample = input[i];
        
        // Apply high-pass filter
        sample = processFilter(sample, m_hpCoeff, m_hpState);
        
        // Apply low-pass filter
        sample = processFilter(sample, m_lpCoeff, m_lpState);
        
        // Apply café EQ curve
        if (m_cafeEQEnabled) {
            float eqGain = 1.0f;
            for (int band = 0; band < NUM_EQ_BANDS; band++) {
                // Simple parametric EQ simulation
                float freq = m_eqBands[band].frequency;
                float gain = m_eqBands[band].gain;
                float q = m_eqBands[band].q;
                
                // Simplified EQ response (in real implementation, use proper biquad filters)
                float normalizedFreq = freq / (m_sampleRate * 0.5f);
                float response = 1.0f + gain * expf(-q * (normalizedFreq - 0.1f) * (normalizedFreq - 0.1f));
                eqGain *= response;
            }
            sample *= eqGain;
        }
        
        // Apply distance EQ (high-end rolloff)
        if (m_distanceEQ > 0.0f) {
            float distanceAtten = 1.0f - m_distanceEQ * 0.3f; // Up to 30% high-end reduction
            sample *= distanceAtten;
        }
        
        output[i] = sample;
    }
}

void EQProcessor::setSampleRate(int sampleRate) {
    AudioProcessor::setSampleRate(sampleRate);
    updateHighPassCoeffs();
    updateLowPassCoeffs();
}

void EQProcessor::reset() {
    m_hpState[0] = m_hpState[1] = 0.0f;
    m_lpState[0] = m_lpState[1] = 0.0f;
}

void EQProcessor::setParameter(int param, float value) {
    switch (param) {
        case 0: // High-pass frequency
            setHighPassFilter(value);
            break;
        case 1: // Low-pass frequency
            setLowPassFilter(value);
            break;
        case 2: // Café EQ enabled
            setCafeEQ(value > 0.5f);
            break;
        case 3: // Distance EQ
            setDistanceEQ(value);
            break;
    }
}

float EQProcessor::getParameter(int param) const {
    switch (param) {
        case 0:
            return m_highPassFreq;
        case 1:
            return m_lowPassFreq;
        case 2:
            return m_cafeEQEnabled ? 1.0f : 0.0f;
        case 3:
            return m_distanceEQ;
        default:
            return 0.0f;
    }
}

void EQProcessor::setHighPassFilter(float frequency) {
    m_highPassFreq = clamp(frequency, 20.0f, 1000.0f);
    updateHighPassCoeffs();
}

void EQProcessor::setLowPassFilter(float frequency) {
    m_lowPassFreq = clamp(frequency, 1000.0f, 20000.0f);
    updateLowPassCoeffs();
}

void EQProcessor::setCafeEQ(bool enabled) {
    m_cafeEQEnabled = enabled;
}

void EQProcessor::setDistanceEQ(float distance) {
    m_distanceEQ = clamp(distance, 0.0f, 1.0f);
}

void EQProcessor::updateHighPassCoeffs() {
    // First-order high-pass filter
    float omega = frequencyToRadians(m_highPassFreq);
    float alpha = omega / (omega + 1.0f);
    
    m_hpCoeff[0] = alpha;
    m_hpCoeff[1] = alpha - 1.0f;
}

void EQProcessor::updateLowPassCoeffs() {
    // First-order low-pass filter
    float omega = frequencyToRadians(m_lowPassFreq);
    float alpha = omega / (omega + 1.0f);
    
    m_lpCoeff[0] = alpha;
    m_lpCoeff[1] = 1.0f - alpha;
}

void EQProcessor::setupCafeEQ() {
    // Café EQ curve simulating distant speaker response
    // Based on typical café acoustics and air absorption
    m_eqBands[0] = {100.0f, -2.0f, 1.0f};   // Slight low-end rolloff
    m_eqBands[1] = {400.0f, 1.5f, 0.8f};    // Warm mid-bass boost
    m_eqBands[2] = {1000.0f, -1.0f, 1.2f};  // Mid-range dip
    m_eqBands[3] = {3000.0f, 2.0f, 1.0f};   // Presence boost
    m_eqBands[4] = {8000.0f, -3.0f, 0.6f};  // High-end rolloff
}

float EQProcessor::processFilter(float input, float* coeffs, float* state) {
    // First-order IIR filter implementation
    float output = coeffs[0] * input + coeffs[1] * state[0];
    state[0] = output;
    return output;
} 