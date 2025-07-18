#include "binaural_processor.h"
#include <cstring>
#include <cmath>

// GUARANTEED FIX: Reordered initializers to match declaration order in .h file.
BinauralProcessor::BinauralProcessor()
        : m_distance(0.8f)
        , m_azimuth(0.0f)
        , m_elevation(-20.0f)
        , m_spatialWidth(1.7f)
        , m_distanceAtten(0.7f)
        , m_airAbsorption(0.1f)
        , m_itdSamples(0) {

    clearDelayBuffer();
    updateHRTFCoeffs();
    updateDistanceSimulation();
    setupSpatialProcessing();
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
        float leftSignal = leftIn[i];
        float rightSignal = rightIn[i];

        // 1. Stereo width expansion: 170%
        float widthFactor = m_spatialWidth;
        float mid = (leftSignal + rightSignal) * 0.5f;
        float side = (leftSignal - rightSignal) * 0.5f;

        // Mid/Side processing: Mid -5dB, Side +3dB
        float midGain = 0.56f;
        float sideGain = 1.41f;

        mid *= midGain;
        side *= sideGain * widthFactor;

        leftSignal = mid + side;
        rightSignal = mid - side;

        // 2. Decorrelation: 18% on high frequencies
        float decorrelationAmount = 0.18f;
        int delayReadLeft = (m_delayIndex[0] - m_decorrelationDelay + MAX_ITD_SAMPLES) % MAX_ITD_SAMPLES;
        int delayReadRight = (m_delayIndex[1] - m_decorrelationDelay + MAX_ITD_SAMPLES) % MAX_ITD_SAMPLES;
        float decorrelatedLeft = m_decorrelationBuffer[0][delayReadLeft];
        float decorrelatedRight = m_decorrelationBuffer[1][delayReadRight];
        float highFreqMix = decorrelationAmount;
        leftSignal = leftSignal * (1.0f - highFreqMix) + decorrelatedRight * highFreqMix;
        rightSignal = rightSignal * (1.0f - highFreqMix) + decorrelatedLeft * highFreqMix;

        // 3. HRTF processing for rear positioning
        processHRTF(leftSignal, rightSignal, leftSignal, rightSignal);

        // 4. Distance simulation with air absorption
        applyDistanceSimulation(leftSignal, rightSignal, leftSignal, rightSignal);

        // 5. Soundstage widening algorithms
        applySoundstageWidening(leftSignal, rightSignal, leftSignal, rightSignal);

        // Store in decorrelation buffer
        m_decorrelationBuffer[0][m_delayIndex[0]] = leftIn[i];
        m_decorrelationBuffer[1][m_delayIndex[1]] = rightIn[i];

        // Update delay indices
        m_delayIndex[0] = (m_delayIndex[0] + 1) % MAX_ITD_SAMPLES;
        m_delayIndex[1] = (m_delayIndex[1] + 1) % MAX_ITD_SAMPLES;

        leftOut[i] = leftSignal;
        rightOut[i] = rightSignal;
    }
}

void BinauralProcessor::processHRTF(float leftIn, float rightIn, float& leftOut, float& rightOut) {
    float azimuthRad = m_azimuth * M_PI / 180.0f;
    float elevationRad = m_elevation * M_PI / 180.0f;
    float leftGain = m_hrtfCoeffs.leftGain;
    float rightGain = m_hrtfCoeffs.rightGain;
    float elevationFilter = 0.85f + 0.15f * cosf(elevationRad);
    leftOut = leftIn * leftGain * elevationFilter;
    rightOut = rightIn * rightGain * elevationFilter;
    float phaseShift = sinf(azimuthRad) * 0.1f;
    leftOut *= (1.0f + phaseShift);
    rightOut *= (1.0f - phaseShift);
}

void BinauralProcessor::applyDistanceSimulation(float leftIn, float rightIn, float& leftOut, float& rightOut) {
    float distanceGain = m_distanceAtten;
    float airAbsorb = 1.0f - m_airAbsorption * m_distance;
    float totalGain = distanceGain * airAbsorb;
    leftOut = leftIn * totalGain;
    rightOut = rightIn * totalGain;
}

void BinauralProcessor::applySoundstageWidening(float leftIn, float rightIn, float& leftOut, float& rightOut) {
    float enhancement = (m_spatialWidth - 1.0f) * 0.3f;
    float crossMix = enhancement * 0.1f;
    leftOut = leftIn * (1.0f + enhancement) + rightIn * crossMix;
    rightOut = rightIn * (1.0f + enhancement) + leftIn * crossMix;
    float spatialGain = 1.0f + (m_spatialWidth - 1.0f) * 0.2f;
    leftOut *= spatialGain;
    rightOut *= spatialGain;
}

void BinauralProcessor::setSampleRate(int sampleRate) {
    AudioProcessor::setSampleRate(sampleRate);
    updateHRTFCoeffs();
    setupSpatialProcessing();
}

void BinauralProcessor::reset() {
    clearDelayBuffer();
}

void BinauralProcessor::setParameter(int param, float value) {
    switch (param) {
        case 0: setDistance(value); break;
        case 1: setAzimuth(value); break;
        case 2: setElevation(value); break;
        case 3: setSpatialWidth(value); break;
    }
}

float BinauralProcessor::getParameter(int param) const {
    switch (param) {
        case 0: return m_distance;
        case 1: return m_azimuth;
        case 2: return m_elevation;
        case 3: return m_spatialWidth;
        default: return 0.0f;
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

void BinauralProcessor::setSpatialWidth(float width) {
    m_spatialWidth = clamp(width, 0.5f, 3.0f);
}

void BinauralProcessor::updateHRTFCoeffs() {
    float azimuthRad = m_azimuth * M_PI / 180.0f;
    float elevationRad = m_elevation * M_PI / 180.0f;
    float itdMs = sinf(azimuthRad) * cosf(elevationRad) * 0.8f;
    m_itdSamples = (int)(itdMs * m_sampleRate / 1000.0f);
    m_itdSamples = clamp(m_itdSamples, 0, MAX_ITD_SAMPLES - 1);
    float leftGain = 1.0f;
    float rightGain = 1.0f;
    if (m_azimuth > 0) {
        leftGain = 1.0f - abs(m_azimuth) / 180.0f * 0.4f;
    } else {
        rightGain = 1.0f - abs(m_azimuth) / 180.0f * 0.4f;
    }
    float elevationGain = 0.8f + 0.2f * cosf(abs(elevationRad));
    m_hrtfCoeffs.leftGain = leftGain * elevationGain;
    m_hrtfCoeffs.rightGain = rightGain * elevationGain;
}

void BinauralProcessor::updateDistanceSimulation() {
    m_distanceAtten = 1.0f / (1.0f + m_distance * 1.8f);
    m_airAbsorption = 0.08f + m_distance * 0.18f;
}

void BinauralProcessor::setupSpatialProcessing() {
    m_decorrelationDelay = (int)(3.0f * m_sampleRate / 1000.0f);
    m_decorrelationDelay = clamp(m_decorrelationDelay, 1, MAX_ITD_SAMPLES - 1);
}

void BinauralProcessor::clearDelayBuffer() {
    memset(m_delayBuffer, 0, sizeof(m_delayBuffer));
    memset(m_decorrelationBuffer, 0, sizeof(m_decorrelationBuffer));
    m_delayIndex[0] = 0;
    m_delayIndex[1] = 0;
}