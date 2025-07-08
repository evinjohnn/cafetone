#ifndef EQ_PROCESSOR_H
#define EQ_PROCESSOR_H

#include "audio_processor.h"

class EQProcessor : public AudioProcessor {
public:
    EQProcessor();
    ~EQProcessor() override;
    
    // Core processing
    void process(const float* input, float* output, int frames) override;
    
    // Configuration
    void setSampleRate(int sampleRate) override;
    void reset() override;
    
    // Parameter control
    void setParameter(int param, float value) override;
    float getParameter(int param) const override;
    
    // Specific controls
    void setHighPassFilter(float frequency);
    void setLowPassFilter(float frequency);
    void setCafeEQ(bool enabled);
    void setDistanceEQ(float distance);
    
private:
    // Filter coefficients
    float m_hpCoeff[2];  // High-pass filter coefficients
    float m_lpCoeff[2];  // Low-pass filter coefficients
    
    // Filter state
    float m_hpState[2];  // High-pass filter state
    float m_lpState[2];  // Low-pass filter state
    
    // Parameters
    float m_highPassFreq;
    float m_lowPassFreq;
    bool m_cafeEQEnabled;
    float m_distanceEQ;
    
    // Sony Café EQ bands (exact specifications)
    struct EQBand {
        float frequency;
        float gain;
        float q;
    };
    
    static const int NUM_EQ_BANDS = 5;
    EQBand m_eqBands[NUM_EQ_BANDS];
    
    // Sony-specific processing methods
    float applySonyCafeEQ(float sample);
    float applyDistanceEQ(float sample);
    
    // Utility functions
    void updateHighPassCoeffs();
    void updateLowPassCoeffs();
    void setupSonyCafeEQ();
    float processFilter(float input, float* coeffs, float* state);
};

#endif // EQ_PROCESSOR_H