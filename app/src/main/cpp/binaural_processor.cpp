#include "binaural_processor.h"
#include <cstring>
#include <cmath>

BinauralProcessor::BinauralProcessor()
    : m_distance(0.8f)
    , m_azimuth(0.0f)
    , m_elevation(0.0f)
    , m_distanceAtten(0.7f)
    , m_airAbsorption(0.1f)
    , m_itdSamples(0) {
    
    clearDelayBuffer();
    updateHRTFCoeffs();
    updateDistanceSimulation();
}

BinauralProcessor::~BinauralProcessor() {
}

void BinauralProcessor::process(const float* input, float* output, int frames) {
    // Mono processing - apply distance simulation only
    for (int i = 0; i < frames; i++) {
        output[i] = input[i] * m_distanceAtten;
    }
}

void BinauralProcessor::process(const float* leftIn, const float* rightIn,
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
        // Get delayed samples for ITD simulation
        int delayReadLeft = (m_delayIndex[0] - m_itdSamples + MAX_ITD_SAMPLES) % MAX_ITD_SAMPLES;
        int delayReadRight = (m_delayIndex[1] - m_itdSamples + MAX_ITD_SAMPLES) % MAX_ITD_SAMPLES;
        
        float delayedLeft = m_delayBuffer[0][delayReadLeft];
        float delayedRight = m_delayBuffer[1][delayReadRight];
        
        // Apply HRTF processing
        float leftSignal = leftIn[i];
        float rightSignal = rightIn[i];
        
        // Apply interaural time difference (ITD)
        leftSignal = leftSignal * 0.7f + delayedRight * 0.3f;
        rightSignal = rightSignal * 0.7f + delayedLeft * 0.3f;
        
        // Apply HRTF filtering (simplified)
        leftSignal = processHRTF(leftSignal, m_hrtfCoeffs.leftFilter);
        rightSignal = processHRTF(rightSignal, m_hrtfCoeffs.rightFilter);
        
        // Apply distance simulation
        leftSignal *= m_distanceAtten;
        rightSignal *= m_distanceAtten;
        
        // Apply air absorption (frequency-dependent attenuation)
        float airAbsorb = 1.0f - m_airAbsorption * m_distance;
        leftSignal *= airAbsorb;
        rightSignal *= airAbsorb;
        
        // Store in delay buffer
        m_delayBuffer[0][m_delayIndex[0]] = leftIn[i];
        m_delayBuffer[1][m_delayIndex[1]] = rightIn[i];
        
        // Update delay indices
        m_delayIndex[0] = (m_delayIndex[0] + 1) % MAX_ITD_SAMPLES;
        m_delayIndex[1] = (m_delayIndex[1] + 1) % MAX_ITD_SAMPLES;
        
        leftOut[i] = leftSignal;
        rightOut[i] = rightSignal;
    }
}

void BinauralProcessor::setSampleRate(int sampleRate) {
    AudioProcessor::setSampleRate(sampleRate);
    updateHRTFCoeffs();
}

void BinauralProcessor::reset() {
    clearDelayBuffer();
}

void BinauralProcessor::setParameter(int param, float value) {
    switch (param) {
        case 0: // Distance
            setDistance(value);
            break;
        case 1: // Azimuth
            setAzimuth(value);
            break;
        case 2: // Elevation
            setElevation(value);
            break;
    }
}

float BinauralProcessor::getParameter(int param) const {
    switch (param) {
        case 0:
            return m_distance;
        case 1:
            return m_azimuth;
        case 2:
            return m_elevation;
        default:
            return 0.0f;
    }
}

void BinauralProcessor::setDistance(float distance) {
    m_distance = clamp(distance, 0.0f, 1.0f);
    updateDistanceSimulation();
}

void BinauralProcessor::setAzimuth(float azimuth) {
    m_azimuth = clamp(azimuth, -180.0f, 180.0f);
    updateHRTFCoeffs();
}

void BinauralProcessor::setElevation(float elevation) {
    m_elevation = clamp(elevation, -90.0f, 90.0f);
    updateHRTFCoeffs();
}

void BinauralProcessor::updateHRTFCoeffs() {
    // Simplified HRTF simulation based on azimuth and elevation
    // In a real implementation, this would use measured HRTF data
    
    // Calculate ITD based on azimuth
    float azimuthRad = m_azimuth * M_PI / 180.0f;
    float itdMs = sinf(azimuthRad) * 0.7f; // Max ITD of 0.7ms
    m_itdSamples = (int)(itdMs * m_sampleRate / 1000.0f);
    m_itdSamples = clamp(m_itdSamples, 0, MAX_ITD_SAMPLES - 1);
    
    // Calculate gains based on azimuth (head shadow effect)
    float leftGain = 1.0f;
    float rightGain = 1.0f;
    
    if (m_azimuth > 0) {
        // Sound source on the right
        leftGain = 1.0f - abs(m_azimuth) / 180.0f * 0.3f; // Head shadow
        rightGain = 1.0f;
    } else {
        // Sound source on the left
        leftGain = 1.0f;
        rightGain = 1.0f - abs(m_azimuth) / 180.0f * 0.3f; // Head shadow
    }
    
    // Apply elevation effects (simplified)
    float elevationRad = m_elevation * M_PI / 180.0f;
    float elevationGain = 1.0f - abs(elevationRad) * 0.2f;
    
    m_hrtfCoeffs.leftGain = leftGain * elevationGain;
    m_hrtfCoeffs.rightGain = rightGain * elevationGain;
    
    // Simple filter coefficients for HRTF simulation
    // These would normally come from measured HRTF data
    m_hrtfCoeffs.leftFilter[0] = 0.8f;
    m_hrtfCoeffs.leftFilter[1] = 0.1f;
    m_hrtfCoeffs.leftFilter[2] = 0.1f;
    
    m_hrtfCoeffs.rightFilter[0] = 0.8f;
    m_hrtfCoeffs.rightFilter[1] = 0.1f;
    m_hrtfCoeffs.rightFilter[2] = 0.1f;
}

void BinauralProcessor::updateDistanceSimulation() {
    // Distance attenuation (inverse square law approximation)
    m_distanceAtten = 1.0f / (1.0f + m_distance * 2.0f);
    
    // Air absorption increases with distance
    m_airAbsorption = 0.05f + m_distance * 0.15f;
}

void BinauralProcessor::clearDelayBuffer() {
    memset(m_delayBuffer, 0, sizeof(m_delayBuffer));
    m_delayIndex[0] = 0;
    m_delayIndex[1] = 0;
}

float BinauralProcessor::processHRTF(float input, const float* filterCoeffs) {
    // Simple 3-tap FIR filter for HRTF simulation
    return input * filterCoeffs[0] + 
           input * filterCoeffs[1] + 
           input * filterCoeffs[2];
} 