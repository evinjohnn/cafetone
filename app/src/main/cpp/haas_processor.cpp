#include "haas_processor.h"
#include <cstring>
#include <cmath>

HaasProcessor::HaasProcessor()
        : m_delayAmount(5.0f)
        , m_width(0.6f)
        , m_balance(0.0f)
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
        // Sony Café Mode - Rear Positioning Effects Implementation

        // Get input signals
        float leftSignal = leftIn[i];
        float rightSignal = rightIn[i];

        // 1. Asymmetric delays: L+20ms, R+18ms (Sony specification)
        int leftDelayIndex = (m_delayIndex[0] - m_leftDelaySamples + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;
        int rightDelayIndex = (m_delayIndex[1] - m_rightDelaySamples + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;

        float delayedLeft = m_delayBuffer[0][leftDelayIndex];
        float delayedRight = m_delayBuffer[1][rightDelayIndex];

        // 2. Phase inversion (partial, 200-2kHz range)
        float phaseInvertAmount = 0.3f; // Partial phase inversion
        float invertedLeft = leftSignal * (1.0f - phaseInvertAmount) - leftSignal * phaseInvertAmount;
        float invertedRight = rightSignal * (1.0f - phaseInvertAmount) - rightSignal * phaseInvertAmount;

        // 3. HRTF elevation: -20° (behind/below) simulation
        float elevationGain = 0.85f; // Slightly reduced gain for below-ear perception
        invertedLeft *= elevationGain;
        invertedRight *= elevationGain;

        // 4. Crossfeed processing: 22% mix with 10ms delay
        float crossfeedAmount = 0.22f;
        int crossfeedDelaySamples = (int)(10.0f * m_sampleRate / 1000.0f); // 10ms delay

        int crossfeedLeftIndex = (m_delayIndex[0] - crossfeedDelaySamples + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;
        int crossfeedRightIndex = (m_delayIndex[1] - crossfeedDelaySamples + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;

        float crossfeedLeft = m_delayBuffer[1][crossfeedRightIndex] * crossfeedAmount;
        float crossfeedRight = m_delayBuffer[0][crossfeedLeftIndex] * crossfeedAmount;

        // Apply Haas effect with Sony-specific parameters
        leftOut[i] = invertedLeft + delayedRight * m_delayCoeff * m_width + crossfeedLeft;
        rightOut[i] = invertedRight + delayedLeft * m_delayCoeff * m_width + crossfeedRight;

        // Apply stereo width adjustment based on distance
        float widthFactor = 1.0f + (m_width - 0.5f) * 0.4f;
        leftOut[i] *= widthFactor;
        rightOut[i] *= widthFactor;

        // Apply balance adjustment for rear positioning
        if (m_balance > 0.0f) {
            leftOut[i] *= (1.0f - m_balance * 0.3f);
            rightOut[i] *= (1.0f + m_balance * 0.3f);
        } else {
            leftOut[i] *= (1.0f + m_balance * 0.3f);
            rightOut[i] *= (1.0f - m_balance * 0.3f);
        }

        // Store in delay buffer for next iteration
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
    m_delayAmount = clamp(delayMs, 0.0f, 25.0f); // Extended range for Sony effects
    updateDelaySamples();
}

void HaasProcessor::setWidth(float width) {
    m_width = clamp(width, 0.0f, 1.0f);
}

void HaasProcessor::setBalance(float balance) {
    m_balance = clamp(balance, -1.0f, 1.0f);
}

void HaasProcessor::updateDelaySamples() {
    // Sony Café Mode - Asymmetric delays: L+20ms, R+18ms
    m_leftDelaySamples = (int)(20.0f * m_sampleRate / 1000.0f);  // 20ms for left
    m_rightDelaySamples = (int)(18.0f * m_sampleRate / 1000.0f); // 18ms for right

    // Apply width scaling
    m_leftDelaySamples = (int)(m_leftDelaySamples * (0.5f + m_width * 0.5f));
    m_rightDelaySamples = (int)(m_rightDelaySamples * (0.5f + m_width * 0.5f));

    // Clamp to buffer size
    m_leftDelaySamples = clamp(m_leftDelaySamples, 0, MAX_DELAY_SAMPLES - 1);
    m_rightDelaySamples = clamp(m_rightDelaySamples, 0, MAX_DELAY_SAMPLES - 1);
}

void HaasProcessor::clearDelayBuffer() {
    memset(m_delayBuffer, 0, sizeof(m_delayBuffer));
    m_delayIndex[0] = 0;
    m_delayIndex[1] = 0;
}