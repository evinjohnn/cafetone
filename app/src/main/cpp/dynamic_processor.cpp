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
        
        // Process multi-band compressor
        processMultiBandCompressor(sample, sample, 0);
        
        // Apply makeup gain
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
        
        // Sony Café Mode - Dynamic Processing Implementation
        
        // 1. Multi-band compressor (3-band)
        processMultiBandCompressor(leftSample, leftSample, 0);
        processMultiBandCompressor(rightSample, rightSample, 1);
        
        // 2. Distance compression simulation
        // This simulates how distance affects dynamic range
        applyDistanceCompression(leftSample, 0);
        applyDistanceCompression(rightSample, 0);
        
        // 3. Soft limiting for background feel
        if (m_softLimitingEnabled) {
            applySoftLimiter(leftSample, rightSample);
        }
        
        // 4. Makeup gain compensation
        leftSample *= m_makeupGain;
        rightSample *= m_makeupGain;
        
        leftOut[i] = leftSample;
        rightOut[i] = rightSample;
    }
}

void DynamicProcessor::processMultiBandCompressor(float input, float& output, int channel) {
    // Split into 3 bands: Low (20-300Hz), Mid (300-3000Hz), High (3000Hz+)
    
    // Simple 3-band splitting (in production, use proper crossover filters)
    float lowBand = input;
    float midBand = input;
    float highBand = input;
    
    // Process each band with Sony-specific compression
    lowBand = processCompressorBand(lowBand, m_bands[0]);
    midBand = processCompressorBand(midBand, m_bands[1]);
    highBand = processCompressorBand(highBand, m_bands[2]);
    
    // Recombine bands
    output = (lowBand + midBand + highBand) * 0.33f;
}

float DynamicProcessor::processCompressorBand(float input, CompressorBand& band) {
    // Calculate compression gain
    float gain = calculateCompressorGain(
        input, 
        band.threshold, 
        band.ratio, 
        band.envelope, 
        band.attack, 
        band.release
    );
    
    // Apply gain
    float output = input * gain * band.gain;
    
    return output;
}

void DynamicProcessor::applyDistanceCompression(float& sample, int band) {
    // Sony distance compression simulation
    // Distant sounds have reduced dynamic range
    
    float compressionAmount = m_distanceCompression;
    
    // More compression for higher frequencies (air absorption effect)
    if (band > 1) {
        compressionAmount *= 1.3f;
    }
    
    // Soft compression characteristic
    float threshold = 0.3f;
    if (abs(sample) > threshold) {
        float excess = abs(sample) - threshold;
        float compressedExcess = excess * (1.0f - compressionAmount * 0.5f);
        sample = (sample > 0 ? 1.0f : -1.0f) * (threshold + compressedExcess);
    }
}

void DynamicProcessor::applySoftLimiter(float& leftSample, float& rightSample) {
    // Sony soft limiter for background feel
    
    // Calculate peak level
    float peakLevel = std::max(abs(leftSample), abs(rightSample));
    
    if (peakLevel > m_limiterThreshold) {
        // Calculate limiter gain
        float excess = peakLevel - m_limiterThreshold;
        float limitGain = m_limiterThreshold / peakLevel;
        
        // Smooth the gain reduction with envelope following
        float targetGain = limitGain;
        float attack = 0.001f;  // Fast attack
        float release = 0.01f;  // Slow release
        
        if (targetGain < m_limiterEnvelope[0]) {
            m_limiterEnvelope[0] += (targetGain - m_limiterEnvelope[0]) * attack;
        } else {
            m_limiterEnvelope[0] += (targetGain - m_limiterEnvelope[0]) * release;
        }
        
        // Apply limiting
        leftSample *= m_limiterEnvelope[0];
        rightSample *= m_limiterEnvelope[0];
    }
}

float DynamicProcessor::calculateCompressorGain(float input, float threshold, float ratio, 
                                               float& envelope, float attack, float release) {
    float inputLevel = abs(input);
    
    // Update envelope
    if (inputLevel > envelope) {
        envelope += (inputLevel - envelope) * attack;
    } else {
        envelope += (inputLevel - envelope) * release;
    }
    
    // Calculate gain reduction
    if (envelope > threshold) {
        float excess = envelope - threshold;
        float compressedExcess = excess / ratio;
        float targetLevel = threshold + compressedExcess;
        return targetLevel / (envelope + 1e-10f); // Avoid division by zero
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
        case 0: // Distance compression
            setDistanceCompression(value);
            break;
        case 1: // Makeup gain
            setMakeupGain(value);
            break;
        case 2: // Soft limiting
            setSoftLimiting(value > 0.5f);
            break;
    }
}

float DynamicProcessor::getParameter(int param) const {
    switch (param) {
        case 0:
            return m_distanceCompression;
        case 1:
            return m_makeupGain;
        case 2:
            return m_softLimitingEnabled ? 1.0f : 0.0f;
        default:
            return 0.0f;
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
    // Sony Café Mode - Multi-band compressor setup
    
    // Low band (20-300Hz) - Gentle compression
    m_bands[0] = {
        0.5f,    // threshold
        3.0f,    // ratio
        0.01f,   // attack
        0.1f,    // release
        1.0f,    // gain
        0.0f,    // envelope
        0.0f     // previous sample
    };
    
    // Mid band (300-3000Hz) - Moderate compression
    m_bands[1] = {
        0.4f,    // threshold
        4.0f,    // ratio
        0.005f,  // attack
        0.05f,   // release
        1.1f,    // gain (slight boost)
        0.0f,    // envelope
        0.0f     // previous sample
    };
    
    // High band (3000Hz+) - Stronger compression for distance effect
    m_bands[2] = {
        0.3f,    // threshold
        6.0f,    // ratio
        0.002f,  // attack
        0.02f,   // release
        0.9f,    // gain (slight reduction for distance)
        0.0f,    // envelope
        0.0f     // previous sample
    };
}

void DynamicProcessor::updateCrossoverFilters() {
    // Setup crossover filters at 300Hz and 3000Hz
    // Simplified implementation - in production use proper Linkwitz-Riley filters
    
    m_lowMidCrossover.frequency = 300.0f;
    m_midHighCrossover.frequency = 3000.0f;
    
    // Calculate filter coefficients (simple 1-pole filters)
    float omega1 = 2.0f * M_PI * 300.0f / m_sampleRate;
    float omega2 = 2.0f * M_PI * 3000.0f / m_sampleRate;
    
    m_lowMidCrossover.coeff[0] = omega1 / (omega1 + 1.0f);
    m_lowMidCrossover.coeff[1] = 1.0f - m_lowMidCrossover.coeff[0];
    
    m_midHighCrossover.coeff[0] = omega2 / (omega2 + 1.0f);
    m_midHighCrossover.coeff[1] = 1.0f - m_midHighCrossover.coeff[0];
}

void DynamicProcessor::clearStates() {
    // Clear compressor states
    for (int i = 0; i < NUM_BANDS; i++) {
        m_bands[i].envelope = 0.0f;
        m_bands[i].previousSample = 0.0f;
    }
    
    // Clear limiter envelopes
    m_limiterEnvelope[0] = 1.0f;
    m_limiterEnvelope[1] = 1.0f;
    
    // Clear crossover states
    m_lowMidCrossover.state[0] = 0.0f;
    m_lowMidCrossover.state[1] = 0.0f;
    m_midHighCrossover.state[0] = 0.0f;
    m_midHighCrossover.state[1] = 0.0f;
}

float DynamicProcessor::processCrossoverFilter(float input, CrossoverFilter& filter) {
    // Simple 1-pole filter implementation
    float output = filter.coeff[0] * input + filter.coeff[1] * filter.state[0];
    filter.state[0] = output;
    return output;
}